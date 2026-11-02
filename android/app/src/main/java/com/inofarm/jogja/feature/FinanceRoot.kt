package com.inofarm.jogja.feature

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inofarm.jogja.BuildConfig
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.network.LocalNetworkAccess
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.feature.activities.ActivityUiState
import com.inofarm.jogja.feature.activities.ActivityViewModel
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.activities.domain.MaterialDraft
import com.inofarm.jogja.feature.auth.AuthUiState
import com.inofarm.jogja.feature.auth.AuthViewModel
import com.inofarm.jogja.feature.context.ContextViewModel
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.history.*
import com.inofarm.jogja.feature.harvest.*
import com.inofarm.jogja.feature.expenses.*
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
    val authState by auth.state.collectAsStateWithLifecycle();val contextState by context.state.collectAsStateWithLifecycle();val activityState by activities.state.collectAsStateWithLifecycle()
    val historyState by history.state.collectAsStateWithLifecycle()
    val harvestState by harvest.state.collectAsStateWithLifecycle();val harvestHistoryState by harvestHistory.state.collectAsStateWithLifecycle()
    val financialState by financial.state.collectAsStateWithLifecycle();val expenseHistoryState by expenseHistory.state.collectAsStateWithLifecycle();val allocationHistoryState by allocationHistory.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if(authState.session!=null) context.refresh() }
    LaunchedEffect(authState.session?.scope?.key) { context.bind(authState.session);activities.bind(authState.session);history.bind(authState.session);harvest.bind(authState.session);harvestHistory.bind(authState.session) }
    LaunchedEffect(authState.session?.scope?.key,authState.session?.role) {val manager=authState.session?.takeIf {it.role==UserRole.MANAGER};financial.bind(manager);expenseHistory.bind(manager);allocationHistory.bind(manager)}
    LaunchedEffect(contextState.loading,contextState.message) { if(authState.session!=null && !contextState.loading) {activities.reload();history.reload();harvest.reload();harvestHistory.reload()} }
    LaunchedEffect(activityState.records,historyState.changed) {if(authState.session!=null) history.reload()}
    LaunchedEffect(historyState.changed) {if(historyState.changed>0 && authState.session!=null) {activities.reload();context.refresh()} }
    LaunchedEffect(harvestState.records) {if(authState.session!=null) harvestHistory.reload()}
    LaunchedEffect(harvestState.changed,harvestHistoryState.changed) {if((harvestState.changed>0 || harvestHistoryState.changed>0) && authState.session!=null) {activities.reload();context.refresh()} }
    LaunchedEffect(contextState.lastSyncedAt) {if(authState.session?.role==UserRole.MANAGER) {expenseHistory.reload();allocationHistory.reload()}}
    LaunchedEffect(financialState.changed,expenseHistoryState.changed,allocationHistoryState.changed) {
        if(authState.session?.role==UserRole.MANAGER) {expenseHistory.reload();allocationHistory.reload();if(financialState.changed>0 || expenseHistoryState.changed>0 || allocationHistoryState.changed>0) {activities.reload();context.refresh()}}
    }
    val pending=activityState.queue.firstOrNull { it.state=="QUEUED" }?.id
    LaunchedEffect(pending,authState.session?.scope?.key) { if(pending!=null && authState.session!=null) context.refresh() }
    val logout:()->Unit={
        val session=auth.state.value.session
        if(session!=null) {
            activities.pauseAutosave();harvest.pauseAutosave();financial.pauseAutosave()
            val draft=activities.state.value.draft;val harvestDraft=harvest.state.value.draft;val ticket=runtime.vault.generation
            val financialDraft=financial.state.value.draft
            auth.logout {
                check(runtime.vault.matches(ticket,session.scope)) {"Sesi berubah sebelum penyimpanan."}
                draft?.let {runtime.activities.flush(session,ticket,it)}
                harvestDraft?.let {runtime.harvest.flush(session,ticket,it)}
                financialDraft?.let {runtime.financialEntry.flush(session,ticket,it)}
                check(runtime.vault.matches(ticket,session.scope)) {"Sesi berubah selama penyimpanan."}
            }
        }
    }
    BackHandler(authState.loading && authState.session!=null) { /* Wait for the bounded local persistence boundary. */ }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        if(authState.session==null) FinanceLogin(authState,auth::login)
        else if(authState.loading) Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.Center) {CircularProgressIndicator();Spacer(Modifier.height(16.dp));Text("Menyimpan isian terakhir sebelum keluar…")}
        else key(authState.session!!.scope.key) { FinanceHome(authState.session!!,contextState,activityState,context::refresh,logout,activities,historyState,history,harvestState,harvest,harvestHistoryState,harvestHistory,financialState,financial,expenseHistoryState,expenseHistory,allocationHistoryState,allocationHistory) }
    }
    if(authState.session!=null && !authState.loading && authState.error!=null) AlertDialog(onDismissRequest=auth::clearError,title={Text("Belum keluar")},text={Text(authState.error!!)},confirmButton={TextButton(onClick=auth::clearError) {Text("Kembali ke isian")}})
}

