package com.inofarm.jogja.feature.context

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.activities.domain.ManualMeasurement
import com.inofarm.jogja.feature.timeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

private data class GreenhouseSummary(val hasGroup: Boolean, val needsGroup: Boolean, val due: Int, val recorded: Int, val measurement: ManualMeasurement?)

private fun readSummary(
    store: LocalFinanceStore, session: FinanceSession, data: CultivationUiState, greenhouse: JSONObject, today: LocalDate,
): GreenhouseSummary? {
    val scope = session.scope.key
    val id = greenhouse.getString("id")
    if (id !in store.visibleGreenhouses(scope)) return null
    val preferred = data.workerDefault(greenhouse, today)
    val group = preferred.group ?: return GreenhouseSummary(false, preferred.season != null ||
        data.seasons.any { it.optString("greenhouse_id") == id && it.optString("status") == "open" }, 0, 0, null)
    val due = data.schedules.filter {
        it.optString("planting_group_id") == group && it.optBoolean("active", true) &&
            dateWib(it.optString("planned_at")) == today
    }.map { it.getString("id") }.toSet()
    val filter = LocalHistoryFilter(groupId = group, greenhouseId = id, from = today.toString(), until = today.toString(), recordState = "active")
    val recorded = dayRecords(store, scope, "activity", filter)
        .filter { it.syncState == SyncState.ACCEPTED || it.syncState == SyncState.QUEUED }
        .mapNotNull { row -> row.body.takeUnless { it.isNull("schedule_id") }?.optString("schedule_id")?.takeIf(String::isNotBlank) }
        .toSet().count { it in due }
    return GreenhouseSummary(true, false, due.size, recorded, store.latestMeasurement(scope, id, group, today))
}

