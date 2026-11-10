package com.inofarm.jogja.feature.verification

import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalHistoryFilter
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

internal class ActivityVerificationRepository(
    private val api: FinanceApi,
    private val store: LocalFinanceStore,
    private val vault: FinanceSessionVault,
) {
    private val writes = Mutex()
    private fun checkSession(session: FinanceSession, ticket: Long) {
        check(vault.matches(ticket, session.scope) && vault.current?.token == session.token &&
            vault.current?.role == session.role) { "Sesi berubah. Buka kembali catatan." }
    }

    suspend fun read(session: FinanceSession, ticket: Long, path: String, query: Map<String, String> = emptyMap()) =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            require(!session.expired()) { "Masuk kembali untuk memeriksa data server." }
            api.request(session.scope.server, session.token, path, query = query).also { checkSession(session, ticket) }
        }

    private fun draftId(session: FinanceSession, activity: String, version: Long): String =
        UUID.nameUUIDFromBytes("inofarm:activity-review:${session.scope.accountId}:$activity:$version".toByteArray(Charsets.UTF_8)).toString()

    suspend fun draft(session: FinanceSession, ticket: Long, activity: String, version: Long): LocalRecord? =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            require(session.role == UserRole.MANAGER)
            store.detail(session.scope.key, draftId(session, activity, version))?.record
        }

    suspend fun save(session: FinanceSession, ticket: Long, entry: JSONObject, decision: String, reason: String, queue: Boolean) =
        withContext(Dispatchers.IO) {
            writes.withLock {
                checkSession(session, ticket)
                require(session.role == UserRole.MANAGER && entry.optString("status") == "pending")
                require(decision in setOf("valid", "invalid") && reason.length <= 1000)
                require(!queue || decision != "invalid" || reason.isNotBlank()) { "Isi alasan tidak valid." }
                val activity = entry.getJSONObject("activity")
                val activityId = activity.getString("id")
                val version = activity.getLong("version")
                val greenhouse = entry.getJSONObject("context").getString("greenhouse_id")
                val id = draftId(session, activityId, version)
                val old = store.record(session.scope.key, id)
                require(old == null || old.syncState == SyncState.LOCAL) { "Pengajuan sudah ada. Periksa Pengiriman saya." }
                val payload = JSONObject().put("activity_id", activityId).put("activity_version", version)
                    .put("decision", decision).put("reason", reason.trim().ifBlank { null } ?: JSONObject.NULL)
                if (queue) store.saveAndQueue(session.scope.key, id, greenhouse, payload, old?.revision, kind = "activity_review")
                else store.saveDraft(session.scope.key, id, greenhouse, payload, old?.revision, kind = "activity_review")
            }
        }

    suspend fun cachedFeedback(session: FinanceSession, ticket: Long, record: LocalRecord): List<JSONObject> =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            if (session.role == UserRole.WORKER && record.serverBody?.optString("recorded_by") != session.scope.accountId) {
                return@withContext emptyList()
            }
            store.records(session.scope.key, kind = "activity_review", filter = LocalHistoryFilter(ownerId = record.id))
                .filter { it.syncState == SyncState.ACCEPTED }
                .map { it.body }
                .sortedByDescending { it.optLong("activity_version") }
        }
}
