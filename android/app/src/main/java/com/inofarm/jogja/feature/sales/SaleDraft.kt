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

/**
 * One sale line (25 Nov 2026): a planting group and a grade, not a harvest item; the server takes the kg from the oldest
 * harvests. [buyerGrade] is the mitra's own grade A/B/C and stays blank for other buyers. [fruitCount] blank = unknown.
 */
data class SaleLine(val id:String=UUID.randomUUID().toString(),val groupId:String="",val gradeId:String="",val buyerGrade:String="",val quantity:String="",val fruitCount:String="",val price:String="")
data class SaleDraft(val id:String=UUID.randomUUID().toString(),val greenhouseId:String,val seasonId:String="",
    val date:String=LocalDate.now(ManualTime.WIB).toString(),val time:String=LocalTime.now(ManualTime.WIB).format(DateTimeFormatter.ofPattern("HH:mm")),
    val paid:Boolean=false,val customer:String="",val notes:String="",val items:List<SaleLine> = listOf(SaleLine()),val reason:String="",val baseVersion:Long?=null,
    val lockedSources:Set<String> = emptySet(),val lockedSeason:Boolean=false,val replaceValidationRejected:Boolean=false,
    /** Buyer type "consumer", "retail" or "partner" (required for a new sale); [customer] holds the name of the registered buyer in [buyerId]. */
    val buyerType:String="",val buyerId:String="",val buyerWhatsapp:String="",val buyerExisting:Boolean=false) {
    fun hasBuyer()=buyerType.isNotBlank()
    private fun hasCustomer()=hasBuyer() && (buyerType!="consumer" || buyerId.isNotBlank())
    fun paidAt()=ManualTime.encode(date,time)
    fun preview()=SaleAmounts.quote(items.map {decimal(it.quantity,true).toPlainString() to decimal(it.price,false).toPlainString()})
    fun validation(reasonRequired:Boolean=true):Map<String,String> = buildMap {
        if(runCatching {WireContract.uuid(greenhouseId)}.isFailure) put("greenhouse","Pilih greenhouse.")
        if(runCatching {WireContract.uuid(seasonId)}.isFailure) put("season","Pilih musim penjualan.")
        if(runCatching {paidAt()}.isFailure) put("paid_at","Pilih tanggal dan jam lunas dari kalender dan jam.")
        if(!paid) put("paid","Konfirmasikan pembayaran sudah lunas sebelum menyimpan.")
        if(customer.length>200) put("customer","Nama pembeli maksimal200 karakter.")
        if(notes.length>2000) put("notes","Keterangan maksimal2.000 karakter.")
        if(items.size !in 1..30) put("items","Sertakan 1–30 baris penjualan.")
        if(items.map {it.id}.toSet().size!=items.size || items.map {Triple(it.groupId,it.gradeId,if(buyerType=="partner") it.buyerGrade else "")}.toSet().size!=items.size) put("items","Buah dan grade yang sama hanya boleh berulang dengan grade mitra yang berbeda.")
        if(items.any {it.buyerGrade.isNotBlank()} && buyerType!="partner") put("items","Grade A, B, C hanya untuk penjualan ke mitra.")
        if(items.any {it.buyerGrade.isNotBlank() && it.buyerGrade !in setOf("A","B","C")}) put("items","Grade mitra harus A, B, atau C.")
        items.forEachIndexed {index,line ->
            if(line.groupId.isBlank() || line.gradeId.isBlank()) put("items.$index.source","Pilih buah lagi.")
            else if(runCatching {WireContract.uuid(line.id);WireContract.uuid(line.groupId);WireContract.uuid(line.gradeId)}.isFailure) put("items.$index.source","Pilih buah yang sudah terkirim.")
            if(line.fruitCount.isNotBlank() && (line.fruitCount.trim().toIntOrNull()?.let {it<1} != false)) put("items.$index.fruit_count","Jumlah buah berupa bilangan bulat minimal 1, atau kosongkan.")
            if(runCatching {decimal(line.quantity,true)}.isFailure) put("items.$index.quantity","Isi bobot lebih dari nol, misalnya 12,5.")
            if(runCatching {decimal(line.price,false)}.isFailure) put("items.$index.price","Isi harga per kg. Tulis 0 bila memang gratis.")
        }
        if(items.size in 1..30 && runCatching {preview()}.isFailure && keys.none {it.endsWith("quantity") || it.endsWith("price")}) put("total","Total uang atau bobot melebihi rentang angka yang didukung.")
        if(reasonRequired && baseVersion!=null && reason.isBlank()) put("reason","Isi alasan koreksi.")
        if(reason.length>1000) put("reason","Catatan maksimal 1.000 karakter.")
        if(baseVersion==null && buyerType !in BUYER_TYPES) put("buyer_type","Pilih tipe pembeli.")
        else if(buyerType.isNotBlank() && buyerType !in BUYER_TYPES) put("buyer_type","Tipe pembeli tidak dikenal.")
        else if(hasCustomer()) {
            if(runCatching {WireContract.uuid(buyerId)}.isFailure) put("customer","Pilih pembeli terdaftar atau isi pembeli baru.")
            else if(customer.isBlank()) put("customer","Isi nama pembeli.")
        }
    }
    fun payload():JSONObject {
        require(validation(false).isEmpty()) {validation(false).values.joinToString("\n")}
        return JSONObject().put("season_id",seasonId).put("paid_at",paidAt()).put("payment_status","paid").put("customer_name",customer).put("notes",notes)
            .put("lines",JSONArray().also {rows -> items.forEach {rows.put(JSONObject().put("id",it.id).put("planting_group_id",it.groupId).put("grade_id",it.gradeId).put("quantity_kg",decimal(it.quantity,true).toPlainString())
                .put("fruit_count",it.fruitCount.trim().toIntOrNull() ?: JSONObject.NULL).put("price_per_kg",decimal(it.price,false).toPlainString()).apply {if(it.buyerGrade.isNotBlank()) put("buyer_grade",it.buyerGrade)})}})
            .apply {if(baseVersion!=null && reason.isNotBlank()) put("reason",reason)}
            .apply {
                if(hasCustomer()) put("customer_type",buyerType).put("customer",JSONObject().put("id",buyerId).put("type",buyerType).put("name",customer.trim())
                    .put("whatsapp",buyerWhatsapp.trim().ifBlank {null} ?: JSONObject.NULL))
                else if(buyerType.isNotBlank()) put("customer_type",buyerType).put("customer",JSONObject.NULL)
                // Explicit nulls: a correction to "Tidak dicatat" unlinks the stored buyer instead of keeping it.
                else put("customer_type",JSONObject.NULL).put("customer",JSONObject.NULL)
            }
    }
    fun localJson():JSONObject {
        val form=JSONObject().put("season_id",seasonId).put("date",date).put("time",time).put("paid",paid).put("customer_name",customer).put("notes",notes).put("reason",reason)
            .put("locked_season",lockedSeason).put("locked_sources",JSONArray(lockedSources.toList())).put("replace_validation_rejected",replaceValidationRejected)
            .put("buyer_type",buyerType).put("buyer_id",buyerId).put("buyer_whatsapp",buyerWhatsapp).put("buyer_existing",buyerExisting)
            .put("lines",JSONArray().also {rows -> items.forEach {rows.put(JSONObject().put("id",it.id).put("group_id",it.groupId).put("grade_id",it.gradeId).put("buyer_grade",it.buyerGrade).put("quantity",it.quantity).put("fruit_count",it.fruitCount).put("price",it.price))}})
        return JSONObject().put("season_id",seasonId).put("paid_at",runCatching {paidAt()}.getOrNull() ?: JSONObject.NULL).put("customer_name",customer).put("notes",notes).put("completeness","draft").put("_local_form",form)
    }
    companion object {
        fun decimal(text:String,positive:Boolean):BigDecimal {
            val value=text.trim().replace(',','.');require(!value.startsWith('-'));val parsed=WireContract.decimal(value,6);require(!positive || parsed.signum()>0)
            return parsed.setScale(6)
        }
        val BUYER_TYPES=setOf("consumer","retail","partner")
        /** Rebuilds a draft from a local form or a server body; [stock] upgrades lines saved by app versions that picked a harvest item. */
        fun from(id:String,gh:String,body:JSONObject,base:Long?,stock:SaleStock=SaleStock.EMPTY)=fromLocal(id,gh,body,base,stock)
        fun fromLocal(id:String,gh:String,body:JSONObject,base:Long?,stock:SaleStock=SaleStock.EMPTY):SaleDraft {
            val local=body.optJSONObject("_local_form");val raw=local ?: body
            val stamp=if(local!=null) raw.optString("date") to raw.optString("time") else ManualTime.decode(body.getString("paid_at"))
            val lines=parseLines(raw,local!=null,stock)
            val locked=local?.optJSONArray("locked_sources")?.let {a -> (0 until a.length()).map {a.getString(it)}.toSet()} ?: if(base!=null) lines.map {it.id}.toSet() else emptySet()
            return SaleDraft(id,gh,raw.optString("season_id"),stamp.first,stamp.second,if(local!=null) raw.optBoolean("paid") else body.optString("payment_status")=="paid",raw.optString("customer_name"),raw.optString("notes"),lines,raw.optString("reason"),base,locked,local?.optBoolean("locked_season") ?: (base!=null),local?.optBoolean("replace_validation_rejected")==true,
                buyerType=local?.optString("buyer_type") ?: body.optString("customer_type").takeIf {!body.isNull("customer_type")}.orEmpty(),
                buyerId=local?.optString("buyer_id") ?: body.optString("customer_id").takeIf {!body.isNull("customer_id")}.orEmpty(),
                buyerWhatsapp=local?.optString("buyer_whatsapp").orEmpty(),
                buyerExisting=local?.optBoolean("buyer_existing") ?: !body.isNull("customer_id"))
        }
        private fun parseLines(raw:JSONObject,local:Boolean,stock:SaleStock):List<SaleLine> {
            fun rows(key:String)=raw.optJSONArray(key)?.let {a -> (0 until a.length()).map {a.getJSONObject(it)}}
            fun text(row:JSONObject,key:String)=if(row.isNull(key)) "" else row.optString(key)
            rows("lines")?.let {list -> return list.map {row ->
                if(local) SaleLine(row.getString("id"),row.optString("group_id"),row.optString("grade_id"),row.optString("buyer_grade"),row.optString("quantity"),row.optString("fruit_count"),row.optString("price"))
                else SaleLine(row.getString("id"),row.optString("planting_group_id"),row.optString("grade_id"),text(row,"buyer_grade"),row.getString("quantity_kg"),text(row,"fruit_count"),row.getString("price_per_kg"))
            }}
            return (rows("items") ?: throw org.json.JSONException("lines")).map {row ->
                val source=stock.sourceOf(row.optString(if(local) "source_id" else "harvest_item_id"))
                SaleLine(row.getString("id"),source?.first.orEmpty(),source?.second.orEmpty(),if(local) row.optString("grade") else text(row,"buyer_grade"),
                    row.getString(if(local) "quantity" else "quantity_kg"),"",row.getString(if(local) "price" else "price_per_kg"))
            }
        }
    }
}
