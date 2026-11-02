package com.inofarm.jogja.core.contracts

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** UI accepts a decimal comma, never grouping separators or an implicit zero. */
object InvestmentInputs {
    fun money(raw:String,initial:Boolean=false):String? {
        if(raw.isBlank()) return null
        val value=WireContract.decimal(raw.trim().replace(',','.'),2)
        require(!initial || value.signum()>=0) {"Investasi awal tidak boleh negatif."}
        return value.setScale(2).toPlainString()
    }
    fun horizon(raw:String):Int {require(Regex("[1-9][0-9]{0,2}").matches(raw));return raw.toInt().also {require(it in 1..100)}}
    fun rateFromPercent(raw:String):String? {
        if(raw.isBlank()) return null
        val value=try {WireContract.decimal(raw.trim().replace(',','.'),8,22).movePointLeft(2).setScale(8,RoundingMode.UNNECESSARY)}
        catch(error:ArithmeticException) {throw IllegalArgumentException("Diskonto memerlukan pembulatan. Kurangi angka desimal persen.",error)}
        require(value>BigDecimal("-1"));WireContract.decimal(value.toPlainString(),8)
        return value.toPlainString()
    }
    fun percentFromRate(raw:String)=BigDecimal(raw).movePointRight(2).stripTrailingZeros().toPlainString()
    fun baseDate(raw:String,horizon:Int):LocalDate {
        require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(raw))
        return LocalDate.parse(raw).also {require(it.year>=1 && it.year+horizon<=9999)}
    }
}
