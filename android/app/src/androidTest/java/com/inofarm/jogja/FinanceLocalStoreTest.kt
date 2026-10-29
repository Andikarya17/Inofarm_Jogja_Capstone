package com.inofarm.jogja

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.FinanceDatabase
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.feature.harvest.domain.HarvestDraft
import com.inofarm.jogja.feature.harvest.domain.HarvestLine
import com.inofarm.jogja.core.session.AndroidPayloadCipher
import com.inofarm.jogja.core.session.PayloadCipher
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

internal class FixtureCipher : PayloadCipher {
    override fun encode(scope: String,value: String) = value
    override fun decode(scope: String,value: String) = value
}

@RunWith(AndroidJUnit4::class)
class FinanceLocalStoreTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-test-${UUID.randomUUID()}.db"
    private val epoch=UUID.randomUUID().toString()
    private val gh=UUID.randomUUID().toString()
    private lateinit var helper: FinanceDatabase
    private lateinit var store: LocalFinanceStore
    @Before fun setUp() {
        assertTrue("Run with -PfinanceApp; no legacy APK data",context.packageName.endsWith(".finance"))
        helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher())
        store.initialize("account-a",epoch,UserRole.WORKER);store.applyPull("account-a",page())
    }
    @After fun tearDown() { helper.close();assertTrue(name.startsWith("finance-test-"));context.deleteDatabase(name) }
    private fun body(note: String="Fixture")=JSONObject().put("planting_group_id",UUID.randomUUID().toString()).put("activity_type","general")
        .put("occurred_at","2026-10-29T08:00:00+07:00").put("work_state","completed").put("materials",JSONArray()).put("notes",note)
    private fun page(rows: JSONArray=JSONArray(),scope: JSONArray=JSONArray().put(JSONObject().put("id",gh)))=JSONObject()
        .put("sync_epoch",epoch).put("scope",scope).put("data",rows).put("next_cursor","fixture-cursor").put("has_more",false)
    private fun receipt(id: String,op: String,note: String="Accepted",version: Int=1)=JSONObject().put("operation_id",op).put("entity_id",id).put("version",version)
        .put("data",body(note).put("id",id).put("version",version))

    @Test fun durableOutboxSurvivesCloseReopenAndDoesNotCrossAccountOrServer() {
        val id=UUID.randomUUID().toString()
        store.saveAndQueue("account-a",id,gh,body())
        val op=store.next("account-a")!!
        helper.close();helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher())
        assertEquals(op.id,store.next("account-a")!!.id)
        assertEquals(op.hash,store.next("account-a")!!.hash)
        store.initialize("account-b",epoch,UserRole.WORKER)
        assertNull(store.record("account-b",id));assertNull(store.next("account-b"));assertTrue(store.records("account-b").isEmpty())
        store.initialize("same-account-other-server",epoch,UserRole.WORKER)
        assertNull(store.next("same-account-other-server"))
        assertEquals(1,store.outbox("account-a").size)
    }
    @Test fun faultBetweenRecordAndOperationRollsBackBothRows() {
        val failing=LocalFinanceStore(helper,FixtureCipher()) { error("Synthetic crash boundary") }
        val id=UUID.randomUUID().toString()
        assertThrows(IllegalStateException::class.java) { failing.saveAndQueue("account-a",id,gh,body()) }
        assertNull(store.record("account-a",id));assertTrue(store.outbox("account-a").isEmpty())
    }
    @Test fun lateAckCannotEraseNewDraftAndDuplicateAckCannotDowngradeServerVersion() {
        val id=UUID.randomUUID().toString()
        val queued=store.saveAndQueue("account-a",id,gh,body("Awal"))
        val op=store.next("account-a")!!
        store.saveDraft("account-a",id,gh,body("Edit baru saat antre"),queued.revision)
        store.acknowledge("account-a",op.id,receipt(id,op.id))
        val current=store.record("account-a",id)!!
        assertEquals("Edit baru saat antre",current.body.getString("notes"));assertEquals(SyncState.LOCAL,current.syncState)
        assertEquals(1L,current.serverVersion);assertEquals("ACCEPTED",store.outbox("account-a").single().state)
        assertEquals(1L,current.baseVersion)
        val remote=JSONObject().put("entity_type","activity").put("entity_id",id).put("greenhouse_id",gh).put("version",3).put("tombstone",false).put("data",body("Server v3").put("id",id).put("version",3))
        store.applyPull("account-a",page(JSONArray().put(remote)))
        store.acknowledge("account-a",op.id,receipt(id,op.id))
        assertEquals(3L,store.record("account-a",id)!!.serverVersion)
        assertEquals("Server v3",store.record("account-a",id)!!.serverBody!!.getString("notes"))
        assertEquals("Edit baru saat antre",store.record("account-a",id)!!.body.getString("notes"))
    }
    @Test fun revokedScopeAndRestoredEpochHoldQueueAndKeepPayloadIdentity() {
        val id=UUID.randomUUID().toString();store.saveAndQueue("account-a",id,gh,body())
        val original=store.next("account-a")!!
        store.applyPull("account-a",page(scope=JSONArray()))
        assertNull(store.next("account-a"));assertEquals("SCOPE_REVOKED",store.outbox("account-a").single().state)
        assertTrue(store.records("account-a").isEmpty());assertNotNull(store.record("account-a",id))
        store.initialize("account-a",UUID.randomUUID().toString(),UserRole.WORKER)
        val held=store.outbox("account-a").single()
        assertEquals("EPOCH_REVIEW",held.state);assertEquals(original.hash,held.hash);assertEquals(original.id,held.id)
        assertThrows(IllegalArgumentException::class.java) { store.acknowledge("account-a",original.id,receipt(id,original.id)) }
    }
    @Test fun encryptionIsBoundToAccountScopeAndWorkerCannotReadManagerCache() {
        val cipher=AndroidPayloadCipher();val encrypted=cipher.encode("account-a","Private fixture")
        assertFalse(encrypted.contains("Private fixture"));assertEquals("Private fixture",cipher.decode("account-a",encrypted))
        assertThrows(Exception::class.java) { cipher.decode("account-b",encrypted) }
        assertThrows(IllegalArgumentException::class.java) { store.cached("account-a","expense_categories") }
    }
    @Test fun remotePullBeforeFirstDraftSaveDoesNotRebaseAnEditSilently() {
        val id=UUID.randomUUID().toString()
        fun row(version:Int)=JSONObject().put("entity_type","activity").put("entity_id",id).put("greenhouse_id",gh).put("version",version).put("tombstone",false).put("data",body("Server $version").put("id",id).put("version",version))
        store.applyPull("account-a",page(JSONArray().put(row(1))))
        val opened=store.record("account-a",id)!!
        store.applyPull("account-a",page(JSONArray().put(row(2))))
        store.saveDraft("account-a",id,gh,body("Edit dari versi1"),opened.revision,editingBaseVersion=1)
        assertEquals(1L,store.record("account-a",id)!!.baseVersion)
        assertEquals(2L,store.record("account-a",id)!!.serverVersion)
    }
    @Test fun migrationOneToCurrentKeepsDraftAndOutbox() {
        val migrationName="finance-test-migration-${UUID.randomUUID()}.db"
        val id=UUID.randomUUID().toString();val operationId=UUID.randomUUID().toString()
        context.openOrCreateDatabase(migrationName,Context.MODE_PRIVATE,null).use { old ->
            FinanceDatabase.createVersionOne(old);old.version=1
            old.execSQL("INSERT INTO scope_metadata(scope,epoch,role,visible_greenhouses) VALUES(?,?,?,?)",arrayOf("migrate",epoch,"worker",JSONArray().put(gh).toString()))
            old.execSQL("INSERT INTO local_records(scope,entity_id,kind,greenhouse_id,revision,body,sync_state,updated_at) VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("migrate",id,"activity",gh,1,body().toString(),"QUEUED",1))
            old.execSQL("INSERT INTO outbox(scope,operation_id,entity_id,local_revision,envelope,canonical_hash,state,created_at) VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("migrate",operationId,id,1,"{}","fixture","QUEUED",1))
        }
        val upgraded=FinanceDatabase(context,migrationName)
        try {
            val migrated=LocalFinanceStore(upgraded,FixtureCipher())
            assertNotNull(migrated.record("migrate",id));assertEquals(operationId,migrated.outbox("migrate").single().id)
            assertNull(migrated.pullCursor("migrate"));assertEquals(FinanceDatabase.VERSION,upgraded.readableDatabase.version)
        } finally { upgraded.close();context.deleteDatabase(migrationName) }
    }
    private fun remote(id:String,version:Int,note:String)=JSONObject().put("entity_type","activity").put("entity_id",id).put("greenhouse_id",gh).put("version",version)
        .put("tombstone",false).put("data",body(note).put("id",id).put("version",version))
    private fun conflict(id:String):com.inofarm.jogja.core.database.QueuedOperation {
        store.applyPull("account-a",page(JSONArray().put(remote(id,1,"Awal"))))
        val old=store.record("account-a",id)!!
        store.saveAndQueue("account-a",id,gh,body("Lokal"),old.revision,1)
        val op=store.next("account-a")!!
        store.mark("account-a",op,SyncState.CONFLICT,JSONObject().put("code","version_conflict").put("current_version",2).put("current_data",body("Server dua").put("id",id).put("version",2)))
        return op
    }
    @Test fun conflictUseServerRequiresReviewedVersionAndArchivesLocalBody() {
        val id=UUID.randomUUID().toString();val op=conflict(id);val review=store.record("account-a",id)!!
        assertThrows(IllegalArgumentException::class.java) {store.resolveConflict("account-a",id,1,review.revision,null)}
        assertEquals("CONFLICT",store.outbox("account-a").single().state)
        store.resolveConflict("account-a",id,2,review.revision,null)
        assertNull(store.next("account-a"));assertEquals("SUPERSEDED",store.outbox("account-a").single().state)
        assertEquals("Server dua",store.record("account-a",id)!!.body.getString("notes"))
        assertEquals("Lokal",store.archived("account-a",id).single().getString("notes"))
        store.mark("account-a",op,SyncState.CONFLICT,JSONObject().put("message","Delayed failure"))
        assertEquals(SyncState.ACCEPTED,store.record("account-a",id)!!.syncState)
    }
    @Test fun keepLocalMakesNewOperationAndStaleConflictCannotDowngradeServer() {
        val id=UUID.randomUUID().toString();val original=conflict(id)
        store.applyPull("account-a",page(JSONArray().put(remote(id,3,"Server tiga"))))
        store.mark("account-a",original,SyncState.CONFLICT,JSONObject().put("current_version",2).put("current_data",body("Server dua").put("id",id).put("version",2)))
        val reviewed=store.record("account-a",id)!!
        assertEquals(3L,reviewed.serverVersion)
        assertThrows(IllegalArgumentException::class.java) {store.resolveConflict("account-a",id,2,reviewed.revision,body("Lokal"))}
        store.resolveConflict("account-a",id,3,reviewed.revision,body("Lokal diperiksa").put("reason","Koreksi sintetis").put("reference_policy","preserve"))
        val next=store.next("account-a")!!
        assertNotEquals(original.id,next.id);assertEquals(3L,next.envelope.getLong("base_version"));assertEquals("update",next.envelope.getString("action"))
        assertEquals(original.hash,store.outbox("account-a").single {it.id==original.id}.hash)
        assertEquals("Lokal",store.archived("account-a",id).single().getString("notes"))
    }
    @Test fun editingDraftAfterComparisonRequiresAnotherReview() {
        val id=UUID.randomUUID().toString();conflict(id);val review=store.record("account-a",id)!!
        store.saveDraft("account-a",id,gh,body("Edit setelah dibandingkan"),review.revision)
        assertThrows(IllegalArgumentException::class.java) {store.resolveConflict("account-a",id,2,review.revision,null)}
        assertEquals("Edit setelah dibandingkan",store.record("account-a",id)!!.body.getString("notes"))
    }
    @Test fun filteringOccursBeforePaginationWithInclusiveWibDates() {
        val group=UUID.randomUUID().toString();val rows=JSONArray()
        repeat(120) {n -> val id=UUID.randomUUID().toString();val data=body("Baris $n").put("id",id).put("version",1).put("planting_group_id",group)
            .put("activity_type",if(n<105) "nutrition" else "general").put("occurred_at","2026-10-28T17:30:00Z").put("record_state",if(n==0) "cancelled" else "active")
            rows.put(JSONObject().put("entity_type","activity").put("entity_id",id).put("greenhouse_id",gh).put("version",1).put("tombstone",false).put("data",data)) }
        store.applyPull("account-a",page(rows))
        val filter=LocalHistoryFilter(group,"nutrition","2026-10-29","2026-10-29","active",gh,setOf(group))
        val first=store.records("account-a",50,filter=filter);val second=store.records("account-a",50,filter=filter,offset=50);val third=store.records("account-a",50,filter=filter,offset=100)
        assertEquals(50,first.size);assertEquals(50,second.size);assertEquals(4,third.size)
        assertEquals(104,(first+second+third).map {it.id}.toSet().size)
        assertTrue(store.records("account-a",filter=filter.copy(groups=emptySet())).isEmpty())
        assertTrue(store.records("account-a",filter=filter.copy(until="2026-10-28")).isEmpty())
    }
    @Test fun rejectedValidationRequiresExplicitReplacementAndKeepsOriginalEnvelope() {
        val id=UUID.randomUUID().toString();val row=store.saveAndQueue("account-a",id,gh,body("Ditolak"));val original=store.next("account-a")!!
        store.mark("account-a",original,SyncState.REJECTED,JSONObject().put("code","validation_failed"))
        assertThrows(IllegalArgumentException::class.java) {store.saveAndQueue("account-a",id,gh,body("Perbaikan"),row.revision)}
        store.saveAndQueue("account-a",id,gh,body("Perbaikan"),row.revision,replaceValidationRejected=true)
        val next=store.next("account-a")!!
        assertNotEquals(original.id,next.id);assertEquals("create",next.envelope.getString("action"))
        assertEquals(original.hash,store.outbox("account-a").single {it.id==original.id}.hash)
        assertEquals("Ditolak",store.archived("account-a",id).single().getString("notes"))
    }
    @Test fun demotedManagerCannotReadOrSendOldManagerRecords() {
        store.initialize("account-a",epoch,UserRole.MANAGER)
        val id=UUID.randomUUID().toString()
        store.queueAction("account-a",id,gh,"expense_categories",JSONObject().put("id",id).put("version",1).put("name","Kategori privat"),JSONObject().put("name","Perbaikan").put("reason","Uji"),"update",1)
        assertNotNull(store.next("account-a"));store.initialize("account-a",epoch,UserRole.WORKER)
        assertThrows(IllegalArgumentException::class.java) {store.record("account-a",id)}
        assertTrue(store.outbox("account-a").isEmpty());assertNull(store.next("account-a"))
        assertThrows(IllegalArgumentException::class.java) {store.saveDraft("account-a",UUID.randomUUID().toString(),gh,JSONObject(),kind="seasons")}
        store.initialize("account-a",epoch,UserRole.MANAGER);assertEquals(1,store.outbox("account-a").size)
    }
    @Test fun reviewedClosedSeasonRetryKeepsOriginalOperationAndPayload() {
        val id=UUID.randomUUID().toString();store.saveAndQueue("account-a",id,gh,body("Tertahan musim"));val original=store.next("account-a")!!
        store.mark("account-a",original,SyncState.REJECTED,JSONObject().put("code","season_closed"))
        assertNull(store.next("account-a"));store.retryClosedSeason("account-a",original.id)
        val retry=store.next("account-a")!!;assertEquals(original.id,retry.id);assertEquals(original.hash,retry.hash);assertEquals(original.envelope.toString(),retry.envelope.toString())
        store.mark("account-a",retry,SyncState.REJECTED,JSONObject().put("code","operation_conflict"))
        assertThrows(IllegalArgumentException::class.java) {store.retryClosedSeason("account-a",original.id)}
    }
    @Test fun aCrossKindUuidCollisionCannotOverwriteAnotherRecordOrAdvanceCursor() {
        store.initialize("account-a",epoch,UserRole.MANAGER);val id=UUID.randomUUID().toString()
        store.queueAction("account-a",id,gh,"seasons",JSONObject().put("id",id).put("version",1).put("name","Musim asli"),JSONObject().put("reason","Uji"),"close",1)
        val cursor=store.pullCursor("account-a")
        assertThrows(IllegalArgumentException::class.java) {store.applyPull("account-a",page(JSONArray().put(remote(id,2,"Collision"))).put("next_cursor","bad-cursor"))}
        assertEquals("seasons",store.record("account-a",id)!!.kind);assertEquals("Musim asli",store.record("account-a",id)!!.body.getString("name"))
        assertEquals(cursor,store.pullCursor("account-a"));assertNull(store.cachedOne("account-a","activity",id))
    }
    @Test fun harvestRawLocalFormAndValidatedOutboxPayloadAreAtomicAndStaySeparate() {
        val draft=HarvestDraft(greenhouseId=gh,groupId=UUID.randomUUID().toString(),items=listOf(HarvestLine(gradeId=UUID.randomUUID().toString(),fruitCount="10",weightKg="15,25")))
        store.saveAndQueue("account-a",draft.id,gh,draft.localJson(),kind="harvest",queuePayload=draft.payload())
        val op=store.next("account-a")!!
        assertEquals("harvest",op.envelope.getString("entity_type"));assertFalse(op.envelope.getJSONObject("payload").has("_local_form"))
        assertEquals("15.25",op.envelope.getJSONObject("payload").getJSONArray("items").getJSONObject(0).getString("weight_kg"))
        assertEquals("15,25",store.record("account-a",draft.id)!!.body.getJSONObject("_local_form").getJSONArray("items").getJSONObject(0).getString("weight_kg"))
        helper.close();helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher())
        assertEquals(op.hash,store.next("account-a")!!.hash)
        assertTrue(store.records("account-a").isEmpty());assertEquals(draft.id,store.records("account-a",kind="harvest").single().id)
        val data=JSONObject(draft.payload().toString()).put("id",draft.id).put("version",1).put("recorded_by",UUID.randomUUID().toString())
        store.acknowledge("account-a",op.id,JSONObject().put("operation_id",op.id).put("entity_id",draft.id).put("version",1).put("data",data))
        assertEquals(SyncState.ACCEPTED,store.record("account-a",draft.id)!!.syncState)
    }
    @Test fun workerCanReadScopedHarvestBalanceAndAuditCacheWithoutManagerData() {
        val id=UUID.randomUUID().toString()
        val data=JSONObject().put("id",id).put("version",1).put("available_weight_kg","0.000000")
        val row=JSONObject().put("entity_type","harvest_balance").put("entity_id",id).put("greenhouse_id",gh).put("version",1).put("tombstone",false).put("data",data)
        store.applyPull("account-a",page(JSONArray().put(row)))
        assertEquals("0.000000",store.cachedOne("account-a","harvest_balance",id)!!.getString("available_weight_kg"))
        store.cacheAudit("account-a",id,gh,JSONObject().put("data",JSONArray()).put("fetched_at",1),"harvest_audit")
        assertNotNull(store.cachedOne("account-a","harvest_audit",id));assertNull(store.cachedOne("account-a","activity_audit",id))
        assertThrows(IllegalArgumentException::class.java) {store.cacheAudit("account-a",id,gh,JSONObject(),"expense_audit")}
        store.applyPull("account-a",page(scope=JSONArray()))
        assertNull(store.cachedOne("account-a","harvest_balance",id));assertNull(store.cachedOne("account-a","harvest_audit",id))
    }
    @Test fun harvestUsedBalanceRejectionRequiresReviewAndNewOperationWithoutRewritingTheRejectedPayload() {
        val draft=HarvestDraft(greenhouseId=gh,groupId=UUID.randomUUID().toString(),items=listOf(HarvestLine(gradeId=UUID.randomUUID().toString(),fruitCount="10",weightKg="15")))
        val data=draft.payload().put("id",draft.id).put("version",1)
        val row=JSONObject().put("entity_type","harvest").put("entity_id",draft.id).put("greenhouse_id",gh).put("version",1).put("tombstone",false).put("data",data)
        store.applyPull("account-a",page(JSONArray().put(row)))
        val old=store.record("account-a",draft.id)!!
        store.saveAndQueue("account-a",draft.id,gh,draft.copy(baseVersion=1,reason="Koreksi sintetis").payload(),old.revision,1,kind="harvest")
        val rejected=store.next("account-a")!!
        store.mark("account-a",rejected,SyncState.REJECTED,JSONObject().put("code","harvest_in_use"))
        val review=store.record("account-a",draft.id)!!
        assertThrows(IllegalArgumentException::class.java) {store.saveAndQueue("account-a",draft.id,gh,draft.payload(),review.revision,1,kind="harvest")}
        store.saveAndQueue("account-a",draft.id,gh,draft.copy(baseVersion=1,reason="Diperiksa ulang").payload(),review.revision,1,true,kind="harvest")
        val next=store.next("account-a")!!
        assertNotEquals(rejected.id,next.id);assertEquals(1L,next.envelope.getLong("base_version"))
        assertEquals(rejected.hash,store.outbox("account-a").single {it.id==rejected.id}.hash)
        assertEquals("SUPERSEDED",store.outbox("account-a").single {it.id==rejected.id}.state)
    }
    @Test fun revokedAssignmentKeepsLatestOwnedDraftButCannotQueueOrReadItsOldContext() {
        val ghRow=JSONObject().put("entity_type","greenhouses").put("entity_id",gh).put("greenhouse_id",gh).put("version",1).put("tombstone",false).put("data",JSONObject().put("id",gh).put("version",1).put("name","Known fixture GH"))
        store.applyPull("account-a",page(JSONArray().put(ghRow)))
        val id=UUID.randomUUID().toString();val first=store.saveDraft("account-a",id,gh,body("Sebelum dicabut"))
        store.applyPull("account-a",page(scope=JSONArray()))
        val saved=store.saveDraft("account-a",id,gh,body("Teks terakhir tetap disimpan"),first.revision)
        assertEquals(SyncState.SCOPE_REVOKED,saved.syncState);assertEquals("Teks terakhir tetap disimpan",saved.body.getString("notes"))
        assertThrows(IllegalArgumentException::class.java) {store.saveAndQueue("account-a",id,gh,body("Tidak boleh dikirim"),saved.revision)}
        val unsavedId=UUID.randomUUID().toString()
        val held=store.saveDraft("account-a",unsavedId,gh,body("Draf dimulai sebelum penugasan dicabut"))
        assertEquals(SyncState.SCOPE_REVOKED,held.syncState)
        assertTrue(store.records("account-a").isEmpty());assertNull(store.cachedOne("account-a","greenhouses",gh));assertNull(store.next("account-a"))
        val foreign=UUID.randomUUID().toString()
        assertThrows(IllegalArgumentException::class.java) {store.saveDraft("account-a",foreign,UUID.randomUUID().toString(),body("Unknown"))}
        assertNull(store.record("account-a",foreign))
    }
    @Test fun migrationTwoToCurrentKeepsCursorDraftAndOutbox() {
        val migrationName="finance-test-migration-${UUID.randomUUID()}.db"
        val id=UUID.randomUUID().toString();val op=UUID.randomUUID().toString()
        context.openOrCreateDatabase(migrationName,Context.MODE_PRIVATE,null).use {old ->
            FinanceDatabase.createVersionOne(old);old.execSQL("ALTER TABLE scope_metadata ADD COLUMN pull_cursor TEXT");old.version=2
            old.execSQL("INSERT INTO scope_metadata(scope,epoch,role,visible_greenhouses,pull_cursor) VALUES(?,?,?,?,?)",arrayOf("migrate",epoch,"worker","[]","cursor-v2"))
            old.execSQL("INSERT INTO scope_access(scope,greenhouse_id) VALUES(?,?)",arrayOf("migrate",gh))
            old.execSQL("INSERT INTO local_records(scope,entity_id,kind,greenhouse_id,revision,body,sync_state,updated_at) VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("migrate",id,"activity",gh,1,body().toString(),"QUEUED",1))
            old.execSQL("INSERT INTO outbox(scope,operation_id,entity_id,local_revision,envelope,canonical_hash,state,created_at) VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("migrate",op,id,1,"{}","fixture","QUEUED",1))
        }
        val upgraded=FinanceDatabase(context,migrationName)
        try {
            val migrated=LocalFinanceStore(upgraded,FixtureCipher())
            assertEquals("cursor-v2",migrated.pullCursor("migrate"));assertEquals(FinanceDatabase.VERSION,upgraded.readableDatabase.version)
            assertEquals(op,migrated.outbox("migrate").single().id)
            assertEquals(id,migrated.records("migrate",filter=LocalHistoryFilter(type="general")).single().id)
        } finally {upgraded.close();context.deleteDatabase(migrationName)}
    }
    @Test fun migrationThreeKeepsArchivedDraftAndOrdersByOccurrenceInsteadOfArrival() {
        val migrationName="finance-test-migration-${UUID.randomUUID()}.db";val id=UUID.randomUUID().toString();val older=UUID.randomUUID().toString()
        context.openOrCreateDatabase(migrationName,Context.MODE_PRIVATE,null).use {old ->
            FinanceDatabase.createVersionOne(old);old.execSQL("ALTER TABLE scope_metadata ADD COLUMN pull_cursor TEXT")
            for(column in listOf("occurred_date","activity_type","group_id","record_state")) old.execSQL("ALTER TABLE local_records ADD COLUMN $column TEXT")
            old.execSQL("ALTER TABLE local_records ADD COLUMN indexed INTEGER NOT NULL DEFAULT 0")
            old.execSQL("CREATE INDEX local_history_filter ON local_records(scope,kind,group_id,occurred_date)")
            old.execSQL("CREATE TABLE local_record_revisions(scope TEXT NOT NULL,entity_id TEXT NOT NULL,revision INTEGER NOT NULL,kind TEXT NOT NULL,greenhouse_id TEXT NOT NULL,body TEXT NOT NULL,server_body TEXT,base_version INTEGER,server_version INTEGER,resolution TEXT NOT NULL,saved_at INTEGER NOT NULL,PRIMARY KEY(scope,entity_id,revision),FOREIGN KEY(scope,entity_id) REFERENCES local_records(scope,entity_id))")
            old.version=3
            old.execSQL("INSERT INTO scope_metadata(scope,epoch,role,visible_greenhouses) VALUES(?,?,?,?)",arrayOf("migrate",epoch,"worker",JSONArray().put(gh).toString()))
            old.execSQL("INSERT INTO scope_access(scope,greenhouse_id) VALUES(?,?)",arrayOf("migrate",gh))
            for((recordId,time,arrival) in listOf(Triple(id,"2026-10-29T09:00:00+07:00",1),Triple(older,"2026-10-29T08:00:00+07:00",2)))
                old.execSQL("INSERT INTO local_records(scope,entity_id,kind,greenhouse_id,revision,body,sync_state,updated_at,indexed) VALUES(?,?,?,?,?,?,?,?,?)",arrayOf<Any>("migrate",recordId,"activity",gh,1,body().put("occurred_at",time).toString(),"LOCAL",arrival,1))
            old.execSQL("INSERT INTO local_record_revisions(scope,entity_id,revision,kind,greenhouse_id,body,resolution,saved_at) VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("migrate",id,1,"activity",gh,body("Arsip v3 utuh").toString(),"keep_local",1))
        }
        val upgraded=FinanceDatabase(context,migrationName)
        try {
            val migrated=LocalFinanceStore(upgraded,FixtureCipher());assertEquals(FinanceDatabase.VERSION,upgraded.readableDatabase.version)
            assertEquals("Arsip v3 utuh",migrated.archived("migrate",id).single().getString("notes"))
            assertEquals(listOf(id,older),migrated.records("migrate").map {it.id})
        } finally {upgraded.close();context.deleteDatabase(migrationName)}
    }
}
