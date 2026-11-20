package com.inofarm.jogja.feature.financehome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceVisibilityPreferenceTest {
    private class MemoryStorage : FlagStorage {
        val values = mutableMapOf<String, Boolean>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: Boolean) { values[key] = value }
    }

    @Test fun real_costs_hidden_by_default() {
        assertFalse(FinanceVisibilityPreference(MemoryStorage()).showRealCosts("server|akun-1"))
    }

    @Test fun preference_is_per_account_scope() {
        val preference = FinanceVisibilityPreference(MemoryStorage())
        preference.setShowRealCosts("server|akun-1", true)
        assertTrue(preference.showRealCosts("server|akun-1"))
        assertFalse(preference.showRealCosts("server|akun-2"))
    }
}
