package com.inofarm.jogja.feature.financehome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.auth.userMessage
import org.json.JSONObject
import java.math.BigDecimal

/** Chart colours from the plan: costs in greens, sales in ambers (darker = mitra at the base of each stack). */
internal object FinanceChart {
    val Fc = Color(0xFF2B503D)
    val Vc = Color(0xFF9DB58A)
    val FcOnDark = Color(0xFFD4E4B7)
    val VcOnDark = Color(0xFF7E9481)
    val Partner = Color(0xFFB87424)
    val Retail = Color(0xFFD49A4E)
    val Consumer = Color(0xFFE8C48A)
    val Untyped = Color(0xFFC9D1C2)
    /** Amount text on light cards; amber dark enough for 4.5:1 on the cream surface. */
    val SalesInk = Color(0xFF8A5414)
    val SalesPanel = Color(0xFFF6EEDD)
    val SalesPanelLabel = Color(0xFF6B4A1C)
    val SalesTrack = Color(0xFFF2EADB)
    val SelectedColumn = Color(0xFFF1F3E8)
    val CostTrack = Color(0xFFECEFE2)
}

private enum class FinancePage { OVERVIEW, COSTS, LEDGER, CUSTOMERS, SALES }

/**
 * Manager Keuangan tab per Musim ke-n (25 Nov 2026, artboard KeuanganMusim): the season's standard cost (TC, FC, VC),
 * paid sales by week and greenhouse, and costs against sales across seasons. No profit or ratio is shown by decision; the
 * eye toggle still opens the real-cost ledger.
 */
@Composable internal fun FinanceOverviewScreen(
    session: FinanceSession, ticket: Long, repository: FinanceHomeRepository, preference: FinanceVisibilityPreference,
    refresh: Long, onRecordSale: () -> Unit, customers: com.inofarm.jogja.feature.customers.CustomerRepository,
    /** Cached sale bodies of every season with this number (Penjualan Musim n). */
    loadSales: suspend (Int) -> List<JSONObject> = { emptyList() },
    /** Opens one sale's detail in the ledger. */
    onOpenSale: ((String) -> Unit)? = null,
    /** Excel export for a season number (null = all); the button stays hidden until the export sheet is wired. */
    onExcel: ((Int?) -> Unit)? = null,
    /** True while a page with its own close button (cost sheet, buyers, season sales) is open, so the shell hides its bars. */
    onFullScreen: (Boolean) -> Unit = {},
    ledger: @Composable (onBack: () -> Unit) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(FinancePage.OVERVIEW) }
    var ledgerReturn by rememberSaveable { mutableStateOf(FinancePage.OVERVIEW) }
    /** The season number the manager picked; -1 lets the server pick the largest running one (shown from the response). */
    var sequence by rememberSaveable(session.scope.key) { mutableIntStateOf(-1) }
    var salesFilter by remember(session.scope.key) { mutableStateOf(SalesFilter()) }
    var reload by remember { mutableIntStateOf(0) }
    var overview by remember(session.scope.key) { mutableStateOf<SeasonOverview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(sequence, refresh, session.scope.key, reload) {
        loading = true; error = null
        runCatching { repository.seasonOverview(session, ticket, sequence.takeIf { it > 0 }) }
            .onSuccess { overview = it }
            .onFailure {
                // A picked season number that no longer exists (its greenhouses were turned off) falls back to the server's choice.
                if (it is com.inofarm.jogja.core.network.FinanceApiFailure && it.status == 404 && sequence > 0) sequence = -1 else error = userMessage(it)
            }
        loading = false
    }
    BackHandler(page != FinancePage.OVERVIEW) { page = if (page == FinancePage.LEDGER) ledgerReturn else FinancePage.OVERVIEW }
    val fullScreen = page in setOf(FinancePage.COSTS, FinancePage.CUSTOMERS, FinancePage.SALES)
    DisposableEffect(fullScreen) {
        onFullScreen(fullScreen)
        onDispose { onFullScreen(false) }
    }
    val openSale: ((String) -> Unit)? = onOpenSale?.let { open -> { id: String -> open(id); ledgerReturn = page; page = FinancePage.LEDGER } }
    when (page) {
        FinancePage.COSTS -> StandardCostScreen(session, ticket, repository, initialSequence = overview?.selected?.sequence) {
            page = FinancePage.OVERVIEW; reload++
        }
        FinancePage.LEDGER -> ledger { page = ledgerReturn }
        FinancePage.CUSTOMERS -> com.inofarm.jogja.feature.customers.CustomerListScreen(session, ticket, customers) { page = FinancePage.OVERVIEW }
        FinancePage.SALES -> SeasonSalesScreen(overview?.selected, overview?.sequences?.find { it.sequence == overview?.selected?.sequence }?.greenhouseCount,
            loadSales, onBack = { page = FinancePage.OVERVIEW; salesFilter = SalesFilter() }, onOpen = openSale, onExcel = onExcel,
            filter = salesFilter, onFilter = { salesFilter = it }, refresh = refresh)
        FinancePage.OVERVIEW -> Overview(session, preference, overview, error, loading, onSequence = { sequence = it }, onRetry = { reload++ },
            onRecordSale = onRecordSale, onExcel = onExcel, open = { ledgerReturn = FinancePage.OVERVIEW; page = it })
    }
}

