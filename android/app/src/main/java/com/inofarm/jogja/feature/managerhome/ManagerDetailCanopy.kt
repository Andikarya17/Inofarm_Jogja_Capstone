@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.inofarm.jogja.feature.managerhome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.feature.expenses.moneyLabel
import org.json.JSONObject

/** Forest header of one greenhouse: state, planting, three age facts and the planting action. */
@Composable
internal fun GreenhouseHero(selected: JSONObject, updated: String, onPeriod: () -> Unit) {
    val active = selected.optBoolean("active", true)
    val period = selected.optJSONObject("period") ?: JSONObject()
    val ready = period.text("state") == "ready"
    val age = if (ready) period.getJSONObject("age") else null
    val phase = if (ready) period.optJSONObject("phase")?.text("value") else null
    FarmHeroCard(Modifier.testTag("manager-detail-hero")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            FarmStatusChip(if (active) "Aktif" else "Nonaktif", tone = if (active) FarmTone.Good else FarmTone.Neutral)
            if (updated.isNotEmpty()) Text(updated, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            ready -> {
                Text(period.getString("variety"), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(period.text("group_name")?.let { "Kelompok $it" }, period.text("season_name")).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            period.text("state") == "needs_review" -> Text("Tanam saat ini belum dipilih", style = MaterialTheme.typography.titleMedium)
            period.text("state") == "inactive" -> Text("Greenhouse tidak aktif", style = MaterialTheme.typography.titleMedium)
            else -> Text("Belum ada tanam saat ini", style = MaterialTheme.typography.titleMedium)
        }
        if (ready) {
            val stack = LocalDensity.current.fontScale > 1.3f
            val hss = age?.text("hss")
            val hst = age?.text("hst")
            Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                if (stack) {
                    AgeTile("HSS", hss ?: "–", Modifier.fillMaxWidth(), unit = hss?.let { " hari" } ?: "")
                    AgeTile("HST", hst ?: "Belum berlaku", Modifier.fillMaxWidth(), unit = hst?.let { " hari" } ?: "")
                } else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    AgeTile("HSS", hss ?: "–", Modifier.weight(1f).fillMaxHeight(), unit = hss?.let { " hari" } ?: "")
                    AgeTile("HST", hst ?: "Belum berlaku", Modifier.weight(1f).fillMaxHeight(), unit = hst?.let { " hari" } ?: "")
                }
                AgeTile("Fase", phase ?: "belum tercatat", Modifier.fillMaxWidth(), small = true)
            }
        }
        if (active) OutlinedButton(onClick = onPeriod, shape = FarmShape.Control,
            border = BorderStroke(1.dp, ForestScheme.outline),
            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-period-history")) {
            Text(if (ready) "Ganti tanam saat ini" else "Pilih tanam saat ini")
        }
    }
}

@Composable
private fun AgeTile(label: String, value: String, modifier: Modifier, small: Boolean = false, unit: String = "") {
    Column(modifier.background(ForestScheme.surfaceVariant, FarmShape.Tile)
        .semantics(mergeDescendants = true) { contentDescription = "$label, $value$unit" }.padding(FarmSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (small) Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
        else Text(value, style = if (value.length > 3) MaterialTheme.typography.titleSmall else FarmType.HeroNumber, fontWeight = FontWeight.ExtraBold)
    }
}

/** One tappable line for the current planting's harvest, with a basket tile and a chevron. */
@Composable
internal fun HarvestRow(title: String, headline: String, note: String?, tag: String = "manager-harvest-row", onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().testTag(tag), shape = FarmShape.Card, color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)) {
        Row(Modifier.clickable(role = Role.Button, onClick = onClick).heightIn(min = 64.dp).padding(FarmSpace.Medium),
            horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
            FarmIconTile(FarmIcon.Basket)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
            FarmControlIcon(FarmIcon.ChevronRight, null)
        }
    }
}

/** Month cash of one greenhouse: two aligned rows and the period, then the links to the lists. */
@Composable
internal fun CashCard(snapshot: HomeSnapshot, onFinance: () -> Unit, onSales: () -> Unit) {
    val cash = snapshot.cash
    val receipts = cash.getJSONObject("receipts")
    val payments = cash.getJSONObject("payments")
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            Text("Keuangan bulan ini", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Caption("${dateLabel(cash.getString("from"))} – ${dateLabel(cash.getString("until"))} WIB · ${snapshot.selected?.getString("name") ?: "Semua greenhouse"}")
            if (receipts.getLong("records") + payments.getLong("records") == 0L) {
                Text("Belum ada transaksi tercatat pada periode ini.", style = MaterialTheme.typography.bodyMedium)
            } else {
                CashLine("Penerimaan", "Penjualan lunas", receipts)
                HorizontalDivider()
                CashLine("Pengeluaran", "Pembayaran aktual, termasuk pembelian aset", payments)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                TextButton(onClick = onFinance, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manager-home-finance")) { Text("Lihat keuangan") }
                TextButton(onClick = onSales, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Lihat penjualan lunas") }
            }
        }
    }
}

@Composable
private fun CashLine(label: String, note: String, total: JSONObject) {
    val records = total.getLong("records")
    val stack = LocalDensity.current.fontScale > 1.3f
    val labels: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Caption(if (records == 0L) "$note · Belum ada catatan" else "$note · $records catatan")
        }
    }
    val amount: @Composable () -> Unit = { if (records > 0) FarmAmount(moneyLabel(total.getString("amount")), MaterialTheme.typography.titleMedium) }
    if (stack) Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
        labels(Modifier.fillMaxWidth()); amount()
    } else Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.Top) {
        labels(Modifier.weight(1f)); amount()
    }
}
