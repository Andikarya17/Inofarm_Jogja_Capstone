package com.inofarm.jogja

import com.inofarm.jogja.feature.activities.domain.ManualMeasurement
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Inofarm 20 Nov 2026: PPM is optional, and of two readings at one time the one after injection is the latest. */
class ManualMeasurementTest {
    private val today = LocalDate.parse("2026-11-20")
    private fun body(ec: Any?, ppm: Any?, moment: String? = null) = JSONObject().put("id", "11111111-1111-4111-8111-111111111111")
        .put("activity_type", "measurement").put("work_state", "completed").put("record_state", "active")
        .put("occurred_at", "2026-11-20T08:00:00+07:00").put("manual_ec", ec ?: JSONObject.NULL).put("manual_ppm", ppm ?: JSONObject.NULL)
        .apply { moment?.let { put("observations", JSONObject().put("measurement_moment", it)) } }

    @Test fun anEcOnlyReadingIsAMeasurement() {
        val pair = ManualMeasurement.parse(body("1.6", null, "before_injection"), today)!!
        assertEquals("1.6", pair.ec)
        assertNull(pair.ppm)
        assertEquals("before_injection", pair.moment)
    }

    @Test fun afterInjectionWinsAtTheSameTime() {
        val at = Instant.parse("2026-11-20T01:00:00Z")
        val before = ManualMeasurement("b", at, "1.6", null, null, false, moment = "before_injection")
        val after = ManualMeasurement("a", at, "2.1", "1000", null, false, moment = "after_injection")
        assertEquals("a", ManualMeasurement.latestOf(listOf(before, after))!!.id)
        assertEquals("b", ManualMeasurement.latestOf(listOf(before))!!.id)
        assertNull(ManualMeasurement.latestOf(emptyList()))
    }
}
