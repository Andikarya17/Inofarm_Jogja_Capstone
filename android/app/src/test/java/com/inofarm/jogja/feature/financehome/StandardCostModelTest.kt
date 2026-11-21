package com.inofarm.jogja.feature.financehome

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class StandardCostModelTest {
    private val inofarm = linkedMapOf("Biaya penyusutan" to "5806100.00", "Sewa tanah" to "275000.00", "Tenaga kerja" to "1500000.00",
        "Cocopeat/rockwool" to "180000.00", "Nutrisi AB Mix" to "1600000.00", "Nutrisi tambahan dan pestisida" to "350000.00", "Benih" to "1300000.00")

    private fun wire(amounts: Map<String, String?>): JSONObject {
        val lines = JSONArray()
        amounts.entries.forEachIndexed { index, (name, amount) ->
            lines.put(JSONObject().put("id", "00000000-0000-4000-8000-00000000000$index").put("name", name)
                .put("cost_class", if (index < 3) "FC" else "VC").put("amount", amount ?: JSONObject.NULL)
                .put("is_required", true).put("sort_order", index))
        }
        return JSONObject().put("id", "10000000-0000-4000-8000-000000000001").put("sequence", 2).put("version", 3)
            .put("lines", lines).put("greenhouse_count", 4)
    }

    @Test fun totals_match_inofarm_table() {
        val sheet = CostSheet.from(wire(inofarm))
        assertEquals(BigDecimal("7581100.00"), sheet.total("FC"))
        assertEquals(BigDecimal("3430000.00"), sheet.total("VC"))
        assertEquals(BigDecimal("11011100.00"), sheet.tc())
        assertEquals(BigDecimal("44044400.00"), sheet.farmTotal())
        assertEquals(4, sheet.greenhouseCount)
    }

    @Test fun locked_sheet_names_closed_greenhouses() {
        val open = CostSheet.from(wire(inofarm))
        assertEquals(false, open.locked)
        val locked = CostSheet.from(wire(inofarm).put("locked", true).put("closed_greenhouse_names", JSONArray(listOf("GH A", "GH B"))))
        assertEquals(true, locked.locked)
        assertEquals(listOf("GH A", "GH B"), locked.closedGreenhouses)
    }

    @Test fun blank_required_amount_gives_null_total() {
        val sheet = CostSheet.from(wire(inofarm + ("Benih" to null)))
        assertNull(sheet.total("VC"))
        assertNull(sheet.tc())
        assertEquals(BigDecimal("7581100.00"), sheet.total("FC"))
    }

    @Test fun required_line_cannot_be_removed() {
        val sheet = CostSheet.from(wire(inofarm))
        assertThrows(IllegalArgumentException::class.java) { sheet.withoutLine(sheet.lines.first().id) }
    }

    @Test fun extra_line_round_trips_to_payload() {
        val sheet = CostSheet.from(wire(inofarm))
            .withLine(CostLine("20000000-0000-4000-8000-000000000001", "Listrik pompa", "VC", BigDecimal("150000"), required = false, sortOrder = 7))
        assertEquals(BigDecimal("3580000.00"), sheet.total("VC"))
        val payload = sheet.payloadLines()
        assertEquals(8, payload.length())
        assertEquals("150000.00", payload.getJSONObject(7).getString("amount"))
        assertEquals(false, payload.getJSONObject(7).getBoolean("is_required"))
        val removed = sheet.withoutLine("20000000-0000-4000-8000-000000000001")
        assertEquals(7, removed.lines.size)
    }

    @Test fun blank_amount_is_sent_as_null() {
        val sheet = CostSheet.from(wire(inofarm)).withAmount("00000000-0000-4000-8000-000000000001", null)
        assertEquals(JSONObject.NULL, sheet.payloadLines().getJSONObject(1).get("amount"))
    }
}
