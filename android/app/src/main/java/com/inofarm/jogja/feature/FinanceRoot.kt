package com.inofarm.jogja.feature

import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import com.inofarm.jogja.feature.activities.domain.CorrectionReference
import com.inofarm.jogja.feature.activities.domain.ObservationField
import com.inofarm.jogja.feature.activities.domain.workerTargetLine
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.activities.domain.MaterialDraft
import com.inofarm.jogja.feature.admin.AdminHost
import com.inofarm.jogja.feature.admin.AdminResource
import com.inofarm.jogja.feature.auth.AuthViewModel
import com.inofarm.jogja.feature.auth.FinanceLogin
import com.inofarm.jogja.feature.auth.LastLoginEmail
import com.inofarm.jogja.feature.context.ContextViewModel
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.context.HomeContext
import com.inofarm.jogja.feature.context.HomeContextCard
import com.inofarm.jogja.feature.context.WorkerContextSheet
import com.inofarm.jogja.feature.context.WorkerCorrectionsCard
import com.inofarm.jogja.feature.context.NutritionRunForm
import com.inofarm.jogja.feature.context.NutritionRunItem
import com.inofarm.jogja.feature.context.NutritionRunReview
import com.inofarm.jogja.feature.context.nutritionMainLabel
import com.inofarm.jogja.feature.context.nutritionRunTargets
import kotlinx.coroutines.flow.first
import com.inofarm.jogja.feature.context.WorkerGreenhouseHero
import com.inofarm.jogja.feature.context.WorkerGreenhouseList
import com.inofarm.jogja.feature.context.WorkerGroupChips
import com.inofarm.jogja.feature.context.WorkerSummaryCard
import com.inofarm.jogja.feature.context.rememberWorkerHome
import com.inofarm.jogja.feature.context.workerAge
import com.inofarm.jogja.feature.context.WorkerCorrectionsScreen
import com.inofarm.jogja.feature.context.rememberWorkerCorrections
import com.inofarm.jogja.feature.context.WorkerEventTime
import com.inofarm.jogja.feature.context.WorkerDayAgenda
import com.inofarm.jogja.feature.context.greenhouseAvailability
import com.inofarm.jogja.feature.context.workerDefault
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.history.*
import com.inofarm.jogja.feature.verification.ActivityVerificationRepository
import com.inofarm.jogja.feature.verification.ActivityVerificationScreen
import com.inofarm.jogja.feature.verification.ReviewKind
import com.inofarm.jogja.feature.harvest.*
import com.inofarm.jogja.feature.expenses.*
import com.inofarm.jogja.feature.attachments.*
import com.inofarm.jogja.feature.sales.*
import com.inofarm.jogja.feature.managerhome.*
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.delay

