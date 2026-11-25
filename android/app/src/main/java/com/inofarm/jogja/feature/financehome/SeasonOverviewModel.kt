package com.inofarm.jogja.feature.financehome

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

/*
 * Keuangan per Musim ke-n (25 Nov 2026), read from GET reports/season-overview. By decision the model carries only costs and
 * sales: no profit or ratio fields, so the screen cannot show them by accident.
 */

private val WIB: ZoneId = ZoneId.of("Asia/Jakarta")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

private fun JSONObject.nullableMoney(key: String): BigDecimal? = if (!has(key) || isNull(key)) null else BigDecimal(getString(key))
private fun JSONObject.decimal(key: String): BigDecimal = if (!has(key) || isNull(key)) BigDecimal.ZERO else BigDecimal(getString(key))

/** Paid, active sales of one slice (a buyer type, a greenhouse, a week). */
data class SalesSlot(val amount: BigDecimal, val kg: BigDecimal, val count: Int) {
    companion object {
        val EMPTY = SalesSlot(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO, 0)
        fun from(body: JSONObject?) = if (body == null) EMPTY else SalesSlot(body.decimal("amount"), body.decimal("kg"), body.optInt("count", 0))
    }
}

/** Wire buyer types: consumer = Perorangan, retail = Retail, partner = Mitra; unspecified = older sales without a type. */
data class SalesByType(val total: SalesSlot, val consumer: SalesSlot, val retail: SalesSlot, val partner: SalesSlot, val unspecified: SalesSlot) {
    fun of(type: String?): SalesSlot = when (type) { "consumer" -> consumer; "retail" -> retail; "partner" -> partner; "unspecified" -> unspecified; else -> total }

    companion object {
        fun from(total: SalesSlot, types: JSONObject?) = SalesByType(total, SalesSlot.from(types?.optJSONObject("consumer")),
            SalesSlot.from(types?.optJSONObject("retail")), SalesSlot.from(types?.optJSONObject("partner")), SalesSlot.from(types?.optJSONObject("unspecified")))
    }
}

/** A null value means a cost line is still blank: shown as "Belum diisi", never as Rp0. */
data class CostTriple(val fc: BigDecimal?, val vc: BigDecimal?, val tc: BigDecimal?) {
    companion object {
        fun from(body: JSONObject?) = CostTriple(body?.nullableMoney("fc"), body?.nullableMoney("vc"), body?.nullableMoney("tc"))
    }
}

data class OverviewCostLine(val name: String, val costClass: String, val amount: BigDecimal?, val required: Boolean)

data class SeasonCosts(val sheetId: String, val version: Int, val lines: List<OverviewCostLine>, val perGreenhouse: CostTriple, val farm: CostTriple) {
    /**
     * Share of [line] in one greenhouse's TC, for the bar under each line. While TC is unknown the filled lines share
     * their own sum; a blank line has no bar.
     */
    fun costShare(line: OverviewCostLine): Float {
        val amount = line.amount ?: return 0f
        val base = perGreenhouse.tc ?: lines.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
        if (base.signum() <= 0) return 0f
        return amount.divide(base, 6, RoundingMode.HALF_UP).toFloat()
    }
}

/** One row of the season chips and the cross-season chart: farm totals of every greenhouse running that season number. */
data class SeasonTotals(val sequence: Int, val running: Boolean, val greenhouseCount: Int, val fc: BigDecimal?, val vc: BigDecimal?,
                        val tc: BigDecimal?, val salesAmount: BigDecimal, val salesKg: BigDecimal)

data class GreenhouseSales(val id: String, val name: String, val sales: SalesSlot)

data class SeasonWeek(val key: String, val label: String, val sales: SalesByType)

data class SeasonDetail(val sequence: Int, val from: LocalDate?, val until: LocalDate?, val running: Boolean, val costs: SeasonCosts,
                        val sales: SalesByType, val byGreenhouse: List<GreenhouseSales>, val weekly: List<SeasonWeek>) {
    /** "8 Sep 2026 sampai sekarang", "3 Mar sampai 28 Mei 2026". */
    fun dateLabel(): String {
        val start = from ?: return if (running) "Berjalan" else "Tanggal belum tersedia"
        if (running) return "${dayLabel(start)} sampai sekarang"
        val end = until ?: return "Mulai ${dayLabel(start)}"
        val first = if (start.year == end.year) "${start.dayOfMonth} ${MONTHS[start.monthValue - 1]}" else dayLabel(start)
        return "$first sampai ${dayLabel(end)}"
    }
}

/** Stacked weekly bar, bottom to top: mitra, retail, perorangan, tanpa tipe. */
data class WeekBar(val key: String, val label: String, val partner: BigDecimal, val retail: BigDecimal, val consumer: BigDecimal, val unspecified: BigDecimal) {
    fun total(): BigDecimal = partner + retail + consumer + unspecified
}

