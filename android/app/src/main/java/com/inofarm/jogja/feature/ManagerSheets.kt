package com.inofarm.jogja.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.core.design.*

private typealias Admin = com.inofarm.jogja.feature.admin.AdminResource

/** What the manager can record from the "Catat" button. */
internal enum class RecordChoice(val label: String, val detail: String, val icon: FarmIcon) {
    ACTIVITY("Kegiatan", "Ukur, nutrisi, semprot", FarmIcon.Schedule),
    MEASUREMENT("Pengukuran", "EC dan PPM", FarmIcon.Droplet),
    HARVEST("Panen", "Layak dan cacat per varietas", FarmIcon.Basket),
    EXPENSE("Biaya", "Uang keluar", FarmIcon.Wallet),
    SALE("Penjualan", "Sudah lunas", FarmIcon.TrendUp),
}

/**
 * The greenhouse choice inside the Catat sheet, for both roles: recording only asks for a greenhouse (17 Nov 2026).
 * [onPick] gets the chosen greenhouse id. [onStartPlanting] is offered when [notPlanted] says the greenhouse has no planting.
 */
internal class WorkerRecordTarget(
    val greenhouses: List<org.json.JSONObject>, val chosen: String?, val caption: (String?) -> String?,
    val onPick: (RecordChoice, String?) -> Unit, val onNutritionRun: (() -> Unit)? = null,
    val notPlanted: (String?) -> Boolean = { false }, val onStartPlanting: ((String) -> Unit)? = null,
    val workerDetails: Boolean = true,
)

private val WorkerRecordDetails = mapOf(
    RecordChoice.ACTIVITY to "Ukur, nutrisi, semprot, stadia",
    RecordChoice.HARVEST to "Layak dan cacat per varietas",
)

/** One "Kegiatan" form holds readings too (17 Nov 2026), so Pengukuran is no longer a separate tile. */
internal val WorkerRecordChoices = listOf(RecordChoice.ACTIVITY, RecordChoice.HARVEST)
internal val ManagerRecordChoices = RecordChoice.entries.filter { it != RecordChoice.MEASUREMENT }

