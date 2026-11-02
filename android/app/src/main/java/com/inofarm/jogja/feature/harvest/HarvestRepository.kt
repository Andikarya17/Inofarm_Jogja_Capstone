package com.inofarm.jogja.feature.harvest

import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.harvest.domain.HarvestDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class HarvestRecords(val records:List<LocalRecord>,val queue:List<QueuedOperation>)
class HarvestValidationFailure(val fields:Map<String,String>,val baseVersion:Long?):IllegalArgumentException(fields.values.joinToString("\n"))
class HarvestRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore) {
    private val writes=Mutex()
    suspend fun snapshot(session:FinanceSession)=withContext(Dispatchers.IO) { HarvestRecords(store.records(session.scope.key,1000,kind="harvest"),store.outbox(session.scope.key,1000)) }
    suspend fun save(session:FinanceSession,ticket:Long,draft:HarvestDraft,queue:Boolean)=withContext(Dispatchers.IO) { writes.withLock {
        check(vault.matches(ticket,session.scope)) { "Sesi berubah." }
        val existing=store.record(session.scope.key,draft.id)
        if(queue) {
            val effective=draft.copy(baseVersion=draft.baseVersion ?: existing?.baseVersion)
            val errors=effective.validation()
            if(errors.isNotEmpty()) throw HarvestValidationFailure(errors,effective.baseVersion)
            val local=effective.copy(lockedItems=effective.lockedItems+effective.items.map {it.id}).localJson().put("completeness","ready")
            store.saveAndQueue(session.scope.key,draft.id,draft.greenhouseId,local,existing?.revision,effective.baseVersion,effective.replaceValidationRejected,kind="harvest",queuePayload=effective.payload())
        }
        else store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion,kind="harvest")
    } }
}
