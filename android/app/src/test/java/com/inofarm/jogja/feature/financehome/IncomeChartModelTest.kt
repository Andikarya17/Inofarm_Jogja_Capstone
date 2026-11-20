package com.inofarm.jogja.feature.financehome

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class IncomeChartModelTest {
    private fun slot(receipts: String, kg: String) = JSONObject().put("receipts", receipts).put("weight_kg", kg)
    private fun bucket(key: String, label: String, consumer: String, partner: String, unspecified: String = "0.00") =
        JSONObject().put("key", key).put("label", label).put("start", "$key-01").put("receipts", "0").put("weight_kg", "0")
            .put("by_type", JSONObject().put("consumer", slot(consumer, "180.000000")).put("partner", slot(partner, "728.000000"))
                .put("unspecified", slot(unspecified, "0.000000")))

    private fun bars(vararg buckets: JSONObject) = IncomeBar.list(JSONObject().put("buckets", JSONArray(buckets.toList())))

    @Test fun month_total_and_split() {
        val nov = bars(bucket("2026-11", "Nov", "6300000.00", "18200000.00")).single()
        assertEquals(BigDecimal("24500000.00"), nov.total())
        assertEquals(74, nov.partnerShare())
        assertEquals(BigDecimal("908.000000"), nov.weightKg())
    }

    @Test fun compare_text_rises_from_previous() {
        val (oct, nov) = bars(bucket("2026-10", "Okt", "4000000.00", "11800000.00"), bucket("2026-11", "Nov", "6300000.00", "18200000.00"))
        assertEquals("naik Rp8.700.000 dari Okt", nov.compareTo(oct))
        assertEquals("turun Rp8.700.000 dari Nov", oct.compareTo(nov))
        assertEquals("periode pertama di grafik", oct.compareTo(null))
    }

    @Test fun heights_scale_to_tallest_bar() {
        val list = bars(bucket("2026-10", "Okt", "4000000.00", "11800000.00"), bucket("2026-11", "Nov", "6300000.00", "18200000.00"),
            bucket("2026-12", "Des", "0.00", "0.00"))
        val heights = barHeights(list, 100f)
        assertEquals(100f, heights[1].first + heights[1].second + heights[1].third, 0.01f)
        assertEquals(48.16f, heights[0].first, 0.01f)
        assertEquals(0f, heights[2].first + heights[2].second + heights[2].third, 0.0f)
    }

    @Test fun small_nonzero_segment_stays_visible() {
        val list = bars(bucket("2026-11", "Nov", "1.00", "100000000.00"))
        assertEquals(3f, barHeights(list, 100f)[0].second, 0.0f)
    }

    @Test fun week_range_starts_on_monday_and_covers_eight_weeks() {
        val (from, until) = IncomePeriod.WEEK.range(LocalDate.parse("2026-11-21"))
        assertEquals(LocalDate.parse("2026-09-28"), from)
        assertEquals(LocalDate.parse("2026-11-21"), until)
        val (mFrom, _) = IncomePeriod.MONTH.range(LocalDate.parse("2026-11-21"))
        assertEquals(LocalDate.parse("2026-06-01"), mFrom)
    }

    @Test fun unspecified_only_shown_when_present() {
        assertFalse(bars(bucket("2026-11", "Nov", "1.00", "2.00")).any { it.hasUnspecified() })
        assertTrue(bars(bucket("2026-11", "Nov", "1.00", "2.00", "5.00")).single().hasUnspecified())
    }
}
