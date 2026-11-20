package com.inofarm.jogja.feature.activities.domain

import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.cultivation.ManualTime
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class ActivityType(val wire: String,val label: String) { MEASUREMENT("measurement","Pengukuran manual"), GENERAL("general","Pekerjaan umum"), NUTRITION("nutrition","Nutrisi"), SPRAY("spray","Penyemprotan"), STAGE("stage","Stadia/Fase") }
enum class ObservationField(val wire: String, val label: String, val unit: String) {
    OXYGEN("dissolved_oxygen_mg_l", "Oksigen terlarut (DO)", "mg/L"),
    WATER_TEMPERATURE("water_temperature_c", "Suhu air", "°C"),
    PLANT_HEIGHT("plant_height_cm", "Tinggi tanaman", "cm"),
    LEAF_COUNT("leaf_count", "Jumlah daun", "helai"),
    LEAF_WIDTH("leaf_width_cm", "Lebar daun", "cm"),
    SAMPLE_BASIS("sample_basis", "Sampel atau cara pengamatan", ""),
}

/**
 * [name] is a material typed by hand: a spray (shared master list, 17 Nov 2026) or a nutrient (AB Mix and extras,
 * 20 Nov 2026). [unitName] is one of [NutritionUnits], sent for a typed nutrient instead of a unit id.
 */
data class MaterialDraft(val materialId: String="",val unitId: String="",val quantity: String="",val name: String="",val unitName: String="") {
    val doseGiven: Boolean get() = unitId.isNotBlank() || unitName.isNotBlank() || quantity.isNotBlank()
}

