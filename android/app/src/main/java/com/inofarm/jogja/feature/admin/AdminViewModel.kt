package com.inofarm.jogja.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AdminState(val resource:AdminResource=AdminResource.GREENHOUSE,val greenhouse:String?=null,val rows:List<JSONObject> = emptyList(),val cursor:String?=null,
    val form:AdminForm?=null,val editing:Boolean=false,val pending:Boolean=false,val needsPassword:Boolean=false,val busy:Boolean=false,val saved:Boolean=true,
    val options:Map<String,List<JSONObject>> = emptyMap(),val message:String?=null,val rejected:Boolean=false,val server:JSONObject?=null)

class AdminViewModel(private val repository:AdminRepository,private val session:FinanceSession):ViewModel() {
    private val ticket=repository.vault.generation
    private val mutable=MutableStateFlow(AdminState(busy=true));val state=mutable.asStateFlow()
    private var journal=JSONObject();private var revision=0L;private var autosave:Job?=null
    private var loaded=false
    init {viewModelScope.launch {
        try {
            journal=withContext(Dispatchers.IO) {repository.read(session,ticket)};revision=journal.optLong("revision");loaded=true
            val form=journal.optJSONObject("form")?.let(AdminForm::parse)
            mutable.value=state.value.copy(form=form,pending=journal.has("pending"),needsPassword=journal.optBoolean("needs_password"),rejected=journal.optBoolean("rejected"),server=journal.optJSONObject("server"))
            load()
        } catch(error:Exception) {mutable.value=state.value.copy(busy=false,message=userMessage(error))}
    }}
    private fun snapshot():JSONObject=JSONObject(journal.toString()).put("revision",++revision).apply {
        state.value.form?.let {put("form",it.json())} ?: remove("form")
    }.also {journal=it;mutable.value=state.value.copy(saved=false)}
    private suspend fun persist(value:JSONObject) {
        withContext(Dispatchers.IO) {repository.write(session,ticket,value)}
        if(value.optLong("revision")==revision) mutable.value=state.value.copy(saved=true)
    }
    fun flush():Boolean {
        if(!loaded) return true
        if(state.value.busy) return false
        autosave?.cancel();val value=snapshot()
        return try {repository.write(session,ticket,value);mutable.value=state.value.copy(saved=true);true}
        catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error));false}
    }
    fun choose(resource:AdminResource) {if(state.value.busy) return;mutable.value=state.value.copy(resource=resource,rows=emptyList(),cursor=null);reload()}
    fun greenhouse(id:String) {if(state.value.busy) return;mutable.value=state.value.copy(greenhouse=id,rows=emptyList(),cursor=null);reload()}
    fun reload(more:Boolean=false) {if(!state.value.busy) viewModelScope.launch {load(more)}}
    private suspend fun load(more:Boolean=false) {
        mutable.value=state.value.copy(busy=true,message=null)
        try {
            val page=repository.list(session,ticket,state.value.resource,state.value.greenhouse,if(more) state.value.cursor else null)
            val data=page.getJSONArray("data");val rows=(0 until data.length()).map(data::getJSONObject)
            val greenhouses=repository.options(session,ticket,"greenhouses",null)
            mutable.value=state.value.copy(rows=if(more) (state.value.rows+rows).distinctBy {it.getString("id")} else rows,cursor=page.optString("next_cursor").takeUnless {it.isBlank() || it=="null"},options=state.value.options+("greenhouses" to greenhouses),message="Daftar diperiksa dari server.")
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error))}
        finally {mutable.value=state.value.copy(busy=false)}
    }
    fun open(row:JSONObject?=null,action:String="update") {
        if(state.value.busy || !loaded) return
        if(state.value.form!=null) {resume();return}
        val form=if(row==null) AdminForm(state.value.resource,values=state.value.greenhouse?.let {mapOf("greenhouse_id" to it)} ?: emptyMap()) else AdminForm.edit(state.value.resource,row,action)
        mutable.value=state.value.copy(form=form,editing=true,message=null)
        flush();loadOptions()
    }
    fun resume() {mutable.value=state.value.copy(editing=true);loadOptions()}
    fun back() {if(!state.value.busy && flush()) mutable.value=state.value.copy(editing=false)}
    fun discard() {
        if(state.value.busy || state.value.pending) return
        mutable.value=state.value.copy(form=null,editing=false,message="Draf administrasi dihapus atas pilihan Anda.")
        journal=JSONObject();flush()
    }
    fun edit(key:String,value:String) {
        if(state.value.pending || state.value.busy) return
        val form=state.value.form ?: return
        mutable.value=state.value.copy(form=form.copy(values=form.values+(key to value)),message=null)
        autosave?.cancel();val snapshot=snapshot()
        autosave=viewModelScope.launch {delay(250);try {persist(snapshot)} catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error))}}
        if(key=="greenhouse_id") loadOptions()
    }
    private var optionsJob:Job?=null
    private fun loadOptions() {
        optionsJob?.cancel()
        val form=state.value.form ?: return
        val gh=form.values["greenhouse_id"]?.takeIf {it.isNotBlank()} ?: state.value.greenhouse
        optionsJob=viewModelScope.launch {
            try {
                val result=mutableMapOf<String,List<JSONObject>>()
                form.fields.mapNotNull {it.source}.distinct().forEach {source ->
                    if(source!="users" || gh!=null) result[source]=repository.options(session,ticket,source,gh)
                }
                mutable.value=state.value.copy(options=state.value.options+result)
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error))}
        }
    }
    fun reviseRejected() {
        if(!state.value.rejected || state.value.busy) return
        // Only definitive validation/version rejection may be replaced after explicit review.
        journal.remove("pending");journal.remove("hash");journal.remove("needs_password");journal.remove("rejected");journal.remove("server")
        mutable.value=state.value.copy(pending=false,needsPassword=false,rejected=false,server=null,message="Draf lokal dipertahankan. Versi dasar tidak diganti otomatis; bandingkan dan muat ulang sebelum koreksi konflik.")
        flush()
    }
    fun useServer() {
        val row=state.value.server ?: return;val form=state.value.form ?: return
        if(!state.value.rejected || state.value.busy) return
        journal=JSONObject();mutable.value=state.value.copy(form=AdminForm.edit(form.resource,row,form.action),pending=false,needsPassword=false,rejected=false,server=null,message="Versi server dimuat atas pilihan Anda.")
        flush();loadOptions()
    }
    fun submit(password:String,onAccepted:()->Unit) {
        if(state.value.busy) return
        val form=state.value.form ?: return
        autosave?.cancel();mutable.value=state.value.copy(busy=true,message=null)
        viewModelScope.launch {
            try {
                persist(snapshot())
                if(!journal.has("pending")) journal=repository.prepare(session,ticket,form,password,journal)
                mutable.value=state.value.copy(pending=true,needsPassword=journal.optBoolean("needs_password"))
                repository.send(session,ticket,journal,password)
                journal=JSONObject();mutable.value=state.value.copy(form=null,editing=false,pending=false,needsPassword=false,rejected=false,server=null)
                persist(snapshot());onAccepted();load()
                mutable.value=state.value.copy(message="Perubahan diterima server dan tercatat dalam audit.")
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {
                val definitive=error is FinanceApiFailure && (error.status==422 || error.code=="version_conflict")
                journal.put("rejected",definitive)
                if(error is FinanceApiFailure) error.detail.optJSONObject("current_data")?.let {journal.put("server",it)}
                mutable.value=state.value.copy(pending=journal.has("pending"),needsPassword=journal.optBoolean("needs_password"),rejected=definitive,server=journal.optJSONObject("server"),message=userMessage(error))
                try {persist(snapshot())} catch(saveError:Exception) {mutable.value=state.value.copy(message=userMessage(saveError))}
            } finally {mutable.value=state.value.copy(busy=false)}
        }
    }
}
