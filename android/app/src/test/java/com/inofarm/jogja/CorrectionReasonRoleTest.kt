package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.RecordKind
import com.inofarm.jogja.feature.expenses.FinancialDraft
import com.inofarm.jogja.feature.sales.SaleDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionReasonRoleTest {
    private val id = "11111111-1111-4111-8111-111111111111"

    private fun expense(reason: String) = FinancialDraft(
        kind = RecordKind.EXPENSE, greenhouseId = id, categoryId = id, paymentKind = "operating",
        amount = "1000", date = "2026-11-02", time = "08:00", reason = reason, baseVersion = 2,
    )

    @Test fun workerMustGiveACorrectionReasonButManagerMayLeaveItBlank() {
        assertTrue(expense("").validation(reasonRequired = true).containsKey("reason"))
        assertFalse(expense("").validation(reasonRequired = false).containsKey("reason"))
    }

    @Test fun blankReasonIsOmittedFromThePayloadAndFilledReasonIsSent() {
        assertFalse(expense("").payload().has("reason"))
        assertTrue(expense("Salah ketik").payload().getString("reason") == "Salah ketik")
    }

    @Test fun reasonLengthLimitAppliesToEveryRole() {
        assertTrue(expense("x".repeat(1001)).validation(reasonRequired = false).containsKey("reason"))
        assertTrue(SaleDraft(greenhouseId = id, reason = "x".repeat(1001), baseVersion = 2).validation(reasonRequired = false).containsKey("reason"))
    }
}
