package com.inofarm.jogja

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.design.ForestScheme
import com.inofarm.jogja.core.sync.ContextCheck
import com.inofarm.jogja.feature.context.CultivationUiState
import com.inofarm.jogja.feature.context.HomeContext
import com.inofarm.jogja.feature.context.WorkerContextSheet
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class WorkerContextUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun checkedEmptyAndOfflineAreDistinctAndRecordingKeepsItsDestination() {
        val data = mutableStateOf(CultivationUiState(loading = true))
        val selected = mutableStateOf(HomeContext())
        var continued = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                MaterialTheme(colorScheme = ForestScheme) {
                    Surface(Modifier.fillMaxSize()) { Text("Pencatatan") }
                    WorkerContextSheet(data.value, selected.value, "Catat panen", { selected.value = it },
                        onClose = {}, onContinue = { continued++ }, onSync = {})
                }
            }
        }
        rule.onNodeWithText("Memeriksa greenhouse dan musim Anda", substring = true).assertExists()
        rule.runOnIdle { data.value = CultivationUiState(check = ContextCheck.OFFLINE) }
        rule.onNodeWithText("Belum bisa terhubung dan belum ada greenhouse tersimpan", substring = true).assertExists()
        rule.onNodeWithText("Anda belum ditugaskan di greenhouse mana pun", substring = true).assertDoesNotExist()
        rule.runOnIdle { data.value = CultivationUiState(check = ContextCheck.FAILED) }
        rule.onNodeWithText("Greenhouse gagal dimuat", substring = true).assertExists()
        rule.runOnIdle { data.value = CultivationUiState(check = ContextCheck.CHECKED) }
        rule.onNodeWithText("Anda belum ditugaskan di greenhouse mana pun", substring = true).assertExists()
        rule.onNodeWithTag("finance-context-continue").assertDoesNotExist()

        rule.runOnIdle {
            data.value = CultivationUiState(
                check = ContextCheck.CHECKED,
                greenhouses = listOf(row("gh", "Greenhouse sintetis")),
                seasons = listOf(row("season", "Musim sintetis").put("greenhouse_id", "gh").put("status", "open")),
                groups = listOf(row("group", "Kelompok sintetis").put("season_id", "season"))
            )
        }
        rule.onNodeWithTag("finance-gh").performScrollTo().performClick()
        rule.onNodeWithText("Greenhouse sintetis").performClick()
        rule.onNodeWithTag("finance-season").performScrollTo().performClick()
        rule.onNodeWithText("Musim sintetis").performClick()
        rule.onNodeWithTag("finance-group").performScrollTo().performClick()
        rule.onNodeWithText("Kelompok sintetis").performClick()
        rule.onNodeWithTag("finance-context-continue").performScrollTo().assertIsDisplayed()
        captureWorkerRecordingEvidence("wr-worker-dark.png")
        rule.onNodeWithTag("finance-context-continue").performClick()
        rule.runOnIdle {
            assertEquals(HomeContext("gh", "season", "group"), selected.value)
            assertEquals(1, continued)
        }
    }

    private fun row(id: String, name: String) = JSONObject().put("id", id).put("name", name).put("active", true)
}

internal fun captureWorkerRecordingEvidence(name: String) {
    val instrument = InstrumentationRegistry.getInstrumentation()
    val automation = instrument.uiAutomation
    automation.waitForIdle(500, 5000)
    val bitmap = checkNotNull(automation.takeScreenshot())
    File(instrument.targetContext.getExternalFilesDir(null), name).outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
}
