package com.inofarm.jogja.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*

/** A bottom-bar destination. [icon] picks the drawing in [FarmNavigationIcon]. */
internal data class NavDestination(val label: String, val icon: Int)

/** Where each role's tabs lead. The manager has four always-visible tabs; the worker has Beranda and Riwayat. */
internal enum class ManagerTab(val label: String, val icon: Int) {
    HOME("Beranda", 0), HISTORY("Riwayat", 1), FINANCE("Keuangan", 4), MANAGE("Kelola", 5)
}

internal val WorkerDestinations = listOf(NavDestination("Beranda", 0), NavDestination("Riwayat", 1))
internal val ManagerDestinations = ManagerTab.entries.map { NavDestination(it.label, it.icon) }

/**
 * Screen ids FinanceHome renders: 0 Beranda, 1 Cek catatan, 2 and 3 old activity/harvest lists,
 * 4 Keuangan, 5 Riwayat, 6 Kelola. These map each bottom-bar position to a screen id and back.
 */
internal val ManagerScreenIds = intArrayOf(0, 5, 4, 6)
internal val WorkerScreenIds = intArrayOf(0, 5)
internal fun navIndex(screen: Int): Int = when (screen) { 0, 1 -> 0; 2, 3, 5 -> 1; 4 -> 2; 6 -> 3; else -> 0 }

@Composable
internal fun FinanceNavigation(
    selected: Int,
    destinations: List<NavDestination>,
    transmitBackdrop: Boolean,
    onSelect: (Int) -> Unit
) {
    FarmGlass(
        Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
        transmitBackdrop = transmitBackdrop
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Tiny).selectableGroup()) {
            destinations.forEachIndexed { index, destination ->
                FarmTab(destination.icon, destination.label, selected == index, { onSelect(index) },
                    Modifier.weight(1f).testTag("finance-tab-$index"))
            }
        }
    }
}
