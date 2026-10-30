package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.ReviewableRejections
import com.inofarm.jogja.core.sync.OperationRoute
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OperationRouteTest {
    private val id=UUID.randomUUID().toString()
    @Test fun activityAndManagerSeasonHaveDistinctEndpoints() {
        assertEquals(OperationRoute("sync/operations","POST"),OperationRoute.forOperation("activity","cancel",id,UserRole.MANAGER))
        assertEquals(OperationRoute("seasons/$id/reopen","POST"),OperationRoute.forOperation("seasons","reopen",id,UserRole.MANAGER))
        assertEquals(OperationRoute("planting-groups/$id","PATCH"),OperationRoute.forOperation("planting_groups","update",id,UserRole.MANAGER))
        assertEquals(OperationRoute("harvests","POST"),OperationRoute.forOperation("harvest","create",id,UserRole.WORKER))
        assertEquals(OperationRoute("harvests/$id","PATCH"),OperationRoute.forOperation("harvest","update",id,UserRole.WORKER))
    }
    @Test fun workerAndUnknownOperationsCannotFallThroughToActivityEndpoint() {
        assertThrows(IllegalArgumentException::class.java) {OperationRoute.forOperation("seasons","close",id,UserRole.WORKER)}
        assertThrows(IllegalStateException::class.java) {OperationRoute.forOperation("unknown_future_kind","create",id,UserRole.MANAGER)}
        assertEquals(OperationRoute("sales","POST"),OperationRoute.forOperation("sale","create",id,UserRole.MANAGER))
        assertThrows(IllegalStateException::class.java) {OperationRoute.forOperation("targets","cancel",id,UserRole.MANAGER)}
    }
    @Test fun rejectedSourceBalanceMayBeReviewedWhileIdentityAndEpochFailuresRemainHeld() {
        assertTrue(ReviewableRejections.mayReplace("harvest","harvest_in_use"))
        assertFalse(ReviewableRejections.mayReplace("activity","harvest_in_use"))
        for(code in listOf("entity_exists","operation_conflict","epoch_mismatch","season_closed")) assertFalse(ReviewableRejections.mayReplace("harvest",code))
    }
    @Test fun financialRoutesRequireManagerAndOnlyReviewedBudgetRejectionsAreReplaceable() {
        assertEquals(OperationRoute("expenses","POST"),OperationRoute.forOperation("expense","create",id,UserRole.MANAGER))
        assertEquals(OperationRoute("cost-allocations/$id/cancel","POST"),OperationRoute.forOperation("cost_allocation","cancel",id,UserRole.MANAGER))
        for(kind in listOf("expense","cost_allocation","sale")) for(action in listOf("create","update","cancel")) assertThrows(IllegalArgumentException::class.java) {OperationRoute.forOperation(kind,action,id,UserRole.WORKER)}
        assertTrue(ReviewableRejections.mayReplace("sale","harvest_exceeded"))
        assertTrue(ReviewableRejections.mayReplace("expense","expense_in_use"));assertTrue(ReviewableRejections.mayReplace("cost_allocation","allocation_exceeds_source"))
        for(code in listOf("entity_exists","operation_conflict","epoch_mismatch","season_closed","expense_balance_mismatch")) assertFalse(ReviewableRejections.mayReplace("cost_allocation",code))
    }
}
