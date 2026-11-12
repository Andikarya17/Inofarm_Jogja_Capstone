package com.inofarm.jogja.core.contracts

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime

object InvestmentContract {
    const val SCENARIO="investment_scenario"
    const val REPORT="investment_report"
    const val VERSION="annual-relative-rational-bisection-v1"
    val labels=linkedMapOf("NPV" to "Nilai kini bersih", "IRR" to "Tingkat pengembalian internal", "PP" to "Waktu pengembalian", "DPP" to "Waktu pengembalian terdiskonto")
    private val hash=Regex("[0-9a-f]{64}")
    fun integer(body:JSONObject,key:String,min:Int=0,max:Int=Int.MAX_VALUE):Int {
        val value=body.get(key);require(value is Int || value is Long)
        return (value as Number).toLong().also {require(it in min.toLong()..max.toLong())}.toInt()
    }
    private fun nullableDecimal(body:JSONObject,key:String,scale:Int,initial:Boolean=false) {
        require(body.has(key));if(body.isNull(key)) return
        require(body.get(key) is String);val value=WireContract.decimal(body.getString(key),scale)
        require(value.scale()==scale && (!initial || value.signum()>=0))
    }
    private fun source(body:JSONObject,kind:String,reference:String,basis:String) {
        require(body.has(kind) && (body.isNull(kind) || body.get(kind) is String && body.getString(kind) in setOf("actual","projection")))
        require(body.get(reference) is String && body.getString(reference).length<=200 && body.get(basis) is String && body.getString(basis).length<=2000)
    }
    fun scenario(body:JSONObject):JSONObject {
        WireContract.uuid(body.getString("id"));WireContract.uuid(body.getString("greenhouse_id"));integer(body,"version",1)
        require(body.getString("name").isNotBlank() && body.getString("record_state") in setOf("active","cancelled"))
        val n=integer(body,"horizon_years",1,100);InvestmentInputs.baseDate(body.getString("base_date"),n)
        nullableDecimal(body,"initial_outlay",2,true);nullableDecimal(body,"discount_rate",8)
        if(!body.isNull("discount_rate")) require(BigDecimal(body.getString("discount_rate"))>BigDecimal("-1"))
        require(body.has("payback_convention") && (body.isNull("payback_convention") || body.getString("payback_convention") in setOf("interpolation","end_of_year")))
        source(body.getJSONObject("initial_source"),"kind","reference","basis")
        val flows=body.getJSONArray("cash_flows");require(flows.length()==n)
        repeat(n) {i->val row=flows.getJSONObject(i);require(integer(row,"year",1,100)==i+1);nullableDecimal(row,"amount",2);source(row,"source_kind","source_reference","basis")}
        require(body.getString("source_provenance") in setOf("declared_complete","incomplete") && body.getString("completeness") in setOf("evaluation_ready","incomplete"))
        WireContract.uuid(body.getString("recorded_by"));OffsetDateTime.parse(body.getString("created_at"));OffsetDateTime.parse(body.getString("updated_at"))
        return body
    }
    fun report(body:JSONObject,accepted:Boolean):JSONObject {
        require(body.getString("formula_version")==VERSION) {"Versi rumus investasi belum didukung. Perbarui aplikasi."}
        WireContract.uuid(body.getString("greenhouse_id"));WireContract.uuid(body.getString("scenario_id"));integer(body,"scenario_version",1)
        require(body.has("season_id") && body.isNull("season_id") && body.getString("currency")=="IDR" && body.getString("timezone")=="Asia/Jakarta" && body.getString("report_state")=="provisional")
        require(hash.matches(body.getString("sources_hash")));integer(body,"source_count",2,101)
        val scope=body.getJSONObject("scope");require(scope.getJSONObject("greenhouse").getString("id")==body.getString("greenhouse_id"))
        val scenario=scope.getJSONObject("scenario");require(scenario.getString("id")==body.getString("scenario_id") && integer(scenario,"version",1)==body.getInt("scenario_version"))
        val n=integer(scenario,"horizon_years",1,100);InvestmentInputs.baseDate(scenario.getString("base_date"),n)
        nullableDecimal(scenario,"discount_rate",8)
        require(scenario.has("payback_convention") && (scenario.isNull("payback_convention") || scenario.getString("payback_convention") in setOf("interpolation","end_of_year")))
        require(body.getInt("source_count")==n+1)
        val summary=body.getJSONObject("source_summary");require(listOf("actual","projection","unknown").sumOf {integer(summary,it,0,101)}==body.getInt("source_count"))
        val metrics=body.getJSONObject("metrics");require(metrics.keys().asSequence().toSet()==labels.keys)
        var commonInputs:String?=null
        for(key in labels.keys) {
            val m=metrics.getJSONObject(key);val status=m.getString("status");val scale=if(key=="NPV") 2 else 8
            require(status in setOf("available","provisional","unavailable") || key in setOf("PP","DPP") && status=="not_recovered")
            require(integer(m,"display_scale")==scale && m.getString("rounding")=="HALF_UP" && m.getString("unit")==when(key) {"NPV"->"IDR";"IRR"->"percent";else->"year"})
            val reasons=m.getJSONArray("reasons");repeat(reasons.length()) {require(reasons.get(it) is String && reasons.getString(it).isNotBlank())}
            require(m.getString("formula").isNotBlank());m.getJSONArray("assumptions");m.getJSONObject("calculation_detail")
            val input=m.getJSONObject("inputs");require(integer(input,"horizon_years",1,100)==n);nullableDecimal(input,"initial_outlay",2,true);nullableDecimal(input,"discount_rate",8)
            require(input.get("discount_rate")==scenario.get("discount_rate") && input.get("payback_convention")==scenario.get("payback_convention"))
            val flows=input.getJSONArray("annual_cash_flows");require(flows.length()==n)
            repeat(n) {i->if(!flows.isNull(i)) {require(flows.get(i) is String);val cash=WireContract.decimal(flows.getString(i),2);require(cash.scale()==2)}}
            val inputHash=WireContract.hash(input);require(commonInputs==null || commonInputs==inputHash);commonInputs=inputHash
            if(status in setOf("unavailable","not_recovered")) require(m.isNull("value") && m.isNull("exact_fraction") && reasons.length()>0)
            else {
                require(m.get("value") is String && m.getString("value").length<=1024 && Regex("-?(?:0|[1-9][0-9]*)\\.[0-9]+") .matches(m.getString("value")) && BigDecimal(m.getString("value")).scale()==scale)
                if(key=="IRR") {
                    require(m.isNull("exact_fraction"));val detail=m.getJSONObject("calculation_detail");require(detail.get("converged")==true && detail.getString("method")=="conventional-horner-decimal80-bisection-v1")
                    integer(detail,"iterations",1,256);integer(detail,"max_iterations",256,256);integer(detail,"precision",80,80);integer(detail,"bracket_expansions",0,128)
                    fun rate(name:String):BigDecimal {require(detail.get(name) is String && Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?").matches(detail.getString(name)));return BigDecimal(detail.getString(name))}
                    val lower=rate("lower_rate_fraction");val upper=rate("upper_rate_fraction");val value=rate("rate_fraction")
                    require(detail.getString("tolerance_fraction")=="0.00000000000001" && lower>BigDecimal("-1") && value>=lower && value<=upper && upper-lower<=BigDecimal("0.00000000000001"))
                    require(value.movePointRight(2).setScale(8,RoundingMode.HALF_UP).toPlainString()==m.getString("value"))
                } else {
                    val f=m.getJSONObject("exact_fraction");require(Regex("-?(?:0|[1-9][0-9]*)").matches(f.getString("numerator")) && Regex("[1-9][0-9]*").matches(f.getString("denominator")))
                    require(BigDecimal(f.getString("numerator")).divide(BigDecimal(f.getString("denominator")),scale,RoundingMode.HALF_UP).toPlainString()==m.getString("value"))
                }
                if(status=="provisional") require(reasons.length()>0)
            }
        }
        require(body.getString("review_note").isNotBlank());body.getJSONArray("assumptions")
        if(accepted) {WireContract.uuid(body.getString("id"));WireContract.uuid(body.getString("recorded_by"));require(body.getString("kind")==REPORT && integer(body,"version")==1 && hash.matches(body.getString("snapshot_hash")));OffsetDateTime.parse(body.getString("created_at"));OffsetDateTime.parse(body.getString("generated_at"))}
        return body
    }
    fun source(body:JSONObject,scenario:String,version:Int):JSONObject {
        require(body.getString("scenario_id")==scenario && integer(body,"version",1)==version && body.get("known") is Boolean)
        val year=integer(body,"year",0,100);val kind=body.getString("kind");require(kind==if(year==0) "initial_outlay" else "annual_cash_flow")
        require(body.getString("source_key")==if(year==0) "initial_outlay" else "year:$year")
        val data=body.getJSONObject("data");nullableDecimal(data,"amount",2,year==0);require(body.getBoolean("known")==!data.isNull("amount"));source(data,"source_kind","source_reference","basis")
        if(year==0) LocalDate.parse(data.getString("at_date")) else {require(integer(data,"year",1,100)==year && data.getString("timing")=="end_of_annual_period");require(LocalDate.parse(data.getString("period_start")).isBefore(LocalDate.parse(data.getString("period_end"))))}
        return body
    }
}
