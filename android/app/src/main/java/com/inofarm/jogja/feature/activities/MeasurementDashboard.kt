package com.inofarm.jogja.feature.activities

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ManualMeasurement
import com.inofarm.jogja.feature.timeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable internal fun MeasurementDashboard(
    runtime: FinanceRuntime, session: FinanceSession, gh: String, group: String, changed: Any?,
    lastSync: Long?, syncMessage: String?, lastKnown: Boolean
) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val android=LocalContext.current
    var pair by remember(session.scope.key,gh,group) {mutableStateOf<ManualMeasurement?>(null)}
    var loading by remember(session.scope.key,gh,group) {mutableStateOf(true)}
    var message by remember(session.scope.key,gh,group) {mutableStateOf<String?>(null)}
    var syncedAt by remember(session.scope.key,gh,group) {mutableStateOf(lastSync)}
    var offline by remember {mutableStateOf(false)}
    var today by remember {mutableStateOf(LocalDate.now(ActivityDraft.WIB))}
    LaunchedEffect(session.scope.key,gh,group,changed,lastSync) {
        val ticket=runtime.vault.generation
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                today=LocalDate.now(ActivityDraft.WIB)
                val result:Result<Pair<ManualMeasurement?,Long?>> = try {
                    Result.success(withContext(Dispatchers.IO) {runtime.store.latestMeasurement(session.scope.key,gh,group,today) to runtime.store.lastSyncedAt(session.scope.key)})
                } catch(cancelled:CancellationException) {throw cancelled}
                catch(error:Exception) {Result.failure(error)}
                if(!runtime.vault.matches(ticket,session.scope)) {pair=null;return@repeatOnLifecycle}
                result.getOrNull()?.let { (latest, synced) ->
                    pair = latest
                    syncedAt = synced
                }
                message=result.exceptionOrNull()?.let {"Pengukuran tersimpan belum dapat dibaca. Coba buka kembali."};loading=false
                val manager=android.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                offline=manager.activeNetwork==null
                delay(5_000) // Background pull, cancellation, revocation and WIB midnight without navigating away.
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        MeasurementCards(pair?.let { it.copy(ec = it.ec?.replace('.', ','), ppm = it.ppm.replace('.', ',')) }, loading)
        message?.let { Notice(it, true) }
        MeasurementSource(pair, today, offline || lastKnown, syncedAt, syncMessage)
    }
}

@Composable internal fun MeasurementCards(pair: ManualMeasurement?, loading: Boolean = false, unavailable: Boolean = false, showTitle: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        if (showTitle) Surface(color = MaterialTheme.colorScheme.background, shape = FarmShape.Control) {
            Text("Pengukuran manual", Modifier.padding(horizontal = FarmSpace.Small, vertical = FarmSpace.Tiny)
                .semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        }
        MeasurementPair(pair, loading, unavailable)
    }
}

@Composable
private fun MeasurementPair(pair: ManualMeasurement?, loading: Boolean, unavailable: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 268.dp || LocalDensity.current.fontScale > 1.3f
        val empty = when {
            loading -> "Memuat…"
            unavailable -> "Belum dapat dibaca"
            else -> "Belum ada pengukuran"
        }
        val ec = pair?.ec ?: if (pair != null) "EC belum tersedia" else empty
        val ppm = pair?.ppm ?: empty
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            MeasurementCard("EC", ec, pair?.ec != null, "mS/cm", Modifier.fillMaxWidth())
            MeasurementCard("PPM", ppm, pair?.ppm != null, "ppm", Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            MeasurementCard("EC", ec, pair?.ec != null, "mS/cm", Modifier.weight(1f))
            MeasurementCard("PPM", ppm, pair?.ppm != null, "ppm", Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MeasurementSource(
    pair: ManualMeasurement?, today: LocalDate, offline: Boolean, syncedAt: Long?, syncMessage: String?,
    snapshotSource: Boolean = false,
) {
    var expanded by rememberSaveable(pair?.id, pair?.at) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(shape = FarmShape.Control, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.fillMaxWidth().animateContentSize(tween(200)).padding(horizontal = FarmSpace.Medium, vertical = FarmSpace.Small),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2) {
                Column(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                    if (pair != null) {
                        Text(if (snapshotSource) timeLabel(pair.at.toString()) else pair.message(today), style = MaterialTheme.typography.labelLarge)
                        Text(if (pair.queued) "Belum terkirim" else "Terkirim",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (!snapshotSource) {
                            val label = when (pair.verification) {
                                "valid" -> "Sudah dicek"
                                "pending" -> "Belum dicek"
                                "manager" -> "Dicatat pengelola"
                                "legacy" -> "Data lama · belum dicek"
                                else -> null
                            }
                            label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                        }
                    }
                    if (offline) Text("Menampilkan data tersimpan.", style = MaterialTheme.typography.bodySmall)
                    Text(syncedAt?.let {
                        val label = timeLabel(java.time.Instant.ofEpochMilli(it).toString())
                        if (snapshotSource) "Diperbarui $label" else "Diperbarui $label"
                    } ?: "Belum pernah diperbarui.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (pair != null) TextButton(onClick = { expanded = !expanded }, shape = FarmShape.Control,
                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("measurement-source")) {
                    Text(if (expanded) "Tutup sumber" else "Lihat sumber")
                }
            }
            if (expanded && pair != null) {
                HorizontalDivider()
                Text("${timeLabel(pair.at.toString())} · input manual", style = MaterialTheme.typography.bodySmall)
                Text(if (pair.queued) "Belum terkirim · pencatat: ${pair.recorder ?: "akun ini"}"
                    else "Terkirim · pencatat: ${pair.recorder ?: "nama belum tersedia"}",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (syncMessage != null) Text(syncMessage, Modifier.testTag("finance-sync-message"),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun MeasurementCard(label: String, value: String, measured: Boolean, unit: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.testTag(if (label == "EC") "measurement-card-ec" else "measurement-card-ppm"),
        shape = FarmShape.Card,
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            Text(value, style = if (measured) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyMedium)
            Text(unit, style = MaterialTheme.typography.labelMedium)
        }
    }
}
