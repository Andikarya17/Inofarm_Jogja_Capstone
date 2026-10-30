package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.DepreciationAmounts
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.math.BigDecimal

class DepreciationAmountsTest {
    @Test fun annualRemaindersAndAnniversariesAreExplicit() {
        val budgets = (1..3).map { DepreciationAmounts.year("100", "0", 3, it, LocalDate.parse("2024-02-29")) }
        assertEquals(listOf("33.33", "33.34", "33.33"), budgets.map {it.amount.toPlainString()})
        assertEquals(LocalDate.parse("2025-02-28"), budgets[1].start)
        assertEquals(LocalDate.parse("2026-02-28"), budgets[1].end)
    }
    @Test fun largeAndZeroBasesNeverOverAllocate() {
        val date = LocalDate.parse("2024-02-29")
        val sum = (1..997).map { DepreciationAmounts.year("999999999999999999.99", "0", 997, it, date).amount }.fold(BigDecimal.ZERO, BigDecimal::add)
        assertEquals("999999999999999999.99", sum.toPlainString())
        assertEquals("0.00", DepreciationAmounts.year("10", "10", 3, 1, date).amount.toPlainString())
        listOf("", "1e2", "-1", "1.001").forEach { invalid -> assertTrue(runCatching { DepreciationAmounts.year(invalid,"0",3,1,date) }.isFailure) }
    }
}
