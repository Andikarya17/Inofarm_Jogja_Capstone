package com.inofarm.jogja.core.sync

import com.inofarm.jogja.core.contracts.AttachmentBytes
import com.inofarm.jogja.core.contracts.LocalAttachmentFailure
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.files.EncryptedAttachmentStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.FinanceSession
import org.json.JSONObject

class AttachmentTransfer(private val store:LocalFinanceStore,private val files:EncryptedAttachmentStore) {
    suspend fun upload(api:FinanceApi,session:FinanceSession,operation:QueuedOperation):JSONObject {
        val id=operation.blobId ?: throw LocalAttachmentFailure("Pengajuan belum memiliki ikatan salinan berkas. Isi dan ID tetap disimpan.")
        try {
            val blob=store.blob(session.scope.key,id) ?: throw LocalAttachmentFailure("Salinan berkas belum ditemukan.")
            val expected=AttachmentBytes.parse(operation.envelope.getJSONObject("payload"))
            if(blob.entityId!=operation.entityId || blob.state!="READY" || AttachmentBytes.parse(blob.body)!=expected) throw LocalAttachmentFailure("Salinan berkas tidak cocok dengan pengajuan yang ditinjau.")
            files.verify(session.scope.key,id,expected)
        } catch(error:LocalAttachmentFailure) {throw error}
        catch(error:Exception) {throw LocalAttachmentFailure("Metadata atau salinan berkas belum dapat diverifikasi. Operasi tetap disimpan untuk ditinjau.")}
        return api.upload(session.scope.server,session.token,operation.envelope) {files.open(session.scope.key,id)}
    }
}
