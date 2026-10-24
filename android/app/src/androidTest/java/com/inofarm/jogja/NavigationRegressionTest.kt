package com.inofarm.jogja

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.data.SessionStore
import com.inofarm.jogja.ui.OwnerViewModel
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

/** Runs MainActivity's real OwnerApp; use -PperformanceTest to isolate app/session storage. */
class NavigationRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var vm: OwnerViewModel
    private val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))

    @Before fun setup() {
        check(BuildConfig.APPLICATION_ID.endsWith(".performance")) { "Use isolated -PperformanceTest package" }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val body = when (request.requestUrl!!.encodedPath.substringAfter("/api/v1/")) {
                    "login" -> "{\"token\":\"navigation-fixture-only\"}"
                    "dashboard" -> "{\"connectionState\":\"never_seen\",\"device\":null,\"telemetry\":null,\"latestRecord\":null,\"activePhase\":null}"
                    "config" -> "{\"config\":null,\"activePhase\":null}"
                    "workers", "shifts" -> "{\"data\":[],\"last_page\":1}"
                    "history/export" -> "id,phase,manual_ppm\n"
                    else -> "{\"data\":[],\"next_cursor\":null}"
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        rule.runOnIdle {
            vm = ViewModelProvider(rule.activity)[OwnerViewModel::class.java]
        }
        rule.waitUntil(10000) { !vm.state.value.restoring }
        val base = server.url("/api/v1/").toString()
        rule.runOnIdle { vm.login(base, "fixture@example.invalid", "fixture-only") }
        rule.waitUntil(10000) { vm.state.value.dashboard != null && vm.state.value.config != null && !vm.state.value.resource("history").loading }
        rule.waitForIdle()
    }

    @After fun cleanup() {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        SessionStore(instrumentation.targetContext).clear()
        server.shutdown()
    }

    private fun nav(label: String) = rule.onNode(hasText(label) and hasClickAction() and isSelectable()).performClick()
    private fun segment(label: String) = rule.onNode(hasText(label) and isSelectable()).performScrollTo().performClick()
    private fun screenshot(name: String) {
        rule.waitForIdle()
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/nav-$name.png").let {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
    }
    private fun awaitFilter(expected: Map<String, String>) {
        rule.waitUntil(10000) { vm.state.value.appliedFilters == expected && !vm.state.value.resource("history").loading }
    }

    private fun closeKeyboard() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitForIdle()
    }

    private fun assertSelection(label: String) {
        rule.onNode(hasText(label) and isSelectable()).assertIsSelected()
    }

    private fun assertQuery(request: RecordedRequest, expected: Map<String, String>) {
        val url = request.requestUrl!!
        assertEquals(expected, url.queryParameterNames.filter { it != "limit" }.associateWith { url.queryParameter(it)!! })
    }

    private fun exportAndCancel(expected: Map<String, String>) {
        val previous = requests.count { it.requestUrl!!.encodedPath.endsWith("history/export") }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Ekspor riwayat"))
        rule.onNodeWithText("Ekspor riwayat").performScrollTo().performClick()
        rule.waitUntil(10000) { requests.count { it.requestUrl!!.encodedPath.endsWith("history/export") } > previous }
        assertQuery(requests.last { it.requestUrl!!.encodedPath.endsWith("history/export") }, expected)
        rule.waitUntil(10000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui") == true }
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        rule.waitUntil(10000) { rule.activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED }
        rule.onNodeWithText("Ekspor riwayat").assertIsEnabled()
    }

    @Test fun filterSelectionFollowsUserIntentAndKeepsQueryApplied() {
        nav("Riwayat")
        segment("Hari ini")
        val applied = mapOf("from" to today.toString(), "to" to today.toString())
        awaitFilter(applied)
        segment("Filter")
        screenshot("filter-open")
        rule.onNode(hasText("Filter") and isSelectable()).assertIsSelected()
        rule.onNode(hasText("Hari ini") and isSelectable()).assertIsNotSelected()
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").performScrollTo().assertIsDisplayed()
        assertEquals(applied, vm.state.value.appliedFilters)

        // Opening Filter again must not hide the currently selected panel.
        segment("Filter")
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").performScrollTo().assertIsDisplayed()
        val historyRequests = requests.count { it.requestUrl!!.encodedPath.endsWith("/history") }
        val draftFrom = today.minusDays(3).toString()
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").performScrollTo().performTextReplacement(draftFrom)
        closeKeyboard()
        rule.onNode(hasText("Valid") and hasClickAction()).performScrollTo().performClick()
        assertEquals(applied, vm.state.value.appliedFilters)
        assertEquals(historyRequests, requests.count { it.requestUrl!!.encodedPath.endsWith("/history") })
        exportAndCancel(applied)
        nav("Dashboard"); nav("Riwayat")
        assertSelection("Filter")
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").performScrollTo().assertTextContains(draftFrom)
        rule.activityRule.scenario.recreate()
        assertSelection("Filter")
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").performScrollTo().assertTextContains(draftFrom)
        rule.onNode(hasText("Valid") and hasClickAction()).assertIsSelected()
        rule.onNodeWithText("Terapkan").performScrollTo().performClick()
        val custom = applied + mapOf("from" to draftFrom, "status" to "valid")
        awaitFilter(custom)
        assertSelection("Filter")
        assertQuery(requests.last { it.requestUrl!!.encodedPath.endsWith("/history") }, custom)
        exportAndCancel(custom)

        segment("7 hari")
        val week = mapOf("from" to today.minusDays(6).toString(), "to" to today.toString(), "status" to "valid")
        awaitFilter(week)
        assertSelection("7 hari")
        rule.activityRule.scenario.recreate()
        assertSelection("7 hari")
        assertEquals(week, vm.state.value.appliedFilters)
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").assertDoesNotExist()
        segment("Filter")
        assertSelection("Filter")
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").performScrollTo().performTextReplacement("invalid-date")
        closeKeyboard()
        rule.onNodeWithText("Terapkan").performScrollTo().performClick()
        assertEquals(week, vm.state.value.appliedFilters)
        rule.onNodeWithText("Reset").performScrollTo().performClick()
        awaitFilter(emptyMap())
        assertSelection("Filter")
        rule.onNodeWithText("Dari tanggal (YYYY-MM-DD)").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithText("Sampai tanggal (YYYY-MM-DD)").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNode(hasText("Semua") and hasClickAction()).assertIsSelected()
        // A shortcut must not silently apply a draft status that was never submitted.
        rule.onNode(hasText("Ada masalah") and hasClickAction()).performScrollTo().performClick()
        segment("Hari ini")
        awaitFilter(applied)
        assertSelection("Hari ini")
        screenshot("filter-shortcut-final")
    }

    @Test fun dashboardShortcutReturnsToActualDashboardRepeatedly() {
        repeat(3) { index ->
            rule.onNodeWithTag("dashboardHero").performScrollTo().performClick()
            rule.onNodeWithText("Fase aktif tandon").assertExists()
            screenshot("shortcut-settings-$index")
            nav("Dashboard")
            screenshot("shortcut-return-$index")
            rule.onNode(hasText("Dashboard") and isSelectable()).assertIsSelected()
            rule.onNodeWithTag("dashboardHero").assertExists()
        }
    }

    @Test fun realOwnerAppLastContentRemainsReachablePortrait() {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        verifyLastContent()
    }

    @Test fun realOwnerAppLastContentRemainsReachableLandscape() {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        verifyLastContent()
    }

    @Test fun systemBackAndSettingsDraftHaveIndependentBehavior() {
        rule.onNodeWithTag("dashboardHero").performScrollTo().performClick()
        androidx.test.espresso.Espresso.pressBack()
        assertSelection("Dashboard")
        rule.onNodeWithTag("dashboardHero").assertExists()
        nav("Pengaturan")
        // Reselecting the same destination must not add a duplicate entry.
        nav("Pengaturan")
        rule.onNodeWithText("Jadwal nutrisi").performScrollTo().performClick()
        rule.onNodeWithTag("Toleransi awal (menit)").performScrollTo().performTextReplacement("12")
        closeKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText("Fase aktif tandon").assertExists()
        nav("Dashboard"); nav("Pengaturan")
        rule.onNodeWithText("Jadwal nutrisi").performScrollTo().performClick()
        rule.onNodeWithTag("Toleransi awal (menit)").performScrollTo().assertTextContains("12")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("Toleransi awal (menit)").performScrollTo().assertTextContains("12")
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText("Fase aktif tandon").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        assertSelection("Dashboard")
        rule.onNodeWithTag("dashboardHero").assertExists()
        assertFalse(requests.any { it.method == "PUT" })
    }

    @Test fun ownerEditorKeyboardKeepsFooterAboveImeAndCancelDoesNotSave() {
        nav("Pengaturan")
        rule.onNodeWithText("Jadwal nutrisi").performScrollTo().performClick()
        rule.onNodeWithTag("Toleransi akhir (menit)").performScrollTo().performClick().performTextReplacement("15")
        rule.waitUntil(10000) { rule.activity.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom > 0 }
        // System IME animation runs outside the Compose test clock. Check the settled
        // viewport and then scroll the focused field into it before taking evidence.
        Thread.sleep(350)
        rule.waitForIdle()
        rule.onNodeWithTag("Toleransi akhir (menit)").performScrollTo()
        rule.onNodeWithText("Simpan konfigurasi").assertIsDisplayed()
        rule.onNodeWithText("Batal").assertIsDisplayed()
        rule.onNodeWithTag("Toleransi akhir (menit)").assertIsDisplayed()
        screenshot("editor-keyboard")
        rule.onNodeWithTag("ownerNavigation").assertDoesNotExist()
        val imeBottom = rule.activity.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
        assertTrue("IME must actually be open", imeBottom > 0)
        val usableBottom = rule.activity.window.decorView.height - imeBottom
        val footer = rule.onNodeWithTag("formFooter").fetchSemanticsNode().boundsInRoot
        assertTrue("Footer $footer must be above IME $usableBottom", footer.bottom <= usableBottom + 1)
        val fieldBounds = rule.onNodeWithTag("Toleransi akhir (menit)").getUnclippedBoundsInRoot()
        val footerBounds = rule.onNodeWithTag("formFooter").getUnclippedBoundsInRoot()
        assertTrue("Focused field $fieldBounds must fit above footer $footerBounds", fieldBounds.bottom <= footerBounds.top)
        closeKeyboard()
        rule.onNodeWithText("Batal").performClick()
        rule.onNodeWithText("Fase aktif tandon").assertExists()
        assertFalse(requests.any { it.method == "PUT" })
    }

    private fun verifyLastContent() {
        // Swipe the actual viewport, not only the semantics scroll action.
        swipeToEnd()
        rule.onAllNodesWithText("Waktu tidak tersedia").onLast().assertIsDisplayed()
        // On a very short viewport the label can sit above its last value at scroll end.
        // It must also be reachable when scrolled into view.
        rule.onNodeWithText("Bacaan diterima server").performScrollTo()
        rule.onNodeWithText("Bacaan diterima server").assertIsDisplayed()
        val last = rule.onNodeWithText("Bacaan diterima server").fetchSemanticsNode().boundsInRoot
        val navTop = rule.onNode(hasText("Dashboard") and isSelectable()).fetchSemanticsNode().boundsInRoot.top
        screenshot("dashboard-bottom")
        assertTrue("Dashboard last content $last must end above navigation $navTop", last.bottom <= navTop)
        assertViewportAboveNavigation()
        nav("Pengaturan")
        swipeToEnd()
        rule.onNodeWithText("Keluar akun").assertIsDisplayed()
        rule.onNodeWithText("Refresh").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Refresh").performClick()
        rule.onNodeWithText("Keluar akun").performScrollTo().assertIsDisplayed()
        val logout = rule.onNodeWithText("Keluar akun").fetchSemanticsNode().boundsInRoot
        val settingsNavTop = rule.onNode(hasText("Pengaturan") and isSelectable()).fetchSemanticsNode().boundsInRoot.top
        screenshot("settings-bottom")
        assertTrue("Last settings control $logout must end above navigation $settingsNavTop", logout.bottom <= settingsNavTop)
        assertViewportAboveNavigation()
        // Every hub entry remains reachable and usable even with enlarged fonts/short screens.
        listOf("Ambang sensor", "Usia data & perangkat", "Rentang PPM per fase", "Jadwal nutrisi").forEach { label ->
            rule.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
            rule.onNodeWithText("Batal").assertIsDisplayed().performClick()
            rule.onNodeWithText("Fase aktif tandon").assertExists()
        }
    }

    private fun swipeToEnd() {
        repeat(80) {
            val range = rule.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            if (range.value() >= range.maxValue() - 1) return
            rule.onNode(hasScrollAction()).performTouchInput { swipeUp(durationMillis = 150) }
        }
        fail("Touch scrolling did not reach the end of the actual viewport")
    }

    private fun assertViewportAboveNavigation() {
        val viewport = rule.onNodeWithTag("ownerContent").fetchSemanticsNode().boundsInRoot
        val navigation = rule.onNodeWithTag("ownerNavigation").fetchSemanticsNode().boundsInRoot
        android.util.Log.i("NavigationRegression", "viewport=$viewport; navigation=$navigation; fontScale=${rule.activity.resources.configuration.fontScale}; orientation=${rule.activity.resources.configuration.orientation}")
        assertTrue("Viewport $viewport overlaps actual navigation $navigation", viewport.bottom <= navigation.top + 1)
        assertTrue("Content must have positive usable height", viewport.height > 0)
    }
}
