@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.inofarm.jogja.feature.managerhome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.expenses.moneyLabel
import com.inofarm.jogja.feature.timeLabel
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cash for the current WIB month on forest. Only the two recorded totals; no profit and no month-over-month change. */
@Composable
internal fun CashHero(snapshot: HomeSnapshot, updated: String, loading: Boolean, onOpen: (HomeDestination) -> Unit) {
    val cash = snapshot.cash
    val from = cash.getString("from")
    val until = cash.getString("until")
    val receipts = cash.getJSONObject("receipts")
    val payments = cash.getJSONObject("payments")
    val month = LocalDate.parse(from).format(DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("id-ID")))
    val empty = receipts.getLong("records") + payments.getLong("records") == 0L
    val filter = LocalHistoryFilter(greenhouseId = snapshot.selection, from = from, until = until, recordState = "active")
    FarmHeroCard(Modifier.testTag("manager-cash-hero"), motifAlpha = 0.16f) {
        Text("Kas $month · WIB", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Semua greenhouse", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (empty) {
            Text("Belum ada transaksi tercatat bulan ini.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val longest = listOf(receipts, payments).maxOf { t ->
                if (t.getLong("records") > 0) wholeRupiah(moneyLabel(t.getString("amount"))).length else 0
            }
            val stack = LocalDensity.current.fontScale > 1.3f || longest > 11
            val columns: @Composable (Modifier) -> Unit = { m ->
                HeroTotal("Penerimaan", "Penjualan lunas", receipts, m, FarmColors.Sage)
                HeroTotal("Pengeluaran", "Pembayaran aktual", payments, m, ForestScheme.secondary)
            }
            if (stack) Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) { columns(Modifier.fillMaxWidth()) }
            else Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Large)) { columns(Modifier.weight(1f)) }
            val share = cashShare(receipts.optString("amount").toBigDecimalOrNull(), payments.optString("amount").toBigDecimalOrNull())
            if (share != null) Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).clearAndSetSemantics {}) {
                if (share.first > 0f) Box(Modifier.weight(share.first).fillMaxHeight().background(FarmColors.Sage))
                if (share.first > 0f && share.second > 0f) Spacer(Modifier.width(2.dp))
                if (share.second > 0f) Box(Modifier.weight(share.second).fillMaxHeight().background(ForestScheme.secondary))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(updated, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onOpen(HomeDestination(OperationalKind.EXPENSE, filter)) },
                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-home-finance")) { Text("Lihat keuangan") }
        }
    }
}

@Composable
private fun HeroTotal(label: String, note: String, total: JSONObject, modifier: Modifier, marker: androidx.compose.ui.graphics.Color) {
    val records = total.getLong("records")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(marker, RoundedCornerShape(4.dp)).clearAndSetSemantics {})
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (records > 0) {
            val full = moneyLabel(total.getString("amount"))
            Text(wholeRupiah(full), Modifier.clearAndSetSemantics { contentDescription = full },
                style = FarmType.HeroAmount, maxLines = 1, softWrap = false)
        } else Text("Belum ada", style = FarmType.HeroAmount, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (records > 0) "$note · $records" else note, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class Shortcut(val tag: String, val label: String, val icon: FarmIcon, val badge: Int?, val onClick: () -> Unit)

/** Four shortcuts to places that already exist; wraps 2 by 2 on narrow screens or large fonts. */
@Composable
internal fun HomeShortcuts(
    reviewCount: Int?, onReview: () -> Unit, onAddGreenhouse: () -> Unit, onHarvestProgress: () -> Unit, onSeasonReport: () -> Unit
) {
    val twoByTwo = LocalConfiguration.current.screenWidthDp < 380 || LocalDensity.current.fontScale > 1.15f
    val items = listOf(
        Shortcut("manager-review-inbox", "Cek catatan", FarmIcon.CheckList, reviewCount, onReview),
        Shortcut("manager-add-greenhouse", "Tambah greenhouse", FarmIcon.GreenhouseAdd, null, onAddGreenhouse),
        Shortcut("manager-home-harvest-progress", "Rekap panen", FarmIcon.BarChart, null, onHarvestProgress),
        Shortcut("manager-home-season-report", "Evaluasi musim", FarmIcon.Report, null, onSeasonReport),
    )
    val perRow = if (twoByTwo) 2 else 4
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        items.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                row.forEach { FarmQuickAction(it.icon, it.label, it.onClick, Modifier.weight(1f).testTag(it.tag), badge = it.badge, badgeNote = if (it.badge != null) "perlu dicek" else "") }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun ReviewCard(count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(FarmShape.Card).background(colors.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick).testTag("manager-review-card")
            .heightIn(min = FarmSpace.Touch + FarmSpace.Medium).padding(FarmSpace.Medium),
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically
    ) {
        FarmIconTile(FarmIcon.CheckList, tone = FarmTone.Forest)
        Text("${badgeText(count)} catatan pekerja perlu dicek", Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer)
        CompositionLocalProvider(LocalContentColor provides colors.onPrimaryContainer) { FarmControlIcon(FarmIcon.ChevronRight, null) }
    }
}

/** One greenhouse: state tile, name, planting line, age figure, then fact chips. */
@Composable
internal fun GreenhouseCard(card: JSONObject, index: Int, outsideRange: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val id = card.getString("id")
    val name = card.getString("name")
    val period = card.getJSONObject("period")
    val state = period.text("state")
    val ready = state == "ready"
    val inactive = state == "inactive"
    val age = if (ready) period.getJSONObject("age") else null
    val figure = ageFigure(age?.text("hst")?.toIntOrNull(), age?.text("hss")?.toIntOrNull())
    val line = when {
        ready -> listOfNotNull(period.getString("variety"), period.text("group_name")?.let { "Kelompok $it" }).joinToString(" · ")
        state == "needs_review" -> "Tanam saat ini belum dipilih"
        inactive -> "Greenhouse tidak aktif"
        else -> "Belum ada tanam saat ini"
    }
    val phase = if (ready) period.optJSONObject("phase")?.text("value") else null
    val harvest = card.optJSONObject("harvest")?.let { h ->
        BigDecimal(h.getString("weight_kg")).stripTrailingZeros().toPlainString().replace('.', ',')
    }
    val last = card.text("last_operational_at")?.let { "Catatan ${timeLabel(it)}" }
    val summary = listOfNotNull(name, line, figure?.let { "${it.first} ${it.second}" }, phase?.let { "Fase $it" },
        harvest?.let { "Panen $it kg" }, last ?: "Belum ada catatan", if (outsideRange) "PPM di luar acuan" else null).joinToString(", ")
    val interaction = remember { MutableInteractionSource() }
    val large = LocalDensity.current.fontScale > 1.3f
    Surface(
        Modifier.fillMaxWidth().farmStaggerEntrance(index, "greenhouse-$id").farmPressable(interaction)
            .testTag("manager-greenhouse-$id")
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = summary },
        shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                val attention = state == "needs_review" || (!ready && !inactive)
                Box(Modifier.then(if (inactive) Modifier.graphicsAlpha(0.6f) else Modifier)) {
                    FarmIconTile(if (attention) FarmIcon.AlertTriangle else FarmIcon.Greenhouse,
                        tone = if (attention) FarmTone.Amber else FarmTone.Neutral)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(line, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    if (large && figure != null) AgeFigure(figure, Alignment.Start)
                }
                if (!large && figure != null) AgeFigure(figure, Alignment.End)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                phase?.let { FarmStatusChip("Fase $it") }
                harvest?.let { FarmStatusChip("Panen $it kg", tone = FarmTone.Good) }
                FarmStatusChip(last ?: "Belum ada catatan", tone = FarmTone.Neutral)
                if (outsideRange) FarmStatusChip("PPM di luar acuan", tone = FarmTone.Amber)
            }
        }
    }
}

