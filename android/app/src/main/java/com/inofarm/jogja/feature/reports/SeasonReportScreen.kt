package com.inofarm.jogja.feature.reports

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import com.inofarm.jogja.core.design.FarmDraftStatus
import com.inofarm.jogja.core.design.FarmMotion
import com.inofarm.jogja.core.design.FarmPanel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@Composable fun SeasonReportScreen(workspace:SeasonReportWorkspace,context:CultivationUiState) {
    val state=workspace.state;val vm=workspace.vm;val draft=state.draft;val selected=state.selected
    val accepted=selected?.serverVersion!=null
    val data=runCatching {draft?.preview() ?: if(accepted) selected?.serverBody ?: selected?.body else selected?.body?.optJSONObject("_local_form")?.optJSONObject("preview")}.getOrNull()
    LazyColumn(Modifier.fillMaxSize().testTag("report-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("Evaluasi musim",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f));TextButton(onClick=vm::back,enabled=!state.busy,modifier=Modifier.testTag("report-back")) {Text(if(draft!=null) "Simpan draf" else "Kembali")}}}
        state.message?.let {message -> item {Notice(message,true)}}
        if(state.accessDenied) {item {Notice("Akses perlu diperiksa kembali. Sinkronkan atau masuk ulang; data lokal tidak dihapus.",true)}}
        else if(state.busy) {item {CircularProgressIndicator();Text("Memeriksa atau menyimpan evaluasi…")}}
        else if(draft!=null) {
            item {Text(context.greenhouses.find {it.optString("id")==draft.greenhouseId}?.optString("name") ?: "Greenhouse tersimpan")}
            item {FarmDraftStatus(state.saved, Modifier.testTag("report-draft-status"))}
            item {Choice("Musim laporan",draft.seasonId,context.seasons.filter {it.optString("greenhouse_id")==draft.greenhouseId},"report-season") {value->vm.edit {it.copy(seasonId=value)}}}
            item {Coverage("Biaya dan alokasi",draft.costs,"report-costs") {value->vm.edit {it.copy(costs=value)}}}
            item {Coverage("Penjualan lunas",draft.sales,"report-sales") {value->vm.edit {it.copy(sales=value)}}}
            item {Choice("Status yang ditinjau",draft.requestedState,listOf(option("provisional","Sementara"),option("reviewed_final","Akhir setelah ditinjau")),"report-state") {value->vm.edit {it.copy(requestedState=value)}}}
            item {FinanceField("Jumlah antrean yang diketahui",draft.pending,{value->vm.edit {it.copy(pending=value)}},"report-pending",state.errors["pending"],KeyboardType.Number,hint="Kosong: belum diketahui. Isi 0 hanya setelah ditinjau.")}
            item {FinanceField("Catatan tinjauan pengelola",draft.note,{value->vm.edit {it.copy(note=value)}},"report-note",state.errors["note"],singleLine=false)}
            item {Notice("Lengkap adalah pernyataan tinjauan Anda. Aplikasi tidak dapat memastikan seluruh perangkat offline atau transaksi yang belum dicatat. Pembelian aset dan pembayaran sumber tidak dibebankan lagi bersama alokasi.")}
            state.errors.values.distinct().forEach {message->item {Notice(message,true)}}
            item {Button(onClick=vm::preview,modifier=Modifier.fillMaxWidth().testTag("report-preview")) {Text("Muat dan tinjau pratinjau server")}}
            if(data!=null) {
                item {Notice("Pratinjau belum menjadi laporan yang diterima server. Perubahan sumber memerlukan peninjauan ulang.");draft.previewFetchedAt?.let {Text("Pratinjau diperiksa pada perangkat: ${timeLabel(Instant.ofEpochMilli(it).toString())}")}}
                item {ReportHeader(data,false)}
                items(SeasonReportContract.labels.keys.toList(),key={it}) {key ->Indicator(key,data.getJSONObject("metrics").getJSONObject(key))}
                item {ReportAssumptions(data)}
                item {OutlinedButton(onClick=vm::sources,modifier=Modifier.fillMaxWidth().testTag("report-sources")) {Text("Tinjau sumber dan versinya")}}
                item {Button(onClick=vm::queue,modifier=Modifier.fillMaxWidth().testTag("report-queue")) {Text("Simpan permintaan dan antrekan")}}
            }
            item {OutlinedButton(onClick=vm::back,modifier=Modifier.fillMaxWidth().testTag("report-save-draft")) {Text("Simpan draf dan kembali")}}
        } else if(selected!=null) {
            if(accepted) item {com.inofarm.jogja.feature.exports.ReportExportActions(selected)}
            item {Notice(if(accepted) {if(state.fresh) "Laporan tersimpan telah diperiksa ke server pada sesi ini." else "Menampilkan salinan laporan tersimpan. Pemeriksaan terbaru belum terkonfirmasi."} else "Permintaan lokal belum diterima server. Hasil berikut hanya pratinjau yang pernah ditinjau.",!accepted)}
            selected.error?.let {error ->item {ErrorReasons(error)}}
            if(data!=null) {
                item {ReportHeader(data,accepted)}
                items(SeasonReportContract.labels.keys.toList(),key={it}) {key ->Indicator(key,data.getJSONObject("metrics").getJSONObject(key))}
                item {ReportAssumptions(data)}
                if(accepted) {
                    item {OutlinedButton(onClick=vm::sources,modifier=Modifier.fillMaxWidth().testTag("report-sources")) {Text("Lihat sumber laporan")}}
                    item {OutlinedButton(onClick={vm.new(selected.greenhouseId,data.getString("season_id"))},modifier=Modifier.fillMaxWidth().testTag("report-evaluate-again")) {Text("Evaluasi ulang sebagai laporan baru")}}
                }
            } else item {Notice("Belum ada laporan yang diterima server untuk ditampilkan. Permintaan tetap tersimpan.")}
        } else {
            item {Text("Riwayat lokal per akun. Sinkronkan untuk menerima laporan server terbaru; laporan lama tetap tersimpan.")}
            item {Choice("Greenhouse laporan",state.greenhouse,listOf(option("","Semua greenhouse"))+context.greenhouses,"report-gh") {vm.filter(it,"")}}
            item {Choice("Musim",state.season,listOf(option("","Semua musim"))+context.seasons.filter {state.greenhouse.isBlank() || it.optString("greenhouse_id")==state.greenhouse},"report-filter-season") {vm.filter(state.greenhouse,it)}}
            item {Button(onClick={vm.new()},enabled=context.greenhouses.any {it.optString("id")==state.greenhouse},modifier=Modifier.fillMaxWidth().testTag("report-new")) {Text("Buat evaluasi musim")}}
            if(state.records.isEmpty()) item {Notice("Belum ada draf atau laporan dalam riwayat yang tersimpan.")}
            items(state.records,key={it.id}) {row ->OutlinedCard(onClick={vm.select(row.id)},modifier=Modifier.fillMaxWidth().testTag("report-record-${row.id}")) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(row.serverBody?.optJSONObject("scope")?.optJSONObject("season")?.optString("name") ?: context.seasons.find {it.optString("id")==row.body.optString("season_id")}?.optString("name") ?: "Evaluasi tersimpan")
                Text(if(row.syncState==SyncState.ACCEPTED) "Laporan diterima server" else row.syncState.label())
                row.error?.let {ErrorReasons(it)}
            }}}
            if(state.more) item {OutlinedButton(onClick={vm.reload(true)},modifier=Modifier.testTag("report-more")) {Text("Riwayat berikutnya")}}
        }
        if(!state.accessDenied && !state.busy) state.sources?.let {page ->
            item {Text("Sumber yang ditinjau",style=MaterialTheme.typography.titleLarge);Text("${page.rows.length()} dari ${page.body.getInt("source_count")} sumber · ${if(page.cached) "salinan tersimpan" else "diperiksa server"}");Text("Diperiksa pada perangkat: ${timeLabel(Instant.ofEpochMilli(page.body.getLong("fetched_at")).toString())}",style=MaterialTheme.typography.bodySmall)}
            items((0 until page.rows.length()).toList(),key={"source-$it"}) {SourceCard(page.rows.getJSONObject(it))}
            if(page.cursor!=null) item {OutlinedButton(onClick=vm::sources,modifier=Modifier.fillMaxWidth().testTag("report-more-sources")) {Text("Sumber berikutnya")}}
        }
    }
}

