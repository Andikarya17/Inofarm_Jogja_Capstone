package com.inofarm.jogja.feature.context

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.cultivation.ManualTime
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.label
import com.inofarm.jogja.feature.recorderId
import com.inofarm.jogja.feature.timeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime

private data class WorkerDay(
    val synchronizedActivities: List<JSONObject>,
    val notes: List<LocalRecord>,
    val corrections: List<LocalRecord>,
    val held: Int,
)

internal fun dayRecords(store: LocalFinanceStore, scope: String, kind: String, filter: LocalHistoryFilter): List<LocalRecord> {
    val records = mutableListOf<LocalRecord>()
    do {
        val page = store.records(scope, kind = kind, filter = filter, limit = 100, offset = records.size)
        records.addAll(page)
    } while (page.size == 100)
    return records
}

private fun readWorkerDay(
    store: LocalFinanceStore, session: FinanceSession, greenhouse: String, group: String, today: LocalDate,
): WorkerDay? {
    val scope = session.scope.key
    if (greenhouse !in store.visibleGreenhouses(scope)) return null
    val filter = LocalHistoryFilter(groupId = group, greenhouseId = greenhouse,
        from = today.toString(), until = today.toString(), recordState = "active")
    val activity = dayRecords(store, scope, "activity", filter)
    val notes = (activity + dayRecords(store, scope, "harvest", filter)).filter {
        it.recorderId() == session.scope.accountId ||
            (it.serverVersion == null && it.syncState in setOf(SyncState.LOCAL, SyncState.QUEUED, SyncState.REJECTED))
    }.sortedByDescending { row ->
        runCatching {
            val draft = row.body.optJSONObject("_local_form")
            val at = if (draft == null) row.body.getString("occurred_at")
                else ManualTime.encode(draft.getString("date"), draft.getString("time"))
            OffsetDateTime.parse(at).toInstant()
        }.getOrNull()
    }
    val synchronizedActivities = store.cachedAll(scope, "activity", greenhouse).filter {
        it.optString("planting_group_id") == group && it.optString("record_state", "active") == "active" &&
            dateWib(it.optString("occurred_at")) == today
    }
    val corrections = workerCorrections(store, session, greenhouse, group)
    val held = store.outbox(scope).count { operation ->
        operation.state in setOf("REJECTED", "CONFLICT", "AUTH_REQUIRED", "SCOPE_REVOKED", "EPOCH_REVIEW") &&
            store.record(scope, operation.entityId)?.greenhouseId == greenhouse
    }
    return WorkerDay(synchronizedActivities, notes, corrections, held)
}

/** An own record the manager marked "Perlu diperbaiki", with the reviewer's reason for it. */
internal data class WorkerCorrection(val row: LocalRecord, val reason: String)

/** Own accepted records whose current revision the manager marked "Perlu diperbaiki", optionally limited to one greenhouse or group. */
internal fun workerCorrections(store: LocalFinanceStore, session: FinanceSession, greenhouse: String? = null, group: String? = null): List<LocalRecord> =
    workerCorrectionItems(store, session, greenhouse, group).map { it.row }

internal fun workerCorrectionItems(store: LocalFinanceStore, session: FinanceSession, greenhouse: String? = null, group: String? = null): List<WorkerCorrection> {
    val scope = session.scope.key
    val visible = store.visibleGreenhouses(scope)
    return listOf("activity", "harvest").flatMap { kind ->
        store.cachedAll(scope, "${kind}_review", greenhouse).filter { it.optString("decision") == "invalid" }
            .mapNotNull { review ->
                val row = store.record(scope, review.optString("${kind}_id")) ?: return@mapNotNull null
                val accepted = row.serverBody ?: return@mapNotNull null
                row.takeIf {
                    it.greenhouseId in visible && it.recorderId() == session.scope.accountId && accepted.optString("record_state", "active") == "active" &&
                        (group == null || accepted.optString("planting_group_id") == group) &&
                        it.serverVersion == review.optLong("${kind}_version")
                }?.let { WorkerCorrection(it, review.optString("reason")) }
            }
    }.distinctBy { it.row.id }
}

