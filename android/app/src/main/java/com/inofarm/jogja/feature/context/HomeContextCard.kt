package com.inofarm.jogja.feature.context

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import org.json.JSONObject
import java.time.OffsetDateTime

internal data class HomeContext(val greenhouse: String? = null, val season: String? = null, val group: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContextCard(
    data: CultivationUiState,
    selected: HomeContext,
    onSelect: (HomeContext) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
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
    FarmPanel {
        Text("Konteks budidaya", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(greenhouse?.optString("name") ?: "Pilih greenhouse", style = MaterialTheme.typography.headlineSmall)
        if (season != null || group != null) {
            Text(listOfNotNull(season?.optString("name"), group?.optString("name")).joinToString(" · "))
        }
        if (group != null) {
            Text(
                "HSS ${age?.hss ?: "belum tersedia"} · HST ${age?.hst ?: "belum berlaku"}",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text("Pilih musim dan kelompok untuk membuka pengukuran dan pencatatan.", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(
            onClick = { editing = true }, shape = FarmShape.Control,
            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-context-open")
        ) { Text(if (greenhouse == null) "Pilih konteks" else "Ubah konteks") }
    }
    if (editing) {
        ModalBottomSheet(
            onDismissRequest = { editing = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(FarmSpace.Page),
                verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)
            ) {
                Text("Pilih konteks budidaya", style = MaterialTheme.typography.titleLarge)
                Choice("Greenhouse", selected.greenhouse, greenhouses, "finance-gh") {
                    if (it != selected.greenhouse) onSelect(HomeContext(greenhouse = it))
                }
                if (greenhouse != null) Choice("Musim", selected.season, seasons, "finance-season") {
                    if (it != selected.season) onSelect(selected.copy(season = it, group = null))
                }
                if (season != null) Choice("Kelompok tanam", selected.group, groups, "finance-group") {
                    onSelect(selected.copy(group = it))
                }
                Button(
                    onClick = { editing = false }, shape = FarmShape.Control,
                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("finance-context-close")
                ) { Text("Kembali ke beranda") }
            }
        }
    }
}
