package com.inofarm.jogja.feature.assets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.DepreciationAmounts
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.feature.Choice
import com.inofarm.jogja.feature.Notice
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.FinanceField
import com.inofarm.jogja.feature.expenses.moneyLabel
import org.json.JSONObject
import java.util.UUID

data class AssetWorkspace(val entry:AssetEntryState,val vm:AssetEntryViewModel,val history:com.inofarm.jogja.feature.history.HistoryUiState,val historyVm:com.inofarm.jogja.feature.history.HistoryViewModel,val depreciation:com.inofarm.jogja.feature.history.HistoryUiState,val depreciationVm:com.inofarm.jogja.feature.history.HistoryViewModel)

@Composable fun AssetForm(state:AssetEntryState,context:CultivationUiState,vm:AssetEntryViewModel) {
    val draft=state.draft ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(draft.kind==AssetKind.ASSET) "Catat aset" else "Alokasikan penyusutan",style=MaterialTheme.typography.headlineMedium)
        Text(if(state.saved) "Isian terakhir tersimpan lokal." else "Perubahan belum selesai disimpan.",modifier=Modifier.testTag("asset-draft-status"))
        if(draft.kind==AssetKind.ASSET) {
            val sources=context.expenses.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("payment_kind")=="asset_acquisition" && it.optString("record_state")=="active"}.map {JSONObject(it.toString()).put("name","${moneyLabel(it.optString("amount"))} · ${it.optString("notes")} · ${it.optString("id").take(8)}")}
            if(draft.lockedParents) Text("Pembayaran sumber: ${sources.find {it.optString("id")==draft.expenseId}?.optString("name") ?: "terakhir tersimpan"}")
            else Choice("Pembayaran pembelian aset",draft.expenseId,sources,"asset-expense") {value -> vm.edit {it.copy(expenseId=value)}}
            FinanceField("Nama aset",draft.name,{value -> vm.edit {it.copy(name=value)}},"asset-name",state.errors["name"])
            FinanceField("Harga perolehan (IDR)",draft.cost,{value -> vm.edit {it.copy(cost=value)}},"asset-cost",state.errors["cost"],KeyboardType.Decimal)
            FinanceField("Nilai sisa (IDR)",draft.residual,{value -> vm.edit {it.copy(residual=value)}},"asset-residual",state.errors["cost"],KeyboardType.Decimal)
            FinanceField("Umur pemakaian (tahun)",draft.lifeYears,{value -> vm.edit {it.copy(lifeYears=value)}},"asset-life",state.errors["depreciation"],KeyboardType.Number)
            FinanceField("Mulai dipakai (YYYY-MM-DD)",draft.inServiceDate,{value -> vm.edit {it.copy(inServiceDate=value)}},"asset-start",state.errors["depreciation"])
            runCatching {DepreciationAmounts.year(AssetDraft.money(draft.cost).toPlainString(),AssetDraft.money(draft.residual).toPlainString(),AssetDraft.year(draft.lifeYears),1,AssetDraft.date(draft.inServiceDate))}.getOrNull()?.let {
                Text("Pratinjau anggaran tahun pertama: ${moneyLabel(it.amount.toPlainString())} (${it.start} hingga ${it.end}, akhir tidak termasuk).")
            }
        } else {
            val sources=context.assets.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("record_state")=="active"}
            if(draft.lockedParents) Text("Aset: ${sources.find {it.optString("id")==draft.assetId}?.optString("name") ?: "terakhir tersimpan"} · musim ${context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "terakhir tersimpan"}")
            else {
                Choice("Aset sumber",draft.assetId,sources,"asset-source") {value -> vm.edit {it.copy(assetId=value)}}
                Choice("Musim tujuan",draft.seasonId,context.seasons.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("status")=="open"},"asset-season") {value -> vm.edit {it.copy(seasonId=value)}}
            }
            if(draft.lockedParents) Text("Tahun ${draft.usageYear} · ${draft.periodStart} hingga ${draft.periodEnd} (akhir eksklusif)")
            else {
                FinanceField("Tahun pemakaian ke",draft.usageYear,{value -> vm.edit {it.copy(usageYear=value)}},"asset-usage-year",state.errors["usage_year"],KeyboardType.Number)
                FinanceField("Awal periode (YYYY-MM-DD)",draft.periodStart,{value -> vm.edit {it.copy(periodStart=value)}},"asset-period-start",state.errors["period"])
                FinanceField("Akhir periode eksklusif (YYYY-MM-DD)",draft.periodEnd,{value -> vm.edit {it.copy(periodEnd=value)}},"asset-period-end",state.errors["period"])
            }
            if(draft.usageReference.isBlank()) OutlinedButton(onClick={vm.edit {it.copy(usageReference=UUID.randomUUID().toString())}},modifier=Modifier.testTag("asset-new-usage")) {Text("Buat referensi pemakaian baru")}
            else Text("Referensi pemakaian: ${draft.usageReference.take(8)}. Pastikan pemakaian ini belum dicatat sebelumnya.")
            FinanceField("Nominal alokasi (IDR)",draft.amount,{value -> vm.edit {it.copy(amount=value)}},"asset-amount",state.errors["amount"],KeyboardType.Decimal)
            runCatching {val source=sources.single {it.getString("id")==draft.assetId};DepreciationAmounts.year(source.getString("cost"),source.getString("residual"),source.getInt("life_years"),AssetDraft.year(draft.usageYear),AssetDraft.date(source.getString("in_service_date")))}.getOrNull()?.let {
                Text("Anggaran tahun ini: ${moneyLabel(it.amount.toPlainString())}. Sisa setelah alokasi lain diperiksa server.")
            }
        }
        FinanceField("Dasar pemakaian / pembagian",draft.basis,{value -> vm.edit {it.copy(basis=value)}},"asset-basis",state.errors["basis"],singleLine=false)
        if(draft.baseVersion!=null) FinanceField("Alasan koreksi",draft.reason,{value -> vm.edit {it.copy(reason=value)}},"asset-reason",state.errors["reason"],singleLine=false)
        Notice("Harga pembelian aset terpisah dari biaya operasi dan alokasi penyusutan. Draf/antrean belum diterima server. Anggaran dan sumber diperiksa ulang saat sinkronisasi.")
        state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
        Button(onClick=vm::review,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("asset-review")) {Text("Periksa catatan aset")}
        OutlinedButton(onClick=vm::saveAndClose,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("asset-save-draft")) {Text("Simpan draf dan kembali")}
    }
}

