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
        val grade = item.optString("grade_name").ifBlank { "Grade belum tersedia" }
        val detail = "${timeLabel(harvest.optString("occurred_at"))} · ${balance?.optString("available_weight_kg")?.let { kg -> "sisa ${runCatching { java.math.BigDecimal(kg).stripTrailingZeros().toPlainString().replace('.', ',') }.getOrDefault(kg)} kg" } ?: "saldo belum tersedia"}"
        JSONObject().put("id", item.getString("id")).put("name", "$grade · $detail")
            .put("choice_label", grade).put("choice_detail", detail)
    }}

@Composable fun SaleForm(state:SaleEntryState,context:CultivationUiState,vm:SaleEntryViewModel) {
    val draft=state.draft ?: return
    FarmFormFrame(header = { FarmFormHeader(if(draft.baseVersion==null) "Catat penjualan lunas" else "Koreksi penjualan", state.saved, "sale-draft-status", "sale-close", vm::saveAndClose, !state.busy) }, bar = { FarmFormBar("sale-save-draft", vm::saveAndClose, "sale-review", "Periksa", vm::review, enabled = !state.busy, busy = state.busy) }) {
        FarmFormSection("Pembayaran dan pembeli") {
        if(draft.lockedSeason) Text("Musim: ${context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "terakhir tersimpan"}")
        else Choice("Musim penjualan",draft.seasonId,context.seasons.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("status")=="open"},"sale-season") {value -> vm.edit {it.copy(seasonId=value,items=listOf(SaleLine()))}}
        FinanceDateField("Tanggal lunas (YYYY-MM-DD)",draft.date,{value -> vm.edit {it.copy(date=value)}},"sale-date",state.errors["paid_at"])
        FinanceTimeField("Jam lunas WIB (HH:mm)",draft.time,{value -> vm.edit {it.copy(time=value)}},"sale-time",hint="Detik opsional HH:mm:ss.")
        FinanceField("Pembeli (boleh kosong)",draft.customer,{value -> vm.edit {it.copy(customer=value)}},"sale-customer",state.errors["customer"])
        }
        val options=sources(context,draft)
        draft.items.forEachIndexed {index,line -> key(line.id) { FarmFormSection("Rincian panen ${index+1}") {
            fun edit(updated:SaleLine) {vm.edit {it.copy(items=it.items.toMutableList().apply {set(index,updated)})}}
            if(line.id in draft.lockedSources) Text(options.find {it.optString("id")==line.sourceId}?.optString("name") ?: "Sumber terakhir tersimpan · perbarui untuk saldo terbaru")
            else Choice("Sumber panen ${index+1}",line.sourceId,options,"sale-source-$index") {edit(line.copy(sourceId=it))}
            FinanceField("Bobot ${index+1} (kg)",line.quantity,{edit(line.copy(quantity=it))},"sale-quantity-$index",state.errors["items.$index.quantity"],KeyboardType.Decimal,hint="Lebih dari nol, maksimal6 desimal; tanpa pemisah ribuan.", suffix = "kg", prominent = true)
            FinanceField("Harga ${index+1} (IDR/kg)",line.price,{edit(line.copy(price=it))},"sale-price-$index",state.errors["items.$index.price"],KeyboardType.Decimal,hint="Harga manual, maksimal6 desimal. Kosong bukan nol.", suffix = "IDR/kg", prominent = true)
            TextButton(onClick={vm.edit {it.copy(items=it.items.filterIndexed {i,_ -> i!=index})}},modifier=Modifier.testTag("sale-remove-$index")) {Text("Hapus rincian ${index+1}")}
        }}}
        OutlinedButton(onClick={vm.edit {it.copy(items=it.items+SaleLine())}},enabled=draft.items.size<30,modifier=Modifier.fillMaxWidth().testTag("sale-add")) {Text("Tambah sumber panen")}
        FarmCheckbox("Saya memastikan pembayaran penjualan ini sudah lunas.", draft.paid,
            { value -> vm.edit { it.copy(paid = value) } }, Modifier.testTag("sale-paid"))
        FinanceField("Keterangan penjualan",draft.notes,{value -> vm.edit {it.copy(notes=value)}},"sale-notes",state.errors["notes"],singleLine=false)
        if(draft.baseVersion!=null) FinanceField(vm.reasonLabel,draft.reason,{value -> vm.edit {it.copy(reason=value)}},"sale-reason",state.errors["reason"],singleLine=false)
        Notice("Saldo bisa berubah. Penjualan baru tercatat setelah terkirim.")
        runCatching { draft.preview() }.getOrNull()?.let {
            FarmHeroCard {
                Text("Total penerimaan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FarmAmount(moneyLabel(it.total.toPlainString()), com.inofarm.jogja.core.design.FarmType.HeroAmount)
                Text("${it.weightKg.toPlainString()} kg dari rincian. Total isian, bukan nilai resmi.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
    }
}
@Composable fun SaleReview(draft:SaleDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    FarmReviewFrame("penjualan lunas", whenLabel(draft.date, draft.time), FarmIcon.TrendUp, busy, "sale-queue", "sale-back", onBack, onQueue) {
        FarmFormSection("Rincian transaksi") { SaleDraftFacts(draft,context) }
        if(draft.replaceValidationRejected) Notice("Perbaikan ini dikirim sebagai catatan baru. Catatan yang ditolak tetap ada di riwayat.")
    }
}
@Composable private fun SaleDraftFacts(draft:SaleDraft,context:CultivationUiState,acceptedTotal:String?=null) {
    val preview=runCatching { draft.preview() }.getOrNull()
    if(acceptedTotal!=null) FarmFact("Total tercatat", moneyLabel(acceptedTotal),
        Modifier.testTag("sale-accepted-total").semantics(mergeDescendants=true) {}, prominent=true)
    else if(preview!=null) FarmFact("Perkiraan total penjualan",moneyLabel(preview.total.toPlainString()),
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
        FarmFact("Bobot", line.quantity.takeIf(String::isNotBlank)?.let { "$it kg" } ?: "Belum diisi")
        FarmFact("Harga per kg", line.price.takeIf(String::isNotBlank)?.let { "$it IDR/kg" } ?: "Belum diisi")
    }
    if(draft.notes.isNotBlank()) FarmFact("Keterangan",draft.notes)
    if(draft.reason.isNotBlank()) FarmFact("Alasan koreksi",draft.reason)
}
@Composable fun SaleFacts(record:LocalRecord,context:CultivationUiState) {
    val draft=runCatching {SaleDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion)}.getOrNull()
    if(draft==null) {Notice("Rincian penjualan belum lengkap. Data tetap tersimpan.",true);return}
    SaleDraftFacts(draft,context,record.body.optString("total_amount").takeUnless {
        record.body.isNull("total_amount") || it.isBlank()
    })
    record.body.optJSONArray("items")?.let {items -> repeat(items.length()) {val line=items.getJSONObject(it);if(line.has("grade_name")) Text("Grade saat diterima: ${line.getString("grade_name")} · versi panen ${line.optLong("harvest_version")}")}}
    if(record.body.optString("record_state")=="cancelled") Notice("Penjualan dibatalkan dan dikecualikan dari penerimaan aktif.",true)
    record.body.optString("recorded_by_name").takeIf {it.isNotBlank()}?.let {Text("Dicatat oleh: $it")}
}
