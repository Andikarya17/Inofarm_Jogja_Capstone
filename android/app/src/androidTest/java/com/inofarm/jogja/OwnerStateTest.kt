package com.inofarm.jogja

import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.File

class OwnerStateTest {
    private val server=MockWebServer()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private lateinit var session:SessionStore
    private lateinit var repository:OwnerRepository
    private lateinit var vm:OwnerViewModel
    private val store=ViewModelStore()
    private val workerFailure=AtomicReference(false)
    private val configConflict=AtomicReference(false)
    private val slowDashboard=AtomicReference(false)
    private val slowOldHistory=AtomicReference(false)
    private val savedWorker=AtomicReference<String?>(null)
    private val dashboardFailure=AtomicReference(false)
    @Before fun setup() {
        session=SessionStore(context);session.clear();server.start()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val path=request.requestUrl!!.encodedPath.substringAfter("/api/v1/")
                val response=MockResponse().setHeader("Content-Type","application/json")
                return when {
                    path=="login" -> {
                        val user=if(request.body.readUtf8().contains("other@")) 2 else 1
                        response.setBody(JSONObject().put("token","fixture-token-$user").put("expiresAt",Instant.now().plusSeconds(3600).toString()).put("user",JSONObject().put("id",user).put("tank_id",user)).toString())
                    }
                    path=="logout" -> response.setResponseCode(204)
                    path=="dashboard" -> {
                        if(dashboardFailure.get()) return response.setResponseCode(503).setBody("{\"message\":\"Offline fixture\"}")
                        if(slowDashboard.get()) response.setBodyDelay(2,TimeUnit.SECONDS)
                        response.setBody("{\"connectionState\":\"unknown\",\"activePhase\":null,\"serverTime\":\"2026-10-10T00:00:00Z\"}")
                    }
                    path=="config" && request.method=="PUT" -> if(configConflict.get()) response.setResponseCode(409).setBody("{\"message\":\"Versi berubah\"}") else response.setBody(JSONObject().put("config",JSONObject().put("id",2).put("body",ConfigDraft().json())).toString())
                    path=="active-phase" -> response.setBody("{\"activePhase\":{\"id\":7}}")
                    path=="config" -> response.setBody("{\"config\":null,\"activePhase\":null}")
                    path.startsWith("workers") && request.method=="PUT" -> { val body=request.body.readUtf8();savedWorker.set(body);response.setBody("{\"id\":1,\"version\":2,\"name\":\"Renamed\",\"active\":false}") }
                    path=="workers" -> if(workerFailure.get()) response.setResponseCode(503).setBody("{\"message\":\"Worker refresh unavailable\"}") else response.setBody("{\"data\":[{\"id\":1,\"version\":1,\"name\":\"Old\",\"active\":false}],\"last_page\":1}")
                    path=="history/export" -> response.setBody("fixture CSV")
                    path=="history" -> {
                        if(request.requestUrl!!.queryParameter("from")=="2026-10-01" && slowOldHistory.get()) response.setBodyDelay(2,TimeUnit.SECONDS)
                        if(request.requestUrl!!.queryParameter("from")=="2026-10-02" && slowOldHistory.get()) response.setBodyDelay(1,TimeUnit.SECONDS)
                        response.setBody("{\"data\":[],\"next_cursor\":null}")
                    }
                    else -> response.setBody("{\"data\":[],\"last_page\":1,\"next_cursor\":null}")
                }
            }
        }
        repository=OwnerRepository(ApiClient({session.baseUrl},{session.readToken()},true),session,ReadCache(context,session))
        instrumentation.runOnMainSync { vm=ViewModelProvider(store,object:ViewModelProvider.Factory { override fun <T:ViewModel> create(modelClass:Class<T>):T { @Suppress("UNCHECKED_CAST") return OwnerViewModel(repository) as T } })[OwnerViewModel::class.java] }
    }
    @After fun cleanup() { instrumentation.runOnMainSync { store.clear() };session.clear();server.shutdown() }
    private fun onMain(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun await(block:()->Boolean) { val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!block()) { if(System.nanoTime()>until) fail("State timed out: ${vm.state.value}");Thread.sleep(20) } }
    private fun login(email:String="owner@example.invalid") { val base=server.url("/api/v1/").toString();onMain { vm.login(base,email,"fixture") };await { vm.state.value.signedIn && vm.state.value.resources.size>=6 && vm.state.value.resources.values.none { it.loading } } }

    @Test fun partialFailureAndMutationAckAreIndependentOfRefreshFailure() {
        workerFailure.set(true);login()
        assertNotNull(vm.state.value.dashboard);assertNull(vm.state.value.resource("dashboard").error);assertNotNull(vm.state.value.resource("workers").error)
        var id:Long?=null
        onMain { id=vm.worker("Renamed",Worker(1,1,"Old",false)) }
        await { vm.state.value.action("worker").ackId==id && !vm.state.value.resource("workers").loading }
        assertFalse(JSONObject(savedWorker.get()!!).getBoolean("active"))
        assertEquals(2,vm.state.value.workers.single().version)
        assertNull(vm.state.value.action("worker").error);assertNotNull(vm.state.value.resource("workers").error)
        onMain { vm.saveConfig(ConfigDraft().json(),null) };await { vm.state.value.config?.version==2L }
        assertNull(vm.state.value.action("config").error)
        onMain { vm.phase(PlantPhase.Generatif) };await { vm.state.value.config?.phaseVersion==7L }
        assertEquals(PlantPhase.Generatif,vm.state.value.config?.phase)
    }
    @Test fun oldQueryCannotPublishAndExportUsesAppliedFilter() {
        login();slowOldHistory.set(true)
        onMain { vm.filterHistory("2026-10-01","","") };Thread.sleep(100)
        onMain { vm.filterHistory("2026-10-02","","issues") }
        Thread.sleep(100);assertTrue("Canceled request must not clear its replacement's loading state",vm.state.value.resource("history").loading)
        await { vm.state.value.appliedFilters["from"]=="2026-10-02" && !vm.state.value.resource("history").loading }
        Thread.sleep(2200);assertEquals("2026-10-02",vm.state.value.appliedFilters["from"])
        onMain { vm.export() };await { vm.state.value.exportReady }
        var exported:RecordedRequest?=null
        while(true) { val request=server.takeRequest(1,TimeUnit.SECONDS) ?: break;if(request.requestUrl!!.encodedPath.endsWith("/history/export")) exported=request }
        assertEquals("2026-10-02",exported!!.requestUrl!!.queryParameter("from"));assertEquals("issues",exported.requestUrl!!.queryParameter("status"))
    }
    @Test fun logoutDuringSlowRefreshAndDifferentAccountCannotPublishOldResults() {
        login();slowDashboard.set(true)
        onMain { vm.refreshDashboard() };await { vm.state.value.resource("dashboard").loading }
        onMain { vm.logout() };assertFalse(vm.state.value.signedIn);assertNull(vm.state.value.dashboard);assertNull(runBlocking {repository.cached()})
        slowDashboard.set(false);login("other@example.invalid")
        val newScope=session.cacheScope;Thread.sleep(2200)
        assertEquals(newScope,session.cacheScope);assertTrue(vm.state.value.signedIn)
        assertNull(vm.state.value.error)
    }
    @Test fun versionConflictDoesNotProduceMutationAck() {
        login();configConflict.set(true)
        onMain { vm.saveConfig(ConfigDraft().json(),null) };await { vm.state.value.action("config").error!=null }
        assertNull(vm.state.value.action("config").ackId);assertNull(vm.state.value.config?.version)
    }
    @Test fun encryptedCacheIsBoundedScopedAndClearedAtExpiry() {
        login();val scope=session.cacheScope!!;val cache=ReadCache(context,session)
        val rows=org.json.JSONArray();repeat(550) { rows.put(JSONObject().put("id",it)) }
        cache.history(scope,JSONObject().put("data",rows),emptyMap(),false)
        assertEquals(500,cache.read()!!.getJSONObject("resources").getJSONObject("history").getJSONObject("payload").getJSONArray("data").length())
        val file=File(context.noBackupFilesDir,"owner-read-cache-v2-history.bin")
        assertFalse(file.readText().contains("schemaVersion"))
        session.identity(JSONObject().put("id",2).put("tank_id",2),Instant.now().plusSeconds(3600).toString());assertNull(cache.read())
        cache.save(scope,"config",JSONObject());assertNull(cache.read())
        session.identity(JSONObject().put("id",1).put("tank_id",1),Instant.now().minusSeconds(1).toString())
        assertNull(session.readToken());assertFalse(file.exists());assertNull(cache.read())
    }
    @Test fun recreatedViewModelRestoresCacheWithProvenanceAndFailedRefreshStaysLastKnown() {
        login();dashboardFailure.set(true)
        val restoredStore=ViewModelStore();lateinit var restored:OwnerViewModel
        try {
            onMain { restored=ViewModelProvider(restoredStore,object:ViewModelProvider.Factory { override fun <T:ViewModel> create(modelClass:Class<T>):T { @Suppress("UNCHECKED_CAST") return OwnerViewModel(repository) as T } })[OwnerViewModel::class.java] }
            val restoredUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            while((restored.state.value.restoring || restored.state.value.resource("dashboard").error==null) && System.nanoTime()<restoredUntil) Thread.sleep(20)
            assertFalse(restored.state.value.restoring)
            assertNotNull(restored.state.value.dashboard)
            assertTrue(restored.state.value.resource("dashboard").lastKnown)
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            while(restored.state.value.resource("dashboard").loading && System.nanoTime()<until) Thread.sleep(20)
            assertNotNull(restored.state.value.resource("dashboard").fetchedAt)
            assertNotNull(restored.state.value.resource("dashboard").error)
            assertTrue(restored.state.value.resource("dashboard").lastKnown)
        } finally { onMain { restoredStore.clear() } }
    }
}
