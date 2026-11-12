package com.inofarm.jogja.feature.context

/** Where the last PPM reading sits against the reference that applied when it was taken. */
internal enum class PpmPosition { BELOW, ABOVE, WITHIN, UNKNOWN }

/**
 * A range (minimum and maximum) wins. A single value needs a tolerance to say anything;
 * without one the position is unknown, never "within".
 */
internal fun ppmPosition(minimum: String?, maximum: String?, single: String?, tolerance: String?, ppm: String): PpmPosition {
    val value = ppm.toBigDecimalOrNull() ?: return PpmPosition.UNKNOWN
    val min = minimum?.toBigDecimalOrNull()
    val max = maximum?.toBigDecimalOrNull()
    val low: java.math.BigDecimal
    val high: java.math.BigDecimal
    if (min != null && max != null) {
        low = min
        high = max
    } else {
        val target = single?.toBigDecimalOrNull() ?: return PpmPosition.UNKNOWN
        val spread = tolerance?.toBigDecimalOrNull() ?: return PpmPosition.UNKNOWN
        low = target - spread
        high = target + spread
    }
    return when {
        value < low -> PpmPosition.BELOW
        value > high -> PpmPosition.ABOVE
        else -> PpmPosition.WITHIN
    }
}

/** The schedule's first non-blank note line names the job; the activity type stands in when there is none. */
internal fun taskTitle(notes: String?, typeLabel: String): String =
    notes?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: typeLabel

internal fun daySession(hourWib: Int): String = if (hourWib < 12) "Pagi" else "Sore"

/** What the Beranda ordering needs to know about one greenhouse. */
internal data class HomeRank(val active: Boolean, val hasGroup: Boolean, val tasksLeft: Int, val checkedToday: Boolean, val name: String)

/** Greenhouses with open work first, finished ones next, those without a current planting after, inactive last; the name breaks ties. */
internal fun <T> orderForHome(rows: List<T>, rank: (T) -> HomeRank): List<T> = rows.sortedWith(
    compareBy<T>({
        val r = rank(it)
        when {
            !r.active -> 3
            !r.hasGroup -> 2
            r.tasksLeft > 0 || !r.checkedToday -> 0
            else -> 1
        }
    }, { rank(it).name.lowercase() })
)
