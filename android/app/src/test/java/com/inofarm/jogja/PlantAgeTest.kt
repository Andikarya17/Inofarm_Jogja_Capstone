package com.inofarm.jogja

import com.inofarm.jogja.core.cultivation.PlantAge
import org.junit.Assert.*
import org.junit.Test

class PlantAgeTest {
    @Test fun jakartaCalendarFirstDayTransplantAndLeapDay() {
        assertEquals(1L, PlantAge.calculate("2026-10-01", null, "2026-10-01T16:59:59Z").hss)
        assertEquals(2L, PlantAge.calculate("2026-10-01", null, "2026-10-01T17:00:00Z").hss)
        assertNull(PlantAge.calculate("2026-10-01", "2026-10-10", "2026-10-09T12:00:00+07:00").hst)
        assertEquals(1L, PlantAge.calculate("2026-10-01", "2026-10-10", "2026-10-10T00:00:00+07:00").hst)
        assertNull(PlantAge.calculate("2026-10-01", null, "2026-09-30T12:00:00+07:00").hss)
        assertEquals(3L, PlantAge.calculate("2024-02-28", null, "2024-03-01T00:00:00+07:00").hss)
        assertThrows(IllegalArgumentException::class.java) { PlantAge.calculate("2026-10-01", "2026-09-30", "2026-10-10T00:00:00+07:00") }
    }
}