private fun <T:ViewModel> factory(create:()->T)=object:ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <V:ViewModel> create(modelClass:Class<V>):V=create() as V
}
internal fun SyncState.label()=when(this) {
    SyncState.LOCAL -> "Tersimpan di perangkat";SyncState.QUEUED -> "Belum terkirim";SyncState.ACCEPTED -> "Terkirim"
    SyncState.CONFLICT -> "Data berubah di perangkat lain";SyncState.REJECTED -> "Perlu diperbaiki";SyncState.AUTH_REQUIRED -> "Perlu masuk lagi"
    SyncState.SCOPE_REVOKED -> "Perlu dicek";SyncState.EPOCH_REVIEW -> "Perlu dicek setelah data dipulihkan"
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
    val completedSync by runtime.sync.updates.collectAsStateWithLifecycle()
    LaunchedEffect(completedSync, authState.session?.scope, authState.session?.role) {
        if (completedSync > 0 && authState.session?.role == UserRole.WORKER) context.reloadCache()
    }
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
    LaunchedEffect(contextState.lastSyncedAt, authState.session?.role) {
        if (authState.session?.role == UserRole.WORKER && contextState.lastSyncedAt != null) {
            activities.reload()
            history.reload()
            harvest.reload()
            harvestHistory.reload()
        }
    }
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
    var adminEntry by rememberSaveable(authState.session?.scope?.key) { mutableStateOf<AdminResource?>(null) }
    var adminPeriodRequest by rememberSaveable(authState.session?.scope?.key) { mutableStateOf<String?>(null) }
    var adminGreenhouse by rememberSaveable(authState.session?.scope?.key) { mutableStateOf<String?>(null) }
    var wizardOpen by rememberSaveable(authState.session?.scope?.key) { mutableStateOf(false) }
    var wizardTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var adminEdit by rememberSaveable(authState.session?.scope?.key) { mutableStateOf(false) }
    val openAdmin: (AdminResource) -> Unit = { adminGreenhouse = null; adminEdit = false; adminEntry = it }
    BackHandler(authState.loading && authState.session!=null) { /* Wait for the bounded local persistence boundary. */ }
    // The email is remembered only after the server accepted the login, never the password.
    var pendingLoginEmail by remember { mutableStateOf<String?>(null) }
    val loginContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(authState.session?.scope?.key) {
        val accepted = pendingLoginEmail
        if (authState.session != null && accepted != null) { LastLoginEmail.save(loginContext, accepted); pendingLoginEmail = null }
    }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        if(authState.session==null) {
            val configured = runtime.configuredLoginEndpoint.value
            val endpoint = remember(configured) { runCatching { runtime.api.validateBase(configured) }.getOrNull() }
            FinanceLogin(authState, endpoint) { base, email, password -> pendingLoginEmail = email; auth.login(base, email, password) }
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
                                    investmentWorkspace, openAdmin, { adminEntry = null },
                                    adminPeriodRequest, { adminPeriodRequest = null }, { id, edit -> adminGreenhouse = id; adminEdit = edit },
                                    { wizardOpen = true }, wizardTarget, { wizardTarget = null })
                            }
                        }
                    }
                }
            }
        }
    }
    val admin = adminEntry
    val adminSession = authState.session
    if (admin != null && adminSession?.role == UserRole.MANAGER && !adminSession.rejected) {
        AdminHost(adminSession, runtime.admin, context::refresh, admin,
            onReviewPeriod = { greenhouse -> adminPeriodRequest = greenhouse; adminEntry = null; adminGreenhouse = null; adminEdit = false },
            onClose = { adminEntry = null; adminGreenhouse = null; adminEdit = false },
            initialGreenhouse = adminGreenhouse, initialEdit = adminEdit)
    }
    if (wizardOpen && adminSession?.role == UserRole.MANAGER && !adminSession.rejected) {
        com.inofarm.jogja.feature.admin.GreenhouseWizardHost(adminSession, runtime.admin, context::refresh,
            onOpenGreenhouse = { id -> wizardTarget = id to false; wizardOpen = false },
            onRecord = { id -> wizardTarget = id to true; wizardOpen = false },
            onClose = { wizardOpen = false })
    }
    if(authState.session!=null && !authState.loading && authState.error!=null) AlertDialog(onDismissRequest=auth::clearError,title={Text("Belum keluar")},text={Text(authState.error!!)},confirmButton={TextButton(onClick=auth::clearError) {Text("Kembali ke isian")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FinanceHome(session:FinanceSession,context:CultivationUiState,activity:ActivityUiState,onSync:()->Unit,onLogout:()->Unit,vm:ActivityViewModel,history:HistoryUiState,historyVm:HistoryViewModel,
    harvest:HarvestUiState,harvestVm:HarvestViewModel,harvestHistory:HistoryUiState,harvestHistoryVm:HistoryViewModel,financial:FinancialEntryState,financialVm:FinancialEntryViewModel,
    expenseHistory:HistoryUiState,expenseHistoryVm:HistoryViewModel,allocationHistory:HistoryUiState,allocationHistoryVm:HistoryViewModel,attachment:AttachmentUiState,attachmentVm:AttachmentViewModel,ticket:Long,sale:SaleEntryState,saleVm:SaleEntryViewModel,saleHistory:HistoryUiState,saleHistoryVm:HistoryViewModel,asset:AssetWorkspace,reports:SeasonReportWorkspace,investments:InvestmentWorkspace,onAdmin:(AdminResource)->Unit,onAccessDenied:()->Unit,
    adminPeriodRequest: String?, onAdminPeriodOpened: () -> Unit, onAdminGreenhouse: (String?, Boolean) -> Unit,
    onAddGreenhouse: () -> Unit, wizardTarget: Pair<String, Boolean>?, onWizardTargetUsed: () -> Unit) {
    val runtime:FinanceRuntime=viewModel()
    val tabState = rememberSaveableStateHolder()
    var tab by rememberSaveable { mutableStateOf(0) };var gh by rememberSaveable { mutableStateOf<String?>(null) };var season by rememberSaveable { mutableStateOf<String?>(null) };var group by rememberSaveable { mutableStateOf<String?>(null) }
    var queueReviewError by rememberSaveable { mutableStateOf<String?>(null) }
    var queueReviewOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var queueFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var queueOpening by remember { mutableStateOf(false) }
    var showOutbox by rememberSaveable { mutableStateOf(false) }
    var recordSheet by rememberSaveable { mutableStateOf(false) }
    var moneyPick by rememberSaveable { mutableStateOf<String?>(null) }
    var historyChip by rememberSaveable { mutableStateOf("activity") }
    var lastGreenhouse by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewActivity by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewKind by rememberSaveable { mutableStateOf(ReviewKind.ACTIVITY) }
    val verification = remember(runtime) { ActivityVerificationRepository(runtime.api, runtime.store, runtime.vault) }
    val manager = session.role == UserRole.MANAGER
    val tabEntrance = remember { Animatable(1f) }
    var previousWorkerTab by remember { mutableIntStateOf(tab) }
    LaunchedEffect(tab, manager) {
        if (!manager && tab != previousWorkerTab) {
            previousWorkerTab = tab
            tabEntrance.snapTo(0f)
            tabEntrance.animateTo(1f, tween(180))
        }
    }
    var managerRefresh by remember { mutableLongStateOf(0L) }
    var periodReview by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(adminPeriodRequest, manager) {
        if (manager && adminPeriodRequest != null) {
            gh = adminPeriodRequest
            tab = 0
            periodReview = true
            onAdminPeriodOpened()
        }
    }
    LaunchedEffect(wizardTarget, manager) {
        val target = wizardTarget
        if (manager && target != null) {
            gh = target.first
            tab = 0
            periodReview = false
            if (target.second) recordSheet = true
            onWizardTargetUsed()
        }
    }
    var financeKind by rememberSaveable { mutableStateOf(OperationalKind.EXPENSE.wire) }
    var financeLink by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(gh) { if (gh != null) lastGreenhouse = gh }
    val homeRepository = remember(runtime) { ManagerHomeRepository(runtime.vault, runtime.store, runtime.api) }
    val syncUpdates by runtime.sync.updates.collectAsStateWithLifecycle()
    var reviewCount by remember { mutableStateOf<Int?>(null) }
    if (manager) LaunchedEffect(syncUpdates, session.scope) {
        val readTicket = runtime.vault.generation
        reviewCount = runCatching {
            ReviewKind.entries.sumOf { kind ->
                verification.read(session, readTicket, kind.inbox, mapOf("status" to "pending", "limit" to "50"))
                    .optJSONArray("data")?.length() ?: 0
            }
        }.getOrNull()
    }
    var workerToday by remember { mutableStateOf(LocalDate.now(ActivityDraft.WIB)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (!manager) LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = ZonedDateTime.now(ActivityDraft.WIB)
                workerToday = now.toLocalDate()
                delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(ActivityDraft.WIB)).toMillis() + 1_000)
            }
        }
    }
    val workerCorrections = if (manager) emptyList() else
        rememberWorkerCorrections(runtime, session, Triple(activity.records, harvest.records, activity.queue), syncUpdates)
    val managerHome = if (manager) rememberManagerHome(homeRepository, session, ticket, gh, syncUpdates, activity.records, managerRefresh)
        else ManagerHomeState()
    if (session.rejected || managerHome.sessionRejected) {
        LaunchedEffect(Unit) { onAccessDenied() }
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(FarmSpace.Page),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            FarmPageTitle("Periksa akses akun")
            FarmPanel {
                Text("Akses akun berubah. Masuk lagi untuk melanjutkan.")
                Text("Draf dan catatan yang belum terkirim tetap tersimpan.")
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
        val shownTab = if (manager && destinationTab in 2..3) 5 else destinationTab
        if (shownTab == 5) historyChip = if (destinationTab == 2) "activity" else "harvest"
        val id = destination.recordId
        if (id == null) tab = shownTab
        else target.select(id) { error -> if (error != null) tab = shownTab }
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
        ?.takeUnless { context.check == ContextCheck.ACCESS_CHANGED }
    val selectedSeason = context.seasons.find { it.optString("id") == season && it.optString("greenhouse_id") == gh }
    val selectedGroup = context.groups.find { it.optString("id") == group && it.optString("season_id") == season && it.optBoolean("active", true) }
    var contextSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var contextChange by rememberSaveable { mutableStateOf<String?>(null) }
    var managerRecording by rememberSaveable { mutableStateOf<String?>(null) }
    var harvestProgressOpen by rememberSaveable { mutableStateOf(false) }
    var correctionsOpen by rememberSaveable { mutableStateOf(false) }
    // Nutrition run: 0 off, 1 filling a greenhouse, 2 saving it, 3 reviewing all, 4 sending. Ids are kept as text so the run survives recreation.
    var runPhase by rememberSaveable { mutableIntStateOf(0) }
    var runIds by rememberSaveable { mutableStateOf("") }
    var runIndex by rememberSaveable { mutableIntStateOf(0) }
    var runSaved by rememberSaveable { mutableStateOf("") }
    var runSkipped by rememberSaveable { mutableStateOf("") }
    var runNotReady by rememberSaveable { mutableStateOf("") }
    var runPendingId by rememberSaveable { mutableStateOf("") }
    var detailFromOutbox by rememberSaveable { mutableStateOf(false) }
    var harvestProgressGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var harvestProgressGreenhouse by rememberSaveable { mutableStateOf<String?>(null) }
    var workerProgressSourceError by remember { mutableStateOf<String?>(null) }
    fun openWorkerHarvestProgress() {
        harvestProgressGreenhouse = gh
        harvestProgressGroup = group
        workerProgressSourceError = null
        harvestProgressOpen = true
    }
    var entryGreenhouse by rememberSaveable { mutableStateOf<String?>(null) }
    var entrySeason by rememberSaveable { mutableStateOf<String?>(null) }
    var entryGroup by rememberSaveable { mutableStateOf<String?>(null) }
    managerRecording?.let { destination -> WorkerContextSheet(
        context, HomeContext(entryGreenhouse, entrySeason, entryGroup),
        when (destination) { "harvest" -> "Catat panen"; "measurement" -> "Catat pengukuran"; else -> "Catat kegiatan" },
        onSelect = { entryGreenhouse = it.greenhouse; entrySeason = it.season; entryGroup = it.group },
        onClose = { managerRecording = null },
        onContinue = {
            val parent = entryGreenhouse
            val plantingGroup = entryGroup
            if (parent != null && plantingGroup != null) {
                managerRecording = null
                when (destination) {
                    "harvest" -> harvestVm.new(parent, plantingGroup)
                    "measurement" -> vm.new(parent, plantingGroup, ActivityType.MEASUREMENT)
                    else -> vm.new(parent, plantingGroup)
                }
            }
        },
        onSync = onSync,
        onSetup = { managerRecording = null; onAdmin(AdminResource.GREENHOUSE) },
        chooseSingleGroup = destination == "harvest",
    ) }
    LaunchedEffect(context.lastSyncedAt, context.greenhouses, context.seasons, context.groups) {
        if (!manager && (context.lastSyncedAt != null || context.greenhouses.isNotEmpty())) {
            if (gh != null && selectedGreenhouse == null) {
                contextChange = if (context.greenhouses.any { it.optString("id") == gh })
                    "Greenhouse yang dipilih sekarang nonaktif. Draf tetap tersimpan."
                else "Anda tidak lagi bertugas di greenhouse ini. Draf tetap tersimpan."
                gh = null
            }
            if (gh == null || selectedSeason == null) season = null
            if (season == null || selectedGroup == null) group = null
        }
    }
    BackHandler(harvestProgressOpen || correctionsOpen || !manager && selectedGreenhouse != null || investments.state.active || reports.state.active || asset.entry.draft!=null || asset.history.selected!=null || asset.depreciation.selected!=null || sale.draft!=null || saleHistory.selected!=null || attachment.parent!=null || writing || activity.draft!=null || harvest.draft!=null || financial.draft!=null || history.selected!=null || harvestHistory.selected!=null || expenseHistory.selected!=null || allocationHistory.selected!=null || tab!=0) {
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
            harvestProgressOpen -> harvestProgressOpen = false
            correctionsOpen -> correctionsOpen = false
            tab == 1 && reviewActivity != null -> reviewActivity = null
            tab == 0 && !manager && selectedGreenhouse != null -> { gh = null; season = null; group = null }
            else -> tab=0
        }
    }
    val showNavigation = runPhase != 3 && !harvestProgressOpen && !(tab == 1 && reviewActivity != null) && !investments.state.active && !reports.state.active && asset.entry.draft==null && asset.history.selected==null && asset.depreciation.selected==null && sale.draft==null && saleHistory.selected==null && attachment.parent==null && activity.draft==null && harvest.draft==null && financial.draft==null && history.selected==null && harvestHistory.selected==null && expenseHistory.selected==null && allocationHistory.selected==null
    val sessionExpired = session.expired()
    val detailOpen = history.selected != null || harvestHistory.selected != null
    val formOpen = activity.draft != null || harvest.draft != null
    LaunchedEffect(detailOpen, formOpen) {
        if (formOpen) detailFromOutbox = false
        else if (!manager && detailFromOutbox && !detailOpen) { showOutbox = true; detailFromOutbox = false }
    }
    // A saved tab id from before the worker had two tabs would show a blank body.
    LaunchedEffect(manager, tab) { if (!manager && tab !in WorkerScreenIds.toList()) tab = 0 }
    val workerReady = selectedGreenhouse != null && selectedGroup != null &&
        selectedSeason?.optString("status") == "open" && context.check != ContextCheck.ACCESS_CHANGED &&
        runCatching { LocalDate.parse(selectedGroup.getString("sowing_date")) <= workerToday }.getOrDefault(false)
    fun setWorkerContext(value: HomeContext) {
        gh = value.greenhouse
        season = value.season
        group = value.group
        contextChange = null
    }
    fun recordWorker(destination: String) {
        val greenhouse = selectedGreenhouse?.getString("id")
        val plantingGroup = selectedGroup?.getString("id")
        if (!workerReady || greenhouse == null || plantingGroup == null) {
            contextSheet = destination
            return
        }
        contextSheet = null
        val version = selectedGreenhouse.takeIf { it?.optString("running_group_id") == plantingGroup }
            ?.optLong("version")?.takeIf { it > 0 }
        if (destination == "harvest") harvestVm.new(greenhouse, plantingGroup, version)
        else vm.new(greenhouse, plantingGroup, if (destination == "measurement") ActivityType.MEASUREMENT else ActivityType.GENERAL,
            runningPeriodVersion = version)
    }
    // Recording from the Catat sheet: the worker picked a greenhouse there, so open its current planting directly.
    fun recordWorkerIn(greenhouseId: String?, choice: RecordChoice) {
        val destination = when (choice) { RecordChoice.HARVEST -> "harvest"; RecordChoice.MEASUREMENT -> "measurement"; else -> "activity" }
        val row = context.greenhouses.find { it.optString("id") == greenhouseId && it.optBoolean("active", true) }
        if (row == null || row.getString("id") == selectedGreenhouse?.optString("id")) return recordWorker(destination)
        val picked = context.workerDefault(row, workerToday)
        setWorkerContext(picked)
        val pickedGroup = context.groups.find { it.optString("id") == picked.group }
        val pickedSeason = context.seasons.find { it.optString("id") == picked.season }
        val ready = pickedGroup != null && pickedSeason?.optString("status") == "open" &&
            runCatching { LocalDate.parse(pickedGroup.getString("sowing_date")) <= workerToday }.getOrDefault(false)
        if (!ready || pickedGroup == null) { contextSheet = destination; return }
        val version = row.takeIf { it.optString("running_group_id") == pickedGroup.getString("id") }?.optLong("version")?.takeIf { it > 0 }
        if (destination == "harvest") harvestVm.new(row.getString("id"), pickedGroup.getString("id"), version)
        else vm.new(row.getString("id"), pickedGroup.getString("id"),
            if (destination == "measurement") ActivityType.MEASUREMENT else ActivityType.GENERAL, runningPeriodVersion = version)
    }
    // --- Nutrition run: one measurement draft per ready greenhouse, then a review of all of them.
    val runList = runIds.split(',').filter { it.isNotBlank() }
    fun runOpen(greenhouseId: String) {
        val row = context.greenhouses.find { it.optString("id") == greenhouseId } ?: return
        val ctx = context.workerDefault(row, workerToday)
        val grp = context.groups.find { it.optString("id") == ctx.group } ?: return
        val version = row.takeIf { it.optString("running_group_id") == grp.getString("id") }?.optLong("version")?.takeIf { it > 0 }
        vm.new(greenhouseId, grp.getString("id"), ActivityType.MEASUREMENT, runningPeriodVersion = version)
    }
    fun runEnd() { runPhase = 0; runIds = ""; runSaved = ""; runSkipped = ""; runNotReady = ""; runIndex = 0; runPendingId = "" }
    fun runAdvance() {
        val next = runIndex + 1
        if (next < runList.size) {
            runIndex = next
            runPhase = 1
            runOpen(runList[next])
        } else runPhase = 3
    }
    fun runStart() {
        val (ready, notReady) = nutritionRunTargets(context, workerToday)
        if (ready.isEmpty()) return
        recordSheet = false
        runIds = ready.joinToString(",") { it.getString("id") }
        runNotReady = notReady.joinToString("|") { it.optString("name") }
        runSaved = ""
        runSkipped = ""
        runIndex = 0
        runPhase = 1
        runOpen(ready.first().getString("id"))
    }
    LaunchedEffect(runPhase, activity.draft == null) {
        val live = vm.state.value
        if (runPhase == 2 && live.draft == null && !live.busy) {
            runSaved = listOf(runSaved, runPendingId).filter { it.isNotBlank() }.joinToString(",")
            runAdvance()
        } else if (runPhase == 1 && live.draft == null && !live.busy) runEnd() // the worker left the form
    }
    LaunchedEffect(runPhase) {
        if (runPhase == 4) {
            for (id in runSaved.split(',').filter { it.isNotBlank() }) {
                val row = vm.state.value.records.find { it.id == id } ?: continue
                vm.open(row)
                vm.queue()
                kotlinx.coroutines.withTimeoutOrNull(15_000) { vm.state.first { it.draft == null } } ?: vm.discard()
            }
            runEnd()
        }
    }
    BackHandler(enabled = runPhase == 3) { runEnd() }
    BackHandler(enabled = runPhase == 4) { /* Each queued record is being saved; wait for the local persistence boundary. */ }
    val nutritionForm: NutritionRunForm? = if (runPhase == 1 && runList.isNotEmpty()) {
        val names = runList.map { id -> context.greenhouses.find { it.optString("id") == id }?.optString("name").orEmpty() }
        NutritionRunForm(runIndex + 1, names, nutritionMainLabel(runIndex + 1, names),
            onSkip = {
                runSkipped = listOf(runSkipped, names[runIndex]).filter { it.isNotBlank() }.joinToString("|")
                vm.discard()
                runAdvance()
            },
            onMain = {
                val draft = vm.state.value.draft
                if (draft != null) {
                    if (draft.validation().isNotEmpty()) vm.review()
                    else {
                        runPendingId = draft.id
                        runPhase = 2
                        vm.saveAndClose()
                    }
                }
            })
    } else null
    if (!manager) contextSheet?.let { destination ->
        WorkerContextSheet(context, HomeContext(gh, season, group), when (destination) {
            "harvest" -> "Catat panen"
            "activity" -> "Catat kegiatan"
            "measurement" -> "Catat pengukuran"
            else -> null
        }, onSelect = ::setWorkerContext, onClose = { contextSheet = null }, onContinue = { recordWorker(destination) }, onSync = onSync,
            preferRunningPeriod = true, startableOnly = true)
    }
    val queueScreen: @Composable () -> Unit = {
        QueueScreen(activity, context, sessionExpired, queueReviewOperation, queueReviewError, queueOpening, vm::reload,
            onReview = { op ->
                queueReviewError = null
                queueReviewOperation = op.id
                val kind = op.envelope.optString("entity_type")
                val destination = queueHistory[kind]
                if (manager) showOutbox = false
                when {
                    destination != null -> {
                        queueOpening = true
                        destination.select(op.entityId) { error ->
                            queueOpening = false
                            queueReviewError = error
                            // A worker's sheet stays open on failure so the reason is visible, and returns after the detail closes.
                            if (!manager && error == null) { showOutbox = false; detailFromOutbox = true }
                        }
                    }
                    manager && kind in investmentQueueKinds -> investments.vm.select(op.entityId,fromQueue=true)
                    manager && kind == "season_report" -> reports.vm.select(op.entityId)
                    manager && kind == "planting_groups" -> {
                        val parent = context.groups.find { it.optString("id") == op.entityId }
                        harvestProgressGroup = parent?.optString("id")
                        harvestProgressGreenhouse = context.seasons.find {
                            it.optString("id") == parent?.optString("season_id")
                        }?.optString("greenhouse_id")
                        historyChip = "harvest"
                        tab = 5
                        harvestProgressOpen = true
                    }
                    manager && kind in setOf("activity_review", "harvest_review") -> {
                        reviewKind = ReviewKind.entries.first { it.operationKind == kind }
                        reviewActivity = op.envelope.getJSONObject("payload").getString(reviewKind.parentKey)
                        tab = 1
                    }
                }
            },
            canReview = { op ->
                val kind = op.envelope.optString("entity_type")
                kind in queueHistory || manager && (kind in investmentQueueKinds || kind in setOf("season_report", "activity_review", "harvest_review", "planting_groups"))
            }, focusOperation = queueFocus,
            onSendNow = onSync,
            title = "Belum terkirim", worker = !manager)
    }
    val unsent = activity.queue.count { it.state != "ACCEPTED" && it.state != "SUPERSEDED" }
    fun startRecord(choice: RecordChoice, pickedGreenhouse: String? = null) {
        recordSheet = false
        if (!manager) return recordWorkerIn(pickedGreenhouse ?: gh, choice)
        val target = gh ?: lastGreenhouse ?: context.greenhouses.singleOrNull()?.optString("id")
        when (choice) {
            RecordChoice.ACTIVITY, RecordChoice.MEASUREMENT, RecordChoice.HARVEST ->
                if (canRecordFromHome) when (choice) {
                    RecordChoice.HARVEST -> harvestVm.new(gh!!, runningGroup!!, runningVersion)
                    RecordChoice.MEASUREMENT -> vm.new(gh!!, runningGroup!!, ActivityType.MEASUREMENT, null, runningVersion)
                    else -> vm.new(gh!!, runningGroup!!, ActivityType.GENERAL, null, runningVersion)
                } else {
                    entryGreenhouse = target
                    entrySeason = null
                    entryGroup = null
                    managerRecording = when (choice) { RecordChoice.HARVEST -> "harvest"; RecordChoice.MEASUREMENT -> "measurement"; else -> "activity" }
                }
            RecordChoice.EXPENSE ->
                if (target != null) financialVm.new(com.inofarm.jogja.core.contracts.RecordKind.EXPENSE, target)
                else moneyPick = "expense"
            RecordChoice.SALE ->
                if (target != null) saleVm.new(target)
                else moneyPick = "sale"
        }
    }
    moneyPick?.let { kind ->
        FarmPicker(
            title = "Pilih greenhouse",
            options = context.greenhouses.filter { it.optBoolean("active", true) }.map { FarmOption(it.getString("id"), it.getString("name")) },
            selected = "",
            optionTag = { "record-greenhouse-$it" },
            onDismiss = { moneyPick = null },
            onSelect = { id ->
                moneyPick = null
                if (kind == "expense") financialVm.new(com.inofarm.jogja.core.contracts.RecordKind.EXPENSE, id) else saleVm.new(id)
            },
            sheet = true
        )
    }
    if (recordSheet) RecordSheet(::startRecord,
        if (manager && gh != null && runningGroup == null && !managerHome.loading) ({ recordSheet = false; onAdmin(AdminResource.GROUP) }) else null,
        { recordSheet = false }, if (manager) RecordChoice.entries else WorkerRecordChoices,
        worker = if (manager) null else WorkerRecordTarget(
            context.greenhouses.filter { it.optBoolean("active", true) }, gh ?: lastGreenhouse,
            caption = { id ->
                val row = context.greenhouses.find { it.optString("id") == id }
                val ctx = row?.let { context.workerDefault(it, workerToday) }
                val grp = context.groups.find { it.optString("id") == ctx?.group }
                if (grp == null) row?.let { "Tanam saat ini belum dipilih pengelola" }
                else listOf(grp.optString("name"), workerAge(grp, workerToday).substringAfter(" · ")).joinToString(" · ")
            }, onPick = { choice, id -> startRecord(choice, id) },
            onNutritionRun = if (nutritionRunTargets(context, workerToday).first.isNotEmpty()) ({ runStart() }) else null),
        contextLine = if (manager && gh != null) listOfNotNull(homeSnapshot?.selected?.text("name"),
            homeSnapshot?.period?.text("group_name")?.let { "Kelompok $it" }).joinToString(" · ").ifBlank { null } else null)
    if (showOutbox) ModalBottomSheet(
        onDismissRequest = { showOutbox = false; queueFocus = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing }
    ) { Box(Modifier.fillMaxHeight(.92f).padding(horizontal = FarmSpace.Page)) { queueScreen() } }
    val translucentNavigation = false
    val wibNow = remember { java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Jakarta")) }
    val managerBar: ManagerBar? = if (!manager) when {
        activity.draft != null || harvest.draft != null || runPhase >= 3 -> null
        tab == 0 && !correctionsOpen && !harvestProgressOpen && selectedGreenhouse == null -> ManagerBar("${greetingForWib(wibNow.hour, wibNow.minute)}, ${session.name.trim().substringBefore(' ')}",
            dateLabel(wibNow.toLocalDate()), null)
        tab == 0 && correctionsOpen -> ManagerBar("Perlu diperbaiki", null, { correctionsOpen = false }, "Kembali ke Beranda")
        tab == 0 && !harvestProgressOpen && selectedGreenhouse != null -> ManagerBar(selectedGreenhouse.optString("name"), null,
            { setWorkerContext(HomeContext()) }, "Kembali ke semua greenhouse")
        tab == 5 && history.selected == null && harvestHistory.selected == null -> ManagerBar("Riwayat", null, null)
        else -> null
    } else when {
        tab == 0 && gh != null -> ManagerBar(homeSnapshot?.selected?.text("name") ?: "Greenhouse", null, onBack = { gh = null; periodReview = false })
        tab == 0 -> ManagerBar(greetingForWib(wibNow.hour, wibNow.minute), dateLabel(wibNow.toLocalDate()), null)
        tab == 5 -> ManagerBar("Riwayat", null, null)
        tab == 4 -> ManagerBar("Keuangan", null, null)
        tab == 6 -> ManagerBar("Kelola", null, null)
        tab == 1 -> ManagerBar("Cek catatan pekerja", null, { if (reviewActivity != null) reviewActivity = null else tab = 0 },
            if (reviewActivity != null) "Kembali ke daftar" else "Kembali ke Beranda")
        else -> null
    }
    val snackbar = remember { SnackbarHostState() }
    // A worker's save message belongs to the screen the form returned to, so it shows there and clears itself.
    val savedMessage = if (manager || activity.draft != null || harvest.draft != null) null else activity.message ?: harvest.message
    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            snackbar.showSnackbar(savedMessage)
            vm.clearMessage()
            harvestVm.clearMessage()
        }
    }
    Scaffold(
        contentWindowInsets=WindowInsets.safeDrawing,snackbarHost={ SnackbarHost(snackbar) },topBar={ Column(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal=FarmSpace.Page)
        ) {
        if((attachment.parent!=null && (ime || LocalDensity.current.fontScale>1.5f || LocalConfiguration.current.screenHeightDp<400)) || (ime && (investments.state.scenarioDraft!=null || investments.state.reportDraft!=null || investments.state.cancelReason!=null || reports.state.draft!=null || (!manager && (asset.entry.draft!=null || sale.draft!=null || activity.draft!=null || harvest.draft!=null || financial.draft!=null))))) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(if(attachment.parent!=null) "Lampiran" else if(investments.state.active) "Investasi" else if(reports.state.draft!=null) "Evaluasi" else if(asset.entry.draft!=null) "Aset" else if(sale.draft!=null) "Penjualan" else if(financial.draft!=null && LocalDensity.current.fontScale>1.5f) "Isian" else if(financial.draft!=null) "Catat ${financial.draft.kind.label}" else if(harvest.draft!=null) "Catat panen" else "Catat kegiatan",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={keyboard?.hide();if(attachment.parent!=null) {if(attachment.selected!=null) attachmentVm.closeSelected() else attachmentVm.close()} else if(investments.state.active) investments.vm.back() else if(reports.state.draft!=null) reports.vm.back() else if(asset.entry.draft!=null) asset.vm.saveAndClose() else if(sale.draft!=null) saleVm.saveAndClose() else if(financial.draft!=null) financialVm.saveAndClose() else if(harvest.draft!=null) harvestVm.saveAndClose() else vm.saveAndClose()},enabled=!writing) {Text(if(attachment.parent!=null) "Kembali" else "Simpan draf")} }
        } else {
            FinanceToolbar(session, context.loading, writing, sessionExpired, onSync, onLogout, onAdmin,
                unsent = unsent, onUnsent = { queueFocus = null; showOutbox = true }, bar = managerBar)
        }
    } },bottomBar={
        if (showNavigation) {
            val internalTabs = if (manager) ManagerScreenIds else WorkerScreenIds
            val selectTab: (Int) -> Unit = { index ->
                if (!queueOpening) {
                    queueFocus = null
                    val target = internalTabs[index]
                    if (target != tab) { showOutbox = false; reviewActivity = null }
                    correctionsOpen = false
                    tab = target
                }
            }
            FarmDockNavigation(navIndex(tab), (if (manager) ManagerDestinations else WorkerDestinations).map { it.label to it.icon }, selectTab,
                onRecord = { recordSheet = true }, recordEnabled = !writing,
                modifier = Modifier.padding(horizontal = 12.dp).navigationBarsPadding())
        }
    }) { padding ->
        val menuViewport = showNavigation && !writing
        val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val contentPadding = if (translucentNavigation) {
            PaddingValues(start = padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                end = padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                top = padding.calculateTopPadding(), bottom = systemBottom)
        } else padding
        Box(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(padding).imePadding().padding(horizontal=FarmSpace.Page),
            contentAlignment = Alignment.TopCenter) {
        val workerTabMotion = if (!manager && menuViewport) Modifier.graphicsLayer {
            alpha = .74f + .26f * tabEntrance.value
            translationY = 5.dp.toPx() * (1f - tabEntrance.value)
        } else Modifier
        // The Catat button grows with the font, so a worker's list keeps more room behind it at large sizes.
        val recordInset = 8.dp
        CompositionLocalProvider(LocalFarmListBottomInset provides recordInset) {
        // A form stays visible while its save runs, so touches are held until the save has settled.
        val saving = asset.entry.busy || sale.busy || activity.busy || harvest.busy || financial.busy || runPhase == 4
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        LaunchedEffect(saving) { if (saving) focusManager.clearFocus() }
        Box(Modifier.widthIn(max = 900.dp).fillMaxSize().then(workerTabMotion).holdTouches(saving)) {
            when {
                session.role==UserRole.MANAGER && investments.state.active -> InvestmentScreen(investments,context,returnToQueue=tab==1)
                session.role==UserRole.MANAGER && reports.state.active -> SeasonReportScreen(reports,context,returnToQueue=tab==1)
                attachment.parent!=null -> AttachmentScreen(session,context,attachment,attachmentVm,ticket)
                // Phase 4 keeps the review on screen; its main button reports the sending in place.
                runPhase == 3 || runPhase == 4 -> NutritionRunReview(
                    saved = runSaved.split(',').filter { it.isNotBlank() }.mapNotNull { id ->
                        activity.records.find { it.id == id }?.let { record ->
                            val draft = ActivityDraft.from(record.id, record.greenhouseId, record.body, record.baseVersion ?: record.serverVersion)
                            fun value(text: String) = text.replace('.', ',')
                            NutritionRunItem(context.greenhouses.find { it.optString("id") == record.greenhouseId }?.optString("name") ?: "Greenhouse",
                                listOfNotNull(draft.ec.takeIf { it.isNotBlank() }?.let { "EC ${value(it)} mS/cm" },
                                    draft.ppm.takeIf { it.isNotBlank() }?.let { "PPM ${value(it)} ppm" },
                                    draft.ph.takeIf { it.isNotBlank() }?.let { "pH ${value(it)}" }).joinToString(" · "))
                        }
                    },
                    skipped = runSkipped.split('|').filter { it.isNotBlank() }, notReady = runNotReady.split('|').filter { it.isNotBlank() },
                    busy = activity.busy || runPhase == 4, onExit = { runEnd() }, onSend = { runPhase = 4 })
                session.role==UserRole.MANAGER && asset.entry.draft!=null -> if(asset.entry.reviewing) FarmReviewEntrance { AssetReview(asset.entry.draft,context,asset.entry.busy,asset.vm::backToForm,asset.vm::queue) } else AssetForm(asset.entry,context,asset.vm)
                session.role==UserRole.MANAGER && asset.history.selected!=null -> HistoryScreen(session,context,asset.history,asset.historyVm,activity.queue,onEdit={record,replace -> asset.vm.open(record,replace);asset.historyVm.close()},kind=OperationalKind.ASSET,returnToQueue=tab==1)
                session.role==UserRole.MANAGER && asset.depreciation.selected!=null -> HistoryScreen(session,context,asset.depreciation,asset.depreciationVm,activity.queue,onEdit={record,replace -> asset.vm.open(record,replace);asset.depreciationVm.close()},kind=OperationalKind.DEPRECIATION,returnToQueue=tab==1)
                activity.draft!=null -> if(activity.reviewing) FarmReviewEntrance { ActivityReview(activity.draft,context,activity.busy,vm::backToForm,vm::queue,worker = !manager) } else ActivityForm(activity,context,vm,worker = !manager,run = nutritionForm)
                harvest.draft!=null -> if(harvest.reviewing) FarmReviewEntrance { HarvestReview(harvest.draft,context,harvest.busy,harvestVm::backToForm,harvestVm::queue,worker = !manager) } else HarvestForm(harvest,context,harvestVm,worker = !manager)
                session.role==UserRole.MANAGER && sale.draft!=null -> if(sale.reviewing) FarmReviewEntrance { SaleReview(sale.draft,context,sale.busy,saleVm::backToForm,saleVm::queue) } else SaleForm(sale,context,saleVm)
                session.role==UserRole.MANAGER && saleHistory.selected!=null -> HistoryScreen(session,context,saleHistory,saleHistoryVm,activity.queue,onEdit={record,replace -> saleVm.open(record,replace);saleHistoryVm.close()},kind=OperationalKind.SALE,returnToQueue=tab==1)
                session.role==UserRole.MANAGER && financial.draft!=null -> if(financial.reviewing) FarmReviewEntrance { FinancialReview(financial.draft,context,financial.busy,financialVm::backToForm,financialVm::queue) } else FinancialForm(financial,context,financialVm)
                session.role==UserRole.MANAGER && expenseHistory.selected!=null -> HistoryScreen(session,context,expenseHistory,expenseHistoryVm,activity.queue,onEdit={record,replace -> financialVm.open(record,replace);expenseHistoryVm.close()},kind=OperationalKind.EXPENSE,returnToQueue=tab==1)
                session.role==UserRole.MANAGER && allocationHistory.selected!=null -> HistoryScreen(session,context,allocationHistory,allocationHistoryVm,activity.queue,onEdit={record,replace -> financialVm.open(record,replace);allocationHistoryVm.close()},kind=OperationalKind.COST_ALLOCATION,returnToQueue=tab==1)
                history.selected!=null -> HistoryScreen(session,context,history,historyVm,activity.queue,onEdit={record,replace -> vm.open(record,replace);historyVm.close()},returnToQueue=tab==1 || detailFromOutbox)
                harvestHistory.selected!=null -> HistoryScreen(session,context,harvestHistory,harvestHistoryVm,activity.queue,onEdit={record,replace -> harvestVm.open(record,replace);harvestHistoryVm.close()},kind=OperationalKind.HARVEST,returnToQueue=tab==1 || detailFromOutbox)
                tab == 0 && manager -> tabState.SaveableStateProvider("manager-home-${gh.orEmpty()}") {
                    ManagerHomeScreen(gh, managerHome, activity.queue, PaddingValues(
                        top = FarmSpace.Medium,
                        bottom = FarmSpace.Large + LocalFarmListBottomInset.current + if (translucentNavigation) (padding.calculateBottomPadding() - systemBottom).coerceAtLeast(0.dp) else 0.dp,
                    ), periodReview, { periodReview = it }, { id -> gh = id; periodReview = false }, {
                        managerRefresh++
                        onSync()
                    }, openHomeDestination, { operation -> queueFocus = operation.id; showOutbox = true },
                        onRecord = { type, schedule ->
                            if (canRecordFromHome) vm.new(gh!!, runningGroup!!, type, schedule, runningVersion)
                        },
                        onHarvest = { if (canRecordFromHome) harvestVm.new(gh!!, runningGroup!!, runningVersion) },
                        onAdmin = { onAdmin(AdminResource.GREENHOUSE); onAdminGreenhouse(gh, false) },
                        seasonActions = { row -> SeasonActions(row, history.busy, history.message, historyVm::season) },
                        onDesignate = { groupId, reason ->
                            try {
                                homeRepository.designate(session, ticket, requireNotNull(homeSnapshot), groupId, reason)
                            } finally {
                                managerRefresh++
                                onSync()
                            }
                        },
                        reviewCount = reviewCount,
                        onReviewInbox = { reviewActivity = null; tab = 1 },
                        onAddGreenhouse = onAddGreenhouse,
                        onAdminFor = { resource, edit -> onAdmin(resource); onAdminGreenhouse(gh, edit) },
                        onHarvestProgress = {
                            harvestProgressGreenhouse = gh
                            harvestProgressGroup = runningGroup
                            harvestProgressOpen = true
                        },
                        onSeasonReport = { reports.vm.show("") })
                }
                tab==0 && correctionsOpen -> WorkerCorrectionsScreen(workerCorrections, context, onOpen = { row ->
                    if (row.kind == "harvest") harvestHistoryVm.select(row.id) else historyVm.select(row.id)
                })
                tab==0 && !harvestProgressOpen -> tabState.SaveableStateProvider("worker-home-${selectedGreenhouse?.optString("id").orEmpty()}") {
                val homeState = if (selectedGreenhouse == null) rememberWorkerHome(runtime, session, context, workerToday,
                    Triple(activity.records, harvest.records, activity.queue), syncUpdates) else null
                LazyColumn(
                    modifier=Modifier.fillMaxSize().testTag("finance-home-list"),
                    verticalArrangement=Arrangement.spacedBy(FarmSpace.Medium),
                    contentPadding=PaddingValues(
                        top = 12.dp,
                        bottom = FarmSpace.Large + LocalFarmListBottomInset.current + if (translucentNavigation) {
                            (padding.calculateBottomPadding() - systemBottom).coerceAtLeast(0.dp)
                        } else 0.dp
                    )
                ) {
                    if (homeState != null) item("summary") { WorkerSummaryCard(homeState, context) }
                    if (selectedGreenhouse != null) item("context") {
                        Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                            WorkerGreenhouseHero(runtime, session, context, HomeContext(gh, season, group), workerToday,
                                Triple(activity.records, harvest.records, activity.queue), syncUpdates)
                            WorkerGroupChips(context, HomeContext(gh, season, group), workerToday) { picked -> group = picked }
                            TextButton(onClick = { contextSheet = "context" }, shape = FarmShape.Control,
                                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-context-open")) { Text("Ubah musim atau kelompok") }
                        }
                    }
                    if (homeState != null && workerCorrections.isNotEmpty()) item("corrections") {
                        WorkerCorrectionsCard(workerCorrections.size) { correctionsOpen = true }
                    }
                    if (homeState != null) item("greenhouses") {
                        WorkerGreenhouseList(homeState, context) { greenhouse ->
                            setWorkerContext(context.workerDefault(greenhouse, workerToday))
                        }
                    }
                    contextChange?.let { message -> item("context-changed") { Notice(message) } }
                    context.greenhouseAvailability()?.let { message -> item("context-availability") { Notice(message) } }
                    if (sessionExpired) item("expired-session") { ExpiredSessionNotice() }
                    if (selectedGreenhouse != null && selectedSeason?.optString("status") == "closed") item("recording") {
                        FarmAttention("Musim ditutup. Pilih musim terbuka untuk mencatat.")
                    }
                    if (selectedGreenhouse != null && selectedSeason != null && selectedGroup != null) {
                        val greenhouseId = selectedGreenhouse.getString("id")
                        val groupId = selectedGroup.getString("id")
                        val open = selectedSeason.optString("status") == "open"
                        item("measurements") {
                            com.inofarm.jogja.feature.activities.MeasurementDashboard(
                                runtime, session, greenhouseId, groupId, activity.records, context.lastSyncedAt,
                                context.message.takeUnless { sessionExpired },
                                lastKnown = context.check in setOf(ContextCheck.OFFLINE, ContextCheck.FAILED)
                            )
                        }
                        item("worker-day") {
                            WorkerDayAgenda(runtime, session, greenhouseId, groupId, workerToday, context.schedules,
                                Triple(activity.records, harvest.records, activity.queue), syncUpdates, open,
                                onSchedule = { type, schedule -> vm.new(greenhouseId, groupId, type, schedule) },
                                onRecord = { row ->
                                    if (row.kind == "harvest") harvestHistoryVm.select(row.id) else historyVm.select(row.id)
                                },
                                onQueue = { queueFocus = null; showOutbox = true })
                        }
                    }
                    if (selectedGreenhouse != null) item("harvest-progress-link") {
                        FarmActionRow("Progres panen", "Target, hasil tercatat dan hasil yang sudah dicek", FarmIcon.Report,
                            Modifier.testTag("worker-harvest-progress-open"), onClick = ::openWorkerHarvestProgress)
                    }
                    if (selectedGreenhouse != null) item("record-outside-schedule") {
                        OutlinedButton(onClick = { recordSheet = true }, shape = FarmShape.Control,
                            modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("worker-record-outside")) {
                            Text("Catat di luar jadwal")
                        }
                    }
                } }
                harvestProgressOpen && manager -> HarvestProgressScreen(session, ticket, context, runtime.harvest,
                    harvestProgressGreenhouse, harvestProgressGroup, syncUpdates,
                    { harvestProgressOpen = false }, onSync, {
                        harvestProgressOpen = false
                        showOutbox = true
                        tab = 1
                    })
                harvestProgressOpen -> tabState.SaveableStateProvider("worker-progress-${harvestProgressGreenhouse.orEmpty()}-${harvestProgressGroup.orEmpty()}") {
                    WorkerHarvestProgress(session, ticket, context, runtime.harvest,
                        harvestProgressGreenhouse, harvestProgressGroup, syncUpdates, { harvestProgressOpen = false },
                        onSource = { id, version -> harvestHistoryVm.select(id, expectedVersion = version) { workerProgressSourceError = it } },
                        sourceSelectionError = workerProgressSourceError)
                }
                tab == 1 && manager -> tabState.SaveableStateProvider("verification-${reviewKind.wire}") {
                    ActivityVerificationScreen(session, ticket, context, verification, syncUpdates, reviewActivity,
                        { reviewActivity = it }, { showOutbox = true }, onSync, reviewKind, { reviewKind = it })
                }
                tab==4 && manager -> tabState.SaveableStateProvider(4) {
                    FinancialHub(session,context,financialVm,expenseHistory,expenseHistoryVm,allocationHistory,allocationHistoryVm,activity.queue,saleVm,saleHistory,saleHistoryVm,asset,reports,investments,onSync,financeKind,financeLink,homeRepository,ticket,syncUpdates)
                }
                tab==6 && manager -> tabState.SaveableStateProvider(6) {
                    ManageScreen(session.name, onAdmin, onLogout, enabled = !writing, onAddGreenhouse = onAddGreenhouse, greenhouseCount = context.greenhouses.size.takeIf { it > 0 })
                }
                tab==5 -> tabState.SaveableStateProvider("history-$historyChip") {
                    val isActivity = historyChip != "harvest"
                    HistoryScreen(session,context,if (isActivity) history else harvestHistory,
                        if (isActivity) historyVm else harvestHistoryVm,activity.queue,
                        onEdit={record,replace ->
                            if (isActivity) { vm.open(record,replace);historyVm.close() }
                            else { harvestVm.open(record,replace);harvestHistoryVm.close() }
                        },
                        kind=if (isActivity) OperationalKind.ACTIVITY else OperationalKind.HARVEST,
                        recordScope=if (manager && canRecordFromHome) listOfNotNull(homeSnapshot?.selected?.text("name"),homeSnapshot?.period?.text("season_name")).joinToString(" · ")
                        else null,
                        onSync=onSync, canopy = true,
                        historyKindPicker = if (tab == 5) ({
                            val choose: (String) -> Unit = { id ->
                                when (id) {
                                    "mine" -> { historyVm.mine(!history.mine); harvestHistoryVm.mine(!history.mine) }
                                    "activity", "measurement" -> {
                                        historyChip = id
                                        historyVm.filter(history.filter.copy(type = if (id == "measurement") ActivityType.MEASUREMENT.wire else null), history.filterSeason)
                                    }
                                    "harvest" -> historyChip = id
                                    else -> {
                                        financeKind = when (id) { "expense" -> OperationalKind.EXPENSE.wire; "sale" -> OperationalKind.SALE.wire; else -> OperationalKind.ASSET.wire }
                                        financeLink++
                                        tab = 4
                                    }
                                }
                            }
                            if (manager) com.inofarm.jogja.feature.history.CanopyTypeChips(historyChip, choose)
                            else Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                                RecordTypeChips(historyChip, manager, history.mine, choose)
                                com.inofarm.jogja.feature.history.WorkerGreenhouseChips(context, history.filter.greenhouseId) { picked ->
                                    historyVm.filter(history.filter.copy(greenhouseId = picked), history.filterSeason)
                                    harvestHistoryVm.filter(harvestHistory.filter.copy(greenhouseId = picked), harvestHistory.filterSeason)
                                }
                            }
                        }) else null,
                        header = {
                            if (manager && !isActivity) item("harvest-progress-link") {
                                com.inofarm.jogja.feature.managerhome.HarvestRow("Rekap panen", "Target dan hasil kelompok tanam", null,
                                    tag = "harvest-progress-open") {
                                    harvestProgressGreenhouse = gh
                                    harvestProgressGroup = runningGroup
                                    harvestProgressOpen = true
                                }
                            }
                            if (!manager && !isActivity) item("worker-harvest-progress-link") {
                                FarmActionRow("Progres panen", "Lihat target dan hasil kelompok tanam", FarmIcon.Report,
                                    Modifier.testTag("worker-harvest-progress-open"), onClick = ::openWorkerHarvestProgress)
                            }
                        })
                }
            }
        }
        }
        }
    }
}

