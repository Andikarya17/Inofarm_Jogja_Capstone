package com.inofarm.jogja.feature.context

import androidx.compose.foundation.layout.*
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
    FarmPanel(title = "Pekerjaan hari ini") {
        Text("Jadwal kelompok · catatan akun ini · WIB", style = MaterialTheme.typography.bodySmall)
        val day = snapshot
        if (failed) Text("Catatan tersimpan gagal dibaca kembali.")
        if (day == null) {
            if (!failed) Text("Memeriksa catatan tersimpan…")
            return@FarmPanel
        }
        FarmSectionHeading("Jadwal kelompok")
        val due = schedules.filter {
            it.optString("planting_group_id") == group && it.optBoolean("active", true) &&
                dateWib(it.optString("planned_at")) == today
        }.sortedBy { it.optString("planned_at") }
        if (due.isEmpty()) Text("Belum ada jadwal hari ini. Kegiatan spontan tetap dapat dicatat.")
        due.forEach { schedule ->
            val id = schedule.getString("id")
            val type = ActivityType.entries.find { it.wire == schedule.optString("activity_type") }
            val accepted = day.synchronizedActivities.filter { it.optString("schedule_id") == id }
            val completed = accepted.count { it.optString("work_state") == "completed" }
            val skipped = accepted.count { it.optString("work_state") == "skipped" }
            val status = listOfNotNull("Ada $completed catatan hasil terkirim".takeIf { completed > 0 },
                "$skipped dilewati dengan alasan".takeIf { skipped > 0 }).joinToString(" · ")
                .ifBlank { "Belum ada catatan terkirim hari ini" }
            FarmActionRow(type?.let { "Catat hasil · ${it.label}" } ?: "Kegiatan", "${timeLabel(schedule.optString("planned_at"))}\n$status",
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
            FarmActionRow("Catatan tertahan", "${day.held} catatan saya di greenhouse ini", FarmIcon.Schedule, onClick = onQueue)
        }
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
