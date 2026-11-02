package com.inofarm.jogja.feature.investments

import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.feature.reports.ReportSourcePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class InvestmentAccessDenied:IllegalStateException("Akses greenhouse perlu diperiksa kembali. Data lokal dipertahankan.")

class InvestmentRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val api:FinanceApi) {
    private val writes=Mutex()
    private fun check(session:FinanceSession,ticket:Long,gh:String?=null) {
        check(vault.matches(ticket,session.scope));require(session.role==UserRole.MANAGER) {"Investasi hanya untuk pengelola."}
        if(gh!=null && gh !in store.visibleGreenhouses(session.scope.key)) throw InvestmentAccessDenied()
    }
    suspend fun records(session:FinanceSession,kind:String,gh:String?,scenario:String?,offset:Int)=withContext(Dispatchers.IO) {
        check(session,vault.generation);store.hydrateReportCache(session.scope.key,kind)
        store.records(session.scope.key,51,kind,LocalHistoryFilter(greenhouseId=gh?.takeIf {it.isNotBlank()},scenarioId=scenario?.takeIf {it.isNotBlank()}),offset)
    }
    suspend fun detail(session:FinanceSession,id:String)=withContext(Dispatchers.IO) {
        check(session,vault.generation);listOf(InvestmentContract.SCENARIO,InvestmentContract.REPORT).forEach {store.hydrateReportCache(session.scope.key,it)}
        val row=store.record(session.scope.key,id) ?: error("Catatan belum tersimpan.");require(row.kind in setOf(InvestmentContract.SCENARIO,InvestmentContract.REPORT));check(session,vault.generation,row.greenhouseId);row
    }
    suspend fun visible(session:FinanceSession,gh:String)=withContext(Dispatchers.IO) {gh in store.visibleGreenhouses(session.scope.key)}
    suspend fun flushScenario(session:FinanceSession,ticket:Long,draft:InvestmentScenarioDraft)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);val old=store.record(session.scope.key,draft.id);require(old==null || old.kind==InvestmentContract.SCENARIO)
        if(old?.syncState==SyncState.ACCEPTED && InvestmentScenarioDraft.from(old.id,old.greenhouseId,old.body,old.serverVersion)==draft) return@withLock old
        if(old?.body?.optJSONObject("_local_form")?.let {WireContract.hash(it)}==WireContract.hash(draft.localJson().getJSONObject("_local_form"))) return@withLock old
        store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),old?.revision,draft.baseVersion,InvestmentContract.SCENARIO)
    }}
    suspend fun queueScenario(session:FinanceSession,ticket:Long,draft:InvestmentScenarioDraft)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket,draft.greenhouseId);val old=store.record(session.scope.key,draft.id)
        require(old==null || old.kind==InvestmentContract.SCENARIO);require(old?.serverBody?.optString("record_state")!="cancelled") {"Skenario telah dibatalkan."}
        val effective=draft.copy(baseVersion=draft.baseVersion ?: old?.baseVersion ?: old?.serverVersion);val payload=effective.payload()
        store.saveAndQueue(session.scope.key,draft.id,draft.greenhouseId,effective.localJson().put("completeness","ready"),old?.revision,effective.baseVersion,draft.replaceRejected,InvestmentContract.SCENARIO,payload)
    }}
    suspend fun flushCancel(session:FinanceSession,ticket:Long,row:LocalRecord,reason:String)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);val old=store.record(session.scope.key,row.id) ?: error("Skenario belum tersimpan.")
        require(old.kind==InvestmentContract.SCENARIO && old.serverVersion==row.serverVersion)
        val body=JSONObject((old.serverBody ?: error("Belum diterima server.")).toString()).put("_local_cancel",reason)
        if(old.body.optString("_local_cancel")==reason && old.body.has("_local_cancel")) old else store.saveDraft(session.scope.key,row.id,row.greenhouseId,body,old.revision,row.serverVersion,InvestmentContract.SCENARIO)
    }}
    suspend fun cancel(session:FinanceSession,ticket:Long,row:LocalRecord,reason:String)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket,row.greenhouseId);require(reason.isNotBlank() && reason.length<=2000) {"Alasan pembatalan wajib, maksimal 2.000 karakter."}
        val old=store.record(session.scope.key,row.id) ?: error("Skenario belum tersimpan.");val body=InvestmentContract.scenario(old.serverBody ?: error("Belum diterima server."))
        require(body.getString("record_state")=="active" && old.serverVersion==row.serverVersion)
        store.queueAction(session.scope.key,row.id,row.greenhouseId,InvestmentContract.SCENARIO,JSONObject(old.body.toString()).put("_local_cancel",reason),JSONObject().put("reason",reason.trim()),"cancel",row.serverVersion!!,old.revision,replaceValidationRejected=old.syncState==SyncState.REJECTED)
    }}
    suspend fun discardCancel(session:FinanceSession,ticket:Long,row:LocalRecord)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket,row.greenhouseId);val current=store.record(session.scope.key,row.id) ?: error("Skenario belum tersimpan.")
        store.discardInvestmentCancel(session.scope.key,current.id,current.revision)
    }}
    suspend fun resolve(session:FinanceSession,ticket:Long,row:LocalRecord,local:Boolean)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket,row.greenhouseId);require(row.kind==InvestmentContract.SCENARIO)
        val payload=if(local) {require(!row.body.has("_local_cancel")) {"Tinjau server lalu ajukan pembatalan baru."};InvestmentScenarioDraft.from(row.id,row.greenhouseId,row.body,row.serverVersion).payload()} else null
        store.resolveConflict(session.scope.key,row.id,row.serverVersion ?: error("Versi server belum tersedia."),row.revision,payload)
    }}
    suspend fun verify(session:FinanceSession,ticket:Long,row:LocalRecord)=withContext(Dispatchers.IO) {
        check(session,ticket,row.greenhouseId);val local=row.serverBody ?: row.body
        val path=if(row.kind==InvestmentContract.REPORT) "investment-reports" else "investment-scenarios"
        val response=api.request(session.scope.server,session.token,"$path/${row.id}");check(session,ticket,row.greenhouseId)
        if(row.kind==InvestmentContract.REPORT) {InvestmentContract.report(response,true);require(WireContract.hash(local)==WireContract.hash(response)) {"Snapshot server berbeda. Periksa integritas; laporan tidak dihitung ulang."}}
        else {InvestmentContract.scenario(response);require(response.getString("id")==row.id && response.getString("greenhouse_id")==row.greenhouseId);require(WireContract.hash(local)==WireContract.hash(response)) {"Skenario server berubah. Sinkronkan dan tinjau versi terbaru."}}
    }
    private fun saveReport(session:FinanceSession,ticket:Long,draft:InvestmentReportDraft):LocalRecord {
        check(session,ticket);val old=store.record(session.scope.key,draft.id)
        require(old==null || old.kind==InvestmentContract.REPORT && old.serverVersion==null) {"Laporan immutable. Buat evaluasi baru."}
        val body=draft.localJson();if(old?.body?.optJSONObject("_local_form")?.let {WireContract.hash(it)}==WireContract.hash(body.getJSONObject("_local_form"))) return old!!
        return store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,body,old?.revision,kind=InvestmentContract.REPORT)
    }
    suspend fun flushReport(session:FinanceSession,ticket:Long,draft:InvestmentReportDraft)=withContext(Dispatchers.IO) {writes.withLock {saveReport(session,ticket,draft)}}
    private fun acceptedScenario(session:FinanceSession,draft:InvestmentReportDraft):JSONObject {
        check(session,vault.generation,draft.greenhouseId);store.hydrateReportCache(session.scope.key,InvestmentContract.SCENARIO)
        val row=store.record(session.scope.key,draft.scenarioId) ?: error("Skenario belum tersinkron.")
        require(row.kind==InvestmentContract.SCENARIO && row.greenhouseId==draft.greenhouseId && row.syncState==SyncState.ACCEPTED) {"Sinkronkan perubahan skenario sebelum evaluasi."}
        return InvestmentContract.scenario(row.serverBody ?: row.body).also {require(it.getString("record_state")=="active") {"Skenario telah dibatalkan."}}
    }
    suspend fun preview(session:FinanceSession,ticket:Long,draft:InvestmentReportDraft)=withContext(Dispatchers.IO) {writes.withLock {
        saveReport(session,ticket,draft);val scenario=acceptedScenario(session,draft)
        val body=InvestmentContract.report(api.request(session.scope.server,session.token,"investment-reports/preview","POST",draft.input()),false)
        check(session,ticket,draft.greenhouseId);require(body.getString("scenario_id")==draft.scenarioId && body.getString("greenhouse_id")==draft.greenhouseId && body.getInt("scenario_version")==scenario.getInt("version")) {"Versi skenario berubah. Sinkronkan dan tinjau kembali."}
        draft.copy(previewText=body.toString(),previewFetchedAt=System.currentTimeMillis()).also {saveReport(session,ticket,it)}
    }}
    suspend fun queueReport(session:FinanceSession,ticket:Long,draft:InvestmentReportDraft)=withContext(Dispatchers.IO) {writes.withLock {
        val saved=saveReport(session,ticket,draft);val scenario=acceptedScenario(session,draft);val payload=draft.payload()
        require(scenario.getInt("version")==payload.getInt("expected_scenario_version")) {"Versi skenario berubah. Tinjau pratinjau kembali."}
        store.saveAndQueue(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),saved.revision,replaceValidationRejected=draft.replaceRejected,kind=InvestmentContract.REPORT,queuePayload=payload)
    }}
    suspend fun cachedSources(session:FinanceSession,row:LocalRecord):ReportSourcePage?=withContext(Dispatchers.IO) {
        check(session,vault.generation,row.greenhouseId);val data=InvestmentContract.report(row.serverBody ?: row.body,true)
        val body=store.cachedOne(session.scope.key,"investment_report_sources",row.id) ?: return@withContext null
        if(body.getString("snapshot_hash")!=data.getString("snapshot_hash") || body.getString("sources_hash")!=data.getString("sources_hash")) return@withContext null
        ReportSourcePage(body,true)
    }
    suspend fun sources(session:FinanceSession,ticket:Long,row:LocalRecord,previous:ReportSourcePage?)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket,row.greenhouseId);val data=InvestmentContract.report(row.serverBody ?: row.body,true)
        val query=mutableMapOf("limit" to "25");previous?.cursor?.let {query["cursor"]=it}
        val page=api.request(session.scope.server,session.token,"investment-reports/${row.id}/sources",query=query);check(session,ticket,row.greenhouseId)
        require(page.getString("sources_hash")==data.getString("sources_hash") && page.getString("snapshot_hash")==data.getString("snapshot_hash") && page.getInt("source_count")==data.getInt("source_count"))
        val all=JSONArray();val seen=mutableSetOf<String>()
        fun add(rows:JSONArray) {repeat(rows.length()) {val source=InvestmentContract.source(rows.getJSONObject(it),data.getString("scenario_id"),data.getInt("scenario_version"));if(seen.add(source.getString("source_key"))) all.put(source)}}
        if(previous!=null && previous.cursor!=null) add(previous.rows);add(page.getJSONArray("data"));require(all.length()<=page.getInt("source_count"))
        val body=JSONObject(page.toString()).put("data",all).put("fetched_at",System.currentTimeMillis());store.cacheReportSources(session.scope.key,row.id,row.greenhouseId,body,InvestmentContract.REPORT);ReportSourcePage(body,false)
    }}
    suspend fun cachedAudit(session:FinanceSession,row:LocalRecord)=withContext(Dispatchers.IO) {
        check(session,vault.generation,row.greenhouseId);store.cachedOne(session.scope.key,"investment_scenario_audit",row.id)
    }
    suspend fun audit(session:FinanceSession,ticket:Long,row:LocalRecord,previous:JSONObject?)=withContext(Dispatchers.IO) {
        check(session,ticket,row.greenhouseId);require(row.kind==InvestmentContract.SCENARIO)
        val query=mutableMapOf("limit" to "25");previous?.optString("next_cursor")?.takeUnless {it.isBlank() || it=="null"}?.let {query["cursor"]=it}
        val page=api.request(session.scope.server,session.token,"investment-scenarios/${row.id}/audit",query=query);check(session,ticket,row.greenhouseId)
        val all=JSONArray();val seen=mutableSetOf<Long>()
        fun add(rows:JSONArray) {repeat(rows.length()) {val event=rows.getJSONObject(it);if(seen.add(event.getLong("id"))) all.put(event)}}
        if(previous!=null && query.containsKey("cursor")) add(previous.getJSONArray("data"));add(page.getJSONArray("data"))
        val body=JSONObject(page.toString()).put("data",all).put("fetched_at",System.currentTimeMillis());store.cacheAudit(session.scope.key,row.id,row.greenhouseId,body,"investment_scenario_audit");body
    }
}
