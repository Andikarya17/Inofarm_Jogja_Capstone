package com.inofarm.jogja

import com.inofarm.jogja.feature.sales.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class SaleStockTest {
    private val season = "20000000-0000-4000-8000-000000000001"
    private val group = "30000000-0000-4000-8000-000000000001"
    private val variety = "60000000-0000-4000-8000-000000000001"
    private val grade = "70000000-0000-4000-8000-000000000001"

    private fun harvest(id: String, at: String, vararg items: Triple<String, String, String>, state: String = "active") = JSONObject().put("id", id).put("planting_group_id", group)
        .put("occurred_at", at).put("version", 1).put("record_state", state)
        .put("items", JSONArray().also { rows -> items.forEach { (itemId, gradeId, kg) -> rows.put(JSONObject().put("id", itemId).put("grade_id", gradeId).put("grade_name", "Layak").put("weight_kg", kg).put("quality", "saleable")) } })
    private fun balance(itemId: String, harvestId: String, weight: String, available: String) = JSONObject().put("id", itemId).put("harvest_id", harvestId)
        .put("weight_kg", weight).put("available_weight_kg", available).put("harvest_version", 1).put("active", true)
    private fun stock(harvests: List<JSONObject>, balances: List<JSONObject> = emptyList()) = SaleStock(harvests, balances,
        listOf(JSONObject().put("id", group).put("season_id", season).put("variety_id", variety)),
        listOf(JSONObject().put("id", variety).put("name", "Golden")), listOf(JSONObject().put("id", grade).put("name", "Layak")))

    private val h1 = harvest("h1", "2026-11-17T08:00:00+07:00", Triple("i1", grade, "10"))
    private val h2 = harvest("h2", "2026-11-18T08:00:00+07:00", Triple("i2", grade, "20"))
    private val h3 = harvest("h3", "2026-11-19T08:00:00+07:00", Triple("i3", grade, "30"))

    @Test fun stockSumsHarvestsPerGroupAndGrade() {
        val option = stock(listOf(h3, h1, h2)).options(season).single()
        assertEquals(group, option.groupId);assertEquals(grade, option.gradeId);assertEquals("Golden", option.varietyName);assertEquals("saleable", option.quality)
        assertEquals(0, BigDecimal("60").compareTo(option.availableKg))
    }

    @Test fun stockSkipsCancelledHarvestsAndUsesBalances() {
        val s = stock(listOf(h1, h2, harvest("hx", "2026-11-16T08:00:00+07:00", Triple("ix", grade, "99"), state = "cancelled")),
            listOf(balance("i1", "h1", "10", "4"), balance("i2", "h2", "20", "20")))
        assertEquals(0, BigDecimal("24").compareTo(s.options(season).single().availableKg))
    }

    @Test fun fifoPreviewTakesOldestFirst() {
        val s = stock(listOf(h2, h1))
        val line = SaleLine(id = "l1", groupId = group, gradeId = grade, quantity = "25", price = "1000")
        val after = s.fifoPreview(season, listOf(line)).single()
        assertEquals(listOf("i1", "i2"), after.takes.map { it.harvestItemId })
        assertEquals(0, BigDecimal("10").compareTo(after.takes[0].kg));assertEquals(0, BigDecimal("15").compareTo(after.takes[1].kg))
        assertEquals(0, BigDecimal.ZERO.compareTo(after.shortfallKg));assertEquals(0, BigDecimal("5").compareTo(after.remainingKg))
    }

    @Test fun fifoPreviewSharesStockBetweenLinesAndReportsShortfall() {
        val s = stock(listOf(h1))
        val a = SaleLine(id = "a", groupId = group, gradeId = grade, quantity = "7");val b = SaleLine(id = "b", groupId = group, gradeId = grade, quantity = "5")
        val after = s.fifoPreview(season, listOf(a, b))
        assertEquals(0, BigDecimal("3").compareTo(after[1].takes.single().kg));assertEquals(0, BigDecimal("2").compareTo(after[1].shortfallKg))
    }

    @Test fun sourceOfMapsHarvestItemToGroupAndGrade() {
        val s = stock(listOf(h1))
        assertEquals(group to grade, s.sourceOf("i1"));assertNull(s.sourceOf("nope"))
    }

    @Test fun consumerPriceMatchesVarietyIgnoringCaseAndSpaces() {
        val prices = listOf(JSONObject().put("variety_name", "Golden  Melon").put("quality", "saleable").put("price_per_kg", "18000.00"),
            JSONObject().put("variety_name", "Golden Melon").put("quality", "defective").put("price_per_kg", "9000.00"))
        assertEquals("18000.00", SalePricing.consumerPrice(prices, "  golden melon ", "saleable"))
        assertEquals("9000.00", SalePricing.consumerPrice(prices, "GOLDEN MELON", "defective"))
        assertNull(SalePricing.consumerPrice(prices, "Inthanon", "saleable"));assertNull(SalePricing.consumerPrice(prices, "Golden Melon", null))
    }

    @Test fun lastRetailPriceUsesSameCustomerAndVariety() {
        fun sale(customer: String, paidAt: String, variety: String, price: String, kg: String, state: String = "active") = JSONObject().put("customer_id", customer).put("paid_at", paidAt).put("record_state", state)
            .put("lines", JSONArray().put(JSONObject().put("variety_name", variety).put("price_per_kg", price).put("quantity_kg", kg)))
        val sales = listOf(sale("c1", "2026-11-10T10:00:00+07:00", "Golden", "20000", "5"), sale("c1", "2026-11-20T10:00:00+07:00", "golden", "22000", "8"),
            sale("c2", "2026-11-22T10:00:00+07:00", "Golden", "30000", "1"), sale("c1", "2026-11-23T10:00:00+07:00", "Inthanon", "40000", "2"),
            sale("c1", "2026-11-24T10:00:00+07:00", "Golden", "99000", "3", "cancelled"))
        val hint = SalePricing.lastRetailPrice(sales, "c1", "Golden")!!
        assertEquals("22000", hint.pricePerKg);assertEquals("8", hint.kg);assertEquals("2026-11-20T10:00:00+07:00", hint.paidAt)
        assertNull(SalePricing.lastRetailPrice(sales, "c3", "Golden"))
    }
}
