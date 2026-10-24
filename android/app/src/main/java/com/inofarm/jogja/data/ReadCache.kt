package com.inofarm.jogja.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Schema 2: individually atomic encrypted resources, one aggregate storage budget. IO only. */
class ReadCache(context: Context, private val session: SessionStore) {
    private val context=context.applicationContext
    private fun file(resource: String)=AtomicFile(File(context.noBackupFilesDir,name(resource)))
    private fun recover(resource: String): AtomicFile {
        val target=file(resource)
        if(File(target.baseFile.path+".bak").exists() || File(target.baseFile.path+".new").exists()) {
            runCatching {target.openRead().close()}.onFailure {if(!target.baseFile.exists()) target.delete()}
        }
        return target
    }
    companion object {
        private val storageLock=Any()
        const val maxBytes=2 * 1024 * 1024
        val resources=listOf("dashboard","config","history","telemetry","workers","shifts")
        private fun name(resource: String)="owner-read-cache-v2-$resource.bin"
        val fileNames=resources.map(::name)+"owner-read-cache.bin"
        internal fun clearFiles(context: Context, session: SessionStore, ticket: Long) = synchronized(storageLock) {
            if(ticket==session.generation) fileNames.forEach {AtomicFile(File(context.noBackupFilesDir,it)).delete()}
        }
    }
    private fun entry(resource: String, scope: String): JSONObject? = runCatching {
        val raw=recover(resource).openRead().use {stream -> require(stream.channel.size()<=maxBytes);stream.readBytes()}
        JSONObject(session.decrypt(raw.toString(Charsets.UTF_8))).takeIf {it.optInt("schemaVersion")==2 && it.optString("scope")==scope}
    }.getOrNull()
    @Synchronized fun read(): JSONObject? = synchronized(storageLock) {
        val scope=session.cacheScope ?: return null
        AtomicFile(File(context.noBackupFilesDir,"owner-read-cache.bin")).delete()
        val data=JSONObject()
        var total=0L
        resources.forEach { key -> entry(key,scope)?.let {data.put(key,it)};total+=file(key).baseFile.length() }
        if(total>maxBytes || data.length()==0 || scope!=session.cacheScope) return null
        return JSONObject().put("schemaVersion",2).put("scope",scope).put("resources",data)
    }
    @Synchronized fun save(scope: String?, resource: String, payload: JSONObject, filters: Map<String,String> = emptyMap(), ticket: Long = session.generation, active: ()->Boolean = {true}): Unit = synchronized(storageLock) {
        if(resource !in resources || scope==null || scope!=session.cacheScope || ticket!=session.generation || !active()) return
        runCatching {
            AtomicFile(File(context.noBackupFilesDir,"owner-read-cache.bin")).delete()
            var otherBytes=0L
            resources.forEach { key ->
                val other=recover(key)
                if(key!=resource) otherBytes+=other.baseFile.length()
            }
            val entry=JSONObject().put("schemaVersion",2).put("scope",scope).put("fetchedAt",Instant.now().toString()).put("payload",payload).put("filters",JSONObject(filters))
            val encrypted=session.encrypt(entry.toString()).toByteArray(Charsets.UTF_8)
            val target=file(resource)
            // Include the good old version while AtomicFile stages its replacement.
            require(encrypted.size+otherBytes+target.baseFile.length()<=maxBytes)
            if(scope!=session.cacheScope || ticket!=session.generation || !active()) return
            val stream=target.startWrite()
            try {
                stream.write(encrypted)
                if(scope!=session.cacheScope || ticket!=session.generation || !active()) {target.failWrite(stream);return}
                target.finishWrite(stream)
            } catch(error:Exception) {target.failWrite(stream);throw error}
        }
        Unit
    }
    @Synchronized fun history(scope: String?, payload: JSONObject, filters: Map<String,String>, append: Boolean, ticket: Long = session.generation, active: ()->Boolean = {true}): Unit = synchronized(storageLock) {
        if(scope==null || scope!=session.cacheScope || ticket!=session.generation || !active()) return
        val old=if(append) entry("history",scope) else null
        val matching=old?.optJSONObject("filters")?.let {obj -> obj.keys().asSequence().associateWith {obj.getString(it)}}==filters
        val rows=linkedMapOf<Long,JSONObject>()
        if(matching) old?.getJSONObject("payload")?.getJSONArray("data")?.mapItems {it}?.forEach {rows[it.getLong("id")]=it}
        payload.getJSONArray("data").mapItems {it}.forEach {rows[it.getLong("id")]=it}
        save(scope,"history",JSONObject().put("data",JSONArray(rows.values.take(500))).put("next_cursor",JSONObject.NULL),filters,ticket,active)
    }
    @Synchronized fun clear() = synchronized(storageLock) {fileNames.forEach {AtomicFile(File(context.noBackupFilesDir,it)).delete()}}
    @Synchronized fun clearIfCurrent(ticket: Long) = synchronized(storageLock) {if(ticket==session.generation) clear()}
}
