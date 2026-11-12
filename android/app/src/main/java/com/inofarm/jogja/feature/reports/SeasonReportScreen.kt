package com.inofarm.jogja.feature.reports

import com.inofarm.jogja.core.design.FarmAmount
import com.inofarm.jogja.core.design.FarmDraftStatus
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmPageTitle
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmResultRow
import com.inofarm.jogja.core.design.FarmSectionHeading
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.FarmShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.SeasonReportContract
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.expenses.moneyLabel
import org.json.JSONObject
import java.time.Instant
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

data class SeasonReportWorkspace(val state:SeasonReportState,val vm:SeasonReportViewModel)

@Composable fun SeasonReportScreen(workspace:SeasonReportWorkspace,context:CultivationUiState,returnToQueue:Boolean=false) {
    val state=workspace.state;val vm=workspace.vm;val draft=state.draft;val selected=state.selected
    val accepted=selected?.serverVersion!=null
    val data=runCatching {draft?.preview() ?: if(accepted) selected?.serverBody ?: selected?.body else selected?.body?.optJSONObject("_local_form")?.optJSONObject("preview")}.getOrNull()
    LazyColumn(Modifier.fillMaxSize().testTag("report-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
        item {
            FarmPageTitle("Evaluasi musim")
            TextButton(onClick = {vm.back(returnToQueue)}, enabled = !state.busy, modifier = Modifier.heightIn(min=48.dp).testTag("report-back")) {
                Text(if (draft != null) "Simpan dan kembali" else if(returnToQueue) "Kembali ke belum terkirim" else "Kembali")
            }
        }
        state.message?.let {message -> item {Notice(message,true)}}
        if(state.accessDenied) {item {Notice("Akses akun berubah. Perbarui atau masuk ulang. Data di perangkat tidak dihapus.",true)}}
        else if(state.busy) {item {CircularProgressIndicator();Text("Sedang memproses…")}}
        else if(draft!=null) {
            item {Text(context.greenhouses.find {it.optString("id")==draft.greenhouseId}?.optString("name") ?: "Greenhouse tersimpan")}
            item {FarmDraftStatus(state.saved, Modifier.testTag("report-draft-status"))}
            item {Choice("Musim laporan",draft.seasonId,context.seasons.filter {it.optString("greenhouse_id")==draft.greenhouseId},"report-season") {value->vm.edit {it.copy(seasonId=value)}}}
            item {Coverage("Biaya dan alokasi",draft.costs,"report-costs") {value->vm.edit {it.copy(costs=value)}}}
            item {Coverage("Penjualan lunas",draft.sales,"report-sales") {value->vm.edit {it.copy(sales=value)}}}
            item {Choice("Status laporan",draft.requestedState,listOf(option("provisional","Sementara"),option("reviewed_final","Akhir")),"report-state") {value->vm.edit {it.copy(requestedState=value)}}}
            item {FinanceField("Jumlah catatan belum terkirim",draft.pending,{value->vm.edit {it.copy(pending=value)}},"report-pending",state.errors["pending"],KeyboardType.Number,hint="Kosongkan jika belum tahu. Isi 0 jika semua sudah terkirim.")}
            item {FinanceField("Catatan pengelola",draft.note,{value->vm.edit {it.copy(note=value)}},"report-note",state.errors["note"],singleLine=false)}
            item {Notice("Pilih Sudah lengkap hanya jika semua biaya dan penjualan sudah tercatat, termasuk dari perangkat lain.")}
            state.errors.values.distinct().forEach {message->item {Notice(message,true)}}
            item {Button(onClick=vm::preview,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().testTag("report-preview")) {Text("Lihat dulu")}}
            if(data!=null) {
                item {Notice("Ini baru tampilan awal, belum menjadi laporan terbit.");draft.previewFetchedAt?.let {Text("Dilihat pada: ${timeLabel(Instant.ofEpochMilli(it).toString())}")}}
                item {ReportHeader(data,false)}
                seasonIndicators(data.getJSONObject("metrics"))
                item {ReportAssumptions(data)}
                item {OutlinedButton(onClick=vm::sources,modifier=Modifier.fillMaxWidth().testTag("report-sources")) {Text("Lihat sumber data")}}
                item {Button(onClick=vm::queue,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().testTag("report-queue")) {Text("Simpan")}}
            }
            item {OutlinedButton(onClick={vm.back(returnToQueue)},shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("report-save-draft")) {Text("Simpan dan kembali")}}
        } else if(selected!=null) {
            item {Notice(if(accepted) {if(state.fresh) "Laporan sudah dicocokkan dengan data terbaru." else "Menampilkan salinan di perangkat. Belum bisa dicocokkan dengan data terbaru."} else "Laporan ini belum terkirim. Yang tampil hanya tampilan awal.",!accepted)}
            selected.error?.let {error ->item {ErrorReasons(error)}}
            if(data!=null) {
                item {ReportHeader(data,accepted)}
                seasonIndicators(data.getJSONObject("metrics"))
                item {ReportAssumptions(data)}
                if(accepted) {
                    item {OutlinedButton(onClick=vm::sources,modifier=Modifier.fillMaxWidth().testTag("report-sources")) {Text("Lihat sumber data")}}
                    item {OutlinedButton(onClick={vm.new(selected.greenhouseId,data.getString("season_id"))},modifier=Modifier.fillMaxWidth().testTag("report-evaluate-again")) {Text("Buat evaluasi baru")}}
                }
            } else item {Notice("Laporan belum bisa ditampilkan. Catatan Anda tetap tersimpan.")}
            if(accepted) item {com.inofarm.jogja.feature.exports.ReportExportActions(selected)}
        } else {
            item {Choice("Greenhouse laporan",state.greenhouse,listOf(option("","Semua greenhouse"))+context.greenhouses,"report-gh") {vm.filter(it,"")}}
            item {Choice("Musim",state.season,listOf(option("","Semua musim"))+context.seasons.filter {state.greenhouse.isBlank() || it.optString("greenhouse_id")==state.greenhouse},"report-filter-season") {vm.filter(state.greenhouse,it)}}
            item {Button(onClick={vm.new()},enabled=context.greenhouses.any {it.optString("id")==state.greenhouse},modifier=Modifier.fillMaxWidth().testTag("report-new")) {Text("Buat evaluasi musim")}}
            if(state.records.isEmpty()) item {Notice("Belum ada laporan.")}
            items(state.records,key={it.id}) {row ->OutlinedCard(onClick={vm.select(row.id)},modifier=Modifier.fillMaxWidth().testTag("report-record-${row.id}")) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(row.serverBody?.optJSONObject("scope")?.optJSONObject("season")?.optString("name") ?: context.seasons.find {it.optString("id")==row.body.optString("season_id")}?.optString("name") ?: "Evaluasi tersimpan")
                Text(if(row.syncState==SyncState.ACCEPTED) "Laporan terbit" else row.syncState.label())
                row.error?.let {ErrorReasons(it)}
            }}}
            if(state.more) item {OutlinedButton(onClick={vm.reload(true)},modifier=Modifier.testTag("report-more")) {Text("Riwayat berikutnya")}}
        }
        if(!state.accessDenied && !state.busy) state.sources?.let {page ->
            item {Text("Sumber data",style=MaterialTheme.typography.titleLarge);Text("${page.rows.length()} dari ${page.body.getInt("source_count")} sumber · ${if(page.cached) "salinan di perangkat" else "data terbaru"}");Text("Diperbarui: ${timeLabel(Instant.ofEpochMilli(page.body.getLong("fetched_at")).toString())}",style=MaterialTheme.typography.bodySmall)}
            items((0 until page.rows.length()).toList(),key={"source-$it"}) {SourceCard(page.rows.getJSONObject(it))}
            if(page.cursor!=null) item {OutlinedButton(onClick=vm::sources,modifier=Modifier.fillMaxWidth().testTag("report-more-sources")) {Text("Sumber berikutnya")}}
        }
    }
}

