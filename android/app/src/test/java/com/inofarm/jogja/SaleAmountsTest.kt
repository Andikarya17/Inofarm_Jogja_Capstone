package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.SaleAmounts
import com.inofarm.jogja.core.contracts.WireContract
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SaleAmountsTest {
    @Test fun independentFixtureBindsUtf8OperationAndTransactionRoundingOnce() {
        val fixture=JSONObject(File("../../contracts/v2/fixtures/sale-operation.json").readText())
        val op=fixture.getJSONObject("operation");assertEquals(fixture.getString("canonical_sha256"),WireContract.hash(op))
        val lines=op.getJSONObject("payload").getJSONArray("items")
        val quote=SaleAmounts.quote((0 until lines.length()).map {lines.getJSONObject(it).let {it.getString("quantity_kg") to it.getString("price_per_kg")}})
        val expected=fixture.getJSONObject("expected")
        assertEquals(expected.getString("exact_total"),quote.exactTotal.toPlainString());assertEquals(expected.getString("total_amount"),quote.total.toPlainString());assertEquals(expected.getString("total_weight_kg"),quote.weightKg.toPlainString())
        assertEquals("0.01",SaleAmounts.quote(listOf("1.000001" to "0.005")).total.toPlainString())
        assertEquals("0.00",SaleAmounts.quote(listOf("1" to "0")).total.toPlainString())
    }
    @Test fun overflowZeroWeightSignLocaleAndExponentAreRejected() {
        for(line in listOf("0" to "1","-0" to "1","1" to "-0","1,5" to "1","1" to "1e2","99999999999999.999999" to "99999999999999.999999")) assertThrows(IllegalArgumentException::class.java) {SaleAmounts.quote(listOf(line))}
        assertThrows(IllegalArgumentException::class.java) {SaleAmounts.quote(listOf("99999999999999.999999" to "0","0.000001" to "0"))}
    }
}
