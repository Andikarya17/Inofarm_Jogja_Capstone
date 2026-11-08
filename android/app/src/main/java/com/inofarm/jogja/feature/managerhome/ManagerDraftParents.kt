package com.inofarm.jogja.feature.managerhome

import androidx.compose.runtime.Composable
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.feature.context.CultivationUiState

@Composable
internal fun ManagerDraftParents(context: CultivationUiState, greenhouse: String, group: String) {
    val greenhouseName = context.greenhouses.firstOrNull { it.text("id") == greenhouse }?.text("name")
    val season = context.groups.firstOrNull { it.text("id") == group }?.text("season_id")
    val periodName = context.seasons.firstOrNull { it.text("id") == season }?.text("name")
    FarmFact("Greenhouse", greenhouseName ?: "Nama greenhouse belum tersimpan")
    FarmFact("Periode tanam", periodName ?: "Nama periode belum tersimpan")
}
