package com.inofarm.jogja.feature.financehome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.auth.userMessage
import org.json.JSONObject
import java.math.BigDecimal

/** Forest-scheme colours for the stacked bars, taken from DESIGN.md tokens (sage, forest-outline, forest-warning). */
private val MitraBar = FarmColors.Sage
private val PeroranganBar = Color(0xFF9BAC9C)
private val UntypedBar = Color(0xFFDCC28C)

private enum class FinancePage { OVERVIEW, COSTS, INVESTOR, LEDGER, CUSTOMERS }

/**
 * Manager Keuangan tab (Inofarm 20-21 Nov 2026): stacked income chart by buyer type, running-season profit on standard
 * costs, shortcuts, and an eye toggle that shows or hides the older real-cost ledger.
 */
@Composable internal fun FinanceOverviewScreen(
    session: FinanceSession, ticket: Long, repository: FinanceHomeRepository, preference: FinanceVisibilityPreference,
    refresh: Long, onRecordSale: () -> Unit, customers: com.inofarm.jogja.feature.customers.CustomerRepository,
    ledger: @Composable (onBack: () -> Unit) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(FinancePage.OVERVIEW) }
    BackHandler(page != FinancePage.OVERVIEW) { page = FinancePage.OVERVIEW }
    when (page) {
        FinancePage.COSTS -> StandardCostScreen(session, ticket, repository) { page = FinancePage.OVERVIEW }
        FinancePage.INVESTOR -> InvestorSummaryScreen(session, ticket, repository) { page = FinancePage.OVERVIEW }
        FinancePage.LEDGER -> ledger { page = FinancePage.OVERVIEW }
        FinancePage.CUSTOMERS -> com.inofarm.jogja.feature.customers.CustomerListScreen(session, ticket, customers) { page = FinancePage.OVERVIEW }
        FinancePage.OVERVIEW -> Overview(session, ticket, repository, preference, refresh, onRecordSale) { page = it }
    }
}

@Composable private fun Overview(
    session: FinanceSession, ticket: Long, repository: FinanceHomeRepository, preference: FinanceVisibilityPreference,
    refresh: Long, onRecordSale: () -> Unit, open: (FinancePage) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(IncomePeriod.MONTH) }
    var bars by remember { mutableStateOf<List<IncomeBar>?>(null) }
    var selected by rememberSaveable(period) { mutableStateOf(-1) }
    var profit by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showReal by remember(session.scope.key) { mutableStateOf(preference.showRealCosts(session.scope.key)) }
    LaunchedEffect(period, refresh, session.scope.key) {
        error = null
        runCatching { repository.income(session, ticket, period) }.onSuccess { bars = it }.onFailure { bars = null; error = userMessage(it) }
    }
    LaunchedEffect(refresh, session.scope.key) {
        runCatching { repository.profitSummary(session, ticket) }.onSuccess { profit = it }.onFailure { profit = null; error = error ?: userMessage(it) }
    }
    val list = bars.orEmpty()
    val index = if (selected in list.indices) selected else list.lastIndex
    val current = list.getOrNull(index)

    // The shell already pads the page and shows "Keuangan" in its app bar.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(top = FarmSpace.Small, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Penjualan lunas dan biaya standar", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton({ showReal = !showReal; preference.setShowRealCosts(session.scope.key, showReal) },
                Modifier.size(FarmSpace.Touch).testTag("finance-real-costs-toggle")) {
                FarmControlIcon(if (showReal) FarmIcon.Eye else FarmIcon.EyeOff, if (showReal) "Sembunyikan biaya nyata" else "Tampilkan biaya nyata")
            }
        }
        FarmSegmentedTabs(IncomePeriod.entries.map { it.wire to it.label }, period.wire, { id -> period = IncomePeriod.entries.single { it.wire == id } },
            tagPrefix = "finance-period-")
        FarmHeroCard(Modifier.animateContentSize(tween(FarmMotion.State))) {
            Text(when (period) { IncomePeriod.WEEK -> "Pemasukan penjualan per minggu"; IncomePeriod.MONTH -> "Pemasukan penjualan per bulan"; IncomePeriod.YEAR -> "Pemasukan penjualan per tahun" },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
            when {
                bars == null && error == null -> Text("Memuat pemasukan…", style = MaterialTheme.typography.bodyMedium)
                bars == null -> Text("Pemasukan belum bisa dimuat. $error", style = MaterialTheme.typography.bodyMedium)
                current == null -> Text("Belum ada penjualan lunas di rentang ini.", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Text(rupiah(current.total()), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("finance-income-total"))
                    Text("${current.label} · ${current.compareTo(list.getOrNull(index - 1))}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Legend(list.any { it.hasUnspecified() })
                    IncomeBarChart(list, index) { selected = it }
                    DetailPanel(current)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            ActionTile("Catat penjualan", FarmIcon.Add, true, Modifier.weight(1f).testTag("finance-record-sale"), onRecordSale)
            ActionTile("Biaya standar", FarmIcon.CheckList, false, Modifier.weight(1f).testTag("finance-standard-costs")) { open(FinancePage.COSTS) }
            ActionTile("Ringkasan investor", FarmIcon.BarChart, false, Modifier.weight(1f).testTag("finance-investor")) { open(FinancePage.INVESTOR) }
        }
        if (current != null) BuyerCard(current) { open(FinancePage.CUSTOMERS) }
        ProfitCard(profit?.optJSONObject("open"))
        if (showReal) OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Text("Biaya nyata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Pembayaran, alokasi, aset dan penyusutan tetap bisa dicatat. Laba musim tidak memakainya; laba memakai biaya standar.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ open(FinancePage.LEDGER) }, shape = FarmShape.Control, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-open-ledger")) {
                    Text("Buka pencatatan biaya nyata")
                }
            }
        }
    }
}

