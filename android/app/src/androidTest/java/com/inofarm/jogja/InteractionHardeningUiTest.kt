package com.inofarm.jogja

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.design.FarmMotion
import com.inofarm.jogja.core.design.FarmSpace
import com.inofarm.jogja.core.design.InofarmTheme
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.FarmFormBar
import com.inofarm.jogja.feature.auth.AuthUiState
import com.inofarm.jogja.feature.auth.FinanceLogin
import com.inofarm.jogja.feature.auth.LastLoginEmail
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.context.WorkerGreenhouseList
import com.inofarm.jogja.feature.context.WorkerHomeState
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Fase F2: simpan tanpa layar spinner penuh, email terakhir di login, dan placeholder Beranda pekerja. */
class InteractionHardeningUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun forget() = LastLoginEmail.forget(context)

    // Internal storage: readable through run-as on a debug build, without storage permissions or root.
    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun formBarReportsSavingInPlaceOnlyAfterTheRevealDelay() {
        var busy by mutableStateOf(false)
        var saved = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            InofarmTheme { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(horizontal = FarmSpace.Page)) {
                FarmFormBar("save", { saved++ }, "main", "Periksa", { saved++ }, enabled = !busy, busy = busy)
            } } }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("main").assertIsEnabled().assertTextContains("Periksa")

        busy = true
        rule.mainClock.advanceTimeBy(FarmMotion.BusyReveal - 100)
        // A quick save never flashes a status, but the controls are already held.
        rule.onNodeWithText("Menyimpan…").assertDoesNotExist()
        rule.onNodeWithTag("main").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("save").assertIsNotEnabled().performClick()
        assertEquals(0, saved)

        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("main").assertIsNotEnabled().assertTextContains("Menyimpan…")
        rule.onNodeWithTag("save").assertIsDisplayed()
        capture("f2-form-bar-saving.png")

        busy = false
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("main").assertIsEnabled().assertTextContains("Periksa")
        rule.onNodeWithText("Menyimpan…").assertDoesNotExist()
    }

    @Test fun loginOffersTheRememberedEmailAndBukanSayaRemovesIt() {
        LastLoginEmail.save(context, "  Pekerja@Example.Invalid ")
        rule.setContent { InofarmTheme { FinanceLogin(AuthUiState(loading = false), "https://example.invalid/api/v2/") { _, _, _ -> } } }
        rule.onNodeWithTag("finance-email").assertTextContains("pekerja@example.invalid")
        rule.onNodeWithTag("finance-login").assertIsNotEnabled()
        // Password managers find the two fields through these hints.
        rule.onNodeWithTag("finance-email").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentType))
        rule.onNodeWithTag("finance-password").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.Password))
        capture("f2-login-remembered-email.png")

        rule.onNodeWithTag("finance-forget-email").performScrollTo().assertHasClickAction().performClick()
        rule.onNodeWithTag("finance-forget-email").assertDoesNotExist()
        rule.onNodeWithTag("finance-email").assert(hasText("pekerja@example.invalid").not())
        assertEquals("", LastLoginEmail.read(context))

        // Typing another address never shows the action: nothing is remembered until a login is accepted.
        rule.onNodeWithTag("finance-email").performTextReplacement("lain@example.invalid")
        rule.onNodeWithTag("finance-forget-email").assertDoesNotExist()
        assertEquals("", LastLoginEmail.read(context))
    }

    @Test fun workerHomeShowsPlaceholdersOnlyWhenTheCacheIsSlow() {
        val data = CultivationUiState(check = ContextCheck.CHECKED,
            greenhouses = listOf(JSONObject().put("id", "gh-1").put("name", "Greenhouse sintetis").put("version", 1).put("active", true)))
        var state by mutableStateOf(WorkerHomeState(null, false))
        rule.mainClock.autoAdvance = false
        rule.setContent { InofarmTheme { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(FarmSpace.Page)) {
            WorkerGreenhouseList(state, data) {}
        } } } }
        rule.mainClock.advanceTimeBy(FarmMotion.PlaceholderReveal - 50)
        rule.onNodeWithText("Memuat data perangkat…").assertDoesNotExist()

        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Memuat data perangkat…").assertIsDisplayed()
        capture("f2-worker-home-placeholder.png")

        state = WorkerHomeState(emptyList(), false)
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Memuat data perangkat…").assertDoesNotExist()

        state = WorkerHomeState(null, true)
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Data perangkat belum bisa dibaca. Perbarui untuk mencoba lagi.").assertIsDisplayed()
    }
}
