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

internal enum class ReviewKind(val wire: String, val inbox: String, val collection: String, val label: String) {
    ACTIVITY("activity", "activity-reviews", "activities", "kegiatan"),
    HARVEST("harvest", "harvest-reviews", "harvests", "panen");

    val versionKey get() = "${wire}_version"
    val parentKey get() = "${wire}_id"
    val operationKind get() = "${wire}_review"
    fun detail(id: String) = "$collection/$id/verification"
}

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

    private fun draftId(session: FinanceSession, kind: ReviewKind, record: String, version: Long): String =
        UUID.nameUUIDFromBytes("inofarm:${kind.wire}-review:${session.scope.accountId}:$record:$version".toByteArray(Charsets.UTF_8)).toString()

    suspend fun draft(session: FinanceSession, ticket: Long, kind: ReviewKind, record: String, version: Long): LocalRecord? =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            require(session.role == UserRole.MANAGER)
            store.detail(session.scope.key, draftId(session, kind, record, version))?.record
        }

    suspend fun save(session: FinanceSession, ticket: Long, kind: ReviewKind, entry: JSONObject, decision: String, reason: String, queue: Boolean) =
        withContext(Dispatchers.IO) {
            writes.withLock {
                checkSession(session, ticket)
                require(session.role == UserRole.MANAGER && entry.optString("status") == "pending")
                require(kind != ReviewKind.HARVEST || entry.optBoolean("can_review")) { "Revisi panen ini tidak dapat diputuskan." }
                require(decision in setOf("valid", "invalid") && reason.length <= 1000)
                require(!queue || decision != "invalid" || reason.isNotBlank()) { "Isi alasan tidak valid." }
                val activity = entry.getJSONObject(kind.wire)
                val activityId = activity.getString("id")
                val version = activity.getLong("version")
                val greenhouse = entry.getJSONObject("context").getString("greenhouse_id")
                val id = draftId(session, kind, activityId, version)
                val old = store.record(session.scope.key, id)
                require(old == null || old.syncState == SyncState.LOCAL) { "Pengajuan sudah ada. Periksa Pengiriman saya." }
                val payload = JSONObject().put(kind.parentKey, activityId).put(kind.versionKey, version)
                    .put("decision", decision).put("reason", reason.trim().ifBlank { null } ?: JSONObject.NULL)
                if (queue) store.saveAndQueue(session.scope.key, id, greenhouse, payload, old?.revision, kind = kind.operationKind)
                else store.saveDraft(session.scope.key, id, greenhouse, payload, old?.revision, kind = kind.operationKind)
            }
        }

    suspend fun cachedFeedback(session: FinanceSession, ticket: Long, kind: ReviewKind, record: LocalRecord): List<JSONObject> =
        withContext(Dispatchers.IO) {
            checkSession(session, ticket)
            if (session.role == UserRole.WORKER && record.serverBody?.optString("recorded_by") != session.scope.accountId) {
                return@withContext emptyList()
            }
            buildList {
                do {
                    val page = store.records(session.scope.key, kind = kind.operationKind,
                        filter = LocalHistoryFilter(ownerId = record.id), limit = 100, offset = size)
                    addAll(page)
                } while (page.size == 100)
            }.filter { it.syncState == SyncState.ACCEPTED }
                .map { it.body }.sortedByDescending { it.optLong(kind.versionKey) }
        }
}
