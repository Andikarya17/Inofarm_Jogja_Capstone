package com.inofarm.jogja.feature.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleBuyerDraftTest {
    private val gh = "10000000-0000-4000-8000-000000000001"
    private val group = "40000000-0000-4000-8000-000000000001"
    private val grade = "70000000-0000-4000-8000-000000000001"
    private fun draft() = SaleDraft(greenhouseId = gh, seasonId = "20000000-0000-4000-8000-000000000001", date = "2026-11-20", time = "10:15", paid = true,
        buyerType = "consumer", items = listOf(SaleLine("30000000-0000-4000-8000-000000000001", group, grade, "", "3,2", "", "30000")))

    @Test fun picked_buyer_goes_out_as_customer_object() {
        val payload = draft().copy(buyerType = "consumer", buyerId = "50000000-0000-4000-8000-000000000001", customer = "Bu Sari", buyerWhatsapp = "").payload()
        val customer = payload.getJSONObject("customer")
        assertEquals("50000000-0000-4000-8000-000000000001", customer.getString("id"))
        assertEquals("consumer", customer.getString("type"))
        assertEquals("Bu Sari", customer.getString("name"))
        assertTrue(customer.isNull("whatsapp"))
        assertEquals("consumer", payload.getString("customer_type"))
    }

    @Test fun new_buyer_needs_a_name() {
        val missing = draft().copy(buyerType = "consumer", buyerId = "50000000-0000-4000-8000-000000000002", customer = " ")
        assertTrue(missing.validation(false).containsKey("customer"))
        val named = missing.copy(customer = "Pak Joko", buyerWhatsapp = "0812 5555 6666").payload()
        assertEquals("0812 5555 6666", named.getJSONObject("customer").getString("whatsapp"))
    }

    @Test fun sale_without_buyer_sends_explicit_null_customer() {
        // An explicit null lets a correction to "Tidak dicatat" unlink the stored buyer on the server.
        val payload = draft().copy(buyerType = "", baseVersion = 2).payload()
        assertTrue(payload.has("customer") && payload.isNull("customer"))
        assertTrue(payload.has("customer_type") && payload.isNull("customer_type"))
    }

    @Test fun partner_total_sums_grade_lines() {
        val graded = draft().copy(buyerType = "partner", buyerId = "50000000-0000-4000-8000-000000000004", customer = "Mitra 1", items = listOf(
            SaleLine("30000000-0000-4000-8000-000000000011", group, grade, "A", "60", "", "30000"),
            SaleLine("30000000-0000-4000-8000-000000000012", group, grade, "B", "50", "", "24000"),
            SaleLine("30000000-0000-4000-8000-000000000013", group, grade, "C", "25", "", "18000")))
        assertTrue(graded.validation(false).isEmpty())
        assertEquals("3450000.00", graded.preview().total.toPlainString())
        val lines = graded.payload().getJSONArray("lines")
        assertEquals("B", lines.getJSONObject(1).getString("buyer_grade"))
        val duplicate = graded.copy(items = graded.items.map { it.copy(buyerGrade = "A") })
        assertTrue(duplicate.validation(false).containsKey("items"))
        assertTrue(graded.copy(buyerType = "consumer").validation(false).containsKey("items"))
    }

    @Test fun buyer_survives_local_round_trip() {
        val original = draft().copy(buyerType = "partner", buyerId = "50000000-0000-4000-8000-000000000003", customer = "Mitra 1", buyerWhatsapp = "081200001111", buyerExisting = true)
        val restored = SaleDraft.from(original.id, gh, original.localJson(), null)
        assertEquals(original.buyerType, restored.buyerType)
        assertEquals(original.buyerId, restored.buyerId)
        assertEquals(original.buyerWhatsapp, restored.buyerWhatsapp)
        assertTrue(restored.buyerExisting)
    }
}
