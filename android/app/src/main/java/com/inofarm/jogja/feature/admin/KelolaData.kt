package com.inofarm.jogja.feature.admin

import org.json.JSONObject
import java.time.Instant

/** One greenhouse row on the Kelola home: only values the API returns, nothing estimated. */
data class KelolaGreenhouse(
    val id: String, val name: String, val active: Boolean,
    /** manager-home period state: ready, needs_review, no_active_period or inactive. */
    val periodState: String?, val variety: String?, val groupName: String?,
    /** Workers assigned to this greenhouse now. Every worker can still record in every active greenhouse. */
    val workers: Int,
    /** False when the running planting group has no active EC/PPM/pH target. Null when nothing is planted. */
    val hasTargets: Boolean?,
)

data class KelolaOverview(val greenhouses: List<KelolaGreenhouse>, val managers: Int, val workers: Int, val inactiveAccounts: Int)

/** An assignment counts while it is active and has not ended yet. */
internal fun assignmentCurrent(row: JSONObject, now: Instant): Boolean {
    if (!row.optBoolean("active", true)) return false
    if (row.isNull("effective_until") || row.optString("effective_until").isBlank()) return true
    return runCatching { Instant.parse(row.getString("effective_until")) > now }.getOrDefault(true)
}

/**
 * Only worker accounts count as "pekerja" (QA 28 Nov 2026: old assignments of the pengelola made GH1 show "1 pekerja").
 * Without the account list every current assignment counts, as before.
 */
internal fun workerAssignment(row: JSONObject, users: List<JSONObject>): Boolean {
    val id = row.optString("user_id")
    if (users.isEmpty() || id.isBlank()) return true
    val user = users.find { it.optString("id") == id } ?: return true
    return user.optString("role") == "worker"
}

internal fun buildKelolaOverview(
    greenhouses: List<JSONObject>, periods: Map<String, JSONObject>, assignments: List<JSONObject>,
    targets: List<JSONObject>, users: List<JSONObject>, now: Instant = Instant.now(),
): KelolaOverview {
    val workerCount = assignments.filter { assignmentCurrent(it, now) && workerAssignment(it, users) }.groupingBy { it.optString("greenhouse_id") }.eachCount()
    val targetGroups = targets.filter { it.optBoolean("active", true) }.map { it.optString("planting_group_id") }.toSet()
    val rows = greenhouses.map { row ->
        val id = row.getString("id")
        val period = periods[id]
        val state = period?.optString("state")?.takeIf { it.isNotBlank() }
        val group = period?.optString("group_id")?.takeIf { state == "ready" && it.isNotBlank() && it != "null" }
        KelolaGreenhouse(id, row.optString("name"), row.optBoolean("active", true), state,
            period?.optString("variety")?.takeIf { group != null && it.isNotBlank() && it != "null" },
            period?.optString("group_name")?.takeIf { group != null && it.isNotBlank() && it != "null" },
            workerCount[id] ?: 0, group?.let { it in targetGroups })
    }.sortedWith(compareBy({ !it.active }, { it.name.lowercase() }))
    val activeUsers = users.filter { it.optBoolean("active", true) }
    return KelolaOverview(rows, activeUsers.count { it.optString("role") == "manager" },
        activeUsers.count { it.optString("role") == "worker" }, users.size - activeUsers.size)
}

/** What the settings page of one greenhouse shows next to each list. */
data class GreenhouseSettings(
    val openSeasons: Int, val activeGroups: Int, val varieties: Int, val schedules: Int, val targets: Int,
    val materials: Int, val units: List<String>, val grades: List<String>, val categories: Int, val workers: Int,
    /** The manager-home period object of this greenhouse. */
    val period: JSONObject?,
)

/** "gram, mL, L" for at most three names, then "dan N lagi". */
internal fun shortNames(names: List<String>): String = when {
    names.isEmpty() -> "Belum ada"
    names.size <= 3 -> names.joinToString(", ")
    else -> names.take(3).joinToString(", ") + " dan ${names.size - 3} lagi"
}

/** The line under a row in a master list, from the fields that row really has. */
internal fun masterRowTitle(resource: AdminResource, row: JSONObject): String = when (resource) {
    AdminResource.SCHEDULE -> ActivityTypeLabels[row.optString("activity_type")] ?: row.optString("activity_type")
    AdminResource.TARGET -> TargetLabels[row.optString("parameter")] ?: row.optString("parameter")
    AdminResource.ASSIGNMENT -> row.optString("user_name").ifBlank { "Pekerja" }
    else -> row.optString("name").ifBlank { resource.label }
}

