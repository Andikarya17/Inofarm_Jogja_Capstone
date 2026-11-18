package com.inofarm.jogja

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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

/**
 * Recording rework (17–18 Nov 2026) against the real Laravel API on guarded MySQL inofarm_test, never a fixture server.
 * Run only with `-e devicePhase recording-rework -e recordingFixture <base64 manifest>` from
 * `scripts/finance-device-fixture.php create recording-rework`; the host then runs `verify-recording-rework` on the database.
 */
class RecordingReworkBackendDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun workerRecordsOneEntryPerGreenhouseAndManagerChecksItAsOneEntry() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("devicePhase") == "recording-rework")
        val fixture = JSONObject(android.util.Base64.decode(requireNotNull(args.getString("recordingFixture")),
            android.util.Base64.NO_WRAP).toString(Charsets.UTF_8))
        assertEquals("inofarm_test", fixture.getString("database"))
        assertTrue(context.packageName.endsWith(".finance"))
        val a = fixture.getJSONArray("contexts").getJSONObject(0)
        val b = fixture.getJSONArray("contexts").getJSONObject(1)
        val ghA = a.getString("greenhouse_id")
        val inactive = fixture.getString("inactive_greenhouse_id")
        val note = "Isian rombak ${fixture.getString("fixture_id")}"
        val harvestNote = "Panen rombak ${fixture.getString("fixture_id")}"
        val model = runtime()
        val proof = JSONObject()

        // Worker without any assignment: every active greenhouse, never the inactive one.
        login(fixture, "worker")
        val visible = read("greenhouses").getJSONArray("data").let { rows -> (0 until rows.length()).map { rows.getJSONObject(it).getString("id") } }
        assertTrue(visible.containsAll(listOf(ghA, b.getString("greenhouse_id"))))
        assertFalse(visible.contains(inactive))
        proof.put("worker_greenhouses", visible.size)

        // Catat asks for the greenhouse only.
        openCatat()
        readyNode("record-greenhouse-option-${b.getString("greenhouse_id")}").assertExists()
        rule.onNodeWithTag("record-greenhouse-option-$inactive").assertDoesNotExist()
        rule.onNodeWithTag("finance-season").assertDoesNotExist()
        rule.onNodeWithTag("finance-group").assertDoesNotExist()
        capture("rework-worker-catat")
        readyNode("record-greenhouse-option-$ghA").performScrollTo().performClick()
        readyNode("record-activity").performClick()

        // One "Catat kegiatan" form for the greenhouse: age, reading, nutrition, spray for both varieties, stage, notes.
        readyNode("entry-age-basis-option-hst").performScrollTo().performClick()
        type("entry-age-day", "30")
        type("entry-ec", "1,8")
        type("entry-ppm", "900")
        readyNode("entry-moment-option-after_injection").performScrollTo().performClick()
        readyNode("entry-nutrition-add").performScrollTo().performClick()
        choose("entry-nutrition-material-0", a.getString("nutrition_material_id"))
        type("entry-nutrition-quantity-0", "250")
        choose("entry-nutrition-unit-0", a.getString("unit_id"))
        readyNode("entry-spray-open").performScrollTo().performClick()
        readyNode("entry-spray-period-option-morning").performScrollTo().performClick()
        choose("entry-spray-material-0", fixture.getString("spray_material_id"))
        readyNode("entry-spray-add").performScrollTo().performClick()
        choose("entry-spray-material-1", "__typed")
        type("entry-spray-name-1", fixture.getString("typed_spray_name"))
        type("entry-stage", "Pembesaran buah")
        type("entry-notes", note)
        capture("rework-worker-entry-filled")
        readyNode("finance-review").performClick()
        readyNode("finance-queue").assertExists()
        capture("rework-worker-entry-review")
        readyNode("finance-queue").performClick()
        val worker = model.vault.current!!
        fun entryOps() = model.store.outbox(worker.scope.key).filter { it.envelope.optJSONObject("payload")?.optString("notes") == note }
        rule.waitUntil(30_000) { entryOps().size == 5 && entryOps().all { it.state == "ACCEPTED" } }
        val activityIds = entryOps().map { it.entityId }
        val batch = entryOps().map { it.envelope.getJSONObject("payload").getString("entry_batch_id") }.toSet()
        assertEquals(1, batch.size)
        proof.put("entry_batch_id", batch.single()).put("activity_ids", org.json.JSONArray(activityIds))

        // Harvest for both varieties side by side, fruit count left empty.
        openCatat()
        readyNode("record-greenhouse-option-$ghA").performScrollTo().performClick()
        readyNode("record-harvest").performClick()
        readyNode("harvest-add-item").performScrollTo().performClick()
        choose("harvest-grade-0", a.getJSONArray("grade_ids").getString(0))
        readyNode("harvest-quality-0-option-saleable").performScrollTo().performClick()
        type("harvest-weight-0", "12,5")
        readyNode("harvest-v2-add-item").performScrollTo().performClick()
        choose("harvest-v2-grade-0", a.getJSONArray("grade_ids").getString(0))
        readyNode("harvest-v2-quality-0-option-saleable").performScrollTo().performClick()
        type("harvest-v2-weight-0", "7")
        type("harvest-notes", harvestNote)
        capture("rework-worker-harvest-two")
        readyNode("harvest-review").performClick()
        readyNode("harvest-queue").performClick()
        fun harvestOps() = model.store.outbox(worker.scope.key).filter { it.envelope.optString("entity_type") == "harvest" }
        rule.waitUntil(30_000) { harvestOps().size == 2 && harvestOps().all { it.state == "ACCEPTED" } }
        assertTrue(harvestOps().all { op -> op.envelope.getJSONObject("payload").getJSONArray("items").getJSONObject(0).isNull("fruit_count") })

        // Finance stays closed to the worker: no Keuangan tab and the API refuses.
        rule.onNodeWithTag("finance-tab-2").assertDoesNotExist()
        val denied = runCatching { read("expenses") }.exceptionOrNull()
        assertEquals(403, (denied as FinanceApiFailure).status)
        proof.put("worker_expenses_status", denied.status)

        // Manager checks the whole entry as one card and one decision.
        login(fixture, "manager")
        rule.openReviewInbox()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("activity-verification-list").fetchSemanticsNodes().isNotEmpty() }
        val card = SemanticsMatcher("kartu isian rombak") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { tag -> activityIds.any { tag == "verification-activity-$it" } } == true
        }
        rule.waitUntil(20_000) { runCatching { rule.onNodeWithTag("activity-verification-list").performScrollToNode(card) }.isSuccess }
        val shown = rule.onAllNodes(card).fetchSemanticsNodes()
        assertEquals("one card for one entry", 1, shown.size)
        val head = shown.single().config[SemanticsProperties.TestTag].removePrefix("verification-activity-")
        rule.onNodeWithTag("verification-batch-$head").assertExists()
        capture("rework-manager-inbox")
        rule.onNodeWithTag("verification-activity-$head").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("activity-verification-detail").fetchSemanticsNodes().isNotEmpty() }
        val detail = rule.onNodeWithTag("activity-verification-detail")
        rule.waitUntil(20_000) { runCatching { detail.performScrollToNode(hasTestTag("verification-decision")) }.isSuccess }
        rule.onNodeWithText("seluruh isian (5 bagian)", substring = true).assertExists()
        rule.onNodeWithTag("verification-decision").performClick()
        rule.onNodeWithTag("verification-decision-option-valid").performClick()
        detail.performScrollToNode(hasTestTag("verification-review"))
        rule.onNodeWithTag("verification-review").performClick()
        capture("rework-manager-decision")
        rule.onNodeWithTag("verification-save").performClick()
        val manager = model.vault.current!!
        fun decisions() = model.store.outbox(manager.scope.key).filter {
            it.envelope.optString("entity_type") == "activity_review" && it.envelope.getJSONObject("payload").optString("activity_id") in activityIds
        }
        rule.waitUntil(30_000) { decisions().size == 5 && decisions().all { it.state == "ACCEPTED" } }
        proof.put("manager_decisions", decisions().size)
        File(evidence(), "rework-device-proof.json").writeText(proof.toString(2))
        rule.logoutFromAccount()
    }

    /** App-private evidence folder; the host reads it with `adb exec-out run-as <package> cat files/evidence/<name>`. */
    private fun evidence() = File(rule.activity.filesDir, "evidence").apply { mkdirs() }
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(evidence(), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun runtime(): FinanceRuntime {
        lateinit var runtime: FinanceRuntime
        rule.runOnIdle { runtime = ViewModelProvider(rule.activity)[FinanceRuntime::class.java] }
        return runtime
    }
    private fun read(path: String): JSONObject {
        val model = runtime(); val session = model.vault.current!!
        return runBlocking { model.api.request(session.scope.server, session.token, path) }
    }
    private fun login(f: JSONObject, role: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty()) rule.logoutFromAccount()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty() }
        rule.configureFinanceEndpoint(f.getString("base_url"))
        rule.onNodeWithTag("finance-email").performScrollTo().performTextReplacement(f.getString("${role}_email"))
        rule.onNodeWithTag("finance-password").performScrollTo().performTextReplacement(f.getString("password"))
        hideKeyboard(); rule.onNodeWithTag("finance-login").performScrollTo().performClick()
        val landing = if (role == "manager") "manager-add-greenhouse" else "worker-home-summary"
        rule.waitUntil(20_000) { rule.onAllNodesWithTag(landing).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForFinanceMasterPull()
    }
    private fun openCatat() {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("finance-record-fab").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("finance-record-fab").performClick()
    }
    private fun readyNode(tag: String): SemanticsNodeInteraction {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 }
        return rule.onNodeWithTag(tag)
    }
    /** Scrolls and types until the field takes the text; a long form can re-lay out under the IME between the two. */
    private fun type(tag: String, value: String) {
        rule.waitUntil(15_000) { runCatching { rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value) }.isSuccess }
        hideKeyboard()
    }
    private fun choose(tag: String, id: String) {
        readyNode(tag).performScrollTo().performClick()
        readyNode("$tag-option-$id").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("$tag-option-$id").fetchSemanticsNodes().let { nodes ->
            nodes.isEmpty() || nodes.all { it.config.getOrNull(SemanticsProperties.Selected) == true } } }
    }
    private fun hideKeyboard() {
        rule.runOnIdle { (rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(rule.activity.window.decorView.windowToken, 0) }
        rule.waitForIdle()
    }
}
