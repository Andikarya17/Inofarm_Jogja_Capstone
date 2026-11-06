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
import com.inofarm.jogja.feature.investments.*
import com.inofarm.jogja.core.design.InofarmTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Isolated SQLite/preferences and HTTP transport; no live account or application data clearing. */
class FinanceInvestmentTest {
    @get:Rule val rule=createComposeRule()
    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-test-investment-${UUID.randomUUID()}.db"
    private val prefs="finance-test-investment-${UUID.randomUUID()}"
    private val epoch=UUID.randomUUID().toString();private val server=MockWebServer();private val responseStatus=AtomicInteger(200)
    private lateinit var fixture:JSONObject;private lateinit var scenario:JSONObject;private lateinit var gh:String
    private lateinit var helper:FinanceDatabase;private lateinit var store:LocalFinanceStore;private lateinit var session:FinanceSession;private lateinit var vault:FinanceSessionVault
    private lateinit var repository:InvestmentRepository;private lateinit var vm:InvestmentViewModel
    private val models=ViewModelStore()
    private fun page(rows:JSONArray=JSONArray(),access:Boolean=true)=JSONObject().put("sync_epoch",epoch).put("scope",if(access) JSONArray().put(JSONObject().put("id",gh)) else JSONArray()).put("data",rows).put("next_cursor","fixture").put("has_more",false)
    private fun row(kind:String,data:JSONObject)=JSONObject().put("entity_type",kind).put("entity_id",data.getString("id")).put("greenhouse_id",gh).put("version",data.getInt("version")).put("tombstone",false).put("data",data)
    @Before fun setup() {
        assertTrue(context.packageName.endsWith(".finance"));fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("investment-report-public.json").bufferedReader().use {it.readText()});scenario=fixture.getJSONObject("scenario");gh=scenario.getString("greenhouse_id")
        server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
            val status=responseStatus.get();if(status!=200) return MockResponse().setResponseCode(status).setBody("{\"message\":\"Penolakan fixture investasi\"}")
            val path=request.requestUrl!!.encodedPath
            val body=when {
                path.endsWith("/investment-reports/preview") -> JSONObject(fixture.getJSONObject("preview").toString()).put("review_note",JSONObject(request.body.readUtf8()).getString("review_note"))
                path.endsWith("/sources") -> {
                    val all=fixture.getJSONObject("full").getJSONArray("sources");val offset=request.requestUrl!!.queryParameter("cursor")?.toInt() ?: 0
                    JSONObject().put("data",JSONArray().put(all.get(offset))).put("source_count",3).put("sources_hash",fixture.getJSONObject("preview").getString("sources_hash"))
                        .put("snapshot_hash",fixture.getJSONObject("receipt").getJSONObject("data").getString("snapshot_hash")).put("next_cursor",if(offset==2) JSONObject.NULL else (offset+1).toString())
                }
                path.contains("investment-scenarios/") -> scenario
                else -> fixture.getJSONObject("receipt").getJSONObject("data")
            };return MockResponse().setBody(body.toString())
        }}
        server.start();val base=server.url("api/v2/").toString();session=FinanceSession(AccountScope(base,scenario.getString("recorded_by")),"fixture-token",Instant.now().plusSeconds(3600).toString(),"Pengelola sintetis",UserRole.MANAGER)
        helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher());store.initialize(session.scope.key,epoch,UserRole.MANAGER);store.applyPull(session.scope.key,page())
        vault=FinanceSessionVault(context,FixtureCipher(),prefs);assertTrue(vault.save(vault.generation,session));repository=InvestmentRepository(vault,store,FinanceHttpApi(true));vm=InvestmentViewModel(repository);models.put("investment",vm)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {vm.bind(session);vm.show(gh)}
        rule.setContent {val state by vm.state.collectAsState();InofarmTheme {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
            Box(Modifier.fillMaxSize()) {Box(Modifier.width(320.dp).fillMaxHeight()) {InvestmentScreen(InvestmentWorkspace(state,vm),CultivationUiState(greenhouses=listOf(fixture.getJSONObject("preview").getJSONObject("scope").getJSONObject("greenhouse"))))}}
        }}}
    }
    @After fun close() {rule.runOnIdle {models.clear()};server.shutdown();helper.close();assertTrue(name.startsWith("finance-test-"));context.deleteDatabase(name);context.deleteSharedPreferences(prefs)}
    private fun scroll(tag:String) {rule.onNodeWithTag("investment-list").performScrollToNode(hasTestTag(tag))}
    private fun click(tag:String) {scroll(tag);rule.onNodeWithTag(tag).performClick()}
    private fun acceptedScenario() {store.applyPull(session.scope.key,page(JSONArray().put(row(InvestmentContract.SCENARIO,scenario))));runBlocking {repository.records(session,InvestmentContract.SCENARIO,gh,null,0)}}
    private fun reviewed()=InvestmentReportDraft(greenhouseId=gh,scenarioId=scenario.getString("id"),note="Tinjauan perangkat sintetis")
    @Test fun rawScenarioAndImmutableQueuedIdentitySurviveDatabaseReopen()=runBlocking {
        val draft=InvestmentScenarioDraft.from(UUID.randomUUID().toString(),gh,scenario,null).copy(name="  α skenario  ",initial="",discountPercent="",flows=listOf(InvestmentFlowDraft(amount="-0,01"),InvestmentFlowDraft()))
        repository.flushScenario(session,vault.generation,draft);val saved=store.record(session.scope.key,draft.id)!!;assertEquals(draft,InvestmentScenarioDraft.from(draft.id,gh,saved.body,null))
        repository.queueScenario(session,vault.generation,draft);val first=store.next(session.scope.key)!!;assertEquals("investment_scenario",first.envelope.getString("entity_type"));assertTrue(first.envelope.getJSONObject("payload").isNull("initial_outlay"))
        helper.close();helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher());val reopened=store.next(session.scope.key)!!;assertEquals(first.id,reopened.id);assertEquals(first.hash,reopened.hash);assertNull(store.record(session.scope.key,draft.id)!!.serverVersion)
    }
    @Test fun freshConsumerMaterializesBothKindsButCurrentRoleAndScopeProtectThem()=runBlocking {
        val report=fixture.getJSONObject("receipt").getJSONObject("data")
        store.applyPull(session.scope.key,page(JSONArray().put(row(InvestmentContract.SCENARIO,scenario)).put(row(InvestmentContract.REPORT,report))))
        assertEquals(1,repository.records(session,InvestmentContract.SCENARIO,gh,null,0).size);assertEquals(1,repository.records(session,InvestmentContract.REPORT,gh,scenario.getString("id"),0).size)
        store.initialize(session.scope.key,epoch,UserRole.WORKER);assertThrows(IllegalArgumentException::class.java) {store.record(session.scope.key,scenario.getString("id"))}
        store.initialize(session.scope.key,epoch,UserRole.MANAGER);store.applyPull(session.scope.key,page(access=false));assertTrue(repository.records(session,InvestmentContract.REPORT,gh,null,0).isEmpty())
        try {repository.detail(session,report.getString("id"));fail("Revoked scope must not expose cached report")} catch(expected:InvestmentAccessDenied) { }
    }
    @Test fun reportPreviewQueueAndSourceVersionRemainDurableAndUnaccepted()=runBlocking {
        acceptedScenario();val draft=repository.preview(session,vault.generation,reviewed());repository.queueReport(session,vault.generation,draft)
        val before=store.next(session.scope.key)!!;assertEquals(1,before.envelope.getJSONObject("payload").getInt("expected_scenario_version"));helper.close();helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher())
        val after=store.next(session.scope.key)!!;assertEquals(before.id,after.id);assertEquals(before.hash,after.hash);val saved=store.record(session.scope.key,draft.id)!!;assertEquals(draft,InvestmentReportDraft.from(draft.id,gh,saved.body));assertEquals(SyncState.QUEUED,saved.syncState);assertNull(saved.serverVersion)
    }
    @Test fun freshCancelledOnlyCacheRemainsHistoryWithCurrentRoleAndScopeGuards()=runBlocking {
        val cancelled=JSONObject(scenario.toString()).put("record_state","cancelled").put("version",2)
        store.applyPull(session.scope.key,page(JSONArray().put(row(InvestmentContract.SCENARIO,cancelled).put("tombstone",true))))
        assertNull(store.record(session.scope.key,cancelled.getString("id")))
        val records=repository.records(session,InvestmentContract.SCENARIO,gh,null,0);assertEquals(1,records.size)
        assertEquals("cancelled",records.single().body.getString("record_state"));assertEquals(2L,records.single().serverVersion)
        assertEquals(1,repository.records(session,InvestmentContract.SCENARIO,gh,null,0).size);assertTrue(store.outbox(session.scope.key).isEmpty())
        store.initialize(session.scope.key,epoch,UserRole.WORKER);assertThrows(IllegalArgumentException::class.java) {store.record(session.scope.key,cancelled.getString("id"))}
        store.initialize(session.scope.key,epoch,UserRole.MANAGER);store.applyPull(session.scope.key,page(access=false));assertTrue(repository.records(session,InvestmentContract.SCENARIO,gh,null,0).isEmpty())
    }
    @Test fun cancelledPullAndHydrationNeverOverwriteAnExistingRawDraft()=runBlocking {
        val draft=InvestmentScenarioDraft.from(scenario.getString("id"),gh,scenario,null).copy(name="Isian lokal α belum diterima",initial="1,")
        repository.flushScenario(session,vault.generation,draft)
        val cancelled=JSONObject(scenario.toString()).put("record_state","cancelled").put("version",2)
        store.applyPull(session.scope.key,page(JSONArray().put(row(InvestmentContract.SCENARIO,cancelled).put("tombstone",true))))
        repository.records(session,InvestmentContract.SCENARIO,gh,null,0)
        val raw=store.record(session.scope.key,draft.id)!!.body.getJSONObject("_local_form");assertEquals(draft.name,raw.getString("name"));assertEquals("1,",raw.getString("initial"));assertTrue(store.outbox(session.scope.key).isEmpty())
    }
    @Test fun largeTextFormPreservesInvalidInputAndYearsOutsideHorizon() {
        click("investment-new");scroll("investment-name");rule.onNodeWithTag("investment-name").performTextReplacement("  Skenario α  ");Espresso.closeSoftKeyboard()
        scroll("investment-horizon");rule.onNodeWithTag("investment-horizon").performTextReplacement("2");Espresso.closeSoftKeyboard();scroll("investment-flow-2");rule.onNodeWithTag("investment-flow-2").performTextReplacement("-0,001");Espresso.closeSoftKeyboard()
        scroll("investment-horizon");rule.onNodeWithTag("investment-horizon").performTextReplacement("1");Espresso.closeSoftKeyboard();assertEquals("-0,001",vm.state.value.scenarioDraft!!.flows[1].amount)
        click("investment-scenario-queue");rule.waitUntil(10000) {vm.state.value.errors.isNotEmpty()};assertTrue(store.outbox(session.scope.key).isEmpty());val id=vm.state.value.scenarioDraft!!.id
        click("investment-save-draft");rule.waitUntil(10000) {vm.state.value.scenarioDraft==null && !vm.state.value.busy};rule.runOnIdle {vm.select(id)};rule.waitUntil(10000) {vm.state.value.scenarioDraft?.id==id && !vm.state.value.busy}
        assertEquals("  Skenario α  ",vm.state.value.scenarioDraft!!.name);assertEquals("-0,001",vm.state.value.scenarioDraft!!.flows[1].amount);assertEquals("",vm.state.value.scenarioDraft!!.initial)
    }
    @Test fun fourIndependentIndicatorsSolverDetailsAndPagedFrozenSourcesAreReachable() {
        val report=fixture.getJSONObject("receipt").getJSONObject("data");store.applyPull(session.scope.key,page(JSONArray().put(row(InvestmentContract.REPORT,report))))
        rule.runOnIdle {vm.select(report.getString("id"))};rule.waitUntil(10000) {vm.state.value.selected?.id==report.getString("id") && !vm.state.value.busy}
        InvestmentContract.labels.keys.forEach {scroll("investment-metric-$it");rule.onNodeWithTag("investment-metric-$it").assertExists()}
        click("investment-detail-IRR");rule.onNodeWithTag("investment-list").performScrollToNode(hasText("converged: true",substring=true))
        rule.captureBotanicalWindow("investment-report")
        click("investment-sources");rule.waitUntil(10000) {vm.state.value.sources?.rows?.length()==1 && !vm.state.value.busy}
        repeat(2) {click("investment-sources-more");rule.waitUntil(10000) {vm.state.value.sources?.rows?.length()==it+2 && !vm.state.value.busy}}
        assertEquals(3,vm.state.value.sources!!.rows.length());assertNull(vm.state.value.sources!!.cursor)
        assertEquals(3,runBlocking {repository.cachedSources(session,vm.state.value.selected!!)!!.rows.length()})
    }
    @Test fun cancellationReasonIsDurableAndQueueDoesNotChangeAcceptedSnapshot()=runBlocking {
        acceptedScenario();val row=repository.detail(session,scenario.getString("id"))
        repository.flushScenario(session,vault.generation,InvestmentScenarioDraft.from(row.id,gh,scenario,1));assertEquals(SyncState.ACCEPTED,store.record(session.scope.key,row.id)!!.syncState)
        repository.flushCancel(session,vault.generation,row,"Draf dibatalkan");repository.discardCancel(session,vault.generation,row);assertEquals(SyncState.ACCEPTED,store.record(session.scope.key,row.id)!!.syncState)
        repository.flushCancel(session,vault.generation,row,"  Alasan α  ");assertTrue(store.outbox(session.scope.key).isEmpty())
        assertEquals("  Alasan α  ",store.record(session.scope.key,row.id)!!.body.getString("_local_cancel"));repository.cancel(session,vault.generation,row,"  Alasan α  ")
        val op=store.next(session.scope.key)!!;assertEquals("cancel",op.envelope.getString("action"));assertEquals("Alasan α",op.envelope.getJSONObject("payload").getString("reason"));assertEquals("active",store.record(session.scope.key,row.id)!!.serverBody!!.getString("record_state"))
    }
    @Test fun failedPreviewKeepsLatestRawReviewAndRevokedSessionCannotWrite() {
        acceptedScenario();val row=runBlocking {repository.detail(session,scenario.getString("id"))};rule.runOnIdle {vm.newReport(row);vm.editReport {it.copy(note="  Catatan α\nbaris  ")}}
        responseStatus.set(503);click("investment-preview");rule.waitUntil(10000) {!vm.state.value.busy && vm.state.value.message!=null};val draft=vm.state.value.reportDraft!!
        assertNull(draft.previewText);assertEquals("  Catatan α\nbaris  ",InvestmentReportDraft.from(draft.id,gh,store.record(session.scope.key,draft.id)!!.body).note)
        val ticket=vault.generation;vault.logout();try {runBlocking {repository.flushReport(session,ticket,draft.copy(note="stale"))};fail("Stale session wrote") } catch(expected:IllegalStateException) { }
        assertEquals("  Catatan α\nbaris  ",InvestmentReportDraft.from(draft.id,gh,store.record(session.scope.key,draft.id)!!.body).note)
    }
    @Test fun schemaEightUpgradeRetainsDraftOperationHashAndCursor() {
        val oldName="finance-test-investment-v8-${UUID.randomUUID()}.db";val key=session.scope.key
        val draft=InvestmentScenarioDraft.from(UUID.randomUUID().toString(),gh,scenario,null)
        val opId=UUID.randomUUID().toString();val envelope=JSONObject().put("schema_version","2").put("sync_epoch",epoch).put("operation_id",opId).put("entity_id",draft.id).put("entity_type",InvestmentContract.SCENARIO).put("action","create").put("base_version",JSONObject.NULL).put("payload",draft.payload())
        val hash=WireContract.hash(envelope);val cipher=FixtureCipher()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(oldName),null).use {db->
            FinanceDatabase.createVersionOne(db)
            for(number in listOf("Two","Three","Four","Five","Six","Seven","Eight")) FinanceDatabase.Companion.javaClass.getDeclaredMethod("migrateVersion$number",android.database.sqlite.SQLiteDatabase::class.java).apply {isAccessible=true}.invoke(FinanceDatabase.Companion,db)
            db.execSQL("INSERT INTO scope_metadata(scope,epoch,role,visible_greenhouses,pull_cursor) VALUES(?,?,?,?,?)",arrayOf(key,epoch,"manager","[]","cursor-eight"))
            db.execSQL("INSERT INTO scope_access(scope,greenhouse_id) VALUES(?,?)",arrayOf(key,gh))
            db.execSQL("INSERT INTO local_records(scope,entity_id,kind,greenhouse_id,revision,body,latest_operation_id,sync_state,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",arrayOf(key,draft.id,InvestmentContract.SCENARIO,gh,1,cipher.encode(key,draft.localJson().toString()),opId,"QUEUED",1))
            db.execSQL("INSERT INTO outbox(scope,operation_id,entity_id,local_revision,envelope,canonical_hash,state,created_at) VALUES(?,?,?,?,?,?,?,?)",arrayOf(key,opId,draft.id,1,cipher.encode(key,envelope.toString()),hash,"QUEUED",1));db.version=8
        }
        val upgraded=FinanceDatabase(context,oldName)
        try {val migrated=LocalFinanceStore(upgraded,cipher);assertEquals(9,upgraded.readableDatabase.version);assertEquals("cursor-eight",migrated.pullCursor(key));assertEquals(hash,migrated.next(key)!!.hash);assertEquals(opId,migrated.next(key)!!.id);assertEquals(draft,InvestmentScenarioDraft.from(draft.id,gh,migrated.record(key,draft.id)!!.body,null));assertEquals(1,migrated.records(key,kind=InvestmentContract.SCENARIO).size)}
        finally {upgraded.close();assertTrue(oldName.startsWith("finance-test-"));context.deleteDatabase(oldName)}
    }
}
