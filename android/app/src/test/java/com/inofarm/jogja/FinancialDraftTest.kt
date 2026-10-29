package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.RecordKind
import com.inofarm.jogja.feature.expenses.FinancialDraft
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FinancialDraftTest {
    private val gh=UUID.randomUUID().toString();private val category=UUID.randomUUID().toString();private val expense=UUID.randomUUID().toString();private val season=UUID.randomUUID().toString()
    private fun payment()=FinancialDraft(kind=RecordKind.EXPENSE,greenhouseId=gh,amount="100,01",categoryId=category,paymentKind="operating",date="2026-10-30",time="00:12:34",notes="Pembayaran sintetis")
    private fun allocation()=FinancialDraft(kind=RecordKind.COST_ALLOCATION,greenhouseId=gh,amount="60,01",expenseId=expense,seasonId=season,costClass="VC",basis="Nominal eksplisit sintetis")
    @Test fun rawIncompleteMoneyAndDateSurviveWithoutBecomingZeroOrReady() {
        for(invalid in listOf(payment().copy(amount="1,2,3",date="belum"),allocation().copy(amount="",basis=""))) {
            val restored=FinancialDraft.from(invalid.id,invalid.kind,gh,invalid.localJson(),null)
            assertEquals(invalid,restored);assertTrue(restored.validation().isNotEmpty())
            assertThrows(IllegalArgumentException::class.java) {restored.payload()}
        }
    }
    @Test fun localeMoneyUsesExactFixedTwoAndPayloadContainsOnlyItsDomainFields() {
        val payment=payment().payload();assertEquals("100.01",payment.get("amount"));assertEquals("2026-10-30T00:12:34+07:00",payment.getString("paid_at"))
        assertFalse(payment.has("expense_id"));assertFalse(payment.has("recorded_by"));assertFalse(payment.has("_local_form"))
        val allocation=allocation().payload();assertEquals("60.01",allocation.get("amount"));assertFalse(allocation.has("paid_at"));assertFalse(allocation.has("category_id"))
        assertEquals("0.00",payment().copy(amount="0").payload().getString("amount"))
        assertEquals("999999999999999999.99",payment().copy(amount="999999999999999999,99").payload().getString("amount"))
    }
    @Test fun negativeExponentExtraScaleOverflowAndUnchosenBusinessFieldsAreRejected() {
        for(invalid in listOf("", "-0", "-1", "1e2", "NaN", "01", "1.001", "1000000000000000000", "1.000,50")) assertTrue(payment().copy(amount=invalid).validation().containsKey("amount"))
        assertTrue(payment().copy(paymentKind="").validation().containsKey("payment_kind"))
        assertTrue(allocation().copy(costClass="",basis="  ").validation().keys.containsAll(listOf("cost_class","basis")))
    }
    @Test fun acceptedRevisionPreservesWibSecondsAndRequiresReasonWithoutAgronomyPolicy() {
        for(original in listOf(payment(),allocation())) {
            val accepted=original.payload();if(original.kind==RecordKind.EXPENSE) accepted.put("paid_at","2026-10-29T17:12:34.000000Z")
            val restored=FinancialDraft.from(original.id,original.kind,gh,accepted,2)
            assertTrue(restored.lockedParents);assertTrue(restored.validation().containsKey("reason"))
            if(restored.kind==RecordKind.EXPENSE) {assertEquals("2026-10-30",restored.date);assertEquals("00:12:34",restored.time)}
            val corrected=restored.copy(reason="Koreksi eksplisit")
            assertEquals(corrected,FinancialDraft.from(corrected.id,corrected.kind,gh,corrected.localJson(),2))
            assertFalse(corrected.payload().has("reference_policy"));assertEquals("Koreksi eksplisit",corrected.payload().getString("reason"))
        }
    }
}
