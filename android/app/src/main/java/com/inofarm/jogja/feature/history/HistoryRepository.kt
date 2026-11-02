package com.inofarm.jogja.feature.history

import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.harvest.domain.HarvestDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AuditEntry(val id:String,val actor:String,val action:String,val version:Long,val reason:String,val at:String,val before:JSONObject?,val after:JSONObject?) {
    companion object {
        fun from(row:JSONObject)=AuditEntry(row.getString("id"),row.optString("actor_name").ifBlank {row.getString("actor_id")},row.getString("action"),row.getLong("version"),
            row.optString("reason").takeUnless {row.isNull("reason")}.orEmpty(),row.getString("server_at"),row.getJSONObject("changes").optJSONObject("before"),row.getJSONObject("changes").optJSONObject("after"))
    }
}
data class AuditPage(val entries:List<AuditEntry>,val cursor:String?,val fetchedAt:Long,val cached:Boolean)

class HistoryRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val api:FinanceApi,private val kind:OperationalKind=OperationalKind.ACTIVITY) {
    private fun check(session:FinanceSession,ticket:Long) {check(vault.matches(ticket,session.scope)) {"Sesi berubah."}}
    suspend fun records(session:FinanceSession,filter:LocalHistoryFilter,count:Int)=withContext(Dispatchers.IO) {
        buildList {var offset=0;while(size<count) {
            val requested=minOf(1000,count-size)
            val page=store.records(session.scope.key,requested,kind=kind.wire,filter=filter,offset=offset)
            addAll(page);offset+=page.size;if(page.size<requested) break
        }}
    }
    suspend fun detail(session:FinanceSession,id:String)=withContext(Dispatchers.IO) {store.detail(session.scope.key,id)?.takeIf {it.record.kind==kind.wire}}
    suspend fun cachedAudit(session:FinanceSession,id:String)=withContext(Dispatchers.IO) {
        store.cachedOne(session.scope.key,"${kind.wire}_audit",id)?.let {parseAudit(it,true)}
    }
    private fun parseAudit(body:JSONObject,cached:Boolean):AuditPage {
        val rows=body.getJSONArray("data")
        return AuditPage((0 until rows.length()).map {AuditEntry.from(rows.getJSONObject(it))},body.optString("next_cursor").takeUnless {body.isNull("next_cursor") || it.isBlank()},body.getLong("fetched_at"),cached)
    }
    suspend fun audit(session:FinanceSession,ticket:Long,record:LocalRecord,cursor:String?,previous:AuditPage?):AuditPage=withContext(Dispatchers.IO) {
        check(session,ticket)
        require(record.greenhouseId in store.visibleGreenhouses(session.scope.key)) {"Penugasan tidak lagi berlaku."}
        require(record.kind==kind.wire)
        val result=api.request(session.scope.server,session.token,"${kind.resource}/${record.id}/audit",query=mapOf("limit" to "50")+(cursor?.let {mapOf("cursor" to it)} ?: emptyMap()))
        check(session,ticket)
        val data=JSONArray()
        if(cursor!=null && previous!=null) previous.entries.forEach {entry -> data.put(JSONObject().put("id",entry.id).put("actor_id",entry.actor).put("actor_name",entry.actor)
            .put("action",entry.action).put("version",entry.version).put("reason",entry.reason).put("server_at",entry.at).put("changes",JSONObject().put("before",entry.before ?: JSONObject.NULL).put("after",entry.after ?: JSONObject.NULL))) }
        val rows=result.getJSONArray("data");repeat(rows.length()) {data.put(rows.getJSONObject(it))}
        val snapshot=JSONObject().put("data",data).put("next_cursor",result.opt("next_cursor") ?: JSONObject.NULL).put("fetched_at",System.currentTimeMillis())
        store.cacheAudit(session.scope.key,record.id,record.greenhouseId,snapshot,"${kind.wire}_audit")
        parseAudit(snapshot,false)
    }
    suspend fun resolve(session:FinanceSession,ticket:Long,reviewed:LocalRecord,keepLocal:Boolean,reason:String,policy:String)=withContext(Dispatchers.IO) {
        check(session,ticket)
        require(reviewed.kind==kind.wire)
        val version=reviewed.serverVersion ?: error("Versi server belum tersedia.")
        val payload=if(keepLocal) {
            require(reason.isNotBlank() && reason.length<=1000) {"Alasan koreksi wajib, maksimal 1.000 karakter."}
            require(policy in setOf("preserve","refresh")) {"Pilih kebijakan acuan historis."}
            when(kind) {
                OperationalKind.ACTIVITY -> ActivityDraft.from(reviewed.id,reviewed.greenhouseId,reviewed.body,version).copy(reason=reason,referencePolicy=policy).payload()
                OperationalKind.HARVEST -> HarvestDraft.from(reviewed.id,reviewed.greenhouseId,reviewed.body,version).copy(reason=reason,referencePolicy=policy).payload()
            }
        } else null
        store.resolveConflict(session.scope.key,reviewed.id,version,reviewed.revision,payload)
    }
    suspend fun cancel(session:FinanceSession,ticket:Long,reviewed:LocalRecord,reason:String)=withContext(Dispatchers.IO) {
        check(session,ticket);require(session.role==UserRole.MANAGER) {"Pembatalan memerlukan pengelola."}
        require(reason.isNotBlank() && reason.length<=1000) {"Alasan wajib, maksimal 1.000 karakter."}
        require(reviewed.syncState==SyncState.ACCEPTED && reviewed.body.optString("record_state")!="cancelled") {"Catatan perlu diterima server dan masih aktif."}
        require(reviewed.kind==kind.wire)
        store.queueAction(session.scope.key,reviewed.id,reviewed.greenhouseId,kind.wire,reviewed.body,JSONObject().put("reason",reason),"cancel",reviewed.serverVersion!!,reviewed.revision)
    }
    suspend fun season(session:FinanceSession,ticket:Long,reviewed:JSONObject,reason:String)=withContext(Dispatchers.IO) {
        check(session,ticket);require(session.role==UserRole.MANAGER) {"Tindakan ini memerlukan pengelola."}
        require(reason.isNotBlank() && reason.length<=1000) {"Alasan wajib, maksimal 1.000 karakter."}
        val id=reviewed.getString("id");val gh=reviewed.getString("greenhouse_id")
        val action=if(reviewed.getString("status")=="open") "close" else "reopen"
        val old=store.record(session.scope.key,id)
        store.queueAction(session.scope.key,id,gh,"seasons",reviewed,JSONObject().put("reason",reason),action,reviewed.getLong("version"),old?.revision)
    }
    suspend fun retryClosed(session:FinanceSession,ticket:Long,record:LocalRecord)=withContext(Dispatchers.IO) {
        check(session,ticket)
        val groupId=record.body.optJSONObject("_local_form")?.optString("group_id") ?: record.body.getString("planting_group_id")
        val group=store.cachedOne(session.scope.key,"planting_groups",groupId) ?: error("Kelompok belum tersedia. Sinkronkan dahulu.")
        val season=store.cachedOne(session.scope.key,"seasons",group.getString("season_id")) ?: error("Musim belum tersedia. Sinkronkan dahulu.")
        require(season.getString("status")=="open") {"Musim masih ditutup pada data terakhir. Pengelola perlu membukanya kembali, lalu sinkronkan."}
        val op=store.pendingFor(session.scope.key,record.id).singleOrNull {it.state=="REJECTED" && it.error?.optString("code")=="season_closed"} ?: error("Operasi tertahan tidak ditemukan.")
        store.retryClosedSeason(session.scope.key,op.id)
    }
}
