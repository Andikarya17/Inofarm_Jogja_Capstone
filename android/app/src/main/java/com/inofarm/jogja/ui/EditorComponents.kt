package com.inofarm.jogja.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.*

/** Label is also attached to the editable node, so large fonts and accessibility keep it intact. */
@Composable internal fun FarmField(label: String, value: String, change: (String)->Unit,
    placeholder: String="Belum diisi", suffix: String?=null, error: String?=null,
    keyboard: KeyboardType=KeyboardType.Decimal, modifier: Modifier=Modifier, showLabel: Boolean=true, compact: Boolean=false,
    trailing: (@Composable ()->Unit)?=null) {
    val interaction=remember {MutableInteractionSource()}
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        if(showLabel) Text(label,style=if(compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,color=FarmColors.Ink)
        BasicTextField(value,change,singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(color=FarmColors.Ink),
            keyboardOptions=KeyboardOptions(keyboardType=keyboard),cursorBrush=SolidColor(green),interactionSource=interaction,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics { contentDescription=label; if(error!=null) this.error(error) }.testTag(label),
            decorationBox={ inner ->
                Row(Modifier.fillMaxWidth().border(if(focused) 2.dp else 1.dp,if(error!=null) FarmColors.Error else if(focused) green else FarmColors.Outline,FarmShape.Control).background(FarmColors.Surface,FarmShape.Control).padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).padding(vertical=13.dp)) {
                        if(value.isEmpty()) Text(placeholder,style=MaterialTheme.typography.bodyLarge,color=FarmColors.Muted)
                        inner()
                    }
                    suffix?.let { Text(it,Modifier.background(FarmColors.Soft).padding(horizontal=10.dp,vertical=14.dp),style=MaterialTheme.typography.bodyMedium) }
                    trailing?.invoke()
                }
            })
        error?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=FarmColors.Error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun FarmDateField(label: String, value: String, change: (String)->Unit, error: String?=null) {
    var open by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    FarmField(label,value,change,"YYYY-MM-DD",error=error,keyboard=KeyboardType.Ascii,trailing={
        IconButton({open=true},Modifier.semantics {contentDescription="Pilih tanggal untuk $label"}) {FarmIcon(FarmGlyph.Calendar)}
    })
    if(open) {
        val selected=runCatching {java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull()
        val picker=rememberDatePickerState(initialSelectedDateMillis=selected)
        DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton({
            picker.selectedDateMillis?.let {change(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString())};open=false
        },enabled=picker.selectedDateMillis!=null) {Text("Pilih")}},dismissButton={TextButton({open=false}) {Text("Batal")}}) {DatePicker(picker,title={Text("Pilih tanggal",Modifier.padding(16.dp))})}
    }
}

@Composable internal fun BoundEditor(bound: BoundDraft, change: (BoundDraft)->Unit, unit: String?=null, stacked: Boolean=false, nonnegative: Boolean=false) {
    val min=bound.min.toDoubleOrNull();val max=bound.max.toDoubleOrNull()
    val error=when {
        bound.min.isBlank() && bound.max.isBlank() -> null
        min==null || max==null || !min.isFinite() || !max.isFinite() -> "Isi kedua batas dengan angka finite."
        min>max -> "Minimum harus ≤ maksimum."
        nonnegative && min<0 -> "PPM tidak boleh negatif."
        else -> null
    }
    val first="Minimum${unit?.let { " ($it)" } ?: ""}"
    val last="Maksimum${unit?.let { " ($it)" } ?: ""}"
    if(stacked || LocalDensity.current.fontScale>1.5f) Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FarmField(first,bound.min,{change(bound.copy(min=it))},error=error)
        FarmField(last,bound.max,{change(bound.copy(max=it))})
    } else Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        FarmField(first,bound.min,{change(bound.copy(min=it))},placeholder="—",error=error,modifier=Modifier.weight(1f),compact=true)
        FarmField(last,bound.max,{change(bound.copy(max=it))},placeholder="—",modifier=Modifier.weight(1f),compact=true)
    }
}

@Composable internal fun DetailPage(title: String, subtitle: String?, back: ()->Unit, footer: @Composable (HazeState)->Unit,
    content: @Composable ColumnScope.()->Unit) {
    val haze=rememberHazeState()
    val large=LocalDensity.current.fontScale>1.5f
    val density=LocalDensity.current
    var footerHeight by remember {mutableStateOf(if(large) 176.dp else 76.dp)}
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().then(if(glassEffectsEnabled()) Modifier.hazeSource(haze) else Modifier)) {
        ChromeBackdrop(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(bottom=footerHeight).verticalScroll(rememberScrollState()).padding(horizontal=FarmSpace.Page),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            PageHeader(title,subtitle,back)
            content()
            Spacer(Modifier.height(16.dp))
        }
        }
        Box(Modifier.align(Alignment.BottomCenter).onSizeChanged {footerHeight=with(density) {it.height.toDp()}}.padding(horizontal=10.dp,vertical=8.dp)) {footer(haze)}
    }
}

@Composable internal fun MenuRow(title: String, subtitle: String, glyph: FarmGlyph, click: ()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=click,role=Role.Button).heightIn(min=62.dp).padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        IconMedallion(glyph)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
        }
        FarmIcon(FarmGlyph.Chevron,Modifier.size(18.dp))
    }
}
