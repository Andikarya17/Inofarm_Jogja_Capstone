package com.inofarm.jogja.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object FarmColors {
    val Brand = Color(0xFF245B43)
    val Ink = Color(0xFF103D31)
    val Muted = Color(0xFF63707B)
    val Background = Color(0xFFF4F6F0)
    val Surface = Color(0xFFFFFFFF)
    val Soft = Color(0xFFE1F0E3)
    val Hero = Color(0xFFDDEAD8)
    val Outline = Color(0xFFC5CED4)
    val Divider = Color(0xFFD8E1D4)
    val Warning = Color(0xFF805016)
    val WarningSurface = Color(0xFFFFF0D9)
    val Error = Color(0xFF9A3029)
    val ErrorSurface = Color(0xFFFCEBE7)
    val Info = Color(0xFF365B66)
    val InfoSurface = Color(0xFFE7F0F2)
    val Navigation = Color(0xFFE2F0E8)
}

internal val green = FarmColors.Brand
internal val background = FarmColors.Background

internal object FarmSpace {
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val Page = 16.dp
    val Section = 24.dp
    val Touch = 48.dp
    val MaxWidth = 720.dp
}

internal object FarmShape {
    val Control = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(20.dp)
    val Hero = RoundedCornerShape(24.dp)
    val Navigation = RoundedCornerShape(36.dp)
}

internal object FarmMotion {
    const val Press = 120
    const val State = 200
    const val Navigation = 220
}

internal object FarmDepth {
    val Navigation = 3.dp
    val Content = 0.dp
}

@Composable internal fun InofarmTheme(content: @Composable () -> Unit) {
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
        TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp,
            fontWeight = weight, letterSpacing = tracking.sp)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FarmColors.Brand, onPrimary = Color.White,
            primaryContainer = FarmColors.Hero, onPrimaryContainer = FarmColors.Ink,
            secondary = FarmColors.Muted, onSecondary = Color.White,
            secondaryContainer = FarmColors.Soft, onSecondaryContainer = FarmColors.Ink,
            tertiary = FarmColors.Info, onTertiary = Color.White,
            background = background, onBackground = FarmColors.Ink,
            surface = FarmColors.Surface, onSurface = FarmColors.Ink,
            surfaceVariant = FarmColors.Soft, onSurfaceVariant = FarmColors.Muted,
            surfaceContainer = FarmColors.Navigation, surfaceContainerHighest = FarmColors.Soft,
            outline = FarmColors.Outline, outlineVariant = FarmColors.Divider,
            error = FarmColors.Error, onError = Color.White,
            errorContainer = FarmColors.ErrorSurface, onErrorContainer = FarmColors.Error
        ),
        typography = Typography(
            displaySmall = style(34, 40, FontWeight.SemiBold, -0.8),
            headlineLarge = style(34, 40, FontWeight.Bold, -0.7),
            headlineMedium = style(28, 34, FontWeight.SemiBold, -0.6),
            headlineSmall = style(24, 30, FontWeight.SemiBold, -0.4),
            titleLarge = style(20, 26, FontWeight.Bold, -0.3),
            titleMedium = style(16, 22, FontWeight.SemiBold),
            titleSmall = style(14, 20, FontWeight.Medium),
            bodyLarge = style(16, 24), bodyMedium = style(14, 21), bodySmall = style(12, 18),
            labelLarge = style(14, 20, FontWeight.Medium),
            labelMedium = style(12, 18, FontWeight.Medium), labelSmall = style(11, 16, FontWeight.Medium)
        ),
        shapes = Shapes(small = FarmShape.Control, medium = FarmShape.Card, large = FarmShape.Hero),
        content = {
            DeviceGlassPolicy {
            Surface(Modifier.fillMaxSize(), color = background) {
                Box(Modifier.fillMaxSize()) { content() }
            }
            }
        }
    )
}

@Composable internal fun PrimaryAction(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = FarmShape.Control
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(FarmMotion.Press), label = "Press feedback")
    Button(onClick, modifier.heightIn(min = FarmSpace.Touch).graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled, interactionSource = interaction, shape = shape,
        border = if (focused) BorderStroke(2.dp, FarmColors.Ink) else BorderStroke(1.dp,Color.White.copy(alpha=.3f)),
        contentPadding = PaddingValues(horizontal = FarmSpace.Large, vertical = FarmSpace.Medium)) {
        Text(text)
    }
}
