package com.inofarm.jogja.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.data.*
import java.time.LocalTime

@Composable internal fun SettingsScreen(state: OwnerState, vm: OwnerViewModel, workers: ()->Unit,
    page: String="hub", navigate: (String)->Unit={}) {
    var draft by rememberSaveable(stateSaver=Saver<ConfigDraft,String>(save={it.serialize()},restore={ConfigDraft.restore(it)})) { mutableStateOf(ConfigDraft.from(state.config?.body)) }
    var checkpoint by rememberSaveable { mutableStateOf(draft.serialize()) }
    var baseVersion by rememberSaveable { mutableStateOf(state.config?.version) }
    var initialized by rememberSaveable { mutableStateOf(state.resource("config").fetchedAt!=null) }
    var pending by rememberSaveable { mutableStateOf<Long?>(null) }
    var phaseEditor by rememberSaveable { mutableStateOf(0) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var emptySchedule by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(state.resource("config").fetchedAt) {
        if(!initialized && state.resource("config").fetchedAt!=null) {
            draft=ConfigDraft.from(state.config?.body);baseVersion=state.config?.version;initialized=true;checkpoint=draft.serialize()
        }
    }
    LaunchedEffect(state.action("config").ackId) {
        if(pending!=null && pending==state.action("config").ackId) {
            baseVersion=state.config?.version;pending=null;localError=null;checkpoint=draft.serialize()
        }
    }
    fun open(target: String) { checkpoint=draft.serialize();localError=null;navigate(target) }
    fun back() { navigate("hub") }
    fun cancel() { if(!state.action("config").running) { draft=ConfigDraft.restore(checkpoint);localError=null;back() } }
    fun save() { runCatching { draft.json() }.onSuccess { localError=null;pending=vm.saveConfig(it,baseVersion) }.onFailure {localError=it.message} }
    BackHandler(page!="hub") { back() }
    androidx.compose.animation.Crossfade(page, animationSpec=androidx.compose.animation.core.tween(FarmMotion.Navigation),label="Settings page") { shownPage ->
    if(shownPage=="hub") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FarmSpace.Page),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PageHeader("Pengaturan")
            ResourceFeedback(state.resource("config"),state.config!=null)
            BotanicalHero(leavesOnly=true) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
                        IconMedallion(FarmGlyph.Leaf,size=44.dp,dark=true)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("Fase aktif tandon",style=MaterialTheme.typography.titleMedium,color=Color.White)
                            Text(state.config?.phase?.wire?.replaceFirstChar {it.uppercase()} ?: "Belum ditetapkan",style=MaterialTheme.typography.headlineSmall,color=Color.White)
                            Text("Pilih fase pertumbuhan tanaman melon pada tandon utama.",style=MaterialTheme.typography.bodyMedium,color=Color.White)
                        }
                    }
                    if(LocalDensity.current.fontScale>1.5f) Column { PlantPhase.entries.forEach {phase ->
                        OutlinedButton({vm.phase(phase)},Modifier.fillMaxWidth(),enabled=!state.action("phase").running,colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White)) {Text(phase.wire.replaceFirstChar {it.uppercase()})}
                    } } else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        PlantPhase.entries.forEach { phase ->
                            Button({vm.phase(phase)},Modifier.weight(1f).heightIn(min=48.dp),enabled=!state.action("phase").running,
                                shape=FarmShape.Control,colors=ButtonDefaults.buttonColors(containerColor=FarmColors.Soft,contentColor=FarmColors.Ink),contentPadding=PaddingValues(8.dp)) {
                                FarmIcon(if(phase==PlantPhase.Vegetatif) FarmGlyph.Leaf else FarmGlyph.Sprout,Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp));Text(phase.wire.replaceFirstChar {it.uppercase()},style=MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Text("Pilih fase langsung disimpan.",style=MaterialTheme.typography.bodySmall,color=Color(0xFFC8DBCA))
                }
            }
            state.action("phase").error?.let {Banner(it,true)}
            Surface(shape=FarmShape.Card) {MenuRow("Kelola pekerja & shift","Atur jadwal pekerja",FarmGlyph.People,workers)}
            Text("Konfigurasi nutrisi",style=MaterialTheme.typography.titleLarge)
            Surface(shape=FarmShape.Card) { Column {
                MenuRow("Jadwal nutrisi",if(draft.schedules.isEmpty()) "Belum diatur · WIB" else "${draft.schedules.size} waktu · WIB",FarmGlyph.Calendar) {open("schedule")}
                HorizontalDivider(Modifier.padding(horizontal=14.dp),color=FarmColors.Divider)
                MenuRow("Rentang PPM per fase",if(draft.ppm.values.all {it.min.isBlank() && it.max.isBlank()}) "Belum diisi" else "Vegetatif · Generatif",FarmGlyph.Flask) {open("ppm")}
            } }
            Text("Monitoring & perangkat",style=MaterialTheme.typography.titleLarge)
            Surface(shape=FarmShape.Card) { Column {
                MenuRow("Ambang sensor",if(draft.sensors.values.all {it.min.isBlank() && it.max.isBlank()}) "4 parameter · belum diisi" else "4 parameter",FarmGlyph.Chip) {open("sensors")}
                HorizontalDivider(Modifier.padding(horizontal=14.dp),color=FarmColors.Divider)
                MenuRow("Usia data & perangkat",if(listOf(draft.sensorAge,draft.stale,draft.cadence,draft.alarm).all {it.isBlank()}) "Belum diatur" else "Seluruh durasi dalam detik",FarmGlyph.Database) {open("device")}
            } }
            Text("Draft dipertahankan saat kembali. Batal di halaman detail mengembalikan perubahan halaman tersebut.",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
            OutlinedButton({draft=ConfigDraft.from(state.config?.body);baseVersion=state.config?.version;localError=null;checkpoint=draft.serialize()},enabled=!state.action("config").running) {Text("Muat ulang draft dari server")}
            TextButton(vm::refresh) {Text("Refresh")}
            TextButton(vm::logout) {Text("Keluar akun")}
            Spacer(Modifier.height(LocalBottomSpace.current))
        }
    } else {
    val title=when(shownPage) {"schedule"->"Jadwal nutrisi";"ppm"->"Rentang PPM";"sensors"->"Ambang sensor";else->"Usia data &\nperangkat"}
    val subtitle=when(shownPage) {"schedule"->"Waktu pemberian nutrisi · WIB";"ppm"->"per fase pertumbuhan";"sensors"->"Atur batas empat parameter.";else->"Seluruh durasi dalam detik"}
    DetailPage(title,subtitle,::back,footer={haze ->
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        (localError ?: state.action("config").error)?.let {Banner(it,true)}
        FormFooter(::save,::cancel,text=if(state.action("config").running) "Menyimpan…" else "Simpan konfigurasi",enabled=initialized && !state.action("config").running,haze=haze)
        }
    }) {
        ResourceFeedback(state.resource("config"),state.config!=null)
        when(shownPage) {
            "schedule" -> {
                Section("Jadwal baru") {
                    val rows=draft.schedules.ifEmpty {if(emptySchedule) listOf(ScheduleDraft()) else emptyList()}
                    rows.forEachIndexed {index,row ->
                        if(index>0) HorizontalDivider(color=FarmColors.Divider)
                        Text("Hari pemberian",style=MaterialTheme.typography.titleMedium)
                        fun update(value: ScheduleDraft) {draft=draft.copy(schedules=draft.schedules.toMutableList().also {if(index>=it.size) it.add(value) else it[index]=value})}
                        DayPicker(row.days) {update(row.copy(days=it))}
                        FarmField("Jam pemberian",row.time,{update(row.copy(time=it))},"HH:mm","WIB",timeError(row.time),KeyboardType.Ascii)
                        TextButton({draft=draft.copy(schedules=draft.schedules.filterIndexed {i,_->i!=index});emptySchedule=false},colors=ButtonDefaults.textButtonColors(contentColor=FarmColors.Error)) {FarmIcon(FarmGlyph.Trash,tint=FarmColors.Error);Spacer(Modifier.width(8.dp));Text("Hapus jadwal")}
                    }
                    OutlinedButton({draft=draft.copy(schedules=draft.schedules+ScheduleDraft())},Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=draft.schedules.size<14,shape=FarmShape.Control) {FarmIcon(FarmGlyph.Plus);Spacer(Modifier.width(8.dp));Text("Tambah jadwal")}
                }
                Section("Toleransi waktu") {
                    FarmField("Toleransi awal (menit)",draft.early,{draft=draft.copy(early=it)},"Opsional",error=integerFieldError(draft.early,0..1440),keyboard=KeyboardType.Number)
                    FarmField("Toleransi akhir (menit)",draft.late,{draft=draft.copy(late=it)},"Opsional",error=integerFieldError(draft.late,0..1440),keyboard=KeyboardType.Number)
                    InfoNote("Jadwal ditentukan oleh owner.")
                }
            }
            "ppm" -> {
                Spacer(Modifier.height(26.dp))
                GlassSegments(listOf("Vegetatif","Generatif"),phaseEditor,listOf(FarmGlyph.Leaf,FarmGlyph.Sprout)) {phaseEditor=it}
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {FarmIcon(FarmGlyph.Info,tint=FarmColors.Warning);Text("Pilihan editor tidak mengubah fase aktif.",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)}
                val key=if(phaseEditor==0) "vegetatif" else "generatif"
                Surface(shape=FarmShape.Card) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        IconMedallion(FarmGlyph.Flask,size=48.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Batas PPM · ${key.replaceFirstChar {it.uppercase()}}",style=MaterialTheme.typography.titleLarge)
                            Text("Atur rentang PPM untuk fase $key.",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)
                        }
                    }
                    BoundEditor(draft.ppm.getValue(key),{draft=draft.copy(ppm=draft.ppm+(key to it))},"ppm",stacked=true,nonnegative=true)
                    Text("Minimum ≤ maksimum.",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)
                    if(draft.ppm.values.any {it.min.isBlank() || it.max.isBlank()}) InfoNote("Parameter belum lengkap\nNilai ditetapkan oleh owner.")
                    HorizontalDivider(color=FarmColors.Divider)
                    Text("PPM catatan tetap diinput melalui ESP32.",style=MaterialTheme.typography.bodyMedium,color=FarmColors.Muted)
                } }
            }
            "sensors" -> {
                metrics.forEachIndexed {index,metric ->
                    Surface(shape=FarmShape.Card) { Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            IconMedallion(listOf(FarmGlyph.Thermometer,FarmGlyph.Drop,FarmGlyph.Room,FarmGlyph.Humidity)[index],size=36.dp)
                            Text(metric.label+if(metric.unit=="pH") "" else " (${metric.unit})",style=MaterialTheme.typography.titleLarge)
                        }
                        BoundEditor(draft.sensors.getValue(metric.key),{draft=draft.copy(sensors=draft.sensors+(metric.key to it))})
                    } }
                }
                InfoNote("Kosong berarti belum dikonfigurasi.")
            }
            "device" -> {
                val labels=listOf("Usia sensor saat catatan","Batas kontak terputus","Interval kirim monitoring","Durasi buzzer pengingat")
                val values=listOf(draft.sensorAge,draft.stale,draft.cadence,draft.alarm)
                val ranges=listOf(0..604800,1..604800,2..86400,1..3600)
                val icons=listOf(FarmGlyph.Chip,FarmGlyph.WifiOff,FarmGlyph.Send,FarmGlyph.Bell)
                labels.forEachIndexed {i,label ->
                    Surface(shape=FarmShape.Card) { Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(LocalDensity.current.fontScale<=1.5f) IconMedallion(icons[i])
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(label,style=MaterialTheme.typography.titleMedium)
                            if(i==0) Text("Maksimum usia sensor saat konfirmasi.",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
                            if(i==1) Text("Batas untuk menandai perangkat terputus.",style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
                            FarmField(label,values[i],{value -> draft=when(i) {0->draft.copy(sensorAge=value);1->draft.copy(stale=value);2->draft.copy(cadence=value);else->draft.copy(alarm=value)}},suffix="detik",error=integerFieldError(values[i],ranges[i]),keyboard=KeyboardType.Number,showLabel=false)
                        }
                    } }
                }
                InfoNote("Tersimpan di server belum berarti diterapkan ESP32.")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("Versi server · ${state.config?.version ?: "—"}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
                    Text("Versi ESP32 · ${state.dashboard?.appliedVersion ?: "—"}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=FarmColors.Muted)
                }
            }
        }
        OutlinedButton({draft=ConfigDraft.from(state.config?.body);baseVersion=state.config?.version;localError=null;checkpoint=draft.serialize()},enabled=!state.action("config").running) {Text("Muat ulang draft dari server")}
    }
    }
    }
}

internal fun timeError(value: String): String? = if(value.isBlank()) null else if(runCatching {require(Regex("\\d{2}:\\d{2}").matches(value));LocalTime.parse(value)}.isFailure) "Jam memakai HH:mm." else null
