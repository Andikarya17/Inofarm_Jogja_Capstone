package com.inofarm.jogja.feature.admin

import com.inofarm.jogja.core.design.FarmCheckbox
import com.inofarm.jogja.core.design.FarmDraftStatus
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmPageTitle
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmSectionHeading
import com.inofarm.jogja.core.design.FarmSheetHeading
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.LocalFarmDialogPicker
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.Notice

@Composable fun AdminHost(
    session: FinanceSession, repository: AdminRepository, onAccepted: () -> Unit,
    initialResource: AdminResource = AdminResource.GREENHOUSE,
    onReviewPeriod: ((String) -> Unit)? = null,
    onClose: () -> Unit,
    initialGreenhouse: String? = null,
) {
    if(session.role!=UserRole.MANAGER) {LaunchedEffect(Unit) {onClose()};return}
    val vm:AdminViewModel=viewModel(key="admin-${session.scope.key}-${repository.vault.generation}",factory=object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>)=AdminViewModel(repository,session) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    var entered by rememberSaveable(initialResource) { mutableStateOf(false) }
    LaunchedEffect(initialResource, state.busy) {
        if (!entered && !state.busy) {
            entered = true
            if (state.resource != initialResource || state.detail != null || initialGreenhouse != null && state.greenhouse != initialGreenhouse)
                vm.choose(initialResource, initialGreenhouse ?: state.greenhouse)
        }
    }
    var focused by rememberSaveable(initialResource, initialGreenhouse) { mutableStateOf(false) }
    LaunchedEffect(state.rows, state.busy, state.listChecked) {
        if (!focused && initialGreenhouse != null && initialResource == AdminResource.GREENHOUSE &&
            state.resource == AdminResource.GREENHOUSE && !state.busy && state.listChecked) {
            state.rows.find { it.optString("id") == initialGreenhouse }?.let { focused = true; vm.detail(it) }
        }
    }
    var search by rememberSaveable(state.resource,state.greenhouse) { mutableStateOf("") }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {vm.flush()}
    val close={if(!state.busy && vm.flush()) onClose()}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=false)) {
        BackHandler {if(state.editing) vm.back() else if(state.detail!=null) vm.closeDetail() else close()}
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = FarmSpace.Page)) {
                val title = when (initialResource) {
                    AdminResource.USER -> "Pekerja dan akun"
                    AdminResource.GREENHOUSE -> "Greenhouse"
                    else -> initialResource.label
                }
                FarmSheetHeading(title, close, !state.busy)
                HorizontalDivider()
                CompositionLocalProvider(LocalFarmDialogPicker provides true) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = FarmSpace.Medium),
                    verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                state.message?.let {Notice(it)}
                if(state.busy) { Text("Memuat…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if(state.editing) AdminEditor(state,vm,onAccepted)
                else if(state.detail!=null) {
                    val row=requireNotNull(state.detail)
                    val isGreenhouse=state.resource==AdminResource.GREENHOUSE
                    FarmPageTitle(row.optString("name"))
                    FarmFact("Status",if(row.optBoolean("active",true)) "Aktif" else "Nonaktif")
                    if(!isGreenhouse) {
                        FarmFact("Email",row.optString("email"))
                        FarmFact("Peran",if(row.optString("role")=="manager") "Pengelola" else "Pekerja")
                    }
                    state.readiness?.let { counts ->
                        FarmSection(if(isGreenhouse) "Kesiapan pencatatan" else "Greenhouse pekerja") {
                            counts.forEach { (label,value) -> FarmFact(label,value.toString()) }
                        }
                    }
                    if (isGreenhouse) FarmSection("Tanam saat ini") {
                        val period = state.period
                        when (period?.optString("state")) {
                            "ready" -> {
                                FarmFact("Musim", period.getString("season_name"))
                                FarmFact("Kelompok tanam", period.getString("group_name"))
                                FarmFact("Varietas", period.getString("variety"))
                                FarmFact("Populasi", period.optString("population").takeIf { it.isNotBlank() }?.let { "$it tanaman" } ?: "Belum tersedia")
                                FarmFact("Semai", period.getString("sowing_date"))
                                FarmFact("Pindah tanam", if (period.isNull("transplant_date")) "Belum dicatat" else period.getString("transplant_date"))
                                val phase = period.optJSONObject("phase")
                                FarmFact("Stadia aktual", phase?.optString("value") ?: "Belum dicatat")
                                phase?.let { Text(com.inofarm.jogja.feature.timeLabel(it.getString("occurred_at"))) }
                            }
                            "inactive" -> Text("Greenhouse nonaktif. Riwayat tetap tersedia.")
                            "needs_review", "no_active_period" -> Text("Tanam saat ini belum ditetapkan.")
                            else -> if (!state.busy) Text("Tanam saat ini belum bisa dimuat.")
                        }
                    }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(FarmSpace.Small)) {
                        TextButton(onClick={vm.open(row)},enabled=!state.busy && state.form==null,
                            modifier=Modifier.heightIn(min=FarmSpace.Touch)) { Text("Ubah") }
                        if(isGreenhouse) {
                            TextButton(onClick={vm.setup(AdminResource.SEASON,row)},enabled=!state.busy,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("admin-setup-season")) { Text("Tambah musim") }
                            TextButton(onClick={vm.setup(AdminResource.VARIETY,row)},enabled=!state.busy,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("admin-setup-variety")) { Text("Tambah varietas") }
                            TextButton(onClick={vm.setup(AdminResource.GROUP,row)},enabled=!state.busy,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("admin-setup-group")) { Text("Tambah kelompok tanam") }
                            if (onReviewPeriod != null) TextButton(onClick = {
                                if (vm.flush()) onReviewPeriod(row.getString("id"))
                            }, enabled = !state.busy && row.optBoolean("active", true),
                                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("admin-setup-period")) { Text("Ganti tanam saat ini") }
                            TextButton(onClick={vm.setup(AdminResource.ASSIGNMENT,row)},enabled=!state.busy,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("admin-setup-assignment")) { Text("Atur pekerja") }
                        }
                    }
                    OutlinedButton(onClick=vm::closeDetail,enabled=!state.busy,
                        modifier=Modifier.heightIn(min=FarmSpace.Touch)) { Text("Kembali ke daftar") }
                }
                else {
                    state.setupGreenhouse?.let { greenhouse ->
                        TextButton(onClick = vm::returnToGreenhouse, enabled = !state.busy,
                            modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Kembali ke ${greenhouse.getString("name")}") }
                    }
                    state.form?.let {Button(onClick=vm::resume,enabled=!state.busy,modifier=Modifier.testTag("admin-resume")) {Text(if(state.pending) "Kirim ulang" else "Lanjutkan draf ${it.resource.label}")}}
                    FarmPanel(title = "Pilih data") {
                        Choice("Jenis data",state.resource.name,AdminResource.entries.map {org.json.JSONObject().put("id",it.name).put("name",it.label)},"admin-resource") {vm.choose(AdminResource.valueOf(it))}
                        if(state.resource!=AdminResource.GREENHOUSE)
                            Choice("Greenhouse",state.greenhouse,state.options["greenhouses"].orEmpty(),"admin-greenhouse",
                                loading=state.busy,onSelect=vm::greenhouse)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                            Button(onClick={vm.open()},enabled=!state.busy && state.form==null && (state.resource==AdminResource.GREENHOUSE || state.greenhouse!=null),
                                shape=FarmShape.Control,modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("admin-new")) {
                                Text(if(state.resource==AdminResource.ASSIGNMENT) "Tambah pekerja" else "Tambah ${state.resource.label.lowercase()}")
                            }
                            TextButton(onClick={vm.reload()},enabled=!state.busy,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch)) {Text("Perbarui")}
                        }
                    }
                    FarmSectionHeading("Daftar ${state.resource.label.lowercase()}")
                    if(state.resource!=AdminResource.GREENHOUSE && state.greenhouse==null) Text("Pilih greenhouse untuk melihat dan menambah data.")
                    if(state.rows.isEmpty() && state.listChecked) Text("Belum ada ${state.resource.label.lowercase()}.")
                    val searchable=state.resource in setOf(AdminResource.GREENHOUSE,AdminResource.USER)
                    if(searchable && state.rows.isNotEmpty()) FinanceField("Cari ${state.resource.label.lowercase()}",search,{search=it},"admin-search")
                    val visibleRows=state.rows.filter { !searchable || it.optString("name").contains(search.trim(),true) ||
                        state.resource==AdminResource.USER && it.optString("email").contains(search.trim(),true) }
                    if(visibleRows.isEmpty() && state.rows.isNotEmpty()) Text("Tidak ada kecocokan dalam daftar yang dimuat.")
                    visibleRows.forEach {row ->
                        FarmPanel(title = row.optString("name").ifBlank {
                            row.optString("activity_type").ifBlank {row.optString("user_name").ifBlank {row.optString("user_id")}}
                        }) {
                            Text("Versi ${row.optLong("version")} · ${if(row.optString("status")=="closed") "Musim ditutup" else if(row.optBoolean("active",true)) "Aktif" else "Nonaktif"}",
                                style = MaterialTheme.typography.bodyMedium)
                            if(state.resource==AdminResource.USER)
                                Text("${row.optString("email")} · ${if(row.optString("role")=="manager") "Pengelola" else "Pekerja"}")
                            if(state.resource==AdminResource.ASSIGNMENT) {
                                FarmFact("Mulai",com.inofarm.jogja.feature.timeLabel(row.optString("effective_from")))
                                FarmFact("Akhir",if(row.isNull("effective_until")) "Tanpa batas" else com.inofarm.jogja.feature.timeLabel(row.optString("effective_until")))
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                                if(searchable) TextButton(onClick={vm.detail(row)},enabled=!state.busy,
                                    modifier=Modifier.heightIn(min=FarmSpace.Touch)) {Text("Lihat detail")}
                                TextButton(onClick={vm.open(row)},enabled=!state.busy && state.form==null,
                                    modifier=Modifier.heightIn(min=FarmSpace.Touch)) {Text("Ubah")}
                                if(state.resource==AdminResource.SEASON) TextButton(onClick={vm.open(row,if(row.optString("status")=="closed") "reopen" else "close")},enabled=!state.busy && state.form==null,
                                    modifier=Modifier.heightIn(min=FarmSpace.Touch)) {Text(if(row.optString("status")=="closed") "Buka kembali musim" else "Tutup musim")}
                            }
                        }
                    }
                    if(state.cursor!=null) TextButton(onClick={vm.reload(true)},enabled=!state.busy) {Text("Muat berikutnya")}
                }
                }
                }
            }
        }
    }
}

