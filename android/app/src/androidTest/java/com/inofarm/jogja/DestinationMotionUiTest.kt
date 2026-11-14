package com.inofarm.jogja

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.design.FarmMotion
import com.inofarm.jogja.core.design.InofarmTheme
import com.inofarm.jogja.feature.FarmDestinationMotion
import com.inofarm.jogja.feature.FinanceDestination
import com.inofarm.jogja.feature.auth.LoginCanopyHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Fase F3: transisi tujuan memakai gambar beku untuk layar yang keluar, dan momen kanopi login berakhir sendiri. */
class DestinationMotionUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun leavingScreenIsAStillPictureAndTheNewScreenIsLiveAtOnce() {
        var destination by mutableStateOf<FinanceDestination>(FinanceDestination.Tab(0))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            InofarmTheme { Surface(Modifier.fillMaxSize()) {
                FarmDestinationMotion(destination, 0, Modifier.fillMaxSize()) {
                    val home = destination == FinanceDestination.Tab(0)
                    Box(Modifier.fillMaxSize().background(if (home) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(if (home) "Beranda" else "Greenhouse A", Modifier.align(if (home) Alignment.TopStart else Alignment.Center),
                            style = MaterialTheme.typography.headlineMedium)
                    }
                }
            } }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Beranda").assertIsDisplayed()

        destination = FinanceDestination.Page("greenhouse")
        rule.mainClock.advanceTimeBy(FarmMotion.Depth / 2L)
        // Mid-transition the old screen is only pixels: TalkBack and tests see the new screen alone.
        rule.onNodeWithText("Beranda").assertDoesNotExist()
        rule.onNodeWithText("Greenhouse A").assertIsDisplayed()
        capture("f3-forward-midway.png")

        rule.mainClock.advanceTimeBy(FarmMotion.Depth.toLong())
        rule.onNodeWithText("Greenhouse A").assertIsDisplayed()
        capture("f3-forward-settled.png")

        // The same destination again (a scope change on the manager home) does not start a transition.
        destination = FinanceDestination.Page("greenhouse")
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Greenhouse A").assertIsDisplayed()
    }

    @Test fun loginCanopyFoldsAwayWithinTheFocalBudget() {
        var done = 0
        var shown by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            InofarmTheme { Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Text("Beranda", Modifier.align(Alignment.Center))
                    if (shown) LoginCanopyHandoff { done++; shown = false }
                }
            } }
        }
        rule.mainClock.advanceTimeBy(FarmMotion.Focal / 4L)
        capture("f3-canopy-handoff-quarter.png")
        assertEquals(0, done)
        rule.mainClock.advanceTimeBy(FarmMotion.Focal.toLong() + 100)
        assertEquals(1, done)
        rule.onNodeWithText("Beranda").assertIsDisplayed()
        assertTrue(FarmMotion.Focal <= 400)
    }
}
