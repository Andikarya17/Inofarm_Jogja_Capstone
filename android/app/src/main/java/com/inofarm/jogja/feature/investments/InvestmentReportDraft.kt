package com.inofarm.jogja.feature.investments

import com.inofarm.jogja.core.contracts.*
import org.json.JSONObject
import java.util.UUID

data class InvestmentReportDraft(val id:String=UUID.randomUUID().toString(),val greenhouseId:String,val scenarioId:String="",val note:String="",val previewText:String?=null,val previewFetchedAt:Long?=null,val replaceRejected:Boolean=false) {
    fun input():JSONObject {
        WireContract.uuid(greenhouseId);WireContract.uuid(scenarioId);require(note.isNotBlank() && note.length<=2000) {"Isi catatan laporan, maksimal 2.000 karakter."}
        return JSONObject().put("scenario_id",scenarioId).put("review_note",note.trim())
    }
    fun preview():JSONObject?=previewText?.let {InvestmentContract.report(JSONObject(it),false)}
    fun payload():JSONObject {
        val p=preview() ?: error("Lihat dulu laporannya sebelum menyimpan.");val input=input()
        require(p.getString("greenhouse_id")==greenhouseId && p.getString("scenario_id")==scenarioId && p.getString("review_note")==input.getString("review_note")) {"Isian berubah setelah Anda melihat laporan. Lihat dulu lagi."}
        return input.put("expected_scenario_version",p.getInt("scenario_version")).put("expected_sources_hash",p.getString("sources_hash")).put("expected_formula_version",p.getString("formula_version"))
    }
    fun localJson()=JSONObject().put("scenario_id",scenarioId).put("completeness","draft").put("_local_form",JSONObject().put("scenario_id",scenarioId).put("note",note).put("preview",previewText?.let(::JSONObject) ?: JSONObject.NULL).put("preview_fetched_at",previewFetchedAt ?: JSONObject.NULL).put("replace_rejected",replaceRejected))
    companion object {
        fun from(id:String,gh:String,body:JSONObject):InvestmentReportDraft {val f=body.getJSONObject("_local_form");return InvestmentReportDraft(id,gh,f.getString("scenario_id"),f.getString("note"),f.optJSONObject("preview")?.toString(),if(f.isNull("preview_fetched_at")) null else f.getLong("preview_fetched_at"),f.optBoolean("replace_rejected"))}
    }
}
