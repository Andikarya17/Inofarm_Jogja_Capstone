package com.inofarm.jogja.core.sync

import com.inofarm.jogja.core.contracts.ReviewableRejections
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.session.FinanceSession
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pengelola forms send on "Simpan" and wait for the server (decision 28 Nov 2026), so a refusal shows in the
 * open form instead of turning up later as "N belum terkirim". Without a connection the record keeps its place in
 * the queue and is sent automatically, as before. Workers keep the offline-first flow.
 */
object ManagerDelivery {
    sealed interface Result {
        val message: String
        data class Accepted(override val message: String) : Result
        data class Waiting(override val message: String) : Result
        /** [record] is the refused record; [replaceable] when the form may fix it and send it again. */
        data class Rejected(override val message: String, val record: LocalRecord?, val replaceable: Boolean) : Result
    }

    const val QUEUED = "Tersimpan. Terkirim otomatis saat ada internet."
    private const val WAIT_MILLIS = 25_000L
    @Volatile private var sync: FinanceSyncCoordinator? = null
    @Volatile private var store: LocalFinanceStore? = null

    fun install(sync: FinanceSyncCoordinator, store: LocalFinanceStore) { this.sync = sync; this.store = store }

    fun waits(session: FinanceSession) = session.role == UserRole.MANAGER && sync != null

    suspend fun deliver(session: FinanceSession, ids: Collection<String>, accepted: String = "Tersimpan di server."): Result {
        val sync = sync; val store = store
        if (session.role != UserRole.MANAGER || sync == null || store == null) return Result.Waiting(QUEUED)
        val outcome = withTimeoutOrNull(WAIT_MILLIS) { sync.run(waitForTurn = true) }
        val key = session.scope.key
        val pending = ids.flatMap { id -> runCatching { store.pendingFor(key, id) }.getOrDefault(emptyList()) }
        val refused = pending.firstOrNull { it.state == "REJECTED" }
        if (refused != null) {
            val record = runCatching { store.record(key, refused.entityId) }.getOrNull()
            val code = refused.error?.optString("code")
            val reason = refused.error?.optString("message").orEmpty().ifBlank { "Periksa isian lalu simpan lagi." }
            val replaceable = record != null && ReviewableRejections.mayReplace(record.kind, code, refused.envelope.optString("action"))
            return Result.Rejected("Belum tersimpan. Server menolak: $reason", record, replaceable)
        }
        if (pending.isEmpty()) return Result.Accepted(accepted)
        return Result.Waiting(if (outcome == null || outcome.contextCheck in setOf(ContextCheck.OFFLINE, ContextCheck.FAILED))
            "Belum terkirim karena koneksi ke server gagal. $QUEUED" else QUEUED)
    }
}
