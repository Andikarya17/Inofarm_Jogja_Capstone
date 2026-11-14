package com.inofarm.jogja.feature.context

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.activities.domain.ManualMeasurement
import com.inofarm.jogja.feature.harvest.varietyName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One scheduled job for today; [done] means a realization or a skipped record carries its schedule id. */
internal data class WorkerTask(val id: String, val title: String, val type: String, val plannedAt: String, val done: Boolean)

/** Everything one Beranda greenhouse card shows, read from the device cache. */
internal data class GreenhouseDay(
    val greenhouse: JSONObject, val active: Boolean, val hasGroup: Boolean, val groupName: String?, val variety: String?,
    val ageUnit: String?, val age: Long?, val tasks: List<WorkerTask>, val measurement: ManualMeasurement?,
    val checkedToday: Boolean, val ppm: PpmPosition, val unsent: Int, val unreadable: Boolean = false,
) {
    val id: String get() = greenhouse.getString("id")
    val name: String get() = greenhouse.optString("name")
    val tasksLeft: Int get() = tasks.count { !it.done }
}

internal data class WorkerHomeState(val days: List<GreenhouseDay>?, val failed: Boolean)

private val clockFormat = DateTimeFormatter.ofPattern("HH.mm")

/** The target for [parameter] that applies to [group] at [at], or null when the manager set none for that time. */
internal fun applicableTarget(targets: List<JSONObject>, group: String, parameter: String, at: Instant): JSONObject? = targets.firstOrNull { t ->
    t.optString("planting_group_id") == group && t.optBoolean("active", true) &&
        t.optString("parameter") == parameter && t.optString("activity_type") == ActivityType.MEASUREMENT.wire &&
        runCatching {
            !at.isBefore(OffsetDateTime.parse(t.getString("effective_from")).toInstant()) &&
                (t.isNull("effective_until") || at.isBefore(OffsetDateTime.parse(t.getString("effective_until")).toInstant()))
        }.getOrDefault(false)
}

private fun measurementPosition(targets: List<JSONObject>, group: String, m: ManualMeasurement): PpmPosition {
    val target = applicableTarget(targets, group, "manual_ppm", m.at) ?: return PpmPosition.UNKNOWN
    fun text(key: String) = target.takeUnless { it.isNull(key) }?.optString(key)
    return ppmPosition(text("minimum"), text("maximum"), text("target_value"), text("tolerance"), m.ppm)
}

private fun readDay(store: LocalFinanceStore, session: FinanceSession, data: CultivationUiState, greenhouse: JSONObject, today: LocalDate): GreenhouseDay {
    val scope = session.scope.key
    val id = greenhouse.getString("id")
    val active = greenhouse.optBoolean("active", true)
    // A greenhouse the device cache no longer lists stays on the card as unreadable instead of vanishing.
    if (id !in store.visibleGreenhouses(scope)) return GreenhouseDay(greenhouse, active, false, null, null, null, null, emptyList(), null, false, PpmPosition.UNKNOWN, 0, unreadable = true)
    val preferred = data.workerDefault(greenhouse, today)
    val group = data.groups.find { it.optString("id") == preferred.group }
    if (!active || group == null) return GreenhouseDay(greenhouse, active, false, null, null, null, null, emptyList(), null, false, PpmPosition.UNKNOWN, 0)
    val groupId = group.getString("id")
    val due = data.schedules.filter {
        it.optString("planting_group_id") == groupId && it.optBoolean("active", true) && dateWib(it.optString("planned_at")) == today
    }.sortedBy { it.optString("planned_at") }
    val filter = LocalHistoryFilter(groupId = groupId, greenhouseId = id, from = today.toString(), until = today.toString(), recordState = "active")
    val records = dayRecords(store, scope, "activity", filter)
    val realized = records.filter { it.syncState == SyncState.ACCEPTED || it.syncState == SyncState.QUEUED }
        .mapNotNull { row -> row.body.takeUnless { it.isNull("schedule_id") }?.optString("schedule_id")?.takeIf(String::isNotBlank) }.toSet()
    val tasks = due.map { schedule ->
        val type = schedule.optString("activity_type")
        WorkerTask(schedule.getString("id"),
            taskTitle(schedule.optString("notes"), ActivityType.entries.find { it.wire == type }?.label ?: "Kegiatan"),
            type, schedule.optString("planned_at"), schedule.getString("id") in realized)
    }
    val measurement = store.latestMeasurement(scope, id, groupId, today)
    val checkedToday = measurement != null && measurement.at.atZone(ActivityDraft.WIB).toLocalDate() == today
    val age = runCatching {
        PlantAge.calculate(group.getString("sowing_date"), if (group.isNull("transplant_date")) null else group.getString("transplant_date"),
            today.atStartOfDay(ActivityDraft.WIB).toOffsetDateTime().toString())
    }.getOrNull()
    val unit = if (age?.hst != null) "HST" else if (age?.hss != null) "HSS" else null
    return GreenhouseDay(greenhouse, true, true, group.optString("name"), varietyName(data, group), unit, age?.hst ?: age?.hss, tasks,
        measurement, checkedToday, if (measurement != null && checkedToday) measurementPosition(data.targets, groupId, measurement) else PpmPosition.UNKNOWN,
        records.count { it.syncState == SyncState.QUEUED })
}

