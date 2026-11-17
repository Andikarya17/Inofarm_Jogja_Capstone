package com.inofarm.jogja

import com.inofarm.jogja.feature.admin.AdminForm
import com.inofarm.jogja.feature.admin.AdminResource
import com.inofarm.jogja.feature.admin.WizardRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenhouseWizardRulesTest {
    private val id = "11111111-1111-4111-8111-111111111111"

    @Test fun greenhouseNameMustBeFilledAndNotTooLong() {
        assertEquals("Isi nama greenhouse.", WizardRules.greenhouseNameError("  "))
        assertEquals("Nama maksimal 150 karakter.", WizardRules.greenhouseNameError("x".repeat(151)))
        assertNull(WizardRules.greenhouseNameError("GH Utara"))
    }

    @Test fun completePlantingWithOneOrTwoVarietiesHasNoErrors() {
        assertTrue(WizardRules.plantingErrors("Musim 1", "2026-11-10", listOf("Inthanon" to "120"), "2026-11-10", "").isEmpty())
        assertTrue(WizardRules.plantingErrors("Musim 1", "2026-11-10", listOf("Inthanon" to "120", "Sweetnet" to "80"), "2026-11-10", "2026-11-20").isEmpty())
    }

    @Test fun eachPlantingFieldReportsItsOwnErrorPerVariety() {
        val errors = WizardRules.plantingErrors("", "10-11-2026", listOf("" to "-3", " " to "x"), "salah", "")
        assertEquals(setOf("season", "start", "variety0", "population0", "variety1", "population1", "sowing"), errors.keys)
    }

    @Test fun secondVarietyMustDifferIgnoringCaseAndSpaces() {
        val errors = WizardRules.plantingErrors("M", "2026-11-10", listOf("Inthanon" to "10", " inthanon " to "10"), "2026-11-10", "")
        assertEquals(setOf("variety1"), errors.keys)
    }

    @Test fun transplantMayNotBeBeforeSowing() {
        val errors = WizardRules.plantingErrors("M", "2026-11-10", listOf("V" to "10"), "2026-11-10", "2026-11-09")
        assertEquals(setOf("transplant"), errors.keys)
    }

    @Test fun wizardFormsProducePayloadsTheServerAccepts() {
        val season = AdminForm(AdminResource.SEASON, values = mapOf("greenhouse_id" to id, "name" to "Musim 1", "start_date" to "2026-11-10")).payload()
        assertEquals(setOf("greenhouse_id", "name", "start_date", "end_date"), season.keys().asSequence().toSet())
        val group = AdminForm(AdminResource.GROUP, values = mapOf("season_id" to id, "variety_id" to id, "name" to "A",
            "population" to "120", "sowing_date" to "2026-11-10", "transplant_date" to "")).payload()
        assertTrue(group.isNull("transplant_date"))
        assertFalse(group.has("reason"))
    }
}