/** One card per assigned greenhouse: today's schedule progress and the last measurement, read from the device cache. */
@Composable
internal fun WorkerGreenhouseCards(
    runtime: FinanceRuntime, session: FinanceSession, data: CultivationUiState, today: LocalDate,
    changed: Any?, syncUpdate: Long, onSelect: (JSONObject) -> Unit,
) {
    if (data.greenhouses.isEmpty() || data.check == ContextCheck.ACCESS_CHANGED) return
    var summaries by remember(session.scope.key, today) { mutableStateOf<Map<String, GreenhouseSummary?>?>(null) }
    var failed by remember(session.scope.key, today) { mutableStateOf(false) }
    // JSONObject has no equals, so key on ids and versions to avoid re-reading the cache on every reload.
    val stamp = (data.greenhouses + data.seasons + data.groups + data.schedules).map {
        "${it.optString("id")}:${it.optLong("version")}:${it.optBoolean("active", true)}:${it.optString("running_group_id")}"
    }
    LaunchedEffect(session.scope.key, stamp, today, changed, syncUpdate) {
        val generation = runtime.vault.generation
        try {
            val next = withContext(Dispatchers.IO) {
                data.greenhouses.filter { it.optBoolean("active", true) }
                    .associate { it.getString("id") to readSummary(runtime.store, session, data, it, today) }
            }
            if (runtime.vault.matches(generation, session.scope)) {
                summaries = next
                failed = false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runtime.vault.matches(generation, session.scope)) failed = true
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        FarmSectionHeading("Greenhouse Anda")
        Text(data.lastSyncedAt?.let { "Diperbarui ${timeLabel(java.time.Instant.ofEpochMilli(it).toString())}" } ?: "Belum ada data tersimpan.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        data.greenhouses.forEach { greenhouse ->
            val id = greenhouse.getString("id")
            val active = greenhouse.optBoolean("active", true)
            val preferred = data.workerDefault(greenhouse, today)
            val season = data.seasons.find { it.optString("id") == preferred.season }
            val group = data.groups.find { it.optString("id") == preferred.group }
            val detail = when {
                !active -> "Greenhouse nonaktif. Belum bisa dicatat."
                group != null -> listOfNotNull(season?.optString("name"), group.optString("name"),
                    workerAge(group, today)).joinToString(" · ")
                season != null -> "${season.optString("name")} · Pilih kelompok tanam"
                data.seasons.any { it.optString("greenhouse_id") == id && it.optString("status") == "open" } ->
                    "Pilih musim dan kelompok tanam"
                else -> "Belum ada musim terbuka"
            }
            FarmRecordCard(Modifier.testTag("worker-greenhouse-$id"), onOpen = if (active) ({ onSelect(greenhouse) }) else null) {
                Text(greenhouse.getString("name"), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (active) {
                    val summary = summaries?.get(id)
                    val ready = summaries != null
                    val unreadable = failed || ready && summary == null
                    FarmFact("Jadwal hari ini", when {
                        unreadable -> "Belum bisa dibaca dari perangkat"
                        !ready || summary == null -> "Memeriksa…"
                        summary.needsGroup -> "Pilih kelompok tanam untuk melihat jadwal"
                        !summary.hasGroup -> "Tidak ada kelompok tanam yang berjalan"
                        summary.due == 0 -> "Belum ada jadwal hari ini"
                        else -> "${summary.recorded} dari ${summary.due} jadwal tercatat"
                    })
                    FarmFact("Pengukuran terakhir", when {
                        unreadable -> "Belum bisa dibaca dari perangkat"
                        !ready || summary == null -> "Memeriksa…"
                        summary.needsGroup -> "Pilih kelompok tanam untuk melihat pengukuran"
                        !summary.hasGroup -> "Tidak ada kelompok tanam yang berjalan"
                        summary.measurement == null -> "Belum ada pengukuran"
                        else -> measurementText(summary.measurement)
                    })
                }
            }
        }
    }
}

private fun measurementText(m: ManualMeasurement): String {
    val ec = m.ec?.replace('.', ',')?.let { "EC $it mS/cm" } ?: "EC belum tersedia"
    val status = when {
        m.queued -> "\nBelum terkirim"
        m.verification == "pending" -> "\nBelum dicek"
        else -> ""
    }
    return "$ec, PPM ${m.ppm.replace('.', ',')} ppm\n${timeLabel(m.at.toString())}$status"
}

/** Own records the manager sent back, across every greenhouse and group, read from the device cache. */
@Composable
internal fun rememberWorkerCorrections(runtime: FinanceRuntime, session: FinanceSession, changed: Any?, syncUpdate: Long): List<LocalRecord> {
    var rows by remember(session.scope.key) { mutableStateOf(emptyList<LocalRecord>()) }
    LaunchedEffect(session.scope.key, changed, syncUpdate) {
        val generation = runtime.vault.generation
        try {
            val next = withContext(Dispatchers.IO) { workerCorrections(runtime.store, session) }
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
    FarmRecordCard(Modifier.testTag("worker-corrections-card"), onOpen = onOpen) {
        Text("$count catatan perlu diperbaiki", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text("Pengelola meminta perbaikan. Buka daftarnya.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun WorkerCorrectionsScreen(rows: List<LocalRecord>, data: CultivationUiState, onOpen: (LocalRecord) -> Unit, onClose: () -> Unit) {
    FarmMenuList(Modifier.testTag("worker-corrections-list")) {
        item("corrections-header") {
            FarmMenuHeader("Perlu diperbaiki", "Catatan yang diminta pengelola untuk diperbaiki, di semua greenhouse.") {
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("worker-corrections-close")) {
                    Text("Kembali ke Beranda")
                }
            }
        }
        if (rows.isEmpty()) item("corrections-empty") {
            FarmPanel(title = "Tidak ada catatan yang perlu diperbaiki.") {
                Text("Catatan yang diminta diperbaiki pengelola muncul di sini.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(rows, key = { it.id }) { row ->
            val greenhouse = data.greenhouses.find { it.optString("id") == row.greenhouseId }?.optString("name")
            WorkerNoteRow(row, listOfNotNull(greenhouse, "Lihat alasan pengelola").joinToString(" · "), onOpen)
        }
    }
}
