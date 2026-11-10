package com.inofarm.jogja.feature.context

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.label
import com.inofarm.jogja.feature.recorderId
import com.inofarm.jogja.feature.timeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime

private data class WorkerDay(
    val activities: List<LocalRecord>,
    val notes: List<LocalRecord>,
    val corrections: List<LocalRecord>,
    val held: Int,
)

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
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val store = runtime.store
                val scope = session.scope.key
                if (greenhouse !in store.visibleGreenhouses(scope)) return@runCatching null
                val filter = LocalHistoryFilter(groupId = group, greenhouseId = greenhouse,
                    from = today.toString(), until = today.toString(), recordState = "active")
                fun records(kind: String) = buildList {
                    do {
                        val page = store.records(scope, kind = kind, filter = filter, limit = 100, offset = size)
                        addAll(page)
                    } while (page.size == 100)
                }
                val activity = records("activity")
                val notes = (activity + records("harvest")).filter {
                    it.recorderId() == session.scope.accountId ||
                        (it.serverVersion == null && it.syncState in setOf(SyncState.LOCAL, SyncState.QUEUED, SyncState.REJECTED))
                }.sortedByDescending { it.body.optString("occurred_at") }
                val corrections = store.cachedAll(scope, "activity_review", greenhouse).filter { it.optString("decision") == "invalid" }
                        .mapNotNull { review ->
                            store.record(scope, review.optString("activity_id"))?.takeIf {
                                val accepted = it.serverBody ?: return@takeIf false
                                it.recorderId() == session.scope.accountId && accepted.optString("record_state", "active") == "active" &&
                                    accepted.optString("planting_group_id") == group &&
                                    it.serverVersion == review.optLong("activity_version")
                            }
                        }
                val held = store.outbox(scope).count { operation ->
                    operation.state in setOf("REJECTED", "CONFLICT", "AUTH_REQUIRED", "SCOPE_REVOKED", "EPOCH_REVIEW") &&
                        store.record(scope, operation.entityId)?.greenhouseId == greenhouse
                }
                WorkerDay(activity, notes, corrections.distinctBy { it.id }, held)
            }
        }
        if (runtime.vault.matches(generation, session.scope)) {
            snapshot = result.getOrNull()
            failed = result.isFailure
        }
    }
    FarmPanel(title = "Pekerjaan hari ini") {
        Text("Jadwal kelompok · catatan akun ini · WIB", style = MaterialTheme.typography.bodySmall)
        val day = snapshot
        if (day == null) {
            Text(if (failed) "Catatan tersimpan gagal dibaca." else "Memeriksa catatan tersimpan…")
            return@FarmPanel
        }
        FarmSectionHeading("Jadwal kelompok")
        val due = schedules.filter {
            it.optString("planting_group_id") == group && it.optBoolean("active", true) &&
                runCatching { OffsetDateTime.parse(it.getString("planned_at")).atZoneSameInstant(ActivityDraft.WIB).toLocalDate() }.getOrNull() == today
        }.sortedBy { it.optString("planned_at") }
        if (due.isEmpty()) Text("Belum ada jadwal hari ini. Kegiatan spontan tetap dapat dicatat.")
        due.forEach { schedule ->
            val id = schedule.getString("id")
            val type = ActivityType.entries.find { it.wire == schedule.optString("activity_type") }
            val accepted = day.activities.filter { row ->
                row.serverBody?.optString("schedule_id") == id && row.serverBody.optString("record_state", "active") == "active"
            }
            val completed = accepted.count { it.serverBody?.optString("work_state") == "completed" }
            val skipped = accepted.count { it.serverBody?.optString("work_state") == "skipped" }
            val status = listOfNotNull("Ada $completed realisasi tersinkron".takeIf { completed > 0 },
                "$skipped dilewati dengan alasan".takeIf { skipped > 0 }).joinToString(" · ")
                .ifBlank { "Belum ada catatan tersinkron hari ini" }
            FarmActionRow(type?.label ?: "Kegiatan", "${timeLabel(schedule.optString("planned_at"))}\n$status",
                FarmIcon.Schedule, Modifier.testTag("worker-schedule-$id"), enabled = canRecord && type != null) {
                type?.let { onSchedule(it, id) }
            }
            schedule.optString("notes").takeIf(String::isNotBlank)?.let {
                Text(it, Modifier.padding(horizontal = FarmSpace.Medium), style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()
        FarmSectionHeading("Catatan saya")
        if (day.notes.isEmpty()) Text("Belum ada catatan akun ini untuk hari ini di perangkat.")
        day.notes.forEach { row -> WorkerNoteRow(row, row.syncState.label(), onRecord) }
        if (day.corrections.isNotEmpty()) {
            HorizontalDivider()
            FarmSectionHeading("Perlu diperbaiki")
            day.corrections.forEach { row -> WorkerNoteRow(row, "Lihat alasan pengelola", onRecord) }
        }
        if (day.held > 0) {
            HorizontalDivider()
            FarmActionRow("Kiriman tertahan", "${day.held} kiriman saya di greenhouse ini", FarmIcon.Schedule, onClick = onQueue)
        }
    }
}

@Composable
private fun WorkerNoteRow(row: LocalRecord, status: String, onRecord: (LocalRecord) -> Unit) {
    val draft = row.body.optJSONObject("_local_form")
    val name = if (row.kind == "harvest") "Panen" else ActivityType.entries.find {
        it.wire == (draft?.optString("type") ?: row.body.optString("activity_type"))
    }?.label ?: "Kegiatan"
    val time = row.body.optString("occurred_at").takeIf(String::isNotBlank)?.let(::timeLabel)
        ?: draft?.optString("time")?.let { "$it WIB" }
    FarmActionRow(name, listOfNotNull(time, status).joinToString(" · "), FarmIcon.Report) { onRecord(row) }
}
