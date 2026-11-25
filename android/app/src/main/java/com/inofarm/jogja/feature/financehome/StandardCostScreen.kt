package com.inofarm.jogja.feature.financehome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.FarmFormBar
import com.inofarm.jogja.feature.FarmFormSection
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

private fun raw(amount: BigDecimal?): String = amount?.stripTrailingZeros()?.let { if (it.scale() < 0) it.setScale(0) else it }
    ?.toPlainString()?.replace('.', ',') ?: ""

/**
 * Biaya standar (Inofarm 20-21 Nov 2026): one sheet per season number, the same for every greenhouse. Required lines keep
 * their names; extra lines can be added and removed. Batal / Simpan, then Ringkasan / Simpan like every manager form.
 */
@Composable internal fun StandardCostScreen(session: FinanceSession, ticket: Long, repository: FinanceHomeRepository, onBack: () -> Unit) {
    var sheets by remember { mutableStateOf<List<CostSheet>?>(null) }
    var sequence by rememberSaveable { mutableStateOf(-1) }
    var draft by remember { mutableStateOf<CostSheet?>(null) }
    val amounts = remember { mutableStateMapOf<String, String>() }
    var summary by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scope = rememberCoroutineScope()
    fun pick(sheet: CostSheet) {
        sequence = sheet.sequence; draft = sheet; summary = false
        amounts.clear(); sheet.lines.forEach { amounts[it.id] = raw(it.amount) }
    }
    LaunchedEffect(session.scope.key) {
        runCatching { repository.sheets(session, ticket) }.onSuccess { list ->
            sheets = list.sortedBy { it.sequence }
            (list.firstOrNull { it.sequence == sequence } ?: list.maxByOrNull { it.sequence })?.let(::pick)
        }.onFailure { message = "Biaya standar belum bisa dimuat. ${userMessage(it)} Butuh koneksi untuk mengubah biaya standar." to true }
    }
    /** Applies typed amounts; returns field errors keyed by line id. */
    fun applied(): Pair<CostSheet?, Map<String, String>> {
        val base = draft ?: return null to emptyMap()
        val errors = mutableMapOf<String, String>()
        var sheet = base
        base.lines.forEach { line ->
            runCatching { FinanceHomeRepository.amount(amounts[line.id].orEmpty()) }
                .onSuccess { sheet = sheet.withAmount(line.id, it) }
                .onFailure { errors[line.id] = "Isi nominal IDR tidak negatif, maksimal 2 angka di belakang koma; kosong berarti belum diisi." }
        }
        return sheet to errors
    }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = FarmSpace.Medium),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack, Modifier.size(FarmSpace.Touch).testTag("cost-close")) { FarmControlIcon(FarmIcon.Close, "Batal") }
                Column(Modifier.weight(1f)) {
                    Text(if (summary) "Ringkasan biaya standar" else "Biaya standar", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text(if (summary) "Langkah 2 dari 2" else "Satu lembar untuk semua greenhouse · langkah 1 dari 2", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            message?.let { (text, error) -> Notice(text, error) }
            val list = sheets
            val sheet = draft
            if (list == null && message == null) Text("Memuat biaya standar…")
            if (list != null && list.isEmpty()) Text("Belum ada musim. Mulai semai di satu greenhouse dulu; lembar Musim 1 dibuat otomatis.")
            if (list != null && sheet != null) {
                if (list.size > 1 && !summary) FarmSegmentedTabs(list.map { it.sequence.toString() to "Musim ${it.sequence}" }, sheet.sequence.toString(),
                    { id -> list.single { it.sequence.toString() == id }.let(::pick) }, tagPrefix = "cost-season-")
                val preview = applied().first ?: sheet
                FarmHeroCard(motif = false) {
                    Text("Biaya total satu GH · Musim ${sheet.sequence}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(preview.tc()?.let(::rupiah) ?: "Belum lengkap", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("cost-tc"))
                    Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Large)) {
                        Column(Modifier.weight(1f)) { Text("Biaya tetap", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")); Text(preview.total("FC")?.let(::rupiah) ?: "Belum lengkap", fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f)) { Text("Biaya variabel", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")); Text(preview.total("VC")?.let(::rupiah) ?: "Belum lengkap", fontWeight = FontWeight.Bold) }
                    }
                    HorizontalDivider(color = FarmColors.ForestPill)
                    Text("Berlaku sama untuk ${sheet.greenhouseCount} GH" + (preview.farmTotal()?.let { " · ${rupiah(it)} per musim" } ?: ""),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
                }
                if (sheet.locked) Notice("Musim ${sheet.sequence} sudah ditutup di ${sheet.closedGreenhouses.joinToString(", ")}. Buka kembali musim itu dulu untuk mengubah biaya standar.", true)
                if (summary) {
                    FarmFormSection("Rincian") {
                        preview.lines.forEach { Line(it.name + if (it.required) "" else " (tambahan)", it.amount?.let(::rupiah) ?: "Belum diisi") }
                    }
                    if (preview.tc() == null) Notice("Masih ada baris yang kosong. Laba musim tetap sementara sampai semua baris terisi.")
                } else {
                    listOf("FC" to "Biaya tetap (FC)", "VC" to "Biaya variabel (VC)").forEach { (costClass, title) ->
                        FarmFormSection(title) {
                            sheet.lines.filter { it.costClass == costClass }.forEach { line ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        FinanceField(if (line.required) "${line.name} · wajib" else line.name, amounts[line.id].orEmpty(), { amounts[line.id] = it },
                                            "cost-line-${line.id}", errors[line.id], KeyboardType.Decimal, visual = IdrGrouping, suffix = "IDR")
                                    }
                                    if (!line.required) IconButton({ draft = sheet.withoutLine(line.id); amounts.remove(line.id) },
                                        Modifier.size(FarmSpace.Touch).testTag("cost-remove-${line.id}")) { FarmControlIcon(FarmIcon.Trash, "Hapus ${line.name}") }
                                }
                            }
                            OutlinedButton({ adding = costClass }, shape = FarmShape.Control, modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("cost-add-$costClass")) {
                                Text(if (costClass == "FC") "Tambah biaya tetap" else "Tambah biaya variabel")
                            }
                        }
                    }
                    Text("Baris wajib dari Inofarm tidak bisa dihapus atau diganti namanya. Perubahan berlaku untuk semua GH yang menjalani Musim ${sheet.sequence}; laporan yang sudah terbit tidak berubah.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (draft != null) FarmFormBar(saveTag = if (summary) "cost-back" else "cost-cancel", onSave = { if (summary) summary = false else onBack() },
            mainTag = if (summary) "cost-save" else "cost-review", mainLabel = "Simpan", saveLabel = if (summary) "Ubah" else "Batal",
            mainIcon = summary, busy = busy, enabled = !busy && draft?.locked != true,
            onMain = {
                val (sheet, found) = applied()
                errors = found
                if (found.isNotEmpty() || sheet == null) return@FarmFormBar
                if (!summary) { summary = true; return@FarmFormBar }
                busy = true
                scope.launch {
                    runCatching { repository.save(session, ticket, sheet) }
                        .onSuccess { saved ->
                            sheets = sheets.orEmpty().map { if (it.id == saved.id) saved else it }
                            pick(saved); message = "Biaya standar Musim ${saved.sequence} tersimpan untuk ${saved.greenhouseCount} GH." to false
                        }
                        .onFailure { message = userMessage(it) to true }
                    busy = false
                }
            })
    }
    adding?.let { costClass -> AddCostSheet(costClass, { adding = null }) { name, amount ->
        val base = draft ?: return@AddCostSheet "Lembar belum dimuat."
        if (base.lines.any { it.name.trim().equals(name.trim(), ignoreCase = true) }) return@AddCostSheet "Nama biaya sudah ada di lembar ini."
        val value = runCatching { FinanceHomeRepository.amount(amount) }.getOrElse { return@AddCostSheet "Isi nominal IDR tidak negatif; kosong berarti belum diisi." }
        val line = CostLine(UUID.randomUUID().toString(), name.trim(), costClass, value, required = false, sortOrder = base.lines.size)
        draft = base.withLine(line); amounts[line.id] = raw(value); adding = null
        null
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AddCostSheet(costClass: String, onDismiss: () -> Unit, onAdd: (String, String) -> String?) {
    var name by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Section), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            FarmSheetHeading(if (costClass == "FC") "Tambah biaya tetap" else "Tambah biaya variabel", onDismiss)
            FinanceField("Nama pengeluaran", name, { name = it; error = null }, "cost-new-name", error?.takeIf { name.isBlank() || it.contains("Nama") }, hint = "Contoh: listrik pompa")
            FinanceField("Jumlah per GH per musim", amount, { amount = it }, "cost-new-amount", null, KeyboardType.Decimal, visual = IdrGrouping, suffix = "IDR",
                hint = "Masuk ke setiap GH. Kosong berarti belum diisi.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                OutlinedButton(onDismiss, Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Batal") }
                Button({ error = if (name.isBlank()) "Isi nama pengeluaran." else onAdd(name, amount) }, Modifier.weight(1.4f).heightIn(min = 52.dp).testTag("cost-new-add"),
                    shape = RoundedCornerShape(16.dp)) { Text("Tambah") }
            }
        }
    }
}
