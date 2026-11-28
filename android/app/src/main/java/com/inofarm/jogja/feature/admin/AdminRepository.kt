package com.inofarm.jogja.feature.admin

import android.content.Context
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.core.session.PayloadCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/** A separate, encrypted admin journal; credentials are never written to disk or the sync queue. */
class AdminRepository(context:Context,val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val api:FinanceApi,private val cipher:PayloadCipher,preferenceName:String="finance_admin_v2") {
    private val preferences=context.getSharedPreferences(preferenceName,Context.MODE_PRIVATE)
    private fun current(session:FinanceSession,ticket:Long) {
        check(vault.matches(ticket,session.scope) && vault.current?.token==session.token && vault.current?.role==UserRole.MANAGER) {"Akun yang masuk berubah. Isian sebelumnya tetap tersimpan."}
    }
    @Synchronized fun read(session:FinanceSession,ticket:Long):JSONObject {
        current(session,ticket)
        return preferences.getString(session.scope.key,null)?.let {JSONObject(cipher.decode(session.scope.key,it))} ?: JSONObject()
    }
    @Synchronized fun write(session:FinanceSession,ticket:Long,journal:JSONObject) {
        current(session,ticket)
        val previous=read(session,ticket)
        if(journal.optLong("revision")<previous.optLong("revision")) return
        check(preferences.edit().putString(session.scope.key,cipher.encode(session.scope.key,journal.toString())).commit()) {"Draf administrasi belum berhasil disimpan."}
    }
    suspend fun list(session:FinanceSession,ticket:Long,resource:AdminResource,greenhouse:String?,cursor:String?=null,forAssignment:Boolean=false):JSONObject=withContext(Dispatchers.IO) {
        current(session,ticket)
        if(resource==AdminResource.USER) require(!greenhouse.isNullOrBlank()) {"Pilih greenhouse untuk daftar akun."}
        val query=mutableMapOf("limit" to "100")
        if(forAssignment) query["for_assignment"]="1"
        // Every account, not only those assigned to the greenhouse; the API still needs a greenhouse to place the record.
        else if(resource==AdminResource.USER) query["all_accounts"]="1"
        if(resource !in setOf(AdminResource.SCHEDULE,AdminResource.TARGET) && !greenhouse.isNullOrBlank()) query["greenhouse_id"]=greenhouse
        cursor?.let {query["cursor"]=it}
        val page=api.request(session.scope.server,session.token,resource.path,query=query)
        current(session,ticket)
        if(resource in setOf(AdminResource.SCHEDULE,AdminResource.TARGET) && !greenhouse.isNullOrBlank()) {
            val groups=options(session,ticket,"planting-groups",greenhouse).map {it.getString("id")}.toSet()
            val rows=page.getJSONArray("data")
            page.put("data",org.json.JSONArray((0 until rows.length()).map(rows::getJSONObject).filter {it.getString("planting_group_id") in groups}))
        }
        page
    }
    suspend fun options(session:FinanceSession,ticket:Long,source:String,greenhouse:String?):List<JSONObject> {
        val forAssignment=source=="assignment-users"
        val resource=if(forAssignment) AdminResource.USER else AdminResource.entries.single {it.path==source}
        val rows=mutableListOf<JSONObject>();val seen=mutableSetOf<String>();var cursor:String?=null
        do {
            val page=list(session,ticket,resource,if(resource==AdminResource.GREENHOUSE) null else greenhouse,cursor,forAssignment)
            val data=page.getJSONArray("data");repeat(data.length()) {rows+=data.getJSONObject(it)}
            cursor=page.optString("next_cursor").takeUnless {it.isBlank() || it=="null"}
            check(cursor==null || seen.add(cursor)) {"Daftar tidak bisa dimuat sampai habis. Coba lagi."}
        } while(cursor!=null)
        return rows
    }
    suspend fun assignmentCandidate(session:FinanceSession,ticket:Long,greenhouse:String,email:String):JSONObject=withContext(Dispatchers.IO) {
        current(session,ticket)
        val body=JSONObject().put("greenhouse_id",greenhouse).put("email",email.trim())
        val result=api.request(session.scope.server,session.token,"users/assignment-candidate","POST",body)
        current(session,ticket)
        result.getJSONObject("data")
    }
    /** Copies the active master lists of [source] into [target]; names that already exist are skipped, so a repeat is safe. */
    suspend fun copyMasters(session:FinanceSession,ticket:Long,target:String,source:String):JSONObject=withContext(Dispatchers.IO) {
        current(session,ticket)
        val result=api.request(session.scope.server,session.token,"greenhouses/$target/copy-masters","POST",JSONObject().put("source_greenhouse_id",source))
        current(session,ticket)
        result.getJSONObject("data").also { require(it.getString("greenhouse_id")==target) }.getJSONObject("copied")
    }
    /** Running periods of every greenhouse the manager sees, keyed by greenhouse id, from one manager-home read. */
    suspend fun periods(session: FinanceSession, ticket: Long): Map<String, JSONObject> = withContext(Dispatchers.IO) {
        current(session, ticket)
        val snapshot = api.request(session.scope.server, session.token, "manager-home")
        current(session, ticket)
        val cards = snapshot.getJSONArray("greenhouses")
        (0 until cards.length()).map(cards::getJSONObject).associate { it.getString("id") to it.getJSONObject("period") }
    }
    /** Everything the Kelola home lists: greenhouses with their status, and how many accounts there are. */
    suspend fun overview(session: FinanceSession, ticket: Long): KelolaOverview = coroutineScope {
        // Independent reads run together; only the account list waits for a greenhouse to place the query.
        val assignments = async { options(session, ticket, "assignments", null) }
        val targets = async { options(session, ticket, "targets", null) }
        val periods = async { periods(session, ticket) }
        val greenhouses = options(session, ticket, "greenhouses", null)
        val placement = (greenhouses.firstOrNull { it.optBoolean("active", true) } ?: greenhouses.firstOrNull())?.getString("id")
        val users = placement?.let { options(session, ticket, "users", it) }.orEmpty()
        buildKelolaOverview(greenhouses, if (greenhouses.isEmpty()) emptyMap() else periods.await(),
            assignments.await(), targets.await(), users)
    }
    /** Counts for the settings page of one greenhouse; each list is read whole so the numbers are exact. */
    suspend fun settings(session: FinanceSession, ticket: Long, greenhouse: String): GreenhouseSettings = coroutineScope {
        // The eleven reads do not depend on each other; running them together keeps the page from waiting on each in turn.
        fun read(source: String) = async { options(session, ticket, source, greenhouse) }
        val reads = listOf("seasons", "planting-groups", "varieties", "materials", "units", "grades", "expense-categories",
            "schedules", "targets", "assignments", "users").associateWith(::read)
        val period = async { greenhousePeriod(session, ticket, greenhouse) }
        val seasons = reads.getValue("seasons").await()
        val open = seasons.filter { it.optString("status") == "open" }.map { it.getString("id") }.toSet()
        val groups = reads.getValue("planting-groups").await()
        val varieties = reads.getValue("varieties").await()
        val materials = reads.getValue("materials").await()
        val units = reads.getValue("units").await()
        val grades = reads.getValue("grades").await()
        val categories = reads.getValue("expense-categories").await()
        val schedules = reads.getValue("schedules").await()
        val targets = reads.getValue("targets").await()
        val assignments = reads.getValue("assignments").await()
        val now = java.time.Instant.now()
        fun count(rows: List<JSONObject>) = rows.count { it.optBoolean("active", true) }
        GreenhouseSettings(open.size, groups.count { it.optBoolean("active", true) && it.optString("season_id") in open },
            count(varieties), count(schedules), count(targets), count(materials),
            units.filter { it.optBoolean("active", true) }.map { it.optString("name") },
            grades.filter { it.optBoolean("active", true) }.map { it.optString("name") },
            categories.count { it.optBoolean("active", true) },
            assignments.count { assignmentCurrent(it, now) && workerAssignment(it, reads.getValue("users").await()) }, period.await())
    }
    suspend fun greenhousePeriod(session: FinanceSession, ticket: Long, greenhouse: String): JSONObject = withContext(Dispatchers.IO) {
        current(session, ticket)
        val snapshot = api.request(session.scope.server, session.token, "manager-home", query = mapOf("greenhouse_id" to greenhouse))
        current(session, ticket)
        require(snapshot.getString("greenhouse_id") == greenhouse)
        snapshot.getJSONArray("greenhouses").getJSONObject(0).getJSONObject("period")
    }
    suspend fun prepare(session:FinanceSession,ticket:Long,form:AdminForm,password:String,journal:JSONObject):JSONObject=withContext(Dispatchers.IO) {
        current(session,ticket)
        val status=api.request(session.scope.server,session.token,"sync/status")
        current(session,ticket)
        require(status.getString("schema_version")=="2")
        val epoch=status.getString("sync_epoch");WireContract.uuid(epoch)
        require(store.epoch(session.scope.key)==epoch) {"Data dipulihkan dari cadangan. Beberapa catatan perlu dicek sebelum dikirim."}
        val payload=form.payload(password)
        val operation=JSONObject().put("schema_version","2").put("sync_epoch",epoch).put("operation_id",UUID.randomUUID().toString())
            .put("entity_id",form.id).put("entity_type",form.resource.kind).put("action",form.action).put("base_version",form.version ?: JSONObject.NULL).put("payload",payload)
        val hash=WireContract.hash(operation);val secret=payload.has("password");payload.remove("password")
        JSONObject(journal.toString()).put("pending",operation).put("hash",hash).put("needs_password",secret).also {write(session,ticket,it)}
    }
    /** Builds one admin operation for the setup wizard without touching the shared admin journal. Reuse the result on retry. */
    suspend fun buildOperation(session:FinanceSession,ticket:Long,form:AdminForm,password:String=""):JSONObject=withContext(Dispatchers.IO) {
        current(session,ticket)
        val status=api.request(session.scope.server,session.token,"sync/status")
        current(session,ticket)
        val epoch=status.getString("sync_epoch");WireContract.uuid(epoch)
        require(store.epoch(session.scope.key)==epoch) {"Data dipulihkan dari cadangan. Beberapa catatan perlu dicek sebelum dikirim."}
        JSONObject().put("schema_version","2").put("sync_epoch",epoch).put("operation_id",UUID.randomUUID().toString())
            .put("entity_id",form.id).put("entity_type",form.resource.kind).put("action",form.action)
            .put("base_version",form.version ?: JSONObject.NULL).put("payload",form.payload(password))
    }
    suspend fun sendOperation(session:FinanceSession,ticket:Long,form:AdminForm,operation:JSONObject):JSONObject=withContext(Dispatchers.IO) {
        current(session,ticket)
        val result=api.request(session.scope.server,session.token,form.resource.path,"POST",operation)
        current(session,ticket)
        require(result.getString("operation_id")==operation.getString("operation_id") && result.getString("entity_id")==form.id)
        result
    }
    /** Sets the running planting group of a greenhouse. [version] is the greenhouse version the manager just saw. */
    suspend fun designateOperation(session:FinanceSession,ticket:Long,greenhouse:String,version:Long,group:String?):JSONObject=withContext(Dispatchers.IO) {
        current(session,ticket)
        val epoch=api.request(session.scope.server,session.token,"sync/status").getString("sync_epoch");WireContract.uuid(epoch)
        JSONObject().put("schema_version","2").put("sync_epoch",epoch).put("operation_id",UUID.randomUUID().toString())
            .put("entity_id",greenhouse).put("entity_type","greenhouses").put("action","designate_period").put("base_version",version)
            .put("payload",JSONObject().put("planting_group_id",group ?: JSONObject.NULL))
    }
    suspend fun sendDesignation(session:FinanceSession,ticket:Long,greenhouse:String,operation:JSONObject):JSONObject=withContext(Dispatchers.IO) {
        current(session,ticket)
        val result=api.request(session.scope.server,session.token,"greenhouses/$greenhouse/running-period","POST",operation)
        current(session,ticket)
        require(result.getString("operation_id")==operation.getString("operation_id"))
        result
    }
    suspend fun send(session:FinanceSession,ticket:Long,journal:JSONObject,password:String):JSONObject=withContext(Dispatchers.IO) {
        current(session,ticket)
        val operation=JSONObject(journal.getJSONObject("pending").toString())
        if(journal.optBoolean("needs_password")) operation.getJSONObject("payload").put("password",password)
        require(WireContract.hash(operation)==journal.getString("hash")) {"Masukkan password yang sama seperti sebelumnya."}
        val form=AdminForm.parse(journal.getJSONObject("form"))
        val path=if(form.action=="create") form.resource.path else "${form.resource.path}/${form.id}"+if(form.action in setOf("close","reopen")) "/${form.action}" else ""
        val result=api.request(session.scope.server,session.token,path,if(form.action=="update") "PATCH" else "POST",operation)
        current(session,ticket)
        require(result.getString("operation_id")==operation.getString("operation_id") && result.getString("entity_id")==form.id)
        result
    }
}
