package com.inofarm.jogja.feature.harvest

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.history.ReasonDialog
import com.inofarm.jogja.feature.timeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun HarvestProgressScreen(
    session: FinanceSession,
    ticket: Long,
    context: CultivationUiState,
    repository: HarvestRepository,
    defaultGreenhouse: String?,
    defaultGroup: String?,
    syncRevision: Long,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onOutbox: () -> Unit,
) {
    var greenhouse by rememberSaveable { mutableStateOf(defaultGreenhouse) }
    var group by rememberSaveable { mutableStateOf(defaultGroup) }
    val seasonById = context.seasons.associateBy { it.optString("id") }
    val groups = context.groups.filter { seasonById[it.optString("season_id")]?.optString("greenhouse_id") == greenhouse }
    LaunchedEffect(greenhouse, groups, context.check) {
        if (group == null) group = groups.singleOrNull()?.optString("id")
    }
    FarmMenuList(Modifier.testTag("harvest-progress")) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Kembali ke Panen") }
            FarmPageTitle("Rekap panen")
        }
        item {
            Choice("Greenhouse", greenhouse, context.greenhouses, "harvest-progress-gh", loading = context.loading) {
                if (greenhouse != it) { greenhouse = it; group = null }
            }
        }
        if (greenhouse != null) item {
            val choices = groups.map { row ->
                val details = listOfNotNull(seasonById[row.optString("season_id")]?.optString("name"), varietyName(context, row))
                JSONObject(row.toString()).put("choice_detail", details.joinToString(" · "))
            }
            Choice("Periode dan kelompok tanam", group, choices, "harvest-progress-group", loading = context.loading) { group = it }
            if (groups.isEmpty() && !context.loading) Text("Kelompok tanam belum tersedia untuk greenhouse ini.")
        }
        val groupId = group
        val gh = greenhouse
        if (groupId != null && gh != null) item("progress-$groupId") {
            Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Large)) {
                HarvestProgressDetail(session, ticket, repository, groupId, gh, syncRevision, onSync, onOutbox)
            }
        }
    }
}

