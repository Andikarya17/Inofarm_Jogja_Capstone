package com.inofarm.jogja.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.inofarm.jogja.core.design.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inofarm.jogja.feature.assets.*
import com.inofarm.jogja.feature.reports.*
import com.inofarm.jogja.feature.investments.*
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.R
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.activities.ActivityUiState
import com.inofarm.jogja.feature.activities.ActivityViewModel
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.activities.domain.MaterialDraft
import com.inofarm.jogja.feature.auth.AuthViewModel
import com.inofarm.jogja.feature.auth.FinanceLogin
import com.inofarm.jogja.feature.context.ContextViewModel
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.context.HomeContext
import com.inofarm.jogja.feature.context.HomeContextCard
import com.inofarm.jogja.feature.context.WorkerContextSheet
import com.inofarm.jogja.feature.context.greenhouseAvailability
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.history.*
import com.inofarm.jogja.feature.verification.ActivityVerificationRepository
import com.inofarm.jogja.feature.verification.ActivityVerificationScreen
import com.inofarm.jogja.feature.harvest.*
import com.inofarm.jogja.feature.expenses.*
import com.inofarm.jogja.feature.attachments.*
import com.inofarm.jogja.feature.sales.*
import com.inofarm.jogja.feature.managerhome.*
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.util.Locale

