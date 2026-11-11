package com.inofarm.jogja.feature.context

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.R
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.design.ForestScheme
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.FarmSheetHeading
import com.inofarm.jogja.core.design.FarmActionRow
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmRowDivider
import com.inofarm.jogja.core.design.LocalFarmDialogPicker
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class HomeContext(val greenhouse: String? = null, val season: String? = null, val group: String? = null)

internal fun CultivationUiState.workerDefault(greenhouse: JSONObject, today: LocalDate = LocalDate.now(ActivityDraft.WIB)): HomeContext {
    val id = greenhouse.getString("id")
    val openSeasons = seasons.filter { it.optString("greenhouse_id") == id && it.optString("status") == "open" }
    val openIds = openSeasons.map { it.getString("id") }.toSet()
    val candidates = groups.filter {
        it.optString("season_id") in openIds && it.optBoolean("active", true) &&
            runCatching { LocalDate.parse(it.getString("sowing_date")) <= today }.getOrDefault(false)
    }
    val preferred = candidates.find { it.optString("id") == greenhouse.optString("running_group_id") }
        ?: candidates.singleOrNull()
    return HomeContext(id, preferred?.getString("season_id") ?: openSeasons.singleOrNull()?.getString("id"),
        preferred?.getString("id"))
}

@Composable
internal fun WorkerGreenhouseList(data: CultivationUiState, today: LocalDate, onSelect: (JSONObject) -> Unit) {
    val greenhouses = data.greenhouses
    if (greenhouses.isEmpty() || data.check == ContextCheck.ACCESS_CHANGED) return
    FarmPanel(title = "Greenhouse penugasan") {
        greenhouses.forEachIndexed { index, greenhouse ->
            if (index > 0) FarmRowDivider()
            val preferred = data.workerDefault(greenhouse, today)
            val season = data.seasons.find { it.optString("id") == preferred.season }
            val group = data.groups.find { it.optString("id") == preferred.group }
            val active = greenhouse.optBoolean("active", true)
            val detail = when {
                !active -> "Greenhouse nonaktif"
                group != null -> listOfNotNull(season?.optString("name"), group.optString("name"),
                    workerAge(group, today)).joinToString(" · ")
                season != null -> "${season.optString("name")} · Pilih kelompok tanam"
                data.seasons.any { it.optString("greenhouse_id") == preferred.greenhouse && it.optString("status") == "open" } ->
                    "Pilih musim dan kelompok tanam"
                else -> "Belum ada musim terbuka"
            }
            FarmActionRow(greenhouse.getString("name"), detail, FarmIcon.Greenhouse,
                Modifier.testTag("worker-greenhouse-${preferred.greenhouse}"), enabled = active) { onSelect(greenhouse) }
        }
    }
}

