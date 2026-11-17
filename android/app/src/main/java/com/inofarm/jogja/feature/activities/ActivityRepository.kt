package com.inofarm.jogja.feature.activities

import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.EntryDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ActivityRecords(val records:List<LocalRecord>,val queue:List<QueuedOperation>)
class DraftValidationFailure(val fields:Map<String,String>,val baseVersion:Long?):IllegalArgumentException(fields.values.joinToString("\n"))
class ActivityRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore) {
    private val writes=Mutex()
    suspend fun snapshot(session:FinanceSession)=withContext(Dispatchers.IO) { ActivityRecords(store.records(session.scope.key,1000),store.outbox(session.scope.key,1000)) }
    suspend fun flush(session:FinanceSession,ticket:Long,draft:ActivityDraft)=save(session,ticket,draft,false,onlyIfChanged=true)
    /** "Batal": undoes the autosaved edits of [id] back to [origin], the row as the form opened it (null when new). */
    suspend fun discard(session:FinanceSession,ticket:Long,id:String,origin:com.inofarm.jogja.core.database.LocalRecord?)=withContext(Dispatchers.IO) {writes.withLock {
        check(vault.matches(ticket,session.scope)) {"Sesi berubah."};store.restoreDraft(session.scope.key,id,origin)
    }}
    /**
     * A greenhouse entry: [queue] false keeps it as one local draft; true turns it into its section records and their
     * operations in one local transaction (see LocalFinanceStore.queueEntry). Returns the entry with fixed section ids.
     */
    suspend fun saveEntry(session:FinanceSession,ticket:Long,entry:EntryDraft,queue:Boolean):EntryDraft=withContext(Dispatchers.IO) { writes.withLock {
        check(vault.matches(ticket,session.scope)) { "Sesi berubah." }
        val existing=store.record(session.scope.key,entry.id)
        if(!queue) {
            store.saveDraft(session.scope.key,entry.id,entry.greenhouseId,entry.localJson(),existing?.revision,null)
            return@withLock entry
        }
        val fixed=entry.withSectionIds()
        val errors=fixed.validation()
        if(errors.isNotEmpty()) throw DraftValidationFailure(errors,null)
        // Fix the section ids on the device first, so a later retry after any failure reuses the same UUIDs.
        val kept=store.saveDraft(session.scope.key,fixed.id,fixed.greenhouseId,fixed.localJson(),existing?.revision,null)
        store.queueEntry(session.scope.key,fixed.id,fixed.greenhouseId,fixed.sections().map { section ->
            val payload=section.payload()
            com.inofarm.jogja.core.database.QueuedSection(section.id,payload,payload)
        },kept.revision)
        fixed
    } }
    suspend fun save(session:FinanceSession,ticket:Long,draft:ActivityDraft,queue:Boolean,onlyIfChanged:Boolean=false)=withContext(Dispatchers.IO) { writes.withLock {
        check(vault.matches(ticket,session.scope)) { "Sesi berubah." }
        val existing=store.record(session.scope.key,draft.id)
        if(!queue && onlyIfChanged && existing!=null) {
            val known=existing.baseVersion ?: existing.serverVersion
            val stored=runCatching {ActivityDraft.from(existing.id,existing.greenhouseId,existing.body,known)}.getOrNull()
            if(stored==draft.copy(baseVersion=draft.baseVersion ?: known)) return@withLock existing
        }
        if(queue) {
            val effective=draft.copy(baseVersion=draft.baseVersion ?: existing?.baseVersion)
            val errors=effective.validation(session.role!=com.inofarm.jogja.core.contracts.UserRole.MANAGER)
            if(errors.isNotEmpty()) throw DraftValidationFailure(errors,effective.baseVersion)
            store.saveAndQueue(session.scope.key,draft.id,draft.greenhouseId,effective.payload(),existing?.revision,effective.baseVersion,effective.replaceValidationRejected)
        }
        else store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion)
    } }
}
