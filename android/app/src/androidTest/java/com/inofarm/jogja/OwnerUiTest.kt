package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Rule
import org.junit.Test

class OwnerUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val server=MockWebServer()
    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("inofarm_session",0).edit().clear().commit()
        server.shutdown()
    }
    @Test fun emptyApiNavigationRotationAndEditorStayHonest() {
        server.start()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val body=when(request.requestUrl?.encodedPath?.substringAfter("/api/v1/")) {
                    "login" -> "{\"token\":\"fixture-only-token\"}"
                    "dashboard" -> "{\"connectionState\":\"never_seen\",\"device\":null,\"telemetry\":null,\"latestRecord\":null,\"activePhase\":null}"
                    "config" -> "{\"config\":null,\"activePhase\":null}"
                    "workers","shifts" -> "{\"data\":[],\"last_page\":1}"
                    else -> "{\"data\":[],\"next_cursor\":null}"
                }
                return MockResponse().setHeader("Content-Type","application/json").setBody(body)
            }
        }
        rule.onNodeWithTag("apiUrl").assertDoesNotExist()
        rule.onNodeWithTag("connectionSettings").performScrollTo().performClick()
        rule.onNodeWithTag("apiUrl").performScrollTo().performTextReplacement(server.url("/api/v1/").toString())
        rule.onNodeWithTag("connectionSettings").performScrollTo().performClick()
        rule.onNodeWithTag("apiUrl").assertDoesNotExist()
        rule.onNodeWithTag("email").performTextInput("fixture@example.invalid")
        rule.onNodeWithTag("password").performTextInput("fixture-only-password")
        rule.onNodeWithTag("login").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("dashboardHero").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Fase aktif · Belum ditetapkan").assertExists()
        rule.onAllNodesWithText("Belum ada bacaan").assertCountEquals(4)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/inofarm-dashboard-test.png").let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        rule.onNodeWithText("Riwayat").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("Tidak ada catatan pada filter aktif.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Tidak ada catatan pada filter aktif.").assertExists()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Riwayat nutrisi").assertExists()
        rule.onNodeWithText("Pengaturan").performClick()
        rule.onNodeWithText("Kelola pekerja & shift").performScrollTo().performClick()
        rule.onNodeWithText("Belum ada pekerja").assertExists()
        rule.onNodeWithContentDescription("Kembali ke Pengaturan").performClick()
        rule.onNodeWithText("Fase aktif tandon").assertExists()
    }
}
