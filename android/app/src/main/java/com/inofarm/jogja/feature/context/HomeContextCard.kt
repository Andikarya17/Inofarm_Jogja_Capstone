package com.inofarm.jogja.feature.context

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.design.ForestScheme
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.FarmSheetHeading
import com.inofarm.jogja.core.design.LocalFarmDialogPicker
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import java.time.OffsetDateTime

internal data class HomeContext(val greenhouse: String? = null, val season: String? = null, val group: String? = null)

internal fun CultivationUiState.greenhouseAvailability(): String? = when {
    loading -> "Memeriksa penugasan dan konteks budidaya…"
    check == ContextCheck.ACCESS_CHANGED -> "Akses akun berubah. Masuk kembali dengan akun yang sama."
    check == ContextCheck.OFFLINE && greenhouses.isEmpty() -> "Server belum dapat dihubungi dan belum ada greenhouse tersimpan. Periksa koneksi lalu sinkronkan."
    check == ContextCheck.OFFLINE -> "Menggunakan konteks tersimpan. Penugasan akan diperiksa kembali saat terhubung."
    check == ContextCheck.FAILED && greenhouses.isEmpty() -> "Penugasan gagal dimuat. Sinkronkan untuk mencoba lagi."
    check == ContextCheck.FAILED -> "Pemeriksaan server gagal. Daftar tersimpan mungkin belum mutakhir."
    check == ContextCheck.CHECKED && greenhouses.isEmpty() -> "Belum ada penugasan efektif. Minta pengelola memeriksa penugasan dan masa berlakunya."
    check == ContextCheck.CHECKED && greenhouses.none { it.optBoolean("active", true) } -> "Greenhouse penugasan sedang nonaktif. Minta pengelola memeriksa statusnya."
    else -> null
}

@Composable
internal fun HomeContextCard(
    data: CultivationUiState,
    selected: HomeContext,
    onOpen: () -> Unit
) {
    val greenhouses = data.greenhouses.filter { it.optBoolean("active", true) }
    val seasons = data.seasons.filter { it.optString("greenhouse_id") == selected.greenhouse }
    val groups = data.groups.filter { it.optString("season_id") == selected.season && it.optBoolean("active", true) }
    val greenhouse = greenhouses.find { it.optString("id") == selected.greenhouse }
    val season = seasons.find { it.optString("id") == selected.season }
    val group = groups.find { it.optString("id") == selected.group }
    val age = remember(group, java.time.LocalDate.now(ActivityDraft.WIB)) {
        group?.let {
            runCatching {
                PlantAge.calculate(
                    it.getString("sowing_date"),
                    if (it.isNull("transplant_date")) null else it.getString("transplant_date"),
                    OffsetDateTime.now(ActivityDraft.WIB).toString()
                )
            }.getOrNull()
        }
    }
    MaterialTheme(colorScheme = ForestScheme) {
        Surface(
            Modifier.fillMaxWidth(), shape = FarmShape.Card,
            color = ForestScheme.background, contentColor = ForestScheme.onBackground
        ) {
            Column(
                Modifier.padding(horizontal = FarmSpace.Large).padding(top = FarmSpace.Large, bottom = FarmSpace.Small),
                verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)
            ) {
                Text(greenhouse?.optString("name") ?: "Pilih greenhouse",
                    Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                if (season != null || group != null) {
                    Text(listOfNotNull(season?.optString("name"), group?.optString("name")).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (group != null) {
                    Text("HSS ${age?.hss ?: "belum tersedia"} · HST ${age?.hst ?: "belum berlaku"}",
                        style = MaterialTheme.typography.bodySmall, color = ForestScheme.onSurfaceVariant)
                } else {
                    Text(when {
                        greenhouse == null -> "Lanjutkan dengan memilih musim dan kelompok tanam."
                        season == null -> "Pilih musim untuk melihat kelompok tanam."
                        else -> "Pilih kelompok tanam untuk membuka pengukuran dan pencatatan."
                    }, style = MaterialTheme.typography.bodyMedium, color = ForestScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = onOpen, shape = FarmShape.Control,
                    contentPadding = PaddingValues(end = FarmSpace.Large),
                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-context-open")
                ) { Text(if (greenhouse == null) "Pilih konteks" else "Ubah konteks") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkerContextSheet(
    data: CultivationUiState,
    selected: HomeContext,
    recordingLabel: String?,
    onSelect: (HomeContext) -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    onSync: () -> Unit,
    onSetup: (() -> Unit)? = null,
) {
    val greenhouses = data.greenhouses.filter { it.optBoolean("active", true) }
    val greenhouse = greenhouses.find { it.optString("id") == selected.greenhouse }
    val seasons = data.seasons.filter { it.optString("greenhouse_id") == selected.greenhouse }
    val season = seasons.find { it.optString("id") == selected.season }
    val groups = data.groups.filter { it.optString("season_id") == selected.season && it.optBoolean("active", true) }
    val canContinue = greenhouse != null && season?.optString("status") == "open" &&
        groups.any { it.optString("id") == selected.group } && data.check != ContextCheck.ACCESS_CHANGED
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing }
    ) {
        CompositionLocalProvider(LocalFarmDialogPicker provides true) {
            Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page)) {
                FarmSheetHeading(recordingLabel ?: "Pilih konteks budidaya", onClose)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = FarmSpace.Large),
                    verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    data.greenhouseAvailability()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Choice("Greenhouse", selected.greenhouse, greenhouses, "finance-gh", loading = data.loading) {
                        if (it != selected.greenhouse) onSelect(HomeContext(greenhouse = it))
                    }
                    if (greenhouse != null) {
                        Choice("Musim", selected.season, seasons, "finance-season", loading = data.loading) {
                            if (it != selected.season) onSelect(selected.copy(season = it, group = null))
                        }
                        if (data.check == ContextCheck.CHECKED && seasons.isEmpty())
                            Text(if (onSetup == null) "Belum ada musim di greenhouse ini. Minta pengelola menyiapkannya." else "Belum ada musim di greenhouse ini.")
                    }
                    if (season != null) {
                        Choice("Kelompok tanam", selected.group, groups, "finance-group", loading = data.loading) {
                            onSelect(selected.copy(group = it))
                        }
                        if (season.optString("status") == "closed")
                            Text("Musim ditutup. Pilih musim terbuka atau minta pengelola membukanya kembali.")
                        else if (data.check == ContextCheck.CHECKED && groups.isEmpty())
                            Text(if (onSetup == null) "Belum ada kelompok aktif di musim ini. Minta pengelola menyiapkannya." else "Belum ada kelompok aktif di musim ini.")
                    }
                    if (onSetup != null && !canContinue) {
                        TextButton(onClick = onSetup, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Siapkan greenhouse dan periode") }
                    }
                    if (recordingLabel != null && canContinue) Button(onClick = onContinue, shape = FarmShape.Control,
                        modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("finance-context-continue")) {
                        Text("Lanjut ke formulir")
                    }
                    if (!data.loading && (greenhouses.isEmpty() || data.check != ContextCheck.CHECKED)) {
                        TextButton(onClick = onSync, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Sinkronkan") }
                    }
                    OutlinedButton(onClick = onClose, shape = FarmShape.Control,
                        modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("finance-context-close")) {
                        Text(if (recordingLabel == null) "Selesai memilih" else "Kembali")
                    }
                }
            }
        }
    }
}
