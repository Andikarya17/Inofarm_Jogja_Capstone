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
        // Inofarm 20 Nov 2026: before and after injection in one entry, PPM optional, AB Mix plus a typed extra nutrient.
        readyNode("entry-moment-option-before_injection").performScrollTo().performClick()
        readyNode("entry-moment-option-after_injection").performScrollTo().performClick()
        type("entry-ec", "1,6")
        type("entry-1-ec", "1,8")
        type("entry-1-ppm", "900")
        type("entry-ab-mix-quantity", "250")
        readyNode("entry-ab-mix-unit-option-gram").performScrollTo().performClick()
        readyNode("entry-nutrition-add").performScrollTo().performClick()
        type("entry-nutrition-name-0", "Kalsium")
        type("entry-nutrition-quantity-0", "0,5")
        readyNode("entry-nutrition-unit-0-option-gram").performScrollTo().performClick()
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
        rule.waitUntil(30_000) { entryOps().size == 6 && entryOps().all { it.state == "ACCEPTED" } }
        val activityIds = entryOps().map { it.entityId }
        val batch = entryOps().map { it.envelope.getJSONObject("payload").getString("entry_batch_id") }.toSet()
        assertEquals(1, batch.size)
        proof.put("entry_batch_id", batch.single()).put("activity_ids", org.json.JSONArray(activityIds))

        // Harvest for both varieties side by side: layak and cacat, no grade and no time (Inofarm, 20 Nov 2026).
        openCatat()
        readyNode("record-greenhouse-option-$ghA").performScrollTo().performClick()
        readyNode("record-harvest").performClick()
        rule.onNodeWithTag("harvest-time").assertDoesNotExist()
        rule.onNodeWithTag("harvest-grade-0").assertDoesNotExist()
        type("harvest-weight-0", "12,5")
        type("harvest-weight-1", "1,5")
        type("harvest-count-1", "2")
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
        // The inbox reloads (and empties for a moment) when a sync finishes, so scroll and click together until it lands.
        openFromList("activity-verification-list", "verification-activity-$head", "activity-verification-detail")
        val detail = rule.onNodeWithTag("activity-verification-detail")
        rule.waitUntil(20_000) { runCatching { detail.performScrollToNode(hasTestTag("verification-decision")) }.isSuccess }
        rule.onNodeWithText("seluruh isian (6 bagian)", substring = true).assertExists()
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
        rule.waitUntil(30_000) { decisions().size == 6 && decisions().all { it.state == "ACCEPTED" } }
        proof.put("manager_decisions", decisions().size)

        // Cek catatan panen: the two-variety harvest is two records, one decision each (one Sesuai, one Perlu diperbaiki).
        val harvestIds = harvestOps().map { it.entityId }
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("activity-verification-list").fetchSemanticsNodes().isNotEmpty() }
        readyNode("verification-kind-option-harvest").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("harvest-verification-list").fetchSemanticsNodes().isNotEmpty() }
        harvestIds.forEach { id -> rule.waitUntil(20_000) {
            runCatching { rule.onNodeWithTag("harvest-verification-list").performScrollToNode(hasTestTag("verification-harvest-$id")) }.isSuccess } }
        capture("rework-manager-harvest-inbox")
        val harvestReason = "Berat varietas kedua perlu ditimbang ulang."
        harvestIds.forEachIndexed { index, id ->
            openFromList("harvest-verification-list", "verification-harvest-$id", "harvest-verification-detail")
            decide("harvest-verification-detail", if (index == 0) "valid" else "invalid", if (index == 0) null else harvestReason)
            rule.waitUntil(30_000) { model.store.outbox(manager.scope.key).any { op -> op.envelope.optString("entity_type") == "harvest_review" &&
                op.envelope.getJSONObject("payload").optString("harvest_id") == id && op.state == "ACCEPTED" } }
            if (index == 0) {
                androidx.test.espresso.Espresso.pressBack()
                rule.waitUntil(20_000) { rule.onAllNodesWithTag("harvest-verification-list").fetchSemanticsNodes().isNotEmpty() }
            }
        }
        capture("rework-manager-harvest-decision")
        proof.put("harvest_ids", org.json.JSONArray(harvestIds))
        File(evidence(), "rework-device-proof.json").writeText(proof.toString(2))
        rule.logoutFromAccount()
    }

    /**
     * Correction per section of a combined entry, after [workerRecordsOneEntryPerGreenhouseAndManagerChecksItAsOneEntry] on the same
     * fixture: the worker corrects the measurement section (reason required, client and server), the manager the stage section
     * (no reason). `-e devicePhase rework-correction`.
     */
    @Test fun workerAndManagerCorrectOneSectionOfTheEntry() {
        val fixture = phase("rework-correction")
        val note = "Isian rombak ${fixture.getString("fixture_id")}"
        val model = runtime()

        login(fixture, "worker")
        val worker = model.vault.current!!
        val measurement = section(worker.scope.key, note, "measurement", "after_injection")
        assertEquals(1L, measurement.serverVersion)
        openHistoryRecord(measurement.id)
        readyNode("finance-edit").performScrollTo().performClick()
        type("finance-ppm", "950")
        readyNode("finance-review").performClick()
        // Without a reason the worker stays on the form with the reason error.
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Isi alasan koreksi.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("finance-queue").assertDoesNotExist()
        capture("rework-correction-worker-reason-required")
        // The server refuses it too: a worker update without reason.
        val queued = model.store.outbox(worker.scope.key).first { it.entityId == measurement.id && it.envelope.optString("action") == "create" }
        val bare = JSONObject(queued.envelope.toString()).put("operation_id", java.util.UUID.randomUUID().toString())
            .put("action", "update").put("base_version", 1)
            .put("payload", JSONObject(queued.envelope.getJSONObject("payload").toString()).put("manual_ppm", "950").put("reference_policy", "preserve").apply { remove("reason") })
        val refused = runCatching { runBlocking { model.api.request(worker.scope.server, worker.token, "sync/operations", "POST", bare) } }.exceptionOrNull()
        assertEquals(422, (refused as FinanceApiFailure).status)
        type("finance-reason", "PPM terbaca ulang ${fixture.getString("fixture_id").take(8)}")
        readyNode("finance-review").performClick()
        readyNode("finance-queue").performClick()
        rule.waitUntil(30_000) { model.store.outbox(worker.scope.key).any { it.entityId == measurement.id && it.envelope.optString("action") == "update" && it.state == "ACCEPTED" } }
        val corrected = read("activities/${measurement.id}")
        assertEquals(2, corrected.getInt("version"))
        assertEquals("950.000000", corrected.getString("manual_ppm"))
        assertTrue(corrected.isNull("planting_group_id"))
        assertEquals("pending", read("activities/${measurement.id}/verification").getString("status"))
        capture("rework-correction-worker-sent")

        login(fixture, "manager")
        val manager = model.vault.current!!
        val stage = section(manager.scope.key, note, "stage")
        openHistoryRecord(stage.id)
        readyNode("finance-edit").performScrollTo().performClick()
        type("finance-stage", "Panen")
        readyNode("finance-review").performClick()
        readyNode("finance-queue").assertExists()
        capture("rework-correction-manager-summary")
        readyNode("finance-queue").performClick()
        rule.waitUntil(30_000) { model.store.outbox(manager.scope.key).any { it.entityId == stage.id && it.envelope.optString("action") == "update" && it.state == "ACCEPTED" } }
        val stageNow = read("activities/${stage.id}")
        assertEquals(2, stageNow.getInt("version"))
        assertEquals("Panen", stageNow.getString("actual_stage"))
        // The other sections of the same entry stay at version 1.
        val untouched = model.store.records(manager.scope.key, 200).filter { it.body.optString("notes") == note && it.id !in setOf(measurement.id, stage.id) }
        assertEquals(4, untouched.size)
        untouched.forEach { assertEquals(1, read("activities/${it.id}").getInt("version")) }
        File(evidence(), "rework-correction-proof.json").writeText(JSONObject().put("measurement_id", measurement.id).put("stage_id", stage.id)
            .put("untouched", org.json.JSONArray(untouched.map { it.id })).put("worker_without_reason_status", 422).toString(2))
        rule.logoutFromAccount()
    }

    /**
     * Manager entry from Beranda after designating the running period: every greenhouse-level row carries running_period_version
     * and the server accepts it (fixed in #81). `-e devicePhase rework-manager-home`.
     */
    @Test fun managerEntryAfterDesignatingTheRunningPeriodCarriesItsVersion() {
        val fixture = phase("rework-manager-home")
        val a = fixture.getJSONArray("contexts").getJSONObject(0)
        val ghA = a.getString("greenhouse_id")
        val group = a.getString("group_id")
        val note = "Isian pengelola ${fixture.getString("fixture_id")}"
        val model = runtime()

        login(fixture, "manager")
        val home = rule.onNodeWithTag("finance-home-list")
        rule.waitUntil(20_000) { runCatching { home.performScrollToNode(hasTestTag("manager-greenhouse-$ghA")) }.isSuccess }
        rule.onNodeWithTag("manager-greenhouse-$ghA").performClick()
        rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasTestTag("manager-period-history")) }.isSuccess }
        rule.onNodeWithTag("manager-period-history").performClick()
        rule.waitUntil(15_000) { runCatching { rule.onNodeWithTag("manager-period-list").performScrollToNode(hasTestTag("manager-period-option-$group")) }.isSuccess }
        rule.onNodeWithTag("manager-period-option-$group").performClick()
        rule.onNodeWithTag("manager-period-reason").performTextReplacement("Penetapan rombak sintetis")
        hideKeyboard()
        rule.onNodeWithTag("manager-period-list").performScrollToNode(hasTestTag("manager-period-confirm"))
        capture("rework-manager-period")
        rule.onNodeWithTag("manager-period-confirm").performClick()
        val manager = model.vault.current!!
        var version = 0L
        rule.waitUntil(20_000) {
            val gh = runCatching { runBlocking { model.api.request(manager.scope.server, manager.token, "manager-home", query = mapOf("greenhouse_id" to ghA)) }
                .getJSONArray("greenhouses").getJSONObject(0) }.getOrNull()
            (gh?.optJSONObject("period")?.optString("group_id") == group).also { if (it) version = gh!!.getLong("version") }
        }
        // Catat waits until the designation reached the local cache, so the form takes the new version.
        rule.waitUntil(30_000) { model.store.cachedOne(manager.scope.key, "greenhouses", ghA)?.optLong("version") == version }
        openCatat()
        readyNode("record-greenhouse-option-$ghA").performScrollTo().performClick()
        readyNode("record-activity").performClick()
        readyNode("entry-age-basis-option-hst").performScrollTo().performClick()
        type("entry-age-day", "31")
        type("entry-ec", "2,1")
        type("entry-ppm", "1050")
        type("entry-stage", "Pembesaran buah")
        type("entry-notes", note)
        readyNode("finance-review").performClick()
        readyNode("finance-queue").assertExists()
        capture("rework-manager-entry-summary")
        readyNode("finance-queue").performClick()
        fun ops() = model.store.outbox(manager.scope.key).filter { it.envelope.optJSONObject("payload")?.optString("notes") == note }
        rule.waitUntil(30_000) { ops().size == 2 && ops().all { it.state == "ACCEPTED" } }
        ops().forEach { op ->
            val payload = op.envelope.getJSONObject("payload")
            assertEquals(version, payload.getLong("running_period_version"))
            assertTrue(payload.isNull("planting_group_id"))
        }
        File(evidence(), "rework-manager-home-proof.json").writeText(JSONObject().put("greenhouse_version", version)
            .put("activity_ids", org.json.JSONArray(ops().map { it.entityId })).toString(2))
        rule.logoutFromAccount()
    }

    /**
     * Offline queue, first half: the worker logs in, the host stops the backend once `rework-offline-ready.txt` appears, and the entry
     * is saved while the API is unreachable. `-e devicePhase rework-offline-queue`.
     */
    @Test fun workerQueuesAnEntryWhileTheBackendIsDown() {
        val fixture = phase("rework-offline-queue")
        val ghA = fixture.getJSONArray("contexts").getJSONObject(0).getString("greenhouse_id")
        val note = "Isian offline ${fixture.getString("fixture_id")}"
        val model = runtime()
        login(fixture, "worker")
        val worker = model.vault.current!!
        File(evidence(), "rework-offline-ready.txt").writeText(fixture.getString("fixture_id"))
        val deadline = System.nanoTime() + 120_000_000_000L
        var down = false
        while (System.nanoTime() < deadline && !down) {
            down = runCatching { runBlocking { model.api.request(worker.scope.server, worker.token, "sync/status") } }.exceptionOrNull() is java.io.IOException
            if (!down) Thread.sleep(1000)
        }
        assertTrue("the host must stop the backend before the offline entry", down)
        openCatat()
        readyNode("record-greenhouse-option-$ghA").performScrollTo().performClick()
        readyNode("record-activity").performClick()
        readyNode("entry-age-basis-option-hst").performScrollTo().performClick()
        type("entry-age-day", "32")
        type("entry-ec", "1,6")
        type("entry-ppm", "800")
        type("entry-stage", "Pembesaran buah")
        type("entry-notes", note)
        readyNode("finance-review").performClick()
        readyNode("finance-queue").performClick()
        fun ops() = model.store.outbox(worker.scope.key).filter { it.envelope.optJSONObject("payload")?.optString("notes") == note }
        rule.waitUntil(15_000) { ops().size == 2 }
        Thread.sleep(3000)
        assertTrue(ops().none { it.state == "ACCEPTED" })
        capture("rework-offline-queued")
        File(evidence(), "rework-offline-proof.json").writeText(JSONObject().put("pid", android.os.Process.myPid())
            .put("operations", org.json.JSONArray(ops().map { JSONObject().put("operation_id", it.id).put("entity_id", it.entityId).put("hash", it.hash).put("state", it.state) }))
            .toString(2))
    }

    /** Offline queue, second half, in a new process after the host restarted the backend. `-e devicePhase rework-offline-replay`. */
    @Test fun queuedEntryReplaysOnceAfterTheBackendReturns() {
        val fixture = phase("rework-offline-replay")
        val proof = JSONObject(File(evidence(), "rework-offline-proof.json").readText())
        assertNotEquals(proof.getInt("pid"), android.os.Process.myPid())
        val expected = proof.getJSONArray("operations").let { rows -> (0 until rows.length()).map { rows.getJSONObject(it) } }
        val model = runtime()
        rule.waitUntil(20_000) { model.vault.current != null }
        val worker = model.vault.current!!
        assertEquals(fixture.getString("worker_id"), worker.scope.accountId)
        rule.waitUntil(60_000) { expected.all { e -> model.store.outbox(worker.scope.key).any { it.id == e.getString("operation_id") && it.state == "ACCEPTED" } } }
        expected.forEach { e ->
            val op = model.store.outbox(worker.scope.key).single { it.id == e.getString("operation_id") }
            assertEquals(e.getString("entity_id"), op.entityId)
            assertEquals(e.getString("hash"), op.hash)
            // A second send of the same envelope returns the stored receipt instead of a new row.
            val replay = runBlocking { model.api.request(worker.scope.server, worker.token, "sync/operations", "POST", op.envelope) }
            assertEquals(op.id, replay.getString("operation_id"))
            assertEquals(1, replay.getInt("version"))
            assertEquals(1, read("activities/${op.entityId}/audit").getJSONArray("data").length())
        }
        capture("rework-offline-replayed")
        rule.logoutFromAccount()
    }

    private fun phase(name: String): JSONObject {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("devicePhase") == name)
        val fixture = JSONObject(android.util.Base64.decode(requireNotNull(args.getString("recordingFixture")),
            android.util.Base64.NO_WRAP).toString(Charsets.UTF_8))
        assertEquals("inofarm_test", fixture.getString("database"))
        assertTrue(context.packageName.endsWith(".finance"))
        return fixture
    }
    /** The one cached section of type [type] from the entry with [note], waiting for the pull after login. */
    private fun section(scope: String, note: String, type: String, moment: String? = null): com.inofarm.jogja.core.database.LocalRecord {
        val model = runtime()
        fun find() = model.store.records(scope, 200).filter { it.body.optString("notes") == note && it.body.optString("activity_type") == type &&
            (moment == null || it.body.optJSONObject("observations")?.optString("measurement_moment") == moment) }
        rule.waitUntil(30_000) { find().size == 1 }
        return find().single()
    }
    private fun openHistoryRecord(id: String) {
        readyNode("finance-tab-1").performClick()
        val tag = "finance-record-$id"
        rule.waitUntil(30_000) { runCatching { rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag(tag)) }.isSuccess }
        rule.onNodeWithTag(tag).performClick()
    }
    private fun openFromList(list: String, row: String, detail: String) {
        rule.waitUntil(30_000) {
            runCatching {
                if (rule.onAllNodesWithTag(detail).fetchSemanticsNodes().isEmpty()) {
                    rule.onNodeWithTag(list).performScrollToNode(hasTestTag(row))
                    rule.onNodeWithTag(row).performClick()
                    rule.waitForIdle()
                }
                rule.onAllNodesWithTag(detail).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }
    private fun decide(detailTag: String, choice: String, reason: String?) {
        val detail = rule.onNodeWithTag(detailTag)
        rule.waitUntil(20_000) { runCatching { detail.performScrollToNode(hasTestTag("verification-decision")) }.isSuccess }
        rule.onNodeWithTag("verification-decision").performClick()
        rule.onNodeWithTag("verification-decision-option-$choice").performClick()
        if (reason != null) {
            detail.performScrollToNode(hasTestTag("verification-reason"))
            rule.onNodeWithTag("verification-reason").performTextReplacement(reason)
            hideKeyboard()
        }
        detail.performScrollToNode(hasTestTag("verification-review"))
        rule.onNodeWithTag("verification-review").performClick()
        rule.onNodeWithTag("verification-save").performClick()
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
