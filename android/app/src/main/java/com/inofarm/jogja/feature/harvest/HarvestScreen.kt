package com.inofarm.jogja.feature.harvest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.FarmDraftStatus
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.*
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
    val grades=context.grades.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optBoolean("active",true)}
    val group = context.groups.find { it.optString("id") == draft.groupId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        com.inofarm.jogja.core.design.FarmPageTitle(if (draft.baseVersion == null) "Catat panen" else "Koreksi panen")
        FarmDraftStatus(state.saved)
        FarmSection(title = "Waktu dan kelompok") {
            if (worker) {
                WorkerEventTime(context, draft.greenhouseId, draft.groupId, draft.date, draft.time,
                    { value -> vm.edit { it.copy(date = value) } }, { value -> vm.edit { it.copy(time = value) } },
                    "harvest", state.errors["occurred_at"], draft.baseVersion != null && draft.referencePolicy == "preserve",
                    state.records.find { it.id == draft.id }?.serverBody?.optJSONObject("reference_snapshot")?.optJSONObject("age"))
            } else {
                if (draft.runningPeriodVersion != null) ManagerDraftParents(context, draft.greenhouseId, draft.groupId)
                FarmFact("Kelompok tanam", group?.optString("name") ?: "Nama belum tersedia")
                FinanceField("Tanggal panen WIB",draft.date,{value -> vm.edit {it.copy(date=value)}},"harvest-date",state.errors["occurred_at"],hint="YYYY-MM-DD")
                FinanceField("Jam panen WIB",draft.time,{value -> vm.edit {it.copy(time=value)}},"harvest-time",state.errors["occurred_at"],hint="HH:mm; detik opsional")
            }
            FarmFact("Varietas", varietyName(context, group) ?: "Belum tersedia")
            group?.optJSONObject("harvest_target")?.let { FarmFact("Target buah", "${it.getInt("fruit_count")} buah") }
        }
        if(grades.isEmpty() && !context.loading) Notice("Grade aktif belum tersedia di perangkat untuk greenhouse ini. Sinkronkan atau minta pengelola menyiapkan grade.")
        draft.items.forEachIndexed {index,line -> key(line.id) { FarmPanel(title = "Rincian grade ${index+1}") {
            if(line.id in draft.lockedItems) {
                Text("Grade: ${line.gradeName.ifBlank {context.grades.find {it.optString("id")==line.gradeId}?.optString("name") ?: "belum tersedia"}}")
                Text("Untuk mengganti grade, hapus rincian ini dan tambahkan rincian baru.",style=MaterialTheme.typography.bodySmall)
            } else Choice("Grade",line.gradeId,grades,"harvest-grade-$index",loading=context.loading) {value -> vm.edit {it.copy(items=it.items.mapIndexed {i,row -> if(i==index) row.copy(gradeId=value,gradeName=grades.find {grade -> grade.optString("id")==value}?.optString("name").orEmpty()) else row})}}
            state.errors["items.$index.grade"]?.let {Notice(it,true)}
            Choice("Kondisi buah", line.quality?.wire, HarvestQuality.entries.map {
                JSONObject().put("id", it.wire).put("name", it.label)
            }, "harvest-quality-$index") { value ->
                vm.edit { it.copy(items = it.items.map { row ->
                    if (row.id == line.id) row.copy(quality = HarvestQuality.entries.single { it.wire == value }) else row
                }) }
            }
            state.errors["items.$index.quality"]?.let { Notice(it, true) }
            FinanceField("Jumlah (buah)",line.fruitCount,{value -> vm.edit {it.copy(items=it.items.mapIndexed {i,row -> if(i==index) row.copy(fruitCount=value) else row})}},"harvest-count-$index",state.errors["items.$index.count"],KeyboardType.Number,"Kosong bukan nol")
            FinanceField("Bobot panen (kg)",line.weightKg,{value -> vm.edit {it.copy(items=it.items.mapIndexed {i,row -> if(i==index) row.copy(weightKg=value) else row})}},"harvest-weight-$index",state.errors["items.$index.weight"],KeyboardType.Decimal,"Contoh 15,25 tanpa pemisah ribuan")
            TextButton(onClick={vm.edit {it.copy(items=it.items.filterIndexed {i,_ -> i!=index})}},
                modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("harvest-remove-$index")) {Text("Hapus rincian ${index+1}")}
        }}}
        OutlinedButton(onClick={vm.edit {it.copy(items=it.items+HarvestLine())}},enabled=draft.items.size<30,
            shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("harvest-add-item")) {Text("Tambah grade panen")}
        FinanceField("Keterangan panen",draft.notes,{value -> vm.edit {it.copy(notes=value)}},"harvest-notes",state.errors["notes"],singleLine=false)
        if(draft.baseVersion!=null) {
            FinanceField("Alasan koreksi panen",draft.reason,{value -> vm.edit {it.copy(reason=value)}},"harvest-reason",state.errors["reason"],singleLine=false)
            Choice("Acuan koreksi",draft.referencePolicy,listOf(org.json.JSONObject().put("id","preserve").put("name","Pertahankan tanggal/grade historis"),org.json.JSONObject().put("id","refresh").put("name","Nilai ulang dengan acuan terkini")),"harvest-reference-policy") {value -> vm.edit {it.copy(referencePolicy=value)}}
        }
        if(!worker && (draft.baseVersion==null || draft.referencePolicy=="refresh")) {
            val age=runCatching {if(group==null) null else PlantAge.calculate(group.getString("sowing_date"),if(group.isNull("transplant_date")) null else group.getString("transplant_date"),draft.occurredAt())}.getOrNull()
            if(age!=null) {Text("HSS ${age.hss ?: "belum tersedia"} · HST ${age.hst ?: "belum berlaku"}");age.reasons.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}}
        } else if (draft.baseVersion != null && draft.referencePolicy == "preserve") {
            Notice("Perubahan waktu panen memerlukan pilihan nilai ulang.")
        }
        state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
        Button(onClick=vm::review,enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("harvest-review")) {Text("Periksa panen")}
        OutlinedButton(onClick=vm::saveAndClose,enabled=!state.busy,shape=FarmShape.Control,
            modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("harvest-save-draft")) {Text("Simpan draf dan kembali")}
    }
}

