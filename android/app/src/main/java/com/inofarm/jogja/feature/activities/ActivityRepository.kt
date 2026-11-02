package com.inofarm.jogja.feature.activities

import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
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
            val errors=effective.validation()
            if(errors.isNotEmpty()) throw DraftValidationFailure(errors,effective.baseVersion)
            store.saveAndQueue(session.scope.key,draft.id,draft.greenhouseId,effective.payload(),existing?.revision,effective.baseVersion,effective.replaceValidationRejected)
        }
        else store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion)
    } }
}
