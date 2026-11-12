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
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmRecordCard
import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.FarmControlIcon
import com.inofarm.jogja.core.design.FarmIcon
import androidx.compose.ui.Alignment
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.history.RecordFacts
import org.json.JSONObject
import java.time.Instant

val LocalAttachmentAction=staticCompositionLocalOf<((LocalRecord)->Unit)?> {null}

@Composable fun AttachmentScreen(session:FinanceSession,context:CultivationUiState,state:AttachmentUiState,vm:AttachmentViewModel,ticket:Long) {
    val parent=state.parent ?: return
    val androidContext=LocalContext.current
    var pickerScope by rememberSaveable {mutableStateOf("")};var pickerOwner by rememberSaveable {mutableStateOf("")};var pickerTicket by rememberSaveable {mutableLongStateOf(-1)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> uri?.let {vm.importResult(it,pickerScope,pickerOwner,pickerTicket)}}
    LaunchedEffect(state.preview) {state.preview?.let {intent ->
        val error=runCatching {androidContext.startActivity(intent)}.exceptionOrNull()
        vm.previewHandled(if(error!=null) "Berkas tidak bisa dibuka. Pasang aplikasi pembuka PDF atau gambar, lalu coba lagi." else null)
    }}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp).testTag("attachment-screen"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        com.inofarm.jogja.core.design.FarmPageTitle(when {
            state.parentReview!=null -> "Cek catatan terbaru"
            state.reviewing -> "Cek lampiran"
            else -> "Lampiran catatan"
        })
        if(state.busy || state.loading) {
            Text(if (state.busy) "Memproses berkas…" else "Memuat lampiran…")
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.message?.let {Notice(it,true)}
        val policy=state.policy
        FarmSection(title = "Batas lampiran") {
        if(policy==null) Notice("Batas lampiran belum bisa dicek. Berkas tetap bisa disimpan sebagai draf.")
        else {
            if(!policy.optBoolean("configured")) Notice("Lampiran belum diaktifkan. Hubungi operator sistem.")
            else Text("Maksimal ${policy.optLong("max_bytes")} byte per berkas dan ${policy.optLong("max_files")} berkas. Jenis: ${policy.optJSONArray("allowed_mime_types")?.let {a -> (0 until a.length()).joinToString {typeLabel(a.getString(it))}}}")
            if(policy.has("fetched_at")) Text("${if(state.policyCached) "Batas tersimpan" else "Diperbarui"}: ${timeLabel(Instant.ofEpochMilli(policy.getLong("fetched_at")).toString())}",style=MaterialTheme.typography.bodySmall)
            policy.optJSONArray("reasons")?.let {reasons -> repeat(reasons.length()) {Notice(reasons.getString(it),true)}}
        }
        OutlinedButton(onClick=vm::refreshPolicy,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Perbarui")}
        }
        if(state.selected==null) {
            FarmFact("Catatan terkait", RecordKind.entries.single { it.wire == parent.kind }.label.replaceFirstChar { it.uppercaseChar() })
            val canChoose=session.role==UserRole.MANAGER || parent.recorderId()==session.scope.accountId
            if(canChoose) Button(onClick={pickerScope=session.scope.key;pickerOwner=parent.id;pickerTicket=ticket;picker.launch(AttachmentContract.supportedMimes.toTypedArray())},enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("attachment-choose")) {Text("Pilih berkas")}
            if(state.rows.isEmpty() && !state.loading) Notice("Belum ada lampiran.")
            state.rows.forEach {row -> FarmRecordCard(
                Modifier.testTag("attachment-${row.id}"), onOpen={vm.select(row.id)}) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small),
                        verticalAlignment = Alignment.CenterVertically) {
                        FarmControlIcon(FarmIcon.Report, null)
                        Text(row.body.optString("original_name","Berkas belum selesai disimpan"),
                            Modifier.weight(1f), style=MaterialTheme.typography.titleMedium)
                        FarmControlIcon(FarmIcon.ChevronRight, null)
                    }
                    row.body.optLong("bytes", -1).takeIf { it >= 0 }?.let {
                        Text("$it byte · ${typeLabel(row.body.optString("mime"))}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(copyLabel(row.body),style=MaterialTheme.typography.bodyMedium)
                    RecordStatus(row.syncState)
                    if(row.body.optString("record_state")=="cancelled") Text("Lampiran dibatalkan. Riwayatnya tetap ada.")
            }}
            if(state.hasMore) OutlinedButton(onClick=vm::more,enabled=!state.loading) {Text("Tampilkan 50 lagi")}
            OutlinedButton(onClick=vm::close,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-close")) {Text("Kembali ke catatan")}
        } else {
            val row=state.selected
            FarmPanel(title = row.body.optString("original_name").ifBlank { "Rincian berkas" }) {
                RecordStatus(row.syncState)
                Text(copyLabel(row.body))
                if(row.body.has("bytes")) Text("${row.body.getLong("bytes")} byte · ${typeLabel(row.body.optString("mime"))}",
                    style = MaterialTheme.typography.bodySmall)
                FarmFact("Versi catatan saat dicek",
                    row.body.optLong("owner_version").takeIf { it > 0 }?.toString() ?: "Belum tersedia")
            }
            row.error?.let {ErrorReasons(it)};state.pending.forEach {op -> op.error?.let {ErrorReasons(it)}}
            val ready=row.body.optString("_copy_state")=="READY"
            when {
                state.parentReview!=null -> {
                    val snapshot=state.parentReview
                    FarmPanel(title="Catatan terbaru") {
                        RecordFacts(parent.copy(body=snapshot,serverBody=snapshot,serverVersion=snapshot.getLong("version")),session,context,RecordKind.entries.single {it.wire==parent.kind})
                    }
                    Notice("Lampiran dikirim ulang untuk catatan terbaru dengan berkas yang sama.")
                    Button(onClick=vm::confirmParent,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-confirm-parent")) {Text("Pakai data terbaru")}
                    TextButton(onClick=vm::backToDetails) {Text("Kembali")}
                }
                state.reviewing -> {
                    Notice("Pastikan berkas dan catatan sudah benar sebelum disimpan.")
                    FarmPanel(title="Catatan terkait") {
                        RecordFacts(parent,session,context,RecordKind.entries.single {it.wire==parent.kind})
                    }
                    Button(onClick=vm::queue,enabled=!state.busy && policy?.optBoolean("configured")==true && policy.optBoolean("upload_allowed"),modifier=Modifier.fillMaxWidth().testTag("attachment-queue")) {Text("Simpan lampiran")}
                    TextButton(onClick=vm::backToDetails) {Text("Kembali ke detail berkas")}
                }
                else -> {
                    if(ready && row.serverVersion==null && state.pending.isEmpty()) Button(onClick=vm::review,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-review")) {Text("Cek lampiran")}
                    if(row.body.optString("_copy_state") in setOf("COPYING","FAILED")) OutlinedButton(onClick=vm::resumeCopy,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Salin ulang berkas")}
                    if(ready || row.serverVersion!=null) OutlinedButton(onClick=vm::preview,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-preview")) {Text("Buka berkas")}
                    if(state.pending.any {it.error?.optString("code")=="parent_version_conflict"}) OutlinedButton(onClick=vm::inspectParent,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Pakai data terbaru")}
                    if(state.pending.any {it.state=="REJECTED" && it.error?.optString("code") in setOf("attachment_configuration_missing","attachment_limit_reached","season_closed","local_attachment_unavailable")}) OutlinedButton(onClick=vm::retry,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Coba kirim lagi")}
                    if(row.serverVersion!=null) OutlinedButton(onClick={vm.audit()},enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-audit")) {Text("Lihat riwayat lampiran")}
                    if(session.role==UserRole.MANAGER && row.syncState==SyncState.ACCEPTED && row.body.optString("record_state")=="active" && !state.cancelEditing) OutlinedButton(onClick=vm::beginCancel,modifier=Modifier.fillMaxWidth().testTag("attachment-cancel")) {Text("Batalkan lampiran dengan alasan")}
                }
            }
            if(state.cancelEditing) {
                FinanceField("Alasan pembatalan lampiran",state.reason,vm::editReason,"attachment-reason",singleLine=false)
                Text(if(state.reasonSaved) "Alasan tersimpan." else "Menyimpan alasan…")
                Button(onClick=vm::cancel,enabled=!state.busy && state.reason.isNotBlank() && state.reason.length<=1000,modifier=Modifier.fillMaxWidth().testTag("attachment-confirm-cancel")) {Text("Batalkan lampiran")}
            }
            state.audit?.let {audit ->
                if(state.auditCached) Notice("Menampilkan riwayat tersimpan. Belum bisa memuat yang terbaru.")
                audit.getJSONArray("data").let {rows -> repeat(rows.length()) {val entry=rows.getJSONObject(it);Text("${entry.optString("actor_name")} · ${if(entry.optString("action")=="cancel") "Pembatalan" else "Pencatatan"} · versi ${entry.optLong("version")}");if(!entry.isNull("reason")) Text("Alasan: ${entry.optString("reason")}")}}
                if(!audit.isNull("next_cursor")) TextButton(onClick={vm.audit(true)},enabled=!state.busy) {Text("Tampilkan lagi")}
            }
            OutlinedButton(onClick=vm::closeSelected,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("attachment-back-list")) {Text("Kembali ke lampiran")}
        }
    }
}
private fun copyLabel(body:JSONObject)=when(body.optString("_copy_state")) {"COPYING" -> "Salinan berkas belum selesai.";"FAILED" -> "Berkas gagal disalin. Pilih berkas lagi.";"READY" -> "Berkas tersimpan di perangkat.";else -> "Berkas sudah terkirim. Salinan diunduh saat dibuka."}
private fun typeLabel(mime:String)=when(mime) {"image/png" -> "PNG";"image/jpeg" -> "JPEG";"application/pdf" -> "PDF";else -> "Jenis tidak diketahui"}
