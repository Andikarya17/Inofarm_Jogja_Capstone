package com.inofarm.jogja

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.FinanceDatabase
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.AccountScope
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.core.sync.FinanceSyncCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinanceSyncCoordinatorTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-test-sync-${UUID.randomUUID()}.db"
    private val pref="finance-test-session-${UUID.randomUUID()}"
    private val epoch=UUID.randomUUID().toString();private val gh=UUID.randomUUID().toString()
    private val scope=AccountScope("http://fixture.invalid/api/v2/",UUID.randomUUID().toString())
    private lateinit var helper: FinanceDatabase;private lateinit var store: LocalFinanceStore;private lateinit var vault: FinanceSessionVault
    @Before fun prepare() {
        assertTrue(context.packageName.endsWith(".finance"))
        helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher());vault=FinanceSessionVault(context,FixtureCipher(),pref)
        val ticket=vault.beginLogin();vault.save(ticket,FinanceSession(scope,"synthetic-token",Instant.now().plusSeconds(3600).toString(),"Sintetis",UserRole.WORKER))
        store.initialize(scope.key,epoch,UserRole.WORKER);store.applyPull(scope.key,page())
    }
    @After fun cleanup() { helper.close();context.deleteDatabase(name);context.getSharedPreferences(pref,0).edit().clear().commit() }
    private fun page()=JSONObject().put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh))).put("data",JSONArray()).put("next_cursor","fixture").put("has_more",false)
    private fun queue(): String {
        val id=UUID.randomUUID().toString()
        store.saveAndQueue(scope.key,id,gh,JSONObject().put("planting_group_id",UUID.randomUUID().toString()).put("activity_type","general").put("occurred_at","2026-10-29T08:00:00+07:00").put("work_state","completed").put("materials",JSONArray()).put("notes","Antre sintetis"))
        return id
    }
    @Test fun responseLossRetriesIdenticalOperationAndAccountLogoutDoesNotDeleteQueue() = runBlocking {
        val id=queue();val original=store.next(scope.key)!!
        var commits=0;val receipts=mutableMapOf<String,JSONObject>();val attempts=mutableListOf<String>()
        val api=object : FinanceApi {
            override fun validateBase(base:String)=base
            override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject = when(path) {
                "sync/status" -> JSONObject().put("sync_epoch",epoch)
                "sync/changes" -> page()
                else -> {
                    val op=body!!;attempts+=op.toString();val key=op.getString("operation_id")
                    val receipt=receipts.getOrPut(key) { commits++;JSONObject().put("operation_id",key).put("entity_id",id).put("version",1).put("data",JSONObject(op.getJSONObject("payload").toString()).put("id",id).put("version",1)) }
                    if(attempts.size==1) throw IOException("Synthetic response loss after commit")
                    receipt
                }
            }
        }
        FinanceSyncCoordinator(api,vault,store).run()
        assertEquals(1,commits);assertEquals("QUEUED",store.outbox(scope.key).single().state)
        vault.logout();assertNotNull(store.record(scope.key,id));assertEquals(original.id,store.outbox(scope.key).single().id)
        val ticket=vault.beginLogin();vault.save(ticket,FinanceSession(scope,"new-synthetic-token",Instant.now().plusSeconds(3600).toString(),"Sintetis",UserRole.WORKER))
        delay(1100)
        FinanceSyncCoordinator(api,vault,store).run()
        assertEquals(1,commits);assertEquals(attempts[0],attempts[1]);assertEquals(SyncState.ACCEPTED,store.record(scope.key,id)!!.syncState)
    }
    @Test fun oldAccountFailureCannotHoldNewAccountQueue() = runBlocking {
        val id=queue();val newScope=AccountScope(scope.server,UUID.randomUUID().toString())
        val api=object : FinanceApi {
            override fun validateBase(base:String)=base
            override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject {
                val ticket=vault.beginLogin();vault.save(ticket,FinanceSession(newScope,"other-synthetic-token",Instant.now().plusSeconds(3600).toString(),"B",UserRole.WORKER))
                store.initialize(newScope.key,epoch,UserRole.WORKER);store.applyPull(newScope.key,page())
                store.saveAndQueue(newScope.key,UUID.randomUUID().toString(),gh,JSONObject().put("notes","Akun B"))
                throw FinanceApiFailure(401,JSONObject().put("code","authentication_required"))
            }
        }
        FinanceSyncCoordinator(api,vault,store).run()
        assertEquals("QUEUED",store.outbox(newScope.key).single().state)
        assertEquals("QUEUED",store.outbox(scope.key).single().state)
        assertNotNull(store.record(scope.key,id));assertNull(store.record(newScope.key,id))
    }
}
