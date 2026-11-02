package com.inofarm.jogja.feature.activities

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
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
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ManualMeasurement
import com.inofarm.jogja.feature.timeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable internal fun MeasurementDashboard(runtime:FinanceRuntime,session:FinanceSession,gh:String,group:String,changed:Any?,lastSync:Long?) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val android=LocalContext.current
    var pair by remember(session.scope.key,gh,group) {mutableStateOf<ManualMeasurement?>(null)}
    var loading by remember(session.scope.key,gh,group) {mutableStateOf(true)}
    var message by remember(session.scope.key,gh,group) {mutableStateOf<String?>(null)}
    var offline by remember {mutableStateOf(false)}
    var today by remember {mutableStateOf(LocalDate.now(ActivityDraft.WIB))}
    LaunchedEffect(session.scope.key,gh,group,changed,lastSync) {
        val ticket=runtime.vault.generation
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                today=LocalDate.now(ActivityDraft.WIB)
                val result=runCatching {withContext(Dispatchers.IO) {runtime.store.latestMeasurement(session.scope.key,gh,group,today)}}
                if(!runtime.vault.matches(ticket,session.scope)) {pair=null;return@repeatOnLifecycle}
                pair=result.getOrNull();message=result.exceptionOrNull()?.let {"Pengukuran tersimpan belum dapat dibaca. Coba buka kembali."};loading=false
                val manager=android.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                offline=manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)!=true
                delay(5_000) // Background pull, cancellation, revocation and WIB midnight without navigating away.
            }
        }
    }
    MeasurementCards(pair,today,loading)
    message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    Text(if(offline) "Offline · menampilkan data tersimpan." else "Data tersimpan · gunakan Sinkronkan untuk memeriksa server.",style=MaterialTheme.typography.bodySmall)
    Text(lastSync?.let {"Sinkron terakhir: ${timeLabel(java.time.Instant.ofEpochMilli(it).toString())}"} ?: "Belum pernah tersinkron.",style=MaterialTheme.typography.bodySmall)
}

@Composable internal fun MeasurementCards(pair:ManualMeasurement?,today:LocalDate,loading:Boolean=false) {
    Text("Pengukuran manual",style=MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        for((label,value,unit) in listOf(Triple("EC",pair?.ec,"mS/cm"),Triple("PPM",pair?.ppm,"ppm"))) {
            ElevatedCard(Modifier.weight(1f).testTag("measurement-card-${label.lowercase()}")) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(label,style=MaterialTheme.typography.titleMedium)
                    Text(value ?: if(loading) "Memuat…" else if(pair!=null) "EC belum tersedia" else "Belum ada pengukuran")
                    Text(unit,style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if(pair!=null) {
        Text(pair.message(today))
        Text("${timeLabel(pair.at.toString())} · input manual")
        Text(if(pair.queued) "Belum tersinkron · pencatat akun ini" else "Diterima server · pencatat: ${pair.recorder ?: "nama belum tersedia"}")
    }
}