/** The only nutrient units Inofarm uses (20 Nov 2026). */
val NutritionUnits = listOf("mL", "L", "gram")
data class ActivityDraft(val id: String=UUID.randomUUID().toString(),val greenhouseId: String,val groupId: String,
    val type: ActivityType=ActivityType.GENERAL,val date: String=LocalDate.now(WIB).toString(),val time: String=LocalTime.now(WIB).format(DateTimeFormatter.ofPattern("HH:mm")),
    val ppm: String="",val ph: String="",val stage: String="",val skipped: Boolean=false,val skippedReason: String="",val notes: String="",
    val materials: List<MaterialDraft> = emptyList(),val scheduleId: String?=null,val reason: String="",val referencePolicy: String="preserve",val baseVersion: Long?=null,val replaceValidationRejected:Boolean=false,val ec:String="",val runningPeriodVersion:Long?=null,
    val observations: Map<ObservationField, String> = emptyMap(),
    /** Set for a greenhouse-level record, whose [groupId] is blank (one nutrient tank per greenhouse). */
    val seasonId: String? = null, val entryBatchId: String? = null, val ageBasis: String = "", val ageDay: String = "",
    val sprayDate: String = "", val sprayPeriod: String = "",
    /** "before_injection" or "after_injection" (observations.measurement_moment, backend migration 21); blank when not given. */
    val measurementMoment: String = "") {
    val greenhouseLevel: Boolean get() = groupId.isBlank()
    fun validation(reasonRequired:Boolean=true): Map<String,String> = buildMap {
        if(greenhouseLevel) { if(runCatching { WireContract.uuid(seasonId.orEmpty()) }.isFailure) put("group","Greenhouse belum punya musim terbuka.") }
        else if(runCatching { WireContract.uuid(groupId) }.isFailure) put("group","Pilih kelompok tanam.")
        val entry = entryBatchId != null
        if(measurementMoment.isNotBlank() && measurementMoment !in setOf("before_injection","after_injection")) put("moment","Pilih sebelum atau sesudah inject.")
        if((ageBasis.isBlank() != ageDay.isBlank()) || (entry && ageBasis.isBlank())) put("age","Isi umur tanaman: HSS atau HST dan hari ke berapa.")
        if(ageDay.isNotBlank() && ageDay.trim().toIntOrNull()?.let { it in 1..1000 } != true) put("age","Hari ke berapa diisi angka 1 sampai 1000.")
        if(type==ActivityType.SPRAY && entry) {
            if(greenhouseLevel) put("group","Pilih varietas yang disemprot.")
            if(runCatching { LocalDate.parse(sprayDate) }.isFailure || sprayPeriod !in setOf("morning","afternoon")) put("spray","Isi tanggal semprot dan pilih pagi atau sore.")
        }
        if(runCatching { occurredAt() }.isFailure) put("occurred_at","Gunakan tanggal YYYY-MM-DD dan jam HH:mm (detik opsional) yang sah.")
        for((key,text) in listOf("manual_ec" to ec,"manual_ppm" to ppm,"manual_ph" to ph)) {
            if(text.isNotBlank()) {
                val number=runCatching { numeric(text) }.getOrNull()
                if(number==null || number<BigDecimal.ZERO || (key=="manual_ph" && number>BigDecimal("14"))) put(key,if(key=="manual_ph") "pH harus desimal 0–14." else "EC/PPM harus desimal tidak negatif.")
            }
        }
        if(type==ActivityType.MEASUREMENT) {
            if(ec.isBlank()) put("manual_ec","Isi EC manual.")
            if(skipped) put("work_state","Pengukuran harus selesai untuk dikirim.")
            if(materials.isNotEmpty()) put("materials","Catat pemberian bahan sebagai kegiatan nutrisi terpisah.")
        }
        if(skipped) { if(skippedReason.isBlank()) put("skipped_reason","Alasan kegiatan dilewati wajib diisi.") }
        else {
            // In the greenhouse entry form the readings live in its measurement section.
            if(type==ActivityType.NUTRITION && entryBatchId==null) { if(ppm.isBlank()) put("manual_ppm","Isi PPM manual.");if(ph.isBlank()) put("manual_ph","Isi pH manual.") }
            if(type in setOf(ActivityType.NUTRITION,ActivityType.SPRAY) && materials.isEmpty()) put("materials","Tambahkan sedikitnya satu bahan aktual.")
            if(type==ActivityType.STAGE && stage.isBlank()) put("actual_stage","Isi kondisi stadia aktual.")
            if(type==ActivityType.GENERAL && notes.isBlank()) put("notes","Isi keterangan pekerjaan umum.")
        }
        materials.forEachIndexed { index,row ->
            // A spray may name a new material and leave its dose empty; a nutrient may be typed (20 Nov 2026) but always needs a dose.
            val spray = type==ActivityType.SPRAY
            val typed = type in setOf(ActivityType.SPRAY,ActivityType.NUTRITION) && row.name.isNotBlank() && row.name.trim().length<=150
            val material = runCatching { WireContract.uuid(row.materialId) }.isSuccess || typed
            val unit = runCatching { WireContract.uuid(row.unitId) }.isSuccess || (type==ActivityType.NUTRITION && row.unitName in NutritionUnits)
            val dose = if(spray && !row.doseGiven) true else unit && runCatching { require(numeric(row.quantity)>=BigDecimal.ZERO) }.isSuccess
            if(!material || !dose) put("materials.$index",if(spray) "Pilih atau ketik bahan; dosis boleh kosong, tetapi jumlah dan satuan diisi bersama." else "Isi nama bahan, jumlah tidak negatif, dan satuan.")
        }
        observations.filterValues { it.isNotBlank() }.forEach { (field, value) ->
            val message = when (field) {
                ObservationField.SAMPLE_BASIS -> if (value.length > 500) "Keterangan sampel maksimal 500 karakter." else null
                ObservationField.LEAF_COUNT -> if (value.trim().toIntOrNull()?.let { it >= 0 } != true) "Isi jumlah daun sebagai bilangan bulat tidak negatif." else null
                else -> if (runCatching { numeric(value) }.getOrNull()?.let {
                    field == ObservationField.WATER_TEMPERATURE || it >= BigDecimal.ZERO
                } != true) "Isi ${field.label.lowercase()} sebagai desimal yang sah." else null
            }
            if (message != null) put("observations.${field.wire}", message)
        }
        if(materials.size>30) put("materials","Maksimal 30 bahan per catatan.")
        if(notes.length>2000) put("notes","Keterangan maksimal 2.000 karakter.")
        if(skippedReason.length>1000) put("skipped_reason","Alasan maksimal 1.000 karakter.")
        if(stage.length>100) put("actual_stage","Stadia maksimal 100 karakter.")
        if(reasonRequired && baseVersion!=null && reason.isBlank()) put("reason","Isi alasan koreksi.")
        if(reason.length>1000) put("reason","Catatan maksimal 1.000 karakter.")
    }
    fun occurredAt(): String = ManualTime.encode(date,time)
    fun payload(): JSONObject {
        require(validation(false).isEmpty()) { validation(false).values.joinToString("\n") }
        return JSONObject().put("planting_group_id",groupId.ifBlank { null } ?: JSONObject.NULL).put("schedule_id",scheduleId ?: JSONObject.NULL).put("activity_type",type.wire).put("occurred_at",occurredAt())
            .put("manual_ec",if(ec.isBlank()) JSONObject.NULL else numeric(ec).toPlainString()).put("manual_ppm",if(ppm.isBlank()) JSONObject.NULL else numeric(ppm).toPlainString()).put("manual_ph",if(ph.isBlank()) JSONObject.NULL else numeric(ph).toPlainString())
            .put("actual_stage",stage.takeIf { it.isNotBlank() } ?: JSONObject.NULL).put("work_state",if(skipped) "skipped" else "completed").put("skipped_reason",skippedReason.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("materials",JSONArray().apply { materials.forEach { row -> put(JSONObject().apply {
                if(row.materialId.isNotBlank()) put("material_id",row.materialId) else put("material_name",row.name.trim())
                put("unit_id",row.unitId.ifBlank { null } ?: JSONObject.NULL)
                if(row.unitId.isBlank() && row.unitName.isNotBlank()) put("unit_name",row.unitName)
                put("quantity",if(row.quantity.isBlank()) JSONObject.NULL else numeric(row.quantity).toPlainString())
            }) } }).put("notes",notes)
            .apply {
                seasonId?.let { put("season_id",it) }
                entryBatchId?.let { put("entry_batch_id",it) }
                if(ageBasis.isNotBlank()) put("age_basis",ageBasis).put("age_day",ageDay.trim().toInt())
                if(sprayDate.isNotBlank()) put("spray_date",sprayDate).put("spray_period",sprayPeriod)
            }
            .apply {
                val nonempty = observations.filterValues { it.isNotBlank() }
                if (nonempty.isNotEmpty() || measurementMoment.isNotBlank() || baseVersion != null) {
                    put("observations", if (nonempty.isEmpty() && measurementMoment.isBlank()) JSONObject.NULL else JSONObject().apply {
                        if (measurementMoment.isNotBlank()) put("measurement_moment", measurementMoment)
                        nonempty.forEach { (field, value) ->
                            put(field.wire, when (field) {
                                ObservationField.SAMPLE_BASIS -> value
                                ObservationField.LEAF_COUNT -> value.trim().toInt()
                                else -> numeric(value).toPlainString()
                            })
                        }
                    })
                }
            }
            .apply { if(baseVersion!=null) { if(reason.isNotBlank()) put("reason",reason);put("reference_policy",referencePolicy) } }
            .apply { if(baseVersion==null) runningPeriodVersion?.let { put("running_period_version",it) } }
    }
    fun localJson(): JSONObject = JSONObject().put("activity_type",type.wire).put("notes",notes).put("occurred_at",runCatching {occurredAt()}.getOrNull() ?: JSONObject.NULL).put("completeness","draft").put("_local_form",JSONObject()
        .put("id",id).put("greenhouse_id",greenhouseId).put("group_id",groupId).put("type",type.wire).put("date",date).put("time",time).put("ec",ec).put("ppm",ppm).put("ph",ph).put("stage",stage)
        .put("skipped",skipped).put("skipped_reason",skippedReason).put("notes",notes).put("schedule_id",scheduleId ?: JSONObject.NULL).put("reason",reason).put("reference_policy",referencePolicy).put("base_version",baseVersion ?: JSONObject.NULL).put("replace_validation_rejected",replaceValidationRejected)
        .put("running_period_version",runningPeriodVersion)
        .put("season_id",seasonId ?: JSONObject.NULL).put("entry_batch_id",entryBatchId ?: JSONObject.NULL).put("age_basis",ageBasis).put("age_day",ageDay)
        .put("spray_date",sprayDate).put("spray_period",sprayPeriod).put("measurement_moment",measurementMoment)
        .put("observations", JSONObject().apply { observations.forEach { (field, value) -> put(field.wire, value) } })
        .put("materials",JSONArray().apply { materials.forEach { put(JSONObject().put("material_id",it.materialId).put("unit_id",it.unitId).put("quantity",it.quantity).put("name",it.name).put("unit_name",it.unitName)) } }))
    companion object {
        val WIB: ZoneId = ManualTime.WIB
        fun numeric(text: String): BigDecimal = WireContract.decimal(text.trim().replace(',','.'))
        fun from(id:String,gh:String,body:JSONObject,base:Long?):ActivityDraft {
            val local=body.optJSONObject("_local_form")
            val raw=local ?: body
            val occurred=if(local==null) OffsetDateTime.parse(body.getString("occurred_at")).atZoneSameInstant(WIB) else null
            val materials=raw.optJSONArray("materials") ?: JSONArray()
            fun nullable(key:String)=raw.optString(key).takeUnless { raw.isNull(key) || it.isBlank() }
            fun text(key:String)=if(raw.isNull(key)) "" else raw.optString(key)
            return ActivityDraft(id,gh,text(if(local!=null) "group_id" else "planting_group_id"),ActivityType.entries.single { it.wire==raw.getString(if(local!=null) "type" else "activity_type") },
                local?.getString("date") ?: occurred!!.toLocalDate().toString(),local?.getString("time") ?: occurred!!.toLocalTime().format(DateTimeFormatter.ofPattern(if(occurred.second==0) "HH:mm" else "HH:mm:ss")),
                nullable(if(local!=null) "ppm" else "manual_ppm").orEmpty(),nullable(if(local!=null) "ph" else "manual_ph").orEmpty(),nullable(if(local!=null) "stage" else "actual_stage").orEmpty(),
                if(local!=null) raw.getBoolean("skipped") else raw.getString("work_state")=="skipped",nullable("skipped_reason").orEmpty(),raw.optString("notes"),
                (0 until materials.length()).map { materials.getJSONObject(it).let { row ->
                    fun field(key:String)=if(row.isNull(key)) "" else row.optString(key)
                    MaterialDraft(field("material_id"),field("unit_id"),field("quantity"),field("name").ifBlank { field("material_name") },field("unit_name")) } },nullable("schedule_id"),nullable("reason").orEmpty(),nullable("reference_policy") ?: "preserve",base,local?.optBoolean("replace_validation_rejected")==true,nullable(if(local!=null) "ec" else "manual_ec").orEmpty(),
                raw.optLong("running_period_version").takeIf { it > 0 },
                ObservationField.entries.mapNotNull { field ->
                    val values = raw.optJSONObject("observations")
                    values?.optString(field.wire)?.takeUnless { values.isNull(field.wire) }?.let { field to it }
                }.toMap(),
                nullable("season_id"), nullable("entry_batch_id"), text("age_basis"), text("age_day"), text("spray_date"), text("spray_period"),
                if(local!=null) text("measurement_moment") else raw.optJSONObject("observations")?.let { o -> if(o.isNull("measurement_moment")) "" else o.optString("measurement_moment") }.orEmpty())
        }
    }
}
