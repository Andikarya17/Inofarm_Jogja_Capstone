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
import com.inofarm.jogja.core.design.FarmDraftStatus
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmPageTitle
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
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
        FarmPageTitle(if(draft.kind==AssetKind.ASSET) "Catat aset" else "Alokasikan penyusutan")
        FarmDraftStatus(state.saved, Modifier.testTag("asset-draft-status"))
        if(draft.kind==AssetKind.ASSET) {
            FarmSection("Identitas dan nilai aset") {
                FinanceField("Nama aset",draft.name,{value -> vm.edit {it.copy(name=value)}},"asset-name",state.errors["name"])
                FinanceField("Harga perolehan (IDR)",draft.cost,{value -> vm.edit {it.copy(cost=value)}},"asset-cost",state.errors["cost"],KeyboardType.Decimal)
                FinanceField("Nilai sisa (IDR)",draft.residual,{value -> vm.edit {it.copy(residual=value)}},"asset-residual",state.errors["cost"],KeyboardType.Decimal)
            }
            FarmSection("Pembayaran sumber") {
            val sources=context.expenses.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("payment_kind")=="asset_acquisition" && it.optString("record_state")=="active"}.map {JSONObject(it.toString()).put("name","${moneyLabel(it.optString("amount"))} · ${it.optString("notes")} · ${it.optString("id").take(8)}")}
            if(draft.lockedParents) Text("Pembayaran sumber: ${sources.find {it.optString("id")==draft.expenseId}?.optString("name") ?: "terakhir tersimpan"}")
            else Choice("Pembayaran pembelian aset",draft.expenseId,sources,"asset-expense") {value -> vm.edit {it.copy(expenseId=value)}}
            }
            FarmSection("Masa pemakaian") {
            FinanceField("Umur pemakaian (tahun)",draft.lifeYears,{value -> vm.edit {it.copy(lifeYears=value)}},"asset-life",state.errors["depreciation"],KeyboardType.Number)
            FinanceField("Mulai dipakai (YYYY-MM-DD)",draft.inServiceDate,{value -> vm.edit {it.copy(inServiceDate=value)}},"asset-start",state.errors["depreciation"])
            runCatching {DepreciationAmounts.year(AssetDraft.money(draft.cost).toPlainString(),AssetDraft.money(draft.residual).toPlainString(),AssetDraft.year(draft.lifeYears),1,AssetDraft.date(draft.inServiceDate))}.getOrNull()?.let {
                FarmFact("Pratinjau anggaran tahun pertama", moneyLabel(it.amount.toPlainString()))
                Text("${it.start} hingga ${it.end}, akhir tidak termasuk.", style = MaterialTheme.typography.bodySmall)
            }
            }
        } else {
            val sources=context.assets.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("record_state")=="active"}
            FarmSection("Aset dan musim tujuan") {
            if(draft.lockedParents) Text("Aset: ${sources.find {it.optString("id")==draft.assetId}?.optString("name") ?: "terakhir tersimpan"} · musim ${context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "terakhir tersimpan"}")
            else {
                Choice("Aset sumber",draft.assetId,sources,"asset-source") {value -> vm.edit {it.copy(assetId=value)}}
                Choice("Musim tujuan",draft.seasonId,context.seasons.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("status")=="open"},"asset-season") {value -> vm.edit {it.copy(seasonId=value)}}
            }
            }
            FarmSection("Periode pemakaian") {
            if(draft.lockedParents) Text("Tahun ${draft.usageYear} · ${draft.periodStart} hingga ${draft.periodEnd} (akhir eksklusif)")
            else {
                FinanceField("Tahun pemakaian ke",draft.usageYear,{value -> vm.edit {it.copy(usageYear=value)}},"asset-usage-year",state.errors["usage_year"],KeyboardType.Number)
                FinanceField("Awal periode (YYYY-MM-DD)",draft.periodStart,{value -> vm.edit {it.copy(periodStart=value)}},"asset-period-start",state.errors["period"])
                FinanceField("Akhir periode eksklusif (YYYY-MM-DD)",draft.periodEnd,{value -> vm.edit {it.copy(periodEnd=value)}},"asset-period-end",state.errors["period"])
            }
            if(draft.usageReference.isBlank()) OutlinedButton(onClick={vm.edit {it.copy(usageReference=UUID.randomUUID().toString())}},modifier=Modifier.testTag("asset-new-usage")) {Text("Buat referensi pemakaian baru")}
            else Text("Referensi pemakaian: ${draft.usageReference.take(8)}. Pastikan pemakaian ini belum dicatat sebelumnya.")
            }
            FarmPanel(title = "Nominal alokasi") {
            FinanceField("Nominal alokasi (IDR)",draft.amount,{value -> vm.edit {it.copy(amount=value)}},"asset-amount",state.errors["amount"],KeyboardType.Decimal,
                hint = "Isi nominal pemakaian untuk musim tujuan.")
            runCatching {val source=sources.single {it.getString("id")==draft.assetId};DepreciationAmounts.year(source.getString("cost"),source.getString("residual"),source.getInt("life_years"),AssetDraft.year(draft.usageYear),AssetDraft.date(source.getString("in_service_date")))}.getOrNull()?.let {
                FarmFact("Anggaran tahun pemakaian", moneyLabel(it.amount.toPlainString()))
                Text("Server memeriksa sisa anggaran setelah alokasi lain.", style = MaterialTheme.typography.bodySmall)
            }
            }
        }
        FarmSection(title = "Dasar dan catatan") {
        FinanceField("Dasar pemakaian / pembagian",draft.basis,{value -> vm.edit {it.copy(basis=value)}},"asset-basis",state.errors["basis"],singleLine=false)
        if(draft.baseVersion!=null) FinanceField("Alasan koreksi",draft.reason,{value -> vm.edit {it.copy(reason=value)}},"asset-reason",state.errors["reason"],singleLine=false)
        }
        Notice("Harga pembelian aset terpisah dari biaya operasi dan alokasi penyusutan. Draf/antrean belum diterima server. Anggaran dan sumber diperiksa ulang saat sinkronisasi.")
        state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
        Button(onClick=vm::review,enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("asset-review")) {Text("Periksa catatan aset")}
        OutlinedButton(onClick=vm::saveAndClose,enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("asset-save-draft")) {Text("Simpan draf dan kembali")}
    }
}

