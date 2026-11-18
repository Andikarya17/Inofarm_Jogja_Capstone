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

/** Forest hero for one greenhouse: date and season, variety and group, then the age Inofarm last typed and the stage. */
@Composable
internal fun WorkerGreenhouseHero(
    runtime: FinanceRuntime, session: FinanceSession, data: CultivationUiState, selected: HomeContext, today: LocalDate,
    changed: Any?, syncUpdate: Long,
) {
    val greenhouse = selected.greenhouse
    val group = data.groups.find { it.optString("id") == selected.group }
    val season = data.seasons.find { it.optString("id") == selected.season }
    // Stadia is recorded once per greenhouse season since 18 Nov 2026 (no planting group), older records per group.
    val stage by produceState<String?>(null, session.scope.key, greenhouse, selected.season, selected.group, changed, syncUpdate) {
        value = if (greenhouse == null || selected.group == null) null else withContext(Dispatchers.IO) {
            runCatching {
                latestStage(runtime.store.cachedAll(session.scope.key, "activity", greenhouse)
                    .filter { (it.optString("planting_group_id") == selected.group || it.optString("season_id") == selected.season) &&
                        it.optString("record_state", "active") == "active" }
                    .map { it.optString("occurred_at") to if (it.isNull("actual_stage")) "" else it.optString("actual_stage") })
            }.getOrNull()
        }
    }
    // Plant age as Inofarm last typed it, never computed from the sowing date (decision 17 Nov 2026).
    val age by produceState<Triple<String, String, OffsetDateTime>?>(null, session.scope.key, greenhouse, changed, syncUpdate) {
        value = if (greenhouse == null) null else withContext(Dispatchers.IO) {
            runCatching { runtime.store.latestTypedAge(session.scope.key, greenhouse) }.getOrNull()
        }
    }
    FarmHeroCard(Modifier.testTag("worker-greenhouse-hero")) {
        Text(listOfNotNull(dateLabel(today), season?.optString("name")?.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val designated = data.greenhouses.find { it.optString("id") == greenhouse }?.optString("running_group_id")
            ?.let { it.isNotBlank() && it != "null" } == true
        Text(if (group == null) "Kelompok tanam belum dipilih" else if (!designated) "Tanam saat ini belum dipilih" else listOfNotNull(
            com.inofarm.jogja.feature.harvest.varietyName(data, group), group.optString("name")).joinToString(" · "),
            Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (group != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            val typed = age
            HeroTile(typed?.let { "${it.first.uppercase()} ${it.second}" } ?: "-",
                typed?.let { "umur diisi ${shortDate(it.third)}" } ?: "umur belum dicatat", Modifier.weight(1f).testTag("worker-hero-age"))
            HeroTile(stage ?: "-", if (stage != null) "stadia tercatat" else "stadia belum dicatat", Modifier.weight(1f), text = true)
        }
    }
}

/** "18 Nov" in WIB, the way the form's "Terakhir" line reads. */
private fun shortDate(at: OffsetDateTime): String =
    at.atZoneSameInstant(ActivityDraft.WIB).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("id-ID")))

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