private fun option(id:String,name:String)=JSONObject().put("id",id).put("name",name)
@Composable private fun Coverage(label:String,value:String,tag:String,onChange:(String)->Unit) {
    Choice("Kelengkapan $label",value,listOf(option("unknown","Belum diketahui"),option("partial","Belum lengkap"),option("complete","Sudah ditinjau lengkap")),tag,onChange)
}
@Composable private fun ReportHeader(data:JSONObject,accepted:Boolean) {
    Text(data.getJSONObject("scope").getJSONObject("greenhouse").getString("name"),style=MaterialTheme.typography.titleLarge)
    Text(data.getJSONObject("scope").getJSONObject("season").getString("name"))
    Text(if(data.getString("report_state")=="reviewed_final") "Akhir setelah tinjauan pengelola" else "Sementara",modifier=Modifier.testTag("report-status"))
    if(accepted) {Text("Diterbitkan: ${timeLabel(data.getString("generated_at"))}");Text("Pengelola: ${data.getString("recorded_by_name")}")}
    Text("Dasar: ${data.getInt("source_count")} sumber · metode biaya tercatat, versi 1",style=MaterialTheme.typography.bodySmall)
    fun coverage(value:String)=when(value) {"complete"->"ditinjau lengkap";"partial"->"belum lengkap";else->"belum diketahui"}
    Text("Biaya/alokasi: ${coverage(data.getJSONObject("coverage").getString("costs"))}; penjualan: ${coverage(data.getJSONObject("coverage").getString("sales"))}.")
    Text("Tinjauan: ${data.getString("review_note")}")
}
@Composable private fun Indicator(key:String,metric:JSONObject) {
    var expanded by remember(key,metric.toString()) {mutableStateOf(false)}
    FarmPanel(Modifier.animateContentSize(tween(FarmMotion.State)).testTag("report-metric-$key"), title = SeasonReportContract.labels.getValue(key)) {
        Text(if(metric.isNull("value")) "Belum dapat dihitung" else reportValue(metric.getString("value"),metric.getString("unit"),metric.getInt("display_scale")),style=MaterialTheme.typography.headlineSmall)
        if(metric.getString("status")=="provisional") Text("Hasil sementara dari sumber yang belum dinyatakan lengkap.")
        val reasons=metric.getJSONArray("reasons");repeat(reasons.length()) {Text(reasons.getString(it))}
        TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Tutup perhitungan" else "Rumus, input dan asumsi")}
        if(expanded) {
            Text(metric.getString("formula"));val inputs=metric.getJSONObject("inputs")
            inputs.keys().forEach {name->Text("$name: ${if(inputs.isNull(name)) "belum diketahui" else inputs.getString(name)}")}
            val assumptions=metric.getJSONArray("assumptions");repeat(assumptions.length()) {Text(assumptions.getString(it))}
            metric.optJSONObject("exact_fraction")?.let {Text("Pecahan sebelum pembulatan: ${it.getString("numerator")} / ${it.getString("denominator")}",style=MaterialTheme.typography.bodySmall)}
        }
    }
}
@Composable private fun ReportAssumptions(data:JSONObject) {Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Text("Cakupan dan asumsi",style=MaterialTheme.typography.titleMedium)
    val assumptions=data.getJSONArray("assumptions");repeat(assumptions.length()) {Text(assumptions.getString(it))}
    Text("Antrean yang dinyatakan: ${if(data.isNull("known_pending_count")) "belum diketahui" else data.getInt("known_pending_count").toString()}")
}}
@Composable private fun SourceCard(source:JSONObject) {
    val data=source.getJSONObject("data");var expanded by remember(source.getString("id")) {mutableStateOf(false)}
    val label=when(source.getString("kind")) {"asset"->"Aset";"expense"->"Pembayaran sumber";"cost_allocation"->"Alokasi biaya";"depreciation_allocation"->"Alokasi penyusutan";else->"Penjualan"}
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("$label · versi ${source.getInt("version")}",style=MaterialTheme.typography.titleMedium);Text(source.getString("reason"))
        source.optJSONObject("contribution")?.let {values->values.keys().forEach {key->Text("${SeasonReportContract.labels[key] ?: key}: ${reportValue(values.getString(key),if(key=="Q") "kg" else "IDR",if(key=="Q") 6 else 2)}")}}
        TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Tutup rincian" else "Rincian sumber")}
        if(expanded) {
            for(key in listOf("name","category_name","season_name","customer_name","recorded_by_name")) data.optString(key).takeUnless {it.isBlank() || it=="null"}?.let {Text(it)}
            for((key,labelText) in listOf("amount" to "Nominal", "cost" to "Harga perolehan", "residual" to "Nilai sisa", "total_amount" to "Total lunas")) if(data.has(key)) Text("$labelText: ${moneyLabel(data.getString(key))}")
            if(data.has("total_weight_kg")) Text("Terjual: ${reportValue(data.getString("total_weight_kg"),"kg",6)}")
            if(data.has("paid_at")) Text("Pembayaran: ${timeLabel(data.getString("paid_at"))}")
            if(data.has("cost_class")) Text("Kelompok biaya: ${if(data.getString("cost_class")=="FC") "biaya tetap" else "biaya variabel"}")
            if(data.has("usage_year")) Text("Tahun pemakaian ke-${data.getInt("usage_year")}: ${data.getString("period_start")} hingga ${data.getString("period_end")} (akhir eksklusif)")
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
