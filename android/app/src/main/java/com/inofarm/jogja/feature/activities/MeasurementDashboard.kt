package com.inofarm.jogja.feature.activities

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.inofarm.jogja.FinanceRuntime
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ManualMeasurement
import com.inofarm.jogja.feature.timeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable internal fun MeasurementDashboard(runtime:FinanceRuntime,session:FinanceSession,gh:String,group:String,changed:Any?,lastSync:Long?) {
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
    MeasurementCards(pair,today,loading)
    message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    if (offline) Text("Offline · menampilkan data tersimpan.", style = MaterialTheme.typography.bodySmall)
    Text(syncedAt?.let {"Sinkron terakhir: ${timeLabel(java.time.Instant.ofEpochMilli(it).toString())}"} ?: "Belum pernah tersinkron.",style=MaterialTheme.typography.bodySmall)
}

@Composable internal fun MeasurementCards(pair:ManualMeasurement?,today:LocalDate,loading:Boolean=false) {
    Text("Pengukuran manual",style=MaterialTheme.typography.titleLarge)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 268.dp || LocalDensity.current.fontScale > 1.3f
        val ec = pair?.ec ?: if (loading) "Memuat…" else if (pair != null) "EC belum tersedia" else "Belum ada pengukuran"
        val ppm = pair?.ppm ?: if (loading) "Memuat…" else "Belum ada pengukuran"
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            MeasurementCard("EC", ec, pair?.ec != null, "mS/cm", Modifier.fillMaxWidth())
            MeasurementCard("PPM", ppm, pair?.ppm != null, "ppm", Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            MeasurementCard("EC", ec, pair?.ec != null, "mS/cm", Modifier.weight(1f))
            MeasurementCard("PPM", ppm, pair?.ppm != null, "ppm", Modifier.weight(1f))
        }
    }
    if(pair!=null) {
        Text(pair.message(today))
        Text("${timeLabel(pair.at.toString())} · input manual")
        Text(if(pair.queued) "Belum tersinkron · pencatat: ${pair.recorder ?: "akun ini (menunggu penerimaan server)"}" else "Diterima server · pencatat: ${pair.recorder ?: "nama belum tersedia"}")
    }
}

@Composable
private fun MeasurementCard(label: String, value: String, measured: Boolean, unit: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val ec = label == "EC"
    Surface(
        modifier.testTag(if (ec) "measurement-card-ec" else "measurement-card-ppm"),
        shape = FarmShape.Card,
        color = if (ec) colors.primaryContainer else colors.tertiaryContainer,
        contentColor = if (ec) colors.onPrimaryContainer else colors.onTertiaryContainer
    ) {
        Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value, style = if (measured) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyMedium)
            Text(unit, style = MaterialTheme.typography.labelMedium)
        }
    }
}