private fun Modifier.graphicsAlpha(alpha: Float): Modifier = this.then(Modifier.graphicsLayer { this.alpha = alpha })

@Composable
private fun AgeFigure(figure: Pair<Int, String>, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(figure.first.toString(), style = FarmType.HeroNumber, color = MaterialTheme.colorScheme.onSurface)
        Text(figure.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An amber card for things that need a decision; the icon and the written title carry the meaning. */
@Composable
internal fun AttentionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val (container, onContainer) = farmToneColors(FarmTone.Amber)
    Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = container, contentColor = onContainer) {
        Row(Modifier.padding(FarmSpace.Medium), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            FarmControlIcon(FarmIcon.AlertTriangle, null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                CompositionLocalProvider(LocalContentColor provides onContainer) { content() }
            }
        }
    }
}

/** Latest records in one card: a tile per kind, title, greenhouse and recorder, time on the right. */
@Composable
internal fun RecentRecords(
    records: List<JSONObject>, unreadable: Boolean, names: Map<String, String>, showAllLinks: Boolean,
    onOpen: (JSONObject) -> Unit, onAllActivities: () -> Unit, onAllHarvests: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(vertical = FarmSpace.Small)) {
            Text("Catatan terbaru", Modifier.padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Small).semantics { heading() },
                style = MaterialTheme.typography.titleMedium)
            if (unreadable) Caption2("Catatan terbaru belum dapat dibaca lengkap.")
            else if (records.isEmpty()) Caption2("Belum ada catatan.")
            records.forEachIndexed { index, record ->
                val type = record.text("activity_type")
                val harvest = record.text("kind") == "harvest"
                val measurement = type == ActivityType.MEASUREMENT.wire
                val label = if (harvest) "Panen" else ActivityType.entries.find { it.wire == type }?.label ?: "Kegiatan"
                val gh = record.getString("greenhouse_id")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onOpen(record) }
                        .padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Small),
                    horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically
                ) {
                    FarmIconTile(if (harvest) FarmIcon.Basket else if (measurement) FarmIcon.Droplet else FarmIcon.Schedule,
                        tone = if (measurement) FarmTone.Info else FarmTone.Neutral, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(names[gh], record.text("recorded_by_name") ?: "Pencatat belum tersedia").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Text(timeLabel(record.getString("occurred_at")), Modifier.widthIn(max = 120.dp),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (index < records.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp, end = FarmSpace.Large))
            }
            if (showAllLinks) FlowRow(Modifier.padding(horizontal = FarmSpace.Small), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                TextButton(onClick = onAllActivities, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat semua kegiatan") }
                TextButton(onClick = onAllHarvests, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat semua panen") }
            }
        }
    }
}

@Composable
private fun Caption2(label: String) {
    Text(label, Modifier.padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Small),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
