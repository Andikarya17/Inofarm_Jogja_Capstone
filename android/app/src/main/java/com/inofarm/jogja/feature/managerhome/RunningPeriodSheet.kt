package com.inofarm.jogja.feature.managerhome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.design.FarmMotion
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RunningPeriodSheet(
    snapshot: HomeSnapshot,
    state: ManagerHomeState,
    onVisible: (Boolean) -> Unit,
    onAdmin: () -> Unit,
    onOpen: (HomeDestination) -> Unit,
    seasonActions: @Composable (JSONObject) -> Unit,
    onDesignate: suspend (String?, String) -> Unit,
) {
    val greenhouse = requireNotNull(snapshot.selected)
    val gh = greenhouse.getString("id")
    val current = snapshot.period?.text("group_id")
    val candidates = snapshot.detail?.rows("periods").orEmpty()
    val pending = state.pendingPeriod?.optJSONObject("payload")
    var editing by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val review: (String?) -> Unit = { id ->
        group = id
        reason = ""
        editing = true
        scope.launch { list.scrollToItem(1) }
    }
    val submit = {
        busy = true
        error = null
        scope.launch {
            try {
                onDesignate(group, reason)
                onVisible(false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = userMessage(failure)
            } finally {
                busy = false
            }
        }
        Unit
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onVisible(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(Modifier.fillMaxWidth().imePadding().testTag("manager-period-list"), state = list, contentPadding = PaddingValues(FarmSpace.Page),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            item("title") {
                Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    SectionTitle("Riwayat periode tanam")
                    Text(greenhouse.getString("name"), style = MaterialTheme.typography.titleLarge)
                    Caption("Pilihan periode berjalan dipakai untuk catatan baru. Riwayat dan draf mempertahankan konteks aslinya.")
                    if (state.cached) Caption("Periksa koneksi untuk menetapkan periode.")
                    if (error != null) Notice(error!!, true)
                }
            }
            if (pending != null) item("pending") {
                FarmPanel(title = "Penetapan menunggu konfirmasi") {
                    Caption(pending.getString("reason"))
                    Button(onClick = submit, enabled = !busy && !state.cached, modifier = Modifier.heightIn(min = FarmSpace.Touch)) {
                        Text("Periksa pengajuan yang sama")
                    }
                }
            }
            if (pending == null) item("review") {
                val candidate = candidates.find { it.text("group_id") == group }
                AnimatedVisibility(visible = editing, enter = fadeIn(tween(FarmMotion.State)), exit = fadeOut(tween(FarmMotion.State))) {
                    FarmPanel(title = if (candidate == null) "Akhiri periode berjalan" else "Tinjau periode berjalan") {
                        if (candidate != null) {
                            Text(candidate.getString("season_name"))
                            if (candidates.count { it.text("season_id") == candidate.text("season_id") } > 1) {
                                candidate.text("group_name")?.let { Caption("Kelompok $it") }
                            }
                            Caption(candidate.getString("variety"))
                            Caption("Semai ${dateLabel(candidate.getString("sowing_date"))}")
                            Caption(candidate.text("transplant_date")?.let { "Pindah tanam ${dateLabel(it)}" } ?: "Tanggal pindah tanam belum tercatat")
                        }
                        Caption("Status periode untuk transaksi keuangan tetap berlaku.")
                        OutlinedTextField(reason, { reason = it }, label = { Text("Alasan penetapan") },
                            enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("manager-period-reason"))
                        Button(onClick = submit, enabled = !busy && !state.cached && reason.isNotBlank() && reason.length <= 1000,
                            shape = FarmShape.Control, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-period-confirm")) {
                            Text(if (busy) "Mengirim…" else "Konfirmasi periode")
                        }
                        TextButton(onClick = { editing = false }, enabled = !busy,
                            modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Kembali ke daftar periode") }
                    }
                }
            }
            val seasons = snapshot.detail?.rows("seasons").orEmpty()
            if (seasons.isEmpty()) item("empty") { Caption("Belum ada periode tanam untuk greenhouse ini.") }
            items(seasons, key = { it.getString("id") }) { season ->
                val seasonId = season.getString("id")
                FarmPanel(title = season.getString("name")) {
                    Caption("Mulai ${dateLabel(season.getString("start_date"))} · ${if (season.text("status") == "open") "Terbuka untuk transaksi" else "Ditutup"}")
                    val seasonGroups = candidates.filter { it.text("season_id") == seasonId }
                    seasonGroups.forEach { candidate ->
                        val id = candidate.getString("group_id")
                        if (seasonGroups.size > 1) candidate.text("group_name")?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        Caption("${candidate.getString("variety")} · semai ${dateLabel(candidate.getString("sowing_date"))}")
                        if (id == current) Text("Periode berjalan", style = MaterialTheme.typography.labelLarge)
                        else if (candidate.optBoolean("eligible") && greenhouse.optBoolean("active") && pending == null) {
                            TextButton(onClick = { review(id) }, enabled = !state.cached && !busy,
                                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-period-option-$id")) {
                                Text("Tinjau penetapan")
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        val filter = LocalHistoryFilter(greenhouseId = gh, groups = seasonGroups.map { it.getString("group_id") }.toSet())
                        TextButton(onClick = { onVisible(false); onOpen(HomeDestination(OperationalKind.ACTIVITY, filter, seasonLabelId = seasonId)) },
                            modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat kegiatan") }
                        TextButton(onClick = { onVisible(false); onOpen(HomeDestination(OperationalKind.HARVEST, filter, seasonLabelId = seasonId)) },
                            modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat panen") }
                    }
                    if (greenhouse.optBoolean("active") && !state.cached && seasonId != snapshot.period?.text("season_id")) seasonActions(season)
                }
            }
            if (current != null && pending == null) item("end") {
                OutlinedButton(onClick = { review(null) }, enabled = !state.cached && !busy,
                    modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Akhiri periode berjalan") }
            }
            item("admin") {
                TextButton(onClick = { onVisible(false); onAdmin() }, enabled = !busy, modifier = Modifier.heightIn(min = FarmSpace.Touch)) {
                    Text("Kelola periode tanam")
                }
            }
        }
    }
}
