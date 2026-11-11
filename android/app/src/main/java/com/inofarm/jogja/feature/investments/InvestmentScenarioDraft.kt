package com.inofarm.jogja.feature.investments

import com.inofarm.jogja.core.contracts.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class InvestmentFlowDraft(val amount:String="",val kind:String="",val reference:String="",val basis:String="") {
    fun raw()=JSONObject().put("amount",amount).put("kind",kind).put("reference",reference).put("basis",basis)
}
data class InvestmentScenarioDraft(val id:String=UUID.randomUUID().toString(),val greenhouseId:String,val name:String="",val baseDate:String="",val horizon:String="",
    val initial:String="",val initialKind:String="",val initialReference:String="",val initialBasis:String="",val discountPercent:String="",val convention:String="",
    val flows:List<InvestmentFlowDraft> = emptyList(),val notes:String="",val reason:String="",val baseVersion:Long?=null,val replaceRejected:Boolean=false) {
    fun withHorizon(value:String):InvestmentScenarioDraft {
        val count=runCatching {InvestmentInputs.horizon(value)}.getOrNull() ?: 0
        return copy(horizon=value,flows=(0 until maxOf(count,flows.size)).map {flows.getOrNull(it) ?: InvestmentFlowDraft()})
    }
    fun validation(reasonRequired:Boolean=true):Map<String,String> = buildMap {
        if(runCatching {WireContract.uuid(greenhouseId)}.isFailure) put("greenhouse","Pilih greenhouse.")
        if(name.isBlank() || name.length>120) put("name","Nama wajib, maksimal 120 karakter.")
        val count=runCatching {InvestmentInputs.horizon(horizon)}.getOrNull()
        if(count==null) put("horizon","Jumlah tahun harus integer 1–100; batas teknis aplikasi.")
        if(runCatching {InvestmentInputs.baseDate(baseDate,count ?: 1)}.isFailure) put("base_date","Isi tanggal dasar YYYY-MM-DD dan periode dalam tahun 1–9999.")
        if(runCatching {InvestmentInputs.money(initial,true)}.isFailure) put("initial","Investasi awal nonnegatif, maksimal 2 desimal. Kosong berarti belum diketahui.")
        if(runCatching {InvestmentInputs.rateFromPercent(discountPercent)}.isFailure) put("discount","Diskonto harus lebih dari −100%; jangan memakai pemisah ribuan. Maksimal presisi setara 8 desimal fraksi.")
        if(convention !in setOf("","interpolation","end_of_year")) put("convention","Pilih konvensi yang tersedia.")
        if(initialKind !in setOf("","actual","projection") || initialReference.length>200 || initialBasis.length>2000) put("initial_source","Periksa jenis, referensi (200) dan dasar (2.000 karakter) investasi awal.")
        if(count!=null) {
            if(flows.size<count) put("flows","Lengkapi baris tahun 1 sampai horizon.")
            flows.take(count).forEachIndexed {i,f->if(runCatching {InvestmentInputs.money(f.amount)}.isFailure || f.kind !in setOf("","actual","projection") || f.reference.length>200 || f.basis.length>2000) put("flow-${i+1}","Periksa nominal bertanda, sumber, referensi dan dasar tahun ${i+1}.")}
        }
        if(notes.length>2000) put("notes","Catatan maksimal 2.000 karakter.")
        if(reason.length>2000 || reasonRequired && baseVersion!=null && reason.isBlank()) put("reason","Isi alasan koreksi, maksimal 2.000 karakter.")
    }
    fun payload():JSONObject {
        require(validation(false).isEmpty()) {validation(false).values.joinToString("\n")}
        return JSONObject().put("greenhouse_id",greenhouseId).put("name",name.trim()).put("base_date",baseDate).put("horizon_years",InvestmentInputs.horizon(horizon))
            .put("initial_outlay",InvestmentInputs.money(initial,true) ?: JSONObject.NULL).put("initial_source",JSONObject().put("kind",initialKind.ifBlank {null} ?: JSONObject.NULL).put("reference",initialReference.trim()).put("basis",initialBasis.trim()))
            .put("discount_rate",InvestmentInputs.rateFromPercent(discountPercent) ?: JSONObject.NULL).put("payback_convention",convention.ifBlank {null} ?: JSONObject.NULL)
            .put("cash_flows",JSONArray().also {array->flows.take(InvestmentInputs.horizon(horizon)).forEachIndexed {i,f->array.put(JSONObject().put("year",i+1).put("amount",InvestmentInputs.money(f.amount) ?: JSONObject.NULL).put("source_kind",f.kind.ifBlank {null} ?: JSONObject.NULL).put("source_reference",f.reference.trim()).put("basis",f.basis.trim()))}})
            .put("notes",notes).apply {if(baseVersion!=null && reason.isNotBlank()) put("reason",reason.trim())}
    }
    fun localJson()=JSONObject().put("completeness","draft").put("_local_form",JSONObject().put("name",name).put("base_date",baseDate).put("horizon",horizon).put("initial",initial)
        .put("initial_kind",initialKind).put("initial_reference",initialReference).put("initial_basis",initialBasis).put("discount_percent",discountPercent).put("convention",convention)
        .put("flows",JSONArray().also {a->flows.forEach {a.put(it.raw())}}).put("notes",notes).put("reason",reason).put("replace_rejected",replaceRejected))
    companion object {
        fun from(id:String,gh:String,body:JSONObject,version:Long?):InvestmentScenarioDraft {
            val raw=body.optJSONObject("_local_form")
            fun nullable(body:JSONObject,key:String)=if(body.isNull(key)) "" else body.getString(key)
            if(raw!=null) return InvestmentScenarioDraft(id,gh,raw.optString("name"),raw.optString("base_date"),raw.optString("horizon"),raw.optString("initial"),raw.optString("initial_kind"),raw.optString("initial_reference"),raw.optString("initial_basis"),raw.optString("discount_percent"),raw.optString("convention"),raw.getJSONArray("flows").let {a->(0 until a.length()).map {a.getJSONObject(it).let {f->InvestmentFlowDraft(f.getString("amount"),f.getString("kind"),f.getString("reference"),f.getString("basis"))}}},raw.optString("notes"),raw.optString("reason"),version,raw.optBoolean("replace_rejected"))
            InvestmentContract.scenario(body);val source=body.getJSONObject("initial_source")
            return InvestmentScenarioDraft(id,gh,body.getString("name"),body.getString("base_date"),body.getInt("horizon_years").toString(),nullable(body,"initial_outlay"),nullable(source,"kind"),source.getString("reference"),source.getString("basis"),if(body.isNull("discount_rate")) "" else InvestmentInputs.percentFromRate(body.getString("discount_rate")),nullable(body,"payback_convention"),body.getJSONArray("cash_flows").let {a->(0 until a.length()).map {a.getJSONObject(it).let {f->InvestmentFlowDraft(nullable(f,"amount"),nullable(f,"source_kind"),f.getString("source_reference"),f.getString("basis"))}}},body.getString("notes"),baseVersion=version)
        }
    }
}
