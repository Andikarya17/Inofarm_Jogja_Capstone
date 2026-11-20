package com.inofarm.jogja

import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.activities.domain.EntryDraft
import com.inofarm.jogja.feature.activities.domain.MaterialDraft
import com.inofarm.jogja.feature.activities.domain.MeasurementDraft
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The greenhouse entry form (user decision 17 Nov 2026) and the records it becomes. */
class EntryDraftTest {
    private val gh = "11111111-1111-4111-8111-111111111111"
    private val season = "22222222-2222-4222-8222-222222222222"
    private val groupA = "33333333-3333-4333-8333-333333333333"
    private val groupB = "44444444-4444-4444-8444-444444444444"
    private val mix = "55555555-5555-4555-8555-555555555555"
    private val gram = "66666666-6666-4666-8666-666666666666"
    private val bion = "77777777-7777-4777-8777-777777777777"
    private fun base() = EntryDraft(greenhouseId = gh, seasonId = season, date = "2026-11-17", time = "08:00", ageBasis = "hst", ageDay = "30")

    @Test fun anEntryNeedsTypedAgeAndAtLeastOneSection() {
        assertEquals(setOf("entry"), base().validation().keys)
        assertTrue("age" in base().copy(ageBasis = "", notes = "Cek").validation())
        assertTrue("age" in base().copy(ageDay = "0", notes = "Cek").validation())
        assertTrue(base().copy(notes = "Cek kebersihan").validation().isEmpty())
    }

    @Test fun ecIsRequiredButPpmIsOptional() {
        assertTrue("measurements.0.manual_ec" in base().copy(measurements = listOf(MeasurementDraft(ppm = "900"))).validation())
        assertTrue(base().copy(measurements = listOf(MeasurementDraft(ec = "1,8"))).validation().isEmpty())
    }

    @Test fun beforeAndAfterInjectionBecomeTwoMeasurementRecordsOfOneBatch() {
        val both = base().withMoments(setOf("before_injection", "after_injection")).let {
            it.copy(measurements = listOf(it.measurements[0].copy(ec = "1,6"), it.measurements[1].copy(ec = "2,1", ppm = "1000")))
        }
        val rows = both.sections().filter { it.type == ActivityType.MEASUREMENT }
        assertEquals(listOf("before_injection", "after_injection"), rows.map { it.measurementMoment })
        assertEquals(setOf(both.id), rows.map { it.entryBatchId }.toSet())
        assertEquals(2, rows.map { it.id }.toSet().size)
        assertTrue(rows[0].payload().isNull("manual_ppm"))
        assertEquals(both, EntryDraft.from(both.localJson()))
        val dropped = both.withMoments(setOf("before_injection"))
        assertEquals(listOf(MeasurementDraft("before_injection", ec = "1,6")), dropped.measurements)
        // A single block without a moment keeps its values when a moment is chosen.
        val plain = base().copy(measurements = listOf(MeasurementDraft(ec = "1,8")))
        assertEquals(listOf(MeasurementDraft("after_injection", ec = "1,8")), plain.withMoments(setOf("after_injection")).measurements)
    }

    @Test fun anEntryDraftSavedBeforeTwoMomentsReopensAsOneBlock() {
        val legacy = JSONObject().put("_local_form", JSONObject().put("entry", true).put("id", "88888888-8888-4888-8888-888888888888")
            .put("greenhouse_id", gh).put("season_id", season).put("date", "2026-11-17").put("time", "08:00")
            .put("ec", "1,8").put("ppm", "900").put("ph", "6").put("water_temperature", "").put("moment", "after_injection"))
        assertEquals(listOf(MeasurementDraft("after_injection", "1,8", "900", "6", "")), EntryDraft.from(legacy).measurements)
    }

    @Test fun sprayNeedsVarietyTimeAndMaterial() {
        val spray = base().copy(sprayOpen = true, sprayGroups = setOf(groupA), spray = listOf(MaterialDraft(name = "Bion M")))
        assertEquals(setOf("spray_period"), spray.validation().keys)
        assertTrue(spray.copy(sprayPeriod = "morning").validation().isEmpty())
        assertTrue("spray_groups" in spray.copy(sprayPeriod = "morning", sprayGroups = emptySet()).validation())
        assertTrue("spray" in spray.copy(sprayPeriod = "morning", spray = emptyList()).validation())
        // Half a dose is refused; no dose at all is fine for a spray.
        assertTrue("spray.0" in spray.copy(sprayPeriod = "morning", spray = listOf(MaterialDraft(name = "Bion M", quantity = "5"))).validation())
    }

