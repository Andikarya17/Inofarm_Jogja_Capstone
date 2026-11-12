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
import java.util.UUID

/** Real Laravel/MySQL integration in explicitly selected isolated instrumentation processes. */
class FinanceAssetBackendDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name:String)=File(context.getExternalFilesDir(null),name)
    private fun fixture():JSONObject { assertTrue(context.packageName.endsWith(".finance"));return JSONObject(file("finance-asset-fixture.json").readText()).also {assertEquals("inofarm_test",it.getString("database"))} }
    private fun login(f:JSONObject,role:String) {
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty()}
        if(rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty()) rule.logoutFromAccount()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        rule.configureFinanceEndpoint(f.getString("base_url"))
        rule.onNodeWithTag("finance-email").performScrollTo().performTextReplacement(f.getString("${role}_email"))
        rule.onNodeWithTag("finance-password").performScrollTo().performTextReplacement(f.getString("password"))
        hideKeyboard()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().singleOrNull()?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)==false}
        rule.onNodeWithTag("finance-login").performScrollTo().assertIsEnabled().performClick()
        try {rule.waitUntil(15000) {rule.onAllNodesWithTag(if (role == "manager") "manager-add-greenhouse" else "finance-context-open").fetchSemanticsNodes().isNotEmpty()}}
        catch(error:Exception) {
            lateinit var auth:com.inofarm.jogja.feature.auth.AuthViewModel;rule.runOnIdle {auth=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.auth.AuthViewModel::class.java]}
            throw AssertionError("Login transition: loading=${auth.state.value.loading}, hasSession=${auth.state.value.session!=null}, reason=${auth.state.value.error}",error)
        }
        rule.waitForFinanceMasterPull()
    }
    private fun choose(tag:String,id:String) {val selector=rule.onNodeWithTag(tag);selector.performScrollTo().performClick();rule.onNodeWithTag("$tag-option-$id").performScrollTo().performClick()}
    private fun hideKeyboard() {rule.runOnIdle {(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0)};rule.waitForIdle()}
    private fun runtime():FinanceRuntime {lateinit var runtime:FinanceRuntime;rule.runOnIdle {runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]};return runtime}

    private fun barrier(f:JSONObject,phase:String) {
        file("finance-asset-$phase-ready.txt").writeText(f.getString("fixture_id"))
        val model=runtime();val session=model.vault.current!!;var offline=false
        repeat(45) {if(!offline) {try {runBlocking {model.api.request(session.scope.server,session.token,"sync/status")};Thread.sleep(500)} catch(expected:IOException) {offline=true}}}
        assertTrue("The owned API must actually be offline before saving",offline)
    }
    private fun fields(values:List<Pair<String,String>>) {values.forEach {(tag,value)->rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)};hideKeyboard()}
    private fun remember(proof:JSONObject,kind:String) {
        val model=runtime();val session=model.vault.current!!
        rule.onNodeWithTag("asset-review").scrollIfScrollable().performClick();rule.onNodeWithTag("asset-queue").scrollIfScrollable().performClick()
        rule.waitUntil(15000) {model.store.outbox(session.scope.key).any {it.envelope.optString("entity_type")==kind}}
        val op=model.store.outbox(session.scope.key).single {it.envelope.optString("entity_type")==kind}
        assertEquals("QUEUED",op.state)
        proof.put(kind,JSONObject().put("pid",android.os.Process.myPid()).put("boot_count",android.provider.Settings.Global.getInt(context.contentResolver,"boot_count",-1)).put("id",op.entityId).put("op",op.id).put("hash",op.hash))
        file("finance-asset-proof.json").writeText(proof.toString())
    }
    private suspend fun replay(proof:JSONObject,kind:String,path:String):String {
        val saved=proof.getJSONObject(kind)
        if(InstrumentationRegistry.getArguments().getString("restartMode")=="os-reboot") {val boot=android.provider.Settings.Global.getInt(context.contentResolver,"boot_count",-1);assertTrue("A real OS reboot must have occurred",saved.getInt("boot_count")>=0 && boot>saved.getInt("boot_count"))}
        else assertNotEquals(saved.getInt("pid"),android.os.Process.myPid())
        val model=runtime()
        rule.waitUntil(15000) {model.vault.current!=null};val session=model.vault.current!!;val id=saved.getString("id")
        rule.waitUntil(20000) {model.store.record(session.scope.key,id)?.syncState==com.inofarm.jogja.core.contracts.SyncState.ACCEPTED && model.store.cachedOne(session.scope.key,kind,id)!=null}
        val op=model.store.outbox(session.scope.key).single {it.id==saved.getString("op")};assertEquals(saved.getString("hash"),op.hash)
        val receipt=model.api.request(session.scope.server,session.token,path,"POST",op.envelope)
        val second=model.api.request(session.scope.server,session.token,path,"POST",op.envelope)
        assertEquals(com.inofarm.jogja.core.contracts.WireContract.hash(receipt),com.inofarm.jogja.core.contracts.WireContract.hash(second));assertEquals(1,receipt.getInt("version"))
        return id
    }
    @Test fun queueAssetOffline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="asset")
        val f=fixture();login(f,"manager");rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",f.getString("greenhouse_id"));rule.clickFinanceTool("asset-new");choose("asset-expense",f.getString("expense_id"))
        fields(listOf("asset-name" to "Pompa perangkat sintetis","asset-cost" to "100,00","asset-residual" to "0","asset-life" to "3","asset-start" to "2026-01-01","asset-basis" to "Pemakaian sintetis eksplisit"))
        barrier(f,"asset");remember(JSONObject(),"asset")
    }
    @Test fun replayAssetThenQueueDepreciationOffline():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="allocation")
        val f=fixture();val proof=JSONObject(file("finance-asset-proof.json").readText());val id=replay(proof,"asset","assets")
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",f.getString("greenhouse_id"));rule.clickFinanceTool("depreciation-new");choose("asset-source",id);choose("asset-season",f.getString("season_id"))
        fields(listOf("asset-usage-year" to "1","asset-period-start" to "2026-01-01","asset-period-end" to "2027-01-01","asset-amount" to "33,33","asset-basis" to "Satu pemakaian sintetis, pembagian manual"))
        rule.onNodeWithTag("asset-new-usage").performScrollTo().performClick();barrier(f,"allocation");remember(proof,"depreciation_allocation")
    }
    @Test fun replayDepreciationAuditCancelAndWorkerScope():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="verify")
        val f=fixture();val proof=JSONObject(file("finance-asset-proof.json").readText());replay(proof,"depreciation_allocation","depreciation-allocations")
        val model=runtime();val session=model.vault.current!!;rule.onNodeWithTag("finance-tab-2").performClick()
        for(kind in listOf("depreciation_allocation","asset")) {
            val id=proof.getJSONObject(kind).getString("id")
            choose("expense-history-kind",kind)
            rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("history-audit").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithTag("history-audit").performScrollTo().performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Pencatatan · versi 1").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithTag("history-cancel").performScrollTo().performClick();rule.onNodeWithTag("history-action-reason").performTextReplacement("Pembatalan uji aset ${f.getString("fixture_id")}");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
            rule.waitUntil(20000) {model.store.record(session.scope.key,id)?.serverVersion==2L && model.store.record(session.scope.key,id)?.body?.optString("record_state")=="cancelled"}
            rule.runOnUiThread {rule.activity.onBackPressedDispatcher.onBackPressed()};rule.waitForIdle()
        }
        login(f,"worker");val worker=model.vault.current!!
        for(path in listOf("assets","depreciation-allocations")) {try {model.api.request(worker.scope.server,worker.token,path);fail("Worker cannot read finance") } catch(error:FinanceApiFailure) {assertEquals(403,error.status)}}
        for(kind in listOf("asset","depreciation_allocation")) {try {model.store.cached(worker.scope.key,kind);fail("Worker cache access must be denied")} catch(expected:IllegalArgumentException) {assertTrue(expected.message!!.contains("pengelola"))}}
        rule.onNodeWithTag("finance-tab-2").assertDoesNotExist()
        rule.logoutFromAccount()
    }
}
