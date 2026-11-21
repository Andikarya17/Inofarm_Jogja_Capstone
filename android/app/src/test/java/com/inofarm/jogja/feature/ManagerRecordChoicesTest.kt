package com.inofarm.jogja.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerRecordChoicesTest {
    @Test fun expense_follows_the_eye_toggle() {
        assertFalse(RecordChoice.EXPENSE in managerRecordChoices(showRealCosts = false))
        assertTrue(RecordChoice.EXPENSE in managerRecordChoices(showRealCosts = true))
        assertTrue(RecordChoice.SALE in managerRecordChoices(showRealCosts = false))
    }
}
