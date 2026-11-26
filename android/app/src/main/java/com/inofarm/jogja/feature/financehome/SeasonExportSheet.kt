package com.inofarm.jogja.feature.financehome

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.network.ExportFile
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.network.SeasonExportTransport
import com.inofarm.jogja.core.session.AccountScope
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/** The downloaded workbook waiting for the file picker, bound to the account and sign-in it was fetched for. */
class PendingExportSlot {
    private class Held(val file: ExportFile, val scope: AccountScope, val ticket: Long)
    @Volatile private var held: Held? = null
    val isEmpty get() = held == null
    fun put(file: ExportFile, scope: AccountScope, ticket: Long) { held = Held(file, scope, ticket) }
    fun clear() { held = null }
    /** Takes the file out (the slot is emptied) only while [valid] still accepts the account and sign-in it was fetched for. */
    fun claim(valid: (AccountScope, Long) -> Boolean): ExportFile? {
        val current = held; held = null
        return current?.takeIf { valid(it.scope, it.ticket) }?.file
    }
}

/** Download of the season workbook: fetch first (integrity checked), then the Android file picker, then the bytes are written. */
class SeasonExportRepository(private val context: Context, private val vault: FinanceSessionVault, private val api: FinanceApi) {
    private val slot = PendingExportSlot()

    suspend fun fetch(session: FinanceSession, ticket: Long, sequence: String, parts: Set<String>): ExportFile = withContext(Dispatchers.IO) {
        check(vault.matches(ticket, session.scope)) { "Akun yang masuk berubah. Buka kembali Keuangan." }
        require(session.role == UserRole.MANAGER) { "Excel keuangan hanya untuk pengelola." }
        slot.clear()
        val file = api.exportSeason(session.scope.server, session.token, sequence, parts)
        check(vault.matches(ticket, session.scope)) { "Akun yang masuk berubah. Buka kembali Keuangan." }
        file.also { slot.put(it, session.scope, ticket) }
    }

    fun discard() = slot.clear()

    /** Writes the waiting workbook to [uri]; returns its name, or null (nothing written) when it is gone or the account changed. */
    suspend fun save(uri: Uri): String? = withContext(Dispatchers.IO) {
        val file = slot.claim { scope, ticket -> vault.matches(ticket, scope) } ?: return@withContext null
        try {
            require(uri.scheme == "content") { "Pilih tujuan berkas melalui penyimpanan Android." }
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(file.bytes); it.flush() }
                ?: throw IOException("Berkas tujuan tidak bisa dibuka. Pilih lokasi lain.")
            file.fileName
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw error
        }
    }
}

internal data class ExportPart(val wire: String, val title: String, val sub: String)

internal val EXPORT_PARTS = listOf(
    ExportPart("summary", "Ringkasan per GH", "FC, VC, TC, penjualan (Rp) dan kg terjual tiap GH"),
    ExportPart("costs", "Rincian biaya standar", "Semua baris FC dan VC per GH"),
    ExportPart("sales", "Daftar penjualan", "Satu baris per transaksi: tanggal, GH, pembeli, tipe, kg, harga"),
)

/** "inofarm-keuangan-musim-3-20261125.xlsx", the name the server will also send (shown before downloading). */
internal fun exportFileName(sequence: String, today: LocalDate = LocalDate.now(ZoneId.of("Asia/Jakarta"))): String =
    "inofarm-keuangan-musim-${if (sequence == "all") "semua" else sequence}-${"%04d%02d%02d".format(today.year, today.monthValue, today.dayOfMonth)}.xlsx"

/**
 * The "Unduh Excel" flow (artboard EksporExcel): a sheet to pick the season and contents, and a dialog with the result.
 * [defaultSequence] is the season open on the screen.
 */
