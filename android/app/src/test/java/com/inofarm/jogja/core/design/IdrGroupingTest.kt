package com.inofarm.jogja.core.design

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class IdrGroupingTest {
    private fun shown(raw: String) = IdrGrouping.filter(AnnotatedString(raw)).text.text

    @Test fun groupsTheWholePartOnly() {
        assertEquals("1.234.567.890,50", shown("1234567890,50"))
        assertEquals("150.000", shown("150000"))
        assertEquals("999", shown("999"))
        assertEquals("-12.500", shown("-12500"))
        assertEquals("0,004", shown("0,004"))
        assertEquals("1.000,", shown("1000,"))
    }

    @Test fun anythingElseIsShownAsTyped() {
        assertEquals("", shown(""))
        assertEquals("12a3", shown("12a3"))
        assertEquals("1,2,3", shown("1,2,3"))
        assertEquals("1.000", shown("1.000"))
        assertEquals("000123", shown("000123"))
        assertEquals("-0500", shown("-0500"))
        assertEquals("0,5", shown("0,5"))
    }

    @Test fun cursorMappingRoundTripsAndStaysInBounds() {
        for (raw in listOf("1234567890,50", "-12500", "150000", "1000,", "7")) {
            val mapping = IdrGrouping.filter(AnnotatedString(raw))
            val length = mapping.text.length
            for (o in 0..raw.length) {
                val t = mapping.offsetMapping.originalToTransformed(o)
                assert(t in 0..length) { "$raw $o -> $t" }
                assertEquals("$raw $o", o, mapping.offsetMapping.transformedToOriginal(t))
            }
            for (t in 0..length) assert(mapping.offsetMapping.transformedToOriginal(t) in 0..raw.length)
            assertEquals(length, mapping.offsetMapping.originalToTransformed(raw.length))
        }
    }
}
