package com.inofarm.jogja

import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.feature.activities.domain.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.util.UUID

class FinanceMeasurementStoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="measurement-fixture-${UUID.randomUUID()}.db"
    private val gh=UUID.randomUUID().toString();private val group=UUID.randomUUID().toString();private val epoch=UUID.randomUUID().toString()
    private lateinit var helper:FinanceDatabase;private lateinit var store:LocalFinanceStore
    private val today=LocalDate.parse("2026-11-02")
    @Before fun setup() {helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher());store.initialize("a",epoch,UserRole.MANAGER);store.applyPull("a",page())}
    @After fun close() {helper.close();assertTrue(name.startsWith("measurement-fixture-"));context.deleteDatabase(name)}
    private fun page(rows:JSONArray=JSONArray(),scope:JSONArray=JSONArray().put(JSONObject().put("id",gh)))=JSONObject().put("sync_epoch",epoch).put("scope",scope).put("data",rows).put("next_cursor","fixture").put("has_more",false)
    private fun body(at:String="2026-11-02T08:00:00+07:00",ec:String="1.2",ppm:String="600")=ActivityDraft(greenhouseId=gh,groupId=group,type=ActivityType.MEASUREMENT,date=at.substringBefore('T'),time=at.substringAfter('T').take(5),ec=ec,ppm=ppm).payload().put("occurred_at",at)
    private fun accept(id:String,body:JSONObject,version:Int=1) {
        val row=JSONObject().put("entity_type","activity").put("entity_id",id).put("greenhouse_id",gh).put("version",version).put("tombstone",false).put("data",body.put("id",id).put("version",version))
        store.applyPull("a",page(JSONArray().put(row)))
    }
    private fun latest()=store.latestMeasurement("a",gh,group,today)
    @Test fun workerFallsBackAfterReviewAndReconsidersTheCorrectedRevision() {
        store.initialize("a",epoch,UserRole.WORKER)
        val older=UUID.randomUUID().toString()
        val newer=UUID.randomUUID().toString()
        accept(older,body("2026-11-01T23:59:59+07:00"))
        accept(newer,body("2026-11-02T00:00:00+07:00","1.8","850"))
        assertEquals(newer,latest()!!.id)
        fun invalidate(activity: String) {
            val id=UUID.randomUUID().toString()
            val review=JSONObject().put("id",id).put("version",1).put("activity_id",activity)
                .put("activity_version",1).put("decision","invalid")
            val row=JSONObject().put("entity_type","activity_review").put("entity_id",id).put("greenhouse_id",gh)
                .put("version",1).put("tombstone",false).put("data",review)
            store.applyPull("a",page(JSONArray().put(row)))
        }
        invalidate(newer)
        assertEquals(older,latest()!!.id)
        invalidate(older)
        assertNull(latest())
        accept(newer,body("2026-11-02T00:00:00+07:00","1.3","620").put("recorded_by_role","worker"),2)
        assertEquals("1.3",latest()!!.ec)
        assertEquals("620",latest()!!.ppm)
        assertNull(store.latestMeasurement("a",gh,group,today.minusDays(1)))
    }
    @Test fun fallbackTieDelayedSyncCorrectionCancellationAndWholeCache() {
        assertNull(latest());val old=UUID.randomUUID().toString();accept(old,body("2026-10-28T08:00:00+07:00"));assertEquals(old,latest()!!.id)
        val yesterday=UUID.randomUUID().toString();accept(yesterday,body("2026-11-01T23:59:00+07:00"));assertEquals(yesterday,latest()!!.id)
        val a=UUID.randomUUID().toString();val b=UUID.randomUUID().toString();accept(b,body());accept(a,body());assertEquals(maxOf(a,b),latest()!!.id)
        accept(UUID.randomUUID().toString(),body("2026-11-01T23:58:00+07:00"));assertEquals(maxOf(a,b),latest()!!.id)
        // More than the UI cap and multiple DAO batches contain newer non-measurement activities.
        val rows=JSONArray();repeat(1005) {val id=UUID.randomUUID().toString();rows.put(JSONObject().put("entity_type","activity").put("entity_id",id).put("greenhouse_id",gh).put("version",1).put("tombstone",false).put("data",body("2026-11-02T10:00:00+07:00").put("id",id).put("version",1).put("activity_type","general").put("manual_ec",JSONObject.NULL).put("manual_ppm",JSONObject.NULL)))}
        store.applyPull("a",page(rows));assertEquals(maxOf(a,b),latest()!!.id)
        accept(maxOf(a,b),body("2026-10-27T08:00:00+07:00"),2);assertEquals(minOf(a,b),latest()!!.id)
        accept(minOf(a,b),body().put("record_state","cancelled"),2);assertEquals(yesterday,latest()!!.id)
        assertNull(store.latestMeasurement("a",gh,UUID.randomUUID().toString(),today));store.initialize("b",epoch,UserRole.WORKER);assertNull(store.latestMeasurement("b",gh,group,today))
        store.applyPull("a",page(scope=JSONArray()));assertNull(latest())
    }
    @Test fun draftQueueReopenRejectConflictCancelAndEpochNeverInventReadings() {
        val legacy=UUID.randomUUID().toString();accept(legacy,body("2026-11-01T12:00:00+07:00").put("activity_type","nutrition").put("manual_ec",JSONObject.NULL));assertNull(latest()!!.ec)
        store.saveDraft("a",UUID.randomUUID().toString(),gh,ActivityDraft(greenhouseId=gh,groupId=group,type=ActivityType.MEASUREMENT,ppm="600").localJson());assertEquals(legacy,latest()!!.id)
        val id=UUID.randomUUID().toString();store.saveAndQueue("a",id,gh,body());val op=store.next("a")!!;assertTrue(latest()!!.queued)
        helper.close();helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher());assertEquals(id,latest()!!.id);assertEquals(op.hash,store.next("a")!!.hash)
        store.mark("a",op,SyncState.CONFLICT,JSONObject());assertEquals(legacy,latest()!!.id)
        val second=UUID.randomUUID().toString();store.saveAndQueue("a",second,gh,body());store.mark("a",store.next("a")!!,SyncState.REJECTED,JSONObject());assertEquals(legacy,latest()!!.id)
        store.queueAction("a",legacy,gh,"activity",store.record("a",legacy)!!.body,JSONObject().put("reason","uji batal"),"cancel",1,store.record("a",legacy)!!.revision);assertNull(latest())
        store.initialize("a",UUID.randomUUID().toString(),UserRole.MANAGER);assertNull(latest());assertNotNull(store.record("a",id))
    }
}
