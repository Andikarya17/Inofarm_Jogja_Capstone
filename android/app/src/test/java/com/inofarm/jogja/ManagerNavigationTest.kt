package com.inofarm.jogja

import com.inofarm.jogja.feature.ManagerDestinations
import com.inofarm.jogja.feature.ManagerScreenIds
import com.inofarm.jogja.feature.RecordChoice
import com.inofarm.jogja.feature.WorkerDestinations
import com.inofarm.jogja.feature.managerNavIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerNavigationTest {
    @Test fun managerHasFourLabelledTabsWithoutAnAntreanTab() {
        assertEquals(listOf("Beranda", "Riwayat", "Keuangan", "Kelola"), ManagerDestinations.map { it.label })
        assertTrue("Antrean" !in ManagerDestinations.map { it.label })
    }

    @Test fun workerNavigationIsUnchanged() {
        assertEquals(listOf("Beranda", "Antrean", "Kegiatan", "Panen"), WorkerDestinations.map { it.label })
    }

    @Test fun everyBarPositionMapsToItsScreenAndBack() {
        ManagerScreenIds.forEachIndexed { index, screen -> assertEquals(index, managerNavIndex(screen)) }
    }

    @Test fun screensOpenedFromTheHomeCardsKeepTheirParentTabSelected() {
        assertEquals(0, managerNavIndex(1))
        assertEquals(1, managerNavIndex(2))
        assertEquals(1, managerNavIndex(3))
    }

    @Test fun recordButtonOffersFiveEntriesAndSaleAndExpenseAreAmongThem() {
        assertEquals(listOf("Kegiatan", "Pengukuran", "Panen", "Biaya", "Penjualan"), RecordChoice.entries.map { it.label })
    }
}
