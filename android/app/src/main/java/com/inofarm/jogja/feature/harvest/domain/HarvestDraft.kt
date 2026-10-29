package com.inofarm.jogja.feature.harvest.domain

import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.cultivation.ManualTime
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class HarvestLine(val id:String=UUID.randomUUID().toString(),val gradeId:String="",val fruitCount:String="",val weightKg:String="",val gradeName:String="")
data class HarvestDraft(val id:String=UUID.randomUUID().toString(),val greenhouseId:String,val groupId:String,
    val date:String=LocalDate.now(ManualTime.WIB).toString(),val time:String=LocalTime.now(ManualTime.WIB).format(DateTimeFormatter.ofPattern("HH:mm")),
    val items:List<HarvestLine> = emptyList(),val notes:String="",val reason:String="",val referencePolicy:String="preserve",val baseVersion:Long?=null,
    val replaceValidationRejected:Boolean=false,val lockedItems:Set<String> = emptySet()) {
    fun occurredAt()=ManualTime.encode(date,time)
    fun validation():Map<String,String> = buildMap {
        if(runCatching {WireContract.uuid(groupId)}.isFailure) put("group","Pilih kelompok tanam.")
        if(runCatching {occurredAt()}.isFailure) put("occurred_at","Gunakan tanggal YYYY-MM-DD dan jam HH:mm yang sah; detik opsional.")
        if(items.isEmpty() || items.size>30) put("items","Isi 1–30 rincian grade panen.")
        if(items.map {it.id}.distinct().size!=items.size || items.map {it.gradeId}.distinct().size!=items.size) put("items","Setiap grade hanya satu rincian dalam panen ini.")
        items.forEachIndexed {index,line ->
            if(runCatching {WireContract.uuid(line.id);WireContract.uuid(line.gradeId)}.isFailure) put("items.$index.grade","Pilih grade untuk rincian ${index+1}.")
            if(count(line.fruitCount)==null) put("items.$index.count","Jumlah buah harus integer 0–2.147.483.647; kosong bukan nol.")
            val weight=runCatching {number(line.weightKg)}.getOrNull()
            if(weight==null || weight<BigDecimal.ZERO) put("items.$index.weight","Isi bobot kg desimal tidak negatif, maksimal 6 angka di belakang koma.")
        }
        if(notes.length>2000) put("notes","Keterangan maksimal 2.000 karakter.")
        if(baseVersion!=null && reason.isBlank()) put("reason","Koreksi panen memerlukan alasan.")
        if(reason.length>1000) put("reason","Alasan maksimal 1.000 karakter.")
        if(referencePolicy !in setOf("preserve","refresh")) put("reference_policy","Pilih kebijakan acuan.")
    }
    fun payload():JSONObject {
        require(validation().isEmpty()) {validation().values.joinToString("\n")}
        return JSONObject().put("planting_group_id",groupId).put("occurred_at",occurredAt()).put("notes",notes)
            .put("items",JSONArray().apply {items.forEach {line -> put(JSONObject().put("id",line.id).put("grade_id",line.gradeId).put("fruit_count",count(line.fruitCount)!!).put("weight_kg",number(line.weightKg).toPlainString()))}})
            .apply {if(baseVersion!=null) {put("reason",reason);put("reference_policy",referencePolicy)}}
    }
    fun localJson()=JSONObject().put("planting_group_id",groupId).put("notes",notes).put("occurred_at",runCatching {occurredAt()}.getOrNull() ?: JSONObject.NULL).put("completeness","draft")
        .put("_local_form",JSONObject().put("group_id",groupId).put("date",date).put("time",time).put("notes",notes).put("reason",reason).put("reference_policy",referencePolicy)
            .put("base_version",baseVersion ?: JSONObject.NULL).put("replace_validation_rejected",replaceValidationRejected).put("locked_items",JSONArray(lockedItems.toList()))
            .put("items",JSONArray().apply {items.forEach {put(JSONObject().put("id",it.id).put("grade_id",it.gradeId).put("fruit_count",it.fruitCount).put("weight_kg",it.weightKg).put("grade_name",it.gradeName))}}))
    companion object {
        fun count(text:String):Int?=text.trim().takeIf {Regex("[0-9]+").matches(it)}?.toIntOrNull()
        fun number(text:String)=WireContract.decimal(text.trim().replace(',','.'))
        fun from(id:String,gh:String,body:JSONObject,base:Long?):HarvestDraft {
            val local=body.optJSONObject("_local_form");val raw=local ?: body
            val time=if(local==null) ManualTime.decode(body.getString("occurred_at")) else local.getString("date") to local.getString("time")
            val items=raw.optJSONArray("items") ?: JSONArray()
            val lines=(0 until items.length()).map {items.getJSONObject(it).let {row -> HarvestLine(row.getString("id"),row.getString("grade_id"),row.optString("fruit_count"),row.optString("weight_kg"),row.optString("grade_name"))}}
            val locked=local?.optJSONArray("locked_items")?.let {array -> (0 until array.length()).map {array.getString(it)}.toSet()} ?: if(base==null) emptySet() else lines.map {it.id}.toSet()
            return HarvestDraft(id,gh,raw.getString(if(local!=null) "group_id" else "planting_group_id"),time.first,time.second,lines,raw.optString("notes"),raw.optString("reason").takeUnless {raw.isNull("reason")}.orEmpty(),
                raw.optString("reference_policy","preserve"),base,local?.optBoolean("replace_validation_rejected")==true,locked)
        }
    }
}
