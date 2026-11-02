package com.inofarm.jogja.feature.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.auth.userMessage
import com.inofarm.jogja.feature.reports.ReportSourcePage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class InvestmentState(val active:Boolean=false,val greenhouse:String="",val kind:String=InvestmentContract.SCENARIO,val scenarioFilter:String="",
    val scenarioDraft:InvestmentScenarioDraft?=null,val reportDraft:InvestmentReportDraft?=null,val selected:LocalRecord?=null,val cancelReason:String?=null,
    val records:List<LocalRecord> = emptyList(),val more:Boolean=false,val sources:ReportSourcePage?=null,val audit:JSONObject?=null,
    val busy:Boolean=false,val saved:Boolean=false,val fresh:Boolean=false,val denied:Boolean=false,val errors:Map<String,String> = emptyMap(),val message:String?=null,val changed:Long=0)

class InvestmentViewModel(private val repository:InvestmentRepository):ViewModel() {
    private val vault get()=repository.vault
    private val mutable=MutableStateFlow(InvestmentState());val state=mutable.asStateFlow()
    private var scope:String?=null;private var autosave:Job?=null;private var read:Job?=null;private var operation:Job?=null;private var selection=0L
    fun bind(session:FinanceSession?) {if(scope==session?.scope?.key) return;pauseAutosave();read?.cancel();operation?.cancel();selection++;scope=session?.scope?.key;mutable.value=InvestmentState()}
    fun pauseAutosave() {autosave?.cancel();autosave=null}
    private fun reset(gh:String=state.value.greenhouse)=InvestmentState(active=true,greenhouse=gh,kind=state.value.kind,changed=state.value.changed)
    fun show(gh:String="") {selection++;mutable.value=reset(gh);reload()}
    fun filter(kind:String) {require(kind in setOf(InvestmentContract.SCENARIO,InvestmentContract.REPORT));selection++;mutable.value=reset().copy(kind=kind);reload()}
    fun newScenario(gh:String=state.value.greenhouse) {pauseAutosave();selection++;mutable.value=reset(gh).copy(kind=InvestmentContract.SCENARIO,scenarioDraft=InvestmentScenarioDraft(greenhouseId=gh));autosave=viewModelScope.launch {persistCurrent(false)}}
    fun newReport(row:LocalRecord) {pauseAutosave();selection++;mutable.value=reset(row.greenhouseId).copy(kind=InvestmentContract.REPORT,reportDraft=InvestmentReportDraft(greenhouseId=row.greenhouseId,scenarioId=row.id));autosave=viewModelScope.launch {persistCurrent(false)}}
    fun reevaluate() {val row=state.value.selected ?: return;val body=InvestmentContract.report(row.serverBody ?: row.body,true);pauseAutosave();selection++;mutable.value=reset(row.greenhouseId).copy(kind=InvestmentContract.REPORT,reportDraft=InvestmentReportDraft(greenhouseId=row.greenhouseId,scenarioId=body.getString("scenario_id")));autosave=viewModelScope.launch {persistCurrent(false)}}
    fun editScenario(change:(InvestmentScenarioDraft)->InvestmentScenarioDraft) {
        val draft=state.value.scenarioDraft ?: return;mutable.value=state.value.copy(scenarioDraft=change(draft),saved=false,errors=emptyMap(),message=null)
        pauseAutosave();autosave=viewModelScope.launch {delay(350);persistCurrent(false)}
    }
    fun editReport(change:(InvestmentReportDraft)->InvestmentReportDraft) {
        val draft=state.value.reportDraft ?: return;mutable.value=state.value.copy(reportDraft=change(draft).copy(previewText=null,previewFetchedAt=null),saved=false,sources=null,message=null)
        pauseAutosave();autosave=viewModelScope.launch {delay(350);persistCurrent(false)}
    }
    fun editCancel(value:String) {mutable.value=state.value.copy(cancelReason=value,saved=false);pauseAutosave();autosave=viewModelScope.launch {delay(350);persistCurrent(false)}}
    suspend fun flush(session:FinanceSession,ticket:Long,snapshot:InvestmentState) {
        snapshot.scenarioDraft?.let {repository.flushScenario(session,ticket,it)}
        snapshot.reportDraft?.let {repository.flushReport(session,ticket,it)}
        snapshot.selected?.let {row->snapshot.cancelReason?.let {repository.flushCancel(session,ticket,row,it)}}
    }
    fun persist(session:FinanceSession,ticket:Long,snapshot:InvestmentState) {viewModelScope.launch {try {flush(session,ticket,snapshot)} catch(c:CancellationException) {throw c} catch(e:Exception) {failure(session,ticket,e)}}}
    private suspend fun persistCurrent(close:Boolean) {
        val session=vault.current ?: return;val ticket=vault.generation;val snapshot=state.value
        try {
            flush(session,ticket,snapshot)
            if(vault.matches(ticket,session.scope) && state.value.scenarioDraft==snapshot.scenarioDraft && state.value.reportDraft==snapshot.reportDraft && state.value.cancelReason==snapshot.cancelReason) {
                mutable.value=if(close) reset().copy(saved=true,changed=state.value.changed+1) else state.value.copy(saved=true)
                if(close) reload()
            }
        } catch(c:CancellationException) {throw c} catch(e:Exception) {failure(session,ticket,e)}
    }
    fun back() {
        if(state.value.busy) return
        pauseAutosave();selection++
        if(state.value.scenarioDraft!=null || state.value.reportDraft!=null || state.value.cancelReason!=null) action {_,_->persistCurrent(true)}
        else if(state.value.selected!=null) {mutable.value=reset();reload()}
        else mutable.value=state.value.copy(active=false)
    }
    fun reload(more:Boolean=false) {
        val session=vault.current ?: return;val ticket=vault.generation;if(state.value.denied) return
        val snapshot=state.value;read?.cancel();read=viewModelScope.launch {try {
            val rows=repository.records(session,snapshot.kind,snapshot.greenhouse,snapshot.scenarioFilter,if(more) snapshot.records.size else 0)
            val gh=snapshot.scenarioDraft?.greenhouseId ?: snapshot.reportDraft?.greenhouseId ?: snapshot.selected?.greenhouseId
            val visible=gh==null || repository.visible(session,gh)
            if(vault.matches(ticket,session.scope) && state.value.kind==snapshot.kind && state.value.greenhouse==snapshot.greenhouse) mutable.value=state.value.copy(records=((if(more) snapshot.records else emptyList())+rows.take(50)).distinctBy {it.id},more=rows.size>50,denied=!visible,selected=if(visible) state.value.selected else null,sources=if(visible) state.value.sources else null,audit=if(visible) state.value.audit else null)
        } catch(c:CancellationException) {throw c} catch(e:Exception) {failure(session,ticket,e)}}
    }
    fun select(id:String) {
        pauseAutosave();val token=++selection
        action {session,ticket->
            val row=repository.detail(session,id);if(token!=selection) return@action
            val editable=row.syncState in setOf(SyncState.LOCAL,SyncState.REJECTED)
            val base=row.baseVersion ?: row.serverVersion
            val scenario=if(row.kind==InvestmentContract.SCENARIO && editable && row.body.has("_local_form")) InvestmentScenarioDraft.from(row.id,row.greenhouseId,row.body,base).copy(replaceRejected=row.syncState==SyncState.REJECTED) else null
            val report=if(row.kind==InvestmentContract.REPORT && editable && row.body.has("_local_form")) InvestmentReportDraft.from(row.id,row.greenhouseId,row.body).let {if(row.syncState==SyncState.REJECTED) it.copy(previewText=null,previewFetchedAt=null,replaceRejected=true) else it} else null
            mutable.value=state.value.copy(active=true,scenarioDraft=scenario,reportDraft=report,selected=if(scenario==null && report==null) row else null,cancelReason=if(row.body.has("_local_cancel") && editable) row.body.getString("_local_cancel") else null,sources=null,audit=null,saved=true,fresh=false)
            if(scenario==null && report==null && row.serverVersion!=null) {repository.verify(session,ticket,row);if(token==selection && state.value.selected?.id==row.id) mutable.value=state.value.copy(fresh=true)}
        }
    }
    fun editSelected() {
        val row=state.value.selected ?: return;require(row.kind==InvestmentContract.SCENARIO && row.syncState==SyncState.ACCEPTED)
        val draft=InvestmentScenarioDraft.from(row.id,row.greenhouseId,row.serverBody ?: row.body,row.serverVersion)
        mutable.value=state.value.copy(selected=null,scenarioDraft=draft,errors=emptyMap(),saved=true,message=null,audit=null,sources=null)
    }
    fun beginCancel() {val row=state.value.selected ?: return;require(row.kind==InvestmentContract.SCENARIO && row.syncState==SyncState.ACCEPTED);mutable.value=state.value.copy(cancelReason="");pauseAutosave();autosave=viewModelScope.launch {persistCurrent(false)}}
    fun queueScenario() {val draft=state.value.scenarioDraft ?: return;val errors=draft.validation();if(errors.isNotEmpty()) {mutable.value=state.value.copy(errors=errors);return};action {s,t->repository.queueScenario(s,t,draft);completed("Skenario tersimpan lokal dan menunggu penerimaan server.")}}
    fun preview() {val draft=state.value.reportDraft ?: return;action {s,t->val preview=repository.preview(s,t,draft);if(state.value.reportDraft==draft) mutable.value=state.value.copy(reportDraft=preview,saved=true)}}
    fun queueReport() {val draft=state.value.reportDraft ?: return;action {s,t->repository.queueReport(s,t,draft);completed("Permintaan laporan diantrekan; hasil belum menjadi snapshot diterima.")}}
    fun cancel() {val row=state.value.selected ?: return;val reason=state.value.cancelReason ?: return;action {s,t->repository.cancel(s,t,row,reason);completed("Pembatalan diantrekan; status server belum berubah.")}}
    fun discardCancel() {val row=state.value.selected ?: return;action {s,t->repository.discardCancel(s,t,row);completed("Draf pembatalan diarsipkan. Catatan server dipertahankan.")}}
    fun resolve(local:Boolean) {val row=state.value.selected ?: return;action {s,t->repository.resolve(s,t,row,local);completed(if(local) "Koreksi baru diantrekan berdasarkan versi yang ditinjau." else "Salinan server dipilih; operasi lama tetap diarsipkan.")}}
    fun sources() {val row=state.value.selected?.takeIf {it.kind==InvestmentContract.REPORT && it.serverVersion!=null} ?: return;action {s,t->
        val previous=state.value.sources ?: repository.cachedSources(s,row)
        if(previous!=null) mutable.value=state.value.copy(sources=previous)
        if(previous==null || previous.cursor!=null) mutable.value=state.value.copy(sources=repository.sources(s,t,row,previous))
    }}
    fun audit() {val row=state.value.selected ?: return;action {s,t->
        val previous=state.value.audit ?: repository.cachedAudit(s,row)
        if(previous!=null) mutable.value=state.value.copy(audit=previous)
        if(previous==null || !previous.isNull("next_cursor")) mutable.value=state.value.copy(audit=repository.audit(s,t,row,previous))
    }}
    private fun completed(message:String) {mutable.value=reset().copy(message=message,changed=state.value.changed+1);reload()}
    private fun action(block:suspend(FinanceSession,Long)->Unit) {
        if(state.value.busy) return;val session=vault.current ?: return;val ticket=vault.generation;pauseAutosave();mutable.value=state.value.copy(busy=true,message=null)
        operation=viewModelScope.launch {try {block(session,ticket)} catch(c:CancellationException) {throw c} catch(e:Exception) {failure(session,ticket,e)} finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}}
    }
    private fun failure(session:FinanceSession,ticket:Long,error:Exception) {
        if(!vault.matches(ticket,session.scope)) return
        val denied=error is InvestmentAccessDenied || error is FinanceApiFailure && error.status in setOf(401,403,404)
        mutable.value=state.value.copy(message=userMessage(error),denied=denied || state.value.denied,selected=if(denied) null else state.value.selected,records=if(denied) emptyList() else state.value.records,sources=if(denied) null else state.value.sources,audit=if(denied) null else state.value.audit)
    }
}
