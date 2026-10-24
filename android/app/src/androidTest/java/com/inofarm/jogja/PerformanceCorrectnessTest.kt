package com.inofarm.jogja

import android.os.StrictMode
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.IntSize
import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

/** Only installed in the explicitly isolated .performance package, using local mock responses. */
class PerformanceCorrectnessTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val server=MockWebServer()
    private val stores=mutableListOf<ViewModelStore>()
    private lateinit var session: SessionStore
    private lateinit var cache: ReadCache
    private lateinit var repository: OwnerRepository
    private lateinit var testHttp: okhttp3.OkHttpClient
    @Volatile private var online=false
    @Volatile private var paging=false
    @Volatile private var unauthorized=false
    @Volatile private var slowPagination=false
    @Volatile private var slowDashboard=false
    @Before fun setup() {
        check(context.packageName.endsWith(".performance")) {"Use -PperformanceTest; never clear owner app data"}
        session=SessionStore(context);session.clear();cache=ReadCache(context,session)
        testHttp=httpsFixture(server);server.start();session.baseUrl=server.url("/api/v1/").toString()
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if(unauthorized) return MockResponse().setResponseCode(401).setBody("{\"message\":\"expired test\"}")
                if(!online) return MockResponse().setResponseCode(503).setBody("{\"message\":\"offline test\"}")
                val path=request.requestUrl!!.encodedPath.substringAfter("/api/v1/")
                val body=when(path) {
                    "dashboard" -> "{\"connectionState\":\"recent_contact\"}"
                    "config" -> "{\"config\":null,\"activePhase\":null}"
                    "history" -> {
                        val offset=request.requestUrl!!.queryParameter("cursor")?.removePrefix("previous:")?.toInt() ?: 0
                        val top=if(request.requestUrl!!.queryParameter("from")!=null) 4000L else 2000L
                        val rows=JSONArray();if(paging) repeat(25) {rows.put(record(top-offset-it))}
                        JSONObject().put("prev_cursor",if(offset>0) "previous:${offset-25}" else JSONObject.NULL).put("data",rows).put("next_cursor",if(paging && offset<575) (offset+25).toString() else JSONObject.NULL).toString()
                    }
                    "workers" -> {
                        val page=request.requestUrl!!.queryParameter("page")?.toInt() ?: 1
                        JSONObject().put("data",JSONArray().put(JSONObject().put("id",page).put("version",page).put("name","Worker $page").put("active",true))).put("last_page",3).toString()
                    }
                    else -> "{\"data\":[],\"last_page\":1,\"next_cursor\":null}"
                }
                return MockResponse().setBody(body).apply {
                    if(path=="history" && slowPagination && request.requestUrl!!.queryParameter("cursor")!=null) setBodyDelay(1,TimeUnit.SECONDS)
                    if(path=="dashboard" && slowDashboard) setBodyDelay(2,TimeUnit.SECONDS)
                }
            }
        }
        repository=OwnerRepository(ApiClient({session.baseUrl},{session.readToken()},false,testHttp),session,cache)
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync {stores.forEach {it.clear()}}
        runBlocking {repository.clearExpired(repository.expire())}
        server.shutdown()
    }
    private fun seed() {session.saveToken("test-only-token");session.identity(JSONObject().put("id",1).put("tank_id",1),Instant.now().plusSeconds(3600).toString())}
    private fun start(repo: OwnerRepository = repository): OwnerViewModel {
        val store=ViewModelStore();stores+=store;lateinit var vm: OwnerViewModel
        instrumentation.runOnMainSync {
            vm=ViewModelProvider(store,object:ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=OwnerViewModel(repo) as T
            })[OwnerViewModel::class.java]
        }
        return vm
    }
    private fun await(condition: ()->Boolean) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
        while(!condition()) {if(System.nanoTime()>end) fail("State timeout");Thread.sleep(15)}
    }
    private fun record(id: Long)=JSONObject().put("id",id).put("body",JSONObject().put("confirmedAt","2026-10-10T00:00:00Z").put("measuredAt",JSONObject.NULL))
        .put("received_at","2026-10-10T00:01:00Z").put("phase","vegetatif").put("manual_ppm",900)
        .put("clock_trusted",true).put("config_version_id",JSONObject.NULL).put("phase_version_id",JSONObject.NULL)
        .put("assignment",JSONObject().put("status","unscheduled").put("workerNameSnapshot",JSONObject.NULL))
        .put("validation_result",JSONObject().put("status","incomplete").put("checks",JSONArray().put(JSONObject().put("result","incomplete").put("code","missing_config").put("message","Parameter belum lengkap"))))
    private fun cacheFile(resource: String)=File(context.noBackupFilesDir,"owner-read-cache-v2-$resource.bin")

    @Test fun emptyColdStartDoesNotTouchMainThreadDiskOrFlashSignedInData() {
        val violations=CopyOnWriteArrayList<android.os.strictmode.Violation>()
        instrumentation.runOnMainSync {
            val old=StrictMode.getThreadPolicy()
            try {
                if(android.os.Build.VERSION.SDK_INT>=28) StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyListener({it.run()},{violations.add(it)}).build())
                val vm=OwnerViewModel(repository)
                assertTrue(vm.state.value.restoring);assertFalse(vm.state.value.signedIn);assertNull(vm.state.value.dashboard)
                vm.logout()
            } finally {StrictMode.setThreadPolicy(old)}
        }
        assertTrue("Main disk violations: ${violations.size}",violations.isEmpty())
    }
    @Test fun nearLimitColdOfflineCachePreservesAllReasonsAndProvenance() {
        seed();val scope=session.cacheScope!!
        cache.save(scope,"config",JSONObject().put("config",JSONObject().put("id",1).put("body",JSONObject().put("padding","x".repeat(1_200_000)))))
        cache.save(scope,"dashboard",JSONObject().put("connectionState","unknown"))
        val rows=JSONArray();repeat(550) {rows.put(record(it.toLong()))}
        cache.history(scope,JSONObject().put("data",rows),mapOf("status" to "incomplete"),false)
        val bytes=ReadCache.resources.sumOf {cacheFile(it).length()}
        assertTrue(bytes>1_700_000);assertTrue(bytes<=ReadCache.maxBytes)
        val coldSession=SessionStore(context)
        val coldRepo=OwnerRepository(ApiClient({coldSession.baseUrl},{coldSession.readToken()},false,testHttp),coldSession,ReadCache(context,coldSession))
        val vm=start(coldRepo);await {!vm.state.value.restoring && vm.state.value.resource("history").error!=null}
        assertTrue(vm.state.value.signedIn);assertEquals(500,vm.state.value.history.size)
        assertEquals("missing_config",vm.state.value.history.first().checks.single().code)
        assertEquals("incomplete",vm.state.value.appliedFilters["status"])
        assertNull(vm.state.value.nextCursor);assertTrue(vm.state.value.resource("history").lastKnown)
        assertNotNull(vm.state.value.resource("history").fetchedAt)
    }
    @Test fun expiredSessionCannotReadCache() {
        seed();cache.save(session.cacheScope,"dashboard",JSONObject().put("connectionState","unknown"))
        session.identity(JSONObject().put("id",1).put("tank_id",1),Instant.now().minusSeconds(1).toString())
        val vm=start();await {!vm.state.value.restoring}
        assertFalse(vm.state.value.signedIn);assertNull(vm.state.value.dashboard);assertFalse(cacheFile("dashboard").exists())
    }
    @Test fun corruptResourceAndSchemaOneDoNotDeleteValidSessionOrOtherResources() {
        seed();cache.save(session.cacheScope,"dashboard",JSONObject().put("connectionState","unknown"))
        cacheFile("config").writeText("corrupt")
        val legacy=File(context.noBackupFilesDir,"owner-read-cache.bin")
        legacy.writeText(session.encrypt(JSONObject().put("schemaVersion",1).toString()))
        val vm=start();await {!vm.state.value.restoring && vm.state.value.resource("dashboard").error!=null}
        assertTrue(vm.state.value.signedIn);assertNotNull(vm.state.value.dashboard);assertNull(vm.state.value.config)
        assertFalse(legacy.exists());assertNotNull(session.validToken)
    }
    @Test fun logoutWhileRestoreBlockedNeverResurrectsSession() {
        seed();cache.save(session.cacheScope,"dashboard",JSONObject().put("connectionState","unknown"))
        lateinit var vm: OwnerViewModel
        synchronized(cache) {
            vm=start();assertTrue(vm.state.value.restoring)
            instrumentation.runOnMainSync {vm.logout()}
            assertFalse(vm.state.value.signedIn);assertNull(vm.state.value.dashboard)
        }
        runBlocking {repository.clearExpired(session.generation)}
        Thread.sleep(150);assertFalse(vm.state.value.signedIn);assertNull(vm.state.value.dashboard);assertNull(cache.read())
    }
    @Test fun newServerResultCannotBeOverwrittenByDelayedRestore() {
        seed();cache.save(session.cacheScope,"dashboard",JSONObject().put("connectionState","unknown"));online=true
        lateinit var vm: OwnerViewModel
        synchronized(cache) {vm=start();assertNull(vm.state.value.dashboard);assertEquals(0,server.requestCount)}
        await {vm.state.value.dashboard?.connection=="recent_contact" && !vm.state.value.resource("dashboard").loading}
        Thread.sleep(100);assertEquals("recent_contact",vm.state.value.dashboard?.connection);assertFalse(vm.state.value.resource("dashboard").lastKnown)
    }
    @Test fun invalidatedWriteTicketCannotWriteIntoSameAccountNewSession() {
        seed();val scope=session.cacheScope!!;val oldTicket=session.generation
        val ready=CountDownLatch(1);val release=CountDownLatch(1)
        runBlocking {
            val write=launch(Dispatchers.IO) {cache.save(scope,"dashboard",JSONObject().put("connectionState","old"),ticket=oldTicket,active={ready.countDown();release.await(5,TimeUnit.SECONDS);true})}
            assertTrue(ready.await(5,TimeUnit.SECONDS))
            val ticket=repository.expire();seed();release.countDown();write.join()
            assertNull(cache.read())
            // Old cleanup cannot clear a later login generation.
            session.invalidate();seed();repository.clearExpired(ticket);assertNotNull(session.validToken)
        }
    }
    @Test fun oversizeAndDiskFailurePreserveGoodCacheAndServerSuccess() = runBlocking {
        seed();val scope=session.cacheScope!!;cache.save(scope,"dashboard",JSONObject().put("connectionState","unknown"))
        val before=cacheFile("dashboard").readBytes()
        cache.save(scope,"dashboard",JSONObject().put("padding","x".repeat(2_000_000)))
        assertArrayEquals(before,cacheFile("dashboard").readBytes())
        val blocked=cacheFile("config");blocked.mkdir();File(blocked,"fixture").writeText("test-only");online=true
        assertNull(repository.config().version)
        assertArrayEquals(before,cacheFile("dashboard").readBytes())
        assertTrue(cacheFile("config").isDirectory);File(blocked,"fixture").delete();blocked.delete()
        Unit
    }
    @Test fun dashboardWriteDoesNotRewriteHistoryAndAggregateBoundSurvives() {
        seed();val scope=session.cacheScope!!
        cache.history(scope,JSONObject().put("data",JSONArray().put(record(1))),emptyMap(),false)
        val history=cacheFile("history").readBytes()
        repeat(6) {cache.save(scope,"dashboard",JSONObject().put("connectionState","unknown"))}
        assertArrayEquals(history,cacheFile("history").readBytes())
        assertTrue(ReadCache.resources.sumOf {cacheFile(it).length()}<=ReadCache.maxBytes)
    }
    @Test fun twentyFourPagesStayBoundedAndEarlierRowsRemainReachable() {
        seed();online=true;paging=true;val vm=start()
        await {vm.state.value.history.size==25 && !vm.state.value.resource("history").loading}
        repeat(23) {
            instrumentation.runOnMainSync {vm.more()}
            await {!vm.state.value.resource("pagination").loading}
            assertTrue(vm.state.value.history.size<=100)
            assertEquals(vm.state.value.history.size,vm.state.value.history.map {it.id}.distinct().size)
        }
        assertTrue(vm.state.value.hasPreviousHistory);assertEquals(1401L,vm.state.value.history.last().id)
        repeat(5) {instrumentation.runOnMainSync {vm.previousHistory()};await {!vm.state.value.resource("pagination").loading}}
        assertFalse(vm.state.value.hasPreviousHistory);assertEquals(2000L,vm.state.value.history.first().id)
        assertEquals((1901L..2000L).reversed().toList(),vm.state.value.history.map {it.id})
    }
    @Test fun workersRetainEveryPageForEditorIdentity() = runBlocking {
        seed();online=true
        assertEquals(listOf(1L,2L,3L),repository.workers().map {it.id})
    }
    @Test fun filterChangeDuringPaginationDiscardsOldRowsAndCacheFilters() {
        seed();online=true;paging=true;val vm=start()
        await {vm.state.value.history.size==25 && !vm.state.value.resource("history").loading}
        slowPagination=true
        instrumentation.runOnMainSync {vm.more()}
        instrumentation.runOnMainSync {vm.filterHistory("2026-10-01","","")}
        await {!vm.state.value.resource("history").loading && vm.state.value.appliedFilters["from"]=="2026-10-01"}
        Thread.sleep(1200)
        assertEquals(25,vm.state.value.history.size);assertEquals(4000L,vm.state.value.history.first().id)
        assertEquals("2026-10-01",cache.read()!!.getJSONObject("resources").getJSONObject("history").getJSONObject("filters").getString("from"))
    }
    @Test fun unauthorizedResponseAndDirectLogoutClearAccessAndStorage() = runBlocking {
        seed();cache.save(session.cacheScope,"dashboard",JSONObject().put("connectionState","unknown"));unauthorized=true
        val vm=start();await {!vm.state.value.restoring && vm.state.value.error!=null && !vm.state.value.signedIn}
        await {!cacheFile("dashboard").exists()};assertNull(session.validToken);assertNull(vm.state.value.dashboard)
        unauthorized=false;online=true;seed();repository.logout();assertFalse(repository.signedIn);assertNull(cache.read())
        Unit
    }
    @Test fun heroDecodedDimensionsAndAllocationFollowViewportWithoutDensityUpscaling() {
        val measurements=mutableListOf<String>()
        for(resource in listOf(R.drawable.melon_hero,R.drawable.leaf_hero)) {
            val large=decodeHero(context.resources,resource,IntSize(1080,420))
            val small=decodeHero(context.resources,resource,IntSize(240,100))
            try {
                assertEquals(960,large.width);assertTrue(small.width<=480)
                assertTrue(small.allocationByteCount<large.allocationByteCount)
                measurements+="${context.resources.getResourceEntryName(resource)} large=${large.width}x${large.height},${large.allocationByteCount}B small=${small.width}x${small.height},${small.allocationByteCount}B"
            } finally {large.recycle();small.recycle()}
        }
        File(context.getExternalFilesDir(null),"hero-allocation.txt").writeText(measurements.joinToString("\n"))
    }
    @Test fun productionHttpsRejectsFixtureCertificateAndCleartext() = runBlocking {
        val api=ApiClient({session.baseUrl},{null},false)
        assertThrows(IllegalArgumentException::class.java) {api.validateBase("http://localhost/api/v1/")}
        try {api.raw("dashboard");fail("Default client must not trust the test certificate")} catch(_:javax.net.ssl.SSLHandshakeException) {}
        Unit
    }
    @Test fun independentCacheInstancesShareBudgetAndRecoverInterruptedAtomicFiles() = runBlocking {
        seed();val scope=session.cacheScope!!;val other=ReadCache(context,session)
        cache.save(scope,"config",JSONObject().put("padding","x".repeat(1_200_000)))
        val first=launch(Dispatchers.IO) {cache.save(scope,"workers",JSONObject().put("padding","x".repeat(350_000)))}
        val second=launch(Dispatchers.IO) {other.save(scope,"shifts",JSONObject().put("padding","x".repeat(350_000)))}
        first.join();second.join()
        assertTrue(cacheFile("workers").exists() xor cacheFile("shifts").exists())
        assertTrue(context.noBackupFilesDir.listFiles()!!.filter {it.name.startsWith("owner-read-cache")}.sumOf {it.length()}<=ReadCache.maxBytes)
        cache.clear();cache.save(scope,"dashboard",JSONObject().put("connectionState","unknown"))
        val original=cacheFile("dashboard");assertTrue(original.renameTo(File(original.path+".bak")))
        File(original.path+".new").writeText("interrupted fixture")
        val orphan=File(cacheFile("telemetry").path+".new");orphan.writeText("orphan fixture")
        assertEquals("unknown",cache.read()!!.getJSONObject("resources").getJSONObject("dashboard").getJSONObject("payload").getString("connectionState"))
        assertFalse(File(original.path+".bak").exists());assertFalse(orphan.exists())
        Unit
    }
    @Test fun coldClockRollbackInvalidatesSessionAndCache() {
        seed();cache.save(session.cacheScope,"dashboard",JSONObject().put("connectionState","unknown"))
        context.getSharedPreferences("inofarm_session",0).edit().putLong("startedAt",Instant.now().plusSeconds(60).toEpochMilli()).commit()
        val cold=SessionStore(context)
        assertNull(cold.readToken());assertNull(cold.cacheScope);assertFalse(cacheFile("dashboard").exists())
    }
    @Test fun responseArrivingAfterKnownExpiryCannotPublishCachedOrServerData() {
        seed();cache.save(session.cacheScope,"dashboard",JSONObject().put("connectionState","unknown"))
        session.identity(JSONObject().put("id",1).put("tank_id",1),Instant.now().plusSeconds(1).toString())
        online=true;slowDashboard=true;val vm=start()
        await {!vm.state.value.restoring && vm.state.value.error!=null && !vm.state.value.signedIn}
        assertNull(vm.state.value.dashboard);await {!cacheFile("dashboard").exists()}
    }
}
