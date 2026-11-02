package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.feature.activities.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class ManualMeasurementTest {
    private val gh=UUID.randomUUID().toString();private val group=UUID.randomUUID().toString()
    private val today=LocalDate.parse("2026-11-02")
    private fun draft()=ActivityDraft(greenhouseId=gh,groupId=group,type=ActivityType.MEASUREMENT,date=today.toString(),time="00:01",ec="1,25",ppm="625,5")
    private fun row(state:SyncState=SyncState.ACCEPTED,body:JSONObject=draft().payload())=LocalRecord(UUID.randomUUID().toString(),"activity",gh,1,1,1,body,null,null,state,null)
    @Test fun decimalPairDoesNotRequireMaterialOrPhAndDraftRetainsRawMissingEc() {
        val d=draft();assertTrue(d.validation().isEmpty());assertEquals("1.25",d.payload().getString("manual_ec"));assertEquals("625.5",d.payload().getString("manual_ppm"));assertTrue(d.payload().isNull("manual_ph"))
        for(draft in listOf(d.copy(ec=""),d.copy(ppm=""),d.copy(ec="1.0000001"),d.copy(ec="NaN"),d.copy(ec="-1"),d.copy(ec="1,2.3"),d.copy(skipped=true,skippedReason="uji"))) assertFalse(draft.validation().isEmpty())
        assertTrue(d.copy(ec="0",ppm="0").validation().isEmpty())
        val incomplete=d.copy(ec="belum",ppm="");assertEquals(incomplete,ActivityDraft.from(d.id,gh,incomplete.localJson(),null))
        val legacy=d.payload().apply {remove("manual_ec");put("activity_type","general");put("notes","histori")}
        assertEquals("",ActivityDraft.from(d.id,gh,legacy,1).ec)
    }
    @Test fun onlyReadyActiveAcceptedOrCompleteQueuedPairsAreCandidates() {
        assertNotNull(ManualMeasurement.from(row(),today));assertTrue(ManualMeasurement.from(row(SyncState.QUEUED),today)!!.queued)
        for(state in SyncState.entries.filter {it !in setOf(SyncState.ACCEPTED,SyncState.QUEUED)}) assertNull(ManualMeasurement.from(row(state),today))
        for(body in listOf(draft().localJson(),draft().payload().put("record_state","cancelled"),draft().payload().put("work_state","skipped"),draft().payload().put("manual_ppm",JSONObject.NULL),draft().payload().put("manual_ec","bad"))) assertNull(ManualMeasurement.from(row(body=body),today))
        val legacy=draft().payload().put("activity_type","nutrition").put("manual_ec",JSONObject.NULL)
        assertNull(ManualMeasurement.from(row(SyncState.QUEUED,legacy),today));assertNull(ManualMeasurement.from(row(body=legacy),today)!!.ec)
    }
    @Test fun wibBoundaryAndFallbackUseOccurredAtWithActualDate() {
        val pair=ManualMeasurement.from(row(body=draft().payload().put("occurred_at","2026-11-01T17:01:00Z")),today)!!
        assertEquals("Pengukuran hari ini",pair.message(today))
        for(at in listOf("2026-11-01T16:59:00Z","2026-10-28T16:59:00Z")) {
            val old=ManualMeasurement.from(row(body=draft().payload().put("occurred_at",at)),today)!!
            assertTrue(old.message(today).startsWith("Belum ada pengukuran hari ini. Data "));assertTrue(old.message(today).endsWith("WIB."))
        }
        assertNull(ManualMeasurement.from(row(body=draft().payload().put("occurred_at","2026-11-02T17:00:00Z")),today))
    }
}
