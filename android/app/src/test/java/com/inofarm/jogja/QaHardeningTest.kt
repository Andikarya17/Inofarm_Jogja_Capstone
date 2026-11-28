package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.cultivation.ManualTime
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.FarmFormat
import com.inofarm.jogja.feature.activities.domain.EntryDraft
import com.inofarm.jogja.feature.activities.domain.MeasurementDraft
import com.inofarm.jogja.feature.admin.AdminForm
import com.inofarm.jogja.feature.admin.AdminResource
import com.inofarm.jogja.feature.admin.workerAssignment
import com.inofarm.jogja.feature.history.groupEntries
import com.inofarm.jogja.feature.sales.WhatsappNumber
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Rules added after the pengelola QA on the app database (28 Nov 2026). */
class QaHardeningTest {
    private val gh = "11111111-1111-4111-8111-111111111111"
    private val season = "22222222-2222-4222-8222-222222222222"
    private fun entry() = EntryDraft(greenhouseId = gh, seasonId = season, date = "2026-11-17", time = "08:00", ageBasis = "hss", ageDay = "34")

    @Test fun phOutOfRangeLandsOnThePhFieldInsteadOfNowhere() {
        val errors = entry().copy(measurements = listOf(MeasurementDraft("before_injection", ec = "2,0", ph = "15"))).validation()
        assertEquals("pH harus desimal 0–14.", errors["measurements.0.manual_ph"])
    }

    @Test fun futureDatesAreRefusedButBackDatesStayFree() {
        val today = LocalDate.of(2026, 11, 28)
        assertTrue(ManualTime.afterToday("2026-12-31T12:17:00+07:00", today))
        assertFalse(ManualTime.afterToday("2026-11-28T23:59:00+07:00", today))
        assertFalse(ManualTime.afterToday("2025-01-01T08:00:00+07:00", today))
    }

    @Test fun whatsappFollowsTheServerRule() {
        assertEquals("6281200000000", WhatsappNumber.normalize("0812-0000-0000"))
        assertEquals("6281234567890", WhatsappNumber.normalize("+62 812 3456 7890"))
        assertNull(WhatsappNumber.normalize("abc12"))
        assertNull(WhatsappNumber.normalize("0812"))
    }

    @Test fun targetsCheckOrderAndPhRangeBeforeTheServer() {
        val id = "33333333-3333-4333-8333-333333333333"
        fun target(min: String, max: String, parameter: String = "manual_ec") = AdminForm(AdminResource.TARGET, values = mapOf(
            "planting_group_id" to id, "activity_type" to "measurement", "parameter" to parameter, "unit" to AdminForm.TARGET_UNITS.getValue(parameter),
            "effective_from" to "2026-11-28T00:00:00+07:00", "minimum" to min, "maximum" to max))
        val reversed = runCatching { target("3", "2").payload() }.exceptionOrNull()?.message
        assertEquals("Batas minimum tidak boleh melebihi batas maksimum.", reversed)
        assertEquals("Nilai pH harus 0 sampai 14.", runCatching { target("5", "15", "manual_ph").payload() }.exceptionOrNull()?.message)
        assertTrue(runCatching { target("1,8", "2,4").payload() }.isSuccess)
    }

    @Test fun serverNumbersAndDatesReadLikeAPerson() {
        assertEquals("16 Nov 2026", FarmFormat.date("2026-11-16"))
        assertEquals("29 Nov 2026 07:00 WIB", FarmFormat.dateTime("2026-11-29T00:00:00.000000Z"))
        assertEquals("−Rp52.592,04", FarmFormat.money("-52592.04"))
        assertEquals("Rp15.000", FarmFormat.money(BigDecimal("15000.00")))
        assertEquals("2,5 kg", FarmFormat.kg("2.500000"))
        assertEquals("15000", FarmFormat.input("15000.00"))
        assertEquals("4", FarmFormat.input("4.000000"))
        assertEquals("2,5", FarmFormat.input("2.5"))
        assertEquals("2,5", FarmFormat.input("2,5"))
        assertEquals("28 Nov 2026 sampai 27 Nov 2027", FarmFormat.period("2026-11-28", "2027-11-28"))
    }

    @Test fun sectionsOfOneEntryShowAsOneGroup() {
        fun record(id: String, batch: String?) = LocalRecord(id, "activity", gh, 1, null, 1,
            JSONObject().put("activity_type", "measurement").put("entry_batch_id", batch ?: JSONObject.NULL), null, null, SyncState.ACCEPTED, null)
        val rows = listOf(record("a", "b1"), record("b", "b1"), record("c", null), record("d", "b1"), record("e", "b2"))
        val groups = groupEntries(rows, OperationalKind.ACTIVITY)
        assertEquals(listOf(listOf("a", "b", "d"), listOf("c"), listOf("e")), groups.map { group -> group.map { it.id } })
        assertEquals(5, groupEntries(rows, OperationalKind.HARVEST).size)
    }

    @Test fun onlyWorkerAccountsCountAsPekerja() {
        val users = listOf(JSONObject().put("id", "m").put("role", "manager"), JSONObject().put("id", "w").put("role", "worker"))
        assertFalse(workerAssignment(JSONObject().put("user_id", "m"), users))
        assertTrue(workerAssignment(JSONObject().put("user_id", "w"), users))
        assertTrue(workerAssignment(JSONObject().put("user_id", "m"), emptyList()))
    }
}