@Composable private fun Legend(untyped: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Large), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MitraBar, "Mitra")
        LegendItem(PeroranganBar, "Perorangan")
        if (untyped) LegendItem(UntypedBar, "Tanpa tipe")
    }
}

@Composable private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tappable stacked bars: mitra at the bottom, perorangan on top. Each bar is a button with its figures as its label. */
@Composable private fun IncomeBarChart(bars: List<IncomeBar>, selected: Int, onSelect: (Int) -> Unit) {
    val area = 112f
    val heights = barHeights(bars, area)
    Row(Modifier.fillMaxWidth().height(176.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        bars.forEachIndexed { i, bar ->
            val on = i == selected
            val (mitra, perorangan, untyped) = heights[i]
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                .background(if (on) FarmColors.ForestSurface else Color.Transparent)
                .clickable(role = Role.Button) { onSelect(i) }
                .semantics(mergeDescendants = true) {
                    this.selected = on
                    contentDescription = "${bar.label}: total ${shortMillions(bar.total())}, mitra ${shortMillions(bar.partner)}, perorangan ${shortMillions(bar.consumer)}"
                }
                .testTag("finance-bar-${bar.key}"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (on) Text(shortMillions(bar.total()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.widthIn(max = 34.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Segment(untyped, UntypedBar)
                    Segment(perorangan, PeroranganBar)
                    Segment(mitra, MitraBar)
                }
                HorizontalDivider(color = FarmColors.ForestPill)
                Text(bar.label, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun Segment(px: Float, color: Color) {
    if (px <= 0f) return
    val height by animateDpAsState(px.dp, tween(FarmMotion.State), label = "Tinggi batang")
    Box(Modifier.fillMaxWidth().height(height).background(color, RoundedCornerShape(4.dp)))
}

@Composable private fun DetailPanel(bar: IncomeBar) {
    // Rows, not columns: full rupiah amounts must fit at 360 dp and large font scales.
    Column(Modifier.fillMaxWidth().background(FarmColors.ForestSurface, RoundedCornerShape(16.dp)).padding(FarmSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        Fact("Mitra · ${kg(bar.partnerKg)}", rupiah(bar.partner))
        Fact("Perorangan · ${kg(bar.consumerKg)}", rupiah(bar.consumer))
        if (bar.hasUnspecified()) Fact("Tanpa tipe · ${kg(bar.unspecifiedKg)}", rupiah(bar.unspecified))
        HorizontalDivider(color = FarmColors.ForestPill)
        Fact("Total ${kg(bar.weightKg())}", bar.averagePerKg()?.let { "rata-rata ${rupiah(it)}/kg" } ?: "Belum ada bobot")
    }
}

@Composable private fun Fact(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
    }
}

internal fun kg(value: BigDecimal): String = value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
    .toPlainString().replace('.', ',') + " kg"

@Composable private fun ActionTile(label: String, icon: FarmIcon, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick, modifier.heightIn(min = 84.dp), shape = RoundedCornerShape(16.dp),
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            FarmControlIcon(icon, null)
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun BuyerCard(bar: IncomeBar, onList: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Per tipe pembeli, ${bar.label}", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onList, Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-customers")) { Text("Daftar pembeli") }
            }
            BuyerRow("Mitra", bar.partner, bar.partnerKg, bar.partnerShare())
            BuyerRow("Perorangan", bar.consumer, bar.consumerKg, bar.consumerShare())
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, FarmShape.Control).padding(FarmSpace.Medium)) {
                Text("Retail", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Belum tersedia", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun BuyerRow(name: String, amount: BigDecimal, weight: BigDecimal, share: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row { Text(name, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(rupiah(amount), fontWeight = FontWeight.Bold, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")) }
        LinearProgressIndicator({ share / 100f }, Modifier.fillMaxWidth().height(8.dp), color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
        Text("${kg(weight)} · $share% dari pemasukan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ProfitCard(open: JSONObject?) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("finance-profit")) {
        Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (open == null) "Laba musim berjalan" else "Laba musim berjalan · ${open.optInt("greenhouse_count")} GH",
                    Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (open != null && !open.optBoolean("complete")) FarmStatusChip("Biaya belum lengkap", tone = FarmTone.Amber)
            }
            if (open == null) { Text("Memuat laba…", style = MaterialTheme.typography.bodyMedium); return@Column }
            fun money(key: String) = if (open.isNull(key)) "Belum bisa dihitung" else rupiah(BigDecimal(open.getString(key)))
            Pair("Penerimaan (TR)", money("TR")).let { (k, v) -> Line(k, v) }
            Pair("Biaya total (TC)", money("TC")).let { (k, v) -> Line(k, v) }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Laba", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(money("profit"), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Tile("R/C", if (open.isNull("rc")) "-" else BigDecimal(open.getString("rc")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ','), Modifier.weight(1f))
                Tile("BEP harga", if (open.isNull("bep_price")) "-" else rupiah(BigDecimal(open.getString("bep_price")).setScale(0, java.math.RoundingMode.HALF_UP)) + "/kg", Modifier.weight(1f))
                Tile("BEP produksi", if (open.isNull("bep_kg")) "-" else kg(BigDecimal(open.getString("bep_kg")).setScale(0, java.math.RoundingMode.HALF_UP)), Modifier.weight(1f))
            }
            Text("Dihitung dari biaya standar musim berjalan dan penjualan lunas. Terjual ${kg(BigDecimal(open.optString("sold_kg", "0")))}.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun Line(label: String, value: String) {
    Row { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")) }
}

@Composable private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, FarmShape.Tile).padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
    }
}
