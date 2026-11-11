package com.inofarm.jogja.feature.recovery

import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmSectionHeading
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.FarmShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    fun create(id:String,reason:String)=work {val result=repository.create(id,reason);load(false);mutable.value=state.value.copy(message=if(result.getString("state")=="completed") "Cadangan dibuat." else "Permintaan cadangan tercatat. Cek lagi sebentar lagi.")}
    fun validate(row:JSONObject)=work {val result=repository.validate(row);mutable.value=state.value.copy(message=result.getString("message"))}
    fun review(op:QueuedOperation) {mutable.value=RecoveryState(active=true,operation=op);work {mutable.value=state.value.copy(review=repository.inspect(op))}}
    fun acknowledge(changed:()->Unit)=work {val op=state.value.operation!!;repository.acknowledge(op,state.value.review!!);mutable.value=state.value.copy(review=null,message="Selesai dicek. Catatan tidak dikirim ulang.");changed()}
}

@Composable fun RecoveryHost(session:FinanceSession,repository:RecoveryRepository,onChanged:()->Unit,content: @Composable () -> Unit) {
    val vm:RecoveryViewModel=viewModel(key="finance-recovery",factory=remember(repository){object:ViewModelProvider.Factory {@Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=RecoveryViewModel(repository) as T}})
    val state by vm.state.collectAsStateWithLifecycle();var reason by rememberSaveable(session.scope.key){mutableStateOf("")};var requestId by rememberSaveable(session.scope.key){mutableStateOf(UUID.randomUUID().toString())};var confirm by remember {mutableStateOf(false)}
    DisposableEffect(session.scope.key){onDispose {vm.close()}}
    CompositionLocalProvider(LocalRecoveryActions provides RecoveryActions(vm::open,vm::review,repository::canReview)){content()}
    BackHandler(state.active){vm.close()}
    if(state.active) AlertDialog(onDismissRequest=vm::close,containerColor=MaterialTheme.colorScheme.surface,
        title={Text(if(state.operation==null) "Cadangan sistem" else "Cek setelah pemulihan")},text={LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(FarmSpace.Medium)) {
        if(state.busy)item {Text("Memeriksa…"); LinearProgressIndicator(Modifier.fillMaxWidth())}
        state.message?.let {item {Text(it,Modifier.testTag("recovery-message"))}}
        if(state.operation!=null) {
            item {Text("Pengecekan ini tidak mengirim ulang catatan.")}
            state.review?.let {review->item {FarmSection("Hasil cek") {
                Text(review.getString("message"))
                FarmFact("Kode catatan", state.operation!!.id)
                FarmFact("Kode pemulihan data", review.getString("sync_epoch"))
            }}
                if(review.getString("state")=="receipt_found")item {Button(onClick={vm.acknowledge(onChanged)},enabled=!state.busy,modifier=Modifier.testTag("recovery-ack")){Text("Tandai sudah dicek")}}
            }
        } else {
            item {FarmSection(title = "Isi cadangan") {
                Text("Cadangan berisi seluruh data dan lampiran. Catatan yang belum terkirim tidak ikut. Pemulihan dilakukan oleh operator sistem.")
            }}
            state.policy?.let {policy->
                if(!policy.getBoolean("available"))item {Text("Akun ini belum bisa membuat cadangan. Hubungi operator sistem.")}
                else {
                    item {OutlinedTextField(reason,{reason=it},label={Text("Alasan cadangan")},isError=reason.length>1000,supportingText={Text("${reason.length}/1000 karakter")},enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("recovery-reason"));Text("Jadwal cadangan otomatis belum diatur.")}
                    item {Button(onClick={confirm=true},enabled=!state.busy && reason.isNotBlank() && reason.length<=1000,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().testTag("recovery-create")){Text("Buat cadangan")}}
                    item {TextButton(onClick={requestId=UUID.randomUUID().toString();reason=""},enabled=!state.busy){Text("Mulai ulang")};TextButton(onClick={vm.refresh()},enabled=!state.busy){Text("Perbarui")}}
                    if(state.rows.isEmpty() && !state.busy)item {Text("Belum ada cadangan.")}
                    items(state.rows,key={it.getString("id")}) { row ->
                        val completed = row.getString("state") == "completed"
                        var details by rememberSaveable(row.getString("id")) { mutableStateOf(false) }
                        FarmPanel(title = when(row.getString("state")) {
                            "completed" -> "Cadangan dibuat"
                            "requested" -> "Permintaan tercatat. Cek lagi nanti."
                            "interrupted_by_restore" -> "Terhenti karena data dipulihkan"
                            else -> "Cadangan gagal. Jangan dipakai."
                        }) {
                            FarmFact("Alasan", row.getString("reason"))
                            FarmFact("Dibuat (UTC)", row.optString("created_at").ifBlank { "Belum tersedia" })
                            TextButton(onClick={details=!details}, modifier=Modifier.heightIn(min=FarmSpace.Touch)) {
                                Text(if (details) "Tutup rincian" else "Rincian")
                            }
                            if (details) {
                                FarmFact("ID cadangan", row.getString("id"))
                                if (completed) FarmFact("Kode pemeriksa (SHA256)", row.getString("manifest_hash"))
                            }
                            if (completed) OutlinedButton(onClick={vm.validate(row)},enabled=!state.busy,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch)) { Text("Cek cadangan") }
                        }
                    }
                    item { FarmSectionHeading("Uji pemulihan") }
                    if(state.restores.isEmpty() && !state.busy)item {Text("Belum ada uji pemulihan.")}
                    items(state.restores,key={"restore-"+it.getString("id")}){row->Column {Text(if(row.optString("state")=="verified_isolated") "Uji pemulihan berhasil: ${row.getString("id")}" else "Uji pemulihan belum berhasil: ${row.getString("id")}");Text("Kode data baru: ${row.optString("new_epoch")}");Text("Kode pemeriksa: ${row.getString("manifest_hash")}");Text("Ini hanya uji coba, bukan pemulihan data asli.")}}
                    if(state.restoreCursor!=null)item {TextButton(onClick=vm::moreRestores,enabled=!state.busy){Text("Tampilkan lagi")}}
                    if(state.cursor!=null)item {TextButton(onClick={vm.refresh(true)},enabled=!state.busy){Text("Tampilkan lagi")}}
                }
            }
        }
    }},confirmButton={TextButton(onClick=vm::close){Text("Tutup")}})
    if(confirm)AlertDialog(onDismissRequest={confirm=false},containerColor=MaterialTheme.colorScheme.surface,
        title={Text("Buat cadangan?")},text={Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(FarmSpace.Medium)) {
            Text("Cadangan berisi seluruh data dan lampiran.")
            FarmFact("Alasan cadangan", reason)
            Text("Pemulihan data dicek terpisah.")
        }},confirmButton={Button(onClick={confirm=false;vm.create(requestId,reason)}){Text("Buat cadangan")}},dismissButton={TextButton(onClick={confirm=false}){Text("Kembali")}})
}

@Composable fun RecoveryBackupButton(onOpen: () -> Unit = {}) {
    val actions = LocalRecoveryActions.current ?: return
    com.inofarm.jogja.core.design.FarmActionRow(
        label = "Cadangan data",
        detail = "Buat dan cek cadangan",
        icon = com.inofarm.jogja.core.design.FarmIcon.Backup,
        onClick = { onOpen(); actions.backups() },
        modifier = Modifier.testTag("recovery-open")
    )
}
