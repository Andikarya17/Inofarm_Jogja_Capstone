package com.inofarm.jogja.feature

import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceTransitionTest {
    private val home = FinanceDestination.Tab(0)
    private val history = FinanceDestination.Tab(5)
    private val greenhouse = FinanceDestination.Page("greenhouse")
    private val form = FinanceDestination.Form("activity")
    private val review = FinanceDestination.Review("activity")

    @Test fun sameDestinationNeverAnimates() {
        // The manager home keeps one destination for every greenhouse scope, so a scope change stays bound at once.
        assertEquals(FinanceTransition.None, financeTransition(home, FinanceDestination.Tab(0)))
    }

    @Test fun deeperIsForwardAndShallowerIsBack() {
        assertEquals(FinanceTransition.Forward, financeTransition(home, greenhouse))
        assertEquals(FinanceTransition.Forward, financeTransition(greenhouse, form))
        assertEquals(FinanceTransition.Forward, financeTransition(form, review))
        assertEquals(FinanceTransition.Backward, financeTransition(review, form))
        assertEquals(FinanceTransition.Backward, financeTransition(form, home))
    }

    @Test fun tabsFadeThroughAndPeersCrossfade() {
        assertEquals(FinanceTransition.Tab, financeTransition(home, history))
        assertEquals(FinanceTransition.Fade, financeTransition(form, FinanceDestination.Detail("harvest")))
        assertEquals(FinanceTransition.Fade, financeTransition(greenhouse, FinanceDestination.Page("corrections")))
    }

    @Test fun aDockTapIsAlwaysAFadeThrough() {
        // The dock stays visible on the worker greenhouse page, so Riwayat from there is a tab switch, not a step back.
        assertEquals(FinanceTransition.Tab, financeTransition(greenhouse, history, dockChanged = true))
        assertEquals(FinanceTransition.Tab, financeTransition(history, greenhouse, dockChanged = true))
        assertEquals(FinanceTransition.None, financeTransition(home, FinanceDestination.Tab(0), dockChanged = true))
    }
}
