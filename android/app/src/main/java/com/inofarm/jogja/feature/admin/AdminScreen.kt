package com.inofarm.jogja.feature.admin

import com.inofarm.jogja.core.design.FarmPanel
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
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                com.inofarm.jogja.core.design.FarmPageTitle("Administrasi")
                Text("Isian disimpan per akun. Pengajuan memerlukan koneksi dan persetujuan Anda; hasil server menentukan hak akses serta versi.")
                TextButton(onClick={if(state.editing) vm.back() else close()},enabled=!state.busy) {Text(if(state.editing) "Simpan draf dan kembali" else "Tutup administrasi")}
                state.message?.let {Notice(it)}
                if(state.busy) { Text("Memeriksa data administrasi…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if(state.editing) AdminEditor(state,vm,onAccepted)
                else {
                    state.form?.let {Button(onClick=vm::resume,enabled=!state.busy,modifier=Modifier.testTag("admin-resume")) {Text(if(state.pending) "Tinjau pengajuan tertunda" else "Lanjutkan draf ${it.resource.label}")}}
                    FarmPanel(title = "Cakupan administrasi") {
                        Choice("Jenis data",state.resource.name,AdminResource.entries.map {org.json.JSONObject().put("id",it.name).put("name",it.label)},"admin-resource") {vm.choose(AdminResource.valueOf(it))}
                        Choice("Filter greenhouse",state.greenhouse,state.options["greenhouses"].orEmpty(),"admin-greenhouse",vm::greenhouse)
                        Button(onClick={vm.open()},enabled=!state.busy && state.form==null,modifier=Modifier.testTag("admin-new")) {Text("Tambah ${state.resource.label}")}
                        TextButton(onClick={vm.reload()},enabled=!state.busy) {Text("Perbarui daftar")}
                    }
                    if(state.rows.isEmpty() && !state.busy) Text("Belum ada data yang dapat ditampilkan. Pilih greenhouse atau periksa koneksi.")
                    state.rows.forEach {row ->
                        FarmPanel(title = row.optString("name").ifBlank {row.optString("activity_type").ifBlank {row.optString("user_id")}}) {
                            Text("Versi ${row.optLong("version")} · ${if(row.optString("status")=="closed") "Musim ditutup" else if(row.optBoolean("active",true)) "Aktif" else "Nonaktif"}")
                            TextButton(onClick={vm.open(row)},enabled=!state.busy && state.form==null) {Text("Ubah")}
                            if(state.resource==AdminResource.SEASON) TextButton(onClick={vm.open(row,if(row.optString("status")=="closed") "reopen" else "close")},enabled=!state.busy && state.form==null) {Text(if(row.optString("status")=="closed") "Buka kembali musim" else "Tutup musim")}
                        }
                    }
                    if(state.cursor!=null) TextButton(onClick={vm.reload(true)},enabled=!state.busy) {Text("Muat berikutnya")}
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
    Text("${if(form.version==null) "Tambah" else if(form.action=="close") "Tutup" else if(form.action=="reopen") "Buka kembali" else "Ubah"} ${form.resource.label}",style=MaterialTheme.typography.titleLarge)
    Text(if(state.saved) "Draf tersimpan di perangkat." else "Menyimpan draf…",modifier=Modifier.testTag("admin-saved"))
    if(state.pending) Notice("Pengajuan tersimpan. Isi dan ID tetap sama saat dicoba kembali; respons terputus belum berarti ditolak.")
    form.fields.forEach {field ->
        val value=form.values[field.key].orEmpty()
        if(state.pending || state.busy) Text("${field.label}: ${field.choices[value] ?: state.options[field.source]?.find {it.optString("id")==value}?.optString("name") ?: value.ifBlank {"Belum diisi"}}")
        else when {
            field.type=="boolean" -> Row {Checkbox(value=="true",{vm.edit(field.key,it.toString())});Text(field.label,Modifier.padding(top=12.dp))}
            field.source!=null || field.choices.isNotEmpty() -> {
                val options=if(field.source!=null) state.options[field.source].orEmpty().map {row -> org.json.JSONObject(row.toString()).put("name",row.optString("name").ifBlank {row.optString("id")})} else field.choices.map {(id,label) -> org.json.JSONObject().put("id",id).put("name",label)}
                Choice(field.label,value,options,"admin-${field.key}") {vm.edit(field.key,it)}
            }
            else -> FinanceField(field.label+(if(field.optional) " (opsional)" else ""),value,{vm.edit(field.key,it)},"admin-${field.key}",keyboard=if(field.type in setOf("integer","decimal")) KeyboardType.Decimal else KeyboardType.Text,hint=when(field.type) {"date" -> "YYYY-MM-DD";"timestamp" -> "YYYY-MM-DDTHH:mm:ss+07:00";else -> null})
        }
    }
    if(form.resource==AdminResource.USER && (!state.pending || state.needsPassword)) OutlinedTextField(password,{password=it},label={Text(if(state.pending) "Password pengajuan pertama" else if(form.version==null) "Password akun baru (minimal 12 karakter)" else "Password baru (opsional)")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth().testTag("admin-password"),enabled=!state.busy)
    if(form.resource==AdminResource.USER) Text("Password hanya berada di memori. Jika respons terputus atau aplikasi ditutup, masukkan password pengajuan pertama untuk retry yang identik.")
    state.server?.let {row ->
        Notice("Versi server ${row.optLong("version")} berbeda. Draf lokal tetap disimpan.")
        form.fields.forEach {field -> Text("Server · ${field.label}: ${if(row.isNull(field.key)) "Belum diisi" else row.optString(field.key)}")}
        TextButton(onClick=vm::useServer,enabled=!state.busy) {Text("Gunakan versi server untuk draf")}
    }
    if(state.rejected) TextButton(onClick=vm::reviseRejected,enabled=!state.busy) {Text("Perbaiki draf yang ditolak")}
    Button(onClick={confirm=true},enabled=!state.busy && !state.rejected,modifier=Modifier.fillMaxWidth().testTag("admin-submit")) {Text(if(state.pending) "Coba pengajuan yang sama" else "Tinjau dan ajukan")}
    if(!state.pending) TextButton(onClick={discard=true},enabled=!state.busy) {Text("Hapus draf ini")}
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Ajukan perubahan?")},text={Text("Periksa isian di atas. Perubahan akses dapat mencabut sesi; perubahan musim dan acuan memengaruhi pencatatan berikutnya. Pengajuan akan diperiksa server dan diaudit.")},confirmButton={TextButton(onClick={confirm=false;vm.submit(password,onAccepted)}) {Text("Ajukan")}},dismissButton={TextButton(onClick={confirm=false}) {Text("Kembali")}})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("Hapus draf lokal?")},text={Text("Isian administrasi ini akan dihapus. Data yang sudah diterima server tetap tersimpan.")},confirmButton={TextButton(onClick={discard=false;vm.discard()}) {Text("Hapus draf")}},dismissButton={TextButton(onClick={discard=false}) {Text("Kembali")}})
}