private fun option(id:String,name:String)=JSONObject().put("id",id).put("name",name)
@Composable private fun Coverage(label:String,value:String,tag:String,onChange:(String)->Unit) {
    Choice("Kelengkapan $label",value,listOf(option("unknown","Belum diketahui"),option("partial","Belum lengkap"),option("complete","Sudah lengkap")),tag,onSelect=onChange)
}
@Composable private fun ReportHeader(data:JSONObject,accepted:Boolean) {
    val scope = data.getJSONObject("scope")
    val season = scope.getJSONObject("season")
    val coverage = data.getJSONObject("coverage")
    fun coverageLabel(value: String) = when (value) {
        "complete" -> "lengkap"
        "partial" -> "belum lengkap"
        else -> "belum diketahui"
    }
    FarmPanel(title = season.getString("name")) {
        Text(scope.getJSONObject("greenhouse").getString("name"), style = MaterialTheme.typography.bodyMedium)
        Text(if (data.getString("report_state") == "reviewed_final") "Akhir" else "Sementara",
            modifier = Modifier.testTag("report-status"), style = MaterialTheme.typography.labelLarge)
        val start = if (season.isNull("start_date")) "Awal belum tersedia" else season.getString("start_date")
        val end = if (season.isNull("end_date")) "akhir belum tercatat" else season.getString("end_date")
        FarmFact("Periode musim", "$start · $end")
        FarmFact("Dasar evaluasi", "${data.getInt("source_count")} sumber · metode biaya tercatat, versi 1")
        Text("Biaya dan alokasi ${coverageLabel(coverage.getString("costs"))}. Penjualan ${coverageLabel(coverage.getString("sales"))}.")
        if (accepted) {
            Text("Diterbitkan ${timeLabel(data.getString("generated_at"))}", style = MaterialTheme.typography.bodySmall)
            Text("Pengelola ${data.getString("recorded_by_name")}", style = MaterialTheme.typography.bodySmall)
        }
        FarmFact("Catatan", data.getString("review_note"))
    }
}
private fun LazyListScope.seasonIndicators(metrics: JSONObject) {
    val groups = listOf(
        "Hasil usaha" to listOf("L", "RC", "BC"),
        "Penerimaan dan biaya" to listOf("TR", "Q", "P", "TC", "FC", "VC"),
        "Titik impas" to listOf("BEP_Q", "BEP_PRICE")
    )
    for ((title, keys) in groups) {
        item("result-$title") { FarmSectionHeading(title, Modifier.padding(top = FarmSpace.Medium)) }
        items(keys, key = { "result-$it" }) { key -> Indicator(key, metrics.getJSONObject(key)) }
    }
}

