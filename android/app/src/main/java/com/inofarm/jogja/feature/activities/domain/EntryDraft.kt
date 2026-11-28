package com.inofarm.jogja.feature.activities.domain

import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.cultivation.ManualTime
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** One reading block of the entry: none, one moment, or before and after injection (Inofarm, 20 Nov 2026). */
data class MeasurementDraft(val moment: String = "", val ec: String = "", val ppm: String = "", val ph: String = "", val waterTemperature: String = "") {
    val filled: Boolean get() = listOf(ec, ppm, ph, waterTemperature).any { it.isNotBlank() }
}

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
    val measurements: List<MeasurementDraft> = listOf(MeasurementDraft()),
    /** AB Mix, the primary nutrient (Inofarm, 20 Nov 2026): amount and one of [NutritionUnits]. */
    val abMixQuantity: String = "", val abMixUnit: String = "",
    /** Extra nutrients mixed into the AB Mix, typed by hand; rows from before 20 Nov 2026 may name a master material. */
    val nutrition: List<MaterialDraft> = emptyList(),
    val sprayOpen: Boolean = false, val sprayDate: String = LocalDate.now(ActivityDraft.WIB).toString(), val sprayPeriod: String = "",
    val sprayGroups: Set<String> = emptySet(), val spray: List<MaterialDraft> = emptyList(),
    val stage: String = "", val notes: String = "", val runningPeriodVersion: Long? = null,
    val sectionIds: Map<String, String> = emptyMap(),
    /** The schedule this entry carries out, its activity type and planting group; the matching section links to it. */
    val scheduleId: String = "", val scheduleType: String = "", val scheduleGroup: String = "",
) {
    val hasMeasurement: Boolean get() = measurements.any { it.filled }

    /** The blocks for the chosen moments, before injection first; values stay with their moment. */
    fun withMoments(selected: Set<String>): EntryDraft {
        val chosen = MomentOrder.filter { it in selected }
        if (chosen.isEmpty()) return copy(measurements = listOf((measurements.firstOrNull { it.filled } ?: MeasurementDraft()).copy(moment = "")))
        val byMoment = measurements.filter { it.moment.isNotBlank() }.associateBy { it.moment }
        val plain = measurements.singleOrNull()?.takeIf { it.moment.isBlank() }
        return copy(measurements = chosen.mapIndexed { index, moment ->
            byMoment[moment] ?: if (index == 0 && plain != null) plain.copy(moment = moment) else MeasurementDraft(moment)
        })
    }
    val hasSpray: Boolean get() = sprayOpen && (spray.isNotEmpty() || sprayGroups.isNotEmpty())
    private val hasAbMix: Boolean get() = abMixQuantity.isNotBlank() || abMixUnit.isNotBlank()

    /** Every material of the nutrition record: AB Mix first, then the extras. */
    val nutritionMaterials: List<MaterialDraft> get() =
        listOfNotNull(if (hasAbMix) MaterialDraft(name = AB_MIX, quantity = abMixQuantity, unitName = abMixUnit) else null) + nutrition

    fun validation(): Map<String, String> = buildMap {
        if (runCatching { ManualTime.encode(date, time) }.isFailure) put("occurred_at", "Gunakan tanggal dan jam yang sah.")
        else if (ManualTime.afterToday(ManualTime.encode(date, time))) put("occurred_at", "Waktu kegiatan tidak boleh sesudah hari ini.")
        if (ageBasis !in setOf("hss", "hst")) put("age", "Pilih HSS atau HST.")
        else if (ageDay.trim().toIntOrNull()?.let { it in 1..1000 } != true) put("age", "Isi hari ke berapa, angka 1 sampai 1000.")
        if (!hasMeasurement && nutritionMaterials.isEmpty() && !hasSpray && stage.isBlank() && notes.isBlank())
            put("entry", "Isi sedikitnya satu bagian: pengukuran, nutrisi, penyemprotan, stadia, atau catatan.")
        // Inofarm 20 Nov 2026: EC is the reading that must be there; PPM, pH and water temperature are optional.
        measurements.forEachIndexed { index, block -> if (block.filled && block.ec.isBlank()) put("measurements.$index.manual_ec", "Isi EC.") }
        if (hasSpray) {
            if (sprayGroups.isEmpty()) put("spray_groups", "Pilih varietas yang disemprot.")
            if (runCatching { LocalDate.parse(sprayDate) }.isFailure) put("spray_date", "Isi tanggal semprot.")
            if (sprayPeriod !in setOf("morning", "afternoon")) put("spray_period", "Pilih pagi atau sore.")
            if (spray.isEmpty()) put("spray", "Tambahkan sedikitnya satu bahan semprot.")
        }
        if (scheduleId.isNotBlank() && sections().none { it.scheduleId == scheduleId })
            put("entry", "Isi bagian ${scheduleSection(scheduleType)} untuk jadwal ini.")
        if (stage.length > 100) put("actual_stage", "Stadia maksimal 100 karakter.")
        if (notes.length > 2000) put("notes", "Catatan maksimal 2.000 karakter.")
        // Each section's own checks (decimals, pH range, material and dose) are reported under its field keys.
        // One copy with fixed section ids: sections() and the lookup below must agree, or a section's error (pH out
        // of range) lands on a key no field shows and Simpan seems to do nothing (QA 28 Nov 2026).
        val fixed = withSectionIds()
        if (isEmpty()) fixed.sections().forEach { section ->
            val ids = fixed.sectionIds
            val block = measurements.indices.firstOrNull { index -> measurementKey(index).let { ids[it] } == section.id && measurements[index].filled }
            section.validation(false).forEach { (key, message) ->
                val field = when {
                    section.type == ActivityType.MEASUREMENT && block != null -> "measurements.$block.$key"
                    // AB Mix is the first material when filled; extras keep their own row index.
                    section.type == ActivityType.NUTRITION && key.startsWith("materials.") -> key.removePrefix("materials.").substringBefore('.').toIntOrNull()
                        ?.let { if (hasAbMix && it == 0) "nutrition.ab_mix" else "nutrition.${if (hasAbMix) it - 1 else it}" } ?: "nutrition"
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

    /** The first reading block keeps the old "measurement" key so earlier drafts keep their ids; the second has its own. */
    private fun measurementKey(index: Int) = if (index == 0) sectionKey(ActivityType.MEASUREMENT) else sectionKey(ActivityType.MEASUREMENT, "after_injection")

    /** Stable ids for every section this entry would send now, keeping ids it already had. */
    fun withSectionIds(): EntryDraft {
        val keys = buildList {
            measurements.forEachIndexed { index, block -> if (block.filled) add(measurementKey(index)) }
            if (nutritionMaterials.isNotEmpty()) add(sectionKey(ActivityType.NUTRITION))
            if (hasSpray) sprayGroups.sorted().forEach { add(sectionKey(ActivityType.SPRAY, it)) }
            if (stage.isNotBlank()) add(sectionKey(ActivityType.STAGE))
            if (onlyNotes) add(sectionKey(ActivityType.GENERAL))
        }
        val missing = keys.filter { it !in sectionIds }
        return if (missing.isEmpty()) this else copy(sectionIds = sectionIds + missing.associateWith { UUID.randomUUID().toString() })
    }

    private val onlyNotes: Boolean get() = !hasMeasurement && nutritionMaterials.isEmpty() && !hasSpray && stage.isBlank() && notes.isNotBlank()

    /** The activity records this entry becomes. Notes travel with every record so each one reads on its own. */
    fun sections(): List<ActivityDraft> {
        val ids = withSectionIds().sectionIds
        fun base(type: ActivityType, group: String = "", key: String = sectionKey(type, group.ifBlank { null })) = ActivityDraft(
            id = ids.getValue(key), greenhouseId = greenhouseId, groupId = group, type = type,
            date = date, time = time, notes = notes, seasonId = seasonId, entryBatchId = id, ageBasis = ageBasis, ageDay = ageDay,
            runningPeriodVersion = runningPeriodVersion)
        return buildList {
            measurements.forEachIndexed { index, block ->
                if (block.filled) add(base(ActivityType.MEASUREMENT, key = measurementKey(index)).copy(ec = block.ec, ppm = block.ppm, ph = block.ph,
                    observations = buildMap {
                        if (block.waterTemperature.isNotBlank()) put(ObservationField.WATER_TEMPERATURE, block.waterTemperature)
                    }, measurementMoment = block.moment))
            }
            if (nutritionMaterials.isNotEmpty()) add(base(ActivityType.NUTRITION).copy(materials = nutritionMaterials))
            if (hasSpray) sprayGroups.sorted().forEach { group ->
                add(base(ActivityType.SPRAY, group).copy(materials = spray, sprayDate = sprayDate, sprayPeriod = sprayPeriod))
            }
            if (stage.isNotBlank()) add(base(ActivityType.STAGE).copy(stage = stage.trim()))
            if (onlyNotes) add(base(ActivityType.GENERAL))
        }.let { rows ->
            // The schedule goes on the first section of its type; a spray schedule only on the variety it names.
            val linked = rows.indexOfFirst { it.type.wire == scheduleType && (it.type != ActivityType.SPRAY || it.groupId == scheduleGroup) }
            if (scheduleId.isBlank() || linked < 0) rows else rows.mapIndexed { i, row -> if (i == linked) row.copy(scheduleId = scheduleId) else row }
        }
    }

    /** Stored as a local activity draft of type "entry"; it never reaches the server itself, only its sections do. */
    fun localJson(): JSONObject = JSONObject().put("activity_type", TYPE).put("notes", notes)
        .put("occurred_at", runCatching { ManualTime.encode(date, time) }.getOrNull() ?: JSONObject.NULL).put("completeness", "draft")
        .put("_local_form", JSONObject().put("entry", true).put("id", id).put("type", TYPE).put("greenhouse_id", greenhouseId)
            .put("season_id", seasonId).put("date", date).put("time", time).put("age_basis", ageBasis).put("age_day", ageDay)
            .put("measurements", JSONArray().apply { measurements.forEach { put(JSONObject().put("moment", it.moment).put("ec", it.ec)
                .put("ppm", it.ppm).put("ph", it.ph).put("water_temperature", it.waterTemperature)) } })
            .put("ab_mix_quantity", abMixQuantity).put("ab_mix_unit", abMixUnit)
            .put("nutrition", materialsJson(nutrition)).put("spray_open", sprayOpen).put("spray_date", sprayDate)
            .put("spray_period", sprayPeriod).put("spray_groups", JSONArray(sprayGroups.sorted())).put("spray", materialsJson(spray))
            .put("stage", stage).put("notes", notes).put("running_period_version", runningPeriodVersion ?: JSONObject.NULL)
            .put("section_ids", JSONObject(sectionIds as Map<*, *>))
            .put("schedule_id", scheduleId).put("schedule_type", scheduleType).put("schedule_group", scheduleGroup))

    companion object {
        const val TYPE = "entry"
        const val AB_MIX = "AB Mix"
        val MomentOrder = listOf("before_injection", "after_injection")

        /** The form section a scheduled activity type is filled in, as its heading reads. */
        fun scheduleSection(type: String): String = when (type) {
            "measurement" -> "Pengukuran"; "nutrition" -> "Nutrisi AB Mix"; "spray" -> "Penyemprotan"; "stage" -> "Stadia/Fase"; else -> "Catatan"
        }

        fun isEntry(body: JSONObject): Boolean = body.optJSONObject("_local_form")?.optBoolean("entry") == true

        private fun materialsJson(rows: List<MaterialDraft>) = JSONArray().apply {
            rows.forEach { put(JSONObject().put("material_id", it.materialId).put("unit_id", it.unitId).put("quantity", it.quantity).put("name", it.name)
                .put("unit_name", it.unitName)) }
        }

        private fun materials(array: JSONArray?) = (0 until (array?.length() ?: 0)).map { index ->
            val row = array!!.getJSONObject(index)
            MaterialDraft(row.optString("material_id"), row.optString("unit_id"), com.inofarm.jogja.core.design.FarmFormat.input(row.optString("quantity")), row.optString("name"), row.optString("unit_name"))
        }

        fun from(body: JSONObject): EntryDraft {
            val form = body.getJSONObject("_local_form")
            val ids = form.optJSONObject("section_ids")
            val groups = form.optJSONArray("spray_groups")
            return EntryDraft(
                id = WireContract.uuid(form.getString("id")), greenhouseId = form.getString("greenhouse_id"), seasonId = form.getString("season_id"),
                date = form.getString("date"), time = form.getString("time"), ageBasis = form.optString("age_basis"), ageDay = form.optString("age_day"),
                measurements = form.optJSONArray("measurements")?.let { array -> (0 until array.length()).map { index ->
                    array.getJSONObject(index).let { MeasurementDraft(it.optString("moment"), it.optString("ec"), it.optString("ppm"), it.optString("ph"), it.optString("water_temperature")) }
                } }?.ifEmpty { null }
                    // A draft saved before two readings existed (20 Nov 2026) has one set of reading keys.
                    ?: listOf(MeasurementDraft(form.optString("moment"), form.optString("ec"), form.optString("ppm"), form.optString("ph"), form.optString("water_temperature"))),
                abMixQuantity = form.optString("ab_mix_quantity"), abMixUnit = form.optString("ab_mix_unit"),
                nutrition = materials(form.optJSONArray("nutrition")), sprayOpen = form.optBoolean("spray_open"),
                sprayDate = form.optString("spray_date"), sprayPeriod = form.optString("spray_period"),
                sprayGroups = (0 until (groups?.length() ?: 0)).map { groups!!.getString(it) }.toSet(), spray = materials(form.optJSONArray("spray")),
                stage = form.optString("stage"), notes = form.optString("notes"),
                runningPeriodVersion = form.optLong("running_period_version").takeIf { it > 0 },
                sectionIds = ids?.keys()?.asSequence()?.associateWith { ids.getString(it) } ?: emptyMap(),
                scheduleId = form.optString("schedule_id"), scheduleType = form.optString("schedule_type"),
                scheduleGroup = form.optString("schedule_group"),
            )
        }
    }
}
