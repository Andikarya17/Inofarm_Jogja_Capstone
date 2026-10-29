package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.WireContract
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CanonicalUnicodeTest {
    @Test fun unicodeUtf8AndJsonEscapesMatchWireRatherThanPlatformQuoteRules() {
        val text="— € é 中 🍈 / \u2028\u2029 \u0085 \" \\ \b\t\n\u000c\r\u0000\u001f"
        val expected="\"— € é 中 🍈 / \u2028\u2029 \u0085 \\\" \\\\ \\b\\t\\n\\f\\r\\u0000\\u001f\""
        assertEquals(expected,WireContract.canonical(text))
        for(name in listOf("expense-operation.json","cost-allocation-operation.json","canonical-unicode.json")) {
            val fixture=JSONObject(File("../../contracts/v2/fixtures/$name").readText())
            assertEquals(fixture.getString("canonical_sha256"),WireContract.hash(fixture.getJSONObject(if(name=="canonical-unicode.json") "value" else "operation")))
        }
    }
    @Test fun malformedSurrogateIsRejectedBeforeAnAmbiguousOperationCanBeHashed() {
        for(value in listOf("\ud800","\udc00","a\ud800b")) assertThrows(IllegalArgumentException::class.java) {WireContract.canonical(value)}
    }
}
