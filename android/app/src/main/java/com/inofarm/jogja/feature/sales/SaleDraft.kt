package com.inofarm.jogja.feature.sales

import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.cultivation.ManualTime
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class SaleLine(val id:String=UUID.randomUUID().toString(),val sourceId:String="",val quantity:String="",val price:String="")
data class SaleDraft(val id:String=UUID.randomUUID().toString(),val greenhouseId:String,val seasonId:String="",
    val date:String=LocalDate.now(ManualTime.WIB).toString(),val time:String=LocalTime.now(ManualTime.WIB).format(DateTimeFormatter.ofPattern("HH:mm")),
    val paid:Boolean=false,val customer:String="",val notes:String="",val items:List<SaleLine> = listOf(SaleLine()),val reason:String="",val baseVersion:Long?=null,
    val lockedSources:Set<String> = emptySet(),val lockedSeason:Boolean=false,val replaceValidationRejected:Boolean=false) {
    fun paidAt()=ManualTime.encode(date,time)
    fun preview()=SaleAmounts.quote(items.map {decimal(it.quantity,true).toPlainString() to decimal(it.price,false).toPlainString()})
    fun validation():Map<String,String> = buildMap {
        if(runCatching {WireContract.uuid(greenhouseId)}.isFailure) put("greenhouse","Pilih greenhouse.")
        if(runCatching {WireContract.uuid(seasonId)}.isFailure) put("season","Pilih musim penjualan.")
        if(runCatching {paidAt()}.isFailure) put("paid_at","Isi tanggal YYYY-MM-DD dan jam HH:mm WIB yang sah.")
        if(!paid) put("paid","Konfirmasikan pembayaran sudah lunas sebelum pengajuan.")
        if(customer.length>200) put("customer","Nama pembeli maksimal200 karakter.")
        if(notes.length>2000) put("notes","Keterangan maksimal2.000 karakter.")
        if(items.size !in 1..30) put("items","Sertakan1–30 rincian sumber panen.")
        if(items.map {it.id}.toSet().size!=items.size || items.map {it.sourceId}.toSet().size!=items.size) put("items","Identitas rincian dan sumber panen tidak boleh berulang.")
        items.forEachIndexed {index,line ->
            if(runCatching {WireContract.uuid(line.id);WireContract.uuid(line.sourceId)}.isFailure) put("items.$index.source","Pilih sumber panen yang telah diterima server.")
            if(runCatching {decimal(line.quantity,true)}.isFailure) put("items.$index.quantity","Bobot kg harus lebih dari nol, maksimal6 desimal; kosong bukan nol.")
            if(runCatching {decimal(line.price,false)}.isFailure) put("items.$index.price","Harga IDR/kg wajib tidak negatif, maksimal6 desimal; kosong bukan nol.")
        }
        if(items.size in 1..30 && runCatching {preview()}.isFailure && keys.none {it.endsWith("quantity") || it.endsWith("price")}) put("total","Total uang atau bobot melebihi rentang angka yang didukung.")
        if(baseVersion!=null && reason.isBlank()) put("reason","Koreksi memerlukan alasan.")
        if(reason.length>1000) put("reason","Alasan maksimal1.000 karakter.")
    }
    fun payload():JSONObject {
        require(validation().isEmpty()) {validation().values.joinToString("\n")}
        return JSONObject().put("season_id",seasonId).put("paid_at",paidAt()).put("payment_status","paid").put("customer_name",customer).put("notes",notes)
            .put("items",JSONArray().also {rows -> items.forEach {rows.put(JSONObject().put("id",it.id).put("harvest_item_id",it.sourceId).put("quantity_kg",decimal(it.quantity,true).toPlainString()).put("price_per_kg",decimal(it.price,false).toPlainString()))}})
            .apply {if(baseVersion!=null) put("reason",reason)}
    }
    fun localJson():JSONObject {
        val form=JSONObject().put("season_id",seasonId).put("date",date).put("time",time).put("paid",paid).put("customer_name",customer).put("notes",notes).put("reason",reason)
            .put("locked_season",lockedSeason).put("locked_sources",JSONArray(lockedSources.toList())).put("replace_validation_rejected",replaceValidationRejected)
            .put("items",JSONArray().also {rows -> items.forEach {rows.put(JSONObject().put("id",it.id).put("source_id",it.sourceId).put("quantity",it.quantity).put("price",it.price))}})
        return JSONObject().put("season_id",seasonId).put("paid_at",runCatching {paidAt()}.getOrNull() ?: JSONObject.NULL).put("customer_name",customer).put("notes",notes).put("completeness","draft").put("_local_form",form)
    }
    companion object {
        fun decimal(text:String,positive:Boolean):BigDecimal {
            val value=text.trim().replace(',','.');require(!value.startsWith('-'));val parsed=WireContract.decimal(value,6);require(!positive || parsed.signum()>0)
            return parsed.setScale(6)
        }
        fun from(id:String,gh:String,body:JSONObject,base:Long?):SaleDraft {
            val local=body.optJSONObject("_local_form");val raw=local ?: body
            val stamp=if(local!=null) raw.optString("date") to raw.optString("time") else ManualTime.decode(body.getString("paid_at"))
            val rows=raw.getJSONArray("items");val lines=(0 until rows.length()).map {rows.getJSONObject(it).let {line -> SaleLine(line.getString("id"),line.getString(if(local!=null) "source_id" else "harvest_item_id"),line.getString(if(local!=null) "quantity" else "quantity_kg"),line.getString(if(local!=null) "price" else "price_per_kg"))}}
            val locked=local?.optJSONArray("locked_sources")?.let {a -> (0 until a.length()).map {a.getString(it)}.toSet()} ?: if(base!=null) lines.map {it.id}.toSet() else emptySet()
            return SaleDraft(id,gh,raw.optString("season_id"),stamp.first,stamp.second,if(local!=null) raw.optBoolean("paid") else body.optString("payment_status")=="paid",raw.optString("customer_name"),raw.optString("notes"),lines,raw.optString("reason"),base,locked,local?.optBoolean("locked_season") ?: (base!=null),local?.optBoolean("replace_validation_rejected")==true)
        }
    }
}
