package com.inofarm.jogja

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.files.EncryptedAttachmentStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.core.sync.*
import com.inofarm.jogja.feature.attachments.AttachmentRepository
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinanceAttachmentTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val nonce=UUID.randomUUID().toString();private val name="finance-test-files-$nonce.db";private val pref="finance-test-files-$nonce"
    private val scope=AccountScope("http://fixture.invalid/api/v2/",UUID.randomUUID().toString())
    private val gh=UUID.randomUUID().toString();private val epoch=UUID.randomUUID().toString();private val parentId=UUID.randomUUID().toString()
    private val root by lazy {File(context.cacheDir,"finance-test-blobs-$nonce")}
    private val source by lazy {File(context.cacheDir,"finance-test-source-$nonce.png")}
    private val png=android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j0ioAAAAASUVORK5CYII=",android.util.Base64.DEFAULT)
    private lateinit var helper:FinanceDatabase;private lateinit var store:LocalFinanceStore;private lateinit var files:EncryptedAttachmentStore;private lateinit var vault:FinanceSessionVault
    private val session get()=vault.current!!
    private val api=object:FinanceApi {
        override fun validateBase(base:String)=base
        override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject=when(path) {
            "sync/status" -> JSONObject().put("sync_epoch",epoch)
            "sync/changes" -> page()
            else -> JSONObject().put("data",profile())
        }
    }
    private fun repo()=AttachmentRepository(context,vault,store,api,files)
    private fun profile(configured:Boolean=true)=JSONObject().put("configured",configured).put("upload_allowed",configured).put("owner_version",1).put("max_bytes",if(configured) 32768 else JSONObject.NULL).put("max_files",if(configured) 2 else JSONObject.NULL).put("allowed_mime_types",JSONArray().put("image/png")).put("reasons",JSONArray())
    private fun page(rows:JSONArray=JSONArray(),visible:Boolean=true)=JSONObject().put("sync_epoch",epoch).put("scope",if(visible) JSONArray().put(JSONObject().put("id",gh)) else JSONArray()).put("data",rows).put("next_cursor","fixture").put("has_more",false)
    @Before fun prepare() {
        assertTrue(context.packageName.endsWith(".finance"));helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,AndroidPayloadCipher());files=EncryptedAttachmentStore(context,root);vault=FinanceSessionVault(context,FixtureCipher(),pref)
        val ticket=vault.beginLogin();vault.save(ticket,FinanceSession(scope,"synthetic-token",Instant.now().plusSeconds(3600).toString(),"Sintetis",UserRole.WORKER))
        store.initialize(scope.key,epoch,UserRole.WORKER)
        val parent=JSONObject().put("id",parentId).put("version",1).put("recorded_by",scope.accountId).put("record_state","active").put("planting_group_id",UUID.randomUUID().toString()).put("notes","Induk sintetis")
        store.applyPull(scope.key,page(JSONArray().put(JSONObject().put("entity_type","activity").put("entity_id",parentId).put("greenhouse_id",gh).put("version",1).put("tombstone",false).put("data",parent))))
        source.writeBytes(png)
    }
    @After fun cleanup() {
        helper.close();context.deleteDatabase(name);context.getSharedPreferences(pref,0).edit().clear().commit();source.delete()
        require(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator) && root.name=="finance-test-blobs-$nonce")
        if(root.exists()) root.walkBottomUp().forEach {check(it.delete())}
    }
    private suspend fun draft():LocalRecord=repo().importUri(session,vault.generation,store.record(scope.key,parentId)!!,Uri.fromFile(source),"Bukti — sintetis.png")
    private fun payload(record:LocalRecord)=AttachmentBytes.parse(record.body).json().put("owner_type","activity").put("owner_id",parentId).put("owner_version",record.body.getLong("owner_version"))
    @Test fun encryptedCopyAndImmutableBindingSurviveReopenAndSourceDisappearing():Unit=runBlocking {
        val draft=draft();val blob=store.blobsFor(scope.key,draft.id).single();assertEquals("READY",blob.state)
        val raw=File(File(root,scope.key),"${blob.id}.blob").readBytes();assertFalse(raw.toList().windowed(8).any {it.toByteArray().contentEquals(png.copyOfRange(0,8))})
        assertTrue(source.delete());repo().policy(session,vault.generation,store.record(scope.key,parentId)!!);repo().queue(session,vault.generation,draft.id)
        val original=store.next(scope.key)!!;assertEquals(blob.id,original.blobId);assertFalse(original.envelope.toString().contains("source_uri"));assertFalse(original.envelope.toString().contains("_local_blob"))
        helper.close();helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,AndroidPayloadCipher());files=EncryptedAttachmentStore(context,root)
        val reopened=store.next(scope.key)!!;assertEquals(original.id,reopened.id);assertEquals(original.hash,reopened.hash);assertEquals(original.blobId,reopened.blobId)
        files.verify(scope.key,blob.id,AttachmentBytes.parse(blob.body));assertArrayEquals(png,files.open(scope.key,blob.id).use {it.readBytes()})
        val other=AccountScope(scope.server,UUID.randomUUID().toString());store.initialize(other.key,epoch,UserRole.WORKER);assertNull(store.blob(other.key,blob.id));assertNull(store.next(other.key))
        val swapped=File(File(root,other.key),"${blob.id}.blob");swapped.parentFile!!.mkdirs();swapped.writeBytes(raw)
        assertThrows(LocalAttachmentFailure::class.java) {files.verify(other.key,blob.id,AttachmentBytes.parse(blob.body))}
    }
    @Test fun crashBetweenEncryptedFileAndMetadataCommitCanResumeWithoutDuplicateRecord():Unit=runBlocking {
        val id=UUID.randomUUID().toString();val blobId=UUID.randomUUID().toString()
        store.beginAttachmentCopy(scope.key,id,gh,"activity_attachment",JSONObject().put("owner_type","activity").put("owner_id",parentId).put("owner_version",1),blobId,JSONObject().put("source_uri",Uri.fromFile(source).toString()).put("original_name","source.png").put("copy_mode","upload"))
        files.copy(scope.key,blobId,"source.png",ByteArrayInputStream(png));assertEquals("COPYING",store.blob(scope.key,blobId)!!.state);assertTrue(source.delete())
        helper.close();helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,AndroidPayloadCipher());files=EncryptedAttachmentStore(context,root)
        val resumed=repo().resumeCopy(session,vault.generation,id);assertEquals(id,resumed.id);assertEquals("READY",store.blob(scope.key,blobId)!!.state);assertEquals(1,store.records(scope.key,kind="activity_attachment").size);assertTrue(store.outbox(scope.key).isEmpty())
    }
    @Test fun incompleteCopyAndMissingPolicyStayLocalAndAtomicQueueFailureKeepsReadyCopy():Unit=runBlocking {
        val draft=draft();assertThrows(IllegalArgumentException::class.java) {runBlocking {repo().queue(session,vault.generation,draft.id)}}
        assertTrue(store.outbox(scope.key).isEmpty());store.cacheAttachmentPolicy(scope.key,parentId,"activity_attachment",gh,profile(false))
        assertThrows(IllegalArgumentException::class.java) {runBlocking {repo().queue(session,vault.generation,draft.id)}}
        val blob=store.blobsFor(scope.key,draft.id).single();val failing=LocalFinanceStore(helper,AndroidPayloadCipher()) {error("Synthetic atomic interruption")}
        assertThrows(IllegalStateException::class.java) {failing.saveAndQueue(scope.key,draft.id,gh,draft.body,draft.revision,kind=draft.kind,queuePayload=payload(draft),blobId=blob.id)}
        assertEquals(draft.revision,store.record(scope.key,draft.id)!!.revision);assertTrue(store.outbox(scope.key).isEmpty());assertEquals("READY",store.blob(scope.key,blob.id)!!.state)
        val interrupted=UUID.randomUUID().toString();val bad=object:InputStream() {var n=0;override fun read():Int {if(n++>4) throw IOException("Synthetic source failure");return png[0].toInt() and 255}}
        assertThrows(IOException::class.java) {files.copy(scope.key,interrupted,"bad.png",bad)};assertFalse(files.exists(scope.key,interrupted))
    }
    @Test fun responseLossRetainsExactBytesAndOperationThenCorruptLocalFileHoldsBeforeNetwork():Unit=runBlocking {
        val draft=draft();repo().policy(session,vault.generation,store.record(scope.key,parentId)!!);repo().queue(session,vault.generation,draft.id)
        val original=store.next(scope.key)!!;var commits=0;var attempts=0;var accepted:JSONObject?=null
        val transport=object:FinanceApi {
            override fun validateBase(base:String)=base
            override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>)=api.request(base,token,path,method,body,query)
            override suspend fun upload(base:String,token:String,envelope:JSONObject,source:()->InputStream):JSONObject {
                attempts++;assertEquals(original.hash,WireContract.hash(envelope));assertArrayEquals(png,source().use {it.readBytes()})
                if(accepted==null) {commits++;accepted=JSONObject().put("operation_id",original.id).put("entity_id",draft.id).put("version",1).put("data",payload(draft).put("id",draft.id).put("version",1).put("record_state","active"))}
                if(attempts==1) throw IOException("Synthetic response loss after commit")
                return accepted!!
            }
        }
        FinanceSyncCoordinator(transport,vault,store,AttachmentTransfer(store,files)).run();assertEquals(1,commits)
        val prior=session;vault.logout();assertNotNull(store.blob(scope.key,original.blobId!!));val ticket=vault.beginLogin();vault.save(ticket,prior)
        delay(1100);FinanceSyncCoordinator(transport,vault,store,AttachmentTransfer(store,files)).run();assertEquals(1,commits);assertEquals(2,attempts);assertEquals(SyncState.ACCEPTED,store.record(scope.key,draft.id)!!.syncState)
        val next=draft();repo().queue(session,vault.generation,next.id);val nextOp=store.next(scope.key)!!
        val raw=File(File(root,scope.key),"${nextOp.blobId}.blob");val bytes=raw.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();raw.writeBytes(bytes)
        FinanceSyncCoordinator(transport,vault,store,AttachmentTransfer(store,files)).run();assertEquals(2,attempts);assertEquals("local_attachment_unavailable",store.outbox(scope.key).single {it.id==nextOp.id}.error!!.getString("code"));assertEquals(nextOp.hash,store.outbox(scope.key).single {it.id==nextOp.id}.hash)
    }
    @Test fun parentConflictNeedsNewReviewedOperationButPreservesOldBindingAndEpochHold():Unit=runBlocking {
        val draft=draft();repo().policy(session,vault.generation,store.record(scope.key,parentId)!!);repo().queue(session,vault.generation,draft.id)
        val original=store.next(scope.key)!!;store.mark(scope.key,original,SyncState.REJECTED,JSONObject().put("code","parent_version_conflict").put("current_version",2))
        val row=store.record(scope.key,draft.id)!!;store.reviewAttachmentParent(scope.key,row.id,2,row.revision,original.blobId!!)
        val replacement=store.next(scope.key)!!;assertNotEquals(original.id,replacement.id);assertEquals(original.entityId,replacement.entityId);assertEquals(original.blobId,replacement.blobId);assertEquals(2,replacement.envelope.getJSONObject("payload").getInt("owner_version"));assertEquals("SUPERSEDED",store.outbox(scope.key).single {it.id==original.id}.state)
        assertEquals(original.hash,store.outbox(scope.key).single {it.id==original.id}.hash);assertEquals(1,store.archived(scope.key,row.id).size)
        store.initialize(scope.key,UUID.randomUUID().toString(),UserRole.WORKER);assertNull(store.next(scope.key));assertEquals("EPOCH_REVIEW",store.outbox(scope.key).single {it.id==replacement.id}.state);files.verify(scope.key,original.blobId!!,AttachmentBytes.parse(store.blob(scope.key,original.blobId!!)!!.body))
    }
}
