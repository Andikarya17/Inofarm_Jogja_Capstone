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

/** [grade] is the mitra's own grade A/B/C (21 Nov 2026); blank for other buyers. */
data class SaleLine(val id:String=UUID.randomUUID().toString(),val sourceId:String="",val quantity:String="",val price:String="",val grade:String="")
data class SaleDraft(val id:String=UUID.randomUUID().toString(),val greenhouseId:String,val seasonId:String="",
    val date:String=LocalDate.now(ManualTime.WIB).toString(),val time:String=LocalTime.now(ManualTime.WIB).format(DateTimeFormatter.ofPattern("HH:mm")),
    val paid:Boolean=false,val customer:String="",val notes:String="",val items:List<SaleLine> = listOf(SaleLine()),val reason:String="",val baseVersion:Long?=null,
    val lockedSources:Set<String> = emptySet(),val lockedSeason:Boolean=false,val replaceValidationRejected:Boolean=false,
    /** Named buyer (21 Nov 2026): "consumer" or "partner" with a buyer id; [customer] holds the name. Blank type = no buyer. */
    val buyerType:String="",val buyerId:String="",val buyerWhatsapp:String="",val buyerExisting:Boolean=false) {
    fun hasBuyer()=buyerType.isNotBlank()
    fun paidAt()=ManualTime.encode(date,time)
    fun preview()=SaleAmounts.quote(items.map {decimal(it.quantity,true).toPlainString() to decimal(it.price,false).toPlainString()})
    fun validation(reasonRequired:Boolean=true):Map<String,String> = buildMap {
        if(runCatching {WireContract.uuid(greenhouseId)}.isFailure) put("greenhouse","Pilih greenhouse.")
        if(runCatching {WireContract.uuid(seasonId)}.isFailure) put("season","Pilih musim penjualan.")
        if(runCatching {paidAt()}.isFailure) put("paid_at","Pilih tanggal dan jam lunas dari kalender dan jam.")
        if(!paid) put("paid","Konfirmasikan pembayaran sudah lunas sebelum menyimpan.")
        if(customer.length>200) put("customer","Nama pembeli maksimal200 karakter.")
        if(notes.length>2000) put("notes","Keterangan maksimal2.000 karakter.")
        if(items.size !in 1..30) put("items","Sertakan1–30 rincian sumber panen.")
        if(items.map {it.id}.toSet().size!=items.size || items.map {it.sourceId to it.grade}.toSet().size!=items.size) put("items","Sumber panen hanya boleh berulang dengan grade mitra yang berbeda.")
        if(items.any {it.grade.isNotBlank()} && buyerType!="partner") put("items","Grade A, B, C hanya untuk penjualan ke mitra.")
        if(items.any {it.grade.isNotBlank() && it.grade !in setOf("A","B","C")}) put("items","Grade mitra harus A, B, atau C.")
        items.forEachIndexed {index,line ->
            if(runCatching {WireContract.uuid(line.id);WireContract.uuid(line.sourceId)}.isFailure) put("items.$index.source","Pilih sumber panen yang sudah terkirim.")
            if(runCatching {decimal(line.quantity,true)}.isFailure) put("items.$index.quantity","Isi bobot lebih dari nol, misalnya 12,5.")
            if(runCatching {decimal(line.price,false)}.isFailure) put("items.$index.price","Isi harga per kg. Tulis 0 bila memang gratis.")
        }
        if(items.size in 1..30 && runCatching {preview()}.isFailure && keys.none {it.endsWith("quantity") || it.endsWith("price")}) put("total","Total uang atau bobot melebihi rentang angka yang didukung.")
        if(reasonRequired && baseVersion!=null && reason.isBlank()) put("reason","Isi alasan koreksi.")
        if(reason.length>1000) put("reason","Catatan maksimal 1.000 karakter.")
        if(hasBuyer()) {
            if(buyerType !in setOf("consumer","partner") || runCatching {WireContract.uuid(buyerId)}.isFailure) put("customer","Pilih pembeli terdaftar atau isi pembeli baru.")
            else if(customer.isBlank()) put("customer","Isi nama pembeli.")
        }
    }
    fun payload():JSONObject {
        require(validation(false).isEmpty()) {validation(false).values.joinToString("\n")}
        return JSONObject().put("season_id",seasonId).put("paid_at",paidAt()).put("payment_status","paid").put("customer_name",customer).put("notes",notes)
            .put("items",JSONArray().also {rows -> items.forEach {rows.put(JSONObject().put("id",it.id).put("harvest_item_id",it.sourceId).put("quantity_kg",decimal(it.quantity,true).toPlainString()).put("price_per_kg",decimal(it.price,false).toPlainString()).apply {if(it.grade.isNotBlank()) put("buyer_grade",it.grade)})}})
            .apply {if(baseVersion!=null && reason.isNotBlank()) put("reason",reason)}
            .apply {
                if(hasBuyer()) put("customer_type",buyerType).put("customer",JSONObject().put("id",buyerId).put("type",buyerType).put("name",customer.trim())
                    .put("whatsapp",buyerWhatsapp.trim().ifBlank {null} ?: JSONObject.NULL))
                // Explicit nulls: a correction to "Tidak dicatat" unlinks the stored buyer instead of keeping it.
                else put("customer_type",JSONObject.NULL).put("customer",JSONObject.NULL)
            }
    }
    fun localJson():JSONObject {
        val form=JSONObject().put("season_id",seasonId).put("date",date).put("time",time).put("paid",paid).put("customer_name",customer).put("notes",notes).put("reason",reason)
            .put("locked_season",lockedSeason).put("locked_sources",JSONArray(lockedSources.toList())).put("replace_validation_rejected",replaceValidationRejected)
            .put("buyer_type",buyerType).put("buyer_id",buyerId).put("buyer_whatsapp",buyerWhatsapp).put("buyer_existing",buyerExisting)
            .put("items",JSONArray().also {rows -> items.forEach {rows.put(JSONObject().put("id",it.id).put("source_id",it.sourceId).put("quantity",it.quantity).put("price",it.price).put("grade",it.grade))}})
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
            val rows=raw.getJSONArray("items");val lines=(0 until rows.length()).map {rows.getJSONObject(it).let {line -> SaleLine(line.getString("id"),line.getString(if(local!=null) "source_id" else "harvest_item_id"),line.getString(if(local!=null) "quantity" else "quantity_kg"),line.getString(if(local!=null) "price" else "price_per_kg"),if(local!=null) line.optString("grade") else line.optString("buyer_grade").takeIf {!line.isNull("buyer_grade")}.orEmpty())}}
            val locked=local?.optJSONArray("locked_sources")?.let {a -> (0 until a.length()).map {a.getString(it)}.toSet()} ?: if(base!=null) lines.map {it.id}.toSet() else emptySet()
            return SaleDraft(id,gh,raw.optString("season_id"),stamp.first,stamp.second,if(local!=null) raw.optBoolean("paid") else body.optString("payment_status")=="paid",raw.optString("customer_name"),raw.optString("notes"),lines,raw.optString("reason"),base,locked,local?.optBoolean("locked_season") ?: (base!=null),local?.optBoolean("replace_validation_rejected")==true,
                buyerType=local?.optString("buyer_type") ?: body.optString("customer_type").takeIf {!body.isNull("customer_type") && !body.isNull("customer_id")}.orEmpty(),
                buyerId=local?.optString("buyer_id") ?: body.optString("customer_id").takeIf {!body.isNull("customer_id")}.orEmpty(),
                buyerWhatsapp=local?.optString("buyer_whatsapp").orEmpty(),
                buyerExisting=local?.optBoolean("buyer_existing") ?: !body.isNull("customer_id"))
        }
    }
}
