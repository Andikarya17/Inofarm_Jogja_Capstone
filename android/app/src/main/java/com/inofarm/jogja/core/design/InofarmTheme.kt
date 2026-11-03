package com.inofarm.jogja.core.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object FarmColors {
    val Brand = Color(0xFF176747)
    val Ink = Color(0xFF103D31)
    val Muted = Color(0xFF63707B)
    val Background = Color(0xFFF2F5EE)
    val Surface = Color(0xFFFFFFFF)
    val Soft = Color(0xFFE1F0E3)
    val Hero = Color(0xFFD3EEDB)
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
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme(
            primary = Color(0xFF8AD7AC), onPrimary = Color(0xFF003923),
            primaryContainer = Color(0xFF174D37), onPrimaryContainer = Color(0xFFB6F2CF),
            secondary = Color(0xFFDCC28C), onSecondary = Color(0xFF3C2E10),
            secondaryContainer = Color(0xFF383D30), onSecondaryContainer = Color(0xFFE0E8D7),
            tertiary = Color(0xFF9BD0DB), onTertiary = Color(0xFF00363F),
            tertiaryContainer = Color(0xFF214751), onTertiaryContainer = Color(0xFFC4EAF0),
            background = Color(0xFF101B16), onBackground = Color(0xFFE3EDE4),
            surface = Color(0xFF17251E), onSurface = Color(0xFFE3EDE4),
            surfaceVariant = Color(0xFF283A30), onSurfaceVariant = Color(0xFFBBCDC0),
            surfaceContainer = Color(0xFF203128), surfaceContainerHighest = Color(0xFF304238),
            outline = Color(0xFF8A9D90), outlineVariant = Color(0xFF405348),
            error = Color(0xFFFFB4A9), onError = Color(0xFF601410),
            errorContainer = Color(0xFF712820), onErrorContainer = Color(0xFFFFDAD4)
        ) else lightColorScheme(
            primary = FarmColors.Brand, onPrimary = Color.White,
            primaryContainer = FarmColors.Hero, onPrimaryContainer = FarmColors.Ink,
            secondary = FarmColors.Muted, onSecondary = Color.White,
            secondaryContainer = FarmColors.Soft, onSecondaryContainer = FarmColors.Ink,
            tertiary = FarmColors.Info, onTertiary = Color.White,
            tertiaryContainer = FarmColors.InfoSurface, onTertiaryContainer = Color(0xFF173E48),
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
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    )
}
