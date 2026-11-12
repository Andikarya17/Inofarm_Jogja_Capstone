package com.inofarm.jogja.feature

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.inofarm.jogja.core.design.FarmControlIcon
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.dateLabel
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import java.time.LocalDate
import java.time.LocalTime

/** The platform dialog theme that follows the app's light or dark scheme. */
private fun pickerTheme(dark: Boolean): Int =
    if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert

/**
 * A date field that still takes typed text (YYYY-MM-DD) and also opens the Android date dialog from an icon.
 * The readable date shows below the field once the text is a valid date; [hint] shows otherwise.
 */
@Composable
internal fun FinanceDateField(
    label: String, value: String, onChange: (String) -> Unit, tag: String,
    error: String? = null, hint: String? = null, enabled: Boolean = true
) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val day = runCatching { LocalDate.parse(value.trim()) }.getOrNull()
    val shownHint = day?.let { readable -> if (hint != null && "YYYY" !in hint) "${dateLabel(readable)} · $hint" else dateLabel(readable) } ?: hint
    FinanceField(label, value, onChange, tag, error = error, hint = shownHint,
        keyboard = KeyboardType.Text, enabled = enabled) {
        IconButton(onClick = {
            val initial = day ?: LocalDate.now(ActivityDraft.WIB)
            DatePickerDialog(context, pickerTheme(dark), { _, year, month, dayOfMonth ->
                onChange(LocalDate.of(year, month + 1, dayOfMonth).toString())
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }, enabled = enabled, modifier = Modifier.testTag("$tag-picker")) {
            FarmControlIcon(FarmIcon.Calendar, "Buka kalender untuk ${label.substringBefore(" (")}")
        }
    }
}

/** A time field that takes typed text (HH:mm; seconds already present are kept) and opens the Android time dialog. */
@Composable
internal fun FinanceTimeField(
    label: String, value: String, onChange: (String) -> Unit, tag: String,
    error: String? = null, hint: String? = null, enabled: Boolean = true
) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val time = runCatching { LocalTime.parse(value.trim()) }.getOrNull()
    FinanceField(label, value, onChange, tag, error = error, hint = hint, keyboard = KeyboardType.Text, enabled = enabled) {
        IconButton(onClick = {
            val initial = time ?: LocalTime.now(ActivityDraft.WIB)
            TimePickerDialog(context, pickerTheme(dark), { _, hour, minute ->
                val seconds = time?.second?.takeIf { it != 0 }?.let { ":%02d".format(it) } ?: ""
                onChange("%02d:%02d%s".format(hour, minute, seconds))
            }, initial.hour, initial.minute, true).show()
        }, enabled = enabled, modifier = Modifier.testTag("$tag-picker")) {
            FarmControlIcon(FarmIcon.Schedule, "Buka pilihan jam untuk ${label.substringBefore(" (")}")
        }
    }
}
