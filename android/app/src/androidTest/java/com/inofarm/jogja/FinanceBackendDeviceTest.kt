package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.network.FinanceApiFailure
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

/** Real Laravel/MySQL integration in explicitly selected isolated instrumentation processes. */
class FinanceBackendDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name:String)=File(context.getExternalFilesDir(null),name)
    private fun fixture():JSONObject { assertTrue(context.packageName.endsWith(".finance"));return JSONObject(file("finance-device-fixture.json").readText()).also {assertEquals("inofarm_test",it.getString("database"))} }
    private fun login(f:JSONObject,role:String) {
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("finance-logout").fetchSemanticsNodes().isNotEmpty()}
        if(rule.onAllNodesWithTag("finance-logout").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag("finance-logout").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-server").performScrollTo().performTextReplacement(f.getString("base_url"))
        rule.onNodeWithTag("finance-email").performScrollTo().performTextReplacement(f.getString("${role}_email"))
        rule.onNodeWithTag("finance-password").performScrollTo().performTextReplacement(f.getString("password"))
        hideKeyboard();rule.onNodeWithTag("finance-login").performScrollTo().performClick()
        rule.waitUntil(15000) {rule.onAllNodesWithTag("finance-gh").fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(15000) {rule.onAllNodesWithText("Data server sudah diperiksa. Tinjau antrean yang ditahan.").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun choose(tag:String,id:String) {rule.onNodeWithTag(tag).performScrollTo().performClick();rule.onNodeWithTag("$tag-option-$id").performScrollTo().performClick()}
    private fun hideKeyboard() {rule.runOnIdle {(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0)};rule.waitForIdle()}
    private fun runtime():FinanceRuntime {lateinit var runtime:FinanceRuntime;rule.runOnIdle {runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]};return runtime}
    private fun financeEditor():com.inofarm.jogja.feature.expenses.FinancialEntryViewModel {lateinit var editor:com.inofarm.jogja.feature.expenses.FinancialEntryViewModel;rule.runOnIdle {editor=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.expenses.FinancialEntryViewModel::class.java]};return editor}
    private fun financialOfflineBarrier(f:JSONObject,phase:String) {
        file("finance-$phase-ready.txt").writeText(f.getString("fixture_id"))
        val model=runtime();val session=model.vault.current!!
        val deadline=System.nanoTime()+90_000_000_000L
        var unavailable=false
        while(System.nanoTime()<deadline) {
            try {runBlocking {model.api.request(session.scope.server,session.token,"sync/status")}}
            catch(expected:IOException) {unavailable=true;break}
            Thread.sleep(1000)
        }
        assertTrue("Actual guarded API must become unavailable before offline entry",unavailable)
    }
    @Test fun queueFinancialPaymentWithBackendOffline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="payment-queue")
        val f=fixture();login(f,"manager");val a=f.getJSONArray("contexts").getJSONObject(0)
        rule.onNodeWithTag("finance-tab-4").performClick();rule.onNodeWithTag("expense-gh").performClick();rule.onNodeWithTag("expense-gh-option-${a.getString("greenhouse_id")}").performScrollTo().performClick()
        rule.onNodeWithTag("expense-new").performClick();choose("expense-category",a.getString("category_id"));choose("expense-payment-kind","operating")
        rule.onNodeWithTag("expense-date").performScrollTo().performTextReplacement("2026-10-30");rule.onNodeWithTag("expense-time").performScrollTo().performTextReplacement("08:12:34")
        rule.onNodeWithTag("expense-amount").performScrollTo().performTextReplacement("100,01");rule.onNodeWithTag("expense-notes").performScrollTo().performTextReplacement("DEVICE EXPENSE — ${f.getString("fixture_id")}");hideKeyboard()
        financialOfflineBarrier(f,"payment")
        rule.onNodeWithTag("expense-review").performScrollTo().performClick();rule.onNodeWithTag("expense-queue").performScrollTo().performClick()
        val model=runtime();val session=model.vault.current!!
        rule.waitUntil(15000) {model.store.outbox(session.scope.key).any {it.envelope.getString("entity_type")=="expense"}}
        val payment=model.store.outbox(session.scope.key).single {it.envelope.getString("entity_type")=="expense"};assertEquals("QUEUED",payment.state)
        val editor=financeEditor()
        rule.runOnIdle {editor.new(com.inofarm.jogja.core.contracts.RecordKind.COST_ALLOCATION,a.getString("greenhouse_id"));editor.edit {it.copy(expenseId=payment.entityId,seasonId=a.getString("season_id"),amount="60,01",costClass="VC",basis="DEVICE ALLOCATION ${f.getString("fixture_id")}")}}
        val allocationId=editor.state.value.draft!!.id;rule.onNodeWithTag("expense-save-draft").performScrollTo().performClick()
        rule.waitUntil(10000) {editor.state.value.draft==null && model.store.record(session.scope.key,allocationId)!=null}
        assertTrue(model.store.outbox(session.scope.key).none {it.envelope.getString("entity_type")=="cost_allocation"})
        file("finance-device-financial-proof.json").writeText(JSONObject().put("payment_pid",android.os.Process.myPid()).put("expense_id",payment.entityId).put("payment_op",payment.id).put("payment_hash",payment.hash).put("allocation_id",allocationId).toString())
    }
    @Test fun replayPaymentThenQueueAllocationWithBackendOffline():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="allocation-queue")
        val f=fixture();val proof=JSONObject(file("finance-device-financial-proof.json").readText());assertNotEquals(proof.getInt("payment_pid"),android.os.Process.myPid())
        val model=runtime();rule.waitUntil(15000) {model.vault.current!=null};val session=model.vault.current!!
        rule.waitUntil(20000) {model.store.record(session.scope.key,proof.getString("expense_id"))?.syncState==com.inofarm.jogja.core.contracts.SyncState.ACCEPTED && model.store.cachedOne(session.scope.key,"expense",proof.getString("expense_id"))!=null}
        val op=model.store.outbox(session.scope.key).single {it.id==proof.getString("payment_op")};assertEquals(proof.getString("payment_hash"),op.hash)
        val receipt=model.api.request(session.scope.server,session.token,"expenses","POST",op.envelope);assertEquals(1,receipt.getInt("version"));assertEquals("100.01",receipt.getJSONObject("data").getString("amount"))
        val allocation=model.store.record(session.scope.key,proof.getString("allocation_id"))!!;assertEquals("60,01",allocation.body.getJSONObject("_local_form").getString("amount"))
        val editor=financeEditor();rule.runOnIdle {editor.open(allocation)}
        financialOfflineBarrier(f,"allocation")
        rule.onNodeWithTag("expense-review").performScrollTo().performClick();rule.onNodeWithTag("expense-queue").performScrollTo().performClick()
        rule.waitUntil(10000) {model.store.outbox(session.scope.key).any {it.entityId==allocation.id}}
        val queued=model.store.outbox(session.scope.key).single {it.entityId==allocation.id};assertEquals("QUEUED",queued.state)
        proof.put("allocation_pid",android.os.Process.myPid()).put("allocation_op",queued.id).put("allocation_hash",queued.hash)
        file("finance-device-financial-proof.json").writeText(proof.toString())
    }
    @Test fun replayAllocationAndReviewFinancialScopeAndAudit():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="finance-verify")
        val f=fixture();val proof=JSONObject(file("finance-device-financial-proof.json").readText());assertNotEquals(proof.getInt("allocation_pid"),android.os.Process.myPid())
        val model=runtime();rule.waitUntil(15000) {model.vault.current!=null};val session=model.vault.current!!
        rule.waitUntil(20000) {model.store.record(session.scope.key,proof.getString("allocation_id"))?.syncState==com.inofarm.jogja.core.contracts.SyncState.ACCEPTED}
        val op=model.store.outbox(session.scope.key).single {it.id==proof.getString("allocation_op")};assertEquals(proof.getString("allocation_hash"),op.hash)
        val receipt=model.api.request(session.scope.server,session.token,"cost-allocations","POST",op.envelope);assertEquals(1,receipt.getInt("version"));assertEquals("60.01",receipt.getJSONObject("data").getString("amount"))
        val balance=model.api.request(session.scope.server,session.token,"expenses/${proof.getString("expense_id")}/availability").getJSONObject("data")
        assertEquals("60.01",balance.getString("allocated_amount"));assertEquals("40.00",balance.getString("available_amount"));assertEquals(1,balance.getInt("expense_version"));assertEquals(2,balance.getInt("version"))
        rule.onNodeWithTag("finance-tab-4").performClick();rule.onNodeWithTag("expense-history-kind").performClick();rule.onNodeWithTag("expense-history-kind-option-cost_allocation").performScrollTo().performClick()
        rule.waitUntil(15000) {rule.onAllNodesWithTag("finance-record-${op.entityId}").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-${op.entityId}").performScrollTo().performClick()
        rule.onNodeWithTag("history-audit").performScrollTo().performClick();rule.waitUntil(15000) {rule.onAllNodesWithText("Pencatatan · versi 1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-logout").performClick();login(f,"worker");val worker=model.vault.current!!
        rule.onNodeWithTag("finance-tab-4").assertDoesNotExist()
        try {model.api.request(worker.scope.server,worker.token,"expenses/${proof.getString("expense_id")}");fail("Worker financial read must be denied")} catch(error:FinanceApiFailure) {assertEquals(403,error.status)}
        val changes=model.api.request(worker.scope.server,worker.token,"sync/changes").getJSONArray("data")
        assertTrue((0 until changes.length()).none {changes.getJSONObject(it).getString("entity_type") in setOf("expense","expense_balance","cost_allocation","expense_categories")})
        rule.onNodeWithTag("finance-logout").performClick()
    }

    @Test fun queueWithBackendOffline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="queue")
        val f=fixture();login(f,"worker");val a=f.getJSONArray("contexts").getJSONObject(0)
        rule.onNodeWithTag("finance-gh").performClick()
        rule.onNodeWithTag("finance-gh-option-${f.getJSONArray("contexts").getJSONObject(1).getString("greenhouse_id")}").assertDoesNotExist()
        rule.onNodeWithTag("finance-gh-option-${a.getString("greenhouse_id")}").performClick()
        choose("finance-season",a.getString("season_id"));choose("finance-group",a.getString("group_id"))
        financialOfflineBarrier(f,"device")
        rule.onNodeWithTag("finance-new").performScrollTo().performClick()
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement("DEVICE OFFLINE ${f.getString("fixture_id")}")
        hideKeyboard();rule.onNodeWithTag("finance-review").performScrollTo().performClick();rule.onNodeWithTag("finance-queue").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-2").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-2").performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Menunggu pengiriman").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-0").performClick();rule.onNodeWithTag("harvest-new").performScrollTo().performClick()
        repeat(2) {index ->
            rule.onNodeWithTag("harvest-add-item").performScrollTo().performClick();choose("harvest-grade-$index",a.getJSONArray("grade_ids").getString(index))
            rule.onNodeWithTag("harvest-count-$index").performScrollTo().performTextReplacement(if(index==0) "3" else "0");hideKeyboard()
            rule.onNodeWithTag("harvest-weight-$index").performScrollTo().performTextReplacement(if(index==0) "4,5" else "0");hideKeyboard()
        }
        rule.onNodeWithTag("harvest-notes").performScrollTo().performTextReplacement("DEVICE HARVEST OFFLINE ${f.getString("fixture_id")}");hideKeyboard()
        rule.onNodeWithTag("harvest-review").performScrollTo().performClick();rule.onNodeWithTag("harvest-queue").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-3").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-tab-3").performClick()
        val runtime=runtime();val session=runtime.vault.current!!
        val op=runtime.store.outbox(session.scope.key).single {it.envelope.getString("entity_type")=="activity"}
        val harvest=runtime.store.outbox(session.scope.key).single {it.envelope.getString("entity_type")=="harvest"}
        assertEquals("QUEUED",op.state)
        assertEquals("QUEUED",harvest.state)
        file("finance-device-queued-proof.json").writeText(JSONObject().put("fixture_id",f.getString("fixture_id")).put("entity_id",op.entityId).put("operation_id",op.id).put("hash",op.hash).put("pid",android.os.Process.myPid())
            .put("harvest",JSONObject().put("entity_id",harvest.entityId).put("operation_id",harvest.id).put("hash",harvest.hash)).toString())
    }
    @Test fun reconnectReplayAndManagerReview():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="verify")
        val f=fixture();val proof=JSONObject(file("finance-device-queued-proof.json").readText())
        assertNotEquals(proof.getInt("pid"),android.os.Process.myPid())
        rule.waitUntil(15000) {rule.onAllNodesWithTag("finance-tab-2").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-2").performClick()
        rule.waitUntil(20000) {rule.onAllNodesWithText("Diterima server").fetchSemanticsNodes().isNotEmpty()}
        val runtime=runtime();val worker=runtime.vault.current!!
        rule.waitUntil(20000) {runtime.store.outbox(worker.scope.key).count {it.state=="ACCEPTED"}==2}
        val record=runtime.api.request(worker.scope.server,worker.token,"activities/${proof.getString("entity_id")}")
        assertEquals(f.getString("worker_id"),record.getString("recorded_by"));assertEquals(1,record.getInt("version"))
        try {runtime.api.request(worker.scope.server,worker.token,"greenhouses/${f.getJSONArray("contexts").getJSONObject(1).getString("greenhouse_id")}");fail("Cross GH must be denied")} catch(error:FinanceApiFailure) {assertEquals(404,error.status)}
        val op=runtime.store.outbox(worker.scope.key).single {it.entityId==proof.getString("entity_id")};assertEquals(proof.getString("operation_id"),op.id);assertEquals(proof.getString("hash"),op.hash)
        val replay=runtime.api.request(worker.scope.server,worker.token,"sync/operations","POST",op.envelope)
        assertEquals(op.id,replay.getString("operation_id"));assertEquals(1,replay.getInt("version"))
        assertEquals(1,runtime.api.request(worker.scope.server,worker.token,"activities/${op.entityId}/audit").getJSONArray("data").length())
        val harvestProof=proof.getJSONObject("harvest")
        val harvestOp=runtime.store.outbox(worker.scope.key).single {it.entityId==harvestProof.getString("entity_id")}
        assertEquals(harvestProof.getString("operation_id"),harvestOp.id);assertEquals(harvestProof.getString("hash"),harvestOp.hash)
        val harvest=runtime.api.request(worker.scope.server,worker.token,"harvests/${harvestOp.entityId}")
        assertEquals(1,harvest.getInt("version"));assertEquals(2,harvest.getJSONArray("items").length());assertEquals(f.getString("worker_id"),harvest.getString("recorded_by"))
        val firstGrade=(0 until 2).map {harvest.getJSONArray("items").getJSONObject(it)}.single {it.getString("grade_id")==f.getJSONArray("contexts").getJSONObject(0).getJSONArray("grade_ids").getString(0)}
        assertEquals(3,firstGrade.getInt("fruit_count"));assertEquals("4.500000",firstGrade.getString("weight_kg"))
        val harvestReplay=runtime.api.request(worker.scope.server,worker.token,"harvests","POST",harvestOp.envelope);assertEquals(1,harvestReplay.getInt("version"))
        rule.onNodeWithTag("finance-logout").performClick();login(f,"manager")
        rule.onNodeWithTag("finance-tab-2").performClick();rule.waitUntil(15000) {rule.onAllNodesWithText("DEVICE OFFLINE ${f.getString("fixture_id")}").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("DEVICE OFFLINE ${f.getString("fixture_id")}").performScrollTo().performClick()
        rule.onNodeWithText("Pencatat: Pekerja perangkat sintetis").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Kembali ke riwayat").performScrollTo().performClick();rule.onNodeWithTag("finance-tab-3").performClick()
        rule.waitUntil(15000) {rule.onAllNodesWithText("DEVICE HARVEST OFFLINE ${f.getString("fixture_id")}").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("DEVICE HARVEST OFFLINE ${f.getString("fixture_id")}").performScrollTo().performClick()
        rule.onNodeWithText("Pencatat: Pekerja perangkat sintetis").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("history-cancel").performScrollTo().performClick();rule.onNodeWithTag("history-action-reason").performTextReplacement("DEVICE HARVEST CANCEL ${f.getString("fixture_id")}");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(15000) {rule.onAllNodesWithText("Dibatalkan").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-logout").performClick();login(f,"worker")
        val currentWorker=runtime.vault.current!!
        val oldReceipt=runtime.api.request(currentWorker.scope.server,currentWorker.token,"harvests","POST",harvestOp.envelope)
        assertEquals(1,oldReceipt.getInt("version"));runtime.store.acknowledge(currentWorker.scope.key,harvestOp.id,oldReceipt)
        val after=runtime.store.record(currentWorker.scope.key,harvestOp.entityId)!!
        assertEquals(2L,after.serverVersion);assertEquals(2L,after.baseVersion);assertEquals("cancelled",after.body.getString("record_state"))
        rule.onNodeWithTag("finance-logout").performClick()
    }
}