/** Catat pengeluaran follows the eye toggle on the Keuangan tab (spec section 7): hidden while real costs are hidden. */
internal fun managerRecordChoices(showRealCosts: Boolean) = ManagerRecordChoices.filter { showRealCosts || it != RecordChoice.EXPENSE }
private val FieldChoices = setOf(RecordChoice.ACTIVITY, RecordChoice.MEASUREMENT, RecordChoice.HARVEST)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordSheet(
    onPick: (RecordChoice) -> Unit,
    onDismiss: () -> Unit,
    choices: List<RecordChoice> = RecordChoice.entries,
    contextLine: String? = null,
    worker: WorkerRecordTarget? = null,
) {
    var chosen by remember { mutableStateOf(worker?.chosen) }
    var askStart by remember { mutableStateOf<String?>(null) }
    askStart?.let { ghId ->
        StartSowingDialog(onNo = { askStart = null }, onContinue = { askStart = null; worker?.onStartPlanting?.invoke(ghId) })
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Large)) {
            FarmSheetHeading("Catat", onDismiss)
            contextLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
                Spacer(Modifier.height(FarmSpace.Tiny))
                val field = choices.filter { it in FieldChoices }
                val money = choices.filterNot { it in FieldChoices }
                if (worker != null && worker.greenhouses.isNotEmpty()) {
                    FarmChoiceChips("Greenhouse", chosen, worker.greenhouses, "record-greenhouse", scroll = true) { chosen = it }
                    worker.caption(chosen)?.let {
                        Text(it, Modifier.testTag("record-greenhouse-caption"), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val start = worker.onStartPlanting
                    val ghId = chosen
                    if (start != null && ghId != null && worker.notPlanted(ghId)) StartPlantingCard { start(ghId) }
                }
                val pick: (RecordChoice) -> Unit = if (worker != null) { choice ->
                    // Inofarm 20 Nov 2026: a manager recording on a greenhouse not yet sown is asked to start sowing first.
                    val ghId = chosen
                    if (worker.onStartPlanting != null && ghId != null && choice in FieldChoices && worker.notPlanted(ghId)) askStart = ghId
                    else worker.onPick(choice, chosen)
                } else onPick
                if (field.isNotEmpty()) RecordGroup(if (worker != null) null else "Di greenhouse", field, 3, 0, pick,
                    if (worker?.workerDetails == true) WorkerRecordDetails else emptyMap())
                worker?.onNutritionRun?.let { NutritionRunRow(it) }
                if (money.isNotEmpty()) RecordGroup("Uang", money, 2, field.size, pick)
                if (money.isNotEmpty() && worker == null) Text(
                    "Greenhouse lain? Pilih dulu di Beranda, atau biaya dan penjualan akan menanyakan greenhouse-nya.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Sage row that starts the greenhouse-by-greenhouse nutrition check. */
@Composable
private fun NutritionRunRow(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().farmPressable(interaction).clip(FarmShape.Card).background(FarmColors.Sage)
        .clickable(interaction, androidx.compose.material3.ripple(color = FarmColors.Ink), role = Role.Button, onClick = onClick).testTag("record-nutrition-run").padding(FarmSpace.Medium),
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
        FarmIconTile(FarmIcon.Droplet, tone = FarmTone.Forest)
        Column(Modifier.weight(1f)) {
            Text("Cek nutrisi semua greenhouse", style = MaterialTheme.typography.titleMedium, color = FarmColors.Ink, fontWeight = FontWeight.Bold)
            Text("Isi berurutan, satu catatan per greenhouse", style = MaterialTheme.typography.bodySmall, color = FarmColors.Ink)
        }
        CompositionLocalProvider(LocalContentColor provides FarmColors.Ink) { FarmControlIcon(FarmIcon.ChevronRight, null) }
    }
}

@Composable
private fun StartSowingDialog(onNo: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        modifier = Modifier.testTag("start-sowing-dialog"),
        title = { Text("Greenhouse belum dimulai semai") },
        text = { Text("Apakah mau mulai semai?") },
        dismissButton = {
            TextButton(onClick = onNo, modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("start-sowing-no")) { Text("Tidak") }
        },
        confirmButton = {
            Button(onClick = onContinue, shape = FarmShape.Control,
                modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("start-sowing-continue")) { Text("Lanjut") }
        },
    )
}

@Composable
private fun StartPlantingCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().farmPressable(interaction).clip(FarmShape.Card)
        .background(MaterialTheme.colorScheme.primaryContainer)
        .clickable(interaction, androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick).testTag("record-start-planting")
        .padding(FarmSpace.Medium), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
        verticalAlignment = Alignment.CenterVertically) {
        FarmIconTile(FarmIcon.Add, tone = FarmTone.Forest)
        Column(Modifier.weight(1f)) {
            Text("Mulai semai", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Greenhouse ini belum dimulai semai", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) { FarmControlIcon(FarmIcon.ChevronRight, null) }
    }
}

@Composable
private fun RecordGroup(title: String?, items: List<RecordChoice>, columns: Int, offset: Int, onPick: (RecordChoice) -> Unit,
    details: Map<RecordChoice, String> = emptyMap()) {
    val perRow = if (LocalDensity.current.fontScale > 1.3f) 1 else columns
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        title?.let { FarmSectionHeading(it) }
        items.chunked(perRow).forEachIndexed { row, rowItems ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                rowItems.forEachIndexed { col, choice ->
                    RecordTile(choice, offset + row * perRow + col, Modifier.weight(1f).fillMaxHeight(), onPick, details[choice] ?: choice.detail)
                }
                repeat(perRow - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RecordTile(choice: RecordChoice, index: Int, modifier: Modifier, onPick: (RecordChoice) -> Unit, detail: String) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.farmStaggerEntrance(index, "record-tile-${choice.name}", 30)
            .farmPressable(interaction, 0.96f).clip(FarmShape.Card)
            .background(MaterialTheme.colorScheme.surface, FarmShape.Card)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FarmShape.Card)
            .clickable(interaction, androidx.compose.material3.ripple(), role = Role.Button) { onPick(choice) }
            .testTag("record-${choice.name.lowercase()}").padding(FarmSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)
    ) {
        FarmIconTile(choice.icon, tone = FarmTone.Neutral)
        Text(choice.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Filter chips for the Riwayat tab. Activity, measurement and harvest stay here; money records open Keuangan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordTypeChips(selected: String, manager: Boolean = true, mine: Boolean = false, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        listOf("activity" to "Semua kegiatan", "measurement" to "Pengukuran", "harvest" to "Panen").forEach { (id, label) ->
            FilterChip(selected == id, { onSelect(id) }, label = { Text(label) }, modifier = Modifier.testTag("history-chip-$id"))
        }
        if (!manager) FilterChip(mine, { onSelect("mine") }, label = { Text("Catatan saya") }, modifier = Modifier.testTag("history-chip-mine"))
        // Money records live in Keuangan, so these open that tab instead of filtering this list.
        if (manager) listOf("expense" to "Biaya", "sale" to "Penjualan", "asset" to "Aset").forEach { (id, label) ->
            AssistChip({ onSelect(id) }, label = { Text(label) }, trailingIcon = { FarmControlIcon(FarmIcon.ChevronRight, null) },
                modifier = Modifier.testTag("history-chip-$id"))
        }
    }
}
