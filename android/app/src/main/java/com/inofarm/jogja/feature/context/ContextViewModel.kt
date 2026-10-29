package com.inofarm.jogja.feature.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.core.sync.FinanceSyncCoordinator
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CultivationUiState(val loading:Boolean=false,val greenhouses:List<JSONObject> = emptyList(),val seasons:List<JSONObject> = emptyList(),val groups:List<JSONObject> = emptyList(),
    val materials:List<JSONObject> = emptyList(),val units:List<JSONObject> = emptyList(),val schedules:List<JSONObject> = emptyList(),val targets:List<JSONObject> = emptyList(),val message:String?=null,val lastSyncedAt:Long?=null,val grades:List<JSONObject> = emptyList(),val harvestBalances:List<JSONObject> = emptyList(),val expenseCategories:List<JSONObject> = emptyList(),val expenses:List<JSONObject> = emptyList(),val expenseBalances:List<JSONObject> = emptyList())
class ContextViewModel(private val repository:ContextRepository):ViewModel() {
    private val vault get()=repository.vault
    private val mutable=MutableStateFlow(CultivationUiState());val state=mutable.asStateFlow();private var job:Job?=null
    private var boundScope:String?=null
    fun bind(session:FinanceSession?) {
        if(boundScope==session?.scope?.key) return
        boundScope=session?.scope?.key
        job?.cancel();mutable.value=CultivationUiState()
        if(session!=null) job=viewModelScope.launch { loadLocal(session);synchronize() }
    }
    private suspend fun loadLocal(session:FinanceSession) {
        val ticket=vault.generation
        val records=repository.snapshot(session)
        val data=CultivationUiState(greenhouses=records.greenhouses,seasons=records.seasons,groups=records.groups,materials=records.materials,units=records.units,schedules=records.schedules,targets=records.targets,message=mutable.value.message,lastSyncedAt=records.lastSyncedAt,grades=records.grades,harvestBalances=records.harvestBalances,expenseCategories=records.expenseCategories,expenses=records.expenses,expenseBalances=records.expenseBalances)
        if(vault.matches(ticket,session.scope)) mutable.value=data
    }
    fun refresh() { if(job?.isActive!=true) job=viewModelScope.launch { synchronize() } }
    private suspend fun synchronize() {
        val session=vault.current ?: return;val ticket=vault.generation
        mutable.value=state.value.copy(loading=true,message="Memeriksa server…")
        try { val result=repository.synchronize();if(vault.matches(ticket,session.scope)) { mutable.value=state.value.copy(message=result.message);loadLocal(session) } }
        catch(cancelled:CancellationException) { throw cancelled } catch(error:Exception) { if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error)) }
        finally { if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(loading=false) }
    }
}
