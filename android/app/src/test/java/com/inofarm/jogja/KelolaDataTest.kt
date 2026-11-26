package com.inofarm.jogja

import com.inofarm.jogja.feature.admin.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class KelolaDataTest {
    private val now = Instant.parse("2026-11-21T05:00:00Z")
    private fun gh(id: String, name: String, active: Boolean = true) = JSONObject().put("id", id).put("name", name).put("active", active)
    private fun period(state: String, group: String? = null) = JSONObject().put("state", state).put("group_id", group ?: JSONObject.NULL)
        .put("variety", if (group == null) JSONObject.NULL else "Yellow hami").put("group_name", if (group == null) JSONObject.NULL else "Kelompok A")

    @Test fun overviewCountsOnlyCurrentAssignmentsAndMarksMissingTargets() {
        val assignments = listOf(
            JSONObject().put("greenhouse_id", "a").put("active", true).put("effective_until", JSONObject.NULL),
            JSONObject().put("greenhouse_id", "a").put("active", true).put("effective_until", "2026-11-20T00:00:00Z"),
            JSONObject().put("greenhouse_id", "a").put("active", false).put("effective_until", JSONObject.NULL),
            JSONObject().put("greenhouse_id", "b").put("active", true).put("effective_until", "2026-12-01T00:00:00Z"))
        val targets = listOf(JSONObject().put("planting_group_id", "gb").put("active", true),
            JSONObject().put("planting_group_id", "ga").put("active", false))
        val users = listOf(JSONObject().put("role", "manager").put("active", true), JSONObject().put("role", "worker").put("active", true),
            JSONObject().put("role", "worker").put("active", false))
        val overview = buildKelolaOverview(listOf(gh("b", "GH2"), gh("a", "GH1"), gh("c", "GH3"), gh("d", "GH0", false)),
            mapOf("a" to period("ready", "ga"), "b" to period("ready", "gb"), "c" to period("no_active_period")), assignments, targets, users, now)
        assertEquals(listOf("GH1", "GH2", "GH3", "GH0"), overview.greenhouses.map { it.name })
        val (a, b, c, d) = overview.greenhouses
        assertEquals(1, a.workers); assertEquals(false, a.hasTargets); assertEquals("Yellow hami", a.variety)
        assertEquals(1, b.workers); assertEquals(true, b.hasTargets)
        assertNull(c.hasTargets); assertNull(c.variety); assertEquals("no_active_period", c.periodState)
        assertFalse(d.active); assertNull(d.periodState)
        assertEquals(1, overview.managers); assertEquals(1, overview.workers); assertEquals(1, overview.inactiveAccounts)
    }

    @Test fun routesRoundTripAndStartFromTheRightPage() {
        assertEquals("people", initialRoute(AdminResource.USER, null))
        assertEquals("gh:x", initialRoute(AdminResource.GREENHOUSE, "x"))
        assertEquals("list:GREENHOUSE:-", initialRoute(AdminResource.GREENHOUSE, null))
        assertEquals(AdminResource.MATERIAL to "x", parseList(initialRoute(AdminResource.MATERIAL, "x")))
        assertNull(parseList("gh:x")); assertNull(parseList("list:NOPE:x"))
    }

    @Test fun rowsReadTheirOwnFieldsAndSeasonsFilterByStatus() {
        val spray = JSONObject().put("name", "Daconil").put("purpose", "spray").put("dimension", "mass").put("active", true)
        assertEquals("Semprot", masterRowDetail(AdminResource.MATERIAL, spray))
        assertEquals("Massa", masterRowDetail(AdminResource.MATERIAL, spray.put("purpose", JSONObject.NULL)))
        val target = JSONObject().put("parameter", "manual_ppm").put("target_value", "1200").put("unit", "ppm").put("minimum", JSONObject.NULL)
        assertEquals("PPM", masterRowTitle(AdminResource.TARGET, target)); assertEquals("Target 1200 ppm", masterRowDetail(AdminResource.TARGET, target))
        assertFalse(masterRowOn(AdminResource.SEASON, JSONObject().put("status", "closed").put("active", true)))
        assertTrue(masterRowOn(AdminResource.SEASON, JSONObject().put("status", "open")))
        assertEquals("Berjalan" to "Ditutup", masterFilterLabels(AdminResource.SEASON))
        assertEquals("gram, mL, L dan 1 lagi", shortNames(listOf("gram", "mL", "L", "kg")))
        assertEquals("Belum ada", shortNames(emptyList()))
    }
}