@Composable fun AssetReview(draft:AssetDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    val acquisition = draft.kind == AssetKind.ASSET
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FarmPageTitle(if (acquisition) "Periksa perolehan aset" else "Periksa alokasi penyusutan")
        FarmPanel { AssetDraftFacts(draft,context) }
        if(draft.replaceValidationRejected) Notice("Perbaikan ini dikirim sebagai catatan baru. Catatan yang ditolak tetap ada di riwayat.")
        Button(onClick=onQueue,enabled=!busy,shape=FarmShape.Control,
            modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("asset-queue")) {Text("Simpan")}
        OutlinedButton(onClick=onBack,enabled=!busy,shape=FarmShape.Control,
            modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch)) {Text("Kembali ke isian")}
    }
}

@Composable private fun AssetDraftFacts(draft:AssetDraft,context:CultivationUiState) {
    fun nominal(value:String) = runCatching { moneyLabel(AssetDraft.money(value).toPlainString()) }
        .getOrDefault(if (value.isBlank()) "Belum diisi" else "Isian belum valid")
    if(draft.kind==AssetKind.ASSET) {
        FarmFact("Aset",draft.name.ifBlank { "Belum diisi" })
        FarmFact("Harga perolehan",nominal(draft.cost),Modifier.testTag("asset-cost-fact"),prominent=true)
        FarmFact("Nilai sisa",nominal(draft.residual))
        FarmFact("Umur pemakaian",draft.lifeYears.takeIf { it.isNotBlank() }?.let { "$it tahun" } ?: "Belum diisi")
        FarmFact("Mulai dipakai",draft.inServiceDate.ifBlank { "Belum diisi" })
        val source=context.expenses.find { it.optString("id")==draft.expenseId && it.optString("greenhouse_id")==draft.greenhouseId }
        val payment=source?.let { listOf(nominal(it.optString("amount")), it.optString("notes")).filter(String::isNotBlank).joinToString(" · ") }
        FarmFact("Pembayaran sumber",payment ?: draft.expenseId.takeIf { it.isNotBlank() } ?: "Belum tersedia")
    } else {
        FarmFact("Nominal alokasi",nominal(draft.amount),prominent=true)
        val asset=context.assets.find { it.optString("id")==draft.assetId && it.optString("greenhouse_id")==draft.greenhouseId }
        val season=context.seasons.find { it.optString("id")==draft.seasonId && it.optString("greenhouse_id")==draft.greenhouseId }
        FarmFact("Aset sumber",asset?.optString("name") ?: draft.assetId.ifBlank { "Belum tersedia" })
        FarmFact("Musim tujuan",season?.optString("name") ?: draft.seasonId.ifBlank { "Belum tersedia" })
        FarmFact("Tahun pemakaian ke",draft.usageYear.ifBlank { "Belum diisi" })
        FarmFact("Periode pemakaian", "${draft.periodStart.ifBlank { "Awal belum diisi" }} hingga ${draft.periodEnd.ifBlank { "akhir belum diisi" }} (akhir tidak termasuk)")
        FarmFact("Referensi pemakaian",draft.usageReference.ifBlank { "Belum tersedia" })
    }
    FarmFact("Dasar pemakaian / pembagian",draft.basis.ifBlank { "Belum diisi" })
    if(draft.reason.isNotBlank()) FarmFact("Alasan koreksi",draft.reason)
}

@Composable fun AssetFacts(record:LocalRecord,context:CultivationUiState) {
    val kind=AssetKind.entries.single {it.wire==record.kind}
    AssetDraftFacts(AssetDraft.from(record.id,record.greenhouseId,kind,record.body,record.baseVersion ?: record.serverVersion),context)
    record.body.optString("formula_version").takeIf {it.isNotBlank()}?.let {Text("Metode: penyusutan garis lurus, anggaran ulang tahun pemakaian; pembulatan kumulatif.")}
    if(record.body.has("year_budget")) Text("Anggaran tahun saat diterima: ${moneyLabel(record.body.getString("year_budget"))}")
    if(record.body.optString("record_state")=="cancelled") Notice("Dibatalkan; tidak dihitung sebagai alokasi aktif.",true)
}
