package com.inofarm.jogja.feature.harvest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.cultivation.PlantAge
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.harvest.domain.HarvestDraft
import com.inofarm.jogja.feature.harvest.domain.HarvestLine

@Composable fun HarvestForm(state:HarvestUiState,context:CultivationUiState,vm:HarvestViewModel) {
    val draft=state.draft ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Catat panen",style=MaterialTheme.typography.headlineMedium)
        Text(if(state.saved) "Isian terakhir tersimpan di perangkat." else "Perubahan menunggu penyimpanan lokal.",style=MaterialTheme.typography.bodySmall)
        Text(context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Kelompok tanam")
        Notice("Jumlah buah dan bobot aktual per grade. Panen belum menjadi penjualan atau penerimaan uang.")
        FinanceField("Tanggal panen WIB",draft.date,{value -> vm.edit {it.copy(date=value)}},"harvest-date",state.errors["occurred_at"],hint="YYYY-MM-DD")
        FinanceField("Jam panen WIB",draft.time,{value -> vm.edit {it.copy(time=value)}},"harvest-time",state.errors["occurred_at"],hint="HH:mm; detik opsional")
        draft.items.forEachIndexed {index,line -> OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Rincian grade ${index+1}",fontWeight=FontWeight.SemiBold)
            if(line.id in draft.lockedItems) {
                Text("Grade: ${line.gradeName.ifBlank {context.grades.find {it.optString("id")==line.gradeId}?.optString("name") ?: "belum tersedia"}}")
                Text("Rincian sudah tersimpan atau diantrekan. Untuk grade berbeda, hapus rincian dan tambahkan rincian baru; saldo tetap diperiksa server.",style=MaterialTheme.typography.bodySmall)
            } else Choice("Grade",line.gradeId,context.grades.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optBoolean("active",true)},"harvest-grade-$index") {value -> vm.edit {it.copy(items=it.items.mapIndexed {i,row -> if(i==index) row.copy(gradeId=value,gradeName=context.grades.find {grade -> grade.optString("id")==value}?.optString("name").orEmpty()) else row})}}
            state.errors["items.$index.grade"]?.let {Notice(it,true)}
            FinanceField("Jumlah buah",line.fruitCount,{value -> vm.edit {it.copy(items=it.items.mapIndexed {i,row -> if(i==index) row.copy(fruitCount=value) else row})}},"harvest-count-$index",state.errors["items.$index.count"],KeyboardType.Number,"buah; kosong bukan nol")
            FinanceField("Bobot panen",line.weightKg,{value -> vm.edit {it.copy(items=it.items.mapIndexed {i,row -> if(i==index) row.copy(weightKg=value) else row})}},"harvest-weight-$index",state.errors["items.$index.weight"],KeyboardType.Decimal,"kg; contoh 15,25, tanpa pemisah ribuan")
            TextButton(onClick={vm.edit {it.copy(items=it.items.filterIndexed {i,_ -> i!=index})}},modifier=Modifier.testTag("harvest-remove-$index")) {Text("Hapus rincian ${index+1}")}
        }}}
        OutlinedButton(onClick={vm.edit {it.copy(items=it.items+HarvestLine())}},enabled=draft.items.size<30,modifier=Modifier.fillMaxWidth().testTag("harvest-add-item")) {Text("Tambah grade panen")}
        FinanceField("Keterangan panen",draft.notes,{value -> vm.edit {it.copy(notes=value)}},"harvest-notes",state.errors["notes"],singleLine=false)
        if(draft.baseVersion!=null) {
            FinanceField("Alasan koreksi panen",draft.reason,{value -> vm.edit {it.copy(reason=value)}},"harvest-reason",state.errors["reason"],singleLine=false)
            Choice("Acuan koreksi",draft.referencePolicy,listOf(org.json.JSONObject().put("id","preserve").put("name","Pertahankan tanggal/grade historis"),org.json.JSONObject().put("id","refresh").put("name","Nilai ulang dengan acuan terkini")),"harvest-reference-policy") {value -> vm.edit {it.copy(referencePolicy=value)}}
        }
        val group=context.groups.find {it.optString("id")==draft.groupId}
        if(draft.baseVersion==null || draft.referencePolicy=="refresh") {
            val age=runCatching {if(group==null) null else PlantAge.calculate(group.getString("sowing_date"),if(group.isNull("transplant_date")) null else group.getString("transplant_date"),draft.occurredAt())}.getOrNull()
            if(age!=null) {Text("HSS ${age.hss ?: "belum tersedia"} · HST ${age.hst ?: "belum berlaku"}");age.reasons.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}}
        } else Notice("Acuan historis dipertahankan. Perubahan waktu panen memerlukan pilihan nilai ulang.")
        state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
        Button(onClick=vm::review,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("harvest-review")) {Text("Periksa panen")}
        OutlinedButton(onClick=vm::saveAndClose,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("harvest-save-draft")) {Text("Simpan draf dan kembali")}
    }
}

@Composable fun HarvestReview(draft:HarvestDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Periksa panen",style=MaterialTheme.typography.headlineMedium)
        Text("${draft.date} ${draft.time} WIB");Text(context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Kelompok tanam")
        draft.items.forEach {line -> Text("${line.gradeName.ifBlank {context.grades.find {it.optString("id")==line.gradeId}?.optString("name") ?: "Grade belum tersedia"}}: ${line.fruitCount} buah · ${line.weightKg} kg")}
        if(draft.notes.isNotBlank()) Text(draft.notes);if(draft.reason.isNotBlank()) Text("Alasan koreksi: ${draft.reason}")
        if(draft.replaceValidationRejected) Notice("Panen yang ditolak akan diganti setelah tinjauan ini. Pengajuan lama tetap disimpan sebagai riwayat.")
        Notice("Panen disimpan lokal dan diantrekan. Pembaruan atau pembatalan ditolak bila melampaui saldo sumber yang sudah dipakai penjualan.")
        Button(onClick=onQueue,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("harvest-queue")) {Text(if(busy) "Menyimpan…" else "Simpan lokal & antrekan")}
        OutlinedButton(onClick=onBack,enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Kembali ke isian")}
    }
}

@Composable fun HarvestFacts(record:LocalRecord,session:FinanceSession,context:CultivationUiState) {
    val draft=runCatching {HarvestDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion)}.getOrNull()
    if(record.body.optString("record_state")=="cancelled") Text("Dibatalkan",color=MaterialTheme.colorScheme.error)
    if(draft!=null) {
        Text(context.groups.find {it.optString("id")==draft.groupId}?.optString("name") ?: "Kelompok tanam")
        Text("Panen · ${draft.date} ${draft.time} WIB")
        draft.items.forEach {line ->
            Text("${line.gradeName.ifBlank {context.grades.find {it.optString("id")==line.gradeId}?.optString("name") ?: "Grade belum tersedia"}}: ${line.fruitCount.ifBlank {"belum diisi"}} buah · ${line.weightKg.ifBlank {"belum diisi"}} kg")
            val balance=context.harvestBalances.find {it.optString("id")==line.id && it.optString("harvest_id")==record.id}
            Text(if(balance!=null && record.serverVersion!=null && balance.optLong("harvest_version")==record.serverVersion) "Saldo terakhir diterima: ${balance.optString("available_weight_kg")} kg tersedia · ${balance.optString("sold_weight_kg")} kg terpakai · versi saldo ${balance.optLong("version")}" else "Saldo bobot tersedia belum dapat dibandingkan dengan versi panen ini.",style=MaterialTheme.typography.bodySmall)
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
