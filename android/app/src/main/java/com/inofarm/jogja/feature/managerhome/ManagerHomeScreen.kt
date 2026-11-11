package com.inofarm.jogja.feature.managerhome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.activities.MeasurementCards
import com.inofarm.jogja.feature.activities.MeasurementSource
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.expenses.moneyLabel
import com.inofarm.jogja.feature.timeLabel
import com.inofarm.jogja.feature.verification.verificationLabel
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class HomeDestination(
    val kind: OperationalKind,
    val filter: LocalHistoryFilter,
    val recordId: String? = null,
    val seasonLabelId: String? = filter.seasonId,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManagerHomeScreen(
    selection: String?,
    state: ManagerHomeState,
    queue: List<QueuedOperation>,
    padding: PaddingValues,
    periodReview: Boolean,
    onPeriodReview: (Boolean) -> Unit,
    onSelect: (String?) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (HomeDestination) -> Unit,
    onQueue: (QueuedOperation) -> Unit,
    onRecord: (ActivityType, String?) -> Unit,
    onHarvest: () -> Unit,
    onAdmin: () -> Unit,
    seasonActions: @Composable (JSONObject) -> Unit,
    onDesignate: suspend (String?, String) -> Unit,
) {
    val snapshot = state.snapshot
    val selected = snapshot?.selected
    val period = snapshot?.period
    val today = LocalDate.now(ActivityDraft.WIB)
    val validToday = snapshot?.day == today && snapshot.complete && !state.cached && !state.loading
    var picker by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val placeholderList = rememberLazyListState()
    val appearance = remember { Animatable(if (selection == null) 1f else .78f) }
    LaunchedEffect(Unit) { appearance.animateTo(1f, tween(FarmMotion.State)) }
    val names = snapshot?.choices.orEmpty().associate { it.getString("id") to it.getString("name") }
    val activities = LocalHistoryFilter(greenhouseId = selection,
        groupId = period?.text("group_id"), recordState = "active")
    val periodId = period?.text("season_id")
    val queued = queue.firstOrNull { it.state in setOf("CONFLICT", "REJECTED", "AUTH_REQUIRED", "SCOPE_REVOKED", "EPOCH_REVIEW") }

    LazyColumn(
        Modifier.fillMaxSize().graphicsLayer { alpha = appearance.value }.testTag("finance-home-list"),
        state = if (snapshot == null) placeholderList else list,
        contentPadding = padding, verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
    ) {
        item("scope") {
            FarmMenuHeader(if (selection == null) "Semua greenhouse" else selected?.text("name") ?: "Greenhouse",
                dateLabel((snapshot?.day ?: today).toString()) + " WIB", contextual = true) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    FilledTonalButton(onClick = { picker = true }, enabled = !state.denied, shape = FarmShape.Control,
                        modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-home-picker")) {
                        Text(if (selection == null) "Semua greenhouse" else "Ganti greenhouse")
                    }
                    if (selection != null) TextButton(onClick = { onSelect(null) }, shape = FarmShape.Control,
                        modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-home-all")) { Text("Semua greenhouse") }
                }
                if (snapshot != null) Text(
                    if (state.cached) "Data tersimpan · ${timeLabel(snapshot.asOf.toString())}"
                    else "Diperbarui ${snapshot.asOf.atZone(ActivityDraft.WIB).format(DateTimeFormatter.ofPattern("HH:mm 'WIB'"))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (state.message != null || state.cached) item("coverage") {
            FarmPanel {
                Text(if (state.cached) "Belum bisa memperbarui. Data yang tampil adalah data tersimpan." else state.message.orEmpty())
                TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Perbarui") }
            }
        }
        if (snapshot == null) {
            if (state.loading) item("loading") { FarmPanel { Text("Memuat ringkasan greenhouse…") } }
        } else if (selection == null) {
            item("greenhouse-title") { SectionTitle("Greenhouse") }
            if (snapshot.cards.isEmpty()) item("empty") { FarmPanel { Text("Belum ada greenhouse. Tambah greenhouse pertama.") } }
            items(snapshot.cards, key = { it.getString("id") }) { card ->
                FarmRecordCard(Modifier.testTag("manager-greenhouse-${card.getString("id")}"), onOpen = { onSelect(card.getString("id")) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        Text(card.getString("name"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        FarmControlIcon(FarmIcon.ChevronRight, null)
                    }
                    PeriodFacts(card.getJSONObject("period"), expanded = false)
                    Caption(card.text("last_operational_at")?.let { "Catatan terakhir ${timeLabel(it)}" } ?: "Belum ada catatan operasional")
                    card.optJSONObject("harvest")?.let { harvest ->
                        val kg = harvest.getString("weight_kg").toBigDecimal().stripTrailingZeros().toPlainString()
                        Caption("Panen tanam saat ini ·${harvest.getLong("fruit_count")} buah · $kg kg")
                    }
                }
            }
            if (snapshot.cards.any { it.optBoolean("active") }) item("record-scope") {
                OutlinedButton(onClick = { picker = true }, shape = FarmShape.Control,
                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch)) { Text("Pilih greenhouse untuk mencatat") }
            }
        } else if (selected != null) {
            item("period") {
                FarmPanel(title = "Tanam saat ini") {
                    PeriodFacts(selected.getJSONObject("period"), expanded = true)
                    TextButton(onClick = { onPeriodReview(true) }, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-period-history")) {
                        Text("Ganti tanam saat ini")
                    }
                }
            }
            item("measurement") {
                Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    val server = snapshot.detail?.optJSONObject("measurement")
                    val dayState = snapshot.detail?.text("measurement_day_state")
                    val checked = validToday && period != null && snapshot.detail?.optBoolean("measurement_available", true) != false
                    val serverToday = server?.text("occurred_at")?.let { Instant.parse(it).atZone(ActivityDraft.WIB).toLocalDate() == today } == true
                    val status = when {
                        period == null -> "Pilih tanam saat ini untuk melihat pengukuran."
                        !checked -> "Pencatatan hari ini belum bisa dimuat"
                        dayState == "invalid_only" -> "Pengukuran hari ini perlu diperbaiki. Lihat riwayat kegiatan."
                        dayState == "unavailable" -> "Pencatatan hari ini belum bisa dimuat"
                        dayState == "missing" || !serverToday -> "Belum ada pencatatan hari ini"
                        else -> "Pengukuran hari ini"
                    }
                    MeasurementCards(state.measurement, loading = state.loading && state.measurement == null, unavailable = !checked)
                    Text(status, Modifier.testTag("manager-measurement-day-status"), style = MaterialTheme.typography.titleSmall)
                    if (state.measurement?.queued == true) {
                        Caption("Angka ini belum terkirim.")
                    } else if (state.measurement != null) {
                        if (!serverToday) Caption("Kartu menampilkan pengukuran terakhir.")
                        server?.text("verification_status")?.let { Caption(verificationLabel(it)) }
                    }
                    MeasurementSource(state.measurement, today, state.cached, snapshot.asOf.toEpochMilli(), null, snapshotSource = true)
                    val check = server?.optJSONObject("reference_snapshot")?.optJSONObject("checks")?.optJSONObject("manual_ppm")
                    if (state.measurement?.queued == false && check?.text("status") !in setOf("within_range", "outside_range")) {
                        Caption("PPM belum dapat dibandingkan dengan acuan saat pengukuran.")
                    }
                }
            }
            if (period != null) {
                item("record") {
                    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        Button(onClick = { onRecord(ActivityType.MEASUREMENT, null) }, shape = FarmShape.Control,
                            modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("finance-measurement-new")) { Text("Catat pengukuran") }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                            FilledTonalButton(onClick = { onRecord(ActivityType.GENERAL, null) }, shape = FarmShape.Control,
                                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-new")) { Text("Catat kegiatan") }
                            FilledTonalButton(onClick = onHarvest, shape = FarmShape.Control,
                                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("harvest-new")) { Text("Catat panen") }
                        }
                    }
                }
            }
            item("work") {
                FarmPanel(title = "Kegiatan dan jadwal") {
                    val schedules = snapshot.detail?.rows("schedules").orEmpty()
                    if (period != null && schedules.isEmpty()) Caption("Belum ada jadwal aktif mulai tanggal ringkasan.")
                    schedules.forEachIndexed { index, schedule ->
                        if (index > 0) HorizontalDivider()
                        val type = ActivityType.entries.find { it.wire == schedule.text("activity_type") }
                        FarmFact(timeLabel(schedule.getString("planned_at")), type?.label ?: "Kegiatan")
                        schedule.text("notes")?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (type != null) TextButton(onClick = { onRecord(type, schedule.getString("id")) },
                            modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Catat hasil") }
                    }
                    TextButton(onClick = { onOpen(HomeDestination(OperationalKind.ACTIVITY, activities, seasonLabelId = periodId)) },
                        modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat kegiatan") }
                }
            }
            item("harvest") {
                FarmPanel(title = if (period == null) "Riwayat panen" else "Panen tanam saat ini") {
                    snapshot.detail?.optJSONObject("harvest")?.let { harvest ->
                        if (harvest.getInt("records") == 0) Caption("Belum ada panen tercatat.")
                        else {
                            val weight = BigDecimal(harvest.getString("weight_kg")).stripTrailingZeros().toPlainString().replace('.', ',')
                            Text("$weight kg · ${harvest.getLong("fruit_count")} buah", style = MaterialTheme.typography.titleMedium)
                            Caption("${harvest.getInt("records")} catatan panen")
                        }
                    }
                    TextButton(onClick = { onOpen(HomeDestination(OperationalKind.HARVEST, activities, seasonLabelId = periodId)) },
                        modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat panen") }
                }
            }
        }
        val findings = snapshot?.json?.rows("attention").orEmpty().filter {
            !state.cached && (it.text("type") == "period_review" || validToday)
        }
        if (findings.isNotEmpty() || queued != null) {
            item("attention") { SectionTitle("Perlu perhatian") }
            if (queued != null) item("local-queue") {
                FarmPanel(title = "Belum terkirim") {
                    Caption(queued.error?.text("message") ?: "Ada catatan yang perlu dicek.")
                    TextButton(onClick = { onQueue(queued) }, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat rinciannya") }
                }
            }
            items(findings, key = { it.getString("id") }) { finding ->
                val periodFinding = finding.text("type") == "period_review"
                val outside = finding.text("type") == "measurement_outside_range"
                val gh = finding.getString("greenhouse_id")
                val title = when {
                    periodFinding -> "Tanam saat ini belum dipilih"
                    outside -> "PPM di luar acuan"
                    else -> "Belum ada pengukuran tercatat hari ini"
                }
                FarmPanel(title = title) {
                    Caption("${finding.getString("greenhouse_name")} · ${timeLabel(finding.getString("checked_at"))}")
                    if (outside) {
                        val target = finding.getJSONObject("target")
                        val basis = target.text("target_value")?.let { "$it ± ${target.getString("tolerance")}" }
                            ?: "${target.getString("minimum")}–${target.getString("maximum")}"
                        Text("${finding.getString("actual")} ppm · ${timeLabel(finding.getString("occurred_at"))}")
                        Caption("Acuan versi ${target.getInt("version")} · $basis ppm")
                    }
                    TextButton(onClick = {
                        if (periodFinding) {
                            if (selection != gh) onSelect(gh)
                            onPeriodReview(true)
                        } else {
                            onOpen(HomeDestination(OperationalKind.ACTIVITY, LocalHistoryFilter(
                                greenhouseId = gh, groupId = finding.text("group_id"),
                                type = ActivityType.MEASUREMENT.wire.takeUnless { outside },
                                from = today.toString(), until = today.toString(), recordState = "active"), finding.text("source_record_id")))
                        }
                    }, modifier = Modifier.heightIn(min = FarmSpace.Touch)) {
                        Text(if (periodFinding) "Pilih tanam saat ini" else "Lihat riwayat pengukuran")
                    }
                }
            }
        }
        if (snapshot != null) {
            item("recent") {
                FarmPanel(title = "Catatan terbaru") {
                    val recent = snapshot.json.rows("recent")
                    if (snapshot.json.isNull("recent")) Caption("Catatan terbaru belum dapat dibaca lengkap.")
                    else if (recent.isEmpty()) Caption("Belum ada catatan.")
                    recent.forEach { record ->
                        val type = record.text("activity_type")
                        val kind = if (record.text("kind") == "harvest") OperationalKind.HARVEST else OperationalKind.ACTIVITY
                        val gh = record.getString("greenhouse_id")
                        val at = record.getString("occurred_at")
                        val received = record.getString("received_at")
                        val label = if (kind == OperationalKind.HARVEST) "Panen" else ActivityType.entries.find { it.wire == type }?.label ?: "Kegiatan"
                        TextButton(onClick = {
                            val date = Instant.parse(at).atZone(ActivityDraft.WIB).toLocalDate().toString()
                            onOpen(HomeDestination(kind, LocalHistoryFilter(greenhouseId = gh, groupId = record.text("group_id"),
                                type = type, from = date, until = date, recordState = "active"), record.getString("id"), record.text("season_id")))
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch), shape = FarmShape.Control,
                            contentPadding = PaddingValues(horizontal = FarmSpace.Small, vertical = FarmSpace.Small)) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Caption(listOfNotNull(names[gh], record.text("recorded_by_name") ?: "Pencatat belum tersedia").joinToString(" · "))
                                Caption(timeLabel(at))
                                if (java.time.Duration.between(Instant.parse(at), Instant.parse(received)).toHours() >= 1)
                                    Caption("Terkirim ${timeLabel(received)}")
                            }
                        }
                        HorizontalDivider()
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        TextButton(onClick = { onOpen(HomeDestination(OperationalKind.ACTIVITY, activities, seasonLabelId = periodId)) },
                            modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat semua kegiatan") }
                        TextButton(onClick = { onOpen(HomeDestination(OperationalKind.HARVEST, activities, seasonLabelId = periodId)) },
                            modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat semua panen") }
                    }
                }
            }
            item("cash") { CashSummary(snapshot, onOpen) }
        }
    }

    if (picker) FarmPicker(
        title = "Pilih greenhouse",
        options = listOf(FarmOption("", "Semua greenhouse")) + snapshot?.choices.orEmpty().map {
            FarmOption(it.getString("id"), it.getString("name"))
        },
        selected = selection.orEmpty(),
        optionTag = { "manager-picker-${it.ifEmpty { "all" }}" },
        onDismiss = { picker = false },
        onSelect = { picker = false; onSelect(it.ifEmpty { null }) },
        sheet = true
    )
    if (periodReview && snapshot?.selected != null) RunningPeriodSheet(snapshot, state, onPeriodReview, onAdmin, onOpen, seasonActions, onDesignate)
}

@Composable
private fun PeriodFacts(period: JSONObject, expanded: Boolean) {
    when (period.text("state")) {
        "ready" -> {
            Text(period.getString("variety"), style = MaterialTheme.typography.titleMedium)
            val age = period.getJSONObject("age")
            val days = listOfNotNull(age.text("hss")?.let { "$it hari sejak semai (HSS)" },
                age.text("hst")?.let { "$it hari sejak pindah tanam (HST)" })
            Caption(days.joinToString(" · ").ifBlank { "Umur tanam belum tersedia" })
            val phase = period.optJSONObject("phase")?.text("value")
            Caption(phase?.let { "Fase aktual · $it" } ?: "Fase aktual belum tercatat")
            if (expanded) {
                Caption(period.getString("season_name"))
                Caption("Semai ${dateLabel(period.getString("sowing_date"))}")
                Caption(period.text("transplant_date")?.let { "Pindah tanam ${dateLabel(it)}" } ?: "Tanggal pindah tanam belum tercatat")
            }
        }
        "needs_review" -> Text("Tanam saat ini belum dipilih")
        "inactive" -> Text("Greenhouse tidak aktif")
        else -> Text("Belum ada tanam saat ini")
    }
}

@Composable
private fun CashSummary(snapshot: HomeSnapshot, onOpen: (HomeDestination) -> Unit) {
    val cash = snapshot.cash
    val from = cash.getString("from")
    val until = cash.getString("until")
    val receipts = cash.getJSONObject("receipts")
    val payments = cash.getJSONObject("payments")
    val filter = LocalHistoryFilter(greenhouseId = snapshot.selection, from = from, until = until, recordState = "active")
    FarmPanel(title = "Keuangan bulan ini") {
        Caption("${dateLabel(from)} – ${dateLabel(until)} WIB · ${snapshot.selected?.getString("name") ?: "Semua greenhouse"}")
        if (receipts.getLong("records") + payments.getLong("records") == 0L) {
            Text("Belum ada transaksi tercatat pada periode ini.")
        } else {
            CashAmount("Penerimaan tercatat", "Penjualan lunas", receipts)
            HorizontalDivider()
            CashAmount("Pengeluaran tercatat", "Pembayaran aktual, termasuk pembelian aset", payments)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            TextButton(onClick = { onOpen(HomeDestination(OperationalKind.EXPENSE, filter)) },
                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-home-finance")) { Text("Lihat keuangan") }
            TextButton(onClick = { onOpen(HomeDestination(OperationalKind.SALE, filter)) },
                modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat penjualan lunas") }
        }
    }
}

@Composable
private fun CashAmount(label: String, description: String, total: JSONObject) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    val records = total.getLong("records")
    if (records > 0) FarmAmount(moneyLabel(total.getString("amount")))
    Caption(if (records == 0L) "$description · Belum ada catatan" else "$description · $records catatan")
}

@Composable
internal fun SectionTitle(label: String) {
    Text(label, Modifier.padding(top = FarmSpace.Small).semantics { heading() },
        style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun Caption(label: String) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun dateLabel(date: String): String =
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID")))
