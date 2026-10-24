package com.inofarm.jogja.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.data.*
import com.inofarm.jogja.displayTime

@Composable internal fun Banner(text: String, error: Boolean = false) {
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = if (error) FarmColors.ErrorSurface else FarmColors.Soft, shape = FarmShape.Control) {
        Text(text, Modifier.padding(FarmSpace.Medium), color = if (error) FarmColors.Error else green,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun PageHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted) }
    }
}

@Composable internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = FarmColors.Surface,
        tonalElevation = FarmDepth.Content) {
        Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable internal fun DetailDisclosure(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(FarmMotion.State))) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()
            .heightIn(min = FarmSpace.Touch).semantics { stateDescription = if (expanded) "Terbuka" else "Tertutup" }) {
            Text(if (expanded) "Tutup $title" else "Lihat $title", modifier = Modifier.weight(1f))
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small), content = content)
    }
}

@Composable internal fun MetaLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FarmColors.Muted)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun RecordCard(record: NutrientRecord) {
    val statusColor=when(record.status) {ValidationStatus.Valid->green;ValidationStatus.Issues->FarmColors.Error;ValidationStatus.Incomplete->FarmColors.Warning}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconMedallion(FarmGlyph.Drop)
            Column(Modifier.weight(1.1f)) {
                Text("Nilai nutrisi (PPM)",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
                Text("${ppmText(record.manualPpm)} ppm",style=MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings="tnum"))
                Text("Input manual ESP32",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
            }
            Column(Modifier.weight(.9f)) {
                Text("Fase perangkat",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
                Text(record.phase.wire.replaceFirstChar {it.uppercase()},style=MaterialTheme.typography.titleMedium)
            }
        }
        HorizontalDivider(color=FarmColors.Divider)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconMedallion(FarmGlyph.People)
            Column {
                Text("Pekerja terjadwal",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)
                Text(record.workerName ?: when(record.assignmentStatus) {"time_unknown"->"Waktu tidak dapat ditentukan";"conflict"->"Jadwal konflik";else->"Belum terjadwal"},style=MaterialTheme.typography.titleMedium,color=if(record.workerName==null) FarmColors.Warning else FarmColors.Ink)
            }
        }
        Surface(Modifier.fillMaxWidth(),shape=FarmShape.Control,color=when(record.status) {ValidationStatus.Valid->FarmColors.Soft;ValidationStatus.Issues->FarmColors.ErrorSurface;else->FarmColors.WarningSurface}) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {FarmIcon(FarmGlyph.Info,tint=statusColor);Text(record.status.label,style=MaterialTheme.typography.titleMedium,color=statusColor)}
                record.checks.forEach {check ->
                    Text("• ${when(check.result) {"pass"->"Lulus · ";"warning"->"Peringatan · ";"issue"->"Masalah · ";else->""}}${check.message}",style=MaterialTheme.typography.bodyMedium,color=when(check.result) {"pass"->FarmColors.Muted;"issue"->FarmColors.Error;else->FarmColors.Warning})
                }
            }
        }
        HorizontalDivider(color=FarmColors.Divider)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconMedallion(FarmGlyph.Database,size=36.dp)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${if(record.clockTrusted) "Konfirmasi perangkat" else "Waktu perangkat belum dipercaya"} · ${clockTime(record.sourceAt)}",style=MaterialTheme.typography.bodySmall,color=if(record.clockTrusted) FarmColors.Muted else FarmColors.Warning)
                Text("Diterima server · ${clockTime(record.receivedAt)}",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
            }
        }
        HorizontalDivider(color=FarmColors.Divider)
        DetailDisclosure("metadata") {
            MetaLine("ID catatan",record.id.toString())
            MetaLine("Konfirmasi",displayTime(record.sourceAt))
            MetaLine("Diterima server",displayTime(record.receivedAt))
            MetaLine("Dievaluasi server",displayTime(record.evaluatedAt))
            MetaLine("Pengukuran handheld",record.measuredAt?.let(::displayTime) ?: "Waktu belum diketahui")
            MetaLine("Fase owner saat catatan",record.ownerPhase?.wire ?: "Belum tersedia")
            Text("Aturan #${record.configVersion ?: "—"} · fase #${record.phaseVersion ?: "—"}",style=MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun integerFieldError(value: String, range: IntRange): String? =
    if (value.isBlank()) null else if (value.toIntOrNull()?.let { it in range } == true) null
    else "Isi bilangan bulat ${range.first}–${range.last}."

@Composable internal fun Field(
    label: String, value: String, change: (String) -> Unit, numeric: Boolean = false,
    helper: String? = null, error: String? = null, keyboard: KeyboardType? = null
) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
        singleLine = true, shape = FarmShape.Control, isError = error != null,
        supportingText = if (error != null || helper != null) ({ Text(error ?: helper.orEmpty()) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard ?: if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable internal fun RangeFields(label: String, bound: BoundDraft, change: (BoundDraft) -> Unit) {
    RangeFields(label, bound, nonnegative = false, change = change)
}

@Composable internal fun RangeFields(label: String, bound: BoundDraft, nonnegative: Boolean, change: (BoundDraft) -> Unit) {
    val min = bound.min.toDoubleOrNull(); val max = bound.max.toDoubleOrNull()
    val started = bound.min.isNotBlank() || bound.max.isNotBlank()
    val error = when {
        !started -> null
        min == null || max == null || !min.isFinite() || !max.isFinite() -> "Isi kedua batas dengan angka finite."
        min > max -> "Minimum harus ≤ maksimum."
        nonnegative && min < 0 -> "PPM tidak boleh negatif."
        else -> null
    }
    Text(label, style = MaterialTheme.typography.titleSmall)
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        Field("Minimum", bound.min, { change(bound.copy(min = it)) }, true, error = error)
        Field("Maksimum", bound.max, { change(bound.copy(max = it)) }, true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DayPicker(days: Set<Int>, change: (Set<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            FilterChip(day in days, { change(if (day in days) days - day else days + day) },
                label = { Text(listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")[day - 1]) },
                modifier = Modifier.heightIn(min = FarmSpace.Touch), shape = FarmShape.Control,
                colors=FilterChipDefaults.filterChipColors(containerColor=FarmColors.Soft,labelColor=FarmColors.Ink,selectedContainerColor=green,selectedLabelColor=androidx.compose.ui.graphics.Color.White),
                border=FilterChipDefaults.filterChipBorder(enabled=true,selected=day in days,borderColor=FarmColors.Divider,selectedBorderColor=green))
        }
    }
}

@Composable internal fun ResourceFeedback(resource: ResourceState, hasData: Boolean) {
    if (!resource.loading && resource.error == null && !resource.lastKnown) return
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        if (resource.loading) {
            Text(if (hasData) "Memperbarui data…" else "Memuat data…", style = MaterialTheme.typography.bodySmall, color = FarmColors.Muted)
            LoadingBar()
            if (!hasData) Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                repeat(3) { Box(Modifier.fillMaxWidth().height(18.dp).background(FarmColors.Soft, FarmShape.Control)) }
            }
        }
        resource.error?.let { Banner(it, true) }
        if (resource.lastKnown && resource.fetchedAt != null)
            Banner("Data terakhir dimuat ${displayTime(resource.fetchedAt)}. Refresh diperlukan untuk memeriksa server.")
    }
}

@Composable internal fun LoadingBar() {
    Box(Modifier.fillMaxWidth().height(4.dp).background(FarmColors.Soft, FarmShape.Control)
        .semantics { contentDescription = "Sedang memuat" })
}
