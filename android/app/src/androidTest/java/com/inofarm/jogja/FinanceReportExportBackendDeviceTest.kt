package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.feature.investments.*
import com.inofarm.jogja.feature.exports.ReportExportSelection
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
class FinanceReportExportBackendDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name:String)=File(context.getExternalFilesDir(null),name)
    private fun fixture():JSONObject { assertTrue(context.packageName.endsWith(".finance"));return JSONObject(file("finance-report-export-fixture.json").readText()).also {assertEquals("inofarm_test",it.getString("database"))} }
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
        try {rule.waitUntil(15000) {rule.onAllNodesWithTag(if (role == "manager") "manager-home-picker" else "finance-context-open").fetchSemanticsNodes().isNotEmpty()}}
        catch(error:Exception) {
            lateinit var auth:com.inofarm.jogja.feature.auth.AuthViewModel;rule.runOnIdle {auth=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.auth.AuthViewModel::class.java]}
            throw AssertionError("Login transition: loading=${auth.state.value.loading}, hasSession=${auth.state.value.session!=null}, reason=${auth.state.value.error}",error)
        }
        rule.waitForFinanceMasterPull()
    }
    private fun choose(tag:String,id:String) {val selector=rule.onNodeWithTag(tag);selector.performScrollTo().performClick();rule.onNodeWithTag("$tag-option-$id").performScrollTo().performClick()}
    private fun hideKeyboard() {rule.runOnIdle {(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0)};rule.waitForIdle()}
    private fun runtime():FinanceRuntime {lateinit var runtime:FinanceRuntime;rule.runOnIdle {runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]};return runtime}


    private fun target(format:String):android.net.Uri {
        val name="finance-test-real-export-${UUID.randomUUID()}.$format"
        return context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply {put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,name);put(android.provider.MediaStore.MediaColumns.MIME_TYPE,if(format=="pdf") "application/pdf" else "text/csv");put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)}) ?: error("Owned destination unavailable")
    }
    private suspend fun save(row:com.inofarm.jogja.core.database.LocalRecord,format:String):ByteArray {
        val model=runtime();val session=model.vault.current!!;val uri=target(format)
        try {model.reportExports.save(session,model.vault.generation,ReportExportSelection.from(session.scope.key,row,format),uri)
            return context.contentResolver.openInputStream(uri)!!.use {it.readBytes()}
        } finally {
            val name=context.contentResolver.query(uri,arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME),null,null,null)!!.use {check(it.moveToFirst());it.getString(0)}
            check(name.startsWith("finance-test-real-export-"));context.contentResolver.delete(uri,null,null)
        }
    }
    @Test fun realNativeExportsRemainBoundAfterSourceCorrections():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="export")
        val f=fixture();login(f,"manager");val model=runtime();val session=model.vault.current!!;model.sync.run()
        model.store.hydrateReportCache(session.scope.key,"season_report");model.store.hydrateReportCache(session.scope.key,"investment_report")
        val season=model.store.record(session.scope.key,f.getString("season_report_id"))!!;val investment=model.store.record(session.scope.key,f.getString("investment_report_id"))!!
        assertEquals(f.getString("season_report_hash"),season.serverBody!!.getString("snapshot_hash"));assertEquals(f.getString("investment_report_hash"),investment.serverBody!!.getString("snapshot_hash"))
        lateinit var reports:com.inofarm.jogja.feature.reports.SeasonReportViewModel
        rule.runOnIdle {reports=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.reports.SeasonReportViewModel::class.java];reports.select(season.id)}
        rule.waitUntil(15000) {reports.state.value.selected?.id==season.id && !reports.state.value.busy};rule.onNodeWithTag("report-list").performScrollToNode(hasTestTag("report-download-pdf"));rule.onNodeWithTag("report-download-pdf").assertExists()
        rule.runOnIdle {reports.back();reports.back()}
        lateinit var investments:InvestmentViewModel;rule.runOnIdle {investments=ViewModelProvider(rule.activity)[InvestmentViewModel::class.java];investments.select(investment.id)}
        rule.waitUntil(15000) {investments.state.value.selected?.id==investment.id && !investments.state.value.busy};rule.onNodeWithTag("investment-list").performScrollToNode(hasTestTag("report-download-csv"));rule.onNodeWithTag("report-download-csv").assertExists()
        val before=mutableMapOf<String,ByteArray>()
        for(row in listOf(season,investment)) for(format in listOf("pdf","csv")) {
            val bytes=save(row,format);assertTrue(bytes.isNotEmpty());if(format=="pdf") assertEquals("%PDF-",bytes.copyOfRange(0,5).toString(Charsets.US_ASCII)) else {before[row.id]=bytes;assertTrue(bytes.toString(Charsets.UTF_8).contains(if(row.kind=="season_report") "113.33" else "4132231.40"))}
        }
        fun operation(kind:String,id:String,payload:JSONObject,action:String,version:Int)=JSONObject().put("schema_version","2").put("sync_epoch",model.store.epoch(session.scope.key)).put("operation_id",UUID.randomUUID().toString()).put("entity_id",id).put("entity_type",kind).put("action",action).put("base_version",version).put("payload",payload)
        model.api.request(session.scope.server,session.token,"depreciation-allocations/${f.getString("depreciation_id")}/cancel","POST",operation("depreciation_allocation",f.getString("depreciation_id"),JSONObject().put("reason","Koreksi sumber setelah ekspor"),"cancel",1))
        val payload=JSONObject(f.getJSONObject("scenario_payload").toString()).put("initial_outlay","150000000.00").put("reason","Koreksi investasi setelah ekspor")
        model.api.request(session.scope.server,session.token,"investment-scenarios/${f.getString("scenario_id")}","PATCH",operation("investment_scenario",f.getString("scenario_id"),payload,"update",1))
        for(row in listOf(season,investment)) {assertArrayEquals(before[row.id],save(row,"csv"));assertTrue(save(row,"pdf").isNotEmpty())}
        file("finance-report-export-proof.json").writeText(JSONObject().put("fixture_id",f.getString("fixture_id")).put("season_snapshot_hash",f.getString("season_report_hash")).put("investment_snapshot_hash",f.getString("investment_report_hash")).put("pdf_csv_before_after","PASS").toString())
        login(f,"worker");rule.onNodeWithTag("finance-tab-4").assertDoesNotExist();val worker=model.vault.current!!
        for(row in listOf(season,investment)) try {model.api.exportReport(worker.scope.server,worker.token,row.id,"csv",row.serverBody!!.getString("snapshot_hash"));fail("Worker downloaded report")} catch(expected:FinanceApiFailure) {assertEquals(403,expected.status)}
    }
}
