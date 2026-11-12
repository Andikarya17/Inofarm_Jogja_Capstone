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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.core.design.*

private typealias Admin = com.inofarm.jogja.feature.admin.AdminResource

/** What the manager can record from the "Catat" button. */
internal enum class RecordChoice(val label: String, val detail: String, val icon: FarmIcon) {
    ACTIVITY("Kegiatan", "Pekerjaan harian", FarmIcon.Schedule),
    MEASUREMENT("Pengukuran", "EC dan PPM", FarmIcon.Droplet),
    HARVEST("Panen", "Hasil per grade", FarmIcon.Basket),
    EXPENSE("Biaya", "Uang keluar", FarmIcon.Wallet),
    SALE("Penjualan", "Sudah lunas", FarmIcon.TrendUp),
}

internal val WorkerRecordChoices = listOf(RecordChoice.ACTIVITY, RecordChoice.MEASUREMENT, RecordChoice.HARVEST)
private val FieldChoices = setOf(RecordChoice.ACTIVITY, RecordChoice.MEASUREMENT, RecordChoice.HARVEST)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordSheet(
    onPick: (RecordChoice) -> Unit,
    onStartPlanting: (() -> Unit)?,
    onDismiss: () -> Unit,
    choices: List<RecordChoice> = RecordChoice.entries,
    contextLine: String? = null,
) {
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
                if (onStartPlanting != null) StartPlantingCard(onStartPlanting)
                val field = choices.filter { it in FieldChoices }
                val money = choices.filterNot { it in FieldChoices }
                if (field.isNotEmpty()) RecordGroup("Di greenhouse", field, 3, 0, onPick)
                if (money.isNotEmpty()) RecordGroup("Uang", money, 2, field.size, onPick)
                if (money.isNotEmpty()) Text(
                    "Greenhouse lain? Pilih dulu di Beranda, atau biaya dan penjualan akan menanyakan greenhouse-nya.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StartPlantingCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().farmPressable(interaction).clip(FarmShape.Card)
        .background(MaterialTheme.colorScheme.primaryContainer)
        .clickable(interaction, null, role = Role.Button, onClick = onClick).testTag("record-start-planting")
        .padding(FarmSpace.Medium), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium),
        verticalAlignment = Alignment.CenterVertically) {
        FarmIconTile(FarmIcon.Add, tone = FarmTone.Forest)
        Column(Modifier.weight(1f)) {
            Text("Mulai tanam", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Greenhouse ini belum punya tanam saat ini", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) { FarmControlIcon(FarmIcon.ChevronRight, null) }
    }
}

@Composable
private fun RecordGroup(title: String, items: List<RecordChoice>, columns: Int, offset: Int, onPick: (RecordChoice) -> Unit) {
    val perRow = if (LocalDensity.current.fontScale > 1.3f) 1 else columns
    Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        FarmSectionHeading(title)
        items.chunked(perRow).forEachIndexed { row, rowItems ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                rowItems.forEachIndexed { col, choice ->
                    RecordTile(choice, offset + row * perRow + col, Modifier.weight(1f).fillMaxHeight(), onPick)
                }
                repeat(perRow - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RecordTile(choice: RecordChoice, index: Int, modifier: Modifier, onPick: (RecordChoice) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.farmStaggerEntrance(index, "record-tile-${choice.name}", 30)
            .farmPressable(interaction, 0.96f).clip(FarmShape.Card)
            .background(MaterialTheme.colorScheme.surface, FarmShape.Card)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FarmShape.Card)
            .clickable(interaction, null, role = Role.Button) { onPick(choice) }
            .testTag("record-${choice.name.lowercase()}").padding(FarmSpace.Medium),
        verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)
    ) {
        FarmIconTile(choice.icon, tone = FarmTone.Neutral)
        Text(choice.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(choice.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A list row without an icon, for lists where every row would otherwise get the same drawing. */
@Composable
private fun TextActionRow(label: String, detail: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch + FarmSpace.Medium)
        .clip(FarmShape.Control).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = FarmSpace.Medium, vertical = FarmSpace.Small),
        horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FarmSpace.Tiny)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled) FarmControlIcon(FarmIcon.ChevronRight, null)
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

/** Kelola tab: every setup screen the manager owns, one tap away. */
@Composable
internal fun ManageScreen(
    name: String,
    onOpen: (Admin) -> Unit,
    onLogout: () -> Unit,
    enabled: Boolean,
    onAddGreenhouse: () -> Unit,
    greenhouseCount: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    FarmMenuList(Modifier.testTag("manage-list")) {
        item("manage-title") {
            FarmHeroCard {
                Row(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(FarmColors.Sage, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        Text(initialsOf(name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FarmColors.Ink)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pengelola · semua greenhouse dan akun", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item("manage-main-title") { FarmSectionHeading("Greenhouse dan orang") }
        item("manage-main") {
            FarmRecordCard {
                ManageRow("Tambah greenhouse", "Greenhouse baru, tanam pertama dan pekerja", FarmIcon.GreenhouseAdd, FarmTone.Brand,
                    Modifier.testTag("manage-add-greenhouse"), enabled, onClick = onAddGreenhouse)
                FarmRowDivider(44.dp + FarmSpace.Medium)
                ManageRow("Greenhouse", "Tambah dan atur greenhouse", FarmIcon.Greenhouse, FarmTone.Neutral,
                    Modifier.testTag("manage-greenhouse"), enabled, greenhouseCount?.toString()) { onOpen(Admin.GREENHOUSE) }
                FarmRowDivider(44.dp + FarmSpace.Medium)
                ManageRow("Pekerja dan akun", "Buat akun, ubah peran, reset password", FarmIcon.Account, FarmTone.Neutral,
                    Modifier.testTag("manage-users"), enabled) { onOpen(Admin.USER) }
            }
        }
        item("manage-plan-title") { FarmSectionHeading("Jadwal dan target") }
        item("manage-plan") {
            val stack = LocalDensity.current.fontScale > 1.3f
            val boxes: @Composable (Modifier) -> Unit = { m ->
                ManageBox("Jadwal", "Kegiatan yang direncanakan", FarmIcon.Schedule, Modifier.testTag("manage-schedule").then(m), enabled) { onOpen(Admin.SCHEDULE) }
                ManageBox("Target", "Acuan EC, PPM dan pH", FarmIcon.Report, Modifier.testTag("manage-target").then(m), enabled) { onOpen(Admin.TARGET) }
            }
            if (stack) Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) { boxes(Modifier.fillMaxWidth()) }
            else Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) { boxes(Modifier.weight(1f).fillMaxHeight()) }
        }
        item("manage-master-title") { FarmSectionHeading("Data master") }
        item("manage-master") {
            FarmRecordCard {
                listOf(Admin.VARIETY to "Jenis melon", Admin.MATERIAL to "Pupuk dan bahan lain", Admin.UNIT to "Gram, liter, buah",
                    Admin.GRADE to "Kelas hasil panen", Admin.CATEGORY to "Pengelompokan pengeluaran").forEachIndexed { index, (resource, detail) ->
                    TextActionRow(resource.label, detail, Modifier.testTag("manage-${resource.name.lowercase()}"), enabled) { onOpen(resource) }
                    if (index < 4) FarmRowDivider()
                }
            }
        }
        item("manage-account") {
            Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
                OutlinedButton(onClick = onLogout, enabled = enabled, shape = FarmShape.Control,
                    modifier = Modifier.fillMaxWidth().heightIn(min = FarmSpace.Touch).testTag("manage-logout")) {
                    FarmControlIcon(FarmIcon.Logout, null)
                    Spacer(Modifier.width(FarmSpace.Small))
                    Text("Keluar")
                }
                Text("Draf yang belum terkirim tetap tersimpan di perangkat.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ManageRow(label: String, detail: String, icon: FarmIcon, tone: FarmTone, modifier: Modifier, enabled: Boolean, trailing: String? = null, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 60.dp).clip(FarmShape.Control).farmRowFocus().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = FarmSpace.Small).alpha(if (enabled) 1f else 0.5f), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.CenterVertically) {
        FarmIconTile(icon, tone = tone)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (enabled) FarmControlIcon(FarmIcon.ChevronRight, null)
    }
}

@Composable
private fun ManageBox(label: String, detail: String, icon: FarmIcon, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.clip(FarmShape.Card).background(colors.surface, FarmShape.Card)
        .border(1.dp, colors.outlineVariant, FarmShape.Card).farmRowFocus().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .alpha(if (enabled) 1f else 0.5f).padding(FarmSpace.Medium), verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        FarmIconTile(icon)
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