/** One state for the whole Beranda: every assigned greenhouse, ordered by what needs doing first. */
@Composable
internal fun rememberWorkerHome(
    runtime: FinanceRuntime, session: FinanceSession, data: CultivationUiState, today: LocalDate, changed: Any?, syncUpdate: Long,
): WorkerHomeState {
    var days by remember(session.scope.key, today) { mutableStateOf<List<GreenhouseDay>?>(null) }
    var failed by remember(session.scope.key, today) { mutableStateOf(false) }
    // JSONObject has no equals, so key on ids and versions to avoid re-reading the cache on every reload.
    val stamp = (data.greenhouses + data.seasons + data.groups + data.schedules + data.targets).map {
        "${it.optString("id")}:${it.optLong("version")}:${it.optBoolean("active", true)}:${it.optString("running_group_id")}"
    }
    LaunchedEffect(session.scope.key, stamp, today, changed, syncUpdate) {
        if (data.check == ContextCheck.ACCESS_CHANGED) return@LaunchedEffect
        val generation = runtime.vault.generation
        try {
            val next = withContext(Dispatchers.IO) {
                orderForHome(data.greenhouses.map { readDay(runtime.store, session, data, it, today) }) {
                    HomeRank(it.active, it.hasGroup, it.tasksLeft, it.checkedToday, it.name)
                }
            }
            if (runtime.vault.matches(generation, session.scope)) {
                days = next
                failed = false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runtime.vault.matches(generation, session.scope)) failed = true
        }
    }
    return WorkerHomeState(days, failed)
}

private fun clock(instant: Instant) = instant.atZone(ActivityDraft.WIB).format(clockFormat)

