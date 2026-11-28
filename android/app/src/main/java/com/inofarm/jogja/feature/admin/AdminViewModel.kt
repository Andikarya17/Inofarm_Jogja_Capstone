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

/** The master lists a greenhouse needs before recording, in the order Kelola shows them. */
internal val MasterResources = listOf(AdminResource.VARIETY, AdminResource.MATERIAL, AdminResource.UNIT, AdminResource.GRADE, AdminResource.CATEGORY)

/** "Tersalin: 2 varietas, 10 bahan." from the copy-masters counts; nothing new is said plainly. */
internal fun copiedSummary(copied: JSONObject): String {
    val parts = listOf("varieties" to "varietas", "materials" to "bahan", "units" to "satuan", "grades" to "grade",
        "expense_categories" to "kategori biaya").mapNotNull { (key, label) -> copied.optInt(key).takeIf { it > 0 }?.let { "$it $label" } }
    return if (parts.isEmpty()) "Tidak ada yang disalin. Semua nama sudah ada di greenhouse ini."
    else "Tersalin: ${parts.joinToString(", ")}."
}

data class AdminState(val resource:AdminResource=AdminResource.GREENHOUSE,val greenhouse:String?=null,val rows:List<JSONObject> = emptyList(),val cursor:String?=null,
    val form:AdminForm?=null,val editing:Boolean=false,val pending:Boolean=false,val needsPassword:Boolean=false,val busy:Boolean=false,val saved:Boolean=true,
    val options:Map<String,List<JSONObject>> = emptyMap(),val message:String?=null,val rejected:Boolean=false,val server:JSONObject?=null,
    val listChecked:Boolean=false,
    val optionsLoading:Boolean=false,
    /** The greenhouse whose settings page is open; its counts are reloaded with the list after every save. */
    val settingsFor:String?=null,val settings:GreenhouseSettings?=null,
    /** The user changed a field since the form opened, so cancelling asks first. */
    val dirty:Boolean=false,
    /** The cancel question is showing. */
    val confirmCancel:Boolean=false,
    /** Another form the user asked to open while an unfinished one exists; the user decides whether to drop the old one. */
    val replace:AdminForm?=null)

