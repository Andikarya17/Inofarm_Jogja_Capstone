package com.inofarm.jogja.feature.context

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.feature.FarmFormBar
import com.inofarm.jogja.feature.FarmFormFrame
import com.inofarm.jogja.feature.FarmFormSection
import org.json.JSONObject
import java.time.LocalDate

/** What the activity form needs to act as one step of "Cek nutrisi semua greenhouse". */
internal class NutritionRunForm(
    val step: Int, val names: List<String>, val mainLabel: String, val onSkip: () -> Unit, val onMain: () -> Unit,
)

internal class NutritionRunItem(val greenhouse: String, val summary: String)

/** Label of the main button: it names the next greenhouse, or says the run goes to review after the last one. */
internal fun nutritionMainLabel(step: Int, names: List<String>): String =
    if (step < names.size) "Simpan dan lanjut ke ${names[step]}" else "Simpan dan periksa semua"

/**
 * Greenhouses a worker can record nutrition in right now (active, an open season, a current planting that has been sown),
 * ordered by name, and the active ones that cannot be recorded yet.
 */
internal fun nutritionRunTargets(data: CultivationUiState, today: LocalDate): Pair<List<JSONObject>, List<JSONObject>> {
    val active = data.greenhouses.filter { it.optBoolean("active", true) }.sortedBy { it.optString("name").lowercase() }
    val (ready, notReady) = active.partition { greenhouse ->
        val ctx = data.workerDefault(greenhouse, today)
        val group = data.groups.find { it.optString("id") == ctx.group }
        val season = data.seasons.find { it.optString("id") == ctx.season }
        group != null && season?.optString("status") == "open" &&
            runCatching { LocalDate.parse(group.getString("sowing_date")) <= today }.getOrDefault(false)
    }
    return ready to notReady
}

/** The review step: every greenhouse saved in this run, the ones skipped, and the ones that could not be recorded at all. */
@Composable
internal fun NutritionRunReview(
    saved: List<NutritionRunItem>, skipped: List<String>, notReady: List<String>, busy: Boolean, onExit: () -> Unit, onSend: () -> Unit,
) {
    FarmFormFrame(
        header = {
            FarmPageTitle("Periksa semua greenhouse", "Pastikan isinya benar. Setelah dikirim, tiap catatan terkirim otomatis saat ada internet.")
        },
        bar = {
            FarmFormBar("nutrition-run-exit", onExit, "nutrition-run-send", "Kirim semua", onSend, enabled = !busy, busy = busy, busyLabel = "Mengirim…", mainEnabled = saved.isNotEmpty(),
                mainIcon = true, saveLabel = "Simpan sebagai draf")
        }
    ) {
        FarmFormSection("Pengukuran yang akan dikirim") {
            if (saved.isEmpty()) Text("Belum ada pengukuran yang disimpan.", style = MaterialTheme.typography.bodyMedium)
            saved.forEach { item ->
                Column(Modifier.testTag("nutrition-run-item"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.greenhouse, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        (skipped.map { "$it dilewati." } + notReady.map { "$it dilewati karena tanam saat ini belum dipilih pengelola." }).forEach { message ->
            val (container, content) = farmToneColors(FarmTone.Amber)
            Row(Modifier.fillMaxWidth().background(container, FarmShape.Tile).padding(FarmSpace.Medium),
                horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small), verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalContentColor provides content) { FarmControlIcon(FarmIcon.AlertTriangle, null) }
                Text(message, style = MaterialTheme.typography.bodyMedium, color = content)
            }
        }
    }
}
