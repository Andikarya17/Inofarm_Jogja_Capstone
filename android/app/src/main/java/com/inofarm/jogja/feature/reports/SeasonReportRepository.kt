package com.inofarm.jogja.feature.reports

import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReportSourcePage(val body:JSONObject,val cached:Boolean) {
    val rows get()=body.getJSONArray("data")
    val cursor get()=body.optString("next_cursor").takeUnless {body.isNull("next_cursor") || it.isBlank()}
}

class SeasonReportRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val api:FinanceApi) {
    private val writes=Mutex()
    private fun check(session:FinanceSession,ticket:Long) {check(vault.matches(ticket,session.scope));require(session.role==UserRole.MANAGER) {"Evaluasi keuangan hanya untuk pengelola."}}
    suspend fun records(session:FinanceSession,gh:String?,season:String?,offset:Int)=withContext(Dispatchers.IO) {
        check(session,vault.generation);store.hydrateReportCache(session.scope.key)
        store.records(session.scope.key,51,kind=SeasonReportContract.KIND,filter=LocalHistoryFilter(greenhouseId=gh?.takeIf {it.isNotBlank()},seasonId=season?.takeIf {it.isNotBlank()}),offset=offset)
    }
    suspend fun flush(session:FinanceSession,ticket:Long,draft:SeasonReportDraft)=withContext(Dispatchers.IO) {writes.withLock {saveRaw(session,ticket,draft)}}
    private fun saveRaw(session:FinanceSession,ticket:Long,draft:SeasonReportDraft):LocalRecord {
        check(session,ticket);val old=store.record(session.scope.key,draft.id)
        require(old==null || old.kind==SeasonReportContract.KIND && old.serverVersion==null) {"Laporan ini sudah terbit dan tidak bisa diubah. Buat evaluasi baru."}
        val previous=old?.body?.optJSONObject("_local_form");val body=draft.localJson()
        if(previous!=null && WireContract.hash(previous)==WireContract.hash(body.getJSONObject("_local_form"))) return old
        return store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,body,old?.revision,kind=SeasonReportContract.KIND)
    }
    private fun pendingReview(session:FinanceSession,draft:SeasonReportDraft) {
        val known=store.pendingForSeasonReport(session.scope.key,draft.greenhouseId,draft.seasonId)
        require(draft.pending.isBlank() || draft.pending.toInt()>=known) {"Ada $known catatan musim ini yang belum terkirim dari perangkat ini. Isi jumlah belum terkirim minimal $known, atau kosongkan."}
        require(draft.requestedState!="reviewed_final" || known==0) {"Masih ada catatan musim ini yang belum terkirim. Kirim dulu sebelum membuat laporan akhir."}
    }
    suspend fun preview(session:FinanceSession,ticket:Long,draft:SeasonReportDraft)=withContext(Dispatchers.IO) {writes.withLock {
        saveRaw(session,ticket,draft)
        val input=draft.input();pendingReview(session,draft);val season=store.cachedOne(session.scope.key,"seasons",draft.seasonId) ?: error("Musim belum ada di perangkat. Perbarui dulu.")
        require(season.getString("greenhouse_id")==draft.greenhouseId)
        require(draft.requestedState!="reviewed_final" || season.getString("status")=="closed") {"Tutup musim dulu sebelum membuat laporan akhir."}
        val body=SeasonReportContract.validate(api.request(session.scope.server,session.token,"season-reports/preview","POST",input),false)
        check(session,ticket);require(body.getString("greenhouse_id")==draft.greenhouseId && body.getString("season_id")==draft.seasonId)
        draft.copy(previewText=body.toString(),previewFetchedAt=System.currentTimeMillis()).also {saveRaw(session,ticket,it)}
    }}
    suspend fun queue(session:FinanceSession,ticket:Long,draft:SeasonReportDraft)=withContext(Dispatchers.IO) {writes.withLock {
        val saved=saveRaw(session,ticket,draft);val payload=draft.payload();pendingReview(session,draft)
        store.saveAndQueue(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),saved.revision,replaceValidationRejected=draft.replaceRejected,kind=SeasonReportContract.KIND,queuePayload=payload)
    }}
    suspend fun detail(session:FinanceSession,id:String)=withContext(Dispatchers.IO) {
        check(session,vault.generation);store.hydrateReportCache(session.scope.key)
        val row=store.record(session.scope.key,id) ?: error("Laporan belum tersimpan.")
        require(row.kind==SeasonReportContract.KIND && row.greenhouseId in store.visibleGreenhouses(session.scope.key))
        if(row.serverVersion!=null) require(SeasonReportContract.validate(row.serverBody ?: row.body,true).getString("greenhouse_id")==row.greenhouseId)
        row
    }
    suspend fun visible(session:FinanceSession,gh:String)=withContext(Dispatchers.IO) {gh in store.visibleGreenhouses(session.scope.key)}
    suspend fun verify(session:FinanceSession,ticket:Long,row:LocalRecord)=withContext(Dispatchers.IO) {
        check(session,ticket);val local=SeasonReportContract.validate(row.serverBody ?: row.body,true)
        val response=SeasonReportContract.validate(api.request(session.scope.server,session.token,"reports/${row.id}"),true)
        check(session,ticket);require(WireContract.hash(local)==WireContract.hash(response)) {"Isi laporan di server berbeda dari salinan di perangkat. Laporan tidak dihitung ulang."}
    }
    suspend fun cachedSources(session:FinanceSession,id:String,hash:String,snapshotHash:String?):ReportSourcePage?=withContext(Dispatchers.IO) {
        val body=store.cachedOne(session.scope.key,"season_report_sources",id) ?: return@withContext null
        if(body.optString("sources_hash")!=hash || body.optString("snapshot_hash").takeUnless {body.isNull("snapshot_hash")}!=snapshotHash) return@withContext null
        ReportSourcePage(body,true)
    }
    suspend fun sources(session:FinanceSession,ticket:Long,id:String,gh:String,season:String,hash:String,snapshotHash:String?,previous:ReportSourcePage?)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);require(gh in store.visibleGreenhouses(session.scope.key))
        val cursor=previous?.cursor;val query=mutableMapOf("limit" to "25");cursor?.let {query["cursor"]=it}
        val path=if(snapshotHash==null) {query["season_id"]=season;query["sources_hash"]=hash;"season-reports/preview/sources"} else "reports/$id/sources"
        val page=api.request(session.scope.server,session.token,path,query=query);check(session,ticket)
        require(page.getString("sources_hash")==hash && page.getInt("source_count") in 0..10000)
        if(snapshotHash!=null) require(page.getString("snapshot_hash")==snapshotHash)
        val all=JSONArray();val seen=mutableSetOf<String>()
        fun add(rows:JSONArray) {repeat(rows.length()) {val row=rows.getJSONObject(it);WireContract.uuid(row.getString("id"));require(row.getString("kind") in setOf("expense","cost_allocation","asset","depreciation_allocation","sale") && row.getInt("version")>0);row.getBoolean("included");row.getJSONObject("data");require(row.getString("reason").isNotBlank());if(seen.add(row.getString("kind")+row.getString("id"))) all.put(row)}}
        if(previous!=null && cursor!=null) add(previous.rows);add(page.getJSONArray("data"));require(all.length()<=page.getInt("source_count"))
        val body=JSONObject(page.toString()).put("data",all).put("snapshot_hash",snapshotHash ?: JSONObject.NULL).put("fetched_at",System.currentTimeMillis())
        check(session,ticket);store.cacheReportSources(session.scope.key,id,gh,body);ReportSourcePage(body,false)
    }}
}
