package com.inofarm.jogja.feature.financehome

import org.json.JSONObject

/**
 * The cached seasons that belong to "Musim n". Seasons synced before migration 23 have no `sequence` in their
 * feed body, so it is derived the way the server backfilled it: per greenhouse, ordered by start date then id.
 */
object SeasonSequence {
    fun seasonsFor(seasons: List<JSONObject>, sequence: Int): List<JSONObject> {
        val derived = HashMap<String, Int>()
        seasons.groupBy { it.optString("greenhouse_id") }.values.forEach { inGreenhouse ->
            inGreenhouse.sortedWith(compareBy<JSONObject>({ it.optString("start_date") }, { it.optString("id") }))
                .forEachIndexed { index, season -> derived[season.optString("id")] = index + 1 }
        }
        return seasons.filter { season ->
            val stored = if (season.isNull("sequence")) -1 else season.optInt("sequence", -1)
            (if (stored > 0) stored else derived[season.optString("id")]) == sequence
        }
    }
}
