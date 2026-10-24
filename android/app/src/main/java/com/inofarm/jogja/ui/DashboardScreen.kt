package com.inofarm.jogja.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.data.*
import com.inofarm.jogja.displayTime
import java.time.OffsetDateTime

internal fun ageAt(source: String?, checked: String?): String {
    if (source == null || checked == null) return "Belum dapat ditentukan"
    val seconds = java.time.Duration.between(OffsetDateTime.parse(source), OffsetDateTime.parse(checked)).seconds
    return if (seconds < 0) "Urutan waktu tidak konsisten" else "$seconds detik"
}

@Composable internal fun DashboardScreen(state: OwnerState, settings: ()->Unit = {}) {
    val dashboard = state.dashboard
    val resource = state.resource("dashboard")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FarmSpace.Page),
        verticalArrangement = Arrangement.spacedBy(FarmSpace.Large)) {
        PageHeader("Dashboard")
        ResourceFeedback(resource, dashboard != null)
        BotanicalHero(Modifier.testTag("dashboardHero").clickable(onClick=settings)) {
            Row(Modifier.padding(14.dp).heightIn(min=130.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                IconMedallion(FarmGlyph.Drop,size=40.dp,dark=true)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("Tandon nutrisi",style=MaterialTheme.typography.headlineMedium,color=Color.White)
                    Text("Fase aktif · ${dashboard?.activePhase?.wire?.replaceFirstChar {it.uppercase()} ?: "Belum ditetapkan"}",style=MaterialTheme.typography.titleMedium,color=Color.White)
                    Text("Kontak terakhir · ${clockTime(dashboard?.lastContactAt)}",style=MaterialTheme.typography.bodyMedium,color=Color.White)
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) { IconMedallion(FarmGlyph.Arrow,size=32.dp) }
        }
        val connection=when(if(resource.lastKnown || resource.loading) "unchecked" else dashboard?.connection) {
            "never_seen" -> "Perangkat belum pernah terhubung"
            "recent_contact" -> "Kontak perangkat masih baru pada pemeriksaan server"
            "disconnected" -> "Perangkat terputus menurut batas owner"
            else -> "Status koneksi belum dapat ditentukan"
        }
        if(dashboard?.connection!="recent_contact" || resource.lastKnown || resource.loading)
            Text(connection,style=MaterialTheme.typography.bodySmall,color=if(dashboard?.connection=="disconnected" && !resource.lastKnown && !resource.loading) FarmColors.Error else FarmColors.Muted)
        if(resource.lastKnown) Text("Last-known · belum diperiksa ulang",style=MaterialTheme.typography.bodySmall,color=FarmColors.Warning)
        if (dashboard?.latestTelemetryReceipt?.clockTrusted == false) {
            Banner("Clock perangkat belum dipercaya pada kiriman terbaru. Bacaan terpercaya terakhir tetap ditampilkan.", true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Text("Monitoring sensor", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("Capture sensor · ${clockTime(dashboard?.telemetry?.sourceAt)}",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
            SensorMetrics(dashboard?.telemetry?.readings.orEmpty())
            Text("Usia capture pada pemeriksaan server: ${ageAt(dashboard?.telemetry?.sourceAt, dashboard?.serverTime)}",
                style = MaterialTheme.typography.bodySmall, color = FarmColors.Muted)
        }
        Text("Nutrisi terakhir",style=MaterialTheme.typography.titleLarge)
        Surface(shape=FarmShape.Card,color=FarmColors.Surface) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            (dashboard?.latestRecordReceipt ?: dashboard?.latestRecord)?.let { record ->
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconMedallion(FarmGlyph.Flask)
                    Column(Modifier.weight(1f)) {
                        Text("${ppmText(record.manualPpm)} ppm",style=MaterialTheme.typography.headlineMedium)
                        Text("Input manual ESP32",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
                        Text("${clockTime(record.sourceAt)} · ${record.phase.wire.replaceFirstChar {it.uppercase()}}",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(record.status.label,style=MaterialTheme.typography.titleSmall,color=if(record.status==ValidationStatus.Valid) green else FarmColors.Warning)
                        Text(record.checks.firstOrNull {it.result!="pass"}?.message ?: "Validasi server",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
                    }
                }
                DetailDisclosure("catatan dan seluruh alasan") {RecordCard(record)}
            }
                ?: Text("Belum ada pencatatan nutrisi dari perangkat.", style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted)
        }
        }
        Section("Tren monitoring") {
            ResourceFeedback(state.resource("telemetry"), state.telemetry.isNotEmpty())
            Trend(state.telemetry, state.history)
        }
        Section("Sumber dan penerimaan data") {
            if(dashboard?.connection=="recent_contact" && !resource.lastKnown && !resource.loading) MetaLine("Status koneksi pada pemeriksaan server",connection)
            MetaLine("Dashboard diperiksa",displayTime(resource.fetchedAt))
            MetaLine("Capture sensor",displayTime(dashboard?.telemetry?.sourceAt))
            MetaLine("Kontak terakhir perangkat", displayTime(dashboard?.lastContactAt))
            MetaLine("Usia kontak pada pemeriksaan server", ageAt(dashboard?.lastContactAt, dashboard?.serverTime))
            MetaLine("Bacaan diterima server", displayTime(dashboard?.telemetry?.receivedAt))
            dashboard?.latestTelemetryReceipt?.let { incoming ->
                HorizontalDivider(color = FarmColors.Divider)
                Text("Kiriman monitoring terbaru", style = MaterialTheme.typography.titleSmall)
                Text("Diterima ${displayTime(incoming.receivedAt)}", style = MaterialTheme.typography.bodySmall)
                Text(if (incoming.clockTrusted) "Capture ${displayTime(incoming.sourceAt)}"
                    else "Clock perangkat belum dipercaya; kiriman ini tidak mengganti bacaan terpercaya.",
                    style = MaterialTheme.typography.bodySmall, color = if (incoming.clockTrusted) FarmColors.Muted else FarmColors.Warning)
                incoming.readings.forEach { (key, value) ->
                    Text("${metrics.find { it.key == key }?.label}: ${qualityLabel(value)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(LocalBottomSpace.current))
    }
}

internal fun qualityLabel(value: SensorValue?): String = when (value?.quality) {
    SensorQuality.Ok -> "Bacaan tersedia"
    SensorQuality.Missing -> "Bacaan tidak tersedia"
    SensorQuality.Failed -> "Sensor gagal"
    SensorQuality.Uncalibrated -> "Belum dikalibrasi"
    null -> "Belum ada bacaan"
}

@Composable internal fun SensorMetrics(readings: Map<String, SensorValue>) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 310.dp && fontScale <= 1.3f) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            metrics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                    row.forEach { metric ->
                        SensorMetric(metric, readings[metric.key], Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SensorMetric(metric: Metric, value: SensorValue?, modifier: Modifier) {
    var details by rememberSaveable {mutableStateOf(false)}
    Surface(modifier.testTag("metric-${metric.key}").clickable {details=true},shape=FarmShape.Card,color=FarmColors.Soft) {
        Row(Modifier.padding(10.dp).heightIn(min=68.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            IconMedallion(when(metric.key) {"waterTemperatureC"->FarmGlyph.Thermometer;"solutionPh"->FarmGlyph.Drop;"airTemperatureC"->FarmGlyph.Room;else->FarmGlyph.Leaf},size=34.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(metric.label,style=MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(value?.value?.let {sensorText(it)} ?: "—",style=MaterialTheme.typography.headlineLarge.copy(fontSize=30.sp,fontFeatureSettings="tnum"))
                    if(metric.unit!="pH") Text(metric.unit,Modifier.padding(top=5.dp),style=MaterialTheme.typography.titleMedium)
                }
                if(value?.quality!=SensorQuality.Ok) Text(qualityLabel(value),style=MaterialTheme.typography.bodySmall,color=FarmColors.Warning)
            }
        }
    }
    if(details) AlertDialog(onDismissRequest={details=false},title={Text(metric.label)},text={Text("${value?.value?.let {sensorText(it)} ?: "—"} ${metric.unit}\n${qualityLabel(value)}\nKualitas bacaan tidak menyatakan kondisi agronomis normal.")},confirmButton={TextButton({details=false}) {Text("Tutup")}})
}

internal fun clockTime(value: String?): String = value?.let {java.time.Instant.from(java.time.OffsetDateTime.parse(it)).atZone(java.time.ZoneId.of("Asia/Jakarta")).format(java.time.format.DateTimeFormatter.ofPattern("HH.mm 'WIB'"))} ?: "—"
internal fun sensorText(value: Double): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("id-ID")).apply {maximumFractionDigits=1}.format(value)
internal fun ppmText(value: Double): String = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("id-ID")).apply {maximumFractionDigits=3;isGroupingUsed=false}.format(value)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun Trend(telemetry: List<Telemetry>, records: List<NutrientRecord>) {
    var selected by rememberSaveable { mutableStateOf(0) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) { metrics.forEachIndexed { index, metric -> FilterChip(selected == index, { selected = index }, label = { Text(metric.label) }, modifier = Modifier.heightIn(min = FarmSpace.Touch), shape = FarmShape.Control) }; FilterChip(selected==4,{ selected=4 },label={ Text("PPM manual") },modifier=Modifier.heightIn(min=FarmSpace.Touch),shape=FarmShape.Control) }
    val points = remember(telemetry, records, selected) { if (selected==4) records.filter { it.sourceAt != null && it.clockTrusted }.map { OffsetDateTime.parse(it.sourceAt).toInstant().toEpochMilli().toDouble() to it.manualPpm }.sortedBy { it.first }
        else telemetry.filter { it.sourceAt != null && it.clockTrusted }.map { OffsetDateTime.parse(it.sourceAt).toInstant().toEpochMilli().toDouble() to it.readings[metrics[selected].key]?.value }.sortedBy { it.first } }
    val actual = remember(points) { points.mapNotNull { it.second } }
    if (actual.isEmpty()) { Text("Belum ada data tren untuk metrik ini."); return }
    val min = actual.min(); val max = actual.max(); val span = (max-min).takeIf { it>0 } ?: 1.0
    val start = points.first().first; val end = points.last().first; val timeSpan = (end-start).takeIf { it>0 } ?: 1.0
    Text("${actual.size} bacaan · ${if(selected==4) "PPM manual, halaman riwayat dimuat" else metrics[selected].unit + ", maksimal 100 event terbaru"}",style=MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription="Tren ${if(selected==4) "PPM manual" else metrics[selected].label}: ${actual.size} bacaan, minimum $min, maksimum $max. Rentang ${displayTime(java.time.Instant.ofEpochMilli(start.toLong()).toString())} sampai ${displayTime(java.time.Instant.ofEpochMilli(end.toLong()).toString())}. Celah menandakan bacaan tidak tersedia." }) {
        drawLine(FarmColors.Divider,Offset(8f,size.height-8),Offset(size.width-8,size.height-8))
        fun point(value: Pair<Double,Double?>) = Offset((8+(value.first-start)/timeSpan*(size.width-16)).toFloat(),(size.height-8-(value.second!!-min)/span*(size.height-16)).toFloat())
        points.zipWithNext().forEach { (a,b) -> if(a.second!=null && b.second!=null) drawLine(green,point(a),point(b),4f) }
        points.filter { it.second!=null }.forEach { drawCircle(green,4f,point(it)) }
    }
    Text("Min ${"%.1f".format(min)} · Maks ${"%.1f".format(max)}",style=MaterialTheme.typography.bodySmall)
}

