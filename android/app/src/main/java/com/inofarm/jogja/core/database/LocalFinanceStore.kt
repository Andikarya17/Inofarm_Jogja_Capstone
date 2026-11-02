package com.inofarm.jogja.core.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.session.PayloadCipher
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalRecord(val id: String, val kind: String, val greenhouseId: String, val revision: Long, val baseVersion: Long?, val serverVersion: Long?,
    val body: JSONObject, val serverBody: JSONObject?, val latestOperationId: String?, val syncState: SyncState, val error: JSONObject?)
data class QueuedOperation(val id: String, val entityId: String, val revision: Long, val envelope: JSONObject, val hash: String, val state: String, val attempts: Int, val error: JSONObject?)

/** Blocking DAO. Repository invokes it on Dispatchers.IO. Every query binds account/server scope. */
class LocalFinanceStore(private val helper: FinanceDatabase, private val cipher: PayloadCipher, private val afterRecordWrite: () -> Unit = {}) {
    private val db get() = helper.writableDatabase
    private fun Cursor.string(key: String) = getString(getColumnIndexOrThrow(key))
    private fun Cursor.nullableString(key: String) = getColumnIndexOrThrow(key).let { if(isNull(it)) null else getString(it) }
    private fun Cursor.long(key: String) = getLong(getColumnIndexOrThrow(key))
    private fun Cursor.nullableLong(key: String) = getColumnIndexOrThrow(key).let { if(isNull(it)) null else getLong(it) }
    private fun values(vararg pairs: Pair<String, Any?>) = ContentValues().apply { pairs.forEach { (key,value) -> when(value) {
        null -> putNull(key); is String -> put(key,value); is Int -> put(key,value); is Long -> put(key,value); else -> error("Unsupported database value")
    } } }
    @Synchronized private fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val connection = db; connection.beginTransaction()
        try { return block(connection).also { connection.setTransactionSuccessful() } } finally { connection.endTransaction() }
    }
    private fun encrypt(scope: String, value: JSONObject?) = value?.let { cipher.encode(scope,it.toString()) }
    private fun decode(scope: String, value: String?) = value?.let { JSONObject(cipher.decode(scope,it)) }
    private fun Cursor.record(scope: String) = LocalRecord(string("entity_id"),string("kind"),string("greenhouse_id"),long("revision"),nullableLong("base_version"),nullableLong("server_version"),
        decode(scope,string("body"))!!,decode(scope,nullableString("server_body")),nullableString("latest_operation_id"),SyncState.valueOf(string("sync_state")),decode(scope,nullableString("error")))
    private fun Cursor.operation(scope: String) = QueuedOperation(string("operation_id"),string("entity_id"),long("local_revision"),decode(scope,string("envelope"))!!,
        string("canonical_hash"),string("state"),getInt(getColumnIndexOrThrow("attempts")),decode(scope,nullableString("error")))

    fun initialize(scope: String, epoch: String, role: UserRole) = transaction { connection ->
        WireContract.uuid(epoch)
        val previous = connection.rawQuery("SELECT epoch FROM scope_metadata WHERE scope=?",arrayOf(scope)).use { if(it.moveToFirst()) it.getString(0) else null }
        if(previous == null) connection.insertOrThrow("scope_metadata",null,values("scope" to scope,"epoch" to epoch,"role" to role.wire,"visible_greenhouses" to "[]"))
        else {
            if(previous != epoch) {
                connection.update("outbox",values("state" to "EPOCH_REVIEW"),"scope=? AND state NOT IN ('ACCEPTED','SUPERSEDED')",arrayOf(scope))
                connection.update("local_records",values("sync_state" to "EPOCH_REVIEW"),"scope=?",arrayOf(scope))
                connection.delete("cached_records","scope=?",arrayOf(scope))
                connection.delete("scope_access","scope=?",arrayOf(scope))
                connection.update("scope_metadata",values("pull_cursor" to null,"visible_greenhouses" to "[]"),"scope=?",arrayOf(scope))
            }
            connection.update("scope_metadata",values("epoch" to epoch,"role" to role.wire),"scope=?",arrayOf(scope))
        }
        if(role == UserRole.WORKER) connection.delete("cached_records","scope=? AND kind IN ('users','greenhouse_assignments','expense_categories','expenses','cost_allocations','assets','sales','investment_scenarios','report_snapshots')",arrayOf(scope))
    }
    fun epoch(scope: String): String? = db.rawQuery("SELECT epoch FROM scope_metadata WHERE scope=?",arrayOf(scope)).use { if(it.moveToFirst()) it.getString(0) else null }
    fun pullCursor(scope: String): String? = db.rawQuery("SELECT pull_cursor FROM scope_metadata WHERE scope=?",arrayOf(scope)).use { if(it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
    fun lastSyncedAt(scope:String):Long? = db.rawQuery("SELECT last_synced_at FROM scope_metadata WHERE scope=?",arrayOf(scope)).use {if(it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null}
    fun visibleGreenhouses(scope: String): Set<String> = db.rawQuery("SELECT visible_greenhouses FROM scope_metadata WHERE scope=?",arrayOf(scope)).use {
        if(!it.moveToFirst()) emptySet() else JSONArray(it.getString(0)).let { rows -> (0 until rows.length()).map(rows::getString).toSet() }
    }
    fun record(scope: String, id: String): LocalRecord? = db.rawQuery("SELECT * FROM local_records WHERE scope=? AND entity_id=?",arrayOf(scope,id)).use { if(it.moveToFirst()) it.record(scope) else null }
    fun records(scope: String, limit: Int = 100): List<LocalRecord> {
        require(limit in 1..1000)
        return db.rawQuery("SELECT * FROM local_records WHERE scope=? AND greenhouse_id IN (SELECT greenhouse_id FROM scope_access WHERE scope=?) ORDER BY updated_at DESC,entity_id LIMIT ?",arrayOf(scope,scope,limit.toString())).use { rows -> buildList { while(rows.moveToNext()) add(rows.record(scope)) } }
    }
    fun cached(scope: String, kind: String, greenhouse: String? = null, limit: Int = 100): List<JSONObject> {
        require(limit in 1..1000)
        val role=db.rawQuery("SELECT role FROM scope_metadata WHERE scope=?",arrayOf(scope)).use { if(it.moveToFirst()) it.getString(0) else null }
        require(role == "manager" || kind in setOf("greenhouses","varieties","units","materials","grades","seasons","planting_groups","schedules","targets","activity","harvest")) { "Data ini hanya untuk pengelola." }
        val args = mutableListOf(scope,kind,scope)
        val filter = if(greenhouse != null) { args.add(greenhouse); " AND greenhouse_id=?" } else ""
        args.add(limit.toString())
        return db.rawQuery("SELECT body FROM cached_records WHERE scope=? AND kind=? AND greenhouse_id IN (SELECT greenhouse_id FROM scope_access WHERE scope=?)$filter ORDER BY entity_id LIMIT ?",args.toTypedArray()).use { rows -> buildList { while(rows.moveToNext()) add(decode(scope,rows.getString(0))!!) } }
    }
    fun outbox(scope: String, limit: Int = 100): List<QueuedOperation> {
        require(limit in 1..1000)
        return db.rawQuery("SELECT * FROM outbox WHERE scope=? ORDER BY created_at DESC,operation_id LIMIT ?",arrayOf(scope,limit.toString())).use { rows -> buildList { while(rows.moveToNext()) add(rows.operation(scope)) } }
    }
    private fun persistDraft(scope: String, id: String, gh: String, body: JSONObject, expectedRevision: Long?, editingBaseVersion:Long?=null): LocalRecord {
        WireContract.uuid(id); require(gh in visibleGreenhouses(scope)) { "Greenhouse tidak lagi dalam penugasan. Draf lama tetap disimpan." }
        val old = record(scope,id)
        require(old==null || old.greenhouseId==gh) { "Greenhouse catatan tidak dapat dipindah." }
        require(if(old == null) expectedRevision == null else expectedRevision == old.revision) { "Draf berubah. Muat ulang sebelum menyimpan." }
        val revision = (old?.revision ?: 0) + 1
        val base = if(old?.syncState == SyncState.ACCEPTED) editingBaseVersion ?: old.serverVersion else old?.baseVersion
        val held=old?.syncState?.takeIf { it in setOf(SyncState.EPOCH_REVIEW,SyncState.SCOPE_REVOKED) }
        val data = values("scope" to scope,"entity_id" to id,"kind" to "activity","greenhouse_id" to gh,"revision" to revision,
            "base_version" to base,"server_version" to old?.serverVersion,"body" to encrypt(scope,body),"server_body" to encrypt(scope,old?.serverBody),
            "latest_operation_id" to old?.latestOperationId,"sync_state" to (held?.name ?: "LOCAL"),"error" to if(held != null) encrypt(scope,old.error) else null,"updated_at" to System.currentTimeMillis())
        if(old == null) db.insertOrThrow("local_records",null,data) else db.update("local_records",data,"scope=? AND entity_id=?",arrayOf(scope,id))
        return record(scope,id)!!
    }
    fun saveDraft(scope: String, id: String, gh: String, body: JSONObject, expectedRevision: Long? = null, editingBaseVersion:Long?=null): LocalRecord = transaction {
        persistDraft(scope,id,gh,body,expectedRevision,editingBaseVersion)
    }
    fun saveAndQueue(scope: String, id: String, gh: String, body: JSONObject, expectedRevision: Long? = null, editingBaseVersion:Long?=null): LocalRecord = transaction {
        require(record(scope,id)?.syncState !in setOf(SyncState.EPOCH_REVIEW,SyncState.SCOPE_REVOKED)) { "Catatan ditahan untuk rekonsiliasi atau penugasan. Draf tetap disimpan." }
        val blocking = db.rawQuery("SELECT 1 FROM outbox WHERE scope=? AND entity_id=? AND state NOT IN ('ACCEPTED','SUPERSEDED') LIMIT 1",arrayOf(scope,id)).use { it.moveToFirst() }
        require(!blocking) { "Operasi sebelumnya masih menunggu atau perlu ditinjau. Simpan draf dulu." }
        val saved = persistDraft(scope,id,gh,body,expectedRevision,editingBaseVersion)
        afterRecordWrite()
        val operationId = UUID.randomUUID().toString()
        val operation = JSONObject().put("schema_version","2").put("sync_epoch",epoch(scope) ?: error("Epoch server belum dikenal."))
            .put("operation_id",operationId).put("entity_id",id).put("entity_type","activity").put("action",if(saved.baseVersion == null) "create" else "update")
            .put("base_version",saved.baseVersion ?: JSONObject.NULL).put("payload",JSONObject(body.toString()))
        db.insertOrThrow("outbox",null,values("scope" to scope,"operation_id" to operationId,"entity_id" to id,"local_revision" to saved.revision,
            "envelope" to encrypt(scope,operation),"canonical_hash" to WireContract.hash(operation),"state" to "QUEUED","created_at" to System.currentTimeMillis()))
        db.update("local_records",values("latest_operation_id" to operationId,"sync_state" to "QUEUED"),"scope=? AND entity_id=?",arrayOf(scope,id))
        record(scope,id)!!
    }
    fun next(scope: String, now: Long = System.currentTimeMillis()): QueuedOperation? = db.rawQuery("SELECT * FROM outbox WHERE scope=? AND state='QUEUED' AND next_attempt_at<=? ORDER BY created_at,operation_id LIMIT 1",arrayOf(scope,now.toString())).use { if(it.moveToFirst()) it.operation(scope) else null }
    fun acknowledge(scope: String, id: String, receipt: JSONObject) = transaction {
        val op = db.rawQuery("SELECT * FROM outbox WHERE scope=? AND operation_id=?",arrayOf(scope,id)).use { if(it.moveToFirst()) it.operation(scope) else null } ?: error("Operasi ACK tidak ditemukan.")
        require(op.envelope.getString("sync_epoch") == epoch(scope)) { "ACK berasal dari epoch lama; rekonsiliasi diperlukan." }
        require(receipt.getString("operation_id") == id && receipt.getString("entity_id") == op.entityId && receipt.getLong("version") > 0) { "Identitas ACK tidak cocok." }
        val authoritative = receipt.getJSONObject("data"); require(authoritative.getString("id") == op.entityId)
        val version = receipt.getLong("version")
        val current = record(scope,op.entityId) ?: error("Catatan ACK tidak ditemukan.")
        db.update("outbox",values("state" to "ACCEPTED","error" to null),"scope=? AND operation_id=?",arrayOf(scope,id))
        val patch = values()
        if(version >= (current.serverVersion ?: 0)) { patch.put("server_version",version);patch.put("server_body",encrypt(scope,authoritative)) }
        if(current.latestOperationId == id) patch.put("base_version",version)
        if(current.revision == op.revision && current.latestOperationId == id && version >= (current.serverVersion ?: 0)) {
            patch.put("body",encrypt(scope,authoritative));patch.put("sync_state","ACCEPTED");patch.put("base_version",version);patch.putNull("error")
        } else if(current.latestOperationId == id && current.revision != op.revision && current.syncState != SyncState.EPOCH_REVIEW) patch.put("sync_state","LOCAL")
        if(patch.size() > 0) db.update("local_records",patch,"scope=? AND entity_id=?",arrayOf(scope,op.entityId))
    }
    fun mark(scope: String, op: QueuedOperation, state: SyncState, error: JSONObject, nextAttemptAt: Long = 0) = transaction {
        db.update("outbox",values("state" to state.name,"attempts" to op.attempts + 1,"next_attempt_at" to nextAttemptAt,"error" to encrypt(scope,error)),"scope=? AND operation_id=? AND state!='ACCEPTED'",arrayOf(scope,op.id))
        val record = record(scope,op.entityId)
        if(record?.latestOperationId == op.id) {
            val patch=values("error" to encrypt(scope,error))
            if(record.revision==op.revision || state in setOf(SyncState.AUTH_REQUIRED,SyncState.SCOPE_REVOKED,SyncState.EPOCH_REVIEW)) patch.put("sync_state",state.name)
            if(error.has("current_data") && !error.isNull("current_data")) { patch.put("server_body",encrypt(scope,error.getJSONObject("current_data")));patch.put("server_version",error.getLong("current_version")) }
            db.update("local_records",patch,"scope=? AND entity_id=?",arrayOf(scope,op.entityId))
        }
    }
    fun resumeAuthentication(scope: String) = transaction {
        db.update("outbox",values("state" to "QUEUED","next_attempt_at" to 0L),"scope=? AND state='AUTH_REQUIRED'",arrayOf(scope))
        db.update("local_records",values("sync_state" to "QUEUED"),"scope=? AND sync_state='AUTH_REQUIRED'",arrayOf(scope))
    }
    fun hold(scope: String,state: SyncState,error: JSONObject) = transaction {
        require(state in setOf(SyncState.AUTH_REQUIRED,SyncState.SCOPE_REVOKED,SyncState.EPOCH_REVIEW))
        db.update("outbox",values("state" to state.name,"error" to encrypt(scope,error)),"scope=? AND state NOT IN ('ACCEPTED','SUPERSEDED','EPOCH_REVIEW')",arrayOf(scope))
        db.update("local_records",values("sync_state" to state.name,"error" to encrypt(scope,error)),"scope=? AND latest_operation_id IN (SELECT operation_id FROM outbox WHERE scope=? AND state=?)",arrayOf(scope,scope,state.name))
    }
    fun applyPull(scope: String, page: JSONObject) = transaction {
        require(page.getString("sync_epoch") == epoch(scope)) { "Epoch berubah; antrean harus ditinjau." }
        val scopes=page.getJSONArray("scope");val visible=(0 until scopes.length()).map { scopes.getJSONObject(it).getString("id") }.toSet()
        db.delete("scope_access","scope=?",arrayOf(scope))
        visible.forEach { db.insertOrThrow("scope_access",null,values("scope" to scope,"greenhouse_id" to it)) }
        db.update("scope_metadata",values("visible_greenhouses" to JSONArray(visible.toList()).toString(),"pull_cursor" to page.getString("next_cursor"),"last_synced_at" to System.currentTimeMillis()),"scope=?",arrayOf(scope))
        val rows=page.getJSONArray("data")
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i);val kind=row.getString("entity_type");val id=row.getString("entity_id");val version=row.getLong("version");val gh=row.getString("greenhouse_id")
            require(gh in visible) { "Respons pull di luar scope." }
            val old=db.rawQuery("SELECT version FROM cached_records WHERE scope=? AND kind=? AND entity_id=?",arrayOf(scope,kind,id)).use { if(it.moveToFirst()) it.getLong(0) else 0L }
            if(version > old) {
                db.insertWithOnConflict("cached_records",null,values("scope" to scope,"kind" to kind,"entity_id" to id,"greenhouse_id" to gh,"version" to version,
                    "tombstone" to if(row.getBoolean("tombstone")) 1 else 0,"body" to encrypt(scope,row.getJSONObject("data"))),SQLiteDatabase.CONFLICT_REPLACE)
                if(kind == "activity") {
                    val local=record(scope,id);val body=row.getJSONObject("data")
                    if(local == null) db.insertOrThrow("local_records",null,values("scope" to scope,"entity_id" to id,"kind" to kind,"greenhouse_id" to gh,"revision" to 1L,
                        "base_version" to version,"server_version" to version,"body" to encrypt(scope,body),"server_body" to encrypt(scope,body),"sync_state" to "ACCEPTED","updated_at" to System.currentTimeMillis()))
                    else if(version > (local.serverVersion ?: 0)) {
                        val patch=values("server_version" to version,"server_body" to encrypt(scope,body))
                        if(local.syncState == SyncState.ACCEPTED) {patch.put("body",encrypt(scope,body));patch.put("base_version",version)}
                        db.update("local_records",patch,"scope=? AND entity_id=?",arrayOf(scope,id))
                    }
                }
            }
        }
        val heldError=encrypt(scope,JSONObject().put("message","Penugasan greenhouse telah dicabut. Draf tetap disimpan untuk ditinjau."))
        db.execSQL("""UPDATE outbox SET state='SCOPE_REVOKED',error=? WHERE scope=? AND state NOT IN ('ACCEPTED','SUPERSEDED','EPOCH_REVIEW')
            AND entity_id IN (SELECT entity_id FROM local_records WHERE scope=? AND greenhouse_id NOT IN (SELECT greenhouse_id FROM scope_access WHERE scope=?))""",arrayOf(heldError,scope,scope,scope))
        db.execSQL("""UPDATE local_records SET sync_state='SCOPE_REVOKED',error=? WHERE scope=? AND sync_state!='EPOCH_REVIEW'
            AND greenhouse_id NOT IN (SELECT greenhouse_id FROM scope_access WHERE scope=?)""",arrayOf(heldError,scope,scope))
    }
    @Synchronized fun close() = helper.close()
}
