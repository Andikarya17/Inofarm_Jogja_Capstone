package com.inofarm.jogja.feature.financehome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.auth.userMessage
import org.json.JSONObject
import java.math.BigDecimal

/** Filters of "Penjualan Musim n"; [type] "all" or a wire buyer type, [greenhouse] null = all. */
data class SalesFilter(val query: String = "", val type: String = "all", val greenhouse: String? = null)

private val SaleTypes = listOf("all" to "Semua", "consumer" to "Perorangan", "retail" to "Retail", "partner" to "Mitra")

/**
 * "Penjualan Musim n" (artboard PenjualanMusim): every paid sale of the greenhouses running that season number, read from
 * the device cache, grouped by WIB day with the day total. Search, buyer type and greenhouse filters run on the device.
 */
@Composable internal fun SeasonSalesScreen(
    detail: SeasonDetail?, greenhouseCount: Int?, loadSales: suspend (Int) -> List<JSONObject>,
    onBack: () -> Unit, onOpen: ((String) -> Unit)?, onExcel: ((Int?) -> Unit)?,
    /** Search, type and greenhouse filters, held by the caller so they survive opening a sale and coming back. */
    filter: SalesFilter = SalesFilter(), onFilter: (SalesFilter) -> Unit = {},
    /** Changes after each sync, so the list rereads the cache. */
    refresh: Long = 0L,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = FarmSpace.Small), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onBack, Modifier.size(FarmSpace.Touch).testTag("season-sales-back")) { FarmControlIcon(FarmIcon.ChevronLeft, "Kembali ke Keuangan") }
            Column(Modifier.weight(1f)) {
                Text(detail?.let { "Penjualan Musim ${it.sequence}" } ?: "Penjualan musim", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                detail?.let { Text(it.dateLabel() + (greenhouseCount?.let { n -> " · $n GH" } ?: ""), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (onExcel != null && detail != null) Surface({ onExcel(detail.sequence) }, Modifier.size(FarmSpace.Touch).testTag("season-sales-excel"), shape = CircleShape,
                color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Box(contentAlignment = Alignment.Center) { FarmControlIcon(FarmIcon.Download, "Unduh Excel") }
            }
        }
        if (detail == null) { Notice("Musim belum dimuat. Kembali ke Keuangan lalu coba lagi.", true); return@Column }
        var rows by remember(detail.sequence) { mutableStateOf<List<SeasonSaleRow>?>(null) }
        var error by remember(detail.sequence) { mutableStateOf<String?>(null) }
        var attempt by remember { mutableIntStateOf(0) }
        // Only greenhouses the server counts (active ones), so the rows add up to the summary.
        val names = remember(detail) { detail.byGreenhouse.associate { it.id to it.name } }
        LaunchedEffect(detail, refresh, attempt) {
            error = null
            runCatching { SeasonSaleRow.list(loadSales(detail.sequence), names) }.onSuccess { rows = it }.onFailure { error = userMessage(it) }
        }
        val query = filter.query; val type = filter.type; val greenhouse = filter.greenhouse
        Column(Modifier.padding(vertical = FarmSpace.Small), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                OutlinedTextField(query, { onFilter(filter.copy(query = it)) }, Modifier.weight(1f).testTag("season-sales-search"), singleLine = true,
                    placeholder = { Text("Cari pembeli") }, leadingIcon = { FarmControlIcon(FarmIcon.Search, null) }, shape = RoundedCornerShape(14.dp),
                    label = null)
                GreenhouseFilter(detail.byGreenhouse, greenhouse) { onFilter(filter.copy(greenhouse = it)) }
            }
            FarmSegmentedTabs(SaleTypes, type, { onFilter(filter.copy(type = it)) }, tagPrefix = "season-sales-type-")
        }
        val filterType = type.takeUnless { it == "all" }
        val all = rows
        val shown = all?.let { filterSales(it, query, filterType, greenhouse) }
        Summary(detail, query, filterType, greenhouse, shown)
        when {
            error != null -> Column(Modifier.padding(top = FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Notice("Penjualan belum bisa dibaca. $error", true)
                OutlinedButton({ attempt++ }, shape = FarmShape.Control, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Coba lagi") }
            }
            all == null -> Row(Modifier.padding(top = FarmSpace.Large), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Membaca penjualan…")
            }
            all.isEmpty() -> Text("Belum ada penjualan lunas di Musim ${detail.sequence} yang tersimpan di perangkat. Tarik ke bawah di Beranda untuk memperbarui.",
                Modifier.padding(top = FarmSpace.Large), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            shown.isNullOrEmpty() -> Text("Tidak ada penjualan yang cocok dengan pencarian dan filter ini.", Modifier.padding(top = FarmSpace.Large),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> SaleList(groupByDay(shown), onOpen)
        }
    }
}

@Composable private fun GreenhouseFilter(options: List<GreenhouseSales>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.heightIn(min = 56.dp).testTag("season-sales-gh"), shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Text(options.find { it.id == selected }?.name ?: "Semua GH", Modifier.widthIn(max = 104.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            FarmControlIcon(FarmIcon.ChevronDown, "Pilih greenhouse")
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("Semua GH") }, { onSelect(null); open = false }, Modifier.testTag("season-sales-gh-all"))
            options.forEach { gh -> DropdownMenuItem({ Text(gh.name) }, { onSelect(gh.id); open = false }, Modifier.testTag("season-sales-gh-${gh.id}")) }
        }
    }
}

/**
 * Head strip. Without search it uses the server totals (the type slot, or the greenhouse row when only a greenhouse is
 * picked); with a search or both filters it adds up the rows on the device.
 */
@Composable private fun Summary(detail: SeasonDetail, query: String, type: String?, greenhouse: String?, shown: List<SeasonSaleRow>?) {
    val ghRow = detail.byGreenhouse.find { it.id == greenhouse }
    val slot = when {
        query.isBlank() && greenhouse == null -> detail.sales.of(type)
        query.isBlank() && type == null && ghRow != null -> ghRow.sales
        shown != null -> SalesSlot(shown.fold(BigDecimal.ZERO.setScale(2)) { sum, row -> sum + row.amount }, shown.fold(BigDecimal.ZERO) { sum, row -> sum + row.kg }, shown.size)
        else -> null
    }
    val label = when {
        query.isNotBlank() -> "Hasil pencarian"
        type != null && ghRow != null -> "${buyerTypeLabel(type)} di ${ghRow.name}"
        type != null -> "${buyerTypeLabel(type)} musim ini"
        ghRow != null -> ghRow.name
        else -> "Total musim"
    }
    FarmHeroCard(Modifier.testTag("season-sales-summary").semantics(mergeDescendants = true) {}, motif = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            Fact(label, slot?.amount?.let(::rupiah) ?: "-", Modifier.weight(1.4f), large = true)
            Fact("Terjual", slot?.kg?.let(::kgLabel) ?: "-", Modifier.weight(1f))
            Fact("Transaksi", slot?.count?.toString() ?: "-", Modifier.weight(0.8f))
        }
    }
}

@Composable private fun Fact(label: String, value: String, modifier: Modifier, large: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = (if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge).copy(fontFeatureSettings = "tnum"),
            fontWeight = if (large) FontWeight.ExtraBold else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun SaleList(days: List<SaleDay>, onOpen: ((String) -> Unit)?) {
    val dark = LocalFarmTheme.current.dark
    LazyColumn(Modifier.fillMaxSize().testTag("season-sales-list"), contentPadding = PaddingValues(bottom = FarmSpace.Section)) {
        days.forEach { day ->
            item("day-${day.date}") {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = FarmSpace.Large, bottom = FarmSpace.Small).semantics(mergeDescendants = true) { heading() }) {
                    Text(dayLabel(day.date), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rupiah(day.total), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(day.rows, key = { "sale-${it.id}" }) { row ->
                val first = row == day.rows.first(); val last = row == day.rows.last()
                val shape = RoundedCornerShape(topStart = if (first) 18.dp else 0.dp, topEnd = if (first) 18.dp else 0.dp,
                    bottomStart = if (last) 18.dp else 0.dp, bottomEnd = if (last) 18.dp else 0.dp)
                SaleRow(row, shape, dark, Modifier.offset(y = if (first) 0.dp else (-1).dp), onOpen)
            }
        }
    }
}

@Composable private fun SaleRow(row: SeasonSaleRow, shape: RoundedCornerShape, dark: Boolean, modifier: Modifier, onOpen: ((String) -> Unit)?) {
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 14.dp, vertical = FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            val (icon, badge, tint) = when (row.type) {
                "partner" -> Triple(FarmIcon.Truck, if (dark) FarmColors.WarningSurfaceDark else FinanceChart.SalesPanel, if (dark) FarmColors.Sage else FinanceChart.SalesInk)
                "retail" -> Triple(FarmIcon.Store, if (dark) FarmColors.WarningSurfaceDark else FinanceChart.SalesPanel, if (dark) FarmColors.Sage else FinanceChart.SalesInk)
                "consumer" -> Triple(FarmIcon.Account, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primary)
                else -> Triple(FarmIcon.Basket, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(40.dp).background(badge, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides tint) { FarmControlIcon(icon, null) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.meta(), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(rupiah(row.amount), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold,
                color = if (dark) FarmColors.Sage else FinanceChart.SalesInk)
        }
    }
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val tagged = modifier.fillMaxWidth().testTag("season-sale-${row.id}")
    if (onOpen != null) Surface({ onOpen(row.id) }, tagged, shape = shape, color = MaterialTheme.colorScheme.surface, border = border, content = content)
    else Surface(tagged, shape = shape, color = MaterialTheme.colorScheme.surface, border = border, content = content)
}
