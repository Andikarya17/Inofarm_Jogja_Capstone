package com.inofarm.jogja.feature.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.RecordKind
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.history.*
import org.json.JSONObject
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

internal fun moneyLabel(value:String)=runCatching {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {minimumFractionDigits=2;maximumFractionDigits=2}.format(BigDecimal(value))
}.getOrDefault("Nominal belum lengkap: $value")
private fun option(id:String,name:String)=JSONObject().put("id",id).put("name",name)
private fun sourceName(row:JSONObject)="${moneyLabel(row.optString("amount"))} · ${row.optString("notes").ifBlank {"Pembayaran operasi"}} · ${timeLabel(row.optString("paid_at"))}"

@Composable fun FinancialHub(session:FinanceSession,context:CultivationUiState,entry:FinancialEntryViewModel,
    expense:HistoryUiState,expenseVm:HistoryViewModel,allocation:HistoryUiState,allocationVm:HistoryViewModel,queue:List<QueuedOperation>) {
    var gh by rememberSaveable {mutableStateOf("")};var selected by rememberSaveable {mutableStateOf(RecordKind.EXPENSE.wire)}
    LaunchedEffect(context.greenhouses.map {it.optString("id")}) {if(gh.isNotBlank() && context.greenhouses.none {it.optString("id")==gh}) gh=""}
    val kind=RecordKind.entries.single {it.wire==selected}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Choice("Greenhouse untuk catatan baru",gh,context.greenhouses.filter {it.optBoolean("active",true)},"expense-gh") {gh=it}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={entry.new(RecordKind.EXPENSE,gh)},enabled=gh.isNotBlank(),modifier=Modifier.weight(1f).testTag("expense-new")) {Text("Catat pengeluaran")}
            OutlinedButton(onClick={entry.new(RecordKind.COST_ALLOCATION,gh)},enabled=gh.isNotBlank(),modifier=Modifier.weight(1f).testTag("allocation-new")) {Text("Alokasikan biaya")}
        }
        Choice("Riwayat keuangan",selected,listOf(option(RecordKind.EXPENSE.wire,"Pembayaran aktual"),option(RecordKind.COST_ALLOCATION.wire,"Alokasi biaya musim")),"expense-history-kind") {selected=it}
        Box(Modifier.weight(1f)) {key(kind) {HistoryScreen(session,context,if(kind==RecordKind.EXPENSE) expense else allocation,if(kind==RecordKind.EXPENSE) expenseVm else allocationVm,queue,
            onEdit={record,replace -> entry.open(record,replace);if(kind==RecordKind.EXPENSE) expenseVm.close() else allocationVm.close()},kind=kind)}}
    }
}

