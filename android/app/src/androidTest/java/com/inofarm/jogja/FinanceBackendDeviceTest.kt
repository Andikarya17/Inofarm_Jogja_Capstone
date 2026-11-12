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
import java.util.UUID

/** Real Laravel/MySQL integration in explicitly selected isolated instrumentation processes. */
class FinanceBackendDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name:String)=File(context.getExternalFilesDir(null),name)
    private fun fixture():JSONObject { assertTrue(context.packageName.endsWith(".finance") || context.packageName.endsWith(".finance.performance"));return JSONObject(file("finance-device-fixture.json").readText()).also {assertEquals("inofarm_test",it.getString("database"))} }
    private fun login(f:JSONObject,role:String) {
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty()}
        if(rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty()) rule.logoutFromAccount()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        rule.configureFinanceEndpoint(f.getString("base_url"))
        rule.onNodeWithTag("finance-email").performScrollTo().performTextReplacement(f.getString("${role}_email"))
        rule.onNodeWithTag("finance-password").performScrollTo().performTextReplacement(f.getString("password"))
        hideKeyboard();rule.onNodeWithTag("finance-login").performScrollTo().performClick()
        val landing = if (role == "manager") "manager-add-greenhouse" else "finance-context-open"
        rule.waitUntil(15000) {rule.onAllNodesWithTag(landing).fetchSemanticsNodes().isNotEmpty()}
        rule.waitForFinanceMasterPull()
    }

    @Test fun workerProgressMotionFixtureSession() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("devicePhase")
        assumeTrue(phase in setOf("worker-motion", "worker-progress-offline", "worker-motion-logout"))
        val fixture = JSONObject(android.util.Base64.decode(requireNotNull(args.getString("managerHomeFixture")),
            android.util.Base64.NO_WRAP).toString(Charsets.UTF_8))
        assertEquals("inofarm_test", fixture.getString("database"))
        if (phase == "worker-motion") {
            login(fixture, "worker")
            rule.captureBotanicalWindow("worker-picker-after")
            return
        }
        val session = runtime().vault.current
        assertEquals(fixture.getString("worker_id"), session?.scope?.accountId)
        if (phase == "worker-motion-logout") {
            rule.logoutFromAccount()
            return
        }
        val greenhouse = fixture.getJSONArray("contexts").getJSONObject(0).getString("greenhouse_id")
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("finance-home-list").fetchSemanticsNodes().isNotEmpty() }
        val home = rule.onNodeWithTag("finance-home-list")
        home.performScrollToNode(hasTestTag("worker-greenhouse-$greenhouse"))
        rule.onNodeWithTag("worker-greenhouse-$greenhouse").performClick()
        home.performScrollToNode(hasTestTag("worker-harvest-progress-open"))
        rule.onNodeWithTag("worker-harvest-progress-open").performClick()
        rule.waitUntil(25_000) {
            rule.onAllNodesWithText("Data tersimpan ·", substring = true).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("Hasil layak tercatat").fetchSemanticsNodes().isNotEmpty()
        }
        rule.captureBotanicalWindow("worker-harvest-progress-offline")
    }

    @Test fun workerHarvestReviewRoundTripThroughGuardedBackend() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("devicePhase") == "worker-harvest-review")
        val fixture = JSONObject(android.util.Base64.decode(requireNotNull(args.getString("managerHomeFixture")),
            android.util.Base64.NO_WRAP).toString(Charsets.UTF_8))
        assertEquals("inofarm_test", fixture.getString("database"))
        val first = fixture.getJSONArray("contexts").getJSONObject(0)
        val greenhouse = first.getString("greenhouse_id")
        val note = "Panen uji perangkat ${fixture.getString("fixture_id")}"
        val reason = "Periksa bobot grade kedua."
        val model = runtime()
        fun waitTag(tag: String) = rule.waitUntil(20_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        fun read(path: String): JSONObject {
            val session = model.vault.current!!
            return runBlocking { model.api.request(session.scope.server, session.token, path) }
        }
        fun submitHarvest() {
            rule.onNodeWithTag("harvest-review").scrollIfScrollable().performClick()
            rule.onNodeWithTag("harvest-queue").scrollIfScrollable().performClick()
        }
        login(fixture, "worker")
        waitTag("worker-greenhouse-$greenhouse")
        rule.captureBotanicalWindow("worker-harvest-real-picker")
        rule.onNodeWithTag("worker-greenhouse-$greenhouse").performClick()
        rule.captureBotanicalWindow("worker-harvest-real-home")
        rule.openRecord("harvest")
        waitTag("harvest-pick-date")
        rule.captureBotanicalWindow("worker-harvest-real-form")
        repeat(2) { index ->
            rule.onNodeWithTag("harvest-add-item").performScrollTo().performClick()
            choose("harvest-grade-$index", first.getJSONArray("grade_ids").getString(index))
            choose("harvest-quality-$index", if (index == 0) "saleable" else "defective")
            rule.onNodeWithTag("harvest-count-$index").performScrollTo().performTextReplacement(if (index == 0) "6" else "2")
            rule.onNodeWithTag("harvest-weight-$index").performScrollTo().performTextReplacement(if (index == 0) "9,25" else "3")
            hideKeyboard()
        }
        rule.onNodeWithTag("harvest-notes").performScrollTo().performTextReplacement(note)
        hideKeyboard()
        submitHarvest()
        val worker = model.vault.current!!
        fun originalHarvest() = model.store.outbox(worker.scope.key).firstOrNull {
            it.envelope.optJSONObject("payload")?.optString("notes") == note && it.envelope.optString("action") == "create"
        }
        rule.waitUntil(20_000) { originalHarvest()?.state == "ACCEPTED" }
        val harvest = originalHarvest()!!.entityId
        val path = "harvests/$harvest/verification"
        assertEquals("pending", read(path).getString("status"))
        assertEquals(403, (runCatching { read("harvest-reviews") }.exceptionOrNull() as FinanceApiFailure).status)

        fun decide(version: Int, decision: String, message: String?) {
            rule.selectFinanceTab(1)
            waitTag("activity-verification-list")
            choose("verification-kind", "harvest")
            val inbox = rule.onNodeWithTag("harvest-verification-list")
            val entry = "verification-harvest-$harvest"
            rule.waitUntil(20_000) { runCatching { inbox.performScrollToNode(hasTestTag(entry)) }.isSuccess }
            rule.onNodeWithTag(entry).performClick()
            val detail = rule.onNodeWithTag("harvest-verification-detail")
            rule.waitUntil(20_000) { runCatching { detail.performScrollToNode(hasTestTag("verification-decision")) }.isSuccess }
            choose("verification-decision", decision)
            if (message != null) {
                detail.performScrollToNode(hasTestTag("verification-reason"))
                rule.onNodeWithTag("verification-reason").performTextReplacement(message)
                hideKeyboard()
            }
            detail.performScrollToNode(hasTestTag("verification-review"))
            rule.onNodeWithTag("verification-review").performClick()
            rule.captureBotanicalWindow("worker-harvest-real-manager-${decision}")
            rule.onNodeWithTag("verification-submit").performClick()
            val manager = model.vault.current!!
            fun operation() = model.store.outbox(manager.scope.key).firstOrNull {
                val payload = it.envelope.optJSONObject("payload")
                it.envelope.optString("entity_type") == "harvest_review" &&
                    payload?.optString("harvest_id") == harvest && payload.optInt("harvest_version") == version
            }
            rule.waitUntil(20_000) { operation()?.state == "ACCEPTED" }
            val sent = operation()!!
            val replay = runBlocking { model.api.request(manager.scope.server, manager.token, "harvest-reviews", "POST", sent.envelope) }
            assertEquals(sent.id, replay.getString("operation_id"))
        }

        login(fixture, "manager")
        decide(1, "invalid", reason)
        login(fixture, "worker")
        rule.openWorkerHarvestHistory()
        val row = "finance-record-$harvest"
        rule.waitUntil(20_000) {
            runCatching { rule.onNodeWithTag("finance-history-list").performScrollToNode(hasTestTag(row)) }.isSuccess
        }
        rule.onNodeWithTag(row).performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText(reason, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(reason, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.captureBotanicalWindow("worker-harvest-real-feedback")
        val acceptedItems = read("harvests/$harvest").getJSONArray("items")
        val correctedGrade = first.getJSONArray("grade_ids").getString(1)
        val correctedIndex = (0 until acceptedItems.length()).single { acceptedItems.getJSONObject(it).getString("grade_id") == correctedGrade }
        rule.onNodeWithTag("finance-edit").performScrollTo().performClick()
        rule.onNodeWithTag("harvest-weight-$correctedIndex").performScrollTo().performTextReplacement("3,25")
        rule.onNodeWithTag("harvest-reason").performScrollTo().performTextReplacement("Bobot grade kedua ditimbang ulang.")
        hideKeyboard()
        submitHarvest()
        rule.waitUntil(20_000) { model.store.record(worker.scope.key, harvest)?.serverVersion == 2L }
        assertEquals("pending", read(path).getString("status"))
        login(fixture, "manager")
        decide(2, "valid", null)
        val verified = read(path)
        assertEquals("valid", verified.getString("status"))
        assertEquals(2, verified.getJSONArray("history").length())
        assertEquals(reason, verified.getJSONArray("history").getJSONObject(1).getString("reason"))
        File(botanicalEvidenceDirectory(context), "worker-harvest-reconciliation.json").writeText(verified.toString(2))
        if (args.getString("checkWorkerProgress") == "1") {
            login(fixture, "worker")
            val group = first.getString("group_id")
            rule.openWorkerHarvestHistory()
            rule.onNodeWithTag("finance-history-list").performScrollToNode(hasTestTag("worker-harvest-progress-open"))
            rule.onNodeWithTag("worker-harvest-progress-open").performClick()
            waitTag("worker-harvest-progress")
            choose("worker-progress-gh", greenhouse)
            rule.waitUntil(20_000) { rule.onAllNodesWithText("120% target buah · target terlampaui").fetchSemanticsNodes().isNotEmpty() }
            rule.captureBotanicalWindow("worker-harvest-real-progress")
            val progress = read("planting-groups/$group/harvest-progress")
            assertEquals("9.250000", progress.getJSONObject("verification").getJSONObject("valid")
                .getJSONObject("saleable").getString("weight_kg"))
            assertEquals("3.250000", progress.getJSONObject("totals").getJSONObject("defective").getString("weight_kg"))
            assertFalse(progress.has("completion"))
            rule.onNodeWithTag("worker-harvest-progress").performScrollToNode(hasTestTag("worker-progress-sources"))
            rule.onNodeWithTag("worker-progress-sources").performClick()
            val sourceTag = "worker-progress-source-$harvest"
            rule.waitUntil(20_000) {
                runCatching { rule.onNodeWithTag("worker-harvest-progress").performScrollToNode(hasTestTag(sourceTag)) }.isSuccess
            }
            rule.onNodeWithTag(sourceTag).performClick()
            rule.waitUntil(20_000) { rule.onAllNodesWithText("Detail panen", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
            File(botanicalEvidenceDirectory(context), "worker-progress-reconciliation.json").writeText(progress.toString(2))
        }
        rule.logoutFromAccount()
    }

    @Test fun managerHomeReadsGuardedMysqlAndDesignatesFromNativeReview() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase") == "manager-home")
        val encoded = requireNotNull(InstrumentationRegistry.getArguments().getString("managerHomeFixture"))
        val fixture = JSONObject(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP).toString(Charsets.UTF_8))
        assertEquals("inofarm_test", fixture.getString("database"))
        val first = fixture.getJSONArray("contexts").getJSONObject(0)
        val gh = first.getString("greenhouse_id")
        val group = first.getString("group_id")
        val model = runtime()
        val now = java.time.OffsetDateTime.now(com.inofarm.jogja.feature.activities.domain.ActivityDraft.WIB).withNano(0)
        val eventAt = now.format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx"))
        fun create(kind: String, route: String, payload: JSONObject): String {
            val session = model.vault.current!!
            val id = UUID.randomUUID().toString()
            val operation = JSONObject().put("schema_version", "2").put("sync_epoch", model.store.epoch(session.scope.key))
                .put("operation_id", UUID.randomUUID().toString()).put("entity_id", id).put("entity_type", kind)
                .put("action", "create").put("base_version", JSONObject.NULL).put("payload", payload)
            val accepted = runBlocking { model.api.request(session.scope.server, session.token, route, "POST", operation) }
            assertEquals(id, accepted.getString("entity_id"))
            return id
        }
        login(fixture, "worker")
        val worker = model.vault.current!!
        val denied = runCatching { runBlocking { model.api.request(worker.scope.server, worker.token, "manager-home") } }.exceptionOrNull()
        assertEquals(403, (denied as FinanceApiFailure).status)
        rule.captureBotanicalWindow("real-worker")
        val measurement = create("activity", "activities", JSONObject().put("planting_group_id", group).put("activity_type", "measurement")
            .put("occurred_at", eventAt).put("manual_ec", "1.25").put("manual_ppm", "625.50").put("work_state", "completed")
            .put("materials", org.json.JSONArray()).put("notes", "MANAGER HOME ${fixture.getString("fixture_id")}"))
        login(fixture, "manager")
        val expense = create("expense", "expenses", JSONObject().put("greenhouse_id", gh).put("category_id", first.getString("category_id"))
            .put("amount", "100.01").put("paid_at", eventAt).put("payment_kind", "asset_acquisition").put("notes", "MANAGER HOME ${fixture.getString("fixture_id")}"))
        rule.onNodeWithTag("finance-sync").performClick()
        val home = rule.onNodeWithTag("finance-home-list")
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("manager-greenhouse-$gh").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Ringkasan kebun").assertIsDisplayed()
        rule.captureBotanicalWindow("real-overview")
        rule.onNodeWithTag("manager-greenhouse-$gh").performClick()
        rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasTestTag("manager-period-history")) }.isSuccess }
        rule.onNodeWithTag("manager-period-history").performClick()
        rule.onNodeWithTag("manager-period-list").performScrollToNode(hasTestTag("manager-period-option-$group"))
        rule.onNodeWithTag("manager-period-option-$group").performClick()
        rule.onNodeWithTag("manager-period-reason").performTextReplacement("Penetapan sintetis dari Android")
        androidx.test.espresso.Espresso.pressBack()
        rule.captureBotanicalWindow("real-period-review")
        rule.onNodeWithTag("manager-period-list").performScrollToNode(hasTestTag("manager-period-confirm"))
        rule.onNodeWithTag("manager-period-confirm").performClick()
        rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasTestTag("measurement-card-ec")) }.isSuccess }
        rule.onNodeWithText("1.25").assertExists()
        rule.onNodeWithText("625.5").assertExists()
        rule.captureBotanicalWindow("real-single")
        home.performScrollToNode(hasTestTag("manager-home-finance"))
        rule.onNodeWithText("100,01", substring = true).assertExists()
        rule.captureBotanicalWindow("real-cash")
        val session = model.vault.current!!
        val snapshot = runBlocking { model.api.request(session.scope.server, session.token, "manager-home", query = mapOf("greenhouse_id" to gh)) }
        assertEquals(measurement, snapshot.getJSONObject("detail").getJSONObject("measurement").getString("id"))
        assertEquals("100.01", snapshot.getJSONObject("cash").getJSONObject("payments").getString("amount"))
        assertEquals(group, snapshot.getJSONArray("greenhouses").getJSONObject(0).getJSONObject("period").getString("group_id"))
        home.performScrollToNode(hasText("Catatan terbaru"))
        rule.onNodeWithText("Pengukuran manual").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Detail kegiatan").fetchSemanticsNodes().isNotEmpty() }
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithTag("finance-home-list").assertIsDisplayed()
        File(botanicalEvidenceDirectory(context), "real-snapshot.json").writeText(snapshot.toString(2))
        File(botanicalEvidenceDirectory(context), "real-reconciliation.json").writeText(
            JSONObject().put("greenhouse", gh).put("group", group).put("measurement", measurement)
                .put("expense", expense).put("worker_http_status", 403).put("payments", "100.01").toString(2))
    }
    @Test fun managerVerifiesWorkerRevisionsThroughGuardedBackend() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase") == "activity-verification")
        val encoded = requireNotNull(InstrumentationRegistry.getArguments().getString("managerHomeFixture"))
        val fixture = JSONObject(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP).toString(Charsets.UTF_8))
        assertEquals("inofarm_test", fixture.getString("database"))
        val first = fixture.getJSONArray("contexts").getJSONObject(0)
        val model = runtime()
        val note = "Verifikasi sintetis ${UUID.randomUUID()}"
        fun waitFor(tag: String) = rule.waitUntil(20_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        fun read(path: String): JSONObject {
            val session = model.vault.current!!
            return runBlocking { model.api.request(session.scope.server, session.token, path) }
        }

        login(fixture, "worker")
        choose("finance-gh", first.getString("greenhouse_id"))
        choose("finance-season", first.getString("season_id"))
        choose("finance-group", first.getString("group_id"))
        rule.openRecord("measurement")
        rule.onNodeWithTag("finance-ec").performScrollTo().performTextReplacement("1,25")
        rule.onNodeWithTag("finance-ppm").performScrollTo().performTextReplacement("625,5")
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement(note)
        hideKeyboard()
        rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick()
        rule.onNodeWithTag("finance-queue").scrollIfScrollable().performClick()
        val worker = model.vault.current!!
        fun workerOperation() = model.store.outbox(worker.scope.key).firstOrNull {
            it.envelope.optJSONObject("payload")?.optString("notes") == note
        }
        rule.waitUntil(20_000) { workerOperation()?.state == "ACCEPTED" }
        val original = workerOperation()!!
        val activity = original.entityId

        fun openReview() {
            rule.selectFinanceTab(1)
            waitFor("activity-verification-list")
            val tag = "verification-activity-$activity"
            rule.waitUntil(20_000) {
                runCatching { rule.onNodeWithTag("activity-verification-list").performScrollToNode(hasTestTag(tag)) }.isSuccess
            }
            rule.onNodeWithTag(tag).performClick()
            waitFor("activity-verification-detail")
        }

        fun decide(choice: String, reason: String? = null) {
            val detail = rule.onNodeWithTag("activity-verification-detail")
            rule.waitUntil(20_000) {
                runCatching { detail.performScrollToNode(hasTestTag("verification-decision")) }.isSuccess
            }
            rule.onNodeWithTag("verification-decision").performClick()
            rule.onNodeWithTag("verification-decision-option-$choice").performClick()
            if (reason != null) {
                detail.performScrollToNode(hasTestTag("verification-reason"))
                rule.onNodeWithTag("verification-reason").performTextReplacement(reason)
                hideKeyboard()
            }
            detail.performScrollToNode(hasTestTag("verification-review"))
            rule.onNodeWithTag("verification-review").performClick()
            rule.onNodeWithTag("verification-submit").performClick()
        }

        login(fixture, "manager")
        openReview()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Diterima server · revisi 1").fetchSemanticsNodes().isNotEmpty() }
        rule.captureBotanicalWindow("pg1-manager-review")
        val reason = "Periksa satuan pada versi pertama."
        decide("invalid", reason)
        val manager = model.vault.current!!
        rule.waitUntil(20_000) { model.store.outbox(manager.scope.key).any {
            it.envelope.optString("entity_type") == "activity_review" && it.state == "ACCEPTED" &&
                it.envelope.getJSONObject("payload").optString("activity_id") == activity
        } }
        val decisionOp = model.store.outbox(manager.scope.key).first {
            it.envelope.optString("entity_type") == "activity_review" &&
                it.envelope.getJSONObject("payload").optString("activity_id") == activity
        }
        val replay = runBlocking { model.api.request(manager.scope.server, manager.token, "activity-reviews", "POST", decisionOp.envelope) }
        assertEquals(decisionOp.id, replay.getString("operation_id"))
        rule.captureBotanicalWindow("pg1-manager-decision")
        androidx.test.espresso.Espresso.pressBack()
        waitFor("activity-verification-list")
        rule.onNodeWithTag("activity-verification-list").performScrollToNode(hasTestTag("verification-outbox"))
        rule.onNodeWithTag("verification-outbox").performClick()
        rule.onNodeWithTag("finance-queue-list").assertIsDisplayed()

        login(fixture, "worker")
        val currentWorker = model.vault.current!!
        val path = "activities/$activity/verification"
        assertEquals(reason, read(path).getJSONObject("decision").getString("reason"))
        rule.selectFinanceTab(1)
        rule.waitUntil(20_000) { rule.onAllNodesWithText(note).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(note).performClick()
        val workerFeedback = rule.onNodeWithText(reason, useUnmergedTree = true)
        rule.waitUntil(20_000) { rule.onAllNodesWithText(reason, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        workerFeedback.performScrollTo().assertIsDisplayed()
        rule.captureBotanicalWindow("pg1-worker-feedback")
        val payload = JSONObject(original.envelope.getJSONObject("payload").toString()).put("manual_ppm", "630")
            .put("reason", "Koreksi uji").put("reference_policy", "preserve")
        val correction = JSONObject(original.envelope.toString()).put("operation_id", UUID.randomUUID().toString())
            .put("action", "update").put("base_version", 1).put("payload", payload)
        val updated = runBlocking { model.api.request(currentWorker.scope.server, currentWorker.token, "sync/operations", "POST", correction) }
        assertEquals(2, updated.getInt("version"))
        assertEquals("pending", read(path).getString("status"))

        login(fixture, "manager")
        openReview()
        decide("valid")
        rule.waitUntil(20_000) { model.store.outbox(model.vault.current!!.scope.key).any {
            it.envelope.optString("entity_type") == "activity_review" && it.state == "ACCEPTED" &&
                it.envelope.getJSONObject("payload").optString("activity_id") == activity &&
                it.envelope.getJSONObject("payload").optLong("activity_version") == 2L
        } }
        val checked = read(path)
        assertEquals("valid", checked.getString("status"))
        assertEquals(2, checked.getJSONArray("history").length())
        assertEquals("invalid", checked.getJSONArray("history").getJSONObject(1).getString("decision"))
        val greenhouse = first.getString("greenhouse_id")
        val group = first.getString("group_id")
        val currentManager = model.vault.current!!
        val homeSnapshot = runBlocking { model.api.request(currentManager.scope.server, currentManager.token, "manager-home",
            query = mapOf("greenhouse_id" to greenhouse)) }
        val greenhouseBody = homeSnapshot.getJSONArray("greenhouses").getJSONObject(0)
        if (greenhouseBody.getJSONObject("period").optString("group_id") != group) {
            val designation = JSONObject().put("schema_version", "2").put("sync_epoch", model.store.epoch(currentManager.scope.key))
                .put("operation_id", UUID.randomUUID().toString()).put("entity_id", greenhouse).put("entity_type", "greenhouses")
                .put("action", "designate_period").put("base_version", greenhouseBody.getInt("version"))
                .put("payload", JSONObject().put("planting_group_id", group).put("reason", "Uji sumber Beranda"))
            runBlocking { model.api.request(currentManager.scope.server, currentManager.token, "greenhouses/$greenhouse/running-period", "POST", designation) }
        }
        androidx.test.espresso.Espresso.pressBack()
        rule.selectFinanceTab(0)
        val home = rule.onNodeWithTag("finance-home-list")
        rule.waitUntil(20_000) { runCatching { home.performScrollToNode(hasTestTag("manager-greenhouse-$greenhouse")) }.isSuccess }
        rule.onNodeWithTag("manager-greenhouse-$greenhouse").performClick()
        rule.waitUntil(20_000) { runCatching { home.performScrollToNode(hasTestTag("measurement-card-ppm")) }.isSuccess }
        rule.onNodeWithText("630").assertExists()
        rule.onNodeWithText("Valid").assertExists()
        rule.captureBotanicalWindow("pg7-manager-home-reviewed")
        File(botanicalEvidenceDirectory(context), "pg1-reconciliation.json").writeText(checked.toString(2))
        rule.logoutFromAccount()
    }

    @Test fun managerReadsPartialHarvestAndCompletesOnlyHarvest() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase") == "harvest-progress")
        val encoded = requireNotNull(InstrumentationRegistry.getArguments().getString("managerHomeFixture"))
        val fixture = JSONObject(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP).toString(Charsets.UTF_8))
        assertEquals("inofarm_test", fixture.getString("database"))
        val first = fixture.getJSONArray("contexts").getJSONObject(0)
        val greenhouse = first.getString("greenhouse_id")
        val group = first.getString("group_id")
        val model = runtime()
        login(fixture, "manager")
        val manager = model.vault.current!!
        val path = "planting-groups/$group"
        val master = runBlocking { model.api.request(manager.scope.server, manager.token, path) }
        val payload = JSONObject().put("name", master.getString("name")).put("variety_id", master.getString("variety_id"))
            .put("population", 171).put("sowing_date", master.getString("sowing_date"))
            .put("transplant_date", master.opt("transplant_date") ?: JSONObject.NULL).put("active", true).put("reason", "Uji rekap")
            .put("harvest_target", JSONObject().put("fruit_count", 160).put("basis", "Estimasi petikan uji"))
        val operation = JSONObject().put("schema_version", "2").put("sync_epoch", model.store.epoch(manager.scope.key))
            .put("operation_id", UUID.randomUUID().toString()).put("entity_id", group).put("entity_type", "planting_groups")
            .put("action", "update").put("base_version", master.getLong("version")).put("payload", payload)
        runBlocking { model.api.request(manager.scope.server, manager.token, path, "PATCH", operation) }

        login(fixture, "worker")
        choose("finance-gh", greenhouse)
        choose("finance-season", first.getString("season_id"))
        choose("finance-group", group)
        val worker = model.vault.current!!
        val testNote = "Panen bertahap ${UUID.randomUUID()}"
        listOf("100" to "61,25", "50" to "29,5").forEachIndexed { index, (count, weight) ->
            rule.selectFinanceTab(0)
            rule.openRecord("harvest")
            rule.onNodeWithTag("harvest-add-item").performScrollTo().performClick()
            choose("harvest-grade-0", first.getJSONArray("grade_ids").getString(0))
            choose("harvest-quality-0", "saleable")
            rule.onNodeWithTag("harvest-count-0").performScrollTo().performTextReplacement(count)
            rule.onNodeWithTag("harvest-weight-0").performScrollTo().performTextReplacement(weight)
            val note = "$testNote ${index + 1}"
            rule.onNodeWithTag("harvest-notes").performScrollTo().performTextReplacement(note)
            hideKeyboard()
            rule.onNodeWithTag("harvest-review").scrollIfScrollable().performClick()
            rule.onNodeWithTag("harvest-queue").scrollIfScrollable().performClick()
            rule.waitUntil(20_000) { model.store.outbox(worker.scope.key).any {
                it.state == "ACCEPTED" && it.envelope.optJSONObject("payload")?.optString("notes") == note
            } }
        }

        login(fixture, "manager")
        rule.openWorkerHarvestHistory()
        rule.onNodeWithTag("finance-history-list").performScrollToNode(hasTestTag("harvest-progress-open"))
        rule.onNodeWithTag("harvest-progress-open").performClick()
        rule.onNodeWithTag("harvest-progress-gh").performClick()
        rule.onNodeWithTag("harvest-progress-gh-option-$greenhouse").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("93,75%").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("93,75%").performScrollTo().assertIsDisplayed()
        rule.captureBotanicalWindow("pg4-harvest-progress")
        rule.onNodeWithTag("harvest-progress-transition").performScrollTo().performClick()
        rule.onNodeWithTag("history-action-reason").performTextReplacement("Dua petikan sudah tercatat.")
        hideKeyboard()
        rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Panen selesai").fetchSemanticsNodes().isNotEmpty() }
        val session = model.vault.current!!
        val checked = runBlocking { model.api.request(session.scope.server, session.token, "$path/harvest-progress") }
        assertEquals("open", checked.getString("season_status"))
        assertEquals(2, checked.getInt("records"))
        assertEquals(10, checked.getInt("difference_fruit"))
        assertEquals("93.750000", checked.getString("achievement_percent"))
        assertEquals("Dua petikan sudah tercatat.", checked.getJSONObject("completion").getString("reason"))
        File(botanicalEvidenceDirectory(context), "pg4-reconciliation.json").writeText(checked.toString(2))
        rule.logoutFromAccount()
    }

    private fun choose(tag:String,id:String) {
        rule.prepareChoice(tag)
        rule.waitUntil(10000) {rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size==1}
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("$tag-option-$id").fetchSemanticsNodes().size==1}
        rule.onNodeWithTag("$tag-option-$id").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("$tag-option-$id").fetchSemanticsNodes().let {nodes -> nodes.isEmpty() || nodes.all {node -> (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected) && node.config[androidx.compose.ui.semantics.SemanticsProperties.Selected])}}}
        rule.finishChoice(tag)
    }
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
    private fun privateTestFiles():List<Pair<String,ByteArray>> {
        val png=android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j0ioAAAAASUVORK5CYII=",android.util.Base64.DEFAULT)
        val pdf=buildString {
            append("%PDF-1.4\n%");append("x".repeat(20000));append('\n')
            val offsets=mutableListOf<Int>()
            listOf("<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>").forEachIndexed { index,body -> offsets+=length;append("${index+1} 0 obj\n$body\nendobj\n") }
            val xref=length;append("xref\n0 4\n0000000000 65535 f \n");offsets.forEach {append(it.toString().padStart(10,'0'));append(" 00000 n \n")};append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }.toByteArray(Charsets.US_ASCII)
        assertTrue(pdf.size>16384);return listOf("png" to png,"pdf" to pdf)
    }
    private suspend fun verifyPrivateAttachments(proof:JSONObject):List<String> {
        val model=runtime();val session=model.vault.current!!;val saved=proof.getJSONArray("attachment_operations")
        val ids=(0 until saved.length()).map {saved.getJSONObject(it).getString("entity_id")}
        rule.waitUntil(20000) {ids.all {model.store.record(session.scope.key,it)?.syncState==com.inofarm.jogja.core.contracts.SyncState.ACCEPTED}}
        ids.forEachIndexed {index,id ->
            val stored=saved.getJSONObject(index);val op=model.store.outbox(session.scope.key).single {it.id==stored.getString("operation_id")}
            assertEquals(stored.getString("hash"),op.hash);assertEquals(stored.getString("blob_id"),op.blobId)
            val expected=privateTestFiles()[index].second
            val replay=model.api.upload(session.scope.server,session.token,op.envelope) {model.files.open(session.scope.key,op.blobId!!)}
            assertEquals(id,replay.getJSONObject("data").getString("id"));assertEquals(1,replay.getInt("version"));assertFalse(replay.getJSONObject("data").has("path"));assertFalse(replay.getJSONObject("data").has("url"))
            val second=model.api.upload(session.scope.server,session.token,op.envelope) {model.files.open(session.scope.key,op.blobId!!)}
            assertEquals(com.inofarm.jogja.core.contracts.WireContract.hash(replay),com.inofarm.jogja.core.contracts.WireContract.hash(second))
            var downloaded:ByteArray?=null
            model.api.download(session.scope.server,session.token,id,expected.size.toLong()) {downloaded=it.readBytes()};assertArrayEquals(expected,downloaded)
            val local=model.attachments.download(session,model.vault.generation,model.store.record(session.scope.key,id)!!)
            assertNotEquals(op.blobId,local.id);assertArrayEquals(expected,model.files.open(session.scope.key,local.id).use {it.readBytes()})
            assertEquals(op.blobId,model.store.outbox(session.scope.key).single {it.id==op.id}.blobId)
            assertEquals(1,model.api.request(session.scope.server,session.token,"attachments/$id/audit").getJSONArray("data").length())
            if(index==1) {
                val third=JSONObject(op.envelope.toString()).put("operation_id",UUID.randomUUID().toString()).put("entity_id",UUID.randomUUID().toString())
                try {model.api.upload(session.scope.server,session.token,third) {model.files.open(session.scope.key,op.blobId!!)};fail("Configured file cap must hold")} catch(error:FinanceApiFailure) {assertEquals(409,error.status);assertEquals("attachment_limit_reached",error.code)}
            }
        }
        return ids
    }
    @Test fun queueFinancialPaymentWithBackendOffline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="payment-queue")
        val f=fixture();login(f,"manager");val a=f.getJSONArray("contexts").getJSONObject(0)
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",a.getString("greenhouse_id"))
        rule.onNodeWithTag("expense-new").performClick();choose("expense-category",a.getString("category_id"));choose("expense-payment-kind","operating")
        rule.onNodeWithTag("expense-date").performScrollTo().performTextReplacement("2026-10-30");rule.onNodeWithTag("expense-time").performScrollTo().performTextReplacement("08:12:34")
        rule.onNodeWithTag("expense-amount").performScrollTo().performTextReplacement("100,01");rule.onNodeWithTag("expense-notes").performScrollTo().performTextReplacement("DEVICE EXPENSE — ${f.getString("fixture_id")}");hideKeyboard()
        financialOfflineBarrier(f,"payment")
        rule.onNodeWithTag("expense-review").scrollIfScrollable().performClick();rule.onNodeWithTag("expense-queue").scrollIfScrollable().performClick()
        val model=runtime();val session=model.vault.current!!
        rule.waitUntil(15000) {model.store.outbox(session.scope.key).any {it.envelope.getString("entity_type")=="expense"}}
        val payment=model.store.outbox(session.scope.key).single {it.envelope.getString("entity_type")=="expense"};assertEquals("QUEUED",payment.state)
        val editor=financeEditor()
        rule.runOnIdle {editor.new(com.inofarm.jogja.core.contracts.RecordKind.COST_ALLOCATION,a.getString("greenhouse_id"));editor.edit {it.copy(expenseId=payment.entityId,seasonId=a.getString("season_id"),amount="60,01",costClass="VC",basis="DEVICE ALLOCATION ${f.getString("fixture_id")}")}}
        val allocationId=editor.state.value.draft!!.id;rule.onNodeWithTag("expense-save-draft").scrollIfScrollable().performClick()
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
        val parent=model.store.record(session.scope.key,proof.getString("expense_id"))!!
        val policy=model.attachments.policy(session,model.vault.generation,parent)
        assertTrue(policy.toString(),policy.getBoolean("configured"));assertEquals(32768,policy.getInt("max_bytes"));assertEquals(2,policy.getInt("max_files"))
        val attachmentDrafts=privateTestFiles().map {(extension,bytes) ->
            val source=File(context.cacheDir,"device-proof-${f.getString("fixture_id")}.$extension");source.writeBytes(bytes)
            try {model.attachments.importUri(session,model.vault.generation,parent,android.net.Uri.fromFile(source),"DEVICE PROOF — ${f.getString("fixture_id")}.$extension")} finally {assertTrue(source.delete())}
        }
        val editor=financeEditor();rule.runOnIdle {editor.open(allocation)}
        lateinit var attachmentVm:com.inofarm.jogja.feature.attachments.AttachmentViewModel
        rule.runOnIdle {attachmentVm=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.attachments.AttachmentViewModel::class.java];attachmentVm.open(parent)}
        rule.waitUntil(10000) {attachmentVm.state.value.policy?.optBoolean("configured")==true}
        financialOfflineBarrier(f,"allocation")
        val attachmentOperations=org.json.JSONArray()
        attachmentDrafts.forEach {draft ->
            rule.runOnIdle {attachmentVm.select(draft.id)}
            rule.waitUntil(10000) {attachmentVm.state.value.selected?.id==draft.id && !attachmentVm.state.value.busy}
            rule.onNodeWithTag("attachment-review").performScrollTo().performClick()
            rule.onNodeWithTag("attachment-queue").performScrollTo().performClick()
            rule.waitUntil(10000) {model.store.outbox(session.scope.key).any {it.entityId==draft.id} && !attachmentVm.state.value.busy}
            val queuedFile=model.store.outbox(session.scope.key).single {it.entityId==draft.id}
            assertEquals("QUEUED",queuedFile.state)
            attachmentOperations.put(JSONObject().put("entity_id",draft.id).put("operation_id",queuedFile.id).put("hash",queuedFile.hash).put("blob_id",queuedFile.blobId))
            rule.onNodeWithTag("attachment-back-list").performScrollTo().performClick()
            rule.waitUntil(10000) {attachmentVm.state.value.selected==null && !attachmentVm.state.value.busy}
        }
        proof.put("attachment_operations",attachmentOperations)
        rule.onNodeWithTag("attachment-close").performScrollTo().performClick()
        rule.waitUntil(10000) {attachmentVm.state.value.parent==null && !attachmentVm.state.value.busy}
        rule.onNodeWithTag("expense-review").scrollIfScrollable().performClick();rule.onNodeWithTag("expense-queue").scrollIfScrollable().performClick()
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
        val attachmentIds=verifyPrivateAttachments(proof)
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-history-kind","cost_allocation")
        rule.waitUntil(15000) {runCatching {rule.onNodeWithTag("finance-history-list").performScrollToNode(hasTestTag("finance-record-${op.entityId}"))}.isSuccess}
        rule.onNodeWithTag("finance-record-${op.entityId}").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("history-audit").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("history-audit").performScrollTo().performClick();rule.waitUntil(15000) {rule.onAllNodesWithText("Pencatatan · versi 1").fetchSemanticsNodes().isNotEmpty()}
        rule.logoutFromAccount();login(f,"worker");val worker=model.vault.current!!
        rule.onNodeWithTag("finance-tab-2").assertDoesNotExist()
        try {model.api.request(worker.scope.server,worker.token,"expenses/${proof.getString("expense_id")}");fail("Worker financial read must be denied")} catch(error:FinanceApiFailure) {assertEquals(403,error.status)}
        attachmentIds.forEach {id -> listOf("attachments/$id","attachments/$id/content","attachments/$id/audit").forEach {path ->
            try {model.api.request(worker.scope.server,worker.token,path);fail("Worker private financial file must be denied")} catch(error:FinanceApiFailure) {assertEquals(404,error.status)}
        }}
        try {model.api.request(worker.scope.server,worker.token,"attachments/policy",query=mapOf("owner_type" to "expense","owner_id" to proof.getString("expense_id")));fail("Worker financial upload policy must be denied")} catch(error:FinanceApiFailure) {assertEquals(403,error.status)}
        val changes=model.api.request(worker.scope.server,worker.token,"sync/changes").getJSONArray("data")
        assertTrue((0 until changes.length()).none {changes.getJSONObject(it).getString("entity_type") in setOf("expense","expense_balance","cost_allocation","expense_categories","expense_attachment")})
        rule.logoutFromAccount()
    }

    @Test fun queueSaleHarvestWithBackendOffline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="sale-source-queue")
        val f=fixture();login(f,"worker");val ctx=f.getJSONArray("contexts").getJSONObject(0)
        choose("finance-gh",ctx.getString("greenhouse_id"));choose("finance-season",ctx.getString("season_id"));choose("finance-group",ctx.getString("group_id"))
        rule.openRecord("harvest")
        rule.onNodeWithTag("harvest-manual-time").performScrollTo().performClick()
        rule.onNodeWithTag("harvest-date").performScrollTo().performTextReplacement("2026-10-30")
        repeat(2) {index ->
            rule.onNodeWithTag("harvest-add-item").performScrollTo().performClick();choose("harvest-grade-$index",ctx.getJSONArray("grade_ids").getString(index))
            choose("harvest-quality-$index", "saleable")
            rule.onNodeWithTag("harvest-count-$index").performScrollTo().performTextReplacement("3")
            rule.onNodeWithTag("harvest-weight-$index").performScrollTo().performTextReplacement("5");hideKeyboard()
        }
        rule.onNodeWithTag("harvest-notes").performScrollTo().performTextReplacement("DEVICE SALE HARVEST ${f.getString("fixture_id")}");hideKeyboard()
        financialOfflineBarrier(f,"sale-source")
        rule.onNodeWithTag("harvest-review").scrollIfScrollable().performClick();rule.onNodeWithTag("harvest-queue").scrollIfScrollable().performClick()
        val model=runtime();val session=model.vault.current!!
        rule.waitUntil(10000) {model.store.outbox(session.scope.key).any {it.envelope.optString("entity_type")=="harvest" && it.state=="QUEUED"}}
        val op=model.store.outbox(session.scope.key).single {it.envelope.optString("entity_type")=="harvest" && it.state=="QUEUED"}
        val items=op.envelope.getJSONObject("payload").getJSONArray("items")
        file("finance-device-sale-proof.json").writeText(JSONObject().put("source_pid",android.os.Process.myPid()).put("harvest_id",op.entityId).put("harvest_op",op.id).put("harvest_hash",op.hash).put("sources",org.json.JSONArray().apply {repeat(items.length()) {put(items.getJSONObject(it).getString("id"))}}).toString())
    }
    @Test fun replayHarvestThenQueueSaleWithBackendOffline():Unit=kotlinx.coroutines.runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="sale-queue")
        val f=fixture();val proof=JSONObject(file("finance-device-sale-proof.json").readText());assertNotEquals(proof.getInt("source_pid"),android.os.Process.myPid())
        val model=runtime();rule.waitUntil(15000) {model.vault.current!=null};val worker=model.vault.current!!
        rule.waitUntil(20000) {model.store.record(worker.scope.key,proof.getString("harvest_id"))?.syncState==com.inofarm.jogja.core.contracts.SyncState.ACCEPTED}
        val sourceOp=model.store.outbox(worker.scope.key).single {it.id==proof.getString("harvest_op")};assertEquals(proof.getString("harvest_hash"),sourceOp.hash)
        model.api.request(worker.scope.server,worker.token,"harvests","POST",sourceOp.envelope)
        login(f,"manager");val ctx=f.getJSONArray("contexts").getJSONObject(0)
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",ctx.getString("greenhouse_id"));rule.clickFinanceTool("sale-new")
        choose("sale-season",ctx.getString("season_id"));rule.onNodeWithTag("sale-date").performScrollTo().performTextReplacement("2026-10-30")
        repeat(2) {index ->
            if(index>0) rule.onNodeWithTag("sale-add").performScrollTo().performClick()
            choose("sale-source-$index",proof.getJSONArray("sources").getString(index))
            rule.onNodeWithTag("sale-quantity-$index").performScrollTo().performTextReplacement("1");rule.onNodeWithTag("sale-price-$index").performScrollTo().performTextReplacement("0,004");hideKeyboard()
        }
        rule.onNodeWithTag("sale-paid").performScrollTo().performClick();rule.onNodeWithTag("sale-notes").performScrollTo().performTextReplacement("DEVICE SALE ${f.getString("fixture_id")}");hideKeyboard()
        financialOfflineBarrier(f,"sale")
        rule.onNodeWithTag("sale-review").scrollIfScrollable().performClick();rule.onNodeWithTag("sale-queue").scrollIfScrollable().performClick()
        val session=model.vault.current!!;rule.waitUntil(10000) {model.store.outbox(session.scope.key).any {it.envelope.optString("entity_type")=="sale"}}
        val op=model.store.outbox(session.scope.key).single {it.envelope.optString("entity_type")=="sale"};assertEquals("QUEUED",op.state)
        proof.put("sale_pid",android.os.Process.myPid()).put("sale_id",op.entityId).put("sale_op",op.id).put("sale_hash",op.hash);file("finance-device-sale-proof.json").writeText(proof.toString())
    }
    @Test fun replaySaleAuditCancelAndWorkerScope():Unit=kotlinx.coroutines.runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="sale-verify")
        val f=fixture();val proof=JSONObject(file("finance-device-sale-proof.json").readText());assertNotEquals(proof.getInt("sale_pid"),android.os.Process.myPid())
        val model=runtime();rule.waitUntil(15000) {model.vault.current!=null};val session=model.vault.current!!;val id=proof.getString("sale_id")
        rule.waitUntil(20000) {model.store.record(session.scope.key,id)?.syncState==com.inofarm.jogja.core.contracts.SyncState.ACCEPTED}
        val op=model.store.outbox(session.scope.key).single {it.id==proof.getString("sale_op")};assertEquals(proof.getString("sale_hash"),op.hash)
        val receipt=model.api.request(session.scope.server,session.token,"sales","POST",op.envelope);assertEquals("0.01",receipt.getJSONObject("data").getString("total_amount"))
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-history-kind","sale")
        rule.waitUntil(15000) {runCatching {rule.onNodeWithTag("finance-history-list").performScrollToNode(hasTestTag("finance-record-$id"))}.isSuccess}
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("history-audit").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("history-audit").performScrollTo().performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Pencatatan · versi 1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("history-cancel").performScrollTo().performClick();rule.onNodeWithTag("history-action-reason").performTextReplacement("DEVICE SALE CANCEL ${f.getString("fixture_id")}");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(20000) {model.store.record(session.scope.key,id)?.serverVersion==2L && model.store.record(session.scope.key,id)?.body?.optString("record_state")=="cancelled"}
        model.store.acknowledge(session.scope.key,op.id,receipt);assertEquals(2L,model.store.record(session.scope.key,id)!!.serverVersion)
        val balances=model.api.request(session.scope.server,session.token,"harvests/${proof.getString("harvest_id")}/availability").getJSONArray("data")
        repeat(balances.length()) {assertEquals("0.000000",balances.getJSONObject(it).getString("sold_weight_kg"));assertEquals("5.000000",balances.getJSONObject(it).getString("available_weight_kg"))}
        rule.logoutFromAccount();login(f,"worker");val worker=model.vault.current!!
        try {model.api.request(worker.scope.server,worker.token,"sales/$id");fail("Worker sale must be denied")} catch(error:FinanceApiFailure) {assertEquals(403,error.status)}
        val changes=model.api.request(worker.scope.server,worker.token,"sync/changes").getJSONArray("data");assertTrue((0 until changes.length()).none {changes.getJSONObject(it).getString("entity_type")=="sale"})
        rule.logoutFromAccount()
    }

    @Test fun queueWithBackendOffline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="queue")
        val f=fixture();login(f,"worker");val a=f.getJSONArray("contexts").getJSONObject(0)
        rule.openHomeContext();rule.onNodeWithTag("finance-gh").performClick()
        rule.onNodeWithTag("finance-gh-option-${f.getJSONArray("contexts").getJSONObject(1).getString("greenhouse_id")}").assertDoesNotExist()
        rule.onNodeWithTag("finance-gh-option-${a.getString("greenhouse_id")}").performClick()
        rule.finishChoice("finance-gh")
        choose("finance-season",a.getString("season_id"));choose("finance-group",a.getString("group_id"))
        financialOfflineBarrier(f,"device")
        val measurement=InstrumentationRegistry.getArguments().getString("measurementPair")=="true"
        rule.openRecord(if(measurement) "measurement" else "activity")
        if(measurement) {
            rule.onNodeWithTag("finance-ec").performScrollTo().performTextReplacement("1,25")
            rule.onNodeWithTag("finance-ppm").performScrollTo().performTextReplacement("625,5")
        }
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement("DEVICE OFFLINE ${f.getString("fixture_id")}")
        hideKeyboard();rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick();rule.onNodeWithTag("finance-queue").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-1").performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Belum terkirim").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-0").performClick();rule.openRecord("harvest")
        repeat(2) {index ->
            rule.onNodeWithTag("harvest-add-item").performScrollTo().performClick();choose("harvest-grade-$index",a.getJSONArray("grade_ids").getString(index))
            choose("harvest-quality-$index", "saleable")
            rule.onNodeWithTag("harvest-count-$index").performScrollTo().performTextReplacement(if(index==0) "3" else "0");hideKeyboard()
            rule.onNodeWithTag("harvest-weight-$index").performScrollTo().performTextReplacement(if(index==0) "4,5" else "0");hideKeyboard()
        }
        rule.onNodeWithTag("harvest-notes").performScrollTo().performTextReplacement("DEVICE HARVEST OFFLINE ${f.getString("fixture_id")}");hideKeyboard()
        rule.onNodeWithTag("harvest-review").scrollIfScrollable().performClick();rule.onNodeWithTag("harvest-queue").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-1").fetchSemanticsNodes().isNotEmpty()};rule.openWorkerHarvestHistory()
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
        rule.waitUntil(15000) {rule.onAllNodesWithTag("finance-tab-1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-1").performClick()
        rule.waitUntil(20000) {rule.onAllNodesWithText("Terkirim").fetchSemanticsNodes().isNotEmpty()}
        val runtime=runtime();val worker=runtime.vault.current!!
        rule.waitUntil(20000) {runtime.store.outbox(worker.scope.key).count {it.state=="ACCEPTED"}==2}
        val record=runtime.api.request(worker.scope.server,worker.token,"activities/${proof.getString("entity_id")}")
        assertEquals(f.getString("worker_id"),record.getString("recorded_by"));assertEquals(1,record.getInt("version"))
        if(InstrumentationRegistry.getArguments().getString("measurementPair")=="true") {
            assertEquals("measurement",record.getString("activity_type"));assertEquals("1.250000",record.getString("manual_ec"));assertEquals("625.500000",record.getString("manual_ppm"))
            val ctx=f.getJSONArray("contexts").getJSONObject(0)
            val latest=runtime.api.request(worker.scope.server,worker.token,"measurements/latest",query=mapOf("planting_group_id" to ctx.getString("group_id"))).getJSONObject("data")
            assertEquals(record.getString("id"),latest.getString("id"))
            assertEquals("1.25",runtime.store.latestMeasurement(worker.scope.key,ctx.getString("greenhouse_id"),ctx.getString("group_id"),java.time.LocalDate.now(com.inofarm.jogja.feature.activities.domain.ActivityDraft.WIB))!!.ec)
        }
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
        rule.logoutFromAccount();login(f,"manager")
        rule.onNodeWithTag("finance-tab-1").performClick();rule.waitUntil(15000) {rule.onAllNodesWithText("DEVICE OFFLINE ${f.getString("fixture_id")}").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("DEVICE OFFLINE ${f.getString("fixture_id")}").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-edit").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Pencatat: Pekerja perangkat sintetis").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Kembali ke riwayat").performScrollTo().performClick();rule.openWorkerHarvestHistory()
        rule.waitUntil(15000) {rule.onAllNodesWithText("DEVICE HARVEST OFFLINE ${f.getString("fixture_id")}").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("DEVICE HARVEST OFFLINE ${f.getString("fixture_id")}").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-edit").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Pencatat: Pekerja perangkat sintetis").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("history-cancel").performScrollTo().performClick();rule.onNodeWithTag("history-action-reason").performTextReplacement("DEVICE HARVEST CANCEL ${f.getString("fixture_id")}");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(15000) {rule.onAllNodesWithText("Dibatalkan").fetchSemanticsNodes().isNotEmpty()}
        rule.logoutFromAccount();login(f,"worker")
        val currentWorker=runtime.vault.current!!
        val oldReceipt=runtime.api.request(currentWorker.scope.server,currentWorker.token,"harvests","POST",harvestOp.envelope)
        assertEquals(1,oldReceipt.getInt("version"));runtime.store.acknowledge(currentWorker.scope.key,harvestOp.id,oldReceipt)
        val after=runtime.store.record(currentWorker.scope.key,harvestOp.entityId)!!
        assertEquals(2L,after.serverVersion);assertEquals(2L,after.baseVersion);assertEquals("cancelled",after.body.getString("record_state"))
        rule.logoutFromAccount()
    }
}
