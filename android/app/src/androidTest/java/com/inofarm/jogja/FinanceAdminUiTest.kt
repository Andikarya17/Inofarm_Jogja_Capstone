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
                else -> JSONObject().put("operation_id",body!!.getString("operation_id")).put("entity_id",body.getString("entity_id"))
            }
        }
        repository=AdminRepository(context,vault,store,api,FixtureCipher(),name+"-admin")
    }
    @After fun cleanup() {helper.close();context.deleteDatabase(name+".db");listOf("-session","-admin").forEach {context.getSharedPreferences(name+it,0).edit().clear().commit()}}
    @Test fun largeFontFormSavesBeforeBackAndResumesWithoutSending() {
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {MaterialTheme(colorScheme=ForestScheme) {androidx.compose.material3.Text("Admin fixture");AdminHost(session,repository,{},onClose={})}}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("admin-new").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-new").performScrollTo().performClick()
        rule.onNodeWithTag("admin-name").performScrollTo().performTextInput("GH draf sintetis")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Simpan draf dan kembali").scrollIfScrollable().assertIsDisplayed()
        captureWorkerRecordingEvidence("wr-admin-dark.png")
        rule.onNodeWithText("Simpan draf dan kembali").scrollIfScrollable().performClick()
        rule.onNodeWithTag("admin-resume").performScrollTo().performClick()
        rule.onNodeWithTag("admin-name").assertTextContains("GH draf sintetis")
        val saved=repository.read(session,vault.generation)
        assertEquals("GH draf sintetis",saved.getJSONObject("form").getJSONObject("values").getString("name"));assertFalse(saved.has("pending"))
    }
    @Test fun savingAnAdminChangeSendsItWithoutAConfirmationDialog() {
        var accepted=0
        rule.setContent {MaterialTheme {androidx.compose.material3.Text("Admin fixture");AdminHost(session,repository,{accepted++},onClose={})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("admin-new").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-new").performScrollTo().performClick()
        rule.onNodeWithTag("admin-name").performScrollTo().performTextInput("GH diterima sintetis")
        rule.onNodeWithTag("admin-submit").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {accepted==1}
        rule.waitForIdle();assertFalse(repository.read(session,vault.generation).has("pending"))
    }
}
