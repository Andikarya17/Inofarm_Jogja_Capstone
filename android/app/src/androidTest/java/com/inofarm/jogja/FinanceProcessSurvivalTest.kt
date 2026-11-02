package com.inofarm.jogja

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.FinanceDatabase
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.session.AndroidPayloadCipher
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
    @Test fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("survivalPhase") == "prepare")
        assertTrue(context.packageName.endsWith(".finance"))
        val helper=FinanceDatabase(context,name);val store=LocalFinanceStore(helper,AndroidPayloadCipher())
        try {
            val epoch=UUID.randomUUID().toString();val gh=UUID.randomUUID().toString();val id=UUID.randomUUID().toString()
            store.initialize("survival-account",epoch,UserRole.WORKER)
            store.applyPull("survival-account",JSONObject().put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh))).put("data",JSONArray()).put("next_cursor","fixture"))
            store.saveAndQueue("survival-account",id,gh,JSONObject().put("planting_group_id",UUID.randomUUID().toString()).put("activity_type","general").put("occurred_at","2026-10-29T08:00:00+07:00").put("work_state","completed").put("materials",JSONArray()).put("notes","Tetap ada setelah proses mati"))
            val op=store.next("survival-account")!!
            context.getSharedPreferences("finance-survival-test",0).edit().putString("id",id).putString("op",op.id).putString("hash",op.hash).putLong("pid",android.os.Process.myPid().toLong()).commit()
        } finally { helper.close() }
    }
    @Test fun verify() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("survivalPhase") == "verify")
        assertTrue(context.packageName.endsWith(".finance"))
        val expected=context.getSharedPreferences("finance-survival-test",0)
        assertNotEquals(expected.getLong("pid",0),android.os.Process.myPid().toLong())
        val helper=FinanceDatabase(context,name);val store=LocalFinanceStore(helper,AndroidPayloadCipher())
        try {
            val op=store.next("survival-account")!!
            assertEquals(expected.getString("op",null),op.id);assertEquals(expected.getString("hash",null),op.hash)
            assertEquals("Tetap ada setelah proses mati",store.record("survival-account",expected.getString("id",null)!!)!!.body.getString("notes"))
        } finally { helper.close();context.deleteDatabase(name);expected.edit().clear().commit() }
    }
}
