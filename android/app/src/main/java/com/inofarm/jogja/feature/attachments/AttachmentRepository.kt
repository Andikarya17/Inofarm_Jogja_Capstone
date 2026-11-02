package com.inofarm.jogja.feature.attachments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.LocalBlob
import com.inofarm.jogja.core.files.EncryptedAttachmentStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

/** File copy is durable before upload is queued. No plaintext copy is kept in app storage. */
class AttachmentRepository(context:Context,private val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val api:FinanceApi,private val files:EncryptedAttachmentStore) {
    private val resolver=context.applicationContext.contentResolver
    private val writes=Mutex()
    private fun check(session:FinanceSession,ticket:Long) {check(vault.matches(ticket,session.scope)) {"Sesi berubah; pekerjaan akun lama tetap disimpan."}}
    private fun authorized(session:FinanceSession,record:LocalRecord,write:Boolean=false) {
        require(vault.current?.scope==session.scope && vault.current?.role==session.role) {"Sesi berubah."}
        require(record.greenhouseId in store.visibleGreenhouses(session.scope.key)) {"Penugasan tidak lagi berlaku."}
        val owner=record.kind.removeSuffix("_attachment")
        require(owner in setOf("activity","harvest","expense"))
        require(owner!="expense" || session.role==UserRole.MANAGER) {"Bukti transaksi hanya untuk pengelola."}
        if(write && record.kind !in AttachmentContract.kinds) {
            val accepted=record.serverBody ?: error("Catatan induk belum diterima server. Simpan dan sinkronkan dahulu.")
            require(session.role==UserRole.MANAGER || accepted.optString("recorded_by")==session.scope.accountId) {"Lampiran pekerja hanya untuk catatan sendiri."}
        }
    }
    private fun owner(session:FinanceSession,attachment:LocalRecord):LocalRecord {
        val body=attachment.serverBody ?: attachment.body
        val parent=store.record(session.scope.key,body.getString("owner_id")) ?: error("Catatan induk belum tersimpan lengkap. Sinkronkan dahulu.")
        require(parent.kind==body.getString("owner_type") && parent.greenhouseId==attachment.greenhouseId)
        return parent
    }
    suspend fun rows(session:FinanceSession,parent:LocalRecord,count:Int=50)=withContext(Dispatchers.IO) {
        authorized(session,parent)
        buildList {var offset=0;while(size<count) {
            val requested=minOf(1000,count-size);val page=store.records(session.scope.key,requested,kind=parent.kind+"_attachment",filter=LocalHistoryFilter(ownerId=parent.id),offset=offset)
            addAll(page);offset+=page.size;if(page.size<requested) break
        }}
    }
    suspend fun policy(session:FinanceSession,ticket:Long,parent:LocalRecord):JSONObject=withContext(Dispatchers.IO) {
        check(session,ticket);authorized(session,parent)
        val response=api.request(session.scope.server,session.token,"attachments/policy",query=mapOf("owner_type" to parent.kind,"owner_id" to parent.id)).getJSONObject("data").put("fetched_at",System.currentTimeMillis())
        check(session,ticket);store.cacheAttachmentPolicy(session.scope.key,parent.id,parent.kind+"_attachment",parent.greenhouseId,response);response
    }
    suspend fun cachedPolicy(session:FinanceSession,parent:LocalRecord)=withContext(Dispatchers.IO) {
        authorized(session,parent);store.cachedOne(session.scope.key,parent.kind+"_attachment_policy",parent.id)
    }
    suspend fun importUri(session:FinanceSession,ticket:Long,parent:LocalRecord,uri:Uri,name:String?=null):LocalRecord=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);authorized(session,parent,true)
        require(parent.serverVersion!=null) {"Catatan induk belum diterima server."}
        val original=name ?: resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null} ?: ""
        val persistent=runCatching {resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);true}.getOrDefault(false)
        val id=UUID.randomUUID().toString();val blobId=UUID.randomUUID().toString()
        val source=JSONObject().put("source_uri",uri.toString()).put("persistable_grant",persistent).put("original_name",original).put("copy_mode","upload")
        val body=JSONObject().put("owner_type",parent.kind).put("owner_id",parent.id).put("owner_version",parent.serverVersion).put("original_name",original)
        store.beginAttachmentCopy(session.scope.key,id,parent.greenhouseId,parent.kind+"_attachment",body,blobId,source)
        copy(session,ticket,blobId)
    }}
    private suspend fun copy(session:FinanceSession,ticket:Long,blobId:String):LocalRecord {
        val blob=store.blob(session.scope.key,blobId) ?: error("Draf salinan tidak ditemukan.")
        require(blob.state in setOf("COPYING","FAILED")) {"Salinan siap tidak ditimpa."}
        try {
            val job=currentCoroutineContext()
            val metadata=if(files.exists(session.scope.key,blob.id)) files.describe(session.scope.key,blob.id,blob.body.getString("original_name")) else {
                val uri=Uri.parse(blob.body.getString("source_uri"))
                (resolver.openInputStream(uri) ?: error("Berkas sumber belum dapat dibuka. Pilih kembali sebagai lampiran baru.")).use {input -> files.copy(session.scope.key,blob.id,blob.body.getString("original_name"),input) {job.ensureActive()}}
            }
            // A late copy stays encrypted in its original scope; it never switches account.
            val saved=store.finishAttachmentCopy(session.scope.key,blob.id,metadata)
            check(session,ticket);return saved
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(error:Exception) {
            if(store.blob(session.scope.key,blob.id)?.state!="READY") store.failAttachmentCopy(session.scope.key,blob.id,error.message ?: "Salinan belum selesai. Izin sumber dapat berakhir; pilih ulang bila diperlukan.")
            throw error
        }
    }
    suspend fun resumeCopy(session:FinanceSession,ticket:Long,id:String)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);val record=store.record(session.scope.key,id) ?: error("Draf tidak ditemukan.");authorized(session,record)
        val parent=owner(session,record);authorized(session,parent,true)
        copy(session,ticket,record.body.getString("_local_blob_id"))
    }}
    private fun payload(record:LocalRecord):JSONObject {
        val body=record.body;val data=AttachmentBytes.parse(body).json()
        listOf("owner_type","owner_id","owner_version").forEach {data.put(it,body.get(it))}
        return data
    }
    private fun reviewed(session:FinanceSession,record:LocalRecord):Pair<LocalBlob,JSONObject> {
        authorized(session,record);val parent=owner(session,record);authorized(session,parent,true)
        require(parent.serverBody?.optString("record_state")=="active") {"Catatan induk telah dibatalkan atau belum diterima."}
        val policy=store.cachedOne(session.scope.key,record.kind+"_policy",parent.id)
        val metadata=AttachmentBytes.parse(record.body);AttachmentContract.validatePolicy(policy,metadata)
        require(policy!!.getLong("owner_version")==record.body.getLong("owner_version") && parent.serverVersion==record.body.getLong("owner_version")) {"Versi catatan induk berubah. Tinjau versi terbaru sebelum pengajuan baru."}
        val blob=store.blob(session.scope.key,record.body.getString("_local_blob_id")) ?: error("Salinan belum ditemukan.")
        require(blob.state=="READY");files.verify(session.scope.key,blob.id,metadata)
        return blob to policy
    }
    suspend fun queue(session:FinanceSession,ticket:Long,id:String)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);val record=store.record(session.scope.key,id) ?: error("Draf tidak ditemukan.")
        val (blob,_)=reviewed(session,record)
        store.saveAndQueue(session.scope.key,id,record.greenhouseId,record.body,record.revision,kind=record.kind,queuePayload=payload(record),blobId=blob.id)
    }}
    suspend fun retry(session:FinanceSession,ticket:Long,id:String)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);val record=store.record(session.scope.key,id) ?: error("Lampiran tidak ditemukan.");authorized(session,record)
        val pending=store.pendingFor(session.scope.key,id);require(pending.isNotEmpty()) {"Tidak ada pengajuan yang dapat dicoba kembali."}
        if(pending.any {it.envelope.getString("action")=="create"}) reviewed(session,record)
        else {require(session.role==UserRole.MANAGER);require(owner(session,record).serverBody?.optString("record_state")=="active")}
        pending.forEach {store.retryAttachment(session.scope.key,it.id)}
    }}
    suspend fun inspectParent(session:FinanceSession,ticket:Long,record:LocalRecord):JSONObject=withContext(Dispatchers.IO) {
        check(session,ticket);authorized(session,record);val parent=owner(session,record)
        val route=when(parent.kind) {"activity" -> "activities";"harvest" -> "harvests";else -> "expenses"}
        val snapshot=api.request(session.scope.server,session.token,"$route/${parent.id}")
        check(session,ticket);require(snapshot.getString("id")==parent.id);snapshot
    }
    suspend fun reviewParent(session:FinanceSession,ticket:Long,record:LocalRecord,snapshot:JSONObject)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);authorized(session,record);val parent=owner(session,record)
        require(snapshot.getString("id")==parent.id && snapshot.getLong("version")>0 && snapshot.optString("record_state")=="active")
        authorized(session,parent.copy(serverBody=snapshot,serverVersion=snapshot.getLong("version")),true)
        val latest=policy(session,ticket,parent);require(latest.getLong("owner_version")==snapshot.getLong("version")) {"Catatan induk berubah lagi. Muat dan tinjau versi terbaru."}
        AttachmentContract.validatePolicy(latest,AttachmentBytes.parse(record.body))
        val blob=store.blob(session.scope.key,record.body.getString("_local_blob_id")) ?: error("Salinan belum ditemukan.")
        files.verify(session.scope.key,blob.id,AttachmentBytes.parse(record.body));check(session,ticket)
        store.reviewAttachmentParent(session.scope.key,record.id,snapshot.getLong("version"),record.revision,blob.id)
    }}
    suspend fun cancel(session:FinanceSession,ticket:Long,record:LocalRecord,reason:String)=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);authorized(session,record);require(session.role==UserRole.MANAGER)
        require(reason.isNotBlank() && reason.length<=1000) {"Alasan wajib, maksimal1.000 karakter."}
        require(record.syncState==SyncState.ACCEPTED && record.body.optString("record_state")=="active")
        store.queueAction(session.scope.key,record.id,record.greenhouseId,record.kind,record.body,JSONObject().put("reason",reason),"cancel",record.serverVersion!!,record.revision)
    }}
    suspend fun localCopy(session:FinanceSession,record:LocalRecord):LocalBlob?=withContext(Dispatchers.IO) {
        authorized(session,record)
        store.blobsFor(session.scope.key,record.id).firstOrNull {it.state=="READY"}?.also {files.verify(session.scope.key,it.id,AttachmentBytes.parse(it.body))}
    }
    suspend fun download(session:FinanceSession,ticket:Long,record:LocalRecord):LocalBlob=withContext(Dispatchers.IO) {writes.withLock {
        check(session,ticket);authorized(session,record);val expected=AttachmentBytes.parse(record.serverBody ?: error("Metadata belum diterima server."))
        val blob=store.beginAttachmentDownload(session.scope.key,record.id,UUID.randomUUID().toString())
        try {
            api.download(session.scope.server,session.token,record.id,expected.bytes) {input ->
                val actual=files.copy(session.scope.key,blob.id,expected.name,input)
                require(actual==expected) {"Checksum unduhan tidak cocok dengan metadata diterima."}
            }
            files.verify(session.scope.key,blob.id,expected);store.finishAttachmentCopy(session.scope.key,blob.id,expected)
            check(session,ticket);store.blob(session.scope.key,blob.id)!!
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(error:Exception) {if(store.blob(session.scope.key,blob.id)?.state!="READY") store.failAttachmentCopy(session.scope.key,blob.id,error.message ?: "Unduhan belum lengkap.");throw error}
    }}
}
