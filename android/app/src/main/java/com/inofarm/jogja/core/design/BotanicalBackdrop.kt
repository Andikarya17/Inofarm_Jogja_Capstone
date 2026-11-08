package com.inofarm.jogja.core.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun BotanicalBackdrop(@DrawableRes artwork: Int, accentHeight: Dp) {
    val background = MaterialTheme.colorScheme.background
    val dark = isSystemInDarkTheme()
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        if (maxWidth < maxHeight && maxWidth < 600.dp) {
            Box(Modifier.fillMaxWidth().height(accentHeight)) {
                Image(painterResource(artwork), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(background.copy(alpha = if (dark) .86f else .36f), background))))
            }
        }
    }
}
