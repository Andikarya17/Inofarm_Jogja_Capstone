package com.inofarm.jogja

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun ComposeTestRule.captureBotanicalWindow(name: String) {
    waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "frontend-botanical")
    directory.mkdirs()
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
