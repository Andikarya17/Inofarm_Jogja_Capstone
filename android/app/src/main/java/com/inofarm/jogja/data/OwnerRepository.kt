package com.inofarm.jogja.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject

class OwnerRepository(private val api: ApiClient, private val session: SessionStore, private val cache: ReadCache? = null) {
    val signedIn: Boolean get() = session.validToken != null
    val baseUrl: String get() = session.baseUrl
    val expiresAt: String? get() = session.expiresAt
    suspend fun initialize() = withContext(Dispatchers.IO) { session.readToken() != null }
    suspend fun cached() = withContext(Dispatchers.IO) { cache?.read()?.optJSONObject("resources") }
    suspend fun login(base: String, email: String, password: String) {
        val ticket=expire()
        clearExpired(ticket)
        withContext(Dispatchers.IO) { session.baseUrl = api.validateBase(base) }
        val result = json("login", "POST", JSONObject().put("email", email.trim()).put("password", password))
        withContext(Dispatchers.IO) { ensureActive();session.saveLogin(ticket,result.getString("token"),result.optJSONObject("user"),result.nullString("expiresAt")) }
    }
    fun requestSession() = session.requestSession()
    suspend fun logout() {
        val remote=requestSession();val ticket=expire()
        clearExpired(ticket)
        logout(remote)
    }
    suspend fun logout(remote: ApiSession) { if(remote.token!=null) api.raw("logout", "POST", session=remote) }
    fun expire(): Long = session.invalidate()
    suspend fun clearExpired(ticket: Long) = withContext(NonCancellable + Dispatchers.IO) {
        cache?.clearIfCurrent(ticket); session.clearIfCurrent(ticket)
    }
    private suspend fun json(path: String, method: String = "GET", body: JSONObject? = null, query: Map<String,String> = emptyMap()) = api.json(path,method,body,query,requestSession())
    private suspend fun <T> mapped(block: ()->T): T = withContext(Dispatchers.Default) {block()}
    private suspend fun read(path: String): JSONObject {
        val scope=session.cacheScope; val ticket=session.generation;val job=currentCoroutineContext()
        val result=json(path)
        withContext(Dispatchers.IO) { cache?.save(scope,path,result,ticket=ticket,active={job.isActive}) }
        return result
    }
    suspend fun dashboard(): Dashboard {val json=read("dashboard");return mapped {ApiModels.dashboard(json)}}
    suspend fun config(): RemoteConfig {val json=read("config");return mapped {ApiModels.config(json)}}
    suspend fun saveConfig(version: Long?, body: JSONObject): RemoteConfig {val json=json("config", "PUT", JSONObject().put("baseVersion", version ?: JSONObject.NULL).put("config", body));return mapped {ApiModels.config(json)}}
    suspend fun savePhase(version: Long?, phase: PlantPhase): Long = json("active-phase", "PUT", JSONObject().put("baseVersion", version ?: JSONObject.NULL).put("phase", phase.wire)).getJSONObject("activePhase").getLong("id")
    suspend fun history(filters: Map<String, String>, cursor: String? = null): Page<NutrientRecord> {
        val scope=session.cacheScope;val ticket=session.generation;val job=currentCoroutineContext()
        val json = json("history", query = filters + mapOf("limit" to "25") + (cursor?.let { mapOf("cursor" to it) } ?: emptyMap()))
        val page=mapped {Page(json.getJSONArray("data").mapItems(ApiModels::record), json.nullString("next_cursor"), json.nullString("prev_cursor"))}
        withContext(Dispatchers.IO) { runCatching {cache?.history(scope,json,filters,cursor!=null,ticket,active={job.isActive})} }
        return page
    }
    suspend fun telemetry(): List<Telemetry> {
        val scope=session.cacheScope;val ticket=session.generation;val job=currentCoroutineContext(); val json=json("telemetry", query = mapOf("limit" to "100"))
        val values=mapped {json.getJSONArray("data").mapItems(ApiModels::telemetry).reversed()}
        withContext(Dispatchers.IO) { cache?.save(scope,"telemetry",json,ticket=ticket,active={job.isActive}) }; return values
    }
    suspend fun export(filters: Map<String, String>) = api.raw("history/export", query = filters,session=requestSession())
    private suspend fun <T> pages(path: String, map: (JSONObject) -> T): List<T> {
        val scope=session.cacheScope;val ticket=session.generation;val job=currentCoroutineContext(); val items = mutableListOf<T>(); val raw=JSONArray(); var page = 1
        do {
            val response = json(path, query = mapOf("page" to page.toString()))
            mapped {response.getJSONArray("data").mapItems { it }.forEach { raw.put(it); items+=map(it) }}
            page++
        } while (page <= response.getInt("last_page"))
        withContext(Dispatchers.IO) { cache?.save(scope,path,JSONObject().put("data",raw),ticket=ticket,active={job.isActive}) }
        return items
    }
    suspend fun workers() = pages("workers", ApiModels::worker)
    suspend fun shifts() = pages("shifts", ApiModels::shift)
    suspend fun saveWorker(name: String, worker: Worker? = null, active: Boolean? = null): Worker {
        require(name.trim().length in 1..100) { "Isi nama 1–100 karakter" }
        require(name.none { it.code < 32 }) { "Nama tidak boleh mengandung karakter kontrol" }
        val body = JSONObject().put("name", name.trim())
        worker?.let { body.put("baseVersion", it.version).put("active", active ?: it.active) }
        val json=json(worker?.let { "workers/${it.id}" } ?: "workers", if (worker == null) "POST" else "PUT", body)
        return mapped {ApiModels.worker(json)}
    }
    suspend fun saveShift(body: JSONObject, shift: Shift?): Shift {
        val payload=mapped {JSONObject(body.toString()).put("baseVersion",shift?.id ?: JSONObject.NULL)}
        val json=json(shift?.let { "shifts/${it.key}" } ?: "shifts",if(shift==null) "POST" else "PUT",payload)
        return mapped {ApiModels.shift(json)}
    }
    suspend fun deleteShift(shift: Shift) { api.raw("shifts/${shift.key}", "DELETE", JSONObject().put("baseVersion", shift.id),session=requestSession()) }
}
