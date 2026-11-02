package com.inofarm.jogja

import com.inofarm.jogja.feature.assets.*
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.sync.OperationRoute
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AssetDraftTest {
    private fun id()=UUID.randomUUID().toString()
    @Test fun incompleteRawFormRoundTripsWithoutInventedValues() {
        val draft=AssetDraft(greenhouseId=id(),kind=AssetKind.ASSET,cost="1,",name="Pompa",lifeYears="",reason="Isian terakhir")
        assertEquals(draft,AssetDraft.from(draft.id,draft.greenhouseId,draft.kind,draft.localJson(),null))
        assertTrue(draft.validation().isNotEmpty());assertTrue(runCatching {draft.payload()}.isFailure)
    }
    @Test fun validAssetAndAllocationUseExactWireTypesAndRequiredBasis() {
        val asset=AssetDraft(greenhouseId=id(),kind=AssetKind.ASSET,expenseId=id(),name="Pompa sintetis",cost="100,00",residual="0",lifeYears="3",inServiceDate="2024-02-29",basis="Pemakaian manual")
        assertTrue(asset.validation().isEmpty());assertEquals("100.00",asset.payload().getString("cost"));assertTrue(asset.payload().get("life_years") is Int)
        assertTrue(asset.copy(residual="100.01").validation().isNotEmpty())
        val allocation=AssetDraft(greenhouseId=asset.greenhouseId,kind=AssetKind.ALLOCATION,assetId=id(),seasonId=id(),usageReference=id(),usageYear="1",periodStart="2024-02-29",periodEnd="2025-02-28",amount="33,33",basis="Pemakaian unik")
        assertTrue(allocation.validation().isEmpty());assertEquals("33.33",allocation.payload().getString("amount"))
        assertTrue(allocation.copy(periodEnd=allocation.periodStart).validation().isNotEmpty())
        assertTrue(allocation.copy(baseVersion=1).validation().isNotEmpty());assertTrue(allocation.copy(amount="0").validation().isNotEmpty())
    }
    @Test fun managerRoutesAreExplicitAndWorkerCannotSendFinancialRecords() {
        val uuid=id()
        assertEquals("assets",OperationRoute.forOperation("asset","create",uuid,UserRole.MANAGER).path)
        assertEquals("depreciation-allocations/$uuid",OperationRoute.forOperation("depreciation_allocation","update",uuid,UserRole.MANAGER).path)
        assertTrue(runCatching {OperationRoute.forOperation("asset","create",uuid,UserRole.WORKER)}.isFailure)
    }
}