@Composable private fun AdminEditor(state:AdminState,vm:AdminViewModel,onAccepted:()->Unit) {
    val form=state.form ?: return
    var password by remember(form.id,state.pending) {mutableStateOf("")}
    var passwordConfirmation by remember(form.id,state.pending) {mutableStateOf("")}
    var unassignedEmail by rememberSaveable(form.id,form.values["greenhouse_id"]) {mutableStateOf("")}
    val confirmPassword=form.resource==AdminResource.USER && !state.pending && (form.version==null || password.isNotEmpty())
    var confirm by rememberSaveable {mutableStateOf(false)}
    var discard by rememberSaveable {mutableStateOf(false)}
    val action = when {
        form.version == null -> "Tambah"
        form.action == "close" -> "Tutup"
        form.action == "reopen" -> "Buka kembali"
        else -> "Ubah"
    }
    fun display(field: AdminField): String {
        val value = form.values[field.key].orEmpty()
        return if (field.type == "boolean") {
            if (value == "true") "Ya" else "Tidak"
        } else field.choices[value] ?: state.options[field.source]?.find { it.optString("id") == value }
            ?.optString("name") ?: value.ifBlank { "Belum diisi" }
    }
    val original = state.rows.find { it.optString("id") == form.id }
    val confirmation: Triple<String, String, String>? = when {
        form.action == "close" -> Triple("Tutup musim?", "Catatan baru ditahan sampai musim dibuka lagi.", "Tutup musim")
        form.version != null && original?.optBoolean("active", true) != false && form.values["active"] == "false" ->
            Triple("Nonaktifkan ${form.resource.label.lowercase()}?", when (form.resource) {
                AdminResource.GREENHOUSE -> "Data lama tidak dihapus. Greenhouse dinonaktifkan dan riwayatnya tetap ada."
                AdminResource.USER -> "Akun ini tidak bisa masuk lagi. Riwayatnya tetap ada."
                AdminResource.ASSIGNMENT -> "Pekerja tidak lagi melihat greenhouse ini."
                else -> "Data lama tidak dihapus. Riwayatnya tetap ada."
            }, "Nonaktifkan")
        form.resource == AdminResource.USER && form.version != null && original != null && original.optString("role") != form.values["role"] ->
            Triple("Ubah peran akun?", "Akun ini perlu masuk ulang.", "Ubah peran")
        form.resource == AdminResource.USER && form.version != null && password.isNotEmpty() ->
            Triple("Reset password?", "Akun ini perlu masuk ulang.", "Reset password")
        else -> null
    }
    FarmPageTitle("$action ${form.resource.label}")
    FarmDraftStatus(state.saved, Modifier.testTag("admin-saved"))
    if(state.pending) Notice("Belum terkirim. Kirim ulang, isinya tetap sama.")
    FarmSection("Isian ${form.resource.label.lowercase()}") {
    form.fields.forEach {field ->
        val value=form.values[field.key].orEmpty()
        if(state.pending || state.busy || form.version!=null && field.key=="greenhouse_id") FarmFact(field.label, display(field))
        else when {
            field.type=="boolean" -> FarmCheckbox(field.label, value == "true", { vm.edit(field.key, it.toString()) })
            field.source!=null || field.choices.isNotEmpty() -> {
                val options=if(field.source!=null) state.options[field.source].orEmpty().map {row -> org.json.JSONObject(row.toString()).put("name",row.optString("name").ifBlank {row.optString("id")})} else field.choices.map {(id,label) -> org.json.JSONObject().put("id",id).put("name",label)}
                Choice(field.label,value,options,"admin-${field.key}",loading=state.optionsLoading) {vm.edit(field.key,it)}
                if(field.source=="assignment-users" && !form.values["greenhouse_id"].isNullOrBlank()) {
                    if(options.isEmpty() && !state.optionsLoading && state.message==null)
                        Text("Belum ada pekerja lain. Cari akun pekerja dengan email lengkap.")
                    FinanceField("Email pekerja",unassignedEmail,{unassignedEmail=it},
                        "admin-unassigned-email",keyboard=KeyboardType.Email)
                    TextButton(onClick={vm.findUnassigned(unassignedEmail)},enabled=unassignedEmail.isNotBlank() && !state.optionsLoading,
                        modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("admin-find-unassigned")) {Text("Cari akun")}
                }
            }
            else -> FinanceField(field.label+(if(field.optional) " (opsional)" else ""),value,{vm.edit(field.key,it)},"admin-${field.key}",keyboard=when(field.type) {
                "integer" -> KeyboardType.Number
                "decimal" -> KeyboardType.Decimal
                "email" -> KeyboardType.Email
                else -> KeyboardType.Text
            },hint=when(field.type) {"date" -> "YYYY-MM-DD";"timestamp" -> "YYYY-MM-DDTHH:mm:ss+07:00";else -> null})
        }
    }
    if(form.resource==AdminResource.USER && (!state.pending || state.needsPassword)) OutlinedTextField(password,{password=it},label={Text(if(state.pending) "Password yang sama seperti sebelumnya" else if(form.version==null) "Password akun baru (minimal 12 karakter)" else "Password baru (opsional)")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth().testTag("admin-password"),enabled=!state.busy)
    if(confirmPassword) OutlinedTextField(passwordConfirmation,{passwordConfirmation=it},label={Text("Ulangi password")},
        visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
        modifier=Modifier.fillMaxWidth().testTag("admin-password-confirmation"),enabled=!state.busy,
        isError=passwordConfirmation.isNotEmpty() && passwordConfirmation!=password)
    if(form.resource==AdminResource.USER && state.pending) Text("Masukkan password yang sama untuk mengirim ulang.")
    }
    state.server?.let {row ->
        Notice("Data ini baru diubah di perangkat lain. Isian Anda tetap tersimpan.")
        form.fields.forEach {field -> Text("Terbaru · ${field.label}: ${if(row.isNull(field.key)) "Belum diisi" else row.optString(field.key)}")}
        TextButton(onClick=vm::useServer,enabled=!state.busy) {Text("Pakai data terbaru")}
    }
    if(state.rejected) TextButton(onClick=vm::reviseRejected,enabled=!state.busy) {Text("Perbaiki draf yang ditolak")}
    val passwordMatches=!confirmPassword || password.length>=12 && password==passwordConfirmation
    if(confirmPassword && !passwordMatches) Text("Isi password minimal 12 karakter dan ulangi dengan isian yang sama.")
    val submitLabel = when {
        state.pending -> "Kirim ulang"
        form.action == "close" -> "Tutup musim"
        form.action == "reopen" -> "Buka musim"
        else -> "Simpan ${form.resource.label.lowercase()}"
    }
    Button(onClick={if(confirmation!=null && !state.pending) confirm=true else vm.submit(password,onAccepted)},enabled=!state.busy && !state.rejected && passwordMatches,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("admin-submit")) {Text(submitLabel)}
    OutlinedButton(onClick=vm::back, enabled=!state.busy, shape=FarmShape.Control,
        modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch)) { Text("Simpan draf dan kembali") }
    if(!state.pending) TextButton(onClick={discard=true},enabled=!state.busy) {Text("Hapus draf ini")}
    if(confirm && confirmation!=null) AlertDialog(onDismissRequest={confirm=false}, containerColor = MaterialTheme.colorScheme.surface,
        title={Text(confirmation.first)},text={Text(confirmation.second)},
        confirmButton={Button(onClick={confirm=false;vm.submit(password,onAccepted)}, shape=FarmShape.Control) {Text(confirmation.third)}},
        dismissButton={TextButton(onClick={confirm=false}) {Text("Batal")}})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("Hapus draf?")},text={Text("Isian ini akan dihapus. Data yang sudah tersimpan tetap ada.")},confirmButton={TextButton(onClick={discard=false;vm.discard()}) {Text("Hapus draf")}},dismissButton={TextButton(onClick={discard=false}) {Text("Kembali")}})
}