@Composable fun FinancialForm(state:FinancialEntryState,context:CultivationUiState,vm:FinancialEntryViewModel) {
    val draft=state.draft ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("${if(draft.baseVersion==null) "Catat" else "Koreksi"} ${draft.kind.label}",style=MaterialTheme.typography.headlineMedium)
        Text(if(state.saved) "Draf tersimpan di perangkat." else "Perubahan belum selesai disimpan.",modifier=Modifier.testTag("expense-draft-status"))
        Text(context.greenhouses.find {it.optString("id")==draft.greenhouseId}?.optString("name") ?: "Greenhouse terakhir tersimpan")
        if(draft.kind==RecordKind.EXPENSE) {
            Choice("Kategori pengeluaran",draft.categoryId,context.expenseCategories.filter {it.optString("greenhouse_id")==draft.greenhouseId && (it.optBoolean("active",true) || it.optString("id")==draft.categoryId)},"expense-category") {v -> vm.edit {it.copy(categoryId=v)}}
            if(draft.lockedParents) Text(if(draft.paymentKind=="asset_acquisition") "Pembelian aset" else "Pembayaran operasi")
            else Choice("Jenis pembayaran",draft.paymentKind,listOf(option("operating","Pembayaran operasi"),option("asset_acquisition","Pembelian aset")),"expense-payment-kind") {v -> vm.edit {it.copy(paymentKind=v)}}
            FinanceField("Tanggal pembayaran (YYYY-MM-DD)",draft.date,{v -> vm.edit {it.copy(date=v)}},"expense-date",state.errors["paid_at"])
            FinanceField("Jam pembayaran WIB (HH:mm)",draft.time,{v -> vm.edit {it.copy(time=v)}},"expense-time",hint="Detik boleh ditulis HH:mm:ss.")
        } else {
            val source=context.expenses.find {it.optString("id")==draft.expenseId}
            if(draft.lockedParents) {
                Text(source?.let(::sourceName) ?: "Pembayaran sumber terakhir tersimpan")
                Text(context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "Musim tujuan terakhir tersimpan")
                Notice("Sumber pembayaran dan musim dipertahankan. Perpindahan memerlukan pembatalan lalu alokasi baru.")
            } else {
                Choice("Pembayaran sumber yang diterima",draft.expenseId,context.expenses.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("record_state")=="active" && it.optString("payment_kind")=="operating"}.map {option(it.getString("id"),sourceName(it))},"allocation-source") {v -> vm.edit {it.copy(expenseId=v)}}
                Choice("Musim tujuan",draft.seasonId,context.seasons.filter {it.optString("greenhouse_id")==draft.greenhouseId && it.optString("status")=="open"},"allocation-season") {v -> vm.edit {it.copy(seasonId=v)}}
            }
            ExpenseBalanceCard(source,context)
            Choice("Kelas biaya",draft.costClass,listOf(option("FC","Biaya tetap (FC)"),option("VC","Biaya variabel (VC)")),"allocation-class") {v -> vm.edit {it.copy(costClass=v)}}
            FinanceField("Dasar alokasi",draft.basis,{v -> vm.edit {it.copy(basis=v)}},"allocation-basis",state.errors["basis"],singleLine=false,hint="Jelaskan dasar nominal untuk musim ini. Tidak dihitung otomatis.")
        }
        FinanceField("Nominal IDR (Rp)",draft.amount,{v -> vm.edit {it.copy(amount=v)}},"expense-amount",state.errors["amount"],KeyboardType.Decimal,hint="Desimal boleh koma, maksimal 2 angka; tanpa pemisah ribuan. Kosong bukan nol.")
        if(draft.kind==RecordKind.EXPENSE) FinanceField("Keterangan pembayaran",draft.notes,{v -> vm.edit {it.copy(notes=v)}},"expense-notes",state.errors["notes"],singleLine=false)
        if(draft.baseVersion!=null) FinanceField("Alasan koreksi",draft.reason,{v -> vm.edit {it.copy(reason=v)}},"expense-reason",state.errors["reason"],singleLine=false)
        state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
        Button(onClick=vm::review,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("expense-review")) {Text("Periksa catatan keuangan")}
        OutlinedButton(onClick=vm::saveAndClose,enabled=!state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("expense-save-draft")) {Text("Simpan draf dan kembali")}
    }
}

@Composable fun FinancialReview(draft:FinancialDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Periksa ${draft.kind.label}",style=MaterialTheme.typography.headlineMedium)
        FinancialDraftFacts(draft,context)
        if(draft.replaceValidationRejected) Notice("Perbaikan ini membuat operasi baru. Isi pengajuan yang ditolak tetap disimpan dalam histori.")
        Notice("Disimpan lokal dan diantrekan dahulu. Nominal belum menjadi transaksi diterima sebelum server mengonfirmasi.")
        Button(onClick=onQueue,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("expense-queue")) {Text("Simpan lokal & antrekan")}
        OutlinedButton(onClick=onBack,enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Kembali ke isian")}
    }
}

