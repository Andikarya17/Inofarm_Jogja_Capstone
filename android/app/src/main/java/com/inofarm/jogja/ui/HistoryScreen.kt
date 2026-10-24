package com.inofarm.jogja.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.data.*
import java.time.LocalDate

private enum class HistoryFilterMode { Today, SevenDays, Custom }

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun HistoryScreen(state: OwnerState, vm: OwnerViewModel, export: () -> Unit) {
    val today=LocalDate.now(java.time.ZoneId.of("Asia/Jakarta"))
    // The chosen control is presentation state, independent of applied query values.
    var filterMode by rememberSaveable { mutableStateOf(
        when {
            state.appliedFilters["from"]==today.toString() && state.appliedFilters["to"]==today.toString() -> HistoryFilterMode.Today
            state.appliedFilters["from"]==today.minusDays(6).toString() && state.appliedFilters["to"]==today.toString() -> HistoryFilterMode.SevenDays
            else -> HistoryFilterMode.Custom
        }
    ) }
    val filtersOpen = filterMode == HistoryFilterMode.Custom
    var from by rememberSaveable { mutableStateOf(state.appliedFilters["from"] ?: "") }; var to by rememberSaveable { mutableStateOf(state.appliedFilters["to"] ?: "") }; var status by rememberSaveable { mutableStateOf(state.appliedFilters["status"] ?: "") }; var error by rememberSaveable { mutableStateOf<String?>(null) }
    val listState=rememberLazyListState()
    var shownWindow by rememberSaveable {mutableStateOf(state.historyWindow)}
    var shownFilters by rememberSaveable {mutableStateOf(state.appliedFilters.toString())}
    LaunchedEffect(state.historyWindow,state.appliedFilters) {
        if(shownWindow!=state.historyWindow || shownFilters!=state.appliedFilters.toString()) listState.scrollToItem(0)
        shownWindow=state.historyWindow;shownFilters=state.appliedFilters.toString()
    }
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(start=FarmSpace.Page,end=FarmSpace.Page,top=FarmSpace.Page,bottom=LocalBottomSpace.current+16.dp),verticalArrangement=Arrangement.spacedBy(FarmSpace.Large)) {
        item(key="title") { PageHeader("Riwayat nutrisi") }
        item(key="filters") { Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            GlassSegments(listOf("Hari ini","7 hari","Filter"),filterMode.ordinal,listOf(FarmGlyph.Calendar,FarmGlyph.History,FarmGlyph.History)) {i ->
                filterMode=HistoryFilterMode.entries[i]
                error=null
                if(filterMode!=HistoryFilterMode.Custom) {
                    from=if(filterMode==HistoryFilterMode.Today) today.toString() else today.minusDays(6).toString()
                    to=today.toString()
                    // A shortcut keeps the applied status, never an unsubmitted draft status.
                    status=state.appliedFilters["status"].orEmpty()
                    vm.filterHistory(from,to,status)
                }
            }
            if (filtersOpen) Column(verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium)) {
            Field("Dari tanggal (YYYY-MM-DD)",from,{from=it},helper="Opsional · waktu sumber terpercaya",error=if(from.isNotBlank() && runCatching { LocalDate.parse(from) }.isFailure) "Tanggal memakai YYYY-MM-DD." else null)
            Field("Sampai tanggal (YYYY-MM-DD)",to,{to=it},error=if(to.isNotBlank() && runCatching { LocalDate.parse(to) }.isFailure) "Tanggal memakai YYYY-MM-DD." else null)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) { FilterChip(status.isEmpty(),{status=""},label={Text("Semua")},modifier=Modifier.heightIn(min=FarmSpace.Touch)); ValidationStatus.entries.forEach { entry -> FilterChip(status==entry.wire,{status=entry.wire},label={Text(entry.label)},modifier=Modifier.heightIn(min=FarmSpace.Touch)) } }
            error?.let { Banner(it,true) }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PrimaryAction("Terapkan",onClick={ error=runCatching { if(from.isNotBlank()) LocalDate.parse(from); if(to.isNotBlank()) LocalDate.parse(to); require(from.isBlank() || to.isBlank() || from<=to) { "Tanggal akhir sebelum awal" } }.exceptionOrNull()?.message; if(error==null) vm.filterHistory(from,to,status) })
                TextButton(onClick={from="";to="";status="";error=null;vm.filterHistory("","","")}) { Text("Reset") }
            }
            }
            if(filtersOpen) Text("Filter diterapkan: ${state.appliedFilters.entries.joinToString { (key,value) -> when(key) { "from" -> "Dari $value"; "to" -> "sampai $value"; else -> ValidationStatus.entries.find { it.wire==value }?.label ?: value } }.ifBlank { "Semua catatan" }}",style=MaterialTheme.typography.bodySmall)
            if(mapOf("from" to from,"to" to to,"status" to status).filterValues { it.isNotBlank() }!=state.appliedFilters) Text("Draft filter belum diterapkan; daftar dan ekspor menggunakan filter diterapkan.",style=MaterialTheme.typography.bodySmall)
        } }
        item(key="status") {
            if(state.hasPreviousHistory) {
                OutlinedButton(vm::previousHistory,Modifier.fillMaxWidth(),enabled=!state.resource("pagination").loading && !state.resource("history").loading) {Text("Halaman sebelumnya")}
                Text("Riwayat ditampilkan per jendela · halaman sebelumnya dapat dimuat kembali",style=MaterialTheme.typography.bodySmall)
            }
            ResourceFeedback(state.resource("history"),state.history.isNotEmpty())
            if(state.history.size>1) Text("${state.history.size} catatan dimuat · urutan penerimaan terbaru",style=MaterialTheme.typography.bodySmall)
            if(state.history.isEmpty() && !state.resource("history").loading && state.resource("history").error==null) Text("Tidak ada catatan pada filter aktif.",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)
        }
        items(state.history,key={it.id},contentType={"record"}) { record ->
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(record.clockTrusted && record.sourceAt!=null) java.time.OffsetDateTime.parse(record.sourceAt).atZoneSameInstant(java.time.ZoneId.of("Asia/Jakarta")).format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy",java.util.Locale.forLanguageTag("id-ID"))) else "Tanggal sumber belum diketahui",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)
                Text("●  ${clockTime(record.sourceAt)}",style=MaterialTheme.typography.bodyMedium,color=green)
                Surface(shape=FarmShape.Card,color=FarmColors.Surface,modifier=Modifier.testTag("record-${record.id}")) {
                    Column(Modifier.padding(14.dp)) {RecordCard(record)}
                }
            }
        }
        item(key="export") {
            PrimaryAction("Ekspor riwayat",export,Modifier.fillMaxWidth(),!state.action("export").running && !state.resource("history").loading)
            Text("Filter diterapkan · maksimum 1000 catatan",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
            if(state.action("export").running) LoadingBar()
            state.action("export").error?.let {Banner(it,true)}
        }
        item(key="pagination") {
            ResourceFeedback(state.resource("pagination"),state.history.isNotEmpty())
            if(state.nextCursor!=null) OutlinedButton(shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch),onClick=vm::more,enabled=!state.resource("pagination").loading && !state.resource("history").loading) { Text(if(state.resource("pagination").loading) "Memuat berikutnya…" else "Muat berikutnya") }
            else if(state.history.isNotEmpty() && !state.resource("history").lastKnown) Text(if(!state.hasPreviousHistory) "Seluruh halaman pada filter ini sudah dimuat." else "Akhir riwayat; halaman sebelumnya tetap tersedia.",style=MaterialTheme.typography.bodySmall)
            else if(state.resource("history").lastKnown) Text("Cache terbatas; refresh untuk memeriksa halaman server.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
