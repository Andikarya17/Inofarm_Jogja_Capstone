package com.inofarm.jogja.feature.expenses

import com.inofarm.jogja.core.contracts.RecordKind
import com.inofarm.jogja.feature.assets.AssetDraft
import com.inofarm.jogja.feature.assets.AssetKind
import com.inofarm.jogja.core.contracts.DepreciationAmounts
import com.inofarm.jogja.feature.context.CultivationUiState

/**
 * The server's balance rules, checked in the form against the last cached balances (QA 28 Nov 2026: an allocation
 * above the payment's remainder, an asset above its payment and depreciation above the year's budget were only
 * refused after the Ringkasan or by the server). The server still decides; these only stop a known refusal early.
 */
object FinanceLimits {
    fun allocation(draft: FinancialDraft, context: CultivationUiState): Map<String, String> {
        if (draft.kind != RecordKind.COST_ALLOCATION || draft.baseVersion != null || draft.expenseId.isBlank()) return emptyMap()
        val amount = runCatching { FinancialDraft.money(draft.amount) }.getOrNull() ?: return emptyMap()
        val expense = context.expenses.find { it.optString("id") == draft.expenseId } ?: return emptyMap()
        val balance = context.expenseBalances.find { it.optString("expense_id") == draft.expenseId && it.optLong("expense_version") == expense.optLong("version") }
            ?: return emptyMap()
        val available = balance.optString("available_amount").toBigDecimalOrNull() ?: return emptyMap()
        return if (amount > available) mapOf("amount" to "Nominal melebihi sisa pembayaran yang bisa dialokasikan (${moneyLabel(available.toPlainString())}).") else emptyMap()
    }

    fun asset(draft: AssetDraft, context: CultivationUiState): Map<String, String> = runCatching {
        if (draft.kind == AssetKind.ASSET) {
            val payment = context.expenses.find { it.optString("id") == draft.expenseId } ?: return emptyMap()
            val cost = AssetDraft.money(draft.cost); val paid = AssetDraft.money(payment.getString("amount"))
            if (cost > paid) mapOf("cost" to "Harga aset melebihi pembayaran sumber (${moneyLabel(paid.toPlainString())}).") else emptyMap()
        } else {
            val asset = context.assets.find { it.optString("id") == draft.assetId } ?: return emptyMap()
            val budget = DepreciationAmounts.year(asset.getString("cost"), asset.getString("residual"), asset.getInt("life_years"),
                AssetDraft.year(draft.usageYear), AssetDraft.date(asset.getString("in_service_date")))
            val amount = AssetDraft.money(draft.amount)
            if (amount > budget.amount) mapOf("amount" to "Nominal melebihi anggaran penyusutan tahun ini (${moneyLabel(budget.amount.toPlainString())}).") else emptyMap()
        }
    }.getOrDefault(emptyMap())

}
