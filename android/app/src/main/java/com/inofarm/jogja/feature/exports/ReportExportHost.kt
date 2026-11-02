package com.inofarm.jogja.feature.exports

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

val LocalReportExport=staticCompositionLocalOf<((LocalRecord,String)->Unit)?> {null}
data class ReportExportState(val busy:Boolean=false,val message:String?=null)
class ReportExportViewModel(private val repository:ReportExportRepository):ViewModel() {
    private val mutable=MutableStateFlow(ReportExportState());val state=mutable.asStateFlow();private var job:Job?=null
    fun clear() {mutable.value=state.value.copy(message=null)}
    fun cancel() {job?.cancel();job=null;mutable.value=ReportExportState()}
    fun save(selection:ReportExportSelection,uri:Uri) {
        if(state.value.busy) return
        val session=repository.vault.current ?: return;val ticket=repository.vault.generation
        mutable.value=ReportExportState(busy=true)
        job=viewModelScope.launch {try {
            val name=repository.save(session,ticket,selection,uri)
            if(repository.vault.matches(ticket,session.scope)) mutable.value=ReportExportState(message="Laporan tersimpan: $name")
        } catch(c:CancellationException) {throw c} catch(error:Exception) {if(repository.vault.matches(ticket,session.scope)) mutable.value=ReportExportState(message="Berkas belum terkonfirmasi tersimpan lengkap. ${userMessage(error)}")}
        finally {if(repository.vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}}
    }
}

@Composable fun ReportExportHost(session:FinanceSession,repository:ReportExportRepository,content: @Composable () -> Unit) {
    val vm:ReportExportViewModel=viewModel(key="report-export",factory=remember(repository) {object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=ReportExportViewModel(repository) as T
    }})
    val state by vm.state.collectAsStateWithLifecycle();var pending by rememberSaveable(session.scope.key) {mutableStateOf<String?>(null)};var pickerError by remember {mutableStateOf<String?>(null)}
    DisposableEffect(session.scope.key) {onDispose {vm.cancel()}}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        val saved=pending;pending=null
        if(result.resultCode==android.app.Activity.RESULT_OK && result.data?.data!=null && saved!=null) {
            runCatching {ReportExportSelection.parse(JSONObject(saved))}.onSuccess {vm.save(it,result.data!!.data!!)}.onFailure {pickerError="Pilihan laporan belum dapat dipulihkan. Buka kembali laporan."}
        }
    }
    CompositionLocalProvider(LocalReportExport provides {row,format->
        if(!state.busy && pending==null) runCatching {
            val selection=ReportExportSelection.from(session.scope.key,row,format);pending=selection.json().toString()
            launcher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(selection.mime).putExtra(Intent.EXTRA_TITLE,selection.filename))
        }.onFailure {pending=null;pickerError=userMessage(it)}
    }) {content()}
    if(state.busy) AlertDialog(onDismissRequest={},title={Text("Menyimpan laporan")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {CircularProgressIndicator();Text("Memeriksa akses dan mengunduh snapshot yang dipilih…")}},confirmButton={TextButton(onClick=vm::cancel) {Text("Batalkan")}})
    val message=pickerError ?: state.message
    if(!state.busy && message!=null) AlertDialog(onDismissRequest={pickerError=null;vm.clear()},title={Text("Unduhan laporan")},text={Text(message)},confirmButton={TextButton(onClick={pickerError=null;vm.clear()}) {Text("Tutup")}})
}

@Composable fun ReportExportActions(row:LocalRecord) {
    val action=LocalReportExport.current ?: return
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={action(row,"pdf")},modifier=Modifier.fillMaxWidth().testTag("report-download-pdf")) {Text("Unduh PDF dari laporan ini")}
        OutlinedButton(onClick={action(row,"csv")},modifier=Modifier.fillMaxWidth().testTag("report-download-csv")) {Text("Unduh CSV dari laporan ini")}
    }
}
