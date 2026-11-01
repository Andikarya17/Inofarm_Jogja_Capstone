package com.inofarm.jogja.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.contracts.SeasonReportContract
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SeasonReportState(val active:Boolean=false,val greenhouse:String="",val season:String="",val draft:SeasonReportDraft?=null,
    val records:List<LocalRecord> = emptyList(),val more:Boolean=false,val selected:LocalRecord?=null,val fresh:Boolean=false,
    val sources:ReportSourcePage?=null,val busy:Boolean=false,val saved:Boolean=false,val message:String?=null,
    val errors:Map<String,String> = emptyMap(),val changed:Long=0,val accessDenied:Boolean=false)

class SeasonReportViewModel(private val repository:SeasonReportRepository):ViewModel() {
    private val vault get()=repository.vault
    private val mutable=MutableStateFlow(SeasonReportState());val state=mutable.asStateFlow()
    private var scope:String?=null;private var autosave:Job?=null;private var read:Job?=null
    fun bind(session:FinanceSession?) {if(scope==session?.scope?.key) return;pauseAutosave();read?.cancel();scope=session?.scope?.key;mutable.value=SeasonReportState()}
    fun show(gh:String="") {mutable.value=state.value.copy(active=true,greenhouse=gh,season="",draft=null,selected=null,sources=null,message=null,accessDenied=false);reload()}
    fun filter(gh:String,season:String) {mutable.value=state.value.copy(greenhouse=gh,season=season,selected=null,sources=null);reload()}
    fun new(gh:String=state.value.greenhouse,season:String="") {pauseAutosave();mutable.value=state.value.copy(active=true,draft=SeasonReportDraft(greenhouseId=gh,seasonId=season),selected=null,sources=null,errors=emptyMap(),message=null,saved=false);val draft=state.value.draft!!;autosave=viewModelScope.launch {save(draft,false)}}
    fun edit(change:(SeasonReportDraft)->SeasonReportDraft) {
        val current=state.value.draft ?: return;val draft=change(current).copy(previewText=null,previewFetchedAt=null)
        mutable.value=state.value.copy(draft=draft,sources=null,saved=false,errors=emptyMap(),message=null)
        pauseAutosave();autosave=viewModelScope.launch {delay(350);save(draft,false)}
    }
    fun pauseAutosave() {autosave?.cancel();autosave=null}
    fun persist(session:FinanceSession,ticket:Long,draft:SeasonReportDraft) {viewModelScope.launch {try {repository.flush(session,ticket,draft)} catch(c:CancellationException) {throw c} catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error))}}}
    fun preview() {
        val draft=state.value.draft ?: return;val errors=draft.validation()
        if(errors.isNotEmpty()) {mutable.value=state.value.copy(errors=errors);return}
        val session=vault.current ?: return;val ticket=vault.generation;pauseAutosave();mutable.value=state.value.copy(busy=true,message=null)
        viewModelScope.launch {try {
            val result=repository.preview(session,ticket,draft)
            if(vault.matches(ticket,session.scope) && state.value.draft==draft) mutable.value=state.value.copy(draft=result,saved=true,sources=null)
        } catch(c:CancellationException) {throw c} catch(error:Exception) {failure(session,ticket,error)} finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}}
    }
    fun queue() {
        if(state.value.busy) return
        val draft=state.value.draft ?: return;val session=vault.current ?: return;val ticket=vault.generation;pauseAutosave();mutable.value=state.value.copy(busy=true,message=null)
        viewModelScope.launch {try {
            repository.queue(session,ticket,draft)
            if(vault.matches(ticket,session.scope) && state.value.draft==draft) mutable.value=state.value.copy(draft=null,sources=null,changed=state.value.changed+1,message="Permintaan tersimpan lokal dan diantrekan; snapshot belum diterima server.")
            reload()
        } catch(c:CancellationException) {throw c} catch(error:Exception) {failure(session,ticket,error)} finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}}
    }
    private suspend fun save(draft:SeasonReportDraft,close:Boolean) {
        val session=vault.current ?: return;val ticket=vault.generation
        try {repository.flush(session,ticket,draft);if(vault.matches(ticket,session.scope) && state.value.draft==draft) mutable.value=state.value.copy(draft=if(close) null else draft,saved=true,changed=if(close) state.value.changed+1 else state.value.changed);if(close) reload()}
        catch(c:CancellationException) {throw c} catch(error:Exception) {failure(session,ticket,error)}
    }
    fun back() {
        if(state.value.busy) return
        val draft=state.value.draft
        if(draft!=null) {val session=vault.current ?: return;val ticket=vault.generation;pauseAutosave();mutable.value=state.value.copy(busy=true);viewModelScope.launch {try {save(draft,true)} finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}}}
        else if(state.value.selected!=null) mutable.value=state.value.copy(selected=null,sources=null,message=null)
        else mutable.value=state.value.copy(active=false,sources=null)
    }
    fun reload(more:Boolean=false) {
        val session=vault.current ?: return;val ticket=vault.generation;if(state.value.accessDenied) return
        val offset=if(more) state.value.records.size else 0;val gh=state.value.greenhouse;val season=state.value.season
        read?.cancel();read=viewModelScope.launch {try {
            val rows=repository.records(session,gh,season,offset)
            val target=state.value.selected?.greenhouseId ?: state.value.draft?.greenhouseId;val visible=target==null || repository.visible(session,target)
            if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(records=(if(more) state.value.records else emptyList())+rows.take(50),more=rows.size>50,
                selected=if(visible) state.value.selected else null,sources=if(visible) state.value.sources else null,accessDenied=state.value.accessDenied || !visible)
        } catch(c:CancellationException) {throw c} catch(error:Exception) {failure(session,ticket,error)}}
    }
    fun select(id:String) {
        val session=vault.current ?: return;val ticket=vault.generation;pauseAutosave();mutable.value=state.value.copy(active=true,busy=true,message=null)
        viewModelScope.launch {try {
            val row=repository.detail(session,id)
            if(row.serverVersion==null && row.body.has("_local_form") && row.syncState in setOf(SyncState.LOCAL,SyncState.REJECTED)) {
                val draft=SeasonReportDraft.from(row.id,row.greenhouseId,row.body)
                if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(draft=if(row.syncState==SyncState.REJECTED) draft.copy(previewText=null,previewFetchedAt=null,replaceRejected=true) else draft,selected=null,sources=null,saved=true)
            } else {
                if(row.serverVersion!=null) SeasonReportContract.validate(row.serverBody ?: row.body,true)
                if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(selected=row,draft=null,sources=null,fresh=false)
                if(row.serverVersion!=null) {repository.verify(session,ticket,row);if(vault.matches(ticket,session.scope) && state.value.selected?.id==row.id) mutable.value=state.value.copy(fresh=true)}
            }
        } catch(c:CancellationException) {throw c} catch(error:Exception) {failure(session,ticket,error)} finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}}
    }
    fun sources() {
        if(state.value.busy) return
        val session=vault.current ?: return;val ticket=vault.generation;val draft=state.value.draft;val row=state.value.selected
        val data=runCatching {draft?.preview() ?: row?.serverBody}.getOrNull() ?: return
        val id=draft?.id ?: row!!.id;val gh=draft?.greenhouseId ?: row!!.greenhouseId
        val snapshot=data.optString("snapshot_hash").takeIf {row?.serverVersion!=null};val hash=data.getString("sources_hash")
        mutable.value=state.value.copy(busy=true,message=null)
        viewModelScope.launch {try {
            val previous=state.value.sources ?: repository.cachedSources(session,id,hash,snapshot)
            if(previous!=null && vault.matches(ticket,session.scope)) mutable.value=state.value.copy(sources=previous)
            if(previous==null || previous.cursor!=null) {
                val page=repository.sources(session,ticket,id,gh,data.getString("season_id"),hash,snapshot,previous)
                if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(sources=page)
            }
        } catch(c:CancellationException) {throw c} catch(error:Exception) {failure(session,ticket,error)} finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}}
    }
    private fun failure(session:FinanceSession,ticket:Long,error:Exception) {
        if(!vault.matches(ticket,session.scope)) return
        val denied=(error is FinanceApiFailure && error.status in setOf(401,403,404)) || (error is IllegalArgumentException && error.message.orEmpty().contains("pengelola"))
        mutable.value=state.value.copy(message=userMessage(error),accessDenied=denied || state.value.accessDenied,
            selected=if(denied) null else state.value.selected,records=if(denied) emptyList() else state.value.records,sources=if(denied) null else state.value.sources)
    }
}