@Composable private fun Overview(
    session: FinanceSession, preference: FinanceVisibilityPreference, overview: SeasonOverview?, error: String?, loading: Boolean,
    onSequence: (Int) -> Unit, onRetry: () -> Unit, onRecordSale: () -> Unit, onExcel: ((Int?) -> Unit)?, open: (FinancePage) -> Unit,
) {
    var showReal by remember(session.scope.key) { mutableStateOf(preference.showRealCosts(session.scope.key)) }
    val selected = overview?.selected
    // The shell already pads the page and shows "Keuangan" in its app bar.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("finance-overview").padding(top = FarmSpace.Small, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            Text("Biaya standar dan penjualan per musim", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface({ showReal = !showReal; preference.setShowRealCosts(session.scope.key, showReal) }, Modifier.size(FarmSpace.Touch).testTag("finance-eye"),
                shape = CircleShape, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Box(contentAlignment = Alignment.Center) {
                    FarmControlIcon(if (showReal) FarmIcon.Eye else FarmIcon.EyeOff, if (showReal) "Sembunyikan biaya nyata" else "Tampilkan biaya nyata")
                }
            }
            if (onExcel != null) Button({ onExcel(selected?.sequence) }, Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-excel"),
                shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(start = 12.dp, end = 16.dp)) {
                FarmControlIcon(FarmIcon.Download, null)
                Spacer(Modifier.width(6.dp))
                Text("Excel", fontWeight = FontWeight.Bold)
            }
        }
        error?.let { message ->
            Notice("Keuangan belum bisa dimuat. $message", true)
            OutlinedButton(onRetry, shape = FarmShape.Control, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-retry")) { Text("Muat ulang") }
        }
        when {
            overview == null && error == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Memuat keuangan musim…", style = MaterialTheme.typography.bodyMedium)
            }
            overview != null && selected == null -> FinanceCard {
                Text("Belum ada musim", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text("Mulai semai di satu greenhouse dulu. Biaya standar dan penjualan Musim 1 muncul di sini.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            overview != null && selected != null -> {
                SeasonChips(overview.sequences, selected.sequence, loading, onSequence)
                val totals = overview.sequences.find { it.sequence == selected.sequence }
                TcCard(selected, totals?.greenhouseCount ?: 0)
                CostDetail(selected.costs) { open(FinancePage.COSTS) }
                SalesSection(overview, selected, onRecordSale, onAll = { open(FinancePage.SALES) }, onBuyers = { open(FinancePage.CUSTOMERS) })
                if (overview.sequences.isNotEmpty()) CompareSection(overview.compareBars(), excel = onExcel != null)
            }
        }
        if (showReal) FinanceCard {
            Text("Biaya nyata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text("Pembayaran, alokasi, aset dan penyusutan tetap bisa dicatat. Angka musim di atas memakai biaya standar.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ open(FinancePage.LEDGER) }, shape = FarmShape.Control, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-open-ledger")) {
                Text("Buka pencatatan biaya nyata")
            }
        }
    }
}

@Composable internal fun FinanceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = FarmShape.Card, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium), content = content)
    }
}

