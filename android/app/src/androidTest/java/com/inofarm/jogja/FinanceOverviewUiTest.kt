package com.inofarm.jogja

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.feature.customers.CustomerRepository
import com.inofarm.jogja.feature.financehome.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.UUID

/**
 * Keuangan per Musim ke-n with a fixture server for reports/season-overview (amounts from the artboard, test only).
 * Shots land in the app's external files dir for the visual check against KeuanganMusim and PenjualanMusim.
 */
class FinanceOverviewUiTest {
    @get:Rule val rule=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-test-overview-${UUID.randomUUID()}"
    private lateinit var vault:FinanceSessionVault;private lateinit var session:FinanceSession
    private val requested=java.util.Collections.synchronizedList(mutableListOf<String?>())
    private val flags=mutableMapOf<String,Boolean>()
    private val preference=FinanceVisibilityPreference(object:FlagStorage {
        override fun read(key:String)=flags[key]
        override fun write(key:String,value:Boolean) {flags[key]=value}
    })

    @Before fun setup() {
        vault=FinanceSessionVault(context,FixtureCipher(),name+"-session")
        session=FinanceSession(AccountScope("http://fixture.invalid/api/v2/",UUID.randomUUID().toString()),"synthetic-token",Instant.now().plusSeconds(3600).toString(),"Pengelola",UserRole.MANAGER)
        vault.save(vault.generation,session)
    }
    @After fun cleanup() {context.getSharedPreferences(name+"-session",0).edit().clear().commit()}

    private fun slot(amount:String,kg:String="0.000000",count:Int=0)=JSONObject().put("amount",amount).put("kg",kg).put("count",count)
    private fun types(consumer:String,retail:String,partner:String,unspecified:String="0.00")=JSONObject().put("consumer",slot(consumer)).put("retail",slot(retail))
        .put("partner",slot(partner)).put("unspecified",slot(unspecified))
    private fun money(value:String?):Any=value ?: JSONObject.NULL
    private fun line(name:String,costClass:String,amount:String?)=JSONObject().put("name",name).put("cost_class",costClass).put("amount",money(amount)).put("is_required",true)
    private val ghs=listOf("10000000-0000-4000-8000-00000000000a" to "GH A","10000000-0000-4000-8000-00000000000b" to "GH B",
        "10000000-0000-4000-8000-00000000000c" to "GH C","10000000-0000-4000-8000-00000000000d" to "GH D")
    private fun sequences()=JSONArray(listOf(
        JSONObject().put("sequence",3).put("status","running").put("greenhouse_count",4).put("fc","30324400.00").put("vc","13720000.00").put("tc","44044400.00").put("sales_amount","41200000.00").put("sales_kg","1573.000000"),
        JSONObject().put("sequence",2).put("status","closed").put("greenhouse_count",4).put("fc",JSONObject.NULL).put("vc","13000000.00").put("tc",JSONObject.NULL).put("sales_amount","50400000.00").put("sales_kg","1900.000000"),
        JSONObject().put("sequence",1).put("status","closed").put("greenhouse_count",4).put("fc","28800000.00").put("vc","12200000.00").put("tc","41000000.00").put("sales_amount","50400000.00").put("sales_kg","1880.000000")))

