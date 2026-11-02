package com.inofarm.jogja

import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.FinanceDatabase
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.feature.admin.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.Instant
import java.util.UUID

class FinanceAdminTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-test-admin-${UUID.randomUUID()}"
    private val epoch=UUID.randomUUID().toString()
    private lateinit var helper:FinanceDatabase;private lateinit var store:LocalFinanceStore;private lateinit var vault:FinanceSessionVault
    private lateinit var session:FinanceSession
    private val attempts=mutableListOf<String>();private var loseResponse=false;private var switchAccount=false
    private val api=object:FinanceApi {
        override fun validateBase(base:String)=base
        override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject {
            if(path=="sync/status") return JSONObject().put("schema_version","2").put("sync_epoch",epoch)
            attempts+=body!!.toString()
            if(loseResponse) throw IOException("Synthetic response lost after commit")
            if(switchAccount) vault.logout()
            return JSONObject().put("operation_id",body.getString("operation_id")).put("entity_id",body.getString("entity_id"))
        }
    }
    private fun repository()=AdminRepository(context,vault,store,api,AndroidPayloadCipher(),name+"-admin")
    @Before fun setup() {
        assertTrue(context.packageName.endsWith(".finance"))
        helper=FinanceDatabase(context,name+".db");store=LocalFinanceStore(helper,AndroidPayloadCipher())
        vault=FinanceSessionVault(context,AndroidPayloadCipher(),name+"-session")
        session=FinanceSession(AccountScope("http://fixture.invalid/api/v2/",UUID.randomUUID().toString()),"synthetic-token",Instant.now().plusSeconds(3600).toString(),"Pengelola",UserRole.MANAGER)
        assertTrue(vault.save(vault.generation,session));store.initialize(session.scope.key,epoch,session.role)
    }
    @After fun cleanup() {
        helper.close();context.deleteDatabase(name+".db")
        listOf("-admin","-session").forEach {context.getSharedPreferences(name+it,0).edit().clear().commit()}
    }
    private fun form()=AdminForm(AdminResource.GREENHOUSE,values=mapOf("name" to "GH sintetis"))
    @Test fun lostResponseAndReopenRetryTheExactOperationWithoutDuplicateIdentity()=runBlocking {
        val form=form();val repo=repository();val ticket=vault.generation
        val journal=repo.prepare(session,ticket,form,"",JSONObject().put("revision",1).put("form",form.json()))
        loseResponse=true;assertTrue(runCatching {repo.send(session,ticket,journal,"")}.isFailure)
        val reopened=repository();val recovered=reopened.read(session,ticket)
        loseResponse=false;reopened.send(session,ticket,recovered,"")
        assertEquals(2,attempts.size);assertEquals(attempts[0],attempts[1])
        assertEquals(form.id,recovered.getJSONObject("pending").getString("entity_id"))
    }
    @Test fun passwordStaysOutOfDurableJournalAndRetryRequiresTheOriginalSecret()=runBlocking {
        val form=AdminForm(AdminResource.USER,values=mapOf("greenhouse_id" to UUID.randomUUID().toString(),"name" to "Akun uji","email" to "test@example.invalid","role" to "worker"))
        val password="synthetic-passphrase-only";val repo=repository();val ticket=vault.generation
        val journal=repo.prepare(session,ticket,form,password,JSONObject().put("revision",1).put("form",form.json()))
        assertFalse(repo.read(session,ticket).toString().contains(password))
        assertTrue(runCatching {repo.send(session,ticket,journal,"different-passphrase-only")}.isFailure)
        assertTrue(attempts.isEmpty());repo.send(session,ticket,journal,password);assertEquals(1,attempts.size)
    }
    @Test fun olderAutosaveCannotRemoveNewerPendingAndWorkerCannotReadManagerJournal()=runBlocking {
        val repo=repository();val form=form();val ticket=vault.generation
        repo.prepare(session,ticket,form,"",JSONObject().put("revision",2).put("form",form.json()))
        repo.write(session,ticket,JSONObject().put("revision",1).put("form",form.json()))
        assertTrue(repo.read(session,ticket).has("pending"))
        vault.save(ticket,session.copy(role=UserRole.WORKER))
        assertTrue(runCatching {repo.read(session,ticket)}.isFailure)
    }
    @Test fun delayedOldAccountResponseCannotBeAcceptedAndJournalRemains()=runBlocking {
        val repo=repository();val form=form();val ticket=vault.generation
        val journal=repo.prepare(session,ticket,form,"",JSONObject().put("revision",1).put("form",form.json()))
        switchAccount=true;assertTrue(runCatching {repo.send(session,ticket,journal,"")}.isFailure)
        vault.save(vault.generation,session)
        assertTrue(repo.read(session,vault.generation).has("pending"))
    }
    @Test fun changedEpochCannotCreateAnOperation()=runBlocking {
        store.initialize(session.scope.key,UUID.randomUUID().toString(),session.role)
        assertTrue(runCatching {repository().prepare(session,vault.generation,form(),"",JSONObject())}.isFailure)
        assertFalse(repository().read(session,vault.generation).has("pending"));assertTrue(attempts.isEmpty())
    }
    @Test fun referenceListKeepsPaginationButFiltersGroupsToTheSelectedGreenhouse()=runBlocking {
        val gh=UUID.randomUUID().toString();val group=UUID.randomUUID().toString()
        val transport=object:FinanceApi {
            override fun validateBase(base:String)=base
            override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject {
                return if(path=="planting-groups") {
                    assertEquals(gh,query["greenhouse_id"])
                    JSONObject().put("data",org.json.JSONArray().put(JSONObject().put("id",group))).put("next_cursor",JSONObject.NULL)
                } else JSONObject().put("data",org.json.JSONArray().put(JSONObject().put("id","a").put("planting_group_id",group)).put(JSONObject().put("id","b").put("planting_group_id",UUID.randomUUID().toString()))).put("next_cursor","next-page")
            }
        }
        val repo=AdminRepository(context,vault,store,transport,AndroidPayloadCipher(),name+"-admin")
        val page=repo.list(session,vault.generation,AdminResource.TARGET,gh)
        assertEquals(1,page.getJSONArray("data").length());assertEquals("a",page.getJSONArray("data").getJSONObject(0).getString("id"));assertEquals("next-page",page.getString("next_cursor"))
    }
}
