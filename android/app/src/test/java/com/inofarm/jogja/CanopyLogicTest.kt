package com.inofarm.jogja

import com.inofarm.jogja.core.design.ageFigure
import com.inofarm.jogja.core.design.badgeText
import com.inofarm.jogja.core.design.cashShare
import com.inofarm.jogja.core.design.dateLabel
import com.inofarm.jogja.core.design.greetingForWib
import com.inofarm.jogja.core.design.initialsOf
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanopyLogicTest {
    @Test fun greetingFollowsWibWindows() {
        assertEquals("Selamat malam", greetingForWib(4, 59))
        assertEquals("Selamat pagi", greetingForWib(5, 0))
        assertEquals("Selamat pagi", greetingForWib(10, 59))
        assertEquals("Selamat siang", greetingForWib(11, 0))
        assertEquals("Selamat siang", greetingForWib(14, 59))
        assertEquals("Selamat sore", greetingForWib(15, 0))
        assertEquals("Selamat sore", greetingForWib(18, 29))
        assertEquals("Selamat malam", greetingForWib(18, 30))
        assertEquals("Selamat malam", greetingForWib(0, 0))
    }

    @Test fun cashShareIsNullWithoutPositiveAmounts() {
        assertNull(cashShare(null, null))
        assertNull(cashShare(BigDecimal.ZERO, BigDecimal("0.00")))
        assertNull(cashShare(BigDecimal("-5"), null))
    }

    @Test fun cashShareSplitsProportionallyAndSumsToOne() {
        val (a, b) = cashShare(BigDecimal("3000000.00"), BigDecimal("1000000.00"))!!
        assertEquals(0.75f, a, 0.0001f)
        assertEquals(1f, a + b, 0.0001f)
        assertEquals(1f to 0f, cashShare(BigDecimal("10"), null))
    }

    @Test fun ageFigurePrefersHstThenHss() {
        assertEquals(12 to "HST", ageFigure(12, 40))
        assertEquals(40 to "HSS", ageFigure(null, 40))
        assertNull(ageFigure(null, null))
    }

    @Test fun badgeCapsAtFiftyPlus() {
        assertEquals("7", badgeText(7))
        assertEquals("49", badgeText(49))
        assertEquals("50+", badgeText(50))
        assertEquals("50+", badgeText(300))
    }

    @Test fun initialsUseFirstTwoWords() {
        assertEquals("PS", initialsOf("Pengelola sintetis"))
        assertEquals("A", initialsOf("  andika "))
        assertEquals("AB", initialsOf("Ani Budi Cahya"))
        assertEquals("?", initialsOf("   "))
    }

    @Test fun dateLabelIsIndonesian() {
        assertEquals("Rabu, 11 Nov 2026", dateLabel(java.time.LocalDate.of(2026, 11, 11)))
        assertEquals("Minggu, 1 Mar 2026", dateLabel(java.time.LocalDate.of(2026, 3, 1)))
    }
}
