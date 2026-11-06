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
    val Brand = Color(0xFF2B503D)
    val Ink = Color(0xFF142B23)
    val Muted = Color(0xFF52665B)
    val Background = Color(0xFFF4F3E8)
    val Surface = Color(0xFFFFFDF4)
    val Soft = Color(0xFFE6EBD9)
    val Sage = Color(0xFFD4E4B7)
    val Outline = Color(0xFF687970)
    val Divider = Color(0xFFD3DBCB)
    val Warning = Color(0xFF805016)
    val WarningSurface = Color(0xFFFFF0D9)
    val Error = Color(0xFF9A3029)
    val ErrorSurface = Color(0xFFFCEBE7)
    val Info = Color(0xFF365B66)
    val InfoSurface = Color(0xFFE7F0F2)
    val Navigation = Color(0xFFE9EEDF)
}

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
    val Navigation = RoundedCornerShape(20.dp)
}

internal object FarmMotion {
    const val Press = 120
    const val State = 200
    const val Navigation = 220
}

internal object FarmDepth {
    val Navigation = 2.dp
}

internal val ForestScheme = darkColorScheme(
    primary = FarmColors.Sage, onPrimary = FarmColors.Ink,
    primaryContainer = Color(0xFF385442), onPrimaryContainer = FarmColors.Sage,
    secondary = Color(0xFFDCC28C), onSecondary = Color(0xFF3C2E10),
    secondaryContainer = Color(0xFF304739), onSecondaryContainer = Color(0xFFE0E8D7),
    tertiary = Color(0xFF9BD0DB), onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF214751), onTertiaryContainer = Color(0xFFC4EAF0),
    background = Color(0xFF132820), onBackground = FarmColors.Background,
    surface = Color(0xFF203B30), onSurface = FarmColors.Background,
    surfaceVariant = Color(0xFF2E483C), onSurfaceVariant = Color(0xFFC4CEC3),
    surfaceContainerLowest = Color(0xFF12251E), surfaceContainerLow = Color(0xFF192F26),
    surfaceContainer = Color(0xFF263F34), surfaceContainerHigh = Color(0xFF2C473B),
    surfaceContainerHighest = Color(0xFF365145),
    outline = Color(0xFF9BAC9C), outlineVariant = Color(0xFF4A6456),
    error = Color(0xFFFFB4A9), onError = Color(0xFF601410),
    errorContainer = Color(0xFF712820), onErrorContainer = Color(0xFFFFDAD4)
)

@Composable internal fun InofarmTheme(content: @Composable () -> Unit) {
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
        TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = line.sp,
            fontWeight = weight, letterSpacing = tracking.sp)
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) ForestScheme else lightColorScheme(
            primary = FarmColors.Brand, onPrimary = Color.White,
            primaryContainer = FarmColors.Sage, onPrimaryContainer = FarmColors.Ink,
            secondary = FarmColors.Muted, onSecondary = Color.White,
            secondaryContainer = FarmColors.Soft, onSecondaryContainer = FarmColors.Ink,
            tertiary = FarmColors.Info, onTertiary = Color.White,
            tertiaryContainer = FarmColors.InfoSurface, onTertiaryContainer = Color(0xFF173E48),
            background = FarmColors.Background, onBackground = FarmColors.Ink,
            surface = FarmColors.Surface, onSurface = FarmColors.Ink,
            surfaceVariant = FarmColors.Soft, onSurfaceVariant = FarmColors.Muted,
            surfaceContainerLowest = FarmColors.Surface, surfaceContainerLow = Color(0xFFF9F8EE),
            surfaceContainer = FarmColors.Navigation, surfaceContainerHighest = FarmColors.Soft,
            surfaceContainerHigh = Color(0xFFEDF0E4),
            outline = FarmColors.Outline, outlineVariant = FarmColors.Divider,
            error = FarmColors.Error, onError = Color.White,
            errorContainer = FarmColors.ErrorSurface, onErrorContainer = FarmColors.Error
        ),
        typography = Typography(
            displaySmall = style(32, 37, FontWeight.SemiBold, -0.5).copy(fontFeatureSettings = "tnum"),
            headlineLarge = style(34, 40, FontWeight.Bold, -0.7),
            headlineMedium = style(28, 34, FontWeight.SemiBold, -0.6),
            headlineSmall = style(24, 30, FontWeight.SemiBold, -0.4),
            titleLarge = style(20, 26, FontWeight.SemiBold, -0.3),
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
