package com.inofarm.jogja

import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.activities.domain.MaterialDraft
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ActivityDraftTest {
    private val gh=UUID.randomUUID().toString();private val group=UUID.randomUUID().toString()
    private fun nutrition()=ActivityDraft(greenhouseId=gh,groupId=group,type=ActivityType.NUTRITION,date="2026-10-29",time="08:00",ppm="0",ph="6,5",
        materials=listOf(MaterialDraft(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"0")))
    @Test fun localeDecimalIsConvertedOnlyAtWireBoundaryAndMissingIsNotZero() {
        val draft=nutrition();assertTrue(draft.validation().isEmpty());assertEquals("6.5",draft.payload().getString("manual_ph"));assertEquals("0",draft.payload().getString("manual_ppm"))
        assertEquals("2026-10-29T08:00:00+07:00",draft.payload().getString("occurred_at"));assertTrue(draft.copy(ppm="").validation().containsKey("manual_ppm"))
        assertTrue(draft.copy(ph="14.01").validation().containsKey("manual_ph"));assertTrue(draft.copy(time="24:00").validation().containsKey("occurred_at"))
    }
    @Test fun incompleteDraftRoundTripKeepsRawInvalidFieldsAndDoesNotBecomeReady() {
        val draft=nutrition().copy(ppm="bukan angka",date="belum lengkap",notes="Draf mentah")
        val restored=ActivityDraft.from(draft.id,gh,draft.localJson(),null)
        assertEquals(draft,restored);assertFalse(restored.validation().isEmpty());assertThrows(IllegalArgumentException::class.java) {restored.payload()}
    }
    @Test fun skippedReasonMultiMaterialAndCorrectionReasonAreSeparate() {
        assertTrue(nutrition().copy(skipped=true,skippedReason="").validation().containsKey("skipped_reason"))
        assertTrue(nutrition().copy(skipped=true,skippedReason="Alasan uji",ppm="",ph="",materials=emptyList()).validation().isEmpty())
        assertEquals(2,nutrition().copy(type=ActivityType.SPRAY,materials=nutrition().materials+nutrition().materials).payload().getJSONArray("materials").length())
        assertTrue(nutrition().copy(baseVersion=1).validation().containsKey("reason"))
    }
    @Test fun existingSecondsRemainUnchangedDuringNotesCorrection() {
        val payload=nutrition().payload().put("occurred_at","2026-10-29T01:00:35Z")
        val restored=ActivityDraft.from(UUID.randomUUID().toString(),gh,payload,1)
        assertEquals("08:00:35",restored.time)
        assertEquals("2026-10-29T08:00:35+07:00",restored.copy(reason="Koreksi catatan").payload().getString("occurred_at"))
    }
}
