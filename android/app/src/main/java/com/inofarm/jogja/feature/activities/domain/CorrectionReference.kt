package com.inofarm.jogja.feature.activities.domain

import com.inofarm.jogja.core.cultivation.ManualTime
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.OffsetDateTime
import java.util.Locale

/** A worker's correction keeps the historical reference unless the event time moved; the manager still chooses by hand. */
object CorrectionReference {
    const val PRESERVE = "preserve"
    const val REFRESH = "refresh"

    fun policy(date: String, time: String, acceptedOccurredAt: String?): String =
        policyBetween(runCatching { ManualTime.encode(date, time) }.getOrNull(), acceptedOccurredAt)

    fun policyBetween(draftOccurredAt: String?, acceptedOccurredAt: String?): String {
        val draft = instant(draftOccurredAt) ?: return PRESERVE
        val accepted = instant(acceptedOccurredAt) ?: return PRESERVE
        return if (draft == accepted) PRESERVE else REFRESH
    }

    private fun instant(value: String?) = value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
}

/** One plain line for the target a worker is recording against, or null when the target has no usable value. */
fun workerTargetLine(parameter: String, unit: String, single: String?, minimum: String?, maximum: String?): String? {
    val format = DecimalFormat("#,##0.######", DecimalFormatSymbols(Locale.forLanguageTag("id-ID")))
    fun number(text: String?) = text?.toBigDecimalOrNull()?.let { format.format(it.stripTrailingZeros()) }
    val value = number(single) ?: if (minimum != null && maximum != null) "${number(minimum) ?: return null}–${number(maximum) ?: return null}" else return null
    val name = if (parameter == "manual_ppm") "PPM" else "pH"
    return "Target $name $value${unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}"
}
