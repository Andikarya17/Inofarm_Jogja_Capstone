package com.inofarm.jogja.core.contracts

import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.time.OffsetDateTime

object SeasonReportContract {
    const val KIND="season_report"
    const val VERSION="season-recorded-cash-rational-half-up-v1"
    val labels=linkedMapOf("FC" to "Biaya tetap teralokasi", "VC" to "Biaya variabel teralokasi", "TC" to "Total biaya tercatat",
        "TR" to "Penerimaan lunas", "Q" to "Berat terjual", "L" to "Surplus / pendapatan usaha", "RC" to "R/C", "BC" to "B/C sederhana",
        "P" to "Harga rata-rata tertimbang", "BEP_Q" to "BEP kuantitas pada biaya tercatat", "BEP_PRICE" to "BEP harga")
    private val decimal=Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")
    private val hash=Regex("[0-9a-f]{64}")
    private fun integer(body:JSONObject,key:String):Int {
        val value=body.get(key);require(value is Int || value is Long)
        val number=(value as Number).toLong();require(number in 0..Int.MAX_VALUE.toLong());return number.toInt()
    }
    fun validate(body:JSONObject,accepted:Boolean):JSONObject {
        require(body.getString("formula_version")==VERSION) {"Versi rumus belum didukung aplikasi ini."}
        WireContract.uuid(body.getString("greenhouse_id"));WireContract.uuid(body.getString("season_id"))
        require(body.getString("currency")=="IDR" && body.getString("timezone")=="Asia/Jakarta")
        require(body.getString("report_state") in setOf("provisional","reviewed_final"))
        require(hash.matches(body.getString("sources_hash")) && integer(body,"source_count") in 0..10000)
        val coverage=body.getJSONObject("coverage")
        for(key in listOf("costs","sales")) require(coverage.getString(key) in setOf("unknown","partial","complete"))
        if(!body.isNull("known_pending_count")) require(integer(body,"known_pending_count")>=0)
        require(body.get("review_note") is String && body.getString("review_note").isNotBlank())
        val assumptions=body.getJSONArray("assumptions");repeat(assumptions.length()) {require(assumptions.get(it) is String)}
        val scope=body.getJSONObject("scope")
        require(scope.getJSONObject("greenhouse").getString("id")==body.getString("greenhouse_id") && scope.getJSONObject("season").getString("id")==body.getString("season_id"))
        val metrics=body.getJSONObject("metrics");require(metrics.keys().asSequence().toSet()==labels.keys)
        labels.keys.forEach {key ->
            val metric=metrics.getJSONObject(key);val status=metric.getString("status")
            require(status in setOf("available","provisional","unavailable"))
            val scale=if(key in setOf("RC","BC")) 8 else if(key in setOf("Q","P","BEP_Q","BEP_PRICE")) 6 else 2
            require((metric.get("display_scale") is Int || metric.get("display_scale") is Long) && metric.getInt("display_scale")==scale && metric.getString("rounding")=="HALF_UP")
            require(metric.getString("unit")==when(key) {"Q","BEP_Q"->"kg";"P","BEP_PRICE"->"IDR/kg";"RC","BC"->"ratio";else->"IDR"})
            require(metric.getString("formula").isNotBlank());val reasons=metric.getJSONArray("reasons")
            repeat(reasons.length()) {require(reasons.get(it) is String && reasons.getString(it).isNotBlank())}
            metric.getJSONObject("inputs");metric.getJSONArray("assumptions")
            if(status=="unavailable") require(metric.isNull("value") && metric.isNull("exact_fraction") && reasons.length()>0)
            else {
                require(metric.get("value") is String && decimal.matches(metric.getString("value")) && BigDecimal(metric.getString("value")).scale()==scale)
                val fraction=metric.getJSONObject("exact_fraction")
                require(Regex("-?(?:0|[1-9][0-9]*)").matches(fraction.getString("numerator")) && Regex("[1-9][0-9]*").matches(fraction.getString("denominator")))
                require(BigInteger(fraction.getString("denominator")).signum()>0)
                if(status=="provisional") require(reasons.length()>0)
            }
        }
        if(accepted) {
            WireContract.uuid(body.getString("id"));WireContract.uuid(body.getString("recorded_by"))
            require(body.getString("kind")==KIND && integer(body,"version")==1 && hash.matches(body.getString("snapshot_hash")))
            OffsetDateTime.parse(body.getString("created_at"));OffsetDateTime.parse(body.getString("generated_at"))
        }
        return body
    }
}
