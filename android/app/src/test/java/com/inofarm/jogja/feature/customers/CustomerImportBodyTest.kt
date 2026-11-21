package com.inofarm.jogja.feature.customers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CustomerImportBodyTest {
    @Test fun every_row_carries_its_stable_client_id() {
        val rows = listOf(ImportRow(2, "Bu Sari", null), ImportRow(3, "Pak Joko", "0812"))
        val first = importBody("consumer", rows, dryRun = false).getJSONArray("rows")
        val retry = importBody("consumer", rows, dryRun = false).getJSONArray("rows")
        assertEquals(first.getJSONObject(0).getString("id"), retry.getJSONObject(0).getString("id"))
        assertNotEquals(first.getJSONObject(0).getString("id"), first.getJSONObject(1).getString("id"))
        assertEquals(rows[1].id, first.getJSONObject(1).getString("id"))
    }
}