/** Forest summary: how many of today's jobs are done across every greenhouse the worker can see. */
@Composable
internal fun WorkerSummaryCard(state: WorkerHomeState, data: CultivationUiState) {
    val active = state.days?.filter { it.active }.orEmpty()
    val total = active.sumOf { it.tasks.size }
    val done = active.sumOf { day -> day.tasks.count { it.done } }
    FarmHeroCard(Modifier.testTag("worker-home-summary").farmStaggerEntrance(0, "worker-summary")) {
        Text("Pekerjaan hari ini · ${active.size} greenhouse", Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.days == null -> Text(if (state.failed) "Data perangkat belum bisa dibaca. Perbarui untuk mencoba lagi." else "Memuat data perangkat…",
                style = MaterialTheme.typography.bodyLarge)
            total == 0 -> Text("Belum ada jadwal hari ini. Kegiatan tetap bisa dicatat lewat Catat.", style = MaterialTheme.typography.bodyLarge)
            else -> {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$done", style = FarmType.HeroNumber.copy(fontSize = 44.sp, lineHeight = 48.sp))
                    Text("dari $total tugas", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, FarmShape.Chip)
                    .semantics { contentDescription = "$done dari $total tugas selesai" }) {
                    Box(Modifier.fillMaxWidth(done.toFloat() / total).fillMaxHeight().background(FarmColors.Sage, FarmShape.Chip))
                }
            }
        }
        Text(data.lastSyncedAt?.let { "Diperbarui ${clock(Instant.ofEpochMilli(it))} WIB dari perangkat" } ?: "Belum ada data tersimpan.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The list of greenhouse cards, or the one honest message when there is nothing to list. */
@Composable
internal fun WorkerGreenhouseList(state: WorkerHomeState, data: CultivationUiState, onSelect: (JSONObject) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FarmSectionHeading("Greenhouse")
            if (!state.days.isNullOrEmpty()) Text("Urut dari yang belum selesai", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val days = state.days
        when {
            data.greenhouses.isEmpty() -> Text(
                if (data.check == ContextCheck.CHECKED) "Belum ada greenhouse dalam penugasan. Hubungi pengelola."
                else data.greenhouseAvailability() ?: "Memuat data perangkat…", style = MaterialTheme.typography.bodyMedium)
            days == null && state.failed -> Text("Data perangkat belum bisa dibaca. Perbarui untuk mencoba lagi.",
                style = MaterialTheme.typography.bodyMedium)
            else -> {
                // The device cache usually answers at once. Placeholders only appear when it does not, then hand over to the cards.
                var slow by remember { mutableStateOf(false) }
                LaunchedEffect(days == null) { if (days == null) { delay(FarmMotion.PlaceholderReveal); slow = true } }
                val stage = if (days != null) HomeListStage.Cards else if (slow) HomeListStage.Placeholder else HomeListStage.Waiting
                AnimatedContent(stage, transitionSpec = {
                    if (initialState == HomeListStage.Placeholder) fadeIn(tween(FarmMotion.State)) togetherWith fadeOut(tween(FarmMotion.State))
                    else EnterTransition.None togetherWith ExitTransition.None
                }, label = "Daftar greenhouse") { shown ->
                    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                        when (shown) {
                            HomeListStage.Waiting -> Unit
                            HomeListStage.Placeholder -> {
                                Text("Memuat data perangkat…", Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                repeat(data.greenhouses.size.coerceIn(1, 3)) { WorkerGreenhousePlaceholder() }
                            }
                            HomeListStage.Cards -> days.orEmpty().forEachIndexed { index, day ->
                                WorkerGreenhouseCard(day, index + 1) { onSelect(day.greenhouse) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class HomeListStage { Waiting, Placeholder, Cards }

/** A still outline of one greenhouse card, so the list does not jump when the real cards arrive. No shimmer. */
@Composable
private fun WorkerGreenhousePlaceholder() {
    val colors = MaterialTheme.colorScheme
    val line = with(LocalDensity.current) { 20.sp.toDp() }
    @Composable fun block(width: Float, height: androidx.compose.ui.unit.Dp = line, modifier: Modifier = Modifier) =
        Box(modifier.fillMaxWidth(width).height(height).background(colors.surfaceVariant, FarmShape.Chip))
    Surface(Modifier.fillMaxWidth().clearAndSetSemantics {}, shape = FarmShape.Card, color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
                block(0.5f)
                block(0.7f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Box(Modifier.weight(1f)) { block(1f, line + FarmSpace.Small) }
                Box(Modifier.weight(1f)) { block(0.7f, line + FarmSpace.Small) }
            }
            HorizontalDivider(color = colors.outlineVariant)
            block(0.6f)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkerGreenhouseCard(day: GreenhouseDay, index: Int, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val stacked = LocalDensity.current.fontScale > 1.3f
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            val title: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(day.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val detail = when {
                        !day.active -> "Greenhouse nonaktif. Belum bisa dicatat."
                        day.unreadable -> "Belum bisa dibaca dari perangkat"
                        day.hasGroup -> listOfNotNull(day.variety, day.groupName).joinToString(" · ")
                        else -> null
                    }
                    detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
                }
            }
            val age: @Composable () -> Unit = {
                if (day.age != null && day.ageUnit != null) Column(horizontalAlignment = if (stacked) Alignment.Start else Alignment.End) {
                    Text("${day.age}", style = FarmType.HeroNumber, color = colors.onSurface)
                    Text(day.ageUnit, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            if (stacked) {
                title(Modifier.fillMaxWidth())
                age()
            } else Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.Top) {
                title(Modifier.weight(1f))
                age()
            }
            if (day.active && !day.hasGroup && !day.unreadable) {
                val (container, content) = farmToneColors(FarmTone.Amber)
                Row(Modifier.fillMaxWidth().background(container, FarmShape.Tile).padding(FarmSpace.Medium),
                    horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small), verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalContentColor provides content) { FarmControlIcon(FarmIcon.AlertTriangle, null) }
                    Text("Tanam saat ini belum dipilih pengelola", style = MaterialTheme.typography.bodyMedium, color = content)
                }
            }
            if (day.active && day.hasGroup) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    if (day.checkedToday) FarmStatusChip("Nutrisi dicek ${clock(day.measurement!!.at)}", tone = FarmTone.Good)
                    else FarmStatusChip("Nutrisi belum dicek", tone = FarmTone.Amber)
                    if (day.tasks.isNotEmpty()) FarmStatusChip("${day.tasks.size - day.tasksLeft} dari ${day.tasks.size} tugas",
                        tone = if (day.tasksLeft == 0) FarmTone.Good else FarmTone.Neutral)
                    when (day.ppm) {
                        PpmPosition.BELOW -> FarmStatusChip("PPM di bawah acuan", tone = FarmTone.Amber)
                        PpmPosition.ABOVE -> FarmStatusChip("PPM di atas acuan", tone = FarmTone.Amber)
                        else -> Unit
                    }
                    if (day.unsent > 0) FarmStatusChip("${day.unsent} belum terkirim", tone = FarmTone.Amber)
                }
                HorizontalDivider(color = colors.outlineVariant)
                val next = day.tasks.firstOrNull { !it.done }
                Text(when {
                    next != null -> "Berikutnya · ${next.title} · ${daySession(runCatching {
                        OffsetDateTime.parse(next.plannedAt).atZoneSameInstant(ActivityDraft.WIB).hour }.getOrDefault(0))}"
                    day.tasks.isNotEmpty() -> "Semua tugas hari ini selesai"
                    else -> "Belum ada jadwal hari ini"
                }, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
    val modifier = Modifier.fillMaxWidth().testTag("worker-greenhouse-${day.id}").farmStaggerEntrance(index.coerceAtMost(6), "worker-gh-${day.id}")
    if (day.active) Surface(onClick = onOpen, modifier = modifier.farmPressable(interaction), shape = FarmShape.Card, color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant), interactionSource = interaction, content = body)
    else Surface(modifier = modifier, shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant), content = body)
}

/** Own records the manager sent back, across every greenhouse and group, read from the device cache. */
@Composable
internal fun rememberWorkerCorrections(runtime: FinanceRuntime, session: FinanceSession, changed: Any?, syncUpdate: Long): List<WorkerCorrection> {
    var rows by remember(session.scope.key) { mutableStateOf(emptyList<WorkerCorrection>()) }
    LaunchedEffect(session.scope.key, changed, syncUpdate) {
        val generation = runtime.vault.generation
        try {
            val next = withContext(Dispatchers.IO) { workerCorrectionItems(runtime.store, session) }
            if (runtime.vault.matches(generation, session.scope)) rows = next
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the last list: a failed read must not hide records that still need fixing.
        }
    }
    return rows
}

@Composable
internal fun WorkerCorrectionsCard(count: Int, onOpen: () -> Unit) {
    if (count == 0) return
    val (container, content) = farmToneColors(FarmTone.Error)
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("worker-corrections-card"), shape = FarmShape.Card,
        color = container, contentColor = content) {
        Row(Modifier.padding(FarmSpace.Large), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
            FarmControlIcon(FarmIcon.AlertTriangle, null)
            Column(Modifier.weight(1f)) {
                Text("$count catatan perlu diperbaiki", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Ada alasan dari pengelola", style = MaterialTheme.typography.bodyMedium)
            }
            FarmControlIcon(FarmIcon.ChevronRight, null)
        }
    }
}

private fun correctionSummary(row: LocalRecord): String {
    val body = row.serverBody ?: row.body
    if (row.kind == "harvest") return "Panen"
    val type = body.optString("activity_type")
    val name = ActivityType.entries.find { it.wire == type }?.label ?: "Kegiatan"
    if (type != ActivityType.MEASUREMENT.wire) return name
    fun value(key: String) = body.takeUnless { it.isNull(key) }?.optString(key)?.takeIf(String::isNotBlank)?.replace('.', ',')
    return listOfNotNull(name, value("manual_ec")?.let { "EC $it" }, value("manual_ppm")?.let { "PPM $it" }).joinToString(" · ")
}

@Composable
internal fun WorkerCorrectionsScreen(rows: List<WorkerCorrection>, data: CultivationUiState, onOpen: (LocalRecord) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (reasonContainer, reasonContent) = farmToneColors(FarmTone.Error)
    FarmMenuList(Modifier.testTag("worker-corrections-list")) {
        item("corrections-intro") {
            Text("Pengelola menandai catatan ini. Baca alasannya, lalu koreksi. Koreksi akan dicek ulang.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (rows.isEmpty()) item("corrections-empty") {
            Text("Tidak ada catatan yang perlu diperbaiki.", style = MaterialTheme.typography.titleMedium)
        }
        items(rows, key = { it.row.id }) { item ->
            val row = item.row
            val body = row.serverBody ?: row.body
            val greenhouse = data.greenhouses.find { it.optString("id") == row.greenhouseId }?.optString("name")
            val group = data.groups.find { it.optString("id") == body.optString("planting_group_id") }?.optString("name")
            val day = dateWib(body.optString("occurred_at"))?.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID")))
            Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
                Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.Top) {
                        Text(correctionSummary(row), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        day?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    }
                    Text(listOfNotNull(greenhouse, group).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (item.reason.isNotBlank()) Text(item.reason, Modifier.fillMaxWidth().background(reasonContainer, FarmShape.Tile).padding(FarmSpace.Medium),
                        style = MaterialTheme.typography.bodyMedium, color = reasonContent)
                    Button(onClick = { onOpen(row) }, shape = FarmShape.Control,
                        modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("worker-correction-open-${row.id}")) {
                        Text("Buka dan koreksi")
                    }
                }
            }
        }
    }
}
