package com.inofarm.jogja.feature.financehome

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonSequenceTest {
    private fun season(id: String, gh: String, start: String, sequence: Int? = null) = JSONObject()
        .put("id", id).put("greenhouse_id", gh).put("start_date", start).also { if (sequence != null) it.put("sequence", sequence) }

    @Test fun storedSequenceIsUsed() {
        val seasons = listOf(season("a", "gh1", "2026-11-16", 1), season("b", "gh1", "2026-12-20", 2))
        assertEquals(listOf("b"), SeasonSequence.seasonsFor(seasons, 2).map { it.getString("id") })
    }

    // Feed bodies written before migration 23 carry no sequence (28 Nov QA: the season sales list was always empty).
    @Test fun missingSequenceIsDerivedPerGreenhouseLikeTheServerBackfill() {
        val seasons = listOf(season("gh2-late", "gh2", "2027-02-01"), season("gh1-first", "gh1", "2026-11-17"),
            season("gh2-first", "gh2", "2026-11-16"), season("gh3-first", "gh3", "2026-10-01"))
        assertEquals(setOf("gh1-first", "gh2-first", "gh3-first"), SeasonSequence.seasonsFor(seasons, 1).map { it.getString("id") }.toSet())
        assertEquals(listOf("gh2-late"), SeasonSequence.seasonsFor(seasons, 2).map { it.getString("id") })
    }

    @Test fun nullSequenceFallsBackToDerived() {
        val seasons = listOf(season("a", "gh1", "2026-11-16").put("sequence", JSONObject.NULL))
        assertEquals(listOf("a"), SeasonSequence.seasonsFor(seasons, 1).map { it.getString("id") })
    }
}
