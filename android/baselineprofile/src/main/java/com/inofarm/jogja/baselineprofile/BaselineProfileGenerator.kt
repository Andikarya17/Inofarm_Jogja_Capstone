package com.inofarm.jogja.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Generates `baseline-prof.txt` for :app. Run with `:app:generateBaselineProfile` on a connected device or emulator. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) { loginJourney() }
}
