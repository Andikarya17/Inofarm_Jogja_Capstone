package com.inofarm.jogja

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.feature.sales.*
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SaleRepositoryDeviceTest {
    @Test fun rejectedQueueRetainsLatestRawFormAndNeverInventsPaymentOrOperation() = runBlocking {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(c.packageName.endsWith(".finance"))
        val name="finance-test-sale-${UUID.randomUUID()}.db"; val pref="finance-test-sale-${UUID.randomUUID()}"
        val db=FinanceDatabase(c,name)
        try {
            val store=LocalFinanceStore(db,FixtureCipher());val vault=FinanceSessionVault(c,FixtureCipher(),pref)
            val session=FinanceSession(AccountScope("http://fixture.invalid/api/v2/",UUID.randomUUID().toString()),"synthetic",Instant.now().plusSeconds(3600).toString(),"Sintetis",UserRole.MANAGER)
            val ticket=vault.beginLogin();vault.save(ticket,session)
            val gh=UUID.randomUUID().toString();val epoch=UUID.randomUUID().toString()
            store.initialize(session.scope.key,epoch,UserRole.MANAGER)
            store.applyPull(session.scope.key,JSONObject().put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh))).put("data",JSONArray()).put("next_cursor","fixture").put("has_more",false))
            val repo=SaleRepository(vault,store);val initial=SaleDraft(greenhouseId=gh,notes="Awal")
            repo.flush(session,ticket,initial)
            val latest=initial.copy(notes="Isian terakhir sebelum validasi gagal",items=listOf(SaleLine(quantity="1,",price="")))
            try {repo.save(session,ticket,latest,true);fail("Expected validation failure")} catch (_:SaleValidationFailure) { }
            val row=store.record(session.scope.key,latest.id)!!
            assertEquals(latest,SaleDraft.from(row.id,gh,row.body,null));assertFalse(row.body.getJSONObject("_local_form").getBoolean("paid"))
            assertTrue(store.outbox(session.scope.key).isEmpty())
            vault.logout();assertNotNull(store.record(session.scope.key,latest.id))
        } finally { db.close();c.deleteDatabase(name);c.getSharedPreferences(pref,0).edit().clear().commit() }
    }
}