/** Manager app bar content: a title, an optional second line, and a back action when a greenhouse is open. */
internal class ManagerBar(val title: String, val subtitle: String?, val onBack: (() -> Unit)?, val backLabel: String = "Kembali ke semua greenhouse")

@Composable private fun FinanceToolbar(
    session: FinanceSession,
    syncing: Boolean,
    writing: Boolean,
    sessionExpired: Boolean,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onAdmin: (AdminResource) -> Unit,
    unsent: Int = 0,
    onUnsent: () -> Unit = {},
    bar: ManagerBar? = null
) {
    var accountOpen by rememberSaveable { mutableStateOf(false) }
    val manager = session.role == UserRole.MANAGER
    val largeFont = LocalDensity.current.fontScale > 1.3f
    // The worker's greeting needs the full row, so the unsent button always sits on its own row below it.
    val stackUnsent = (largeFont || !manager) && unsent > 0
    val amber = farmToneColors(FarmTone.Amber).second
    val unsentButton: @Composable () -> Unit = {
        OutlinedButton(onClick = onUnsent, shape = FarmShape.Control,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = amber),
            border = androidx.compose.foundation.BorderStroke(1.dp, amber.copy(alpha = 0.5f)),
            contentPadding = PaddingValues(horizontal = FarmSpace.Medium),
            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-unsent")
                .semantics { liveRegion = LiveRegionMode.Polite }) {
            if (!manager) {
                FarmControlIcon(FarmIcon.Sync, null)
                Spacer(Modifier.width(FarmSpace.Small))
            }
            Text("$unsent belum terkirim", style = MaterialTheme.typography.labelLarge)
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bar != null) {
            bar.onBack?.let { back ->
                IconButton(onClick = back, modifier = Modifier.testTag("manager-back").semantics { contentDescription = bar.backLabel }) {
                    Box(Modifier.graphicsLayer { rotationZ = 180f }) { FarmControlIcon(FarmIcon.ChevronRight, null) }
                }
            }
            Column(Modifier.weight(1f).padding(vertical = FarmSpace.Small)) {
                Text(bar.title, Modifier.semantics { heading() }, maxLines = if (!manager && largeFont) 2 else 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                bar.subtitle?.let { Text(it, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else Text("Inofarm", Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge)
        if (unsent > 0) { if (!stackUnsent) unsentButton() }
        else if (syncing && (manager || !largeFont)) Text("Memperbarui",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.labelMedium)
        // The worker has no manual refresh on Beranda, so the toolbar keeps its button.
        if (!manager) IconButton(onClick = onSync, enabled = !syncing, modifier = Modifier.testTag("finance-sync")) {
            FarmControlIcon(FarmIcon.Sync, if (syncing) "Memperbarui" else "Perbarui")
        }
        Box {
            Box(Modifier.size(FarmSpace.Touch).clickable(role = androidx.compose.ui.semantics.Role.Button) { accountOpen = true }
                .semantics { contentDescription = if (sessionExpired) "Menu akun. Sesi berakhir." else "Menu akun" }
                .testTag("finance-account"), contentAlignment = Alignment.Center) {
                Box(Modifier.size(44.dp).background(FarmColors.Sage, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Text(initialsOf(session.name), Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FarmColors.Ink)
                }
            }
            DropdownMenu(expanded = accountOpen, onDismissRequest = { accountOpen = false },
                containerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.widthIn(max = 248.dp).padding(horizontal = FarmSpace.Page, vertical = FarmSpace.Small)) {
                    Text(session.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (session.role == UserRole.WORKER) "Pekerja" else "Pengelola",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(horizontal = FarmSpace.Page))
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
    if (stackUnsent) unsentButton()
    if (sessionExpired) Text(
        "Sesi berakhir",
        Modifier.padding(bottom = FarmSpace.Tiny).semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error
    )
}

@Composable
internal fun ExpiredSessionNotice() {
    Notice("Sesi berakhir. Masuk lagi untuk mengirim catatan. Draf tetap bisa disimpan.", true)
}

@Composable private fun ActivityForm(state:ActivityUiState,context:CultivationUiState,vm:ActivityViewModel,worker:Boolean,run:NutritionRunForm?=null) {
    val draft=state.draft ?: return
    if (worker && draft.baseVersion != null) {
        val policy = CorrectionReference.policy(draft.date, draft.time, state.records.find { it.id == draft.id }?.serverBody?.optString("occurred_at"))
        LaunchedEffect(policy, draft.referencePolicy) { if (draft.referencePolicy != policy) vm.edit { it.copy(referencePolicy = policy) } }
    }
    val title = when {
        run != null -> "Cek nutrisi semua greenhouse"
        draft.baseVersion != null -> "Koreksi kegiatan"
        draft.type == ActivityType.MEASUREMENT -> "Catat pengukuran"
        else -> "Catat kegiatan"
    }
    run {
        val groupData = context.groups.find { it.optString("id") == draft.groupId }
        val age = runCatching {
            if (groupData == null) null else PlantAge.calculate(groupData.getString("sowing_date"),
                if (groupData.isNull("transplant_date")) null else groupData.getString("transplant_date"), draft.occurredAt())
        }.getOrNull()
        val figure = ageFigure(age?.hst?.toString()?.toIntOrNull(), age?.hss?.toString()?.toIntOrNull())
        FarmFormFrame(
            header = { FarmFormHeader(title, state.saved, "finance-draft-status", "finance-close", vm::saveAndClose, !state.busy) },
            bar = {
                if (run != null) FarmFormBar("nutrition-run-skip", run.onSkip, "nutrition-run-next", run.mainLabel, run.onMain, enabled = !state.busy, busy = state.busy, saveLabel = "Lewati")
                else FarmFormBar("finance-save-draft", vm::saveAndClose, "finance-review", "Periksa", vm::review, enabled = !state.busy, busy = state.busy,
                    saveLabel = if (worker) "Simpan sebagai draf" else "Simpan draf")
            }
        ) {
            if (run != null) {
                Text("Greenhouse ${run.step} dari ${run.names.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
                FarmStepIndicator(run.step, run.names)
            }
            FarmContextStrip(context.greenhouses.find { it.optString("id") == draft.greenhouseId }?.optString("name") ?: "Greenhouse terakhir tersimpan",
                groupData?.optString("name"), figure?.let { (value, unit) -> "$unit $value" })
            ActivityFormBody(state, context, vm, draft, worker = worker)
        }
    }
}

@Composable private fun ActivityFormGroup(canopy:Boolean,title:String,content:@Composable ColumnScope.()->Unit) {
    if (canopy) FarmFormSection(title, content) else FarmSection(title = title, content = content)
}

/** The form fields shared by the worker and manager layouts. Only the manager layout (canopy) changes shape; the worker body is as before. */
@Composable private fun ColumnScope.ActivityFormBody(state:ActivityUiState,context:CultivationUiState,vm:ActivityViewModel,draft:ActivityDraft,worker:Boolean) {
    val canopy = true
    if (worker && draft.baseVersion != null) {
        // The correction reason comes first so nobody scrolls past it.
        ActivityFormGroup(canopy, "Alasan koreksi") {
            FinanceField(vm.reasonLabel,draft.reason,{value -> vm.edit {it.copy(reason=value)}},"finance-reason",state.errors["reason"])
            if (draft.referencePolicy == "preserve") FarmInfoCard { Text("Waktu kejadian tidak berubah, acuan lama dipakai otomatis.") }
        }
    }
    ActivityFormGroup(canopy, "Jenis dan waktu") {
        // A schedule, a correction or a measurement fixes the type; a worker's plain activity never offers Pengukuran.
        val typeLocked = worker && (draft.scheduleId != null || draft.baseVersion != null || draft.type == ActivityType.MEASUREMENT)
        val typeOptions = ActivityType.entries.filter { !worker || it != ActivityType.MEASUREMENT }.map { JSONObject().put("id",it.wire).put("name",it.label) }
        val pickType = { value: String -> vm.edit { it.copy(type=ActivityType.entries.single { type -> type.wire==value }) } }
        if (!typeLocked) {
            if (canopy) FarmChoiceChips("Jenis kegiatan", draft.type.wire, typeOptions, "finance-type", onSelect = pickType)
            else Choice("Jenis kegiatan",draft.type.wire,typeOptions,"finance-type") { value -> pickType(value) }
        } else Text(draft.type.label, style = MaterialTheme.typography.titleMedium)
        if (worker) {
            WorkerEventTime(context, draft.greenhouseId, draft.groupId, draft.date, draft.time,
                { value -> vm.edit { it.copy(date = value) } }, { value -> vm.edit { it.copy(time = value) } },
                "finance", state.errors["occurred_at"], draft.baseVersion != null && draft.referencePolicy == "preserve",
                state.records.find { it.id == draft.id }?.serverBody?.optJSONObject("reference_snapshot")?.optJSONObject("age"))
        } else {
            val dateField: @Composable () -> Unit = {
                FinanceDateField("Tanggal kejadian (YYYY-MM-DD)",draft.date,{value -> vm.edit {it.copy(date=value)}},"finance-date",state.errors["occurred_at"])
            }
            val timeField: @Composable () -> Unit = {
                FinanceTimeField("Jam kejadian WIB (HH:mm)",draft.time,{value -> vm.edit {it.copy(time=value)}},"finance-time",hint="Detik boleh ditulis HH:mm:ss dan tetap dipertahankan.")
            }
            if (draft.type == ActivityType.MEASUREMENT) {
                FarmWhenCard(draft.date, draft.time, "finance-time-edit", forceOpen = state.errors["occurred_at"] != null) { dateField(); timeField() }
            } else { dateField(); timeField() }
        }
    }
    // Managers see the results first; the skip toggle follows the details.
    val skippedGroup: @Composable () -> Unit = {
        ActivityFormGroup(canopy, "Pelaksanaan") {
            FarmCheckbox("Kegiatan dilewati", draft.skipped, { value -> vm.edit { it.copy(skipped = value) } },
                Modifier.testTag("finance-skipped"))
            if(draft.skipped) FinanceField("Alasan kegiatan dilewati",draft.skippedReason,{value -> vm.edit {it.copy(skippedReason=value)}},"finance-skipped-reason",state.errors["skipped_reason"],singleLine=false)
        }
    }
    if (worker) skippedGroup()
    if(draft.type in setOf(ActivityType.MEASUREMENT,ActivityType.NUTRITION) || draft.ec.isNotBlank() || draft.ppm.isNotBlank() || draft.ph.isNotBlank()) {
        ActivityFormGroup(canopy, if (worker) "Hasil ukur" else "Hasil ukur aktual") {
            val ecField: @Composable () -> Unit = {
                FinanceField("EC manual (mS/cm)",draft.ec,{value -> vm.edit {it.copy(ec=value)}},"finance-ec",state.errors["manual_ec"],keyboard=KeyboardType.Decimal,
                    suffix = if (canopy) "mS/cm" else null, prominent = canopy)
            }
            val ppmField: @Composable () -> Unit = {
                FinanceField("PPM manual (ppm)",draft.ppm,{value -> vm.edit {it.copy(ppm=value)}},"finance-ppm",state.errors["manual_ppm"],KeyboardType.Decimal,
                    suffix = if (canopy) "ppm" else null, prominent = canopy)
            }
            if (canopy) FarmFieldPair(ecField, ppmField) else { ecField(); ppmField() }
            if (worker && draft.type == ActivityType.MEASUREMENT) PpmReferenceWarning(context, draft)
            FinanceField("pH manual (0–14)",draft.ph,{value -> vm.edit {it.copy(ph=value)}},"finance-ph",state.errors["manual_ph"],KeyboardType.Decimal)
            if (worker && draft.type == ActivityType.MEASUREMENT) Text("EC dan PPM wajib diisi dan tidak dikonversi satu sama lain. Desimal pakai koma.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if(draft.type==ActivityType.STAGE || draft.stage.isNotBlank()) FinanceField("Kondisi stadia aktual",draft.stage,{value -> vm.edit {it.copy(stage=value)}},"finance-stage",state.errors["actual_stage"],hint="Dipilih dari kondisi aktual, bukan umur")
    if(draft.type in setOf(ActivityType.NUTRITION,ActivityType.SPRAY) || draft.materials.isNotEmpty()) {
        val materials=context.materials.filter {it.optString("greenhouse_id")==draft.greenhouseId}
        val units=context.units.filter {it.optString("greenhouse_id")==draft.greenhouseId}
        ActivityFormGroup(canopy, "Bahan aktual") {
        if(!draft.skipped && !context.loading && (materials.none {it.optBoolean("active",true)} || units.none {it.optBoolean("active",true)}))
            Notice("Bahan atau satuan belum tersedia untuk greenhouse ini. Perbarui, atau tambahkan di Kelola.")
        draft.materials.forEachIndexed { index,row ->
            val rowContent: @Composable ColumnScope.() -> Unit = {
                fun change(updated:MaterialDraft) { vm.edit {it.copy(materials=it.materials.toMutableList().apply {set(index,updated)})} }
                val material=materials.find {it.optString("id")==row.materialId}
                val compatibleUnits=units.filter {it.optString("dimension")==material?.optString("dimension") && (it.optBoolean("active",true) || it.optString("id")==row.unitId)}
                Choice("Bahan ${index+1}",row.materialId,materials.filter {it.optBoolean("active",true) || it.optString("id")==row.materialId},"finance-material-$index") {change(row.copy(materialId=it,unitId=""))}
                Choice("Satuan ${index+1}",row.unitId,compatibleUnits,"finance-unit-$index") {change(row.copy(unitId=it))}
                if(material!=null && compatibleUnits.isEmpty() && !context.loading)
                    Text("Belum ada satuan aktif dengan dimensi yang sesuai. Minta pengelola menambahkannya.")
                FinanceField("Jumlah ${index+1}",row.quantity,{change(row.copy(quantity=it))},"finance-quantity-$index",state.errors["materials.$index"],KeyboardType.Decimal,hint="Desimal boleh koma; tanpa pemisah ribuan")
            }
            val remove = { vm.edit {it.copy(materials=it.materials.filterIndexed { i,_ -> i!=index })} }
            if (canopy) FarmDetailCard("Hapus bahan ${index+1}", remove, "finance-material-remove-$index", rowContent)
            else FarmPanel {
                rowContent()
                TextButton(onClick=remove, modifier=Modifier.heightIn(min=FarmSpace.Touch)) {Text("Hapus bahan ${index+1}")}
            }
        }
        OutlinedButton(onClick={vm.edit {it.copy(materials=it.materials+MaterialDraft())}},enabled=draft.materials.size<30,
            shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("finance-add-material")) {Text("Tambah bahan")}
        state.errors["materials"]?.let { Notice(it,true) }
        }
    }
    if (!worker) skippedGroup()
    var observationsOpen by rememberSaveable(draft.id) { mutableStateOf(draft.observations.values.any { it.isNotBlank() }) }
    ActivityFormGroup(canopy, "Catatan lapangan") {
        if(draft.type==ActivityType.GENERAL) Text(
            "Jelaskan pekerjaan seperti persemaian, kebersihan, polinasi, seleksi buah, atau penanganan hama.",
            style=MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = { observationsOpen = !observationsOpen }, modifier = Modifier.heightIn(min = FarmSpace.Touch)
            .testTag("activity-observations")) {
            Text(if (observationsOpen) "Tutup isian pengamatan" else "Tambah pengamatan (opsional)")
        }
        Column(Modifier.animateContentSize(tween(190)), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        if (observationsOpen) ObservationField.entries.forEach { field ->
            val label = if (field.unit.isBlank()) field.label else "${field.label} (${field.unit})"
            val keyboard = when (field) {
                ObservationField.LEAF_COUNT -> KeyboardType.Number
                ObservationField.SAMPLE_BASIS -> KeyboardType.Text
                else -> KeyboardType.Decimal
            }
            FinanceField(label, draft.observations[field].orEmpty(),
                { value -> vm.edit { it.copy(observations = it.observations + (field to value)) } },
                "activity-${field.wire}", state.errors["observations.${field.wire}"], keyboard,
                hint = if (worker && field == ObservationField.SAMPLE_BASIS) "Misalnya 5 tanaman baris tengah, pohon tak berbuah 0" else null)
        }
        }
        FinanceField("Keterangan pekerjaan",draft.notes,{value -> vm.edit {it.copy(notes=value)}},"finance-notes",state.errors["notes"],singleLine=false)
    }
    if(draft.baseVersion!=null) {
        if (!worker) FinanceField(vm.reasonLabel,draft.reason,{value -> vm.edit {it.copy(reason=value)}},"finance-reason",state.errors["reason"])
        if(!worker) Choice("Acuan koreksi",draft.referencePolicy,listOf(JSONObject().put("id","preserve").put("name","Pertahankan tanggal/target historis"),JSONObject().put("id","refresh").put("name","Nilai ulang dengan acuan terkini")),"finance-reference-policy") {value -> vm.edit {it.copy(referencePolicy=value)}}
    }
    if(worker) {
        if (draft.type in setOf(ActivityType.NUTRITION, ActivityType.SPRAY, ActivityType.MEASUREMENT)) FarmInfoCard {
            ReferenceCards(context,draft.groupId,draft,worker=true)
            if (draft.type != ActivityType.MEASUREMENT) Text("Isi jumlah yang benar-benar dipakai, bukan angka anjuran.")
        }
    } else if(draft.baseVersion!=null && draft.referencePolicy=="preserve") {
        Notice("Koreksi mempertahankan tanggal dan target historis. Pilih nilai ulang bila acuan memang perlu diperbarui.")
        val historical=state.records.find {it.id==draft.id}?.serverBody?.optJSONObject("reference_snapshot")
        historical?.optJSONObject("age")?.let { age -> Text("HSS acuan: ${if(age.isNull("hss")) "belum tersedia" else age.optString("hss")} · HST acuan: ${if(age.isNull("hst")) "belum berlaku" else age.optString("hst")}") }
    } else ReferenceCards(context,draft.groupId,draft)
    state.message?.let { Notice(it,true) }
    state.errors.values.distinct().forEach { Notice(it,true) }
}

@Composable private fun ActivityReview(draft:ActivityDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit,worker:Boolean=false) {
    if (!worker) {
        val greenhouse = context.greenhouses.find { it.optString("id") == draft.greenhouseId }?.optString("name") ?: "Greenhouse terakhir tersimpan"
        FarmReviewFrame(if (draft.type == ActivityType.MEASUREMENT) "pengukuran" else "kegiatan", "$greenhouse · ${whenLabel(draft.date, draft.time)}",
            FarmIcon.CheckList, busy, "finance-queue", "finance-back", onBack, onQueue) {
            if(draft.replaceValidationRejected) Notice("Perbaikan ini dikirim sebagai catatan baru. Catatan yang ditolak tetap ada di riwayat dan tidak dikirim ulang.")
            FarmFormSection("Rincian") { ActivityReviewFacts(draft, context, readable = true) }
        }
        return
    }
    val greenhouse = context.greenhouses.find { it.optString("id") == draft.greenhouseId }?.optString("name") ?: "Greenhouse terakhir tersimpan"
    FarmReviewFrame(if (draft.type == ActivityType.MEASUREMENT) "pengukuran" else "kegiatan", "$greenhouse · ${whenLabel(draft.date, draft.time)}",
        FarmIcon.CheckList, busy, "finance-queue", "finance-back", onBack, onQueue, mainLabel = "Kirim",
        hint = "Pastikan isinya benar. Setelah dikirim, catatan terkirim otomatis saat ada internet.") {
        if(draft.replaceValidationRejected) Notice("Perbaikan ini dikirim sebagai catatan baru. Catatan yang ditolak tetap ada di riwayat dan tidak dikirim ulang.")
        FarmFormSection(draft.type.label) { ActivityReviewFacts(draft, context, readable = true) }
        FarmInfoCard { Text("Muncul di Riwayat dengan label Belum terkirim sampai diterima, lalu menunggu cek pengelola.") }
    }
}

@Composable private fun ColumnScope.ActivityReviewFacts(draft:ActivityDraft,context:CultivationUiState,readable:Boolean=false) {
            if (draft.runningPeriodVersion != null) ManagerDraftParents(context, draft.greenhouseId, draft.groupId)
            FarmFact("Kelompok tanam", context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Nama belum tersedia")
            FarmFact("Waktu kejadian", if (readable) whenLabel(draft.date, draft.time) else "${draft.date} ${draft.time} WIB")
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
            ObservationFacts(draft)
            if (draft.notes.isNotBlank()) FarmFact("Keterangan", draft.notes)
            if(draft.reason.isNotBlank()) FarmFact("Alasan koreksi", draft.reason)
}

@Composable internal fun RecordDetail(record:LocalRecord,session:FinanceSession,context:CultivationUiState,onClose:()->Unit,onEdit:()->Unit,kind:OperationalKind=OperationalKind.ACTIVITY,returnToQueue:Boolean=false,actions:@Composable ()->Unit={}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FarmPageTitle("Detail ${kind.label}")
        FarmPanel {
            if (session.role == UserRole.WORKER) FarmStatusChip(record.syncState.label(), tone = when {
                record.syncState.needsReview -> FarmTone.Error
                record.syncState == SyncState.ACCEPTED -> FarmTone.Good
                else -> FarmTone.Amber
            }) else RecordStatus(record.syncState)
            RecordFacts(record,session,context,kind)
            record.error?.let { ErrorReasons(it) }
        }
        val own=record.recorderId().let {it.isBlank() || it==session.scope.accountId}
        if(record.body.optString("record_state")!="cancelled" && (own || session.role==UserRole.MANAGER)) {
            val editLabel = if(record.serverVersion==null) "Lanjutkan draf" else "Koreksi catatan"
            if (session.role == UserRole.WORKER) {
                Button(onClick=onEdit,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("finance-edit")) {Text(editLabel)}
                if (record.serverVersion != null) Text("Koreksi membuat revisi baru dan dicek ulang oleh pengelola. Alasan koreksi wajib. Pembatalan hanya bisa dilakukan pengelola.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else OutlinedButton(onClick=onEdit,modifier=Modifier.fillMaxWidth().testTag("finance-edit")) {Text(editLabel)}
        }
        actions()
        OutlinedButton(onClick=onClose,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch)) {
            Text(if (returnToQueue) "Kembali ke belum terkirim" else "Kembali ke riwayat")
        }
    }
}
@Composable private fun ObservationFacts(draft: ActivityDraft) {
    draft.observations.filterValues { it.isNotBlank() }.forEach { (field, value) ->
        FarmFact(field.label, "$value ${field.unit}".trim())
    }
}

@Composable internal fun ActivityFacts(record:LocalRecord,session:FinanceSession,context:CultivationUiState, showComparison: Boolean = true) {
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
            ObservationFacts(draft)
            Text(draft.notes)
        } ?: Text(record.body.optString("notes"))
        val recorder=record.body.optString("recorded_by_name").ifBlank {record.serverBody?.optString("recorded_by_name").orEmpty()}.ifBlank {
            if(record.serverVersion==null || record.recorderId()==session.scope.accountId) session.name else "Nama pencatat belum tersedia"
        }
        Text("Pencatat: $recorder")
        if(session.role==UserRole.MANAGER) Text("Versi: ${record.serverVersion ?: "belum terkirim"}")
        val snapshot=record.body.optJSONObject("reference_snapshot")
        snapshot?.let { snap -> val age=snap.optJSONObject("age");Text("HSS ${if(age?.isNull("hss")==false) age.optString("hss") else "belum tersedia"} · HST ${if(age?.isNull("hst")==false) age.optString("hst") else "belum berlaku"}")
            age?.optJSONArray("reasons")?.let { a -> repeat(a.length()) {Text(a.getString(it))} }
            if (showComparison) snap.optJSONObject("checks")?.let { checks -> checks.keys().forEach { parameter -> val check=checks.getJSONObject(parameter)
                val worker=session.role==UserRole.WORKER
                val verdict=when(check.optString("status")) {"delta_only" -> if(worker) null else "selisih ${check.optString("delta")}";"within_range" -> "dalam rentang";"outside_range" -> "di luar rentang";else -> if(worker) null else "belum dapat dibandingkan"}
                verdict?.let { Text("${when(parameter) {"manual_ec" -> "EC";"manual_ppm" -> "PPM";else -> "pH"}}: $it") }
                if(!worker) check.optJSONArray("reasons")?.let { a -> repeat(a.length()) {Text(a.getString(it),style=MaterialTheme.typography.bodySmall)} } } }
        }
}

@Composable private fun ReferenceCards(context:CultivationUiState,group:String,draft:ActivityDraft?,worker:Boolean=false) {
    val instant=draft?.let { runCatching {OffsetDateTime.parse(it.occurredAt()).toInstant()}.getOrNull() }
    if(draft!=null && !worker) {
        val groupData=context.groups.find {it.optString("id")==group}
        val age=runCatching { if(groupData==null) null else PlantAge.calculate(groupData.getString("sowing_date"),if(groupData.isNull("transplant_date")) null else groupData.getString("transplant_date"),draft.occurredAt()) }.getOrNull()
        if(age!=null) { Text("HSS ${age.hss ?: "belum tersedia"} · HST ${age.hst ?: "belum berlaku"}");age.reasons.forEach {Text(it,style=MaterialTheme.typography.bodySmall)} }
        else Text("Umur belum dapat ditentukan dari tanggal yang tersedia.",style=MaterialTheme.typography.bodySmall)
    }
    val targets=context.targets.filter { it.optString("planting_group_id")==group && it.optBoolean("active",true) && (draft!=null || it.optString("parameter")!="manual_ph") && (draft==null || it.optString("activity_type")==draft.type.wire) }.filter { target ->
        instant==null || runCatching { !instant.isBefore(OffsetDateTime.parse(target.getString("effective_from")).toInstant()) && (target.isNull("effective_until") || instant.isBefore(OffsetDateTime.parse(target.getString("effective_until")).toInstant())) }.getOrDefault(false)
    }
    if(worker) {
        targets.forEach { target ->
            fun text(key:String)=target.takeUnless {it.isNull(key)}?.optString(key)
            workerTargetLine(target.optString("parameter"),target.optString("unit"),text("target_value"),text("minimum"),text("maximum"))?.let { Text(it,style=MaterialTheme.typography.bodyMedium) }
        }
        return
    }
    if(targets.isEmpty()) Notice("Belum ada target untuk waktu ini. Nilai aktual tetap dicatat.")
    targets.forEach { target -> ReferenceCard {
        Text("Acuan ${if(target.optString("parameter")=="manual_ppm") "PPM" else "pH"} · versi ${target.optInt("version")}",fontWeight=FontWeight.SemiBold)
        val single=if(target.isNull("target_value")) null else target.optString("target_value")
        Text(if(single!=null) "$single ${target.optString("unit")}" else if(!target.isNull("minimum") && !target.isNull("maximum")) "${target.optString("minimum")} – ${target.optString("maximum")} ${target.optString("unit")}" else "Aturan acuan belum lengkap")
        if(single!=null && target.isNull("tolerance")) Text("Tanpa toleransi: hanya selisih, belum dapat menyimpulkan kesesuaian.",style=MaterialTheme.typography.bodySmall)
        Text("Aktual tidak diisi dari target.",style=MaterialTheme.typography.bodySmall)
    } }
}

@Composable private fun ReferenceCard(content:@Composable ColumnScope.()->Unit) {
    if (LocalCanopyForm.current) FarmInfoCard(content)
    else OutlinedCard { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content) }
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
@Composable internal fun FinanceField(label:String,value:String,onChange:(String)->Unit,tag:String,error:String?=null,keyboard:KeyboardType=KeyboardType.Text,hint:String?=null,singleLine:Boolean=true,enabled:Boolean=true,suffix:String?=null,prominent:Boolean=false,visual:androidx.compose.ui.text.input.VisualTransformation=androidx.compose.ui.text.input.VisualTransformation.None,leadingIcon:(@Composable ()->Unit)?=null,trailingAction:(@Composable ()->Unit)?=null) {
    val canopy = LocalCanopyForm.current
    val labelAbove = LocalDensity.current.fontScale > 1.5f || canopy
    val support = error ?: hint
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (labelAbove) Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (canopy) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
        OutlinedTextField(
            value, onChange, shape = if (canopy) FarmShape.Tile else FarmShape.Control,
            suffix = suffix?.let { unit -> { Text(unit) } },
            textStyle = if (prominent) MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum") else LocalTextStyle.current,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            label = if (labelAbove) null else ({ Text(label) }),
            isError = error != null,
            supportingText = support?.let { message -> { Text(message) } }, trailingIcon = trailingAction, leadingIcon = leadingIcon, visualTransformation = visual,
            singleLine = singleLine, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
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


/** Amber note under the PPM field when the typed value sits outside the reference that applies at the chosen time. */
@Composable private fun PpmReferenceWarning(context: CultivationUiState, draft: ActivityDraft) {
    val at = runCatching { OffsetDateTime.parse(draft.occurredAt()).toInstant() }.getOrNull() ?: return
    val target = com.inofarm.jogja.feature.context.applicableTarget(context.targets, draft.groupId, "manual_ppm", at) ?: return
    fun text(key: String) = target.takeUnless { it.isNull(key) }?.optString(key)
    val ppm = draft.ppm.replace(',', '.')
    val position = com.inofarm.jogja.feature.context.ppmPosition(text("minimum"), text("maximum"), text("target_value"), text("tolerance"), ppm)
    val bounds = com.inofarm.jogja.feature.context.ppmBounds(text("minimum"), text("maximum"), text("target_value"), text("tolerance")) ?: return
    val format = java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols(Locale.forLanguageTag("id-ID")))
    val message = when (position) {
        com.inofarm.jogja.feature.context.PpmPosition.BELOW -> "PPM di bawah acuan ${format.format(bounds.first)} ppm. Tetap bisa dikirim."
        com.inofarm.jogja.feature.context.PpmPosition.ABOVE -> "PPM di atas acuan ${format.format(bounds.second)} ppm. Tetap bisa dikirim."
        else -> return
    }
    val (container, content) = farmToneColors(FarmTone.Amber)
    Row(Modifier.fillMaxWidth().background(container, FarmShape.Tile).padding(FarmSpace.Medium).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small), verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides content) { FarmControlIcon(FarmIcon.AlertTriangle, null) }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

/** Swallows every touch while [hold] is true. The controls underneath stay visible and keep their own disabled state. */
private fun Modifier.holdTouches(hold: Boolean): Modifier = if (!hold) this else pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