private fun workerAge(group: JSONObject, today: LocalDate): String {
    val age = runCatching {
        PlantAge.calculate(group.getString("sowing_date"),
            if (group.isNull("transplant_date")) null else group.getString("transplant_date"),
            today.atStartOfDay(ActivityDraft.WIB).toOffsetDateTime().toString())
    }.getOrNull()
    return "HSS ${age?.hss ?: "belum tersedia"} · HST ${age?.hst ?: "belum berlaku"}"
}

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
    today: LocalDate,
    onOverview: () -> Unit,
    onOpen: () -> Unit
) {
    val greenhouses = data.greenhouses.filter { it.optBoolean("active", true) }
    val seasons = data.seasons.filter { it.optString("greenhouse_id") == selected.greenhouse }
    val groups = data.groups.filter { it.optString("season_id") == selected.season && it.optBoolean("active", true) }
    val greenhouse = greenhouses.find { it.optString("id") == selected.greenhouse }
    val season = seasons.find { it.optString("id") == selected.season }
    val group = groups.find { it.optString("id") == selected.group }
    val date = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID")))
    var entered by remember(greenhouse?.optString("id")) { mutableStateOf(greenhouse == null) }
    LaunchedEffect(greenhouse?.optString("id")) { entered = true }
    val alpha by animateFloatAsState(if (entered) 1f else .38f, tween(230), label = "Panel greenhouse")
    MaterialTheme(colorScheme = ForestScheme) {
        Surface(
            Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }, shape = FarmShape.Card,
            color = ForestScheme.background, contentColor = ForestScheme.onBackground
        ) {
            BoxWithConstraints {
                val artwork = maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.3f
                if (artwork) Box(Modifier.align(Alignment.TopEnd).width(104.dp).height(110.dp)) {
                    Image(painterResource(R.drawable.background_dashboard), contentDescription = null,
                        modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                        alignment = Alignment.TopEnd, alpha = .8f)
                    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                        0f to ForestScheme.background, .34f to Color.Transparent)))
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                        .58f to Color.Transparent, 1f to ForestScheme.background)))
                }
                Column(
                    Modifier.padding(start = FarmSpace.Large, end = if (artwork) 116.dp else FarmSpace.Large,
                        top = FarmSpace.Large, bottom = FarmSpace.Small),
                    verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)
                ) {
                    Text(greenhouse?.optString("name") ?: "Di greenhouse mana hari ini?",
                        Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    Text("$date WIB", style = MaterialTheme.typography.bodySmall, color = ForestScheme.onSurfaceVariant)
                    if (season != null || group != null) {
                        Text(listOfNotNull(season?.optString("name"), group?.optString("name")).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    val summary = when {
                        group != null -> workerAge(group, today)
                        greenhouse == null -> "Pilih dari penugasan Anda."
                        season == null -> "Pilih musim terbuka untuk mulai mencatat."
                        else -> "Pilih kelompok tanam untuk mulai mencatat."
                    }
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = ForestScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        TextButton(
                            onClick = onOpen, shape = FarmShape.Control,
                            contentPadding = PaddingValues(end = FarmSpace.Large),
                            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-context-open")
                        ) { Text(if (greenhouse == null) "Pilihan konteks" else "Ubah musim atau kelompok") }
                        if (greenhouse != null) TextButton(
                            onClick = onOverview, shape = FarmShape.Control,
                            contentPadding = PaddingValues(end = FarmSpace.Large),
                            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("worker-greenhouse-overview")
                        ) { Text("Daftar greenhouse") }
                    }
                }
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
    chooseSingleGroup: Boolean = false,
    preferRunningPeriod: Boolean = false,
) {
    val greenhouses = data.greenhouses.filter { it.optBoolean("active", true) }
    val greenhouse = greenhouses.find { it.optString("id") == selected.greenhouse }
    val seasons = data.seasons.filter { it.optString("greenhouse_id") == selected.greenhouse }
    val season = seasons.find { it.optString("id") == selected.season }
    val groups = data.groups.filter { it.optString("season_id") == selected.season && it.optBoolean("active", true) }
    val singleSeason = seasons.singleOrNull { it.optString("status") == "open" }
    val singleGroup = groups.singleOrNull()
    LaunchedEffect(chooseSingleGroup, selected, singleSeason, singleGroup) {
        if (chooseSingleGroup && selected.greenhouse != null) {
            if (selected.season == null && singleSeason != null) onSelect(selected.copy(season = singleSeason.getString("id")))
            else if (selected.group == null && singleGroup != null) onSelect(selected.copy(group = singleGroup.getString("id")))
        }
    }
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
                        if (it != selected.greenhouse) {
                            val next = greenhouses.find { row -> row.optString("id") == it }
                            onSelect(if (preferRunningPeriod && next != null) data.workerDefault(next) else HomeContext(greenhouse = it))
                        }
                    }
                    if (greenhouse != null) {
                        Choice("Musim", selected.season, seasons, "finance-season", loading = data.loading) {
                            if (it != selected.season) onSelect(selected.copy(season = it, group = null))
                        }
                        if (data.check == ContextCheck.CHECKED && seasons.isEmpty())
                            Text(if (onSetup == null) "Belum ada musim di greenhouse ini. Minta pengelola menyiapkannya." else "Belum ada musim di greenhouse ini.")
                    }
                    if (season != null) {
                        val groupChoices = if (!chooseSingleGroup) groups else groups.map { group ->
                            val variety = com.inofarm.jogja.feature.harvest.varietyName(data, group)
                            org.json.JSONObject(group.toString()).put("choice_detail", variety)
                        }
                        Choice("Kelompok tanam", selected.group, groupChoices, "finance-group", loading = data.loading) {
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
