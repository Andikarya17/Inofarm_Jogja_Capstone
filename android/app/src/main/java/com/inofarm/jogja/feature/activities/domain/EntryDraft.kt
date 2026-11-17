package com.inofarm.jogja.feature.activities.domain

import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.cultivation.ManualTime
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * One "Catat kegiatan" form for a greenhouse (user decision 17 Nov 2026). Readings, nutrition, stage and notes are
 * one record per greenhouse season (one nutrient tank); a spray is one record per variety sprayed. Every record of an
 * entry shares [id] as its entry_batch_id, the time and the typed plant age.
 *
 * [sectionIds] fixes the UUID of each record the entry becomes, so saving again or retrying never makes duplicates.
 */
data class EntryDraft(
    val id: String = UUID.randomUUID().toString(), val greenhouseId: String, val seasonId: String,
    val date: String = LocalDate.now(ActivityDraft.WIB).toString(),
    val time: String = LocalTime.now(ActivityDraft.WIB).format(DateTimeFormatter.ofPattern("HH:mm")),
    val ageBasis: String = "", val ageDay: String = "",
    val ec: String = "", val ppm: String = "", val ph: String = "", val waterTemperature: String = "", val moment: String = "",
    val nutrition: List<MaterialDraft> = emptyList(),
    val sprayOpen: Boolean = false, val sprayDate: String = LocalDate.now(ActivityDraft.WIB).toString(), val sprayPeriod: String = "",
    val sprayGroups: Set<String> = emptySet(), val spray: List<MaterialDraft> = emptyList(),
    val stage: String = "", val notes: String = "", val runningPeriodVersion: Long? = null,
    val sectionIds: Map<String, String> = emptyMap(),
) {
    val hasMeasurement: Boolean get() = listOf(ec, ppm, ph, waterTemperature).any { it.isNotBlank() }
    val hasSpray: Boolean get() = sprayOpen && (spray.isNotEmpty() || sprayGroups.isNotEmpty())

    fun validation(): Map<String, String> = buildMap {
        if (runCatching { ManualTime.encode(date, time) }.isFailure) put("occurred_at", "Gunakan tanggal dan jam yang sah.")
        if (ageBasis !in setOf("hss", "hst")) put("age", "Pilih HSS atau HST.")
        else if (ageDay.trim().toIntOrNull()?.let { it in 1..1000 } != true) put("age", "Isi hari ke berapa, angka 1 sampai 1000.")
        if (!hasMeasurement && nutrition.isEmpty() && !hasSpray && stage.isBlank() && notes.isBlank())
            put("entry", "Isi sedikitnya satu bagian: pengukuran, nutrisi, penyemprotan, stadia, atau catatan.")
        if (hasMeasurement && (ec.isBlank() || ppm.isBlank())) put("manual_pair", "EC dan PPM diisi berpasangan.")
        if (hasSpray) {
            if (sprayGroups.isEmpty()) put("spray_groups", "Pilih varietas yang disemprot.")
            if (runCatching { LocalDate.parse(sprayDate) }.isFailure) put("spray_date", "Isi tanggal semprot.")
            if (sprayPeriod !in setOf("morning", "afternoon")) put("spray_period", "Pilih pagi atau sore.")
            if (spray.isEmpty()) put("spray", "Tambahkan sedikitnya satu bahan semprot.")
        }
        if (stage.length > 100) put("actual_stage", "Stadia maksimal 100 karakter.")
        if (notes.length > 2000) put("notes", "Catatan maksimal 2.000 karakter.")
        // Each section's own checks (decimals, pH range, material and dose) are reported under its field keys.
        if (isEmpty()) sections().forEach { section ->
            section.validation(false).forEach { (key, message) ->
                val field = when {
                    section.type == ActivityType.NUTRITION && key.startsWith("materials") -> key.replace("materials", "nutrition")
                    section.type == ActivityType.SPRAY && key.startsWith("materials") -> key.replace("materials", "spray")
                    else -> key
                }
                putIfAbsent(field, message)
            }
        }
    }

    /** The section key of a record; spray keys carry the planting group (variety) they were sprayed on. */
    private fun sectionKey(type: ActivityType, group: String? = null) = if (group == null) type.wire else "${type.wire}:$group"

    /** Stable ids for every section this entry would send now, keeping ids it already had. */
    fun withSectionIds(): EntryDraft {
        val keys = buildList {
            if (hasMeasurement) add(sectionKey(ActivityType.MEASUREMENT))
            if (nutrition.isNotEmpty()) add(sectionKey(ActivityType.NUTRITION))
            if (hasSpray) sprayGroups.sorted().forEach { add(sectionKey(ActivityType.SPRAY, it)) }
            if (stage.isNotBlank()) add(sectionKey(ActivityType.STAGE))
            if (onlyNotes) add(sectionKey(ActivityType.GENERAL))
        }
        val missing = keys.filter { it !in sectionIds }
        return if (missing.isEmpty()) this else copy(sectionIds = sectionIds + missing.associateWith { UUID.randomUUID().toString() })
    }

    private val onlyNotes: Boolean get() = !hasMeasurement && nutrition.isEmpty() && !hasSpray && stage.isBlank() && notes.isNotBlank()

    /** The activity records this entry becomes. Notes travel with every record so each one reads on its own. */
    fun sections(): List<ActivityDraft> {
        val ids = withSectionIds().sectionIds
        fun base(type: ActivityType, group: String = "") = ActivityDraft(
            id = ids.getValue(sectionKey(type, group.ifBlank { null })), greenhouseId = greenhouseId, groupId = group, type = type,
            date = date, time = time, notes = notes, seasonId = seasonId, entryBatchId = id, ageBasis = ageBasis, ageDay = ageDay,
            runningPeriodVersion = runningPeriodVersion)
        return buildList {
            if (hasMeasurement) add(base(ActivityType.MEASUREMENT).copy(ec = ec, ppm = ppm, ph = ph,
                observations = buildMap {
                    if (waterTemperature.isNotBlank()) put(ObservationField.WATER_TEMPERATURE, waterTemperature)
                }, measurementMoment = moment))
            if (nutrition.isNotEmpty()) add(base(ActivityType.NUTRITION).copy(materials = nutrition))
            if (hasSpray) sprayGroups.sorted().forEach { group ->
                add(base(ActivityType.SPRAY, group).copy(materials = spray, sprayDate = sprayDate, sprayPeriod = sprayPeriod))
            }
            if (stage.isNotBlank()) add(base(ActivityType.STAGE).copy(stage = stage.trim()))
            if (onlyNotes) add(base(ActivityType.GENERAL))
        }
    }

    /** Stored as a local activity draft of type "entry"; it never reaches the server itself, only its sections do. */
    fun localJson(): JSONObject = JSONObject().put("activity_type", TYPE).put("notes", notes)
        .put("occurred_at", runCatching { ManualTime.encode(date, time) }.getOrNull() ?: JSONObject.NULL).put("completeness", "draft")
        .put("_local_form", JSONObject().put("entry", true).put("id", id).put("type", TYPE).put("greenhouse_id", greenhouseId)
            .put("season_id", seasonId).put("date", date).put("time", time).put("age_basis", ageBasis).put("age_day", ageDay)
            .put("ec", ec).put("ppm", ppm).put("ph", ph).put("water_temperature", waterTemperature).put("moment", moment)
            .put("nutrition", materialsJson(nutrition)).put("spray_open", sprayOpen).put("spray_date", sprayDate)
            .put("spray_period", sprayPeriod).put("spray_groups", JSONArray(sprayGroups.sorted())).put("spray", materialsJson(spray))
            .put("stage", stage).put("notes", notes).put("running_period_version", runningPeriodVersion ?: JSONObject.NULL)
            .put("section_ids", JSONObject(sectionIds as Map<*, *>)))

    companion object {
        const val TYPE = "entry"

        fun isEntry(body: JSONObject): Boolean = body.optJSONObject("_local_form")?.optBoolean("entry") == true

        private fun materialsJson(rows: List<MaterialDraft>) = JSONArray().apply {
            rows.forEach { put(JSONObject().put("material_id", it.materialId).put("unit_id", it.unitId).put("quantity", it.quantity).put("name", it.name)) }
        }

        private fun materials(array: JSONArray?) = (0 until (array?.length() ?: 0)).map { index ->
            val row = array!!.getJSONObject(index)
            MaterialDraft(row.optString("material_id"), row.optString("unit_id"), row.optString("quantity"), row.optString("name"))
        }

        fun from(body: JSONObject): EntryDraft {
            val form = body.getJSONObject("_local_form")
            val ids = form.optJSONObject("section_ids")
            val groups = form.optJSONArray("spray_groups")
            return EntryDraft(
                id = WireContract.uuid(form.getString("id")), greenhouseId = form.getString("greenhouse_id"), seasonId = form.getString("season_id"),
                date = form.getString("date"), time = form.getString("time"), ageBasis = form.optString("age_basis"), ageDay = form.optString("age_day"),
                ec = form.optString("ec"), ppm = form.optString("ppm"), ph = form.optString("ph"),
                waterTemperature = form.optString("water_temperature"), moment = form.optString("moment"),
                nutrition = materials(form.optJSONArray("nutrition")), sprayOpen = form.optBoolean("spray_open"),
                sprayDate = form.optString("spray_date"), sprayPeriod = form.optString("spray_period"),
                sprayGroups = (0 until (groups?.length() ?: 0)).map { groups!!.getString(it) }.toSet(), spray = materials(form.optJSONArray("spray")),
                stage = form.optString("stage"), notes = form.optString("notes"),
                runningPeriodVersion = form.optLong("running_period_version").takeIf { it > 0 },
                sectionIds = ids?.keys()?.asSequence()?.associateWith { ids.getString(it) } ?: emptyMap(),
            )
        }
    }
}
