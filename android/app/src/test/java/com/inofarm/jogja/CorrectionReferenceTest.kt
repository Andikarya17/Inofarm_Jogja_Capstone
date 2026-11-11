package com.inofarm.jogja

import com.inofarm.jogja.feature.activities.domain.CorrectionReference
import com.inofarm.jogja.feature.activities.domain.workerTargetLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorrectionReferenceTest {
    private val accepted = "2026-11-11T08:30:00+07:00"

    @Test fun unchangedTimeKeepsTheHistoricalReference() {
        assertEquals("preserve", CorrectionReference.policy("2026-11-11", "08:30", accepted))
        assertEquals("preserve", CorrectionReference.policy("2026-11-11", "08:30:00", "2026-11-11T01:30:00Z"))
    }

    @Test fun changedDateOrTimeRefreshesTheReference() {
        assertEquals("refresh", CorrectionReference.policy("2026-11-11", "08:45", accepted))
        assertEquals("refresh", CorrectionReference.policy("2026-11-12", "08:30", accepted))
        assertEquals("refresh", CorrectionReference.policy("2026-11-11", "08:30:15", accepted))
    }

    @Test fun missingOrInvalidTimesKeepTheReference() {
        assertEquals("preserve", CorrectionReference.policy("2026-11-11", "08:30", null))
        assertEquals("preserve", CorrectionReference.policy("bukan-tanggal", "08:30", accepted))
        assertEquals("preserve", CorrectionReference.policy("2026-11-11", "25:99", accepted))
    }

    @Test fun conflictComparesTheLocalRevisionWithTheServerRevision() {
        assertEquals("preserve", CorrectionReference.policyBetween(accepted, "2026-11-11T01:30:00Z"))
        assertEquals("refresh", CorrectionReference.policyBetween("2026-11-11T09:00:00+07:00", accepted))
        assertEquals("preserve", CorrectionReference.policyBetween(null, accepted))
    }

    @Test fun targetLineIsOnePlainSentence() {
        assertEquals("Target PPM 800–1.000 ppm", workerTargetLine("manual_ppm", "ppm", null, "800.000000", "1000"))
        assertEquals("Target PPM 850 ppm", workerTargetLine("manual_ppm", "ppm", "850.000000", null, null))
        assertEquals("Target pH 6,5", workerTargetLine("manual_ph", "", "6.5", null, null))
    }

    @Test fun targetWithoutUsableValueShowsNothing() {
        assertNull(workerTargetLine("manual_ppm", "ppm", null, null, "1000"))
        assertNull(workerTargetLine("manual_ppm", "ppm", null, "abc", "1000"))
    }
}
