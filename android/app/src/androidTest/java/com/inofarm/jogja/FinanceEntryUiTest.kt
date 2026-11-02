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
    private val conflictNext=AtomicBoolean(false)
    private val harvestReceipts=ConcurrentHashMap<String,JSONObject>();private val harvestPosts=AtomicInteger();private val harvestConflictNext=AtomicBoolean(false);private val harvestUseNext=AtomicBoolean(false)
    private val gradeA=UUID.randomUUID().toString();private val gradeB=UUID.randomUUID().toString()
    private val seasonState=java.util.concurrent.atomic.AtomicReference("open");private val seasonVersion=AtomicInteger(1)
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
                if(path.startsWith("harvests") && request.method in setOf("POST","PATCH")) return harvestMutation(request,response)
                if(path.startsWith("harvests/") && path.endsWith("/audit")) return harvestAudit(path,response)
                return when(path) {
                    "auth/login" -> { val isManager=JSONObject(request.body.readUtf8()).getString("email").startsWith("manager");response.setBody(JSONObject().put("token",if(isManager) "manager-fixture" else "worker-fixture").put("expires_at","2030-01-01T00:00:00Z").put("user",JSONObject().put("id",if(isManager) manager else worker).put("name",if(isManager) "Pengelola sintetis" else "Pekerja sintetis").put("role",if(isManager) "manager" else "worker")).toString()) }
                    "auth/logout" -> response.setResponseCode(204)
                    "sync/status" -> response.setBody(JSONObject().put("schema_version","2").put("sync_epoch",epoch).toString())
                    "sync/changes" -> response.setBody(page().toString())
                    "sync/operations" -> {
                        val op=JSONObject(request.body.readUtf8());val key=op.getString("operation_id");val id=op.getString("entity_id")
                        if(op.getString("action")=="update" && conflictNext.compareAndSet(true,false)) {
                            val remote=JSONObject(receipts.values.first {it.getString("entity_id")==id}.getJSONObject("data").toString()).put("version",2).put("notes","Koreksi server sintetis")
                            val remoteId=UUID.randomUUID().toString();receipts[remoteId]=JSONObject().put("operation_id",remoteId).put("entity_id",id).put("version",2).put("data",remote)
                            response.setResponseCode(409).setBody(JSONObject().put("code","version_conflict").put("message","Versi telah berubah.").put("current_version",2).put("current_data",remote).toString())
                        } else {
                            val receipt=receipts.getOrPut(key) {
                                posts.incrementAndGet()
                                val previous=receipts.values.filter {it.getString("entity_id")==id}.maxByOrNull {it.getInt("version")}
                                val version=(previous?.getInt("version") ?: 0)+1
                                val data=if(op.getString("action")=="cancel") JSONObject(previous!!.getJSONObject("data").toString()).put("record_state","cancelled") else JSONObject(op.getJSONObject("payload").toString()).put("record_state","active")
                                JSONObject().put("operation_id",key).put("entity_id",id).put("version",version)
                                    .put("data",data.put("id",id).put("version",version).put("recorded_by",previous?.getJSONObject("data")?.optString("recorded_by") ?: if(request.getHeader("Authorization").orEmpty().contains("manager-fixture")) manager else worker))
                            }
                            response.setResponseCode(if(op.getString("action")=="create") 201 else 200).setBody(receipt.toString())
                        }
                    }
                    "seasons/$season/close", "seasons/$season/reopen" -> {
                        val op=JSONObject(request.body.readUtf8());seasonState.set(if(path.endsWith("/close")) "closed" else "open");val version=seasonVersion.incrementAndGet()
                        response.setBody(JSONObject().put("operation_id",op.getString("operation_id")).put("entity_id",season).put("version",version)
                            .put("data",JSONObject().put("id",season).put("version",version).put("greenhouse_id",gh).put("name","Musim UI sintetis").put("status",seasonState.get())).toString())
                    }
                    else -> if(path.endsWith("/audit")) {
                        val id=path.substringAfter("activities/").substringBefore("/audit");val rows=JSONArray()
                        receipts.values.filter {it.getString("entity_id")==id}.sortedBy {it.getInt("version")}.forEach {receipt ->
                            rows.put(JSONObject().put("id",receipt.getInt("version")).put("actor_id",worker).put("actor_name","Pekerja sintetis").put("version",receipt.getInt("version"))
                                .put("action",if(receipt.getInt("version")==1) "create" else "update").put("reason","Audit sintetis").put("server_at","2026-10-29T09:00:00+07:00")
                                .put("changes",JSONObject().put("before",JSONObject.NULL).put("after",receipt.getJSONObject("data"))))
                        }
                        response.setBody(JSONObject().put("data",rows).put("next_cursor",JSONObject.NULL).toString())
                    } else response.setResponseCode(404).setBody("{\"message\":\"Fixture route absent\"}")
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
        fun row(kind:String,id:String,data:JSONObject) { rows.put(JSONObject().put("sequence",rows.length()+1).put("entity_type",kind).put("entity_id",id).put("greenhouse_id",gh).put("version",data.optInt("version",1)).put("tombstone",false).put("data",data.put("id",id).put("version",data.optInt("version",1)))) }
        row("greenhouses",gh,JSONObject().put("name","GH UI sintetis").put("active",true))
        row("seasons",season,JSONObject().put("greenhouse_id",gh).put("name","Musim UI sintetis").put("status",seasonState.get()).put("version",seasonVersion.get()))
        row("planting_groups",group,JSONObject().put("season_id",season).put("name","Kelompok UI sintetis").put("active",true).put("sowing_date","2026-10-01").put("transplant_date",JSONObject.NULL))
        for((id,name) in listOf(gradeA to "Grade A sintetis",gradeB to "Grade B sintetis")) row("grades",id,JSONObject().put("greenhouse_id",gh).put("name",name).put("active",true))
        row("units",unit,JSONObject().put("greenhouse_id",gh).put("name","gram uji").put("active",true).put("dimension","mass"))
        for((id,name) in listOf(materialA to "Bahan A sintetis",materialB to "Bahan B sintetis")) row("materials",id,JSONObject().put("greenhouse_id",gh).put("name",name).put("active",true).put("dimension","mass"))
        receipts.values.forEach { receipt -> row("activity",receipt.getString("entity_id"),JSONObject(receipt.getJSONObject("data").toString())) }
        harvestReceipts.values.forEach {receipt -> row("harvest",receipt.getString("entity_id"),JSONObject(receipt.getJSONObject("data").toString()))}
        harvestReceipts.values.groupBy {it.getString("entity_id")}.values.forEach {versions -> val receipt=versions.maxBy {it.getInt("version")};val data=receipt.getJSONObject("data");val items=data.getJSONArray("items")
            repeat(items.length()) {index -> val item=items.getJSONObject(index);val active=data.getString("record_state")=="active"
                row("harvest_balance",item.getString("id"),JSONObject().put("version",receipt.getInt("version")).put("harvest_id",receipt.getString("entity_id")).put("harvest_version",receipt.getInt("version")).put("active",active).put("weight_kg",item.getString("weight_kg")).put("sold_weight_kg","0.000000").put("available_weight_kg",if(active) item.getString("weight_kg") else "0.000000"))}
        }
        return JSONObject().put("schema_version","2").put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh).put("active",true))).put("data",rows).put("next_cursor","fixture").put("has_more",false)
    }
    private fun harvestMutation(request:RecordedRequest,response:MockResponse):MockResponse {
        val op=JSONObject(request.body.readUtf8());assertEquals("harvest",op.getString("entity_type"));val id=op.getString("entity_id");val key=op.getString("operation_id")
        val previous=harvestReceipts.values.filter {it.getString("entity_id")==id}.maxByOrNull {it.getInt("version")}
        if(op.getString("action")=="update" && harvestUseNext.compareAndSet(true,false)) return response.setResponseCode(409).setBody(JSONObject().put("code","harvest_in_use").put("message","Bobot sudah digunakan penjualan. Tinjau koreksi panen.").toString())
        if(op.getString("action")=="update" && harvestConflictNext.compareAndSet(true,false)) {
            val remote=JSONObject(previous!!.getJSONObject("data").toString()).put("version",2).put("notes","Panen server sintetis")
            val remoteId=UUID.randomUUID().toString();harvestReceipts[remoteId]=JSONObject().put("operation_id",remoteId).put("entity_id",id).put("version",2).put("data",remote)
            return response.setResponseCode(409).setBody(JSONObject().put("code","version_conflict").put("message","Versi panen berubah.").put("current_version",2).put("current_data",remote).toString())
        }
        val receipt=harvestReceipts.getOrPut(key) {
            harvestPosts.incrementAndGet();val version=(previous?.getInt("version") ?: 0)+1
            val payload=op.getJSONObject("payload");assertFalse(payload.has("_local_form"))
            val data=if(op.getString("action")=="cancel") JSONObject(previous!!.getJSONObject("data").toString()).put("record_state","cancelled") else JSONObject(payload.toString()).put("record_state","active")
            val items=data.getJSONArray("items");repeat(items.length()) {index -> val item=items.getJSONObject(index);assertTrue(item.get("fruit_count") is Int)
                item.put("grade_name",if(item.getString("grade_id")==gradeA) "Grade A sintetis" else "Grade B sintetis").put("grade_version",1).put("weight_kg",java.math.BigDecimal(item.getString("weight_kg")).setScale(6).toPlainString())}
            val recorder=previous?.getJSONObject("data")?.getString("recorded_by") ?: if(request.getHeader("Authorization").orEmpty().contains("manager-fixture")) manager else worker
            data.put("id",id).put("version",version).put("recorded_by",recorder).put("recorded_by_name",if(recorder==worker) "Pekerja sintetis" else "Pengelola sintetis").put("completeness","ready")
                .put("reference_snapshot",JSONObject().put("age",JSONObject().put("hss",29).put("hst",JSONObject.NULL).put("status","available").put("reasons",JSONArray().put("HST belum berlaku sebelum pindah tanam."))))
            JSONObject().put("operation_id",key).put("entity_id",id).put("version",version).put("data",data)
        }
        return response.setResponseCode(if(op.getString("action")=="create") 201 else 200).setBody(receipt.toString())
    }
    private fun harvestAudit(path:String,response:MockResponse):MockResponse {
        val id=path.substringAfter("harvests/").substringBefore("/audit");val rows=JSONArray()
        harvestReceipts.values.filter {it.getString("entity_id")==id}.sortedBy {it.getInt("version")}.forEach {receipt -> rows.put(JSONObject().put("id",receipt.getInt("version")).put("actor_id",worker).put("actor_name","Pekerja sintetis").put("version",receipt.getInt("version"))
            .put("action",if(receipt.getInt("version")==1) "create" else "update").put("reason","Audit panen sintetis").put("server_at","2026-10-29T09:00:00+07:00").put("changes",JSONObject().put("before",JSONObject.NULL).put("after",receipt.getJSONObject("data"))))}
        return response.setBody(JSONObject().put("data",rows).put("next_cursor",JSONObject.NULL).toString())
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
    private fun chooseContext(harvest:Boolean=false) {
        choose("finance-gh",gh);choose("finance-season",season);choose("finance-group",group)
        rule.onNodeWithTag(if(harvest) "harvest-new" else "finance-new").performScrollTo().performClick()
    }
    private fun choose(tag:String,id:String) {rule.onNodeWithTag(tag).performScrollTo().performClick();rule.onNodeWithTag("$tag-option-$id").performScrollTo().performClick()}
    private fun hideKeyboard() {rule.runOnIdle { val input=rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager;input.hideSoftInputFromWindow(rule.activity.window.decorView.windowToken,0) };rule.waitForIdle()}

    @Test fun offlineQueueRecreationReconnectAndManagerHistoryKeepHonestStatus() {
        chooseContext();offline.set(true)
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement("Catatan offline UI sintetis")
        hideKeyboard();rule.onNodeWithTag("finance-review").performScrollTo().performClick();rule.onNodeWithTag("finance-queue").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-2").fetchSemanticsNodes().isNotEmpty()}
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
    private fun createAccepted(note:String):String {
        chooseContext();rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement(note);hideKeyboard()
        rule.onNodeWithTag("finance-review").performScrollTo().performClick();rule.onNodeWithTag("finance-queue").performScrollTo().performClick()
        rule.waitUntil(10000) {posts.get()==1}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-2").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-2").performClick()
        val id=receipts.values.single().getString("entity_id")
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-edit").fetchSemanticsNodes().isNotEmpty()}
        return id
    }
    private fun makeConflict() {
        conflictNext.set(true)
        rule.onNodeWithTag("finance-edit").performScrollTo().performClick()
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement("Koreksi lokal sintetis");hideKeyboard()
        rule.onNodeWithTag("finance-reason").performScrollTo().performTextReplacement("Perbaikan lokal");hideKeyboard()
        rule.onNodeWithTag("finance-review").performScrollTo().performClick();rule.onNodeWithTag("finance-queue").performScrollTo().performClick()
        rule.waitUntil(10000) {receipts.size==2}
        val id=receipts.values.first().getString("entity_id")
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("history-compare").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("history-compare").performScrollTo().performClick()
        try {rule.waitUntil(10000) {rule.onAllNodesWithText("Server · versi 2").fetchSemanticsNodes().isNotEmpty()}}
        catch(error:AssertionError) {throw AssertionError(rule.onRoot().printToString(),error)}
        rule.onNodeWithText("Server · versi 2").performScrollTo().assertExists()
        val image=rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.getExternalFilesDir(null),"finance-conflict-review.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun conflictUseServerPreservesLocalCopyAndWorkerHasNoCancel() {
        createAccepted("Catatan awal konflik");rule.onNodeWithTag("history-cancel").assertDoesNotExist();makeConflict()
        rule.onNodeWithTag("conflict-use-server").performScrollTo().performClick();rule.onNodeWithTag("conflict-confirm-server").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("history-archive-0").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("history-archive-0").performScrollTo().performClick()
        rule.onNodeWithText("Koreksi lokal sintetis").performScrollTo().assertExists()
        assertEquals(1,posts.get())
    }
    @Test fun conflictKeepLocalRequiresReasonAndManagerCanAuditAndCancel() {
        val id=createAccepted("Catatan awal penyelesaian");makeConflict()
        rule.onNodeWithTag("conflict-keep-local").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("conflict-reason").performScrollTo().performTextReplacement("Isi lokal sudah ditinjau");hideKeyboard();choose("conflict-policy","preserve")
        rule.onNodeWithTag("conflict-keep-local").performScrollTo().performClick()
        rule.waitUntil(10000) {posts.get()==2}
        val latest=receipts.values.maxBy {it.getInt("version")};assertEquals(3,latest.getInt("version"));assertEquals("Koreksi lokal sintetis",latest.getJSONObject("data").getString("notes"))
        rule.onNodeWithTag("finance-logout").performClick();login("manager@example.invalid")
        rule.onNodeWithTag("finance-tab-2").performClick();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.onNodeWithTag("history-audit").performScrollTo().performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Audit sintetis",substring=true).fetchSemanticsNodes().isNotEmpty()}
        offline.set(true);rule.onNodeWithTag("history-audit").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Audit terbaru belum tersedia.",substring=true).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Audit terakhir tersimpan; pemeriksaan terbaru belum selesai atau koneksi terputus.").assertExists();offline.set(false)
        rule.onNodeWithTag("history-cancel").performScrollTo().performClick()
        rule.onNodeWithTag("history-action-confirm").assertIsNotEnabled();rule.onNodeWithTag("history-action-reason").performTextReplacement("Pembatalan sintetis");hideKeyboard()
        rule.onNodeWithTag("history-action-confirm").performClick();rule.waitUntil(10000) {posts.get()==3}
        rule.waitUntil(10000) {rule.onAllNodesWithText("Dibatalkan").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun managerSeasonCloseAndReopenUseReasonedVersionedQueue() {
        rule.onNodeWithTag("finance-logout").performClick();login("manager@example.invalid");choose("finance-gh",gh);choose("finance-season",season)
        rule.onNodeWithTag("finance-season-transition").performScrollTo().performClick()
        rule.onNodeWithTag("history-action-confirm").assertIsNotEnabled();rule.onNodeWithTag("history-action-reason").performTextReplacement("Penutupan sintetis");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(10000) {seasonState.get()=="closed"}
        rule.onNodeWithTag("finance-sync").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Buka kembali musim").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-season-transition").performScrollTo().performClick()
        rule.onNodeWithTag("history-action-reason").performTextReplacement("Buka untuk koreksi");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(10000) {seasonState.get()=="open" && seasonVersion.get()==3}
    }
    @Test fun historyFilterShowsEmptyThenRestoresMatchingRecord() {
        val id=createAccepted("Catatan filter UI")
        Espresso.pressBack()
        rule.onNodeWithTag("finance-history-filters").performClick();choose("history-type","nutrition")
        rule.onNodeWithTag("history-apply").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Belum ada catatan yang cocok dengan filter ini.").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-record-$id").assertDoesNotExist()
        rule.onNodeWithTag("finance-history-filters").performClick();rule.onNodeWithText("Hapus semua filter").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun fillHarvest(note:String,twoGrades:Boolean=false) {
        chooseContext(true)
        for((index,grade) in (if(twoGrades) listOf(gradeA,gradeB) else listOf(gradeA)).withIndex()) {
            rule.onNodeWithTag("harvest-add-item").performScrollTo().performClick();choose("harvest-grade-$index",grade)
            rule.onNodeWithTag("harvest-count-$index").performScrollTo().performTextReplacement(if(index==0) "10" else "0");hideKeyboard()
            rule.onNodeWithTag("harvest-weight-$index").performScrollTo().performTextReplacement(if(index==0) "15,25" else "0");hideKeyboard()
        }
        rule.onNodeWithTag("harvest-notes").performScrollTo().performTextReplacement(note);hideKeyboard()
    }
    private fun queueHarvest() {
        rule.onNodeWithTag("harvest-review").performScrollTo().performClick();rule.onNodeWithTag("harvest-queue").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-3").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-tab-3").performClick()
    }
    @Test fun offlineMultigradeHarvestRecreationReconnectManagerAuditAndCancel() {
        fillHarvest("Panen offline UI sintetis",true);offline.set(true)
        rule.activityRule.scenario.recreate();rule.onNodeWithTag("harvest-weight-0").performScrollTo().assertTextContains("15,25")
        rule.onNodeWithTag("harvest-count-1").performScrollTo().assertTextContains("0");queueHarvest()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Menunggu pengiriman").fetchSemanticsNodes().isNotEmpty()};assertEquals(0,harvestPosts.get())
        offline.set(false);rule.onNodeWithTag("finance-sync").performClick();rule.waitUntil(10000) {harvestPosts.get()==1}
        val id=harvestReceipts.values.single().getString("entity_id");val data=harvestReceipts.values.single().getJSONObject("data")
        assertEquals(2,data.getJSONArray("items").length());assertEquals("15.250000",data.getJSONArray("items").getJSONObject(0).getString("weight_kg"))
        rule.onNodeWithTag("finance-tab-2").performClick();rule.onNodeWithText("Panen offline UI sintetis").assertDoesNotExist();rule.onNodeWithTag("finance-tab-3").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.onNodeWithTag("history-cancel").assertDoesNotExist();rule.onNodeWithText("Pencatat: Pekerja sintetis").performScrollTo().assertExists()
        rule.onNodeWithTag("finance-logout").performClick();login("manager@example.invalid");rule.onNodeWithTag("finance-tab-3").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.onNodeWithTag("history-audit").performScrollTo().performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Audit panen sintetis",substring=true).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("history-cancel").performScrollTo().performClick();rule.onNodeWithTag("history-action-reason").performTextReplacement("Pembatalan panen UI");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(10000) {harvestPosts.get()==2};rule.waitUntil(10000) {rule.onAllNodesWithText("Dibatalkan").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(2,harvestReceipts.values.maxBy {it.getInt("version")}.getJSONObject("data").getJSONArray("items").length())
    }
    @Test fun harvestOwnCorrectionConflictUsesNewVersionAndRetainsLocalArchive() {
        fillHarvest("Panen awal konflik");queueHarvest();rule.waitUntil(10000) {harvestPosts.get()==1}
        val id=harvestReceipts.values.single().getString("entity_id")
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.onNodeWithTag("finance-edit").performScrollTo().performClick();rule.onNodeWithTag("harvest-grade-0").assertDoesNotExist()
        rule.onNodeWithTag("harvest-weight-0").performScrollTo().performTextReplacement("16,5");hideKeyboard();rule.onNodeWithTag("harvest-reason").performScrollTo().performTextReplacement("Timbang ulang UI");hideKeyboard()
        harvestConflictNext.set(true);queueHarvest();rule.waitUntil(10000) {harvestReceipts.size==2}
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick();rule.waitUntil(10000) {rule.onAllNodesWithTag("history-compare").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("history-compare").performScrollTo().performClick();rule.onNodeWithTag("conflict-reason").performScrollTo().performTextReplacement("Bobot lokal sudah diperiksa");hideKeyboard();choose("conflict-policy","preserve")
        rule.onNodeWithTag("conflict-keep-local").performScrollTo().performClick();rule.waitUntil(10000) {harvestPosts.get()==2}
        val latest=harvestReceipts.values.maxBy {it.getInt("version")};assertEquals(3,latest.getInt("version"));assertEquals("16.500000",latest.getJSONObject("data").getJSONArray("items").getJSONObject(0).getString("weight_kg"))
        rule.waitUntil(10000) {rule.onAllNodesWithTag("history-archive-0").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun harvestBalanceRejectionCanBeCorrectedAfterExplicitReview() {
        fillHarvest("Panen perbaikan saldo UI");queueHarvest();rule.waitUntil(10000) {harvestPosts.get()==1}
        val id=harvestReceipts.values.single().getString("entity_id")
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.onNodeWithTag("finance-edit").performScrollTo().performClick();rule.onNodeWithTag("harvest-weight-0").performScrollTo().performTextReplacement("1");hideKeyboard()
        rule.onNodeWithTag("harvest-reason").performScrollTo().performTextReplacement("Koreksi saldo UI");hideKeyboard();harvestUseNext.set(true);queueHarvest()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Perlu diperbaiki").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.onNodeWithTag("history-fix-rejected").performScrollTo().performClick();rule.onNodeWithTag("harvest-weight-0").performScrollTo().performTextReplacement("17,25");hideKeyboard();queueHarvest()
        rule.waitUntil(10000) {harvestPosts.get()==2};assertEquals("17.250000",harvestReceipts.values.maxBy {it.getInt("version")}.getJSONObject("data").getJSONArray("items").getJSONObject(0).getString("weight_kg"))
        lateinit var runtime:FinanceRuntime;rule.runOnIdle {runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]}
        val superseded=runtime.store.outbox(runtime.vault.current!!.scope.key).single {it.state=="SUPERSEDED"}
        assertEquals("1",superseded.envelope.getJSONObject("payload").getJSONArray("items").getJSONObject(0).getString("weight_kg"))
    }
    @Test fun invalidHarvestDraftKeepsSaveReachableAtNarrowAndLandscapeLargeFont() {
        fillHarvest("Draf panen font besar")
        fun content(narrow:Boolean) {rule.runOnUiThread {val runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java];val density=rule.activity.resources.displayMetrics.density
            rule.activity.setContent {CompositionLocalProvider(LocalDensity provides Density(density,2f)) {InofarmTheme {Box(Modifier.fillMaxSize()) {Box((if(narrow) Modifier.width(320.dp) else Modifier.fillMaxWidth()).fillMaxHeight()) {FinanceRoot(runtime)}}}}}
        };rule.waitForIdle()}
        fun capture(name:String) {val bitmap=rule.onRoot().captureToImage().asAndroidBitmap();File(rule.activity.getExternalFilesDir(null),name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        content(true);rule.onNodeWithTag("harvest-weight-0").performScrollTo().performTextReplacement("belum selesai")
        Thread.sleep(500);rule.waitForIdle();rule.onNodeWithTag("harvest-save-draft").performScrollTo();capture("finance-harvest-narrow-ime.png")
        rule.onNodeWithTag("harvest-save-draft").assertIsDisplayed();hideKeyboard()
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE};rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        content(false);rule.onNodeWithTag("harvest-weight-0").performScrollTo().performClick();Thread.sleep(500);rule.waitForIdle()
        rule.onNodeWithTag("harvest-save-draft").performScrollTo();capture("finance-harvest-landscape-ime.png");rule.onNodeWithTag("harvest-save-draft").assertIsDisplayed();hideKeyboard()
        rule.onNodeWithTag("harvest-save-draft").performClick();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-3").fetchSemanticsNodes().isNotEmpty()}
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
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
