package com.inofarm.jogja

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.design.ForestScheme
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.feature.admin.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID

class FinanceAdminUiTest {
    @get:Rule val rule=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-test-admin-ui-${UUID.randomUUID()}"
    private val epoch=UUID.randomUUID().toString()
    @Volatile private var refusal:FinanceApiFailure?=null
    private lateinit var helper:FinanceDatabase;private lateinit var vault:FinanceSessionVault;private lateinit var repository:AdminRepository;private lateinit var session:FinanceSession
    @Before fun setup() {
        assertTrue(context.packageName.endsWith(".finance") || context.packageName.endsWith(".finance.performance"))
        helper=FinanceDatabase(context,name+".db")
        val store=LocalFinanceStore(helper,FixtureCipher());vault=FinanceSessionVault(context,FixtureCipher(),name+"-session")
        session=FinanceSession(AccountScope("http://fixture.invalid/api/v2/",UUID.randomUUID().toString()),"synthetic-token",Instant.now().plusSeconds(3600).toString(),"Pengelola",UserRole.MANAGER)
        vault.save(vault.generation,session);store.initialize(session.scope.key,epoch,session.role)
        val api=object:FinanceApi {
            override fun validateBase(base:String)=base
            override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject = when {
                path=="sync/status" -> JSONObject().put("schema_version","2").put("sync_epoch",epoch)
                method=="GET" -> JSONObject("{\"data\":[],\"next_cursor\":null}")
                refusal!=null -> throw refusal!!
                else -> JSONObject().put("operation_id",body!!.getString("operation_id")).put("entity_id",body.getString("entity_id"))
            }
        }
        repository=AdminRepository(context,vault,store,api,FixtureCipher(),name+"-admin")
    }
    @After fun cleanup() {helper.close();context.deleteDatabase(name+".db");listOf("-session","-admin").forEach {context.getSharedPreferences(name+it,0).edit().clear().commit()}}
    @Test fun largeFontCancelAsksBeforeDroppingAChangedForm() {
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {MaterialTheme(colorScheme=ForestScheme) {androidx.compose.material3.Text("Admin fixture");AdminHost(session,repository,{},onClose={})}}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("admin-new").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-new").performScrollTo().performClick()
        rule.onNodeWithTag("admin-name").performScrollTo().performTextInput("GH batal sintetis")
        Espresso.closeSoftKeyboard()
        rule.onAllNodesWithText("Simpan draf dan kembali").assertCountEquals(0)
        captureWorkerRecordingEvidence("wr-admin-dark.png")
        // The form is kept on the device while editing, so closing the app does not lose it.
        rule.waitUntil(5000) {repository.read(session,vault.generation).optJSONObject("form")?.getJSONObject("values")?.optString("name")=="GH batal sintetis"}
        rule.onNodeWithTag("admin-cancel").scrollIfScrollable().performClick()
        rule.onNodeWithText("Buang perubahan?").assertIsDisplayed()
        rule.onNodeWithText("Lanjut mengisi").performClick()
        rule.onNodeWithTag("admin-name").assertTextContains("GH batal sintetis")
        rule.onNodeWithTag("admin-cancel").scrollIfScrollable().performClick()
        rule.onNodeWithTag("admin-cancel-confirm").performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("admin-new").fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodesWithTag("admin-resume").assertCountEquals(0)
        assertFalse(repository.read(session,vault.generation).has("form"))
    }
    @Test fun anUnfinishedFormNeverBlocksAddingAnotherRecord() {
        val stale=AdminForm(AdminResource.GREENHOUSE,values=mapOf("name" to "GH lama sintetis"))
        repository.write(session,vault.generation,JSONObject().put("revision",1).put("form",stale.json()).put("dirty",true))
        rule.setContent {MaterialTheme {androidx.compose.material3.Text("Admin fixture");AdminHost(session,repository,{},onClose={})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("admin-resume").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-resume").assertTextContains("Lanjutkan isian",substring=true)
        rule.onNodeWithTag("admin-new").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithText("Buang isian sebelumnya?").assertIsDisplayed()
        rule.onNodeWithTag("admin-replace-confirm").performClick()
        rule.onNodeWithTag("admin-name").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("GH lama sintetis",substring=true).assertCountEquals(0)
        rule.waitUntil(5000) {repository.read(session,vault.generation).optJSONObject("form")?.getJSONObject("values")?.has("name")==false}
    }
    @Test fun aFinalRefusalUnlocksTheFormInsteadOfLeavingOnlyRetry() {
        refusal=FinanceApiFailure(409,JSONObject().put("code","season_closed").put("message","Musim ditutup. Buka kembali dengan alasan sebelum mengubah kelompok."))
        var accepted=0
        rule.setContent {MaterialTheme {androidx.compose.material3.Text("Admin fixture");AdminHost(session,repository,{accepted++},onClose={})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("admin-new").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-new").performScrollTo().performClick()
        rule.onNodeWithTag("admin-name").performScrollTo().performTextInput("GH ditolak sintetis")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("admin-submit").scrollIfScrollable().performClick()
        rule.onNodeWithTag("admin-save").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Musim ditutup",substring=true).fetchSemanticsNodes().isNotEmpty()}
        assertFalse(repository.read(session,vault.generation).has("pending"))
        rule.onAllNodesWithText("Kirim ulang",substring=true).assertCountEquals(0)
        rule.onNodeWithTag("admin-name").performScrollTo().performTextReplacement("GH diperbaiki sintetis")
        Espresso.closeSoftKeyboard()
        refusal=null
        rule.onNodeWithTag("admin-submit").scrollIfScrollable().performClick()
        rule.onNodeWithTag("admin-save").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {accepted==1}
    }
    /** Saves the screen, dialog included, to the app's external files for a visual check of both steps. */
    private fun shot(name:String) {
        rule.waitForIdle();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun savingAnAdminChangeShowsTheSummaryThenSends() {
        var accepted=0
        rule.setContent {com.inofarm.jogja.core.design.InofarmTheme {androidx.compose.material3.Text("Admin fixture");AdminHost(session,repository,{accepted++},onClose={})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("admin-new").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-new").performScrollTo().performClick()
        rule.onNodeWithTag("admin-name").performScrollTo().performTextInput("GH diterima sintetis")
        Espresso.closeSoftKeyboard();shot("admin-form-step1")
        rule.onNodeWithTag("admin-submit").scrollIfScrollable().performClick()
        rule.onNodeWithText("Ringkasan greenhouse").assertExists();shot("admin-summary-step2")
        rule.onNodeWithTag("admin-save").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {accepted==1}
        rule.waitForIdle();assertFalse(repository.read(session,vault.generation).has("pending"))
    }
}
