package com.inofarm.jogja.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.*

/** A bottom-bar destination. [icon] picks the drawing in [FarmNavigationIcon]. */
internal data class NavDestination(val label: String, val icon: Int)

/** Where each role's tabs lead. The manager has four always-visible tabs; the worker keeps the original four. */
internal enum class ManagerTab(val label: String, val icon: Int) {
    HOME("Beranda", 0), HISTORY("Riwayat", 1), FINANCE("Keuangan", 4), MANAGE("Kelola", 5)
}

internal val WorkerDestinations = listOf(
    NavDestination("Beranda", 0), NavDestination("Antrean", 1), NavDestination("Kegiatan", 2), NavDestination("Panen", 3)
)
internal val ManagerDestinations = ManagerTab.entries.map { NavDestination(it.label, it.icon) }

/**
 * Screen ids FinanceHome renders: 0 Beranda, 1 Cek catatan, 2 and 3 old activity/harvest lists,
 * 4 Keuangan, 5 Riwayat, 6 Kelola. These map each bottom-bar position to a screen id and back.
 */
internal val ManagerScreenIds = intArrayOf(0, 5, 4, 6)
internal fun managerNavIndex(screen: Int): Int = when (screen) { 0, 1 -> 0; 2, 3, 5 -> 1; 4 -> 2; 6 -> 3; else -> 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceNavigation(
    selected: Int,
    destinations: List<NavDestination>,
    transmitBackdrop: Boolean,
    allowMenu: Boolean,
    onSelect: (Int) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    FarmGlass(
        Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
        transmitBackdrop = transmitBackdrop
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Tiny)) {
            val compact = allowMenu && maxWidth / LocalDensity.current.fontScale < 54.dp * destinations.size
            if (compact) {
                TextButton(
                    onClick = { expanded = true }, shape = FarmShape.Control,
                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("finance-navigation-menu")
                ) {
                    FarmNavigationIcon(destinations[selected].icon)
                    Spacer(Modifier.width(FarmSpace.Small))
                    Text("Menu · ${destinations[selected].label}", style = MaterialTheme.typography.labelLarge)
                }
                if (expanded) ModalBottomSheet(
                    onDismissRequest = { expanded = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentWindowInsets = { WindowInsets.safeDrawing }
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Large)
                    ) {
                        FarmSheetHeading("Pindah halaman", { expanded = false })
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup()) {
                            destinations.forEachIndexed { index, destination ->
                                FarmTab(destination.icon, destination.label, selected == index, {
                                    onSelect(index)
                                    expanded = false
                                }, Modifier.fillMaxWidth().testTag("finance-tab-$index"), inline = true)
                                if (index < destinations.lastIndex) FarmRowDivider(FarmSpace.Medium * 2 + 24.dp)
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    destinations.forEachIndexed { index, destination ->
                        FarmTab(destination.icon, destination.label, selected == index, { onSelect(index) },
                            Modifier.weight(1f).testTag("finance-tab-$index"))
                    }
                }
            }
        }
    }
}
