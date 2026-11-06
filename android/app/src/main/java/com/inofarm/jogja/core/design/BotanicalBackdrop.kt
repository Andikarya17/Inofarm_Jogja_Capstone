package com.inofarm.jogja.core.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun BotanicalBackdrop(@DrawableRes artwork: Int) {
    val background = MaterialTheme.colorScheme.background
    val dark = isSystemInDarkTheme()
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        if (maxWidth < maxHeight && maxWidth < 600.dp) {
            Image(
                painterResource(artwork), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            if (dark) Box(Modifier.fillMaxSize().background(background.copy(alpha = .86f)))
        }
    }
}
