package com.inofarm.jogja.core.design

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * One way to show dates, times, money and quantities on every pengelola screen (QA 28 Nov 2026): never the server's
 * raw text ("2026-11-16", "15000.00", "2.500000", "2026-11-29T00:00:00.000000Z"). A value that cannot be read is
 * shown as it is rather than hidden, so nothing silently disappears.
 */
object FarmFormat {
    private val WIB: ZoneId = ZoneId.of("Asia/Jakarta")
    private val ID = Locale.forLanguageTag("id-ID")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

    private fun day(date: LocalDate) = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"

    /** "16 Nov 2026" from "2026-11-16" or from a timestamp (read in WIB). */
    fun date(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return "Belum diisi"
        if (value.length == 10) runCatching { return day(LocalDate.parse(value)) }
        return runCatching { day(OffsetDateTime.parse(value).atZoneSameInstant(WIB).toLocalDate()) }.getOrDefault(value)
    }

    /** "28 Nov 2026 sampai 27 Nov 2027" for a period whose stored end is exclusive (the server's convention). */
    fun period(start: String?, endExclusive: String?): String {
        val end = runCatching { LocalDate.parse(endExclusive).minusDays(1).toString() }.getOrNull()
        return "${date(start)} sampai ${end?.let(::date) ?: date(endExclusive)}"
    }

    /** "28 Nov 2026 12:17 WIB". */
    fun dateTime(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return "Belum diisi"
        return runCatching {
            val at = OffsetDateTime.parse(value).atZoneSameInstant(WIB)
            "${day(at.toLocalDate())} %02d:%02d WIB".format(at.hour, at.minute)
        }.getOrDefault(date(value))
    }

    /** "Rp15.000", "Rp1.250,50", "−Rp52.592,04". */
    fun money(value: String?): String = value?.trim()?.toBigDecimalOrNull()?.let(::money) ?: "Belum diisi"
    fun money(value: BigDecimal): String {
        val scaled = value.setScale(2, RoundingMode.HALF_UP)
        val format = NumberFormat.getNumberInstance(ID).apply {
            minimumFractionDigits = if (scaled.stripTrailingZeros().scale() > 0) 2 else 0; maximumFractionDigits = 2
        }
        return (if (scaled.signum() < 0) "−Rp" else "Rp") + format.format(scaled.abs())
    }

    /** "2,5", "1.250": grouped, at most [maxFraction] decimals, minus sign as "−". */
    fun number(value: String?, maxFraction: Int = 2): String = value?.trim()?.toBigDecimalOrNull()?.let { number(it, maxFraction) } ?: (value ?: "")
    fun number(value: BigDecimal, maxFraction: Int = 2): String {
        val format = NumberFormat.getNumberInstance(ID).apply { maximumFractionDigits = maxFraction; minimumFractionDigits = 0 }
        val text = format.format(value.setScale(maxFraction, RoundingMode.HALF_UP).abs())
        return if (value.signum() < 0) "−$text" else text
    }

    /** "2,5 kg". */
    fun kg(value: String?): String = value?.trim()?.toBigDecimalOrNull()?.let { number(it) + " kg" } ?: "Belum diisi"

    /** "9,70%" from a ratio given in percent. */
    fun percent(value: String?): String = value?.trim()?.toBigDecimalOrNull()?.let { number(it) + "%" } ?: "Belum diisi"

    /**
     * A server decimal put back into a form field the way a person types it: "15000.00" → "15000",
     * "4.000000" → "4", "2.5" → "2,5". Grouping is left to the field.
     */
    fun input(value: String?): String {
        val number = value?.trim()?.toBigDecimalOrNull() ?: return value.orEmpty()
        return number.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString().replace('.', ',')
    }
}
