package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import com.inofarm.jogja.feature.FinanceRoot
import com.inofarm.jogja.ui.InofarmTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import android.content.pm.ActivityInfo
import android.content.res.Configuration

/** Device UI + real HTTP transport, synthetic dispatcher. MySQL integration is a separate test. */
class FinanceEntryUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val server=MockWebServer();private val offline=AtomicBoolean(false);private val posts=AtomicInteger()
    private val receipts=ConcurrentHashMap<String,JSONObject>()
    private val epoch=UUID.randomUUID().toString();private val worker=UUID.randomUUID().toString();private val manager=UUID.randomUUID().toString()
    private val gh=UUID.randomUUID().toString();private val season=UUID.randomUUID().toString();private val group=UUID.randomUUID().toString()
    private val unit=UUID.randomUUID().toString();private val materialA=UUID.randomUUID().toString();private val materialB=UUID.randomUUID().toString()
    @Before fun prepare() {
        assertTrue(rule.activity.packageName.endsWith(".finance"))
        server.start()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(offline.get()) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                val path=request.requestUrl!!.encodedPath.substringAfter("/api/v2/")
                val response=MockResponse().setHeader("Content-Type","application/json")
                return when(path) {
                    "auth/login" -> { val isManager=JSONObject(request.body.readUtf8()).getString("email").startsWith("manager");response.setBody(JSONObject().put("token",if(isManager) "manager-fixture" else "worker-fixture").put("expires_at","2030-01-01T00:00:00Z").put("user",JSONObject().put("id",if(isManager) manager else worker).put("name",if(isManager) "Pengelola sintetis" else "Pekerja sintetis").put("role",if(isManager) "manager" else "worker")).toString()) }
                    "auth/logout" -> response.setResponseCode(204)
                    "sync/status" -> response.setBody(JSONObject().put("schema_version","2").put("sync_epoch",epoch).toString())
                    "sync/changes" -> response.setBody(page().toString())
                    "sync/operations" -> {
                        val op=JSONObject(request.body.readUtf8());val key=op.getString("operation_id")
                        val receipt=receipts.getOrPut(key) { posts.incrementAndGet();JSONObject().put("operation_id",key).put("entity_id",op.getString("entity_id")).put("version",1)
                            .put("data",JSONObject(op.getJSONObject("payload").toString()).put("id",op.getString("entity_id")).put("version",1).put("recorded_by",worker).put("record_state","active")) }
                        response.setResponseCode(201).setBody(receipt.toString())
                    }
                    else -> response.setResponseCode(404).setBody("{\"message\":\"Fixture route absent\"}")
                }
            }
        }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithTag("finance-logout").fetchSemanticsNodes().isNotEmpty() }
        if(rule.onAllNodesWithTag("finance-logout").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag("finance-logout").performClick()
        login("worker@example.invalid")
    }
    @After fun close() { server.shutdown() }
    private fun page():JSONObject {
        val rows=JSONArray()
        fun row(kind:String,id:String,data:JSONObject) { rows.put(JSONObject().put("sequence",rows.length()+1).put("entity_type",kind).put("entity_id",id).put("greenhouse_id",gh).put("version",1).put("tombstone",false).put("data",data.put("id",id).put("version",1))) }
        row("greenhouses",gh,JSONObject().put("name","GH UI sintetis").put("active",true))
        row("seasons",season,JSONObject().put("greenhouse_id",gh).put("name","Musim UI sintetis").put("status","open"))
        row("planting_groups",group,JSONObject().put("season_id",season).put("name","Kelompok UI sintetis").put("active",true).put("sowing_date","2026-10-01").put("transplant_date",JSONObject.NULL))
        row("units",unit,JSONObject().put("greenhouse_id",gh).put("name","gram uji").put("active",true).put("dimension","mass"))
        for((id,name) in listOf(materialA to "Bahan A sintetis",materialB to "Bahan B sintetis")) row("materials",id,JSONObject().put("greenhouse_id",gh).put("name",name).put("active",true).put("dimension","mass"))
        receipts.values.forEach { receipt -> row("activity",receipt.getString("entity_id"),JSONObject(receipt.getJSONObject("data").toString())) }
        return JSONObject().put("schema_version","2").put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh).put("active",true))).put("data",rows).put("next_cursor","fixture").put("has_more",false)
    }
    private fun login(email:String) {
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-server").performScrollTo().performTextReplacement(server.url("/api/v2/").toString())
        rule.onNodeWithTag("finance-email").performScrollTo().performTextReplacement(email)
        rule.onNodeWithTag("finance-password").performScrollTo().performTextReplacement("synthetic-ui-password")
        hideKeyboard();rule.onNodeWithTag("finance-login").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-gh").fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(10000) {rule.onAllNodesWithText("Data server sudah diperiksa. Tinjau antrean yang ditahan.").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun chooseContext() {
        choose("finance-gh",gh);choose("finance-season",season);choose("finance-group",group)
        rule.onNodeWithTag("finance-new").performScrollTo().performClick()
    }
    private fun choose(tag:String,id:String) {rule.onNodeWithTag(tag).performScrollTo().performClick();rule.onNodeWithTag("$tag-option-$id").performScrollTo().performClick()}
    private fun hideKeyboard() {rule.runOnIdle { val input=rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager;input.hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0) };rule.waitForIdle()}

    @Test fun offlineQueueRecreationReconnectAndManagerHistoryKeepHonestStatus() {
        chooseContext();offline.set(true)
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement("Catatan offline UI sintetis")
        hideKeyboard();rule.onNodeWithTag("finance-review").performScrollTo().performClick();rule.onNodeWithTag("finance-queue").performScrollTo().performClick()
        rule.onNodeWithTag("finance-tab-2").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Menunggu pengiriman").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(0,posts.get())
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("finance-tab-2").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Catatan offline UI sintetis").fetchSemanticsNodes().isNotEmpty()}
        offline.set(false)
        rule.waitUntil(10000) {rule.onNodeWithTag("finance-sync").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}
        rule.onNodeWithTag("finance-sync").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Diterima server").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(1,posts.get())
        rule.onNodeWithTag("finance-logout").performClick();login("manager@example.invalid")
        rule.onNodeWithTag("finance-tab-2").performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Catatan offline UI sintetis").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(worker,receipts.values.single().getJSONObject("data").getString("recorded_by"))
    }
    @Test fun multiMaterialDraftRotationAndBackRetainRawFieldsBeforeReview() {
        chooseContext();choose("finance-type","nutrition")
        rule.onNodeWithTag("finance-ppm").assertTextContains("")
        rule.onNodeWithTag("finance-review").performScrollTo().performClick()
        rule.onAllNodesWithText("PPM manual wajib; kosong bukan nol.").onFirst().assertExists()
        rule.onNodeWithTag("finance-ppm").performScrollTo().performTextReplacement("900")
        rule.onNodeWithTag("finance-ph").performScrollTo().performTextReplacement("6,1")
        hideKeyboard()
        for((index,material) in listOf(materialA,materialB).withIndex()) {
            rule.onNodeWithTag("finance-add-material").performScrollTo().performClick();choose("finance-material-$index",material);choose("finance-unit-$index",unit)
            rule.onNodeWithTag("finance-quantity-$index").performScrollTo().performTextReplacement((index+1).toString());hideKeyboard()
        }
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement("Draf dua bahan tetap ada");hideKeyboard()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("finance-ph").performScrollTo().assertTextContains("6,1")
        rule.onNodeWithTag("finance-quantity-1").performScrollTo().assertTextContains("2")
        Espresso.pressBack()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-2").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-2").performClick();rule.onNodeWithText("Draf dua bahan tetap ada").performScrollTo().performClick()
        rule.onNodeWithTag("finance-edit").performScrollTo().performClick()
        rule.onNodeWithTag("finance-ph").performScrollTo().assertTextContains("6,1")
        hideKeyboard();rule.onNodeWithTag("finance-review").performScrollTo().performClick();rule.onNodeWithTag("finance-queue").performScrollTo().performClick()
        rule.waitUntil(10000) {posts.get()==1}
        val body=receipts.values.single().getJSONObject("data");assertEquals("6.1",body.getString("manual_ph"));assertEquals(2,body.getJSONArray("materials").length())
    }
    @Test fun narrowAndLandscapeAtTwoHundredPercentKeepSaveReachableWithKeyboard() {
        chooseContext()
        fun overrideContent(narrow:Boolean) {
            rule.runOnUiThread {
                val runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]
                val density=rule.activity.resources.displayMetrics.density
                rule.activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density,2f)) {
                        InofarmTheme { Box(Modifier.fillMaxSize()) { Box((if(narrow) Modifier.width(320.dp) else Modifier.fillMaxWidth()).fillMaxHeight()) {FinanceRoot(runtime)} } }
                    }
                }
            }
            rule.waitForIdle()
        }
        fun capture(name:String) {
            val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
            File(rule.activity.getExternalFilesDir(null),name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
        overrideContent(true)
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement("Uji font 200 persen")
        Thread.sleep(500);rule.waitForIdle()
        rule.onNodeWithTag("finance-save-draft").performScrollTo().assertIsDisplayed()
        capture("finance-narrow-ime.png")
        hideKeyboard()
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        overrideContent(false)
        rule.onNodeWithTag("finance-notes").performScrollTo().assertTextContains("Uji font 200 persen")
        rule.onNodeWithTag("finance-notes").performTextReplacement("Landscape tetap tersimpan")
        Thread.sleep(500);rule.waitForIdle()
        rule.onNodeWithTag("finance-save-draft").performScrollTo()
        capture("finance-landscape-ime.png")
        rule.onNodeWithTag("finance-save-draft").assertIsDisplayed()
        rule.onNodeWithTag("finance-save-draft").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-2").fetchSemanticsNodes().isNotEmpty()}
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
    }
}
