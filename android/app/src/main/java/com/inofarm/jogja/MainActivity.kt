package com.inofarm.jogja

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.inofarm.jogja.core.design.InofarmTheme
import com.inofarm.jogja.feature.FinanceRoot

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
        )
        val runtime=ViewModelProvider(this)[FinanceRuntime::class.java]
        setContent {InofarmTheme {FinanceRoot(runtime)}}
    }
}
