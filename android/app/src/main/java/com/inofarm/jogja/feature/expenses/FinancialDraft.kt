package com.inofarm.jogja.feature.expenses

import com.inofarm.jogja.core.contracts.RecordKind
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.cultivation.ManualTime
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Raw text remains local; only payload() can produce a validated financial operation. */
data class FinancialDraft(val id:String=UUID.randomUUID().toString(),val kind:RecordKind,val greenhouseId:String,
    val amount:String="",val categoryId:String="",val paymentKind:String="",val notes:String="",
    val date:String=if(kind==RecordKind.EXPENSE) LocalDate.now(ManualTime.WIB).toString() else "",val time:String=if(kind==RecordKind.EXPENSE) LocalTime.now(ManualTime.WIB).format(DateTimeFormatter.ofPattern("HH:mm")) else "",
    val expenseId:String="",val seasonId:String="",val costClass:String="",val basis:String="",val reason:String="",val baseVersion:Long?=null,
    val replaceValidationRejected:Boolean=false,val lockedParents:Boolean=false) {
    init {require(kind.financial)}
    fun paidAt()=ManualTime.encode(date,time)
    fun validation():Map<String,String> = buildMap {
        if(runCatching {WireContract.uuid(greenhouseId)}.isFailure) put("greenhouse","Pilih greenhouse.")
        if(runCatching {money(amount)}.isFailure) put("amount","Isi nominal IDR tidak negatif, maksimal 2 angka di belakang koma; kosong bukan nol.")
        if(kind==RecordKind.EXPENSE) {
            if(runCatching {WireContract.uuid(categoryId)}.isFailure) put("category","Pilih kategori pengeluaran.")
            if(paymentKind !in setOf("operating","asset_acquisition")) put("payment_kind","Pilih pembayaran operasi atau pembelian aset.")
            if(runCatching {paidAt()}.isFailure) put("paid_at","Isi tanggal YYYY-MM-DD dan jam HH:mm WIB yang sah; detik opsional.")
            if(notes.length>2000) put("notes","Keterangan maksimal 2.000 karakter.")
        } else {
            if(runCatching {WireContract.uuid(expenseId)}.isFailure) put("expense","Pilih pembayaran operasi yang telah diterima server.")
            if(runCatching {WireContract.uuid(seasonId)}.isFailure) put("season","Pilih musim tujuan alokasi.")
            if(costClass !in setOf("FC","VC")) put("cost_class","Pilih biaya tetap (FC) atau variabel (VC).")
            if(basis.isBlank() || basis.length>2000) put("basis","Dasar alokasi wajib, maksimal 2.000 karakter.")
        }
        if(baseVersion!=null && reason.isBlank()) put("reason","Koreksi memerlukan alasan.")
        if(reason.length>1000) put("reason","Alasan maksimal 1.000 karakter.")
    }
    fun payload():JSONObject {
        require(validation().isEmpty()) {validation().values.joinToString("\n")}
        val result=if(kind==RecordKind.EXPENSE) JSONObject().put("greenhouse_id",greenhouseId).put("category_id",categoryId)
            .put("paid_at",paidAt()).put("payment_kind",paymentKind).put("notes",notes)
        else JSONObject().put("expense_id",expenseId).put("season_id",seasonId).put("cost_class",costClass).put("allocation_basis",basis)
        return result.put("amount",money(amount).toPlainString()).apply {if(baseVersion!=null) put("reason",reason)}
    }
    fun localJson():JSONObject {
        val form=JSONObject().put("amount",amount).put("category_id",categoryId).put("payment_kind",paymentKind).put("notes",notes)
            .put("expense_id",expenseId).put("season_id",seasonId).put("cost_class",costClass).put("allocation_basis",basis).put("reason",reason)
            .put("replace_validation_rejected",replaceValidationRejected).put("locked_parents",lockedParents)
        val body=JSONObject().put("amount",amount).put("notes",notes).put("completeness","draft").put("_local_form",form)
        if(kind==RecordKind.EXPENSE) {
            form.put("date",date).put("time",time)
            body.put("greenhouse_id",greenhouseId).put("paid_at",runCatching {paidAt()}.getOrNull() ?: JSONObject.NULL)
        } else body.put("expense_id",expenseId).put("season_id",seasonId).put("allocation_basis",basis)
        return body
    }
    companion object {
        fun money(text:String):BigDecimal {
            val value=text.trim().replace(',','.')
            require(!value.startsWith('-')) {"Nominal tidak boleh negatif."}
            return WireContract.decimal(value,2).setScale(2)
        }
        fun from(id:String,kind:RecordKind,gh:String,body:JSONObject,base:Long?):FinancialDraft {
            val local=body.optJSONObject("_local_form");val raw=local ?: body
            val stamp=if(kind==RecordKind.EXPENSE) {
                if(local==null) ManualTime.decode(body.getString("paid_at")) else local.getString("date") to local.getString("time")
            } else "" to ""
            return FinancialDraft(id,kind,gh,raw.optString("amount"),raw.optString("category_id"),raw.optString("payment_kind"),raw.optString("notes"),stamp.first,stamp.second,
                raw.optString("expense_id"),raw.optString("season_id"),raw.optString("cost_class"),raw.optString("allocation_basis"),raw.optString("reason"),base,
                local?.optBoolean("replace_validation_rejected")==true,local?.optBoolean("locked_parents") ?: (base!=null))
        }
    }
}