@Composable private fun FinanceLogin(state:AuthUiState,onLogin:(String,String,String)->Unit) {
    var server by rememberSaveable { mutableStateOf(BuildConfig.FINANCE_API_BASE_URL) };var email by rememberSaveable { mutableStateOf("") };var password by remember { mutableStateOf("") }
    val context=LocalContext.current
    var permissionMessage by remember {mutableStateOf<String?>(null)}
    var pendingLogin by remember {mutableStateOf<Triple<String,String,String>?>(null)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending=pendingLogin;pendingLogin=null
        if(granted && pending!=null) onLogin(pending.first,pending.second,pending.third)
        else permissionMessage="Akses jaringan lokal belum diizinkan. Izinkan untuk tersambung ke server ini; draf tetap disimpan."
    }
    Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max=640.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(24.dp));Text("INOFARM",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text("Catatan greenhouse,\ndalam satu tempat.",style=MaterialTheme.typography.headlineLarge)
        Text("Masuk dengan akun pekerja atau pengelola. Draf dan antrean tetap tersimpan saat koneksi terputus.")
        FinanceField("URL API operasional",server,{server=it},"finance-server",hint="https://…/api/v2/")
        FinanceField("Email",email,{email=it},"finance-email",keyboard=KeyboardType.Email)
        OutlinedTextField(password,{password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("finance-password"),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
        state.error?.let { Notice(it,true) }
        permissionMessage?.let {Notice(it,true)}
        Button(onClick={
            permissionMessage=null
            if(LocalNetworkAccess.requiredFor(server,android.os.Build.VERSION.SDK_INT) && ContextCompat.checkSelfPermission(context,LocalNetworkAccess.PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingLogin=Triple(server,email,password);permission.launch(LocalNetworkAccess.PERMISSION)
            } else onLogin(server,email,password)
        },enabled=!state.loading && email.isNotBlank() && password.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("finance-login")) { Text(if(state.loading) "Memproses…" else "Masuk") }
        Text("Login pertama memerlukan koneksi. Data keuangan hanya tersedia bagi pengelola.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun FinanceHome(session:FinanceSession,context:CultivationUiState,activity:ActivityUiState,onSync:()->Unit,onLogout:()->Unit,vm:ActivityViewModel,history:HistoryUiState,historyVm:HistoryViewModel,
    harvest:HarvestUiState,harvestVm:HarvestViewModel,harvestHistory:HistoryUiState,harvestHistoryVm:HistoryViewModel,financial:FinancialEntryState,financialVm:FinancialEntryViewModel,
    expenseHistory:HistoryUiState,expenseHistoryVm:HistoryViewModel,allocationHistory:HistoryUiState,allocationHistoryVm:HistoryViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) };var gh by rememberSaveable { mutableStateOf<String?>(null) };var season by rememberSaveable { mutableStateOf<String?>(null) };var group by rememberSaveable { mutableStateOf<String?>(null) }
    val writing=activity.busy || harvest.busy || history.busy || harvestHistory.busy || financial.busy || expenseHistory.busy || allocationHistory.busy
    val keyboard=LocalSoftwareKeyboardController.current;val ime=WindowInsets.ime.getBottom(LocalDensity.current)>0
    LaunchedEffect(context.greenhouses.map { it.getString("id") }) { if(gh!=null && context.greenhouses.none { it.getString("id")==gh }) { gh=null;season=null;group=null } }
    BackHandler(writing || activity.draft!=null || harvest.draft!=null || financial.draft!=null || history.selected!=null || harvestHistory.selected!=null || expenseHistory.selected!=null || allocationHistory.selected!=null || tab!=0) {
        when { writing -> Unit;ime -> keyboard?.hide();activity.draft!=null && activity.reviewing -> vm.backToForm();activity.draft!=null -> vm.saveAndClose();harvest.draft!=null && harvest.reviewing -> harvestVm.backToForm();harvest.draft!=null -> harvestVm.saveAndClose();financial.draft!=null && financial.reviewing -> financialVm.backToForm();financial.draft!=null -> financialVm.saveAndClose();expenseHistory.comparison!=null -> expenseHistoryVm.closeComparison();expenseHistory.selected!=null -> expenseHistoryVm.close();allocationHistory.comparison!=null -> allocationHistoryVm.closeComparison();allocationHistory.selected!=null -> allocationHistoryVm.close();history.comparison!=null -> historyVm.closeComparison();history.selected!=null -> historyVm.close();harvestHistory.comparison!=null -> harvestHistoryVm.closeComparison();harvestHistory.selected!=null -> harvestHistoryVm.close();else -> tab=0 }
    }
    Scaffold(contentWindowInsets=WindowInsets.safeDrawing,topBar={ Column(Modifier.statusBarsPadding().padding(horizontal=16.dp,vertical=8.dp)) {
        if(ime && (activity.draft!=null || harvest.draft!=null || financial.draft!=null)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(if(financial.draft!=null && LocalDensity.current.fontScale>1.5f) "Isian" else if(financial.draft!=null) "Catat ${financial.draft.kind.label}" else if(harvest.draft!=null) "Catat panen" else "Catat kegiatan",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={keyboard?.hide();if(financial.draft!=null) financialVm.saveAndClose() else if(harvest.draft!=null) harvestVm.saveAndClose() else vm.saveAndClose()},enabled=!writing) {Text("Simpan draf")} }
        } else {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text("Inofarm",style=MaterialTheme.typography.titleLarge);Text("${session.name} · ${if(session.role==UserRole.WORKER) "Pekerja" else "Pengelola"}",style=MaterialTheme.typography.bodySmall) };TextButton(onClick=onLogout,enabled=!writing,modifier=Modifier.testTag("finance-logout")) {Text("Keluar")} }
        if(session.expired()) Notice("Sesi berakhir. Masuk kembali dengan akun yang sama untuk mengirim antrean. Draf tetap dapat disimpan.",true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Pencatatan manual",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);TextButton(onClick=onSync,enabled=!context.loading,modifier=Modifier.testTag("finance-sync")) {Text(if(context.loading) "Memeriksa…" else "Sinkronkan")} }
        }
    } },bottomBar={ if(activity.draft==null && harvest.draft==null && financial.draft==null && history.selected==null && harvestHistory.selected==null && expenseHistory.selected==null && allocationHistory.selected==null) NavigationBar { (listOf("Beranda","Antrean","Kegiatan","Panen")+if(session.role==UserRole.MANAGER) listOf("Keuangan") else emptyList()).forEachIndexed { index,label -> NavigationBarItem(selected=tab==index,onClick={tab=index},icon={Text(listOf("⌂","↻","≡","▦","Rp")[index])},label={Text(label)},modifier=Modifier.testTag("finance-tab-$index")) } } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal=16.dp).widthIn(max=900.dp)) {
            when {
                activity.busy || harvest.busy || financial.busy -> Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center) {CircularProgressIndicator();Text("Menyimpan isian ke perangkat…")}
                activity.draft!=null -> if(activity.reviewing) ActivityReview(activity.draft,context,activity.busy,vm::backToForm,vm::queue) else ActivityForm(activity,context,vm)
                harvest.draft!=null -> if(harvest.reviewing) HarvestReview(harvest.draft,context,harvest.busy,harvestVm::backToForm,harvestVm::queue) else HarvestForm(harvest,context,harvestVm)
                session.role==UserRole.MANAGER && financial.draft!=null -> if(financial.reviewing) FinancialReview(financial.draft,context,financial.busy,financialVm::backToForm,financialVm::queue) else FinancialForm(financial,context,financialVm)
                session.role==UserRole.MANAGER && expenseHistory.selected!=null -> HistoryScreen(session,context,expenseHistory,expenseHistoryVm,activity.queue,onEdit={record,replace -> financialVm.open(record,replace);expenseHistoryVm.close()},kind=OperationalKind.EXPENSE)
                session.role==UserRole.MANAGER && allocationHistory.selected!=null -> HistoryScreen(session,context,allocationHistory,allocationHistoryVm,activity.queue,onEdit={record,replace -> financialVm.open(record,replace);allocationHistoryVm.close()},kind=OperationalKind.COST_ALLOCATION)
                history.selected!=null -> HistoryScreen(session,context,history,historyVm,activity.queue,onEdit={record,replace -> vm.open(record,replace);historyVm.close()})
                harvestHistory.selected!=null -> HistoryScreen(session,context,harvestHistory,harvestHistoryVm,activity.queue,onEdit={record,replace -> harvestVm.open(record,replace);harvestHistoryVm.close()},kind=OperationalKind.HARVEST)
                tab==0 -> LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
                    item { Text("Kegiatan hari ini",style=MaterialTheme.typography.headlineMedium);Text("Pilih konteks budidaya sebelum mencatat. Target hanya menjadi acuan.") }
                    item { context.message?.let { Text(it,modifier=Modifier.testTag("finance-sync-message")) };Text(context.lastSyncedAt?.let {"Data lokal diperbarui: ${timeLabel(Instant.ofEpochMilli(it).toString())}"} ?: "Belum ada data server tersimpan.",style=MaterialTheme.typography.bodySmall) }
                    item { Choice("Greenhouse",gh,context.greenhouses.filter { it.optBoolean("active",true) },"finance-gh") {gh=it;season=null;group=null} }
                    item { Choice("Musim",season,context.seasons.filter { it.optString("greenhouse_id")==gh },"finance-season") {season=it;group=null} }
                    if(session.role==UserRole.MANAGER && season!=null) item {context.seasons.find {it.optString("id")==season}?.let {SeasonActions(it,history.busy,history.message,historyVm::season)}}
                    item { Choice("Kelompok tanam",group,context.groups.filter { it.optString("season_id")==season && it.optBoolean("active",true) },"finance-group") {group=it} }
                    if(group!=null && gh!=null) {
                        val open=context.seasons.find { it.optString("id")==season }?.optString("status")=="open"
                        item { Button(onClick={vm.new(gh!!,group!!)},enabled=open,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("finance-new")) {Text("Catat kegiatan")};if(!open) Notice("Musim ditutup. Pengelola perlu membuka kembali sebelum pencatatan.",true) }
                        item {OutlinedButton(onClick={harvestVm.new(gh!!,group!!)},enabled=open,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("harvest-new")) {Text("Catat panen")}}
                        item { Text("Jadwal dan target",style=MaterialTheme.typography.titleLarge) }
                        val schedules=context.schedules.filter { it.optString("planting_group_id")==group && it.optBoolean("active",true) }
                        if(schedules.isEmpty()) item { Notice("Belum ada jadwal untuk kelompok ini. Kegiatan spontan tetap dapat dicatat.") }
                        items(schedules,key={it.getString("id")}) { schedule -> ElevatedCard { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(ActivityType.entries.find { it.wire==schedule.optString("activity_type") }?.label ?: "Kegiatan");Text(timeLabel(schedule.optString("planned_at")));Text(schedule.optString("notes"));Text("Satu catatan tidak otomatis menyelesaikan seluruh jadwal.",style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={vm.new(gh!!,group!!,ActivityType.entries.single { it.wire==schedule.getString("activity_type") },schedule.getString("id"))},enabled=open) {Text("Catat realisasi")} } } }
                        item { ReferenceCards(context,group!!,null) }
                    } else if(!context.loading && context.greenhouses.isEmpty()) item { Notice("Belum ada greenhouse dalam penugasan yang tersimpan. Sinkronkan atau hubungi pengelola.") }
                    item { activity.message?.let { Notice(it) } }
                }
                tab==1 -> LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
                    item { Text("Antrean akun ini",style=MaterialTheme.typography.headlineMedium);Text("Disimpan lokal belum berarti diterima server. Operasi yang ditahan tetap disimpan.") }
                    item { context.message?.let { Text(it,modifier=Modifier.testTag("finance-sync-message")) } }
                    if(activity.queue.isEmpty()) item { Notice("Belum ada operasi dalam antrean.") }
                    items(activity.queue,key={it.id}) { op -> ElevatedCard { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(if(op.state=="SUPERSEDED") "Digantikan setelah ditinjau" else runCatching { SyncState.valueOf(op.state).label() }.getOrDefault("Perlu ditinjau"),fontWeight=FontWeight.SemiBold)
                        Text("Catatan ${op.entityId.take(8)} · percobaan ${op.attempts}",style=MaterialTheme.typography.bodySmall)
                        op.error?.let { ErrorReasons(it) }
                        if(op.envelope.optString("entity_type") in setOf("activity","harvest","expense","cost_allocation")) TextButton(onClick={when(op.envelope.optString("entity_type")) {"harvest" -> {harvestHistoryVm.select(op.entityId);tab=3};"expense" -> {expenseHistoryVm.select(op.entityId);tab=4};"cost_allocation" -> {allocationHistoryVm.select(op.entityId);tab=4};else -> {historyVm.select(op.entityId);tab=2}}}) {Text("Tinjau catatan")}
                        Text(if(op.state=="ACCEPTED") "Penerimaan tersimpan. Ini tidak menyatakan kesesuaian agronomi." else "ID operasi tetap sama saat dicoba kembali.",style=MaterialTheme.typography.bodySmall)
                    } } }
                }
                tab==4 && session.role==UserRole.MANAGER -> FinancialHub(session,context,financialVm,expenseHistory,expenseHistoryVm,allocationHistory,allocationHistoryVm,activity.queue)
                tab==2 -> HistoryScreen(session,context,history,historyVm,activity.queue,onEdit={record,replace -> vm.open(record,replace);historyVm.close()})
                else -> HistoryScreen(session,context,harvestHistory,harvestHistoryVm,activity.queue,onEdit={record,replace -> harvestVm.open(record,replace);harvestHistoryVm.close()},kind=OperationalKind.HARVEST)
            }
        }
    }
}

