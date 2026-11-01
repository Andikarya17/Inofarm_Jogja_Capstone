package com.inofarm.jogja.feature.reports

import com.inofarm.jogja.core.contracts.SeasonReportContract
import com.inofarm.jogja.core.contracts.WireContract
import org.json.JSONObject
import java.util.UUID

data class SeasonReportDraft(val id:String=UUID.randomUUID().toString(),val greenhouseId:String,val seasonId:String="",val costs:String="",val sales:String="",
    val pending:String="",val requestedState:String="",val note:String="",val previewText:String?=null,val previewFetchedAt:Long?=null,val replaceRejected:Boolean=false) {
    fun validation():Map<String,String> = buildMap {
        if(runCatching {WireContract.uuid(greenhouseId);WireContract.uuid(seasonId)}.isFailure) put("scope","Pilih greenhouse dan musim yang sesuai.")
        if(costs !in coverage || sales !in coverage) put("coverage","Nyatakan kelengkapan biaya dan penjualan secara eksplisit.")
        if(requestedState !in setOf("provisional","reviewed_final")) put("state","Pilih laporan sementara atau akhir setelah ditinjau.")
        if(pending.isNotBlank() && ( !Regex("0|[1-9][0-9]{0,9}").matches(pending) || pending.toIntOrNull()==null)) put("pending","Jumlah antrean harus integer nonnegatif; kosong berarti belum diketahui.")
        if(requestedState=="reviewed_final" && (costs!="complete" || sales!="complete" || pending!="0")) put("final","Laporan akhir memerlukan biaya dan penjualan ditinjau lengkap serta antrean dinyatakan nol.")
        if(note.isBlank() || note.length>2000) put("note","Catatan tinjauan wajib, maksimal 2.000 karakter.")
    }
    fun input():JSONObject {
        require(validation().isEmpty()) {validation().values.joinToString("\n")}
        return JSONObject().put("season_id",seasonId).put("coverage",JSONObject().put("costs",costs).put("sales",sales))
            .put("known_pending_count",if(pending.isBlank()) JSONObject.NULL else pending.toInt()).put("requested_state",requestedState).put("review_note",note.trim())
    }
    fun preview():JSONObject?=previewText?.let {SeasonReportContract.validate(JSONObject(it),false)}
    fun payload():JSONObject {
        val preview=preview() ?: error("Muat dan tinjau pratinjau server sebelum mengantrekan laporan.")
        require(preview.getString("greenhouse_id")==greenhouseId && preview.getString("season_id")==seasonId)
        val reviewed=input()
        require(preview.getJSONObject("coverage").getString("costs")==costs && preview.getJSONObject("coverage").getString("sales")==sales &&
            preview.getString("report_state")==requestedState && preview.getString("review_note")==reviewed.getString("review_note") &&
            preview.get("known_pending_count")==reviewed.get("known_pending_count")) {"Isian berubah setelah pratinjau. Muat dan tinjau kembali."}
        return input().put("expected_sources_hash",preview.getString("sources_hash")).put("expected_formula_version",preview.getString("formula_version"))
    }
    fun localJson():JSONObject=JSONObject().put("season_id",seasonId).put("completeness",if(previewText==null) "draft" else "ready")
        .put("_local_form",JSONObject().put("season_id",seasonId).put("costs",costs).put("sales",sales).put("pending",pending).put("requested_state",requestedState)
            .put("note",note).put("preview",previewText?.let(::JSONObject) ?: JSONObject.NULL).put("preview_fetched_at",previewFetchedAt ?: JSONObject.NULL).put("replace_rejected",replaceRejected))
    companion object {
        val coverage=setOf("unknown","partial","complete")
        fun from(id:String,gh:String,body:JSONObject):SeasonReportDraft {
            val raw=body.getJSONObject("_local_form")
            return SeasonReportDraft(id,gh,raw.optString("season_id"),raw.optString("costs"),raw.optString("sales"),raw.optString("pending"),raw.optString("requested_state"),raw.optString("note"),
                raw.optJSONObject("preview")?.toString(),if(raw.isNull("preview_fetched_at")) null else raw.getLong("preview_fetched_at"),raw.optBoolean("replace_rejected"))
        }
    }
}