private fun <T:ViewModel> factory(create:()->T)=object:ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <V:ViewModel> create(modelClass:Class<V>):V=create() as V
}
internal fun SyncState.label()=when(this) {
    SyncState.LOCAL -> "Disimpan lokal";SyncState.QUEUED -> "Menunggu pengiriman";SyncState.ACCEPTED -> "Diterima server"
    SyncState.CONFLICT -> "Konflik versi";SyncState.REJECTED -> "Perlu diperbaiki";SyncState.AUTH_REQUIRED -> "Perlu masuk kembali"
    SyncState.SCOPE_REVOKED -> "Penugasan perlu ditinjau";SyncState.EPOCH_REVIEW -> "Perlu rekonsiliasi"
}
internal fun timeLabel(value:String)=runCatching { OffsetDateTime.parse(value).atZoneSameInstant(ActivityDraft.WIB).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'WIB'",Locale.forLanguageTag("id-ID"))) }.getOrDefault(value)
internal fun LocalRecord.recorderId():String=body.optString("recorded_by").ifBlank {serverBody?.optString("recorded_by").orEmpty()}

@Composable fun FinanceRoot(runtime:FinanceRuntime) {
    val auth:AuthViewModel=viewModel(factory=remember(runtime){factory { AuthViewModel(runtime.auth) }})
    val context:ContextViewModel=viewModel(factory=remember(runtime){factory { ContextViewModel(runtime.context) }})
    val activities:ActivityViewModel=viewModel(factory=remember(runtime){factory { ActivityViewModel(runtime.activities) }})
    val history:HistoryViewModel=viewModel(factory=remember(runtime){factory { HistoryViewModel(runtime.history) }})
    val harvest:HarvestViewModel=viewModel(factory=remember(runtime){factory {HarvestViewModel(runtime.harvest)}})
    val harvestHistory:HistoryViewModel=viewModel(key="harvest-history",factory=remember(runtime){factory {HistoryViewModel(runtime.harvestHistory)}})
    val financial:FinancialEntryViewModel=viewModel(factory=remember(runtime){factory {FinancialEntryViewModel(runtime.financialEntry)}})
    val expenseHistory:HistoryViewModel=viewModel(key="expense-history",factory=remember(runtime){factory {HistoryViewModel(runtime.expenseHistory)}})
    val allocationHistory:HistoryViewModel=viewModel(key="allocation-history",factory=remember(runtime){factory {HistoryViewModel(runtime.allocationHistory)}})
    val attachments:AttachmentViewModel=viewModel(factory=remember(runtime){factory {AttachmentViewModel(runtime.attachments,runtime.viewer)}})
    val attachmentState by attachments.state.collectAsStateWithLifecycle()
    val sales:SaleEntryViewModel=viewModel(factory=remember(runtime){factory {SaleEntryViewModel(runtime.sales)}})
    val saleHistory:HistoryViewModel=viewModel(key="sale-history",factory=remember(runtime){factory {HistoryViewModel(runtime.saleHistory)}})
    val investments:InvestmentViewModel=viewModel(factory=remember(runtime){factory {InvestmentViewModel(runtime.investments)}})
    val investmentState by investments.state.collectAsStateWithLifecycle()
    val investmentWorkspace=InvestmentWorkspace(investmentState,investments)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {investments.pauseAutosave();val session=auth.state.value.session;if(session!=null) investments.persist(session,runtime.vault.generation,investments.state.value)}
    val reports:SeasonReportViewModel=viewModel(factory=remember(runtime){factory {SeasonReportViewModel(runtime.reports)}})
    val reportState by reports.state.collectAsStateWithLifecycle()
    val reportWorkspace=SeasonReportWorkspace(reportState,reports)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {reports.pauseAutosave();val session=auth.state.value.session;val draft=reports.state.value.draft;if(session!=null && draft!=null) reports.persist(session,runtime.vault.generation,draft)}
    val assets:AssetEntryViewModel=viewModel(factory=remember(runtime){factory {AssetEntryViewModel(runtime.assets)}})
    val assetHistory:HistoryViewModel=viewModel(key="asset-history",factory=remember(runtime){factory {HistoryViewModel(runtime.assetHistory)}})
    val depreciationHistory:HistoryViewModel=viewModel(key="depreciation-history",factory=remember(runtime){factory {HistoryViewModel(runtime.depreciationHistory)}})
    val assetState by assets.state.collectAsStateWithLifecycle();val assetHistoryState by assetHistory.state.collectAsStateWithLifecycle();val depreciationHistoryState by depreciationHistory.state.collectAsStateWithLifecycle()
    val assetWorkspace=AssetWorkspace(assetState,assets,assetHistoryState,assetHistory,depreciationHistoryState,depreciationHistory)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {assets.pauseAutosave();val session=auth.state.value.session;val snapshot=assets.state.value.draft;if(session!=null && snapshot!=null) assets.persist(session,runtime.vault.generation,snapshot)}
    val saleState by sales.state.collectAsStateWithLifecycle();val saleHistoryState by saleHistory.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle();val contextState by context.state.collectAsStateWithLifecycle();val activityState by activities.state.collectAsStateWithLifecycle()
    val historyState by history.state.collectAsStateWithLifecycle()
    val harvestState by harvest.state.collectAsStateWithLifecycle();val harvestHistoryState by harvestHistory.state.collectAsStateWithLifecycle()
    val financialState by financial.state.collectAsStateWithLifecycle();val expenseHistoryState by expenseHistory.state.collectAsStateWithLifecycle();val allocationHistoryState by allocationHistory.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { runtime.viewer.clearViews();if(authState.session!=null) context.refresh() }
    LaunchedEffect(authState.session?.scope?.key,authState.session?.role) {attachments.bind(authState.session)}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {attachments.pauseAutosave();val snapshot=attachments.formSnapshot();val session=auth.state.value.session;val ticket=runtime.vault.generation;if(snapshot!=null && session!=null) attachments.persistForm(session,ticket,snapshot)}
    LaunchedEffect(contextState.lastSyncedAt) {if(attachmentState.parent!=null) attachments.reload()}
    LaunchedEffect(attachmentState.changed) {if(attachmentState.changed>0) {activities.reload();context.refresh()}}
    LaunchedEffect(authState.session?.scope?.key,authState.session?.role) { context.bind(authState.session);activities.bind(authState.session);history.bind(authState.session);harvest.bind(authState.session);harvestHistory.bind(authState.session) }
    LaunchedEffect(authState.session?.scope?.key,authState.session?.role) {val manager=authState.session?.takeIf {it.role==UserRole.MANAGER};financial.bind(manager);expenseHistory.bind(manager);allocationHistory.bind(manager);sales.bind(manager);saleHistory.bind(manager);assets.bind(manager);assetHistory.bind(manager);depreciationHistory.bind(manager);reports.bind(manager);investments.bind(manager)}
    LaunchedEffect(contextState.loading,contextState.message) { if(authState.session!=null && !contextState.loading) {activities.reload();history.reload();harvest.reload();harvestHistory.reload()} }
    LaunchedEffect(activityState.records,historyState.changed) {if(authState.session!=null) history.reload()}
    LaunchedEffect(historyState.changed) {if(historyState.changed>0 && authState.session!=null) {activities.reload();context.refresh()} }
    LaunchedEffect(harvestState.records) {if(authState.session!=null) harvestHistory.reload()}
    LaunchedEffect(harvestState.changed,harvestHistoryState.changed) {if((harvestState.changed>0 || harvestHistoryState.changed>0) && authState.session!=null) {activities.reload();context.refresh()} }
    LaunchedEffect(contextState.lastSyncedAt) {if(authState.session?.role==UserRole.MANAGER) {expenseHistory.reload();allocationHistory.reload();saleHistory.reload();assetHistory.reload();depreciationHistory.reload();reports.reload();investments.reload()}}
    LaunchedEffect(financialState.changed,expenseHistoryState.changed,allocationHistoryState.changed,saleState.changed,saleHistoryState.changed) {
        if(authState.session?.role==UserRole.MANAGER) {expenseHistory.reload();allocationHistory.reload();saleHistory.reload();if(financialState.changed>0 || expenseHistoryState.changed>0 || allocationHistoryState.changed>0 || saleState.changed>0 || saleHistoryState.changed>0) {activities.reload();context.refresh()}}
    }
    LaunchedEffect(assetState.changed,assetHistoryState.changed,depreciationHistoryState.changed) {if(authState.session?.role==UserRole.MANAGER) {assetHistory.reload();depreciationHistory.reload();if(assetState.changed>0 || assetHistoryState.changed>0 || depreciationHistoryState.changed>0) {activities.reload();context.refresh()}}}
    LaunchedEffect(reportState.changed) {if(reportState.changed>0 && authState.session?.role==UserRole.MANAGER) {activities.reload();context.refresh();reports.reload()}}
    LaunchedEffect(investmentState.changed) {if(investmentState.changed>0 && authState.session?.role==UserRole.MANAGER) {activities.reload();context.refresh();investments.reload()}}
    val pending=activityState.queue.firstOrNull { it.state=="QUEUED" }?.id
    LaunchedEffect(pending,authState.session?.scope?.key) { if(pending!=null && authState.session!=null) context.refresh() }
    val logout:()->Unit={
        val session=auth.state.value.session
        if(session!=null) {
            activities.pauseAutosave();harvest.pauseAutosave();financial.pauseAutosave();attachments.pauseAutosave();sales.pauseAutosave();assets.pauseAutosave();reports.pauseAutosave();investments.pauseAutosave()
            val investmentSnapshot=investments.state.value
            val draft=activities.state.value.draft;val harvestDraft=harvest.state.value.draft;val ticket=runtime.vault.generation
            val financialDraft=financial.state.value.draft;val attachmentForm=attachments.formSnapshot();val saleDraft=sales.state.value.draft;val assetDraft=assets.state.value.draft;val reportDraft=reports.state.value.draft
            auth.logout {
                check(runtime.vault.matches(ticket,session.scope)) {"Sesi berubah sebelum penyimpanan."}
                draft?.let {runtime.activities.flush(session,ticket,it)}
                harvestDraft?.let {runtime.harvest.flush(session,ticket,it)}
                financialDraft?.let {runtime.financialEntry.flush(session,ticket,it)}
                attachmentForm?.let {attachments.flush(session,ticket,it)}
                saleDraft?.let {runtime.sales.flush(session,ticket,it)}
                assetDraft?.let {runtime.assets.flush(session,ticket,it)}
                reportDraft?.let {runtime.reports.flush(session,ticket,it)}
                investments.flush(session,ticket,investmentSnapshot)
                runtime.viewer.clearViews()
                check(runtime.vault.matches(ticket,session.scope)) {"Sesi berubah selama penyimpanan."}
            }
        }
    }
    var adminOpen by rememberSaveable(authState.session?.scope?.key) {mutableStateOf(false)}
    val openAdmin:()->Unit={adminOpen=true}
    BackHandler(authState.loading && authState.session!=null) { /* Wait for the bounded local persistence boundary. */ }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        if(authState.session==null) {
            val configured = runtime.configuredLoginEndpoint.value
            val endpoint = remember(configured) { runCatching { runtime.api.validateBase(configured) }.getOrNull() }
            FinanceLogin(authState, endpoint, auth::login)
        }
        else if(authState.loading) Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.Center) {CircularProgressIndicator();Spacer(Modifier.height(16.dp));Text("Menyimpan isian terakhir sebelum keluar…")}
        else {
            val session = requireNotNull(authState.session)
            key(session.scope.key, session.role, session.token) {
                Box(Modifier.fillMaxSize().farmFadeEntrance()) {
                    CompositionLocalProvider(LocalAttachmentAction provides attachments::open) {
                        com.inofarm.jogja.feature.exports.ReportExportHost(session, runtime.reportExports) {
                            com.inofarm.jogja.feature.recovery.RecoveryHost(session, runtime.recovery, activities::reload) {
                                FinanceHome(session, contextState, activityState, context::refresh, logout, activities,
                                    historyState, history, harvestState, harvest, harvestHistoryState, harvestHistory,
                                    financialState, financial, expenseHistoryState, expenseHistory, allocationHistoryState,
                                    allocationHistory, attachmentState, attachments, runtime.vault.generation, saleState,
                                    sales, saleHistoryState, saleHistory, assetWorkspace, reportWorkspace,
                                    investmentWorkspace, openAdmin, { adminOpen = false })
                            }
                        }
                    }
                }
            }
        }
    }
    if(adminOpen && authState.session?.role==UserRole.MANAGER && authState.session?.rejected==false) com.inofarm.jogja.feature.admin.AdminHost(authState.session!!,runtime.admin,context::refresh) {adminOpen=false}
    if(authState.session!=null && !authState.loading && authState.error!=null) AlertDialog(onDismissRequest=auth::clearError,title={Text("Belum keluar")},text={Text(authState.error!!)},confirmButton={TextButton(onClick=auth::clearError) {Text("Kembali ke isian")}})
}

