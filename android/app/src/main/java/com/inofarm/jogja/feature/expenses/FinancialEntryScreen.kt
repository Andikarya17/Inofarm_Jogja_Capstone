package com.inofarm.jogja.feature.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.RecordKind
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.design.FarmPageTitle
import com.inofarm.jogja.core.design.FarmActionRow
import com.inofarm.jogja.core.design.FarmIcon
import com.inofarm.jogja.core.design.FarmRowDivider
import com.inofarm.jogja.core.design.FarmSheetHeading
import com.inofarm.jogja.core.design.FarmDraftStatus
import com.inofarm.jogja.core.design.FarmFact
import com.inofarm.jogja.core.design.FarmMenuHeader
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FinancialHub(session:FinanceSession,context:CultivationUiState,entry:FinancialEntryViewModel,
    expense:HistoryUiState,expenseVm:HistoryViewModel,allocation:HistoryUiState,allocationVm:HistoryViewModel,queue:List<QueuedOperation>,sales:com.inofarm.jogja.feature.sales.SaleEntryViewModel,saleHistory:HistoryUiState,saleHistoryVm:HistoryViewModel,asset:com.inofarm.jogja.feature.assets.AssetWorkspace,reports:com.inofarm.jogja.feature.reports.SeasonReportWorkspace,investments:com.inofarm.jogja.feature.investments.InvestmentWorkspace,onSync:()->Unit,
    initialKind: String = RecordKind.EXPENSE.wire, linkRevision: Int = 0) {
    var gh by rememberSaveable {mutableStateOf("")}
    var selected by key(linkRevision) { rememberSaveable { mutableStateOf(initialKind) } }
    val greenhouses = context.greenhouses.filter { it.optBoolean("active", true) }
    val canRecord = greenhouses.any { it.optString("id") == gh }
    LaunchedEffect(context.greenhouses,context.lastSyncedAt) {
        if (gh.isNotBlank() && !canRecord && (context.greenhouses.isNotEmpty() || context.lastSyncedAt != null)) gh=""
    }
    val kind=RecordKind.entries.single {it.wire==selected}
    var showTools by rememberSaveable { mutableStateOf(false) }
    key(kind) {
        HistoryScreen(session,context,when(kind) {RecordKind.ASSET -> asset.history;RecordKind.DEPRECIATION -> asset.depreciation;RecordKind.EXPENSE -> expense;RecordKind.SALE -> saleHistory;else -> allocation},when(kind) {RecordKind.ASSET -> asset.historyVm;RecordKind.DEPRECIATION -> asset.depreciationVm;RecordKind.EXPENSE -> expenseVm;RecordKind.SALE -> saleHistoryVm;else -> allocationVm},queue,
            onEdit={record,replace -> if(kind in setOf(RecordKind.ASSET,RecordKind.DEPRECIATION)) {asset.vm.open(record,replace);if(kind==RecordKind.ASSET) asset.historyVm.close() else asset.depreciationVm.close()} else if(kind==RecordKind.SALE) {sales.open(record,replace);saleHistoryVm.close()} else {entry.open(record,replace);if(kind==RecordKind.EXPENSE) expenseVm.close() else allocationVm.close()}},kind=kind,onSync=onSync,
            historyKindPicker={
                Choice("Riwayat keuangan",selected,listOf(option(RecordKind.EXPENSE.wire,"Pembayaran aktual"),option(RecordKind.COST_ALLOCATION.wire,"Alokasi biaya musim"),option(RecordKind.SALE.wire,"Penjualan lunas"),option(RecordKind.ASSET.wire,"Aset"),option(RecordKind.DEPRECIATION.wire,"Alokasi penyusutan")),"expense-history-kind") {selected=it}
            }) {
            item("finance-workspace") {
                FarmMenuHeader("Keuangan", "Pencatatan dan riwayat dalam penugasan Anda.") {
                    FarmPanel(title = "Catatan baru") {
                        Choice("Greenhouse pencatatan",gh,greenhouses,"expense-gh",loading=context.loading) {gh=it}
                        if (!canRecord) Text("Pilih greenhouse untuk mencatat pengeluaran.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(FarmSpace.Small),
                            verticalArrangement=Arrangement.spacedBy(FarmSpace.Small)) {
                            Button(onClick={entry.new(RecordKind.EXPENSE,gh)},enabled=canRecord,shape=FarmShape.Control,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("expense-new")) {Text("Catat pengeluaran")}
                            OutlinedButton(onClick={showTools=true},shape=FarmShape.Control,
                                modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("finance-tools")) {Text("Alat keuangan")}
                        }
                    }
                }
            }
        }
    }
    if (showTools) ModalBottomSheet(
        onDismissRequest = { showTools = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page)) {
            FarmSheetHeading("Alat keuangan", { showTools = false })
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = FarmSpace.Large),
                verticalArrangement = Arrangement.spacedBy(FarmSpace.Large)) {
                if (!canRecord) Notice("Pilih greenhouse pencatatan di Keuangan untuk mencatat transaksi atau aset.")
                FarmSection("Biaya dan alokasi") {
                    FinanceTool("Alokasikan biaya ke musim", "Pilih pembayaran sumber dan kelas biaya tetap atau variabel", FarmIcon.Allocate, "allocation-new", canRecord) {
                        showTools = false; entry.new(RecordKind.COST_ALLOCATION, gh)
                    }
                }
                FarmSection("Investasi awal, aset dan penyusutan") {
                    Column {
                        FinanceTool("Catat perolehan aset", "Pilih pembayaran aset, nilai sisa, dan umur pakai", FarmIcon.Asset, "asset-new", canRecord) {
                            showTools = false; asset.vm.new(gh, com.inofarm.jogja.feature.assets.AssetKind.ASSET)
                        }
                        FarmRowDivider(FarmSpace.Medium * 2 + 24.dp)
                        FinanceTool("Alokasi penyusutan", "Catat beban pemakaian aset per musim", FarmIcon.Depreciation, "depreciation-new", canRecord) {
                            showTools = false; asset.vm.new(gh, com.inofarm.jogja.feature.assets.AssetKind.ALLOCATION)
                        }
                    }
                }
                FarmSection("Penjualan dan penerimaan") {
                    FinanceTool("Catat penjualan lunas", "Pilih rincian panen, kg terjual, harga, dan waktu pelunasan", FarmIcon.Payment, "sale-new", canRecord) {
                        showTools = false; sales.new(gh)
                    }
                }
                FarmSection("Evaluasi") {
                    Column {
                        FinanceTool("Evaluasi musim", "Periksa biaya, penerimaan, laba, rasio dan titik impas", FarmIcon.Report, "report-open") {
                            showTools = false; reports.vm.show(gh)
                        }
                        FarmRowDivider(FarmSpace.Medium * 2 + 24.dp)
                        FinanceTool("Analisis investasi", "Bandingkan investasi awal dan arus kas tahunan aktual atau proyeksi", FarmIcon.Investment, "investment-open") {
                            showTools = false; investments.vm.show(gh)
                        }
                    }
                }
                FarmSection("Pemeliharaan") {
                    com.inofarm.jogja.feature.recovery.RecoveryBackupButton(onOpen = { showTools = false })
                }
                TextButton(onClick = { showTools = false }, modifier = Modifier.fillMaxWidth().testTag("finance-tools-close")) {
                    Text("Kembali ke keuangan")
                }
            }
        }
    }
}

