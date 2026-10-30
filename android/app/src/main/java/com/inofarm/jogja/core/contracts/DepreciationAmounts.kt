package com.inofarm.jogja.core.contracts

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Preview only; accepted allocation budgets remain authoritative at the server. */
object DepreciationAmounts {
    const val formulaVersion = "anniversary-cumulative-half-up-v1"
    data class Budget(val start: LocalDate, val end: LocalDate, val amount: BigDecimal)
    fun year(cost: String, residual: String, life: Int, year: Int, inService: LocalDate): Budget {
        val pattern = Regex("(?:0|[1-9][0-9]{0,17})(?:\\.[0-9]{1,2})?")
        require(pattern.matches(cost) && pattern.matches(residual))
        val base = BigDecimal(cost).subtract(BigDecimal(residual))
        require(base.signum() >= 0 && life in 1..1000 && year in 1..life && inService.year >= 1 && inService.year + life <= 9999)
        fun cumulative(k: Int) = base.multiply(BigDecimal(k)).divide(BigDecimal(life), 2, RoundingMode.HALF_UP)
        return Budget(inService.plusYears((year - 1).toLong()), inService.plusYears(year.toLong()), cumulative(year).subtract(cumulative(year - 1)))
    }
}
