package com.inofarm.jogja.feature.attachments

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.files.AttachmentViewer
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AttachmentUiState(val parent:LocalRecord?=null,val rows:List<LocalRecord> = emptyList(),val selected:LocalRecord?=null,
    val pending:List<QueuedOperation> = emptyList(),val hasMore:Boolean=false,val loading:Boolean=false,val busy:Boolean=false,
    val policy:JSONObject?=null,val policyCached:Boolean=true,val message:String?=null,val reviewing:Boolean=false,val parentReview:JSONObject?=null,
    val cancelEditing:Boolean=false,val reason:String="",val reasonSaved:Boolean=true,val audit:JSONObject?=null,val auditCached:Boolean=false,
    val preview:Intent?=null,val changed:Long=0)
data class AttachmentFormSnapshot(val id:String,val reason:String)

class AttachmentViewModel(private val repository:AttachmentRepository,private val viewer:AttachmentViewer):ViewModel() {
    private val vault get()=repository.vault
    private val mutable=MutableStateFlow(AttachmentUiState());val state=mutable.asStateFlow()
    private var bound:String?=null;private var role:String?=null;private var count=50;private var selection=0L
    private var query:Job?=null;private var policyJob:Job?=null;private var saveJob:Job?=null
    private fun current(session:FinanceSession,ticket:Long) {check(vault.matches(ticket,session.scope)) {"Sesi berubah."}}
    fun bind(session:FinanceSession?) {
        if(bound==session?.scope?.key && role==session?.role?.wire) return
        bound=session?.scope?.key;role=session?.role?.wire;selection++;query?.cancel();policyJob?.cancel();pauseAutosave();mutable.value=AttachmentUiState();count=50
    }
    fun open(parent:LocalRecord) {selection++;pauseAutosave();count=50;mutable.value=state.value.copy(parent=parent,selected=null,rows=emptyList(),pending=emptyList(),policy=null,reviewing=false,parentReview=null,cancelEditing=false,reason="",audit=null,message=null);reload();refreshPolicy()}
    fun more() {count+=50;reload()}
    fun reload() {
        val session=vault.current ?: return;val ticket=vault.generation;val parent=state.value.parent ?: return;val currentSelection=selection
        query?.cancel();query=viewModelScope.launch {
            mutable.value=state.value.copy(loading=true)
            try {
                val rows=repository.rows(session,parent,count+1);val selected=state.value.selected?.id?.let {repository.detail(session,it)}
                if(vault.matches(ticket,session.scope) && currentSelection==selection) mutable.value=state.value.copy(rows=rows.take(count),hasMore=rows.size>count,selected=selected?.record,pending=selected?.pending ?: emptyList())
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {if(vault.matches(ticket,session.scope) && currentSelection==selection) mutable.value=state.value.copy(message=userMessage(error))}
            finally {if(vault.matches(ticket,session.scope) && currentSelection==selection) mutable.value=state.value.copy(loading=false)}
        }
    }
    fun refreshPolicy() {
        val session=vault.current ?: return;val ticket=vault.generation;val parent=state.value.parent ?: return;val currentSelection=selection
        policyJob?.cancel();policyJob=viewModelScope.launch {
            try {
                repository.cachedPolicy(session,parent)?.let {if(vault.matches(ticket,session.scope) && state.value.parent?.id==parent.id) mutable.value=state.value.copy(policy=it,policyCached=true)}
                val policy=repository.policy(session,ticket,parent)
                if(vault.matches(ticket,session.scope) && state.value.parent?.id==parent.id) mutable.value=state.value.copy(policy=policy,policyCached=false)
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {if(vault.matches(ticket,session.scope) && state.value.parent?.id==parent.id) mutable.value=state.value.copy(policyCached=true,message="Batas terbaru belum tersedia. ${userMessage(error)}")}
        }
    }
    fun select(id:String) {mutate(false) {session,ticket ->
        flushCurrent(session);val detail=repository.detail(session,id) ?: error("Lampiran tidak tersedia dalam penugasan ini.")
        val form=repository.form(session,id)
        current(session,ticket)
        selection++;mutable.value=state.value.copy(selected=detail.record,pending=detail.pending,reviewing=false,parentReview=null,cancelEditing=form!=null,reason=form?.optString("reason").orEmpty(),reasonSaved=true,audit=null,message=null)
    }}
    fun importResult(uri:Uri,scopeKey:String,ownerId:String,ticket:Long,name:String?=null) {
        val parent=state.value.parent ?: return;val session=vault.current ?: return
        if(session.scope.key!=scopeKey || parent.id!=ownerId || vault.generation!=ticket) {mutable.value=state.value.copy(message="Akun atau catatan berubah. Pilih berkas kembali untuk akun ini.");return}
        mutate(false) {captured,generation ->
            val record=repository.importUri(captured,generation,parent,uri,name)
            current(captured,generation)
            selection++;mutable.value=state.value.copy(selected=record,pending=emptyList(),reviewing=false,cancelEditing=false,reason="",reasonSaved=true,message="Salinan terenkripsi siap di perangkat. Berkas belum masuk antrean atau diterima server.")
        }
    }
    fun resumeCopy() {val selected=state.value.selected ?: return;mutate(false) {session,ticket -> repository.resumeCopy(session,ticket,selected.id)}}
    fun review() {mutable.value=state.value.copy(reviewing=true,parentReview=null,message=null)}
    fun backToDetails() {mutable.value=state.value.copy(reviewing=false,parentReview=null)}
    fun queue() {val selected=state.value.selected ?: return;mutate {session,ticket -> repository.queue(session,ticket,selected.id);current(session,ticket);mutable.value=state.value.copy(reviewing=false,message="Lampiran masuk antrean lokal. Penerimaan server ditampilkan terpisah.")}}
    fun retry() {val selected=state.value.selected ?: return;mutate {session,ticket -> repository.retry(session,ticket,selected.id)}}
    fun inspectParent() {val selected=state.value.selected ?: return;mutate(false) {session,ticket ->
        val snapshot=repository.inspectParent(session,ticket,selected);current(session,ticket);mutable.value=state.value.copy(parentReview=snapshot,reviewing=false)
    }}
    fun confirmParent() {val selected=state.value.selected ?: return;val snapshot=state.value.parentReview ?: return;mutate {session,ticket ->
        repository.reviewParent(session,ticket,selected,snapshot);current(session,ticket);mutable.value=state.value.copy(parentReview=null,message="Versi induk telah ditinjau. Operasi baru memakai salinan berkas yang sama; operasi lama tetap tersimpan.")
    }}
    fun beginCancel() {mutable.value=state.value.copy(cancelEditing=true,reviewing=false,parentReview=null)}
    fun editReason(value:String) {
        mutable.value=state.value.copy(reason=value,reasonSaved=false);pauseAutosave()
        val session=vault.current ?: return;val ticket=vault.generation;val id=state.value.selected?.id ?: return
        saveJob=viewModelScope.launch {delay(350);try {repository.flushForm(session,ticket,id,value);if(vault.matches(ticket,session.scope) && state.value.selected?.id==id && state.value.reason==value) mutable.value=state.value.copy(reasonSaved=true)} catch(cancelled:CancellationException) {throw cancelled} catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error))}}
    }
    fun pauseAutosave() {saveJob?.cancel();saveJob=null}
    fun formSnapshot():AttachmentFormSnapshot?=state.value.takeIf {it.cancelEditing}?.selected?.let {AttachmentFormSnapshot(it.id,state.value.reason)}
    suspend fun flush(session:FinanceSession,ticket:Long,snapshot:AttachmentFormSnapshot) {repository.flushForm(session,ticket,snapshot.id,snapshot.reason)}
    fun persistForm(session:FinanceSession,ticket:Long,snapshot:AttachmentFormSnapshot) {viewModelScope.launch {
        try {flush(session,ticket,snapshot);if(vault.matches(ticket,session.scope) && state.value.selected?.id==snapshot.id && state.value.reason==snapshot.reason) mutable.value=state.value.copy(reasonSaved=true)}
        catch(cancelled:CancellationException) {throw cancelled}
        catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error))}
    }}
    private suspend fun flushCurrent(session:FinanceSession) {pauseAutosave();formSnapshot()?.let {flush(session,vault.generation,it)}}
    fun cancel() {val record=state.value.selected ?: return;val reason=state.value.reason;pauseAutosave();mutate {session,ticket ->
        repository.flushForm(session,ticket,record.id,reason);repository.cancel(session,ticket,record,reason)
        current(session,ticket)
        mutable.value=state.value.copy(cancelEditing=false,reason="",reasonSaved=true,message="Pembatalan masuk antrean. Riwayat dan berkas tetap disimpan.")
    }}
    fun close() {mutate(false) {session,ticket ->
        flushCurrent(session);current(session,ticket);selection++;policyJob?.cancel();mutable.value=state.value.copy(parent=null,selected=null,pending=emptyList(),reviewing=false,parentReview=null,cancelEditing=false,reason="",audit=null)
    }}
    fun closeSelected() {mutate(false) {session,ticket ->flushCurrent(session);current(session,ticket);selection++;mutable.value=state.value.copy(selected=null,pending=emptyList(),reviewing=false,parentReview=null,cancelEditing=false,reason="",audit=null)}}
    fun audit(more:Boolean=false) {val record=state.value.selected ?: return;mutate(false) {session,ticket ->
        if(!more) repository.cachedAudit(session,record)?.let {current(session,ticket);mutable.value=state.value.copy(audit=it,auditCached=true)}
        val previous=if(more) state.value.audit else null;val cursor=previous?.optString("next_cursor")?.takeUnless {it.isBlank() || it=="null"}
        val page=repository.audit(session,ticket,record,cursor)
        current(session,ticket)
        if(previous!=null && cursor!=null) {
            val rows=JSONArray();previous.getJSONArray("data").let {old -> repeat(old.length()) {rows.put(old.getJSONObject(it))}};page.getJSONArray("data").let {next -> repeat(next.length()) {rows.put(next.getJSONObject(it))}};page.put("data",rows)
        }
        mutable.value=state.value.copy(audit=page,auditCached=false)
    }}
    fun preview() {val record=state.value.selected ?: return;mutate(false) {session,ticket ->
        val blob=repository.localCopy(session,record) ?: repository.download(session,ticket,record)
        val intent=withContext(Dispatchers.IO) {viewer.prepare(session,record,blob)}
        if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(preview=intent)
    }}
    fun previewHandled(error:String?=null) {mutable.value=state.value.copy(preview=null,message=error ?: state.value.message)}
    private fun mutate(changed:Boolean=true,action:suspend (FinanceSession,Long)->Unit) {
        if(state.value.busy) return
        val session=vault.current ?: return;val ticket=vault.generation
        viewModelScope.launch {
            mutable.value=state.value.copy(busy=true,message=null)
            try {action(session,ticket);if(vault.matches(ticket,session.scope)) {if(changed) mutable.value=state.value.copy(changed=state.value.changed+1);reload()}}
            catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error));reload()}
            finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}
        }
    }
}
