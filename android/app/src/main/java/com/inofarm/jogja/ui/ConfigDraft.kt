package com.inofarm.jogja.ui

import com.inofarm.jogja.data.metrics
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

data class ScheduleDraft(val days: Set<Int> = emptySet(), val time: String = "")
data class BoundDraft(val min: String = "", val max: String = "")
data class ConfigDraft(val early: String = "", val late: String = "", val sensorAge: String = "", val stale: String = "", val cadence: String = "", val alarm: String = "", val schedules: List<ScheduleDraft> = emptyList(), val ppm: Map<String,BoundDraft> = mapOf("vegetatif" to BoundDraft(), "generatif" to BoundDraft()), val sensors: Map<String,BoundDraft> = metrics.associate { it.key to BoundDraft() }) {
    private fun number(value: String, range: IntRange): Any = if (value.isBlank()) JSONObject.NULL else value.toIntOrNull()?.also { require(it in range) { "Isi bilangan bulat ${range.first}–${range.last}" } } ?: error("Isi bilangan bulat")
    private fun bounds(input: Map<String,BoundDraft>, nonnegative: Boolean = false): JSONObject = JSONObject().apply {
        input.forEach { (key, range) ->
            if (range.min.isNotBlank() || range.max.isNotBlank()) {
                val min = range.min.toDoubleOrNull(); val max = range.max.toDoubleOrNull()
                require(min != null && max != null && min.isFinite() && max.isFinite() && min <= max) { "Isi kedua batas dengan min ≤ max" }
                require(!nonnegative || min >= 0) { "PPM tidak boleh negatif" }
                put(key, JSONObject().put("min", min).put("max", max))
            }
        }
    }
    fun json(): JSONObject {
        val rows = JSONArray()
        require(schedules.size <= 14) { "Maksimum 14 baris jadwal" }
        schedules.forEach { row ->
            require(row.days.isNotEmpty() && row.days.all { it in 1..7 }) { "Pilih hari jadwal" }
            require(Regex("\\d{2}:\\d{2}").matches(row.time)) { "Jam memakai HH:mm" }; LocalTime.parse(row.time)
            rows.put(JSONObject().put("days", JSONArray(row.days.sorted())).put("time", row.time))
        }
        return JSONObject().put("timezone", "Asia/Jakarta").put("earlyMinutes", number(early,0..1440)).put("lateMinutes", number(late,0..1440)).put("sensorMaxAgeSeconds", number(sensorAge,0..604800)).put("deviceStaleSeconds", number(stale,1..604800)).put("telemetryIntervalSeconds", number(cadence,2..86400)).put("alarmSeconds", number(alarm,1..3600)).put("schedules", rows).put("ppmRanges", bounds(ppm,true)).put("sensorBounds", bounds(sensors))
    }
    // Preserve invalid/unfinished text too; this is editor state, not an API payload.
    fun serialize(): String = JSONObject().put("fields", JSONArray(listOf(early,late,sensorAge,stale,cadence,alarm)))
        .put("schedules", JSONArray(schedules.map { JSONObject().put("days",JSONArray(it.days.sorted())).put("time",it.time) }))
        .put("ppm", JSONObject(ppm.mapValues { JSONArray(listOf(it.value.min,it.value.max)) }))
        .put("sensors", JSONObject(sensors.mapValues { JSONArray(listOf(it.value.min,it.value.max)) })).toString()
    companion object {
        fun restore(text: String): ConfigDraft {
            val json=JSONObject(text); val fields=json.getJSONArray("fields")
            fun ranges(key: String) = json.getJSONObject(key).let { obj -> obj.keys().asSequence().associateWith { name -> obj.getJSONArray(name).let { BoundDraft(it.getString(0),it.getString(1)) } } }
            val rows=json.getJSONArray("schedules")
            return ConfigDraft(fields.getString(0),fields.getString(1),fields.getString(2),fields.getString(3),fields.getString(4),fields.getString(5),(0 until rows.length()).map { i -> rows.getJSONObject(i).let { row -> val days=row.getJSONArray("days"); ScheduleDraft((0 until days.length()).map { days.getInt(it) }.toSet(),row.getString("time")) } },ranges("ppm"),ranges("sensors"))
        }
        fun from(json: JSONObject?): ConfigDraft {
            if (json == null) return ConfigDraft()
            fun field(key: String) = if (json.isNull(key)) "" else json.get(key).toString()
            fun range(group: String, key: String): BoundDraft = json.optJSONObject(group)?.optJSONObject(key)?.let { BoundDraft(it.get("min").toString(), it.get("max").toString()) } ?: BoundDraft()
            val scheduleRows = json.getJSONArray("schedules")
            return ConfigDraft(field("earlyMinutes"),field("lateMinutes"),field("sensorMaxAgeSeconds"),field("deviceStaleSeconds"),field("telemetryIntervalSeconds"),field("alarmSeconds"),(0 until scheduleRows.length()).map { index ->
                val row = scheduleRows.getJSONObject(index); val days = row.getJSONArray("days")
                ScheduleDraft((0 until days.length()).map { days.getInt(it) }.toSet(), row.getString("time"))
            }, listOf("vegetatif","generatif").associateWith { range("ppmRanges",it) }, metrics.associate { it.key to range("sensorBounds",it.key) })
        }
    }
}
