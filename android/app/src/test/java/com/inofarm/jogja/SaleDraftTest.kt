package com.inofarm.jogja

import com.inofarm.jogja.feature.sales.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SaleDraftTest {
    private val gh=UUID.randomUUID().toString();private val season=UUID.randomUUID().toString()
    private fun ready()=SaleDraft(greenhouseId=gh,seasonId=season,date="2026-10-30",time="08:12:34",paid=true,items=listOf(SaleLine(sourceId=UUID.randomUUID().toString(),quantity="1,000001",price="0,005")))
    @Test fun rawInvalidInputsAndUnconfirmedPaidRemainDraftAcrossSerialization() {
        val draft=ready().copy(date="belum",paid=false,items=listOf(SaleLine(quantity="1,2,3",price="")))
        assertEquals(draft,SaleDraft.from(draft.id,gh,draft.localJson(),null));assertTrue(draft.validation().containsKey("paid"));assertTrue(draft.validation().containsKey("items.0.price"));assertThrows(IllegalArgumentException::class.java) {draft.payload()}
    }
    @Test fun wireAmountsAreExactAndTotalsRemainServerOwned() {
        val draft=ready();val body=draft.payload();val line=body.getJSONArray("items").getJSONObject(0)
        assertEquals("1.000001",line.getString("quantity_kg"));assertEquals("0.005000",line.getString("price_per_kg"));assertEquals("0.01",draft.preview().total.toPlainString())
        assertFalse(body.has("total_amount"));assertFalse(body.has("recorded_by"));assertEquals("paid",body.getString("payment_status"));assertEquals("2026-10-30T08:12:34+07:00",body.getString("paid_at"))
        val duplicate=draft.copy(items=draft.items+draft.items.single().copy(id=UUID.randomUUID().toString()));assertTrue(duplicate.validation().containsKey("items"))
    }
    @Test fun acceptedSourceIdsSeasonAndWibSecondsArePreservedForReasonedCorrection() {
        val draft=ready();val accepted=draft.payload().put("paid_at","2026-10-30T01:12:34.000000Z")
        val restored=SaleDraft.from(draft.id,gh,accepted,2)
        assertTrue(restored.lockedSeason);assertEquals(setOf(draft.items.single().id),restored.lockedSources);assertEquals("08:12:34",restored.time)
        assertTrue(restored.validation().containsKey("reason"));assertFalse(restored.copy(reason="Koreksi nyata").validation().containsKey("reason"))
    }
}
