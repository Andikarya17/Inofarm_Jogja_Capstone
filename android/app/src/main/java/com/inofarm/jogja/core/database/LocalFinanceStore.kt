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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class LocalRecord(val id: String, val kind: String, val greenhouseId: String, val revision: Long, val baseVersion: Long?, val serverVersion: Long?,
    val body: JSONObject, val serverBody: JSONObject?, val latestOperationId: String?, val syncState: SyncState, val error: JSONObject?)
data class QueuedOperation(val id: String, val entityId: String, val revision: Long, val envelope: JSONObject, val hash: String, val state: String, val attempts: Int, val error: JSONObject?)
data class LocalHistoryFilter(val groupId:String?=null,val type:String?=null,val from:String?=null,val until:String?=null,val recordState:String?=null,val greenhouseId:String?=null,val groups:Set<String>?=null)
data class LocalDetail(val record:LocalRecord,val pending:List<QueuedOperation>,val archives:List<JSONObject>)

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
        if(role == UserRole.WORKER) {
            connection.delete("cached_records","scope=? AND kind NOT IN ('greenhouses','varieties','units','materials','grades','seasons','planting_groups','schedules','targets','activity','harvest','activity_audit')",arrayOf(scope))
            val reason=encrypt(scope,JSONObject().put("message","Peran saat ini tidak mengizinkan perubahan pengelola. Antrean tetap disimpan untuk ditinjau."))
            connection.execSQL("UPDATE outbox SET state='SCOPE_REVOKED',error=? WHERE scope=? AND state NOT IN ('ACCEPTED','SUPERSEDED','EPOCH_REVIEW') AND entity_id IN (SELECT entity_id FROM local_records WHERE scope=? AND kind NOT IN ('activity','harvest'))",arrayOf(reason,scope,scope))
        }
    }
    fun epoch(scope: String): String? = db.rawQuery("SELECT epoch FROM scope_metadata WHERE scope=?",arrayOf(scope)).use { if(it.moveToFirst()) it.getString(0) else null }
    fun pullCursor(scope: String): String? = db.rawQuery("SELECT pull_cursor FROM scope_metadata WHERE scope=?",arrayOf(scope)).use { if(it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
    fun lastSyncedAt(scope:String):Long? = db.rawQuery("SELECT last_synced_at FROM scope_metadata WHERE scope=?",arrayOf(scope)).use {if(it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null}
    fun visibleGreenhouses(scope: String): Set<String> = db.rawQuery("SELECT visible_greenhouses FROM scope_metadata WHERE scope=?",arrayOf(scope)).use {
        if(!it.moveToFirst()) emptySet() else JSONArray(it.getString(0)).let { rows -> (0 until rows.length()).map(rows::getString).toSet() }
    }
    fun record(scope: String, id: String): LocalRecord? = db.rawQuery("SELECT * FROM local_records WHERE scope=? AND entity_id=?",arrayOf(scope,id)).use { if(it.moveToFirst()) it.record(scope).also {row -> requireKind(scope,row.kind)} else null }
    private fun indexValues(body:JSONObject):ContentValues {
        val form=body.optJSONObject("_local_form")
        val day=runCatching { if(form!=null) LocalDate.parse(form.getString("date")).toString() else OffsetDateTime.parse(body.getString("occurred_at")).atZoneSameInstant(ZoneId.of("Asia/Jakarta")).toLocalDate().toString() }.getOrNull()
        return values("occurred_date" to day,"activity_type" to (form?.optString("type") ?: body.optString("activity_type")).takeIf {it.isNotBlank()},
            "group_id" to (form?.optString("group_id") ?: body.optString("planting_group_id")).takeIf {it.isNotBlank()},
            "record_state" to body.optString("record_state","active"),"occurred_millis" to runCatching {OffsetDateTime.parse(body.getString("occurred_at")).toInstant().toEpochMilli()}.getOrNull(),"indexed" to 1)
    }
    private fun ensureIndexed(scope:String)=transaction {
        while(true) {
            val pending=db.rawQuery("SELECT entity_id,body FROM local_records WHERE scope=? AND indexed=0 LIMIT 100",arrayOf(scope)).use { rows -> buildList {while(rows.moveToNext()) add(rows.getString(0) to rows.getString(1))} }
            if(pending.isEmpty()) break
            pending.forEach { (id,body) -> db.update("local_records",indexValues(decode(scope,body)!!),"scope=? AND entity_id=?",arrayOf(scope,id)) }
        }
    }
    fun records(scope: String, limit: Int = 100,kind:String="activity",filter:LocalHistoryFilter=LocalHistoryFilter(),offset:Int=0): List<LocalRecord> {
        require(limit in 1..1000)
        require(offset>=0);requireKind(scope,kind);ensureIndexed(scope)
        val where=StringBuilder("scope=? AND greenhouse_id IN (SELECT greenhouse_id FROM scope_access WHERE scope=?) AND kind=?")
        val args=mutableListOf(scope,scope,kind)
        for((column,value) in listOf("group_id" to filter.groupId,"activity_type" to filter.type,"record_state" to filter.recordState)) if(value!=null) {where.append(" AND $column=?");args.add(value)}
        filter.greenhouseId?.let {where.append(" AND greenhouse_id=?");args.add(it)}
        filter.groups?.let { groups ->
            require(groups.size<=1000)
            if(groups.isEmpty()) where.append(" AND 0=1") else {where.append(" AND group_id IN (${groups.joinToString(",") {"?"}})");args.addAll(groups)}
        }
        filter.from?.let {LocalDate.parse(it);where.append(" AND occurred_date>=?");args.add(it)}
        filter.until?.let {LocalDate.parse(it);where.append(" AND occurred_date<=?");args.add(it)}
        args.add(limit.toString());args.add(offset.toString())
        return db.rawQuery("SELECT * FROM local_records WHERE $where ORDER BY COALESCE(occurred_millis,updated_at) DESC,entity_id LIMIT ? OFFSET ?",args.toTypedArray()).use { rows -> buildList { while(rows.moveToNext()) add(rows.record(scope)) } }
    }
    private fun requireKind(scope:String,kind:String) {
        val role=db.rawQuery("SELECT role FROM scope_metadata WHERE scope=?",arrayOf(scope)).use {if(it.moveToFirst()) it.getString(0) else null}
        require(role=="manager" || kind in setOf("activity","harvest","activity_audit","greenhouses","varieties","units","materials","grades","seasons","planting_groups","schedules","targets")) {"Data ini hanya untuk pengelola."}
    }
    private fun requireMutationKind(scope:String,kind:String) {
        requireKind(scope,kind)
        if(kind !in setOf("activity","harvest")) {
            val role=db.rawQuery("SELECT role FROM scope_metadata WHERE scope=?",arrayOf(scope)).use {if(it.moveToFirst()) it.getString(0) else null}
            require(role=="manager") {"Perubahan ini hanya untuk pengelola."}
        }
    }
    fun cached(scope: String, kind: String, greenhouse: String? = null, limit: Int = 100): List<JSONObject> {
        require(limit in 1..1000)
        requireKind(scope,kind)
        val args = mutableListOf(scope,kind,scope)
        val filter = if(greenhouse != null) { args.add(greenhouse); " AND greenhouse_id=?" } else ""
        args.add(limit.toString())
        return db.rawQuery("SELECT body FROM cached_records WHERE scope=? AND kind=? AND greenhouse_id IN (SELECT greenhouse_id FROM scope_access WHERE scope=?)$filter ORDER BY entity_id LIMIT ?",args.toTypedArray()).use { rows -> buildList { while(rows.moveToNext()) add(decode(scope,rows.getString(0))!!) } }
    }
    fun cachedOne(scope:String,kind:String,id:String):JSONObject? {
        requireKind(scope,kind)
        return db.rawQuery("SELECT body FROM cached_records WHERE scope=? AND kind=? AND entity_id=? AND greenhouse_id IN (SELECT greenhouse_id FROM scope_access WHERE scope=?)",arrayOf(scope,kind,id,scope)).use {if(it.moveToFirst()) decode(scope,it.getString(0)) else null}
    }
    fun cacheAudit(scope:String,id:String,gh:String,body:JSONObject)=transaction {
        requireKind(scope,"activity_audit");require(gh in visibleGreenhouses(scope)) {"Penugasan tidak lagi berlaku."}
        db.insertWithOnConflict("cached_records",null,values("scope" to scope,"kind" to "activity_audit","entity_id" to id,"greenhouse_id" to gh,"version" to 1L,
            "tombstone" to 0,"body" to encrypt(scope,body)),SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun outbox(scope: String, limit: Int = 100): List<QueuedOperation> {
        require(limit in 1..1000)
        return db.rawQuery("""SELECT * FROM outbox WHERE scope=? AND entity_id IN (SELECT entity_id FROM local_records WHERE scope=?
            AND (kind IN ('activity','harvest') OR EXISTS(SELECT 1 FROM scope_metadata WHERE scope=? AND role='manager')))
            ORDER BY created_at DESC,operation_id LIMIT ?""",arrayOf(scope,scope,scope,limit.toString())).use {rows -> buildList {while(rows.moveToNext()) add(rows.operation(scope))}}
    }
    private fun persistDraft(scope: String, id: String, gh: String, body: JSONObject, expectedRevision: Long?, editingBaseVersion:Long?=null,kind:String="activity"): LocalRecord {
        requireKind(scope,kind)
        WireContract.uuid(id); require(gh in visibleGreenhouses(scope)) { "Greenhouse tidak lagi dalam penugasan. Draf lama tetap disimpan." }
        val old = record(scope,id)
        require(old==null || old.greenhouseId==gh) { "Greenhouse catatan tidak dapat dipindah." }
        require(old==null || old.kind==kind) {"Jenis catatan tidak dapat diganti."}
        require(if(old == null) expectedRevision == null else expectedRevision == old.revision) { "Draf berubah. Muat ulang sebelum menyimpan." }
        val revision = (old?.revision ?: 0) + 1
        val base = if(old?.syncState == SyncState.ACCEPTED) editingBaseVersion ?: old.serverVersion else old?.baseVersion
        val held=old?.syncState?.takeIf { it in setOf(SyncState.EPOCH_REVIEW,SyncState.SCOPE_REVOKED) }
        val data = values("scope" to scope,"entity_id" to id,"kind" to kind,"greenhouse_id" to gh,"revision" to revision,
            "base_version" to base,"server_version" to old?.serverVersion,"body" to encrypt(scope,body),"server_body" to encrypt(scope,old?.serverBody),
            "latest_operation_id" to old?.latestOperationId,"sync_state" to (held?.name ?: "LOCAL"),"error" to if(held != null) encrypt(scope,old.error) else null,"updated_at" to System.currentTimeMillis())
        data.putAll(indexValues(body))
        if(old == null) db.insertOrThrow("local_records",null,data) else db.update("local_records",data,"scope=? AND entity_id=?",arrayOf(scope,id))
        return record(scope,id)!!
    }
    fun saveDraft(scope: String, id: String, gh: String, body: JSONObject, expectedRevision: Long? = null, editingBaseVersion:Long?=null,kind:String="activity"): LocalRecord = transaction {
        requireMutationKind(scope,kind)
        persistDraft(scope,id,gh,body,expectedRevision,editingBaseVersion,kind)
    }
    fun saveAndQueue(scope: String, id: String, gh: String, body: JSONObject, expectedRevision: Long? = null, editingBaseVersion:Long?=null,replaceValidationRejected:Boolean=false): LocalRecord = transaction {
        require(record(scope,id)?.syncState !in setOf(SyncState.EPOCH_REVIEW,SyncState.SCOPE_REVOKED)) { "Catatan ditahan untuk rekonsiliasi atau penugasan. Draf tetap disimpan." }
        val pending=pending(scope,id)
        if(replaceValidationRejected) {
            require(pending.isNotEmpty() && pending.all {it.state=="REJECTED" && it.error?.optString("code")=="validation_failed"}) {"Operasi ini tidak dapat diganti sebagai perbaikan validasi."}
            record(scope,id)?.let {archive(scope,it,"replace_validation_rejected")}
            db.update("outbox",values("state" to "SUPERSEDED"),"scope=? AND entity_id=? AND state='REJECTED'",arrayOf(scope,id))
        } else require(pending.isEmpty()) { "Operasi sebelumnya masih menunggu atau perlu ditinjau. Simpan draf dulu." }
        val saved = persistDraft(scope,id,gh,body,expectedRevision,editingBaseVersion)
        afterRecordWrite()
        putQueued(scope,saved,if(saved.baseVersion==null) "create" else "update",body)
    }
    private fun putQueued(scope:String,saved:LocalRecord,action:String,payload:JSONObject):LocalRecord {
        requireMutationKind(scope,saved.kind)
        require((action=="create" && saved.baseVersion==null) || (action!="create" && (saved.baseVersion ?: 0)>0)) {"Versi dasar operasi tidak sesuai."}
        val operationId=UUID.randomUUID().toString()
        val operation=JSONObject().put("schema_version","2").put("sync_epoch",epoch(scope) ?: error("Epoch server belum dikenal."))
            .put("operation_id",operationId).put("entity_id",saved.id).put("entity_type",saved.kind).put("action",action)
            .put("base_version",saved.baseVersion ?: JSONObject.NULL).put("payload",JSONObject(payload.toString()))
        db.insertOrThrow("outbox",null,values("scope" to scope,"operation_id" to operationId,"entity_id" to saved.id,"local_revision" to saved.revision,
            "envelope" to encrypt(scope,operation),"canonical_hash" to WireContract.hash(operation),"state" to "QUEUED","created_at" to System.currentTimeMillis()))
        db.update("local_records",values("latest_operation_id" to operationId,"sync_state" to "QUEUED","error" to null),"scope=? AND entity_id=?",arrayOf(scope,saved.id))
        return record(scope,saved.id)!!
    }
    private fun archive(scope:String,current:LocalRecord,resolution:String) {
        db.insertWithOnConflict("local_record_revisions",null,values("scope" to scope,"entity_id" to current.id,"revision" to current.revision,"kind" to current.kind,
            "greenhouse_id" to current.greenhouseId,"body" to encrypt(scope,current.body),"server_body" to encrypt(scope,current.serverBody),
            "base_version" to current.baseVersion,"server_version" to current.serverVersion,"resolution" to resolution,"saved_at" to System.currentTimeMillis()),SQLiteDatabase.CONFLICT_IGNORE)
    }
    private fun pending(scope:String,id:String):List<QueuedOperation> = db.rawQuery("SELECT * FROM outbox WHERE scope=? AND entity_id=? AND state NOT IN ('ACCEPTED','SUPERSEDED')",arrayOf(scope,id)).use {rows -> buildList {while(rows.moveToNext()) add(rows.operation(scope))}}
    fun pendingFor(scope:String,id:String):List<QueuedOperation> {
        val record=record(scope,id) ?: return emptyList()
        require(record.greenhouseId in visibleGreenhouses(scope)) {"Penugasan tidak lagi berlaku."}
        return pending(scope,id)
    }
    fun detail(scope:String,id:String):LocalDetail?=transaction {
        val current=record(scope,id) ?: return@transaction null
        if(current.greenhouseId !in visibleGreenhouses(scope)) return@transaction null
        LocalDetail(current,pending(scope,id),archived(scope,id))
    }
    fun queueAction(scope:String,id:String,gh:String,kind:String,body:JSONObject,payload:JSONObject,action:String,baseVersion:Long,expectedRevision:Long?=null):LocalRecord = transaction {
        require(pending(scope,id).isEmpty()) {"Operasi sebelumnya masih menunggu atau perlu ditinjau."}
        val old=record(scope,id)
        require(old==null || old.syncState==SyncState.ACCEPTED) {"Selesaikan draf atau konflik sebelum tindakan ini."}
        if(old!=null) archive(scope,old,action)
        val saved=persistDraft(scope,id,gh,body,expectedRevision,baseVersion,kind)
        if(saved.baseVersion==null) db.update("local_records",values("base_version" to baseVersion,"server_version" to baseVersion,"server_body" to encrypt(scope,body)),"scope=? AND entity_id=?",arrayOf(scope,id))
        putQueued(scope,record(scope,id)!!,action,payload)
    }
    fun resolveConflict(scope:String,id:String,reviewedVersion:Long,expectedRevision:Long,keepLocalPayload:JSONObject?):LocalRecord = transaction {
        val current=record(scope,id) ?: error("Catatan tidak ditemukan.")
        requireMutationKind(scope,current.kind)
        require(current.revision==expectedRevision) {"Draf berubah. Tinjau perbandingan kembali."}
        require(current.greenhouseId in visibleGreenhouses(scope)) {"Penugasan tidak lagi berlaku."}
        val pending=pending(scope,id)
        require(pending.isNotEmpty() && pending.all {it.state=="CONFLICT"}) {"Tidak ada konflik yang dapat diselesaikan."}
        require(current.serverVersion==reviewedVersion && current.serverBody?.optLong("version")==reviewedVersion) {"Versi server berubah lagi. Muat ulang dan tinjau kembali."}
        archive(scope,current,if(keepLocalPayload==null) "use_server" else "keep_local")
        db.update("outbox",values("state" to "SUPERSEDED"),"scope=? AND entity_id=? AND state='CONFLICT'",arrayOf(scope,id))
        val body=keepLocalPayload ?: current.serverBody!!
        val patch=values("body" to encrypt(scope,body),"base_version" to reviewedVersion,"revision" to current.revision+1,"sync_state" to if(keepLocalPayload==null) "ACCEPTED" else "LOCAL",
            "error" to null,"latest_operation_id" to null,"updated_at" to System.currentTimeMillis());patch.putAll(indexValues(body))
        db.update("local_records",patch,"scope=? AND entity_id=?",arrayOf(scope,id))
        val result=record(scope,id)!!
        if(keepLocalPayload==null) result else putQueued(scope,result,"update",keepLocalPayload)
    }
    fun retryClosedSeason(scope:String,operationId:String) = transaction {
        val op=db.rawQuery("SELECT * FROM outbox WHERE scope=? AND operation_id=?",arrayOf(scope,operationId)).use {if(it.moveToFirst()) it.operation(scope) else null} ?: error("Operasi tidak ditemukan.")
        require(op.state=="REJECTED" && op.error?.optString("code")=="season_closed") {"Operasi ini memerlukan perbaikan lain."}
        require(op.envelope.getString("sync_epoch")==epoch(scope)) {"Epoch lama perlu rekonsiliasi."}
        val row=record(scope,op.entityId)!!;require(row.greenhouseId in visibleGreenhouses(scope)) {"Penugasan tidak lagi berlaku."}
        db.update("outbox",values("state" to "QUEUED","next_attempt_at" to 0L,"error" to null),"scope=? AND operation_id=?",arrayOf(scope,operationId))
        if(row.revision==op.revision) db.update("local_records",values("sync_state" to "QUEUED","error" to null),"scope=? AND entity_id=?",arrayOf(scope,row.id))
    }
    fun archived(scope:String,id:String):List<JSONObject> {
        val current=record(scope,id) ?: return emptyList();requireKind(scope,current.kind)
        require(current.greenhouseId in visibleGreenhouses(scope)) {"Penugasan tidak lagi berlaku."}
        return db.rawQuery("SELECT body FROM local_record_revisions WHERE scope=? AND entity_id=? ORDER BY revision DESC LIMIT 100",arrayOf(scope,id)).use {rows -> buildList {while(rows.moveToNext()) add(decode(scope,rows.getString(0))!!)}}
    }
    fun next(scope: String, now: Long = System.currentTimeMillis()): QueuedOperation? = db.rawQuery("SELECT * FROM outbox WHERE scope=? AND state='QUEUED' AND next_attempt_at<=? ORDER BY created_at,operation_id",arrayOf(scope,now.toString())).use { rows ->
        var result:QueuedOperation?=null
        while(rows.moveToNext() && result==null) {val op=rows.operation(scope);if(runCatching {requireMutationKind(scope,op.envelope.getString("entity_type"))}.isSuccess) result=op}
        result
    }
    fun acknowledge(scope: String, id: String, receipt: JSONObject) = transaction {
        val op = db.rawQuery("SELECT * FROM outbox WHERE scope=? AND operation_id=?",arrayOf(scope,id)).use { if(it.moveToFirst()) it.operation(scope) else null } ?: error("Operasi ACK tidak ditemukan.")
        require(op.envelope.getString("sync_epoch") == epoch(scope)) { "ACK berasal dari epoch lama; rekonsiliasi diperlukan." }
        require(receipt.getString("operation_id") == id && receipt.getString("entity_id") == op.entityId && receipt.getLong("version") > 0) { "Identitas ACK tidak cocok." }
        val authoritative = receipt.getJSONObject("data"); require(authoritative.getString("id") == op.entityId)
        val version = receipt.getLong("version")
        require(authoritative.getLong("version")==version) {"Versi ACK dan data tidak cocok."}
        val current = record(scope,op.entityId) ?: error("Catatan ACK tidak ditemukan.")
        db.update("outbox",values("state" to "ACCEPTED","error" to null),"scope=? AND operation_id=?",arrayOf(scope,id))
        val patch = values()
        if(version >= (current.serverVersion ?: 0)) { patch.put("server_version",version);patch.put("server_body",encrypt(scope,authoritative)) }
        if(current.latestOperationId == id && version>=(current.baseVersion ?: 0)) patch.put("base_version",version)
        if(current.revision == op.revision && current.latestOperationId == id && version >= (current.serverVersion ?: 0)) {
            patch.put("body",encrypt(scope,authoritative));patch.put("sync_state","ACCEPTED");patch.put("base_version",version);patch.putNull("error")
            patch.putAll(indexValues(authoritative))
        } else if(current.latestOperationId == id && current.revision != op.revision && current.syncState != SyncState.EPOCH_REVIEW) patch.put("sync_state","LOCAL")
        if(patch.size() > 0) db.update("local_records",patch,"scope=? AND entity_id=?",arrayOf(scope,op.entityId))
    }
    fun mark(scope: String, op: QueuedOperation, state: SyncState, error: JSONObject, nextAttemptAt: Long = 0) = transaction {
        val changed=db.update("outbox",values("state" to state.name,"attempts" to op.attempts + 1,"next_attempt_at" to nextAttemptAt,"error" to encrypt(scope,error)),"scope=? AND operation_id=? AND state NOT IN ('ACCEPTED','SUPERSEDED')",arrayOf(scope,op.id))
        if(changed==0) return@transaction
        val record = record(scope,op.entityId)
        if(record?.latestOperationId == op.id) {
            val patch=values("error" to encrypt(scope,error))
            if(record.revision==op.revision || state in setOf(SyncState.AUTH_REQUIRED,SyncState.SCOPE_REVOKED,SyncState.EPOCH_REVIEW)) patch.put("sync_state",state.name)
            if(error.has("current_data") && !error.isNull("current_data") && error.optLong("current_version")>=(record.serverVersion ?: 0)) {
                val remote=error.getJSONObject("current_data")
                require(remote.getString("id")==op.entityId && remote.getLong("version")==error.getLong("current_version")) {"Identitas konflik tidak cocok."}
                patch.put("server_body",encrypt(scope,remote));patch.put("server_version",error.getLong("current_version"))
            }
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
            requireKind(scope,kind)
            val existing=record(scope,id)
            require(existing==null || existing.kind==kind) {"Identitas catatan bertabrakan dengan jenis data lain. Sinkronisasi ditahan untuk rekonsiliasi."}
            val old=db.rawQuery("SELECT version FROM cached_records WHERE scope=? AND kind=? AND entity_id=?",arrayOf(scope,kind,id)).use { if(it.moveToFirst()) it.getLong(0) else 0L }
            if(version > old) {
                db.insertWithOnConflict("cached_records",null,values("scope" to scope,"kind" to kind,"entity_id" to id,"greenhouse_id" to gh,"version" to version,
                    "tombstone" to if(row.getBoolean("tombstone")) 1 else 0,"body" to encrypt(scope,row.getJSONObject("data"))),SQLiteDatabase.CONFLICT_REPLACE)
                if(kind in setOf("activity","harvest") || existing!=null) {
                    val local=existing;val body=row.getJSONObject("data")
                    if(local == null) {
                        val data=values("scope" to scope,"entity_id" to id,"kind" to kind,"greenhouse_id" to gh,"revision" to 1L,
                            "base_version" to version,"server_version" to version,"body" to encrypt(scope,body),"server_body" to encrypt(scope,body),"sync_state" to "ACCEPTED","updated_at" to System.currentTimeMillis());data.putAll(indexValues(body))
                        db.insertOrThrow("local_records",null,data)
                    }
                    else if(version > (local.serverVersion ?: 0)) {
                        val patch=values("server_version" to version,"server_body" to encrypt(scope,body))
                        if(local.syncState == SyncState.ACCEPTED) {patch.put("body",encrypt(scope,body));patch.put("base_version",version);patch.putAll(indexValues(body))}
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
