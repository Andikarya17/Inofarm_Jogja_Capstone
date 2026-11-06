package com.inofarm.jogja.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp

@Composable internal fun FarmGlass(
    modifier: Modifier = Modifier,
    transmitBackdrop: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = FarmShape.Navigation,
        color = colors.surfaceContainer.copy(alpha = if (transmitBackdrop) 0.94f else 1f),
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(colors.surface, colors.outlineVariant))),
        shadowElevation = FarmDepth.Navigation) {
        Box(Modifier.background(Brush.verticalGradient(listOf(colors.surface.copy(alpha = .40f), Color.Transparent)))) {
            content()
        }
    }
}

internal enum class FarmIcon { Account, Sync, Eye, EyeOff }

@Composable internal fun FarmControlIcon(icon: FarmIcon, description: String?) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics {
        if (description != null) contentDescription = description
    }) {
        val u = size.width / 24f
        val stroke = Stroke(1.8f * u)
        val path = Path()
        when (icon) {
            FarmIcon.Account -> {
                drawCircle(color, 3.6f * u, Offset(12 * u, 7 * u), style = stroke)
                path.moveTo(4 * u, 21 * u)
                path.cubicTo(4 * u, 12 * u, 20 * u, 12 * u, 20 * u, 21 * u)
            }
            FarmIcon.Sync -> {
                path.moveTo(5 * u, 10 * u)
                path.cubicTo(7 * u, 3 * u, 16 * u, 2 * u, 20 * u, 8 * u)
                path.moveTo(20 * u, 4 * u); path.lineTo(20 * u, 9 * u); path.lineTo(15 * u, 9 * u)
                path.moveTo(19 * u, 14 * u)
                path.cubicTo(17 * u, 21 * u, 8 * u, 22 * u, 4 * u, 16 * u)
                path.moveTo(4 * u, 20 * u); path.lineTo(4 * u, 15 * u); path.lineTo(9 * u, 15 * u)
            }
            FarmIcon.Eye, FarmIcon.EyeOff -> {
                path.moveTo(2 * u, 12 * u)
                path.quadraticTo(12 * u, -1 * u, 22 * u, 12 * u)
                path.quadraticTo(12 * u, 25 * u, 2 * u, 12 * u)
                drawCircle(color, 2.7f * u, Offset(12 * u, 12 * u), style = stroke)
                if (icon == FarmIcon.EyeOff) {
                    path.moveTo(3 * u, 3 * u); path.lineTo(21 * u, 21 * u)
                }
            }
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable internal fun FarmTab(
    index: Int, label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, inline: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val indicator by animateColorAsState(
        if (selected) colors.primaryContainer else colors.primaryContainer.copy(alpha = 0f),
        tween(FarmMotion.State), label = "Indikator navigasi"
    )
    val control = modifier.onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) colors.primary else Color.Transparent, FarmShape.Control)
        .selectable(selected, onClick = onClick, role = Role.Tab)
        .heightIn(min = FarmSpace.Touch)
    if (inline) {
        Row(
            control.background(indicator, FarmShape.Control).padding(horizontal = FarmSpace.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium)
        ) {
            val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface
            CompositionLocalProvider(LocalContentColor provides contentColor) { FarmNavigationIcon(index) }
            Text(label, Modifier.padding(vertical = FarmSpace.Small), style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = contentColor)
        }
        return
    }
    Column(
        control.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) colors.onPrimaryContainer else colors.onSurface) {
            Box(Modifier.background(indicator, FarmShape.Control).padding(horizontal = 8.dp, vertical = 3.dp)) {
                FarmNavigationIcon(index)
            }
        }
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable internal fun FarmPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = FarmShape.Card, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(FarmSpace.Large), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            title?.let { Text(it, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
            content()
        }
    }
}

@Composable internal fun FarmSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
        HorizontalDivider()
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable internal fun FarmFact(label: String, value: String, modifier: Modifier = Modifier, prominent: Boolean = false) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
        if (value.startsWith("Rp")) FarmAmount(value, style)
        else Text(value, style = if (prominent) style.copy(fontFeatureSettings = "tnum") else style)
    }
}

