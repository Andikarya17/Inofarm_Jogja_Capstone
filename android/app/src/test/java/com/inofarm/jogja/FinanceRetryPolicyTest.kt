package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.sync.RetryPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FinanceRetryPolicyTest {
    @Test fun onlyTransientErrorsRetryAndBusinessFailuresRemainReviewable() {
        fun error(status:Int,code:String="")=FinanceApiFailure(status,JSONObject().put("code",code))
        assertEquals(SyncState.AUTH_REQUIRED,RetryPolicy.state(error(401)))
        assertEquals(SyncState.SCOPE_REVOKED,RetryPolicy.state(error(403)))
        assertEquals(SyncState.CONFLICT,RetryPolicy.state(error(409,"version_conflict")))
        assertEquals(SyncState.EPOCH_REVIEW,RetryPolicy.state(error(409,"epoch_mismatch")))
        assertEquals(SyncState.REJECTED,RetryPolicy.state(error(409,"season_closed")))
        assertEquals(SyncState.REJECTED,RetryPolicy.state(error(422)))
        assertEquals(SyncState.QUEUED,RetryPolicy.state(error(429)))
        assertEquals(SyncState.QUEUED,RetryPolicy.state(error(503)))
        assertEquals(60000L,RetryPolicy.delayMillis(0,60))
        assertTrue(RetryPolicy.delayMillis(Int.MAX_VALUE) <= 300000)
    }
}
