package com.inofarm.jogja.feature.financehome

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Chart period on the Keuangan screen; each shows a fixed window ending today (WIB). */
enum class IncomePeriod(val wire: String, val label: String) {
    WEEK("week", "Minggu"), MONTH("month", "Bulan"), YEAR("year", "Tahun");

    fun range(today: LocalDate = LocalDate.now(ZoneId.of("Asia/Jakarta"))): Pair<LocalDate, LocalDate> = when (this) {
        WEEK -> today.with(DayOfWeek.MONDAY).minusWeeks(7) to today
        MONTH -> today.withDayOfMonth(1).minusMonths(5) to today
        YEAR -> today.withDayOfYear(1).minusYears(2) to today
    }
}

/** One bucket of paid income, split by buyer type (consumer = perorangan, partner = mitra). */
data class IncomeBar(val key: String, val label: String, val consumer: BigDecimal, val partner: BigDecimal, val unspecified: BigDecimal,
                     val consumerKg: BigDecimal, val partnerKg: BigDecimal, val unspecifiedKg: BigDecimal) {
    fun total(): BigDecimal = consumer + partner + unspecified
    fun weightKg(): BigDecimal = consumerKg + partnerKg + unspecifiedKg
    fun hasUnspecified() = unspecified.signum() > 0

    /** Whole-percent share of mitra in this bucket; 0 when the bucket is empty. */
    fun partnerShare(): Int = if (total().signum() == 0) 0 else partner.multiply(BigDecimal(100)).divide(total(), 0, RoundingMode.HALF_UP).toInt()
    fun consumerShare(): Int = if (total().signum() == 0) 0 else consumer.multiply(BigDecimal(100)).divide(total(), 0, RoundingMode.HALF_UP).toInt()

    /** Average price per kg for the detail panel, or null without sold weight. */
    fun averagePerKg(): BigDecimal? = if (weightKg().signum() == 0) null else total().divide(weightKg(), 0, RoundingMode.HALF_UP)

    fun compareTo(previous: IncomeBar?): String {
        previous ?: return "periode pertama di grafik"
        val diff = total() - previous.total()
        return "${if (diff.signum() >= 0) "naik" else "turun"} ${rupiah(diff.abs())} dari ${previous.label}"
    }

    companion object {
        fun list(data: JSONObject): List<IncomeBar> {
            val buckets = data.getJSONArray("buckets")
            return (0 until buckets.length()).map { index ->
                val bucket = buckets.getJSONObject(index)
                val types = bucket.getJSONObject("by_type")
                fun money(type: String) = BigDecimal(types.getJSONObject(type).getString("receipts"))
                fun kg(type: String) = BigDecimal(types.getJSONObject(type).getString("weight_kg"))
                IncomeBar(bucket.getString("key"), bucket.getString("label"), money("consumer"), money("partner"), money("unspecified"),
                    kg("consumer"), kg("partner"), kg("unspecified"))
            }
        }
    }
}

/** Heights (partner, consumer, unspecified) per bar, scaled to the tallest; a non-zero segment is at least 3 px. */
fun barHeights(bars: List<IncomeBar>, maxPx: Float): List<Triple<Float, Float, Float>> {
    val tallest = bars.maxOfOrNull { it.total() }?.takeIf { it.signum() > 0 } ?: return bars.map { Triple(0f, 0f, 0f) }
    fun height(value: BigDecimal) = if (value.signum() == 0) 0f
        else maxOf(3f, value.divide(tallest, 6, RoundingMode.HALF_UP).toFloat() * maxPx)
    return bars.map { Triple(height(it.partner), height(it.consumer), height(it.unspecified)) }
}

/** "Rp24.500.000" (no decimals when whole), Indonesian grouping. */
fun rupiah(value: BigDecimal): String {
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    val scaled = value.setScale(2, RoundingMode.HALF_UP)
    format.minimumFractionDigits = if (scaled.stripTrailingZeros().scale() > 0) 2 else 0
    format.maximumFractionDigits = 2
    return "Rp" + format.format(scaled)
}

/** "24,5 jt" for chart labels. */
fun shortMillions(value: BigDecimal): String {
    val millions = value.divide(BigDecimal(1_000_000), 1, RoundingMode.HALF_UP)
    return millions.stripTrailingZeros().toPlainString().replace('.', ',') + " jt"
}