private val rupiahGroupSeparator = Regex("(?<=\\d)\\.(?=\\d{3}(?:[.,]|$))")

@Composable internal fun FarmAmount(value: String, style: TextStyle = MaterialTheme.typography.titleLarge) {
    val display = remember(value) { rupiahGroupSeparator.replace(value, ".\u200B") }
    // Keep the original amount available to accessibility services when the visual line wraps.
    Text(display, Modifier.clearAndSetSemantics { text = AnnotatedString(value) },
        style = style.copy(fontFeatureSettings = "tnum"))
}

@Composable internal fun FarmCheckbox(
    label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch)
            .toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, onCheckedChange = null, modifier = Modifier.size(FarmSpace.Touch))
        Text(label, Modifier.weight(1f).padding(vertical = FarmSpace.Small))
    }
}

@Composable internal fun FarmReviewEntrance(content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(FarmMotion.Navigation)) }
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = progress.value
        translationY = 12.dp.toPx() * (1f - progress.value)
    }) { content() }
}

@Composable internal fun FarmDraftStatus(saved: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val color by animateColorAsState(
        if (saved) colors.primary else colors.onSurfaceVariant,
        tween(FarmMotion.State), label = "Status draf"
    )
    Text(
        if (saved) "Draf tersimpan di perangkat." else "Perubahan belum selesai disimpan.",
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}

@Composable internal fun FarmAttention(message: String) {
    val dark = isSystemInDarkTheme()
    Surface(
        Modifier.fillMaxWidth(),
        shape = FarmShape.Control,
        color = if (dark) Color(0xFF493519) else FarmColors.WarningSurface,
        contentColor = if (dark) ForestScheme.secondary else FarmColors.Warning
    ) {
        Text(message, Modifier.padding(FarmSpace.Medium), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun FarmNavigationIcon(index: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val path = Path()
        when(index) {
            0 -> {path.moveTo(3*u,11*u);path.lineTo(12*u,3*u);path.lineTo(21*u,11*u);path.moveTo(5*u,10*u);path.lineTo(5*u,21*u);path.lineTo(10*u,21*u);path.lineTo(10*u,15*u);path.lineTo(14*u,15*u);path.lineTo(14*u,21*u);path.lineTo(19*u,21*u);path.lineTo(19*u,10*u)}
            1 -> {drawCircle(color,8*u,Offset(12*u,12*u),style=Stroke(1.8f*u));path.moveTo(12*u,7*u);path.lineTo(12*u,12*u);path.lineTo(16*u,14*u)}
            2 -> {for(y in listOf(6,12,18)) {path.moveTo(8*u,y*u);path.lineTo(21*u,y*u);drawCircle(color,1.2f*u,Offset(3*u,y*u))}}
            3 -> {path.moveTo(4*u,8*u);path.lineTo(20*u,8*u);path.lineTo(18*u,21*u);path.lineTo(6*u,21*u);path.close();path.moveTo(8*u,8*u);path.quadraticTo(12*u,-1*u,16*u,8*u);path.moveTo(9*u,12*u);path.lineTo(10*u,18*u);path.moveTo(15*u,12*u);path.lineTo(14*u,18*u)}
            else -> {path.moveTo(3*u,5*u);path.lineTo(21*u,5*u);path.lineTo(21*u,21*u);path.lineTo(3*u,21*u);path.close();path.moveTo(3*u,10*u);path.lineTo(21*u,10*u);path.moveTo(15*u,14*u);path.lineTo(21*u,14*u);path.moveTo(15*u,17*u);path.lineTo(21*u,17*u)}
        }
        drawPath(path,color,style=Stroke(1.8f*u))
    }
}

@Composable internal fun FarmPageTitle(title: String, description: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=8.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.semantics { heading() }, style=MaterialTheme.typography.headlineMedium, color=MaterialTheme.colorScheme.onBackground)
        description?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
