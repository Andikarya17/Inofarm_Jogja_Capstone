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
        for(name in listOf("canonical-unicode.json","activity-operation.json","harvest-operation.json","expense-operation.json","cost-allocation-operation.json")) {
            val fixture=JSONObject(instrumentation.context.assets.open(name).bufferedReader(Charsets.UTF_8).use {it.readText()})
            assertEquals(name,fixture.getString("canonical_sha256"),WireContract.hash(fixture.getJSONObject(if(name=="canonical-unicode.json") "value" else "operation")))
        }
        for(value in listOf("\ud800","\udc00","a\ud800b")) assertThrows(IllegalArgumentException::class.java) {WireContract.canonical(value)}
    }
}
