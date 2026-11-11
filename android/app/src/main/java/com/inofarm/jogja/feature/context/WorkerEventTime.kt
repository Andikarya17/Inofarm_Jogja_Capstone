package com.inofarm.jogja.feature.context

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.core.cultivation.ManualTime
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.Notice
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun WorkerEventTime(
    context: CultivationUiState, greenhouseId: String, groupId: String,
    date: String, time: String, onDate: (String) -> Unit, onTime: (String) -> Unit,
    tag: String, error: String?, preserveAge: Boolean, historicalAge: JSONObject?,
) {
    val greenhouse = context.greenhouses.find { it.optString("id") == greenhouseId }
    val group = context.groups.find { it.optString("id") == groupId }
    val season = context.seasons.find { it.optString("id") == group?.optString("season_id") }
    val parents = listOfNotNull(greenhouse?.optString("name"), season?.optString("name"), group?.optString("name"))
    Text(parents.joinToString(" · ").ifBlank { "Pilihan belum tersimpan" }, style = MaterialTheme.typography.titleMedium)
    val age = remember(group, date, time, preserveAge) {
        if (preserveAge || group == null) null else runCatching {
            PlantAge.calculate(group.getString("sowing_date"), group.optString("transplant_date").takeUnless { group.isNull("transplant_date") },
                ManualTime.encode(date, time))
        }.getOrNull()
    }
    if (preserveAge) {
        val hss = historicalAge?.takeUnless { it.isNull("hss") }?.optString("hss") ?: "belum tersedia"
        val hst = historicalAge?.takeUnless { it.isNull("hst") }?.optString("hst") ?: "belum berlaku"
        Text("Acuan historis · HSS $hss · HST $hst", style = MaterialTheme.typography.bodySmall)
    } else {
        Text("HSS ${age?.hss ?: "belum tersedia"} · HST ${age?.hst ?: "belum berlaku"}", style = MaterialTheme.typography.bodySmall)
        age?.reasons?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    val day = remember(date) { runCatching { LocalDate.parse(date) }.getOrNull() }
    val clock = remember(time) { runCatching { LocalTime.parse(time) }.getOrNull() }
    val dayLabel = day?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id-ID"))) ?: "Pilih tanggal"
    var manual by rememberSaveable { mutableStateOf(day == null || clock == null) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        OutlinedButton(onClick = { picker = "date" }, shape = FarmShape.Control,
            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("$tag-pick-date")) {
            Text(if (day == null) dayLabel else "Tanggal $dayLabel")
        }
        OutlinedButton(onClick = { picker = "time" }, shape = FarmShape.Control,
            modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("$tag-pick-time")) {
            Text(if (clock == null) "Pilih jam" else "Jam $time WIB")
        }
    }
    TextButton(onClick = { manual = !manual }, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("$tag-manual-time")) {
        Text(if (manual) "Tutup isian manual" else "Ketik tanggal atau jam")
    }
    Column(Modifier.animateContentSize(tween(180)), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        if (manual) {
            FinanceField("Tanggal kejadian", date, onDate, "$tag-date", error, hint = "YYYY-MM-DD")
            FinanceField("Jam kejadian WIB", time, onTime, "$tag-time", hint = "HH:mm atau HH:mm:ss")
        } else error?.let { Notice(it, true) }
    }

    val android = LocalContext.current
    val updateDate by rememberUpdatedState(onDate)
    val updateTime by rememberUpdatedState(onTime)
    DisposableEffect(picker, android) {
        val dialog = when (picker) {
            "date" -> {
                val initial = day ?: LocalDate.now(ManualTime.WIB)
                DatePickerDialog(android, { _, year, month, dateOfMonth ->
                    updateDate(LocalDate.of(year, month + 1, dateOfMonth).toString())
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
            }
            "time" -> {
                val initial = clock ?: LocalTime.now(ManualTime.WIB)
                TimePickerDialog(android, { _, hour, minute ->
                    val updated = LocalTime.of(hour, minute, initial.second)
                    updateTime(updated.format(DateTimeFormatter.ofPattern(if (time.length > 5) "HH:mm:ss" else "HH:mm")))
                }, initial.hour, initial.minute, true)
            }
            else -> null
        }
        dialog?.setOnDismissListener { picker = null }
        dialog?.show()
        onDispose {
            dialog?.setOnDismissListener(null)
            dialog?.dismiss()
        }
    }
}
