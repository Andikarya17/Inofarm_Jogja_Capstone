package com.inofarm.jogja

import com.inofarm.jogja.feature.harvest.domain.HarvestDraft
import com.inofarm.jogja.feature.harvest.domain.HarvestLine
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HarvestDraftTest {
    private val gh=UUID.randomUUID().toString();private val group=UUID.randomUUID().toString();private val grade=UUID.randomUUID().toString()
    private fun draft()=HarvestDraft(greenhouseId=gh,groupId=group,date="2026-10-29",time="08:12:34",items=listOf(HarvestLine(gradeId=grade,fruitCount="10",weightKg="15,25")))
    @Test fun integerCountAndDecimalStringWeightKeepTheirWireTypesAndStableItemIds() {
        val draft=draft();val payload=draft.payload();val row=payload.getJSONArray("items").getJSONObject(0)
        assertTrue(row.get("fruit_count") is Int);assertEquals("15.25",row.get("weight_kg"));assertEquals(draft.items.single().id,row.getString("id"))
        assertEquals("2026-10-29T08:12:34+07:00",payload.getString("occurred_at"));assertFalse(payload.has("recorded_by"));assertFalse(payload.has("_local_form"))
    }
    @Test fun rawInvalidDraftSurvivesJsonRoundTripWithoutBecomingReadyOrZero() {
        val invalid=draft().copy(date="belum",items=listOf(HarvestLine(gradeId=grade,fruitCount="",weightKg="1,2,3")))
        val decoded=HarvestDraft.from(invalid.id,gh,invalid.localJson(),null)
        assertEquals(invalid,decoded);assertFalse(decoded.validation().isEmpty())
        assertThrows(IllegalArgumentException::class.java) {decoded.payload()}
        val explicitZero=draft().copy(items=listOf(HarvestLine(gradeId=grade,fruitCount="0",weightKg="0")))
        assertTrue(explicitZero.validation().isEmpty())
    }
    @Test fun duplicateGradesFractionalCountsOverflowAndNegativeWeightsAreRejected() {
        val draft=draft();assertTrue(draft.copy(items=draft.items+draft.items.single().copy(id=UUID.randomUUID().toString())).validation().containsKey("items"))
        for(count in listOf("-1","1.5","2,5","2147483648","NaN")) assertFalse(draft.copy(items=listOf(draft.items.single().copy(fruitCount=count))).validation().isEmpty())
        for(weight in listOf("-0.01","NaN","1e2","1.0000001","100000000000000")) assertFalse(draft.copy(items=listOf(draft.items.single().copy(weightKg=weight))).validation().isEmpty())
    }
    @Test fun correctionRequiresReasonAndPreservesHistoricalSecondsAndLockedItemIdentity() {
        val original=draft();val body=original.payload();val itemId=original.items.single().id
        val restored=HarvestDraft.from(original.id,gh,body,4)
        assertEquals("08:12:34",restored.time);assertEquals(setOf(itemId),restored.lockedItems)
        assertTrue(restored.validation().containsKey("reason"));val ready=restored.copy(reason="Koreksi uji",referencePolicy="refresh")
        assertEquals("refresh",ready.payload().getString("reference_policy"));assertEquals(ready,HarvestDraft.from(ready.id,gh,ready.localJson(),4))
    }
    /** Inofarm 20 Nov 2026: layak and cacat per variety, no grade, no time (sent at noon WIB). */
    @Test fun newHarvestSendsQualityLinesWithoutGradeAndAtNoon() {
        val d = HarvestDraft(greenhouseId = gh, groupId = group, date = "2026-11-20", time = HarvestDraft.NOON).qualityLines()
        assertEquals(listOf(com.inofarm.jogja.feature.harvest.domain.HarvestQuality.SALEABLE, com.inofarm.jogja.feature.harvest.domain.HarvestQuality.DEFECTIVE),
            d.items.map { it.quality })
        val filled = d.copy(items = listOf(d.items[0].copy(weightKg = "40"), d.items[1]))
        assertTrue(filled.validation().toString(), filled.validation().isEmpty())
        val items = filled.payload().getJSONArray("items")
        assertEquals(1, items.length()); assertFalse(items.getJSONObject(0).has("grade_id"))
        assertEquals("saleable", items.getJSONObject(0).getString("quality"))
        assertEquals("2026-11-20T12:00:00+07:00", filled.payload().getString("occurred_at"))
        assertEquals(filled, HarvestDraft.from(filled.id, gh, filled.localJson(), null))
        assertEquals(filled.items.map { it.id }, filled.qualityLines().items.map { it.id })
    }
    @Test fun aCountWithoutWeightIsRejectedAndAllEmptyIsRejected() {
        val d = HarvestDraft(greenhouseId = gh, groupId = group).qualityLines()
        assertTrue(d.validation().containsKey("items"))
        assertTrue(d.copy(items = listOf(d.items[0], d.items[1].copy(fruitCount = "3"))).validation().containsKey("items.1.weight"))
        // Two varieties: either may be empty, not both; the empty one is not sent.
        val two = d.copy(items = listOf(d.items[0].copy(weightKg = "5"), d.items[1]),
            companion = HarvestDraft(greenhouseId = gh, groupId = UUID.randomUUID().toString()).qualityLines())
        assertTrue(two.validation().isEmpty()); assertEquals(1, two.parts().size)
    }
    @Test fun anOldGradedHarvestCorrectsWithItsGradesKept() {
        val restored = HarvestDraft.from(UUID.randomUUID().toString(), gh, draft().payload(), 2).copy(reason = "Koreksi")
        assertEquals(restored, restored.qualityLines())
        assertTrue(restored.validation().isEmpty())
        assertEquals(grade, restored.payload().getJSONArray("items").getJSONObject(0).getString("grade_id"))
    }

    /** Harvests may be weighed only (17 Nov 2026): an empty count is sent as null, never as zero. */
    @Test fun anEmptyFruitCountIsSentAsNullAndReadBackEmpty() {
        val weighed=draft().copy(items=listOf(HarvestLine(gradeId=grade,fruitCount="",weightKg="12,5")))
        assertTrue(weighed.validation().isEmpty())
        val row=weighed.payload().getJSONArray("items").getJSONObject(0)
        assertTrue(row.isNull("fruit_count"));assertEquals("12.5",row.getString("weight_kg"))
        val server=org.json.JSONObject(weighed.payload().toString()).put("planting_group_id",group)
        assertEquals("",HarvestDraft.from(weighed.id,gh,server,1).items.single().fruitCount)
    }

    /** Two varieties of one greenhouse are harvested in one form and become one harvest per variety. */
    @Test fun aSecondVarietyIsItsOwnHarvestSharingTimeAndNotes() {
        val second=UUID.randomUUID().toString()
        val both=draft().copy(notes="Panen pagi",runningPeriodVersion=3,companion=HarvestDraft(greenhouseId=gh,groupId=second,
            items=listOf(HarvestLine(gradeId=grade,fruitCount="",weightKg="8"))))
        assertTrue(both.validation().isEmpty())
        val parts=both.parts()
        assertEquals(listOf(group,second),parts.map { it.groupId })
        assertEquals(2,parts.map { it.id }.toSet().size)
        assertTrue(parts.all { it.date==both.date && it.time==both.time && it.notes=="Panen pagi" && it.companion==null })
        assertEquals(3L,parts[0].runningPeriodVersion);assertNull(parts[1].runningPeriodVersion)
        assertEquals(second,parts[1].payload().getString("planting_group_id"))
        // One variety may have nothing to harvest; both empty is refused.
        assertEquals(listOf(second),both.copy(items=emptyList()).parts().map { it.groupId })
        assertTrue(both.copy(items=emptyList()).validation().isEmpty())
        assertEquals(setOf("items"),both.copy(items=emptyList(),companion=both.companion!!.copy(items=emptyList())).validation().keys)
        // The second variety's errors are reported under its own prefix.
        val bad=both.copy(companion=both.companion!!.copy(items=listOf(HarvestLine(gradeId=grade,fruitCount="x",weightKg="1"))))
        assertTrue(bad.validation().keys.any { it.startsWith("companion.items.0") })
        // The pair survives the device copy, ids included.
        assertEquals(both,HarvestDraft.from(both.id,gh,both.localJson(),null))
    }
}
