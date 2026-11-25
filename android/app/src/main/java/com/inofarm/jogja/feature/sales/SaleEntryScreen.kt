package com.inofarm.jogja.feature.sales

import com.inofarm.jogja.feature.whenLabel
import com.inofarm.jogja.feature.FarmFormFrame
import com.inofarm.jogja.feature.FarmFormHeader
import com.inofarm.jogja.feature.FarmFormBar
import com.inofarm.jogja.feature.FarmFormSection
import com.inofarm.jogja.feature.FarmReviewFrame
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.FarmHeroCard
import com.inofarm.jogja.core.design.FarmAmount
import com.inofarm.jogja.core.design.measureText
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

internal fun saleStock(context:CultivationUiState)=SaleStock(context.harvests,context.harvestBalances,context.groups,context.varieties,context.grades)
private fun lineKey(line:SaleLine)=if(line.groupId.isBlank()) "" else "${line.groupId}|${line.gradeId}"
/** Compile-level bridge until the new form (Task 9): one choice per planting group and grade with its summed stock. */
private fun sources(context:CultivationUiState,draft:SaleDraft):List<JSONObject> = saleStock(context).options(draft.seasonId).map {option ->
    val detail="sisa ${option.availableKg.stripTrailingZeros().toPlainString().replace('.', ',')} kg"
    val grade=option.gradeName.ifBlank {"Grade belum tersedia"}
    JSONObject().put("id","${option.groupId}|${option.gradeId}").put("name","${option.varietyName} · $grade · $detail").put("choice_label","${option.varietyName} · $grade").put("choice_detail",detail)
}

