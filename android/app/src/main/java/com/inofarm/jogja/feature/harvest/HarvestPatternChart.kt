package com.inofarm.jogja.feature.harvest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.feature.harvest.domain.HarvestDay
import com.inofarm.jogja.feature.harvest.domain.VarietyPattern
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayLabel = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("id-ID"))
private fun kg(value: BigDecimal) = value.stripTrailingZeros().toPlainString().replace('.', ',')

/** Layak in the brand green, cacat in the error red of the Canopy palette. */
private val Saleable = com.inofarm.jogja.core.design.FarmColors.Brand
private val Defective = com.inofarm.jogja.core.design.FarmColors.Error

/**
 * The harvest pattern of one variety (Inofarm, 20 Nov 2026): kg layak and cacat stacked per harvest date. A bar shows
 * its numbers when touched; the whole chart reads aloud as one sentence per date.
 */
@Composable
internal fun HarvestPatternChart(pattern: VarietyPattern, varietyName: String, modifier: Modifier = Modifier) {
    var chosen by rememberSaveable(pattern.groupId) { mutableStateOf<String?>(null) }
    Column(modifier.testTag("harvest-pattern-${pattern.groupId}"), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        Text(varietyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (pattern.days.isEmpty()) {
            Text("Belum ada panen di musim ini.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(listOfNotNull("Total ${kg(pattern.totalKg)} kg", pattern.defectivePercent?.let { "cacat $it%" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
                Legend(Saleable, "Layak"); Legend(Defective, "Cacat")
            }
            val top = pattern.days.maxOf { it.saleableKg + it.defectiveKg }.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
            val spoken = pattern.days.joinToString("; ") { "${it.date.format(DayLabel)}: layak ${kg(it.saleableKg)} kg, cacat ${kg(it.defectiveKg)} kg" }
            Row(Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = "Pola panen $varietyName. $spoken" },
                horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small), verticalAlignment = Alignment.Bottom) {
                pattern.days.forEach { day ->
                    val id = day.date.toString()
                    Column(Modifier.width(44.dp).heightIn(min = FarmSpace.Touch).clickable { chosen = if (chosen == id) null else id },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Bar(day, top)
                        Spacer(Modifier.height(4.dp))
                        Text(day.date.format(DayLabel), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            pattern.days.find { it.date.toString() == chosen }?.let { day ->
                Text("${day.date.format(DayLabel)}: layak ${kg(day.saleableKg)} kg · cacat ${kg(day.defectiveKg)} kg",
                    Modifier.testTag("harvest-pattern-detail"), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (pattern.unclassifiedKg.signum() > 0) Text("${kg(pattern.unclassifiedKg)} kg panen lama tanpa layak/cacat tidak dihitung.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Bar(day: HarvestDay, top: BigDecimal) {
    Canvas(Modifier.size(28.dp, 120.dp)) {
        fun share(value: BigDecimal) = value.toFloat() / top.toFloat() * size.height
        val good = share(day.saleableKg)
        val bad = share(day.defectiveKg)
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(Saleable, Offset(0f, size.height - good), Size(size.width, good), radius)
        if (bad > 0f) drawRoundRect(Defective, Offset(0f, size.height - good - bad), Size(size.width, bad), radius)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
