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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.OffsetDateTime
import com.inofarm.jogja.feature.activities.domain.ActivityDraft

data class AdminState(val resource:AdminResource=AdminResource.GREENHOUSE,val greenhouse:String?=null,val rows:List<JSONObject> = emptyList(),val cursor:String?=null,
    val form:AdminForm?=null,val editing:Boolean=false,val pending:Boolean=false,val needsPassword:Boolean=false,val busy:Boolean=false,val saved:Boolean=true,
    val options:Map<String,List<JSONObject>> = emptyMap(),val message:String?=null,val rejected:Boolean=false,val server:JSONObject?=null,
    val listChecked:Boolean=false,val detail:JSONObject?=null,val readiness:Map<String,Int>?=null,
    val optionsLoading:Boolean=false,val period:JSONObject?=null,val setupGreenhouse:JSONObject?=null)

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
    fun choose(resource:AdminResource, greenhouse:String?=state.value.greenhouse) {
        if(state.value.busy) return
        mutable.value=state.value.copy(resource=resource,greenhouse=greenhouse,rows=emptyList(),cursor=null,
            detail=null,readiness=null,period=null,listChecked=false)
        reload()
    }
    fun greenhouse(id:String) = choose(state.value.resource,id)
    fun setup(resource: AdminResource, row: JSONObject) {
        if (state.value.busy) return
        mutable.value = state.value.copy(setupGreenhouse = row)
        choose(resource, row.getString("id"))
    }
    fun returnToGreenhouse() {
        val row = state.value.setupGreenhouse ?: return
        if (state.value.busy) return
        mutable.value = state.value.copy(resource = AdminResource.GREENHOUSE, setupGreenhouse = null,
            rows = emptyList(), cursor = null, listChecked = false)
        detail(row)
    }
    fun reload(more:Boolean=false) {if(!state.value.busy) viewModelScope.launch {load(more)}}
    private suspend fun load(more:Boolean=false) {
        mutable.value=state.value.copy(busy=true,message=null,listChecked=false)
        try {
            val greenhouses=repository.options(session,ticket,"greenhouses",null)
            mutable.value=state.value.copy(options=state.value.options+("greenhouses" to greenhouses))
            val resource=state.value.resource
            val greenhouse=state.value.greenhouse
            if(resource!=AdminResource.GREENHOUSE && greenhouse==null) return
            val page=repository.list(session,ticket,resource,greenhouse,if(more) state.value.cursor else null)
            val data=page.getJSONArray("data");val rows=(0 until data.length()).map(data::getJSONObject)
            mutable.value=state.value.copy(rows=if(more) (state.value.rows+rows).distinctBy {it.getString("id")} else rows,
                cursor=page.optString("next_cursor").takeUnless {it.isBlank() || it=="null"},listChecked=true)
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error))}
        finally {mutable.value=state.value.copy(busy=false)}
    }
    fun detail(row:JSONObject) {
        if(state.value.busy) return
        mutable.value=state.value.copy(detail=row,readiness=null,period=null,message=null,busy=true)
        viewModelScope.launch {
            try {
                val id=row.getString("id")
                val resource=state.value.resource
                val greenhouse=if(resource==AdminResource.GREENHOUSE) id else state.value.greenhouse
                val assignments=repository.options(session,ticket,"assignments",greenhouse)
                val counts=if(resource==AdminResource.USER) {
                    mapOf("Penugasan di greenhouse ini" to assignments.count {it.optString("user_id")==id})
                } else {
                    val seasons=repository.options(session,ticket,"seasons",id)
                        .filter {it.optString("status")=="open"}.map {it.getString("id")}.toSet()
                    mapOf("Penugasan terdaftar" to assignments.size, "Musim terbuka" to seasons.size,
                        "Kelompok aktif pada musim terbuka" to repository.options(session,ticket,"planting-groups",id)
                            .count {it.optBoolean("active") && it.optString("season_id") in seasons},
                        "Bahan aktif" to repository.options(session,ticket,"materials",id).count {it.optBoolean("active")},
                        "Satuan aktif" to repository.options(session,ticket,"units",id).count {it.optBoolean("active")},
                        "Grade aktif" to repository.options(session,ticket,"grades",id).count {it.optBoolean("active")})
                }
                mutable.value=state.value.copy(readiness=counts)
                if (resource == AdminResource.GREENHOUSE) {
                    mutable.value = state.value.copy(period = repository.greenhousePeriod(session, ticket, id))
                }
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error))}
            finally {mutable.value=state.value.copy(busy=false)}
        }
    }
    fun closeDetail() {
        if(!state.value.busy) {
            val needsReload = !state.value.listChecked
            mutable.value=state.value.copy(detail=null,readiness=null,period=null,message=null)
            if (needsReload) reload()
        }
    }
    fun open(row:JSONObject?=null,action:String="update") {
        if(state.value.busy || !loaded) return
        if(state.value.form!=null) {resume();return}
        val form=if(row==null) AdminForm(state.value.resource,values=buildMap {
            state.value.greenhouse?.let {put("greenhouse_id",it)}
            if(state.value.resource==AdminResource.ASSIGNMENT)
                put("effective_from",OffsetDateTime.now(ActivityDraft.WIB).withNano(0).toString())
        }) else AdminForm.edit(state.value.resource,row,action)
        mutable.value=state.value.copy(form=form,editing=true,message=null,detail=null,readiness=null)
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
        val values=if(key=="greenhouse_id" && form.resource==AdminResource.ASSIGNMENT) form.values-"user_id" else form.values
        mutable.value=state.value.copy(form=form.copy(values=values+(key to value)),message=null)
        autosave?.cancel();val snapshot=snapshot()
        autosave=viewModelScope.launch {delay(250);try {persist(snapshot)} catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error))}}
        if(key=="greenhouse_id") loadOptions()
    }
    private var optionsJob:Job?=null
    fun findUnassigned(email:String) {
        val form=state.value.form ?: return
        val greenhouse=form.values["greenhouse_id"] ?: return
        if (state.value.busy || state.value.optionsLoading || email.isBlank()) return
        mutable.value=state.value.copy(optionsLoading=true,message=null)
        optionsJob=viewModelScope.launch {
            try {
                val candidate=repository.assignmentCandidate(session,ticket,greenhouse,email)
                val source="assignment-users"
                val rows=(state.value.options[source].orEmpty()+candidate).distinctBy {it.getString("id")}
                mutable.value=state.value.copy(options=state.value.options+(source to rows),message="Akun ditemukan. Pilih akun untuk ditugaskan.")
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error))}
            finally {if(kotlinx.coroutines.currentCoroutineContext().isActive) mutable.value=state.value.copy(optionsLoading=false)}
        }
    }
    private fun loadOptions() {
        optionsJob?.cancel()
        val form=state.value.form ?: return
        val gh=form.values["greenhouse_id"]?.takeIf {it.isNotBlank()} ?: state.value.greenhouse
        val sources=form.fields.mapNotNull {it.source}.distinct()
        mutable.value=state.value.copy(options=state.value.options-(sources-"greenhouses").toSet(),optionsLoading=sources.isNotEmpty())
        optionsJob=viewModelScope.launch {
            try {
                val result=mutableMapOf<String,List<JSONObject>>()
                sources.forEach {source ->
                    if(source!="assignment-users" || gh!=null) result[source]=repository.options(session,ticket,source,gh)
                }
                mutable.value=state.value.copy(options=state.value.options+result)
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error))}
            finally { if (kotlinx.coroutines.currentCoroutineContext().isActive) mutable.value=state.value.copy(optionsLoading=false) }
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
                journal=JSONObject();mutable.value=state.value.copy(form=null,editing=false,pending=false,needsPassword=false,rejected=false,server=null,
                    greenhouse=if(form.resource==AdminResource.GREENHOUSE && form.version==null) form.id else state.value.greenhouse)
                persist(snapshot());onAccepted();load()
                mutable.value=state.value.copy(message=if(state.value.listChecked) {
                    if(form.resource==AdminResource.USER && form.version==null) "Akun diterima server dan sudah ditugaskan ke greenhouse awal."
                    else "Perubahan diterima server dan tercatat dalam audit."
                } else "Perubahan diterima server. ${state.value.message ?: "Daftar belum diperiksa ulang."}")
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
