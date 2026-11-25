package com.inofarm.jogja.feature.admin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ConsumerPriceFormTest {
    private fun draft(price: String, version: Int? = null, active: Boolean = true) =
        ConsumerPriceDraft(if (version == null) null else "10000000-0000-4000-8000-000000000001", "Golden Melon", "saleable", price, version, active)

    @Test fun priceDraftRejectsNegative() {
        assertThrows(IllegalArgumentException::class.java) { draft("-1").wirePrice() }
        assertThrows(IllegalArgumentException::class.java) { draft("").wirePrice() }
        assertThrows(IllegalArgumentException::class.java) { draft("0").wirePrice() }
        assertThrows(IllegalArgumentException::class.java) { draft("1,234").wirePrice() }
        assertEquals("34000.00", draft("34000").wirePrice())
        assertEquals("34000.50", draft("34000,5").wirePrice())
    }

    @Test fun varietyNamesDedupedAcrossGreenhouses() {
        val rows = listOf(
            JSONObject().put("name", "Golden Melon").put("greenhouse_id", "G1").put("active", true),
            JSONObject().put("name", "  golden   melon ").put("greenhouse_id", "G2").put("active", true),
            JSONObject().put("name", "Inthanon").put("greenhouse_id", "G2").put("active", true),
            JSONObject().put("name", "Lama").put("greenhouse_id", "G2").put("active", false))
        assertEquals(listOf("Golden Melon", "Inthanon"), ConsumerPrices.varietyNames(rows))
    }

    @Test fun updateAlwaysSendsRecordStateAndBaseVersion() {
        val update = ConsumerPrices.operation(draft("34000", version = 3), "20000000-0000-4000-8000-000000000001")
        assertEquals("update", update.getString("action")); assertEquals(3, update.getInt("base_version"))
        assertEquals("active", update.getJSONObject("payload").getString("record_state"))
        val create = ConsumerPrices.operation(draft("34000"), "20000000-0000-4000-8000-000000000001")
        assertEquals("create", create.getString("action")); assertTrue(create.isNull("base_version"))
        assertFalse(create.getJSONObject("payload").has("record_state"))
        assertEquals("inactive", ConsumerPrices.operation(draft("34000", 3, active = false), "x").getJSONObject("payload").getString("record_state"))
    }

    @Test fun findIgnoresCaseAndSpaces() {
        val prices = listOf(ConsumerPrice("a", "Golden Melon", "saleable", "34000.000000", 1, null))
        assertEquals("a", ConsumerPrices.find(prices, " GOLDEN  melon", "saleable")?.id)
        assertNull(ConsumerPrices.find(prices, "Golden Melon", "defective"))
        assertEquals("Rp34.000/kg", ConsumerPrices.display(prices[0]))
    }

    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
}
