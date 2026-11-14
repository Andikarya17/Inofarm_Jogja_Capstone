package com.inofarm.jogja.feature.managerhome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.inofarm.jogja.feature.admin.AdminResource
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
    reviewCount: Int? = null,
    onReviewInbox: () -> Unit = {},
    onAddGreenhouse: () -> Unit = {},
    onAdminFor: (AdminResource, Boolean) -> Unit = { _, _ -> },
    onHarvestProgress: () -> Unit = {},
    onSeasonReport: () -> Unit = {},
) {
    val snapshot = state.snapshot
    val selected = snapshot?.selected
    val period = snapshot?.period
    val today = LocalDate.now(ActivityDraft.WIB)
    val validToday = snapshot?.day == today && snapshot.complete && !state.cached && !state.loading
    var section by rememberSaveable(selection) { mutableStateOf("summary") }
    val list = rememberLazyListState()
    val placeholderList = rememberLazyListState()
    val appearance = remember { Animatable(if (selection == null) 1f else .78f) }
    LaunchedEffect(Unit) { appearance.animateTo(1f, tween(FarmMotion.State)) }
    val names = snapshot?.choices.orEmpty().associate { it.getString("id") to it.getString("name") }
    val activities = LocalHistoryFilter(greenhouseId = selection,
        groupId = period?.text("group_id"), recordState = "active")
    val periodId = period?.text("season_id")
    val queued = queue.firstOrNull { it.state in setOf("CONFLICT", "REJECTED", "AUTH_REQUIRED", "SCOPE_REVOKED", "EPOCH_REVIEW") }

    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) pulled = false }
    PullToRefreshBox(isRefreshing = pulled && state.loading, onRefresh = { pulled = true; onRefresh() }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().graphicsLayer { alpha = appearance.value }.testTag("finance-home-list"),
        state = if (snapshot == null) placeholderList else list,
        contentPadding = padding, verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
    ) {
        if (selection != null && selected != null) item("hero") {
            GreenhouseHero(selected, when {
                snapshot == null -> ""
                state.cached -> "Data tersimpan · ${timeLabel(snapshot.asOf.toString())}"
                else -> "Diperbarui ${snapshot.asOf.atZone(ActivityDraft.WIB).format(DateTimeFormatter.ofPattern("HH.mm 'WIB'"))}"
            }) { onPeriodReview(true) }
        }
        if (state.message != null || state.cached) item("coverage") {
            FarmPanel {
                Text(if (state.cached) "Belum bisa memperbarui. Data yang tampil adalah data tersimpan." else state.message.orEmpty())
                TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Perbarui") }
            }
        }
        if (selection == null) {
            if (snapshot != null) item("cash-hero") {
                CashHero(snapshot, when {
                    state.cached -> "Data tersimpan · ${timeLabel(snapshot.asOf.toString())}"
                    else -> "Diperbarui ${snapshot.asOf.atZone(ActivityDraft.WIB).format(DateTimeFormatter.ofPattern("HH:mm 'WIB'"))}"
                }, state.loading, onOpen)
            }
            item("shortcuts") { HomeShortcuts(reviewCount, onReviewInbox, onAddGreenhouse, onHarvestProgress, onSeasonReport) }
        }
        if (snapshot == null) {
            if (state.loading) item("loading") { FarmPanel { Text("Memuat ringkasan greenhouse…") } }
        } else if (selection == null) {
            if (reviewCount != null && reviewCount > 0) item("review-inbox") { ReviewCard(reviewCount, onReviewInbox) }
            item("greenhouse-title") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Greenhouse") }
                    Text("${snapshot.cards.size} greenhouse", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (snapshot.cards.isEmpty()) item("empty") { FarmPanel { Text("Belum ada greenhouse. Tambah greenhouse pertama.") } }
            val outsideFor = snapshot.json.rows("attention").filter {
                !state.cached && validToday && it.text("type") == "measurement_outside_range"
            }.map { it.getString("greenhouse_id") }.toSet()
            itemsIndexed(snapshot.cards, key = { _, card -> card.getString("id") }) { index, card ->
                // Cards slide into place when a greenhouse is added or leaves the list.
                Column(Modifier.animateItem()) {
                    GreenhouseCard(card, index, card.getString("id") in outsideFor) { onSelect(card.getString("id")) }
                }
            }
        } else if (selected != null) {
            item("hub-sections") {
                FarmSegmentedTabs(listOf("summary" to "Ringkasan", "activity" to "Kegiatan", "harvest" to "Panen",
                    "finance" to "Keuangan", "settings" to "Pengaturan"), section, { section = it })
            }
            if (section == "summary") item("measurement") {
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
                        else -> "Sudah ada pencatatan hari ini"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Pengukuran hari ini", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                        if (serverToday) server?.text("occurred_at")?.let { at ->
                            Text(Instant.parse(at).atZone(ActivityDraft.WIB).format(DateTimeFormatter.ofPattern("HH.mm 'WIB'")),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    MeasurementCards(state.measurement, loading = state.loading && state.measurement == null, unavailable = !checked, showTitle = false)
                    Text(status, Modifier.testTag("manager-measurement-day-status"), style = MaterialTheme.typography.titleSmall)
                    if (state.measurement?.queued == true) {
                        Caption("Angka ini belum terkirim.")
                    } else if (state.measurement != null) {
                        if (!serverToday) Caption("Kartu menampilkan pengukuran terakhir.")
                        server?.text("verification_status")?.let { code ->
                            val label = verificationLabel(code)
                            FarmStatusChip(label, tone = when {
                                "perbaiki" in label -> FarmTone.Error
                                "Belum" in label -> FarmTone.Info
                                else -> FarmTone.Good
                            })
                        }
                    }
                    MeasurementSource(state.measurement, today, state.cached, snapshot.asOf.toEpochMilli(), null, snapshotSource = true)
                    val check = server?.optJSONObject("reference_snapshot")?.optJSONObject("checks")?.optJSONObject("manual_ppm")
                    if (state.measurement?.queued == false && check?.text("status") !in setOf("within_range", "outside_range")) {
                        Caption("PPM belum dapat dibandingkan dengan acuan saat pengukuran.")
                    }
                }
            }
            if (section == "activity") item("work") {
                FarmPanel(title = "Kegiatan dan jadwal") {
                    val schedules = snapshot.detail?.rows("schedules").orEmpty()
                    if (period != null && schedules.isEmpty()) Caption("Belum ada jadwal aktif.")
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
            if (section in setOf("summary", "harvest")) item("harvest") {
                val harvest = snapshot.detail?.optJSONObject("harvest")
                val title = if (period == null) "Riwayat panen" else "Panen tanam saat ini"
                val (headline, note) = when {
                    harvest == null -> "Belum ada panen" to null
                    harvest.getInt("records") == 0 -> "Belum ada panen" to null
                    else -> {
                        val weight = BigDecimal(harvest.getString("weight_kg")).stripTrailingZeros().toPlainString().replace('.', ',')
                        "$weight kg · ${harvest.getLong("fruit_count")} buah" to "${harvest.getInt("records")} catatan panen"
                    }
                }
                HarvestRow(title, headline, note) { onOpen(HomeDestination(OperationalKind.HARVEST, activities, seasonLabelId = periodId)) }
            }
            if (section == "settings") item("settings") {
                val active = selected.optBoolean("active", true)
                FarmRecordCard {
                    if (!active) Text("Greenhouse nonaktif. Ubah greenhouse untuk mengaktifkannya lagi.", style = MaterialTheme.typography.bodyMedium)
                    if (active) {
                        FarmActionRow("Musim dan kelompok tanam", "Tambah musim, kelompok tanam dan varietas", FarmIcon.Schedule,
                            Modifier.testTag("hub-seasons")) { onAdminFor(AdminResource.GREENHOUSE, false) }
                        FarmRowDivider()
                        FarmActionRow("Pekerja di greenhouse ini", "Tambah atau lepas pekerja", FarmIcon.Account,
                            Modifier.testTag("hub-workers")) { onAdminFor(AdminResource.ASSIGNMENT, false) }
                        FarmRowDivider()
                    }
                    FarmActionRow(if (active) "Ubah atau nonaktifkan" else "Ubah greenhouse", "Nama dan status greenhouse", FarmIcon.Greenhouse,
                        Modifier.testTag("hub-edit")) { onAdminFor(AdminResource.GREENHOUSE, true) }
                }
            }
        }
        val findings = snapshot?.json?.rows("attention").orEmpty().filter {
            !state.cached && (it.text("type") == "period_review" || validToday)
        }
        if ((findings.isNotEmpty() || queued != null) && (selection == null || section == "summary")) {
            item("attention") { SectionTitle("Perlu perhatian") }
            if (queued != null) item("local-queue") {
                AttentionCard("Belum terkirim") {
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
                AttentionCard(title) {
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
            if (selection == null || section in setOf("activity", "harvest")) item("recent") {
                val recent = snapshot.json.rows("recent").filter {
                    selection == null || (section == "harvest") == (it.text("kind") == "harvest")
                }
                RecentRecords(recent, snapshot.json.isNull("recent"), names, selection == null, onOpen = { record ->
                    val type = record.text("activity_type")
                    val kind = if (record.text("kind") == "harvest") OperationalKind.HARVEST else OperationalKind.ACTIVITY
                    val date = Instant.parse(record.getString("occurred_at")).atZone(ActivityDraft.WIB).toLocalDate().toString()
                    onOpen(HomeDestination(kind, LocalHistoryFilter(greenhouseId = record.getString("greenhouse_id"),
                        groupId = record.text("group_id"), type = type, from = date, until = date, recordState = "active"),
                        record.getString("id"), record.text("season_id")))
                }, onAllActivities = { onOpen(HomeDestination(OperationalKind.ACTIVITY, activities, seasonLabelId = periodId)) },
                    onAllHarvests = { onOpen(HomeDestination(OperationalKind.HARVEST, activities, seasonLabelId = periodId)) })
            }
            if (selection != null && section in setOf("summary", "finance")) item("cash") {
                val cash = snapshot.cash
                val filter = LocalHistoryFilter(greenhouseId = snapshot.selection, from = cash.getString("from"),
                    until = cash.getString("until"), recordState = "active")
                CashCard(snapshot, { onOpen(HomeDestination(OperationalKind.EXPENSE, filter)) },
                    { onOpen(HomeDestination(OperationalKind.SALE, filter)) })
            }
        }
    }

    }
    if (periodReview && snapshot?.selected != null) RunningPeriodSheet(snapshot, state, onPeriodReview, onAdmin, onOpen, seasonActions, onDesignate)
}

@Composable
private fun PeriodFacts(period: JSONObject, expanded: Boolean) {
    when (period.text("state")) {
        "ready" -> {
            Text(period.getString("variety"), style = MaterialTheme.typography.titleMedium)
            period.text("group_name")?.let { Caption("Kelompok $it") }
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
