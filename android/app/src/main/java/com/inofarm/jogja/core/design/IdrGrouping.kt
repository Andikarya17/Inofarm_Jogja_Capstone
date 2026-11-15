package com.inofarm.jogja.core.design

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows thousands separators in a typed IDR amount ("1234567,50" reads "1.234.567,50") without touching the value:
 * validation and the request still see exactly what was typed. Anything that is not a plain number (a stray letter,
 * a second comma) is shown as typed, so the field never hides what the validator will reject.
 */
internal object IdrGrouping : VisualTransformation {
    private val plain = Regex("""^-?\d+(,\d*)?$""")

    fun group(raw: String): String = if (!plain.matches(raw)) raw else {
        val sign = if (raw.startsWith("-")) "-" else ""
        val body = raw.removePrefix("-")
        val whole = body.substringBefore(',')
        val rest = body.substring(whole.length)
        sign + whole.reversed().chunked(3).joinToString(".").reversed() + rest
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = group(raw)
        if (shown == raw) return TransformedText(text, OffsetMapping.Identity)
        val sign = if (raw.startsWith("-")) 1 else 0
        val digits = raw.removePrefix("-").substringBefore(',').length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + separatorsBefore(offset)
            override fun transformedToOriginal(offset: Int): Int {
                var original = 0
                while (original < raw.length && original + separatorsBefore(original + 1) < offset) original++
                return original.coerceIn(0, raw.length)
            }
            private fun separatorsBefore(offset: Int): Int {
                val inWhole = (offset - sign).coerceIn(0, digits)
                // A dot is placed before a digit when the digits left to its right are a positive multiple of three.
                var count = 0
                for (i in 1 until inWhole) if ((digits - i) % 3 == 0) count++
                return count
            }
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
