package com.inofarm.jogja.feature.context

import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.core.sync.FinanceSyncCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ContextRecords(val greenhouses:List<JSONObject>,val seasons:List<JSONObject>,val groups:List<JSONObject>,val materials:List<JSONObject>,val units:List<JSONObject>,val schedules:List<JSONObject>,val targets:List<JSONObject>,val lastSyncedAt:Long?,val grades:List<JSONObject> = emptyList(),val harvestBalances:List<JSONObject> = emptyList(),val expenseCategories:List<JSONObject> = emptyList(),val expenses:List<JSONObject> = emptyList(),val expenseBalances:List<JSONObject> = emptyList(),val harvests:List<JSONObject> = emptyList(),val assets:List<JSONObject> = emptyList(), val varieties: List<JSONObject> = emptyList())
class ContextRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val sync:FinanceSyncCoordinator) {
    suspend fun snapshot(session: FinanceSession) = withContext(Dispatchers.IO) {
        val key = session.scope.key
        val manager = session.role == UserRole.MANAGER
        ContextRecords(
            greenhouses = store.cachedAll(key, "greenhouses"),
            seasons = store.cachedAll(key, "seasons"),
            groups = store.cachedAll(key, "planting_groups"),
            materials = store.cachedAll(key, "materials"),
            units = store.cachedAll(key, "units"),
            schedules = store.cachedAll(key, "schedules"),
            targets = store.cachedAll(key, "targets"),
            lastSyncedAt = store.lastSyncedAt(key),
            grades = store.cachedAll(key, "grades"),
            harvestBalances = store.cachedAll(key, "harvest_balance"),
            expenseCategories = if (manager) store.cachedAll(key, "expense_categories") else emptyList(),
            expenses = if (manager) store.cachedAll(key, "expense") else emptyList(),
            expenseBalances = if (manager) store.cachedAll(key, "expense_balance") else emptyList(),
            harvests = store.cachedAll(key, "harvest"),
            assets = if (manager) store.cachedAll(key, "asset") else emptyList(),
            varieties = store.cachedAll(key, "varieties"),
        )
    }
    suspend fun synchronize()=sync.run(waitForTurn = true)
}