    @Test fun oneEntryBecomesGreenhouseRecordsOnceAndSprayPerVariety() {
        val entry = base().copy(measurements = listOf(MeasurementDraft("after_injection", "1,8", "900", "6,1", "26")),
            nutrition = listOf(MaterialDraft(mix, gram, "250")), sprayOpen = true, sprayDate = "2026-11-16", sprayPeriod = "afternoon",
            sprayGroups = setOf(groupB, groupA), spray = listOf(MaterialDraft(bion), MaterialDraft(name = "Fungisida baru", unitId = gram, quantity = "15")),
            stage = "Pembesaran buah", notes = "Daun sehat").withSectionIds()
        val sections = entry.sections()
        assertEquals(listOf(ActivityType.MEASUREMENT, ActivityType.NUTRITION, ActivityType.SPRAY, ActivityType.SPRAY, ActivityType.STAGE), sections.map { it.type })
        assertEquals(listOf("", "", groupA, groupB, ""), sections.map { it.groupId })
        sections.forEach { section ->
            assertTrue(section.validation(false).toString(), section.validation(false).isEmpty())
            val payload = section.payload()
            assertEquals(season, payload.getString("season_id"))
            assertEquals(entry.id, payload.getString("entry_batch_id"))
            assertEquals("hst", payload.getString("age_basis"))
            assertEquals(30, payload.getInt("age_day"))
            assertEquals("Daun sehat", payload.getString("notes"))
        }
        val measurement = sections[0].payload()
        assertTrue(measurement.isNull("planting_group_id"))
        assertEquals("1.8", measurement.getString("manual_ec"))
        assertEquals("after_injection", measurement.getJSONObject("observations").getString("measurement_moment"))
        assertEquals("26", measurement.getJSONObject("observations").getString("water_temperature_c"))
        val nutrition = sections[1].payload()
        assertTrue(nutrition.isNull("manual_ppm"))
        val spray = sections[2].payload()
        assertEquals(groupA, spray.getString("planting_group_id"))
        assertEquals("2026-11-16", spray.getString("spray_date"))
        assertEquals("afternoon", spray.getString("spray_period"))
        val materials = spray.getJSONArray("materials")
        assertTrue(materials.getJSONObject(0).isNull("quantity"))
        assertTrue(materials.getJSONObject(0).isNull("unit_id"))
        assertEquals("Fungisida baru", materials.getJSONObject(1).getString("material_name"))
        assertFalse(materials.getJSONObject(1).has("material_id"))
        assertFalse(sections[0].payload().has("spray_date"))
    }

    @Test fun sectionIdsAreStableAcrossSavesAndRoundTrips() {
        val first = base().copy(measurements = listOf(MeasurementDraft(ec = "1", ppm = "2"))).withSectionIds()
        val again = EntryDraft.from(first.localJson()).withSectionIds()
        assertEquals(first, again)
        assertEquals(first.sections().map { it.id }, again.sections().map { it.id })
        // Adding a section later keeps the existing ids and adds one.
        val more = again.copy(stage = "Polinasi").withSectionIds()
        assertEquals(first.sections().first().id, more.sections().first().id)
        assertEquals(2, more.sections().map { it.id }.toSet().size)
        assertTrue(EntryDraft.isEntry(first.localJson()))
    }

    @Test fun notesAloneAreOneGeneralRecord() {
        val sections = base().copy(notes = "Bersihkan talang").sections()
        assertEquals(listOf(ActivityType.GENERAL), sections.map { it.type })
        assertTrue(sections.single().validation(false).isEmpty())
    }

    @Test fun aGreenhouseLevelServerRecordOpensForCorrection() {
        val server = JSONObject().put("planting_group_id", JSONObject.NULL).put("season_id", season).put("entry_batch_id", "88888888-8888-4888-8888-888888888888")
            .put("activity_type", "measurement").put("occurred_at", "2026-11-17T08:00:00+07:00").put("manual_ec", "1.800000").put("manual_ppm", "900.000000")
            .put("manual_ph", JSONObject.NULL).put("actual_stage", JSONObject.NULL).put("work_state", "completed").put("skipped_reason", JSONObject.NULL)
            .put("materials", org.json.JSONArray()).put("notes", "").put("age_basis", "hss").put("age_day", 48)
            .put("observations", JSONObject().put("measurement_moment", "before_injection"))
        val draft = ActivityDraft.from("99999999-9999-4999-8999-999999999999", gh, server, 1)
        assertTrue(draft.greenhouseLevel)
        assertEquals("48", draft.ageDay)
        assertEquals("before_injection", draft.measurementMoment)
        val correction = draft.copy(reason = "Salah ketik").payload()
        assertTrue(correction.isNull("planting_group_id"))
        assertEquals(48, correction.getInt("age_day"))
        assertEquals(draft, ActivityDraft.from(draft.id, gh, draft.localJson(), 1))
    }
}
