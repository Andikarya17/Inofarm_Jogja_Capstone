package com.inofarm.jogja.feature.harvest

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.timeLabel
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.math.BigDecimal

private val harvestStatuses = listOf(
    "valid" to "Sudah dicek pengelola",
    "pending" to "Menunggu cek pengelola",
    "invalid" to "Perlu diperbaiki",
    "manager" to "Dicatat pengelola",
    "legacy" to "Data lama, belum dicek",
)

private val harvestQualities = listOf("saleable" to "Layak", "defective" to "Cacat", "unknown" to "Kondisi belum diketahui")

@Composable
internal fun WorkerHarvestProgress(
    session: FinanceSession, ticket: Long, context: CultivationUiState, repository: HarvestRepository,
    defaultGreenhouse: String?, defaultGroup: String?, syncRevision: Long,
    onClose: () -> Unit, onSource: (String, Long) -> Unit, sourceSelectionError: String?,
) {
    var greenhouse by rememberSaveable { mutableStateOf(defaultGreenhouse) }
    var group by rememberSaveable { mutableStateOf(defaultGroup) }
    var refresh by remember { mutableIntStateOf(0) }
    var showSources by rememberSaveable { mutableStateOf(false) }
    var sourceStatus by rememberSaveable { mutableStateOf("") }
    val gh = context.greenhouses.find { it.optString("id") == greenhouse }
        ?.takeUnless { context.check == ContextCheck.ACCESS_CHANGED }
    val seasonIds = context.seasons.filter { it.optString("greenhouse_id") == greenhouse }.map { it.optString("id") }.toSet()
    val groups = context.groups.filter { it.optString("season_id") in seasonIds }
    val groupId = groups.find { it.optString("id") == group }?.optString("id")
    val ghId = gh?.optString("id")
    LaunchedEffect(ghId, groups) {
        if (ghId != null && groupId == null) group = groups.singleOrNull()?.optString("id")
    }

    var progress by remember(ghId, groupId) { mutableStateOf<JSONObject?>(null) }
    var error by remember(ghId, groupId) { mutableStateOf<String?>(null) }
    var loading by remember(ghId, groupId) { mutableStateOf(false) }
    var savedOnly by remember(ghId, groupId) { mutableStateOf(false) }
    LaunchedEffect(ghId, groupId, syncRevision, refresh, ticket) {
        if (ghId == null || groupId == null) return@LaunchedEffect
        loading = true
        error = null
        try {
            if (progress == null) {
                progress = repository.cachedWorkerProgress(session, ticket, ghId, groupId)
                savedOnly = progress != null
            }
            progress = repository.workerProgress(session, ticket, ghId, groupId)
            savedOnly = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure is FinanceApiFailure && failure.status in setOf(401, 403, 404)) {
                progress = null
                showSources = false
            }
            savedOnly = progress != null
            error = failure.message ?: "Progres belum dapat dimuat."
        } finally {
            loading = false
        }
    }

    var rows by remember(groupId, sourceStatus, syncRevision, refresh) { mutableStateOf(emptyList<JSONObject>()) }
    var cursor by remember(groupId, sourceStatus, syncRevision, refresh) { mutableStateOf<String?>(null) }
    var page by remember(groupId, sourceStatus, syncRevision, refresh) { mutableIntStateOf(0) }
    var sourceLoading by remember { mutableStateOf(false) }
    var sourceError by remember(groupId, sourceStatus) { mutableStateOf<String?>(null) }
    LaunchedEffect(ghId, groupId, sourceStatus, showSources, syncRevision, refresh, page) {
        if (ghId == null || groupId == null || !showSources) return@LaunchedEffect
        sourceLoading = true
        sourceError = null
        try {
            val result = repository.progressSources(session, ticket, groupId, sourceStatus.takeIf(String::isNotBlank), cursor)
            val data = result.getJSONArray("data")
            rows = (rows + List(data.length(), data::getJSONObject)).distinctBy { it.getString("id") }
            cursor = result.optString("next_cursor").takeUnless { result.isNull("next_cursor") || it.isBlank() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure is FinanceApiFailure && failure.status in setOf(401, 403, 404)) rows = emptyList()
            sourceError = "Daftar sumber belum dapat diperbarui."
        } finally {
            sourceLoading = false
        }
    }

    FarmMenuList(Modifier.testTag("worker-harvest-progress")) {
        item {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Kembali") }
            FarmPageTitle("Progres panen")
        }
        item {
            Choice("Greenhouse", greenhouse, context.greenhouses, "worker-progress-gh", loading = context.loading) {
                greenhouse = it
                group = null
                showSources = false
            }
            if (ghId != null) {
                val choices = groups.map { row ->
                    val season = context.seasons.find { it.optString("id") == row.optString("season_id") }
                    JSONObject(row.toString()).put("choice_detail", season?.optString("name"))
                }
                Choice("Kelompok tanam", group, choices, "worker-progress-group", loading = context.loading) {
                    group = it
                    showSources = false
                }
                if (groups.isEmpty()) Text("Belum ada kelompok tanam di greenhouse ini.")
            } else Text("Pilih greenhouse Anda.")
        }
        if (loading) item { Text("Memeriksa progres panen…") }
        error?.let { message -> item {
            FarmAttention(message)
            TextButton(onClick = { refresh++ }) { Text("Periksa kembali") }
        } }
        progress?.let { data -> item("summary") {
            ProgressSummary(data, savedOnly, showSources) { showSources = !showSources }
        } }
        if (showSources && ghId != null && progress != null) {
            item {
                FarmSectionHeading("Revisi panen yang aktif")
                Text("Daftar mencerminkan revisi yang aktif saat dibaca.", style = MaterialTheme.typography.bodySmall)
                sourceSelectionError?.let { FarmAttention(it) }
                val statuses = listOf("" to "Semua status") + harvestStatuses
                Choice("Status sumber", sourceStatus, statuses.map { (id, label) ->
                    JSONObject().put("id", id).put("name", label)
                }, "worker-progress-source-status") { sourceStatus = it }
            }
            if (sourceLoading) item { Text("Memuat sumber…") }
            sourceError?.let { message -> item {
                FarmAttention(message)
                TextButton(onClick = { page++ }) { Text("Coba lagi") }
            } }
            if (rows.isEmpty() && !sourceLoading && sourceError == null) item { Text("Belum ada panen pada status ini.") }
            items(rows, key = { it.getString("id") }) { row ->
                val status = harvestStatuses.find { it.first == row.optString("verification_status") }?.second.orEmpty()
                FarmActionRow(timeLabel(row.getString("occurred_at")), "$status · revisi ${row.getLong("version")}", FarmIcon.Report,
                    Modifier.testTag("worker-progress-source-${row.getString("id")}")) {
                    onSource(row.getString("id"), row.getLong("version"))
                }
            }
            if (!sourceLoading && sourceError == null && cursor != null) item {
                OutlinedButton(onClick = { page++ }) { Text("Muat panen berikutnya") }
            }
        }
    }
}

