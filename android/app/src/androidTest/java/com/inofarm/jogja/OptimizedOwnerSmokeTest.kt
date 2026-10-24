package com.inofarm.jogja

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking

/** Real owner UI over TLS mock, with release network policy intact, no production database. */
class OptimizedOwnerSmokeTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val server=MockWebServer()
    private val store=ViewModelStore()
    private lateinit var vm: OwnerViewModel
    private lateinit var session: SessionStore
    private val dashboards=AtomicInteger()
    @Before fun setup() {
        check(instrumentation.targetContext.packageName.endsWith(".performance"))
        session=SessionStore(instrumentation.targetContext);session.clear()
        val http=httpsFixture(server);server.start()
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body=when(request.requestUrl!!.encodedPath.substringAfter("/api/v1/")) {
                    "login" -> JSONObject().put("token","tls-test-only").put("expiresAt",Instant.now().plusSeconds(3600).toString()).put("user",JSONObject().put("id",1).put("tank_id",1)).toString()
                    "dashboard" -> {dashboards.incrementAndGet();"{\"connectionState\":\"unknown\"}"}
                    "config" -> "{\"config\":null,\"activePhase\":null}"
                    "workers" -> "{\"data\":[{\"id\":1,\"version\":4,\"name\":\"TLS Worker\",\"active\":true}],\"last_page\":1}"
                    "shifts" -> "{\"data\":[{\"id\":7,\"shift_key\":\"test-shift\",\"worker_id\":1,\"body\":{\"days\":[1],\"startTime\":\"08:00\",\"endTime\":\"16:00\",\"startDate\":\"2026-10-10\",\"endDate\":null}}],\"last_page\":1}"
                    "history/export" -> "fixture,csv\n1,2"
                    else -> "{\"data\":[],\"next_cursor\":null}"
                }
                return MockResponse().setBody(body)
            }
        }
        val repo=OwnerRepository(ApiClient({session.baseUrl},{session.readToken()},false,http),session,ReadCache(instrumentation.targetContext,session))
        rule.runOnIdle {
            vm=ViewModelProvider(store,object:ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=OwnerViewModel(repo) as T
            })[OwnerViewModel::class.java]
            rule.activity.setContent {InofarmTheme {OwnerApp(vm) {vm.export()}}}
        }
        rule.waitUntil(10000) {rule.onAllNodesWithTag("connectionSettings").fetchSemanticsNodes().isNotEmpty()}
        if (rule.onAllNodesWithTag("apiUrl").fetchSemanticsNodes().isEmpty()) rule.onNodeWithTag("connectionSettings").performScrollTo().performClick()
        rule.onNodeWithTag("apiUrl").performTextReplacement(server.url("/api/v1/").toString())
        rule.onNodeWithTag("email").performTextInput("tls@example.invalid");rule.onNodeWithTag("password").performTextInput("fixture")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("login").performScrollTo().performClick()
        rule.waitUntil(15000) {vm.state.value.signedIn && vm.state.value.resources.size>=6 && vm.state.value.resources.values.none {it.loading}}
    }
    @After fun cleanup() {rule.runOnIdle {store.clear()};session.clear();server.shutdown()}
    @Test fun optimizedLoginDashboardHistorySettingsWorkersShiftAndExportStayUsable() {
        rule.onNodeWithTag("dashboardHero").assertIsDisplayed()
        rule.onNode(hasText("Riwayat") and hasClickAction()).performClick()
        rule.onNodeWithText("Ekspor riwayat").performScrollTo().performClick()
        rule.waitUntil(10000) {vm.state.value.exportReady};assertEquals("fixture,csv\n1,2",vm.takeExport())
        rule.onNode(hasText("Pengaturan") and hasClickAction()).performClick()
        rule.onNodeWithText("Kelola pekerja & shift").performScrollTo().performClick()
        rule.onNodeWithText("TLS Worker").assertExists()
        rule.onNodeWithText("Atur shift").performScrollTo().performClick()
        rule.onNodeWithText("Tambah shift").assertExists()
        assertEquals(4,vm.state.value.workers.single().version);assertEquals(7,vm.state.value.shifts.single().id)
        rule.runOnIdle {vm.logout()};rule.waitUntil(10000) {!vm.state.value.signedIn}
        rule.onNodeWithTag("login").assertExists();assertNull(vm.state.value.dashboard)
    }
    @Test fun dashboardPollsOnlyWhileResumedAndDashboardSelected() {
        val before=dashboards.get();rule.mainClock.advanceTimeBy(31_000);rule.waitUntil(10000) {dashboards.get()>before}
        rule.onNode(hasText("Pengaturan") and hasClickAction()).performClick();rule.waitForIdle()
        val inSettings=dashboards.get();rule.mainClock.advanceTimeBy(31_000);assertEquals(inSettings,dashboards.get())
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val paused=dashboards.get();rule.mainClock.advanceTimeBy(31_000);assertEquals(paused,dashboards.get())
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(10000) {dashboards.get()>paused}
    }
}