/** One season in "Antar musim": heights as a fraction of the largest FC+VC or sales value on the chart. */
data class CompareBar(val name: String, val fc: Float, val vc: Float, val sales: Float, val selected: Boolean,
                      val tc: BigDecimal?, val salesAmount: BigDecimal)

data class SeasonOverview(val sequences: List<SeasonTotals>, val selected: SeasonDetail?) {
    fun weeklyBars(): List<WeekBar> = selected?.weekly.orEmpty().map {
        WeekBar(it.key, it.label, it.sales.partner.amount, it.sales.retail.amount, it.sales.consumer.amount, it.sales.unspecified.amount)
    }

    /** Oldest season on the left. A blank FC or VC draws no bar for that part. */
    fun compareBars(): List<CompareBar> {
        val ordered = sequences.sortedBy { it.sequence }
        val largest = ordered.flatMap { listOf((it.fc ?: BigDecimal.ZERO) + (it.vc ?: BigDecimal.ZERO), it.salesAmount) }.maxOrNull()
        fun share(value: BigDecimal?): Float = if (value == null || largest == null || largest.signum() <= 0) 0f
            else value.divide(largest, 6, RoundingMode.HALF_UP).toFloat()
        return ordered.map { CompareBar("Musim ${it.sequence}", share(it.fc), share(it.vc), share(it.salesAmount), it.sequence == selected?.sequence, it.tc, it.salesAmount) }
    }

    companion object {
        fun from(data: JSONObject): SeasonOverview {
            val rows = data.getJSONArray("sequences")
            val sequences = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                SeasonTotals(row.getInt("sequence"), row.optString("status") == "running", row.optInt("greenhouse_count", 0),
                    row.nullableMoney("fc"), row.nullableMoney("vc"), row.nullableMoney("tc"), row.decimal("sales_amount"), row.decimal("sales_kg"))
            }
            val selected = if (!data.has("selected") || data.isNull("selected")) null else detail(data.getJSONObject("selected"))
            return SeasonOverview(sequences, selected)
        }

        private fun detail(body: JSONObject): SeasonDetail {
            val range = body.optJSONObject("date_range")
            fun date(key: String) = range?.takeUnless { it.isNull(key) }?.optString(key)?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
            val sheet = body.getJSONObject("cost_sheet")
            val lineRows = sheet.getJSONArray("lines")
            val lines = (0 until lineRows.length()).map { index ->
                val line = lineRows.getJSONObject(index)
                OverviewCostLine(line.getString("name"), line.getString("cost_class"), line.nullableMoney("amount"), line.optBoolean("is_required"))
            }
            val costs = SeasonCosts(sheet.optString("id"), sheet.optInt("version"), lines,
                CostTriple.from(sheet.optJSONObject("per_greenhouse")), CostTriple.from(sheet.optJSONObject("farm")))
            val sales = body.getJSONObject("sales")
            val ghRows = body.optJSONArray("by_greenhouse")
            val byGreenhouse = (0 until (ghRows?.length() ?: 0)).map { index ->
                val row = ghRows!!.getJSONObject(index)
                GreenhouseSales(row.getString("greenhouse_id"), row.optString("name"), SalesSlot.from(row))
            }
            val weekRows = body.optJSONArray("weekly")
            val weekly = (0 until (weekRows?.length() ?: 0)).map { index ->
                val row = weekRows!!.getJSONObject(index)
                SeasonWeek(row.getString("key"), row.getString("label"), SalesByType.from(SalesSlot.from(row), row.optJSONObject("by_type")))
            }
            return SeasonDetail(body.getInt("sequence"), date("from"), date("until"), range?.optBoolean("running") ?: false, costs,
                SalesByType.from(SalesSlot.from(sales), sales.optJSONObject("by_type")), byGreenhouse, weekly)
        }
    }
}

/** Heights per bar (mitra, retail, perorangan, tanpa tipe) scaled to the tallest bar; a non-zero part is at least 3 px. */
fun stackHeights(bars: List<WeekBar>, maxPx: Float): List<List<Float>> {
    val tallest = bars.maxOfOrNull { it.total() }?.takeIf { it.signum() > 0 } ?: return bars.map { listOf(0f, 0f, 0f, 0f) }
    fun height(value: BigDecimal) = if (value.signum() <= 0) 0f else maxOf(3f, value.divide(tallest, 6, RoundingMode.HALF_UP).toFloat() * maxPx)
    return bars.map { listOf(height(it.partner), height(it.retail), height(it.consumer), height(it.unspecified)) }
}

/** Buyer type label for wire values; null or unknown is an older sale without a type. */
fun buyerTypeLabel(type: String?): String = when (type) { "consumer" -> "Perorangan"; "retail" -> "Retail"; "partner" -> "Mitra"; else -> "Tanpa tipe" }

