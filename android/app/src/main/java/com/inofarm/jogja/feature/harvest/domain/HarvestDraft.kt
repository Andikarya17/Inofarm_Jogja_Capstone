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

enum class HarvestQuality(val wire: String, val label: String) {
    SALEABLE("saleable", "Layak"), DEFECTIVE("defective", "Cacat"),
}

data class HarvestLine(val id:String=UUID.randomUUID().toString(),val gradeId:String="",val fruitCount:String="",val weightKg:String="",val gradeName:String="", val quality: HarvestQuality? = null) {
    /**
     * A line filed under a real grade, from before 20 Nov 2026; it keeps its grade and is always sent. A line under the
     * system grade of its own quality ("Layak"/"Cacat") is a layak/cacat line, whose grade the server fills again.
     */
    val legacy: Boolean get() = gradeId.isNotBlank() && !(quality != null && gradeName.trim().equals(quality.label, ignoreCase = true))
    /** Whether the line is sent: a graded line always, a layak/cacat line only once something is typed in it. */
    val sent: Boolean get() = legacy || fruitCount.isNotBlank() || weightKg.isNotBlank()
}
data class HarvestDraft(val id:String=UUID.randomUUID().toString(),val greenhouseId:String,val groupId:String,
    val date:String=LocalDate.now(ManualTime.WIB).toString(),val time:String=LocalTime.now(ManualTime.WIB).format(DateTimeFormatter.ofPattern("HH:mm")),
    val items:List<HarvestLine> = emptyList(),val notes:String="",val reason:String="",val referencePolicy:String="preserve",val baseVersion:Long?=null,
    val replaceValidationRejected:Boolean=false,val lockedItems:Set<String> = emptySet(),val runningPeriodVersion:Long?=null, val requireQuality: Boolean = false,
    /**
     * The second variety of the same greenhouse, harvested in the same session (user decision 17 Nov 2026). It shares
     * time and notes, keeps its own record id and lines, and is sent as its own harvest in the same local transaction.
     */
    val companion: HarvestDraft? = null) {
    fun occurredAt()=ManualTime.encode(date,time)
    private val sentItems: List<HarvestLine> get() = items.filter { it.sent }

    /**
     * Inofarm 20 Nov 2026: one Layak and one Cacat line per variety, existing line ids kept. A harvest with graded
     * lines (recorded before) is left as it is.
     */
    fun qualityLines(): HarvestDraft {
        val second = companion?.qualityLines()
        if (items.any { it.legacy }) return copy(companion = second)
        val lines = HarvestQuality.entries.map { quality -> items.firstOrNull { it.quality == quality } ?: HarvestLine(quality = quality) }
        return copy(items = lines, companion = second)
    }

    /** The harvest records this form becomes: this variety and, when it has lines, the second one. */
    fun parts():List<HarvestDraft> = listOfNotNull(copy(companion=null).takeIf { companion==null || it.sentItems.isNotEmpty() },
        companion?.copy(date=date,time=time,notes=notes,runningPeriodVersion=null,companion=null)?.takeIf { it.sentItems.isNotEmpty() })
    fun validation(reasonRequired:Boolean=true):Map<String,String> {
        val second=companion ?: return single(reasonRequired)
        // Either variety may have no harvest this time, but not both.
        if(sentItems.isEmpty() && second.sentItems.isEmpty()) return mapOf("items" to "Isi bobot panen untuk sedikitnya satu varietas.")
        return buildMap {
            if(sentItems.isNotEmpty()) putAll(copy(companion=null).single(reasonRequired))
            if(second.sentItems.isNotEmpty()) second.copy(date=date,time=time,notes=notes).single(reasonRequired).forEach { (key,value) -> put("companion.$key",value) }
        }
    }
    private fun single(reasonRequired:Boolean):Map<String,String> = buildMap {
        if(runCatching {WireContract.uuid(groupId)}.isFailure) put("group","Pilih kelompok tanam.")
        if(runCatching {occurredAt()}.isFailure) put("occurred_at","Pilih tanggal panen dari kalender.")
        else if(com.inofarm.jogja.core.cultivation.ManualTime.afterToday(occurredAt())) put("occurred_at","Tanggal panen tidak boleh sesudah hari ini.")
        val sent=sentItems
        if(sent.isEmpty() || sent.size>30) put("items",if(items.any { it.legacy }) "Isi 1–30 rincian grade panen." else "Isi bobot buah layak atau cacat.")
        val graded=sent.filter { it.legacy }; val rated=sent.filterNot { it.legacy }
        if(sent.map {it.id}.distinct().size!=sent.size || graded.map {it.gradeId}.distinct().size!=graded.size || rated.map {it.quality}.distinct().size!=rated.size)
            put("items","Setiap grade atau kualitas hanya satu rincian dalam panen ini.")
        items.forEachIndexed {index,line ->
            if(!line.sent) return@forEachIndexed
            if(runCatching {WireContract.uuid(line.id)}.isFailure || (line.legacy && runCatching {WireContract.uuid(line.gradeId)}.isFailure))
                put("items.$index.grade","Pilih grade untuk rincian ${index+1}.")
            if ((requireQuality || !line.legacy) && line.quality == null) put("items.$index.quality", "Pilih layak atau cacat untuk rincian ${index + 1}.")
            // Inofarm weighs the harvest; the fruit count is optional and empty is not zero (17 Nov 2026).
            if(line.fruitCount.isNotBlank() && count(line.fruitCount)==null) put("items.$index.count","Isi jumlah buah sebagai bilangan bulat, atau kosongkan.")
            val weight=runCatching {number(line.weightKg)}.getOrNull()
            if(weight==null || weight<BigDecimal.ZERO) put("items.$index.weight","Isi bobot kg desimal tidak negatif, maksimal 6 angka di belakang koma.")
        }
        if(notes.length>2000) put("notes","Keterangan maksimal 2.000 karakter.")
        if(reasonRequired && baseVersion!=null && reason.isBlank()) put("reason","Isi alasan koreksi.")
        if(reason.length>1000) put("reason","Catatan maksimal 1.000 karakter.")
        if(referencePolicy !in setOf("preserve","refresh")) put("reference_policy","Pilih kebijakan acuan.")
    }
    fun payload():JSONObject {
        require(validation(false).isEmpty()) {validation(false).values.joinToString("\n")}
        return JSONObject().put("planting_group_id",groupId).put("occurred_at",occurredAt()).put("notes",notes)
            .put("items",JSONArray().apply {sentItems.forEach {line -> put(JSONObject().put("id",line.id).apply { if(line.legacy) put("grade_id",line.gradeId) }.put("fruit_count",count(line.fruitCount) ?: JSONObject.NULL).put("weight_kg",number(line.weightKg).toPlainString())
                .apply { line.quality?.let { put("quality", it.wire) } })}})
            .apply {if(baseVersion!=null) {if(reason.isNotBlank()) put("reason",reason);put("reference_policy",referencePolicy)}}
            .apply {if(baseVersion==null) runningPeriodVersion?.let {put("running_period_version",it)}}
    }
    fun localJson():JSONObject=JSONObject().put("planting_group_id",groupId).put("notes",notes).put("occurred_at",runCatching {occurredAt()}.getOrNull() ?: JSONObject.NULL).put("completeness","draft")
        .put("_local_form",JSONObject().put("group_id",groupId).put("date",date).put("time",time).put("notes",notes).put("reason",reason).put("reference_policy",referencePolicy)
            .put("base_version",baseVersion ?: JSONObject.NULL).put("replace_validation_rejected",replaceValidationRejected).put("locked_items",JSONArray(lockedItems.toList()))
            .put("running_period_version",runningPeriodVersion).put("require_quality",requireQuality)
            .put("items",JSONArray().apply {items.forEach { line -> put(JSONObject().put("id",line.id).put("grade_id",line.gradeId)
                .put("fruit_count",line.fruitCount).put("weight_kg",line.weightKg).put("grade_name",line.gradeName)
                .put("quality", line.quality?.wire ?: JSONObject.NULL)) }})
            .put("companion",companion?.localJson()?.getJSONObject("_local_form")?.put("id",companion.id) ?: JSONObject.NULL))
    companion object {
        /** Harvests are recorded by date only (Inofarm, 20 Nov 2026); the time sent is noon WIB. */
        const val NOON = "12:00"
        fun count(text:String):Int?=text.trim().takeIf {Regex("[0-9]+").matches(it)}?.toIntOrNull()
        fun number(text:String)=WireContract.decimal(text.trim().replace(',','.'))
        fun from(id:String,gh:String,body:JSONObject,base:Long?):HarvestDraft {
            val local=body.optJSONObject("_local_form");val raw=local ?: body
            val time=if(local==null) ManualTime.decode(body.getString("occurred_at")) else local.getString("date") to local.getString("time")
            val items=raw.optJSONArray("items") ?: JSONArray()
            val lines=(0 until items.length()).map {items.getJSONObject(it).let {row -> HarvestLine(row.getString("id"),if(row.isNull("grade_id")) "" else row.optString("grade_id"),if(row.isNull("fruit_count")) "" else row.optString("fruit_count"),com.inofarm.jogja.core.design.FarmFormat.input(row.optString("weight_kg")),row.optString("grade_name"),
                HarvestQuality.entries.firstOrNull { it.wire == row.optString("quality") })}}
            val locked=local?.optJSONArray("locked_items")?.let {array -> (0 until array.length()).map {array.getString(it)}.toSet()} ?: if(base==null) emptySet() else lines.map {it.id}.toSet()
            return HarvestDraft(id,gh,raw.getString(if(local!=null) "group_id" else "planting_group_id"),time.first,time.second,lines,raw.optString("notes"),raw.optString("reason").takeUnless {raw.isNull("reason")}.orEmpty(),
                raw.optString("reference_policy","preserve"),base,local?.optBoolean("replace_validation_rejected")==true,locked,
                raw.optLong("running_period_version").takeIf { it > 0 }, local?.optBoolean("require_quality") == true,
                local?.optJSONObject("companion")?.let { form -> from(form.getString("id"),gh,JSONObject().put("_local_form",form),null) })
        }
    }
}
