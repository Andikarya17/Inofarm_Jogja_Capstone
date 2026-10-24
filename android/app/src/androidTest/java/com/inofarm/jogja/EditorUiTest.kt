package com.inofarm.jogja

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class EditorUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val server=MockWebServer()
    private val workerBody=AtomicReference<String?>(null)
    private val configBody=AtomicReference<String?>(null)
    private val posts=AtomicInteger(0)
    private val shiftBody=AtomicReference<String?>(null)
    private val workerName=AtomicReference("Old")
    private val failConfig=AtomicReference(false)
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    @Before fun setup() {
        SessionStore(instrumentation.targetContext).clear();rule.activityRule.scenario.recreate();server.start()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val path=request.requestUrl!!.encodedPath.substringAfter("/api/v1/");val r=MockResponse().setHeader("Content-Type","application/json")
                return when {
                    path=="login" -> r.setBody("{\"token\":\"ui-fixture\"}")
                    path=="dashboard" -> r.setBody("{\"connectionState\":\"never_seen\",\"device\":null,\"telemetry\":null,\"latestRecord\":null,\"activePhase\":null}")
                    path=="config" && request.method=="PUT" -> { configBody.set(request.body.readUtf8()); if(failConfig.get()) r.setResponseCode(409).setBody("{\"message\":\"Version conflict fixture\"}") else r.setBody(JSONObject().put("config",JSONObject().put("id",4).put("body",JSONObject(configBody.get()!!).getJSONObject("config"))).toString()) }
                    path=="config" -> r.setBody(JSONObject().put("config",JSONObject().put("id",3).put("body",ConfigDraft().json())).put("activePhase",JSONObject.NULL).toString())
                    path.startsWith("workers") && request.method=="PUT" -> { workerBody.set(request.body.readUtf8());workerName.set(JSONObject(workerBody.get()!!).getString("name"));r.setBody(JSONObject().put("id",1).put("version",2).put("name",workerName.get()).put("active",false).toString()) }
                    path=="workers" && request.method=="POST" -> { posts.incrementAndGet();workerBody.set(request.body.readUtf8());r.setBody("{\"id\":2,\"version\":5,\"name\":\"New worker\",\"active\":true}") }
                    path=="workers" -> r.setBody(JSONObject().put("data",org.json.JSONArray().put(JSONObject().put("id",1).put("version",1).put("name",workerName.get()).put("active",false))).put("last_page",1).toString())
                    path=="history/export" -> r.setBody("fixture,csv\n1,2")
                    path.startsWith("shifts/") && request.method=="PUT" -> { shiftBody.set(request.body.readUtf8()); val body=JSONObject(shiftBody.get()!!);r.setBody(JSONObject().put("id",11).put("shift_key","fixture-shift-key").put("worker_id",1).put("body",body).toString()) }
                    path=="shifts" -> r.setBody("{\"data\":[{\"id\":10,\"shift_key\":\"fixture-shift-key\",\"worker_id\":1,\"body\":{\"days\":[6],\"startTime\":\"08:00\",\"endTime\":\"16:00\",\"startDate\":\"2026-10-10\",\"endDate\":null}}],\"last_page\":1}")
                    else -> r.setBody("{\"data\":[],\"last_page\":1,\"next_cursor\":null}")
                }
            }
        }
        if (rule.onAllNodesWithTag("apiUrl").fetchSemanticsNodes().isEmpty()) rule.onNodeWithTag("connectionSettings").performScrollTo().performClick()
        rule.onNodeWithTag("apiUrl").performScrollTo().performTextReplacement(server.url("/api/v1/").toString());rule.onNodeWithTag("email").performScrollTo().performTextInput("ui@example.invalid");rule.onNodeWithTag("password").performScrollTo().performTextInput("fixture")
        rule.onNodeWithTag("password").assertTextContains("fixture")
        rule.runOnIdle { (rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0) }
        Thread.sleep(300);rule.waitForIdle();rule.onNodeWithTag("login").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("dashboardHero").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { SessionStore(instrumentation.targetContext).clear();server.shutdown() }
    private fun workers() { rule.onNodeWithText("Pengaturan").performClick();rule.onNodeWithText("Kelola pekerja & shift").performScrollTo().performClick();rule.waitUntil(10000) { rule.onAllNodesWithText("Old · Nonaktif").fetchSemanticsNodes().isNotEmpty() } }
    @Test fun renameAfterRecreationKeepsIdVersionAndInactiveThenClosesEditor() {
        workers();rule.onNodeWithText("Edit nama").performClick();rule.onNodeWithTag("Edit nama pekerja").performScrollTo().performTextReplacement("Renamed")
        rule.onNodeWithTag("Edit nama pekerja").assertTextContains("Renamed")
        rule.waitForIdle()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("Edit nama pekerja").performScrollTo().assertTextContains("Renamed")
        rule.onNodeWithText("Simpan pekerja").performScrollTo().performClick()
        rule.waitUntil(10000) { workerBody.get()!=null && rule.onAllNodesWithText("Nama pekerja baru").fetchSemanticsNodes().isNotEmpty() }
        val sent=JSONObject(workerBody.get()!!);assertEquals(1,sent.getLong("baseVersion"));assertFalse(sent.getBoolean("active"));assertEquals(0,posts.get())
    }
    @Test fun successfulCreateClearsFormAndCannotReplayFromStaleSuccess() {
        workers();rule.onNodeWithTag("Nama pekerja baru").performScrollTo().performTextInput("New worker");rule.onNodeWithText("Simpan pekerja").performScrollTo().performClick()
        rule.waitUntil(10000) { posts.get()==1 && rule.onNodeWithTag("Nama pekerja baru").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty() }
        rule.onNodeWithText("Simpan pekerja").performScrollTo().performClick();rule.onNodeWithText("Isi nama 1–100 karakter").assertExists();assertEquals(1,posts.get())
    }
    @Test fun configDraftAndBaseVersionSurviveRecreationAndConflict() {
        failConfig.set(true);rule.onNodeWithText("Pengaturan").performClick();rule.onNodeWithText("Jadwal nutrisi").performScrollTo().performClick()
        rule.onNodeWithTag("Toleransi awal (menit)").performScrollTo().performTextReplacement("12")
        rule.onNodeWithTag("Toleransi awal (menit)").assertTextContains("12")
        rule.waitForIdle()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("Toleransi awal (menit)").performScrollTo().assertTextContains("12")
        rule.onNodeWithText("Simpan konfigurasi").performClick()
        rule.waitUntil(10000) { configBody.get()!=null && rule.onAllNodesWithText("Version conflict fixture").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(3,JSONObject(configBody.get()!!).getLong("baseVersion"))
        rule.onNodeWithTag("Toleransi awal (menit)").performScrollTo().assertTextContains("12")
    }
    @Test fun shiftEditKeepsKeyAndVersionAcrossRecreationThenCloses() {
        workers();rule.onNodeWithText("Edit",substring=false).performScrollTo().performClick()
        rule.onNodeWithTag("Jam selesai").performScrollTo().performTextReplacement("17:00")
        rule.onNodeWithTag("Jam selesai").assertTextContains("17:00")
        rule.waitForIdle()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("Jam selesai").performScrollTo().assertTextContains("17:00")
        rule.onNodeWithText("Simpan shift").performClick()
        rule.waitUntil(10000) { shiftBody.get()!=null && rule.onAllNodesWithText("Tambah shift").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(10,JSONObject(shiftBody.get()!!).getLong("baseVersion"))
        assertEquals("17:00",JSONObject(shiftBody.get()!!).getString("endTime"))
    }
    @Test fun documentPickerCancellationAfterRecreationIsExplicitAndRetryable() {
        rule.onNodeWithText("Riwayat").performClick();rule.onNodeWithText("Ekspor riwayat").performScrollTo().performClick()
        Thread.sleep(800)
        // ActivityScenario requires RESUMED; request recreation on its stopped activity
        // directly while the external picker owns the foreground.
        rule.activity.runOnUiThread { rule.activity.recreate() };Thread.sleep(800)
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        // The external picker can still own the window while recreation resumes asynchronously.
        rule.waitUntil(10000) { runCatching {rule.onAllNodesWithText("Riwayat nutrisi").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false) }
        rule.onNodeWithText("Ekspor riwayat").assertIsEnabled()
    }
    @Test fun workerRowsAreLazyAndFarWorkerIdentityRemainsInShiftEditor() {
        val session=SessionStore(instrumentation.targetContext)
        val vm=OwnerViewModel(OwnerRepository(ApiClient({server.url("/api/v1/").toString()},{null},true),session))
        val workers=(1L..500L).map {Worker(it,it,"Worker $it",true)}
        val shift=Shift(7,"fixture-large-list",500,listOf(1),"08:00","16:00","2026-10-10",null)
        rule.activity.setContent {InofarmTheme {WorkersScreen(OwnerState(signedIn=true,workers=workers,shifts=listOf(shift)),vm,{})}}
        assertTrue(rule.onAllNodes(hasText("Worker ",substring=true)).fetchSemanticsNodes().size<20)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Edit",substring=false))
        rule.onNodeWithText("Edit",substring=false).performClick()
        rule.onNodeWithText("Worker 500").assertExists()
        rule.onNodeWithTag("Jam mulai").assertTextContains("08:00")
    }
    @Test fun largeHistoryComposesOnlyVisibleRowsAndRetainsAllReasons() {
        val session=SessionStore(instrumentation.targetContext);val base=server.url("/api/v1/").toString();session.baseUrl=base
        val vm=OwnerViewModel(OwnerRepository(ApiClient({base},{null},true),session))
        val records=(1L..500L).map { NutrientRecord(it,null,"2026-10-10T00:00:00Z",PlantPhase.Vegetatif,900.0,ValidationStatus.Incomplete,listOf(Check("incomplete","clock","Clock invalid fixture"),Check("issue","ppm","PPM low fixture")),null,"time_unknown",null,null,null,false) }
        rule.activity.setContent { MaterialTheme { HistoryScreen(OwnerState(signedIn=true,history=records),vm,{}) } }
        assertTrue(rule.onAllNodes(hasText("Input manual ESP32")).fetchSemanticsNodes().size<20)
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        rule.onAllNodesWithText("Clock invalid fixture",substring=true).onFirst().assertExists();rule.onAllNodesWithText("PPM low fixture",substring=true).onFirst().assertExists()
    }
    @Test fun largeFontNarrowScreenAndStaticLoadingKeepControlsAndChartMeaning() {
        rule.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                MaterialTheme { Column(Modifier.width(280.dp).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
                    ResourceFeedback(ResourceState(loading=true),false)
                    DayPicker(setOf(1,7),{})
                    RangeFields("Fixture range",BoundDraft("1","2"),{})
                    val readings=metrics.associate { it.key to SensorValue(25.0,SensorQuality.Ok) }
                    Trend(listOf(Telemetry("2026-10-10T00:00:00Z","2026-10-10T00:01:00Z",readings)),emptyList())
                } }
            }
        }
        rule.onNodeWithText("Min").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Minimum").performScrollTo().assertIsDisplayed()
        rule.onNode(hasContentDescription("Tren Suhu air:",substring=true)).performScrollTo().assertExists()
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/inofarm-large-font-test.png").let { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
    }
}
