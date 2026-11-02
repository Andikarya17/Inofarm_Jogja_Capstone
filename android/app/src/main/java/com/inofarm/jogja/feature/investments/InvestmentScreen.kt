package com.inofarm.jogja.feature.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.context.CultivationUiState
import org.json.JSONObject
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

data class InvestmentWorkspace(val state:InvestmentState,val vm:InvestmentViewModel)
private fun option(id:String,name:String)=JSONObject().put("id",id).put("name",name)
private fun sourceLabel(kind:String)=when(kind) {"actual"->"Aktual yang dinyatakan";"projection"->"Proyeksi";else->"Belum dinyatakan"}
private fun nullable(body:JSONObject,key:String)=if(body.isNull(key)) "Belum diketahui" else body.getString(key)
internal fun investmentValue(value:String,unit:String,scale:Int):String {
    val number=NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID")).apply {minimumFractionDigits=scale;maximumFractionDigits=scale;maximumIntegerDigits=Int.MAX_VALUE}.format(BigDecimal(value))
    return when(unit) {"IDR"->"Rp$number";"percent"->"$number%";else->"$number tahun"}
}
@Composable private fun SourceChoice(label:String,value:String,tag:String,change:(String)->Unit) {
    Choice(label,value,listOf(option("","Belum dinyatakan"),option("actual","Aktual yang dinyatakan"),option("projection","Proyeksi")),tag,change)
}

