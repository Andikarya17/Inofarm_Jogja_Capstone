package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.data.SessionStore
import com.inofarm.jogja.ui.ConfigDraft
import com.inofarm.jogja.ui.OwnerViewModel
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicReference

/** Only this instrumentation APK contains demo records. Main APK never seeds these values. */
class LiquidGlassUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val server=MockWebServer()
    private val saved=AtomicReference<JSONObject?>(null)
    private val version=AtomicReference(3L)
    private val conflict=AtomicReference(false)
    private val workers=AtomicReference(false)
    private val workerBody=AtomicReference<JSONObject?>(null)
    private val shiftBody=AtomicReference<JSONObject?>(null)
    private val shifts=AtomicReference(false)
    private val offline=AtomicReference(false)
    private val paginated=AtomicReference(false)
    private fun event(): JSONObject = JSONObject().put("id",41).put("clock_trusted",true)
        .put("received_at","2026-10-12T01:31:00Z").put("phase","vegetatif").put("manual_ppm",980)
        .put("body",JSONObject().put("confirmedAt","2026-10-12T01:30:00Z").put("measuredAt",JSONObject.NULL))
        .put("config_version_id",JSONObject.NULL).put("phase_version_id",JSONObject.NULL)
        .put("assignment",JSONObject().put("status","unscheduled"))
        .put("validation_result",JSONObject().put("status","incomplete").put("evaluatedAt","2026-10-12T01:31:00Z")
            .put("checks",JSONArray().put(JSONObject().put("result","incomplete").put("code","missing_config").put("message","Parameter belum lengkap"))
                .put(JSONObject().put("result","incomplete").put("code","missing_schedule").put("message","Jadwal nutrisi belum diatur"))))
    private fun telemetry(): JSONObject {
        val keys=listOf("waterTemperatureC","solutionPh","airTemperatureC","relativeHumidityPercent")
        return JSONObject().put("id",42).put("clock_trusted",true).put("received_at","2026-10-12T01:40:00Z")
            .put("body",JSONObject().put("sensorSnapshot",JSONObject().put("capturedAt","2026-10-12T01:39:00Z")
                .put("readings",JSONObject(keys.zip(listOf(25.6,6.2,29.1,72.0)).toMap())).put("quality",JSONObject(keys.associateWith {"ok"}))))
    }
    @Before fun setup() {
        SessionStore(instrumentation.targetContext).clear();rule.activityRule.scenario.recreate();server.start()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val path=request.requestUrl!!.encodedPath.substringAfter("/api/v1/")
                val r=MockResponse().setHeader("Content-Type","application/json")
                if(offline.get() && path=="dashboard") return r.setResponseCode(503).setBody("{\"message\":\"Koneksi fixture terputus\"}")
                return when {
                    path=="login" -> r.setBody("{\"token\":\"liquid-test-only\"}")
                    path=="dashboard" -> r.setBody(JSONObject().put("connectionState","recent_contact").put("serverTime","2026-10-12T01:40:00Z")
                        .put("device",JSONObject().put("last_contact_at","2026-10-12T01:40:00Z"))
                        .put("activePhase",JSONObject().put("id",1).put("body",JSONObject().put("phase","vegetatif")))
                        .put("telemetry",telemetry()).put("latestRecord",event()).put("latestRecordReceipt",event()).toString())
                    path=="config" && request.method=="PUT" -> {
                        saved.set(JSONObject(request.body.readUtf8()))
                        if(conflict.get()) r.setResponseCode(409).setBody("{\"message\":\"Konflik versi fixture\"}")
                        else {version.set(version.get()+1);r.setBody(JSONObject().put("config",JSONObject().put("id",version.get()).put("body",saved.get()!!.getJSONObject("config"))).toString())}
                    }
                    path=="config" -> r.setBody(JSONObject().put("config",JSONObject().put("id",version.get()).put("body",saved.get()?.getJSONObject("config") ?: ConfigDraft().json())).put("activePhase",JSONObject.NULL).toString())
                    path=="history/export" -> r.setBody("id,ppm\n41,980")
                    path=="history" -> {
                        val next=request.requestUrl!!.queryParameter("cursor")!=null
                        r.setBody(JSONObject().put("data",JSONArray().put(if(next) event().put("id",43) else event())).put("next_cursor",if(paginated.get() && !next) "fixture-next" else JSONObject.NULL).toString())
                    }
                    path=="telemetry" -> r.setBody(JSONObject().put("data",JSONArray().put(telemetry())).put("next_cursor",JSONObject.NULL).toString())
                    path=="workers" && request.method=="POST" -> {workerBody.set(JSONObject(request.body.readUtf8()));workers.set(true);r.setBody("{\"id\":1,\"version\":1,\"name\":\"Pekerja uji\",\"active\":true}")}
                    path=="workers" -> r.setBody(if(workers.get()) "{\"data\":[{\"id\":1,\"version\":1,\"name\":\"Pekerja uji\",\"active\":true}],\"last_page\":1}" else "{\"data\":[],\"last_page\":1}")
                    path=="shifts" && request.method=="POST" -> {shiftBody.set(JSONObject(request.body.readUtf8()));shifts.set(true);r.setBody(JSONObject().put("id",1).put("shift_key","test-shift").put("worker_id",1).put("body",shiftBody.get()).toString())}
                    path=="shifts" -> r.setBody(JSONObject().put("data",if(shifts.get()) JSONArray().put(JSONObject().put("id",1).put("shift_key","test-shift").put("worker_id",1).put("body",shiftBody.get())) else JSONArray()).put("last_page",1).toString())
                    else -> r.setBody("{}")
                }
            }
        }
        if (rule.onAllNodesWithTag("apiUrl").fetchSemanticsNodes().isEmpty()) rule.onNodeWithTag("connectionSettings").performScrollTo().performClick()
        rule.onNodeWithTag("apiUrl").performScrollTo().performTextReplacement(server.url("/api/v1/").toString())
        rule.onNodeWithTag("email").performScrollTo().performTextInput("owner@example.invalid")
        rule.onNodeWithTag("password").performScrollTo().performTextInput("test-only")
        rule.onNodeWithTag("password").assertTextContains("test-only")
        hideKeyboard()
        rule.onNodeWithTag("login").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("dashboardHero").fetchSemanticsNodes().isNotEmpty()}
        rule.waitForIdle()
    }
    @After fun cleanup() {SessionStore(instrumentation.targetContext).clear();server.shutdown()}
    private fun nav(label: String) {rule.onNode(hasText(label) and hasClickAction()).performClick();rule.waitForIdle()}
    private fun back() {
        hideKeyboard();rule.onNodeWithContentDescription("Kembali ke Pengaturan").performScrollTo().assertIsDisplayed().performClick();rule.waitForIdle()
    }
    private fun hideKeyboard() {
        rule.runOnIdle { (rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0) }
        Thread.sleep(300);rule.waitForIdle()
    }
    private fun shot(name:String,keyboard:Boolean=false) {
        if(!keyboard) hideKeyboard()
        rule.waitForIdle();Thread.sleep(350)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/liquid-$name.png").let {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {f->f.readBytes()}}
    }
    private fun menu(text: String) {
        rule.waitUntil(10000) {rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText(text).performScrollTo().performClick();rule.waitForIdle()
    }
    @Test fun nineReferenceViewsAndRealNavigation() {
        shot("dashboard")
        nav("Riwayat");nav("Hari ini");shot("riwayat")
        rule.onNodeWithText("Parameter belum lengkap",substring=true).assertExists()
        rule.onNodeWithText("Jadwal nutrisi belum diatur",substring=true).assertExists()
        nav("Pengaturan");shot("pengaturan")
        menu("Kelola pekerja & shift");shot("pekerja-shift")
        nav("Shift");rule.onNodeWithText("Simpan shift").assertIsNotEnabled();shot("form-shift")
        nav("Pekerja");back()
        menu("Jadwal nutrisi");shot("jadwal-nutrisi");back()
        menu("Rentang PPM per fase");shot("rentang-ppm");back()
        menu("Ambang sensor");shot("ambang-sensor");back()
        menu("Usia data & perangkat");shot("usia-perangkat");back()
        nav("Dashboard");rule.onNodeWithTag("dashboardHero").assertExists()
    }
    @Test fun sharedDraftCancelConflictMatchingAckAndRecreation() {
        nav("Pengaturan");menu("Usia data & perangkat")
        rule.onNodeWithTag("Interval kirim monitoring").performScrollTo().performTextReplacement("45")
        rule.waitForIdle();rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("Interval kirim monitoring").performScrollTo().assertTextContains("45")
        back();menu("Rentang PPM per fase")
        rule.onNodeWithTag("Minimum (ppm)").performScrollTo().performTextReplacement("800")
        rule.onNodeWithTag("Maksimum (ppm)").performScrollTo().performTextReplacement("1100")
        nav("Generatif");rule.onNodeWithTag("Minimum (ppm)").assertTextContains("")
        nav("Vegetatif");rule.onNodeWithTag("Minimum (ppm)").assertTextContains("800")
        conflict.set(true);nav("Simpan konfigurasi")
        rule.waitUntil(10000) {saved.get()!=null};assertEquals(3,saved.get()!!.getLong("baseVersion"))
        rule.waitUntil(10000) {rule.onAllNodesWithText("Konflik versi fixture").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Konflik versi fixture").assertIsDisplayed()
        assertEquals(45,saved.get()!!.getJSONObject("config").getInt("telemetryIntervalSeconds"))
        assertEquals(800.0,saved.get()!!.getJSONObject("config").getJSONObject("ppmRanges").getJSONObject("vegetatif").getDouble("min"),0.0)
        rule.onNodeWithTag("Minimum (ppm)").assertTextContains("800")
        conflict.set(false);saved.set(null);nav("Simpan konfigurasi")
        lateinit var savedVm:OwnerViewModel
        rule.runOnIdle {savedVm=androidx.lifecycle.ViewModelProvider(rule.activity)[OwnerViewModel::class.java]}
        rule.waitUntil(10000) {version.get()==4L && savedVm.state.value.config?.version==4L && !savedVm.state.value.action("config").running};rule.waitForIdle()
        back();menu("Ambang sensor")
        rule.onAllNodesWithTag("Minimum").onFirst().performScrollTo().performTextReplacement("22")
        nav("Batal");menu("Ambang sensor")
        rule.onAllNodesWithTag("Minimum").onFirst().assertTextContains("")
        nav("Simpan konfigurasi");rule.waitUntil(10000) {version.get()==5L}
        assertEquals(4,saved.get()!!.getLong("baseVersion"))
        assertEquals(45,saved.get()!!.getJSONObject("config").getInt("telemetryIntervalSeconds"))
        assertFalse(saved.get()!!.getJSONObject("config").getJSONObject("sensorBounds").has("waterTemperatureC"))
    }
    @Test fun workerCreateAndOvernightShiftDraft() {
        nav("Pengaturan");menu("Kelola pekerja & shift")
        rule.onNodeWithTag("Nama pekerja baru").performScrollTo().performTextInput("Pekerja uji")
        hideKeyboard();rule.onNodeWithText("Simpan pekerja").performScrollTo().performClick()
        rule.waitUntil(10000) {workerBody.get()!=null};rule.waitForIdle()
        nav("Shift");nav("Pilih pekerja");nav("Pekerja uji")
        nav("Sen")
        rule.onNodeWithTag("Jam mulai").performScrollTo().performTextInput("22:00")
        rule.onNodeWithTag("Jam selesai").performScrollTo().performTextInput("06:00")
        rule.onNodeWithTag("Tanggal mulai").performScrollTo().performTextInput("2026-10-12")
        rule.waitForIdle();rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("Jam selesai").performScrollTo().assertTextContains("06:00")
        hideKeyboard()
        rule.onNodeWithContentDescription("Pilih tanggal untuk Tanggal akhir · opsional").performScrollTo().performClick()
        rule.onNodeWithText("Pilih tanggal").assertExists()
        rule.onNode(hasText("Batal") and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithTag("Tanggal akhir · opsional").assertTextContains("")
        shot("shift-keyboard",true)
        hideKeyboard();nav("Simpan shift")
        rule.waitUntil(10000) {shiftBody.get()!=null}
        assertEquals("22:00",shiftBody.get()!!.getString("startTime"));assertEquals("06:00",shiftBody.get()!!.getString("endTime"))
        assertTrue(shiftBody.get()!!.isNull("endDate"))
    }
    @Test fun navigationMotionFrameSample() {
        fun shell(command:String):String = instrumentation.uiAutomation.executeShellCommand(command).let {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {f->f.readBytes().toString(Charsets.UTF_8)}}
        shell("dumpsys gfxinfo com.inofarm.jogja reset")
        rule.mainClock.autoAdvance=false
        try {
            repeat(4) {
                listOf("Riwayat","Pengaturan","Dashboard").forEach {label ->
                    nav(label)
                    repeat(24) {rule.mainClock.advanceTimeByFrame();Thread.sleep(16)}
                }
            }
        } finally {rule.mainClock.autoAdvance=true}
        val result=shell("dumpsys gfxinfo com.inofarm.jogja framestats")
        val sample=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"liquid-frame-sample.txt")
        sample.writeText(result)
        shell("cp ${sample.absolutePath} /sdcard/Download/liquid-frame-sample.txt")
        shot("motion-settled")
    }
    @Test fun systemBackRetainsDraftAndPaginationIsReachable() {
        nav("Pengaturan");menu("Jadwal nutrisi")
        rule.onNodeWithTag("Toleransi awal (menit)").performScrollTo().performTextInput("9")
        hideKeyboard()
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        Thread.sleep(350);rule.waitForIdle()
        menu("Jadwal nutrisi")
        rule.onNodeWithTag("Toleransi awal (menit)").performScrollTo().assertTextContains("9")
        back();paginated.set(true);nav("Riwayat");nav("7 hari")
        lateinit var vm: com.inofarm.jogja.ui.OwnerViewModel
        rule.runOnIdle {vm=androidx.lifecycle.ViewModelProvider(rule.activity)[com.inofarm.jogja.ui.OwnerViewModel::class.java]}
        rule.waitUntil(10000) {vm.state.value.nextCursor=="fixture-next"}
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Muat berikutnya"))
        rule.onNodeWithText("Muat berikutnya").performScrollTo().performClick()
        rule.waitUntil(10000) {vm.state.value.history.size==2 && vm.state.value.nextCursor==null}
        assertEquals(listOf(41L,43L),vm.state.value.history.map {it.id})
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Seluruh halaman pada filter ini sudah dimuat."))
        rule.onNodeWithText("Seluruh halaman pada filter ini sudah dimuat.").assertIsDisplayed()
        rule.onNodeWithText("Ekspor riwayat").performScrollTo().assertIsEnabled()
    }
}