@Composable internal fun SeasonExportFlow(
    session: FinanceSession, ticket: Long, repository: SeasonExportRepository, sequences: List<SeasonTotals>, defaultSequence: Int?, onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SeasonExportTransport.MIME)) { uri ->
        picking = false
        if (uri == null) { repository.discard(); result = "Belum disimpan. File Excel tidak dibuat." }
        else scope.launch {
                runCatching { repository.save(uri) }
                    .onSuccess { name -> result = if (name == null) "Hasil unduhan sudah hilang. Buka Excel dan unduh lagi." else "Excel tersimpan: $name" }
                    .onFailure { result = "Berkas belum terkonfirmasi tersimpan lengkap. ${userMessage(it)}" }
        }
    }
    DisposableEffect(repository) { onDispose { repository.discard() } }
    if (result != null) {
        AlertDialog(onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.surface, title = { Text("Unduh Excel") },
            text = { Text(result.orEmpty(), Modifier.testTag("season-export-result")) },
            confirmButton = { TextButton(onClose, Modifier.testTag("season-export-result-close")) { Text("Tutup") } })
        return
    }
    if (picking) return
    SeasonExportSheet(sequences, defaultSequence, busy, failure, onDismiss = { if (!busy) onClose() }) { sequence, parts ->
        busy = true; failure = null
        scope.launch {
            runCatching { repository.fetch(session, ticket, sequence, parts) }
                .onSuccess { picking = true; runCatching { launcher.launch(it.fileName) }.onFailure { error -> picking = false; repository.discard(); failure = userMessage(error) } }
                .onFailure { failure = userMessage(it) }
            busy = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SeasonExportSheet(
    sequences: List<SeasonTotals>, defaultSequence: Int?, busy: Boolean, failure: String?, onDismiss: () -> Unit, onDownload: (sequence: String, parts: Set<String>) -> Unit,
) {
    val options = remember(sequences) { sequences.sortedByDescending { it.sequence } }
    var choice by rememberSaveable { mutableStateOf(defaultSequence?.toString() ?: options.firstOrNull()?.sequence?.toString() ?: "all") }
    var chosen by rememberSaveable { mutableStateOf(EXPORT_PARTS.map { it.wire }) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface, shape = FarmShape.Sheet,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal) }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Large).testTag("season-export-sheet"),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Text("Unduh Excel keuangan", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Berisi angka biaya dan penjualan apa adanya, untuk dihitung sendiri di Excel.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Musim", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                options.forEach { season ->
                    SeasonOption("Musim ${season.sequence}", if (season.running) "Berjalan" else "Selesai", choice == season.sequence.toString(), "season-export-opt-${season.sequence}") {
                        choice = season.sequence.toString()
                    }
                }
                SeasonOption("Semua musim", "Satu baris per musim", choice == "all", "season-export-opt-all") { choice = "all" }
            }
            Text("Isi file, satu sheet per pilihan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(horizontal = FarmSpace.Medium)) {
                    EXPORT_PARTS.forEachIndexed { index, part ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PartRow(part, part.wire in chosen) { on -> chosen = if (on) chosen + part.wire else chosen - part.wire }
                    }
                }
            }
            Text("Nama file: ${exportFileName(choice)}", Modifier.testTag("season-export-file"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (chosen.isEmpty()) Text("Pilih minimal satu isi file.", Modifier.testTag("season-export-empty"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            failure?.let { Notice("Excel belum bisa diunduh. $it", true) }
            Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                OutlinedButton(onDismiss, enabled = !busy, shape = FarmShape.Control, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("season-export-cancel")) { Text("Batal") }
                Button({ onDownload(choice, chosen.toSet()) }, enabled = !busy && chosen.isNotEmpty(), shape = FarmShape.Control,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("season-export-download")) {
                    Text(if (busy) "Menyiapkan…" else "Unduh .xlsx", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun SeasonOption(title: String, sub: String, on: Boolean, tag: String, onPick: () -> Unit) {
    Surface(onPick, Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).semantics { selected = on }.testTag(tag), shape = RoundedCornerShape(16.dp),
        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = FarmSpace.Medium, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(on, onClick = null)
            Spacer(Modifier.width(FarmSpace.Small))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun PartRow(part: ExportPart, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).padding(vertical = 6.dp)
        .toggleable(on, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = onChange).testTag("season-export-part-${part.wire}"), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, onCheckedChange = null)
        Spacer(Modifier.width(FarmSpace.Small))
        Column(Modifier.weight(1f)) {
            Text(part.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(part.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
