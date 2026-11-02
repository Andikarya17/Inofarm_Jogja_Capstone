package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.network.FinanceApiFailure
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

/** Real Laravel/MySQL integration in two explicitly selected instrumentation processes. */
class FinanceBackendDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name:String)=File(context.getExternalFilesDir(null),name)
    private fun fixture():JSONObject { assertTrue(context.packageName.endsWith(".finance"));return JSONObject(file("finance-device-fixture.json").readText()).also {assertEquals("inofarm_test",it.getString("database"))} }
    private fun login(f:JSONObject,role:String) {
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("finance-logout").fetchSemanticsNodes().isNotEmpty()}
        if(rule.onAllNodesWithTag("finance-logout").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag("finance-logout").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-server").performScrollTo().performTextReplacement(f.getString("base_url"))
        rule.onNodeWithTag("finance-email").performScrollTo().performTextReplacement(f.getString("${role}_email"))
        rule.onNodeWithTag("finance-password").performScrollTo().performTextReplacement(f.getString("password"))
        hideKeyboard();rule.onNodeWithTag("finance-login").performScrollTo().performClick()
        rule.waitUntil(15000) {rule.onAllNodesWithTag("finance-gh").fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(15000) {rule.onAllNodesWithText("Data server sudah diperiksa. Tinjau antrean yang ditahan.").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun choose(tag:String,id:String) {rule.onNodeWithTag(tag).performScrollTo().performClick();rule.onNodeWithTag("$tag-option-$id").performScrollTo().performClick()}
    private fun hideKeyboard() {rule.runOnIdle {(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0)};rule.waitForIdle()}
    private fun runtime():FinanceRuntime {lateinit var runtime:FinanceRuntime;rule.runOnIdle {runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]};return runtime}

    @Test fun queueWithBackendOffline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="queue")
        val f=fixture();login(f,"worker");val a=f.getJSONArray("contexts").getJSONObject(0)
        rule.onNodeWithTag("finance-gh").performClick()
        rule.onNodeWithTag("finance-gh-option-${f.getJSONArray("contexts").getJSONObject(1).getString("greenhouse_id")}").assertDoesNotExist()
        rule.onNodeWithTag("finance-gh-option-${a.getString("greenhouse_id")}").performClick()
        choose("finance-season",a.getString("season_id"));choose("finance-group",a.getString("group_id"))
        file("finance-device-ready.txt").writeText(f.getString("fixture_id"))
        val deadline=System.nanoTime()+90_000_000_000L
        while(System.nanoTime()<deadline && (!file("finance-device-go-offline.txt").exists() || file("finance-device-go-offline.txt").readText().trim()!=f.getString("fixture_id"))) Thread.sleep(100)
        assertTrue("Host did not signal stopped test backend",file("finance-device-go-offline.txt").exists())
        val active=runtime().vault.current!!
        try {
            runBlocking { runtime().api.request(active.scope.server,active.token,"sync/status") }
            fail("Test backend is still reachable; offline precondition failed")
        } catch(expected:IOException) { /* Transport really unavailable, not an HTTP validation error. */ }
        rule.onNodeWithTag("finance-new").performScrollTo().performClick()
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement("DEVICE OFFLINE ${f.getString("fixture_id")}")
        hideKeyboard();rule.onNodeWithTag("finance-review").performScrollTo().performClick();rule.onNodeWithTag("finance-queue").performScrollTo().performClick()
        rule.onNodeWithTag("finance-tab-2").performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Menunggu pengiriman").fetchSemanticsNodes().isNotEmpty()}
        val runtime=runtime();val session=runtime.vault.current!!;val op=runtime.store.outbox(session.scope.key).single()
        assertEquals("QUEUED",op.state)
        file("finance-device-queued-proof.json").writeText(JSONObject().put("fixture_id",f.getString("fixture_id")).put("entity_id",op.entityId).put("operation_id",op.id).put("hash",op.hash).put("pid",android.os.Process.myPid()).toString())
    }
    @Test fun reconnectReplayAndManagerReview():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="verify")
        val f=fixture();val proof=JSONObject(file("finance-device-queued-proof.json").readText())
        assertNotEquals(proof.getInt("pid"),android.os.Process.myPid())
        rule.waitUntil(15000) {rule.onAllNodesWithTag("finance-tab-2").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-2").performClick()
        rule.waitUntil(20000) {rule.onAllNodesWithText("Diterima server").fetchSemanticsNodes().isNotEmpty()}
        val runtime=runtime();val worker=runtime.vault.current!!
        val record=runtime.api.request(worker.scope.server,worker.token,"activities/${proof.getString("entity_id")}")
        assertEquals(f.getString("worker_id"),record.getString("recorded_by"));assertEquals(1,record.getInt("version"))
        try {runtime.api.request(worker.scope.server,worker.token,"greenhouses/${f.getJSONArray("contexts").getJSONObject(1).getString("greenhouse_id")}");fail("Cross GH must be denied")} catch(error:FinanceApiFailure) {assertEquals(404,error.status)}
        val op=runtime.store.outbox(worker.scope.key).single();assertEquals(proof.getString("operation_id"),op.id);assertEquals(proof.getString("hash"),op.hash)
        val replay=runtime.api.request(worker.scope.server,worker.token,"sync/operations","POST",op.envelope)
        assertEquals(op.id,replay.getString("operation_id"));assertEquals(1,replay.getInt("version"))
        assertEquals(1,runtime.api.request(worker.scope.server,worker.token,"activities/${op.entityId}/audit").getJSONArray("data").length())
        rule.onNodeWithTag("finance-logout").performClick();login(f,"manager")
        rule.onNodeWithTag("finance-tab-2").performClick();rule.waitUntil(15000) {rule.onAllNodesWithText("DEVICE OFFLINE ${f.getString("fixture_id")}").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("DEVICE OFFLINE ${f.getString("fixture_id")}").performScrollTo().performClick()
        rule.onNodeWithText("Pencatat: Pekerja perangkat sintetis").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("finance-logout").performClick()
    }
}
