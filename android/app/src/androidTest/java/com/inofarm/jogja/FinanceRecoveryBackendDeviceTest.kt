package com.inofarm.jogja

import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.network.*
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.core.sync.FinanceSyncCoordinator
import com.inofarm.jogja.feature.auth.AuthRepository
import com.inofarm.jogja.feature.recovery.RecoveryRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Separate owned SQLite/vault survive instrumentation process and optional OS reboot. */
class FinanceRecoveryBackendDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name:String)=File(context.getExternalFilesDir(null),name)
    private fun fixture()=JSONObject(file("finance-recovery-fixture.json").readText()).also {assertTrue(context.packageName.endsWith(".finance"));assertEquals("inofarm_test",it.getString("database"))}
    private fun phase(name:String)=assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")==name)
    @Test fun beforeBackup():Unit=runBlocking {
        phase("before");val f=fixture();val helper=FinanceDatabase(context,"finance-test-recovery-${f.getString("drill_id")}.db");val store=LocalFinanceStore(helper,FixtureCipher());val vault=FinanceSessionVault(context,FixtureCipher(),"finance-test-recovery-${f.getString("drill_id")}");val api=FinanceHttpApi(true)
        try {
            val session=AuthRepository(api,vault,store).login(f.getString("base_url"),f.getString("worker_email"),f.getString("password"));FinanceSyncCoordinator(api,vault,store).run()
            val id=UUID.randomUUID().toString();val payload=f.getJSONObject("payload");store.saveAndQueue(session.scope.key,id,f.getString("greenhouse_id"),payload)
            val accepted=store.next(session.scope.key)!!;val receipt=api.request(session.scope.server,session.token,"activities","POST",accepted.envelope)
            assertEquals(accepted.id,receipt.getString("operation_id")) // deliberately lose ACK locally
            val local=store.record(session.scope.key,id)!!;store.saveDraft(session.scope.key,id,f.getString("greenhouse_id"),JSONObject(payload.toString()).put("notes","Isian lokal lebih baru setelah pengiriman"),local.revision)
            val missingId=UUID.randomUUID().toString();store.saveAndQueue(session.scope.key,missingId,f.getString("greenhouse_id"),JSONObject(payload.toString()).put("notes","Belum pernah dikirim; pertahankan setelah restore"))
            val missing=store.outbox(session.scope.key).single {it.entityId==missingId}
            file("finance-recovery-device-before.json").writeText(JSONObject().put("fixture_id",f.getString("fixture_id")).put("accepted_operation",accepted.envelope).put("missing_operation",missing.envelope).put("raw_note",store.record(session.scope.key,id)!!.body.getString("notes")).toString())
            assertEquals(2,store.outbox(session.scope.key).size);assertEquals("QUEUED",store.outbox(session.scope.key).first {it.id==accepted.id}.state)
        } finally {helper.close()}
    }
    @Test fun afterRestore():Unit=runBlocking {
        phase("after");val f=fixture();val proof=JSONObject(file("finance-recovery-device-before.json").readText());assertEquals(f.getString("fixture_id"),proof.getString("fixture_id"))
        val helper=FinanceDatabase(context,"finance-test-recovery-${f.getString("drill_id")}.db");val store=LocalFinanceStore(helper,FixtureCipher());val vault=FinanceSessionVault(context,FixtureCipher(),"finance-test-recovery-${f.getString("drill_id")}");val api=FinanceHttpApi(true)
        try {
            vault.restore();val previous=vault.current!!;val oldHash=WireContract.hash(proof.getJSONObject("accepted_operation"));val missingHash=WireContract.hash(proof.getJSONObject("missing_operation"))
            try {api.request(previous.scope.server,previous.token,"sync/status");fail("Restored token revived")}catch(e:FinanceApiFailure){assertEquals(401,e.status)}
            val session=AuthRepository(api,vault,store).login(f.getString("base_url"),f.getString("worker_email"),f.getString("password"));assertNotEquals(f.getString("old_epoch"),store.epoch(session.scope.key))
            assertEquals(2,store.outbox(session.scope.key).count {it.state=="EPOCH_REVIEW"});assertNull(store.next(session.scope.key));FinanceSyncCoordinator(api,vault,store).run()
            val op=store.outbox(session.scope.key).single {it.id==proof.getJSONObject("accepted_operation").getString("operation_id")};val missing=store.outbox(session.scope.key).single {it.id==proof.getJSONObject("missing_operation").getString("operation_id")}
            assertEquals(oldHash,op.hash);assertEquals(missingHash,missing.hash)
            val repository=RecoveryRepository(vault,store,api);val review=repository.inspect(op);assertEquals("receipt_found",review.getString("state"));repository.acknowledge(op,review)
            val local=store.record(session.scope.key,op.entityId)!!;assertEquals(proof.getString("raw_note"),local.body.getString("notes"));assertEquals(SyncState.LOCAL,local.syncState)
            assertEquals("ACCEPTED",store.outbox(session.scope.key).single {it.id==op.id}.state)
            val absent=repository.inspect(missing);assertEquals("manual_review",absent.getString("state"));assertTrue(absent.isNull("receipt"));assertEquals("EPOCH_REVIEW",store.outbox(session.scope.key).single {it.id==missing.id}.state);assertNull(store.next(session.scope.key))
            try {api.request(session.scope.server,session.token,"activities","POST",op.envelope);fail("Old epoch replayed")}catch(e:FinanceApiFailure){assertEquals("epoch_mismatch",e.code)}
            val manager=AuthRepository(api,vault,store).login(f.getString("base_url"),f.getString("manager_email"),f.getString("password"));val restores=api.request(manager.scope.server,manager.token,"recovery/restores").getJSONArray("data")
            assertTrue(restores.length()>0);assertTrue(restores.getJSONObject(0).getJSONObject("verification").getBoolean("point_in_time_match"))
            val activity=api.request(manager.scope.server,manager.token,"activities/${op.entityId}");assertEquals(1,activity.getInt("version"))
            file("finance-recovery-device-after.json").writeText(JSONObject().put("fixture_id",f.getString("fixture_id")).put("state","PASS").put("new_epoch",store.epoch(session.scope.key)).put("accepted_hash",oldHash).put("missing_hash",missingHash).put("raw_preserved",true).put("missing_held",true).toString())
        } finally {helper.close()}
    }
}
