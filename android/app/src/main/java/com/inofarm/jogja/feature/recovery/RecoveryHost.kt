package com.inofarm.jogja.feature.recovery

import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

data class RecoveryActions(val backups:()->Unit,val review:(QueuedOperation)->Unit,val canReview:(QueuedOperation)->Boolean)
val LocalRecoveryActions=staticCompositionLocalOf<RecoveryActions?> {null}
data class RecoveryState(val active:Boolean=false,val busy:Boolean=false,val policy:JSONObject?=null,val rows:List<JSONObject> = emptyList(),val cursor:String?=null,val restores:List<JSONObject> = emptyList(),val restoreCursor:String?=null,val operation:QueuedOperation?=null,val review:JSONObject?=null,val message:String?=null)
class RecoveryViewModel(private val repository:RecoveryRepository):ViewModel() {
    private val mutable=MutableStateFlow(RecoveryState());val state=mutable.asStateFlow();private var job:Job?=null
    fun close() {job?.cancel();mutable.value=RecoveryState()}
    private fun work(block:suspend ()->Unit) {if(state.value.busy)return;val session=repository.vault.current ?: return;val ticket=repository.vault.generation;mutable.value=state.value.copy(busy=true,message=null);job=viewModelScope.launch {try {block()} catch(c:CancellationException){throw c} catch(e:Exception){if(repository.vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(e))} finally {if(repository.vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}}}
    fun open() {mutable.value=RecoveryState(active=true);work {val policy=repository.policy();mutable.value=state.value.copy(policy=policy);if(policy.getBoolean("available")) {load(false);loadRestores(false)}}}
    private suspend fun load(more:Boolean) {val response=repository.list(if(more) state.value.cursor else null);val array=response.getJSONArray("data");val rows=(0 until array.length()).map(array::getJSONObject);mutable.value=state.value.copy(rows=(if(more) state.value.rows+rows else rows).distinctBy {it.getString("id")},cursor=response.optString("next_cursor").takeIf {it.isNotBlank() && it!="null"})}
    fun refresh(more:Boolean=false)=work {load(more)}
    private suspend fun loadRestores(more:Boolean) {val response=repository.restores(if(more) state.value.restoreCursor else null);val rows=response.getJSONArray("data");mutable.value=state.value.copy(restores=(if(more) state.value.restores else emptyList())+(0 until rows.length()).map(rows::getJSONObject),restoreCursor=response.optString("next_cursor").takeIf {it.isNotBlank() && it!="null"})}
    fun moreRestores()=work {loadRestores(true)}
    fun create(id:String,reason:String)=work {val result=repository.create(id,reason);load(false);mutable.value=state.value.copy(message=if(result.getString("state")=="completed") "Cadangan dibuat. Keberhasilan restore belum dibuktikan." else "Tinjau status permintaan cadangan ini.")}
    fun validate(row:JSONObject)=work {val result=repository.validate(row);mutable.value=state.value.copy(message=result.getString("message"))}
    fun review(op:QueuedOperation) {mutable.value=RecoveryState(active=true,operation=op);work {mutable.value=state.value.copy(review=repository.inspect(op))}}
    fun acknowledge(changed:()->Unit)=work {val op=state.value.operation!!;repository.acknowledge(op,state.value.review!!);mutable.value=state.value.copy(review=null,message="Tanda terima historis diakui. Isian lokal yang lebih baru tetap disimpan; tidak ada pengiriman ulang.");changed()}
}

@Composable fun RecoveryHost(session:FinanceSession,repository:RecoveryRepository,onChanged:()->Unit,content: @Composable () -> Unit) {
    val vm:RecoveryViewModel=viewModel(key="finance-recovery",factory=remember(repository){object:ViewModelProvider.Factory {@Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=RecoveryViewModel(repository) as T}})
    val state by vm.state.collectAsStateWithLifecycle();var reason by rememberSaveable(session.scope.key){mutableStateOf("")};var requestId by rememberSaveable(session.scope.key){mutableStateOf(UUID.randomUUID().toString())};var confirm by remember {mutableStateOf(false)}
    DisposableEffect(session.scope.key){onDispose {vm.close()}}
    CompositionLocalProvider(LocalRecoveryActions provides RecoveryActions(vm::open,vm::review,repository::canReview)){content()}
    BackHandler(state.active){vm.close()}
    if(state.active) AlertDialog(onDismissRequest=vm::close,title={Text(if(state.operation==null) "Cadangan sistem" else "Rekonsiliasi setelah pemulihan")},text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(state.busy)item {Text("Memeriksa status di server…"); LinearProgressIndicator(Modifier.fillMaxWidth())}
        state.message?.let {item {Text(it,Modifier.testTag("recovery-message"))}}
        if(state.operation!=null) {
            item {Text("Isi dan ID operasi lama tetap tersimpan. Pemeriksaan ini tidak mengirim ulang transaksi.")}
            state.review?.let {review->item {Text(review.getString("message"));Text("Operasi ${state.operation!!.id}");Text("Identitas server: ${review.getString("sync_epoch")}")}
                if(review.getString("state")=="receipt_found")item {Button(onClick={vm.acknowledge(onChanged)},enabled=!state.busy,modifier=Modifier.testTag("recovery-ack")){Text("Akui tanda terima historis")}}
            }
        } else {
            item {FarmSection(title = "Cakupan cadangan") {
                Text("Cadangan mencakup seluruh sistem dan lampiran server. Isian yang belum tersinkron di perangkat tetap terpisah. Restore produksi dilakukan melalui operator server dengan prosedur tersendiri.")
            }}
            state.policy?.let {policy->
                if(!policy.getBoolean("available"))item {Text("Kewenangan operator pencadangan belum ditetapkan untuk akun ini.")}
                else {
                    item {OutlinedTextField(reason,{reason=it},label={Text("Alasan pencadangan (wajib)")},isError=reason.length>1000,supportingText={Text("${reason.length}/1000 karakter")},enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("recovery-reason"));Text("Jadwal, retensi dan target pemulihan produksi belum ditetapkan.")}
                    item {Button(onClick={confirm=true},enabled=!state.busy && reason.isNotBlank() && reason.length<=1000,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().testTag("recovery-create")){Text("Buat cadangan sistem")}}
                    item {TextButton(onClick={requestId=UUID.randomUUID().toString();reason=""},enabled=!state.busy){Text("Mulai permintaan baru")};TextButton(onClick={vm.refresh()},enabled=!state.busy){Text("Periksa status terbaru")}}
                    if(state.rows.isEmpty() && !state.busy)item {Text("Belum ada cadangan tercatat.")}
                    items(state.rows,key={it.getString("id")}){row->Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(row.getString("id"));Text("Dibuat (UTC): ${row.optString("created_at").ifBlank {"Belum tersedia"}}");Text(when(row.getString("state")){"completed"->"Cadangan dibuat; restore belum dibuktikan";"requested"->"Permintaan tercatat; periksa kembali";"interrupted_by_restore"->"Terhenti pada titik pemulihan";else->"Cadangan gagal; paket tidak boleh digunakan"});Text(row.getString("reason"));if(row.getString("state")=="completed") {Text("SHA256: ${row.getString("manifest_hash")}");OutlinedButton(onClick={vm.validate(row)},enabled=!state.busy){Text("Periksa integritas paket")}}}}
                    item {Text("Riwayat pemulihan terisolasi terverifikasi")}
                    if(state.restores.isEmpty() && !state.busy)item {Text("Belum ada bukti restore terverifikasi pada server ini.")}
                    items(state.restores,key={"restore-"+it.getString("id")}){row->Column {Text(if(row.optString("state")=="verified_isolated") "Drill terisolasi terverifikasi: ${row.getString("id")}" else "Pemulihan belum terverifikasi: ${row.getString("id")}");Text("Identitas baru: ${row.optString("new_epoch")}");Text("Checksum manifest: ${row.getString("manifest_hash")}");Text("Ini bukan bukti pemulihan produksi.")}}
                    if(state.restoreCursor!=null)item {TextButton(onClick=vm::moreRestores,enabled=!state.busy){Text("Muat riwayat pemulihan berikutnya")}}
                    if(state.cursor!=null)item {TextButton(onClick={vm.refresh(true)},enabled=!state.busy){Text("Muat berikutnya")}}
                }
            }
        }
    }},confirmButton={TextButton(onClick=vm::close){Text("Tutup")}})
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Cadangkan seluruh sistem?")},text={Text("Cadangan privat menyertakan seluruh database dan lampiran server. Permintaan dengan ID yang sama tidak membuat cadangan ganda. Ini belum membuktikan restore berhasil.")},confirmButton={Button(onClick={confirm=false;vm.create(requestId,reason)}){Text("Cadangkan sekarang")}},dismissButton={TextButton(onClick={confirm=false}){Text("Kembali")}})
}

@Composable fun RecoveryBackupButton(onOpen: () -> Unit = {}) {
    val actions = LocalRecoveryActions.current ?: return
    OutlinedButton(
        onClick = { onOpen(); actions.backups() },
        shape = FarmShape.Control,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("recovery-open")
    ) { Text("Cadangan dan pemeriksaan pemulihan") }
}
