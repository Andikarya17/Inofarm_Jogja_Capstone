package com.inofarm.jogja.feature.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.core.contracts.ReviewableRejections
import com.inofarm.jogja.feature.harvest.HarvestFacts
import com.inofarm.jogja.feature.expenses.FinancialFacts
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.design.FarmMotion
import com.inofarm.jogja.core.design.FarmAmount
import com.inofarm.jogja.core.design.FarmMenuHeader
import com.inofarm.jogja.core.design.FarmMenuList
import com.inofarm.jogja.core.design.FarmPanel
import com.inofarm.jogja.core.design.FarmRecordCard
import com.inofarm.jogja.core.design.FarmShape
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.FarmSection
import com.inofarm.jogja.core.design.FarmSheetHeading
import com.inofarm.jogja.core.design.LocalFarmDialogPicker
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.*
import com.inofarm.jogja.feature.activities.domain.ActivityType
import com.inofarm.jogja.feature.assets.AssetDraft
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.expenses.FinancialDraft
import com.inofarm.jogja.feature.expenses.moneyLabel
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

@Composable fun HistoryScreen(session:FinanceSession,context:CultivationUiState,state:HistoryUiState,vm:HistoryViewModel,queue:List<QueuedOperation>,onEdit:(LocalRecord,Boolean)->Unit,kind:OperationalKind=OperationalKind.ACTIVITY,
    returnToQueue:Boolean=false, onRecord:(()->Unit)?=null, recordLabel:String="", recordScope:String?=null, onSync:(()->Unit)?=null,
    historyKindPicker:(@Composable ()->Unit)?=null, header:LazyListScope.()->Unit={}) {
    when {
        state.comparison!=null -> ConflictReview(state.comparison,session,context,state.busy,state.message,vm::resolve,vm::closeComparison,kind)
        state.selected!=null -> HistoryDetail(state.selected,session,context,state,vm,queue,onEdit,kind,returnToQueue)
        else -> FarmMenuList(Modifier.testTag("finance-history-list")) {
            if (!kind.financial) item("history-heading") {
                FarmMenuHeader(kind.label.replaceFirstChar { it.uppercaseChar() }, "Riwayat dalam penugasan akun ini.")
            }
            header()
            item("history-filters") {
                FarmRecordCard {
                    if (historyKindPicker != null) Text("Riwayat", Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium)
                    historyKindPicker?.invoke()
                    Text(historyScope(context,state,kind),style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(FarmSpace.Small),
                        verticalArrangement=Arrangement.spacedBy(FarmSpace.Small)) {
                        HistoryFilters(context,state.filter,state.filterSeason,vm::filter,kind)
                        if (onRecord != null) FilledTonalButton(onClick=onRecord,shape=FarmShape.Control,
                            modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("history-new")) { Text(recordLabel) }
                    }
                    recordScope?.let {
                        Text("Catatan baru · $it",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (session.expired()) item("history-session") { ExpiredSessionNotice() }
            else if (context.message != null || context.lastSyncedAt != null) item("history-sync") {
                FarmRecordCard {
                    context.message?.let { Text(it, Modifier.testTag("finance-sync-message"),
                        style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    context.lastSyncedAt?.let {
                        Text("Sinkron terakhir ${timeLabel(Instant.ofEpochMilli(it).toString())}",
                            style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.message?.let { item("history-message") { Notice(it,true) } }
            val filtered = state.filter != LocalHistoryFilter()
            when {
                state.loading || !state.loaded && state.loadError == null -> item("history-loading") {
                    FarmRecordCard { Text("Memuat riwayat…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
                state.loadError != null -> item("history-error") {
                    FarmPanel(title="Riwayat belum dapat dimuat") {
                        Text(state.loadError)
                        OutlinedButton(onClick=vm::reload,shape=FarmShape.Control,
                            modifier=Modifier.heightIn(min=FarmSpace.Touch)) { Text("Muat ulang riwayat") }
                    }
                }
                state.records.isEmpty() -> item("history-empty") {
                    FarmPanel(title=if (filtered) "Belum ada catatan yang cocok dengan filter ini."
                        else "Belum ada catatan ${kind.label} dalam riwayat akun ini.") {
                        if (filtered) TextButton(onClick={vm.filter(LocalHistoryFilter())},
                            modifier=Modifier.heightIn(min=FarmSpace.Touch)) { Text("Hapus filter") }
                        else if (onSync != null && !session.expired()) TextButton(onClick=onSync,enabled=!context.loading,
                            modifier=Modifier.heightIn(min=FarmSpace.Touch)) { Text("Sinkronkan riwayat") }
                    }
                }
            }
            items(state.records, key = { it.id }) { record ->
                HistoryRecordRow(record, kind, context) { vm.select(record.id) }
            }
            if(state.hasMore) item {
                Button(onClick=vm::more,enabled=!state.loading,shape=FarmShape.Control,
                    modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("finance-history-more")) {Text("Muat 50 berikutnya")}
            }
        }
    }
}

@Composable
private fun HistoryRecordRow(record: LocalRecord, kind: OperationalKind, context: CultivationUiState, onOpen: () -> Unit) {
    val data = record.body
    val local = data.optJSONObject("_local_form")
    val facts = local ?: data
    val title = if (kind == OperationalKind.ACTIVITY) {
        ActivityType.entries.find { it.wire == data.optString("activity_type") }?.label ?: "Draf kegiatan"
    } else if (kind == OperationalKind.ASSET) facts.optString("name").ifBlank { "Aset" }
    else kind.label.replaceFirstChar { it.uppercaseChar() }
    val time = listOf("occurred_at", "paid_at", "created_at").firstNotNullOfOrNull { key ->
        data.optString(key).takeUnless { data.isNull(key) || it.isBlank() }
    }?.let(::timeLabel)
    val greenhouse = data.optString("greenhouse_name").takeUnless { data.isNull("greenhouse_name") || it.isBlank() }
        ?: context.greenhouses.find { it.optString("id") == record.greenhouseId }?.optString("name")
    val notes = listOf("notes", "allocation_basis").firstNotNullOfOrNull { key ->
        data.optString(key).takeUnless { data.isNull(key) || it.isBlank() }
    }
    val groupId = local?.optString("group_id") ?: data.optString("planting_group_id")
    val group = context.groups.find { it.optString("id") == groupId }?.optString("name")
    val amountKey = when (kind) {
        OperationalKind.SALE -> "total_amount"
        OperationalKind.ASSET -> "cost"
        else -> "amount"
    }
    FarmRecordCard(Modifier.testTag("finance-record-${record.id}"), onOpen) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        val amount = facts.optString(amountKey).takeUnless { facts.isNull(amountKey) || it.isBlank() }
        if (kind.financial && amount != null) {
            val displayAmount = if (local == null) amount else runCatching {
                when (kind) {
                    OperationalKind.ASSET, OperationalKind.DEPRECIATION -> AssetDraft.money(amount)
                    else -> FinancialDraft.money(amount)
                }.toPlainString()
            }.getOrNull()
            if (displayAmount != null) FarmAmount(moneyLabel(displayAmount))
            else Text("Nominal draf belum valid",style=MaterialTheme.typography.bodySmall)
        }
        if (kind == OperationalKind.HARVEST) facts.optJSONArray("items")?.let { grades ->
            repeat(minOf(grades.length(), 2)) { index ->
                grades.optJSONObject(index)?.let { line ->
                    val name = line.optString("grade_name").ifBlank {
                        context.grades.find { it.optString("id") == line.optString("grade_id") }
                            ?.optString("name") ?: "Grade belum tersedia"
                    }
                    val count = line.optString("fruit_count").takeUnless { line.isNull("fruit_count") || it.isBlank() }
                    val weight = line.optString("weight_kg").takeUnless { line.isNull("weight_kg") || it.isBlank() }
                        ?.let { it.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()?.replace('.', ',') ?: it }
                    Text("$name · ${count?.let { "$it buah" } ?: "Jumlah belum diisi"} · ${weight?.let { "$it kg" } ?: "Bobot belum diisi"}",
                        style=MaterialTheme.typography.bodyMedium)
                }
            }
            if (grades.length() > 2) Text("${grades.length() - 2} grade lainnya di detail",style=MaterialTheme.typography.bodySmall)
        }
        if (notes != null) Text(notes, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(group, greenhouse, time ?: "Waktu belum tersedia").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RecordStatus(record.syncState)
        if (data.optString("record_state") == "cancelled") Text("Dibatalkan", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium)
        if (kind == OperationalKind.ACTIVITY && (local?.optBoolean("skipped") ?: (data.optString("work_state") == "skipped")))
            Text("Kegiatan dilewati", style = MaterialTheme.typography.labelMedium)
    }
}

private fun historyScope(context:CultivationUiState,state:HistoryUiState,kind:OperationalKind):String {
    val filter=state.filter
    fun name(rows:List<JSONObject>,id:String?,fallback:String) =
        id?.let { rows.find { row -> row.optString("id")==it }?.optString("name") ?: fallback }
    val season=name(context.seasons,state.filterSeason,"Musim terpilih")
    val greenhouse=name(context.greenhouses,filter.greenhouseId,"Greenhouse terpilih")
    val group=name(context.groups,filter.groupId,"Kelompok terpilih")
    val period=listOfNotNull(filter.from?.let { "Dari $it" },filter.until?.let { "Sampai $it" })
        .joinToString(" · ").takeIf { it.isNotEmpty() }?.plus(" WIB")
    val type=ActivityType.entries.find { it.wire==filter.type }?.label
    val status=when(filter.recordState) {"active" -> "Aktif"; "cancelled" -> "Dibatalkan"; else -> null}
    val scope=listOfNotNull(greenhouse,season,group,type,status,period).joinToString(" · ")
    if(scope.isBlank()) return "Riwayat semua greenhouse dalam penugasan"
    return when {
        season!=null && kind==OperationalKind.EXPENSE -> "Pembayaran yang dialokasikan · $scope"
        season!=null && kind==OperationalKind.ASSET -> "Aset dengan alokasi penyusutan · $scope"
        else -> scope
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HistoryFilters(context:CultivationUiState,filter:LocalHistoryFilter,filterSeason:String?,onApply:(LocalHistoryFilter,String?)->Unit,kind:OperationalKind) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    var gh by rememberSaveable {mutableStateOf(filter.greenhouseId.orEmpty())};var season by rememberSaveable {mutableStateOf(filter.seasonId.orEmpty())};var group by rememberSaveable {mutableStateOf(filter.groupId.orEmpty())}
    var type by rememberSaveable {mutableStateOf(filter.type.orEmpty())};var status by rememberSaveable {mutableStateOf(filter.recordState.orEmpty())}
    var from by rememberSaveable {mutableStateOf(filter.from.orEmpty())};var until by rememberSaveable {mutableStateOf(filter.until.orEmpty())};var error by rememberSaveable {mutableStateOf<String?>(null)}
    OutlinedButton(onClick={
        gh=filter.greenhouseId.orEmpty();season=filterSeason.orEmpty();group=filter.groupId.orEmpty()
        type=filter.type.orEmpty();status=filter.recordState.orEmpty()
        from=filter.from.orEmpty();until=filter.until.orEmpty();error=null;expanded=true
    },shape=FarmShape.Control,modifier=Modifier.heightIn(min=FarmSpace.Touch).testTag("finance-history-filters")) {
        Text(if (filter == LocalHistoryFilter()) "Filter riwayat" else "Ubah filter")
    }
    if(expanded) CompositionLocalProvider(LocalFarmDialogPicker provides true) { ModalBottomSheet(onDismissRequest={expanded=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.surface, contentWindowInsets = { WindowInsets.safeDrawing }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FarmSpace.Page)) {
        FarmSheetHeading("Filter riwayat", { expanded = false })
        Column(Modifier.weight(1f, fill = false).imePadding().verticalScroll(rememberScrollState()).padding(bottom = FarmSpace.Large),
            verticalArrangement=Arrangement.spacedBy(FarmSpace.Medium)) {
        fun choices(rows:List<JSONObject>)=listOf(JSONObject().put("id","").put("name","Semua"))+rows
        val groups=context.groups.filter {row -> (season.isBlank() || row.optString("season_id")==season) && (gh.isBlank() || context.seasons.any {it.optString("id")==row.optString("season_id") && it.optString("greenhouse_id")==gh})}
        FarmSection("Cakupan catatan") {
            Choice("Greenhouse",gh,choices(context.greenhouses),"history-gh") {gh=it;season="";group=""}
            Choice("Musim",season,choices(context.seasons.filter {gh.isBlank() || it.optString("greenhouse_id")==gh}),"history-season") {season=it;group=""}
            if(!kind.financial) Choice("Kelompok tanam",group,choices(groups),"history-group") {group=it}
        }
        FarmSection(if (kind == OperationalKind.ACTIVITY) "Jenis dan status" else "Status") {
            if(kind==OperationalKind.ACTIVITY) Choice("Jenis kegiatan",type,choices(ActivityType.entries.map {JSONObject().put("id",it.wire).put("name",it.label)}),"history-type") {type=it}
            Choice("Status catatan",status,choices(listOf(JSONObject().put("id","active").put("name","Aktif"),JSONObject().put("id","cancelled").put("name","Dibatalkan"))),"history-state") {status=it}
        }
        FarmSection("Periode WIB") {
            FinanceField("Dari tanggal WIB",from,{from=it},"history-from",hint="YYYY-MM-DD; kosong untuk semua")
            FinanceField("Sampai tanggal WIB",until,{until=it},"history-until",hint="YYYY-MM-DD; termasuk tanggal ini")
        }
        error?.let {Notice(it,true)}
        Button(onClick={
            val valid=runCatching {val start=from.takeIf {it.isNotBlank()}?.let(LocalDate::parse);val end=until.takeIf {it.isNotBlank()}?.let(LocalDate::parse);require(start==null || end==null || !end.isBefore(start))}.isSuccess
            if(!valid) error="Gunakan tanggal YYYY-MM-DD yang sah; tanggal akhir tidak sebelum awal."
            else {
                val selectedSeason = season.takeIf { it.isNotBlank() }
                onApply(LocalHistoryFilter(
                    groupId = group.takeIf { !kind.financial && it.isNotBlank() },
                    type = type.takeIf { kind == OperationalKind.ACTIVITY && it.isNotBlank() },
                    from = from.takeIf { it.isNotBlank() },
                    until = until.takeIf { it.isNotBlank() },
                    recordState = status.takeIf { it.isNotBlank() },
                    greenhouseId = gh.takeIf { it.isNotBlank() },
                    groups = if (!kind.financial && selectedSeason != null) groups.map { it.getString("id") }.toSet() else null,
                    seasonId = selectedSeason.takeIf { kind.financial }
                ), selectedSeason)
                expanded=false
            }
        },shape=FarmShape.Control,modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch).testTag("history-apply")) {Text("Terapkan filter")}
        TextButton(onClick={onApply(LocalHistoryFilter(),null);expanded=false},
            modifier=Modifier.fillMaxWidth().heightIn(min=FarmSpace.Touch)) {Text("Hapus semua filter")}
        }
        }
    } }
}

@Composable private fun HistoryDetail(record:LocalRecord,session:FinanceSession,context:CultivationUiState,state:HistoryUiState,vm:HistoryViewModel,queue:List<QueuedOperation>,onEdit:(LocalRecord,Boolean)->Unit,kind:OperationalKind,returnToQueue:Boolean) {
    var cancelReview by remember {mutableStateOf<LocalRecord?>(null)}
    val pending=state.pending
    val reviewable=pending.isNotEmpty() && pending.all {it.state=="REJECTED" && ReviewableRejections.mayReplace(kind.wire,it.error?.optString("code"))}
    val own=record.recorderId().let {it.isBlank() || it==session.scope.accountId}
    RecordDetail(record,session,context,vm::close,onEdit={onEdit(record,reviewable)},kind=kind,returnToQueue=returnToQueue) {
        if (kind == OperationalKind.ACTIVITY) com.inofarm.jogja.feature.verification.ActivityVerificationFeedback(session, record)
        if (kind == OperationalKind.HARVEST) com.inofarm.jogja.feature.verification.ActivityVerificationFeedback(
            session, record, com.inofarm.jogja.feature.verification.ReviewKind.HARVEST)
        val attachments=com.inofarm.jogja.feature.attachments.LocalAttachmentAction.current
        if(attachments!=null && kind in setOf(OperationalKind.ACTIVITY,OperationalKind.HARVEST,OperationalKind.EXPENSE) && record.serverVersion!=null) OutlinedButton(onClick={attachments(record)},modifier=Modifier.fillMaxWidth().testTag("history-attachments")) {Text("Lampiran dan bukti catatan")}
        state.message?.let {Notice(it,true)}
        pending.forEach {op ->
            var expanded by rememberSaveable(op.id) {mutableStateOf(false)}
            TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().testTag("history-operation-${op.id}")) {Text(if(expanded) "Tutup isi pengajuan" else "Lihat isi pengajuan yang tersimpan")}
            if(expanded) {
                val action=op.envelope.optString("action");val payload=op.envelope.optJSONObject("payload")
                if(payload==null) Notice("Isi pengajuan belum dapat dibaca lengkap. Data tetap disimpan untuk ditinjau.",true)
                else {
                    if(action in setOf("create","update")) RecordFacts(record.copy(body=payload,baseVersion=if(op.envelope.isNull("base_version")) null else op.envelope.getLong("base_version")),session,context,kind)
                    if(payload.has("reason")) Text("Alasan pengajuan: ${payload.optString("reason")}")
                }
                Notice("Isi pengajuan ini tetap saat dicoba ulang. Edit lokal yang lebih baru ditampilkan terpisah pada detail di atas.")
            }
        }
        if(pending.any {it.state=="CONFLICT"}) Button(onClick=vm::compare,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("history-compare")) {Text("Bandingkan lokal dan server")}
        if(reviewable && (own || session.role==UserRole.MANAGER)) OutlinedButton(onClick={onEdit(record,true)},modifier=Modifier.fillMaxWidth().testTag("history-fix-rejected")) {Text("Perbaiki catatan yang ditolak")}
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
                audit.entries.forEach {entry -> AuditCard(entry,record,session,context,kind)}
                if(audit.cursor!=null) TextButton(onClick={vm.audit(true)},enabled=!state.auditLoading,modifier=Modifier.fillMaxWidth()) {Text("Muat audit berikutnya")}
            }
        }
        state.archives.forEachIndexed {index,body ->
            var expanded by rememberSaveable(record.id,index) {mutableStateOf(false)}
            TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().testTag("history-archive-$index")) {Text("Salinan lokal sebelum penyelesaian ${index+1}")}
            if(expanded) RecordFacts(record.copy(body=body),session,context,kind)
        }
    }
    cancelReview?.let {reviewed -> ReasonDialog("Batalkan catatan?","Catatan tetap tersimpan dalam histori. Pembatalan baru berlaku setelah diterima server.",state.busy,onDismiss={cancelReview=null},onConfirm={vm.cancel(reviewed,it);cancelReview=null})}
}

@Composable private fun AuditCard(entry:AuditEntry,record:LocalRecord,session:FinanceSession,context:CultivationUiState,kind:OperationalKind) {
    var expanded by rememberSaveable(entry.id) {mutableStateOf(false)}
    OutlinedCard(Modifier.fillMaxWidth().animateContentSize(tween(FarmMotion.State))) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("${when(entry.action) {"create" -> "Pencatatan";"update" -> "Koreksi";"cancel" -> "Pembatalan";else -> "Perubahan"}} · versi ${entry.version}",fontWeight=FontWeight.SemiBold)
        Text("${entry.actor} · ${timeLabel(entry.at)}");if(entry.reason.isNotBlank()) Text("Alasan: ${entry.reason}")
        TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Tutup perubahan" else "Lihat sebelum dan sesudah")}
        if(expanded) {Text("Sebelum",fontWeight=FontWeight.Bold);entry.before?.let {RecordFacts(record.copy(body=it,serverVersion=entry.version-1),session,context,kind)} ?: Text("Belum ada catatan.")
            HorizontalDivider();Text("Sesudah",fontWeight=FontWeight.Bold);entry.after?.let {RecordFacts(record.copy(body=it,serverVersion=entry.version),session,context,kind)}}
    }}
}

@Composable private fun ConflictReview(reviewed:LocalRecord,session:FinanceSession,context:CultivationUiState,busy:Boolean,message:String?,onResolve:(Boolean,String,String)->Unit,onClose:()->Unit,kind:OperationalKind) {
    var reason by rememberSaveable(reviewed.id,reviewed.revision,reviewed.serverVersion) {mutableStateOf("")}
    var policy by rememberSaveable(reviewed.id,reviewed.revision,reviewed.serverVersion) {mutableStateOf("")}
    var useServer by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        com.inofarm.jogja.core.design.FarmPageTitle("Tinjau konflik versi")
        Notice("Bandingkan seluruh isi. Penyelesaian menyimpan salinan lokal sebelumnya; perubahan ke server memerlukan operasi baru.")
        FarmPanel(title = "Lokal · versi ${reviewed.baseVersion ?: "belum diterima"}") {
            RecordFacts(reviewed,session,context,kind)
        }
        FarmPanel(title = "Server · versi ${reviewed.serverVersion ?: "belum tersedia"}") {
            reviewed.serverBody?.let {RecordFacts(reviewed.copy(body=it),session,context,kind)} ?: Notice("Versi server belum tersedia. Sinkronkan dan buka perbandingan kembali.",true)
        }
        FinanceField("Alasan mempertahankan isi lokal",reason,{reason=it},"conflict-reason",singleLine=false)
        if(!kind.financial) Choice("Acuan untuk koreksi",policy,listOf(JSONObject().put("id","preserve").put("name","Pertahankan acuan historis"),JSONObject().put("id","refresh").put("name","Nilai ulang dengan acuan terkini")),"conflict-policy") {policy=it}
        message?.let {Notice(it,true)}
        Button(onClick={onResolve(true,reason,policy)},enabled=!busy && reason.isNotBlank() && (kind.financial || policy.isNotBlank()) && reviewed.serverBody!=null,modifier=Modifier.fillMaxWidth().testTag("conflict-keep-local")) {Text("Antrekan isi lokal sebagai koreksi baru")}
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

@Composable internal fun ReasonDialog(title:String,description:String,busy:Boolean,onDismiss:()->Unit,onConfirm:(String)->Unit) {
    var reason by rememberSaveable(title) {mutableStateOf("")}
    AlertDialog(onDismissRequest={if(!busy) onDismiss()},title={Text(title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(description);FinanceField("Alasan",reason,{reason=it},"history-action-reason",singleLine=false)
    }},confirmButton={TextButton(onClick={onConfirm(reason)},enabled=!busy && reason.isNotBlank() && reason.length<=1000,modifier=Modifier.testTag("history-action-confirm")) {Text("Simpan & antrekan")}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy) {Text("Kembali")}})
}

@Composable internal fun RecordFacts(record:LocalRecord,session:FinanceSession,context:CultivationUiState,kind:OperationalKind) {
    when(kind) {
        OperationalKind.ACTIVITY -> ActivityFacts(record,session,context)
        OperationalKind.HARVEST -> HarvestFacts(record,session,context)
        OperationalKind.EXPENSE,OperationalKind.COST_ALLOCATION -> FinancialFacts(record,context)
        OperationalKind.ASSET,OperationalKind.DEPRECIATION -> com.inofarm.jogja.feature.assets.AssetFacts(record,context)
        OperationalKind.SALE -> com.inofarm.jogja.feature.sales.SaleFacts(record,context)
    }
}
