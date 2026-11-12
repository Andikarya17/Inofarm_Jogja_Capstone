package com.inofarm.jogja.feature.harvest

import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.database.QueuedOperation
import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.harvest.domain.HarvestDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class HarvestRecords(val records:List<LocalRecord>,val queue:List<QueuedOperation>)
class HarvestValidationFailure(val fields:Map<String,String>,val baseVersion:Long?):IllegalArgumentException(fields.values.joinToString("\n"))
class HarvestRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore, private val api: FinanceApi) {
    private val writes=Mutex()
    suspend fun snapshot(session:FinanceSession)=withContext(Dispatchers.IO) { HarvestRecords(store.records(session.scope.key,1000,kind="harvest"),store.outbox(session.scope.key,1000)) }
    suspend fun flush(session:FinanceSession,ticket:Long,draft:HarvestDraft)=save(session,ticket,draft,false,onlyIfChanged=true)
    private fun checkSession(session: FinanceSession, ticket: Long) {
        check(vault.matches(ticket, session.scope) && vault.current?.token == session.token &&
            vault.current?.role == session.role) { "Sesi berubah. Buka kembali rekap." }
    }

    suspend fun progress(session: FinanceSession, ticket: Long, group: String): JSONObject = withContext(Dispatchers.IO) {
        checkSession(session, ticket)
        api.request(session.scope.server, session.token, "planting-groups/$group/harvest-progress")
            .also { checkSession(session, ticket); require(it.getString("group_id") == group) }
    }

    suspend fun cachedWorkerProgress(session: FinanceSession, ticket: Long, greenhouse: String, group: String): JSONObject? =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            store.cachedOne(session.scope.key, "worker_harvest_progress", group)
                ?.takeIf { it.optString("greenhouse_id") == greenhouse }
        }

    suspend fun workerProgress(session: FinanceSession, ticket: Long, greenhouse: String, group: String): JSONObject =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            require(session.role == UserRole.WORKER)
            val epoch = store.epoch(session.scope.key)
            try {
                progress(session, ticket, group).also {
                    require(it.getString("greenhouse_id") == greenhouse && it.has("verification"))
                    store.cacheWorkerHarvestProgress(session.scope.key, greenhouse, group, it, epoch)
                }
            } catch (error: FinanceApiFailure) {
                checkSession(session, ticket)
                if (error.status in setOf(401, 403, 404)) store.forgetWorkerHarvestProgress(session.scope.key, group)
                throw error
            }
        }

    suspend fun progressSources(session: FinanceSession, ticket: Long, group: String, status: String?, cursor: String?) =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            val query = buildMap {
                status?.let { put("status", it) }
                cursor?.let { put("cursor", it) }
            }
            api.request(session.scope.server, session.token, "planting-groups/$group/harvest-progress/sources", query = query)
                .also { checkSession(session, ticket) }
        }

    suspend fun transitionState(session: FinanceSession, ticket: Long, group: String): LocalRecord? = withContext(Dispatchers.IO) {
        checkSession(session, ticket)
        store.detail(session.scope.key, group)?.record?.takeIf { it.kind == "planting_groups" }
    }

    suspend fun queueTransition(session: FinanceSession, ticket: Long, progress: JSONObject, complete: Boolean, reason: String) =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            require(session.role == UserRole.MANAGER && reason.length <= 1000)
            val id = progress.getString("group_id")
            val greenhouse = progress.getString("greenhouse_id")
            val version = progress.getLong("group_version")
            val current = store.record(session.scope.key, id)
            val master = store.cachedOne(session.scope.key, "planting_groups", id)?.takeIf { it.optLong("version") == version }
                ?: JSONObject().put("id", id).put("season_id", progress.getString("season_id")).put("version", version)
            store.queueAction(session.scope.key, id, greenhouse, "planting_groups", master, JSONObject().apply { if (reason.isNotBlank()) put("reason", reason.trim()) },
                if (complete) "complete_harvest" else "reopen_harvest", version, current?.revision)
        }

    suspend fun useCurrentTransition(session: FinanceSession, ticket: Long, progress: JSONObject, local: LocalRecord) =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            require(session.role == UserRole.MANAGER && local.kind == "planting_groups" && local.syncState == SyncState.CONFLICT)
            require(progress.getString("group_id") == local.id)
            store.resolveConflict(session.scope.key, local.id, progress.getLong("group_version"), local.revision, null)
        }
    suspend fun save(session:FinanceSession,ticket:Long,draft:HarvestDraft,queue:Boolean,onlyIfChanged:Boolean=false)=withContext(Dispatchers.IO) { writes.withLock {
        check(vault.matches(ticket,session.scope)) { "Sesi berubah." }
        val existing=store.record(session.scope.key,draft.id)
        if(!queue && onlyIfChanged && existing!=null) {
            val known=existing.baseVersion ?: existing.serverVersion
            val stored=runCatching {HarvestDraft.from(existing.id,existing.greenhouseId,existing.body,known)}.getOrNull()
            if(stored==draft.copy(baseVersion=draft.baseVersion ?: known)) return@withLock existing
        }
        if(queue) {
            val effective=draft.copy(baseVersion=draft.baseVersion ?: existing?.baseVersion)
            val errors=effective.validation(session.role!=UserRole.MANAGER)
            if(errors.isNotEmpty()) throw HarvestValidationFailure(errors,effective.baseVersion)
            val local=effective.copy(lockedItems=effective.lockedItems+effective.items.map {it.id}).localJson().put("completeness","ready")
            store.saveAndQueue(session.scope.key,draft.id,draft.greenhouseId,local,existing?.revision,effective.baseVersion,effective.replaceValidationRejected,kind="harvest",queuePayload=effective.payload())
        }
        else store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion,kind="harvest")
    } }
}
