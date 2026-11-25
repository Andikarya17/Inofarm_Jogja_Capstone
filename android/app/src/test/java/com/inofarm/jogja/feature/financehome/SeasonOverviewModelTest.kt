package com.inofarm.jogja.feature.financehome

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SeasonOverviewModelTest {
    private fun slot(amount: String, kg: String = "0.000000", count: Int = 0) = JSONObject().put("amount", amount).put("kg", kg).put("count", count)
    private fun byType(consumer: String = "0.00", retail: String = "0.00", partner: String = "0.00", unspecified: String = "0.00") = JSONObject()
        .put("consumer", slot(consumer)).put("retail", slot(retail)).put("partner", slot(partner)).put("unspecified", slot(unspecified))
    private fun money(value: String?): Any = value ?: JSONObject.NULL
    private fun sequence(n: Int, status: String, fc: String?, vc: String?, tc: String?, sales: String) = JSONObject().put("sequence", n).put("status", status)
        .put("greenhouse_count", 4).put("fc", money(fc)).put("vc", money(vc)).put("tc", money(tc)).put("sales_amount", sales).put("sales_kg", "10.000000")
    private fun line(name: String, costClass: String, amount: String?) = JSONObject().put("name", name).put("cost_class", costClass)
        .put("amount", money(amount)).put("is_required", true)
    private fun week(key: String, label: String, consumer: String, retail: String, partner: String, unspecified: String = "0.00") = JSONObject()
        .put("key", key).put("label", label).put("start", key).put("amount", "0.00").put("kg", "0.000000")
        .put("by_type", byType(consumer, retail, partner, unspecified))

    private fun body(lines: List<JSONObject>, perGh: Triple<String?, String?, String?>, weeks: List<JSONObject> = emptyList(),
                     sequences: List<JSONObject> = listOf(sequence(3, "running", perGh.first, perGh.second, perGh.third, "41200000.00"))) = JSONObject()
        .put("sequences", JSONArray(sequences))
        .put("selected", JSONObject().put("sequence", 3)
            .put("date_range", JSONObject().put("from", "2026-09-08").put("until", "2026-11-25").put("running", true))
            .put("cost_sheet", JSONObject().put("id", "c0000000-0000-4000-8000-000000000001").put("version", 2).put("lines", JSONArray(lines))
                .put("per_greenhouse", JSONObject().put("fc", money(perGh.first)).put("vc", money(perGh.second)).put("tc", money(perGh.third)))
                .put("farm", JSONObject().put("fc", money(perGh.first)).put("vc", money(perGh.second)).put("tc", money(perGh.third))))
            .put("sales", slot("41200000.00", "1573.000000", 47).put("by_type", byType(consumer = "9500000.00", partner = "31700000.00")))
            .put("by_greenhouse", JSONArray(listOf(JSONObject().put("greenhouse_id", "g1").put("name", "GH A").put("amount", "1000.00").put("kg", "1.000000").put("count", 1))))
            .put("weekly", JSONArray(weeks)))

    @Test fun parsesOverviewWithNullCosts() {
        val overview = SeasonOverview.from(body(listOf(line("Sewa tanah", "FC", "275000.00"), line("Benih", "VC", null)), Triple("275000.00", null, null)))
        val selected = overview.selected!!
        assertEquals(3, selected.sequence)
        assertTrue(selected.running)
        assertEquals(LocalDate.parse("2026-09-08"), selected.from)
        assertNull(selected.costs.perGreenhouse.tc)
        assertNull(selected.costs.lines[1].amount)
        assertEquals("Belum diisi", moneyOrBlank(selected.costs.perGreenhouse.tc))
        assertEquals("Belum diisi", moneyOrBlank(selected.costs.lines[1].amount))
        assertEquals("Rp275.000", moneyOrBlank(selected.costs.lines[0].amount))
        assertNull(overview.sequences.single().tc)
        assertEquals(BigDecimal("31700000.00"), selected.sales.partner.amount)
        assertEquals(47, selected.sales.total.count)
        assertEquals("8 Sep 2026 sampai sekarang", selected.dateLabel())
    }

    @Test fun selectedMayBeMissing() {
        val overview = SeasonOverview.from(JSONObject().put("sequences", JSONArray()).put("selected", JSONObject.NULL))
        assertNull(overview.selected)
        assertTrue(overview.compareBars().isEmpty())
    }

    @Test fun weeklyBarsStackFourTypes() {
        val overview = SeasonOverview.from(body(emptyList(), Triple(null, null, null),
            listOf(week("2026-11-17", "17 Nov", "100.00", "200.00", "300.00", "50.00"), week("2026-11-24", "24 Nov", "0.00", "0.00", "0.00"))))
        val bars = overview.weeklyBars()
        assertEquals(2, bars.size)
        val first = bars[0]
        assertEquals("17 Nov", first.label)
        assertEquals(BigDecimal("300.00"), first.partner)
        assertEquals(BigDecimal("200.00"), first.retail)
        assertEquals(BigDecimal("100.00"), first.consumer)
        assertEquals(BigDecimal("50.00"), first.unspecified)
        assertEquals(BigDecimal("650.00"), first.total())
        // Heights stack in the order mitra, retail, perorangan, tanpa tipe and add up to the tallest bar.
        val heights = stackHeights(bars, 100f)
        assertEquals(100f, heights[0].sum(), 0.01f)
        assertEquals(300f / 650f * 100f, heights[0][0], 0.01f)
        assertEquals(listOf(0f, 0f, 0f, 0f), heights[1])
    }

    @Test fun compareBarsScaleToLargestValue() {
        val overview = SeasonOverview.from(body(emptyList(), Triple("100.00", "100.00", "200.00"), sequences = listOf(
            sequence(3, "running", "100.00", "100.00", "200.00", "100.00"),
            sequence(2, "closed", "100.00", "300.00", "400.00", "800.00"),
            sequence(1, "closed", null, "50.00", null, "200.00"))))
        val bars = overview.compareBars()
        assertEquals(listOf("Musim 1", "Musim 2", "Musim 3"), bars.map { it.name })
        assertEquals(listOf(false, false, true), bars.map { it.selected })
        // Musim 2 sales (800) is the largest value, so it is the full height.
        assertEquals(1f, bars[1].sales, 0.0001f)
        assertEquals(100f / 800f, bars[1].fc, 0.0001f)
        assertEquals(300f / 800f, bars[1].vc, 0.0001f)
        // A blank cost leaves that bar empty rather than drawing zero as a value.
        assertEquals(0f, bars[0].fc, 0f)
        assertNull(bars[0].tc)
        assertEquals(BigDecimal("200.00"), bars[2].tc)
    }

    @Test fun costShareUsesPerGreenhouseTc() {
        val overview = SeasonOverview.from(body(listOf(line("Sewa tanah", "FC", "250.00"), line("Benih", "VC", "750.00")), Triple("250.00", "750.00", "1000.00")))
        val costs = overview.selected!!.costs
        assertEquals(0.25f, costs.costShare(costs.lines[0]), 0.0001f)
        assertEquals(0.75f, costs.costShare(costs.lines[1]), 0.0001f)
        val blank = SeasonOverview.from(body(listOf(line("Sewa tanah", "FC", "250.00"), line("Benih", "VC", null)), Triple("250.00", null, null))).selected!!.costs
        assertEquals(0f, blank.costShare(blank.lines[1]), 0f)
    }

    @Test fun noRatioFieldsInModel() {
        val forbidden = setOf("rc", "bc", "bep", "bepkg", "bepprice", "profit", "tr", "laba")
        for (type in listOf(SeasonOverview::class.java, SeasonTotals::class.java, SeasonDetail::class.java, SeasonCosts::class.java,
            CostTriple::class.java, WeekBar::class.java, CompareBar::class.java, SalesSlot::class.java, SalesByType::class.java)) {
            val names = type.declaredFields.map { it.name.lowercase().replace("_", "") }
            assertFalse("${type.simpleName} has $names", names.any { it in forbidden })
        }
    }

    private fun sale(id: String, paidAt: String, type: Any, name: String, gh: String, amount: String, kg: String, variety: String = "Golden",
                     state: String = "active") = JSONObject().put("id", id).put("paid_at", paidAt).put("customer_type", type).put("customer_name", name)
        .put("greenhouse_id", gh).put("total_amount", amount).put("total_weight_kg", kg).put("record_state", state).put("payment_status", "paid")
        .put("lines", JSONArray(listOf(JSONObject().put("variety_name", variety).put("quantity_kg", kg))))

    @Test fun seasonSalesGroupByWibDayAndFilter() {
        val names = mapOf("g1" to "GH A", "g2" to "GH B")
        val rows = SeasonSaleRow.list(listOf(
            sale("s1", "2026-11-24T02:00:00Z", "partner", "Mitra 1", "g1", "2940000.00", "120.000000"),
            sale("s2", "2026-11-23T18:00:00Z", "consumer", "Sari", "g2", "102000.00", "3.000000"),
            sale("s3", "2026-11-17T03:00:00Z", JSONObject.NULL, "", "g1", "85000.00", "2.500000", "Inthanon"),
            sale("s4", "2026-11-17T03:00:00Z", "retail", "Toko", "g1", "1.00", "1.000000", state = "cancelled")), names)
        assertEquals(listOf("s1", "s2", "s3"), rows.map { it.id })
        // 23 Nov 18.00 UTC is already 24 Nov in WIB.
        val days = groupByDay(rows)
        assertEquals(listOf(LocalDate.parse("2026-11-24"), LocalDate.parse("2026-11-17")), days.map { it.date })
        assertEquals(BigDecimal("3042000.00"), days[0].total)
        assertEquals("Mitra · GH A · Golden 120 kg", rows[0].meta())
        assertEquals("Tanpa tipe · GH A · Inthanon 2,5 kg", rows[2].meta())
        assertEquals("Tanpa nama", rows[2].name)
        assertEquals(listOf("s2"), filterSales(rows, "sar", null, null).map { it.id })
        assertEquals(listOf("s1"), filterSales(rows, "", "partner", null).map { it.id })
        assertEquals(listOf("s1", "s3"), filterSales(rows, "", null, "g1").map { it.id })
        assertEquals(listOf("s3"), filterSales(rows, "", "unspecified", null).map { it.id })
    }
}
