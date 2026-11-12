package com.inofarm.jogja.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.*
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.assets.AssetDraft
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.expenses.FinancialDraft
import com.inofarm.jogja.feature.expenses.moneyLabel
import com.inofarm.jogja.feature.label
import com.inofarm.jogja.feature.recorderId
import com.inofarm.jogja.feature.timeLabel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** One scrolling row of type chips: three filters, a divider, then three links that open Keuangan. Tags are history-chip-*. */
@Composable
internal fun CanopyTypeChips(selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FarmSpace.Small),
        verticalAlignment = Alignment.CenterVertically) {
        listOf("activity" to "Kegiatan", "measurement" to "Pengukuran", "harvest" to "Panen").forEach { (id, label) ->
            val on = selected == id
            Surface(Modifier.heightIn(min = FarmSpace.Touch).testTag("history-chip-$id")
                .selectable(on, role = Role.RadioButton) { onSelect(id) },
                shape = FarmShape.Control, color = if (on) colors.primary else colors.surface,
                contentColor = if (on) colors.onPrimary else colors.onSurface,
                border = if (on) null else BorderStroke(1.dp, colors.outline)) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
        Box(Modifier.width(1.dp).height(24.dp).background(colors.outlineVariant))
        val dash = colors.outline
        listOf("expense" to "Biaya", "sale" to "Penjualan", "asset" to "Aset").forEach { (id, label) ->
            Row(Modifier.heightIn(min = FarmSpace.Touch).testTag("history-chip-$id").clip10()
                .clickable(role = Role.Button) { onSelect(id) }
                .drawBehind {
                    drawRoundRect(dash, cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                FarmControlIcon(FarmIcon.ChevronRight, null)
            }
        }
    }
}

private fun Modifier.clip10(): Modifier = this.then(Modifier.clip(RoundedCornerShape(10.dp)))

@Composable
internal fun CanopyDayHeader(label: String) {
    Text(label, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = FarmSpace.Small)
        .semantics { heading() },
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun recordInstant(data: org.json.JSONObject): OffsetDateTime? =
    listOf("occurred_at", "paid_at", "created_at").firstNotNullOfOrNull { key ->
        data.optString(key).takeUnless { data.isNull(key) || it.isBlank() }
    }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

/** Day heading for a record: "Hari ini · Kamis, 12 Nov 2026", "Kemarin · ..." or the date alone; null when the time is unknown. */
internal fun dayHeading(record: LocalRecord, today: LocalDate): String {
    val day = recordInstant(record.body)?.atZoneSameInstant(ActivityDraft.WIB)?.toLocalDate() ?: return "Waktu belum tersedia"
    return when (day) {
        today -> "Hari ini · ${dateLabel(day)}"
        today.minusDays(1) -> "Kemarin · ${dateLabel(day)}"
        else -> dateLabel(day)
    }
}

private fun tileFor(kind: OperationalKind, activityType: String?): Pair<FarmIcon, FarmTone> = when (kind) {
    OperationalKind.ACTIVITY -> if (activityType == ActivityType.MEASUREMENT.wire) FarmIcon.Droplet to FarmTone.Info else FarmIcon.Schedule to FarmTone.Neutral
    OperationalKind.HARVEST -> FarmIcon.Basket to FarmTone.Neutral
    OperationalKind.EXPENSE -> FarmIcon.Wallet to FarmTone.Amber
    OperationalKind.SALE -> FarmIcon.TrendUp to FarmTone.Good
    else -> FarmIcon.Report to FarmTone.Neutral
}

/** A record as one row of a day card. [ownAccount] hides the worker check chip on the manager's own records. */
@Composable
internal fun CanopyRecordRow(
    record: LocalRecord, kind: OperationalKind, context: CultivationUiState, ownAccount: String?, checks: Map<String, String>,
    first: Boolean, last: Boolean, onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val data = record.body
    val local = data.optJSONObject("_local_form")
    val facts = local ?: data
    val type = data.optString("activity_type")
    val title = if (kind == OperationalKind.ACTIVITY) {
        ActivityType.entries.find { it.wire == type }?.label ?: "Draf kegiatan"
    } else if (kind == OperationalKind.ASSET) facts.optString("name").ifBlank { "Aset" }
    else kind.label.replaceFirstChar { it.uppercaseChar() }
    val instant = recordInstant(data)
    val clock = instant?.atZoneSameInstant(ActivityDraft.WIB)?.format(DateTimeFormatter.ofPattern("HH.mm"))
    val greenhouse = data.optString("greenhouse_name").takeUnless { data.isNull("greenhouse_name") || it.isBlank() }
        ?: context.greenhouses.find { it.optString("id") == record.greenhouseId }?.optString("name")
    val groupId = local?.optString("group_id") ?: data.optString("planting_group_id")
    val group = context.groups.find { it.optString("id") == groupId }?.optString("name")
    val recorder = data.optString("recorded_by_name").takeUnless { data.isNull("recorded_by_name") || it.isBlank() }
    val notes = listOf("notes", "allocation_basis").firstNotNullOfOrNull { key ->
        data.optString(key).takeUnless { data.isNull(key) || it.isBlank() }
    }
    val amountKey = when (kind) { OperationalKind.SALE -> "total_amount"; OperationalKind.ASSET -> "cost"; else -> "amount" }
    val rawAmount = facts.optString(amountKey).takeUnless { facts.isNull(amountKey) || it.isBlank() }
    val amountText = if (kind.financial && rawAmount != null) {
        val shown = if (local == null) rawAmount else runCatching {
            when (kind) {
                OperationalKind.ASSET, OperationalKind.DEPRECIATION -> AssetDraft.money(rawAmount)
                else -> FinancialDraft.money(rawAmount)
            }.toPlainString()
        }.getOrNull()
        shown?.let { moneyLabel(it) }
    } else null
    val signed = when (kind) {
        OperationalKind.EXPENSE -> amountText?.let { "−$it" }
        OperationalKind.SALE -> amountText?.let { "+$it" }
        else -> amountText
    }
    val spoken = when (kind) {
        OperationalKind.EXPENSE -> amountText?.let { "Pembayaran $it" }
        OperationalKind.SALE -> amountText?.let { "Penerimaan $it" }
        else -> amountText
    }
    val grades = if (kind == OperationalKind.HARVEST) facts.optJSONArray("items")?.let { arr ->
        List(minOf(arr.length(), 2)) { index ->
            val line = arr.optJSONObject(index)
            val name = line?.optString("grade_name").orEmpty().ifBlank {
                context.grades.find { it.optString("id") == line?.optString("grade_id") }?.optString("name") ?: "Grade belum tersedia"
            }
            val count = line?.optString("fruit_count")?.takeUnless { line.isNull("fruit_count") || it.isBlank() }
            val weight = line?.optString("weight_kg")?.takeUnless { line.isNull("weight_kg") || it.isBlank() }
                ?.let { it.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()?.replace('.', ',') ?: it }
            "$name · ${count?.let { "$it buah" } ?: "Jumlah belum diisi"} · ${weight?.let { "$it kg" } ?: "Bobot belum diisi"}"
        }
    }.orEmpty() else emptyList()
    val cancelled = data.optString("record_state") == "cancelled"
    val skipped = kind == OperationalKind.ACTIVITY && (local?.optBoolean("skipped") ?: (data.optString("work_state") == "skipped"))
    val checkDecision = if (record.syncState == SyncState.ACCEPTED && kind in setOf(OperationalKind.ACTIVITY, OperationalKind.HARVEST) &&
        record.recorderId().isNotBlank() && record.recorderId() != ownAccount)
        record.serverVersion?.let { checks["${record.id}#$it"] ?: "pending" } else null
    val (chipText, chipTone) = when {
        cancelled -> "Dibatalkan" to FarmTone.Neutral
        checkDecision == "valid" -> "Sesuai" to FarmTone.Good
        checkDecision == "invalid" -> "Perlu diperbaiki" to FarmTone.Error
        checkDecision != null -> "Belum dicek" to FarmTone.Info
        record.syncState == SyncState.ACCEPTED -> "Terkirim" to FarmTone.Good
        record.syncState == SyncState.LOCAL -> "Draf" to FarmTone.Amber
        record.syncState == SyncState.QUEUED -> "Belum terkirim" to FarmTone.Amber
        else -> record.syncState.label() to FarmTone.Error
    }
    val meta = listOfNotNull(greenhouse, group, recorder).joinToString(" · ")
    val summary = listOfNotNull(title, spoken ?: grades.firstOrNull() ?: notes, meta.ifBlank { null }, clock, chipText).joinToString(", ")
    val shape = RoundedCornerShape(topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp)
    val (icon, tone) = tileFor(kind, type)
    Column(Modifier.fillMaxWidth().background(colors.surface, shape).clickable(role = Role.Button, onClick = onOpen)
        .testTag("finance-record-${record.id}").semantics(mergeDescendants = true) { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = FarmSpace.Medium, vertical = FarmSpace.Medium),
            horizontalArrangement = Arrangement.spacedBy(FarmSpace.Medium), verticalAlignment = Alignment.Top) {
            FarmIconTile(icon, tone = if (record.syncState == SyncState.LOCAL) FarmTone.Amber else tone, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                signed?.let { FarmAmount(it, MaterialTheme.typography.titleMedium) }
                grades.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (kind == OperationalKind.HARVEST && (facts.optJSONArray("items")?.length() ?: 0) > 2)
                    Text("${(facts.optJSONArray("items")?.length() ?: 0) - 2} grade lainnya di detail", style = MaterialTheme.typography.bodySmall)
                if (grades.isEmpty() && notes != null) Text(notes, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
                if (skipped) Text("Kegiatan dilewati", style = MaterialTheme.typography.labelMedium)
                if (clock == null) Text("Waktu belum tersedia", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                clock?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
                FarmStatusChip(chipText, tone = chipTone)
            }
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 68.dp, end = FarmSpace.Medium), color = colors.outlineVariant)
    }
}