/** One paid sale in "Penjualan Musim n", from the cached server body. */
data class SeasonSaleRow(val id: String, val date: LocalDate, val paidAt: OffsetDateTime, val type: String?, val name: String,
                         val greenhouseId: String, val greenhouseName: String, val varieties: List<Pair<String, BigDecimal>>,
                         val amount: BigDecimal, val kg: BigDecimal) {
    fun meta(): String {
        val fruit = varieties.joinToString(", ") { (name, kg) -> "$name ${kgLabel(kg)}" }.ifBlank { kgLabel(kg) }
        return listOf(buyerTypeLabel(type), greenhouseName, fruit).filter { it.isNotBlank() }.joinToString(" · ")
    }

    companion object {
        /** Active, paid sales only (the same rows the overview counts), newest first. Bodies that cannot be read are skipped. */
        fun list(bodies: List<JSONObject>, greenhouseNames: Map<String, String>): List<SeasonSaleRow> = bodies.mapNotNull { body ->
            runCatching {
                if (body.optString("record_state", "active") != "active" || body.optString("payment_status", "paid") != "paid") return@runCatching null
                val paid = OffsetDateTime.parse(body.getString("paid_at"))
                val type = body.optString("customer_type").takeUnless { body.isNull("customer_type") || it.isBlank() }
                val lines = body.optJSONArray("lines") ?: body.optJSONArray("items")
                val varieties = linkedMapOf<String, BigDecimal>()
                for (index in 0 until (lines?.length() ?: 0)) {
                    val line = lines!!.getJSONObject(index)
                    val variety = line.optString("variety_name").takeUnless { line.isNull("variety_name") || it.isBlank() } ?: continue
                    val kg = line.decimal("quantity_kg")
                    varieties[variety] = (varieties[variety] ?: BigDecimal.ZERO) + kg
                }
                val gh = body.getString("greenhouse_id")
                SeasonSaleRow(body.getString("id"), paid.atZoneSameInstant(WIB).toLocalDate(), paid, type,
                    body.optString("customer_name").trim().ifBlank { "Tanpa nama" }, gh, greenhouseNames[gh].orEmpty(),
                    varieties.toList(), BigDecimal(body.getString("total_amount")), body.decimal("total_weight_kg"))
            }.getOrNull()
        }.sortedWith(compareByDescending<SeasonSaleRow> { it.paidAt.toInstant() }.thenBy { it.id })
    }
}

data class SaleDay(val date: LocalDate, val total: BigDecimal, val rows: List<SeasonSaleRow>)

fun groupByDay(rows: List<SeasonSaleRow>): List<SaleDay> = rows.groupBy { it.date }.map { (date, list) ->
    SaleDay(date, list.fold(BigDecimal.ZERO.setScale(2)) { sum, row -> sum + row.amount }, list)
}.sortedByDescending { it.date }

/** [type] null = all types, "unspecified" = without a type; [greenhouse] null = all. The query matches the buyer name. */
fun filterSales(rows: List<SeasonSaleRow>, query: String, type: String?, greenhouse: String?): List<SeasonSaleRow> {
    val text = query.trim().lowercase()
    return rows.filter { row ->
        (text.isEmpty() || row.name.lowercase().contains(text)) &&
            (type == null || (row.type ?: "unspecified") == type) &&
            (greenhouse == null || row.greenhouseId == greenhouse)
    }
}

fun dayLabel(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"

/** "Rp24.500.000" (no decimals when whole), Indonesian grouping. */
fun rupiah(value: BigDecimal): String {
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    val scaled = value.setScale(2, RoundingMode.HALF_UP)
    format.minimumFractionDigits = if (scaled.stripTrailingZeros().scale() > 0) 2 else 0
    format.maximumFractionDigits = 2
    return (if (scaled.signum() < 0) "−Rp" else "Rp") + format.format(scaled.abs())
}

/** A blank cost is "Belum diisi", never Rp0. */
fun moneyOrBlank(value: BigDecimal?): String = value?.let(::rupiah) ?: "Belum diisi"

/** "24,5 jt" for chart labels. */
fun shortMillions(value: BigDecimal): String {
    val millions = value.divide(BigDecimal(1_000_000), 1, RoundingMode.HALF_UP)
    return millions.stripTrailingZeros().toPlainString().replace('.', ',') + " jt"
}

/** "1.573 kg", "2,5 kg": grouped, at most two decimals. */
fun kgLabel(value: BigDecimal): String {
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    format.maximumFractionDigits = 2
    return format.format(value.setScale(2, RoundingMode.HALF_UP)) + " kg"
}

/** Plain kg without grouping, kept for older callers (buyer list). */
internal fun kg(value: BigDecimal): String = value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
    .toPlainString().replace('.', ',') + " kg"
