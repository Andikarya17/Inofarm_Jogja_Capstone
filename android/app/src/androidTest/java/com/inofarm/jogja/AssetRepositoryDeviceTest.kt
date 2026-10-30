package com.inofarm.jogja

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.feature.assets.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AssetRepositoryDeviceTest {
    @Test fun rawFailureThenQueuedAssetSurvivesDatabaseReopenAndLateAckKeepsNewEdit()=runBlocking {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val name="finance-test-asset-${UUID.randomUUID()}.db";val pref="finance-test-asset-${UUID.randomUUID()}"
        var db=FinanceDatabase(c,name)
        try {
            val vault=FinanceSessionVault(c,FixtureCipher(),pref)
            val session=FinanceSession(AccountScope("http://fixture.invalid/api/v2/",UUID.randomUUID().toString()),"synthetic",Instant.now().plusSeconds(3600).toString(),"Sintetis",UserRole.MANAGER)
            val ticket=vault.beginLogin();vault.save(ticket,session)
            var store=LocalFinanceStore(db,FixtureCipher());val gh=UUID.randomUUID().toString();val epoch=UUID.randomUUID().toString();val expense=UUID.randomUUID().toString()
            store.initialize(session.scope.key,epoch,UserRole.MANAGER)
            store.applyPull(session.scope.key,JSONObject().put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh)))
                .put("data",JSONArray().put(JSONObject().put("entity_type","expense").put("entity_id",expense).put("greenhouse_id",gh).put("version",1).put("tombstone",false)
                    .put("data",JSONObject().put("id",expense).put("greenhouse_id",gh).put("version",1).put("record_state","active").put("payment_kind","asset_acquisition").put("amount","100.00"))))
                .put("next_cursor","fixture").put("has_more",false))
            var repo=AssetRepository(vault,store)
            val raw=AssetDraft(greenhouseId=gh,kind=AssetKind.ASSET,name="Isian terakhir",cost="1,")
            try {repo.save(session,ticket,raw,true);fail("Invalid raw form must not queue")} catch(_:AssetValidationFailure) { }
            assertEquals(raw,AssetDraft.from(raw.id,gh,raw.kind,store.record(session.scope.key,raw.id)!!.body,null));assertTrue(store.outbox(session.scope.key).isEmpty())
            val complete=raw.copy(expenseId=expense,cost="100",residual="0",lifeYears="3",inServiceDate="2024-02-29",basis="Pemakaian sintetis eksplisit")
            repo.save(session,ticket,complete,true)
            val pending=store.outbox(session.scope.key).single();assertEquals("asset",pending.envelope.getString("entity_type"));assertEquals(pending.hash,WireContract.hash(pending.envelope))
            db.close();db=FinanceDatabase(c,name);store=LocalFinanceStore(db,FixtureCipher());repo=AssetRepository(vault,store)
            val reopened=store.outbox(session.scope.key).single();assertEquals(pending.hash,reopened.hash);assertEquals(pending.id,reopened.id)
            val latest=complete.copy(name="Edit baru setelah antrean")
            repo.flush(session,ticket,latest)
            val accepted=complete.payload().put("id",complete.id).put("greenhouse_id",gh).put("version",1).put("record_state","active")
            store.acknowledge(session.scope.key,pending.id,JSONObject().put("operation_id",pending.id).put("entity_id",complete.id).put("version",1).put("data",accepted))
            val row=store.record(session.scope.key,complete.id)!!
            assertEquals(latest.name,AssetDraft.from(row.id,gh,raw.kind,row.body,row.baseVersion ?: row.serverVersion).name)
            vault.logout();assertNotNull(store.record(session.scope.key,complete.id))
        } finally {db.close();c.deleteDatabase(name);c.getSharedPreferences(pref,0).edit().clear().commit()}
    }
}