@Composable private fun FinanceHome(session:FinanceSession,context:CultivationUiState,activity:ActivityUiState,onSync:()->Unit,onLogout:()->Unit,vm:ActivityViewModel,history:HistoryUiState,historyVm:HistoryViewModel,
    harvest:HarvestUiState,harvestVm:HarvestViewModel,harvestHistory:HistoryUiState,harvestHistoryVm:HistoryViewModel,financial:FinancialEntryState,financialVm:FinancialEntryViewModel,
    expenseHistory:HistoryUiState,expenseHistoryVm:HistoryViewModel,allocationHistory:HistoryUiState,allocationHistoryVm:HistoryViewModel,attachment:AttachmentUiState,attachmentVm:AttachmentViewModel,ticket:Long,sale:SaleEntryState,saleVm:SaleEntryViewModel,saleHistory:HistoryUiState,saleHistoryVm:HistoryViewModel,asset:AssetWorkspace,reports:SeasonReportWorkspace,investments:InvestmentWorkspace,onAdmin:()->Unit,onAccessDenied:()->Unit) {
    val runtime:FinanceRuntime=viewModel()
    val tabState = rememberSaveableStateHolder()
    var tab by rememberSaveable { mutableStateOf(0) };var gh by rememberSaveable { mutableStateOf<String?>(null) };var season by rememberSaveable { mutableStateOf<String?>(null) };var group by rememberSaveable { mutableStateOf<String?>(null) }
    var queueReviewError by rememberSaveable { mutableStateOf<String?>(null) }
    var queueReviewOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var queueFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var queueOpening by remember { mutableStateOf(false) }
    var showOutbox by rememberSaveable { mutableStateOf(false) }
    var reviewActivity by rememberSaveable { mutableStateOf<String?>(null) }
    val verification = remember(runtime) { ActivityVerificationRepository(runtime.api, runtime.store, runtime.vault) }
    val manager = session.role == UserRole.MANAGER
    var managerRefresh by remember { mutableLongStateOf(0L) }
    var periodReview by rememberSaveable { mutableStateOf(false) }
    var financeKind by rememberSaveable { mutableStateOf(OperationalKind.EXPENSE.wire) }
    var financeLink by rememberSaveable { mutableIntStateOf(0) }
    val homeRepository = remember(runtime) { ManagerHomeRepository(runtime.vault, runtime.store, runtime.api) }
    val syncUpdates by runtime.sync.updates.collectAsStateWithLifecycle()
    val managerHome = if (manager) rememberManagerHome(homeRepository, session, ticket, gh, syncUpdates, activity.records, managerRefresh)
        else ManagerHomeState()
    if (session.rejected || managerHome.sessionRejected) {
        LaunchedEffect(Unit) { onAccessDenied() }
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(FarmSpace.Page),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            FarmPageTitle("Periksa akses akun")
            FarmPanel {
                Text("Akses pengelola telah berubah. Masuk kembali dengan akun yang sama untuk memeriksa penugasan.")
                Text("Draf dan antrean tetap tersimpan di perangkat.")
                Button(onClick = onLogout, shape = FarmShape.Control,
                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-home-relogin")) { Text("Keluar dari sesi ini") }
            }
        }
        return
    }
    LaunchedEffect(managerHome.selectionRevoked) {
        if (managerHome.selectionRevoked) {
            gh = null
            periodReview = false
        }
    }
    val homeSnapshot = managerHome.snapshot
    val runningGroup = homeSnapshot?.period?.text("group_id")
    val runningVersion = homeSnapshot?.selected?.getLong("version")
    val canRecordFromHome = gh != null && runningGroup != null && runningVersion != null && !session.expired()
    val openHomeDestination: (HomeDestination) -> Unit = { destination ->
        val target = when (destination.kind) {
            OperationalKind.ACTIVITY -> historyVm
            OperationalKind.HARVEST -> harvestHistoryVm
            OperationalKind.SALE -> saleHistoryVm
            else -> expenseHistoryVm
        }
        val destinationTab = when (destination.kind) {
            OperationalKind.ACTIVITY -> 2
            OperationalKind.HARVEST -> 3
            else -> 4
        }
        target.filter(destination.filter, destination.seasonLabelId)
        if (destinationTab == 4) {
            financeKind = destination.kind.wire
            financeLink++
        }
        val id = destination.recordId
        if (id == null) tab = destinationTab
        else target.select(id) { error -> if (error != null) tab = destinationTab }
    }
    val queueHistory = buildMap {
        put(OperationalKind.ACTIVITY.wire, historyVm)
        put(OperationalKind.HARVEST.wire, harvestHistoryVm)
        if (manager) {
            put(OperationalKind.EXPENSE.wire, expenseHistoryVm)
            put(OperationalKind.COST_ALLOCATION.wire, allocationHistoryVm)
            put(OperationalKind.SALE.wire, saleHistoryVm)
            put(OperationalKind.ASSET.wire, asset.historyVm)
            put(OperationalKind.DEPRECIATION.wire, asset.depreciationVm)
        }
    }
    val investmentQueueKinds = setOf("investment_scenario", "investment_report")
    val writing=investments.state.busy || reports.state.busy || asset.entry.busy || asset.history.busy || asset.depreciation.busy || sale.busy || saleHistory.busy || attachment.busy || activity.busy || harvest.busy || history.busy || harvestHistory.busy || financial.busy || expenseHistory.busy || allocationHistory.busy
    val keyboard=LocalSoftwareKeyboardController.current;val ime=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val selectedGreenhouse = context.greenhouses.find { it.optString("id") == gh && it.optBoolean("active", true) }
    val selectedSeason = context.seasons.find { it.optString("id") == season && it.optString("greenhouse_id") == gh }
    val selectedGroup = context.groups.find { it.optString("id") == group && it.optString("season_id") == season && it.optBoolean("active", true) }
    var contextSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var contextChange by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(context.lastSyncedAt, context.greenhouses, context.seasons, context.groups) {
        if (!manager && (context.lastSyncedAt != null || context.greenhouses.isNotEmpty())) {
            if (gh != null && selectedGreenhouse == null) {
                contextChange = if (context.greenhouses.any { it.optString("id") == gh })
                    "Greenhouse yang dipilih sekarang nonaktif. Draf tetap tersimpan."
                else "Penugasan untuk greenhouse yang dipilih sudah tidak berlaku. Draf tetap tersimpan."
                gh = null
            }
            if (gh == null || selectedSeason == null) season = null
            if (season == null || selectedGroup == null) group = null
        }
    }
    BackHandler(investments.state.active || reports.state.active || asset.entry.draft!=null || asset.history.selected!=null || asset.depreciation.selected!=null || sale.draft!=null || saleHistory.selected!=null || attachment.parent!=null || writing || activity.draft!=null || harvest.draft!=null || financial.draft!=null || history.selected!=null || harvestHistory.selected!=null || expenseHistory.selected!=null || allocationHistory.selected!=null || tab!=0) {
        when {
            writing || queueOpening -> Unit
            ime -> keyboard?.hide()
            investments.state.active -> investments.vm.back(tab==1)
            reports.state.active -> reports.vm.back(tab==1)
            asset.entry.draft!=null && asset.entry.reviewing -> asset.vm.backToForm()
            asset.entry.draft!=null -> asset.vm.saveAndClose()
            asset.history.comparison!=null -> asset.historyVm.closeComparison()
            asset.history.selected!=null -> asset.historyVm.close()
            asset.depreciation.comparison!=null -> asset.depreciationVm.closeComparison()
            asset.depreciation.selected!=null -> asset.depreciationVm.close()
            attachment.parentReview!=null || attachment.reviewing -> attachmentVm.backToDetails()
            attachment.selected!=null -> attachmentVm.closeSelected()
            attachment.parent!=null -> attachmentVm.close()
            sale.draft!=null && sale.reviewing -> saleVm.backToForm()
            sale.draft!=null -> saleVm.saveAndClose()
            saleHistory.comparison!=null -> saleHistoryVm.closeComparison()
            saleHistory.selected!=null -> saleHistoryVm.close()
            activity.draft!=null && activity.reviewing -> vm.backToForm()
            activity.draft!=null -> vm.saveAndClose()
            harvest.draft!=null && harvest.reviewing -> harvestVm.backToForm()
            harvest.draft!=null -> harvestVm.saveAndClose()
            financial.draft!=null && financial.reviewing -> financialVm.backToForm()
            financial.draft!=null -> financialVm.saveAndClose()
            expenseHistory.comparison!=null -> expenseHistoryVm.closeComparison()
            expenseHistory.selected!=null -> expenseHistoryVm.close()
            allocationHistory.comparison!=null -> allocationHistoryVm.closeComparison()
            allocationHistory.selected!=null -> allocationHistoryVm.close()
            history.comparison!=null -> historyVm.closeComparison()
            history.selected!=null -> historyVm.close()
            harvestHistory.comparison!=null -> harvestHistoryVm.closeComparison()
            harvestHistory.selected!=null -> harvestHistoryVm.close()
            tab == 1 && manager && showOutbox -> showOutbox = false
            tab == 1 && reviewActivity != null -> reviewActivity = null
            else -> tab=0
        }
    }
    val showNavigation = !(tab == 1 && !showOutbox && reviewActivity != null) && !investments.state.active && !reports.state.active && asset.entry.draft==null && asset.history.selected==null && asset.depreciation.selected==null && sale.draft==null && saleHistory.selected==null && attachment.parent==null && activity.draft==null && harvest.draft==null && financial.draft==null && history.selected==null && harvestHistory.selected==null && expenseHistory.selected==null && allocationHistory.selected==null
    val sessionExpired = session.expired()
    val workerReady = selectedGreenhouse != null && selectedGroup != null &&
        selectedSeason?.optString("status") == "open" && context.check != ContextCheck.ACCESS_CHANGED
    fun recordWorker(destination: String) {
        val greenhouse = selectedGreenhouse?.getString("id")
        val plantingGroup = selectedGroup?.getString("id")
        if (!workerReady || greenhouse == null || plantingGroup == null) {
            contextSheet = destination
            return
        }
        contextSheet = null
        if (destination == "harvest") harvestVm.new(greenhouse, plantingGroup)
        else vm.new(greenhouse, plantingGroup, if (destination == "measurement") ActivityType.MEASUREMENT else ActivityType.GENERAL)
    }
    if (!manager) contextSheet?.let { destination ->
        WorkerContextSheet(context, HomeContext(gh, season, group), when (destination) {
            "harvest" -> "Catat panen"
            "activity" -> "Catat kegiatan"
            "measurement" -> "Catat pengukuran"
            else -> null
        }, onSelect = {
            gh = it.greenhouse
            season = it.season
            group = it.group
            contextChange = null
        }, onClose = { contextSheet = null }, onContinue = { recordWorker(destination) }, onSync = onSync)
    }
    val translucentNavigation = tab == 0 && showNavigation && !writing && LocalDensity.current.fontScale <= 1.4f &&
        android.os.Build.VERSION.SDK_INT >= 31 && androidx.compose.ui.platform.LocalView.current.isHardwareAccelerated
    Scaffold(
        contentWindowInsets=WindowInsets.safeDrawing,topBar={ Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal=FarmSpace.Page)
        ) {
        if((attachment.parent!=null && (ime || LocalDensity.current.fontScale>1.5f || LocalConfiguration.current.screenHeightDp<400)) || (ime && (investments.state.scenarioDraft!=null || investments.state.reportDraft!=null || investments.state.cancelReason!=null || reports.state.draft!=null || asset.entry.draft!=null || sale.draft!=null || activity.draft!=null || harvest.draft!=null || financial.draft!=null))) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(if(attachment.parent!=null) "Lampiran" else if(investments.state.active) "Investasi" else if(reports.state.draft!=null) "Evaluasi" else if(asset.entry.draft!=null) "Aset" else if(sale.draft!=null) "Penjualan" else if(financial.draft!=null && LocalDensity.current.fontScale>1.5f) "Isian" else if(financial.draft!=null) "Catat ${financial.draft.kind.label}" else if(harvest.draft!=null) "Catat panen" else "Catat kegiatan",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={keyboard?.hide();if(attachment.parent!=null) {if(attachment.selected!=null) attachmentVm.closeSelected() else attachmentVm.close()} else if(investments.state.active) investments.vm.back() else if(reports.state.draft!=null) reports.vm.back() else if(asset.entry.draft!=null) asset.vm.saveAndClose() else if(sale.draft!=null) saleVm.saveAndClose() else if(financial.draft!=null) financialVm.saveAndClose() else if(harvest.draft!=null) harvestVm.saveAndClose() else vm.saveAndClose()},enabled=!writing) {Text(if(attachment.parent!=null) "Kembali" else "Simpan draf")} }
        } else {
            FinanceToolbar(session, context.loading, writing, sessionExpired, onSync, onLogout, onAdmin)
        }
    } },bottomBar={
        if (showNavigation) FinanceNavigation(tab, manager, translucentNavigation) {
            if (!queueOpening) {
                queueFocus = null
                if (it != tab) { showOutbox = false; reviewActivity = null }
                tab = it
            }
        }
    }) { padding ->
        val menuViewport = showNavigation && !writing
        if (menuViewport && tab == 0) BotanicalBackdrop(R.drawable.background_dashboard, accentHeight = 152.dp)
        val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val contentPadding = if (translucentNavigation) {
            PaddingValues(start = padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                end = padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                top = padding.calculateTopPadding(), bottom = systemBottom)
        } else padding
        Box(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(padding).imePadding().padding(horizontal=FarmSpace.Page),
            contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
            when {
                session.role==UserRole.MANAGER && investments.state.active -> InvestmentScreen(investments,context,returnToQueue=tab==1)
                session.role==UserRole.MANAGER && reports.state.active -> SeasonReportScreen(reports,context,returnToQueue=tab==1)
                attachment.parent!=null -> AttachmentScreen(session,context,attachment,attachmentVm,ticket)
                asset.entry.busy || sale.busy || activity.busy || harvest.busy || financial.busy -> Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center) {CircularProgressIndicator();Text("Menyimpan isian ke perangkat…")}
                session.role==UserRole.MANAGER && asset.entry.draft!=null -> if(asset.entry.reviewing) FarmReviewEntrance { AssetReview(asset.entry.draft,context,asset.entry.busy,asset.vm::backToForm,asset.vm::queue) } else AssetForm(asset.entry,context,asset.vm)
                session.role==UserRole.MANAGER && asset.history.selected!=null -> HistoryScreen(session,context,asset.history,asset.historyVm,activity.queue,onEdit={record,replace -> asset.vm.open(record,replace);asset.historyVm.close()},kind=OperationalKind.ASSET,returnToQueue=tab==1)
                session.role==UserRole.MANAGER && asset.depreciation.selected!=null -> HistoryScreen(session,context,asset.depreciation,asset.depreciationVm,activity.queue,onEdit={record,replace -> asset.vm.open(record,replace);asset.depreciationVm.close()},kind=OperationalKind.DEPRECIATION,returnToQueue=tab==1)
                activity.draft!=null -> if(activity.reviewing) FarmReviewEntrance { ActivityReview(activity.draft,context,activity.busy,vm::backToForm,vm::queue) } else ActivityForm(activity,context,vm)
                harvest.draft!=null -> if(harvest.reviewing) FarmReviewEntrance { HarvestReview(harvest.draft,context,harvest.busy,harvestVm::backToForm,harvestVm::queue) } else HarvestForm(harvest,context,harvestVm)
                session.role==UserRole.MANAGER && sale.draft!=null -> if(sale.reviewing) FarmReviewEntrance { SaleReview(sale.draft,context,sale.busy,saleVm::backToForm,saleVm::queue) } else SaleForm(sale,context,saleVm)
                session.role==UserRole.MANAGER && saleHistory.selected!=null -> HistoryScreen(session,context,saleHistory,saleHistoryVm,activity.queue,onEdit={record,replace -> saleVm.open(record,replace);saleHistoryVm.close()},kind=OperationalKind.SALE,returnToQueue=tab==1)
                session.role==UserRole.MANAGER && financial.draft!=null -> if(financial.reviewing) FarmReviewEntrance { FinancialReview(financial.draft,context,financial.busy,financialVm::backToForm,financialVm::queue) } else FinancialForm(financial,context,financialVm)
                session.role==UserRole.MANAGER && expenseHistory.selected!=null -> HistoryScreen(session,context,expenseHistory,expenseHistoryVm,activity.queue,onEdit={record,replace -> financialVm.open(record,replace);expenseHistoryVm.close()},kind=OperationalKind.EXPENSE,returnToQueue=tab==1)
                session.role==UserRole.MANAGER && allocationHistory.selected!=null -> HistoryScreen(session,context,allocationHistory,allocationHistoryVm,activity.queue,onEdit={record,replace -> financialVm.open(record,replace);allocationHistoryVm.close()},kind=OperationalKind.COST_ALLOCATION,returnToQueue=tab==1)
                history.selected!=null -> HistoryScreen(session,context,history,historyVm,activity.queue,onEdit={record,replace -> vm.open(record,replace);historyVm.close()},returnToQueue=tab==1)
                harvestHistory.selected!=null -> HistoryScreen(session,context,harvestHistory,harvestHistoryVm,activity.queue,onEdit={record,replace -> harvestVm.open(record,replace);harvestHistoryVm.close()},kind=OperationalKind.HARVEST,returnToQueue=tab==1)
                tab == 0 && manager -> tabState.SaveableStateProvider("manager-home-${gh.orEmpty()}") {
                    ManagerHomeScreen(gh, managerHome, activity.queue, PaddingValues(
                        top = FarmSpace.Medium,
                        bottom = FarmSpace.Large + if (translucentNavigation) (padding.calculateBottomPadding() - systemBottom).coerceAtLeast(0.dp) else 0.dp,
                    ), periodReview, { periodReview = it }, { id -> gh = id; periodReview = false }, {
                        managerRefresh++
                        onSync()
                    }, openHomeDestination, { operation -> queueFocus = operation.id; showOutbox = true; tab = 1 },
                        onRecord = { type, schedule ->
                            if (canRecordFromHome) vm.new(gh!!, runningGroup!!, type, schedule, runningVersion)
                        },
                        onHarvest = { if (canRecordFromHome) harvestVm.new(gh!!, runningGroup!!, runningVersion) },
                        onAdmin = onAdmin,
                        seasonActions = { row -> SeasonActions(row, history.busy, history.message, historyVm::season) },
                        onDesignate = { groupId, reason ->
                            try {
                                homeRepository.designate(session, ticket, requireNotNull(homeSnapshot), groupId, reason)
                            } finally {
                                managerRefresh++
                                onSync()
                            }
                        })
                }
                tab==0 -> tabState.SaveableStateProvider(0) { LazyColumn(
                    modifier=Modifier.fillMaxSize().testTag("finance-home-list"),
                    verticalArrangement=Arrangement.spacedBy(FarmSpace.Medium),
                    contentPadding=PaddingValues(
                        top = 12.dp,
                        bottom = FarmSpace.Large + if (translucentNavigation) {
                            (padding.calculateBottomPadding() - systemBottom).coerceAtLeast(0.dp)
                        } else 0.dp
                    )
                ) {
                    item("context") {
                        HomeContextCard(context, HomeContext(gh, season, group)) { contextSheet = "context" }
                    }
                    contextChange?.let { message -> item("context-changed") { Notice(message) } }
                    context.greenhouseAvailability()?.let { message -> item("context-availability") { Notice(message) } }
                    if (sessionExpired) item("expired-session") { ExpiredSessionNotice() }
                    item("recording") {
                        Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                            if (selectedSeason?.optString("status") == "closed")
                                FarmAttention("Musim ditutup. Pilih musim terbuka untuk mencatat.")
                            if (workerReady) Button(onClick={recordWorker("measurement")},shape=FarmShape.Control,
                                modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("finance-measurement-new")) {Text("Catat pengukuran")}
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
                                FilledTonalButton(onClick={recordWorker("activity")},shape=FarmShape.Control,modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("finance-new")) {
                                    FarmNavigationIcon(2); Spacer(Modifier.width(6.dp)); Text("Catat kegiatan")
                                }
                                FilledTonalButton(onClick={recordWorker("harvest")},shape=FarmShape.Control,modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("harvest-new")) {
                                    FarmNavigationIcon(3); Spacer(Modifier.width(6.dp)); Text("Catat panen")
                                }
                            }
                        }
                    }
                    if (selectedGreenhouse != null && selectedSeason != null && selectedGroup != null) {
                        val greenhouseId = selectedGreenhouse.getString("id")
                        val groupId = selectedGroup.getString("id")
                        val open = selectedSeason.optString("status") == "open"
                        item("measurements") {
                            com.inofarm.jogja.feature.activities.MeasurementDashboard(
                                runtime, session, greenhouseId, groupId, activity.records, context.lastSyncedAt,
                                context.message.takeUnless { sessionExpired }
                            )
                        }
                        val blocked = activity.queue.count { it.state in setOf("REJECTED", "CONFLICT", "AUTH_REQUIRED", "SCOPE_REVOKED", "EPOCH_REVIEW") }
                        if (blocked > 0) item("queue-attention") {
                            FarmAttention("$blocked pengajuan perlu ditinjau. Buka Antrean.")
                        }
                        val schedules=context.schedules.filter { it.optString("planting_group_id")==group && it.optBoolean("active",true) }
                        item {
                            FarmPanel(title = "Jadwal kegiatan") {
                                if (schedules.isEmpty())
                                    Text("Belum ada jadwal untuk kelompok ini. Kegiatan spontan tetap dapat dicatat.",
                                        style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        items(schedules,key={it.getString("id")}) { schedule -> ElevatedCard { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(ActivityType.entries.find { it.wire==schedule.optString("activity_type") }?.label ?: "Kegiatan");Text(timeLabel(schedule.optString("planned_at")));Text(schedule.optString("notes"));Text("Satu catatan tidak otomatis menyelesaikan seluruh jadwal.",style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={vm.new(gh!!,group!!,ActivityType.entries.single { it.wire==schedule.getString("activity_type") },schedule.getString("id"))},enabled=open) {Text("Catat realisasi")} } } }
                        item { Notice("Target dan acuan tersedia saat mengisi kegiatan. Hasil pengukuran dicatat manual.") }
                    }
                    if (selectedGroup == null) item("sync") {
                        FarmPanel(title = "Sinkronisasi") {
                            if (!sessionExpired) context.message?.let {
                                Text(it, Modifier.testTag("finance-sync-message"), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(context.lastSyncedAt?.let { "Sinkron terakhir: ${timeLabel(Instant.ofEpochMilli(it).toString())}" }
                                ?: "Belum ada data server tersimpan.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item { activity.message?.let { Notice(it) } }
                } }
                tab == 1 && manager && !showOutbox -> tabState.SaveableStateProvider("verification") {
                    ActivityVerificationScreen(session, ticket, context, verification, syncUpdates, reviewActivity,
                        { reviewActivity = it }, { showOutbox = true }, onSync)
                }
                tab==1 -> tabState.SaveableStateProvider(queueFocus?.let { "queue-$it" } ?: 1) {
                    QueueScreen(activity, context, sessionExpired, queueReviewOperation, queueReviewError, queueOpening, vm::reload,
                        onReview = { op ->
                            queueReviewError = null
                            queueReviewOperation = op.id
                            val kind = op.envelope.optString("entity_type")
                            val destination = queueHistory[kind]
                            when {
                                destination != null -> {
                                    queueOpening = true
                                    destination.select(op.entityId) { error ->
                                        queueOpening = false
                                        queueReviewError = error
                                    }
                                }
                                manager && kind in investmentQueueKinds -> investments.vm.select(op.entityId,fromQueue=true)
                                manager && kind == "season_report" -> reports.vm.select(op.entityId)
                                manager && kind == "activity_review" -> {
                                    reviewActivity = op.envelope.getJSONObject("payload").getString("activity_id")
                                    showOutbox = false
                                }
                            }
                        },
                        canReview = { op ->
                            val kind = op.envelope.optString("entity_type")
                            kind in queueHistory || manager && (kind in investmentQueueKinds || kind in setOf("season_report", "activity_review"))
                        }, focusOperation = queueFocus,
                        onVerification = if (manager) ({ showOutbox = false }) else null)
                }
                tab==4 && manager -> tabState.SaveableStateProvider(4) {
                    FinancialHub(session,context,financialVm,expenseHistory,expenseHistoryVm,allocationHistory,allocationHistoryVm,activity.queue,saleVm,saleHistory,saleHistoryVm,asset,reports,investments,onSync,financeKind,financeLink)
                }
                tab==2 || tab==3 -> tabState.SaveableStateProvider(tab) {
                    val isActivity = tab == 2
                    val canRecord = if (manager) canRecordFromHome else workerReady
                    HistoryScreen(session,context,if (isActivity) history else harvestHistory,
                        if (isActivity) historyVm else harvestHistoryVm,activity.queue,
                        onEdit={record,replace ->
                            if (isActivity) { vm.open(record,replace);historyVm.close() }
                            else { harvestVm.open(record,replace);harvestHistoryVm.close() }
                        },
                        kind=if (isActivity) OperationalKind.ACTIVITY else OperationalKind.HARVEST,
                        onRecord={
                            if (!manager) recordWorker(if (isActivity) "activity" else "harvest")
                            else if (!canRecord) tab=0
                            else {
                                if (isActivity) vm.new(gh!!, runningGroup!!, runningPeriodVersion = runningVersion)
                                else harvestVm.new(gh!!, runningGroup!!, runningVersion)
                            }
                        },
                        recordLabel=when {
                            manager && !canRecord -> "Lihat konteks di Beranda"
                            isActivity -> "Catat kegiatan"
                            else -> "Catat panen"
                        },
                        recordScope=if (canRecord && manager) listOfNotNull(homeSnapshot?.selected?.text("name"),homeSnapshot?.period?.text("season_name")).joinToString(" · ")
                        else if (canRecord) listOf(selectedGreenhouse,selectedSeason,selectedGroup)
                            .joinToString(" · ") { it!!.getString("name") } else null,
                        onSync=onSync)
                }
            }
        }
        }
    }
}

@Composable private fun FinanceToolbar(
    session: FinanceSession,
    syncing: Boolean,
    writing: Boolean,
    sessionExpired: Boolean,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onAdmin: () -> Unit
) {
    var accountOpen by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Inofarm", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (syncing && LocalDensity.current.fontScale <= 1.3f) Text(
            "Sinkronisasi",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        IconButton(onClick = onSync, enabled = !syncing, modifier = Modifier.testTag("finance-sync")) {
            FarmControlIcon(FarmIcon.Sync, if (syncing) "Memeriksa server" else "Sinkronkan")
        }
        Box {
            IconButton(onClick = { accountOpen = true }, modifier = Modifier.testTag("finance-account")) {
                FarmControlIcon(FarmIcon.Account, if (sessionExpired) "Menu akun. Sesi berakhir." else "Menu akun")
            }
            DropdownMenu(expanded = accountOpen, onDismissRequest = { accountOpen = false },
                containerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.widthIn(max = 248.dp).padding(horizontal = FarmSpace.Page, vertical = FarmSpace.Small)) {
                    Text(session.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (session.role == UserRole.WORKER) "Pekerja" else "Pengelola",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(horizontal = FarmSpace.Page))
                if (session.role == UserRole.MANAGER) {
                    DropdownMenuItem(
                        text = { Text("Administrasi") },
                        leadingIcon = { FarmControlIcon(FarmIcon.Account, null) },
                        onClick = { accountOpen = false; onAdmin() },
                        enabled = !writing,
                        modifier = Modifier.testTag("finance-admin")
                    )
                    HorizontalDivider(Modifier.padding(horizontal = FarmSpace.Page))
                }
                DropdownMenuItem(
                    text = { Text("Keluar") },
                    leadingIcon = { FarmControlIcon(FarmIcon.Logout, null) },
                    onClick = { accountOpen = false; onLogout() },
                    enabled = !writing,
                    modifier = Modifier.testTag("finance-logout")
                )
            }
        }
    }
    if (sessionExpired) Text(
        "Sesi berakhir",
        Modifier.padding(bottom = FarmSpace.Tiny).semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error
    )
}

@Composable
internal fun ExpiredSessionNotice() {
    Notice("Sesi berakhir. Masuk kembali dengan akun yang sama untuk mengirim antrean. Draf tetap dapat disimpan.", true)
}

@Composable private fun ActivityForm(state:ActivityUiState,context:CultivationUiState,vm:ActivityViewModel) {
    val draft=state.draft ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        val title = when {
            draft.baseVersion != null -> "Koreksi kegiatan"
            draft.type == ActivityType.MEASUREMENT -> "Catat pengukuran"
            else -> "Catat kegiatan"
        }
        FarmPageTitle(title)
        FarmDraftStatus(state.saved, Modifier.testTag("finance-draft-status"))
        FarmSection(title = "Waktu dan kelompok") {
            if (draft.runningPeriodVersion != null) ManagerDraftParents(context, draft.greenhouseId, draft.groupId)
            context.groups.find { it.optString("id") == draft.groupId }?.let { Text(it.optString("name")) }
            Choice("Jenis kegiatan",draft.type.wire,ActivityType.entries.map { JSONObject().put("id",it.wire).put("name",it.label) },"finance-type") { value -> vm.edit { it.copy(type=ActivityType.entries.single { type -> type.wire==value }) } }
            FinanceField("Tanggal kejadian (YYYY-MM-DD)",draft.date,{value -> vm.edit {it.copy(date=value)}},"finance-date",state.errors["occurred_at"])
            FinanceField("Jam kejadian WIB (HH:mm)",draft.time,{value -> vm.edit {it.copy(time=value)}},"finance-time",hint="Detik boleh ditulis HH:mm:ss dan tetap dipertahankan.")
        }
        FarmSection(title = "Pelaksanaan") {
            FarmCheckbox("Kegiatan dilewati", draft.skipped, { value -> vm.edit { it.copy(skipped = value) } },
                Modifier.testTag("finance-skipped"))
            if(draft.skipped) FinanceField("Alasan kegiatan dilewati",draft.skippedReason,{value -> vm.edit {it.copy(skippedReason=value)}},"finance-skipped-reason",state.errors["skipped_reason"],singleLine=false)
        }
        if(draft.type in setOf(ActivityType.MEASUREMENT,ActivityType.NUTRITION) || draft.ec.isNotBlank() || draft.ppm.isNotBlank() || draft.ph.isNotBlank()) {
            FarmSection(title = "Hasil ukur aktual") {
                FinanceField("EC manual (mS/cm)",draft.ec,{value -> vm.edit {it.copy(ec=value)}},"finance-ec",state.errors["manual_ec"],keyboard=KeyboardType.Decimal)
                FinanceField("PPM manual (ppm)",draft.ppm,{value -> vm.edit {it.copy(ppm=value)}},"finance-ppm",state.errors["manual_ppm"],KeyboardType.Decimal)
                FinanceField("pH manual (0–14)",draft.ph,{value -> vm.edit {it.copy(ph=value)}},"finance-ph",state.errors["manual_ph"],KeyboardType.Decimal)
            }
        }
        if(draft.type==ActivityType.STAGE || draft.stage.isNotBlank()) FinanceField("Kondisi stadia aktual",draft.stage,{value -> vm.edit {it.copy(stage=value)}},"finance-stage",state.errors["actual_stage"],hint="Dipilih dari kondisi aktual, bukan umur")
        if(draft.type in setOf(ActivityType.NUTRITION,ActivityType.SPRAY) || draft.materials.isNotEmpty()) {
            val materials=context.materials.filter {it.optString("greenhouse_id")==draft.greenhouseId}
            val units=context.units.filter {it.optString("greenhouse_id")==draft.greenhouseId}
            FarmSection(title = "Bahan aktual") {
            if(!draft.skipped && !context.loading && (materials.none {it.optBoolean("active",true)} || units.none {it.optBoolean("active",true)}))
                Notice("Bahan atau satuan aktif belum tersedia di perangkat untuk greenhouse ini. Sinkronkan atau minta pengelola menyiapkan master.")
            draft.materials.forEachIndexed { index,row -> FarmPanel {
                fun change(updated:MaterialDraft) { vm.edit {it.copy(materials=it.materials.toMutableList().apply {set(index,updated)})} }
                val material=materials.find {it.optString("id")==row.materialId}
                val compatibleUnits=units.filter {it.optString("dimension")==material?.optString("dimension") && (it.optBoolean("active",true) || it.optString("id")==row.unitId)}
                Choice("Bahan ${index+1}",row.materialId,materials.filter {it.optBoolean("active",true) || it.optString("id")==row.materialId},"finance-material-$index") {change(row.copy(materialId=it,unitId=""))}
                Choice("Satuan ${index+1}",row.unitId,compatibleUnits,"finance-unit-$index") {change(row.copy(unitId=it))}
                if(material!=null && compatibleUnits.isEmpty() && !context.loading)
                    Text("Belum ada satuan aktif dengan dimensi yang sesuai. Minta pengelola menyiapkannya.")
                FinanceField("Jumlah ${index+1}",row.quantity,{change(row.copy(quantity=it))},"finance-quantity-$index",state.errors["materials.$index"],KeyboardType.Decimal,hint="Desimal boleh koma; tanpa pemisah ribuan")
                TextButton(onClick={vm.edit {it.copy(materials=it.materials.filterIndexed { i,_ -> i!=index })}},
                    modifier=Modifier.heightIn(min=FarmSpace.Touch)) {Text("Hapus bahan ${index+1}")}
            } }
            OutlinedButton(onClick={vm.edit {it.copy(materials=it.materials+MaterialDraft())}},enabled=draft.materials.size<30,
                shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("finance-add-material")) {Text("Tambah bahan")}
            state.errors["materials"]?.let { Notice(it,true) }
            }
        }
        FarmSection(title = "Catatan lapangan") {
            if(draft.type==ActivityType.GENERAL) Text(
                "Untuk pengamatan tanaman, tulis tinggi dan lebar daun (cm) serta jumlah daun (helai). Catat DO (mg/L), suhu air (°C), atau pekerjaan seperti polinasi dan seleksi buah bila relevan.",
                style=MaterialTheme.typography.bodySmall
            )
            FinanceField("Keterangan pekerjaan",draft.notes,{value -> vm.edit {it.copy(notes=value)}},"finance-notes",state.errors["notes"],singleLine=false)
        }
        if(draft.baseVersion!=null) {
            FinanceField("Alasan koreksi",draft.reason,{value -> vm.edit {it.copy(reason=value)}},"finance-reason",state.errors["reason"])
            Choice("Acuan koreksi",draft.referencePolicy,listOf(JSONObject().put("id","preserve").put("name","Pertahankan tanggal/target historis"),JSONObject().put("id","refresh").put("name","Nilai ulang dengan acuan terkini")),"finance-reference-policy") {value -> vm.edit {it.copy(referencePolicy=value)}}
        }
        if(draft.baseVersion!=null && draft.referencePolicy=="preserve") {
            Notice("Koreksi mempertahankan tanggal dan target historis. Pilih nilai ulang bila acuan memang perlu diperbarui.")
            val historical=state.records.find {it.id==draft.id}?.serverBody?.optJSONObject("reference_snapshot")
            historical?.optJSONObject("age")?.let { age -> Text("HSS acuan: ${if(age.isNull("hss")) "belum tersedia" else age.optString("hss")} · HST acuan: ${if(age.isNull("hst")) "belum berlaku" else age.optString("hst")}") }
        } else ReferenceCards(context,draft.groupId,draft)
        state.message?.let { Notice(it,true) }
        state.errors.values.distinct().forEach { Notice(it,true) }
        Button(onClick=vm::review,enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("finance-review")) {Text("Periksa catatan")}
        OutlinedButton(onClick=vm::saveAndClose,enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("finance-save-draft")) {Text("Simpan draf dan kembali")}
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun ActivityReview(draft:ActivityDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        FarmPageTitle("Periksa catatan")
        if(draft.replaceValidationRejected) Notice("Perbaikan ini membuat operasi baru. Operasi yang ditolak tetap disimpan sebagai riwayat dan tidak dikirim ulang.")
        FarmPanel(title = draft.type.label) {
            if (draft.runningPeriodVersion != null) ManagerDraftParents(context, draft.greenhouseId, draft.groupId)
            FarmFact("Kelompok tanam", context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Nama belum tersedia")
            FarmFact("Waktu kejadian", "${draft.date} ${draft.time} WIB")
            if (draft.skipped) FarmFact("Alasan kegiatan dilewati", draft.skippedReason)
            else Text("Kegiatan selesai",style=MaterialTheme.typography.labelMedium)
            if(draft.ec.isNotBlank()) FarmFact("EC manual", "${draft.ec} mS/cm", prominent = true)
            if(draft.ppm.isNotBlank()) FarmFact("PPM manual", "${draft.ppm} ppm", prominent = true)
            if(draft.ph.isNotBlank()) FarmFact("pH manual", draft.ph)
            if(draft.stage.isNotBlank()) FarmFact("Stadia aktual", draft.stage)
            draft.materials.forEach { item ->
                val name=context.materials.find {it.optString("id")==item.materialId}?.optString("name") ?: "Bahan"
                val unit=context.units.find {it.optString("id")==item.unitId}?.optString("name").orEmpty()
                FarmFact(name, "${item.quantity} $unit".trim())
            }
            if (draft.notes.isNotBlank()) FarmFact("Keterangan", draft.notes)
            if(draft.reason.isNotBlank()) FarmFact("Alasan koreksi", draft.reason)
        }
        Notice("Catatan akan disimpan lokal dan diantrekan. Status diterima diberikan setelah server mengonfirmasi.")
        Button(onClick=onQueue,enabled=!busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("finance-queue")) {Text(if(busy) "Menyimpan…" else "Simpan lokal & antrekan")}
        OutlinedButton(onClick=onBack,enabled=!busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth()) {Text("Kembali ke isian")}
    }
}

@Composable internal fun RecordDetail(record:LocalRecord,session:FinanceSession,context:CultivationUiState,onClose:()->Unit,onEdit:()->Unit,kind:OperationalKind=OperationalKind.ACTIVITY,returnToQueue:Boolean=false,actions:@Composable ()->Unit={}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FarmPageTitle("Detail ${kind.label}")
        FarmPanel {
            RecordStatus(record.syncState)
            RecordFacts(record,session,context,kind)
            record.error?.let { ErrorReasons(it) }
        }
        val own=record.recorderId().let {it.isBlank() || it==session.scope.accountId}
        if(record.body.optString("record_state")!="cancelled" && (own || session.role==UserRole.MANAGER)) OutlinedButton(onClick=onEdit,modifier=Modifier.fillMaxWidth().testTag("finance-edit")) {Text(if(record.serverVersion==null) "Lanjutkan draf" else "Koreksi catatan")}
        actions()
        OutlinedButton(onClick=onClose,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch)) {
            Text(if (returnToQueue) "Kembali ke antrean" else "Kembali ke riwayat")
        }
    }
}
@Composable internal fun ActivityFacts(record:LocalRecord,session:FinanceSession,context:CultivationUiState) {
        if(record.body.optString("record_state")=="cancelled") Text("Dibatalkan",color=MaterialTheme.colorScheme.error)
        val shown=runCatching {ActivityDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion)}.getOrNull()
        shown?.let { draft ->
            Text(context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Kelompok tanam")
            Text("${draft.type.label} · ${draft.date} ${draft.time} WIB")
            Text(if(draft.skipped) "Dilewati: ${draft.skippedReason}" else "Kegiatan selesai")
            if(draft.ec.isNotBlank()) Text("EC manual: ${draft.ec} mS/cm")
            if(draft.ppm.isNotBlank()) Text("PPM manual: ${draft.ppm} ppm")
            if(draft.ph.isNotBlank()) Text("pH manual: ${draft.ph}")
            if(draft.stage.isNotBlank()) Text("Stadia aktual: ${draft.stage}")
            draft.materials.forEach { material -> Text("${context.materials.find {it.optString("id")==material.materialId}?.optString("name") ?: "Bahan ${material.materialId.take(8)}"}: ${material.quantity} ${context.units.find {it.optString("id")==material.unitId}?.optString("name").orEmpty()}") }
            Text(draft.notes)
        } ?: Text(record.body.optString("notes"))
        val recorder=record.body.optString("recorded_by_name").ifBlank {record.serverBody?.optString("recorded_by_name").orEmpty()}.ifBlank {
            if(record.serverVersion==null || record.recorderId()==session.scope.accountId) session.name else "Nama pencatat belum tersedia"
        }
        Text("Pencatat: $recorder")
        Text("Versi server: ${record.serverVersion ?: "belum diterima"}")
        val snapshot=record.body.optJSONObject("reference_snapshot")
        snapshot?.let { snap -> val age=snap.optJSONObject("age");Text("HSS ${if(age?.isNull("hss")==false) age.optString("hss") else "belum tersedia"} · HST ${if(age?.isNull("hst")==false) age.optString("hst") else "belum berlaku"}")
            age?.optJSONArray("reasons")?.let { a -> repeat(a.length()) {Text(a.getString(it))} }
            snap.optJSONObject("checks")?.let { checks -> checks.keys().forEach { parameter -> val check=checks.getJSONObject(parameter);Text("${when(parameter) {"manual_ec" -> "EC";"manual_ppm" -> "PPM";else -> "pH"}}: ${when(check.optString("status")) {"delta_only" -> "selisih ${check.optString("delta")}";"within_range" -> "dalam rentang";"outside_range" -> "di luar rentang";else -> "belum dapat dibandingkan"}}")
                check.optJSONArray("reasons")?.let { a -> repeat(a.length()) {Text(a.getString(it),style=MaterialTheme.typography.bodySmall)} } } }
        }
}

