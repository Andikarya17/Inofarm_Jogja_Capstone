package com.inofarm.jogja.feature.recovery

import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RecoveryRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val api:FinanceApi) {
    private fun session()=vault.current ?: error("Masuk lagi untuk melanjutkan.")
    private suspend fun request(path:String,method:String="GET",body:JSONObject?=null,query:Map<String,String> = emptyMap()):JSONObject=withContext(Dispatchers.IO) {
        val session=session();val ticket=vault.generation
        val result=api.request(session.scope.server,session.token,path,method,body,query)
        check(vault.matches(ticket,session.scope)) {"Akun berubah. Coba lagi."};result
    }
    suspend fun policy()=request("recovery/policy").getJSONObject("data")
    suspend fun list(before:String?=null)=request("recovery/backups",query=before?.let {mapOf("before" to it)} ?: emptyMap())
    suspend fun restores(before:String?=null)=request("recovery/restores",query=before?.let {mapOf("before" to it)} ?: emptyMap())
    suspend fun create(id:String,reason:String):JSONObject {
        WireContract.uuid(id);require(reason.isNotBlank() && reason.length<=1000) {"Isi alasan, maksimal 1.000 karakter."}
        return request("recovery/backups","POST",JSONObject().put("request_id",id).put("reason",reason)).getJSONObject("data")
    }
    suspend fun validate(row:JSONObject):JSONObject {
        val id=row.getString("id");WireContract.uuid(id);require(row.getString("state")=="completed")
        val hash=row.getString("manifest_hash");require(Regex("[0-9a-f]{64}").matches(hash))
        return request("recovery/backups/$id/validate","POST",JSONObject().put("expected_manifest_hash",hash)).getJSONObject("data")
    }
    fun canReview(op:QueuedOperation):Boolean=runCatching {val session=session();val row=store.record(session.scope.key,op.entityId)!!;op.state in setOf("EPOCH_REVIEW","ACCEPTED") && op.envelope.getString("sync_epoch")!=store.epoch(session.scope.key) && row.syncState==SyncState.EPOCH_REVIEW && row.greenhouseId in store.visibleGreenhouses(session.scope.key) && (session.role==UserRole.MANAGER || op.envelope.getString("entity_type") in setOf("activity","harvest","activity_attachment","harvest_attachment"))}.getOrDefault(false)
    suspend fun inspect(op:QueuedOperation):JSONObject=withContext(Dispatchers.IO) {
        val session=session();val epoch=store.epoch(session.scope.key) ?: error("Data belum dimuat. Perbarui dulu.")
        require(canReview(op) && WireContract.hash(op.envelope)==op.hash)
        require(op.envelope.getString("sync_epoch")!=epoch)
        val row=store.record(session.scope.key,op.entityId) ?: error("Catatan belum ada di perangkat.")
        require(row.greenhouseId in store.visibleGreenhouses(session.scope.key)) {"Anda tidak lagi punya akses ke greenhouse ini."}
        if(op.envelope.getString("entity_type") !in setOf("activity","harvest","activity_attachment","harvest_attachment")) require(session.role==UserRole.MANAGER)
        val result=request("sync/reconcile","POST",JSONObject().put("sync_epoch",epoch).put("operation",op.envelope))
        require(result.getString("sync_epoch")==epoch && result.getString("operation_id")==op.id && result.getString("entity_id")==op.entityId && result.getString("request_hash")==op.hash)
        require(result.getString("state") in setOf("receipt_found","manual_review"));result
    }
    suspend fun acknowledge(op:QueuedOperation,review:JSONObject)=withContext(Dispatchers.IO) {
        val session=session();val ticket=vault.generation
        // Refresh receipt authorization immediately before changing local acceptance state.
        val latest=inspect(op)
        check(vault.matches(ticket,session.scope));require(WireContract.hash(latest)==WireContract.hash(review)) {"Hasil cek berubah. Cek lagi."}
        store.acknowledge(session.scope.key,op.id,latest.getJSONObject("receipt"),latest)
    }
}