@Composable
internal fun WorkerDayAgenda(
    runtime: FinanceRuntime, session: FinanceSession, greenhouse: String, group: String,
    today: LocalDate, schedules: List<JSONObject>, changed: Any?, syncUpdate: Long, canRecord: Boolean,
    onSchedule: (ActivityType, String) -> Unit, onRecord: (LocalRecord) -> Unit, onQueue: () -> Unit,
) {
    var snapshot by remember(session.scope.key, greenhouse, group, today) { mutableStateOf<WorkerDay?>(null) }
    var failed by remember(session.scope.key, greenhouse, group, today) { mutableStateOf(false) }
    LaunchedEffect(session.scope.key, greenhouse, group, today, changed, syncUpdate) {
        val generation = runtime.vault.generation
        try {
            val next = withContext(Dispatchers.IO) { readWorkerDay(runtime.store, session, greenhouse, group, today) }
            if (runtime.vault.matches(generation, session.scope)) {
                snapshot = next
                failed = false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runtime.vault.matches(generation, session.scope)) failed = true
        }
    }
    val day = snapshot
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        if (failed) Text("Catatan tersimpan gagal dibaca kembali.")
        if (day == null) {
            if (!failed) Text("Memeriksa catatan tersimpan…")
            return@Column
        }
        val due = schedules.filter {
            it.optString("planting_group_id") == group && it.optBoolean("active", true) &&
                dateWib(it.optString("planned_at")) == today
        }.sortedBy { it.optString("planned_at") }
        TodayScheduleCard(due, day, canRecord, onSchedule)
        FarmPanel(title = "Catatan saya") {
            if (day.notes.isEmpty()) Text("Belum ada catatan akun ini untuk hari ini di perangkat.")
            day.notes.forEach { row -> WorkerNoteRow(row, row.syncState.label(), onRecord) }
            if (day.corrections.isNotEmpty()) {
                HorizontalDivider()
                FarmSectionHeading("Perlu diperbaiki")
                day.corrections.forEach { row -> WorkerNoteRow(row, "Lihat alasan pengelola", onRecord) }
            }
            if (day.held > 0) {
                HorizontalDivider()
                FarmActionRow("Catatan tertahan", "${day.held} catatan saya di greenhouse ini", FarmIcon.Schedule, onClick = onQueue)
            }
        }
    }
}

private enum class TaskState { DONE, SKIPPED, PENDING }

private class TaskLine(val schedule: JSONObject, val type: ActivityType?, val title: String, val state: TaskState, val detail: String)

