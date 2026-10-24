package com.inofarm.jogja.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.time.Instant

 data class ResourceState(val loading: Boolean = false, val error: String? = null, val fetchedAt: String? = null, val lastKnown: Boolean = false)
 data class ActionState(val running: Boolean = false, val error: String? = null, val ackId: Long? = null)
 data class OwnerState(val signedIn: Boolean = false, val loading: Boolean = false, val error: String? = null, val notice: String? = null, val lastKnown: Boolean = false, val dashboard: Dashboard? = null, val config: RemoteConfig? = null, val history: List<NutrientRecord> = emptyList(), val nextCursor: String? = null, val telemetry: List<Telemetry> = emptyList(), val workers: List<Worker> = emptyList(), val shifts: List<Shift> = emptyList(), val resources: Map<String,ResourceState> = emptyMap(), val actions: Map<String,ActionState> = emptyMap(), val appliedFilters: Map<String,String> = emptyMap(), val historyVersion: Long = 0, val exportReady: Boolean = false, val sessionId: Long = 0, val restoring: Boolean = false, val historyWindow: Int = 0, val hasPreviousHistory: Boolean = false) {
    fun resource(key: String) = resources[key] ?: ResourceState()
    fun action(key: String) = actions[key] ?: ActionState()
 }
class OwnerViewModel(val repository: OwnerRepository): ViewModel() {
    private class SessionExpired: Exception()
    private suspend fun requireSession() {
        currentCoroutineContext().ensureActive()
        if(!repository.signedIn) throw SessionExpired()
    }
    private val mutable = MutableStateFlow(OwnerState(restoring = true))
    val state = mutable.asStateFlow()
    private val jobs = mutableMapOf<String,Job>()
    private var epoch=0L
    private var sequence=0L
    private var historyGeneration=0L
    private val loadVersions=mutableMapOf<String,Long>()
    private var exportText: String?=null
    private var previousCursor: String?=null
    private var pagesInWindow=0
    init {
        jobs["startup"]=viewModelScope.launch {
            val current=epoch
            try {
                val signedIn=repository.initialize()
                val restored=if(signedIn) restoreCache() else OwnerState()
                ensureActive()
                if(current==epoch) {
                    if(signedIn && !repository.signedIn) expired()
                    else {mutable.value=restored.copy(signedIn=signedIn,restoring=false);if(signedIn) refresh()}
                }
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {if(current==epoch) mutable.value=OwnerState(error="Data tersimpan tidak dapat dipulihkan. Masuk kembali.")}
        }
    }
    private suspend fun restoreCache(): OwnerState {
        val resources=repository.cached() ?: return OwnerState()
        return withContext(Dispatchers.Default) {
            var restored=OwnerState()
            for(key in resources.keys()) runCatching {
                ensureActive()
                val entry=resources.getJSONObject(key);val json=entry.getJSONObject("payload")
                val old=restored
                val value=when(key) {
                    "dashboard" -> old.copy(dashboard=ApiModels.dashboard(json))
                    "config" -> old.copy(config=ApiModels.config(json))
                    "history" -> {val filter=entry.getJSONObject("filters");old.copy(history=json.getJSONArray("data").mapItems(ApiModels::record),nextCursor=null,appliedFilters=filter.keys().asSequence().associateWith {filter.getString(it)})}
                    "telemetry" -> old.copy(telemetry=json.getJSONArray("data").mapItems(ApiModels::telemetry).reversed())
                    "workers" -> old.copy(workers=json.getJSONArray("data").mapItems(ApiModels::worker))
                    "shifts" -> old.copy(shifts=json.getJSONArray("data").mapItems(ApiModels::shift))
                    else -> old
                }
                restored=value.copy(resources=value.resources+(key to ResourceState(fetchedAt=entry.getString("fetchedAt"),lastKnown=true)),lastKnown=true)
            }
            restored
        }
    }
    private fun invalidate() { epoch++; jobs.values.toList().forEach(Job::cancel); jobs.clear(); historyGeneration++; exportText=null }
    private fun expired() { invalidate(); val ticket=repository.expire(); mutable.value=OwnerState(error="Sesi berakhir. Masuk kembali."); viewModelScope.launch(start=CoroutineStart.UNDISPATCHED) {repository.clearExpired(ticket)} }
    private fun load(key: String, block: suspend () -> Unit) {
        if(!mutable.value.signedIn) return
        if(!repository.signedIn) { expired();return }
        jobs.remove(key)?.cancel()
        val current=epoch
        val request=(loadVersions[key] ?: 0L)+1
        loadVersions[key]=request
        mutable.update { it.copy(resources=it.resources+(key to it.resource(key).copy(loading=true,error=null))) }
        jobs[key]=viewModelScope.launch {
            try {
                block(); ensureActive()
                if(current==epoch && loadVersions[key]==request) mutable.update { it.copy(resources=it.resources+(key to ResourceState(fetchedAt=Instant.now().toString()))) }
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) {
                if(current==epoch && loadVersions[key]==request) {
                    if(error is SessionExpired || (error is ApiException && error.code==401)) expired()
                    else mutable.update { it.copy(resources=it.resources+(key to it.resource(key).copy(loading=false,error=error.message ?: "Koneksi gagal",lastKnown=it.resource(key).fetchedAt!=null))) }
                }
            } finally {
                if(current==epoch && loadVersions[key]==request) mutable.update { it.copy(resources=it.resources+(key to it.resource(key).copy(loading=false))) }
            }
        }
    }
    fun login(base: String, email: String, password: String) {
        invalidate(); val current=epoch; mutable.value=OwnerState(loading=true)
        jobs["login"]=viewModelScope.launch {
            try { repository.login(base,email,password); ensureActive(); if(current==epoch) { mutable.value=OwnerState(signedIn=true,sessionId=current); refresh() } }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { if(current==epoch) mutable.value=OwnerState(error=error.message) }
        }
    }
    fun logout() {
        val remote=repository.requestSession()
        invalidate();val ticket=repository.expire();mutable.value=OwnerState()
        viewModelScope.launch(start=CoroutineStart.UNDISPATCHED) {repository.clearExpired(ticket)}
        viewModelScope.launch {try {repository.logout(remote)} catch(cancelled:CancellationException) {throw cancelled} catch(_:Exception) { /* Local revocation is already complete. */ }}
    }

    fun resume() {
        if(!mutable.value.signedIn) return
        if(!repository.signedIn) { expired(); return }
        mutable.update { it.copy(resources=it.resources+("dashboard" to it.resource("dashboard").copy(lastKnown=true))) }
        if(!mutable.value.resource("dashboard").loading) refreshDashboard()
    }
    fun pause() { mutable.update { it.copy(resources=it.resources+("dashboard" to it.resource("dashboard").copy(lastKnown=true))) } }
    suspend fun watchExpiry() {
        val expires=repository.expiresAt?.let(Instant::parse) ?: return
        while(Instant.now().isBefore(expires)) delay(java.time.Duration.between(Instant.now(),expires).toMillis().coerceAtLeast(1))
        if(mutable.value.signedIn && !repository.signedIn) expired()
    }
    fun refreshDashboard() = load("dashboard") { val value=repository.dashboard(); requireSession(); mutable.update { it.copy(dashboard=value,lastKnown=false) } }
    private fun refreshConfig() = load("config") { val value=repository.config(); requireSession(); mutable.update { it.copy(config=value) } }
    private fun refreshWorkers() = load("workers") { val value=repository.workers(); requireSession(); mutable.update { it.copy(workers=value) } }
    private fun refreshShifts() = load("shifts") { val value=repository.shifts(); requireSession(); mutable.update { it.copy(shifts=value) } }
    private fun refreshTelemetry() = load("telemetry") { val value=repository.telemetry(); requireSession(); mutable.update { it.copy(telemetry=value) } }
    fun refresh() { if(!repository.signedIn) { if(mutable.value.signedIn) expired(); return }; refreshDashboard();refreshConfig();filterHistoryMap(mutable.value.appliedFilters);refreshTelemetry();refreshWorkers();refreshShifts() }
    fun filterHistory(from: String, to: String, status: String) = filterHistoryMap(buildMap { if(from.isNotBlank()) put("from",from);if(to.isNotBlank()) put("to",to);if(status.isNotBlank()) put("status",status) })
    private fun filterHistoryMap(filters: Map<String,String>) {
        jobs.remove("pagination")?.cancel(); val query=++historyGeneration
        load("history") { val page=repository.history(filters); requireSession(); if(query==historyGeneration) {
            previousCursor=page.previousCursor;pagesInWindow=1
            mutable.update {it.copy(history=page.items.distinctBy(NutrientRecord::id),nextCursor=page.nextCursor,appliedFilters=filters,historyVersion=it.historyVersion+1,historyWindow=it.historyWindow+1,hasPreviousHistory=previousCursor!=null,resources=it.resources+("pagination" to ResourceState()))}
        } }
    }
    fun more() {
        val cursor=mutable.value.nextCursor ?: return
        if(mutable.value.resource("history").loading || mutable.value.resource("pagination").loading) return
        val filters=mutable.value.appliedFilters;val query=historyGeneration
        load("pagination") {
            val page=repository.history(filters,cursor);requireSession()
            if(query==historyGeneration) {
                val replace=pagesInWindow>=4
                if(replace) {
                    previousCursor=page.previousCursor;pagesInWindow=1
                } else pagesInWindow++
                mutable.update {it.copy(history=(if(replace) page.items else it.history+page.items).distinctBy(NutrientRecord::id),nextCursor=page.nextCursor,historyWindow=if(replace) it.historyWindow+1 else it.historyWindow,hasPreviousHistory=previousCursor!=null)}
            }
        }
    }
    fun previousHistory() {
        val previous=previousCursor ?: return
        if(mutable.value.resource("history").loading || mutable.value.resource("pagination").loading) return
        val query=historyGeneration;val filters=mutable.value.appliedFilters
        load("pagination") {
            var cursor: String?=previous;var rows=emptyList<NutrientRecord>();var pages=0;var next: String?=null
            do {
                val page=repository.history(filters,cursor)
                if(pages==0) next=page.nextCursor
                rows=page.items+rows;cursor=page.previousCursor;pages++
            } while(pages<4 && cursor!=null)
            requireSession()
            if(query==historyGeneration) {
                previousCursor=cursor;pagesInWindow=pages
                mutable.update {it.copy(history=rows.distinctBy(NutrientRecord::id),nextCursor=next,historyWindow=it.historyWindow+1,hasPreviousHistory=cursor!=null)}
            }
        }
    }
    private fun action(key: String, block: suspend (Long) -> Unit): Long? {
        if(!mutable.value.signedIn || mutable.value.action(key).running) return null
        if(!repository.signedIn) { expired();return null }
        val id=++sequence; val current=epoch
        mutable.update { it.copy(actions=it.actions+(key to ActionState(running=true))) }
        jobs["action:$key"]=viewModelScope.launch {
            try { block(id); ensureActive() }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { if(current==epoch) { if(error is SessionExpired || (error is ApiException && error.code==401)) expired() else mutable.update { it.copy(actions=it.actions+(key to it.action(key).copy(error=error.message))) } } }
            finally { if(current==epoch) mutable.update { it.copy(actions=it.actions+(key to it.action(key).copy(running=false))) } }
        }
        return id
    }
    private fun ack(key: String,id: Long,notice: String) { mutable.update { it.copy(actions=it.actions+(key to ActionState(ackId=id)),notice=notice) } }
    fun export() = action("export") { _ -> val filters=mutable.value.appliedFilters; exportText=repository.export(filters); requireSession(); mutable.update { it.copy(exportReady=true) } }
    fun pickerLaunched() { mutable.update { it.copy(exportReady=false) } }
    fun takeExport(): String? = exportText.also { exportText=null }
    fun hasExport() = exportText!=null
    fun saveConfig(body: JSONObject,baseVersion: Long? = mutable.value.config?.version) = action("config") { id ->
        val result=repository.saveConfig(baseVersion,body); requireSession()
        mutable.update { it.copy(config=result.copy(phaseVersion=it.config?.phaseVersion,phase=it.config?.phase)) }
        ack("config",id,"Konfigurasi tersimpan di server. Penerapan ESP32 dilaporkan terpisah."); refreshDashboard()
    }
    fun phase(phase: PlantPhase) = action("phase") { id -> val version=repository.savePhase(mutable.value.config?.phaseVersion,phase); requireSession(); mutable.update { it.copy(config=(it.config ?: RemoteConfig(null,null,null,null)).copy(phaseVersion=version,phase=phase)) };ack("phase",id,"Fase aktif tandon tersimpan.");refreshDashboard() }
    fun worker(name: String, worker: Worker? = null, active: Boolean? = null) = action("worker") { id -> val saved=repository.saveWorker(name,worker,active);requireSession();mutable.update { it.copy(workers=(it.workers.filterNot { old -> old.id==saved.id }+saved)) };ack("worker",id,"Pekerja tersimpan.");refreshWorkers();refreshShifts() }
    fun shift(body: JSONObject, shift: Shift?) = action("shift") { id -> val saved=repository.saveShift(body,shift);requireSession();mutable.update { it.copy(shifts=(it.shifts.filterNot { old -> old.key==saved.key }+saved)) };ack("shift",id,"Shift tersimpan.");refreshShifts() }
    fun deleteShift(shift: Shift) = action("shift") { id -> repository.deleteShift(shift);requireSession();mutable.update { it.copy(shifts=it.shifts.filterNot { old -> old.key==shift.key }) };ack("shift",id,"Shift dinonaktifkan.");refreshShifts() }
    fun message(text: String, error: Boolean = false) { mutable.update { if(error) it.copy(error=text) else it.copy(notice=text) } }
}