@Composable private fun FinanceTool(label: String, detail: String, icon: FarmIcon, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    FarmActionRow(label, detail, icon, Modifier.testTag(tag).semantics {
        if (!enabled) stateDescription = "Pilih greenhouse pencatatan"
    }, enabled, onClick)
}

@Composable fun FinancialForm(state:FinancialEntryState,context:CultivationUiState,vm:FinancialEntryViewModel) {
    val draft=state.draft ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        FarmPageTitle("${if(draft.baseVersion==null) "Catat" else "Koreksi"} ${draft.kind.label}")
        FarmDraftStatus(state.saved, Modifier.testTag("expense-draft-status"))
        FarmPanel(title = if (draft.kind == RecordKind.EXPENSE) "Nominal pembayaran" else "Nominal alokasi") {
            FinanceField("Nominal IDR (Rp)", draft.amount, { v -> vm.edit { it.copy(amount = v) } },
                "expense-amount", state.errors["amount"], KeyboardType.Decimal,
                hint = "Desimal boleh koma, maksimal 2 angka. Tanpa pemisah ribuan. Kosong bukan nol.")
        }
        FarmSection(title = if (draft.kind == RecordKind.EXPENSE) "Pembayaran" else "Sumber dan musim tujuan") {
        FarmFact("Greenhouse", context.greenhouses.find {it.optString("id")==draft.greenhouseId}?.optString("name") ?: "Greenhouse terakhir tersimpan")
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
        }
        if(draft.kind==RecordKind.EXPENSE) FarmSection(title = "Keterangan") {
            FinanceField("Keterangan pembayaran",draft.notes,{v -> vm.edit {it.copy(notes=v)}},"expense-notes",state.errors["notes"],singleLine=false)
        }
        if(draft.baseVersion!=null) FinanceField("Alasan koreksi",draft.reason,{v -> vm.edit {it.copy(reason=v)}},"expense-reason",state.errors["reason"],singleLine=false)
        state.errors.values.distinct().forEach {Notice(it,true)};state.message?.let {Notice(it,true)}
        Button(onClick=vm::review,enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("expense-review")) {Text("Periksa catatan keuangan")}
        OutlinedButton(onClick=vm::saveAndClose,enabled=!state.busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("expense-save-draft")) {Text("Simpan draf dan kembali")}
    }
}