    private fun selected(sequence:Int):JSONObject {
        val blank=sequence==2
        val lines=listOf(line("Biaya penyusutan","FC",if(blank) null else "5806100.00"),line("Sewa tanah","FC","275000.00"),line("Tenaga kerja","FC",if(blank) null else "1500000.00"),
            line("Cocopeat / rockwool","VC","180000.00"),line("Nutrisi AB Mix","VC","1600000.00"),line("Nutrisi tambahan dan pestisida","VC","350000.00"),line("Benih","VC","1300000.00"))
        val perGh=if(blank) JSONObject().put("fc",JSONObject.NULL).put("vc","3430000.00").put("tc",JSONObject.NULL) else JSONObject().put("fc","7581100.00").put("vc","3430000.00").put("tc","11011100.00")
        val farm=if(blank) JSONObject().put("fc",JSONObject.NULL).put("vc","13720000.00").put("tc",JSONObject.NULL) else JSONObject().put("fc","30324400.00").put("vc","13720000.00").put("tc","44044400.00")
        val weeks=listOf(Triple("2026-10-26","26 Okt",listOf("600000.00","400000.00","2400000.00")),Triple("2026-11-02","2 Nov",listOf("1400000.00","800000.00","7100000.00")),
            Triple("2026-11-09","9 Nov",listOf("1900000.00","1000000.00","10300000.00")),Triple("2026-11-16","16 Nov",listOf("1500000.00","1000000.00","8800000.00")),
            Triple("2026-11-23","23 Nov",listOf("600000.00","300000.00","3100000.00"))).map {(key,label,v) ->
            JSONObject().put("key",key).put("label",label).put("start",key).put("amount","0.00").put("kg","0.000000").put("by_type",types(v[0],v[1],v[2]))}
        val range=if(sequence==3) JSONObject().put("from","2026-09-08").put("until","2026-11-25").put("running",true)
            else if(sequence==1) JSONObject().put("from","2026-03-03").put("until","2026-05-28").put("running",false)
            else JSONObject().put("from","2026-06-08").put("until","2026-08-30").put("running",false)
        return JSONObject().put("sequence",sequence).put("date_range",range)
            .put("cost_sheet",JSONObject().put("id","c0000000-0000-4000-8000-00000000000$sequence").put("version",1).put("lines",JSONArray(lines)).put("per_greenhouse",perGh).put("farm",farm))
            .put("sales",slot("41200000.00","1573.000000",47).put("by_type",types("9500000.00","3700000.00","28000000.00")))
            .put("by_greenhouse",JSONArray(ghs.mapIndexed {i,(id,gh) -> JSONObject().put("greenhouse_id",id).put("name",gh).put("amount",listOf("11124000.00","9888000.00","10712000.00","9476000.00")[i]).put("kg","393.250000").put("count",12)}))
            .put("weekly",JSONArray(if(sequence==2) emptyList() else weeks))
    }

    private val api=object:FinanceApi {
        override fun validateBase(base:String)=base
        override suspend fun request(base:String,token:String?,path:String,method:String,body:JSONObject?,query:Map<String,String>):JSONObject {
            if(path=="reports/season-overview") {
                requested+=query["sequence"]
                val sequence=query["sequence"]?.toInt() ?: 3
                return JSONObject().put("data",JSONObject().put("sequences",sequences()).put("selected",selected(sequence)))
            }
            return JSONObject().put("data",JSONArray())
        }
    }

    private fun sale(id:String,paidAt:String,type:String?,buyer:String,gh:Int,variety:String,kg:String,amount:String)=JSONObject().put("id",id).put("paid_at",paidAt)
        .put("customer_type",type ?: JSONObject.NULL).put("customer_name",buyer).put("greenhouse_id",ghs[gh].first).put("total_amount",amount).put("total_weight_kg",kg)
        .put("record_state","active").put("payment_status","paid").put("lines",JSONArray(listOf(JSONObject().put("variety_name",variety).put("quantity_kg",kg))))
    private val sales=listOf(
        sale("50000000-0000-4000-8000-000000000001","2026-11-24T02:00:00+00:00","partner","Mitra 1",0,"Golden","120.000000","2940000.00"),
        sale("50000000-0000-4000-8000-000000000002","2026-11-24T04:00:00+00:00","consumer","Sari",1,"Golden","3.000000","102000.00"),
        sale("50000000-0000-4000-8000-000000000003","2026-11-17T03:00:00+00:00","retail","Toko Segar",2,"Inthanon","40.000000","1160000.00"),
        sale("50000000-0000-4000-8000-000000000004","2026-11-17T05:00:00+00:00","consumer","Budi",0,"Golden","2.500000","85000.00"),
        sale("50000000-0000-4000-8000-000000000005","2026-11-10T03:00:00+00:00","partner","Mitra 3",3,"Inthanon","150.000000","3675000.00"),
        // A sale of a greenhouse that was turned off: the server no longer counts it, so the list must not show it.
        sale("50000000-0000-4000-8000-000000000006","2026-11-10T04:00:00+00:00","consumer","GH nonaktif",0,"Golden","1.000000","34000.00")
            .put("greenhouse_id","10000000-0000-4000-8000-0000000000ff"))

