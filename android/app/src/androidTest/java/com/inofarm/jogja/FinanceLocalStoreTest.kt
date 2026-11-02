package com.inofarm.jogja

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.FinanceDatabase
import com.inofarm.jogja.core.database.LocalFinanceStore
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
    @Test fun migrationOneToTwoKeepsDraftAndOutbox() {
        val migrationName="finance-test-migration-${UUID.randomUUID()}.db"
        val id=UUID.randomUUID().toString();val operationId=UUID.randomUUID().toString()
        context.openOrCreateDatabase(migrationName,Context.MODE_PRIVATE,null).use { old ->
            FinanceDatabase.createVersionOne(old);old.version=1
            old.execSQL("INSERT INTO scope_metadata(scope,epoch,role,visible_greenhouses) VALUES(?,?,?,?)",arrayOf("migrate",epoch,"worker","[]"))
            old.execSQL("INSERT INTO local_records(scope,entity_id,kind,greenhouse_id,revision,body,sync_state,updated_at) VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("migrate",id,"activity",gh,1,body().toString(),"QUEUED",1))
            old.execSQL("INSERT INTO outbox(scope,operation_id,entity_id,local_revision,envelope,canonical_hash,state,created_at) VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("migrate",operationId,id,1,"{}","fixture","QUEUED",1))
        }
        val upgraded=FinanceDatabase(context,migrationName)
        try {
            val migrated=LocalFinanceStore(upgraded,FixtureCipher())
            assertNotNull(migrated.record("migrate",id));assertEquals(operationId,migrated.outbox("migrate").single().id)
            assertNull(migrated.pullCursor("migrate"));assertEquals(2,upgraded.readableDatabase.version)
        } finally { upgraded.close();context.deleteDatabase(migrationName) }
    }
}
