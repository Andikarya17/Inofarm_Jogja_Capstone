package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.sync.OperationRoute
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AttachmentContractTest {
    private val png=byteArrayOf(137.toByte(),80,78,71,13,10,26,10)
    @Test fun fileFormatAndNameUseActualPrefixAndCompatibilityCeiling() {
        val good=AttachmentContract.inspect("Bukti — 猫.PNG",png,68,"a".repeat(64))
        assertEquals("image/png",good.mime);assertEquals(68L,good.bytes)
        for(name in listOf("bad.pdf","../b.png","x\n.png","no-extension","x".repeat(201)+".png")) assertThrows(IllegalArgumentException::class.java) {AttachmentContract.inspect(name,png,68,"a".repeat(64))}
        assertThrows(IllegalArgumentException::class.java) {AttachmentContract.inspect("x.png",png,33554433,"a".repeat(64))}
        assertThrows(IllegalStateException::class.java) {AttachmentContract.inspect("x.png","arbitrary".toByteArray(),9,"a".repeat(64))}
    }
    @Test fun unsetOrPartialPolicyNeverBecomesAProductionDefault() {
        val file=AttachmentContract.inspect("b.png",png,68,"a".repeat(64))
        assertThrows(IllegalArgumentException::class.java) {AttachmentContract.validatePolicy(null,file)}
        val policy=JSONObject().put("configured",false).put("upload_allowed",false).put("max_bytes",JSONObject.NULL).put("max_files",JSONObject.NULL)
        assertThrows(IllegalArgumentException::class.java) {AttachmentContract.validatePolicy(policy,file)}
        policy.put("configured",true).put("upload_allowed",true).put("max_bytes",68).put("max_files",1).put("allowed_mime_types",JSONArray().put("image/png"))
        AttachmentContract.validatePolicy(policy,file)
        policy.put("max_bytes",67);assertThrows(IllegalArgumentException::class.java) {AttachmentContract.validatePolicy(policy,file)}
    }
    @Test fun workerAttachmentRoutesNeverAuthorizeFinanceOrCancellationOrUpdate() {
        val id=UUID.randomUUID().toString()
        assertEquals("attachments",OperationRoute.forOperation("activity_attachment","create",id,UserRole.WORKER).path)
        for((kind,action) in listOf("expense_attachment" to "create","harvest_attachment" to "cancel","activity_attachment" to "update")) assertThrows(IllegalArgumentException::class.java) {OperationRoute.forOperation(kind,action,id,UserRole.WORKER)}
        assertEquals("attachments/$id/cancel",OperationRoute.forOperation("expense_attachment","cancel",id,UserRole.MANAGER).path)
    }
}
