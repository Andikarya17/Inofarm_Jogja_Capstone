package com.inofarm.jogja.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

/** The release package; the profile is recorded against the release build the plugin creates. */
internal const val TARGET_PACKAGE = "com.inofarm.jogja"

/**
 * Cold start up to the login form, then the work every user does there: type an email and show the password.
 * A signed-in journey would need a reachable HTTPS backend and a real account, which the profile run does not have.
 */
internal fun MacrobenchmarkScope.loginJourney() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.text("Masuk")), 10_000)
    // Compose redraws a field when it gains focus, so each step looks the node up again instead of keeping it.
    retry { device.findObject(By.desc("Email"))?.click() }
    device.waitForIdle()
    retry { device.findObject(By.desc("Email"))?.text = "pekerja@example.invalid" }
    device.waitForIdle()
    retry { device.findObject(By.desc("Tampilkan password"))?.click() }
    device.waitForIdle()
}

private fun retry(action: () -> Unit) {
    repeat(3) { attempt ->
        try { action(); return } catch (stale: StaleObjectException) { if (attempt == 2) throw stale }
    }
}