@Composable private fun ReferenceCards(context:CultivationUiState,group:String,draft:ActivityDraft?) {
    val instant=draft?.let { runCatching {OffsetDateTime.parse(it.occurredAt()).toInstant()}.getOrNull() }
    if(draft!=null) {
        val groupData=context.groups.find {it.optString("id")==group}
        val age=runCatching { if(groupData==null) null else PlantAge.calculate(groupData.getString("sowing_date"),if(groupData.isNull("transplant_date")) null else groupData.getString("transplant_date"),draft.occurredAt()) }.getOrNull()
        if(age!=null) { Text("HSS ${age.hss ?: "belum tersedia"} · HST ${age.hst ?: "belum berlaku"}");age.reasons.forEach {Text(it,style=MaterialTheme.typography.bodySmall)} }
        else Text("Umur belum dapat ditentukan dari tanggal yang tersedia.",style=MaterialTheme.typography.bodySmall)
    }
    val targets=context.targets.filter { it.optString("planting_group_id")==group && it.optBoolean("active",true) && (draft!=null || it.optString("parameter")!="manual_ph") && (draft==null || it.optString("activity_type")==draft.type.wire) }.filter { target ->
        instant==null || runCatching { !instant.isBefore(OffsetDateTime.parse(target.getString("effective_from")).toInstant()) && (target.isNull("effective_until") || instant.isBefore(OffsetDateTime.parse(target.getString("effective_until")).toInstant())) }.getOrDefault(false)
    }
    if(targets.isEmpty()) Notice("Target pada konteks/waktu ini belum tersedia. Nilai aktual tetap dicatat; kesesuaian belum dapat dibandingkan.")
    targets.forEach { target -> OutlinedCard { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text("Acuan ${if(target.optString("parameter")=="manual_ppm") "PPM" else "pH"} · versi ${target.optInt("version")}",fontWeight=FontWeight.SemiBold)
        val single=if(target.isNull("target_value")) null else target.optString("target_value")
        Text(if(single!=null) "$single ${target.optString("unit")}" else if(!target.isNull("minimum") && !target.isNull("maximum")) "${target.optString("minimum")} – ${target.optString("maximum")} ${target.optString("unit")}" else "Aturan acuan belum lengkap")
        if(single!=null && target.isNull("tolerance")) Text("Tanpa toleransi: hanya selisih, belum dapat menyimpulkan kesesuaian.",style=MaterialTheme.typography.bodySmall)
        Text("Aktual tidak diisi dari target.",style=MaterialTheme.typography.bodySmall)
    } } }
}

