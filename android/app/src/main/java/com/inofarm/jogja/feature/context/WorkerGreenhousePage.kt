package com.inofarm.jogja.feature.context

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime

/** The stage the worker last wrote down for this group, or null when none was recorded yet. */
internal fun latestStage(stages: List<Pair<String, String>>): String? =
    stages.filter { it.second.isNotBlank() }.maxByOrNull { runCatching { OffsetDateTime.parse(it.first).toInstant() }.getOrNull() ?: java.time.Instant.MIN }?.second?.trim()

/** Forest hero for one greenhouse: date and season, variety and group, then days since sowing, since transplant and the stage. */
@Composable
internal fun WorkerGreenhouseHero(
    runtime: FinanceRuntime, session: FinanceSession, data: CultivationUiState, selected: HomeContext, today: LocalDate,
    changed: Any?, syncUpdate: Long,
) {
    val greenhouse = selected.greenhouse
    val group = data.groups.find { it.optString("id") == selected.group }
    val season = data.seasons.find { it.optString("id") == selected.season }
    val stage by produceState<String?>(null, session.scope.key, greenhouse, selected.group, changed, syncUpdate) {
        value = if (greenhouse == null || selected.group == null) null else withContext(Dispatchers.IO) {
            runCatching {
                latestStage(runtime.store.cachedAll(session.scope.key, "activity", greenhouse)
                    .filter { it.optString("planting_group_id") == selected.group && it.optString("record_state", "active") == "active" }
                    .map { it.optString("occurred_at") to it.optString("actual_stage") })
            }.getOrNull()
        }
    }
    val age = group?.let {
        runCatching {
            PlantAge.calculate(it.getString("sowing_date"), if (it.isNull("transplant_date")) null else it.getString("transplant_date"),
                today.atStartOfDay(ActivityDraft.WIB).toOffsetDateTime().toString())
        }.getOrNull()
    }
    FarmHeroCard(Modifier.testTag("worker-greenhouse-hero")) {
        Text(listOfNotNull(dateLabel(today), season?.optString("name")?.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (group == null) "Kelompok tanam belum dipilih" else listOfNotNull(
            com.inofarm.jogja.feature.harvest.varietyName(data, group), group.optString("name")).joinToString(" · "),
            Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (group != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            HeroTile(age?.hss?.toString() ?: "-", "hari sejak semai", Modifier.weight(1f))
            HeroTile(age?.hst?.toString() ?: "-", if (age?.hst != null) "hari sejak pindah tanam" else "belum pindah tanam", Modifier.weight(1f))
            HeroTile(stage ?: "-", if (stage != null) "stadia tercatat" else "stadia belum dicatat", Modifier.weight(1f), text = true)
        }
    }
}

@Composable
private fun HeroTile(value: String, label: String, modifier: Modifier, text: Boolean = false) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, FarmShape.Tile).padding(FarmSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = if (text) MaterialTheme.typography.titleMedium else FarmType.HeroAmount, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Chips to pick between several active groups of the open season; the hero shows only the chosen one. */
@Composable
internal fun WorkerGroupChips(data: CultivationUiState, selected: HomeContext, today: LocalDate, onSelect: (String) -> Unit) {
    val groups = data.groups.filter {
        it.optString("season_id") == selected.season && it.optBoolean("active", true) &&
            runCatching { LocalDate.parse(it.getString("sowing_date")) <= today }.getOrDefault(false)
    }
    if (groups.size > 1) FarmChoiceChips("Kelompok tanam", selected.group, groups, "worker-group", scroll = true, onSelect = onSelect)
}