@Composable fun FinancialReview(draft:FinancialDraft,context:CultivationUiState,busy:Boolean,onBack:()->Unit,onQueue:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        com.inofarm.jogja.core.design.FarmPageTitle("Periksa ${draft.kind.label}")
        FarmPanel(title = "Ringkasan ${draft.kind.label}") { FinancialDraftFacts(draft,context) }
        if(draft.replaceValidationRejected) Notice("Perbaikan ini membuat operasi baru. Isi pengajuan yang ditolak tetap disimpan dalam histori.")
        Notice("Disimpan lokal dan diantrekan dahulu. Nominal belum menjadi transaksi diterima sebelum server mengonfirmasi.")
        Button(onClick=onQueue,enabled=!busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("expense-queue")) {Text("Simpan lokal & antrekan")}
        OutlinedButton(onClick=onBack,enabled=!busy,shape=FarmShape.Control,modifier=Modifier.fillMaxWidth()) {Text("Kembali ke isian")}
    }
}

@Composable private fun FinancialDraftFacts(draft:FinancialDraft,context:CultivationUiState) {
    FarmFact("Nominal", runCatching { moneyLabel(FinancialDraft.money(draft.amount).toPlainString()) }
        .getOrDefault("Belum lengkap: ${draft.amount}"), Modifier.testTag("expense-amount-fact"), prominent = true)
    FarmFact("Greenhouse", context.greenhouses.find {it.optString("id")==draft.greenhouseId}?.optString("name") ?: "Greenhouse terakhir tersimpan")
    if(draft.kind==RecordKind.EXPENSE) {
        Text(context.expenseCategories.find {it.optString("id")==draft.categoryId}?.optString("name") ?: "Kategori tidak tersedia pada daftar acuan terakhir")
        Text(when(draft.paymentKind) {"operating" -> "Pembayaran operasi";"asset_acquisition" -> "Pembelian aset · beban musim melalui penyusutan";else -> "Jenis pembayaran belum dipilih"})
        Text("Pembayaran: ${draft.date} ${draft.time} WIB");Text(draft.notes)
    } else {
        val source=context.expenses.find {it.optString("id")==draft.expenseId}
        FarmFact("Pembayaran sumber", source?.let(::sourceName) ?: "Belum tersedia pada data tersimpan")
        FarmFact("Musim tujuan", context.seasons.find {it.optString("id")==draft.seasonId}?.optString("name") ?: "Belum tersedia")
        FarmFact("Kelas biaya", when(draft.costClass) {"FC" -> "Biaya tetap (FC)";"VC" -> "Biaya variabel (VC)";else -> "Belum dipilih"})
        FarmFact("Dasar alokasi", draft.basis.ifBlank { "Belum diisi" })
        ExpenseBalanceCard(source,context)
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
    else FarmSection("Saldo terakhir tersimpan · versi ${balance.optLong("version")}") {
        FarmFact("Sisa untuk alokasi", moneyLabel(balance.getString("available_amount")),
            Modifier.testTag("expense-remaining-fact").semantics(mergeDescendants = true) {}, prominent = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FarmSpace.Large),
            verticalArrangement = Arrangement.spacedBy(FarmSpace.Small)) {
            Text("Terbayar ${moneyLabel(balance.getString("amount"))}")
            Text("Teralokasi ${moneyLabel(balance.getString("allocated_amount"))}")
        }
        if(!balance.optBoolean("active")) Text("Sumber tidak tersedia untuk alokasi biaya operasi.")
        Text("Saldo server dapat berubah sebelum pengajuan diterima.",style=MaterialTheme.typography.bodySmall)
        Text(context.lastSyncedAt?.let {"Terakhir diperiksa: ${timeLabel(java.time.Instant.ofEpochMilli(it).toString())}"} ?: "Waktu pemeriksaan terakhir belum tersedia.",style=MaterialTheme.typography.bodySmall)
    }
}