    private var opened:String?=null
    private fun show() {
        rule.setContent {com.inofarm.jogja.core.design.InofarmTheme {Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {Box(Modifier.padding(horizontal=16.dp)) {
            FinanceOverviewScreen(session,vault.generation,FinanceHomeRepository(vault,api),preference,0,onRecordSale={},customers=CustomerRepository(vault,api),
                loadSales={sales},onOpenSale={opened=it},exports=SeasonExportRepository(context,vault,api)) {androidx.compose.material3.Text("Buku biaya nyata")}
        }}}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tc").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun shot(name:String) {
        rule.waitForIdle();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null),"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun scrollTo(tag:String) {rule.onNodeWithTag("finance-overview").performScrollToNode(hasTestTag(tag))}
    private fun noRatios() {for(text in listOf("R/C","B/C","BEP","Laba","Rugi","Penerimaan (TR)")) rule.onAllNodesWithText(text,substring=true).assertCountEquals(0)}

    @Test fun runningSeasonShowsStandardCostsSalesAndNoRatios() {
        show()
        assertEquals(null,requested.first())
        rule.onNodeWithTag("finance-season-3").assertIsSelected()
        rule.onNodeWithTag("finance-tc-value").assertTextEquals("Rp44.044.400")
        rule.onNodeWithText("8 Sep 2026 sampai sekarang").assertExists()
        rule.onNodeWithText("Per GH, sama untuk 4 GH").assertExists()
        noRatios();shot("finance-running-1")
        scrollTo("finance-cost-line-6");rule.onNodeWithTag("finance-cost-line-0").assertTextContains("Rp5.806.100",substring=true);shot("finance-running-2")
        scrollTo("finance-week-detail")
        rule.onNodeWithTag("finance-week-1").performClick()
        rule.onNodeWithTag("finance-week-detail").assertTextContains("Minggu 2 Nov",substring=true).assertTextContains("Rp7.100.000",substring=true)
        rule.onNodeWithTag("finance-week-1").assertIsSelected();shot("finance-running-3")
        scrollTo("finance-season-sales");rule.onNodeWithTag("finance-gh-${ghs[0].first}").assertTextContains("Rp11.124.000",substring=true);shot("finance-running-4")
        scrollTo("finance-compare");rule.onNodeWithText("Antar musim · seluruh kebun").assertExists();shot("finance-running-5")
        noRatios()
    }

    @Test fun closedSeasonChipLoadsThatSeasonNumber() {
        show()
        rule.onNodeWithTag("finance-season-1").performClick()
        rule.waitUntil(10000) {runCatching {rule.onNodeWithText("3 Mar sampai 28 Mei 2026").assertExists()}.isSuccess}
        assertTrue("1" in requested)
        rule.onNodeWithTag("finance-season-1").assertIsSelected()
        rule.onNodeWithText("Biaya total (TC) Musim 1 · 4 GH").assertExists();noRatios();shot("finance-closed")
    }

    @Test fun blankCostLinesShowBelumDiisiNotZero() {
        show()
        rule.onNodeWithTag("finance-season-2").performClick()
        rule.waitUntil(10000) {runCatching {rule.onNodeWithTag("finance-tc-value").assertTextEquals("Belum diisi")}.isSuccess}
        rule.onAllNodesWithText("Rp0").assertCountEquals(0)
        scrollTo("finance-cost-line-0");rule.onNodeWithTag("finance-cost-line-0").assertTextContains("Belum diisi",substring=true);shot("finance-empty-costs-1")
        scrollTo("finance-record-sale");rule.onNodeWithText("Belum ada penjualan lunas di Musim 2.").assertExists();shot("finance-empty-costs-2")
    }

    @Test fun eyeShowsRealCostsAndExcelUsesTheSelectedSeason() {
        show()
        rule.onAllNodesWithTag("finance-open-ledger").assertCountEquals(0)
        rule.onNodeWithTag("finance-eye").performClick()
        assertEquals(true,flags.values.single())
        scrollTo("finance-open-ledger");rule.onNodeWithTag("finance-open-ledger").performClick()
        rule.onNodeWithText("Buku biaya nyata").assertExists()
        Espresso.pressBack()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("finance-excel").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-excel").performClick()
        rule.onNodeWithTag("season-export-sheet").assertExists()
        rule.onNodeWithTag("season-export-opt-3").assertIsSelected()
        rule.onNodeWithTag("season-export-opt-2").assertExists();rule.onNodeWithTag("season-export-opt-1").assertExists();rule.onNodeWithTag("season-export-opt-all").assertExists()
        rule.onNodeWithText("Berisi angka biaya dan penjualan apa adanya, untuk dihitung sendiri di Excel.").assertExists()
        for(part in listOf("summary","costs","sales")) rule.onNodeWithTag("season-export-part-$part").assertIsOn()
        rule.onNodeWithTag("season-export-file").assertTextContains("inofarm-keuangan-musim-3-",substring=true)
        shot("finance-export-sheet")
        rule.onNodeWithTag("season-export-opt-all").performClick()
        rule.onNodeWithTag("season-export-file").assertTextContains("inofarm-keuangan-musim-semua-",substring=true)
        for(part in listOf("summary","costs","sales")) rule.onNodeWithTag("season-export-part-$part").performClick()
        rule.onNodeWithTag("season-export-empty").assertExists();rule.onNodeWithTag("season-export-download").assertIsNotEnabled()
        rule.onNodeWithTag("season-export-part-costs").performClick();rule.onNodeWithTag("season-export-download").assertIsEnabled()
        noRatios()
        rule.onNodeWithTag("season-export-cancel").performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("season-export-sheet").fetchSemanticsNodes().isEmpty()}
    }