@Composable fun InvestmentScreen(workspace:InvestmentWorkspace,context:CultivationUiState) {
    val state=workspace.state;val vm=workspace.vm
    LazyColumn(Modifier.fillMaxSize().testTag("investment-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
        item {Text("Evaluasi investasi",style=MaterialTheme.typography.headlineMedium);TextButton(onClick=vm::back,enabled=!state.busy,modifier=Modifier.testTag("investment-back")) {Text(if(state.scenarioDraft!=null || state.reportDraft!=null || state.cancelReason!=null) "Simpan draf dan kembali" else "Kembali")}}
        state.message?.let {item {Notice(it,true)}}
        if(state.denied) item {Notice("Akses perlu diperiksa kembali. Sinkronkan atau masuk ulang. Draf tetap disimpan.",true)}
        else if(state.busy) item {CircularProgressIndicator();Text("Memeriksa atau menyimpan catatan investasi…")}
        else if(state.scenarioDraft!=null) scenarioForm(state.scenarioDraft,state,vm,context)
        else if(state.reportDraft!=null) {
            val draft=state.reportDraft
            item {Text("Tinjau skenario sebelum menerbitkan laporan");Text(if(state.saved) "Draf tersimpan di perangkat." else "Perubahan belum selesai disimpan.",modifier=Modifier.testTag("investment-draft-status"))}
            item {Notice("Laporan memakai skenario yang telah diterima server. Perubahan belum terkirim harus disinkronkan dahulu.")}
            item {FinanceField("Catatan tinjauan",draft.note,{v->vm.editReport {it.copy(note=v)}},"investment-report-note")}
            item {Button(onClick=vm::preview,modifier=Modifier.fillMaxWidth().testTag("investment-preview")) {Text("Muat pratinjau server")}}
            val preview=runCatching {draft.preview()}.getOrNull()
            if(preview!=null) {
                report(preview,false)
                item {Text("Pratinjau dimuat: ${draft.previewFetchedAt?.let {java.time.Instant.ofEpochMilli(it).toString()} ?: "belum tercatat"}. Sumber akan diperiksa lagi saat server menerima permintaan.")}
                item {Button(onClick=vm::queueReport,modifier=Modifier.fillMaxWidth().testTag("investment-report-queue")) {Text("Simpan permintaan dan antrekan")}}
            }
        } else if(state.selected!=null) {
            val row=state.selected;val accepted=row.serverVersion!=null;val body=row.serverBody ?: row.body
            if(accepted && row.kind==InvestmentContract.REPORT) item {com.inofarm.jogja.feature.exports.ReportExportActions(row)}
            item {Notice(if(accepted) {if(state.fresh) "Salinan telah diperiksa ke server pada sesi ini." else "Menampilkan salinan terakhir tersimpan. Pemeriksaan terbaru belum terkonfirmasi."} else "Catatan lokal belum diterima server.",!state.fresh);Text(row.syncState.label())}
            row.error?.let {item {ErrorReasons(it)}}
            if(row.kind==InvestmentContract.SCENARIO) {
                if(accepted) scenario(body) else rawScenario(InvestmentScenarioDraft.from(row.id,row.greenhouseId,row.body,row.baseVersion))
                if(row.syncState==SyncState.CONFLICT) {
                    item {Notice("Bandingkan versi server ${row.serverVersion} dengan isian lokal sebelum memilih penyelesaian.",true)}
                    if(row.body.has("_local_form")) rawScenario(InvestmentScenarioDraft.from(row.id,row.greenhouseId,row.body,row.baseVersion))
                    item {OutlinedButton(onClick={vm.resolve(false)},modifier=Modifier.fillMaxWidth().testTag("investment-use-server")) {Text("Pilih versi server yang ditinjau")}}
                    if(row.body.has("_local_form")) item {OutlinedButton(onClick={vm.resolve(true)},modifier=Modifier.fillMaxWidth().testTag("investment-rebase")) {Text("Antrekan koreksi lokal dari versi ini")}}
                } else if(state.cancelReason!=null) {
                    item {FinanceField("Alasan pembatalan",state.cancelReason,vm::editCancel,"investment-cancel-reason")}
                    item {Button(onClick=vm::cancel,modifier=Modifier.fillMaxWidth().testTag("investment-cancel-confirm")) {Text("Antrekan pembatalan skenario")}}
                    item {OutlinedButton(onClick=vm::discardCancel,modifier=Modifier.fillMaxWidth().testTag("investment-cancel-discard")) {Text("Tutup pembatalan dan pertahankan catatan server")}}
                } else if(accepted && row.syncState==SyncState.ACCEPTED && body.getString("record_state")=="active") {
                    item {Button(onClick={vm.newReport(row)},modifier=Modifier.fillMaxWidth().testTag("investment-evaluate")) {Text("Evaluasi skenario ini")}}
                    item {OutlinedButton(onClick=vm::editSelected,modifier=Modifier.fillMaxWidth().testTag("investment-edit")) {Text("Koreksi skenario dengan alasan")}}
                    item {OutlinedButton(onClick=vm::beginCancel,modifier=Modifier.fillMaxWidth().testTag("investment-cancel")) {Text("Batalkan skenario")}}
                }
                if(accepted) item {OutlinedButton(onClick=vm::audit,modifier=Modifier.fillMaxWidth().testTag("investment-audit")) {Text("Riwayat perubahan skenario")}}
            } else {
                val snapshot=if(accepted) body else row.body.optJSONObject("_local_form")?.optJSONObject("preview")
                if(snapshot!=null) report(snapshot,accepted)
                if(accepted) {
                    item {Notice("Snapshot ini tetap memakai sumber dan rumus saat diterbitkan. Evaluasi baru menghasilkan laporan baru.")}
                    item {OutlinedButton(onClick=vm::sources,modifier=Modifier.fillMaxWidth().testTag("investment-sources")) {Text("Tinjau sumber tersimpan")}}
                    item {OutlinedButton(onClick=vm::reevaluate,modifier=Modifier.fillMaxWidth().testTag("investment-reevaluate")) {Text("Buat evaluasi baru dari skenario ini")}}
                }
            }
            state.sources?.let {page->
                item {Text(if(page.cached) "Sumber salinan tersimpan." else "Sumber diperoleh dari server pada sesi ini.");Text("${page.rows.length()} dari ${page.body.getInt("source_count")} sumber")}
                items((0 until page.rows.length()).map {page.rows.getJSONObject(it)},key={"source-${it.getString("source_key")}"}) {SourceCard(it)}
                if(page.cursor!=null) item {OutlinedButton(onClick=vm::sources,modifier=Modifier.fillMaxWidth().testTag("investment-sources-more")) {Text("Sumber berikutnya")}}
            }
            state.audit?.let {audit->
                items((0 until audit.getJSONArray("data").length()).map {audit.getJSONArray("data").getJSONObject(it)},key={"audit-${it.getLong("id")}"}) {event->AuditCard(event)}
                if(!audit.isNull("next_cursor")) item {OutlinedButton(onClick=vm::audit,modifier=Modifier.fillMaxWidth().testTag("investment-audit-more")) {Text("Perubahan berikutnya")}}
            }
        } else {
            item {Choice("Riwayat investasi",state.kind,listOf(option(InvestmentContract.SCENARIO,"Skenario dan arus kas"),option(InvestmentContract.REPORT,"Laporan investasi tersimpan")),"investment-kind",vm::filter)}
            item {Button(onClick={vm.newScenario()},enabled=state.greenhouse.isNotBlank(),modifier=Modifier.fillMaxWidth().testTag("investment-new")) {Text("Buat skenario investasi")};if(state.greenhouse.isBlank()) Text("Pilih greenhouse di halaman Keuangan untuk skenario baru.")}
            if(state.records.isEmpty()) item {Text("Belum ada catatan tersimpan pada cakupan ini.")}
            items(state.records,key={it.id}) {row->
                OutlinedCard(onClick={vm.select(row.id)},modifier=Modifier.fillMaxWidth().testTag("investment-record-${row.id}")) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    val data=row.serverBody ?: row.body
                    Text(if(row.kind==InvestmentContract.SCENARIO) data.optString("name").ifBlank {data.optJSONObject("_local_form")?.optString("name").orEmpty().ifBlank {"Draf skenario"}} else data.optJSONObject("scope")?.optJSONObject("scenario")?.optString("name") ?: "Permintaan laporan investasi",style=MaterialTheme.typography.titleMedium)
                    Text(row.syncState.label());if(row.serverVersion!=null) Text("Versi ${row.serverVersion}")
                }}
            }
            if(state.more) item {OutlinedButton(onClick={vm.reload(true)},modifier=Modifier.fillMaxWidth().testTag("investment-history-more")) {Text("Catatan berikutnya")}}
        }
    }
}

