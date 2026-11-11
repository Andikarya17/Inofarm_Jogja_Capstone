package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule

internal typealias FinanceTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

internal fun FinanceTestRule.configureFinanceEndpoint(endpoint: String) {
    runOnUiThread { ViewModelProvider(activity)[FinanceRuntime::class.java].configuredLoginEndpoint.value = endpoint }
}

internal fun FinanceTestRule.waitForFinanceMasterPull() {
    val runtime = ViewModelProvider(activity)[FinanceRuntime::class.java]
    waitUntil(15_000) {
        runtime.vault.current?.let { runtime.store.lastSyncedAt(it.scope.key) } != null
    }
}

internal fun FinanceTestRule.selectFinanceTab(index: Int) {
    waitUntil(10_000) { onAllNodesWithTag("finance-tab-$index").fetchSemanticsNodes().isNotEmpty() }
    onNodeWithTag("finance-tab-$index").performClick()
}

/** Worker Riwayat holds activity, measurement and harvest behind chips; this opens the harvest list. */
internal fun FinanceTestRule.openWorkerHarvestHistory() {
    selectFinanceTab(1)
    onNodeWithTag("history-chip-harvest").performClick()
}

/** The "N belum terkirim" button in the top bar opens the outbox sheet; it only exists while something is unsent. */
internal fun FinanceTestRule.openFinanceOutbox() {
    waitUntil(10_000) { onAllNodesWithTag("finance-unsent").fetchSemanticsNodes().isNotEmpty() }
    onNodeWithTag("finance-unsent").performClick()
    waitUntil(10_000) { onAllNodesWithTag("finance-queue-list").fetchSemanticsNodes().isNotEmpty() }
}

internal fun FinanceTestRule.logoutFromAccount() {
    val rejected = onAllNodesWithTag("manager-home-relogin")
    waitUntil(10_000) {
        onAllNodesWithTag("finance-account").fetchSemanticsNodes().size == 1 ||
            rejected.fetchSemanticsNodes().size == 1
    }
    if (rejected.fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag("manager-home-relogin").performScrollTo().performClick()
        return
    }
    onNodeWithTag("finance-account").performClick()
    onNodeWithTag("finance-logout").performClick()
}

internal fun FinanceTestRule.openHomeContext() {
    onNodeWithTag("finance-home-list").performScrollToNode(hasTestTag("finance-context-open"))
    onNodeWithTag("finance-context-open").performClick()
}

internal fun FinanceTestRule.prepareChoice(tag: String) {
    when (tag) {
        "finance-gh", "finance-season", "finance-group" -> {
            if (onAllNodesWithTag("finance-context-close").fetchSemanticsNodes().isEmpty()) openHomeContext()
        }
        "expense-gh", "expense-history-kind" ->
            onNodeWithTag("finance-history-list").performScrollToNode(hasTestTag(tag))
    }
}

internal fun FinanceTestRule.finishChoice(tag: String) {
    if (tag in setOf("finance-gh", "finance-season", "finance-group")) {
        onNodeWithTag("finance-context-close").performScrollTo().performClick()
    }
}

internal fun FinanceTestRule.openFinanceTools() {
    onNodeWithTag("finance-history-list").performScrollToNode(hasTestTag("finance-tools"))
    onNodeWithTag("finance-tools").performScrollTo().performClick()
}

internal fun FinanceTestRule.clickFinanceTool(tag: String) {
    openFinanceTools()
    onNodeWithTag(tag).performScrollTo().performClick()
}
