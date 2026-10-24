package com.inofarm.jogja

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicBoolean

/** Fixtures exist only in instrumentation; none are fallback data in the APK. */
class RedesignUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val server = MockWebServer()
    private val offline = AtomicBoolean(false)
    private val store = ViewModelStore()
    private lateinit var vm: OwnerViewModel
    private val readings = metrics.associate { metric -> metric.key to SensorValue(
        when (metric.key) { "waterTemperatureC" -> 26.7; "airTemperatureC" -> 29.4; "relativeHumidityPercent" -> 74.6; else -> null },
        if (metric.key == "solutionPh") SensorQuality.Uncalibrated else SensorQuality.Ok) }
    private val record = NutrientRecord(41, "2026-10-11T02:18:00Z", "2026-10-11T02:20:00Z",
        PlantPhase.Vegetatif, 923.0, ValidationStatus.Incomplete,
        listOf(Check("warning", "phase_mismatch", "Fixture: fase pekerja berbeda dari fase owner."),
            Check("incomplete", "missing_bounds", "Fixture: parameter sensor belum lengkap.")),
        "Pekerja fixture", "scheduled", PlantPhase.Generatif, 3, 7,
        evaluatedAt = "2026-10-11T02:20:00Z")

    @Before fun setup() {
        SessionStore(instrumentation.targetContext).clear()
        rule.activityRule.scenario.recreate()
        server.start()
        val session = SessionStore(instrumentation.targetContext)
        session.baseUrl = server.url("/api/v1/").toString()
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(store, object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T =
                    OwnerViewModel(OwnerRepository(ApiClient({session.baseUrl},{session.readToken()},true),session)) as T
            })[OwnerViewModel::class.java]
        }
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { store.clear() }
        SessionStore(instrumentation.targetContext).clear()
        server.shutdown()
    }
    private fun screenshot(name: String, hideKeyboard: Boolean = true) {
        if (hideKeyboard) androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        // Wait for the system IME compositor, which is outside the Compose test clock.
        Thread.sleep(250)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/redesign-$name.png").let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun noOverflow(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        if (results.isEmpty() || results.any { it.hasVisualOverflow || it.lineCount > 1 }) screenshot("navigation-overflow")
        assertTrue("No clipped text: $text; ${results.map { "size=${it.size},lines=${it.lineCount},overflow=${it.hasVisualOverflow}" }}",
            results.isNotEmpty() && results.none { it.hasVisualOverflow || it.lineCount > 1 })
    }
    private fun fixtureTelemetry(trusted: Boolean = true): JSONObject {
        val values = JSONObject(); val qualities = JSONObject()
        readings.forEach { (key,value) -> values.put(key,value.value ?: JSONObject.NULL);qualities.put(key,value.quality.wire) }
        return JSONObject().put("id",if(trusted) 50 else 51).put("clock_trusted",trusted)
            .put("received_at","2026-10-11T02:20:59Z").put("body",JSONObject().put("sensorSnapshot",
                JSONObject().put("capturedAt",if(trusted) "2026-10-11T02:20:40Z" else JSONObject.NULL).put("readings",values).put("quality",qualities)))
    }
    private fun fixtureRecord(): JSONObject = JSONObject().put("id",record.id).put("clock_trusted",true)
        .put("body",JSONObject().put("confirmedAt",record.sourceAt).put("measuredAt",JSONObject.NULL))
        .put("received_at",record.receivedAt).put("phase",record.phase.wire).put("manual_ppm",record.manualPpm)
        .put("config_version_id",3).put("phase_version_id",7)
        .put("assignment",JSONObject().put("status","scheduled").put("workerNameSnapshot",record.workerName))
        .put("validation_result",JSONObject().put("status","incomplete").put("evaluatedAt",record.evaluatedAt)
            .put("ownerPhaseSnapshot",JSONObject().put("phase","generatif"))
            .put("checks",JSONArray(record.checks.map { JSONObject().put("result",it.result).put("code",it.code).put("message",it.message) })))

    @Test fun completeFlowsKeepWarningsDetailsAndFilterDraftAcrossNavigation() {
        server.dispatcher = object: Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = MockResponse().setHeader("Content-Type","application/json")
                return when(request.requestUrl!!.encodedPath.substringAfter("/api/v1/")) {
                    "login" -> response.setBody("{\"token\":\"redesign-test-only\"}")
                    "dashboard" -> if(offline.get()) response.setResponseCode(503).setBody("{\"message\":\"Koneksi fixture terputus\"}") else response.setBody(JSONObject()
                        .put("connectionState","recent_contact").put("serverTime","2026-10-11T02:21:00Z")
                        .put("device",JSONObject().put("last_contact_at","2026-10-11T02:20:55Z").put("applied_config_version",2))
                        .put("activePhase",JSONObject().put("id",7).put("body",JSONObject().put("phase","generatif")))
                        .put("telemetry",fixtureTelemetry()).put("latestTelemetryReceipt",fixtureTelemetry(false))
                        .put("latestRecord",fixtureRecord()).put("latestRecordReceipt",fixtureRecord()).toString())
                    "config" -> response.setBody(JSONObject().put("config",JSONObject().put("id",3).put("body",ConfigDraft().json()))
                        .put("activePhase",JSONObject().put("id",7).put("body",JSONObject().put("phase","generatif"))).toString())
                    "history" -> response.setBody(JSONObject().put("data",JSONArray().put(fixtureRecord())).put("next_cursor",JSONObject.NULL).toString())
                    "telemetry" -> response.setBody(JSONObject().put("data",JSONArray().put(fixtureTelemetry())).put("next_cursor",JSONObject.NULL).toString())
                    "workers" -> response.setBody("{\"data\":[{\"id\":1,\"version\":1,\"name\":\"Pekerja fixture\",\"active\":true}],\"last_page\":1}")
                    "shifts" -> response.setBody("{\"data\":[{\"id\":1,\"shift_key\":\"shift-fixture\",\"worker_id\":1,\"body\":{\"days\":[1,2,3],\"startTime\":\"08:00\",\"endTime\":\"16:00\",\"startDate\":\"2026-10-11\",\"endDate\":null}}],\"last_page\":1}")
                    else -> response.setBody("{\"data\":[],\"last_page\":1}")
                }
            }
        }
        rule.activity.setContent { InofarmTheme { OwnerApp(vm) {} } }
        screenshot("login")
        if (rule.onAllNodesWithTag("apiUrl").fetchSemanticsNodes().isEmpty()) rule.onNodeWithTag("connectionSettings").performScrollTo().performClick()
        rule.onNodeWithTag("apiUrl").performScrollTo().performTextReplacement(server.url("/api/v1/").toString())
        rule.onNodeWithTag("email").performScrollTo().performTextInput("owner@example.invalid")
        rule.onNodeWithTag("password").performScrollTo().performTextInput("test-only")
        rule.onNodeWithTag("login").performScrollTo().performClick()
        rule.waitUntil(10000) { vm.state.value.dashboard != null && !vm.state.value.resource("dashboard").loading }
        rule.runOnIdle { vm.message("Fixture pengujian UI · bukan data greenhouse.") }
        rule.onNodeWithText("Belum dikalibrasi").assertExists()
        rule.onNodeWithText("Clock perangkat belum dipercaya pada kiriman terbaru. Bacaan terpercaya terakhir tetap ditampilkan.").assertExists()
        screenshot("dashboard")
        rule.onNodeWithText("Riwayat").performClick()
        rule.onNodeWithTag("record-41").performScrollTo()
        rule.onNodeWithText("Fixture: fase pekerja berbeda dari fase owner.",substring=true).assertExists()
        rule.onNodeWithText("Fixture: parameter sensor belum lengkap.",substring=true).assertExists()
        screenshot("history")
        rule.onNodeWithText("Lihat metadata").performScrollTo().performClick()
        rule.onNodeWithText("Fase owner saat catatan").performScrollTo().assertIsDisplayed()
        screenshot("history-details")
        rule.onNode(hasText("Filter") and hasClickAction()).performScrollTo().performClick()
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").performScrollTo().performTextInput("2026-10-01")
        androidx.test.espresso.Espresso.closeSoftKeyboard();rule.waitForIdle()
        rule.onNode(hasText("Dashboard") and hasClickAction()).performClick()
        rule.onNodeWithText("Riwayat").performClick()
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").performScrollTo().assertTextContains("2026-10-01")
        assertTrue(vm.state.value.appliedFilters.isEmpty())
        rule.onNodeWithText("Pengaturan").performClick()
        screenshot("settings")
        rule.onNodeWithText("Kelola pekerja & shift").performScrollTo().performClick()
        screenshot("workers")
        rule.onNode(hasText("Shift") and hasClickAction()).performClick();rule.onNodeWithText("Tambah shift").performScrollTo()
        screenshot("shifts")
        rule.onNodeWithContentDescription("Kembali ke Pengaturan").performScrollTo().performClick()
        rule.onNode(hasText("Dashboard") and hasClickAction()).performClick()
        offline.set(true); rule.runOnIdle { vm.refreshDashboard() }
        rule.waitUntil(10000) { vm.state.value.resource("dashboard").error != null }
        rule.onNodeWithText("Status koneksi belum dapat ditentukan").assertExists()
        rule.onNodeWithText("Kontak perangkat masih baru pada pemeriksaan server").assertDoesNotExist()
        screenshot("offline")
    }

    @Test fun sensorGridReflowsAndAllQualityLabelsRemainReadable() {
        fun render(width: Int, scale: Float) {
            rule.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                    InofarmTheme { Column(Modifier.width(width.dp).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) { SensorMetrics(readings) } }
                }
            }
        }
        render(420,1f)
        val a = rule.onNodeWithTag("metric-waterTemperatureC").fetchSemanticsNode().boundsInRoot
        val b = rule.onNodeWithTag("metric-solutionPh").fetchSemanticsNode().boundsInRoot
        assertEquals(a.top,b.top,1f);assertTrue(b.left>a.left)
        render(280,2f)
        val narrowA = rule.onNodeWithTag("metric-waterTemperatureC").fetchSemanticsNode().boundsInRoot
        val narrowB = rule.onNodeWithTag("metric-solutionPh").fetchSemanticsNode().boundsInRoot
        assertTrue(narrowB.top>=narrowA.bottom)
        rule.onNodeWithText("Belum dikalibrasi").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Kelembapan").performScrollTo().assertIsDisplayed()
        screenshot("font200-narrow")
    }

    @Test fun largeFontNavigationHasReadableLabelsAndFortyEightDpTargets() {
        var selected = "beranda"
        rule.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                InofarmTheme { Box(Modifier.width(280.dp).safeDrawingPadding()) { OwnerNavigation("beranda") { selected=it } } }
            }
        }
        listOf("Dashboard","Riwayat","Pengaturan").forEach { label ->
            noOverflow(label)
            val bounds = rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width>=48*rule.density.density)
            assertTrue(bounds.height>=48*rule.density.density)
        }
        rule.onNodeWithText("Dashboard").assertIsSelected()
        rule.onNodeWithText("Pengaturan").performClick()
        assertEquals("pengaturan",selected)
        screenshot("navigation-font200")
    }

    @Test fun fullEditorsAtTwoHundredPercentKeepLastActionsReachableWithKeyboard() {
        val configState = OwnerState(config=RemoteConfig(null,ConfigDraft().json(),null,null),
            resources=mapOf("config" to ResourceState(fetchedAt="2026-10-11T02:00:00Z")))
        rule.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                InofarmTheme { Box(Modifier.width(280.dp).fillMaxHeight().safeDrawingPadding()) { SettingsScreen(configState,vm,{},page="schedule") } }
            }
        }
        rule.onNodeWithTag("Toleransi awal (menit)").performScrollTo().performClick().performTextInput("12")
        screenshot("settings-keyboard-font200",hideKeyboard=false)
        rule.onNodeWithText("Simpan konfigurasi").assertIsDisplayed()
        rule.onNodeWithText("Batal").assertIsDisplayed()
        screenshot("settings-font200")
        rule.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                InofarmTheme { Box(Modifier.width(280.dp).fillMaxHeight().safeDrawingPadding()) {
                    WorkersScreen(OwnerState(workers=listOf(Worker(1,1,"Nama pekerja fixture yang panjang",true))),vm,{})
                } }
            }
        }
        rule.onNode(hasText("Shift") and hasClickAction()).performClick()
        rule.onNodeWithTag("Jam mulai").performScrollTo().performClick().performTextInput("08:00")
        rule.onNodeWithText("Simpan shift").assertIsDisplayed()
        screenshot("shifts-font200")
    }

    @Test fun landscapeKeepsDashboardAndNavigationWithinSystemInsets() {
        try {
            rule.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            rule.waitUntil(10000) { rule.activity.resources.configuration.screenWidthDp>rule.activity.resources.configuration.screenHeightDp }
            val dashboard=Dashboard(PlantPhase.Generatif,7,"unknown",null,null,
                Telemetry("2026-10-11T02:20:40Z","2026-10-11T02:20:59Z",readings),record)
            rule.activity.setContent { InofarmTheme { androidx.compose.material3.Scaffold(bottomBar={OwnerNavigation("beranda",navigate={})}) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { DashboardScreen(OwnerState(dashboard=dashboard)) }
            } } }
            rule.onNodeWithTag("metric-relativeHumidityPercent").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Pengaturan").assertIsDisplayed()
            screenshot("landscape")
        } finally {
            rule.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            rule.waitUntil(10000) { rule.activity.resources.configuration.screenWidthDp<rule.activity.resources.configuration.screenHeightDp }
        }
    }

    @Test fun offlineAndLoadingCannotClaimRecentContact() {
        val dashboard = Dashboard(PlantPhase.Vegetatif,1,"recent_contact","2026-10-11T02:20:55Z",null,
            Telemetry("2026-10-11T02:20:40Z","2026-10-11T02:20:59Z",readings),record,serverTime="2026-10-11T02:21:00Z")
        rule.activity.setContent { InofarmTheme { DashboardScreen(OwnerState(dashboard=dashboard,
            resources=mapOf("dashboard" to ResourceState(loading=true,lastKnown=true,fetchedAt="2026-10-11T02:20:00Z")))) } }
        rule.onNodeWithText("Status koneksi belum dapat ditentukan").assertExists()
        rule.onNodeWithText("Kontak perangkat masih baru pada pemeriksaan server").assertDoesNotExist()
        rule.onNodeWithText("Last-known · belum diperiksa ulang").assertExists()
    }
}