private fun LazyListScope.scenarioForm(draft:InvestmentScenarioDraft,state:InvestmentState,vm:InvestmentViewModel,context:CultivationUiState) {
    item {Text(if(draft.baseVersion==null) "Skenario baru" else "Koreksi skenario versi ${draft.baseVersion}",style=MaterialTheme.typography.titleLarge);Text(context.greenhouses.find {it.optString("id")==draft.greenhouseId}?.optString("name") ?: "Greenhouse tersimpan");Text(if(state.saved) "Draf tersimpan di perangkat." else "Perubahan belum selesai disimpan.",modifier=Modifier.testTag("investment-draft-status"))}
    item {Notice("Kosong berarti belum diketahui. Investasi awal dihitung sekali; isi arus kas neto tahunan secara manual beserta sumber dan dasarnya. Nilai sisa atau penyusutan tidak ditambahkan otomatis.")}
    item {FinanceField("Nama skenario",draft.name,{v->vm.editScenario {it.copy(name=v)}},"investment-name",state.errors["name"])}
    item {FinanceField("Tanggal dasar (YYYY-MM-DD)",draft.baseDate,{v->vm.editScenario {it.copy(baseDate=v)}},"investment-base-date",state.errors["base_date"])}
    item {FinanceField("Jumlah tahun (1–100)",draft.horizon,{v->vm.editScenario {it.withHorizon(v)}},"investment-horizon",state.errors["horizon"],hint="Batas teknis. Periode mengikuti ulang tahun tanggal dasar; arus kas pada akhir tahun.")}
    item {FinanceField("Investasi awal / I0 (IDR)",draft.initial,{v->vm.editScenario {it.copy(initial=v)}},"investment-initial",state.errors["initial"])}
    item {SourceChoice("Sumber investasi awal",draft.initialKind,"investment-initial-kind") {v->vm.editScenario {it.copy(initialKind=v)}}}
    item {FinanceField("Referensi investasi awal",draft.initialReference,{v->vm.editScenario {it.copy(initialReference=v)}},"investment-initial-reference")}
    item {FinanceField("Dasar investasi awal",draft.initialBasis,{v->vm.editScenario {it.copy(initialBasis=v)}},"investment-initial-basis",state.errors["initial_source"])}
    item {FinanceField("Diskonto tahunan (%)",draft.discountPercent,{v->vm.editScenario {it.copy(discountPercent=v)}},"investment-discount",state.errors["discount"],hint="Tanpa nilai bawaan. Contoh format desimal: 10,5; ini contoh penulisan, bukan saran tingkat diskonto.")}
    item {Choice("Konvensi waktu pengembalian",draft.convention,listOf(option("","Belum dipilih"),option("interpolation","Interpolasi bagian tahun"),option("end_of_year","Akhir tahun penerimaan")),"investment-convention") {v->vm.editScenario {it.copy(convention=v)}}}
    val count=runCatching {InvestmentInputs.horizon(draft.horizon)}.getOrNull()
    draft.flows.forEachIndexed {index,flow->
        val year=index+1
        item(key="year-heading-$year") {Text("Tahun $year${if(count!=null && year>count) " · di luar horizon, tidak dikirim" else ""}",style=MaterialTheme.typography.titleLarge);state.errors["flow-$year"]?.let {Notice(it,true)}}
        item(key="year-amount-$year") {FinanceField("Kas neto tahun $year (IDR; boleh negatif)",flow.amount,{v->vm.editScenario {d->d.copy(flows=d.flows.mapIndexed {i,f->if(i==index) f.copy(amount=v) else f})}},"investment-flow-$year")}
        item(key="year-kind-$year") {SourceChoice("Sumber tahun $year",flow.kind,"investment-flow-kind-$year") {v->vm.editScenario {d->d.copy(flows=d.flows.mapIndexed {i,f->if(i==index) f.copy(kind=v) else f})}}}
        item(key="year-reference-$year") {FinanceField("Referensi tahun $year",flow.reference,{v->vm.editScenario {d->d.copy(flows=d.flows.mapIndexed {i,f->if(i==index) f.copy(reference=v) else f})}},"investment-flow-reference-$year")}
        item(key="year-basis-$year") {FinanceField("Dasar kas neto tahun $year",flow.basis,{v->vm.editScenario {d->d.copy(flows=d.flows.mapIndexed {i,f->if(i==index) f.copy(basis=v) else f})}},"investment-flow-basis-$year")}
    }
    item {FinanceField("Catatan dan asumsi skenario",draft.notes,{v->vm.editScenario {it.copy(notes=v)}},"investment-notes",state.errors["notes"])}
    if(draft.baseVersion!=null) item {FinanceField("Alasan koreksi",draft.reason,{v->vm.editScenario {it.copy(reason=v)}},"investment-reason",state.errors["reason"])}
    state.errors.filterKeys {it !in setOf("name","base_date","horizon","initial","discount","initial_source","notes","reason") && !it.startsWith("flow-")}.values.forEach {item {Notice(it,true)}}
    item {Button(onClick=vm::queueScenario,modifier=Modifier.fillMaxWidth().testTag("investment-scenario-queue")) {Text("Simpan skenario dan antrekan")}}
    item {OutlinedButton(onClick=vm::back,modifier=Modifier.fillMaxWidth().testTag("investment-save-draft")) {Text("Simpan draf dan kembali")}}
}
private fun LazyListScope.rawScenario(d:InvestmentScenarioDraft) {
    item {Text("Isian lokal",style=MaterialTheme.typography.titleLarge);Text("${d.name} · tanggal ${d.baseDate} · ${d.horizon} tahun");Text("Investasi awal: ${d.initial.ifBlank {"belum diketahui"}} IDR; ${sourceLabel(d.initialKind)}");Text("${d.initialReference}\n${d.initialBasis}");Text("Diskonto: ${d.discountPercent.ifBlank {"belum diketahui"}}%; konvensi: ${d.convention.ifBlank {"belum dipilih"}}");Text("${d.notes}\nAlasan: ${d.reason}")}
    d.flows.forEachIndexed {i,f->item {Text("Tahun ${i+1}: ${f.amount.ifBlank {"belum diketahui"}} IDR · ${sourceLabel(f.kind)}\n${f.reference}\n${f.basis}")}}
}
private fun LazyListScope.scenario(data:JSONObject) {
    item {Text(data.getString("name"),style=MaterialTheme.typography.titleLarge);Text("Versi ${data.getInt("version")} · ${if(data.getString("record_state")=="cancelled") "Dibatalkan" else "Aktif"}");Text("Tanggal dasar ${data.getString("base_date")} · ${data.getInt("horizon_years")} tahun");Text("Investasi awal: ${nullable(data,"initial_outlay")} IDR");val source=data.getJSONObject("initial_source");Text("${sourceLabel(source.optString("kind"))}\n${source.getString("reference")}\n${source.getString("basis")}");Text("Diskonto: ${if(data.isNull("discount_rate")) "belum diketahui" else InvestmentInputs.percentFromRate(data.getString("discount_rate"))+"%"}");Text("Konvensi: ${nullable(data,"payback_convention")}");Text("${data.getString("notes")}\nDicatat ${data.getString("recorded_by_name")}");Notice(if(data.getString("source_provenance")=="declared_complete") "Sumber dan dasar dinyatakan lengkap oleh pengelola; belum merupakan audit bukti usaha." else "Sumber atau dasar belum lengkap.")}
    val flows=data.getJSONArray("cash_flows");repeat(flows.length()) {i->val f=flows.getJSONObject(i);item {Text("Tahun ${f.getInt("year")}: ${nullable(f,"amount")} IDR · ${sourceLabel(f.optString("source_kind"))}\n${f.getString("source_reference")}\n${f.getString("basis")}")}}
}
private fun LazyListScope.report(data:JSONObject,accepted:Boolean) {
    item {val scope=data.getJSONObject("scope");val s=scope.getJSONObject("scenario");Text(scope.getJSONObject("greenhouse").getString("name"),style=MaterialTheme.typography.titleLarge);Text("${s.getString("name")} · skenario versi ${data.getInt("scenario_version")}");Text("Tanggal dasar ${s.getString("base_date")} · ${s.getInt("horizon_years")} tahun");Text("Konvensi: ${nullable(s,"payback_convention")}; diskonto: ${if(s.isNull("discount_rate")) "belum diketahui" else InvestmentInputs.percentFromRate(s.getString("discount_rate"))+"%"}");val summary=data.getJSONObject("source_summary");Text("Sumber: ${summary.getInt("actual")} aktual dinyatakan, ${summary.getInt("projection")} proyeksi, ${summary.getInt("unknown")} belum dinyatakan.");Text("Tinjauan: ${data.getString("review_note")}");if(accepted) Text("Diterbitkan ${timeLabel(data.getString("generated_at"))} oleh ${data.getString("recorded_by_name")}")}
    items(InvestmentContract.labels.keys.toList(),key={"metric-$it"}) {key->InvestmentIndicator(key,data.getJSONObject("metrics").getJSONObject(key))}
    item {val assumptions=data.getJSONArray("assumptions");repeat(assumptions.length()) {Text(assumptions.getString(it))};Text("Versi rumus: ${data.getString("formula_version")}",style=MaterialTheme.typography.bodySmall)}
}
@Composable private fun InvestmentIndicator(key:String,m:JSONObject) {
    var expanded by remember(key,m.toString()) {mutableStateOf(false)}
    OutlinedCard(Modifier.fillMaxWidth().testTag("investment-metric-$key")) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("$key · ${InvestmentContract.labels.getValue(key)}",style=MaterialTheme.typography.titleMedium)
        Text(if(m.isNull("value")) {if(m.getString("status")=="not_recovered") "Belum kembali dalam horizon" else "Belum dapat dihitung"} else investmentValue(m.getString("value"),m.getString("unit"),m.getInt("display_scale")),style=MaterialTheme.typography.titleLarge)
        if(m.getString("status")=="provisional") Text("Hasil sementara; periksa kelengkapan input dan sumber.")
        val reasons=m.getJSONArray("reasons");repeat(reasons.length()) {Text(reasons.getString(it))}
        TextButton(onClick={expanded=!expanded},modifier=Modifier.testTag("investment-detail-$key")) {Text(if(expanded) "Tutup perhitungan" else "Rumus, input dan asumsi")}
        if(expanded) {Text(m.getString("formula"));val input=m.getJSONObject("inputs");Text("Investasi awal: ${nullable(input,"initial_outlay")} IDR");val flows=input.getJSONArray("annual_cash_flows");repeat(flows.length()) {Text("Kas neto tahun ${it+1}: ${if(flows.isNull(it)) "belum diketahui" else flows.getString(it)} IDR")};Text("Diskonto fraksi: ${nullable(input,"discount_rate")}; konvensi: ${nullable(input,"payback_convention")}");val detail=m.getJSONObject("calculation_detail");detail.keys().forEach {name->Text("$name: ${detail.get(name)}",style=MaterialTheme.typography.bodySmall)};m.optJSONObject("exact_fraction")?.let {Text("Pecahan sebelum pembulatan: ${it.getString("numerator")} / ${it.getString("denominator")}")};val assumptions=m.getJSONArray("assumptions");repeat(assumptions.length()) {Text(assumptions.getString(it))}}
    }}
}
@Composable private fun SourceCard(source:JSONObject) {
    val d=source.getJSONObject("data");val year=source.getInt("year")
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(if(year==0) "Investasi awal · tahun 0" else "Kas neto tahun $year",style=MaterialTheme.typography.titleMedium);Text("${nullable(d,"amount")} IDR · ${sourceLabel(d.optString("source_kind"))}");Text(if(year==0) "Tanggal ${d.getString("at_date")}" else "${d.getString("period_start")} sampai ${d.getString("period_end")} · kas akhir periode");Text("Referensi: ${d.getString("source_reference")}\nDasar: ${d.getString("basis")}");Text("Skenario ${source.getString("scenario_id")} · versi ${source.getInt("version")}",style=MaterialTheme.typography.bodySmall)}}
}
@Composable private fun AuditCard(event:JSONObject) {
    var expanded by remember(event.getLong("id")) {mutableStateOf(false)}
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {Text("Versi ${event.getInt("version")} · ${event.getString("actor_name")}");Text(timeLabel(event.getString("server_at")));Text("${event.getString("action")} · ${nullable(event,"reason")}");TextButton(onClick={expanded=!expanded}) {Text("Bandingkan perubahan")};if(expanded) Text(event.getJSONObject("changes").toString(2))}}
}
