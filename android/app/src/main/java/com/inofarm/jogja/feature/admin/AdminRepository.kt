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
