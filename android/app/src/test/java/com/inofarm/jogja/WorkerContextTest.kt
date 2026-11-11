package com.inofarm.jogja

import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.context.HomeContext
import com.inofarm.jogja.feature.context.workerDefault
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WorkerContextTest {
    @Test fun runningPeriodWinsAndFallbackRequiresAUniqueEligibleGroup() {
        val today = LocalDate.parse("2026-11-11")
        val greenhouse = JSONObject().put("id", "gh").put("running_group_id", "second")
        fun season(id: String, status: String) =
            JSONObject().put("id", id).put("greenhouse_id", "gh").put("status", status)
        fun group(id: String, season: String, sowing: String = "2026-11-01") =
            JSONObject().put("id", id).put("season_id", season).put("sowing_date", sowing)
        val first = group("first", "open")
        val second = group("second", "open")
        val closed = group("closed", "closed")
        val future = group("future", "open", "2026-11-12")
        val contexts = CultivationUiState(seasons = listOf(season("open", "open"), season("closed", "closed")),
            groups = listOf(first, second, closed, future))

        assertEquals(HomeContext("gh", "open", "second"), contexts.workerDefault(greenhouse, today))
        greenhouse.put("running_group_id", "closed")
        assertEquals(HomeContext("gh", "open"), contexts.workerDefault(greenhouse, today))
        assertEquals(HomeContext("gh", "open", "first"), contexts.copy(groups = listOf(first, closed, future)).workerDefault(greenhouse, today))
        assertEquals(HomeContext("gh", "open"), contexts.copy(groups = listOf(closed, future)).workerDefault(greenhouse, today))
        assertEquals(HomeContext("gh"), contexts.copy(seasons = listOf(season("closed", "closed"))).workerDefault(greenhouse, today))
    }
}
