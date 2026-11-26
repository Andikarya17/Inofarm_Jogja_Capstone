package com.inofarm.jogja.feature.sales

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

data class SaleEntryState(val draft:SaleDraft?=null,val errors:Map<String,String> = emptyMap(),val busy:Boolean=false,
    val reviewing:Boolean=false,val saved:Boolean=false,val message:String?=null,val changed:Long=0,
    /** Kg per harvest item that the sale being corrected holds on the server, counted back into the stock shown. */
    val credit:Map<String,java.math.BigDecimal> = emptyMap())
class SaleEntryViewModel(private val repository:SaleRepository):ViewModel() {
    private val vault get()=repository.vault
    private val reasonRequired get()=vault.current?.role!=com.inofarm.jogja.core.contracts.UserRole.MANAGER
    val reasonLabel get()=if(reasonRequired) "Alasan koreksi" else "Catatan (opsional)"
    private val mutable=MutableStateFlow(SaleEntryState());val state=mutable.asStateFlow()
    private var saveJob:Job?=null;private var scope:String?=null
    fun bind(session:FinanceSession?) {
        if(scope==session?.scope?.key) return
        scope=session?.scope?.key;pauseAutosave();mutable.value=SaleEntryState()
    }
    private val entry=EntryOrigin<SaleDraft>()
    fun new(gh:String) {pauseAutosave();mutable.value=state.value.copy(draft=SaleDraft(greenhouseId=gh,items=emptyList()),errors=emptyMap(),reviewing=false,saved=false,message=null,credit=emptyMap());entry.start(null,state.value.draft)}
    /** The open season the form found for its greenhouse. Before the first edit it is part of the untouched form. */
    fun settleSeason(seasonId:String) {
        val draft=state.value.draft ?: return
        if(draft.seasonId.isNotBlank() || draft.lockedSeason || draft.baseVersion!=null) return
        val updated=draft.copy(seasonId=seasonId);mutable.value=state.value.copy(draft=updated)
        if(!entry.touched) entry.start(null,updated)
    }
    /**
     * "Ganti GH": a record cannot move to another greenhouse, so the form starts a new sale there. Buyer, payment and
     * notes are kept; the fruit lines belong to the old greenhouse's stock and are cleared.
     */
    fun changeGreenhouse(gh:String) {
        val draft=state.value.draft ?: return
        if(draft.greenhouseId==gh || draft.baseVersion!=null) return
        pauseAutosave()
        entry.undo(viewModelScope,vault,draft.id,{session,ticket,id,origin -> repository.discard(session,ticket,id,origin)}) {
            mutable.value=state.value.copy(changed=state.value.changed+1,message=it)
        }
        val fresh=draft.copy(id=java.util.UUID.randomUUID().toString(),greenhouseId=gh,seasonId="",items=emptyList(),lockedSources=emptySet(),lockedSeason=false)
        entry.start(null,SaleDraft(id=fresh.id,greenhouseId=gh,items=emptyList()))
        mutable.value=state.value.copy(draft=fresh,errors=emptyMap(),reviewing=false,saved=false,message=null,credit=emptyMap())
    }
    fun hasChanges()=entry.changed(state.value.draft)
    /** "Batal": closes the form and undoes its autosave, so nothing stays behind as a draft. */
    fun cancel() {
        val draft=state.value.draft ?: return;pauseAutosave()
        mutable.value=state.value.copy(draft=null,reviewing=false,errors=emptyMap(),message=null)
        entry.undo(viewModelScope,vault,draft.id,{session,ticket,id,origin -> repository.discard(session,ticket,id,origin)}) {
            mutable.value=state.value.copy(changed=state.value.changed+1,message=it)
        }
    }
    /** [stock] maps lines of drafts saved before sale lines existed (a harvest item) to their fruit and grade. */
    fun open(record:LocalRecord,replaceRejected:Boolean=false,stock:SaleStock=SaleStock.EMPTY) {
        pauseAutosave();require(record.kind=="sale")
        val draft=SaleDraft.from(record.id,record.greenhouseId,record.body,record.baseVersion ?: record.serverVersion,stock)
        // A rejected send comes back with the server's reasons on each line (field errors, or the kg a line still has).
        val rejected=SaleErrors.fromServer(record.error,draft.items)
        mutable.value=state.value.copy(credit=SaleStock.creditOf(record.serverBody),draft=draft.copy(replaceValidationRejected=replaceRejected,lockedSeason=draft.lockedSeason || record.serverVersion!=null || record.latestOperationId!=null,lockedSources=draft.lockedSources+((record.serverBody?.let {it.optJSONArray("lines") ?: it.optJSONArray("items")})?.let {rows -> (0 until rows.length()).map {rows.getJSONObject(it).getString("id")}} ?: emptyList())),errors=rejected,reviewing=false,saved=true,message=null)
        entry.start(record,state.value.draft)
    }
    fun edit(change:(SaleDraft)->SaleDraft) {
        val draft=state.value.draft ?: return;val updated=change(draft);entry.touched=true
        mutable.value=state.value.copy(draft=updated,saved=false,reviewing=false,errors=emptyMap())
        pauseAutosave();saveJob=viewModelScope.launch {delay(350);save(updated,false,false)}
    }
    fun pauseAutosave() {saveJob?.cancel();saveJob=null}
    fun review() {val draft=state.value.draft ?: return;val errors=draft.validation(reasonRequired);mutable.value=state.value.copy(errors=errors,reviewing=errors.isEmpty())}
    fun backToForm() {mutable.value=state.value.copy(reviewing=false)}
    fun saveAndClose() {val draft=state.value.draft ?: return;pauseAutosave();viewModelScope.launch {save(draft,false,true)}}
    fun queue() {val draft=state.value.draft ?: return;val errors=draft.validation(reasonRequired);if(errors.isNotEmpty()) {mutable.value=state.value.copy(errors=errors,reviewing=false);return};pauseAutosave();viewModelScope.launch {save(draft,true,true)}}
    private suspend fun save(draft:SaleDraft,queue:Boolean,close:Boolean) {
        val session=vault.current ?: return;val ticket=vault.generation
        if(queue || close) mutable.value=state.value.copy(busy=true)
        try {
            repository.save(session,ticket,draft,queue)
            if(vault.matches(ticket,session.scope) && state.value.draft?.id==draft.id) mutable.value=state.value.copy(draft=if(close) null else state.value.draft,
                saved=state.value.draft==draft,reviewing=if(close) false else state.value.reviewing,changed=if(queue || close) state.value.changed+1 else state.value.changed,
                message=if(queue) "Tersimpan. Terkirim otomatis saat ada internet." else if(close) "Draf penjualan tersimpan di perangkat." else state.value.message)
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(error:SaleValidationFailure) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(draft=state.value.draft?.copy(baseVersion=error.baseVersion),errors=error.fields,reviewing=false,message=error.message)}
        catch(error:Exception) {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error))}
        finally {if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(busy=false)}
    }
}