@Composable private fun Indicator(key:String,metric:JSONObject) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(key, metric.toString()) { mutableStateOf(false) }
    FarmResultRow(SeasonReportContract.labels.getValue(key), Modifier.testTag("report-metric-$key")) {
        val unit = metric.getString("unit")
        val value = if (metric.isNull("value")) "Belum dapat dihitung"
            else reportValue(metric.getString("value"), unit, metric.getInt("display_scale"))
        if (!metric.isNull("value") && unit in setOf("IDR", "IDR/kg")) FarmAmount(value, MaterialTheme.typography.headlineSmall)
        else Text(value, style = if (metric.isNull("value")) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
        if(metric.getString("status")=="provisional") Text("Hasil sementara karena data belum lengkap.",
            style = MaterialTheme.typography.bodyMedium)
        val reasons = metric.getJSONArray("reasons")
        repeat(reasons.length()) { Text(reasons.getString(it), style = MaterialTheme.typography.bodyMedium) }
        TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Tutup perhitungan" else "Rumus dan asumsi")}
        if(expanded) {
            FarmFact("Rumus", metric.getString("formula"))
            if (key == "BC") Text("B/C sederhana membagi laba dari biaya tercatat dengan total biaya.")
            if (key in setOf("BEP_Q", "BEP_PRICE")) Text("Titik impas memakai total biaya tercatat dan penjualan lunas pada musim ini.")
            val inputs=metric.getJSONObject("inputs")
            inputs.keys().forEach { name ->
                FarmFact(SeasonReportContract.labels[name] ?: name, if(inputs.isNull(name)) "Belum diketahui" else inputs.getString(name))
            }
            val assumptions=metric.getJSONArray("assumptions");repeat(assumptions.length()) {Text(assumptions.getString(it))}
            metric.optJSONObject("exact_fraction")?.let {Text("Pecahan sebelum pembulatan: ${it.getString("numerator")} / ${it.getString("denominator")}",style=MaterialTheme.typography.bodySmall)}
        }
    }
}
@Composable private fun ReportAssumptions(data:JSONObject) {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Text("Asumsi",style=MaterialTheme.typography.titleMedium)
    val assumptions=data.getJSONArray("assumptions");repeat(assumptions.length()) {Text(assumptions.getString(it))}
    Text("Catatan belum terkirim: ${if(data.isNull("known_pending_count")) "belum diketahui" else data.getInt("known_pending_count").toString()}")
}}
@Composable private fun SourceCard(source:JSONObject) {
    val data=source.getJSONObject("data");var expanded by remember(source.getString("id")) {mutableStateOf(false)}
    val label=when(source.getString("kind")) {"asset"->"Aset";"expense"->"Pembayaran";"cost_allocation"->"Alokasi biaya";"depreciation_allocation"->"Alokasi penyusutan";else->"Penjualan"}
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("$label · versi ${source.getInt("version")}",style=MaterialTheme.typography.titleMedium);Text(source.getString("reason"))
        source.optJSONObject("contribution")?.let {values->values.keys().forEach {key->Text("${SeasonReportContract.labels[key] ?: key}: ${reportValue(values.getString(key),if(key=="Q") "kg" else "IDR",if(key=="Q") 6 else 2)}")}}
        TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Tutup rincian" else "Lihat rincian")}
        if(expanded) {
            for(key in listOf("name","category_name","season_name","customer_name","recorded_by_name")) data.optString(key).takeUnless {it.isBlank() || it=="null"}?.let {Text(it)}
            for((key,labelText) in listOf("amount" to "Nominal", "cost" to "Harga perolehan", "residual" to "Nilai sisa", "total_amount" to "Total lunas")) if(data.has(key)) Text("$labelText: ${moneyLabel(data.getString(key))}")
            if(data.has("total_weight_kg")) Text("Terjual: ${reportValue(data.getString("total_weight_kg"),"kg",6)}")
            if(data.has("paid_at")) Text("Pembayaran: ${timeLabel(data.getString("paid_at"))}")
            if(data.has("cost_class")) Text("Kelompok biaya: ${if(data.getString("cost_class")=="FC") "biaya tetap" else "biaya variabel"}")
            if(data.has("usage_year")) Text("Tahun pemakaian ke-${data.getInt("usage_year")}: ${data.getString("period_start")} hingga ${data.getString("period_end")} (tanggal akhir tidak termasuk)")
            for(key in listOf("notes","allocation_basis","usage_basis")) data.optString(key).takeUnless {it.isBlank() || it=="null"}?.let {Text(it)}
            if(data.has("life_years")) Text("Umur manfaat: ${data.getInt("life_years")} tahun; mulai digunakan ${data.getString("in_service_date")}")
            if(data.has("year_budget")) Text("Anggaran penyusutan tahun pemakaian: ${moneyLabel(data.getString("year_budget"))}")
            data.optJSONArray("items")?.let {rows->repeat(rows.length()) {index->val line=rows.getJSONObject(index)
                Text("${line.getString("grade_name")}: ${reportValue(line.getString("quantity_kg"),"kg",6)} × ${reportValue(line.getString("price_per_kg"),"IDR/kg",6)}")
                Text("Sumber panen: ${line.getString("harvest_id")} · versi ${line.getInt("harvest_version")}",style=MaterialTheme.typography.bodySmall)
            }}
            Text("Referensi sumber: ${source.getString("id")}",style=MaterialTheme.typography.bodySmall)
        }
    }}
}

private fun reportValue(value:String,unit:String,scale:Int):String {
    val amount=BigDecimal(value)
    if(unit=="IDR") return moneyLabel(value)
    val formatter=if(unit=="IDR/kg") NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")) else NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    formatter.minimumFractionDigits=if(unit=="IDR/kg") 2 else 0;formatter.maximumFractionDigits=scale
    return formatter.format(amount)+when(unit) {"IDR/kg"->"/kg";"ratio"->" rasio";else->" $unit"}
}
