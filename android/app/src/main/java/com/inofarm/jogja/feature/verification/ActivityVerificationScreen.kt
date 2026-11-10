package com.inofarm.jogja.feature.verification

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.ActivityFacts
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.timeLabel
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.attachments.LocalAttachmentAction
import com.inofarm.jogja.feature.context.CultivationUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

private fun JSONObject.rows(key: String): List<JSONObject> = optJSONArray(key)?.let { rows ->
    List(rows.length(), rows::getJSONObject)
}.orEmpty()

private fun JSONObject.text(key: String): String? = optString(key).takeUnless { isNull(key) || it.isBlank() }
private fun option(id: String, name: String) = JSONObject().put("id", id).put("name", name)

internal fun verificationLabel(status: String): String = when (status) {
    "pending" -> "Belum diverifikasi"
    "valid" -> "Valid"
    "invalid" -> "Tidak valid"
    "manager" -> "Dicatat pengelola"
    "legacy" -> "Belum pernah diverifikasi"
    else -> "Status verifikasi belum tersedia"
}

private fun activityLabel(row: JSONObject) =
    ActivityType.entries.firstOrNull { it.wire == row.optString("activity_type") }?.label ?: "Kegiatan"

private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

@Composable
internal fun ActivityVerificationScreen(
    session: FinanceSession,
    ticket: Long,
    context: CultivationUiState,
    repository: ActivityVerificationRepository,
    syncRevision: Long,
    selected: String?,
    onSelect: (String?) -> Unit,
    onOutbox: () -> Unit,
    onSync: () -> Unit,
) {
    if (selected != null) {
        BackHandler { onSelect(null) }
        key(selected) {
            VerificationDetail(session, ticket, context, repository, syncRevision, selected, { onSelect(null) }, onOutbox, onSync)
        }
        return
    }
    var status by rememberSaveable { mutableStateOf("pending") }
    var greenhouse by rememberSaveable { mutableStateOf("") }
    var worker by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var until by rememberSaveable { mutableStateOf("") }
    var appliedFrom by rememberSaveable { mutableStateOf("") }
    var appliedUntil by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val query = mapOf("status" to status, "greenhouse_id" to greenhouse, "worker_id" to worker,
        "activity_type" to type, "from" to appliedFrom, "until" to appliedUntil).filterValues { it.isNotBlank() }
    var workerOptions by remember { mutableStateOf(emptyList<JSONObject>()) }
    var workerMessage by remember { mutableStateOf<String?>(null) }
    var workerPage by remember { mutableIntStateOf(0) }
    var workerCursor by remember(greenhouse) { mutableStateOf<String?>(null) }
    LaunchedEffect(filters, greenhouse, refresh, syncRevision, workerPage) {
        if (filters) {
            val workerQuery = mapOf("greenhouse_id" to greenhouse, "cursor" to workerCursor.orEmpty()).filterValues { it.isNotBlank() }
            attempt { repository.read(session, ticket, "activity-reviews/recorders", workerQuery) }
                .onSuccess { body ->
                    workerOptions = (workerOptions + body.rows("data")).distinctBy { it.getString("id") }
                    workerCursor = body.text("next_cursor")
                    workerMessage = null
                }
                .onFailure { workerMessage = "Daftar pencatat belum dapat diperiksa." }
        }
    }
    key(query, refresh, syncRevision) {
        var rows by remember { mutableStateOf(emptyList<JSONObject>()) }
        var cursor by remember { mutableStateOf<String?>(null) }
        var loadPage by remember { mutableIntStateOf(0) }
        var loading by remember { mutableStateOf(true) }
        var failure by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(loadPage) {
            loading = true
            failure = null
            attempt {
                repository.read(session, ticket, "activity-reviews", query + (cursor?.let { mapOf("cursor" to it) } ?: emptyMap()))
            }.onSuccess { response ->
                rows = (rows + response.rows("data")).distinctBy { it.getJSONObject("activity").getString("id") }
                cursor = response.text("next_cursor")
            }.onFailure { failure = it.message ?: "Server belum dapat dihubungi." }
            loading = false
        }
        FarmMenuList(Modifier.testTag("activity-verification-list")) {
            item {
                FarmMenuHeader("Verifikasi kegiatan", "Periksa catatan pekerja yang sudah diterima server.") {
                    OutlinedButton(onClick = onOutbox, shape = FarmShape.Control,
                        modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("verification-outbox")) { Text("Pengiriman saya") }
                }
            }
            item {
                Choice("Status verifikasi", status, listOf(
                    option("pending", "Menunggu pemeriksaan"), option("decided", "Pernah diputuskan"),
                    option("valid", "Valid"), option("invalid", "Tidak valid"),
                    option("legacy", "Catatan lama")
                ), "verification-status") { status = it }
                TextButton(onClick = { filters = !filters }, modifier = Modifier.heightIn(min = FarmSpace.Touch)) {
                    Text(if (filters) "Tutup filter" else "Filter greenhouse, pencatat dan tanggal")
                }
            }
            if (filters) item {
                FarmPanel(title = "Filter kegiatan") {
                    Choice("Greenhouse", greenhouse, listOf(option("", "Semua greenhouse")) + context.greenhouses,
                        "verification-greenhouse") { greenhouse = it; worker = ""; workerOptions = emptyList() }
                    Choice("Pencatat", worker, listOf(option("", "Semua pekerja")) + workerOptions,
                        "verification-worker") { worker = it }
                    workerMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (workerCursor != null || workerMessage != null) TextButton(onClick = { workerPage++ }) { Text("Muat pencatat") }
                    Choice("Jenis kegiatan", type, listOf(option("", "Semua jenis")) +
                        ActivityType.entries.map { option(it.wire, it.label) }, "verification-type") { type = it }
                    FinanceField("Dari tanggal WIB (YYYY-MM-DD)", from, { from = it }, "verification-from")
                    FinanceField("Sampai tanggal WIB (YYYY-MM-DD)", until, { until = it }, "verification-until")
                    val validDates = runCatching {
                        val first = from.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                        val last = until.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                        first == null || last == null || last >= first
                    }.getOrDefault(false)
                    if (!validDates) Text("Periksa format dan urutan tanggal.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { appliedFrom = from; appliedUntil = until }, enabled = validDates,
                        modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Terapkan tanggal") }
                }
            }
            if (loading) item { Text("Memeriksa kegiatan server…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            failure?.let { error -> item {
                FarmPanel(title = "Kegiatan belum dapat diperiksa") {
                    Text(error)
                    if (rows.isNotEmpty()) Text("Daftar di bawah adalah hasil pemeriksaan terakhir.")
                    OutlinedButton(onClick = { refresh++ }) { Text("Periksa kembali") }
                }
            } }
            if (!loading && failure == null && rows.isEmpty()) item {
                FarmPanel(title = if (status == "pending") "Tidak ada kegiatan yang menunggu pemeriksaan"
                    else "Tidak ada kegiatan yang cocok") {
                    Text("Daftar mengikuti penugasan greenhouse dan filter yang dipilih.")
                    TextButton(onClick = { refresh++ }) { Text("Periksa lagi") }
                }
            }
            items(rows, key = { it.getJSONObject("activity").getString("id") }) { entry ->
                val activity = entry.getJSONObject("activity")
                val id = activity.getString("id")
                FarmRecordCard(Modifier.testTag("verification-activity-$id"), onOpen = { onSelect(id) }) {
                    Text(activityLabel(activity), style = MaterialTheme.typography.titleMedium)
                    val names = entry.getJSONObject("context")
                    Text(listOfNotNull(names.text("greenhouse"), names.text("season"), names.text("variety")).joinToString(" · "))
                    Text(listOfNotNull(activity.text("recorded_by_name"), activity.text("occurred_at")?.let(::timeLabel)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall)
                    val measures = listOfNotNull(activity.text("manual_ec")?.let { "$it mS/cm" }, activity.text("manual_ppm")?.let { "$it ppm" })
                    if (measures.isNotEmpty()) Text(measures.joinToString(" · "))
                    else activity.text("notes")?.let { Text(it, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    val statusLabel = if (activity.optString("record_state") == "cancelled") "Dibatalkan"
                        else verificationLabel(entry.getString("status"))
                    Text(statusLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (cursor != null && !loading && failure == null) item {
                OutlinedButton(onClick = { loadPage++ }) { Text("Muat kegiatan berikutnya") }
            }
        }
    }
}

@Composable
private fun VerificationDetail(
    session: FinanceSession,
    ticket: Long,
    context: CultivationUiState,
    repository: ActivityVerificationRepository,
    syncRevision: Long,
    id: String,
    onBack: () -> Unit,
    onOutbox: () -> Unit,
    onSync: () -> Unit,
) {
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var history by remember { mutableStateOf(emptyList<JSONObject>()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var reviewedVersion by rememberSaveable { mutableStateOf<Long?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<LocalRecord?>(null) }
    val scope = rememberCoroutineScope()
    val attachments = LocalAttachmentAction.current
    LaunchedEffect(reviewedVersion, refresh, syncRevision, context.greenhouses) {
        loading = true
        error = null
        val query = reviewedVersion?.let { mapOf("activity_version" to it.toString()) }.orEmpty()
        attempt { repository.read(session, ticket, "activities/$id/verification", query) }
            .onSuccess {
                draft = attempt { repository.draft(session, ticket, id, it.getJSONObject("activity").getLong("version")) }.getOrNull()
                detail = it
                history = it.rows("history")
                cursor = it.text("next_cursor")
            }
            .onFailure {
                if (it is FinanceApiFailure && it.status in setOf(401, 403, 404)) {
                    detail = null
                    history = emptyList()
                    draft = null
                }
                error = it.message ?: "Catatan belum dapat diperiksa."
            }
        loading = false
    }
    FarmMenuList(Modifier.testTag("activity-verification-detail")) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Kembali ke verifikasi") }
            FarmPageTitle("Periksa kegiatan")
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                FarmAttention(if (detail != null) "Pemeriksaan terbaru gagal. Menampilkan data terakhir. $it" else it)
                TextButton(onClick = { refresh++ }) { Text("Periksa lagi") }
            }
        }
        val entry = detail
        if (entry != null) {
            val names = entry.getJSONObject("context")
            val activity = entry.getJSONObject("activity")
            val version = activity.getLong("version")
            val current = entry.getLong("current_version") == version
            val record = LocalRecord(id, "activity", names.getString("greenhouse_id"), 0, version, version,
                activity, activity, null, SyncState.ACCEPTED, null)
            item {
                FarmPanel(title = activityLabel(activity)) {
                    Text(listOfNotNull(names.text("greenhouse"), names.text("season"), names.text("group"), names.text("variety")).joinToString(" · "))
                    Text("Diterima server · revisi $version")
                    Text(verificationLabel(entry.getString("status")), style = MaterialTheme.typography.titleMedium)
                    if (!current) TextButton(onClick = { detail = null; reviewedVersion = null }) { Text("Buka revisi terbaru") }
                    entry.optJSONObject("decision")?.let { DecisionFacts(it) }
                }
            }
            item { FarmPanel(title = "Catatan aktual") { ActivityFacts(record, session, context) } }
            item { ReferenceFacts(activity) }
            if (attachments != null && current) item {
                OutlinedButton(onClick = { attachments(record) }, modifier = Modifier.heightIn(min = FarmSpace.Touch)) {
                    Text("Lampiran dan bukti catatan")
                }
            }
            if (current && entry.getString("status") == "pending" && activity.optString("record_state") == "active") item {
                DecisionEditor(session, ticket, repository, entry, draft, onOutbox, onSync) { draft = it }
            }
            if (history.isNotEmpty()) item { FarmSectionHeading("Histori keputusan") }
            items(history, key = { it.getString("id") }) { review ->
                FarmRecordCard(onOpen = {
                    val requested = review.getLong("activity_version")
                    if (requested != version) { detail = null; reviewedVersion = requested }
                }.takeIf { review.getLong("activity_version") != version }) {
                    Text("Revisi ${review.getLong("activity_version")} · ${verificationLabel(review.getString("decision"))}",
                        style = MaterialTheme.typography.titleMedium)
                    DecisionFacts(review)
                }
            }
            if (cursor != null) item {
                OutlinedButton(enabled = !loading, onClick = {
                    val next = cursor ?: return@OutlinedButton
                    loading = true
                    scope.launch {
                        attempt { repository.read(session, ticket, "activities/$id/verification", mapOf("cursor" to next)) }
                            .onSuccess {
                                history = (history + it.rows("history")).distinctBy { row -> row.getString("id") }
                                cursor = it.text("next_cursor")
                            }.onFailure { error = it.message }
                        loading = false
                    }
                }) { Text("Muat keputusan berikutnya") }
            }
        }
    }
}

@Composable
private fun ReferenceFacts(activity: JSONObject) {
    val snapshot = activity.optJSONObject("reference_snapshot")
    FarmPanel(title = "Acuan saat pencatatan") {
        if (snapshot == null) {
            Text("Acuan belum tersedia.")
        } else {
            Text("Versi kelompok ${snapshot.optInt("planting_group_version")} · pembanding ${snapshot.optString("comparator_version")}")
            val checks = snapshot.optJSONObject("checks")
            listOf("manual_ec" to "EC", "manual_ppm" to "PPM", "manual_ph" to "pH").forEach { (key, label) ->
                val check = checks?.optJSONObject(key)
                val target = check?.optJSONObject("target")
                val value = target?.text("target_value") ?: target?.text("minimum")?.let { low ->
                    target.text("maximum")?.let { high -> "$low–$high" }
                }
                FarmFact(label, if (target == null || value == null) "Acuan belum tersedia"
                    else "$value ${target.optString("unit")} · versi ${target.optInt("version")}")
                target?.text("tolerance")?.let { Text("Toleransi $it ${target.optString("unit")}") }
            }
        }
    }
}

@Composable
private fun DecisionEditor(
    session: FinanceSession,
    ticket: Long,
    repository: ActivityVerificationRepository,
    entry: JSONObject,
    draft: LocalRecord?,
    onOutbox: () -> Unit,
    onSync: () -> Unit,
    onSaved: (LocalRecord) -> Unit,
) {
    val activity = entry.getJSONObject("activity")
    val version = activity.getLong("version")
    val id = activity.getString("id")
    var decision by rememberSaveable(id, version) { mutableStateOf(draft?.body?.text("decision")) }
    var reason by rememberSaveable(id, version) { mutableStateOf(draft?.body?.text("reason").orEmpty()) }
    var confirmation by rememberSaveable(id, version) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save(queue: Boolean) {
        val choice = decision ?: return
        busy = true
        scope.launch {
            attempt { repository.save(session, ticket, entry, choice, reason, queue) }
                .onSuccess { saved -> onSaved(saved); if (queue) onSync(); confirmation = false }
                .onFailure { error = it.message; confirmation = false }
            busy = false
        }
    }
    FarmPanel(title = "Keputusan untuk revisi $version") {
        val submitted = draft?.syncState?.takeUnless { it == SyncState.LOCAL }
        if (submitted != null) {
            Text(when (submitted) {
                SyncState.ACCEPTED -> "Keputusan diterima server."
                SyncState.QUEUED -> "Keputusan belum terkirim."
                else -> "Pengiriman keputusan perlu ditinjau."
            })
            draft?.error?.text("message")?.let { Text(it) }
            OutlinedButton(onClick = onOutbox) { Text("Buka Pengiriman saya") }
        } else {
            Text("Periksa ketepatan catatan pekerja. Hasil di luar acuan dapat tetap dicatat dengan benar.")
            Choice("Keputusan", decision, listOf(option("valid", "Tandai valid"), option("invalid", "Tandai tidak valid")),
                "verification-decision") { decision = it }
            if (decision != null) {
                FinanceField(if (decision == "invalid") "Alasan tidak valid (wajib)" else "Catatan (opsional)",
                    reason, { reason = it }, "verification-reason", singleLine = false)
                error?.let { FarmAttention(it) }
                Button(onClick = { confirmation = true }, enabled = !busy && reason.length <= 1000 &&
                    (decision != "invalid" || reason.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch)
                    .testTag("verification-review")) { Text("Tinjau keputusan") }
                TextButton(onClick = { save(false) }, enabled = !busy && reason.length <= 1000,
                    modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Simpan draf keputusan") }
                if (draft != null) Text("Draf tersimpan di perangkat.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (confirmation && decision != null) AlertDialog(
        onDismissRequest = { if (!busy) confirmation = false },
        title = { Text("Kirim keputusan?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                Text("${activityLabel(activity)} · revisi $version")
                Text(verificationLabel(decision.orEmpty()))
                if (reason.isNotBlank()) Text(reason)
                Text("Pekerja dapat membaca keputusan setelah diterima server.")
            }
        },
        confirmButton = { TextButton(onClick = { save(true) }, enabled = !busy,
            modifier = Modifier.testTag("verification-submit")) { Text("Simpan dan antrekan") } },
        dismissButton = { TextButton(onClick = { confirmation = false }, enabled = !busy) { Text("Kembali") } },
    )
}

@Composable
private fun DecisionFacts(decision: JSONObject) {
    Text(listOfNotNull(decision.text("reviewer_name"), decision.text("reviewed_at")?.let(::timeLabel)).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall)
    decision.text("reason")?.let { Text(it) }
}

@Composable
internal fun ActivityVerificationFeedback(session: FinanceSession, record: LocalRecord) {
    val version = record.serverVersion ?: return
    val body = record.serverBody ?: return
    if (session.role == UserRole.WORKER && body.text("recorded_by") != session.scope.accountId) return
    val runtime: FinanceRuntime = viewModel()
    val repository = remember(runtime) { ActivityVerificationRepository(runtime.api, runtime.store, runtime.vault) }
    val update by runtime.sync.updates.collectAsStateWithLifecycle()
    var reviews by remember(session.scope, record.id, version) { mutableStateOf(emptyList<JSONObject>()) }
    var status by remember(session.scope, record.id, version) { mutableStateOf<String?>(null) }
    var lastKnown by remember(session.scope, record.id, version) { mutableStateOf(false) }
    LaunchedEffect(session, record.id, version, update) {
        val ticket = runtime.vault.generation
        reviews = attempt { repository.cachedFeedback(session, ticket, record) }.getOrDefault(emptyList())
        lastKnown = reviews.isNotEmpty()
        attempt { repository.read(session, ticket, "activities/${record.id}/verification", mapOf("activity_version" to version.toString())) }
            .onSuccess { entry ->
                reviews = (entry.rows("history") + listOfNotNull(entry.optJSONObject("decision")) + reviews).distinctBy { it.getString("id") }
                status = entry.getString("status")
                lastKnown = false
            }.onFailure {
                status = null
                if (it is FinanceApiFailure && it.status in setOf(401, 403, 404)) {
                    reviews = emptyList()
                    lastKnown = false
                }
            }
    }
    val decision = reviews.firstOrNull { it.optLong("activity_version") == version }
    FarmSection(title = "Verifikasi pengelola") {
        Text(verificationLabel(decision?.optString("decision") ?: status.orEmpty()))
        if (lastKnown) Text("Keputusan terakhir yang tersimpan di perangkat.", style = MaterialTheme.typography.bodySmall)
        decision?.let { DecisionFacts(it) }
        reviews.filter { it.optLong("activity_version") < version }.forEach {
            Text("Revisi ${it.optLong("activity_version")} · ${verificationLabel(it.optString("decision"))}")
            DecisionFacts(it)
        }
    }
}
