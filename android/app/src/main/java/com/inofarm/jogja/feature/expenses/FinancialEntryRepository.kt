package com.inofarm.jogja.feature.expenses

import com.inofarm.jogja.core.contracts.RecordKind
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FinancialValidationFailure(val fields:Map<String,String>,val baseVersion:Long?):IllegalArgumentException(fields.values.joinToString("\n"))
class FinancialEntryRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore) {
    private val writes=Mutex()
    suspend fun flush(session:FinanceSession,ticket:Long,draft:FinancialDraft)=save(session,ticket,draft,false,onlyIfChanged=true)
    suspend fun save(session:FinanceSession,ticket:Long,draft:FinancialDraft,queue:Boolean,onlyIfChanged:Boolean=false)=withContext(Dispatchers.IO) {writes.withLock {
        check(vault.matches(ticket,session.scope)) {"Sesi berubah."};require(session.role==UserRole.MANAGER) {"Keuangan hanya untuk pengelola."}
        val existing=store.record(session.scope.key,draft.id)
        val known=existing?.baseVersion ?: existing?.serverVersion
        if(!queue && onlyIfChanged && existing!=null) {
            val saved=runCatching {FinancialDraft.from(existing.id,draft.kind,existing.greenhouseId,existing.body,known)}.getOrNull()
            if(saved==draft.copy(baseVersion=draft.baseVersion ?: known)) return@withLock existing
        }
        if(queue) {
            val effective=draft.copy(baseVersion=draft.baseVersion ?: known)
            val errors=effective.validation()
            if(errors.isNotEmpty()) throw FinancialValidationFailure(errors,effective.baseVersion)
            if(draft.kind==RecordKind.COST_ALLOCATION) {
                val source=store.cachedOne(session.scope.key,"expense",draft.expenseId) ?: error("Pembayaran sumber belum diterima dan tersinkron. Simpan draf dahulu.")
                require(source.getString("greenhouse_id")==draft.greenhouseId && source.getString("record_state")=="active" && source.getString("payment_kind")=="operating") {"Pilih sumber pembayaran operasi aktif dari greenhouse yang sama."}
                val season=store.cachedOne(session.scope.key,"seasons",draft.seasonId) ?: error("Musim belum tersimpan. Sinkronkan dahulu.")
                require(season.getString("greenhouse_id")==draft.greenhouseId && season.getString("status")=="open") {"Musim harus terbuka dan berasal dari greenhouse yang sama."}
            }
            store.saveAndQueue(session.scope.key,draft.id,draft.greenhouseId,effective.copy(lockedParents=true).localJson().put("completeness","ready"),existing?.revision,effective.baseVersion,
                effective.replaceValidationRejected,kind=draft.kind.wire,queuePayload=effective.payload())
        } else store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion,kind=draft.kind.wire)
    }}
}