internal fun masterRowDetail(resource: AdminResource, row: JSONObject): String? {
    fun text(key: String) = row.optString(key).takeIf { !row.isNull(key) && it.isNotBlank() }
    return when (resource) {
        AdminResource.MATERIAL -> when (text("purpose")) {
            "spray" -> "Semprot"; "nutrition" -> "Nutrisi"
            else -> DimensionLabels[text("dimension")]
        }
        AdminResource.UNIT -> DimensionLabels[text("dimension")]
        AdminResource.SEASON -> text("start_date")?.let { "Mulai ${com.inofarm.jogja.core.design.FarmFormat.date(it)}" }
        AdminResource.GROUP -> text("sowing_date")?.let { "Semai ${com.inofarm.jogja.core.design.FarmFormat.date(it)}" }
        AdminResource.SCHEDULE -> text("planned_at")?.let { com.inofarm.jogja.feature.timeLabel(it) }
        AdminResource.TARGET -> listOfNotNull(text("target_value")?.let { "Target $it" },
            text("minimum")?.let { min -> text("maximum")?.let { "$min sampai $it" } }).firstOrNull()?.let { "$it ${text("unit").orEmpty()}".trim() }
        AdminResource.ASSIGNMENT -> text("effective_until")?.let { "Sampai ${com.inofarm.jogja.feature.timeLabel(it)}" } ?: "Tanpa batas"
        else -> null
    }
}

/** Seasons are open or closed; everything else is active or not. */
internal fun masterRowOn(resource: AdminResource, row: JSONObject): Boolean =
    if (resource == AdminResource.SEASON) row.optString("status") != "closed" else row.optBoolean("active", true)

internal fun masterFilterLabels(resource: AdminResource): Pair<String, String> =
    if (resource == AdminResource.SEASON) "Berjalan" to "Ditutup" else "Aktif" to "Nonaktif"

/** Title of a master list in Kelola, in the words the forms use. */
internal fun masterListTitle(resource: AdminResource): String = when (resource) {
    AdminResource.SEASON -> "Musim"
    AdminResource.GROUP -> "Kelompok tanam"
    AdminResource.SCHEDULE -> "Jadwal kegiatan"
    AdminResource.TARGET -> "Target EC, PPM dan pH"
    AdminResource.GRADE -> "Kualitas panen"
    AdminResource.ASSIGNMENT -> "Pekerja di greenhouse ini"
    AdminResource.GREENHOUSE -> "Semua greenhouse"
    else -> resource.label
}

/** Where the list shows up for the people recording, so the manager knows what a change affects. */
internal fun masterListPurpose(resource: AdminResource): String = when (resource) {
    AdminResource.SEASON, AdminResource.GROUP -> "Musim dan kelompok yang bisa dipilih saat mencatat"
    AdminResource.VARIETY -> "Varietas untuk kelompok tanam"
    AdminResource.MATERIAL -> "Muncul di form Catat kegiatan"
    AdminResource.UNIT -> "Satuan jumlah bahan"
    AdminResource.GRADE -> "Kualitas saat mencatat panen"
    AdminResource.CATEGORY -> "Kategori saat mencatat biaya"
    AdminResource.SCHEDULE -> "Rencana kegiatan kelompok tanam"
    AdminResource.TARGET -> "Acuan pengukuran manual"
    AdminResource.ASSIGNMENT -> "Pekerja tetap bisa mencatat di semua greenhouse aktif"
    else -> ""
}

/** The add button of a list: "Tambah bahan". */
internal fun masterAddLabel(resource: AdminResource): String = when (resource) {
    AdminResource.ASSIGNMENT -> "Tugaskan pekerja"
    AdminResource.GRADE -> "Tambah kualitas"
    AdminResource.SCHEDULE -> "Tambah jadwal"
    AdminResource.TARGET -> "Tambah target"
    else -> "Tambah ${resource.label.lowercase()}"
}

private val ActivityTypeLabels = mapOf("measurement" to "Pengukuran manual", "nutrition" to "Nutrisi", "spray" to "Penyemprotan",
    "general" to "Pekerjaan umum", "stage" to "Stadia/Fase")
private val TargetLabels = mapOf("manual_ec" to "EC", "manual_ppm" to "PPM", "manual_ph" to "pH")
private val DimensionLabels = mapOf("mass" to "Massa", "volume" to "Volume", "count" to "Jumlah")
