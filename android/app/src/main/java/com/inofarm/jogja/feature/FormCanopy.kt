package com.inofarm.jogja.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*

/** True inside a Canopy form: [FinanceField] then draws its label above and uses the larger field shape. */
internal val LocalCanopyForm = compositionLocalOf { false }

/**
 * Manager form frame: a header, a scrolling body and a bar that stays above the keyboard.
 * The shell already applies the keyboard inset, so the bar rides on top of it.
 */
@Composable
internal fun FarmFormFrame(
    header: @Composable () -> Unit,
    bar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalCanopyForm provides true) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = FarmSpace.Medium),
                verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                header()
                content()
            }
            bar()
        }
    }
}

/** Close, title and draft state. Close saves the draft and goes back, the same action as the old "Simpan draf dan kembali". */
@Composable
internal fun FarmFormHeader(title: String, saved: Boolean, statusTag: String, closeTag: String, onClose: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        IconButton(onClick = onClose, enabled = enabled, modifier = Modifier.testTag(closeTag)) {
            FarmControlIcon(FarmIcon.Close, "Simpan draf dan kembali")
        }
        Column(Modifier.weight(1f)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FarmDraftStatus(saved, Modifier.testTag(statusTag))
        }
    }
}

/** Forest strip with the greenhouse and planting the form is for; [age] sits on the right when known. */
@Composable
internal fun FarmContextStrip(greenhouse: String, planting: String?, age: String?) {
    MaterialTheme(colorScheme = ForestScheme, typography = MaterialTheme.typography) {
        Surface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = ForestScheme.background, contentColor = ForestScheme.onBackground) {
            Row(Modifier.padding(FarmSpace.Medium), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
                verticalAlignment = Alignment.CenterVertically) {
                FarmIconTile(FarmIcon.Greenhouse, tone = FarmTone.Forest, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(greenhouse, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    planting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ForestScheme.onSurfaceVariant) }
                }
                age?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** A form group as a card with a title. Replaces the divider-and-heading section in manager forms. */
@Composable
internal fun FarmFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = FarmShape.Card, color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Sticky bar: draft on the left, the main action on the right. Tags follow the form, for example expense-save-draft. */
@Composable
internal fun FarmFormBar(
    saveTag: String, onSave: () -> Unit, mainTag: String, mainLabel: String, onMain: () -> Unit,
    enabled: Boolean = true, mainIcon: Boolean = false, saveLabel: String = "Simpan draf",
) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), color = colors.surface, shadowElevation = 4.dp, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Row(Modifier.padding(horizontal = FarmSpace.Medium, vertical = FarmSpace.Small), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            OutlinedButton(onClick = onSave, enabled = enabled, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag(saveTag)) { Text(saveLabel, maxLines = 2) }
            Button(onClick = onMain, enabled = enabled, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1.4f).heightIn(min = 52.dp).testTag(mainTag)) {
                if (mainIcon) { FarmControlIcon(FarmIcon.Check, null); Spacer(Modifier.width(FarmSpace.Small)) }
                Text(mainLabel, maxLines = 2)
            }
        }
    }
}

/** Step "Periksa": a forest header for what is being saved, the details as cards, and Ubah isian / Simpan. */
@Composable
internal fun FarmReviewFrame(
    title: String, subtitle: String, icon: FarmIcon, busy: Boolean, queueTag: String, backTag: String,
    onBack: () -> Unit, onQueue: () -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    FarmFormFrame(
        header = {
            FarmPageTitle("Periksa $title", "Pastikan isinya benar. Setelah disimpan, catatan dikirim saat ada koneksi.")
            FarmContextStrip(title, subtitle, null)
        },
        bar = { FarmFormBar(backTag, onBack, queueTag, "Simpan", onQueue, enabled = !busy, mainIcon = true, saveLabel = "Ubah isian") },
        content = content
    )
}