@Composable fun SaleForm(state:SaleEntryState,context:CultivationUiState,vm:SaleEntryViewModel,buyerSearch:(suspend (String,String)->List<com.inofarm.jogja.feature.customers.Customer>)?=null) {
    val draft=state.draft ?: return
    val cancel = rememberEntryCancel(state.busy, vm::hasChanges, vm::cancel)
    FarmFormFrame(header = { FarmFormHeader(if(draft.baseVersion==null) "Catat penjualan lunas" else "Koreksi penjualan", state.saved, "sale-draft-status", "sale-close", cancel, !state.busy,
        showDraftStatus = false, closeLabel = "Batal", step = 1) },
        bar = { FarmFormBar("sale-cancel", cancel, "sale-review", "Simpan", vm::review, enabled = !state.busy, busy = state.busy, saveLabel = "Batal") }) {
        FarmFormSection("Pembayaran dan pembeli") {
        if(draft.lockedSeason) Text("Musim: ${context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "terakhir tersimpan"}")
        else Choice("Musim penjualan",draft.seasonId,context.seasons.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("status")=="open"},"sale-season") {value -> vm.edit {it.copy(seasonId=value,items=listOf(SaleLine()))}}
        FinanceDateField("Tanggal lunas",draft.date,{value -> vm.edit {it.copy(date=value)}},"sale-date",state.errors["paid_at"])
        FinanceTimeField("Jam lunas (WIB)",draft.time,{value -> vm.edit {it.copy(time=value)}},"sale-time")
        com.inofarm.jogja.feature.customers.BuyerSection(draft,state.errors["customer"],buyerSearch) {change -> vm.edit(change)}
        }
        val options=sources(context,draft)
        draft.items.forEachIndexed {index,line -> key(line.id) { FarmFormSection("Rincian panen ${index+1}") {
            fun edit(updated:SaleLine) {vm.edit {it.copy(items=it.items.toMutableList().apply {set(index,updated)})}}
            if(line.id in draft.lockedSources) Text(options.find {it.optString("id")==lineKey(line)}?.optString("name") ?: "Sumber terakhir tersimpan · perbarui untuk saldo terbaru")
            else Choice("Buah dan grade ${index+1}",lineKey(line),options,"sale-source-$index") {edit(line.copy(groupId=it.substringBefore('|'),gradeId=it.substringAfter('|',"")))}
            // Mitra grades the fruit when buying (20 Nov 2026): one line per grade, the same Layak source may repeat.
            if(draft.buyerType=="partner") com.inofarm.jogja.core.design.FarmChoiceChips("Grade dari mitra",line.buyerGrade.ifBlank {null},
                listOf("A","B","C").map {org.json.JSONObject().put("id",it).put("name","Grade $it")},"sale-grade-$index") {edit(line.copy(buyerGrade=it))}
            FinanceField("Bobot ${index+1} (kg)",line.quantity,{edit(line.copy(quantity=it))},"sale-quantity-$index",state.errors["items.$index.quantity"],KeyboardType.Decimal,hint="Contoh 12,5", suffix = "kg", prominent = true)
            FinanceField("Harga ${index+1} (IDR/kg)",line.price,{edit(line.copy(price=it))},"sale-price-$index",state.errors["items.$index.price"],KeyboardType.Decimal,hint="Ketik angka saja, titik ribuan muncul sendiri", suffix = "IDR/kg", prominent = true, visual = com.inofarm.jogja.core.design.IdrGrouping)
            TextButton(onClick={vm.edit {it.copy(items=it.items.filterIndexed {i,_ -> i!=index})}},modifier=Modifier.testTag("sale-remove-$index")) {Text("Hapus rincian ${index+1}")}
        }}}
        OutlinedButton(onClick={vm.edit {it.copy(items=it.items+SaleLine())}},enabled=draft.items.size<30,modifier=Modifier.fillMaxWidth().testTag("sale-add")) {Text(if(draft.buyerType=="partner") "Tambah grade atau sumber panen" else "Tambah sumber panen")}
        state.errors["items"]?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        FarmCheckbox("Saya memastikan pembayaran penjualan ini sudah lunas.", draft.paid,
            { value -> vm.edit { it.copy(paid = value) } }, Modifier.testTag("sale-paid"))
        FinanceField("Keterangan penjualan",draft.notes,{value -> vm.edit {it.copy(notes=value)}},"sale-notes",state.errors["notes"],singleLine=false)
        if(draft.baseVersion!=null) FinanceField(vm.reasonLabel,draft.reason,{value -> vm.edit {it.copy(reason=value)}},"sale-reason",state.errors["reason"],singleLine=false)
        Notice("Saldo bisa berubah. Penjualan baru tercatat setelah terkirim.")
        runCatching { draft.preview() }.getOrNull()?.let {
            FarmHeroCard {
                Text("Total penerimaan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FarmAmount(moneyLabel(it.total.toPlainString()), com.inofarm.jogja.core.design.FarmType.HeroAmount)
                Text("${measureText(it.weightKg.toPlainString())} kg terjual. Angka resmi dihitung server setelah tersimpan.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
    }
}
@Composable fun SaleReview(draft:SaleDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    FarmReviewFrame("penjualan lunas", whenLabel(draft.date, draft.time), FarmIcon.TrendUp, busy, "sale-queue", "sale-back", onBack, onQueue, summary = true) {
        FarmFormSection("Rincian transaksi") { SaleDraftFacts(draft,context) }
        if(draft.replaceValidationRejected) Notice("Perbaikan ini dikirim sebagai catatan baru. Catatan yang ditolak tetap ada di riwayat.")
    }
}
@Composable private fun SaleDraftFacts(draft:SaleDraft,context:CultivationUiState,acceptedTotal:String?=null) {
    val preview=runCatching { draft.preview() }.getOrNull()
    if(acceptedTotal!=null) FarmFact("Total tercatat", moneyLabel(acceptedTotal),
        Modifier.testTag("sale-accepted-total").semantics(mergeDescendants=true) {}, prominent=true)
    else if(preview!=null) FarmFact("Total penjualan",moneyLabel(preview.total.toPlainString()),
        Modifier.testTag("sale-total-fact").semantics(mergeDescendants=true) {},prominent=true)
    else Notice("Total belum dapat dihitung dari isian ini.",true)
    preview?.let { FarmFact("Bobot terjual", "${measureText(it.weightKg.toPlainString())} kg") }
    FarmFact("Musim",context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "Nama belum tersedia")
    FarmFact("Pembayaran","${whenLabel(draft.date, draft.time)} · ${if(draft.paid) "Lunas" else "Belum dikonfirmasi"}")
    if(draft.customer.isNotBlank()) FarmFact("Pembeli",draft.customer)
    if(draft.hasBuyer()) FarmFact("Tipe pembeli",BuyerTypes.label(draft.buyerType)+if(draft.buyerExisting) "" else " · pembeli baru, masuk daftar")
    val options=sources(context,draft)
    draft.items.forEachIndexed {index,line ->
        HorizontalDivider()
        FarmFact("Sumber panen ${index+1}"+(if(line.buyerGrade.isNotBlank()) " · grade ${line.buyerGrade}" else ""),options.find {it.optString("id")==lineKey(line)}
            ?.optString("name") ?: "Sumber belum tersedia pada data tersimpan")
        FarmFact("Bobot", line.quantity.takeIf(String::isNotBlank)?.let { "${measureText(it.replace(',', '.'))} kg" } ?: "Belum diisi")
        FarmFact("Harga per kg", line.price.takeIf(String::isNotBlank)?.let { "${moneyLabel(it.replace(',', '.'))}/kg" } ?: "Belum diisi")
    }
    if(draft.notes.isNotBlank()) FarmFact("Keterangan",draft.notes)
    if(draft.reason.isNotBlank()) FarmFact("Alasan koreksi",draft.reason)
}
@Composable fun SaleFacts(record:LocalRecord,context:CultivationUiState) {
    val draft=runCatching {SaleDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion,saleStock(context))}.getOrNull()
    if(draft==null) {Notice("Rincian penjualan belum lengkap. Data tetap tersimpan.",true);return}
    SaleDraftFacts(draft,context,record.body.optString("total_amount").takeUnless {
        record.body.isNull("total_amount") || it.isBlank()
    })
    record.body.optJSONArray("items")?.let {items -> repeat(items.length()) {val line=items.getJSONObject(it);if(line.has("grade_name")) Text("Grade saat diterima: ${line.getString("grade_name")} · versi panen ${line.optLong("harvest_version")}")}}
    if(record.body.optString("record_state")=="cancelled") Notice("Penjualan dibatalkan dan dikecualikan dari penerimaan aktif.",true)
    record.body.optString("recorded_by_name").takeIf {it.isNotBlank()}?.let {Text("Dicatat oleh: $it")}
}
