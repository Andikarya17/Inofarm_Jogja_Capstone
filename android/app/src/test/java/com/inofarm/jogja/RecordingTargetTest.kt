package com.inofarm.jogja

import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.context.RecordingTarget
import com.inofarm.jogja.feature.context.caption
import com.inofarm.jogja.feature.context.recordingTarget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Recording asks only for a greenhouse; its open season and planting follow (user decision 17 Nov 2026). */
class RecordingTargetTest {
    private val today = LocalDate.parse("2026-11-17")
    private val gh = JSONObject().put("id", "gh").put("name", "GH Utara").put("active", true)
    private fun season(id: String, status: String = "open") =
        JSONObject().put("id", id).put("greenhouse_id", "gh").put("status", status).put("name", "Musim $id")
    private fun group(id: String, variety: String, sowing: String = "2026-11-01", season: String = "s1") =
        JSONObject().put("id", id).put("season_id", season).put("variety_id", variety).put("name", id).put("sowing_date", sowing).put("active", true)
    private val varieties = listOf(JSONObject().put("id", "v1").put("name", "Inthanon"), JSONObject().put("id", "v2").put("name", "Sweetnet"))

    @Test fun twoVarietiesAreReadyUnderOneSeasonWithoutChoosingAGroup() {
        val data = CultivationUiState(greenhouses = listOf(gh), seasons = listOf(season("s1")), varieties = varieties,
            groups = listOf(group("b", "v2"), group("a", "v1")))
        val target = data.recordingTarget("gh", today) as RecordingTarget.Ready
        assertEquals("a", target.group.getString("id"))
        assertEquals(listOf("Inthanon", "Sweetnet"), target.varieties)
        assertEquals("Musim s1 · Inthanon dan Sweetnet", target.caption(manager = false))
        gh.put("running_group_id", "b")
        assertEquals("b", (data.recordingTarget("gh", today) as RecordingTarget.Ready).group.getString("id"))
        gh.remove("running_group_id")
    }

    @Test fun noOpenSeasonOrNoPlantingAsksToStartPlanting() {
        val closed = CultivationUiState(greenhouses = listOf(gh), seasons = listOf(season("s1", "closed")), groups = listOf(group("a", "v1")))
        assertTrue(closed.recordingTarget("gh", today) is RecordingTarget.NotPlanted)
        val empty = CultivationUiState(greenhouses = listOf(gh), seasons = listOf(season("s1")))
        val target = empty.recordingTarget("gh", today) as RecordingTarget.NotPlanted
        assertEquals("s1", target.season?.getString("id"))
        assertEquals("Belum mulai tanam. Mulai tanam dulu untuk mencatat.", target.caption(manager = true))
        assertTrue(target.caption(manager = false)!!.contains("Minta pengelola"))
    }

    @Test fun plantingNotYetSownWaitsForItsSowingDate() {
        val data = CultivationUiState(greenhouses = listOf(gh), seasons = listOf(season("s1")), groups = listOf(group("a", "v1", "2026-11-20")))
        val target = data.recordingTarget("gh", today) as RecordingTarget.NotSown
        assertEquals(LocalDate.parse("2026-11-20"), target.sowing)
    }

    @Test fun inactiveOrUnknownGreenhouseHasNoTarget() {
        val inactive = JSONObject(gh.toString()).put("active", false)
        assertNull(CultivationUiState(greenhouses = listOf(inactive)).recordingTarget("gh", today))
        assertNull(CultivationUiState(greenhouses = listOf(gh)).recordingTarget("lain", today))
    }
}
