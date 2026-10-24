package com.inofarm.jogja.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

enum class PlantPhase(val wire: String) {
    Vegetatif("vegetatif"), Generatif("generatif");
    companion object { fun parse(value: String) = entries.firstOrNull { it.wire == value } ?: throw IllegalArgumentException("Fase API tidak dikenal") }
}
enum class ValidationStatus(val wire: String, val label: String) {
    Valid("valid", "Valid"), Issues("issues", "Ada masalah"), Incomplete("incomplete", "Belum dapat divalidasi");
    companion object { fun parse(value: String) = entries.firstOrNull { it.wire == value } ?: throw IllegalArgumentException("Status API tidak dikenal") }
}
enum class SensorQuality(val wire: String) {
    Ok("ok"), Missing("missing"), Failed("failed"), Uncalibrated("uncalibrated");
    companion object { fun parse(value: String) = entries.firstOrNull { it.wire == value } ?: error("Quality API tidak dikenal") }
}
data class Metric(val key: String, val label: String, val unit: String)
val metrics = listOf(Metric("waterTemperatureC", "Suhu air", "°C"), Metric("solutionPh", "pH larutan", "pH"), Metric("airTemperatureC", "Suhu ruangan", "°C"), Metric("relativeHumidityPercent", "Kelembapan", "%"))
data class SensorValue(val value: Double?, val quality: SensorQuality)
data class Telemetry(val sourceAt: String?, val receivedAt: String, val readings: Map<String, SensorValue>, val clockTrusted: Boolean = true, val id: Long? = null)
data class Check(val result: String, val code: String, val message: String)
data class NutrientRecord(val id: Long, val sourceAt: String?, val receivedAt: String, val phase: PlantPhase, val manualPpm: Double, val status: ValidationStatus, val checks: List<Check>, val workerName: String?, val assignmentStatus: String, val ownerPhase: PlantPhase?, val configVersion: Long?, val phaseVersion: Long?, val clockTrusted: Boolean = true, val evaluatedAt: String? = null, val measuredAt: String? = null)
data class Dashboard(val activePhase: PlantPhase?, val activePhaseVersion: Long?, val connection: String, val lastContactAt: String?, val appliedVersion: Long?, val telemetry: Telemetry?, val latestRecord: NutrientRecord?, val latestTelemetryReceipt: Telemetry? = null, val latestRecordReceipt: NutrientRecord? = null, val serverTime: String? = null)
data class Page<T>(val items: List<T>, val nextCursor: String?, val previousCursor: String? = null)
data class Worker(val id: Long, val version: Long, val name: String, val active: Boolean)
data class Shift(val id: Long, val key: String, val workerId: Long, val days: List<Int>, val startTime: String, val endTime: String, val startDate: String, val endDate: String?)
data class RemoteConfig(val version: Long?, val body: JSONObject?, val phaseVersion: Long?, val phase: PlantPhase?)

fun JSONObject.nullString(key: String): String? = if (isNull(key)) null else getString(key)
fun JSONObject.nullLong(key: String): Long? = if (isNull(key)) null else getLong(key)
fun checkedTime(value: String?): String? { if (value != null) OffsetDateTime.parse(value); return value }
fun finite(value: Double): Double { require(value.isFinite()) { "Angka API tidak finite" }; return value }
fun <T> JSONArray.mapItems(map: (JSONObject) -> T): List<T> = (0 until length()).map { map(getJSONObject(it)) }

object ApiModels {
    fun record(json: JSONObject): NutrientRecord {
        val result = json.getJSONObject("validation_result")
        val checks = result.getJSONArray("checks").mapItems { Check(it.getString("result"), it.getString("code"), it.getString("message")) }
        require(checks.isNotEmpty()) { "Hasil validasi tanpa checks" }
        val owner = result.optJSONObject("ownerPhaseSnapshot")?.let { PlantPhase.parse(it.getString("phase")) }
        val assignment = json.getJSONObject("assignment")
        return NutrientRecord(json.getLong("id"), checkedTime(json.getJSONObject("body").nullString("confirmedAt")), checkedTime(json.getString("received_at"))!!, PlantPhase.parse(json.getString("phase")), finite(json.getDouble("manual_ppm")), ValidationStatus.parse(result.getString("status")), checks, assignment.nullString("workerNameSnapshot"), assignment.getString("status"), owner, json.nullLong("config_version_id"), json.nullLong("phase_version_id"), json.getBoolean("clock_trusted"), checkedTime(result.nullString("evaluatedAt")), checkedTime(json.getJSONObject("body").nullString("measuredAt")))
    }
    fun telemetry(json: JSONObject): Telemetry {
        val snapshot = json.getJSONObject("body").getJSONObject("sensorSnapshot")
        val readings = snapshot.getJSONObject("readings"); val quality = snapshot.getJSONObject("quality")
        val values = metrics.associate { metric ->
            val q = SensorQuality.parse(quality.getString(metric.key))
            val v = if (readings.isNull(metric.key)) null else finite(readings.getDouble(metric.key))
            require((q == SensorQuality.Ok) == (v != null)) { "Quality/nilai sensor tidak konsisten" }
            metric.key to SensorValue(v, q)
        }
        return Telemetry(if(json.getBoolean("clock_trusted")) checkedTime(snapshot.nullString("capturedAt")) else null, checkedTime(json.getString("received_at"))!!, values,json.getBoolean("clock_trusted"),json.nullLong("id"))
    }
    fun dashboard(json: JSONObject): Dashboard {
        val phase = json.optJSONObject("activePhase")
        val device = json.optJSONObject("device")
        return Dashboard(phase?.getJSONObject("body")?.getString("phase")?.let(PlantPhase::parse), phase?.getLong("id"), json.getString("connectionState"), checkedTime(device?.nullString("last_contact_at")), device?.nullLong("applied_config_version"), json.optJSONObject("telemetry")?.let(::telemetry), json.optJSONObject("latestRecord")?.let(::record),json.optJSONObject("latestTelemetryReceipt")?.let(::telemetry),json.optJSONObject("latestRecordReceipt")?.let(::record),checkedTime(json.nullString("serverTime")))
    }
    fun worker(json: JSONObject) = Worker(json.getLong("id"), json.getLong("version"), json.getString("name"), json.getBoolean("active"))
    fun shift(json: JSONObject): Shift {
        val body = json.getJSONObject("body"); val days = body.getJSONArray("days")
        return Shift(json.getLong("id"), json.getString("shift_key"), json.getLong("worker_id"), (0 until days.length()).map { days.getInt(it) }, body.getString("startTime"), body.getString("endTime"), body.getString("startDate"), body.nullString("endDate"))
    }
    fun config(json: JSONObject): RemoteConfig {
        val cfg = json.optJSONObject("config"); val phase = json.optJSONObject("activePhase")
        return RemoteConfig(cfg?.getLong("id"), cfg?.getJSONObject("body"), phase?.getLong("id"), phase?.getJSONObject("body")?.getString("phase")?.let(PlantPhase::parse))
    }
}
