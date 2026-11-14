package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.feature.investments.*
import com.inofarm.jogja.core.contracts.InvestmentContract
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
class FinanceInvestmentBackendDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name:String)=File(context.getExternalFilesDir(null),name)
    private fun fixture():JSONObject { assertTrue(context.packageName.endsWith(".finance"));return JSONObject(file("finance-investment-fixture.json").readText()).also {assertEquals("inofarm_test",it.getString("database"))} }
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
        try {rule.waitUntil(15000) {rule.onAllNodesWithTag(if (role == "manager") "manager-add-greenhouse" else "worker-home-summary").fetchSemanticsNodes().isNotEmpty()}}
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
        file("finance-investment-$phase-ready.txt").writeText(f.getString("fixture_id"))
        val model=runtime();val session=model.vault.current!!;var offline=false
        repeat(45) {if(!offline) {try {runBlocking {model.api.request(session.scope.server,session.token,"sync/status")};Thread.sleep(500)} catch(expected:IOException) {offline=true}}}
        assertTrue("The owned API must actually be offline before saving",offline)
    }

    private fun proof()=JSONObject(file("finance-investment-proof.json").readText())
    private fun remember(proof:JSONObject,key:String,id:String) {
        val m=runtime();val session=m.vault.current!!;val op=m.store.outbox(session.scope.key).single {it.entityId==id && it.state=="QUEUED"}
        proof.put(key,JSONObject().put("id",id).put("op",op.id).put("hash",op.hash).put("pid",android.os.Process.myPid()).put("boot_count",android.provider.Settings.Global.getInt(context.contentResolver,"boot_count",-1)))
        file("finance-investment-proof.json").writeText(proof.toString())
    }
    private suspend fun replay(proof:JSONObject,key:String,resource:String):JSONObject {
        val saved=proof.getJSONObject(key)
        if(InstrumentationRegistry.getArguments().getString("restartMode")=="os-reboot") assertTrue(android.provider.Settings.Global.getInt(context.contentResolver,"boot_count",-1)>saved.getInt("boot_count"))
        else assertNotEquals(saved.getInt("pid"),android.os.Process.myPid())
        val m=runtime();rule.waitUntil(15000) {m.vault.current!=null};val session=m.vault.current!!;m.sync.run();val id=saved.getString("id")
        rule.waitUntil(25000) {m.store.record(session.scope.key,id)?.syncState==SyncState.ACCEPTED}
        val op=m.store.outbox(session.scope.key).single {it.id==saved.getString("op")};assertEquals(saved.getString("hash"),op.hash)
        val first=m.api.request(session.scope.server,session.token,resource,"POST",op.envelope);val again=m.api.request(session.scope.server,session.token,resource,"POST",op.envelope)
        assertEquals(WireContract.hash(first),WireContract.hash(again));assertEquals(1,first.getInt("version"));return first.getJSONObject("data")
    }
    @Test fun queueScenarioOffline():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="scenario")
        val f=fixture();login(f,"manager");val m=runtime();val session=m.vault.current!!
        val source=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("investment-report-public.json").bufferedReader().use {it.readText()}).getJSONObject("scenario")
        val draft=InvestmentScenarioDraft.from(f.getString("scenario_id"),f.getString("greenhouse_id"),source,null).copy(name="Investasi end-to-end sintetis "+f.getString("fixture_id"))
        m.investments.flushScenario(session,m.vault.generation,draft);assertTrue(m.store.outbox(session.scope.key).none {it.entityId==draft.id})
        barrier(f,"scenario");m.investments.queueScenario(session,m.vault.generation,draft);remember(JSONObject(),"scenario",draft.id)
        assertNull(m.store.record(session.scope.key,draft.id)!!.serverVersion)
    }
    @Test fun acceptScenarioThenQueueReviewedReplacementOffline():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="report")
        val f=fixture();val proof=proof();val scenario=replay(proof,"scenario","investment-scenarios");InvestmentContract.scenario(scenario)
        val m=runtime();val session=m.vault.current!!;val ticket=m.vault.generation
        val draft=m.investments.preview(session,ticket,InvestmentReportDraft(id=f.getString("report_id"),greenhouseId=f.getString("greenhouse_id"),scenarioId=scenario.getString("id"),note="Laporan pertama sintetis"))
        assertEquals("4132231.40",draft.preview()!!.getJSONObject("metrics").getJSONObject("NPV").getString("value"));m.investments.queueReport(session,ticket,draft);m.sync.run()
        val old=m.store.record(session.scope.key,draft.id)!!;assertEquals(SyncState.ACCEPTED,old.syncState);proof.put("old_snapshot_hash",old.serverBody!!.getString("snapshot_hash"))
        val stale=m.investments.preview(session,ticket,InvestmentReportDraft(id=f.getString("stale_report_id"),greenhouseId=f.getString("greenhouse_id"),scenarioId=scenario.getString("id"),note="Koreksi tinjauan sintetis"))
        val update=JSONObject().put("schema_version","2").put("sync_epoch",m.store.epoch(session.scope.key)).put("operation_id",UUID.randomUUID().toString()).put("entity_id",scenario.getString("id")).put("entity_type","investment_scenario").put("action","update").put("base_version",1)
            .put("payload",InvestmentScenarioDraft.from(scenario.getString("id"),scenario.getString("greenhouse_id"),scenario,1).copy(initial="150000000.00",reason="Koreksi investasi awal sintetis").payload())
        m.api.request(session.scope.server,session.token,"investment-scenarios/${scenario.getString("id")}","PATCH",update)
        m.investments.queueReport(session,ticket,stale);m.sync.run();val rejected=m.store.outbox(session.scope.key).single {it.entityId==stale.id};assertEquals("report_sources_changed",rejected.error!!.getString("code"));assertEquals("REJECTED",rejected.state)
        proof.put("rejected_op",rejected.id).put("rejected_hash",rejected.hash)
        val replacement=m.investments.preview(session,ticket,stale.copy(previewText=null,previewFetchedAt=null,replaceRejected=true));assertEquals(2,replacement.preview()!!.getInt("scenario_version"))
        assertEquals("-45867768.60",replacement.preview()!!.getJSONObject("metrics").getJSONObject("NPV").getString("value"));assertEquals("not_recovered",replacement.preview()!!.getJSONObject("metrics").getJSONObject("PP").getString("status"))
        barrier(f,"report");m.investments.queueReport(session,ticket,replacement);remember(proof,"report",replacement.id)
    }
    @Test fun acceptReplacementThenCheckFrozenSourcesAndWorkerDenial():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="verify")
        val f=fixture();val proof=proof();val current=replay(proof,"report","investment-reports");val m=runtime();val session=m.vault.current!!
        val old=m.api.request(session.scope.server,session.token,"investment-reports/${f.getString("report_id")}");assertEquals(proof.getString("old_snapshot_hash"),old.getString("snapshot_hash"));assertEquals("4132231.40",old.getJSONObject("metrics").getJSONObject("NPV").getString("value"))
        assertEquals("-45867768.60",current.getJSONObject("metrics").getJSONObject("NPV").getString("value"));val rejected=m.store.outbox(session.scope.key).single {it.id==proof.getString("rejected_op")};assertEquals("SUPERSEDED",rejected.state);assertEquals(proof.getString("rejected_hash"),rejected.hash)
        for((data,amount) in listOf(old to "100000000.00",current to "150000000.00")) {
            val sources=m.api.request(session.scope.server,session.token,"investment-reports/${data.getString("id")}/sources");assertEquals(3,sources.getJSONArray("data").length());assertEquals(amount,sources.getJSONArray("data").getJSONObject(0).getJSONObject("data").getString("amount"));assertEquals(data.getString("snapshot_hash"),sources.getString("snapshot_hash"))
        }
        val cancel=JSONObject().put("schema_version","2").put("sync_epoch",m.store.epoch(session.scope.key)).put("operation_id",UUID.randomUUID().toString()).put("entity_id",f.getString("scenario_id")).put("entity_type","investment_scenario").put("action","cancel").put("base_version",2).put("payload",JSONObject().put("reason","Pembatalan skenario sintetis setelah laporan"))
        m.api.request(session.scope.server,session.token,"investment-scenarios/${f.getString("scenario_id")}/cancel","POST",cancel)
        val reportOp=m.store.outbox(session.scope.key).single {it.entityId==f.getString("report_id")};val replay=m.api.request(session.scope.server,session.token,"investment-reports","POST",reportOp.envelope);assertEquals(proof.getString("old_snapshot_hash"),replay.getJSONObject("data").getString("snapshot_hash"))
        login(f,"worker");rule.onNodeWithTag("finance-tab-2").assertDoesNotExist();val worker=m.vault.current!!
        try {m.api.request(worker.scope.server,worker.token,"investment-reports/${old.getString("id")}");fail("Worker accessed finance snapshot")} catch(expected:FinanceApiFailure) {assertEquals(403,expected.status)}
    }
}