@Composable fun AssetReview(draft:AssetDraft,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        com.inofarm.jogja.core.design.FarmPageTitle("Periksa catatan aset");AssetDraftFacts(draft)
        Notice("Alokasi ini nominal manual, bukan pembagian otomatis. Server memeriksa sisa pembayaran, anggaran tahunan, A−S dan duplikasi pemakaian.")
        if(draft.replaceValidationRejected) Notice("Perbaikan membuat operasi baru; pengajuan lama yang ditolak tetap disimpan.")
        Button(onClick=onQueue,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("asset-queue")) {Text("Simpan lokal dan antrekan")}
        OutlinedButton(onClick=onBack,enabled=!busy) {Text("Kembali ke isian")}
    }
}

@Composable private fun AssetDraftFacts(draft:AssetDraft) {
    if(draft.kind==AssetKind.ASSET) {
        Text("Aset: ${draft.name}");Text("Harga ${draft.cost} IDR · nilai sisa ${draft.residual} IDR")
        Text("Umur ${draft.lifeYears} tahun · mulai ${draft.inServiceDate}");Text("Pembayaran: ${draft.expenseId.take(8)}")
    } else {
        Text("Aset ${draft.assetId.take(8)} · musim ${draft.seasonId.take(8)} · pemakaian ${draft.usageReference.take(8)}")
        Text("Tahun ${draft.usageYear} · ${draft.periodStart} hingga ${draft.periodEnd} (akhir tidak termasuk)")
        Text("Nominal alokasi ${draft.amount} IDR")
    }
    Text("Dasar: ${draft.basis}");if(draft.reason.isNotBlank()) Text("Alasan: ${draft.reason}")
}

@Composable fun AssetFacts(record:LocalRecord) {
    val kind=AssetKind.entries.single {it.wire==record.kind}
    AssetDraftFacts(AssetDraft.from(record.id,record.greenhouseId,kind,record.body,record.baseVersion ?: record.serverVersion))
    record.body.optString("formula_version").takeIf {it.isNotBlank()}?.let {Text("Metode: penyusutan garis lurus, anggaran ulang tahun pemakaian; pembulatan kumulatif.")}
    if(record.body.has("year_budget")) Text("Anggaran tahun saat diterima: ${moneyLabel(record.body.getString("year_budget"))}")
    if(record.body.optString("record_state")=="cancelled") Notice("Dibatalkan; tidak dihitung sebagai alokasi aktif.",true)
}
