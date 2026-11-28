package com.inofarm.jogja.feature.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.EntryOrigin
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AssetEntryState(val draft:AssetDraft?=null,val errors:Map<String,String> = emptyMap(),val busy:Boolean=false,
    val reviewing:Boolean=false,val saved:Boolean=false,val message:String?=null,val changed:Long=0)
class AssetEntryViewModel(private val repository:AssetRepository):ViewModel() {
    private val vault get()=repository.vault
    private val reasonRequired get()=vault.current?.role!=com.inofarm.jogja.core.contracts.UserRole.MANAGER
    val reasonLabel get()=if(reasonRequired) "Alasan koreksi" else "Catatan (opsional)"
    private val mutable=MutableStateFlow(AssetEntryState());val state=mutable.asStateFlow()
    private var saveJob:Job?=null;private var scope:String?=null
    fun bind(session:FinanceSession?) {
        if(scope==session?.scope?.key) return
        scope=session?.scope?.key;pauseAutosave();mutable.value=AssetEntryState()
    }
    private val entry=EntryOrigin<AssetDraft>()
    fun new(gh:String,kind:AssetKind) {pauseAutosave();mutable.value=state.value.copy(draft=AssetDraft(greenhouseId=gh,kind=kind),errors=emptyMap(),reviewing=false,saved=false,message=null);entry.start(null,state.value.draft)}
    fun open(record:LocalRecord,replaceRejected:Boolean=false) {
        pauseAutosave();val kind=AssetKind.entries.single {it.wire==record.kind}
        val draft=AssetDraft.from(record.id,record.greenhouseId,kind,record.body,record.baseVersion ?: record.serverVersion)
        mutable.value=state.value.copy(draft=draft.copy(replaceValidationRejected=replaceRejected,lockedParents=draft.lockedParents || record.serverVersion!=null || record.latestOperationId!=null),errors=emptyMap(),reviewing=false,saved=true,message=null)
        entry.start(record,state.value.draft)
    }
    fun hasChanges()=entry.changed(state.value.draft)
    fun clearMessage() {mutable.value=state.value.copy(message=null)}
    /** "Batal": closes the form and undoes its autosave, so nothing stays behind as a draft. */
    fun cancel() {
        val draft=state.value.draft ?: return;pauseAutosave()
        mutable.value=state.value.copy(draft=null,reviewing=false,errors=emptyMap(),message=null)
        entry.undo(viewModelScope,vault,draft.id,{session,ticket,id,origin -> repository.discard(session,ticket,id,origin)}) {
            mutable.value=state.value.copy(changed=state.value.changed+1,message=it)
        }
    }
    fun edit(change:(AssetDraft)->AssetDraft) {
        val draft=state.value.draft ?: return;val updated=change(draft);entry.touched=true
        mutable.value=state.value.copy(draft=updated,saved=false,reviewing=false,errors=emptyMap())
        pauseAutosave();saveJob=viewModelScope.launch {delay(350);save(updated,false,false)}
    }
    fun persist(session:FinanceSession,ticket:Long,draft:AssetDraft) {viewModelScope.launch {try {repository.flush(session,ticket,draft)} catch(cancelled:CancellationException) {throw cancelled} catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error))}}}
    fun pauseAutosave() {saveJob?.cancel();saveJob=null}
    fun review(limits:Map<String,String> = emptyMap()) {val draft=state.value.draft ?: return;val errors=draft.validation(reasonRequired)+limits;mutable.value=state.value.copy(errors=errors,reviewing=errors.isEmpty())}
    fun backToForm() {mutable.value=state.value.copy(reviewing=false)}
    fun saveAndClose() {val draft=state.value.draft ?: return;pauseAutosave();viewModelScope.launch {save(draft,false,true)}}
    fun queue() {val draft=state.value.draft ?: return;val errors=draft.validation(reasonRequired);if(errors.isNotEmpty()) {mutable.value=state.value.copy(errors=errors,reviewing=false);return};pauseAutosave();viewModelScope.launch {save(draft,true,true)}}
    private suspend fun save(draft:AssetDraft,queue:Boolean,close:Boolean) {
        val session=vault.current ?: return;val ticket=vault.generation
        if(queue || close) mutable.value=state.value.copy(busy=true)
        try {
            repository.save(session,ticket,draft,queue)
            // Pengelola waits for the server: a refusal reopens the form with the reason instead of joining the queue.
            val delivery=if(queue) com.inofarm.jogja.core.sync.ManagerDelivery.deliver(session,listOf(draft.id)) else null
            if(delivery is com.inofarm.jogja.core.sync.ManagerDelivery.Result.Rejected && delivery.replaceable && delivery.record!=null && vault.matches(ticket,session.scope) && state.value.draft?.id==draft.id) {
                open(delivery.record,true);mutable.value=state.value.copy(message=delivery.message,changed=state.value.changed+1);return
            }
            if(vault.matches(ticket,session.scope) && state.value.draft?.id==draft.id) mutable.value=state.value.copy(draft=if(close && state.value.draft==draft) null else state.value.draft,
                saved=state.value.draft==draft,reviewing=if(close) false else state.value.reviewing,changed=if(queue || close) state.value.changed+1 else state.value.changed,
                message=if(queue) (delivery?.message ?: com.inofarm.jogja.core.sync.ManagerDelivery.QUEUED) else if(close) "Draf aset tersimpan di perangkat." else state.value.message)
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(error:AssetValidationFailure) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(draft=state.value.draft?.copy(baseVersion=error.baseVersion),errors=error.fields,reviewing=false,message=error.message)}
        catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(reviewing=false,message=userMessage(error))}
        finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}
    }
}
