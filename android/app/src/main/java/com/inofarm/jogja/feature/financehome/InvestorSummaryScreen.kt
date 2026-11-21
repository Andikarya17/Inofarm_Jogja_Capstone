package com.inofarm.jogja.feature.financehome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.auth.userMessage
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/** Totals over season numbers for the investor page; TC is null when any season still has a blank cost line. */
data class InvestorTotals(val tr: BigDecimal, val tc: BigDecimal?, val profit: BigDecimal?, val rc: BigDecimal?)

fun investorTotals(bySequence: JSONArray): InvestorTotals {
    var tr = BigDecimal.ZERO.setScale(2)
    var tc: BigDecimal? = BigDecimal.ZERO.setScale(2)
    for (i in 0 until bySequence.length()) {
        val row = bySequence.getJSONObject(i)
        tr += BigDecimal(row.getString("TR"))
        tc = if (tc == null || row.isNull("TC")) null else tc + BigDecimal(row.getString("TC"))
    }
    val profit = tc?.let { tr - it }
    val rc = tc?.takeIf { it.signum() > 0 }?.let { tr.divide(it, 2, RoundingMode.HALF_UP) }
    return InvestorTotals(tr, tc, profit, rc)
}

/** Read-only summary to show prospective investors: profit per season number and per greenhouse, on standard costs. */
@Composable internal fun InvestorSummaryScreen(session: FinanceSession, ticket: Long, repository: FinanceHomeRepository, onBack: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.scope.key) { runCatching { repository.profitSummary(session, ticket) }.onSuccess { data = it }.onFailure { error = userMessage(it) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = FarmSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack, Modifier.size(FarmSpace.Touch).testTag("investor-back")) { FarmControlIcon(FarmIcon.Close, "Kembali ke Keuangan") }
            Column(Modifier.weight(1f)) {
                Text("Ringkasan untuk investor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text("Semua greenhouse · biaya standar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { Notice("Ringkasan belum bisa dimuat. $it", true) }
        val body = data ?: run { if (error == null) Text("Memuat ringkasan…"); return@Column }
        val sequences = body.getJSONArray("by_sequence")
        if (sequences.length() == 0) { Text("Belum ada musim yang tercatat."); return@Column }
        val totals = investorTotals(sequences)
        FarmHeroCard {
            Text((if (totals.profit?.signum() == -1) "Rugi tercatat" else "Laba tercatat") + ", ${sequences.length()} musim", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(totals.profit?.let(::rupiah) ?: "Biaya belum lengkap", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("investor-profit"))
            Text("Penerimaan ${rupiah(totals.tr)} · biaya ${totals.tc?.let(::rupiah) ?: "belum lengkap"}" + (totals.rc?.let { " · R/C ${it.toPlainString().replace('.', ',')}" } ?: ""),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
            Text("Penerimaan dan biaya per musim", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SeasonBars(sequences)
        }
        val latest = sequences.getJSONObject(sequences.length() - 1).getInt("sequence")
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Text("Per greenhouse, Musim $latest", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Row { Text("GH", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium); Text("Penerimaan", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
                    Text("Laba", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium); Text("R/C", Modifier.weight(0.6f), style = MaterialTheme.typography.labelMedium) }
                val rows = body.getJSONArray("by_greenhouse")
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    if (row.optInt("sequence", -1) != latest) continue
                    HorizontalDivider()
                    Row(Modifier.semantics(mergeDescendants = true) {}) {
                        Text(row.getString("name"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(shortMillions(BigDecimal(row.getString("TR"))), Modifier.weight(1.2f), style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                        Text(if (row.isNull("profit")) "-" else shortMillions(BigDecimal(row.getString("profit"))), Modifier.weight(1.2f), fontWeight = FontWeight.Bold,
                            color = if (!row.isNull("profit") && row.getString("profit").startsWith("-")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                        Text(if (row.isNull("rc")) "-" else BigDecimal(row.getString("rc")).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ','),
                            Modifier.weight(0.6f), style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Text("Kelayakan investasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                Text("NPV, IRR dan lama balik modal dihitung dari skenario investasi (modal awal dan arus kas per tahun). Buka lewat ikon mata › Buka pencatatan biaya nyata › Alat keuangan › Investasi.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Dihitung langsung dari biaya standar dan penjualan lunas yang tercatat; bukan laporan terbit.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SeasonBars(sequences: JSONArray) {
    val values = (0 until sequences.length()).map { sequences.getJSONObject(it) }
    val max = values.maxOf { maxOf(BigDecimal(it.getString("TR")), if (it.isNull("TC")) BigDecimal.ZERO else BigDecimal(it.getString("TC"))) }
        .takeIf { it.signum() > 0 } ?: BigDecimal.ONE
    fun height(v: BigDecimal) = (v.divide(max, 4, RoundingMode.HALF_UP).toFloat() * 110f).dp
    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Large), verticalAlignment = Alignment.Bottom) {
        values.forEach { row ->
            val tr = BigDecimal(row.getString("TR"))
            val tc = if (row.isNull("TC")) null else BigDecimal(row.getString("TC"))
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = "Musim ${row.getInt("sequence")}: penerimaan ${shortMillions(tr)}, biaya ${tc?.let(::shortMillions) ?: "belum lengkap"}"
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(shortMillions(tr), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Box(Modifier.width(28.dp).height(height(tr)).background(FarmColors.Sage, RoundedCornerShape(6.dp)))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tc?.let(::shortMillions) ?: "-", style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.width(28.dp).height(tc?.let(::height) ?: 0.dp).background(androidx.compose.ui.graphics.Color(0xFF9BAC9C), RoundedCornerShape(6.dp)))
                    }
                }
                Text("Musim ${row.getInt("sequence")}", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Large)) {
        Text("Batang terang: penerimaan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Batang abu: biaya standar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