@Composable internal fun HarvestReview(draft:HarvestDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit,worker:Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        com.inofarm.jogja.core.design.FarmPageTitle("Periksa panen")
        FarmSection(title = "Waktu dan kelompok") {
            if (draft.runningPeriodVersion != null) ManagerDraftParents(context, draft.greenhouseId, draft.groupId)
            FarmFact("Waktu panen", "${draft.date} ${draft.time} WIB")
            FarmFact("Kelompok tanam", context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Nama belum tersedia")
            FarmFact("Varietas", varietyName(context, context.groups.find { it.optString("id") == draft.groupId }) ?: "Belum tersedia")
        }
        FarmPanel(title = "Hasil per grade") {
            draft.items.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider()
                FarmFact(gradeName(line, context), "${line.fruitCount} buah · ${line.weightKg} kg · ${line.quality?.label ?: "Kondisi belum diketahui"}", prominent = true)
            }
            val fruit = draft.items.sumOf { HarvestDraft.count(it.fruitCount)?.toLong() ?: 0L }
            val weight = draft.items.fold(BigDecimal.ZERO) { total, line -> total + HarvestDraft.number(line.weightKg) }
            FarmFact("Total panen", "$fruit buah · ${weight.stripTrailingZeros().toPlainString()} kg")
            if(draft.notes.isNotBlank()) FarmFact("Keterangan", draft.notes)
            if(draft.reason.isNotBlank()) FarmFact("Alasan koreksi", draft.reason)
        }
        if(draft.replaceValidationRejected) Notice("Panen yang ditolak akan diganti setelah tinjauan ini. Pengajuan lama tetap disimpan sebagai riwayat.")
        Notice(if (worker && draft.baseVersion != null)
            "Perubahan hasil panen yang sudah dipakai untuk penjualan perlu ditinjau pengelola."
        else if (worker) "Catatan disimpan di perangkat dan dikirim saat server dapat dihubungi."
        else "Panen disimpan lokal dan diantrekan. Pembaruan atau pembatalan dibatasi oleh bobot yang sudah dipakai penjualan.")
        Button(onClick=onQueue,enabled=!busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("harvest-queue")) {Text(if(busy) "Menyimpan…" else "Simpan lokal & antrekan")}
        OutlinedButton(onClick=onBack,enabled=!busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch)) {Text("Kembali ke isian")}
    }
}

@Composable fun HarvestFacts(record:LocalRecord,session:FinanceSession,context:CultivationUiState) {
    val draft=runCatching {HarvestDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion)}.getOrNull()
    if(record.body.optString("record_state")=="cancelled") Text("Dibatalkan",color=MaterialTheme.colorScheme.error)
    if(draft!=null) {
        Text(context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Kelompok tanam")
        val source = record.body.optJSONObject("reference_snapshot")
        FarmFact("Varietas saat dicatat", source?.optJSONObject("variety")?.optString("name") ?: "Belum terekam pada revisi ini")
        source?.optJSONObject("harvest_target")?.let {
            FarmFact("Target saat dicatat", "${it.getInt("fruit_count")} buah · versi ${it.getInt("version")}")
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
    } else Notice("Isian panen belum dapat dibaca lengkap. Catatan tetap disimpan untuk ditinjau.",true)
    val name=record.body.optString("recorded_by_name").ifBlank {record.serverBody?.optString("recorded_by_name").orEmpty()}.ifBlank {if(record.serverVersion==null || record.recorderId()==session.scope.accountId) session.name else "Nama pencatat belum tersedia"}
    Text("Pencatat: $name");Text("Versi server: ${record.serverVersion ?: "belum diterima"}")
    record.body.optJSONObject("reference_snapshot")?.optJSONObject("age")?.let {age ->
        Text("HSS ${if(age.isNull("hss")) "belum tersedia" else age.optString("hss")} · HST ${if(age.isNull("hst")) "belum berlaku" else age.optString("hst")}")
        age.optJSONArray("reasons")?.let {reasons -> repeat(reasons.length()) {Text(reasons.getString(it),style=MaterialTheme.typography.bodySmall)}}
    }
}

private fun gradeName(line:HarvestLine,context:CultivationUiState) = line.gradeName.ifBlank {
    context.grades.find { it.optString("id") == line.gradeId }?.optString("name") ?: "Grade belum tersedia"
}

internal fun varietyName(context: CultivationUiState, group: JSONObject?): String? =
    context.varieties.firstOrNull { it.optString("id") == group?.optString("variety_id") }?.optString("name")

internal fun harvestDecimal(value: String): String =
    value.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()?.replace('.', ',') ?: value
