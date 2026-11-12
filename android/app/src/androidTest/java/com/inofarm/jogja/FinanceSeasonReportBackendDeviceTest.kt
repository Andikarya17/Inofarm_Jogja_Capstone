package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.feature.reports.SeasonReportViewModel
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
class FinanceSeasonReportBackendDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name:String)=File(context.getExternalFilesDir(null),name)
    private fun fixture(): JSONObject {
        assertTrue(context.packageName.endsWith(".finance") || context.packageName.endsWith(".finance.performance"))
        val encoded = InstrumentationRegistry.getArguments().getString("reportFixture")
        val source = encoded?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP).toString(Charsets.UTF_8) }
            ?: file("finance-season-report-fixture.json").readText()
        return JSONObject(source).also { assertEquals("inofarm_test", it.getString("database")) }
    }
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
        file("finance-season-report-$phase-ready.txt").writeText(f.getString("fixture_id"))
        val model=runtime();val session=model.vault.current!!;var offline=false
        repeat(45) {if(!offline) {try {runBlocking {model.api.request(session.scope.server,session.token,"sync/status")};Thread.sleep(500)} catch(expected:IOException) {offline=true}}}
        assertTrue("The owned API must actually be offline before saving",offline)
    }
    private fun reports():SeasonReportViewModel {lateinit var model:SeasonReportViewModel;rule.runOnIdle {model=ViewModelProvider(rule.activity)[SeasonReportViewModel::class.java]};return model}
    private fun scroll(tag:String) {rule.onNodeWithTag("report-list").performScrollToNode(hasTestTag(tag))}
    private fun click(tag:String) {scroll(tag);rule.onNodeWithTag(tag).performClick()}
    private fun open(f:JSONObject) {
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",f.getString("greenhouse_id"));rule.clickFinanceTool("report-open")
        rule.waitUntil(10000) {reports().state.value.active}
    }
    private fun fill(f:JSONObject) {
        click("report-new");choose("report-season",f.getString("season_id"));choose("report-costs","complete");choose("report-sales","complete");choose("report-state","provisional")
        scroll("report-pending");rule.onNodeWithTag("report-pending").performTextReplacement("0")
        scroll("report-note");rule.onNodeWithTag("report-note").performTextReplacement("Tinjauan perangkat sintetis "+f.getString("fixture_id"));hideKeyboard()
        click("report-preview");rule.waitUntil(15000) {!reports().state.value.busy && reports().state.value.draft?.previewText!=null}
    }
    private fun remember(proof:JSONObject,key:String) {
        val model=runtime();val session=model.vault.current!!;val draft=reports().state.value.draft!!
        click("report-queue");rule.waitUntil(15000) {model.store.outbox(session.scope.key).any {it.entityId==draft.id && it.state=="QUEUED"}}
        val op=model.store.outbox(session.scope.key).single {it.entityId==draft.id && it.state=="QUEUED"}
        proof.put(key,JSONObject().put("pid",android.os.Process.myPid()).put("boot_count",android.provider.Settings.Global.getInt(context.contentResolver,"boot_count",-1))
            .put("id",op.entityId).put("op",op.id).put("hash",op.hash).put("sources_hash",op.envelope.getJSONObject("payload").getString("expected_sources_hash")))
        file("finance-season-report-proof.json").writeText(proof.toString())
    }
    private suspend fun replay(proof:JSONObject,key:String):JSONObject {
        val saved=proof.getJSONObject(key)
        if(InstrumentationRegistry.getArguments().getString("restartMode")=="os-reboot") assertTrue(android.provider.Settings.Global.getInt(context.contentResolver,"boot_count",-1)>saved.getInt("boot_count"))
        else assertNotEquals(saved.getInt("pid"),android.os.Process.myPid())
        val model=runtime();rule.waitUntil(15000) {model.vault.current!=null};val session=model.vault.current!!;val id=saved.getString("id")
        rule.waitUntil(25000) {model.store.record(session.scope.key,id)?.syncState==SyncState.ACCEPTED}
        val operation=model.store.outbox(session.scope.key).single {it.id==saved.getString("op")};assertEquals(saved.getString("hash"),operation.hash)
        val first=model.api.request(session.scope.server,session.token,"season-reports","POST",operation.envelope)
        val second=model.api.request(session.scope.server,session.token,"season-reports","POST",operation.envelope)
        assertEquals(WireContract.hash(first),WireContract.hash(second));assertEquals(1,first.getInt("version"))
        return first.getJSONObject("data")
    }
    @Test fun queueReportOffline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="report")
        val f=fixture();login(f,"manager");open(f);fill(f)
        assertEquals("113.33",reports().state.value.draft!!.preview()!!.getJSONObject("metrics").getJSONObject("TC").getString("value"))
        click("report-sources");rule.waitUntil(15000) {!reports().state.value.busy && reports().state.value.sources?.rows?.length()==6}
        barrier(f,"report");remember(JSONObject(),"report")
    }
    @Test fun replayReportThenQueueNewEvaluationOffline():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="reevaluation")
        val f=fixture();val proof=JSONObject(file("finance-season-report-proof.json").readText());val original=replay(proof,"report")
        proof.put("original_snapshot_hash",original.getString("snapshot_hash"));val originalId=original.getString("id")
        open(f);fill(f);val draft=reports().state.value.draft!!;val model=runtime();val session=model.vault.current!!
        val cancellation=JSONObject().put("schema_version","2").put("sync_epoch",model.store.epoch(session.scope.key)).put("operation_id",UUID.randomUUID().toString())
            .put("entity_id",f.getString("depreciation_id")).put("entity_type","depreciation_allocation").put("action","cancel").put("base_version",1)
            .put("payload",JSONObject().put("reason","Koreksi sumber sintetis untuk menguji laporan tetap"))
        model.api.request(session.scope.server,session.token,"depreciation-allocations/${f.getString("depreciation_id")}/cancel","POST",cancellation)
        click("report-queue");rule.waitUntil(10000) {!reports().state.value.busy && reports().state.value.draft==null};click("report-back")
        rule.onNodeWithTag("finance-sync").performClick();rule.waitUntil(25000) {model.store.record(session.scope.key,draft.id)?.syncState==SyncState.REJECTED}
        val rejected=model.store.outbox(session.scope.key).single {it.entityId==draft.id};assertEquals("report_sources_changed",rejected.error!!.getString("code"))
        proof.put("rejected_op",rejected.id);proof.put("rejected_hash",rejected.hash)
        rule.openFinanceOutbox();rule.onNodeWithTag("finance-review-${rejected.id}").performScrollTo().performClick()
        rule.waitUntil(10000) {reports().state.value.draft?.replaceRejected==true && !reports().state.value.busy};assertNull(reports().state.value.draft!!.previewText)
        click("report-preview");rule.waitUntil(15000) {!reports().state.value.busy && reports().state.value.draft?.previewText!=null}
        assertEquals("80.00",reports().state.value.draft!!.preview()!!.getJSONObject("metrics").getJSONObject("TC").getString("value"))
        assertEquals(original.getString("snapshot_hash"),model.api.request(session.scope.server,session.token,"reports/$originalId").getString("snapshot_hash"))
        barrier(f,"reevaluation");remember(proof,"reevaluation")
    }
    @Test fun replayNewEvaluationAndCheckSnapshots():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="verify")
        val f=fixture();val proof=JSONObject(file("finance-season-report-proof.json").readText());val current=replay(proof,"reevaluation")
        val model=runtime();val session=model.vault.current!!;val originalId=proof.getJSONObject("report").getString("id");val currentId=current.getString("id")
        val old=model.api.request(session.scope.server,session.token,"reports/$originalId")
        assertEquals(proof.getString("original_snapshot_hash"),old.getString("snapshot_hash"));assertEquals("113.33",old.getJSONObject("metrics").getJSONObject("TC").getString("value"))
        assertEquals("80.00",current.getJSONObject("metrics").getJSONObject("TC").getString("value"));assertNotEquals(originalId,currentId)
        val firstOperation=model.store.outbox(session.scope.key).single {it.id==proof.getJSONObject("report").getString("op")}
        assertEquals(old.getString("snapshot_hash"),model.api.request(session.scope.server,session.token,"season-reports","POST",firstOperation.envelope).getJSONObject("data").getString("snapshot_hash"))
        val rejected=model.store.outbox(session.scope.key).single {it.id==proof.getString("rejected_op")};assertEquals("SUPERSEDED",rejected.state);assertEquals(proof.getString("rejected_hash"),rejected.hash)
        for((id,version,included) in listOf(Triple(originalId,1,true),Triple(currentId,2,false))) {
            val sources=model.api.request(session.scope.server,session.token,"reports/$id/sources").getJSONArray("data")
            val depreciation=(0 until sources.length()).map(sources::getJSONObject).single {it.getString("kind")=="depreciation_allocation"}
            assertEquals(version,depreciation.getInt("version"));assertEquals(included,depreciation.getBoolean("included"))
        }
        open(f)
        for(id in listOf(originalId,currentId)) {
            scroll("report-record-$id");rule.onNodeWithTag("report-record-$id").performClick();rule.waitUntil(15000) {!reports().state.value.busy && reports().state.value.fresh}
            scroll("report-metric-TC");rule.onNodeWithTag("report-metric-TC").assertIsDisplayed();click("report-sources")
            rule.waitUntil(15000) {!reports().state.value.busy && reports().state.value.sources?.rows?.length()==6};click("report-back")
        }
        click("report-back");login(f,"worker");rule.onNodeWithTag("finance-tab-2").assertDoesNotExist();val worker=model.vault.current!!
        for(path in listOf("season-reports","reports/$originalId","reports/$originalId/sources")) {
            try {model.api.request(worker.scope.server,worker.token,path);fail("Worker must never read financial reports")} catch(error:FinanceApiFailure) {assertEquals(403,error.status)}
        }
        file("finance-season-report-verified.txt").writeText(f.getString("fixture_id"))
    }
}