class AdminViewModel(private val repository:AdminRepository,private val session:FinanceSession):ViewModel() {
    companion object {
        /**
         * The server refused the operation for good: sending it again cannot succeed. Session loss, timeouts and
         * rate limits are not final, and neither are network or server errors, which keep the operation for a retry.
         */
        fun definitive(error:Throwable):Boolean = error is FinanceApiFailure && error.status in 400..499 && error.status !in setOf(401,408,429)
        /**
         * Opening [target] continues [current] instead of replacing it: only a change to the same record. A new
         * record always asks, so "Tambah" never silently shows text typed earlier.
         */
        fun sameTarget(current:AdminForm,target:AdminForm):Boolean = target.version!=null && current.resource==target.resource &&
            current.action==target.action && current.id==target.id
    }
    private val ticket=repository.vault.generation
    private val mutable=MutableStateFlow(AdminState(busy=true));val state=mutable.asStateFlow()
    private var journal=JSONObject();private var revision=0L;private var autosave:Job?=null
    private var loaded=false
    init {viewModelScope.launch {
        try {
            journal=withContext(Dispatchers.IO) {repository.read(session,ticket)};revision=journal.optLong("revision");loaded=true
            val form=journal.optJSONObject("form")?.let(AdminForm::parse)
            mutable.value=state.value.copy(form=form,pending=journal.has("pending"),needsPassword=journal.optBoolean("needs_password"),rejected=journal.optBoolean("rejected"),server=journal.optJSONObject("server"),
                dirty=form!=null && journal.optBoolean("dirty",true))
            load()
        } catch(error:Exception) {mutable.value=state.value.copy(busy=false,message=userMessage(error))}
    }}
    private fun snapshot():JSONObject=JSONObject(journal.toString()).put("revision",++revision).apply {
        state.value.form?.let {put("form",it.json());put("dirty",state.value.dirty)} ?: run {remove("form");remove("dirty")}
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
            listChecked=false,settingsFor=null,settings=null,message=null)
        reload()
    }
    /**
     * Opens the settings page of one greenhouse. Always read again: setting its running period elsewhere bumps its version,
     * and a cached row would make the next save a false "data changed" conflict.
     */
    fun openGreenhouse(id:String) {
        if(state.value.busy) return
        mutable.value=state.value.copy(resource=AdminResource.GREENHOUSE,greenhouse=id,rows=emptyList(),cursor=null,
            listChecked=false,settingsFor=id,settings=null,message=null)
        reload()
    }
    /**
     * Ends the running period of the open greenhouse (planting_group_id null), the step the server asks for before the
     * greenhouse can be turned off. The greenhouse is read again afterwards, so its new version is the one saved next.
     */
    fun clearPeriod(onAccepted:()->Unit) {
        val id=state.value.settingsFor ?: return
        val row=state.value.rows.find {it.optString("id")==id} ?: return
        if(state.value.busy) return
        viewModelScope.launch {
            mutable.value=state.value.copy(busy=true,message=null)
            val message=try {
                val operation=repository.designateOperation(session,ticket,id,row.getLong("version"),null)
                repository.sendDesignation(session,ticket,id,operation)
                onAccepted()
                "Tanam saat ini dikosongkan."
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {userMessage(error)}
            load()
            mutable.value=state.value.copy(message=message)
        }
    }
    fun reload(more:Boolean=false) {if(!state.value.busy) viewModelScope.launch {load(more)}}
    /** Fills the selected greenhouse's master lists from [source]; the caller has already asked the manager. */
    fun copyMasters(source:String,onAccepted:()->Unit) {
        val target=state.value.greenhouse ?: return
        if(state.value.busy) return
        viewModelScope.launch {
            mutable.value=state.value.copy(busy=true,message=null)
            val message=try {
                val copied=repository.copyMasters(session,ticket,target,source)
                onAccepted()
                copiedSummary(copied)
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {userMessage(error)}
            mutable.value=state.value.copy(busy=false)
            load()
            mutable.value=state.value.copy(message=message)
        }
    }
    private suspend fun load(more:Boolean=false) {
        mutable.value=state.value.copy(busy=true,message=null,listChecked=false)
        try {
            val greenhouses=repository.options(session,ticket,"greenhouses",null)
            mutable.value=state.value.copy(options=state.value.options+("greenhouses" to greenhouses))
            val resource=state.value.resource
            // Accounts are listed whole; the first greenhouse only satisfies the API's required placement.
            if(resource==AdminResource.USER && state.value.greenhouse==null) {
                val placement=(greenhouses.firstOrNull {it.optBoolean("active",true)} ?: greenhouses.firstOrNull())?.getString("id")
                mutable.value=state.value.copy(greenhouse=placement,listChecked=placement==null)
            }
            val greenhouse=state.value.greenhouse
            if(resource!=AdminResource.GREENHOUSE && greenhouse==null) return
            val page=repository.list(session,ticket,resource,greenhouse,if(more) state.value.cursor else null)
            val data=page.getJSONArray("data");val rows=(0 until data.length()).map(data::getJSONObject)
            mutable.value=state.value.copy(rows=if(more) (state.value.rows+rows).distinctBy {it.getString("id")} else rows,
                cursor=page.optString("next_cursor").takeUnless {it.isBlank() || it=="null"},listChecked=true)
            val settings=state.value.settingsFor
            if(settings!=null && resource==AdminResource.GREENHOUSE && greenhouse==settings)
                mutable.value=state.value.copy(settings=repository.settings(session,ticket,settings))
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(error:Exception) {mutable.value=state.value.copy(message=userMessage(error))}
        finally {mutable.value=state.value.copy(busy=false)}
    }
    /**
     * Opens a new or existing record. One form is kept at a time (it survives the app being closed), but it never
     * blocks: opening something else asks whether to drop the unfinished form instead of reopening it.
     */
    fun open(row:JSONObject?=null,action:String="update") {
        if(state.value.busy || !loaded) return
        val target=if(row==null) AdminForm(state.value.resource,values=buildMap {
            state.value.greenhouse?.let {put("greenhouse_id",it)}
            if(state.value.resource==AdminResource.ASSIGNMENT)
                put("effective_from",OffsetDateTime.now(ActivityDraft.WIB).withNano(0).toString())
        }) else AdminForm.edit(state.value.resource,row,action)
        request(target)
    }
    private fun request(target:AdminForm) {
        val current=state.value.form
        when {
            current==null -> start(target)
            // An untouched form for an older version would only produce a false conflict; open the fresh row instead.
            sameTarget(current,target) && (state.value.dirty || current.version==target.version) -> resume()
            sameTarget(current,target) -> start(target)
            else -> mutable.value=state.value.copy(replace=target)
        }
    }
    private fun start(form:AdminForm,dirty:Boolean=false) {
        journal=JSONObject()
        mutable.value=state.value.copy(form=form,editing=true,dirty=dirty,message=null,
            pending=false,needsPassword=false,rejected=false,server=null,replace=null,confirmCancel=false)
        flush();loadOptions()
    }
    /** Drops the unfinished form and opens the one the user asked for. */
    fun confirmReplace() {
        val target=state.value.replace ?: return
        if(state.value.busy) return
        start(target)
    }
    fun keepCurrent() {mutable.value=state.value.copy(replace=null)}
    fun resume() {mutable.value=state.value.copy(editing=true,replace=null);loadOptions()}
    /** Cancel leaves at once when nothing would be lost, and asks first otherwise. */
    fun requestCancel() {
        if(state.value.busy) return
        if(state.value.dirty || state.value.pending) mutable.value=state.value.copy(confirmCancel=true) else discard()
    }
    fun dismissCancel() {mutable.value=state.value.copy(confirmCancel=false)}
    /**
     * Drops the form. A pending operation may already have reached the server (only the reply was lost), so the
     * list is reloaded and the message tells the manager to check it.
     */
    fun discard() {
        if(state.value.busy) return
        val wasPending=state.value.pending
        mutable.value=state.value.copy(form=null,editing=false,dirty=false,confirmCancel=false,pending=false,needsPassword=false,
            rejected=false,server=null,message=if(wasPending) "Pengiriman dibatalkan. Perubahan mungkin sudah tersimpan; periksa daftar." else null)
        journal=JSONObject();flush()
        if(wasPending) reload()
    }
    /**
     * Opens an existing record with some values already changed (a new role, active off), so the form goes straight to
     * its summary. Nothing is sent until the manager taps Simpan there.
     */
    fun openChanged(row:JSONObject,changes:Map<String,String>) {
        if(state.value.busy || !loaded) return
        val edit=AdminForm.edit(state.value.resource,row)
        val target=edit.copy(values=edit.values+changes)
        if(state.value.form==null) start(target,dirty=true) else mutable.value=state.value.copy(replace=target)
    }
    /** Turns a record on or off in one step from the list or detail; the caller has already asked the manager. */
    fun setActive(row:JSONObject,active:Boolean,onAccepted:()->Unit) {
        if(state.value.busy || !loaded) return
        val edit=AdminForm.edit(state.value.resource,row)
        val target=edit.copy(values=edit.values+("active" to active.toString()))
        if(state.value.form!=null) {request(target);return}
        start(target,dirty=true)
        mutable.value=state.value.copy(editing=false)
        submit("",onAccepted,done=if(active) "${target.resource.label} aktif lagi." else "${target.resource.label} dinonaktifkan.")
    }
    fun edit(key:String,value:String) {
        if(state.value.pending || state.value.busy) return
        val form=state.value.form ?: return
        val values=if(key=="greenhouse_id" && form.resource==AdminResource.ASSIGNMENT) form.values-"user_id" else form.values
        // A target's unit follows its parameter; picking it separately only let EC be saved as ppm and refused later.
        val unit=if(form.resource==AdminResource.TARGET && key=="parameter") AdminForm.TARGET_UNITS[value]?.let { mapOf("unit" to it) }.orEmpty() else emptyMap()
        mutable.value=state.value.copy(form=form.copy(values=values+(key to value)+unit),message=null,dirty=true)
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
                mutable.value=state.value.copy(options=state.value.options+(source to rows),message="Pekerja ditemukan. Pilih untuk ditambahkan.")
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
    /** After a version conflict the manager compared both versions and keeps editing their own values. */
    fun reviseRejected() {
        if(!state.value.rejected || state.value.busy) return
        unlock()
        mutable.value=state.value.copy(message="Isian Anda tetap ada. Bandingkan dengan data terbaru sebelum menyimpan lagi.")
        flush()
    }
    /** Forgets the refused operation so the fields can be edited and a new operation is built on the next save. */
    private fun unlock() {
        journal.remove("pending");journal.remove("hash");journal.remove("needs_password");journal.remove("rejected");journal.remove("server")
        mutable.value=state.value.copy(pending=false,needsPassword=false,rejected=false,server=null)
    }
    fun useServer() {
        val row=state.value.server ?: return;val form=state.value.form ?: return
        if(!state.value.rejected || state.value.busy) return
        journal=JSONObject();mutable.value=state.value.copy(form=AdminForm.edit(form.resource,row,form.action),pending=false,needsPassword=false,rejected=false,server=null,dirty=false,message="Data terbaru dipakai.")
        flush();loadOptions()
    }
    fun submit(password:String,onAccepted:()->Unit,done:String?=null) {
        if(state.value.busy) return
        val form=state.value.form ?: return
        autosave?.cancel();mutable.value=state.value.copy(busy=true,message=null,confirmCancel=false)
        viewModelScope.launch {
            try {
                persist(snapshot())
                if(!journal.has("pending")) journal=repository.prepare(session,ticket,form,password,journal)
                mutable.value=state.value.copy(pending=true,needsPassword=journal.optBoolean("needs_password"))
                repository.send(session,ticket,journal,password)
                journal=JSONObject();mutable.value=state.value.copy(form=null,editing=false,pending=false,needsPassword=false,rejected=false,server=null,dirty=false,
                    greenhouse=if(form.resource==AdminResource.GREENHOUSE && form.version==null) form.id else state.value.greenhouse)
                persist(snapshot());onAccepted();load()
                val saved=done ?: if(form.resource==AdminResource.USER && form.version==null) "Akun tersimpan." else "Tersimpan."
                mutable.value=state.value.copy(message=if(state.value.listChecked) saved else "$saved ${state.value.message ?: "Daftar belum diperbarui."}")
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {
                // A final refusal never leaves the form stuck on "Kirim ulang". A version conflict waits for the manager
                // to compare versions; every other refusal unlocks the fields at once so the values can be fixed.
                val conflict=error is FinanceApiFailure && error.code=="version_conflict"
                if(definitive(error) && !conflict) unlock()
                else {
                    journal.put("rejected",conflict)
                    if(error is FinanceApiFailure) error.detail.optJSONObject("current_data")?.let {journal.put("server",it)}
                }
                mutable.value=state.value.copy(pending=journal.has("pending"),needsPassword=journal.optBoolean("needs_password"),rejected=journal.optBoolean("rejected"),
                    server=journal.optJSONObject("server"),message=userMessage(error),editing=state.value.editing || definitive(error) || !journal.has("pending"))
                try {persist(snapshot())} catch(saveError:Exception) {mutable.value=state.value.copy(message=userMessage(saveError))}
            } finally {mutable.value=state.value.copy(busy=false)}
        }
    }
}
