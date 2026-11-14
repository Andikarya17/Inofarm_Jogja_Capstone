package com.inofarm.jogja

import com.inofarm.jogja.core.design.dockItemAt
import org.junit.Assert.assertEquals
import org.junit.Test

class FarmDockSlotsTest {
    @Test fun managerDockKeepsItsFourItemsAroundTheCatatGap() {
        assertEquals(listOf(0, 1, null, 2, 3), (0..4).map { dockItemAt(it, 4) })
    }

    @Test fun workerDockHasOneItemEachSideOfTheCatatGap() {
        assertEquals(listOf(0, null, 1), (0..2).map { dockItemAt(it, 2) })
    }
}
