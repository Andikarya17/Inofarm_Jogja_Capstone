package com.inofarm.jogja.core.design

import java.math.BigDecimal

/** WIB greeting windows: 05.00-10.59 pagi, 11.00-14.59 siang, 15.00-18.29 sore, otherwise malam. */
internal fun greetingForWib(hour: Int, minute: Int): String = when (hour * 60 + minute) {
    in 300..659 -> "Selamat pagi"
    in 660..899 -> "Selamat siang"
    in 900..1109 -> "Selamat sore"
    else -> "Selamat malam"
}

/** Display-only split of two cash totals; null when neither is positive. The two fractions add up to 1. */
internal fun cashShare(receipts: BigDecimal?, payments: BigDecimal?): Pair<Float, Float>? {
    val a = receipts?.takeIf { it.signum() > 0 } ?: BigDecimal.ZERO
    val b = payments?.takeIf { it.signum() > 0 } ?: BigDecimal.ZERO
    val total = a.add(b)
    if (total.signum() == 0) return null
    val first = a.divide(total, 6, java.math.RoundingMode.HALF_UP).toFloat()
    return first to (1f - first)
}

/** The age shown big on a greenhouse card: HST when known, otherwise HSS; null when neither exists. */
internal fun ageFigure(hst: Int?, hss: Int?): Pair<Int, String>? = when {
    hst != null -> hst to "HST"
    hss != null -> hss to "HSS"
    else -> null
}

/** Badge text for the quick-action count; counts of 50 or more read "50+". */
internal fun badgeText(count: Int): String = if (count >= 50) "50+" else count.toString()

/** Up to two initials from a display name, upper case; "?" when the name is blank. */
internal fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).joinToString("") { it.first().uppercase() }
}

private val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

/** "Rabu, 11 Nov 2026" for the app bar. */
internal fun dateLabel(date: java.time.LocalDate): String =
    "${dayNames[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${monthNames[date.monthValue - 1]} ${date.year}"
