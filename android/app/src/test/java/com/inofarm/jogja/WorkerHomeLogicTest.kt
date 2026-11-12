package com.inofarm.jogja

import com.inofarm.jogja.feature.context.HomeRank
import com.inofarm.jogja.feature.context.PpmPosition
import com.inofarm.jogja.feature.context.daySession
import com.inofarm.jogja.feature.context.orderForHome
import com.inofarm.jogja.feature.context.ppmPosition
import com.inofarm.jogja.feature.context.taskTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkerHomeLogicTest {
    @Test fun rangeDecidesBelowAboveAndWithin() {
        assertEquals(PpmPosition.BELOW, ppmPosition("1300", "1500", null, null, "1250"))
        assertEquals(PpmPosition.ABOVE, ppmPosition("1300", "1500", null, null, "1600.5"))
        assertEquals(PpmPosition.WITHIN, ppmPosition("1300", "1500", null, null, "1300"))
    }

    @Test fun singleValueNeedsToleranceAndNeverGuessesWithin() {
        assertEquals(PpmPosition.UNKNOWN, ppmPosition(null, null, "1400", null, "1400"))
        assertEquals(PpmPosition.BELOW, ppmPosition(null, null, "1400", "50", "1349"))
        assertEquals(PpmPosition.WITHIN, ppmPosition(null, null, "1400", "50", "1450"))
        assertEquals(PpmPosition.UNKNOWN, ppmPosition(null, null, null, null, "1400"))
        assertEquals(PpmPosition.UNKNOWN, ppmPosition("1300", "1500", null, null, "abc"))
    }

    @Test fun taskTitleUsesFirstNonBlankNoteLineOrTheTypeLabel() {
        assertEquals("Pemupukan pagi", taskTitle("\n  Pemupukan pagi \nCatatan lain", "Nutrisi"))
        assertEquals("Nutrisi", taskTitle("   ", "Nutrisi"))
        assertEquals("Nutrisi", taskTitle(null, "Nutrisi"))
    }

    @Test fun sessionSwitchesAtNoonWib() {
        assertEquals("Pagi", daySession(11))
        assertEquals("Sore", daySession(12))
    }

    @Test fun openWorkComesFirstAndTiesBreakByNameWithInactiveAndNoPlantingLast() {
        val rows = listOf(
            HomeRank(true, true, 0, true, "Selesai A"),
            HomeRank(true, false, 0, false, "Tanpa tanam"),
            HomeRank(false, false, 0, false, "Nonaktif"),
            HomeRank(true, true, 2, true, "Zeta"),
            HomeRank(true, true, 0, false, "alfa"),
        )
        assertEquals(listOf("alfa", "Zeta", "Selesai A", "Tanpa tanam", "Nonaktif"), orderForHome(rows) { it }.map { it.name })
    }
}
