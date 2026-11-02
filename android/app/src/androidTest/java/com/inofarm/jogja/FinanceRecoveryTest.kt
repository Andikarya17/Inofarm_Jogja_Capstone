package com.inofarm.jogja

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.network.FinanceHttpApi
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.feature.recovery.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class FinanceRecoveryTest {
    @get:Rule val rule=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-test-recovery-${UUID.randomUUID()}.db";private val prefs="finance-test-recovery-${UUID.randomUUID()}"
    private val old=UUID.randomUUID().toString();private val epoch=UUID.randomUUID().toString();private val gh=UUID.randomUUID().toString()
    private lateinit var helper:FinanceDatabase;private lateinit var store:LocalFinanceStore;private lateinit var vault:FinanceSessionVault;private lateinit var session:FinanceSession;private lateinit var repository:RecoveryRepository
    private val server=MockWebServer();private val wrongHash=AtomicBoolean(false);private val revoke=AtomicBoolean(false);private val available=AtomicBoolean(false);private var created:JSONObject?=null
    private fun body(note:String="Original")=JSONObject().put("planting_group_id",UUID.randomUUID().toString()).put("activity_type","general").put("occurred_at","2026-11-02T08:00:00+07:00").put("work_state","completed").put("materials",JSONArray()).put("notes",note)
    private fun page(epoch:String,rows:JSONArray=JSONArray())=JSONObject().put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh))).put("data",rows).put("has_more",false).put("next_cursor","recovery-fixture")
    private fun receipt(op:QueuedOperation)=JSONObject().put("operation_id",op.id).put("entity_id",op.entityId).put("version",1).put("data",JSONObject(op.envelope.getJSONObject("payload").toString()).put("id",op.entityId).put("version",1))
    @Before fun setup() {
        assertTrue(context.packageName.endsWith(".finance"));helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher())
        server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse {
            if(request.path!!.endsWith("recovery/policy"))return MockResponse().setBody(JSONObject().put("data",JSONObject().put("available",available.get())).toString())
            if(request.path!!.endsWith("recovery/restores"))return MockResponse().setBody("{\"data\":[],\"next_cursor\":null}")
            if(request.path!!.endsWith("recovery/backups")) {
                if(request.method=="POST") {val input=JSONObject(request.body.readUtf8());created=JSONObject().put("id",input.getString("request_id")).put("reason",input.getString("reason")).put("state","completed").put("manifest_hash","a".repeat(64));return MockResponse().setBody(JSONObject().put("data",created).toString())}
                return MockResponse().setBody(JSONObject().put("data",JSONArray().also {created?.let(it::put)}).put("next_cursor",JSONObject.NULL).toString())
            }
            val input=JSONObject(request.body.readUtf8());val op=input.getJSONObject("operation");val queued=store.outbox(session.scope.key).single {it.id==op.getString("operation_id")}
            if(revoke.get())vault.logout()
            return MockResponse().setBody(JSONObject().put("sync_epoch",input.getString("sync_epoch")).put("operation_id",queued.id).put("entity_id",queued.entityId).put("request_hash",if(wrongHash.get()) "0".repeat(64) else queued.hash).put("state","receipt_found").put("receipt",receipt(queued)).put("message","Tanda terima cocok").toString())
        }};server.start();session=FinanceSession(AccountScope(server.url("/api/v2/").toString(),UUID.randomUUID().toString()),"synthetic-token",Instant.now().plusSeconds(3600).toString(),"Manager fixture",UserRole.MANAGER)
        vault=FinanceSessionVault(context,FixtureCipher(),prefs);assertTrue(vault.save(vault.generation,session));store.initialize(session.scope.key,old,session.role);store.applyPull(session.scope.key,page(old));repository=RecoveryRepository(vault,store,FinanceHttpApi(true))
    }
    @After fun cleanup(){server.shutdown();helper.close();assertTrue(name.startsWith("finance-test-"));context.deleteDatabase(name);context.deleteSharedPreferences(prefs)}
    private fun queued():QueuedOperation {val id=UUID.randomUUID().toString();store.saveAndQueue(session.scope.key,id,gh,body());return store.outbox(session.scope.key).single {it.entityId==id}}
    private fun restored(){store.initialize(session.scope.key,epoch,session.role);store.applyPull(session.scope.key,page(epoch))}
    @Test fun historicalReceiptRequiresExplicitReviewAndKeepsNewerRawDraftAcrossReopen():Unit=runBlocking {
        val op=queued();val row=store.record(session.scope.key,op.entityId)!!;store.saveDraft(session.scope.key,row.id,gh,JSONObject(row.body.toString()).put("notes","New raw edit"),row.revision)
        restored();helper.close();helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher());repository=RecoveryRepository(vault,store,FinanceHttpApi(true))
        assertNull(store.next(session.scope.key));assertEquals(op.hash,store.outbox(session.scope.key).single().hash)
        val review=repository.inspect(store.outbox(session.scope.key).single());assertEquals("EPOCH_REVIEW",store.outbox(session.scope.key).single().state)
        repository.acknowledge(store.outbox(session.scope.key).single(),review)
        assertEquals("ACCEPTED",store.outbox(session.scope.key).single().state);val after=store.record(session.scope.key,op.entityId)!!;assertEquals("New raw edit",after.body.getString("notes"));assertEquals(SyncState.LOCAL,after.syncState);assertEquals(1L,after.serverVersion);assertNull(store.next(session.scope.key))
    }
    @Test fun wrongReceiptHashCannotChangeLocalAcceptance():Unit=runBlocking {
        queued();restored();wrongHash.set(true)
        try{repository.inspect(store.outbox(session.scope.key).single());fail("Wrong hash accepted")}catch(_:IllegalArgumentException){}
        assertEquals("EPOCH_REVIEW",store.outbox(session.scope.key).single().state)
    }
    @Test fun revokedSessionDuringReceiptRefreshCannotAcknowledge():Unit=runBlocking {
        queued();restored();val op=store.outbox(session.scope.key).single();val review=repository.inspect(op);revoke.set(true)
        try{repository.acknowledge(op,review);fail("Revoked session accepted")}catch(_:IllegalStateException){}
        assertEquals("EPOCH_REVIEW",store.outbox(session.scope.key).single().state)
    }
    @Test fun anotherEpochInvalidatesReviewedReceiptAndOrdinaryOldAckStillRejects():Unit=runBlocking {
        queued();restored();val op=store.outbox(session.scope.key).single();val review=repository.inspect(op)
        try{store.acknowledge(session.scope.key,op.id,receipt(op));fail("Old ACK bypassed review")}catch(_:IllegalArgumentException){}
        store.initialize(session.scope.key,UUID.randomUUID().toString(),session.role)
        try{store.acknowledge(session.scope.key,op.id,receipt(op),review);fail("Stale review applied")}catch(_:IllegalArgumentException){}
        assertEquals("EPOCH_REVIEW",store.outbox(session.scope.key).single().state)
    }
    @Test fun restoredLowerVersionKeepsOldCopyForExplicitReviewWithoutReplaying() {
        val op=queued();store.acknowledge(session.scope.key,op.id,receipt(op));
        val remote=receipt(op).getJSONObject("data").put("version",2).put("notes","Before restore v2")
        fun row(data:JSONObject)=JSONObject().put("entity_type","activity").put("entity_id",op.entityId).put("greenhouse_id",gh).put("version",data.getInt("version")).put("tombstone",false).put("data",data)
        store.applyPull(session.scope.key,page(old,JSONArray().put(row(remote))));assertEquals(2L,store.record(session.scope.key,op.entityId)!!.serverVersion)
        store.initialize(session.scope.key,epoch,session.role);assertNull(store.record(session.scope.key,op.entityId)!!.serverVersion)
        val restored=JSONObject(remote.toString()).put("version",1).put("notes","Restored v1");store.applyPull(session.scope.key,page(epoch,JSONArray().put(row(restored))))
        val local=store.record(session.scope.key,op.entityId)!!;assertEquals(1L,local.serverVersion);assertEquals("Restored v1",local.serverBody!!.getString("notes"));assertEquals("Before restore v2",local.body.getString("notes"));assertEquals(SyncState.EPOCH_REVIEW,local.syncState);assertTrue(store.archived(session.scope.key,op.entityId).isNotEmpty())
        runBlocking {val saved=store.outbox(session.scope.key).single();val review=repository.inspect(saved);repository.acknowledge(saved,review)}
        assertEquals("Before restore v2",store.record(session.scope.key,op.entityId)!!.body.getString("notes"));assertEquals(SyncState.LOCAL,store.record(session.scope.key,op.entityId)!!.syncState);assertNull(store.next(session.scope.key))
    }
    @Test fun unconfiguredOperatorUiAtNarrowWidthAndLargeFontDoesNotOfferBackup() {
        rule.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){RecoveryHost(session,repository,{}){androidx.compose.foundation.layout.Column(Modifier.width(320.dp)){RecoveryBackupButton()}}}}
        rule.onNodeWithTag("recovery-open").performClick();rule.waitUntil(10000){rule.onAllNodesWithText("Kewenangan operator pencadangan belum ditetapkan untuk akun ini.").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("recovery-create").assertDoesNotExist();rule.onNodeWithText("Tutup").performClick();rule.onNodeWithTag("recovery-open").assertExists()
    }
    @Test fun operatorBackupRequiresReasonAndConfirmationAndShowsBackupNotRestoreSuccess() {
        available.set(true)
        rule.setContent {RecoveryHost(session,repository,{}){RecoveryBackupButton()}}
        rule.onNodeWithTag("recovery-open").performClick();rule.waitUntil(10000){rule.onAllNodesWithTag("recovery-reason").fetchSemanticsNodes().singleOrNull()?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)==false}
        rule.onNodeWithTag("recovery-create").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("recovery-reason").performScrollTo().performTextInput("Latihan cadangan UI")
        rule.onNodeWithTag("recovery-create").performScrollTo().performClick();assertNull(created)
        rule.onNodeWithText("Cadangkan sekarang").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithText("Cadangan dibuat. Keberhasilan restore belum dibuktikan.").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("Latihan cadangan UI",created!!.getString("reason"));WireContract.uuid(created!!.getString("id"))
    }

}
