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

@Composable fun AdminHost(session:FinanceSession,repository:AdminRepository,onAccepted:()->Unit,onClose:()->Unit) {
    if(session.role!=UserRole.MANAGER) {LaunchedEffect(Unit) {onClose()};return}
    val vm:AdminViewModel=viewModel(key="admin-${session.scope.key}-${repository.vault.generation}",factory=object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>)=AdminViewModel(repository,session) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {vm.flush()}
    val close={if(!state.busy && vm.flush()) onClose()}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=false)) {
        BackHandler {if(state.editing) vm.back() else close()}
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = FarmSpace.Page)) {
                FarmSheetHeading("Administrasi", close, !state.busy)
                HorizontalDivider()
                CompositionLocalProvider(LocalFarmDialogPicker provides true) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = FarmSpace.Medium),
                    verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                if (!state.editing) Text("Kelola akun, budidaya dan acuan. Draf tersimpan per akun.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.message?.let {Notice(it)}
                if(state.busy) { Text("Memeriksa data administrasi…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if(state.editing) AdminEditor(state,vm,onAccepted)
                else {
                    state.form?.let {Button(onClick=vm::resume,enabled=!state.busy,modifier=Modifier.testTag("admin-resume")) {Text(if(state.pending) "Tinjau pengajuan tertunda" else "Lanjutkan draf ${it.resource.label}")}}
                    FarmPanel(title = "Cakupan administrasi") {
                        Choice("Jenis data",state.resource.name,AdminResource.entries.map {org.json.JSONObject().put("id",it.name).put("name",it.label)},"admin-resource") {vm.choose(AdminResource.valueOf(it))}
                        Choice("Filter greenhouse",state.greenhouse,state.options["greenhouses"].orEmpty(),"admin-greenhouse",
                            loading=state.busy,onSelect=vm::greenhouse)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                            Button(onClick={vm.open()},enabled=!state.busy && state.form==null,shape=FarmShape.Control,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("admin-new")) {Text("Tambah ${state.resource.label}")}
                            TextButton(onClick={vm.reload()},enabled=!state.busy,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch)) {Text("Perbarui daftar")}
                        }
                    }
                    FarmSectionHeading("Daftar ${state.resource.label.lowercase()}")
                    if(state.rows.isEmpty() && !state.busy) Text("Belum ada data yang dapat ditampilkan. Pilih greenhouse atau periksa koneksi.")
                    state.rows.forEach {row ->
                        FarmPanel(title = row.optString("name").ifBlank {row.optString("activity_type").ifBlank {row.optString("user_id")}}) {
                            Text("Versi ${row.optLong("version")} · ${if(row.optString("status")=="closed") "Musim ditutup" else if(row.optBoolean("active",true)) "Aktif" else "Nonaktif"}",
                                style = MaterialTheme.typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
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
    FarmPageTitle("$action ${form.resource.label}")
    FarmDraftStatus(state.saved, Modifier.testTag("admin-saved"))
    if(state.pending) Notice("Pengajuan tersimpan. Isi dan ID tetap sama saat dicoba kembali; respons terputus belum berarti ditolak.")
    FarmSection("Isian ${form.resource.label.lowercase()}") {
    form.fields.forEach {field ->
        val value=form.values[field.key].orEmpty()
        if(state.pending || state.busy) FarmFact(field.label, display(field))
        else when {
            field.type=="boolean" -> FarmCheckbox(field.label, value == "true", { vm.edit(field.key, it.toString()) })
            field.source!=null || field.choices.isNotEmpty() -> {
                val options=if(field.source!=null) state.options[field.source].orEmpty().map {row -> org.json.JSONObject(row.toString()).put("name",row.optString("name").ifBlank {row.optString("id")})} else field.choices.map {(id,label) -> org.json.JSONObject().put("id",id).put("name",label)}
                Choice(field.label,value,options,"admin-${field.key}") {vm.edit(field.key,it)}
            }
            else -> FinanceField(field.label+(if(field.optional) " (opsional)" else ""),value,{vm.edit(field.key,it)},"admin-${field.key}",keyboard=if(field.type in setOf("integer","decimal")) KeyboardType.Decimal else KeyboardType.Text,hint=when(field.type) {"date" -> "YYYY-MM-DD";"timestamp" -> "YYYY-MM-DDTHH:mm:ss+07:00";else -> null})
        }
    }
    if(form.resource==AdminResource.USER && (!state.pending || state.needsPassword)) OutlinedTextField(password,{password=it},label={Text(if(state.pending) "Password pengajuan pertama" else if(form.version==null) "Password akun baru (minimal 12 karakter)" else "Password baru (opsional)")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth().testTag("admin-password"),enabled=!state.busy)
    if(form.resource==AdminResource.USER) Text("Password hanya berada di memori. Jika respons terputus atau aplikasi ditutup, masukkan password pengajuan pertama untuk retry yang identik.")
    }
    state.server?.let {row ->
        Notice("Versi server ${row.optLong("version")} berbeda. Draf lokal tetap disimpan.")
        form.fields.forEach {field -> Text("Server · ${field.label}: ${if(row.isNull(field.key)) "Belum diisi" else row.optString(field.key)}")}
        TextButton(onClick=vm::useServer,enabled=!state.busy) {Text("Gunakan versi server untuk draf")}
    }
    if(state.rejected) TextButton(onClick=vm::reviseRejected,enabled=!state.busy) {Text("Perbaiki draf yang ditolak")}
    Button(onClick={confirm=true},enabled=!state.busy && !state.rejected,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("admin-submit")) {Text(if(state.pending) "Coba pengajuan yang sama" else "Tinjau dan ajukan")}
    OutlinedButton(onClick=vm::back, enabled=!state.busy, shape=FarmShape.Control,
        modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch)) { Text("Simpan draf dan kembali") }
    if(!state.pending) TextButton(onClick={discard=true},enabled=!state.busy) {Text("Hapus draf ini")}
    if(confirm) AlertDialog(onDismissRequest={confirm=false}, containerColor = MaterialTheme.colorScheme.surface,
        title={Text("Ajukan perubahan?")},text={
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                FarmSectionHeading("$action ${form.resource.label}")
                form.fields.forEach { field -> FarmFact(field.label, display(field)) }
                Text(when(form.resource) {
                    AdminResource.USER, AdminResource.ASSIGNMENT -> "Perubahan akses dapat mencabut sesi. Server memeriksa dan mencatat pengajuan ini."
                    else -> "Server memeriksa dan mencatat pengajuan ini. Perubahan berlaku setelah diterima."
                })
            }
        },confirmButton={Button(onClick={confirm=false;vm.submit(password,onAccepted)}, shape=FarmShape.Control) {Text("Ajukan")}},
        dismissButton={TextButton(onClick={confirm=false}) {Text("Kembali")}})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("Hapus draf lokal?")},text={Text("Isian administrasi ini akan dihapus. Data yang sudah diterima server tetap tersimpan.")},confirmButton={TextButton(onClick={discard=false;vm.discard()}) {Text("Hapus draf")}},dismissButton={TextButton(onClick={discard=false}) {Text("Kembali")}})
}
