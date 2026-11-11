package com.inofarm.jogja.feature.assets

import com.inofarm.jogja.core.contracts.DepreciationAmounts
import com.inofarm.jogja.core.contracts.WireContract
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class AssetKind(val wire:String) { ASSET("asset"), ALLOCATION("depreciation_allocation") }

/** Raw input is durable even when incomplete; only payload() emits wire data. */
data class AssetDraft(val id:String=UUID.randomUUID().toString(),val greenhouseId:String,val kind:AssetKind,
    val expenseId:String="",val assetId:String="",val seasonId:String="",val name:String="",val cost:String="",val residual:String="",
    val lifeYears:String="",val inServiceDate:String="",val usageYear:String="",val periodStart:String="",val periodEnd:String="",
    val usageReference:String="",val amount:String="",val basis:String="",val reason:String="",val baseVersion:Long?=null,
    val replaceValidationRejected:Boolean=false,val lockedParents:Boolean=false) {
    fun validation(reasonRequired:Boolean=true):Map<String,String> = buildMap {
        fun uuid(field:String,value:String) { if(runCatching {WireContract.uuid(value)}.isFailure) put(field,"Pilih identitas sumber yang sah.") }
        uuid("greenhouse",greenhouseId)
        if(basis.isBlank() || basis.length>2000) put("basis","Dasar pemakaian/alokasi wajib, maksimal 2.000 karakter.")
        if(reasonRequired && baseVersion!=null && reason.isBlank() || reason.length>2000) put("reason","Isi alasan koreksi, maksimal 2.000 karakter.")
        if(kind==AssetKind.ASSET) {
            uuid("expense",expenseId)
            if(name.isBlank() || name.length>150) put("name","Nama aset wajib, maksimal 150 karakter.")
            if(runCatching {money(cost);money(residual)}.isFailure) put("cost","Harga dan nilai sisa wajib, nonnegatif, maksimal 2 desimal; kosong bukan nol.")
            if(runCatching {DepreciationAmounts.year(money(cost).toPlainString(),money(residual).toPlainString(),year(lifeYears),1,date(inServiceDate))}.isFailure) put("depreciation","Isi umur positif, tanggal mulai pakai sah dan nilai sisa tidak melebihi harga.")
        } else {
            uuid("asset",assetId);uuid("season",seasonId);uuid("usage_reference",usageReference)
            if(runCatching {year(usageYear)}.isFailure) put("usage_year","Tahun pemakaian harus integer 1–1000.")
            if(runCatching {require(date(periodStart).isBefore(date(periodEnd)))}.isFailure) put("period","Periode YYYY-MM-DD harus berakhir setelah mulai (akhir eksklusif).")
            if(runCatching {require(money(amount).signum()>0)}.isFailure) put("amount","Alokasi IDR harus positif dan maksimal 2 desimal.")
        }
    }
    fun payload():JSONObject {
        require(validation(false).isEmpty()) {validation(false).values.joinToString("\n")}
        return (if(kind==AssetKind.ASSET) JSONObject().put("expense_id",expenseId).put("name",name).put("cost",money(cost).toPlainString())
            .put("residual",money(residual).toPlainString()).put("life_years",year(lifeYears)).put("in_service_date",date(inServiceDate).toString()).put("usage_basis",basis)
        else JSONObject().put("asset_id",assetId).put("season_id",seasonId).put("usage_reference",usageReference).put("usage_year",year(usageYear))
            .put("period_start",date(periodStart).toString()).put("period_end",date(periodEnd).toString()).put("amount",money(amount).toPlainString()).put("allocation_basis",basis))
            .apply {if(baseVersion!=null && reason.isNotBlank()) put("reason",reason)}
    }
    fun localJson():JSONObject {
        val form=JSONObject().put("expense_id",expenseId).put("asset_id",assetId).put("season_id",seasonId).put("name",name).put("cost",cost).put("residual",residual)
            .put("life_years",lifeYears).put("in_service_date",inServiceDate).put("usage_year",usageYear).put("period_start",periodStart).put("period_end",periodEnd)
            .put("usage_reference",usageReference).put("amount",amount).put("basis",basis).put("reason",reason).put("replace_validation_rejected",replaceValidationRejected).put("locked_parents",lockedParents)
        return JSONObject().put("_local_form",form).put("completeness","draft").put("expense_id",expenseId).put("season_id",seasonId).put("asset_id",assetId)
    }
    companion object {
        fun money(text:String):BigDecimal { val value=text.trim().replace(',','.');require(!value.startsWith('-'));return WireContract.decimal(value,2).setScale(2) }
        fun year(text:String):Int { require(Regex("[1-9][0-9]{0,3}").matches(text));return text.toInt().also {require(it in 1..1000)} }
        fun date(text:String):LocalDate { require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(text));return LocalDate.parse(text).also {require(it.year in 1..9999)} }
        fun from(id:String,gh:String,kind:AssetKind,body:JSONObject,base:Long?):AssetDraft {
            val local=body.optJSONObject("_local_form");val raw=local ?: body
            return AssetDraft(id,gh,kind,raw.optString("expense_id"),raw.optString("asset_id"),raw.optString("season_id"),raw.optString("name"),raw.optString("cost"),raw.optString("residual"),
                raw.optString("life_years"),raw.optString("in_service_date"),raw.optString("usage_year"),raw.optString("period_start"),raw.optString("period_end"),raw.optString("usage_reference"),raw.optString("amount"),
                if(local!=null) raw.optString("basis") else raw.optString(if(kind==AssetKind.ASSET) "usage_basis" else "allocation_basis"),raw.optString("reason"),base,local?.optBoolean("replace_validation_rejected")==true,local?.optBoolean("locked_parents") ?: (base!=null))
        }
    }
}
