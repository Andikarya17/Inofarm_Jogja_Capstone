package com.inofarm.jogja.feature

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
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
internal fun FarmContextStrip(greenhouse: String, planting: String?, age: String?, icon: FarmIcon = FarmIcon.Greenhouse) {
    MaterialTheme(colorScheme = ForestScheme, typography = MaterialTheme.typography) {
        Surface(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = ForestScheme.background, contentColor = ForestScheme.onBackground) {
            Row(Modifier.padding(FarmSpace.Medium), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
                verticalAlignment = Alignment.CenterVertically) {
                FarmIconTile(icon, tone = FarmTone.Forest, size = 40.dp)
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

/**
 * Sticky bar: draft on the left, the main action on the right. Tags follow the form, for example expense-save-draft.
 * While [busy], the form stays on screen and the main button carries the status in place, the way the Masuk button does.
 */
@Composable
internal fun FarmFormBar(
    saveTag: String, onSave: () -> Unit, mainTag: String, mainLabel: String, onMain: () -> Unit,
    enabled: Boolean = true, mainIcon: Boolean = false, saveLabel: String = "Simpan draf",
    mainEnabled: Boolean = enabled, showSave: Boolean = true,
    busy: Boolean = false, busyLabel: String = "Menyimpan…",
) {
    val colors = MaterialTheme.colorScheme
    // A local save usually ends within a frame or two; the status only appears when it does not.
    var showBusy by remember { mutableStateOf(false) }
    // At large font sizes the status word needs the whole button, so the indicator and some padding give way to it.
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    LaunchedEffect(busy) {
        if (busy) { delay(FarmMotion.BusyReveal); showBusy = true } else showBusy = false
    }
    Surface(Modifier.fullBleed(FarmSpace.Page), color = colors.surface, shadowElevation = 4.dp) {
        Column {
        HorizontalDivider(color = colors.outlineVariant)
        // Both buttons take the taller one's height, so a label that wraps at large font sizes does not leave them uneven.
        Row(Modifier.padding(horizontal = FarmSpace.Page, vertical = FarmSpace.Small).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            if (showSave) OutlinedButton(onClick = onSave, enabled = enabled && !busy, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp).testTag(saveTag)) { Text(saveLabel, maxLines = 2) }
            Button(onClick = onMain, enabled = mainEnabled && !busy, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                // The working button keeps its filled colour, so it reads as in progress, not as unavailable.
                colors = if (showBusy) ButtonDefaults.buttonColors(disabledContainerColor = colors.primary, disabledContentColor = colors.onPrimary)
                    else ButtonDefaults.buttonColors(),
                contentPadding = if (largeFont) PaddingValues(horizontal = FarmSpace.Medium, vertical = FarmSpace.Small) else ButtonDefaults.ContentPadding,
                modifier = Modifier.weight(if (showSave) 1.4f else 1f).fillMaxHeight().heightIn(min = 52.dp).testTag(mainTag)) {
                AnimatedContent(showBusy, transitionSpec = { fadeIn(tween(FarmMotion.LoginFeedback)) togetherWith fadeOut(tween(FarmMotion.LoginFeedback)) },
                    label = "Status simpan") { working ->
                    Row(Modifier.semantics { if (working) liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                        if (working && !largeFont) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                        else if (!working && mainIcon) FarmControlIcon(FarmIcon.Check, null)
                        Text(if (working) busyLabel else mainLabel, maxLines = 2)
                    }
                }
            }
        }
        }
    }
}

/** The shell and the wizard pad the page horizontally; the sticky bar cancels that padding so it meets the screen edges. */
private fun Modifier.fullBleed(inset: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extra = inset.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** "Kamis, 12 Nov 2026 · 21:13 WIB" for a valid date, otherwise the typed values as they are. */
internal fun whenLabel(date: String, time: String): String {
    val day = runCatching { java.time.LocalDate.parse(date.trim()) }.getOrNull()
    return if (day != null && time.isNotBlank()) "${dateLabel(day)} · ${time.trim().take(5)} WIB" else "$date $time".trim().ifBlank { "Waktu belum diisi" }
}

/** Step "Periksa": a forest header for what is being saved, the details as cards, and Ubah isian / Simpan. */
@Composable
internal fun FarmReviewFrame(
    title: String, subtitle: String, icon: FarmIcon, busy: Boolean, queueTag: String, backTag: String,
    onBack: () -> Unit, onQueue: () -> Unit, mainLabel: String = "Simpan",
    hint: String = "Pastikan isinya benar. Setelah disimpan, catatan dikirim saat ada koneksi.",
    content: @Composable ColumnScope.() -> Unit,
) {
    FarmFormFrame(
        header = {
            FarmPageTitle("Periksa $title", hint)
            FarmContextStrip(title.replaceFirstChar { it.uppercase() }, subtitle, null, icon)
        },
        bar = { FarmFormBar(backTag, onBack, queueTag, mainLabel, onQueue, enabled = !busy, mainIcon = true, saveLabel = "Ubah isian",
            busy = busy, busyLabel = if (mainLabel.startsWith("Kirim")) "Mengirim…" else "Menyimpan…") },
        content = content
    )
}

/** Event time as one readable line with "Ubah", which opens the date and time fields. Measurement and harvest forms use it. */
@Composable
internal fun FarmWhenCard(date: String, time: String, editTag: String, forceOpen: Boolean, fields: @Composable ColumnScope.() -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val shown = open || forceOpen
    val line = whenLabel(date, time)
    Column(Modifier.fillMaxWidth().animateContentSize(androidx.compose.animation.core.tween(190)), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            FarmIconTile(FarmIcon.Schedule, tone = FarmTone.Neutral)
            Column(Modifier.weight(1f)) {
                Text("Waktu kejadian", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(line, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag(editTag)) {
                Text(if (shown) "Tutup" else "Ubah")
            }
        }
        if (shown) fields()
    }
}

/** Two number fields side by side; they stack once the font is large enough that half a row no longer fits a value. */
@Composable
internal fun FarmFieldPair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) { first(); second() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Box(Modifier.weight(1f)) { first() }
            Box(Modifier.weight(1f)) { second() }
        }
    }
}

/** One repeated detail (a material, a grade) as a bordered card with a red delete icon at the right of the first row. */
@Composable
internal fun FarmDetailCard(deleteLabel: String, onDelete: () -> Unit, deleteTag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = FarmShape.Tile, color = colors.surfaceVariant.copy(alpha = 0.35f), border = BorderStroke(1.dp, colors.outlineVariant)) {
        Box {
            Column(Modifier.padding(FarmSpace.Medium).padding(end = 40.dp), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) { content() }
            IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).then(if (deleteTag != null) Modifier.testTag(deleteTag) else Modifier)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides colors.error) { FarmControlIcon(FarmIcon.Trash, deleteLabel) }
            }
        }
    }
}

