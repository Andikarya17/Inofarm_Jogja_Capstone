package com.inofarm.jogja.feature.admin

import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class AdminField(val key:String,val label:String,val optional:Boolean=false,val choices:Map<String,String> = emptyMap(),val source:String?=null,val type:String="text")
enum class AdminResource(val path:String,val kind:String,val label:String) {
    GREENHOUSE("greenhouses","greenhouses","Greenhouse"), SEASON("seasons","seasons","Musim"),
    GROUP("planting-groups","planting_groups","Kelompok tanam"), VARIETY("varieties","varieties","Varietas"),
    UNIT("units","units","Satuan"), MATERIAL("materials","materials","Bahan"), GRADE("grades","grades","Grade panen"),
    CATEGORY("expense-categories","expense_categories","Kategori biaya"), USER("users","users","Akun"),
    ASSIGNMENT("assignments","greenhouse_assignments","Penugasan"), SCHEDULE("schedules","schedules","Jadwal"), TARGET("targets","targets","Target");

    fun fields(update:Boolean,action:String):List<AdminField> = buildList {
        if(action in setOf("close","reopen")) { add(AdminField("reason","Alasan"));return@buildList }
        if((!update && this@AdminResource !in setOf(GREENHOUSE,GROUP,SCHEDULE,TARGET)) || this@AdminResource==USER)
            add(AdminField("greenhouse_id","Greenhouse",source="greenhouses"))
        when(this@AdminResource) {
            ASSIGNMENT -> {
                if(!update) add(AdminField("user_id","Akun",source="users"))
                add(AdminField("effective_from","Mulai penugasan",type="timestamp"));add(AdminField("effective_until","Akhir penugasan",true,type="timestamp"))
            }
            SCHEDULE,TARGET -> {
                if(!update) add(AdminField("planting_group_id","Kelompok tanam",source="planting-groups"))
                add(AdminField("activity_type","Jenis kegiatan",choices=mapOf("nutrition" to "Nutrisi","spray" to "Penyemprotan","general" to "Pekerjaan umum","stage" to "Stadia")))
                if(this@AdminResource==SCHEDULE) {add(AdminField("planned_at","Waktu rencana",type="timestamp"));add(AdminField("notes","Catatan",true))}
                else {
                    add(AdminField("parameter","Parameter",choices=mapOf("manual_ppm" to "PPM manual","manual_ph" to "pH manual")))
                    add(AdminField("unit","Satuan",choices=mapOf("ppm" to "ppm","pH" to "pH")))
                    add(AdminField("effective_from","Mulai berlaku",type="timestamp"));add(AdminField("effective_until","Akhir berlaku",true,type="timestamp"))
                    listOf("target_value" to "Nilai target","minimum" to "Batas minimum","maximum" to "Batas maksimum","tolerance" to "Toleransi").forEach { (key,label) -> add(AdminField(key,label,true,type="decimal")) }
                }
            }
            else -> {
                add(AdminField("name","Nama"))
                when(this@AdminResource) {
                    SEASON -> {add(AdminField("start_date","Tanggal mulai",type="date"));add(AdminField("end_date","Tanggal akhir",true,type="date"))}
                    GROUP -> {
                        if(!update) add(AdminField("season_id","Musim",source="seasons"))
                        add(AdminField("variety_id","Varietas",source="varieties"));add(AdminField("population","Populasi tanaman",type="integer"))
                        add(AdminField("sowing_date","Tanggal semai",type="date"));add(AdminField("transplant_date","Tanggal pindah",true,type="date"))
                    }
                    UNIT,MATERIAL -> add(AdminField("dimension","Dimensi",choices=mapOf("mass" to "Massa","volume" to "Volume","count" to "Jumlah")))
                    USER -> {
                        if(!update) add(AdminField("email","Email"))
                        add(AdminField("role","Peran",choices=mapOf("worker" to "Pekerja","manager" to "Pengelola")))
                    }
                    else -> Unit
                }
            }
        }
        if(update) {
            if(this@AdminResource!=SEASON) add(AdminField("active","Aktif",type="boolean"))
            add(AdminField("reason","Alasan perubahan"))
        }
    }
}

data class AdminForm(val resource:AdminResource,val id:String=UUID.randomUUID().toString(),val version:Long?=null,val action:String=if(version==null) "create" else "update",val values:Map<String,String> = emptyMap()) {
    val fields get()=resource.fields(version!=null,action)
    fun payload(password:String=""):JSONObject = JSONObject().apply {
        fields.forEach { field ->
            val value=values[field.key].orEmpty()
            require(field.optional || value.isNotBlank()) {"${field.label} wajib diisi."}
            put(field.key,when {
                value.isBlank() && field.optional && field.key!="notes" -> JSONObject.NULL
                field.type=="boolean" -> {require(value in setOf("true","false"));value.toBooleanStrict()}
                field.type=="integer" -> value.toInt().also {require(it>=0) {"${field.label} tidak boleh negatif."}}
                field.type=="date" -> LocalDate.parse(value).toString()
                field.type=="timestamp" -> OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
                field.type=="decimal" -> value.replace(',','.').also {require(it.matches(Regex("[0-9]+(\\.[0-9]+)?"))) {"${field.label} harus angka tanpa pemisah ribuan."}}
                else -> value.also {require(field.choices.isEmpty() || it in field.choices) {"Pilihan ${field.label} tidak sah."}}
            })
        }
        if(resource==AdminResource.USER) {
            require(version!=null || password.length>=12) {"Password akun baru minimal 12 karakter."}
            if(password.isNotEmpty()) {require(password.length in 12..256);put("password",password)}
        }
    }
    fun json()=JSONObject().put("resource",resource.name).put("id",id).put("version",version ?: JSONObject.NULL).put("action",action).put("values",JSONObject(values))
    companion object {
        fun parse(json:JSONObject):AdminForm {
            val values=json.getJSONObject("values")
            return AdminForm(AdminResource.valueOf(json.getString("resource")),json.getString("id"),if(json.isNull("version")) null else json.getLong("version"),json.getString("action"),values.keys().asSequence().associateWith {values.getString(it)})
        }
        fun edit(resource:AdminResource,row:JSONObject,action:String="update"):AdminForm {
            val form=AdminForm(resource,row.getString("id"),row.getLong("version"),action)
            return form.copy(values=form.fields.associate {field -> field.key to if(field.key=="reason" || row.isNull(field.key)) "" else row.get(field.key).toString()})
        }
    }
}
