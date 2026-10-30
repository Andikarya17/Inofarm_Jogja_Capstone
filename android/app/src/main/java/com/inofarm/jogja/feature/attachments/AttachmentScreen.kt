package com.inofarm.jogja.feature.attachments

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.history.RecordFacts
import org.json.JSONObject
import java.time.Instant
import com.inofarm.jogja.core.database.LocalRecord

val LocalAttachmentAction=staticCompositionLocalOf<((LocalRecord)->Unit)?> {null}

@Composable fun AttachmentScreen(session:FinanceSession,context:CultivationUiState,state:AttachmentUiState,vm:AttachmentViewModel,ticket:Long) {
    val parent=state.parent ?: return
    val androidContext=LocalContext.current
    var pickerScope by rememberSaveable {mutableStateOf("")};var pickerOwner by rememberSaveable {mutableStateOf("")};var pickerTicket by rememberSaveable {mutableLongStateOf(-1)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> uri?.let {vm.importResult(it,pickerScope,pickerOwner,pickerTicket)}}
    LaunchedEffect(state.preview) {state.preview?.let {intent ->
        val error=runCatching {androidContext.startActivity(intent)}.exceptionOrNull()
        vm.previewHandled(if(error!=null) "Aplikasi pembuka berkas belum tersedia atau pratinjau tidak dapat dibuka. Salinan terenkripsi tetap disimpan." else null)
    }}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp).testTag("attachment-screen"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Lampiran catatan",style=MaterialTheme.typography.headlineMedium)
        Text("Catatan dan pengiriman berkas memiliki status terpisah. Salinan lokal disimpan terenkripsi.")
        if(state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let {Notice(it,true)}
        val policy=state.policy
        if(policy==null) Notice("Batas lampiran belum tersedia. Berkas dapat disalin sebagai draf; pengajuan menunggu konfigurasi server.")
        else {
            if(!policy.optBoolean("configured")) Notice("Konfigurasi lampiran produksi belum ditetapkan.")
            else Text("Batas ${policy.optLong("max_bytes")} byte per berkas · ${policy.optLong("max_files")} berkas aktif. Jenis: ${policy.optJSONArray("allowed_mime_types")?.let {a -> (0 until a.length()).joinToString {typeLabel(a.getString(it))}}}")
            if(policy.has("fetched_at")) Text("${if(state.policyCached) "Acuan tersimpan" else "Terakhir diperiksa"}: ${timeLabel(Instant.ofEpochMilli(policy.getLong("fetched_at")).toString())}",style=MaterialTheme.typography.bodySmall)
            policy.optJSONArray("reasons")?.let {reasons -> repeat(reasons.length()) {Notice(reasons.getString(it),true)}}
        }
        OutlinedButton(onClick=vm::refreshPolicy,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Periksa batas dan akses terbaru")}
        if(state.selected==null) {
            val canChoose=session.role==UserRole.MANAGER || parent.recorderId()==session.scope.accountId
            if(canChoose) Button(onClick={pickerScope=session.scope.key;pickerOwner=parent.id;pickerTicket=ticket;picker.launch(AttachmentContract.supportedMimes.toTypedArray())},enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-choose")) {Text("Pilih berkas sebagai draf")}
            if(state.rows.isEmpty() && !state.loading) Notice("Belum ada lampiran atau draf berkas pada catatan ini.")
            state.rows.forEach {row -> OutlinedCard(onClick={vm.select(row.id)},modifier=Modifier.fillMaxWidth().testTag("attachment-${row.id}")) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(row.body.optString("original_name","Berkas belum selesai disalin"));Text(row.syncState.label());Text(copyLabel(row.body));if(row.body.optString("record_state")=="cancelled") Text("Lampiran dibatalkan; riwayat tetap tersedia.")}
            }}
            if(state.hasMore) OutlinedButton(onClick=vm::more,enabled=!state.loading) {Text("Muat 50 berikutnya")}
            OutlinedButton(onClick=vm::close,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-close")) {Text("Kembali ke catatan")}
        } else {
            val row=state.selected
            Text(row.body.optString("original_name"),style=MaterialTheme.typography.titleLarge);Text(row.syncState.label());Text(copyLabel(row.body))
            if(row.body.has("bytes")) Text("${row.body.getLong("bytes")} byte · ${typeLabel(row.body.optString("mime"))}")
            Text("Versi catatan induk saat ditinjau: ${row.body.optLong("owner_version")}")
            row.error?.let {ErrorReasons(it)};state.pending.forEach {op -> op.error?.let {ErrorReasons(it)}}
            val ready=row.body.optString("_copy_state")=="READY"
            when {
                state.parentReview!=null -> {
                    Text("Tinjau catatan induk terbaru",style=MaterialTheme.typography.titleLarge)
                    val snapshot=state.parentReview
                    RecordFacts(parent.copy(body=snapshot,serverBody=snapshot,serverVersion=snapshot.getLong("version")),session,context,RecordKind.entries.single {it.wire==parent.kind})
                    Notice("Pengajuan baru memakai versi induk yang ditinjau dan salinan berkas yang sama. Pengajuan lama disimpan sebagai riwayat.")
                    Button(onClick=vm::confirmParent,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-confirm-parent")) {Text("Setujui versi induk dan antrekan")}
                    TextButton(onClick=vm::backToDetails) {Text("Kembali tanpa mengajukan")}
                }
                state.reviewing -> {
                    Notice("Berkas ini terikat pada catatan induk. Server memeriksa ulang versi, akses, ukuran, jenis, dan jumlah saat menerima unggahan.")
                    RecordFacts(parent,session,context,RecordKind.entries.single {it.wire==parent.kind})
                    Button(onClick=vm::queue,enabled=!state.busy && policy?.optBoolean("configured")==true && policy.optBoolean("upload_allowed"),modifier=Modifier.fillMaxWidth().testTag("attachment-queue")) {Text("Simpan dan antrekan lampiran")}
                    TextButton(onClick=vm::backToDetails) {Text("Kembali ke detail berkas")}
                }
                else -> {
                    if(ready && row.serverVersion==null && state.pending.isEmpty()) Button(onClick=vm::review,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-review")) {Text("Periksa pengajuan berkas")}
                    if(row.body.optString("_copy_state") in setOf("COPYING","FAILED")) OutlinedButton(onClick=vm::resumeCopy,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Lanjutkan salinan dari sumber")}
                    if(ready || row.serverVersion!=null) OutlinedButton(onClick=vm::preview,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-preview")) {Text("Buka berkas")}
                    if(state.pending.any {it.error?.optString("code")=="parent_version_conflict"}) OutlinedButton(onClick=vm::inspectParent,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Muat dan tinjau induk terbaru")}
                    if(state.pending.any {it.state=="REJECTED" && it.error?.optString("code") in setOf("attachment_configuration_missing","attachment_limit_reached","season_closed","local_attachment_unavailable")}) OutlinedButton(onClick=vm::retry,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Tinjau dan coba pengajuan yang sama")}
                    if(row.serverVersion!=null) OutlinedButton(onClick={vm.audit()},enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-audit")) {Text("Muat audit lampiran")}
                    if(session.role==UserRole.MANAGER && row.syncState==SyncState.ACCEPTED && row.body.optString("record_state")=="active" && !state.cancelEditing) OutlinedButton(onClick=vm::beginCancel,modifier=Modifier.fillMaxWidth().testTag("attachment-cancel")) {Text("Batalkan lampiran dengan alasan")}
                }
            }
            if(state.cancelEditing) {
                FinanceField("Alasan pembatalan lampiran",state.reason,vm::editReason,"attachment-reason",singleLine=false)
                Text(if(state.reasonSaved) "Draf alasan tersimpan di perangkat." else "Menyimpan draf alasan…")
                Button(onClick=vm::cancel,enabled=!state.busy && state.reason.isNotBlank() && state.reason.length<=1000,modifier=Modifier.fillMaxWidth().testTag("attachment-confirm-cancel")) {Text("Antrekan pembatalan")}
            }
            state.audit?.let {audit ->
                if(state.auditCached) Notice("Audit tersimpan; pemeriksaan terbaru belum berhasil.")
                audit.getJSONArray("data").let {rows -> repeat(rows.length()) {val entry=rows.getJSONObject(it);Text("${entry.optString("actor_name")} · ${if(entry.optString("action")=="cancel") "Pembatalan" else "Pencatatan"} · versi ${entry.optLong("version")}");if(!entry.isNull("reason")) Text("Alasan: ${entry.optString("reason")}")}}
                if(!audit.isNull("next_cursor")) TextButton(onClick={vm.audit(true)},enabled=!state.busy) {Text("Muat audit berikutnya")}
            }
            OutlinedButton(onClick=vm::closeSelected,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-back-list")) {Text("Simpan draf dan kembali ke lampiran")}
        }
    }
}
private fun copyLabel(body:JSONObject)=when(body.optString("_copy_state")) {"COPYING" -> "Salinan berkas belum selesai.";"FAILED" -> "Salinan gagal; sumber mungkin perlu dipilih ulang.";"READY" -> "Salinan terenkripsi siap di perangkat.";else -> "Metadata diterima server; salinan lokal diperiksa saat dibuka."}
private fun typeLabel(mime:String)=when(mime) {"image/png" -> "PNG";"image/jpeg" -> "JPEG";"application/pdf" -> "PDF";else -> "Jenis belum tersedia"}
