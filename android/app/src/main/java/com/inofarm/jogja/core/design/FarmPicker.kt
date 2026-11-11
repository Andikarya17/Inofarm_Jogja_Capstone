package com.inofarm.jogja.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LocalFarmDialogPicker = staticCompositionLocalOf { false }

internal data class FarmOption(val id: String, val label: String, val detail: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FarmPicker(
    title: String,
    options: List<FarmOption>,
    selected: String?,
    optionTag: (String) -> String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    sheet: Boolean,
    loading: Boolean = false,
    emptyMessage: String = "Belum ada pilihan pada data yang tersedia."
) {
    val colors = MaterialTheme.colorScheme
    val list = rememberLazyListState(options.indexOfFirst { it.id == selected }.takeIf { it >= 5 } ?: 0)
    val rows: @Composable (Modifier) -> Unit = { modifier ->
        LazyColumn(modifier.fillMaxWidth().selectableGroup().testTag("farm-picker-options"), state = list) {
            if (options.isEmpty()) item {
                Column(Modifier.padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                    Text(if (loading) "Memuat pilihan…" else emptyMessage, color = colors.onSurfaceVariant)
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            itemsIndexed(options, key = { _, option -> option.id }) { index, option ->
                FarmChoiceRow(option.label, option.detail, option.id == selected,
                    Modifier.testTag(optionTag(option.id))) { onSelect(option.id) }
                if (index < options.lastIndex) FarmRowDivider()
            }
        }
    }
    if (sheet) {
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface,
            contentWindowInsets = { WindowInsets.safeDrawing },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Large)) {
                FarmSheetHeading(title, onDismiss)
                rows(Modifier.weight(1f, fill = false))
            }
        }
    } else {
        AlertDialog(onDismissRequest = onDismiss, containerColor = colors.surface,
            title = { Text(title, Modifier.semantics { heading() }) },
            text = { rows(Modifier.heightIn(max = 380.dp)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } })
    }
}

@Composable
internal fun FarmSheetHeading(title: String, onDismiss: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onDismiss, enabled = enabled) { FarmControlIcon(FarmIcon.Close, "Tutup $title") }
    }
}

@Composable
internal fun FarmChoiceRow(
    label: String,
    detail: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) colors.primaryContainer else colors.surface,
        tween(FarmMotion.State), label = "Warna pilihan")
    Row(modifier.fillMaxWidth().heightIn(min = if (detail == null) 56.dp else 72.dp)
        .farmRowFocus().clip(FarmShape.Control).background(fill)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = FarmSpace.Medium, vertical = FarmSpace.Small),
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
        FarmRowText(label, detail, Modifier.weight(1f), if (selected) colors.onPrimaryContainer else colors.onSurface,
            if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(
            selectedColor = colors.onPrimaryContainer, unselectedColor = colors.outline))
    }
}

@Composable
internal fun FarmActionRow(
    label: String,
    detail: String,
    icon: FarmIcon,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (enabled) colors.onSurface else colors.onSurfaceVariant
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).farmRowFocus().clip(FarmShape.Control)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = FarmSpace.Medium, vertical = FarmSpace.Small),
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) colors.primary else colors.onSurfaceVariant) {
            FarmControlIcon(icon, null)
        }
        FarmRowText(label, detail, Modifier.weight(1f), contentColor, colors.onSurfaceVariant)
        if (enabled) FarmControlIcon(FarmIcon.ChevronRight, null)
    }
}

@Composable
internal fun FarmRowDivider(inset: Dp = FarmSpace.Medium) {
    HorizontalDivider(Modifier.padding(start = inset), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FarmRowText(label: String, detail: String?, modifier: Modifier, contentColor: Color, detailColor: Color) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = contentColor)
        detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = detailColor) }
    }
}

@Composable
private fun Modifier.farmRowFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, FarmShape.Control)
}