/** Reference and age notes at the end of a form: the info tone, no border. */
@Composable
internal fun FarmInfoCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = FarmShape.Tile, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
        Column(Modifier.padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/** Three segments for the wizard: steps before [step] are done (check), [step] is the current one. */
@Composable
internal fun FarmStepIndicator(step: Int, labels: List<String>) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        labels.forEachIndexed { index, label ->
            val number = index + 1
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(if (number <= step) colors.primary else colors.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (number < step) Box(Modifier.size(16.dp)) { androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides colors.primary) { FarmControlIcon(FarmIcon.Check, null) } }
                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (number == step) FontWeight.Bold else FontWeight.Medium,
                        color = if (number <= step) colors.onSurface else colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Account role as two cards. The container is an inert tagged node and each card is "$tag-option-$id", like the chips. */
@Composable
internal fun FarmRoleCards(selected: String, roles: Map<String, String>, tag: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val about = mapOf(
        "worker" to "Mencatat kegiatan dan panen di greenhouse yang ditugaskan.",
        "manager" to "Mengelola semua greenhouse, akun, dan keuangan.",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Peran", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Box(Modifier.size(1.dp).testTag(tag))
        val cards: @Composable (Modifier) -> Unit = { modifier ->
            roles.forEach { (id, name) ->
                val on = id == selected
                Surface(modifier.testTag("$tag-option-$id").selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onSelect(id) }),
                    shape = FarmShape.Tile, color = if (on) colors.primaryContainer else colors.surface,
                    contentColor = if (on) colors.onPrimaryContainer else colors.onSurface,
                    border = BorderStroke(if (on) 2.dp else 1.dp, if (on) colors.primary else colors.outlineVariant)) {
                    Column(Modifier.padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        about[id]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) { cards(Modifier.fillMaxWidth()) }
        else Row(Modifier.selectableGroup().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) { cards(Modifier.weight(1f).fillMaxHeight()) }
    }
}
