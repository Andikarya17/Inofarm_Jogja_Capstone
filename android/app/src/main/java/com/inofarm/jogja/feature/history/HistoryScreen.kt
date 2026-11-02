package com.inofarm.jogja.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.context.CultivationUiState
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

@Composable fun HistoryScreen(session:FinanceSession,context:CultivationUiState,state:HistoryUiState,vm:HistoryViewModel,queue:List<QueuedOperation>,onEdit:(LocalRecord,Boolean)->Unit) {
    when {
        state.comparison!=null -> ConflictReview(state.comparison,session,context,state.busy,state.message,vm::resolve,vm::closeComparison)
        state.selected!=null -> HistoryDetail(state.selected,session,context,state,vm,queue,onEdit)
        else -> LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp),modifier=Modifier.testTag("finance-history-list")) {
            item {Text("Riwayat kegiatan",style=MaterialTheme.typography.headlineMedium);Text("Catatan lokal dan terakhir diterima dalam penugasan saat ini.")}
            item {HistoryFilters(context,state.filter,vm::filter)}
            item {context.message?.let {Text(it,modifier=Modifier.testTag("finance-sync-message"))};state.message?.let {Notice(it,true)}}
            if(state.loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(!state.loading && state.records.isEmpty()) item {Notice("Belum ada catatan yang cocok dengan filter ini.")}
            items(state.records,key={it.id}) {record -> OutlinedCard(onClick={vm.select(record.id)},modifier=Modifier.fillMaxWidth().testTag("finance-record-${record.id}")) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(ActivityType.entries.find {it.wire==record.body.optString("activity_type")}?.label ?: "Draf kegiatan",fontWeight=FontWeight.SemiBold)
                    Text(record.body.optString("notes").ifBlank {"Tanpa keterangan"});Text(record.syncState.label(),color=MaterialTheme.colorScheme.primary)
                    if(record.body.optString("record_state")=="cancelled") Text("Dibatalkan",color=MaterialTheme.colorScheme.error)
                    Text(record.body.optString("occurred_at").takeUnless {it=="null" || it.isBlank()}?.let(::timeLabel) ?: "Draf waktu belum lengkap",style=MaterialTheme.typography.bodySmall)
                }
            } }
            if(state.hasMore) item {OutlinedButton(onClick=vm::more,enabled=!state.loading,modifier=Modifier.fillMaxWidth().testTag("finance-history-more")) {Text("Muat 50 berikutnya")}}
        }
    }
}

@Composable private fun HistoryFilters(context:CultivationUiState,filter:LocalHistoryFilter,onApply:(LocalHistoryFilter)->Unit) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    var gh by rememberSaveable {mutableStateOf(filter.greenhouseId.orEmpty())};var season by rememberSaveable {mutableStateOf("")};var group by rememberSaveable {mutableStateOf(filter.groupId.orEmpty())}
    var type by rememberSaveable {mutableStateOf(filter.type.orEmpty())};var status by rememberSaveable {mutableStateOf(filter.recordState.orEmpty())}
    var from by rememberSaveable {mutableStateOf(filter.from.orEmpty())};var until by rememberSaveable {mutableStateOf(filter.until.orEmpty())};var error by rememberSaveable {mutableStateOf<String?>(null)}
    TextButton(onClick={expanded=!expanded},modifier=Modifier.testTag("finance-history-filters")) {Text(if(expanded) "Tutup filter" else "Filter riwayat")}
    if(expanded) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        fun choices(rows:List<JSONObject>)=listOf(JSONObject().put("id","").put("name","Semua"))+rows
        Choice("Greenhouse",gh,choices(context.greenhouses),"history-gh") {gh=it;season="";group=""}
        Choice("Musim",season,choices(context.seasons.filter {gh.isBlank() || it.optString("greenhouse_id")==gh}),"history-season") {season=it;group=""}
        val groups=context.groups.filter {row -> (season.isBlank() || row.optString("season_id")==season) && (gh.isBlank() || context.seasons.any {it.optString("id")==row.optString("season_id") && it.optString("greenhouse_id")==gh})}
        Choice("Kelompok tanam",group,choices(groups),"history-group") {group=it}
        Choice("Jenis kegiatan",type,choices(ActivityType.entries.map {JSONObject().put("id",it.wire).put("name",it.label)}),"history-type") {type=it}
        Choice("Status catatan",status,choices(listOf(JSONObject().put("id","active").put("name","Aktif"),JSONObject().put("id","cancelled").put("name","Dibatalkan"))),"history-state") {status=it}
        FinanceField("Dari tanggal WIB",from,{from=it},"history-from",hint="YYYY-MM-DD; kosong untuk semua")
        FinanceField("Sampai tanggal WIB",until,{until=it},"history-until",hint="YYYY-MM-DD; termasuk tanggal ini")
        error?.let {Notice(it,true)}
        Button(onClick={
            val valid=runCatching {val start=from.takeIf {it.isNotBlank()}?.let(LocalDate::parse);val end=until.takeIf {it.isNotBlank()}?.let(LocalDate::parse);require(start==null || end==null || !end.isBefore(start))}.isSuccess
            if(!valid) error="Gunakan tanggal YYYY-MM-DD yang sah; tanggal akhir tidak sebelum awal."
            else {error=null;onApply(LocalHistoryFilter(group.takeIf {it.isNotBlank()},type.takeIf {it.isNotBlank()},from.takeIf {it.isNotBlank()},until.takeIf {it.isNotBlank()},status.takeIf {it.isNotBlank()},gh.takeIf {it.isNotBlank()},if(season.isBlank()) null else groups.map {it.getString("id")}.toSet()));expanded=false}
        },modifier=Modifier.fillMaxWidth().testTag("history-apply")) {Text("Terapkan filter")}
        TextButton(onClick={gh="";season="";group="";type="";status="";from="";until="";error=null;onApply(LocalHistoryFilter());expanded=false},modifier=Modifier.fillMaxWidth()) {Text("Hapus semua filter")}
    }
}

