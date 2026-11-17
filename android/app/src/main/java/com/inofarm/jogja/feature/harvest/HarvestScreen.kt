package com.inofarm.jogja.feature.harvest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.FarmDraftStatus
import com.inofarm.jogja.core.design.FarmChoiceChips
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.ageFigure
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.activities.domain.CorrectionReference
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.context.WorkerEventTime
import com.inofarm.jogja.feature.harvest.domain.HarvestDraft
import com.inofarm.jogja.feature.harvest.domain.HarvestLine
import com.inofarm.jogja.feature.harvest.domain.HarvestQuality
import com.inofarm.jogja.feature.managerhome.ManagerDraftParents
import org.json.JSONObject
import java.math.BigDecimal

@Composable internal fun HarvestForm(state:HarvestUiState,context:CultivationUiState,vm:HarvestViewModel,worker:Boolean) {
    val draft=state.draft ?: return
    val group = context.groups.find { it.optString("id") == draft.groupId }
    if (worker && draft.baseVersion != null) {
        val policy = CorrectionReference.policy(draft.date, draft.time, state.records.find { it.id == draft.id }?.serverBody?.optString("occurred_at"))
        LaunchedEffect(policy, draft.referencePolicy) { if (draft.referencePolicy != policy) vm.edit { it.copy(referencePolicy = policy) } }
    }
    val title = if (draft.baseVersion == null) "Catat panen" else "Koreksi panen"
    run {
        val age = runCatching {
            if (group == null) null else PlantAge.calculate(group.getString("sowing_date"),
                if (group.isNull("transplant_date")) null else group.getString("transplant_date"), draft.occurredAt())
        }.getOrNull()
        val figure = ageFigure(age?.hst?.toString()?.toIntOrNull(), age?.hss?.toString()?.toIntOrNull())
        // Workers keep their draft; a manager form has only Batal and Simpan, and Simpan opens the summary.
        val cancel = if (worker) null else rememberEntryCancel(state.busy, vm::hasChanges, vm::cancel)
        FarmFormFrame(
            header = { if (cancel == null) FarmFormHeader(title, state.saved, "harvest-draft-status", "harvest-close", vm::saveAndClose, !state.busy)
                else FarmFormHeader(title, state.saved, "harvest-draft-status", "harvest-close", cancel, !state.busy, showDraftStatus = false, closeLabel = "Batal", step = 1) },
            bar = { if (cancel == null) FarmFormBar("harvest-save-draft", vm::saveAndClose, "harvest-review", "Periksa", vm::review, enabled = !state.busy, busy = state.busy,
                    saveLabel = "Simpan sebagai draf")
                else FarmFormBar("harvest-cancel", cancel, "harvest-review", "Simpan", vm::review, enabled = !state.busy, busy = state.busy, saveLabel = "Batal") }
        ) {
            FarmContextStrip(context.greenhouses.find { it.optString("id") == draft.greenhouseId }?.optString("name") ?: "Greenhouse terakhir tersimpan",
                group?.optString("name"), figure?.let { (value, unit) -> "$unit $value" })
            HarvestFormBody(state, context, vm, draft, group, worker = worker)
        }
    }
}

@Composable private fun HarvestFormGroup(canopy:Boolean,title:String,content:@Composable ColumnScope.()->Unit) {
    if (canopy) FarmFormSection(title, content) else FarmSection(title = title, content = content)
}

