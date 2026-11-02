package com.inofarm.jogja

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.FinanceDatabase
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.session.AndroidPayloadCipher
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinanceStorageTimingTest {
    @Test fun thirtyAtomicSavesWithOneThousandEncryptedSyntheticRecords() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".finance"))
        val name="finance-test-timing-${UUID.randomUUID()}.db";val epoch=UUID.randomUUID().toString();val gh=UUID.randomUUID().toString()
        val helper=FinanceDatabase(context,name);val store=LocalFinanceStore(helper,AndroidPayloadCipher())
        try {
            store.initialize("timing-scope",epoch,UserRole.WORKER)
            store.applyPull("timing-scope",JSONObject().put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh))).put("data",JSONArray()).put("next_cursor","fixture"))
            fun body(i:Int)=JSONObject().put("planting_group_id",gh).put("activity_type","general").put("occurred_at","2026-10-29T08:00:00+07:00").put("work_state","completed").put("materials",JSONArray()).put("notes","Fixture sintetis $i")
            repeat(1000) { store.saveDraft("timing-scope",UUID.randomUUID().toString(),gh,body(it)) }
            val samples=(0 until 30).map { index ->
                val before=System.nanoTime();store.saveAndQueue("timing-scope",UUID.randomUUID().toString(),gh,body(index+1000));(System.nanoTime()-before)/1_000_000.0
            }
            val csv="iteration,storage_save_ms\n"+samples.mapIndexed { index,value -> "${index+1},$value" }.joinToString("\n")+"\n"
            File(context.getExternalFilesDir(null),"finance-storage-timings.csv").writeText(csv)
            android.util.Log.i("FinanceStorageTiming","model=${android.os.Build.MODEL}; api=${android.os.Build.VERSION.SDK_INT}; rows=1000; n=30; min_ms=${samples.min()}; max_ms=${samples.max()}; mean_ms=${samples.average()}; failures=${samples.count { it>2000 }}")
            assertEquals(30,store.outbox("timing-scope").size)
            assertTrue("Storage save exceeded 2s; inspect all samples",samples.all { it<=2000 })
        } finally { helper.close();context.deleteDatabase(name) }
    }
}
