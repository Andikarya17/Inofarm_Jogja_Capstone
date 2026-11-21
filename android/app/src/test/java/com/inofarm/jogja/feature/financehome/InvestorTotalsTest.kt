package com.inofarm.jogja.feature.financehome

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class InvestorTotalsTest {
    private fun row(sequence: Int, tr: String, tc: String?) = JSONObject().put("sequence", sequence).put("TR", tr).put("TC", tc ?: JSONObject.NULL)

    @Test fun investor_totals_sum_sequences() {
        val totals = investorTotals(JSONArray(listOf(row(1, "50400000.00", "44044400.00"), row(2, "61800000.00", "44044400.00"))))
        assertEquals(BigDecimal("112200000.00"), totals.tr)
        assertEquals(BigDecimal("88088800.00"), totals.tc)
        assertEquals(BigDecimal("24111200.00"), totals.profit)
        assertEquals(BigDecimal("1.27"), totals.rc)
    }

    @Test fun incomplete_season_costs_leave_profit_unknown() {
        val totals = investorTotals(JSONArray(listOf(row(1, "50400000.00", "44044400.00"), row(2, "61800000.00", null))))
        assertNull(totals.tc)
        assertNull(totals.profit)
        assertNull(totals.rc)
    }
}
