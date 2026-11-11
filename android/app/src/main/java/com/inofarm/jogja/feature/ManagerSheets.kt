package com.inofarm.jogja.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inofarm.jogja.core.design.*

private typealias Admin = com.inofarm.jogja.feature.admin.AdminResource

/** What the manager can record from the "Catat" button. */
internal enum class RecordChoice(val label: String, val detail: String, val icon: FarmIcon) {
    ACTIVITY("Kegiatan", "Pekerjaan harian di greenhouse", FarmIcon.Schedule),
    MEASUREMENT("Pengukuran", "EC dan PPM yang diukur", FarmIcon.Report),
    HARVEST("Panen", "Hasil panen per grade", FarmIcon.Greenhouse),
    EXPENSE("Biaya", "Pembayaran yang keluar", FarmIcon.Payment),
    SALE("Penjualan", "Penjualan yang sudah lunas", FarmIcon.Allocate),
}

internal val WorkerRecordChoices = listOf(RecordChoice.ACTIVITY, RecordChoice.MEASUREMENT, RecordChoice.HARVEST)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordSheet(
    onPick: (RecordChoice) -> Unit,
    onStartPlanting: (() -> Unit)?,
    onDismiss: () -> Unit,
    choices: List<RecordChoice> = RecordChoice.entries,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page).padding(bottom = FarmSpace.Large)) {
            FarmSheetHeading("Catat", onDismiss)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (onStartPlanting != null) {
                    FarmActionRow("Mulai tanam", "Greenhouse ini belum punya tanam saat ini", FarmIcon.Add,
                        Modifier.testTag("record-start-planting"), onClick = onStartPlanting)
                    FarmRowDivider()
                }
                choices.forEachIndexed { index, choice ->
                    FarmActionRow(choice.label, choice.detail, choice.icon,
                        Modifier.testTag("record-${choice.name.lowercase()}")) { onPick(choice) }
                    if (index < choices.lastIndex) FarmRowDivider()
                }
            }
        }
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
internal fun RecordTypeChips(selected: String, manager: Boolean = true, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
        listOf("activity" to "Semua kegiatan", "measurement" to "Pengukuran", "harvest" to "Panen").forEach { (id, label) ->
            FilterChip(selected == id, { onSelect(id) }, label = { Text(label) }, modifier = Modifier.testTag("history-chip-$id"))
        }
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
) {
    FarmMenuList(Modifier.testTag("manage-list")) {
        item("manage-title") { FarmMenuHeader("Kelola", "") }
        item("manage-main") {
            FarmRecordCard {
                FarmActionRow("Tambah greenhouse", "Greenhouse baru, tanam pertama dan pekerja", FarmIcon.Add,
                    Modifier.testTag("manage-add-greenhouse"), enabled, onClick = onAddGreenhouse)
                FarmRowDivider()
                FarmActionRow("Greenhouse", "Tambah dan atur greenhouse", FarmIcon.Greenhouse,
                    Modifier.testTag("manage-greenhouse"), enabled) { onOpen(Admin.GREENHOUSE) }
                FarmRowDivider()
                FarmActionRow("Pekerja dan akun", "Buat akun, ubah peran, reset password", FarmIcon.Account,
                    Modifier.testTag("manage-users"), enabled) { onOpen(Admin.USER) }
            }
        }
        item("manage-master-title") { Text("Data master", Modifier.padding(top = FarmSpace.Small), style = MaterialTheme.typography.titleMedium) }
        item("manage-master") {
            FarmRecordCard {
                listOf(Admin.VARIETY to "Jenis melon", Admin.MATERIAL to "Pupuk dan bahan lain", Admin.UNIT to "Gram, liter, buah",
                    Admin.GRADE to "Kelas hasil panen", Admin.CATEGORY to "Pengelompokan pengeluaran").forEachIndexed { index, (resource, detail) ->
                    TextActionRow(resource.label, detail, Modifier.testTag("manage-${resource.name.lowercase()}"), enabled) { onOpen(resource) }
                    if (index < 4) FarmRowDivider()
                }
            }
        }
        item("manage-plan-title") { Text("Jadwal dan target", Modifier.padding(top = FarmSpace.Small), style = MaterialTheme.typography.titleMedium) }
        item("manage-plan") {
            FarmRecordCard {
                FarmActionRow("Jadwal", "Kegiatan yang direncanakan", FarmIcon.Schedule, Modifier.testTag("manage-schedule"), enabled) { onOpen(Admin.SCHEDULE) }
                FarmRowDivider()
                FarmActionRow("Target", "Acuan EC, PPM dan pH", FarmIcon.Report, Modifier.testTag("manage-target"), enabled) { onOpen(Admin.TARGET) }
            }
        }
        item("manage-account") {
            FarmRecordCard {
                Text("Akun saya", style = MaterialTheme.typography.titleMedium)
                Text("$name · Pengelola", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onLogout, enabled = enabled, shape = FarmShape.Control,
                    modifier = Modifier.heightIn(min = FarmSpace.Touch).testTag("manage-logout")) { Text("Keluar") }
            }
        }
    }
}
