package com.inofarm.jogja.feature.harvest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.harvest.domain.HarvestDraft
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class HarvestUiState(val records:List<LocalRecord> = emptyList(),val queue:List<QueuedOperation> = emptyList(),val draft:HarvestDraft?=null,val errors:Map<String,String> = emptyMap(),val busy:Boolean=false,val reviewing:Boolean=false,val saved:Boolean=false,val message:String?=null,val changed:Long=0)
class HarvestViewModel(private val repository:HarvestRepository):ViewModel() {
    private val vault get()=repository.vault
    private val mutable=MutableStateFlow(HarvestUiState());val state=mutable.asStateFlow();private var saveJob:Job?=null
    private var boundScope:String?=null
    fun bind(session:FinanceSession?) {
        if(boundScope==session?.scope?.key) {if(session!=null) reload();return}
        boundScope=session?.scope?.key;saveJob?.cancel();mutable.value=HarvestUiState();if(session!=null) reload()
    }
    fun reload() {
        val session=vault.current ?: return;val ticket=vault.generation
        viewModelScope.launch { val data=repository.snapshot(session)
            if(vault.matches(ticket,session.scope)) {
                val draft=state.value.draft
                val known=draft?.let { d -> data.records.find {it.id==d.id}?.baseVersion }
                mutable.value=state.value.copy(records=data.records,queue=data.queue,draft=if(draft?.baseVersion==null && known!=null) draft?.copy(baseVersion=known) else draft)
            } }
    }
    fun new(gh:String,group:String) {saveJob?.cancel();mutable.value=state.value.copy(draft=HarvestDraft(greenhouseId=gh,groupId=group),errors=emptyMap(),saved=false,reviewing=false,message=null)}
    fun open(record:LocalRecord,replaceRejected:Boolean=false) {
        saveJob?.cancel()
        val accepted=record.serverBody?.optJSONArray("items")
        val locked=accepted?.let {array -> (0 until array.length()).map {array.getJSONObject(it).getString("id")}.toSet()} ?: emptySet()
        mutable.value=state.value.copy(draft=HarvestDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion ?: record.serverVersion).let {if(replaceRejected) it.copy(replaceValidationRejected=true,lockedItems=locked) else it},saved=true,errors=emptyMap(),reviewing=false,message=null)
    }
    fun edit(change:(HarvestDraft)->HarvestDraft) {
        val draft=state.value.draft ?: return
        val updated=change(draft);mutable.value=state.value.copy(draft=updated,saved=false,reviewing=false,errors=emptyMap())
        saveJob?.cancel();saveJob=viewModelScope.launch { delay(350);save(updated,false,false) }
    }
    fun review() { val draft=state.value.draft ?: return;val errors=draft.validation();mutable.value=state.value.copy(errors=errors,reviewing=errors.isEmpty()) }
    fun backToForm() { mutable.value=state.value.copy(reviewing=false) }
    fun saveAndClose() { val draft=state.value.draft ?: return;saveJob?.cancel();viewModelScope.launch { save(draft,false,true) } }
    fun queue() { val draft=state.value.draft ?: return;val errors=draft.validation();if(errors.isNotEmpty()) { mutable.value=state.value.copy(errors=errors,reviewing=false);return };saveJob?.cancel();viewModelScope.launch { save(draft,true,true) } }
    private suspend fun save(draft:HarvestDraft,queue:Boolean,close:Boolean) {
        val session=vault.current ?: return;val ticket=vault.generation
        if(queue || close) mutable.value=state.value.copy(busy=true)
        try {
            repository.save(session,ticket,draft,queue)
            if(vault.matches(ticket,session.scope) && state.value.draft?.id==draft.id) {
                mutable.value=state.value.copy(draft=if(close) null else state.value.draft,saved=state.value.draft==draft,reviewing=if(close) false else state.value.reviewing,
                    changed=if(queue) state.value.changed+1 else state.value.changed,message=if(queue) "Tersimpan lokal dan masuk antrean. Belum diterima server." else if(close) "Draf tersimpan di perangkat." else state.value.message)
                reload()
            }
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(error:HarvestValidationFailure) { if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(draft=state.value.draft?.copy(baseVersion=error.baseVersion),errors=error.fields,reviewing=false,message=error.message) }
        catch(error:Exception) { if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error)) }
        finally { if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false) }
    }
}
