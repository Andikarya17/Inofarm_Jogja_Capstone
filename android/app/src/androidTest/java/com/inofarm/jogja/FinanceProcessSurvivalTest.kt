package com.inofarm.jogja

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.FinanceDatabase
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.session.AndroidPayloadCipher
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Run prepare, force-stop .finance test APK, then verify in a second instrumentation process. */
@RunWith(AndroidJUnit4::class)
class FinanceProcessSurvivalTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-process-survival-test.db"
    private val homeParent get() = 3L.takeIf { InstrumentationRegistry.getArguments().getString("managerHome") == "true" }
    private fun isolatedBuild() = context.packageName.endsWith(".finance") || context.packageName.endsWith(".finance.performance")
    @Test fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("survivalPhase") == "prepare")
        assertTrue(isolatedBuild())
        val helper=FinanceDatabase(context,name);val store=LocalFinanceStore(helper,AndroidPayloadCipher())
        try {
            val epoch=UUID.randomUUID().toString();val gh=UUID.randomUUID().toString();val id=UUID.randomUUID().toString()
            store.initialize("survival-account",epoch,if (homeParent == null) UserRole.WORKER else UserRole.MANAGER)
            store.applyPull("survival-account",JSONObject().put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh))).put("data",JSONArray()).put("next_cursor","fixture"))
            store.saveAndQueue("survival-account",id,gh,JSONObject().put("planting_group_id",UUID.randomUUID().toString()).put("activity_type","general").put("occurred_at","2026-10-29T08:00:00+07:00").put("work_state","completed").put("materials",JSONArray()).put("notes","Tetap ada setelah proses mati"))
            val op=store.next("survival-account")!!
            val draft=ActivityDraft(greenhouseId=gh,groupId=UUID.randomUUID().toString(),type=ActivityType.MEASUREMENT,ec="1,",ppm="",runningPeriodVersion=homeParent)
            store.saveDraft("survival-account",draft.id,gh,draft.localJson())
            val ready=draft.copy(id=UUID.randomUUID().toString(),ec="1,25",ppm="625,5")
            store.saveAndQueue("survival-account",ready.id,gh,ready.payload())
            val measurementOp=store.outbox("survival-account").single {it.entityId==ready.id}
            context.getSharedPreferences("finance-survival-test",0).edit().putString("id",id).putString("op",op.id).putString("hash",op.hash)
                .putString("draft",draft.id).putString("measurement",ready.id).putString("measurement_op",measurementOp.id).putString("measurement_hash",measurementOp.hash)
                .putString("greenhouse",gh).putString("group",draft.groupId)
                .putLong("pid",android.os.Process.myPid().toLong()).commit()
        } finally { helper.close() }
    }
    @Test fun verify() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("survivalPhase") == "verify")
        assertTrue(isolatedBuild())
        val expected=context.getSharedPreferences("finance-survival-test",0)
        assertNotEquals(expected.getLong("pid",0),android.os.Process.myPid().toLong())
        val helper=FinanceDatabase(context,name);val store=LocalFinanceStore(helper,AndroidPayloadCipher())
        try {
            val op=store.next("survival-account")!!
            assertEquals(expected.getString("op",null),op.id);assertEquals(expected.getString("hash",null),op.hash)
            assertEquals("Tetap ada setelah proses mati",store.record("survival-account",expected.getString("id",null)!!)!!.body.getString("notes"))
            val raw=store.record("survival-account",expected.getString("draft",null)!!)!!
            val draft=ActivityDraft.from(raw.id,raw.greenhouseId,raw.body,null)
            assertEquals("1,",draft.ec);assertEquals("",draft.ppm);assertFalse(draft.validation().isEmpty())
            assertEquals(homeParent,draft.runningPeriodVersion)
            assertEquals(expected.getString("greenhouse",null),draft.greenhouseId)
            assertEquals(expected.getString("group",null),draft.groupId)
            val measurement=store.outbox("survival-account").single {it.entityId==expected.getString("measurement",null)}
            assertEquals(expected.getString("measurement_op",null),measurement.id);assertEquals(expected.getString("measurement_hash",null),measurement.hash)
            assertEquals("1.25",measurement.envelope.getJSONObject("payload").getString("manual_ec"));assertEquals("625.5",measurement.envelope.getJSONObject("payload").getString("manual_ppm"))
            assertEquals(draft.groupId,measurement.envelope.getJSONObject("payload").getString("planting_group_id"))
            assertEquals(homeParent,measurement.envelope.getJSONObject("payload").optLong("running_period_version").takeIf { it > 0 })
        } finally { helper.close();context.deleteDatabase(name);expected.edit().clear().commit() }
    }
}
