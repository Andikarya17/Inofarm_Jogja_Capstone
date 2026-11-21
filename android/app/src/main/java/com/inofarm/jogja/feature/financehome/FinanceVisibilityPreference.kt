package com.inofarm.jogja.feature.financehome

import android.content.Context

interface FlagStorage {
    fun read(key: String): Boolean?
    fun write(key: String, value: Boolean)
}

/** Eye toggle on Keuangan: shows or hides the real-cost ledger (payments, allocation, assets). Hidden by default, per account. */
class FinanceVisibilityPreference(private val storage: FlagStorage) {
    fun showRealCosts(scopeKey: String): Boolean = storage.read("real_costs|$scopeKey") ?: false
    fun setShowRealCosts(scopeKey: String, show: Boolean) = storage.write("real_costs|$scopeKey", show)
}

class SharedPreferencesFlagStorage(context: Context) : FlagStorage {
    private val preferences = context.applicationContext.getSharedPreferences("inofarm_display", Context.MODE_PRIVATE)
    override fun read(key: String): Boolean? = if (preferences.contains(key)) preferences.getBoolean(key, false) else null
    override fun write(key: String, value: Boolean) { preferences.edit().putBoolean(key, value).apply() }
}
