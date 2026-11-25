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
        rule.onNodeWithTag("admin-new").scrollIfScrollable().performClick()
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
        rule.onNodeWithTag("admin-new").scrollIfScrollable().assertIsEnabled().performClick()
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
        rule.onNodeWithTag("admin-new").scrollIfScrollable().performClick()
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
        rule.onNodeWithTag("admin-new").scrollIfScrollable().performClick()
        rule.onNodeWithTag("admin-name").performScrollTo().performTextInput("GH diterima sintetis")
        Espresso.closeSoftKeyboard();shot("admin-form-step1")
        rule.onNodeWithTag("admin-submit").scrollIfScrollable().performClick()
        rule.onNodeWithText("Ringkasan greenhouse").assertExists();shot("admin-summary-step2")
        rule.onNodeWithTag("admin-save").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {accepted==1}
        rule.waitForIdle();assertFalse(repository.read(session,vault.generation).has("pending"))
    }

    /**
     * A small stateful server for the Kelola pages: two greenhouses, one with a running period, and two accounts.
     * Like the real `GET greenhouses`, rows carry only id, name, active and version; the running period comes from manager-home.
     */
    private inner class KelolaServer {
        val me=session.scope.accountId
        val sent=java.util.Collections.synchronizedList(mutableListOf<Triple<String,String,JSONObject>>())
        @Volatile var running:String?="grp1";@Volatile var ghVersion=3L
        private fun page(vararg rows:JSONObject)=JSONObject().put("data",org.json.JSONArray(rows.toList())).put("next_cursor",JSONObject.NULL)
        private fun greenhouse(id:String)=if(id=="G1") JSONObject().put("id","G1").put("name","GH Satu").put("active",true).put("version",ghVersion)
             else JSONObject().put("id","G2").put("name","GH Dua").put("active",true).put("version",1)
        private fun period(id:String)=if(id=="G1" && running!=null) JSONObject().put("state","ready").put("group_id","grp1").put("group_name","Kelompok A")
            .put("variety","Yellow hami").put("season_name","Musim 1").put("sowing_date","2026-11-01") else JSONObject().put("state","no_active_period").put("group_id",JSONObject.NULL)
        val api=object:FinanceApi {
            override fun validateBase(base:String)=base
            override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject {
                val gh=query["greenhouse_id"]
                if(path=="sync/status") return JSONObject().put("schema_version","2").put("sync_epoch",epoch)
                if(method!="GET") {
                    sent+=Triple(method,path,body!!)
                    if(path=="greenhouses/G1/running-period") {running=null;ghVersion++}
                    return JSONObject().put("operation_id",body.getString("operation_id")).put("entity_id",body.getString("entity_id"))
                }
                return when(path) {
                    "greenhouses" -> if(gh!=null) page(greenhouse(gh)) else page(greenhouse("G1"),greenhouse("G2"))
                    "manager-home" -> JSONObject().put("greenhouse_id",gh ?: JSONObject.NULL).put("greenhouses",org.json.JSONArray(
                        (if(gh!=null) listOf(gh) else listOf("G1","G2")).map {JSONObject().put("id",it).put("period",period(it))}))
                    "seasons" -> if(gh=="G1") page(JSONObject().put("id","S1").put("name","Musim 1").put("status","open").put("start_date","2026-11-01").put("version",1)) else page()
                    "planting-groups" -> if(gh=="G1") page(JSONObject().put("id","grp1").put("name","Kelompok A").put("season_id","S1").put("active",true).put("version",1)) else page()
                    "materials" -> if(gh=="G1") page(JSONObject().put("id","M1").put("greenhouse_id","G1").put("name","Daconil").put("dimension","mass").put("purpose","spray").put("active",true).put("version",2),
                        JSONObject().put("id","M2").put("greenhouse_id","G1").put("name","Lama").put("dimension","mass").put("active",false).put("version",1)) else page()
                    "units" -> if(gh=="G1") page(JSONObject().put("id","U1").put("name","gram").put("dimension","mass").put("active",true).put("version",1)) else page()
                    "grades" -> if(gh=="G1") page(JSONObject().put("id","R1").put("name","Layak").put("active",true).put("version",1)) else page()
                    "assignments" -> page(JSONObject().put("id","A1").put("greenhouse_id","G1").put("user_name","Pekerja Satu").put("active",true).put("effective_until",JSONObject.NULL).put("version",1))
                    "users" -> page(JSONObject().put("id",me).put("name","Pengelola").put("email","pengelola@example.invalid").put("role","manager").put("active",true).put("version",1).put("greenhouse_id","1d2c7a52-0000-4000-8000-000000000001"),
                        JSONObject().put("id","W1").put("name","Pekerja Satu").put("email","pekerja@example.invalid").put("role","worker").put("active",true).put("version",5).put("greenhouse_id","1d2c7a52-0000-4000-8000-000000000001"))
                    else -> page()
                }
            }
        }
        fun repository()=AdminRepository(context,vault,LocalFinanceStore(helper,FixtureCipher()),api,FixtureCipher(),name+"-admin")
    }
    @Test fun greenhouseSettingsShowCountsAndASheetChangesOneRowThroughItsSummary() {
        val server=KelolaServer()
        rule.setContent {com.inofarm.jogja.core.design.InofarmTheme {AdminHost(session,server.repository(),{},AdminResource.GREENHOUSE,onClose={},initialGreenhouse="G1")}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("gh-materials").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Yellow hami").assertExists()
        rule.onNodeWithText("1 musim berjalan").assertExists()
        rule.onNodeWithText("Belum diatur").assertExists();shot("kelola-gh-settings")
        rule.onNodeWithTag("gh-settings").performScrollToNode(hasTestTag("gh-materials"))
        rule.onNodeWithTag("gh-materials").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("admin-row-M1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-filter-on").assertTextContains("Aktif · 1");rule.onNodeWithTag("admin-filter-off").assertTextContains("Nonaktif · 1")
        rule.onAllNodesWithTag("admin-row-M2").assertCountEquals(0);shot("kelola-master-list")
        rule.onNodeWithTag("admin-row-M1").performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("admin-active").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-active").performScrollTo().performClick();shot("kelola-sheet-step1")
        rule.onNodeWithTag("admin-submit").performScrollTo().performClick()
        rule.onNodeWithText("Ringkasan bahan").assertExists();rule.onNodeWithText("Tampil di form").assertExists();shot("kelola-sheet-summary")
        rule.onNodeWithTag("admin-save").performScrollTo().performClick()
        rule.waitUntil(10000) {server.sent.any {it.second=="materials/M1"}}
        val (method,_,body)=server.sent.single {it.second=="materials/M1"}
        assertEquals("PATCH",method);assertFalse(body.getJSONObject("payload").getBoolean("active"));assertEquals(2,body.getInt("base_version"))
    }
    @Test fun turningAGreenhouseOffWaitsForTheRunningPeriodAndUsesTheFreshVersion() {
        val server=KelolaServer()
        rule.setContent {com.inofarm.jogja.core.design.InofarmTheme {AdminHost(session,server.repository(),{},AdminResource.GREENHOUSE,onClose={},initialGreenhouse="G1")}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("gh-materials").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("gh-settings").performScrollToNode(hasTestTag("admin-toggle-active"))
        rule.onNodeWithTag("admin-toggle-active").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("deactivate-clear-period").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-toggle-confirm").assertIsNotEnabled();shot("kelola-deactivate-blocked")
        rule.onNodeWithTag("deactivate-clear-period").performClick()
        rule.waitUntil(10000) {server.sent.any {it.second=="greenhouses/G1/running-period"}}
        val clear=server.sent.single {it.second=="greenhouses/G1/running-period"}.third
        assertTrue(clear.getJSONObject("payload").isNull("planting_group_id"));assertEquals(3,clear.getInt("base_version"))
        rule.waitUntil(10000) {runCatching {rule.onNodeWithTag("admin-toggle-confirm").assertIsEnabled()}.isSuccess};shot("kelola-deactivate-ready")
        rule.onNodeWithTag("admin-toggle-confirm").performClick()
        rule.waitUntil(10000) {server.sent.any {it.second=="greenhouses/G1"}}
        val off=server.sent.single {it.second=="greenhouses/G1"}.third
        assertEquals(4,off.getInt("base_version"));assertFalse(off.getJSONObject("payload").getBoolean("active"))
    }
    @Test fun accountsMarkTheManagerAndKeepRoleChangesOffTheirOwnAccount() {
        val server=KelolaServer()
        rule.setContent {com.inofarm.jogja.core.design.InofarmTheme {AdminHost(session,server.repository(),{},AdminResource.USER,onClose={})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("person-W1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText(" (Anda)").assertExists()
        rule.onNodeWithTag("people-filter-worker").assertTextContains("Pekerja · 1");shot("kelola-people")
        rule.onNodeWithTag("person-${server.me}").performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("account-edit").fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodesWithTag("account-role").assertCountEquals(0);rule.onAllNodesWithTag("account-deactivate").assertCountEquals(0)
        rule.onNodeWithTag("kelola-back").performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("person-W1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("person-W1").performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("account-role").fetchSemanticsNodes().isNotEmpty()};shot("kelola-account")
        rule.onNodeWithTag("account-page").performScrollToNode(hasTestTag("account-role"))
        rule.onNodeWithTag("account-role").performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithText("Ringkasan akun").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("admin-save").assertTextContains("Ubah peran").performScrollTo().performClick()
        rule.waitUntil(10000) {server.sent.any {it.second=="users/W1"}}
        assertEquals("manager",server.sent.single {it.second=="users/W1"}.third.getJSONObject("payload").getString("role"))
    }
    @Test fun kelolaHomeListsGreenhousesWithStatusAndTheAccountCounts() {
        val server=KelolaServer();var opened:String?=null;var planted:String?=null
        rule.setContent {com.inofarm.jogja.core.design.InofarmTheme {KelolaHome(session,server.repository(),0,true,{opened=it},{},{planted=it},{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("manage-gh-G1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Yellow hami · 1 pekerja").assertExists()
        rule.onNodeWithText("Target belum ada").assertExists()
        rule.onNodeWithText("1 pengelola · 1 pekerja").assertExists();shot("kelola-home")
        rule.onNodeWithTag("manage-start-G2").performClick();assertEquals("G2",planted)
        rule.onNodeWithTag("manage-gh-G1").performClick();assertEquals("G1",opened)
    }

    /** Consumer prices and varieties for the "Harga perorangan" page; writes are recorded and answered like the server does. */
    private inner class PriceServer {
        val sent=java.util.Collections.synchronizedList(mutableListOf<Triple<String,String,JSONObject>>())
        private val priceId="30000000-0000-4000-8000-000000000001"
        private fun variety(name:String,gh:String,active:Boolean=true)=JSONObject().put("id",UUID.randomUUID().toString()).put("name",name).put("greenhouse_id",gh).put("active",active)
        val api=object:FinanceApi {
            override fun validateBase(base:String)=base
            override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject {
                if(path=="sync/status") return JSONObject().put("schema_version","2").put("sync_epoch",epoch)
                if(method!="GET") {
                    sent+=Triple(method,path,body!!)
                    val payload=body.getJSONObject("payload")
                    return JSONObject().put("data",JSONObject().put("id",body.getString("entity_id")).put("variety_name",payload.getString("variety_name")).put("quality",payload.getString("quality"))
                        .put("price_per_kg",payload.getString("price_per_kg")+"0000").put("version",body.optInt("base_version",0)+1).put("record_state","active").put("updated_at","2026-11-25T03:00:00.000000Z"))
                }
                return when(path) {
                    "varieties" -> JSONObject().put("data",org.json.JSONArray(listOf(variety("Golden Melon","G1"),variety("golden melon ","G2"),variety("Inthanon","G2"),variety("Lama","G2",false)))).put("next_cursor",JSONObject.NULL)
                    "consumer-prices" -> JSONObject().put("data",org.json.JSONArray(listOf(JSONObject().put("id",priceId).put("variety_name","Golden Melon").put("quality","saleable")
                        .put("price_per_kg","34000.000000").put("version",2).put("record_state","active").put("updated_at","2026-11-20T03:00:00.000000Z"))))
                    else -> JSONObject().put("data",org.json.JSONArray())
                }
            }
        }
        fun repository()=ConsumerPriceRepository(vault,api)
    }
    @Test fun consumerPriceListAndEdit() {
        val server=PriceServer()
        rule.setContent {com.inofarm.jogja.core.design.InofarmTheme {KelolaHome(session,repository,0,true,{},{},{},{},{},consumerPrices=server.repository())}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("kelola-consumer-prices").fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(10000) {rule.onAllNodesWithText("1 harga").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("kelola-consumer-prices").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("consumer-price-saleable-golden-melon").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Harga per kg untuk pembeli akhir",substring=true).assertExists()
        rule.onNodeWithText("Rp34.000/kg").assertExists();rule.onNodeWithText("Diubah 20 Nov 2026").assertExists()
        // The two spellings of Golden Melon are one row, and the deactivated variety has none.
        rule.onAllNodesWithTag("consumer-price-defective-golden-melon").assertCountEquals(1);rule.onAllNodesWithText("Lama").assertCountEquals(0)
        rule.onAllNodesWithText("Belum diatur, diketik saat menjual").assertCountEquals(3);shot("kelola-consumer-prices")
        rule.onNodeWithTag("consumer-price-saleable-golden-melon").performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("consumer-price-input").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("consumer-price-input").performTextReplacement("36000")
        Espresso.closeSoftKeyboard();shot("kelola-consumer-price-step1")
        rule.onNodeWithTag("consumer-price-submit").performScrollTo().performClick()
        rule.onNodeWithText("Ringkasan harga").assertExists();shot("kelola-consumer-price-summary")
        rule.onNodeWithTag("consumer-price-save").performScrollTo().performClick()
        rule.waitUntil(10000) {server.sent.any {it.second.startsWith("consumer-prices/")}}
        val (method,_,body)=server.sent.single()
        assertEquals("PATCH",method);assertEquals(2,body.getInt("base_version"));assertEquals("update",body.getString("action"))
        val payload=body.getJSONObject("payload")
        assertEquals("active",payload.getString("record_state"));assertEquals("36000.00",payload.getString("price_per_kg"));assertEquals("saleable",payload.getString("quality"))
        rule.waitUntil(10000) {rule.onAllNodesWithText("Rp36.000/kg").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun customerListFiltersRetail() {
        val queries=java.util.Collections.synchronizedList(mutableListOf<String>())
        val api=object:FinanceApi {
            override fun validateBase(base:String)=base
            override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject {
                val type=query["type"].orEmpty();queries+=type
                val rows=if(type=="retail") listOf(JSONObject().put("id","C1").put("type","retail").put("name","Toko Sayur Segar").put("whatsapp_display",JSONObject.NULL).put("purchase_count",2).put("total_kg","12.500000").put("source","manual")) else emptyList()
                return JSONObject().put("data",org.json.JSONArray(rows))
            }
        }
        rule.setContent {com.inofarm.jogja.core.design.InofarmTheme {com.inofarm.jogja.feature.customers.CustomerListScreen(session,vault.generation,com.inofarm.jogja.feature.customers.CustomerRepository(vault,api)) {}}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("customers-type-retail").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("customers-type-retail").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Toko Sayur Segar").fetchSemanticsNodes().isNotEmpty()}
        assertTrue("retail" in queries);rule.onNodeWithText("2 kali").assertExists();shot("customers-retail")
    }
}