/** Fields shared by the worker and manager layouts. Only the manager layout (canopy) changes shape; the worker body is as before. */
@Composable private fun ColumnScope.HarvestFormBody(state:HarvestUiState,context:CultivationUiState,vm:HarvestViewModel,draft:HarvestDraft,group:JSONObject?,worker:Boolean) {
    val canopy = true
    val grades=context.grades.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optBoolean("active",true)}
    if (worker && draft.baseVersion != null) {
        // The correction reason comes first so nobody scrolls past it.
        HarvestFormGroup(canopy, "Alasan koreksi") {
            FinanceField(vm.reasonLabel,draft.reason,{value -> vm.edit {it.copy(reason=value)}},"harvest-reason",state.errors["reason"],singleLine=false)
            if (draft.referencePolicy == "preserve") FarmInfoCard { Text("Waktu panen tidak berubah, acuan lama dipakai otomatis.") }
        }
    }
    HarvestFormGroup(canopy, "Waktu panen") {
        if (worker) {
            WorkerEventTime(context, draft.greenhouseId, draft.groupId, draft.date, draft.time,
                { value -> vm.edit { it.copy(date = value) } }, { value -> vm.edit { it.copy(time = value) } },
                "harvest", state.errors["occurred_at"], draft.baseVersion != null && draft.referencePolicy == "preserve",
                state.records.find { it.id == draft.id }?.serverBody?.optJSONObject("reference_snapshot")?.optJSONObject("age"))
        } else {
            FarmWhenCard(draft.date, draft.time, "harvest-time-edit", forceOpen = state.errors["occurred_at"] != null) {
                FinanceDateField("Tanggal panen WIB",draft.date,{value -> vm.edit {it.copy(date=value)}},"harvest-date",state.errors["occurred_at"],hint="YYYY-MM-DD")
                FinanceTimeField("Jam panen WIB",draft.time,{value -> vm.edit {it.copy(time=value)}},"harvest-time",state.errors["occurred_at"],hint="HH:mm; detik opsional")
            }
        }
        if (draft.companion == null) FarmFact("Varietas", plantingLabel(context, draft.groupId) ?: "Belum tersedia")
        group?.optJSONObject("harvest_target")?.fruitText()?.let { FarmFact("Target buah", it) }
    }
    if(grades.isEmpty() && !context.loading) Notice("Grade aktif belum tersedia di perangkat untuk greenhouse ini. Perbarui atau minta pengelola menambahkan grade.")
    // One editor for each variety: the second variety of a greenhouse edits its own lines through the companion (17 Nov 2026).
    val second = draft.companion
    if (second == null) HarvestLines(state, context, draft, grades, vm::edit, "", "harvest-")
    else {
        FarmFormSection(plantingLabel(context, draft.groupId) ?: "Varietas pertama") {
            Text("Kosongkan bila varietas ini tidak dipanen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HarvestLines(state, context, draft, grades, vm::edit, "", "harvest-")
        FarmFormSection(plantingLabel(context, second.groupId) ?: "Varietas kedua") {
            Text("Dicatat sebagai panen tersendiri untuk varietas ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HarvestLines(state, context, second, grades, { change -> vm.edit { it.copy(companion = change(it.companion ?: second)) } }, "companion.", "harvest-v2-")
    }
    if (canopy) HarvestTotalCard(draft)
    val notes: @Composable () -> Unit = {
        FinanceField("Keterangan panen",draft.notes,{value -> vm.edit {it.copy(notes=value)}},"harvest-notes",state.errors["notes"],singleLine=false)
    }
    if (canopy) FarmFormSection("Keterangan") { notes() } else notes()
    if(draft.baseVersion!=null) {
        if (!worker) FinanceField(vm.reasonLabel,draft.reason,{value -> vm.edit {it.copy(reason=value)}},"harvest-reason",state.errors["reason"],singleLine=false)
        if(!worker) Choice("Acuan koreksi",draft.referencePolicy,listOf(org.json.JSONObject().put("id","preserve").put("name","Pertahankan tanggal/grade historis"),org.json.JSONObject().put("id","refresh").put("name","Nilai ulang dengan acuan terkini")),"harvest-reference-policy") {value -> vm.edit {it.copy(referencePolicy=value)}}
    }
    if(!worker && (draft.baseVersion==null || draft.referencePolicy=="refresh")) {
        val age=runCatching {if(group==null) null else PlantAge.calculate(group.getString("sowing_date"),if(group.isNull("transplant_date")) null else group.getString("transplant_date"),draft.occurredAt())}.getOrNull()
        if(age!=null) FarmInfoCard {Text("HSS ${age.hss ?: "belum tersedia"} · HST ${age.hst ?: "belum berlaku"}");age.reasons.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}}
    } else if (!worker && draft.baseVersion != null && draft.referencePolicy == "preserve") {
        Notice("Perubahan waktu panen memerlukan pilihan nilai ulang.")
    }
    state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
}

/** Grade lines of one variety. [edit] changes that variety's draft; errors and tags are prefixed per variety. */
@Composable private fun ColumnScope.HarvestLines(state:HarvestUiState,context:CultivationUiState,part:HarvestDraft,grades:List<JSONObject>,
    edit:((HarvestDraft)->HarvestDraft)->Unit,errorPrefix:String,tagPrefix:String) {
    val canopy = true
    part.items.forEachIndexed {index,line -> key(line.id) {
        val lineContent: @Composable ColumnScope.() -> Unit = {
            if (canopy) Text("Rincian grade ${index+1}", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            if(line.id in part.lockedItems) {
                Text("Grade: ${line.gradeName.ifBlank {context.grades.find {it.optString("id")==line.gradeId}?.optString("name") ?: "belum tersedia"}}")
                Text("Untuk mengganti grade, hapus rincian ini dan tambahkan rincian baru.",style=MaterialTheme.typography.bodySmall)
            } else Choice("Grade",line.gradeId,grades,"${tagPrefix}grade-$index",loading=context.loading) {value -> edit {it.copy(items=it.items.mapIndexed {i,row -> if(i==index) row.copy(gradeId=value,gradeName=grades.find {grade -> grade.optString("id")==value}?.optString("name").orEmpty()) else row})}}
            state.errors["${errorPrefix}items.$index.grade"]?.let {Notice(it,true)}
            val qualityOptions = HarvestQuality.entries.map { JSONObject().put("id", it.wire).put("name", it.label) }
            val pickQuality = { value: String ->
                edit { it.copy(items = it.items.map { row ->
                    if (row.id == line.id) row.copy(quality = HarvestQuality.entries.single { it.wire == value }) else row
                }) }
            }
            if (canopy) FarmChoiceChips("Kondisi buah", line.quality?.wire, qualityOptions, "${tagPrefix}quality-$index",
                warningId = HarvestQuality.DEFECTIVE.wire, onSelect = pickQuality)
            else Choice("Kondisi buah", line.quality?.wire, qualityOptions, "${tagPrefix}quality-$index") { value -> pickQuality(value) }
            state.errors["${errorPrefix}items.$index.quality"]?.let { Notice(it, true) }
            val countField: @Composable () -> Unit = {
                FinanceField("Jumlah (buah)",line.fruitCount,{value -> edit {it.copy(items=it.items.mapIndexed {i,row -> if(i==index) row.copy(fruitCount=value) else row})}},"${tagPrefix}count-$index",state.errors["${errorPrefix}items.$index.count"],KeyboardType.Number,"Boleh kosong; bobot yang utama",
                    suffix = if (canopy) "buah" else null, prominent = canopy)
            }
            val weightField: @Composable () -> Unit = {
                FinanceField("Bobot panen (kg)",line.weightKg,{value -> edit {it.copy(items=it.items.mapIndexed {i,row -> if(i==index) row.copy(weightKg=value) else row})}},"${tagPrefix}weight-$index",state.errors["${errorPrefix}items.$index.weight"],KeyboardType.Decimal,"Contoh 15,25 tanpa pemisah ribuan",
                    suffix = if (canopy) "kg" else null, prominent = canopy)
            }
            if (canopy) FarmFieldPair(countField, weightField) else { countField(); weightField() }
        }
        val remove = { edit {it.copy(items=it.items.filterIndexed {i,_ -> i!=index})} }
        if (canopy) FarmDetailCard("Hapus rincian ${index+1}", remove, "${tagPrefix}remove-$index", lineContent)
        else FarmPanel(title = "Rincian grade ${index+1}") {
            lineContent()
            TextButton(onClick=remove, modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("${tagPrefix}remove-$index")) {Text("Hapus rincian ${index+1}")}
        }
    }}
    OutlinedButton(onClick={edit {it.copy(items=it.items+HarvestLine())}},enabled=part.items.size<30,
        shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("${tagPrefix}add-item")) {Text("Tambah grade panen")}
}

/** Preview only: the sums of what is typed so far. Blank or invalid lines count as zero until the review validates them. */
@Composable private fun HarvestTotalCard(draft:HarvestDraft) {
    val lines = draft.items + draft.companion?.items.orEmpty()
    val counts = lines.mapNotNull { HarvestDraft.count(it.fruitCount)?.toLong() }
    val weight = lines.fold(BigDecimal.ZERO) { total, line -> total + (runCatching { HarvestDraft.number(line.weightKg) }.getOrNull() ?: BigDecimal.ZERO) }
    FarmInfoCard {
        Text(if (draft.companion != null) "Total isian semua varietas" else "Total isian", style = MaterialTheme.typography.labelLarge)
        // Counts are optional, so only the counted fruit is summed; the weight is the measure Inofarm records.
        Text(listOfNotNull("${weight.stripTrailingZeros().toPlainString().replace('.', ',')} kg", counts.takeIf { it.isNotEmpty() }?.let { "${it.sum()} buah" })
            .joinToString(" · "), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

@Composable internal fun HarvestReview(draft:HarvestDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit,worker:Boolean) {
    if (!worker) {
        val greenhouse = context.greenhouses.find { it.optString("id") == draft.greenhouseId }?.optString("name") ?: "Greenhouse terakhir tersimpan"
        FarmReviewFrame("panen", "$greenhouse · ${whenLabel(draft.date, draft.time)}", FarmIcon.Basket, busy, "harvest-queue", "harvest-back", onBack, onQueue, summary = true) {
            FarmFormSection("Waktu panen") { HarvestReviewTime(draft, context, readable = true) }
            HarvestReviewParts(draft, context)
            if(draft.replaceValidationRejected) Notice("Panen yang ditolak diganti dengan catatan ini. Catatan lama tetap ada di riwayat.")
        }
        return
    }
    val greenhouse = context.greenhouses.find { it.optString("id") == draft.greenhouseId }?.optString("name") ?: "Greenhouse terakhir tersimpan"
    FarmReviewFrame("panen", "$greenhouse · ${whenLabel(draft.date, draft.time)}", FarmIcon.Basket, busy, "harvest-queue", "harvest-back", onBack, onQueue,
        mainLabel = "Kirim", hint = "Pastikan isinya benar. Setelah dikirim, catatan terkirim otomatis saat ada internet.") {
        FarmFormSection("Waktu panen") { HarvestReviewTime(draft, context, readable = true) }
        HarvestReviewParts(draft, context)
        if(draft.replaceValidationRejected) Notice("Panen yang ditolak diganti dengan catatan ini. Catatan lama tetap ada di riwayat.")
        if (draft.baseVersion != null) Notice("Hasil panen yang sudah dijual perlu dicek pengelola sebelum diubah.")
        FarmInfoCard { Text("Muncul di Riwayat dengan label Belum terkirim sampai diterima, lalu menunggu cek pengelola.") }
    }
}

@Composable private fun ColumnScope.HarvestReviewTime(draft:HarvestDraft,context:CultivationUiState,readable:Boolean=false) {
    if (draft.runningPeriodVersion != null && draft.companion == null) ManagerDraftParents(context, draft.greenhouseId, draft.groupId)
    FarmFact("Waktu panen", if (readable) whenLabel(draft.date, draft.time) else "${draft.date} ${draft.time} WIB")
    // Varieties, not planting groups (K2); with two varieties each has its own section below.
    if (draft.companion == null) FarmFact("Varietas", plantingLabel(context, draft.groupId) ?: "Belum tersedia")
}

/** One results section per variety that has lines; a single-variety harvest keeps its "Hasil per grade" section. */
@Composable private fun HarvestReviewParts(draft:HarvestDraft,context:CultivationUiState) {
    if (draft.companion == null) { FarmFormSection("Hasil per grade") { HarvestReviewResults(draft, context) }; return }
    draft.parts().forEach { part ->
        FarmFormSection(plantingLabel(context, part.groupId) ?: "Varietas") { HarvestReviewResults(part, context) }
    }
}

@Composable private fun ColumnScope.HarvestReviewResults(draft:HarvestDraft,context:CultivationUiState) {
    draft.items.forEachIndexed { index, line ->
        if (index > 0) HorizontalDivider()
        FarmFact(gradeName(line, context), listOfNotNull(line.fruitCount.takeIf { it.isNotBlank() }?.let { "$it buah" }, "${line.weightKg} kg",
            line.quality?.label ?: "Kondisi belum diketahui").joinToString(" · "), prominent = true)
    }
    val counts = draft.items.mapNotNull { HarvestDraft.count(it.fruitCount)?.toLong() }
    val weight = draft.items.fold(BigDecimal.ZERO) { total, line -> total + HarvestDraft.number(line.weightKg) }
    FarmFact("Total panen", listOfNotNull("${harvestDecimal(weight.toPlainString())} kg", counts.takeIf { it.isNotEmpty() }?.let { "${it.sum()} buah" }).joinToString(" · "))
    if(draft.notes.isNotBlank()) FarmFact("Keterangan", draft.notes)
    if(draft.reason.isNotBlank()) FarmFact("Alasan koreksi", draft.reason)
}

@Composable fun HarvestFacts(record:LocalRecord,session:FinanceSession,context:CultivationUiState) {
    val draft=runCatching {HarvestDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion)}.getOrNull()
    if(record.body.optString("record_state")=="cancelled") Text("Dibatalkan",color=MaterialTheme.colorScheme.error)
    if(draft!=null) {
        Text(context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Kelompok tanam")
        val source = record.body.optJSONObject("reference_snapshot")
        FarmFact("Varietas saat dicatat", source?.optJSONObject("variety")?.optString("name") ?: "Belum terekam pada revisi ini")
        source?.optJSONObject("harvest_target")?.let {
            FarmFact("Target saat dicatat", (it.fruitText() ?: it.harvestAmount().ifBlank { "Tanpa jumlah buah" }) + if (session.role == UserRole.MANAGER) " · versi ${it.optInt("version")}" else "")
        }
        Text("Panen · ${draft.date} ${draft.time} WIB")
        draft.items.forEach {line ->
            HorizontalDivider()
            val count=line.fruitCount.takeIf { it.isNotBlank() }?.let { "$it buah" } ?: "Jumlah belum diisi"
            val weight=line.weightKg.takeIf { it.isNotBlank() }?.let { "${harvestDecimal(it)} kg" } ?: "Bobot belum diisi"
            FarmFact(gradeName(line, context), "$count · $weight · ${line.quality?.label ?: "Kondisi belum diketahui"}", prominent = true)
            if (session.role == UserRole.MANAGER) {
                val balance=context.harvestBalances.find {it.optString("id")==line.id && it.optString("harvest_id")==record.id}
                Text(if(balance!=null && record.serverVersion!=null && balance.optLong("harvest_version")==record.serverVersion) "Saldo terakhir diterima: ${balance.optString("available_weight_kg")} kg tersedia · ${balance.optString("sold_weight_kg")} kg terpakai · versi saldo ${balance.optLong("version")}" else "Saldo bobot tersedia belum dapat dibandingkan dengan versi panen ini.",style=MaterialTheme.typography.bodySmall)
            }
        }
        if(draft.notes.isNotBlank()) Text(draft.notes)
    } else Notice("Isian panen belum dapat dibaca lengkap. Catatan tetap tersimpan.",true)
    val name=record.body.optString("recorded_by_name").ifBlank {record.serverBody?.optString("recorded_by_name").orEmpty()}.ifBlank {if(record.serverVersion==null || record.recorderId()==session.scope.accountId) session.name else "Nama pencatat belum tersedia"}
    Text("Pencatat: $name");if(session.role==UserRole.MANAGER) Text("Versi: ${record.serverVersion ?: "belum diterima"}")
    record.body.optJSONObject("reference_snapshot")?.optJSONObject("age")?.let {age ->
        Text("HSS ${if(age.isNull("hss")) "belum tersedia" else age.optString("hss")} · HST ${if(age.isNull("hst")) "belum berlaku" else age.optString("hst")}")
        age.optJSONArray("reasons")?.let {reasons -> repeat(reasons.length()) {Text(reasons.getString(it),style=MaterialTheme.typography.bodySmall)}}
    }
}

private fun gradeName(line:HarvestLine,context:CultivationUiState) = line.gradeName.ifBlank {
    context.grades.find { it.optString("id") == line.gradeId }?.optString("name") ?: "Grade belum tersedia"
}

/** "12 buah", or null when the count was not recorded: harvests may be weighed only (17 Nov 2026); empty is not zero. */
internal fun JSONObject.fruitText(key: String = "fruit_count"): String? =
    if (!has(key) || isNull(key)) null else optString(key).toLongOrNull()?.let { "$it buah" }

/** "12 buah · 3,5 kg", or just the weight when no count was recorded. */
internal fun JSONObject.harvestAmount(): String =
    listOfNotNull(fruitText(), optString("weight_kg").takeIf { it.isNotBlank() && it != "null" }?.let { "${harvestDecimal(it)} kg" }).joinToString(" · ")

/** The variety a planting stands for; plantings are named after their variety since 17 Nov 2026, so that name is the fallback. */
internal fun plantingLabel(context: CultivationUiState, groupId: String): String? =
    context.groups.find { it.optString("id") == groupId }?.let { group -> varietyName(context, group) ?: group.optString("name").ifBlank { null } }

internal fun varietyName(context: CultivationUiState, group: JSONObject?): String? =
    context.varieties.firstOrNull { it.optString("id") == group?.optString("variety_id") }?.optString("name")

internal fun harvestDecimal(value: String): String =
    value.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()?.replace('.', ',') ?: value
