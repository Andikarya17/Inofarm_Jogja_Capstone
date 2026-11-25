package com.inofarm.jogja.feature.customers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.FarmFormBar
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Buyer list with per-buyer totals (the accumulation Inofarm asked for) and the Excel import entry point. */
@Composable internal fun CustomerListScreen(session: FinanceSession, ticket: Long, repository: CustomerRepository, onBack: () -> Unit) {
    var type by rememberSaveable { mutableStateOf("consumer") }
    var query by rememberSaveable { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<Customer>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    if (importing) { CustomerImportScreen(session, ticket, repository, type) { importing = false; reload++ }; return }
    LaunchedEffect(type, query, reload) {
        delay(250); rows = null
        runCatching { repository.search(session, ticket, type, query, 100) }.onSuccess { rows = it; error = null }.onFailure { rows = emptyList(); error = userMessage(it) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = FarmSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack, Modifier.size(FarmSpace.Touch).testTag("customers-back")) { FarmControlIcon(FarmIcon.Close, "Kembali ke Keuangan") }
            Text("Daftar pembeli", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        FarmSegmentedTabs(listOf("consumer" to "Perorangan", "partner" to "Mitra"), type, { type = it }, tagPrefix = "customers-type-")
        Button({ importing = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("customers-import"), shape = RoundedCornerShape(14.dp)) {
            Text("Impor dari Excel")
        }
        FinanceField("Cari", query, { query = it }, "customers-search", null, hint = "Nama atau nomor WhatsApp")
        error?.let { Notice("Daftar pembeli butuh koneksi. $it", true) }
        when (val list = rows) {
            null -> Text("Memuat pembeli…")
            else -> if (list.isEmpty()) Text(if (query.isBlank()) "Belum ada pembeli. Impor dari Excel, atau catat penjualan dengan pembeli baru." else "Tidak ada pembeli yang cocok.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else OutlinedCard(Modifier.fillMaxWidth()) {
                list.forEachIndexed { index, c ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Small),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Text(c.whatsappDisplay ?: "Tanpa WhatsApp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (c.purchases == 0) (if (c.source == "import") "Dari impor" else "-") else com.inofarm.jogja.feature.financehome.kg(java.math.BigDecimal(c.totalKg)),
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
                            Text(if (c.purchases == 0) "Belum beli" else "${c.purchases} kali", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Pick an .xlsx or .csv, match columns, check on the server (dry run), then import. Duplicate numbers are skipped. */
@Composable private fun CustomerImportScreen(session: FinanceSession, ticket: Long, repository: CustomerRepository, type: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var nameColumn by remember { mutableStateOf<Int?>(null) }
    var phoneColumn by remember { mutableStateOf<Int?>(null) }
    var check by remember { mutableStateOf<ImportOutcome?>(null) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    // One id per sheet row for as long as this file is open, so pressing Impor again reports rows as already there.
    val rowIds = remember(sheet) { sheet?.rows?.associate { it.number to java.util.UUID.randomUUID().toString() }.orEmpty() }
    fun rows(): List<ImportRow> {
        val s = sheet ?: return emptyList()
        val n = nameColumn ?: return emptyList()
        return s.rows.map { ImportRow(it.number, it.cells.getOrElse(n) { "" }, phoneColumn?.let { p -> it.cells.getOrElse(p) { "" }.ifBlank { null } }, rowIds.getValue(it.number)) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; check = null; message = null
            runCatching {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "pembeli.xlsx"
                val type0 = context.contentResolver.getType(uri).orEmpty()
                val effective = if (type0.contains("csv") || name.endsWith(".csv", true)) "pembeli.csv" else "pembeli.xlsx"
                name to withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { SpreadsheetReader.read(effective, it) } }
            }.onSuccess { (name, read) ->
                fileName = name; sheet = read
                SpreadsheetReader.guessColumns(read.headers).let { (n, p) -> nameColumn = n; phoneColumn = p }
            }.onFailure { message = (it.message ?: "File belum bisa dibaca.") to true }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = FarmSpace.Medium),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDone, Modifier.size(FarmSpace.Touch).testTag("import-close")) { FarmControlIcon(FarmIcon.Close, "Batal") }
                Column(Modifier.weight(1f)) {
                    Text("Impor pembeli", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text(com.inofarm.jogja.feature.sales.BuyerTypes.label(type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            message?.let { (text, error) -> Notice(text, error) }
            OutlinedButton({ picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "text/plain")) },
                Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("import-pick"), shape = RoundedCornerShape(14.dp), enabled = !busy) {
                Text(fileName?.let { "Ganti file ($it)" } ?: "Pilih file Excel (.xlsx) atau .csv")
            }
            val s = sheet
            if (s != null) {
                Text("${s.rows.size} baris dibaca. Kolom nama wajib; WhatsApp boleh kosong.", style = MaterialTheme.typography.bodyMedium)
                val options = s.headers.mapIndexed { i, h -> org.json.JSONObject().put("id", i.toString()).put("name", h.ifBlank { "Kolom ${i + 1}" }) }
                FarmChoiceChips("Kolom nama", nameColumn?.toString(), options, "import-name-column", scroll = true) { nameColumn = it.toInt(); check = null }
                FarmChoiceChips("Kolom WhatsApp", phoneColumn?.toString() ?: "none", options + org.json.JSONObject().put("id", "none").put("name", "Tidak ada"),
                    "import-phone-column", scroll = true) { phoneColumn = it.toIntOrNull(); check = null }
                check?.let { o ->
                    Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        Count("${o.created}", "Pembeli baru", Modifier.weight(1f), FarmTone.Brand)
                        Count("${o.existing}", "Sudah ada, dilewati", Modifier.weight(1f), FarmTone.Neutral)
                        Count("${o.invalid}", "Perlu dicek", Modifier.weight(1f), FarmTone.Amber)
                    }
                    o.problems.take(20).forEach { (row, reason) -> Text("Baris $row: $reason", style = MaterialTheme.typography.bodySmall, color = FarmColors.Warning) }
                    if (o.invalid > 0) Text("Baris yang perlu dicek tidak diimpor. Perbaiki di Excel lalu impor lagi; pembeli yang sudah ada tidak akan dobel.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (sheet != null) FarmFormBar(saveTag = "import-cancel", onSave = onDone, mainTag = if (check == null) "import-check" else "import-run",
            mainLabel = if (check == null) "Periksa" else "Impor ${check!!.created} pembeli", saveLabel = "Batal", busy = busy,
            enabled = !busy && nameColumn != null, mainEnabled = !busy && nameColumn != null && (check == null || check!!.created > 0),
            onMain = {
                busy = true
                scope.launch {
                    val dry = check == null
                    runCatching { repository.importRows(session, ticket, type, rows(), dryRun = dry) }
                        .onSuccess { if (dry) check = it else { message = "${it.created} pembeli diimpor; ${it.existing} sudah ada." to false; onDone() } }
                        .onFailure { message = userMessage(it) to true }
                    busy = false
                }
            })
    }
}

@Composable private fun Count(value: String, label: String, modifier: Modifier, tone: FarmTone) {
    Column(modifier.background(if (tone == FarmTone.Brand) MaterialTheme.colorScheme.primaryContainer else if (tone == FarmTone.Amber) FarmColors.WarningSurface
        else MaterialTheme.colorScheme.surfaceVariant, FarmShape.Tile).padding(FarmSpace.Medium)) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
