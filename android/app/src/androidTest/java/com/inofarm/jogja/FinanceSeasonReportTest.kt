package com.inofarm.jogja

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.network.FinanceHttpApi
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.reports.*
import com.inofarm.jogja.core.design.InofarmTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Isolated databases/preferences; real SQLite and HTTP transport, synthetic server DTOs. */
class FinanceSeasonReportTest {
    @get:Rule val rule=createComposeRule()
    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-test-report-${UUID.randomUUID()}.db"
    private val prefs="finance-test-report-${UUID.randomUUID()}"
    private val epoch=UUID.randomUUID().toString()
    private val server=MockWebServer();private val responseStatus=AtomicInteger(200)
    private lateinit var fixture:JSONObject;private lateinit var preview:JSONObject
    private lateinit var gh:String;private lateinit var season:String
    private lateinit var helper:FinanceDatabase;private lateinit var store:LocalFinanceStore
    private lateinit var session:FinanceSession;private lateinit var vault:FinanceSessionVault
    private lateinit var repository:SeasonReportRepository;private lateinit var vm:SeasonReportViewModel
    private val models=ViewModelStore()
    private fun uuid()=UUID.randomUUID().toString()
    private fun page(rows:JSONArray=JSONArray(),access:Boolean=true)=JSONObject().put("sync_epoch",epoch).put("scope",if(access) JSONArray().put(JSONObject().put("id",gh)) else JSONArray())
        .put("data",rows).put("next_cursor","fixture").put("has_more",false)
    private fun row(kind:String,id:String,data:JSONObject)=JSONObject().put("entity_type",kind).put("entity_id",id).put("greenhouse_id",gh).put("version",1).put("tombstone",false).put("data",data)
    @Before fun setup() {
        assertTrue(context.packageName.endsWith(".finance"))
        fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("season-report-public.json").bufferedReader().use {it.readText()})
        preview=fixture.getJSONObject("preview");gh=preview.getString("greenhouse_id");season=preview.getString("season_id")
        server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
            val status=responseStatus.get();if(status!=200) return MockResponse().setResponseCode(status).setBody("{\"message\":\"Penolakan fixture\"}")
            val path=request.requestUrl!!.encodedPath
            val body=when {
                path.endsWith("/season-reports/preview") -> JSONObject(preview.toString()).also {result ->
                    val input=JSONObject(request.body.readUtf8());for(key in listOf("coverage","known_pending_count","review_note")) result.put(key,input.get(key));result.put("report_state",input.getString("requested_state"))
                }
                path.endsWith("/sources") -> {
                    val all=fixture.getJSONObject("full").getJSONArray("sources");val second=request.requestUrl!!.queryParameter("cursor")!=null
                    val rows=JSONArray();for(i in (if(second) 2 else 0) until (if(second) all.length() else 2)) rows.put(all.get(i))
                    JSONObject().put("data",rows).put("source_count",all.length()).put("sources_hash",preview.getString("sources_hash"))
                        .put("snapshot_hash",fixture.getJSONObject("receipt").getJSONObject("data").getString("snapshot_hash")).put("next_cursor",if(second) JSONObject.NULL else "page-2")
                }
                else -> fixture.getJSONObject("receipt").getJSONObject("data")
            }
            return MockResponse().setBody(body.toString())
        }}
        server.start();val base=server.url("/api/v2/").toString()
        helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher())
        session=FinanceSession(AccountScope(base,uuid()),"fixture-token",Instant.now().plusSeconds(86400).toString(),"Pengelola sintetis",UserRole.MANAGER)
        vault=FinanceSessionVault(context,FixtureCipher(),prefs);assertTrue(vault.save(vault.generation,session))
        store.initialize(session.scope.key,epoch,UserRole.MANAGER)
        store.applyPull(session.scope.key,page(JSONArray().put(row("seasons",season,JSONObject(preview.getJSONObject("scope").getJSONObject("season").toString())))))
        repository=SeasonReportRepository(vault,store,FinanceHttpApi(true));vm=SeasonReportViewModel(repository);models.put("report",vm)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {vm.bind(session);vm.show(gh)}
        rule.setContent {val state by vm.state.collectAsState();InofarmTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
                Box(Modifier.fillMaxSize()) {Box(Modifier.width(320.dp).fillMaxHeight()) {SeasonReportScreen(SeasonReportWorkspace(state,vm),
                    CultivationUiState(greenhouses=listOf(preview.getJSONObject("scope").getJSONObject("greenhouse")),seasons=listOf(preview.getJSONObject("scope").getJSONObject("season"))))}}
            }
        }}
        rule.waitUntil(10000) {!vm.state.value.busy}
    }
    @After fun close() {rule.runOnIdle {models.clear()};server.shutdown();helper.close();assertTrue(name.startsWith("finance-test-"));context.deleteDatabase(name);context.deleteSharedPreferences(prefs)}
    private fun reviewed()=SeasonReportDraft(greenhouseId=gh,seasonId=season,costs="complete",sales="complete",pending="0",requestedState="provisional",note="Tinjauan perangkat sintetis")
    private fun scroll(tag:String) {rule.onNodeWithTag("report-list").performScrollToNode(hasTestTag(tag))}
    private fun click(tag:String) {scroll(tag);rule.onNodeWithTag(tag).performClick()}
    private fun choose(tag:String,value:String) {click(tag);rule.onNodeWithTag("$tag-option-$value").performScrollTo().performClick()}
    private fun accepted():JSONObject=JSONObject(fixture.getJSONObject("receipt").getJSONObject("data").toString())
    @Test fun receivedReportMaterializesAndCurrentRoleAndScopeStillProtectIt() {
        val data=accepted();val id=data.getString("id")
        store.applyPull(session.scope.key,page(JSONArray().put(row("season_report",id,data))))
        assertEquals(1,runBlocking {repository.records(session,gh,season,0)}.size)
        assertEquals(SyncState.ACCEPTED,store.record(session.scope.key,id)!!.syncState)
        store.initialize(session.scope.key,epoch,UserRole.WORKER)
        assertThrows(IllegalArgumentException::class.java) {store.record(session.scope.key,id)}
        store.initialize(session.scope.key,epoch,UserRole.MANAGER);store.applyPull(session.scope.key,page(access=false))
        assertTrue(runBlocking {repository.records(session,gh,season,0)}.isEmpty())
    }
    @Test fun previewAndQueuedIdentitySurviveDatabaseReopenWithoutBecomingAccepted()=runBlocking {
        val draft=repository.preview(session,vault.generation,reviewed());repository.queue(session,vault.generation,draft)
        val before=store.next(session.scope.key)!!;assertEquals("season_report",before.envelope.getString("entity_type"));assertEquals("QUEUED",before.state)
        helper.close();helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher());repository=SeasonReportRepository(vault,store,FinanceHttpApi(true))
        val after=store.next(session.scope.key)!!;assertEquals(before.id,after.id);assertEquals(before.hash,after.hash)
        val saved=store.record(session.scope.key,draft.id)!!;assertNull(saved.serverVersion)
        assertEquals(draft,SeasonReportDraft.from(saved.id,gh,saved.body));assertEquals(SyncState.QUEUED,saved.syncState)
    }
    @Test fun pendingReviewCountsRelatedRecordsAndNeverClaimsAnotherSeasonsActivity()=runBlocking {
        val group=uuid();val other=uuid()
        store.applyPull(session.scope.key,page(JSONArray().put(row("planting_groups",group,JSONObject().put("id",group).put("season_id",season)))
            .put(row("planting_groups",other,JSONObject().put("id",other).put("season_id",uuid())))))
        fun queue(kind:String,body:JSONObject)=uuid().also {store.saveAndQueue(session.scope.key,it,gh,body,kind=kind)}
        queue("activity",JSONObject().put("planting_group_id",other));queue("activity",JSONObject().put("planting_group_id",group))
        val expense=queue("expense",JSONObject().put("amount","80.00"))
        queue("cost_allocation",JSONObject().put("expense_id",expense).put("season_id",season))
        val asset=queue("asset",JSONObject().put("expense_id",expense))
        queue("depreciation_allocation",JSONObject().put("asset_id",asset).put("season_id",season))
        queue("sale",JSONObject().put("season_id",season))
        assertEquals(6,store.pendingForSeasonReport(session.scope.key,gh,season))
        val rejected=reviewed()
        try {repository.preview(session,vault.generation,rejected);fail("Known pending records must block a zero declaration")} catch(expected:IllegalArgumentException) {assertTrue(expected.message!!.contains("6"))}
        assertEquals("0",SeasonReportDraft.from(rejected.id,gh,store.record(session.scope.key,rejected.id)!!.body).pending)
        assertTrue(repository.preview(session,vault.generation,reviewed().copy(pending="")).pending.isBlank())
    }
    @Test fun largeTextFormRetainsRawInputAfterNetworkFailureAndCanBeSaved() {
        click("report-new");choose("report-season",season);choose("report-costs","complete");choose("report-sales","complete");choose("report-state","provisional")
        scroll("report-pending");assertEquals("",vm.state.value.draft!!.pending)
        scroll("report-note");rule.onNodeWithTag("report-note").performTextReplacement("  Tinjauan α\nbaris kedua  ");Espresso.closeSoftKeyboard()
        responseStatus.set(503);click("report-preview");rule.waitUntil(10000) {!vm.state.value.busy && vm.state.value.message!=null}
        assertEquals("  Tinjauan α\nbaris kedua  ",vm.state.value.draft!!.note);assertNull(vm.state.value.draft!!.previewText)
        val id=vm.state.value.draft!!.id;click("report-save-draft");rule.waitUntil(10000) {vm.state.value.draft==null && !vm.state.value.busy}
        val saved=store.record(session.scope.key,id)!!;assertEquals("",SeasonReportDraft.from(id,gh,saved.body).pending);assertTrue(store.outbox(session.scope.key).isEmpty())
        rule.runOnIdle {vm.select(id)};rule.waitUntil(10000) {vm.state.value.draft?.id==id && !vm.state.value.busy}
        assertEquals("  Tinjauan α\nbaris kedua  ",vm.state.value.draft!!.note)
    }
    @Test fun previewAllIndicatorsAndQueueStayReadableAtTwoHundredPercentText() {
        rule.runOnIdle {vm.new(gh,season);vm.edit {reviewed().copy(id=it.id)}}
        click("report-preview");rule.waitUntil(10000) {!vm.state.value.busy && vm.state.value.draft?.previewText!=null}
        for(key in SeasonReportContract.labels.keys) {scroll("report-metric-$key");rule.onNodeWithTag("report-metric-$key").assertIsDisplayed()}
        scroll("report-metric-BEP_Q");rule.onNodeWithText("266,666667 kg").assertIsDisplayed()
        rule.captureBotanicalWindow("season-report")
        click("report-queue");rule.waitUntil(10000) {vm.state.value.draft==null && !vm.state.value.busy}
        assertEquals(1,store.outbox(session.scope.key).size);assertNull(store.records(session.scope.key,kind="season_report").single().serverVersion)
    }
    @Test fun revokedAssignmentHidesPreviewAndStillAllowsKeepingTheRawDraft() {
        rule.runOnIdle {vm.new(gh,season);vm.edit {reviewed().copy(id=it.id)};vm.preview()}
        rule.waitUntil(10000) {!vm.state.value.busy && vm.state.value.draft?.previewText!=null}
        val draft=vm.state.value.draft!!;store.applyPull(session.scope.key,page(access=false));rule.runOnIdle {vm.reload()}
        rule.waitUntil(10000) {vm.state.value.accessDenied};rule.onNodeWithTag("report-metric-TC").assertDoesNotExist()
        rule.runOnIdle {vm.back()};rule.waitUntil(10000) {!vm.state.value.busy && vm.state.value.draft==null}
        val retained=store.record(session.scope.key,draft.id)!!;assertEquals(SyncState.SCOPE_REVOKED,retained.syncState)
        assertEquals(draft,SeasonReportDraft.from(draft.id,gh,retained.body));assertTrue(store.outbox(session.scope.key).isEmpty())
    }
    @Test fun immutableSourcePagesRemainCachedOfflineAndServerDenialHidesDetails()=runBlocking {
        val data=accepted();val id=data.getString("id");store.applyPull(session.scope.key,page(JSONArray().put(row("season_report",id,data))))
        repository.records(session,gh,season,0)
        var sources=repository.sources(session,vault.generation,id,gh,season,data.getString("sources_hash"),data.getString("snapshot_hash"),null)
        assertEquals(2,sources.rows.length());sources=repository.sources(session,vault.generation,id,gh,season,data.getString("sources_hash"),data.getString("snapshot_hash"),sources)
        assertEquals(7,sources.rows.length());assertNull(sources.cursor)
        assertTrue(repository.cachedSources(session,id,data.getString("sources_hash"),data.getString("snapshot_hash"))!!.cached)
        responseStatus.set(403);rule.runOnIdle {vm.select(id)}
        rule.waitUntil(10000) {!vm.state.value.busy && vm.state.value.accessDenied}
        assertNull(vm.state.value.selected);assertNull(vm.state.value.sources);rule.onNodeWithTag("report-metric-TC").assertDoesNotExist()
        assertNotNull(store.record(session.scope.key,id))
    }
}
