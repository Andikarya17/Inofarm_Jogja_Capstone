package com.inofarm.jogja.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.inofarm.jogja.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkersScreen(state: OwnerState, vm: OwnerViewModel, back: ()->Unit) {
    var name by rememberSaveable { mutableStateOf("") }; var editWorker by rememberSaveable(stateSaver=Saver<Worker?,String>(save={it?.let { w -> JSONObject().put("id",w.id).put("version",w.version).put("name",w.name).put("active",w.active).toString() } ?: ""},restore={if(it.isBlank()) null else ApiModels.worker(JSONObject(it))})) { mutableStateOf<Worker?>(null) }; var workerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var days by rememberSaveable { mutableStateOf(listOf<Int>()) }; var start by rememberSaveable { mutableStateOf("") }; var end by rememberSaveable { mutableStateOf("") }; var startDate by rememberSaveable { mutableStateOf("") }; var endDate by rememberSaveable { mutableStateOf("") }; var editShift by rememberSaveable(stateSaver=Saver<Shift?,String>(save={it?.let { shift -> JSONObject().put("id",shift.id).put("key",shift.key).put("worker",shift.workerId).put("days",JSONArray(shift.days)).put("start",shift.startTime).put("end",shift.endTime).put("date",shift.startDate).put("until",shift.endDate ?: JSONObject.NULL).toString() } ?: ""},restore={if(it.isBlank()) null else JSONObject(it).let { j -> val d=j.getJSONArray("days"); Shift(j.getLong("id"),j.getString("key"),j.getLong("worker"),(0 until d.length()).map { d.getInt(it) },j.getString("start"),j.getString("end"),j.getString("date"),j.nullString("until")) }})) { mutableStateOf<Shift?>(null) }; var workerError by rememberSaveable { mutableStateOf<String?>(null) }; var shiftError by rememberSaveable { mutableStateOf<String?>(null) }
    var workerPending by rememberSaveable { mutableStateOf<Long?>(null) }; var shiftPending by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.action("worker").ackId) { if(workerPending!=null && workerPending==state.action("worker").ackId) { editWorker=null;name="";workerPending=null;workerError=null } }
    LaunchedEffect(state.action("shift").ackId) { if(shiftPending!=null && shiftPending==state.action("shift").ackId) { editShift=null;workerId=null;days=emptyList();start="";end="";startDate="";endDate="";shiftPending=null;shiftError=null } }
    var segment by rememberSaveable { mutableStateOf(0) }
    var workerMenu by remember { mutableStateOf(false) }
    fun clearShift() {editShift=null;workerId=null;days=emptyList();start="";end="";startDate="";endDate="";shiftError=null}
    fun saveShift() {
        runCatching {
            require(workerId!=null && days.isNotEmpty() && days.distinct().size==days.size && days.all {it in 1..7}) {"Pilih pekerja dan hari"}
            require(timeError(start)==null && timeError(end)==null && start.isNotBlank() && end.isNotBlank()) {"Jam memakai HH:mm"}
            require(start!=end) {"Jam mulai dan selesai harus berbeda"}
            require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(startDate)) {"Tanggal memakai YYYY-MM-DD"};LocalDate.parse(startDate)
            if(endDate.isNotBlank()) {require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(endDate)) {"Tanggal memakai YYYY-MM-DD"};LocalDate.parse(endDate);require(endDate>=startDate) {"Tanggal akhir sebelum awal"}}
            JSONObject().put("workerId",workerId).put("days",JSONArray(days)).put("startTime",start).put("endTime",end).put("startDate",startDate).put("endDate",endDate.ifBlank {null} ?: JSONObject.NULL)
        }.onSuccess {shiftError=null;shiftPending=vm.shift(it,editShift)}.onFailure {shiftError=it.message}
    }
    androidx.activity.compose.BackHandler(segment==1) {segment=0}
    val workerNames=remember(state.workers) {state.workers.associateBy(Worker::id)}
    @Composable fun shiftRow(shift: Shift) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${workerNames[shift.workerId]?.name ?: "Pekerja"}: ${shift.startTime}–${shift.endTime} · ${shift.startDate}")
            Text("Berlaku hingga ${shift.endDate ?: "tanpa tanggal akhir"}",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
            Text(shift.days.joinToString {listOf("Sen","Sel","Rab","Kam","Jum","Sab","Min")[it-1]},style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(FarmSpace.Small)) {
                TextButton({editShift=shift;workerId=shift.workerId;days=shift.days;start=shift.startTime;end=shift.endTime;startDate=shift.startDate;endDate=shift.endDate ?: "";segment=1}) {Text("Edit")}
                TextButton({vm.deleteShift(shift)},enabled=!state.action("shift").running) {Text("Nonaktifkan")}
            }
            HorizontalDivider(color=FarmColors.Divider)
        }
    }
    val segments: @Composable ()->Unit = {GlassSegments(listOf("Pekerja","Shift"),segment,listOf(FarmGlyph.People,FarmGlyph.Calendar)) {segment=it}}
    androidx.compose.animation.Crossfade(segment,animationSpec=androidx.compose.animation.core.tween(FarmMotion.Navigation),label="Workers page") { shownSegment ->
    if(shownSegment==0) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(FarmSpace.Page),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item(key="header") {PageHeader("Pekerja & shift",back=back)}
            item(key="segments") {segments()}
            item(key="worker-status") {
                InfoNote("Nama mengikuti jadwal, bukan identitas penekan tombol.",false)
                Text("Daftar pekerja",style=MaterialTheme.typography.titleLarge)
                ResourceFeedback(state.resource("workers"),state.workers.isNotEmpty())
                state.action("worker").error?.let {Banner(it,true)}
                if(state.workers.isEmpty() && !state.resource("workers").loading && state.resource("workers").error==null) {
                    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                        IconMedallion(FarmGlyph.People,size=68.dp);Text("Belum ada pekerja",style=MaterialTheme.typography.titleLarge)
                        Text("Tambahkan pekerja untuk mengatur shift.",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)
                    }
                }
            }
            items(state.workers,key={"worker-${it.id}"},contentType={"worker"}) {worker ->
                Surface(shape=FarmShape.Card) {Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("${worker.name}${if(!worker.active) " · Nonaktif" else ""}",fontWeight=FontWeight.Medium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(FarmSpace.Small)) {
                        TextButton({editWorker=worker;name=worker.name}) {Text("Edit nama")}
                        TextButton({vm.worker(worker.name,worker,!worker.active)},enabled=!state.action("worker").running) {Text(if(worker.active) "Nonaktifkan" else "Aktifkan")}
                    }
                }}
            }
            item(key="worker-editor") {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    FarmField(if(editWorker==null) "Nama pekerja baru" else "Edit nama pekerja",name,{name=it},"Masukkan nama",error=workerError,keyboard=KeyboardType.Text)
                    PrimaryAction(if(state.action("worker").running) "Menyimpan…" else "Simpan pekerja",{
                        if(name.isBlank() || name.length>100) workerError="Isi nama 1–100 karakter" else {workerError=null;workerPending=vm.worker(name,editWorker)}
                    },Modifier.fillMaxWidth(),!state.action("worker").running)
                    if(name.isNotEmpty() || editWorker!=null) TextButton({editWorker=null;name="";workerError=null}) {Text("Batal edit / pekerja baru")}
                }
            }
            item(key="shift-status") {
                Text("Shift terjadwal",style=MaterialTheme.typography.titleLarge)
                ResourceFeedback(state.resource("shifts"),state.shifts.isNotEmpty())
                if(state.shifts.isEmpty() && !state.resource("shifts").loading && state.resource("shifts").error==null) {
                    Text("Belum ada shift",style=MaterialTheme.typography.titleLarge)
                    Text("Zona waktu · Asia/Jakarta",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)
                }
            }
            items(state.shifts,key={"shift-${it.key}"},contentType={"shift"}) {shift ->shiftRow(shift)}
            item(key="shift-action") {
                OutlinedButton({segment=1},Modifier.fillMaxWidth().heightIn(min=48.dp),shape=FarmShape.Navigation) {Text("Atur shift");Spacer(Modifier.width(8.dp));FarmIcon(FarmGlyph.Arrow)}
                state.action("shift").error?.let {Banner(it,true)}
            }
        }
    } else DetailPage("Pekerja & shift",null,back,footer={haze ->
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        (shiftError ?: state.action("shift").error)?.let {Banner(it,true)}
        FormFooter(::saveShift,{clearShift();segment=0},if(state.action("shift").running) "Menyimpan…" else "Simpan shift",
            !state.action("shift").running && workerId!=null && days.isNotEmpty() && start.isNotBlank() && end.isNotBlank() && startDate.isNotBlank(),haze)
        }
    }) {
        segments()
        Section(if(editShift==null) "Tambah shift" else "Edit shift") {
            Text("Zona waktu · Asia/Jakarta",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)
            Text("Pekerja",style=MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton({workerMenu=true},Modifier.fillMaxWidth().heightIn(min=48.dp),shape=FarmShape.Control,border=androidx.compose.foundation.BorderStroke(1.dp,FarmColors.Outline)) {
                    Text(state.workers.find {it.id==workerId}?.name ?: "Pilih pekerja",Modifier.weight(1f),color=if(workerId==null) Color(0xFF919DA9) else FarmColors.Ink)
                    FarmIcon(FarmGlyph.Chevron)
                }
                DropdownMenu(workerMenu,{workerMenu=false}) {
                    state.workers.filter {it.active}.forEach {worker -> DropdownMenuItem({Text(worker.name)},{workerId=worker.id;workerMenu=false})}
                    if(state.workers.none {it.active}) DropdownMenuItem({Text("Tambahkan pekerja aktif dahulu")},{workerMenu=false},enabled=false)
                }
            }
            Text("Hari mulai shift",style=MaterialTheme.typography.titleMedium)
            DayPicker(days.toSet()) {days=it.sorted()}
            if(LocalDensity.current.fontScale>1.5f) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                FarmField("Jam mulai",start,{start=it},"HH:mm","WIB",timeError(start),KeyboardType.Ascii)
                FarmField("Jam selesai",end,{end=it},"HH:mm","WIB",if(end.isNotBlank() && end==start) "Gunakan jam yang berbeda." else timeError(end),KeyboardType.Ascii)
            } else Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                FarmField("Jam mulai",start,{start=it},"HH:mm","WIB",timeError(start),KeyboardType.Ascii,Modifier.weight(1f))
                FarmField("Jam selesai",end,{end=it},"HH:mm","WIB",if(end.isNotBlank() && end==start) "Gunakan jam yang berbeda." else timeError(end),KeyboardType.Ascii,Modifier.weight(1f))
            }
            FarmDateField("Tanggal mulai",startDate,{startDate=it},error=if(startDate.isNotBlank() && runCatching {LocalDate.parse(startDate)}.isFailure) "Tanggal memakai YYYY-MM-DD." else null)
            FarmDateField("Tanggal akhir · opsional",endDate,{endDate=it},error=if(endDate.isNotBlank() && (runCatching {LocalDate.parse(endDate)}.isFailure || (startDate.isNotBlank() && endDate<startDate))) "Periksa tanggal akhir; tidak boleh sebelum tanggal mulai." else null)
            InfoNote("Jam selesai lebih awal berarti keesokan hari.")
            InfoNote("Shift tidak boleh tumpang tindih.")
        }
        if(state.shifts.isNotEmpty()) TextButton({segment=0}) {Text("Kembali ke daftar shift")}
    }
    }
}