@Composable
private fun TodayScheduleCard(due: List<JSONObject>, day: WorkerDay, canRecord: Boolean, onSchedule: (ActivityType, String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val clock = java.time.format.DateTimeFormatter.ofPattern("HH.mm")
    val rows = due.map { schedule ->
        val id = schedule.getString("id")
        val type = ActivityType.entries.find { it.wire == schedule.optString("activity_type") }
        val typeLabel = type?.label ?: "Kegiatan"
        val title = taskTitle(schedule.optString("notes"), typeLabel)
        val accepted = day.synchronizedActivities.filter { it.optString("schedule_id") == id }
        val completed = accepted.filter { it.optString("work_state") == "completed" }
        val skipped = accepted.filter { it.optString("work_state") == "skipped" }
        val unsent = day.notes.filter { it.kind == "activity" && it.body.optString("schedule_id") == id && it.syncState == SyncState.QUEUED }
        val at = completed.maxOfOrNull { it.optString("occurred_at") }?.let {
            runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ActivityDraft.WIB).format(clock) }.getOrNull()
        }
        when {
            completed.isNotEmpty() || unsent.isNotEmpty() -> TaskLine(schedule, type, title, TaskState.DONE, listOfNotNull(typeLabel,
                if (completed.isNotEmpty()) "selesai ${at ?: ""}".trim() else "belum terkirim",
                "${skipped.size} dilewati".takeIf { skipped.isNotEmpty() }).joinToString(" · "))
            skipped.isNotEmpty() -> TaskLine(schedule, type, title, TaskState.SKIPPED,
                "Dilewati · ${skipped.first().optString("skipped_reason").ifBlank { "tanpa alasan tertulis" }}")
            else -> TaskLine(schedule, type, title, TaskState.PENDING, "$typeLabel · " + daySession(runCatching {
                OffsetDateTime.parse(schedule.optString("planned_at")).atZoneSameInstant(ActivityDraft.WIB).hour }.getOrDefault(0)).lowercase())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            FarmSectionHeading("Jadwal hari ini")
            if (rows.isNotEmpty()) Text("${rows.count { it.state != TaskState.PENDING }} dari ${rows.size} selesai",
                style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        if (rows.isEmpty()) Text("Belum ada jadwal hari ini. Kegiatan spontan tetap dapat dicatat.", style = MaterialTheme.typography.bodyMedium)
        else Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)) {
            Column {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = FarmSpace.Large), color = colors.outlineVariant)
                    val id = row.schedule.getString("id")
                    val enabled = canRecord && row.type != null
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("worker-schedule-$id")
                            .clickable(enabled = enabled, onClickLabel = "Catat ${row.title}", role = androidx.compose.ui.semantics.Role.Button) {
                                row.type?.let { onSchedule(it, id) }
                            }.padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Small),
                        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically
                    ) {
                        TaskMark(row.state)
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        if (row.state == TaskState.PENDING && enabled) Text("Catat",
                            Modifier.heightIn(min = 44.dp).wrapContentHeight(Alignment.CenterVertically)
                                .background(colors.secondaryContainer, FarmShape.Control).padding(horizontal = FarmSpace.Large),
                            style = MaterialTheme.typography.labelLarge, color = colors.onSecondaryContainer)
                    }
                }
            }
        }
        Text("Catat dari jadwal membuka form dengan jenis terisi.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun TaskMark(state: TaskState) {
    val colors = MaterialTheme.colorScheme
    val (good, goodContent) = farmToneColors(FarmTone.Good)
    val amber = farmToneColors(FarmTone.Amber).second
    val mark = Modifier.size(28.dp)
    when (state) {
        TaskState.DONE -> Box(mark.background(good, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides goodContent) { FarmControlIcon(FarmIcon.Check, "Selesai") }
        }
        TaskState.SKIPPED -> Box(mark.border(2.dp, amber, androidx.compose.foundation.shape.CircleShape).semantics { contentDescription = "Dilewati" })
        TaskState.PENDING -> Box(mark.border(2.dp, colors.outline, androidx.compose.foundation.shape.CircleShape).semantics { contentDescription = "Belum dicatat" })
    }
}

internal fun dateWib(timestamp: String): LocalDate? =
    runCatching { OffsetDateTime.parse(timestamp).atZoneSameInstant(ActivityDraft.WIB).toLocalDate() }.getOrNull()

@Composable
internal fun WorkerNoteRow(row: LocalRecord, status: String, onRecord: (LocalRecord) -> Unit) {
    val draft = row.body.optJSONObject("_local_form")
    val name = if (row.kind == "harvest") "Panen" else ActivityType.entries.find {
        it.wire == (draft?.optString("type") ?: row.body.optString("activity_type"))
    }?.label ?: "Kegiatan"
    val time = row.body.optString("occurred_at").takeIf(String::isNotBlank)?.let(::timeLabel)
        ?: draft?.optString("time")?.let { "$it WIB" }
    FarmActionRow(name, listOfNotNull(time, status).joinToString(" · "), FarmIcon.Report) { onRecord(row) }
}
