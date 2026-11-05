package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule

internal typealias FinanceTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

internal fun FinanceTestRule.configureFinanceEndpoint(endpoint: String) {
    runOnUiThread { ViewModelProvider(activity)[FinanceRuntime::class.java].configuredLoginEndpoint.value = endpoint }
}

internal fun FinanceTestRule.logoutFromAccount() {
    waitUntil(10_000) { onAllNodesWithTag("finance-account").fetchSemanticsNodes().size == 1 }
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
