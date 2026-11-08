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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceNavigation(tab: Int, manager: Boolean, transmitBackdrop: Boolean, onSelect: (Int) -> Unit) {
    val labels = listOf("Beranda", "Antrean", "Kegiatan", "Panen", "Keuangan").take(if (manager) 5 else 4)
    var expanded by rememberSaveable { mutableStateOf(false) }
    FarmGlass(
        Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
        transmitBackdrop = transmitBackdrop
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Tiny)) {
            val compact = maxWidth / LocalDensity.current.fontScale < 54.dp * labels.size
            if (compact) {
                TextButton(
                    onClick = { expanded = true }, shape = FarmShape.Control,
                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("finance-navigation-menu")
                ) {
                    FarmNavigationIcon(tab)
                    Spacer(Modifier.width(FarmSpace.Small))
                    Text("Menu · ${labels[tab]}", style = MaterialTheme.typography.labelLarge)
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
                            labels.forEachIndexed { index, label ->
                                FarmTab(index, label, tab == index, {
                                    onSelect(index)
                                    expanded = false
                                }, Modifier.fillMaxWidth().testTag("finance-tab-$index"), inline = true)
                                if (index < labels.lastIndex) FarmRowDivider(FarmSpace.Medium * 2 + 24.dp)
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    labels.forEachIndexed { index, label ->
                        FarmTab(index, label, tab == index, { onSelect(index) },
                            Modifier.weight(1f).testTag("finance-tab-$index"))
                    }
                }
            }
        }
    }
}
