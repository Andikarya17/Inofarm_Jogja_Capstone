package com.inofarm.jogja.feature.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.design.FarmCheckbox
import com.inofarm.jogja.core.design.FarmDraftStatus
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmPageTitle
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.expenses.moneyLabel
import org.json.JSONObject

private fun sources(context:CultivationUiState,draft:SaleDraft):List<JSONObject> = context.harvests.filter {harvest -> harvest.optString("record_state")=="active" && context.groups.any {it.optString("id")==harvest.optString("planting_group_id") && it.optString("season_id")==draft.seasonId}}
    .flatMap {harvest -> val rows=harvest.optJSONArray("items");(0 until (rows?.length() ?: 0)).map {index ->
        val item=rows!!.getJSONObject(index);val balance=context.harvestBalances.find {it.optString("id")==item.optString("id") && it.optLong("harvest_version")==harvest.optLong("version")}
        JSONObject().put("id",item.getString("id")).put("name","${item.optString("grade_name")} · ${timeLabel(harvest.optString("occurred_at"))} · ${balance?.optString("available_weight_kg")?.let {"sisa $it kg"} ?: "saldo belum tersedia"}")
    }}

@Composable fun SaleForm(state:SaleEntryState,context:CultivationUiState,vm:SaleEntryViewModel) {
    val draft=state.draft ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        FarmPageTitle(if(draft.baseVersion==null) "Catat penjualan lunas" else "Koreksi penjualan")
        FarmDraftStatus(state.saved, Modifier.testTag("sale-draft-status"))
        FarmSection(title = "Pembayaran dan pembeli") {
        if(draft.lockedSeason) Text("Musim: ${context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "terakhir tersimpan"}")
        else Choice("Musim penjualan",draft.seasonId,context.seasons.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("status")=="open"},"sale-season") {value -> vm.edit {it.copy(seasonId=value,items=listOf(SaleLine()))}}
        FinanceField("Tanggal lunas (YYYY-MM-DD)",draft.date,{value -> vm.edit {it.copy(date=value)}},"sale-date",state.errors["paid_at"])
        FinanceField("Jam lunas WIB (HH:mm)",draft.time,{value -> vm.edit {it.copy(time=value)}},"sale-time",hint="Detik opsional HH:mm:ss.")
        FinanceField("Pembeli (boleh kosong)",draft.customer,{value -> vm.edit {it.copy(customer=value)}},"sale-customer",state.errors["customer"])
        }
        val options=sources(context,draft)
        draft.items.forEachIndexed {index,line -> key(line.id) { FarmSection(title = "Rincian panen ${index+1}") {
            fun edit(updated:SaleLine) {vm.edit {it.copy(items=it.items.toMutableList().apply {set(index,updated)})}}
            if(line.id in draft.lockedSources) Text(options.find {it.optString("id")==line.sourceId}?.optString("name") ?: "Sumber terakhir tersimpan · sinkronkan untuk saldo terbaru")
            else Choice("Sumber panen ${index+1}",line.sourceId,options,"sale-source-$index") {edit(line.copy(sourceId=it))}
            FinanceField("Bobot ${index+1} (kg)",line.quantity,{edit(line.copy(quantity=it))},"sale-quantity-$index",state.errors["items.$index.quantity"],KeyboardType.Decimal,hint="Lebih dari nol, maksimal6 desimal; tanpa pemisah ribuan.")
            FinanceField("Harga ${index+1} (IDR/kg)",line.price,{edit(line.copy(price=it))},"sale-price-$index",state.errors["items.$index.price"],KeyboardType.Decimal,hint="Harga manual, maksimal6 desimal. Kosong bukan nol.")
            TextButton(onClick={vm.edit {it.copy(items=it.items.filterIndexed {i,_ -> i!=index})}},modifier=Modifier.testTag("sale-remove-$index")) {Text("Hapus rincian ${index+1}")}
        }}}
        OutlinedButton(onClick={vm.edit {it.copy(items=it.items+SaleLine())}},enabled=draft.items.size<30,modifier=Modifier.fillMaxWidth().testTag("sale-add")) {Text("Tambah sumber panen")}
        FarmCheckbox("Saya memastikan pembayaran penjualan ini sudah lunas.", draft.paid,
            { value -> vm.edit { it.copy(paid = value) } }, Modifier.testTag("sale-paid"))
        FinanceField("Keterangan penjualan",draft.notes,{value -> vm.edit {it.copy(notes=value)}},"sale-notes",state.errors["notes"],singleLine=false)
        if(draft.baseVersion!=null) FinanceField("Alasan koreksi",draft.reason,{value -> vm.edit {it.copy(reason=value)}},"sale-reason",state.errors["reason"],singleLine=false)
        Notice("Saldo adalah data terakhir tersimpan dan dapat berubah. Penerimaan transaksi dipastikan server; draf/offline belum menjadi penjualan diterima.")
        runCatching {draft.preview()}.getOrNull()?.let {Text("Pratinjau total: ${moneyLabel(it.total.toPlainString())} · ${it.weightKg.toPlainString()} kg")}
        state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
        Button(onClick=vm::review,enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().testTag("sale-review")) {Text("Periksa penjualan")}
        OutlinedButton(onClick=vm::saveAndClose,enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().testTag("sale-save-draft")) {Text("Simpan draf dan kembali")}
    }
}
@Composable fun SaleReview(draft:SaleDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        FarmPageTitle("Periksa penjualan lunas")
        FarmPanel(title = "Rincian transaksi") { SaleDraftFacts(draft,context) }
        Notice("Pratinjau dihitung dari harga/bobot manual, total dibulatkan sekali. Saldo terakhir diketahui dan kelengkapan diperiksa ulang server.")
        if(draft.replaceValidationRejected) Notice("Perbaikan membuat operasi baru; pengajuan yang ditolak tetap disimpan.")
        Button(onClick=onQueue,enabled=!busy,shape=FarmShape.Control,
            modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("sale-queue")) {Text("Simpan lokal & antrekan")}
        OutlinedButton(onClick=onBack,enabled=!busy,shape=FarmShape.Control,
            modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch)) {Text("Kembali ke isian")}
    }
}
@Composable private fun SaleDraftFacts(draft:SaleDraft,context:CultivationUiState,acceptedTotal:String?=null) {
    val preview=runCatching { draft.preview() }.getOrNull()
    if(acceptedTotal!=null) FarmFact("Total diterima server", moneyLabel(acceptedTotal),
        Modifier.testTag("sale-accepted-total").semantics(mergeDescendants=true) {}, prominent=true)
    else if(preview!=null) FarmFact("Pratinjau total penjualan",moneyLabel(preview.total.toPlainString()),
        Modifier.testTag("sale-total-fact").semantics(mergeDescendants=true) {},prominent=true)
    else Notice("Total belum dapat dihitung dari isian ini.",true)
    preview?.let { FarmFact("Bobot dari rincian", "${it.weightKg.toPlainString()} kg") }
    FarmFact("Musim",context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "Nama belum tersedia")
    FarmFact("Pembayaran","${draft.date} ${draft.time} WIB · ${if(draft.paid) "Lunas" else "Belum dikonfirmasi"}")
    if(draft.customer.isNotBlank()) FarmFact("Pembeli",draft.customer)
    val options=sources(context,draft)
    draft.items.forEachIndexed {index,line ->
        HorizontalDivider()
        FarmFact("Sumber panen ${index+1}",options.find {it.optString("id")==line.sourceId}
            ?.optString("name") ?: "Sumber belum tersedia pada data tersimpan")
        Text("${line.quantity} kg × ${line.price} IDR/kg")
    }
    if(draft.notes.isNotBlank()) FarmFact("Keterangan",draft.notes)
    if(draft.reason.isNotBlank()) FarmFact("Alasan koreksi",draft.reason)
}
@Composable fun SaleFacts(record:LocalRecord,context:CultivationUiState) {
    val draft=runCatching {SaleDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion)}.getOrNull()
    if(draft==null) {Notice("Rincian penjualan belum lengkap. Data tetap disimpan untuk ditinjau.",true);return}
    SaleDraftFacts(draft,context,record.body.optString("total_amount").takeUnless {
        record.body.isNull("total_amount") || it.isBlank()
    })
    record.body.optJSONArray("items")?.let {items -> repeat(items.length()) {val line=items.getJSONObject(it);if(line.has("grade_name")) Text("Grade saat diterima: ${line.getString("grade_name")} · versi panen ${line.optLong("harvest_version")}")}}
    if(record.body.optString("record_state")=="cancelled") Notice("Penjualan dibatalkan dan dikecualikan dari penerimaan aktif.",true)
    record.body.optString("recorded_by_name").takeIf {it.isNotBlank()}?.let {Text("Dicatat oleh: $it")}
}