@Composable private fun HistoryDetail(record:LocalRecord,session:FinanceSession,context:CultivationUiState,state:HistoryUiState,vm:HistoryViewModel,queue:List<QueuedOperation>,onEdit:(LocalRecord,Boolean)->Unit) {
    var cancelReview by remember {mutableStateOf<LocalRecord?>(null)}
    val pending=state.pending
    val own=record.recorderId().let {it.isBlank() || it==session.scope.accountId}
    ActivityDetail(record,session,context,vm::close,onEdit={onEdit(record,false)}) {
        state.message?.let {Notice(it,true)}
        if(pending.any {it.state=="CONFLICT"}) Button(onClick=vm::compare,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("history-compare")) {Text("Bandingkan lokal dan server")}
        if(pending.any {it.state=="REJECTED" && it.error?.optString("code")=="validation_failed"} && (own || session.role==UserRole.MANAGER)) OutlinedButton(onClick={onEdit(record,true)},modifier=Modifier.fillMaxWidth().testTag("history-fix-rejected")) {Text("Perbaiki catatan yang ditolak")}
        if(pending.any {it.state=="REJECTED" && it.error?.optString("code")=="season_closed"}) {
            Notice("Operasi tertahan karena musim ditutup. Setelah pengelola membuka musim dan data disinkronkan, Anda dapat mencoba operasi yang sama.")
            OutlinedButton(onClick=vm::retryClosed,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("history-retry-closed")) {Text("Tinjau musim & coba kembali")}
        }
        if(session.role==UserRole.MANAGER && record.syncState==SyncState.ACCEPTED && record.body.optString("record_state")!="cancelled") OutlinedButton(onClick={cancelReview=record},enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("history-cancel")) {Text("Batalkan catatan dengan alasan")}
        if(record.serverVersion!=null) {
            OutlinedButton(onClick={vm.audit()},enabled=!state.auditLoading,modifier=Modifier.fillMaxWidth().testTag("history-audit")) {Text(if(state.auditLoading) "Memuat audit…" else "Muat audit server")}
            state.audit?.let {audit ->
                Text("Audit terakhir diperiksa: ${timeLabel(Instant.ofEpochMilli(audit.fetchedAt).toString())}",style=MaterialTheme.typography.bodySmall)
                if(audit.cached) Notice("Audit terakhir tersimpan; pemeriksaan terbaru belum selesai atau koneksi terputus.")
                audit.entries.forEach {entry -> AuditCard(entry,record,session,context)}
                if(audit.cursor!=null) TextButton(onClick={vm.audit(true)},enabled=!state.auditLoading,modifier=Modifier.fillMaxWidth()) {Text("Muat audit berikutnya")}
            }
        }
        state.archives.forEachIndexed {index,body ->
            var expanded by rememberSaveable(record.id,index) {mutableStateOf(false)}
            TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().testTag("history-archive-$index")) {Text("Salinan lokal sebelum penyelesaian ${index+1}")}
            if(expanded) ActivityFacts(record.copy(body=body),session,context)
        }
    }
    cancelReview?.let {reviewed -> ReasonDialog("Batalkan catatan?","Catatan tetap tersimpan dalam histori. Pembatalan baru berlaku setelah diterima server.",state.busy,onDismiss={cancelReview=null},onConfirm={vm.cancel(reviewed,it);cancelReview=null})}
}

@Composable private fun AuditCard(entry:AuditEntry,record:LocalRecord,session:FinanceSession,context:CultivationUiState) {
    var expanded by rememberSaveable(entry.id) {mutableStateOf(false)}
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("${when(entry.action) {"create" -> "Pencatatan";"update" -> "Koreksi";"cancel" -> "Pembatalan";else -> "Perubahan"}} · versi ${entry.version}",fontWeight=FontWeight.SemiBold)
        Text("${entry.actor} · ${timeLabel(entry.at)}");if(entry.reason.isNotBlank()) Text("Alasan: ${entry.reason}")
        TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Tutup perubahan" else "Lihat sebelum dan sesudah")}
        if(expanded) {Text("Sebelum",fontWeight=FontWeight.Bold);entry.before?.let {ActivityFacts(record.copy(body=it,serverVersion=entry.version-1),session,context)} ?: Text("Belum ada catatan.")
            HorizontalDivider();Text("Sesudah",fontWeight=FontWeight.Bold);entry.after?.let {ActivityFacts(record.copy(body=it,serverVersion=entry.version),session,context)}}
    }}
}

