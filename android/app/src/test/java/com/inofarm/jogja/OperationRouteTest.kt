package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.UserRole
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
    }
    @Test fun workerAndUnknownOperationsCannotFallThroughToActivityEndpoint() {
        assertThrows(IllegalArgumentException::class.java) {OperationRoute.forOperation("seasons","close",id,UserRole.WORKER)}
        assertThrows(IllegalStateException::class.java) {OperationRoute.forOperation("sale","create",id,UserRole.MANAGER)}
        assertThrows(IllegalStateException::class.java) {OperationRoute.forOperation("targets","cancel",id,UserRole.MANAGER)}
    }
}
