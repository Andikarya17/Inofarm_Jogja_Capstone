package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.sync.OperationRoute
import com.inofarm.jogja.feature.investments.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class InvestmentContractTest {
    private fun fixture()=JSONObject(javaClass.getResource("/investment-report-public.json")!!.readText())
    @Test fun blankZeroNegativeAndExtremeMoneyHaveDifferentMeaning() {
        assertNull(InvestmentInputs.money(""));assertEquals("0.00",InvestmentInputs.money("0"))
        assertEquals("-0.01",InvestmentInputs.money("-0,01"));assertEquals("999999999999999999.99",InvestmentInputs.money("999999999999999999,99"))
        for(value in listOf("-1","1e3","1.000,00","0.001","1000000000000000000")) assertThrows(IllegalArgumentException::class.java) {InvestmentInputs.money(value,true)}
    }
    @Test fun percentConvertsExactlyWithoutSilentRoundingOrDefault() {
        assertNull(InvestmentInputs.rateFromPercent(" "));assertEquals("0.00000000",InvestmentInputs.rateFromPercent("0"));assertEquals("0.10500000",InvestmentInputs.rateFromPercent("10,5"))
        assertEquals("-0.99999999",InvestmentInputs.rateFromPercent("-99,999999"));assertEquals("10.5",InvestmentInputs.percentFromRate("0.10500000"))
        for(value in listOf("-100","-99.9999999","1e2","NaN")) assertThrows(IllegalArgumentException::class.java) {InvestmentInputs.rateFromPercent(value)}
    }
    @Test fun horizonEditsPreserveRawYearsOutsideSelectedHorizon() {
        val gh=fixture().getJSONObject("scenario").getString("greenhouse_id")
        val d=InvestmentScenarioDraft(greenhouseId=gh).withHorizon("2").let {it.copy(flows=it.flows.mapIndexed {i,f->f.copy(amount=if(i==0) "1," else "  -0,001 ")})}
        val reduced=d.withHorizon("1");assertEquals(2,reduced.flows.size);assertEquals(d.flows,reduced.withHorizon("2").flows)
        assertEquals(d.flows,d.withHorizon("invalid").flows);assertThrows(IllegalArgumentException::class.java) {InvestmentInputs.horizon("01")}
        assertThrows(IllegalArgumentException::class.java) {InvestmentInputs.baseDate("9999-01-01",1)}
    }
    @Test fun rawFormRoundTripKeepsWhitespaceIncompleteNumbersAndNulls() {
        val data=fixture().getJSONObject("scenario");val d=InvestmentScenarioDraft.from(data.getString("id"),data.getString("greenhouse_id"),data,1)
            .copy(initial="",discountPercent="",initialBasis="  α\nbaris  ",reason="Koreksi sumber").let {it.copy(flows=it.flows.mapIndexed {i,f->if(i==0) f.copy(amount="-0,01") else f.copy(amount="")})}
        assertEquals(d,InvestmentScenarioDraft.from(d.id,d.greenhouseId,JSONObject(d.localJson().toString()),1))
        val p=d.payload();assertTrue(p.isNull("initial_outlay"));assertTrue(p.isNull("discount_rate"));assertEquals("-0.01",p.getJSONArray("cash_flows").getJSONObject(0).getString("amount"));assertTrue(p.getJSONArray("cash_flows").getJSONObject(1).isNull("amount"))
    }
    @Test fun actualServerScenarioAndReportValidateAndTypedPoisonIsRejected() {
        val f=fixture();InvestmentContract.scenario(f.getJSONObject("scenario"));InvestmentContract.report(f.getJSONObject("preview"),false);InvestmentContract.report(f.getJSONObject("receipt").getJSONObject("data"),true)
        val sources=f.getJSONObject("full").getJSONArray("sources");repeat(sources.length()) {InvestmentContract.source(sources.getJSONObject(it),f.getJSONObject("scenario").getString("id"),1)}
        for(value in listOf("1",true,1.0)) {val p=JSONObject(f.getJSONObject("preview").toString()).put("scenario_version",value);assertThrows(IllegalArgumentException::class.java) {InvestmentContract.report(p,false)}}
        val p=JSONObject(f.getJSONObject("preview").toString());p.getJSONObject("metrics").getJSONObject("NPV").put("value",4132231.4);assertThrows(IllegalArgumentException::class.java) {InvestmentContract.report(p,false)}
        val bad=JSONObject(f.getJSONObject("preview").toString());bad.getJSONObject("scope").getJSONObject("scenario").put("horizon_years","2");assertThrows(IllegalArgumentException::class.java) {InvestmentContract.report(bad,false)}
        val wrong=JSONObject(f.getJSONObject("preview").toString());wrong.getJSONObject("metrics").getJSONObject("IRR").getJSONObject("calculation_detail").put("converged",false);assertThrows(IllegalArgumentException::class.java) {InvestmentContract.report(wrong,false)}
    }
    @Test fun previewReviewIsBoundToScopeNoteScenarioVersionAndFormula() {
        val p=fixture().getJSONObject("preview");val d=InvestmentReportDraft(greenhouseId=p.getString("greenhouse_id"),scenarioId=p.getString("scenario_id"),note=p.getString("review_note"),previewText=p.toString())
        assertEquals(p.getInt("scenario_version"),d.payload().getInt("expected_scenario_version"))
        assertThrows(IllegalArgumentException::class.java) {d.copy(note="Berubah").payload()};assertThrows(IllegalArgumentException::class.java) {d.copy(scenarioId="70000000-0000-0000-0000-000000000001").payload()}
    }
    @Test fun routesNeverAllowWorkerOrMutableInvestmentReports() {
        val id=fixture().getJSONObject("scenario").getString("id")
        assertEquals("investment-scenarios/$id",OperationRoute.forOperation(InvestmentContract.SCENARIO,"update",id,UserRole.MANAGER).path)
        assertEquals("investment-reports",OperationRoute.forOperation(InvestmentContract.REPORT,"create",id,UserRole.MANAGER).path)
        for(kind in listOf(InvestmentContract.SCENARIO,InvestmentContract.REPORT)) assertThrows(IllegalArgumentException::class.java) {OperationRoute.forOperation(kind,"create",id,UserRole.WORKER)}
        assertThrows(IllegalArgumentException::class.java) {OperationRoute.forOperation(InvestmentContract.REPORT,"update",id,UserRole.MANAGER)}
    }
}
