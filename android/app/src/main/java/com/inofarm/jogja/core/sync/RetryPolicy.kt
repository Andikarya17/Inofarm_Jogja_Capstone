package com.inofarm.jogja.core.sync

import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.network.FinanceApiFailure

object RetryPolicy {
    fun state(error: FinanceApiFailure): SyncState = when {
        error.status == 401 -> SyncState.AUTH_REQUIRED
        error.status == 403 || error.status == 404 -> SyncState.SCOPE_REVOKED
        error.code == "epoch_mismatch" -> SyncState.EPOCH_REVIEW
        error.code == "version_conflict" -> SyncState.CONFLICT
        error.status == 429 || error.status >= 500 -> SyncState.QUEUED
        else -> SyncState.REJECTED
    }
    fun delayMillis(attempts: Int, retryAfterSeconds: Long? = null): Long {
        val backoff=(1000L shl attempts.coerceIn(0,8)).coerceAtMost(300000)
        return maxOf(backoff,(retryAfterSeconds ?: 0).coerceIn(0,86400)*1000)
    }
}