    @Test fun seasonSalesListFiltersByTypeGreenhouseAndName() {
        show()
        scrollTo("finance-season-sales");rule.onNodeWithTag("finance-season-sales").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("season-sales-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Penjualan Musim 3").assertExists()
        rule.onNodeWithTag("season-sales-summary").assertTextContains("Rp41.200.000",substring=true)
        rule.onNodeWithText("24 Nov 2026").assertExists();rule.onNodeWithText("Rp3.042.000").assertExists()
        rule.onNodeWithText("Mitra · GH A · Golden 120 kg").assertExists();rule.onAllNodesWithText("GH nonaktif").assertCountEquals(0)
        rule.onNodeWithText("10 Nov 2026").assertExists();rule.onAllNodesWithText("Rp3.675.000").assertCountEquals(2);shot("season-sales-all")
        rule.onNodeWithTag("season-sales-type-partner").performClick()
        rule.onNodeWithTag("season-sales-summary").assertTextContains("Mitra musim ini",substring=true).assertTextContains("Rp28.000.000",substring=true)
        rule.onAllNodesWithText("Sari").assertCountEquals(0);rule.onNodeWithText("Mitra 3").assertExists();shot("season-sales-partner")
        rule.onNodeWithTag("season-sales-type-all").performClick()
        rule.onNodeWithTag("season-sales-gh").performClick();rule.onNodeWithTag("season-sales-gh-${ghs[0].first}").performClick()
        rule.onNodeWithTag("season-sales-summary").assertTextContains("Rp11.124.000",substring=true)
        rule.onNodeWithText("Budi").assertExists();rule.onAllNodesWithText("Toko Segar").assertCountEquals(0)
        rule.onNodeWithTag("season-sales-gh").performClick();rule.onNodeWithTag("season-sales-gh-all").performClick()
        rule.onNodeWithTag("season-sales-search").performTextInput("toko");Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Toko Segar").assertExists();rule.onAllNodesWithText("Mitra 1").assertCountEquals(0)
        rule.onNodeWithTag("season-sales-summary").assertTextContains("Hasil pencarian",substring=true).assertTextContains("Rp1.160.000",substring=true);shot("season-sales-search")
        rule.onNodeWithTag("season-sale-50000000-0000-4000-8000-000000000003").performClick()
        assertEquals("50000000-0000-4000-8000-000000000003",opened)
        rule.onNodeWithText("Buku biaya nyata").assertExists()
        Espresso.pressBack()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("season-sales-list").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("season-sales-search").assertTextContains("toko");rule.onAllNodesWithText("Mitra 1").assertCountEquals(0)
    }
}
