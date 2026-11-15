package com.inofarm.jogja

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.inofarm.jogja.core.design.FarmMotion
import com.inofarm.jogja.feature.rememberAllSent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Fase F4: "Semua terkirim" hanya muncul saat antrean benar-benar habis, sekali, lalu menghilang sendiri. */
class AllSentMomentUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsOnceWhenTheQueueEmptiesThenStepsAside() {
        var unsent by mutableIntStateOf(2)
        var confirmed = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { Text(if (rememberAllSent(unsent) { confirmed++ }) "Semua terkirim" else "-") }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("-").assertIsDisplayed()

        unsent = 1
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("-").assertIsDisplayed()
        assertEquals(0, confirmed)

        unsent = 0
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Semua terkirim").assertIsDisplayed()
        assertEquals(1, confirmed)

        rule.mainClock.advanceTimeBy(FarmMotion.AllSentHold + 100)
        rule.onNodeWithText("-").assertIsDisplayed()
        assertEquals(1, confirmed)
    }

    @Test fun anEmptyQueueAtStartShowsNothing() {
        var confirmed = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { Text(if (rememberAllSent(0) { confirmed++ }) "Semua terkirim" else "-") }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("-").assertIsDisplayed()
        assertEquals(0, confirmed)
    }
}
