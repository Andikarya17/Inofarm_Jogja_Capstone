package com.inofarm.jogja.core.contracts

import java.math.BigDecimal
import java.math.RoundingMode

data class SaleQuote(val exactTotal:BigDecimal,val total:BigDecimal,val weightKg:BigDecimal)

/** Pure preview math. Accepted totals and available harvest remain authoritative on the server. */
object SaleAmounts {
    fun quote(lines:List<Pair<String,String>>):SaleQuote {
        require(lines.size in 1..30)
        var exact=BigDecimal.ZERO;var weight=BigDecimal.ZERO
        lines.forEach {(quantityText,priceText) ->
            require(!quantityText.startsWith('-') && !priceText.startsWith('-'))
            val quantity=WireContract.decimal(quantityText,6);val price=WireContract.decimal(priceText,6)
            require(quantity.signum()>0)
            exact=exact.add(quantity.multiply(price));weight=weight.add(quantity)
        }
        val total=exact.setScale(2,RoundingMode.HALF_UP);weight=weight.setScale(6)
        WireContract.decimal(total.toPlainString(),2);WireContract.decimal(weight.toPlainString(),6)
        return SaleQuote(exact.setScale(12),total,weight)
    }
}
