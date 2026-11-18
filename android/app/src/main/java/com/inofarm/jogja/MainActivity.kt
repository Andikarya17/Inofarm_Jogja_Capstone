package com.inofarm.jogja

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import com.inofarm.jogja.core.design.FarmThemeControl
import com.inofarm.jogja.core.design.InofarmTheme
import com.inofarm.jogja.core.design.ThemeMode
import com.inofarm.jogja.feature.FinanceRoot

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val runtime=ViewModelProvider(this)[FinanceRuntime::class.java]
        val preference=(application as InofarmApplication).theme
        setContent {
            val mode by preference.mode.collectAsState()
            // System bar icons follow the in-app theme, not the system one.
            DisposableEffect(mode) {
                val transparent=android.graphics.Color.TRANSPARENT
                val style=if(mode==ThemeMode.DARK) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent,transparent)
                enableEdgeToEdge(statusBarStyle=style,navigationBarStyle=style)
                onDispose {}
            }
            val control=remember(mode) {FarmThemeControl(mode,preference::select)}
            InofarmTheme(control) {FinanceRoot(runtime)}
        }
    }
}