@Composable private fun ConflictReview(reviewed:LocalRecord,session:FinanceSession,context:CultivationUiState,busy:Boolean,message:String?,onResolve:(Boolean,String,String)->Unit,onClose:()->Unit) {
    var reason by rememberSaveable(reviewed.id,reviewed.revision,reviewed.serverVersion) {mutableStateOf("")}
    var policy by rememberSaveable(reviewed.id,reviewed.revision,reviewed.serverVersion) {mutableStateOf("")}
    var useServer by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Tinjau konflik versi",style=MaterialTheme.typography.headlineMedium)
        Notice("Bandingkan seluruh isi. Penyelesaian menyimpan salinan lokal sebelumnya; perubahan ke server memerlukan operasi baru.")
        Text("Lokal · berdasarkan versi ${reviewed.baseVersion ?: "belum diterima"}",fontWeight=FontWeight.Bold)
        ActivityFacts(reviewed,session,context)
        HorizontalDivider();Text("Server · versi ${reviewed.serverVersion}",fontWeight=FontWeight.Bold)
        reviewed.serverBody?.let {ActivityFacts(reviewed.copy(body=it),session,context)} ?: Notice("Versi server belum tersedia. Sinkronkan dan buka perbandingan kembali.",true)
        HorizontalDivider()
        FinanceField("Alasan mempertahankan isi lokal",reason,{reason=it},"conflict-reason",singleLine=false)
        Choice("Acuan untuk koreksi",policy,listOf(JSONObject().put("id","preserve").put("name","Pertahankan acuan historis"),JSONObject().put("id","refresh").put("name","Nilai ulang dengan acuan terkini")),"conflict-policy") {policy=it}
        message?.let {Notice(it,true)}
        Button(onClick={onResolve(true,reason,policy)},enabled=!busy && reason.isNotBlank() && policy.isNotBlank() && reviewed.serverBody!=null,modifier=Modifier.fillMaxWidth().testTag("conflict-keep-local")) {Text("Antrekan isi lokal sebagai koreksi baru")}
        OutlinedButton(onClick={useServer=true},enabled=!busy && reviewed.serverBody!=null,modifier=Modifier.fillMaxWidth().testTag("conflict-use-server")) {Text("Gunakan isi server")}
        TextButton(onClick=onClose,enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Kembali tanpa menyelesaikan")}
    }
    if(useServer) AlertDialog(onDismissRequest={useServer=false},title={Text("Gunakan versi server?")},text={Text("Isi server yang ditinjau menjadi catatan aktif di perangkat. Salinan lokal dan operasi lama tetap disimpan dalam histori.")},confirmButton={TextButton(onClick={useServer=false;onResolve(false,"","")},modifier=Modifier.testTag("conflict-confirm-server")) {Text("Gunakan versi server")}},dismissButton={TextButton(onClick={useServer=false}) {Text("Kembali")}})
}

@Composable fun SeasonActions(season:JSONObject,busy:Boolean,message:String?,onAction:(JSONObject,String)->Unit) {
    var reviewed by remember {mutableStateOf<JSONObject?>(null)}
    val open=season.optString("status")=="open"
    Text("Musim ${if(open) "terbuka" else "ditutup"} · versi ${season.optLong("version")}",style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick={reviewed=JSONObject(season.toString())},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("finance-season-transition")) {Text(if(open) "Tutup musim dengan alasan" else "Buka kembali musim")}
    message?.let {Notice(it)}
    reviewed?.let {snapshot -> ReasonDialog(if(snapshot.getString("status")=="open") "Tutup musim?" else "Buka kembali musim?","Alasan dicatat dalam audit. Perubahan berlaku setelah diterima server; antrean lama tetap perlu ditinjau.",busy,onDismiss={reviewed=null},onConfirm={onAction(snapshot,it);reviewed=null})}
}

@Composable private fun ReasonDialog(title:String,description:String,busy:Boolean,onDismiss:()->Unit,onConfirm:(String)->Unit) {
    var reason by rememberSaveable(title) {mutableStateOf("")}
    AlertDialog(onDismissRequest={if(!busy) onDismiss()},title={Text(title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(description);FinanceField("Alasan",reason,{reason=it},"history-action-reason",singleLine=false)
    }},confirmButton={TextButton(onClick={onConfirm(reason)},enabled=!busy && reason.isNotBlank() && reason.length<=1000,modifier=Modifier.testTag("history-action-confirm")) {Text("Simpan & antrekan")}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy) {Text("Kembali")}})
}
