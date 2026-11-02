package com.inofarm.jogja

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.WireContract
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalUnicodeDeviceTest {
    @Test fun nativeJsonAndUtf8StringsMatchIndependentContractHashes() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assertTrue(instrumentation.targetContext.packageName.endsWith(".finance"))
        for(name in listOf("canonical-unicode.json","activity-operation.json","harvest-operation.json","expense-operation.json","cost-allocation-operation.json","attachment-operation.json","sale-operation.json")) {
            val fixture=JSONObject(instrumentation.context.assets.open(name).bufferedReader(Charsets.UTF_8).use {it.readText()})
            assertEquals(name,fixture.getString("canonical_sha256"),WireContract.hash(fixture.getJSONObject(if(name=="canonical-unicode.json") "value" else "operation")))
        }
        for(value in listOf("\ud800","\udc00","a\ud800b")) assertThrows(IllegalArgumentException::class.java) {WireContract.canonical(value)}
    }
    @Test fun nativeSalePreviewMatchesIndependentDecimalOracle() {
        val fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("sale-operation.json").bufferedReader(Charsets.UTF_8).use {it.readText()})
        val rows=fixture.getJSONObject("operation").getJSONObject("payload").getJSONArray("items")
        val quote=com.inofarm.jogja.core.contracts.SaleAmounts.quote((0 until rows.length()).map {rows.getJSONObject(it).let {it.getString("quantity_kg") to it.getString("price_per_kg")}})
        assertEquals(fixture.getJSONObject("expected").getString("total_amount"),quote.total.toPlainString())
        assertEquals(fixture.getJSONObject("expected").getString("exact_total"),quote.exactTotal.toPlainString())
        assertEquals(fixture.getJSONObject("expected").getString("total_weight_kg"),quote.weightKg.toPlainString())
    }
}
