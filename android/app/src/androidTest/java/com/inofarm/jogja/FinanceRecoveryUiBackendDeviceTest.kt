package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual MainActivity → manager finance hub → recovery history on the restored API. */
class FinanceRecoveryUiBackendDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun managerSeesVerifiedRestoreFromRoot() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("devicePhase")=="ui")
        val context=InstrumentationRegistry.getInstrumentation().targetContext;assertTrue(context.packageName.endsWith(".finance"))
        val f=JSONObject(File(context.getExternalFilesDir(null),"finance-recovery-fixture.json").readText());assertEquals("inofarm_test",f.getString("database"))
        rule.waitUntil(15000){rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty()}
        if(rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty())rule.logoutFromAccount()
        rule.waitUntil(15000){rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        rule.configureFinanceEndpoint(f.getString("base_url"))
        rule.onNodeWithTag("finance-email").performScrollTo().performTextReplacement(f.getString("manager_email"))
        rule.onNodeWithTag("finance-password").performScrollTo().performTextReplacement(f.getString("password"))
        rule.runOnIdle {(rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0)}
        rule.onNodeWithTag("finance-login").performScrollTo().performClick()
        rule.waitUntil(15000){rule.onAllNodesWithTag("manager-home-picker").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-4").performClick();rule.clickFinanceTool("recovery-open")
        rule.waitUntil(15000){rule.onAllNodesWithTag("recovery-reason").fetchSemanticsNodes().singleOrNull()?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)==false}
        val text="Uji pemulihan berhasil: "+f.getString("restored_run_id")
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex) and hasAnyAncestor(isDialog())).performScrollToNode(hasText(text))
        rule.onNodeWithText(text).assertIsDisplayed();rule.onNodeWithText("Tutup").performClick();rule.onNodeWithTag("finance-tools").assertExists()
    }
}