@Composable private fun ActivityForm(state:ActivityUiState,context:CultivationUiState,vm:ActivityViewModel) {
    val draft=state.draft ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(if(draft.baseVersion==null) "Catat kegiatan" else "Koreksi kegiatan",style=MaterialTheme.typography.headlineMedium)
        Text(if(state.saved) "Draf tersimpan di perangkat." else "Perubahan belum selesai disimpan.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("finance-draft-status"))
        Choice("Jenis kegiatan",draft.type.wire,ActivityType.entries.map { JSONObject().put("id",it.wire).put("name",it.label) },"finance-type") { value -> vm.edit { it.copy(type=ActivityType.entries.single { type -> type.wire==value }) } }
        FinanceField("Tanggal kejadian (YYYY-MM-DD)",draft.date,{value -> vm.edit {it.copy(date=value)}},"finance-date",state.errors["occurred_at"])
        FinanceField("Jam kejadian WIB (HH:mm)",draft.time,{value -> vm.edit {it.copy(time=value)}},"finance-time",hint="Detik boleh ditulis HH:mm:ss dan tetap dipertahankan.")
        Row { Checkbox(draft.skipped,{value -> vm.edit {it.copy(skipped=value)}},modifier=Modifier.testTag("finance-skipped"));Text("Kegiatan dilewati",Modifier.padding(top=12.dp)) }
        if(draft.skipped) FinanceField("Alasan kegiatan dilewati",draft.skippedReason,{value -> vm.edit {it.copy(skippedReason=value)}},"finance-skipped-reason",state.errors["skipped_reason"])
        if(draft.type==ActivityType.NUTRITION || draft.ppm.isNotBlank() || draft.ph.isNotBlank()) {
            FinanceField("PPM manual (ppm)",draft.ppm,{value -> vm.edit {it.copy(ppm=value)}},"finance-ppm",state.errors["manual_ppm"],KeyboardType.Decimal)
            FinanceField("pH manual (0–14)",draft.ph,{value -> vm.edit {it.copy(ph=value)}},"finance-ph",state.errors["manual_ph"],KeyboardType.Decimal)
        }
        if(draft.type==ActivityType.STAGE || draft.stage.isNotBlank()) FinanceField("Kondisi stadia aktual",draft.stage,{value -> vm.edit {it.copy(stage=value)}},"finance-stage",state.errors["actual_stage"],hint="Dipilih dari kondisi aktual, bukan umur")
        if(draft.type in setOf(ActivityType.NUTRITION,ActivityType.SPRAY) || draft.materials.isNotEmpty()) {
            Text("Bahan aktual",style=MaterialTheme.typography.titleLarge)
            draft.materials.forEachIndexed { index,row -> OutlinedCard { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                fun change(updated:MaterialDraft) { vm.edit {it.copy(materials=it.materials.toMutableList().apply {set(index,updated)})} }
                val material=context.materials.find {it.optString("id")==row.materialId}
                Choice("Bahan ${index+1}",row.materialId,context.materials.filter {it.optString("greenhouse_id")==draft.greenhouseId && (it.optBoolean("active",true) || it.optString("id")==row.materialId)},"finance-material-$index") {change(row.copy(materialId=it,unitId=""))}
                Choice("Satuan ${index+1}",row.unitId,context.units.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("dimension")==material?.optString("dimension") && (it.optBoolean("active",true) || it.optString("id")==row.unitId)},"finance-unit-$index") {change(row.copy(unitId=it))}
                FinanceField("Jumlah ${index+1}",row.quantity,{change(row.copy(quantity=it))},"finance-quantity-$index",state.errors["materials.$index"],KeyboardType.Decimal,hint="Desimal boleh koma; tanpa pemisah ribuan")
                TextButton(onClick={vm.edit {it.copy(materials=it.materials.filterIndexed { i,_ -> i!=index })}}) {Text("Hapus bahan ${index+1}")}
            } } }
            OutlinedButton(onClick={vm.edit {it.copy(materials=it.materials+MaterialDraft())}},enabled=draft.materials.size<30,modifier=Modifier.fillMaxWidth().testTag("finance-add-material")) {Text("Tambah bahan")}
            state.errors["materials"]?.let { Notice(it,true) }
        }
        FinanceField("Keterangan pekerjaan",draft.notes,{value -> vm.edit {it.copy(notes=value)}},"finance-notes",state.errors["notes"],singleLine=false)
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
        Button(onClick=vm::review,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("finance-review")) {Text("Periksa catatan")}
        OutlinedButton(onClick=vm::saveAndClose,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("finance-save-draft")) {Text("Simpan draf dan kembali")}
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun ActivityReview(draft:ActivityDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Periksa catatan",style=MaterialTheme.typography.headlineMedium)
        if(draft.replaceValidationRejected) Notice("Perbaikan ini membuat operasi baru. Operasi yang ditolak tetap disimpan sebagai riwayat dan tidak dikirim ulang.")
        Text(context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Kelompok tanam")
        Text("${draft.type.label} · ${draft.date} ${draft.time} WIB")
        Text(if(draft.skipped) "Dilewati: ${draft.skippedReason}" else "Kegiatan selesai")
        if(draft.ppm.isNotBlank()) Text("PPM manual: ${draft.ppm} ppm")
        if(draft.ph.isNotBlank()) Text("pH manual: ${draft.ph}")
        if(draft.stage.isNotBlank()) Text("Stadia aktual: ${draft.stage}")
        draft.materials.forEach { item -> Text("${context.materials.find {it.optString("id")==item.materialId}?.optString("name") ?: "Bahan"}: ${item.quantity} ${context.units.find {it.optString("id")==item.unitId}?.optString("name").orEmpty()}") }
        Text(draft.notes);if(draft.reason.isNotBlank()) Text("Alasan koreksi: ${draft.reason}")
        Notice("Catatan akan disimpan lokal dan diantrekan. Status diterima diberikan setelah server mengonfirmasi.")
        Button(onClick=onQueue,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("finance-queue")) {Text(if(busy) "Menyimpan…" else "Simpan lokal & antrekan")}
        OutlinedButton(onClick=onBack,enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Kembali ke isian")}
    }
}

@Composable internal fun RecordDetail(record:LocalRecord,session:FinanceSession,context:CultivationUiState,onClose:()->Unit,onEdit:()->Unit,kind:OperationalKind=OperationalKind.ACTIVITY,actions:@Composable ()->Unit={}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Detail ${kind.label}",style=MaterialTheme.typography.headlineMedium);Text(record.syncState.label(),fontWeight=FontWeight.Bold)
        RecordFacts(record,session,context,kind)
        record.error?.let { ErrorReasons(it) }
        val own=record.recorderId().let {it.isBlank() || it==session.scope.accountId}
        if(record.body.optString("record_state")!="cancelled" && (own || session.role==UserRole.MANAGER)) OutlinedButton(onClick=onEdit,modifier=Modifier.fillMaxWidth().testTag("finance-edit")) {Text(if(record.serverVersion==null) "Lanjutkan draf" else "Koreksi catatan")}
        actions()
        OutlinedButton(onClick=onClose,modifier=Modifier.fillMaxWidth()) {Text("Kembali ke riwayat")}
    }
}
@Composable internal fun ActivityFacts(record:LocalRecord,session:FinanceSession,context:CultivationUiState) {
        if(record.body.optString("record_state")=="cancelled") Text("Dibatalkan",color=MaterialTheme.colorScheme.error)
        val shown=runCatching {ActivityDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion)}.getOrNull()
        shown?.let { draft ->
            Text(context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Kelompok tanam")
            Text("${draft.type.label} · ${draft.date} ${draft.time} WIB")
            Text(if(draft.skipped) "Dilewati: ${draft.skippedReason}" else "Kegiatan selesai")
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
            snap.optJSONObject("checks")?.let { checks -> checks.keys().forEach { parameter -> val check=checks.getJSONObject(parameter);Text("${if(parameter=="manual_ppm") "PPM" else "pH"}: ${when(check.optString("status")) {"delta_only" -> "selisih ${check.optString("delta")}";"within_range" -> "dalam rentang";"outside_range" -> "di luar rentang";else -> "belum dapat dibandingkan"}}")
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
    val targets=context.targets.filter { it.optString("planting_group_id")==group && it.optBoolean("active",true) && (draft==null || it.optString("activity_type")==draft.type.wire) }.filter { target ->
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
@Composable internal fun Notice(message:String,error:Boolean=false) { Surface(color=if(error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {Text(message,Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)} }
@Composable internal fun FinanceField(label:String,value:String,onChange:(String)->Unit,tag:String,error:String?=null,keyboard:KeyboardType=KeyboardType.Text,hint:String?=null,singleLine:Boolean=true) {
    OutlinedTextField(value,onChange,label={Text(label)},isError=error!=null,supportingText={if(error!=null) Text(error) else if(hint!=null) Text(hint)},singleLine=singleLine,keyboardOptions=KeyboardOptions(keyboardType=keyboard),modifier=Modifier.fillMaxWidth().testTag(tag))
}
@Composable internal fun Choice(label:String,selected:String?,options:List<JSONObject>,tag:String,onSelect:(String)->Unit) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    val value=options.find {it.optString("id")==selected}?.optString("name")
    OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag(tag)) {Text("$label: ${value ?: "pilih"}",modifier=Modifier.fillMaxWidth())}
    if(expanded) AlertDialog(onDismissRequest={expanded=false},title={Text("Pilih $label")},text={LazyColumn(Modifier.heightIn(max=380.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(options.isEmpty()) item {Text("Belum ada pilihan yang tersedia.")}
        items(options,key={it.getString("id")}) { row -> TextButton(onClick={onSelect(row.getString("id"));expanded=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("$tag-option-${row.getString("id")}")) {Text(row.getString("name"),Modifier.fillMaxWidth())} }
    }},confirmButton={TextButton(onClick={expanded=false}) {Text("Tutup")}})
}
