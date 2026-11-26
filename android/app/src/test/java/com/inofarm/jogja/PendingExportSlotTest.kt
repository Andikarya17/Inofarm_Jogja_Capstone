package com.inofarm.jogja

import com.inofarm.jogja.core.network.ExportFile
import com.inofarm.jogja.core.session.AccountScope
import com.inofarm.jogja.feature.financehome.PendingExportSlot
import org.junit.Assert.*
import org.junit.Test

class PendingExportSlotTest {
    private val scope = AccountScope("http://fixture.invalid/api/v2/", "10000000-0000-4000-8000-000000000001")
    private val file = ExportFile(byteArrayOf(1, 2, 3), "inofarm-keuangan-musim-3-20261125.xlsx")

    @Test fun cancelClearsPending() {
        val slot = PendingExportSlot(); slot.put(file, scope, 7)
        slot.clear()
        assertTrue(slot.isEmpty)
        assertNull(slot.claim { _, _ -> true })
    }

    @Test fun claimWithSameAccountReturnsFileOnceAndEmptiesSlot() {
        val slot = PendingExportSlot(); slot.put(file, scope, 7)
        assertSame(file, slot.claim { s, t -> s == scope && t == 7L })
        assertTrue(slot.isEmpty)
        assertNull(slot.claim { _, _ -> true })
    }

    @Test fun claimAfterTicketOrAccountChangeDiscardsWithoutReturningFile() {
        val slot = PendingExportSlot(); slot.put(file, scope, 7)
        assertNull(slot.claim { _, t -> t == 8L })
        assertTrue(slot.isEmpty)
        slot.put(file, scope, 7)
        assertNull(slot.claim { s, _ -> s == AccountScope("http://fixture.invalid/api/v2/", "10000000-0000-4000-8000-000000000002") })
        assertTrue(slot.isEmpty)
    }
}
