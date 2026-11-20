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
import com.inofarm.jogja.core.design.LocalFarmDialogPicker
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class HomeContext(val greenhouse: String? = null, val season: String? = null, val group: String? = null)

/** The season and planting a greenhouse records under; never chosen by the user (see [recordingTarget]). */
internal fun CultivationUiState.workerDefault(greenhouse: JSONObject, today: LocalDate = LocalDate.now(ActivityDraft.WIB)): HomeContext {
    val id = greenhouse.getString("id")
    return when (val target = recordingTarget(greenhouse, today)) {
        is RecordingTarget.Ready -> HomeContext(id, target.season.getString("id"), target.group.getString("id"))
        is RecordingTarget.NotPlanted -> HomeContext(id, target.season?.getString("id"))
        is RecordingTarget.NotSown -> HomeContext(id, target.season.getString("id"))
    }
}

internal fun workerAge(group: JSONObject, today: LocalDate): String {
    val age = runCatching {
        PlantAge.calculate(group.getString("sowing_date"),
            if (group.isNull("transplant_date")) null else group.getString("transplant_date"),
            today.atStartOfDay(ActivityDraft.WIB).toOffsetDateTime().toString())
    }.getOrNull()
    return "HSS ${age?.hss ?: "belum tersedia"} · HST ${age?.hst ?: "belum berlaku"}"
}

internal fun CultivationUiState.greenhouseAvailability(): String? = when {
    loading -> "Memeriksa greenhouse dan musim Anda…"
    check == ContextCheck.ACCESS_CHANGED -> "Akses akun berubah. Masuk kembali dengan akun yang sama."
    check == ContextCheck.OFFLINE && greenhouses.isEmpty() -> "Belum bisa terhubung dan belum ada greenhouse tersimpan. Periksa internet lalu perbarui."
    check == ContextCheck.OFFLINE -> "Memakai data tersimpan. Diperbarui otomatis saat ada internet."
    check == ContextCheck.FAILED && greenhouses.isEmpty() -> "Greenhouse gagal dimuat. Perbarui untuk mencoba lagi."
    check == ContextCheck.FAILED -> "Gagal memeriksa data terbaru. Daftar tersimpan mungkin belum terbaru."
    check == ContextCheck.CHECKED && greenhouses.none { it.optBoolean("active", true) } -> "Belum ada greenhouse aktif. Minta pengelola menambahkannya."
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
    val groups = data.groups.filter { it.optString("season_id") == selected.season && it.optBoolean("active", true) }
    val greenhouse = greenhouses.find { it.optString("id") == selected.greenhouse }
    val group = groups.find { it.optString("id") == selected.group }
    val target = data.recordingTarget(selected.greenhouse, today)
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
                    // Varieties, not planting groups: the group is resolved for the user and never shown.
                    if (target is RecordingTarget.Ready) Text(target.caption(false).orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    val summary = when {
                        group != null -> workerAge(group, today)
                        greenhouse == null -> "Pilih greenhouse Anda."
                        else -> target.caption(false) ?: "Belum mulai semai."
                    }
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = ForestScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        TextButton(
                            onClick = onOpen, shape = FarmShape.Control,
                            contentPadding = PaddingValues(end = FarmSpace.Large),
                            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-context-open")
                        ) { Text(if (greenhouse == null) "Pilih greenhouse" else "Ganti greenhouse") }
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
    onStartPlanting: ((String) -> Unit)? = null,
) {
    val today = LocalDate.now(ActivityDraft.WIB)
    val greenhouses = data.greenhouses.filter { it.optBoolean("active", true) }
    val target = data.recordingTarget(selected.greenhouse, today)
    // Only a greenhouse is chosen; its open season and planting follow from it, so a stale pair is corrected here.
    LaunchedEffect(target, selected) {
        if (target != null && data.check == ContextCheck.CHECKED) {
            val resolved = data.workerDefault(target.greenhouse, today)
            if (resolved != selected) onSelect(resolved)
        }
    }
    val canContinue = target is RecordingTarget.Ready && selected.group == target.group.optString("id") &&
        data.check != ContextCheck.ACCESS_CHANGED
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing }
    ) {
        CompositionLocalProvider(LocalFarmDialogPicker provides true) {
            Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page)) {
                FarmSheetHeading(recordingLabel ?: "Pilih greenhouse", onClose)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = FarmSpace.Large),
                    verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    data.greenhouseAvailability()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Choice("Greenhouse", selected.greenhouse, greenhouses, "finance-gh", loading = data.loading) {
                        val next = greenhouses.find { row -> row.optString("id") == it }
                        if (it != selected.greenhouse && next != null) onSelect(data.workerDefault(next, today))
                    }
                    target.caption(onStartPlanting != null)?.let {
                        Text(it, Modifier.testTag("finance-context-target"), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (target is RecordingTarget.NotPlanted && onStartPlanting != null) Button(
                        onClick = { onStartPlanting(target.greenhouse.getString("id")) }, shape = FarmShape.Control,
                        modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("finance-context-start-planting")) {
                        Text("Mulai semai")
                    }
                    if (recordingLabel != null && canContinue) Button(onClick = onContinue, shape = FarmShape.Control,
                        modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("finance-context-continue")) {
                        Text("Lanjut ke formulir")
                    }
                    if (!data.loading && (greenhouses.isEmpty() || data.check != ContextCheck.CHECKED)) {
                        TextButton(onClick = onSync, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Perbarui") }
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
