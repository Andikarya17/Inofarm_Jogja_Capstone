package com.inofarm.jogja.feature.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SaleEntryState(val draft:SaleDraft?=null,val errors:Map<String,String> = emptyMap(),val busy:Boolean=false,
    val reviewing:Boolean=false,val saved:Boolean=false,val message:String?=null,val changed:Long=0)
class SaleEntryViewModel(private val repository:SaleRepository):ViewModel() {
    private val vault get()=repository.vault
    private val mutable=MutableStateFlow(SaleEntryState());val state=mutable.asStateFlow()
    private var saveJob:Job?=null;private var scope:String?=null
    fun bind(session:FinanceSession?) {
        if(scope==session?.scope?.key) return
        scope=session?.scope?.key;pauseAutosave();mutable.value=SaleEntryState()
    }
    fun new(gh:String) {pauseAutosave();mutable.value=state.value.copy(draft=SaleDraft(greenhouseId=gh),errors=emptyMap(),reviewing=false,saved=false,message=null)}
    fun open(record:LocalRecord,replaceRejected:Boolean=false) {
        pauseAutosave();require(record.kind=="sale")
        val draft=SaleDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion ?: record.serverVersion)
        mutable.value=state.value.copy(draft=draft.copy(replaceValidationRejected=replaceRejected,lockedSeason=draft.lockedSeason || record.serverVersion!=null || record.latestOperationId!=null,lockedSources=draft.lockedSources+(record.serverBody?.optJSONArray("items")?.let {rows -> (0 until rows.length()).map {rows.getJSONObject(it).getString("id")}} ?: emptyList())),errors=emptyMap(),reviewing=false,saved=true,message=null)
    }
    fun edit(change:(SaleDraft)->SaleDraft) {
        val draft=state.value.draft ?: return;val updated=change(draft)
        mutable.value=state.value.copy(draft=updated,saved=false,reviewing=false,errors=emptyMap())
        pauseAutosave();saveJob=viewModelScope.launch {delay(350);save(updated,false,false)}
    }
    fun pauseAutosave() {saveJob?.cancel();saveJob=null}
    fun review() {val draft=state.value.draft ?: return;val errors=draft.validation();mutable.value=state.value.copy(errors=errors,reviewing=errors.isEmpty())}
    fun backToForm() {mutable.value=state.value.copy(reviewing=false)}
    fun saveAndClose() {val draft=state.value.draft ?: return;pauseAutosave();viewModelScope.launch {save(draft,false,true)}}
    fun queue() {val draft=state.value.draft ?: return;val errors=draft.validation();if(errors.isNotEmpty()) {mutable.value=state.value.copy(errors=errors,reviewing=false);return};pauseAutosave();viewModelScope.launch {save(draft,true,true)}}
    private suspend fun save(draft:SaleDraft,queue:Boolean,close:Boolean) {
        val session=vault.current ?: return;val ticket=vault.generation
        if(queue || close) mutable.value=state.value.copy(busy=true)
        try {
            repository.save(session,ticket,draft,queue)
            if(vault.matches(ticket,session.scope) && state.value.draft?.id==draft.id) mutable.value=state.value.copy(draft=if(close) null else state.value.draft,
                saved=state.value.draft==draft,reviewing=if(close) false else state.value.reviewing,changed=if(queue || close) state.value.changed+1 else state.value.changed,
                message=if(queue) "Tersimpan lokal dan masuk antrean. Belum diterima server." else if(close) "Draf penjualan tersimpan di perangkat." else state.value.message)
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(error:SaleValidationFailure) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(draft=state.value.draft?.copy(baseVersion=error.baseVersion),errors=error.fields,reviewing=false,message=error.message)}
        catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error))}
        finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}
    }
}
