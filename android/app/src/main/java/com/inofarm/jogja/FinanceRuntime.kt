package com.inofarm.jogja

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.inofarm.jogja.core.database.FinanceDatabase
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.network.FinanceHttpApi
import com.inofarm.jogja.core.session.AndroidPayloadCipher
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.core.sync.FinanceSyncCoordinator
import com.inofarm.jogja.feature.auth.AuthRepository
import com.inofarm.jogja.feature.activities.ActivityRepository
import com.inofarm.jogja.feature.context.ContextRepository
import com.inofarm.jogja.feature.history.HistoryRepository
import com.inofarm.jogja.feature.harvest.HarvestRepository
import com.inofarm.jogja.core.contracts.OperationalKind
import com.inofarm.jogja.feature.expenses.FinancialEntryRepository

/** One database/session generation per application process, shared across activity recreation. */
class InofarmApplication:Application() {
    val finance:FinanceDependencies by lazy {FinanceDependencies(this)}
}

class FinanceDependencies(application:Application) {
    private val cipher=AndroidPayloadCipher()
    val vault=FinanceSessionVault(application,cipher)
    val store=LocalFinanceStore(FinanceDatabase(application),cipher)
    val api=FinanceHttpApi(BuildConfig.DEBUG)
    val sync=FinanceSyncCoordinator(api,vault,store)
    val auth=AuthRepository(api,vault,store)
    val activities=ActivityRepository(vault,store)
    val context=ContextRepository(vault,store,sync)
    val history=HistoryRepository(vault,store,api)
    val harvest=HarvestRepository(vault,store)
    val harvestHistory=HistoryRepository(vault,store,api,OperationalKind.HARVEST)
    val financialEntry=FinancialEntryRepository(vault,store)
    val expenseHistory=HistoryRepository(vault,store,api,OperationalKind.EXPENSE)
    val allocationHistory=HistoryRepository(vault,store,api,OperationalKind.COST_ALLOCATION)
}

class FinanceRuntime(application:Application):AndroidViewModel(application) {
    private val dependencies=(application as InofarmApplication).finance
    val vault get()=dependencies.vault
    val store get()=dependencies.store
    val api get()=dependencies.api
    val sync get()=dependencies.sync
    val auth get()=dependencies.auth
    val activities get()=dependencies.activities
    val context get()=dependencies.context
    val history get()=dependencies.history
    val harvest get()=dependencies.harvest
    val harvestHistory get()=dependencies.harvestHistory
    val financialEntry get()=dependencies.financialEntry
    val expenseHistory get()=dependencies.expenseHistory
    val allocationHistory get()=dependencies.allocationHistory
}
