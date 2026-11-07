package com.inofarm.jogja

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun botanicalEvidenceDirectory(context: Context): File {
    val run = InstrumentationRegistry.getArguments().getString("evidenceRun")
    require(run == null || run.matches(Regex("[a-z0-9-]+")))
    val folder = if (run == null) "frontend-botanical" else "frontend-secondary-$run"
    return File(context.getExternalFilesDir(null), folder).apply { mkdirs() }
}

internal fun ComposeTestRule.captureBotanicalWindow(name: String) {
    waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    if (InstrumentationRegistry.getArguments().getString("evidenceRun") != null) {
        // Wait for the platform window transition as well as the Compose clock.
        Thread.sleep(350)
        instrumentation.waitForIdleSync()
    }
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    val directory = botanicalEvidenceDirectory(instrumentation.targetContext)
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
