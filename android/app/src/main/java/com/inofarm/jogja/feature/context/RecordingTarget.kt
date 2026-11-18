package com.inofarm.jogja.feature.context

import org.json.JSONObject
import java.time.LocalDate

/**
 * Recording only asks for a greenhouse (user decision 17 Nov 2026): one greenhouse has one open season and its
 * varieties are planted together. The season and the planting group are resolved here, never chosen by the user.
 */
internal sealed interface RecordingTarget {
    val greenhouse: JSONObject

    /** [group] is the planting a single-variety form is filed under: the running period when sown, else the first sown. */
    data class Ready(override val greenhouse: JSONObject, val season: JSONObject, val group: JSONObject,
                     val groups: List<JSONObject>, val varieties: List<String>) : RecordingTarget

    /** No open season, or an open season without an active planting. A manager can start planting. */
    data class NotPlanted(override val greenhouse: JSONObject, val season: JSONObject?) : RecordingTarget

    /** Planted, but nothing is sown yet; recording starts on [sowing]. */
    data class NotSown(override val greenhouse: JSONObject, val season: JSONObject, val sowing: LocalDate) : RecordingTarget
}

internal fun CultivationUiState.recordingTarget(greenhouseId: String?, today: LocalDate): RecordingTarget? =
    greenhouses.find { it.optString("id") == greenhouseId && it.optBoolean("active", true) }?.let { recordingTarget(it, today) }

internal fun CultivationUiState.recordingTarget(greenhouse: JSONObject, today: LocalDate): RecordingTarget {
    val greenhouseId = greenhouse.optString("id")
    val open = seasons.filter { it.optString("greenhouse_id") == greenhouseId && it.optString("status") == "open" }
    val openIds = open.map { it.getString("id") }.toSet()
    val planted = groups.filter { it.optString("season_id") in openIds && it.optBoolean("active", true) }
    if (planted.isEmpty()) return RecordingTarget.NotPlanted(greenhouse, open.firstOrNull())
    fun sowing(group: JSONObject) = runCatching { LocalDate.parse(group.getString("sowing_date")) }.getOrNull()
    val sown = planted.filter { sowing(it)?.let { date -> date <= today } == true }
        .sortedWith(compareBy<JSONObject>({ sowing(it) }, { it.optString("name") }, { it.optString("id") }))
    if (sown.isEmpty()) {
        val first = planted.minBy { sowing(it) ?: LocalDate.MAX }
        return RecordingTarget.NotSown(greenhouse, open.first { it.getString("id") == first.optString("season_id") },
            sowing(first) ?: today)
    }
    val group = sown.find { it.optString("id") == greenhouse.optString("running_group_id") } ?: sown.first()
    val season = open.first { it.getString("id") == group.optString("season_id") }
    val together = sown.filter { it.optString("season_id") == group.optString("season_id") }
    val names = together.map { row -> varieties.firstOrNull { it.optString("id") == row.optString("variety_id") }?.optString("name") ?: row.optString("name") }
    return RecordingTarget.Ready(greenhouse, season, group, together, names.distinct())
}

/** One line under the greenhouse choice: what the record will be filed under, or why it cannot be yet. */
internal fun RecordingTarget?.caption(manager: Boolean): String? = when (this) {
    null -> null
    is RecordingTarget.Ready -> "${season.optString("name")} · ${varieties.joinToString(" dan ")}"
    is RecordingTarget.NotPlanted -> if (manager) "Belum mulai tanam. Mulai tanam dulu untuk mencatat."
        else "Belum mulai tanam. Minta pengelola memulai tanam di greenhouse ini."
    is RecordingTarget.NotSown -> "Tanam dimulai ${sowing.dayOfMonth} ${java.time.format.DateTimeFormatter.ofPattern("MMM yyyy",
        java.util.Locale.forLanguageTag("id-ID")).format(sowing)}. Catatan bisa dibuat mulai tanggal itu."
}
