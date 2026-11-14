package com.inofarm.jogja.feature.context

/** Where the last PPM reading sits against the reference that applied when it was taken. */
internal enum class PpmPosition { BELOW, ABOVE, WITHIN, UNKNOWN }

/** Lowest and highest accepted PPM: a range wins; a single value needs a tolerance, otherwise there is no bound. */
internal fun ppmBounds(minimum: String?, maximum: String?, single: String?, tolerance: String?): Pair<java.math.BigDecimal, java.math.BigDecimal>? {
    val min = minimum?.toBigDecimalOrNull()
    val max = maximum?.toBigDecimalOrNull()
    if (min != null && max != null) return min to max
    val target = single?.toBigDecimalOrNull() ?: return null
    val spread = tolerance?.toBigDecimalOrNull() ?: return null
    return (target - spread) to (target + spread)
}

internal fun ppmPosition(minimum: String?, maximum: String?, single: String?, tolerance: String?, ppm: String): PpmPosition {
    val value = ppm.toBigDecimalOrNull() ?: return PpmPosition.UNKNOWN
    val (low, high) = ppmBounds(minimum, maximum, single, tolerance) ?: return PpmPosition.UNKNOWN
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
