package com.inofarm.jogja.feature.context

import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.core.sync.FinanceSyncCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ContextRecords(val greenhouses:List<JSONObject>,val seasons:List<JSONObject>,val groups:List<JSONObject>,val materials:List<JSONObject>,val units:List<JSONObject>,val schedules:List<JSONObject>,val targets:List<JSONObject>,val lastSyncedAt:Long?,val grades:List<JSONObject> = emptyList(),val harvestBalances:List<JSONObject> = emptyList())
class ContextRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val sync:FinanceSyncCoordinator) {
    suspend fun snapshot(session:FinanceSession)=withContext(Dispatchers.IO) { val key=session.scope.key
        ContextRecords(store.cached(key,"greenhouses",limit=1000),store.cached(key,"seasons",limit=1000),store.cached(key,"planting_groups",limit=1000),
            store.cached(key,"materials",limit=1000),store.cached(key,"units",limit=1000),store.cached(key,"schedules",limit=1000),store.cached(key,"targets",limit=1000),store.lastSyncedAt(key),store.cached(key,"grades",limit=1000),store.cached(key,"harvest_balance",limit=1000)) }
    suspend fun synchronize()=sync.run()
}
