package com.inofarm.jogja

import com.inofarm.jogja.feature.ManagerDestinations
import com.inofarm.jogja.feature.ManagerScreenIds
import com.inofarm.jogja.feature.RecordChoice
import com.inofarm.jogja.feature.WorkerDestinations
import com.inofarm.jogja.feature.WorkerRecordChoices
import com.inofarm.jogja.feature.WorkerScreenIds
import com.inofarm.jogja.feature.navIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerNavigationTest {
    @Test fun managerHasFourLabelledTabsWithoutAnAntreanTab() {
        assertEquals(listOf("Beranda", "Riwayat", "Keuangan", "Kelola"), ManagerDestinations.map { it.label })
        assertTrue("Antrean" !in ManagerDestinations.map { it.label })
    }

    @Test fun workerHasBerandaAndRiwayatOnly() {
        assertEquals(listOf("Beranda", "Riwayat"), WorkerDestinations.map { it.label })
        assertEquals(WorkerDestinations.size, WorkerScreenIds.size)
    }

    @Test fun everyBarPositionMapsToItsScreenAndBack() {
        ManagerScreenIds.forEachIndexed { index, screen -> assertEquals(index, navIndex(screen)) }
        WorkerScreenIds.forEachIndexed { index, screen -> assertEquals(index, navIndex(screen)) }
    }

    @Test fun screensOpenedFromTheHomeCardsKeepTheirParentTabSelected() {
        assertEquals(0, navIndex(1))
        assertEquals(1, navIndex(2))
        assertEquals(1, navIndex(3))
    }

    @Test fun recordButtonOffersFiveEntriesAndSaleAndExpenseAreAmongThem() {
        assertEquals(listOf("Kegiatan", "Pengukuran", "Panen", "Biaya", "Penjualan"), RecordChoice.entries.map { it.label })
    }

    @Test fun workerRecordButtonOffersNoMoneyEntries() {
        assertEquals(listOf("Kegiatan", "Pengukuran", "Panen"), WorkerRecordChoices.map { it.label })
    }
}
