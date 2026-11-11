package com.inofarm.jogja.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class HistoryUiState(val records:List<LocalRecord> = emptyList(),val filter:LocalHistoryFilter=LocalHistoryFilter(),val hasMore:Boolean=false,val loading:Boolean=false,
    val selected:LocalRecord?=null,val comparison:LocalRecord?=null,val audit:AuditPage?=null,val archives:List<JSONObject> = emptyList(),val auditLoading:Boolean=false,
    val message:String?=null,val busy:Boolean=false,val changed:Long=0,val pending:List<QueuedOperation> = emptyList(),
    val loaded:Boolean=false,val loadError:String?=null,val filterSeason:String?=null,
    val mine:Boolean=false,val checks:Map<String,String> = emptyMap())

class HistoryViewModel(private val repository:HistoryRepository):ViewModel() {
    private val vault get()=repository.vault
    private val mutable=MutableStateFlow(HistoryUiState());val state=mutable.asStateFlow()
    private var boundScope:String?=null;private var query:Job?=null;private var auditJob:Job?=null;private var selectJob:Job?=null;private var count=50
    private var selectionVersion=0L
    fun bind(session:FinanceSession?) {
        if(boundScope==session?.scope?.key) return
        boundScope=session?.scope?.key;selectionVersion++;query?.cancel();auditJob?.cancel();selectJob?.cancel();mutable.value=HistoryUiState(mine=session?.role==UserRole.WORKER);count=50;if(session!=null) reload()
    }
    fun filter(filter:LocalHistoryFilter, season:String?=filter.seasonId) {
        count=50
        mutable.value=state.value.copy(filter=filter,filterSeason=season,records=emptyList(),hasMore=false,loaded=false,loadError=null)
        reload()
    }
    fun mine(on:Boolean) {
        count=50
        mutable.value=state.value.copy(mine=on,records=emptyList(),hasMore=false,loaded=false,loadError=null)
        reload()
    }
    fun more() {count+=50;reload()}
    fun reload() {
        val session=vault.current ?: return;val ticket=vault.generation
        val selectedId=state.value.selected?.id;val selection=selectionVersion
        query?.cancel();query=viewModelScope.launch {
            mutable.value=state.value.copy(loading=true)
            try {
                val rows=repository.records(session,state.value.filter,count+1,state.value.mine)
                val checks=if(session.role==UserRole.WORKER) repository.checks(session) else emptyMap()
                val detail=selectedId?.let {repository.detail(session,it)}
                val selected=detail?.record;val pending=detail?.pending ?: emptyList();val archives=detail?.archives ?: emptyList()
                if(vault.matches(ticket,session.scope)) {
                    val current=state.value;val sameSelection=selectionVersion==selection && current.selected?.id==selectedId
                    mutable.value=current.copy(records=rows.take(count),hasMore=rows.size>count,loaded=true,loadError=null,checks=checks,selected=if(sameSelection) selected else current.selected,
                        pending=if(sameSelection) pending else current.pending,archives=if(sameSelection) archives else current.archives,
                        comparison=if(sameSelection && selected==null) null else current.comparison)
                }
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(loadError=userMessage(error))}
            finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(loading=false)}
        }
    }
    fun select(id:String, expectedVersion: Long? = null, onResult: ((String?) -> Unit)? = null) {
        val session=vault.current ?: return;val ticket=vault.generation
        selectionVersion++
        auditJob?.cancel();selectJob?.cancel();selectJob=viewModelScope.launch {
            try {
                val local=repository.detail(session,id)
                val versionMismatch = expectedVersion != null && local != null && local.record.serverVersion != expectedVersion
                val detail = local.takeUnless { versionMismatch }
                val record=detail?.record;val archived=detail?.archives ?: emptyList();val pending=detail?.pending ?: emptyList()
                if(vault.matches(ticket,session.scope)) {
                    val error = when {
                        versionMismatch -> "Revisi di perangkat berbeda dari daftar. Perbarui untuk membandingkan."
                        record == null -> "Catatan belum tersedia di perangkat. Perbarui lalu coba lagi."
                        else -> null
                    }
                    selectionVersion++
                    mutable.value=state.value.copy(selected=record,comparison=null,audit=null,archives=archived,pending=pending,message=error.takeIf { onResult == null })
                    onResult?.invoke(error)
                    reload()
                }
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {
                if(vault.matches(ticket,session.scope)) {
                    if(onResult == null) mutable.value=state.value.copy(message=userMessage(error))
                    else onResult(userMessage(error))
                }
            }
        }
    }
    fun close() {selectionVersion++;selectJob?.cancel();auditJob?.cancel();mutable.value=state.value.copy(selected=null,comparison=null,audit=null,archives=emptyList(),pending=emptyList())}
    fun compare() {mutable.value=state.value.copy(comparison=state.value.selected,message=null)}
    fun closeComparison() {mutable.value=state.value.copy(comparison=null)}
    fun audit(more:Boolean=false) {
        val session=vault.current ?: return;val ticket=vault.generation;val selected=state.value.selected ?: return
        if(state.value.auditLoading) return
        auditJob=viewModelScope.launch {
            mutable.value=state.value.copy(auditLoading=true,message=null)
            try {
                if(!more) repository.cachedAudit(session,selected.id)?.let {if(vault.matches(ticket,session.scope) && state.value.selected?.id==selected.id) mutable.value=state.value.copy(audit=it)}
                val previous=if(more) state.value.audit else null
                val page=repository.audit(session,ticket,selected,previous?.cursor,previous)
                if(vault.matches(ticket,session.scope) && state.value.selected?.id==selected.id) mutable.value=state.value.copy(audit=page)
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message="Riwayat perubahan belum bisa dimuat. ${userMessage(error)}")}
            finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(auditLoading=false)}
        }
    }
    private fun mutate(action:suspend (FinanceSession,Long)->Unit) {
        if(state.value.busy) return
        val session=vault.current ?: return;val ticket=vault.generation
        viewModelScope.launch {
            mutable.value=state.value.copy(busy=true,message=null)
            try {
                action(session,ticket)
                if(vault.matches(ticket,session.scope)) {mutable.value=state.value.copy(comparison=null,message="Tersimpan. Terkirim otomatis saat ada internet.",changed=state.value.changed+1);reload()}
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error))}
            finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}
        }
    }
    fun resolve(keepLocal:Boolean,reason:String,policy:String) {val reviewed=state.value.comparison ?: return;mutate {session,ticket -> repository.resolve(session,ticket,reviewed,keepLocal,reason,policy)}}
    fun cancel(reviewed:LocalRecord,reason:String)=mutate {session,ticket -> repository.cancel(session,ticket,reviewed,reason)}
    fun season(reviewed:JSONObject,reason:String)=mutate {session,ticket -> repository.season(session,ticket,reviewed,reason)}
    fun retryClosed() {val reviewed=state.value.selected ?: return;mutate {session,ticket -> repository.retryClosed(session,ticket,reviewed)}}
}
