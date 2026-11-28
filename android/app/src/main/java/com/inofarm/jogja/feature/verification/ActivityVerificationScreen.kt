@file:OptIn(ExperimentalMaterial3Api::class)

package com.inofarm.jogja.feature.verification

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.feature.FinanceDateField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
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
import com.inofarm.jogja.feature.harvest.HarvestFacts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

private fun JSONObject.rows(key: String): List<JSONObject> = optJSONArray(key)?.let { rows ->
    List(rows.length(), rows::getJSONObject)
}.orEmpty()

private fun JSONObject.text(key: String): String? = optString(key).takeUnless { isNull(key) || it.isBlank() }
private fun option(id: String, name: String) = JSONObject().put("id", id).put("name", name)

internal fun verificationLabel(status: String): String = when (status) {
    "pending" -> "Belum dicek"
    "valid" -> "Sesuai"
    "invalid" -> "Perlu diperbaiki"
    "manager" -> "Dicatat pengelola"
    "legacy" -> "Catatan lama"
    else -> "Status belum tersedia"
}

private fun recordLabel(kind: ReviewKind, row: JSONObject) =
    if (kind == ReviewKind.HARVEST) "Panen" else
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
    kind: ReviewKind,
    onKind: (ReviewKind) -> Unit,
) {
    var status by rememberSaveable { mutableStateOf("pending") }
    var greenhouse by rememberSaveable { mutableStateOf("") }
    var worker by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var until by rememberSaveable { mutableStateOf("") }
    var appliedFrom by rememberSaveable { mutableStateOf("") }
    var appliedUntil by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(false) }
    // The other pending records of the opened entry (17 Nov 2026): one decision covers the whole entry.
    var siblings by remember { mutableStateOf(emptyList<JSONObject>()) }
    if (selected != null) {
        BackHandler { onSelect(null) }
        key(selected) {
            VerificationDetail(session, ticket, context, repository, syncRevision, selected, { onSelect(null) }, onOutbox, onSync, kind,
                siblings.filter { it.getJSONObject(kind.wire).getString("id") != selected })
        }
        return
    }
    var refresh by remember { mutableIntStateOf(0) }
    val query = (mapOf("status" to status, "greenhouse_id" to greenhouse) +
        if (kind == ReviewKind.ACTIVITY) mapOf("worker_id" to worker, "activity_type" to type, "from" to appliedFrom, "until" to appliedUntil)
        else emptyMap()).filterValues { it.isNotBlank() }
    var workerOptions by remember { mutableStateOf(emptyList<JSONObject>()) }
    var workerMessage by remember { mutableStateOf<String?>(null) }
    var workerPage by remember { mutableIntStateOf(0) }
    var workerCursor by remember(greenhouse) { mutableStateOf<String?>(null) }
    LaunchedEffect(filters, greenhouse, refresh, syncRevision, workerPage) {
        if (filters && kind == ReviewKind.ACTIVITY) {
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
                repository.read(session, ticket, kind.inbox, query + (cursor?.let { mapOf("cursor" to it) } ?: emptyMap()))
            }.onSuccess { response ->
                rows = (rows + response.rows("data")).distinctBy { it.getJSONObject(kind.wire).getString("id") }
                cursor = response.text("next_cursor")
            }.onFailure { failure = it.message ?: "Belum tersambung ke internet." }
            loading = false
        }
        FarmMenuList(Modifier.testTag("${kind.wire}-verification-list")) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    FarmChoiceChips("Jenis catatan", kind.wire, listOf(option("activity", "Kegiatan"), option("harvest", "Panen")),
                        "verification-kind") { wire -> onKind(ReviewKind.entries.first { it.wire == wire }) }
                    FarmChoiceChips("Status", status, listOf(
                        option("pending", "Belum dicek"), option("decided", "Sudah dicek"),
                        option("valid", "Sesuai"), option("invalid", "Perlu diperbaiki"),
                        option("legacy", "Catatan lama")
                    ), "verification-status", scroll = true) { status = it }
                    TextButton(onClick = { filters = !filters }, modifier = Modifier.heightIn(min = FarmSpace.Touch)) {
                        Text(if (filters) "Tutup filter" else if (kind == ReviewKind.HARVEST) "Filter greenhouse" else "Filter greenhouse, pencatat dan tanggal")
                    }
                }
            }
            if (loading) item { Text("Memuat ${kind.label}…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            failure?.let { error -> item {
                FarmPanel(title = "Catatan belum bisa dimuat") {
                    Text(error)
                    if (rows.isNotEmpty()) Text("Daftar di bawah adalah data tersimpan.")
                    OutlinedButton(onClick = { refresh++ }) { Text("Coba lagi") }
                }
            } }
            if (!loading && failure == null && rows.isEmpty()) item {
                FarmPanel(title = if (status == "pending") "Semua ${kind.label} sudah dicek"
                    else "Tidak ada ${kind.label} yang cocok") {
                    TextButton(onClick = { refresh++ }) { Text("Perbarui") }
                }
            }
            // Records of one greenhouse entry share entry_batch_id and show as one card.
            val grouped = rows.groupBy { row -> row.getJSONObject(kind.wire).let { it.text("entry_batch_id") ?: it.getString("id") } }.values.toList()
            items(grouped, key = { it.first().getJSONObject(kind.wire).getString("id") }) { batch ->
                // Rows fade and slide into place when the list changes, instead of jumping.
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
                    val entry = batch.first()
                    val activity = entry.getJSONObject(kind.wire)
                    val id = activity.getString("id")
                    VerificationCard(kind, entry, activity, "verification-${kind.wire}-$id") {
                        siblings = batch.filter { it.optString("status") == "pending" }
                        onSelect(id)
                    }
                    if (batch.size > 1) Text("Satu isian: ${batch.joinToString(", ") { recordLabel(kind, it.getJSONObject(kind.wire)).lowercase() }}",
                        Modifier.testTag("verification-batch-$id"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (cursor != null && !loading && failure == null) item {
                OutlinedButton(onClick = { loadPage++ }) { Text("Muat ${kind.label} berikutnya") }
            }
            item {
                TextButton(onClick = onOutbox, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("verification-outbox")) {
                    Text("Catatan saya yang belum terkirim")
                }
            }
        }
        if (filters) CompositionLocalProvider(LocalFarmDialogPicker provides true) {
            ModalBottomSheet(
                onDismissRequest = { filters = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = { WindowInsets.safeDrawing }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page)) {
                    FarmSheetHeading("Filter ${kind.label}", { filters = false })
                    Column(Modifier.weight(1f, fill = false).imePadding().verticalScroll(rememberScrollState()).padding(bottom = FarmSpace.Large),
                        verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                        Choice("Greenhouse", greenhouse, listOf(option("", "Semua greenhouse")) + context.greenhouses,
                            "verification-greenhouse") { greenhouse = it; worker = ""; workerOptions = emptyList() }
                        if (kind == ReviewKind.ACTIVITY) {
                            Choice("Pencatat", worker, listOf(option("", "Semua pekerja")) + workerOptions,
                                "verification-worker") { worker = it }
                            workerMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (workerCursor != null || workerMessage != null) TextButton(onClick = { workerPage++ }) { Text("Muat pencatat") }
                            Choice("Jenis kegiatan", type, listOf(option("", "Semua jenis")) +
                                ActivityType.entries.map { option(it.wire, it.label) }, "verification-type") { type = it }
                            FinanceDateField("Dari tanggal", from, { from = it }, "verification-from")
                            FinanceDateField("Sampai tanggal", until, { until = it }, "verification-until")
                            val validDates = runCatching {
                                val first = from.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                                val last = until.takeIf(String::isNotBlank)?.let(LocalDate::parse)
                                first == null || last == null || last >= first
                            }.getOrDefault(false)
                            if (!validDates) Text("Periksa format dan urutan tanggal.", color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { appliedFrom = from; appliedUntil = until }, enabled = validDates,
                                modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Terapkan tanggal") }
                        }
                        Button(onClick = { filters = false }, modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("verification-filter-done")) {
                            Text("Selesai")
                        }
                    }
                }
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
    kind: ReviewKind,
    siblings: List<JSONObject> = emptyList(),
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
        val query = reviewedVersion?.let { mapOf(kind.versionKey to it.toString()) }.orEmpty()
        attempt { repository.read(session, ticket, kind.detail(id), query) }
            .onSuccess {
                draft = attempt { repository.draft(session, ticket, kind, id, it.getJSONObject(kind.wire).getLong("version")) }.getOrNull()
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
    FarmMenuList(Modifier.testTag("${kind.wire}-verification-detail")) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Kembali ke daftar") }
            FarmPageTitle("Cek ${kind.label}")
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                FarmAttention(if (detail != null) "Belum bisa memperbarui. Data yang tampil adalah data tersimpan. $it" else it)
                TextButton(onClick = { refresh++ }) { Text("Coba lagi") }
            }
        }
        val entry = detail
        if (entry != null) {
            val names = entry.getJSONObject("context")
            val activity = entry.getJSONObject(kind.wire)
            val version = activity.getLong("version")
            val current = entry.getLong("current_version") == version
            val record = LocalRecord(id, kind.wire, names.getString("greenhouse_id"), 0, version, version,
                activity, activity, null, SyncState.ACCEPTED, null)
            item {
                FarmPanel(title = recordLabel(kind, activity)) {
                    Text(listOfNotNull(names.text("greenhouse"), names.text("season"), names.text("group"), names.text("variety")).joinToString(" · "))
                    Text("Revisi $version")
                    Text(verificationLabel(entry.getString("status")), style = MaterialTheme.typography.titleMedium)
                    if (!current) TextButton(onClick = { detail = null; reviewedVersion = null }) { Text("Buka revisi terbaru") }
                    entry.optJSONObject("decision")?.let { DecisionFacts(it) }
                    if (kind == ReviewKind.HARVEST && entry.optBoolean("used_in_sales")) {
                        FarmAttention("Sebagian hasil panen sudah digunakan untuk penjualan. Periksa transaksi terkait bila perlu koreksi.")
                    }
                }
            }
            item { FarmPanel(title = "Catatan aktual") {
                if (kind == ReviewKind.HARVEST) HarvestFacts(record, session, context)
                else ActivityFacts(record, session, context, showComparison = false)
            } }
            if (kind == ReviewKind.ACTIVITY) item { ReferenceFacts(activity) }
            if (attachments != null && current) item {
                OutlinedButton(onClick = { attachments(record) }, modifier = Modifier.heightIn(min = FarmSpace.Touch)) {
                    Text("Lampiran dan bukti catatan")
                }
            }
            if (current && entry.getString("status") == "pending" && activity.optString("record_state") == "active") item {
                if (kind == ReviewKind.ACTIVITY || entry.optBoolean("can_review")) {
                    DecisionEditor(session, ticket, repository, kind, entry, draft, onOutbox, onSync, siblings) { draft = it }
                } else Text("Panen sendiri perlu diperiksa pengelola lain.")
            }
            if (history.isNotEmpty()) item { FarmSectionHeading("Riwayat keputusan") }
            items(history, key = { it.getString("id") }) { review ->
                // Rows fade and slide into place when the list changes, instead of jumping.
                Column(Modifier.animateItem()) {
                    FarmRecordCard(onOpen = {
                        val requested = review.getLong(kind.versionKey)
                        if (requested != version) { detail = null; reviewedVersion = requested }
                    }.takeIf { review.getLong(kind.versionKey) != version }) {
                        Text("Revisi ${review.getLong(kind.versionKey)} · ${verificationLabel(review.getString("decision"))}",
                            style = MaterialTheme.typography.titleMedium)
                        DecisionFacts(review)
                    }
                }
            }
            if (cursor != null) item {
                OutlinedButton(enabled = !loading, onClick = {
                    val next = cursor ?: return@OutlinedButton
                    loading = true
                    scope.launch {
                        attempt { repository.read(session, ticket, kind.detail(id), mapOf("cursor" to next)) }
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
            Text("Acuan yang berlaku saat catatan dibuat")
            snapshot.optJSONObject("schedule")?.let { schedule ->
                FarmFact("Jadwal versi ${schedule.optInt("version")}", timeLabel(schedule.optString("planned_at")))
                schedule.text("notes")?.let { Text(it) }
            }
            val checks = snapshot.optJSONObject("checks")
            listOf("manual_ec" to "EC", "manual_ppm" to "PPM", "manual_ph" to "pH").forEach { (key, label) ->
                val check = checks?.optJSONObject(key)
                val target = check?.optJSONObject("target")
                val value = target?.text("target_value")?.let(::measureText) ?: target?.text("minimum")?.let { low ->
                    target.text("maximum")?.let { high -> "${measureText(low)}–${measureText(high)}" }
                }
                FarmFact(label, if (target == null || value == null) "Acuan belum tersedia"
                    else "$value ${target.optString("unit")} · versi ${target.optInt("version")}")
                target?.text("tolerance")?.let { Text("Toleransi ${measureText(it)} ${target.optString("unit")}") }
                val comparison = when (check?.text("status")) {
                    "within_range" -> "Dalam rentang acuan"
                    "outside_range" -> "Di luar rentang acuan"
                    "delta_only" -> "Selisih ${check.text("delta")?.let(::measureText).orEmpty()}"
                    else -> "Belum dapat dibandingkan"
                }
                Text(comparison, style = MaterialTheme.typography.bodySmall)
                check?.optJSONArray("reasons")?.let { reasons ->
                    repeat(reasons.length()) { Text(reasons.getString(it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun DecisionEditor(
    session: FinanceSession,
    ticket: Long,
    repository: ActivityVerificationRepository,
    kind: ReviewKind,
    entry: JSONObject,
    draft: LocalRecord?,
    onOutbox: () -> Unit,
    onSync: () -> Unit,
    siblings: List<JSONObject> = emptyList(),
    onSaved: (LocalRecord) -> Unit,
) {
    val activity = entry.getJSONObject(kind.wire)
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
            // The same decision goes to every pending record of the entry; the server keeps one review per record.
            attempt {
                siblings.forEach { repository.save(session, ticket, kind, it, choice, reason, queue) }
                repository.save(session, ticket, kind, entry, choice, reason, queue)
            }
                .onSuccess { saved -> onSaved(saved); if (queue) onSync(); confirmation = false }
                .onFailure { error = it.message; confirmation = false }
            busy = false
        }
    }
    FarmPanel(title = if (siblings.isEmpty()) "Keputusan untuk revisi $version" else "Keputusan untuk seluruh isian (${siblings.size + 1} bagian)") {
        val submitted = draft?.syncState?.takeUnless { it == SyncState.LOCAL }
        // Felt once, when a "Sesuai" decision is accepted by the server in front of the manager, not when an old one is reopened.
        val haptics = com.inofarm.jogja.core.design.rememberFarmHaptics()
        var seenState by remember(draft?.id) { mutableStateOf(submitted) }
        LaunchedEffect(submitted) {
            if (submitted == SyncState.ACCEPTED && seenState != SyncState.ACCEPTED && draft?.body?.text("decision") == "valid") haptics.confirm()
            seenState = submitted
        }
        if (submitted != null) {
            Text(when (submitted) {
                SyncState.ACCEPTED -> "Keputusan terkirim."
                SyncState.QUEUED -> "Keputusan belum terkirim."
                else -> "Keputusan perlu dicek."
            })
            draft?.error?.text("message")?.let { Text(it) }
            OutlinedButton(onClick = onOutbox) { Text("Lihat belum terkirim") }
        } else {
            Choice("Keputusan", decision, listOf(option("valid", "Sesuai"), option("invalid", "Perlu diperbaiki")),
                "verification-decision") { decision = it }
            // The reason field opens under the choice; "Perlu diperbaiki" needs a reason, so it is scrolled into view.
            val reasonView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
            LaunchedEffect(decision) {
                if (decision == "invalid") { delay(com.inofarm.jogja.core.design.FarmMotion.State.toLong()); reasonView.bringIntoView() }
            }
            val motion = com.inofarm.jogja.core.design.FarmMotion
            androidx.compose.animation.AnimatedVisibility(decision != null,
                enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(motion.State, easing = motion.EmphasizedDecelerate)) +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(motion.State)),
                exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(motion.Press)) +
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(motion.Press))) {
            Column(Modifier.bringIntoViewRequester(reasonView), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                FinanceField(if (decision == "invalid") "Alasan (wajib)" else "Catatan (opsional)",
                    reason, { reason = it.take(1000) }, "verification-reason", singleLine = false)
                error?.let { FarmAttention(it) }
                Button(onClick = { confirmation = true }, enabled = !busy && reason.length <= 1000 &&
                    (decision != "invalid" || reason.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch)
                    .testTag("verification-review")) { Text("Simpan keputusan") }
            } }
        }
    }
    // The summary before the decision is sent: what was decided, for which revision, and the reason or note.
    if (confirmation && decision != null) AlertDialog(onDismissRequest = { if (!busy) confirmation = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Ringkasan keputusan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                FarmFact("Keputusan", if (decision == "invalid") "Perlu diperbaiki" else "Sesuai", prominent = true)
                FarmFact("Revisi", "Revisi $version")
                FarmFact(if (decision == "invalid") "Alasan" else "Catatan", reason.trim().ifBlank { "Tidak ada" })
            }
        },
        confirmButton = { Button(onClick = { save(true) }, enabled = !busy, shape = FarmShape.Control,
            modifier = Modifier.testTag("verification-save")) { Text(if (busy) "Menyimpan…" else "Simpan") } },
        dismissButton = { TextButton(onClick = { confirmation = false }, enabled = !busy) { Text("Ubah") } })
}

@Composable
private fun DecisionFacts(decision: JSONObject) {
    Text(listOfNotNull(decision.text("reviewer_name"), decision.text("reviewed_at")?.let(::timeLabel)).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall)
    decision.text("reason")?.let { Text(it) }
}

@Composable
internal fun ActivityVerificationFeedback(session: FinanceSession, record: LocalRecord, kind: ReviewKind = ReviewKind.ACTIVITY) {
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
        reviews = attempt { repository.cachedFeedback(session, ticket, kind, record) }.getOrDefault(emptyList())
        lastKnown = reviews.isNotEmpty()
        attempt { repository.read(session, ticket, kind.detail(record.id), mapOf(kind.versionKey to version.toString())) }
            .onSuccess { entry ->
                reviews = (entry.rows("history") + listOfNotNull(entry.optJSONObject("decision")) + reviews).distinctBy { it.getString("id") }
                status = entry.getString("status")
                lastKnown = false
            }.onFailure {
                status = when (body.text("recorded_by_role")) {
                    "worker" -> "pending"
                    "manager" -> "manager"
                    else -> "legacy"
                }
                lastKnown = true
                if (it is FinanceApiFailure && it.status in setOf(401, 403, 404)) {
                    reviews = emptyList()
                    lastKnown = false
                    status = null
                }
            }
    }
    val decision = reviews.firstOrNull { it.optLong(kind.versionKey) == version }
    FarmSection(title = "Cek pengelola") {
        Text(verificationLabel(decision?.optString("decision") ?: status.orEmpty()))
        if (lastKnown) Text("Status berdasarkan data tersimpan di perangkat.", style = MaterialTheme.typography.bodySmall)
        decision?.let { DecisionFacts(it) }
        reviews.filter { it.optLong(kind.versionKey) < version }.forEach {
            Text("Revisi ${it.optLong(kind.versionKey)} · ${verificationLabel(it.optString("decision"))}")
            DecisionFacts(it)
        }
    }
}

@Composable
private fun VerificationCard(kind: ReviewKind, entry: JSONObject, activity: JSONObject, tag: String, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val names = entry.getJSONObject("context")
    val type = activity.text("activity_type")
    val measurement = kind == ReviewKind.ACTIVITY && type == ActivityType.MEASUREMENT.wire
    val cancelled = activity.optString("record_state") == "cancelled"
    val statusCode = entry.getString("status")
    val chip = if (cancelled) "Dibatalkan" else verificationLabel(statusCode)
    val tone = when {
        cancelled -> FarmTone.Neutral
        statusCode == "pending" -> FarmTone.Info
        statusCode == "valid" -> FarmTone.Good
        statusCode == "invalid" -> FarmTone.Error
        else -> FarmTone.Neutral
    }
    val title = recordLabel(kind, activity)
    val who = listOfNotNull(activity.text("recorded_by_name"), activity.text("occurred_at")?.let(::timeLabel)).joinToString(" · ")
    val context = listOfNotNull(names.text("greenhouse"), names.text("season"), names.text("group")).joinToString(" · ")
    val ec = activity.text("manual_ec")?.let(::measureText)
    val ppm = activity.text("manual_ppm")?.let(::measureText)
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Surface(
        Modifier.fillMaxWidth().testTag(tag).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(title, who.ifBlank { null }, chip, ec?.let { "EC $it mS/cm" }, ppm?.let { "PPM $it ppm" }, context.ifBlank { null }).joinToString(", ")
            },
        shape = FarmShape.Card, color = colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.Top) {
                FarmIconTile(if (kind == ReviewKind.HARVEST) FarmIcon.Basket else if (measurement) FarmIcon.Droplet else FarmIcon.Schedule,
                    tone = if (measurement) FarmTone.Info else FarmTone.Neutral, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(who, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (!large) FarmStatusChip(chip, tone = tone)
            }
            if (large) FarmStatusChip(chip, tone = tone)
            if (ec != null || ppm != null) Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                MeasureBox("EC", ec?.let { "$it mS/cm" } ?: "belum ada", Modifier.weight(1f))
                MeasureBox("PPM", ppm?.let { "$it ppm" } ?: "belum ada", Modifier.weight(1f))
            } else activity.text("notes")?.let {
                Text(it, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            if (context.isNotBlank()) Text(context, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun MeasureBox(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, FarmShape.Tile).padding(FarmSpace.Medium)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}