@Composable private fun FinancialDraftFacts(draft:FinancialDraft,context:CultivationUiState) {
    Text(context.greenhouses.find {it.optString("id")==draft.greenhouseId}?.optString("name") ?: "Greenhouse terakhir tersimpan")
    Text("Nominal: ${runCatching {moneyLabel(FinancialDraft.money(draft.amount).toPlainString())}.getOrDefault("Belum lengkap: ${draft.amount}")}",modifier=Modifier.testTag("expense-amount-fact"))
    if(draft.kind==RecordKind.EXPENSE) {
        Text(context.expenseCategories.find {it.optString("id")==draft.categoryId}?.optString("name") ?: "Kategori tidak tersedia pada daftar acuan terakhir")
        Text(when(draft.paymentKind) {"operating" -> "Pembayaran operasi";"asset_acquisition" -> "Pembelian aset · beban musim melalui penyusutan";else -> "Jenis pembayaran belum dipilih"})
        Text("Pembayaran: ${draft.date} ${draft.time} WIB");Text(draft.notes)
    } else {
        val source=context.expenses.find {it.optString("id")==draft.expenseId}
        Text("Sumber: ${source?.let(::sourceName) ?: "belum tersedia pada data tersimpan"}")
        Text("Musim: ${context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "belum tersedia"}")
        Text("Kelas: ${when(draft.costClass) {"FC" -> "Biaya tetap (FC)";"VC" -> "Biaya variabel (VC)";else -> "belum dipilih"}}")
        Text("Dasar alokasi: ${draft.basis}");ExpenseBalanceCard(source,context)
    }
    if(draft.reason.isNotBlank()) Text("Alasan koreksi: ${draft.reason}")
}

@Composable fun FinancialFacts(record:LocalRecord,context:CultivationUiState) {
    val kind=RecordKind.entries.single {it.wire==record.kind}
    val draft=runCatching {FinancialDraft.from(record.id,kind,record.greenhouseId,record.body,record.baseVersion)}.getOrNull()
    if(draft==null) {Notice("Isi catatan belum dapat dibaca lengkap. Data tetap disimpan untuk ditinjau.",true);return}
    FinancialDraftFacts(draft,context)
    if(record.body.has("category_name")) Text("Kategori saat diterima: ${record.body.getString("category_name")}")
    if(record.body.has("season_name")) Text("Musim saat diterima: ${record.body.getString("season_name")}")
    record.body.optString("recorded_by_name").takeIf {it.isNotBlank()}?.let {Text("Dicatat oleh: $it")}
    if(kind==RecordKind.EXPENSE && record.body.has("version")) ExpenseBalanceCard(record.body,context)
    if(record.body.optString("record_state")=="cancelled") Notice("Catatan telah dibatalkan.",true)
}

@Composable fun ExpenseBalanceCard(expense:JSONObject?,context:CultivationUiState) {
    val balance=expense?.let {source -> context.expenseBalances.find {it.optString("expense_id")==source.optString("id") && it.optLong("expense_version")==source.optLong("version")}}
    if(balance==null) Notice("Saldo alokasi belum tersedia untuk versi pembayaran ini. Sinkronkan; nilai kosong tidak dianggap nol.")
    else OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Saldo terakhir tersimpan · versi ${balance.optLong("version")}")
        Text("Terbayar: ${moneyLabel(balance.getString("amount"))} · teralokasi: ${moneyLabel(balance.getString("allocated_amount"))}")
        Text("Sisa untuk alokasi: ${moneyLabel(balance.getString("available_amount"))}")
        if(!balance.optBoolean("active")) Text("Sumber tidak tersedia untuk alokasi biaya operasi.")
        Text("Saldo server dapat berubah sebelum pengajuan diterima.",style=MaterialTheme.typography.bodySmall)
        Text(context.lastSyncedAt?.let {"Terakhir diperiksa: ${timeLabel(java.time.Instant.ofEpochMilli(it).toString())}"} ?: "Waktu pemeriksaan terakhir belum tersedia.",style=MaterialTheme.typography.bodySmall)
    }}
}
