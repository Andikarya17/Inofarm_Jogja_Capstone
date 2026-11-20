package com.inofarm.jogja

import com.inofarm.jogja.feature.harvest.domain.HarvestPattern
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Inofarm 20 Nov 2026: the harvest pattern, kg layak and cacat per harvest date per variety. */
class HarvestPatternTest {
    private val groupA = "33333333-3333-4333-8333-333333333333"
    private fun harvest(group: String, at: String, vararg lines: Pair<String?, String>) = JSONObject()
        .put("planting_group_id", group).put("occurred_at", at).put("record_state", "active")
        .put("items", JSONArray().apply { lines.forEach { (quality, kg) -> put(JSONObject().put("quality", quality ?: JSONObject.NULL).put("weight_kg", kg)) } })

    @Test fun daysSumPerQualityAndSkipCancelledAndUnclassified() {
        val p = HarvestPattern.build(listOf(
            harvest(groupA, "2026-11-18T12:00:00+07:00", "saleable" to "40", "defective" to "5"),
            harvest(groupA, "2026-11-18T15:00:00+07:00", "saleable" to "10"),
            harvest(groupA, "2026-11-19T12:00:00+07:00", "defective" to "5").put("record_state", "cancelled"),
            harvest(groupA, "2026-11-20T12:00:00+07:00", null to "7")), listOf(groupA)).single()
        assertEquals(listOf(LocalDate.parse("2026-11-18")), p.days.map { it.date })
        assertEquals(0, p.days.single().saleableKg.compareTo(BigDecimal("50")))
        assertEquals(0, p.totalKg.compareTo(BigDecimal("55")))
        assertEquals(9, p.defectivePercent)
        assertEquals(0, p.unclassifiedKg.compareTo(BigDecimal("7")))
    }

    @Test fun datesFollowWibAndDraftsAreSkipped() {
        val p = HarvestPattern.build(listOf(
            harvest(groupA, "2026-11-18T23:30:00+00:00", "saleable" to "3"),
            harvest(groupA, "2026-11-18T08:00:00+07:00", "saleable" to "9").put("_local_form", JSONObject())), listOf(groupA)).single()
        assertEquals(listOf(LocalDate.parse("2026-11-19")), p.days.map { it.date })
    }

    @Test fun noHarvestGivesNoPercent() { assertNull(HarvestPattern.build(emptyList(), listOf(groupA)).single().defectivePercent) }
}
