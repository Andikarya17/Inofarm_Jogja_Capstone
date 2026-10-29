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
}
