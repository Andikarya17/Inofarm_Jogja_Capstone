package com.inofarm.jogja.feature

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.feature.activities.ActivityUiState
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.recovery.LocalRecoveryActions
import java.time.Instant

internal val SyncState.needsReview: Boolean
    get() = when (this) {
        SyncState.CONFLICT, SyncState.REJECTED, SyncState.AUTH_REQUIRED,
        SyncState.SCOPE_REVOKED, SyncState.EPOCH_REVIEW -> true
        else -> false
    }

@Composable
internal fun RecordStatus(state: SyncState) {
    val colors = MaterialTheme.colorScheme
    val color by animateColorAsState(when {
        state.needsReview -> colors.error
        state == SyncState.ACCEPTED -> colors.primary
        else -> colors.onSurfaceVariant
    }, tween(FarmMotion.State), label = "Status catatan")
    Text(
        state.label(),
        style = MaterialTheme.typography.labelLarge,
        color = color
    )
}

private fun QueuedOperation.syncState() = SyncState.entries.find { it.name == state }

@Composable
internal fun QueueScreen(
    activity: ActivityUiState,
    context: CultivationUiState,
    expired: Boolean,
    reviewOperation: String?,
    reviewError: String?,
    opening: Boolean,
    onReload: () -> Unit,
    onReview: (QueuedOperation) -> Unit,
    canReview: (QueuedOperation) -> Boolean,
    focusOperation: String? = null,
    onVerification: (() -> Unit)? = null,
) {
    val queue = activity.queue
    val displayOrder = remember(queue, focusOperation) {
        queue.sortedWith(compareBy({ it.id != focusOperation }, { it.syncState()?.needsReview != true }))
    }
    FarmMenuList(Modifier.testTag("finance-queue-list")) {
        item("queue-header") {
            FarmMenuHeader(if (onVerification == null) "Antrean" else "Pengiriman saya",
                "Pengajuan lokal dan tanggapan server untuk akun ini.") {
                if (onVerification != null) TextButton(onClick = onVerification, modifier = Modifier.heightIn(min = FarmSpace.Touch)) {
                    Text("Kembali ke verifikasi")
                }
                if (activity.queueLoaded && activity.queueLoadError == null && queue.isNotEmpty()) {
                    val counts = listOf(
                        queue.count { it.syncState()?.needsReview == true } to "Perlu ditinjau",
                        queue.count { it.state == SyncState.QUEUED.name } to "Menunggu",
                        queue.count { it.state == SyncState.ACCEPTED.name } to "Diterima"
                    ).filter { it.first > 0 }
                    if (counts.isNotEmpty()) {
                        FarmPanel {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Section),
                                verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                                counts.forEach { (count, label) ->
                                    Column {
                                        Text(count.toString(), style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary)
                                        Text(label, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    if (queue.size == 1000) Text("Menampilkan 1.000 pengajuan terbaru.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (expired) item("queue-session") { ExpiredSessionNotice() }
        else if (context.message != null || context.lastSyncedAt != null) item("queue-sync") {
            FarmPanel {
                context.message?.let {
                    Text(it, Modifier.testTag("finance-sync-message"), style = MaterialTheme.typography.bodyMedium)
                }
                context.lastSyncedAt?.let {
                    Text("Sinkron terakhir ${timeLabel(Instant.ofEpochMilli(it).toString())}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when {
            activity.queueLoadError != null -> item("queue-error") {
                FarmPanel(title = "Antrean belum dapat dimuat") {
                    Text(activity.queueLoadError)
                    OutlinedButton(onClick = onReload, shape = FarmShape.Control,
                        modifier = Modifier.heightIn(min = FarmSpace.Touch)) { Text("Muat ulang antrean") }
                }
            }
            !activity.queueLoaded -> item("queue-loading") {
                FarmPanel {
                    Text("Memuat antrean…")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            queue.isEmpty() -> item("queue-empty") {
                FarmPanel(title = "Belum ada operasi dalam antrean.") {
                    Text("Pengajuan akan muncul di sini setelah Anda memilih Simpan lokal & antrekan.")
                }
            }
        }
        items(displayOrder, key = { it.id }) { operation ->
            QueueRecord(operation, opening, reviewError.takeIf { reviewOperation == operation.id },
                if (canReview(operation)) ({ onReview(operation) }) else null, operation.id == focusOperation)
        }
    }
}

@Composable
private fun QueueRecord(operation: QueuedOperation, opening: Boolean, reviewError: String?, onReview: (() -> Unit)?, initiallyExpanded: Boolean) {
    val kind = operation.envelope.optString("entity_type")
    val state = operation.syncState()
    val label = OperationalKind.entries.find { it.wire == kind }?.label ?: when (kind) {
        "activity_review" -> "keputusan verifikasi"
        "season_report" -> "evaluasi musim"
        "investment_scenario" -> "skenario investasi"
        "investment_report" -> "evaluasi investasi"
        "activity_attachment", "harvest_attachment", "expense_attachment" -> "lampiran"
        "seasons" -> "musim"
        else -> "catatan"
    }
    val payload = operation.envelope.optJSONObject("payload")
    val time = listOf("occurred_at", "paid_at").firstNotNullOfOrNull { key ->
        payload?.optString(key)?.takeUnless { payload.isNull(key) || it.isBlank() }
    }
    val recovery = LocalRecoveryActions.current
    val reviewReceipt = recovery?.canReview?.invoke(operation) == true
    var expanded by rememberSaveable(operation.id) { mutableStateOf(initiallyExpanded) }
    FarmRecordCard(Modifier.animateContentSize(tween(FarmMotion.State))) {
        if (state == SyncState.ACCEPTED && reviewReceipt) {
            Text("Penerimaan historis", style = MaterialTheme.typography.labelMedium)
            RecordStatus(SyncState.EPOCH_REVIEW)
        } else if (state != null) RecordStatus(state)
        else Text(if (operation.state == "SUPERSEDED") "Digantikan setelah ditinjau" else "Perlu ditinjau",
            style = MaterialTheme.typography.labelMedium)
        Text(label.replaceFirstChar { it.uppercaseChar() }, style = MaterialTheme.typography.titleMedium)
        time?.let { Text(timeLabel(it), style = MaterialTheme.typography.bodySmall) }
        operation.error?.let { ErrorReasons(it) }
        reviewError?.let { Notice(it,true) }
        when (state) {
            SyncState.AUTH_REQUIRED -> Text("Masuk kembali dengan akun yang sama untuk melanjutkan pengiriman.")
            SyncState.SCOPE_REVOKED -> Text("Hubungi pengelola untuk memeriksa penugasan. Pengajuan tetap tersimpan.")
            SyncState.EPOCH_REVIEW -> Text("Pengiriman ditahan sampai tanda terima diperiksa setelah pemulihan.")
            else -> Unit
        }
        if (reviewReceipt) OutlinedButton(
            onClick = { recovery.review(operation) }, shape = FarmShape.Control,
            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("recovery-review-${operation.id}")
        ) { Text("Periksa tanda terima setelah pemulihan") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            if (onReview != null) FilledTonalButton(
                onClick = onReview, enabled = !opening, shape = FarmShape.Control,
                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("finance-review-${operation.id}")
            ) { Text("Tinjau catatan") }
            TextButton(
                onClick = { expanded = !expanded }, shape = FarmShape.Control,
                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("queue-operation-${operation.id}")
            ) { Text(if (expanded) "Tutup rincian operasi" else "Rincian operasi") }
        }
        if (expanded) {
            HorizontalDivider()
            FarmFact("ID operasi", operation.id)
            FarmFact("ID catatan", operation.entityId)
            FarmFact("Percobaan pengiriman", operation.attempts.toString())
            if (kind == "activity_review" && payload != null) {
                FarmFact("Revisi kegiatan", payload.optLong("activity_version").toString())
                FarmFact("Keputusan", if (payload.optString("decision") == "valid") "Valid" else "Tidak valid")
                payload.optString("reason").takeUnless { payload.isNull("reason") || it.isBlank() }?.let { FarmFact("Alasan", it) }
            } else if (state == SyncState.ACCEPTED) Text("Server telah menerima pengajuan. Periksa detail catatan untuk hasil pembandingan dengan acuan.")
        }
    }
}