/** Up to three seasons share the width; more scroll sideways so each chip keeps its two lines. */
@Composable private fun SeasonChips(sequences: List<SeasonTotals>, selected: Int, loading: Boolean, onSelect: (Int) -> Unit) {
    val ordered = sequences.sortedBy { it.sequence }
    val scroll = ordered.size > 3
    Row(Modifier.fillMaxWidth().then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        ordered.forEach { season ->
            val on = season.sequence == selected
            Surface({ if (!on) onSelect(season.sequence) }, (if (scroll) Modifier.width(112.dp) else Modifier.weight(1f))
                .heightIn(min = 56.dp).semantics { this.selected = on }.testTag("finance-season-${season.sequence}"),
                shape = RoundedCornerShape(16.dp), color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Musim ${season.sequence}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (on && loading) "Memuat…" else if (season.running) "Berjalan" else "Selesai", style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (season.running && !on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                        color = when { on -> if (LocalFarmTheme.current.dark) FarmColors.Ink else FarmColors.Sage; season.running -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant })
                }
            }
        }
    }
}

@Composable private fun TcCard(detail: SeasonDetail, greenhouses: Int) {
    val farm = detail.costs.farm
    FarmHeroCard(Modifier.testTag("finance-tc").animateContentSize(tween(FarmMotion.State)), motifAlpha = 0.14f) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Biaya total (TC) Musim ${detail.sequence} · $greenhouses GH", style = MaterialTheme.typography.labelLarge, color = muted,
                modifier = Modifier.semantics { heading() })
            Text(moneyOrBlank(farm.tc), style = (if (farm.tc == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge)
                .copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, modifier = Modifier.testTag("finance-tc-value"))
            Text(detail.dateLabel(), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        val fc = farm.fc; val vc = farm.vc
        if (fc != null && vc != null && (fc + vc).signum() > 0) {
            val fcShare = fc.divide(fc + vc, 4, java.math.RoundingMode.HALF_UP).toFloat()
            Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (fcShare > 0f) Box(Modifier.weight(fcShare).fillMaxHeight().background(FinanceChart.FcOnDark, RoundedCornerShape(5.dp)))
                if (fcShare < 1f) Box(Modifier.weight(1f - fcShare).fillMaxHeight().background(FinanceChart.VcOnDark, RoundedCornerShape(5.dp)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            TcPart("Biaya tetap (FC)", fc, FinanceChart.FcOnDark, Modifier.weight(1f))
            TcPart("Biaya variabel (VC)", vc, FinanceChart.VcOnDark, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Per GH, sama untuk $greenhouses GH", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = muted)
            Text(moneyOrBlank(detail.costs.perGreenhouse.tc), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
        }
        if (farm.tc == null) Text("Ada baris biaya standar yang belum diisi. Isi lewat Ubah di bawah.", style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable private fun TcPart(label: String, value: BigDecimal?, swatch: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).background(swatch, RoundedCornerShape(3.dp)))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(moneyOrBlank(value), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
    }
}

@Composable private fun CostDetail(costs: SeasonCosts, onEdit: () -> Unit) {
    val dark = LocalFarmTheme.current.dark
    FinanceCard(Modifier.testTag("finance-costs")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rincian biaya per GH", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onEdit, Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-cost-edit")) { Text("Ubah", fontWeight = FontWeight.SemiBold) }
        }
        var index = 0
        listOf("FC" to "Biaya tetap", "VC" to "Biaya variabel").forEachIndexed { group, (costClass, title) ->
            if (group > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val subtotal = if (costClass == "FC") costs.perGreenhouse.fc else costs.perGreenhouse.vc
            Row {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(moneyOrBlank(subtotal), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val color = when { costClass == "FC" && dark -> FinanceChart.FcOnDark; costClass == "FC" -> FinanceChart.Fc; dark -> FinanceChart.VcOnDark; else -> FinanceChart.Vc }
            costs.lines.filter { it.costClass == costClass }.forEach { line ->
                Column(Modifier.testTag("finance-cost-line-${index++}").semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        Text(line.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(moneyOrBlank(line.amount), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = if (line.amount == null) FontWeight.Normal else FontWeight.SemiBold,
                            color = if (line.amount == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                    ShareBar(costs.costShare(line), color, if (dark) FarmColors.ForestPill else FinanceChart.CostTrack, minimum = line.amount != null)
                }
            }
        }
        Text(if (costs.perGreenhouse.tc != null) "Panjang garis = bagian dari biaya total satu GH (${rupiah(costs.perGreenhouse.tc)})."
            else "Panjang garis = bagian dari baris yang sudah diisi. Biaya total menunggu semua baris terisi.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ShareBar(share: Float, color: Color, track: Color, minimum: Boolean = true) {
    val shown = if (minimum && share > 0f) share.coerceIn(0.015f, 1f) else share.coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
        if (shown > 0f) Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
    }
}

private data class Series(val label: String, val color: Color)
private val PartnerSeries = Series("Mitra", FinanceChart.Partner)
private val RetailSeries = Series("Retail", FinanceChart.Retail)
private val ConsumerSeries = Series("Perorangan", FinanceChart.Consumer)
private val UntypedSeries = Series("Tanpa tipe", FinanceChart.Untyped)

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Legend(items: List<Series>, trailing: String? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(item.color, RoundedCornerShape(3.dp)))
                Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun SalesSection(overview: SeasonOverview, detail: SeasonDetail, onRecordSale: () -> Unit, onAll: () -> Unit, onBuyers: () -> Unit) {
    val dark = LocalFarmTheme.current.dark
    val sales = detail.sales
    FinanceCard(Modifier.testTag("finance-sales")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Penjualan Musim ${detail.sequence}", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onBuyers, Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-customers")) { Text("Daftar pembeli", fontWeight = FontWeight.SemiBold) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(rupiah(sales.total.amount), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("finance-sales-total"))
            Text("${kgLabel(sales.total.kg)} terjual · ${sales.total.count} transaksi lunas", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val bars = overview.weeklyBars()
        if (bars.isEmpty()) {
            Text("Belum ada penjualan lunas di Musim ${detail.sequence}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onRecordSale, shape = FarmShape.Control, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-record-sale")) { Text("Catat penjualan") }
        } else {
            val untyped = bars.any { it.unspecified.signum() > 0 }
            Legend(listOfNotNull(PartnerSeries, RetailSeries, ConsumerSeries, UntypedSeries.takeIf { untyped }), "Per minggu · ketuk batang")
            var picked by rememberSaveable(detail.sequence) { mutableIntStateOf(-1) }
            val index = if (picked in bars.indices) picked else bars.lastIndex
            WeeklyChart(bars, index) { picked = it }
            WeekPanel(bars[index], untyped, dark)
        }
        if (detail.byGreenhouse.isNotEmpty()) {
            Text("Per greenhouse", Modifier.padding(top = FarmSpace.Small).semantics { heading() }, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val largest = detail.byGreenhouse.maxOf { it.sales.amount }
            detail.byGreenhouse.forEach { gh ->
                Column(Modifier.testTag("finance-gh-${gh.id}").semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        Text(gh.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(rupiah(gh.sales.amount), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
                    }
                    ShareBar(if (largest.signum() > 0) gh.sales.amount.divide(largest, 4, java.math.RoundingMode.HALF_UP).toFloat() else 0f,
                        FinanceChart.Partner, if (dark) FarmColors.ForestPill else FinanceChart.SalesTrack)
                    Text("${kgLabel(gh.sales.kg)} terjual · ${gh.sales.count} transaksi", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(onAll, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("finance-season-sales"), shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            Row(Modifier.padding(horizontal = FarmSpace.Large, vertical = FarmSpace.Medium), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Lihat semua penjualan Musim ${detail.sequence}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                FarmControlIcon(FarmIcon.ChevronRight, null)
            }
        }
    }
}

/** Weekly stacked bars (mitra at the base). Up to seven share the width; a longer season scrolls and starts at its latest week. */
@Composable private fun WeeklyChart(bars: List<WeekBar>, selected: Int, onSelect: (Int) -> Unit) {
    val area = 108f
    val heights = stackHeights(bars, area)
    val scroll = bars.size > 7
    val state = rememberScrollState()
    if (scroll) LaunchedEffect(bars.size) { state.scrollTo(state.maxValue) }
    val dark = LocalFarmTheme.current.dark
    Row(Modifier.fillMaxWidth().height(176.dp).then(if (scroll) Modifier.horizontalScroll(state) else Modifier)
        .semantics { contentDescription = "Penjualan per minggu. Ketuk batang untuk melihat rincian." },
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        bars.forEachIndexed { i, bar ->
            val on = i == selected
            Column((if (scroll) Modifier.width(44.dp) else Modifier.weight(1f)).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                .background(if (on) (if (dark) FarmColors.WarningSurfaceDark else FinanceChart.SalesPanel) else Color.Transparent)
                .clickable(role = Role.Button) { onSelect(i) }
                .semantics(mergeDescendants = true) {
                    this.selected = on
                    contentDescription = "Minggu ${bar.label}: ${shortMillions(bar.total())}, mitra ${shortMillions(bar.partner)}, " +
                        "retail ${shortMillions(bar.retail)}, perorangan ${shortMillions(bar.consumer)}"
                }
                .testTag("finance-week-$i"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(shortMillions(bar.total()), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold,
                    maxLines = 1, color = if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.widthIn(max = 34.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Top to bottom: tanpa tipe, perorangan, retail, mitra.
                    Segment(heights[i][3], FinanceChart.Untyped)
                    Segment(heights[i][2], FinanceChart.Consumer)
                    Segment(heights[i][1], FinanceChart.Retail)
                    Segment(heights[i][0], FinanceChart.Partner)
                }
                HorizontalDivider(Modifier.padding(top = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(bar.label, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun Segment(px: Float, color: Color) {
    if (px <= 0f) return
    val height by animateDpAsState(px.dp, tween(FarmMotion.State), label = "Tinggi batang")
    Box(Modifier.fillMaxWidth().height(height).background(color, RoundedCornerShape(3.dp)))
}

/** The tapped week split by buyer type. Rows of two so full rupiah amounts fit at 360 dp. */
@Composable private fun WeekPanel(bar: WeekBar, untyped: Boolean, dark: Boolean) {
    val label = if (dark) FarmColors.Sage else FinanceChart.SalesPanelLabel
    Column(Modifier.fillMaxWidth().background(if (dark) FarmColors.WarningSurfaceDark else FinanceChart.SalesPanel, RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = FarmSpace.Medium).testTag("finance-week-detail").semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        val cells = listOfNotNull("Minggu ${bar.label}" to bar.total(), "Mitra" to bar.partner, "Retail" to bar.retail, "Perorangan" to bar.consumer,
            ("Tanpa tipe" to bar.unspecified).takeIf { untyped })
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                row.forEach { (name, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, style = MaterialTheme.typography.labelMedium, color = label)
                        Text(rupiah(value), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold,
                            color = if (dark) MaterialTheme.colorScheme.onSurface else FarmColors.Ink)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** FC+VC of the whole farm against sales, one group per season number, scaled to the largest value on the chart. */
@Composable private fun CompareSection(bars: List<CompareBar>, excel: Boolean) {
    val dark = LocalFarmTheme.current.dark
    val fcColor = if (dark) FinanceChart.FcOnDark else FinanceChart.Fc
    val vcColor = if (dark) FinanceChart.VcOnDark else FinanceChart.Vc
    FinanceCard {
        Text("Antar musim · seluruh kebun", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Legend(listOf(Series("FC", fcColor), Series("VC", vcColor), Series("Penjualan", FinanceChart.Partner)))
        val scroll = bars.size > 4
        val area = 112f
        Row(Modifier.fillMaxWidth().then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier).testTag("finance-compare")
            .semantics { contentDescription = bars.joinToString("; ") { "${it.name}: TC ${it.tc?.let(::shortMillions) ?: "belum diisi"}, penjualan ${shortMillions(it.salesAmount)}" } },
            horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            bars.forEach { bar ->
                Column((if (scroll) Modifier.width(96.dp) else Modifier.weight(1f)).clip(RoundedCornerShape(14.dp))
                    .background(if (bar.selected) (if (dark) FarmColors.ForestSurface else FinanceChart.SelectedColumn) else Color.Transparent)
                    .padding(top = FarmSpace.Small), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.height(150.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(bar.tc?.let(::shortMillions) ?: "-", style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, maxLines = 1)
                            Column(Modifier.width(26.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                CompareSegment((bar.vc * area).dp, vcColor)
                                CompareSegment((bar.fc * area).dp, fcColor)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(shortMillions(bar.salesAmount), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, maxLines = 1)
                            Box(Modifier.width(26.dp)) { CompareSegment((bar.sales * area).dp, FinanceChart.Partner) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = FarmSpace.Small), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(bar.name, Modifier.padding(top = FarmSpace.Small, bottom = 10.dp), style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (bar.selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (bar.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(if (excel) "Hanya angka biaya dan penjualan. Hitungan lain bisa dibuat sendiri dari file Excel." else "Hanya angka biaya dan penjualan.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun CompareSegment(height: Dp, color: Color) {
    if (height <= 0.dp) return
    val shown by animateDpAsState(maxOf(height, 3.dp), tween(FarmMotion.State), label = "Tinggi batang musim")
    Box(Modifier.fillMaxWidth().height(shown).background(color, RoundedCornerShape(4.dp)))
}

@Composable internal fun Line(label: String, value: String) {
    Row { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")) }
}