@Composable
private fun ProgressSummary(data: JSONObject, saved: Boolean, showSources: Boolean, toggleSources: () -> Unit) {
    val target = data.optJSONObject("target")
    val totals = data.getJSONObject("totals")
    val saleable = totals.getJSONObject("saleable")
    val percent = data.optString("achievement_percent").toBigDecimalOrNull()
    val ratio by animateFloatAsState(((percent?.toFloat() ?: 0f) / 100f).coerceIn(0f, 1f), tween(240), label = "Capaian buah layak")
    var details by rememberSaveable(data.getString("group_id")) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        FarmSectionHeading(data.getString("group"))
        Text(listOfNotNull(data.optString("greenhouse"), data.optString("season"),
            data.optString("variety").takeUnless { data.isNull("variety") }).joinToString(" · "))
        if (data.optString("season_status") == "closed") Text("Musim ditutup")
        if (data.optBoolean("completed")) Text("Panen kelompok ditandai selesai")
        Text(if (saved) "Data tersimpan · ${timeLabel(data.getString("as_of"))}"
            else "Diperbarui ${timeLabel(data.getString("as_of"))}", style = MaterialTheme.typography.bodySmall)
        Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    FarmNavigationIcon(3)
                    Text("Hasil layak tercatat", style = MaterialTheme.typography.titleMedium)
                }
                Text(formatHarvestAmount(saleable), style = MaterialTheme.typography.headlineSmall)
                if (target == null) Text("Target belum ditetapkan.")
                else {
                    Text("Target layak ${target.getLong("fruit_count")} buah" +
                        if (target.isNull("weight_kg")) "" else " · ${harvestDecimal(target.getString("weight_kg"))} kg")
                    Text(target.getString("basis"), style = MaterialTheme.typography.bodySmall)
                    if (percent != null) {
                        Text("${harvestDecimal(percent.toPlainString())}% target buah" +
                            if (percent > BigDecimal("100")) " · target terlampaui" else "")
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .23f))
                    } else Text(if (data.optInt("records") == 0) "Belum ada panen tercatat."
                        else "Capaian belum lengkap karena kondisi sebagian buah belum diketahui.")
                    data.optString("weight_achievement_percent").toBigDecimalOrNull()?.let {
                        Text("${harvestDecimal(it.toPlainString())}% target bobot")
                    }
                }
            }
        }
        HorizontalDivider()
        FarmSectionHeading("Hasil menurut cek pengelola")
        Text("Tercatat mencakup semua revisi aktif yang sudah terkirim.", style = MaterialTheme.typography.bodySmall)
        harvestQualities.forEach { (key, label) -> FarmFact("Tercatat · $label", formatHarvestAmount(totals.getJSONObject(key))) }
        val verification = data.getJSONObject("verification")
        harvestStatuses.forEach { (status, label) ->
            FarmFact("$label · layak", formatHarvestAmount(verification.getJSONObject(status).getJSONObject("saleable")))
        }
        TextButton(onClick = { details = !details }, modifier = Modifier.heightIn(min = FarmSpace.Touch)) {
            Text(if (details) "Tutup rincian kondisi" else "Lihat rincian kondisi buah")
        }
        Column(Modifier.animateContentSize(tween(190)), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            if (details) harvestStatuses.forEach { (status, label) ->
                harvestQualities.drop(1).forEach { (key, quality) ->
                    FarmFact("$label · $quality", formatHarvestAmount(verification.getJSONObject(status).getJSONObject(key)))
                }
            }
        }
        OutlinedButton(onClick = toggleSources, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("worker-progress-sources")) {
            Text(if (showSources) "Tutup daftar sumber" else "Lihat panen sumber")
        }
    }
}

private fun formatHarvestAmount(value: JSONObject): String =
    "${value.getLong("fruit_count")} buah · ${harvestDecimal(value.getString("weight_kg"))} kg"
