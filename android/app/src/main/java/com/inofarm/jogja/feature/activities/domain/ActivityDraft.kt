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

enum class ActivityType(val wire: String,val label: String) { GENERAL("general","Pekerjaan umum"), NUTRITION("nutrition","Nutrisi"), SPRAY("spray","Penyemprotan"), STAGE("stage","Stadia") }
data class MaterialDraft(val materialId: String="",val unitId: String="",val quantity: String="")
data class ActivityDraft(val id: String=UUID.randomUUID().toString(),val greenhouseId: String,val groupId: String,
    val type: ActivityType=ActivityType.GENERAL,val date: String=LocalDate.now(WIB).toString(),val time: String=LocalTime.now(WIB).format(DateTimeFormatter.ofPattern("HH:mm")),
    val ppm: String="",val ph: String="",val stage: String="",val skipped: Boolean=false,val skippedReason: String="",val notes: String="",
    val materials: List<MaterialDraft> = emptyList(),val scheduleId: String?=null,val reason: String="",val referencePolicy: String="preserve",val baseVersion: Long?=null,val replaceValidationRejected:Boolean=false) {
    fun validation(): Map<String,String> = buildMap {
        if(runCatching { WireContract.uuid(groupId) }.isFailure) put("group","Pilih kelompok tanam.")
        if(runCatching { occurredAt() }.isFailure) put("occurred_at","Gunakan tanggal YYYY-MM-DD dan jam HH:mm (detik opsional) yang sah.")
        for((key,text) in listOf("manual_ppm" to ppm,"manual_ph" to ph)) {
            if(text.isNotBlank()) {
                val number=runCatching { numeric(text) }.getOrNull()
                if(number==null || number<BigDecimal.ZERO || (key=="manual_ph" && number>BigDecimal("14"))) put(key,if(key=="manual_ph") "pH harus desimal 0–14." else "PPM harus desimal tidak negatif.")
            }
        }
        if(skipped) { if(skippedReason.isBlank()) put("skipped_reason","Alasan kegiatan dilewati wajib diisi.") }
        else {
            if(type==ActivityType.NUTRITION) { if(ppm.isBlank()) put("manual_ppm","PPM manual wajib; kosong bukan nol.");if(ph.isBlank()) put("manual_ph","pH manual wajib; kosong bukan nol.") }
            if(type in setOf(ActivityType.NUTRITION,ActivityType.SPRAY) && materials.isEmpty()) put("materials","Tambahkan sedikitnya satu bahan aktual.")
            if(type==ActivityType.STAGE && stage.isBlank()) put("actual_stage","Isi kondisi stadia aktual.")
            if(type==ActivityType.GENERAL && notes.isBlank()) put("notes","Isi keterangan pekerjaan umum.")
        }
        materials.forEachIndexed { index,row ->
            if(runCatching { WireContract.uuid(row.materialId);WireContract.uuid(row.unitId);require(numeric(row.quantity)>=BigDecimal.ZERO) }.isFailure) put("materials.$index","Pilih bahan, satuan, dan jumlah tidak negatif yang sah.")
        }
        if(materials.size>30) put("materials","Maksimal 30 bahan per catatan.")
        if(notes.length>2000) put("notes","Keterangan maksimal 2.000 karakter.")
        if(skippedReason.length>1000) put("skipped_reason","Alasan maksimal 1.000 karakter.")
        if(stage.length>100) put("actual_stage","Stadia maksimal 100 karakter.")
        if(baseVersion!=null && reason.isBlank()) put("reason","Koreksi memerlukan alasan.")
        if(reason.length>1000) put("reason","Alasan koreksi maksimal 1.000 karakter.")
    }
    fun occurredAt(): String = ManualTime.encode(date,time)
    fun payload(): JSONObject {
        require(validation().isEmpty()) { validation().values.joinToString("\n") }
        return JSONObject().put("planting_group_id",groupId).put("schedule_id",scheduleId ?: JSONObject.NULL).put("activity_type",type.wire).put("occurred_at",occurredAt())
            .put("manual_ppm",if(ppm.isBlank()) JSONObject.NULL else numeric(ppm).toPlainString()).put("manual_ph",if(ph.isBlank()) JSONObject.NULL else numeric(ph).toPlainString())
            .put("actual_stage",stage.takeIf { it.isNotBlank() } ?: JSONObject.NULL).put("work_state",if(skipped) "skipped" else "completed").put("skipped_reason",skippedReason.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("materials",JSONArray().apply { materials.forEach { put(JSONObject().put("material_id",it.materialId).put("unit_id",it.unitId).put("quantity",numeric(it.quantity).toPlainString())) } }).put("notes",notes)
            .apply { if(baseVersion!=null) { put("reason",reason);put("reference_policy",referencePolicy) } }
    }
    fun localJson(): JSONObject = JSONObject().put("activity_type",type.wire).put("notes",notes).put("occurred_at",runCatching {occurredAt()}.getOrNull() ?: JSONObject.NULL).put("completeness","draft").put("_local_form",JSONObject()
        .put("id",id).put("greenhouse_id",greenhouseId).put("group_id",groupId).put("type",type.wire).put("date",date).put("time",time).put("ppm",ppm).put("ph",ph).put("stage",stage)
        .put("skipped",skipped).put("skipped_reason",skippedReason).put("notes",notes).put("schedule_id",scheduleId ?: JSONObject.NULL).put("reason",reason).put("reference_policy",referencePolicy).put("base_version",baseVersion ?: JSONObject.NULL).put("replace_validation_rejected",replaceValidationRejected)
        .put("materials",JSONArray().apply { materials.forEach { put(JSONObject().put("material_id",it.materialId).put("unit_id",it.unitId).put("quantity",it.quantity)) } }))
    companion object {
        val WIB: ZoneId = ManualTime.WIB
        fun numeric(text: String): BigDecimal = WireContract.decimal(text.trim().replace(',','.'))
        fun from(id:String,gh:String,body:JSONObject,base:Long?):ActivityDraft {
            val local=body.optJSONObject("_local_form")
            val raw=local ?: body
            val occurred=if(local==null) OffsetDateTime.parse(body.getString("occurred_at")).atZoneSameInstant(WIB) else null
            val materials=raw.optJSONArray("materials") ?: JSONArray()
            fun nullable(key:String)=raw.optString(key).takeUnless { raw.isNull(key) || it.isBlank() }
            return ActivityDraft(id,gh,raw.getString(if(local!=null) "group_id" else "planting_group_id"),ActivityType.entries.single { it.wire==raw.getString(if(local!=null) "type" else "activity_type") },
                local?.getString("date") ?: occurred!!.toLocalDate().toString(),local?.getString("time") ?: occurred!!.toLocalTime().format(DateTimeFormatter.ofPattern(if(occurred.second==0) "HH:mm" else "HH:mm:ss")),
                nullable(if(local!=null) "ppm" else "manual_ppm").orEmpty(),nullable(if(local!=null) "ph" else "manual_ph").orEmpty(),nullable(if(local!=null) "stage" else "actual_stage").orEmpty(),
                if(local!=null) raw.getBoolean("skipped") else raw.getString("work_state")=="skipped",nullable("skipped_reason").orEmpty(),raw.optString("notes"),
                (0 until materials.length()).map { materials.getJSONObject(it).let { row -> MaterialDraft(row.getString("material_id"),row.getString("unit_id"),row.getString("quantity")) } },nullable("schedule_id"),nullable("reason").orEmpty(),nullable("reference_policy") ?: "preserve",base,local?.optBoolean("replace_validation_rejected")==true)
        }
    }
}
