package com.inofarm.jogja.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Tonal glass fallback: stable contrast, no content blur or expensive backdrop capture. */
@Composable internal fun FarmGlass(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainer, contentColor = colors.onSurface,
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(colors.surface, colors.outlineVariant))),
        shadowElevation = 4.dp) {
        Box(Modifier.background(Brush.linearGradient(listOf(colors.surface.copy(alpha = .65f), Color.Transparent, colors.primary.copy(alpha = .07f))))) { content() }
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
        Text(title, style=MaterialTheme.typography.headlineMedium, color=MaterialTheme.colorScheme.onBackground)
        description?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
