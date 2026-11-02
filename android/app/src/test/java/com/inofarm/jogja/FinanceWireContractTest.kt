package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.WireContract
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FinanceWireContractTest {
    @Test fun sharedFixtureMatchesIndependentHashAndReorderedKeys() {
        val fixture = JSONObject(File("../../contracts/v2/fixtures/activity-operation.json").readText())
        assertEquals(fixture.getString("canonical_sha256"), WireContract.hash(fixture.getJSONObject("operation")))
        val source = fixture.getJSONObject("operation")
        val reversed = JSONObject().also { result -> source.keys().asSequence().toList().reversed().forEach { result.put(it, source.get(it)) } }
        assertEquals(WireContract.hash(source), WireContract.hash(reversed))
    }
    @Test fun decimalDoesNotCoerceLocaleExponentOrOverflow() {
        assertEquals("0.00", WireContract.decimal("0.00", 2).toPlainString())
        listOf("", "1,5", "NaN", "1e2", "01", "1.001", "1000000000000000000.00").forEach {
            assertThrows(IllegalArgumentException::class.java) { WireContract.decimal(it, 2) }
        }
    }
    @Test fun sharedHarvestFixturePreservesIntegerAndDecimalWireTypes() {
        val fixture=JSONObject(File("../../contracts/v2/fixtures/harvest-operation.json").readText())
        assertEquals(fixture.getString("canonical_sha256"),WireContract.hash(fixture.getJSONObject("operation")))
        val item=fixture.getJSONObject("operation").getJSONObject("payload").getJSONArray("items").getJSONObject(0)
        assertEquals(10,item.getInt("fruit_count"));assertEquals("15.250000",item.getString("weight_kg"))
    }
    @Test fun expenseAndAllocationFixturesMatchIndependentHashesAndMoneyStrings() {
        for(kind in listOf("expense","cost-allocation")) {
            val fixture=JSONObject(File("../../contracts/v2/fixtures/$kind-operation.json").readText())
            val op=fixture.getJSONObject("operation")
            assertEquals(fixture.getString("canonical_sha256"),WireContract.hash(op))
            assertTrue(op.getJSONObject("payload").get("amount") is String)
            assertEquals(op.getJSONObject("payload").getString("amount"),WireContract.decimal(op.getJSONObject("payload").getString("amount"),2).toPlainString())
        }
    }
}