@Composable internal fun ErrorReasons(error:JSONObject) {
    val messages=mutableListOf<String>()
    error.optString("message").takeIf {it.isNotBlank()}?.let {messages.add(it)}
    error.optJSONArray("reasons")?.let { reasons -> repeat(reasons.length()) {messages.add(reasons.getString(it))} }
    error.optJSONObject("field_errors")?.let { fields ->
        fields.keys().forEach { key -> fields.optJSONArray(key)?.let { values -> repeat(values.length()) {messages.add("$key: ${values.getString(it)}")} } }
    }
    messages.distinct().forEach { Text(it,color=MaterialTheme.colorScheme.error) }
}
@Composable internal fun Notice(message:String,error:Boolean=false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (error) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (error) colors.onErrorContainer else colors.onSecondaryContainer,
        shape = FarmShape.Control,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) { Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
}
@Composable internal fun FinanceField(label:String,value:String,onChange:(String)->Unit,tag:String,error:String?=null,keyboard:KeyboardType=KeyboardType.Text,hint:String?=null,singleLine:Boolean=true) {
    val labelAbove = LocalDensity.current.fontScale > 1.5f
    val support = error ?: hint
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (labelAbove) Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value, onChange, shape = FarmShape.Control,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            label = if (labelAbove) null else ({ Text(label) }),
            isError = error != null,
            supportingText = support?.let { message -> { Text(message) } },
            singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics { if (labelAbove) contentDescription = label }
        )
    }
}
@Composable internal fun Choice(
    label: String, selected: String?, options: List<JSONObject>, tag: String,
    loading: Boolean = false, onSelect: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (!expanded && restoreFocus) {
            focus.requestFocus()
            restoreFocus = false
        }
    }
    val dialogOnly = LocalFarmDialogPicker.current
    val value = options.find { it.optString("id") == selected }?.optString("name")
        ?: if (selected.isNullOrEmpty()) "Belum dipilih" else "Pilihan tersimpan belum tersedia"
    OutlinedButton(
        onClick = {
            restoreFocus = focused
            sheet = !dialogOnly && options.size > 6
            expanded = true
        }, shape = FarmShape.Control,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag(tag)
            .focusRequester(focus).onFocusChanged { focused = it.isFocused }
    ) {
        Column(Modifier.weight(1f).padding(vertical = FarmSpace.Tiny), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.width(FarmSpace.Small))
        FarmControlIcon(FarmIcon.ChevronDown, null)
    }
    if (expanded) FarmPicker(
        title = "Pilih ${label.replaceFirstChar { it.lowercaseChar() }}",
        options = options.map {
            FarmOption(it.getString("id"), it.optString("choice_label").ifBlank { it.getString("name") },
                it.optString("choice_detail").takeIf(String::isNotBlank))
        },
        selected = selected,
        optionTag = { "$tag-option-$it" },
        onDismiss = { expanded = false },
        onSelect = { expanded = false; onSelect(it) },
        sheet = sheet && !dialogOnly,
        loading = loading
    )
}