@Composable
private fun HarvestProgressDetail(
    session: FinanceSession,
    ticket: Long,
    repository: HarvestRepository,
    group: String,
    greenhouse: String,
    syncRevision: Long,
    onSync: () -> Unit,
    onOutbox: () -> Unit,
) {
    var data by remember(group) { mutableStateOf<JSONObject?>(null) }
    var transition by remember(group) { mutableStateOf<LocalRecord?>(null) }
    var loading by remember(group) { mutableStateOf(true) }
    var error by remember(group) { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableIntStateOf(0) }
    var decision by rememberSaveable(group) { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(group, greenhouse, syncRevision, refreshing, ticket) {
        loading = true
        error = null
        try {
            transition = repository.transitionState(session, ticket, group)
            val next = repository.progress(session, ticket, group)
            check(next.getString("greenhouse_id") == greenhouse) { "Pilihan rekap berubah." }
            data = next
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (failure is FinanceApiFailure && failure.status in setOf(401, 403, 404)) {
                data = null
                transition = null
            }
            error = failure.message ?: "Rekap belum tersedia."
        } finally {
            loading = false
        }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let {
        FarmAttention(if (data == null) it else "Menampilkan rekap tersimpan. $it")
        TextButton(onClick = { refreshing++ }) { Text("Coba lagi") }
    }
    val progress = data ?: return
    val completion = progress.optJSONObject("completion")
    val target = progress.optJSONObject("target")
    FarmPanel(title = progress.getString("group")) {
        Text("${progress.getString("greenhouse")} · ${progress.getString("season")}")
        FarmFact("Varietas", progress.optJSONObject("variety")?.optString("name") ?: "Belum tersedia")
        FarmFact("Populasi", "${progress.getLong("population")} tanaman")
        FarmFact("Tanggal semai", progress.getString("sowing_date"))
        FarmFact("Status", if (completion == null) "Panen bertahap" else "Panen selesai")
        completion?.let {
            Text("${it.getString("by_name")} · ${timeLabel(it.getString("at"))}")
            if (!it.isNull("reason")) Text(it.optString("reason"))
        }
    }
    FarmSection(title = "Target dan hasil layak") {
        if (target == null) Text("Target buah belum ditetapkan untuk kelompok ini.")
        else {
            FarmFact("Target buah", "${target.getInt("fruit_count")} buah")
            if (!target.isNull("weight_kg")) FarmFact("Target bobot", "${harvestDecimal(target.getString("weight_kg"))} kg")
            Text(target.getString("basis"))
            Text("Versi target ${target.getInt("version")} · ${target.getString("set_by_name")} · ${timeLabel(target.getString("set_at"))}")
        }
        val records = progress.getInt("records")
        if (records == 0) Text("Belum ada panen tercatat.")
        else {
            Text("$records catatan panen aktif")
            val totals = progress.getJSONObject("totals")
            listOf("saleable" to "Layak", "defective" to "Cacat").forEach { (key, label) ->
                val value = totals.getJSONObject(key)
                FarmFact(label, "${value.getLong("fruit_count")} buah · ${harvestDecimal(value.getString("weight_kg"))} kg")
            }
            if (!progress.getBoolean("complete_quality")) {
                val unknown = totals.getJSONObject("unknown")
                FarmFact("Kondisi belum diketahui", "${unknown.getLong("fruit_count")} buah · ${harvestDecimal(unknown.getString("weight_kg"))} kg")
                Text("Capaian menunggu klasifikasi rincian lama.")
            }
            if (!progress.isNull("achievement_percent")) {
                FarmFact("Capaian buah", "${harvestDecimal(progress.getString("achievement_percent"))}%", prominent = true)
                val remaining = progress.getLong("difference_fruit")
                Text(when {
                    remaining > 0 -> "Selisih ke target $remaining buah."
                    remaining < 0 -> "Melampaui target ${-remaining} buah."
                    else -> "Target buah tercapai."
                })
            }
        }
        Text("Diperbarui ${timeLabel(progress.getString("as_of"))}")
    }
    val local = transition?.takeUnless { it.syncState == SyncState.ACCEPTED }
    if (local != null) FarmSection(title = "Belum terkirim") {
        Text(if (local.syncState == SyncState.QUEUED) "Perubahan belum terkirim." else "Perubahan perlu dicek.")
        local.error?.optString("message")?.takeIf { it.isNotBlank() }?.let { Text(it) }
        if (local.syncState == SyncState.CONFLICT && error == null && !loading) {
            TextButton(onClick = {
                scope.launch {
                    try {
                        repository.useCurrentTransition(session, ticket, progress, local)
                        refreshing++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message
                    }
                }
            }) { Text("Pakai data terbaru") }
        }
        OutlinedButton(onClick = onOutbox) { Text("Lihat belum terkirim") }
    } else if (progress.getString("season_status") == "open" && (completion != null || progress.getInt("records") > 0)) {
        Button(onClick = { decision = completion == null }, enabled = !busy && !loading && error == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("harvest-progress-transition")) {
            Text(if (completion == null) "Tandai panen selesai" else "Buka kembali panen")
        }
    }
    decision?.let { complete ->
        ReasonDialog(if (complete) "Tandai panen selesai?" else "Buka kembali panen?",
            if (complete) "Catatan panen baru ditahan sampai panen dibuka lagi. Musim tetap terbuka."
            else "Pekerja dan pengelola bisa mencatat panen lagi untuk kelompok ini.",
            if (complete) "Tandai selesai" else "Buka panen",
            busy, onDismiss = { decision = null }, onConfirm = { reason ->
                busy = true
                scope.launch {
                    try {
                        transition = repository.queueTransition(session, ticket, progress, complete, reason)
                        decision = null
                        onSync()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message
                        decision = null
                    } finally {
                        busy = false
                    }
                }
            })
    }
}
