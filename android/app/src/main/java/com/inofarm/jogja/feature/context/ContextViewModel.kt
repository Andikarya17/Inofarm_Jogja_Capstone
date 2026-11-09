package com.inofarm.jogja.feature.context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class CultivationUiState(val loading:Boolean=false,val greenhouses:List<JSONObject> = emptyList(),val seasons:List<JSONObject> = emptyList(),val groups:List<JSONObject> = emptyList(),
    val materials:List<JSONObject> = emptyList(),val units:List<JSONObject> = emptyList(),val schedules:List<JSONObject> = emptyList(),val targets:List<JSONObject> = emptyList(),val message:String?=null,val lastSyncedAt:Long?=null,val grades:List<JSONObject> = emptyList(),val harvestBalances:List<JSONObject> = emptyList(),val expenseCategories:List<JSONObject> = emptyList(),val expenses:List<JSONObject> = emptyList(),val expenseBalances:List<JSONObject> = emptyList(),val harvests:List<JSONObject> = emptyList(),val assets:List<JSONObject> = emptyList(),
    val check: ContextCheck = ContextCheck.UNCHECKED)
class ContextViewModel(private val repository:ContextRepository):ViewModel() {
    private val vault get()=repository.vault
    private val mutable=MutableStateFlow(CultivationUiState());val state=mutable.asStateFlow();private var job:Job?=null
    private var boundScope:String?=null
    fun bind(session:FinanceSession?) {
        val identity=session?.let {"${it.scope.key}:${it.role}"}
        if(boundScope==identity) return
        boundScope=identity
        job?.cancel();mutable.value=CultivationUiState(loading=session!=null)
        if(session!=null) job=viewModelScope.launch { synchronize(loadCache = true) }
    }
    private suspend fun loadLocal(session:FinanceSession) {
        val ticket=vault.generation
        val records=repository.snapshot(session)
        val data=state.value.copy(greenhouses=records.greenhouses,seasons=records.seasons,groups=records.groups,materials=records.materials,units=records.units,schedules=records.schedules,targets=records.targets,lastSyncedAt=records.lastSyncedAt,grades=records.grades,harvestBalances=records.harvestBalances,expenseCategories=records.expenseCategories,expenses=records.expenses,expenseBalances=records.expenseBalances,harvests=records.harvests,assets=records.assets)
        if(vault.matches(ticket,session.scope)) mutable.value=data
    }
    fun refresh() { if(job?.isActive!=true) job=viewModelScope.launch { synchronize() } }
    private suspend fun synchronize(loadCache: Boolean = false) {
        val session=vault.current ?: return;val ticket=vault.generation
        mutable.value=state.value.copy(loading=true,message="Memeriksa server…")
        try {
            if (loadCache) loadLocal(session)
            val result=repository.synchronize()
            if (vault.matches(ticket,session.scope)) {
                loadLocal(vault.current ?: session)
                mutable.value=state.value.copy(message=result.message,check=result.contextCheck)
            }
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) {
            if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(message=userMessage(error),check=ContextCheck.FAILED)
        }
        finally { if(vault.matches(ticket,session.scope)) mutable.value=state.value.copy(loading=false) }
    }
}
