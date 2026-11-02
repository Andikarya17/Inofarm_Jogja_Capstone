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
    private val server=MockWebServer();private val offline=AtomicBoolean(false);private val posts=AtomicInteger();private val logoutPosts=AtomicInteger()
    private val receipts=ConcurrentHashMap<String,JSONObject>()
    private val conflictNext=AtomicBoolean(false)
    private val harvestReceipts=ConcurrentHashMap<String,JSONObject>();private val harvestPosts=AtomicInteger();private val harvestConflictNext=AtomicBoolean(false);private val harvestUseNext=AtomicBoolean(false)
    private val gradeA=UUID.randomUUID().toString();private val gradeB=UUID.randomUUID().toString()
    private val seasonState=java.util.concurrent.atomic.AtomicReference("open");private val seasonVersion=AtomicInteger(1)
    private val epoch=UUID.randomUUID().toString();private val worker=UUID.randomUUID().toString();private val manager=UUID.randomUUID().toString()
    private val gh=UUID.randomUUID().toString();private val season=UUID.randomUUID().toString();private val group=UUID.randomUUID().toString()
    private val unit=UUID.randomUUID().toString();private val materialA=UUID.randomUUID().toString();private val materialB=UUID.randomUUID().toString()
    private val category=UUID.randomUUID().toString();private val financeReceipts=ConcurrentHashMap<String,JSONObject>();private val financeKinds=ConcurrentHashMap<String,String>();private val financePosts=AtomicInteger()
    @Before fun prepare() {
        assertTrue(rule.activity.packageName.endsWith(".finance"))
        server.start()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(offline.get()) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                val path=request.requestUrl!!.encodedPath.substringAfter("/api/v2/")
                val response=MockResponse().setHeader("Content-Type","application/json")
                if((path.startsWith("expenses") || path.startsWith("cost-allocations")) && request.method in setOf("POST","PATCH")) return financeMutation(request,response)
                if((path.startsWith("expenses/") || path.startsWith("cost-allocations/")) && path.endsWith("/audit")) return financeAudit(path,response)
                if(path.startsWith("harvests") && request.method in setOf("POST","PATCH")) return harvestMutation(request,response)
                if(path.startsWith("harvests/") && path.endsWith("/audit")) return harvestAudit(path,response)
                return when(path) {
                    "auth/login" -> { val isManager=JSONObject(request.body.readUtf8()).getString("email").startsWith("manager");response.setBody(JSONObject().put("token",if(isManager) "manager-fixture" else "worker-fixture").put("expires_at","2030-01-01T00:00:00Z").put("user",JSONObject().put("id",if(isManager) manager else worker).put("name",if(isManager) "Pengelola sintetis" else "Pekerja sintetis").put("role",if(isManager) "manager" else "worker")).toString()) }
                    "auth/logout" -> {logoutPosts.incrementAndGet();response.setResponseCode(204)}
                    "sync/status" -> response.setBody(JSONObject().put("schema_version","2").put("sync_epoch",epoch).toString())
                    "sync/changes" -> response.setBody(page(request.getHeader("Authorization").orEmpty().contains("manager-fixture")).toString())
                    "sync/operations" -> {
                        val op=JSONObject(request.body.readUtf8());val key=op.getString("operation_id");val id=op.getString("entity_id")
                        if(op.getString("action")=="update" && conflictNext.compareAndSet(true,false)) {
                            val remote=JSONObject(receipts.values.first {it.getString("entity_id")==id}.getJSONObject("data").toString()).put("version",2).put("notes","Koreksi server sintetis")
                            val remoteId=UUID.randomUUID().toString();receipts[remoteId]=JSONObject().put("operation_id",remoteId).put("entity_id",id).put("version",2).put("data",remote)
                            response.setResponseCode(409).setBody(JSONObject().put("code","version_conflict").put("message","Versi telah berubah.").put("current_version",2).put("current_data",remote).toString())
                        } else {
                            var created=false
                            val receipt=receipts.computeIfAbsent(key) {
                                created=true
                                val previous=receipts.values.filter {it.getString("entity_id")==id}.maxByOrNull {it.getInt("version")}
                                val version=(previous?.getInt("version") ?: 0)+1
                                val data=if(op.getString("action")=="cancel") JSONObject(previous!!.getJSONObject("data").toString()).put("record_state","cancelled") else JSONObject(op.getJSONObject("payload").toString()).put("record_state","active")
                                JSONObject().put("operation_id",key).put("entity_id",id).put("version",version)
                                    .put("data",data.put("id",id).put("version",version).put("recorded_by",previous?.getJSONObject("data")?.optString("recorded_by") ?: if(request.getHeader("Authorization").orEmpty().contains("manager-fixture")) manager else worker))
                            }
                            if(created) posts.incrementAndGet()
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
    private fun page(managerAccess:Boolean=false):JSONObject {
        val rows=JSONArray()
        fun row(kind:String,id:String,data:JSONObject) { rows.put(JSONObject().put("sequence",rows.length()+1).put("entity_type",kind).put("entity_id",id).put("greenhouse_id",gh).put("version",data.optInt("version",1)).put("tombstone",false).put("data",data.put("id",id).put("version",data.optInt("version",1)))) }
        row("greenhouses",gh,JSONObject().put("name","GH UI sintetis").put("active",true))
        row("seasons",season,JSONObject().put("greenhouse_id",gh).put("name","Musim UI sintetis").put("status",seasonState.get()).put("version",seasonVersion.get()))
        row("planting_groups",group,JSONObject().put("season_id",season).put("name","Kelompok UI sintetis").put("active",true).put("sowing_date","2026-10-01").put("transplant_date",JSONObject.NULL))
        for((id,name) in listOf(gradeA to "Grade A sintetis",gradeB to "Grade B sintetis")) row("grades",id,JSONObject().put("greenhouse_id",gh).put("name",name).put("active",true))
        row("units",unit,JSONObject().put("greenhouse_id",gh).put("name","gram uji").put("active",true).put("dimension","mass"))
        for((id,name) in listOf(materialA to "Bahan A sintetis",materialB to "Bahan B sintetis")) row("materials",id,JSONObject().put("greenhouse_id",gh).put("name",name).put("active",true).put("dimension","mass"))
        if(managerAccess) {
            row("expense_categories",category,JSONObject().put("greenhouse_id",gh).put("name","Kategori biaya sintetis").put("active",true))
            financeReceipts.values.forEach {receipt -> row(financeKinds.getValue(receipt.getString("entity_id")),receipt.getString("entity_id"),JSONObject(receipt.getJSONObject("data").toString()))}
            financeReceipts.values.filter {financeKinds[it.getString("entity_id")]=="expense"}.groupBy {it.getString("entity_id")}.forEach {(_,versions) ->
                val latest=versions.maxBy {it.getInt("version")};val source=latest.getJSONObject("data")
                val allocations=financeReceipts.values.filter {financeKinds[it.getString("entity_id")]=="cost_allocation"}.groupBy {it.getString("entity_id")}.values.map {it.maxBy {r -> r.getInt("version")}.getJSONObject("data")}.filter {it.getString("expense_id")==source.getString("id") && it.optString("record_state")=="active"}
                val used=allocations.fold(java.math.BigDecimal.ZERO) {sum,r -> sum+java.math.BigDecimal(r.getString("amount"))}.setScale(2)
                row("expense_balance",source.getString("balance_id"),JSONObject().put("expense_id",source.getString("id")).put("expense_version",source.getInt("version")).put("version",financePosts.get()+1)
                    .put("active",true).put("amount",source.getString("amount")).put("allocated_amount",used.toPlainString()).put("available_amount",java.math.BigDecimal(source.getString("amount")).subtract(used).setScale(2).toPlainString()))
            }
        }
        receipts.values.forEach { receipt -> row("activity",receipt.getString("entity_id"),JSONObject(receipt.getJSONObject("data").toString())) }
        harvestReceipts.values.forEach {receipt -> row("harvest",receipt.getString("entity_id"),JSONObject(receipt.getJSONObject("data").toString()))}
        harvestReceipts.values.groupBy {it.getString("entity_id")}.values.forEach {versions -> val receipt=versions.maxBy {it.getInt("version")};val data=receipt.getJSONObject("data");val items=data.getJSONArray("items")
            repeat(items.length()) {index -> val item=items.getJSONObject(index);val active=data.getString("record_state")=="active"
                row("harvest_balance",item.getString("id"),JSONObject().put("version",receipt.getInt("version")).put("harvest_id",receipt.getString("entity_id")).put("harvest_version",receipt.getInt("version")).put("active",active).put("weight_kg",item.getString("weight_kg")).put("sold_weight_kg","0.000000").put("available_weight_kg",if(active) item.getString("weight_kg") else "0.000000"))}
        }
        return JSONObject().put("schema_version","2").put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh).put("active",true))).put("data",rows).put("next_cursor","fixture").put("has_more",false)
    }
    private fun financeMutation(request:RecordedRequest,response:MockResponse):MockResponse {
        if(!request.getHeader("Authorization").orEmpty().contains("manager-fixture")) return response.setResponseCode(403).setBody("{\"code\":\"access_denied\"}")
        val op=JSONObject(request.body.readUtf8());val key=op.getString("operation_id");val id=op.getString("entity_id");val kind=op.getString("entity_type");val action=op.getString("action")
        financeReceipts[key]?.let {return response.setResponseCode(if(action=="create") 201 else 200).setBody(it.toString())}
        val previous=financeReceipts.values.filter {it.getString("entity_id")==id}.maxByOrNull {it.getInt("version")}
        val payload=op.getJSONObject("payload");assertFalse(payload.has("_local_form"));if(action!="cancel") assertTrue(payload.get("amount") is String)
        if(kind=="cost_allocation" && action!="cancel") {
            val source=financeReceipts.values.filter {it.getString("entity_id")==payload.getString("expense_id")}.maxBy {it.getInt("version")}.getJSONObject("data")
            val others=financeReceipts.values.filter {financeKinds[it.getString("entity_id")]=="cost_allocation" && it.getString("entity_id")!=id}.groupBy {it.getString("entity_id")}.values
                .map {it.maxBy {r -> r.getInt("version")}.getJSONObject("data")}.filter {it.getString("expense_id")==source.getString("id") && it.optString("record_state")=="active"}
            val used=others.fold(java.math.BigDecimal.ZERO) {sum,row -> sum+java.math.BigDecimal(row.getString("amount"))}
            if(used+java.math.BigDecimal(payload.getString("amount"))>java.math.BigDecimal(source.getString("amount"))) return response.setResponseCode(409).setBody("{\"code\":\"allocation_exceeds_source\",\"message\":\"Jumlah alokasi melebihi sumber sintetis.\"}")
        }
        val version=(previous?.getInt("version") ?: 0)+1
        val data=if(action=="cancel") JSONObject(previous!!.getJSONObject("data").toString()).put("record_state","cancelled") else JSONObject(payload.toString()).put("record_state","active")
        data.remove("reason");data.put("id",id).put("version",version).put("currency","IDR").put("recorded_by",manager).put("recorded_by_name","Pengelola sintetis")
            .put("created_at","2026-10-30T00:00:00+07:00").put("updated_at","2026-10-30T00:00:00+07:00").put("amount",java.math.BigDecimal(data.getString("amount")).setScale(2).toPlainString())
        if(kind=="expense") data.put("balance_id",previous?.getJSONObject("data")?.optString("balance_id") ?: UUID.randomUUID().toString()).put("category_name","Kategori biaya sintetis").put("category_version",1)
        else data.put("season_name","Musim UI sintetis").put("season_version",1)
        financeKinds[id]=kind
        val receipt=JSONObject().put("operation_id",key).put("entity_id",id).put("version",version).put("data",data)
        financeReceipts[key]=receipt;financePosts.incrementAndGet()
        return response.setResponseCode(if(action=="create") 201 else 200).setBody(receipt.toString())
    }
    private fun financeAudit(path:String,response:MockResponse):MockResponse {
        val id=path.split('/')[1];val rows=JSONArray()
        financeReceipts.values.filter {it.getString("entity_id")==id}.sortedBy {it.getInt("version")}.forEach {receipt -> rows.put(JSONObject().put("id",receipt.getInt("version"))
            .put("actor_id",manager).put("actor_name","Pengelola sintetis").put("version",receipt.getInt("version")).put("action","create").put("reason","Audit biaya sintetis").put("server_at","2026-10-30T00:00:00+07:00")
            .put("changes",JSONObject().put("before",JSONObject.NULL).put("after",receipt.getJSONObject("data"))))}
        return response.setBody(JSONObject().put("data",rows).put("next_cursor",JSONObject.NULL).toString())
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
        var created=false
        val receipt=harvestReceipts.computeIfAbsent(key) {
            created=true;val version=(previous?.getInt("version") ?: 0)+1
            val payload=op.getJSONObject("payload");assertFalse(payload.has("_local_form"))
            val data=if(op.getString("action")=="cancel") JSONObject(previous!!.getJSONObject("data").toString()).put("record_state","cancelled") else JSONObject(payload.toString()).put("record_state","active")
            val items=data.getJSONArray("items");repeat(items.length()) {index -> val item=items.getJSONObject(index);assertTrue(item.get("fruit_count") is Int)
                item.put("grade_name",if(item.getString("grade_id")==gradeA) "Grade A sintetis" else "Grade B sintetis").put("grade_version",1).put("weight_kg",java.math.BigDecimal(item.getString("weight_kg")).setScale(6).toPlainString())}
            val recorder=previous?.getJSONObject("data")?.getString("recorded_by") ?: if(request.getHeader("Authorization").orEmpty().contains("manager-fixture")) manager else worker
            data.put("id",id).put("version",version).put("recorded_by",recorder).put("recorded_by_name",if(recorder==worker) "Pekerja sintetis" else "Pengelola sintetis").put("completeness","ready")
                .put("reference_snapshot",JSONObject().put("age",JSONObject().put("hss",29).put("hst",JSONObject.NULL).put("status","available").put("reasons",JSONArray().put("HST belum berlaku sebelum pindah tanam."))))
            JSONObject().put("operation_id",key).put("entity_id",id).put("version",version).put("data",data)
        }
        if(created) harvestPosts.incrementAndGet()
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
    private fun choose(tag:String,id:String) {val selector=rule.onNodeWithTag(tag);(if(tag in setOf("expense-gh","expense-history-kind")) selector else selector.performScrollTo()).performClick();rule.onNodeWithTag("$tag-option-$id").performScrollTo().performClick()}
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
    private fun attachmentDraft():Pair<com.inofarm.jogja.feature.attachments.AttachmentViewModel,ByteArray> {
        val id=createAccepted("Foto kegiatan sintetis")
        rule.onNodeWithTag("history-attachments").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("attachment-choose").fetchSemanticsNodes().isNotEmpty()}
        val vm=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.attachments.AttachmentViewModel::class.java]
        val bytes=android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j0ioAAAAASUVORK5CYII=",android.util.Base64.DEFAULT)
        val source=File(rule.activity.cacheDir,"finance-test-picker-${UUID.randomUUID()}.png");source.writeBytes(bytes)
        val model=runtime();val session=model.vault.current!!
        rule.runOnIdle {vm.importResult(android.net.Uri.fromFile(source),session.scope.key,id,model.vault.generation,"Foto sintetis.png")}
        rule.waitUntil(10000) {vm.state.value.selected?.body?.optString("_copy_state")=="READY" && !vm.state.value.busy}
        assertTrue(source.delete());return vm to bytes
    }
    @Test fun attachmentUnknownPolicyKeepsEncryptedDraftAndPreventsQueue() {
        val (vm,_)=attachmentDraft()
        rule.onNodeWithTag("attachment-review").performScrollTo().performClick()
        rule.onNodeWithTag("attachment-queue").performScrollTo().assertIsNotEnabled()
        assertTrue(runtime().store.outbox(runtime().vault.current!!.scope.key).none {it.entityId==vm.state.value.selected!!.id})
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("attachment-queue").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("attachment-queue").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("finance-logout").performClick();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun attachmentPreviewProviderChecksBytesWriteModeAndLogout() {
        val (vm,bytes)=attachmentDraft();val model=runtime();val session=model.vault.current!!;val record=vm.state.value.selected!!
        val blob=model.store.blobsFor(session.scope.key,record.id).single()
        val intent=model.viewer.prepare(session,record,blob);val uri=intent.data!!
        assertEquals("image/png",rule.activity.contentResolver.getType(uri))
        rule.activity.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)!!.use {assertTrue(it.moveToFirst());assertEquals("Foto sintetis.png",it.getString(0))}
        assertArrayEquals(bytes,rule.activity.contentResolver.openInputStream(uri)!!.use {it.readBytes()})
        assertThrows(SecurityException::class.java) {rule.activity.contentResolver.openFileDescriptor(uri,"w")}
        val partial=File(rule.activity.cacheDir,"finance-attachment-views/"+uri.pathSegments.drop(1).joinToString("/")+".new");partial.writeBytes(bytes)
        rule.onNodeWithTag("finance-logout").performClick();rule.waitUntil(10000) {model.vault.current==null}
        assertFalse(partial.exists())
        assertThrows(SecurityException::class.java) {rule.activity.contentResolver.openInputStream(uri)}
        model.files.verify(session.scope.key,blob.id,com.inofarm.jogja.core.contracts.AttachmentBytes.parse(blob.body))
    }
    private fun acceptedAttachmentReason():com.inofarm.jogja.feature.attachments.AttachmentViewModel {
        managerLogin();val (vm,_)=attachmentDraft();val model=runtime();val session=model.vault.current!!;val row=vm.state.value.selected!!
        val blob=model.store.blobsFor(session.scope.key,row.id).single()
        val payload=com.inofarm.jogja.core.contracts.AttachmentBytes.parse(row.body).json().put("owner_type","activity").put("owner_id",row.body.getString("owner_id")).put("owner_version",1)
        // Explicit synthetic accepted file fixture; actual upload is covered by the guarded real runner.
        model.store.saveAndQueue(session.scope.key,row.id,row.greenhouseId,row.body,row.revision,kind=row.kind,queuePayload=payload,blobId=blob.id)
        val op=model.store.outbox(session.scope.key).single {it.entityId==row.id}
        model.store.acknowledge(session.scope.key,op.id,JSONObject().put("operation_id",op.id).put("entity_id",row.id).put("version",1).put("data",JSONObject(payload.toString()).put("id",row.id).put("version",1).put("record_state","active").put("recorded_by",session.scope.accountId)))
        rule.runOnIdle {vm.select(row.id)}
        rule.waitUntil(10000) {vm.state.value.selected?.serverVersion==1L && !vm.state.value.busy}
        rule.onNodeWithTag("attachment-cancel").performScrollTo().performClick();return vm
    }
    @Test fun attachmentReasonLogoutFlushesLatestRawTextWithoutChangingAcceptedMetadata() {
        val vm=acceptedAttachmentReason();val model=runtime();val scope=model.vault.current!!.scope.key;val row=vm.state.value.selected!!
        rule.onNodeWithTag("attachment-reason").performScrollTo().performTextReplacement("Alasan terbaru — belum dibatalkan")
        hideKeyboard();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-logout").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-logout").performClick();rule.waitUntil(10000) {model.vault.current==null}
        assertEquals("Alasan terbaru — belum dibatalkan",model.store.attachmentForm(scope,row.id)!!.getString("reason"))
        assertEquals(com.inofarm.jogja.core.contracts.SyncState.ACCEPTED,model.store.record(scope,row.id)!!.syncState)
        assertTrue(model.store.outbox(scope).filter {it.entityId==row.id}.all {it.state=="ACCEPTED"})
    }
    @Test fun attachmentReasonFailedFlushRetainsSessionAndTextThroughRecreation() {
        acceptedAttachmentReason();val model=runtime();val session=model.vault.current!!;val previousLogoutPosts=logoutPosts.get()
        val trigger="finance_test_reason_"+UUID.randomUUID().toString().replace("-","")
        fun sql(statement:String) {android.database.sqlite.SQLiteDatabase.openDatabase(rule.activity.getDatabasePath("inofarm_finance_v2.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {it.execSQL(statement)}}
        assertTrue(rule.activity.packageName.endsWith(".finance"))
        try {
            sql("CREATE TRIGGER $trigger BEFORE INSERT ON local_attachment_forms WHEN NEW.scope='${session.scope.key}' BEGIN SELECT RAISE(ABORT,'Synthetic form write failure'); END")
            rule.onNodeWithTag("attachment-reason").performScrollTo().performTextReplacement("Alasan yang wajib tetap ada")
            hideKeyboard();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-logout").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-logout").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("Belum keluar").fetchSemanticsNodes().isNotEmpty()}
            assertNotNull(model.vault.current);assertEquals(previousLogoutPosts,logoutPosts.get())
            rule.onNodeWithText("Kembali ke isian").performClick();rule.activityRule.scenario.recreate()
            rule.onNodeWithTag("attachment-reason").performScrollTo().assertTextContains("Alasan yang wajib tetap ada")
        } finally {sql("DROP TRIGGER IF EXISTS $trigger")}
        hideKeyboard();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-logout").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-logout").performClick();rule.waitUntil(10000) {model.vault.current==null}
    }
    @Test fun attachmentReviewRemainsReachableAtLargeFontNarrowAndLandscape() {
        attachmentDraft();val model=runtime()
        rule.activity.setContent {InofarmTheme {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {Box(Modifier.widthIn(max=320.dp)) {FinanceRoot(model)}}}}
        rule.onNodeWithTag("attachment-review").performScrollTo().performClick();rule.onNodeWithTag("attachment-queue").performScrollTo().assertIsNotEnabled()
        rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        rule.activity.setContent {InofarmTheme {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {Box(Modifier.widthIn(max=320.dp)) {FinanceRoot(model)}}}}
        rule.onNodeWithTag("attachment-queue").performScrollTo().assertIsNotEnabled()
        rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
    private fun editorSnapshotLogout(harvest:Boolean) {
        lateinit var runtime:FinanceRuntime;rule.runOnIdle {runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]}
        val session=runtime.vault.current!!
        val id=UUID.randomUUID().toString();val latest="Isian terakhir sebelum logout $id"
        val record=if(harvest) {
            val draft=com.inofarm.jogja.feature.harvest.domain.HarvestDraft(id=id,greenhouseId=gh,groupId=group,notes="Draf lama",items=listOf(com.inofarm.jogja.feature.harvest.domain.HarvestLine(gradeId=gradeA,fruitCount="",weightKg="belum")))
            val old=runtime.store.saveDraft(session.scope.key,id,gh,draft.localJson(),kind="harvest")
            // Deterministically leave the newest editor snapshot ahead of disk, without a timer race.
            val memory=old.copy(body=draft.copy(notes=latest).localJson())
            rule.runOnIdle {ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.harvest.HarvestViewModel::class.java].open(memory)}
            memory
        } else {
            val draft=com.inofarm.jogja.feature.activities.domain.ActivityDraft(id=id,greenhouseId=gh,groupId=group,notes="Draf lama")
            val old=runtime.store.saveDraft(session.scope.key,id,gh,draft.localJson())
            val memory=old.copy(body=draft.copy(notes=latest).localJson())
            rule.runOnIdle {ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.activities.ActivityViewModel::class.java].open(memory)}
            memory
        }
        assertEquals("Draf lama",runtime.store.record(session.scope.key,id)!!.body.getString("notes"))
        rule.waitUntil(10000) {rule.onAllNodesWithTag(if(harvest) "harvest-notes" else "finance-notes").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-logout").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(latest,runtime.store.record(session.scope.key,id)!!.body.getString("notes"));assertTrue(runtime.store.outbox(session.scope.key).isEmpty())
        login("worker@example.invalid");rule.onNodeWithTag(if(harvest) "finance-tab-3" else "finance-tab-2").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick()
        rule.onNodeWithTag("finance-edit").performScrollTo().performClick();rule.onNodeWithTag(if(harvest) "harvest-notes" else "finance-notes").performScrollTo().assertTextContains(latest)
        if(harvest) rule.onNodeWithTag("harvest-weight-0").performScrollTo().assertTextContains("belum")
        assertEquals(record.id,id)
    }
    @Test fun logoutFlushesLatestActivityEditorSnapshotBeforeClearingCredentials() {editorSnapshotLogout(false)}
    @Test fun logoutFlushesIncompleteHarvestEditorSnapshotBeforeClearingCredentials() {editorSnapshotLogout(true)}
    private fun unchangedAcceptedEditorLogout(harvest:Boolean) {
        val id=if(harvest) {
            fillHarvest("Panen diterima tanpa edit");queueHarvest();rule.waitUntil(10000) {harvestPosts.get()==1}
            val id=harvestReceipts.values.single().getString("entity_id")
            rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$id").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$id").performScrollTo().performClick();id
        } else createAccepted("Kegiatan diterima tanpa edit")
        lateinit var runtime:FinanceRuntime;rule.runOnIdle {runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]}
        val session=runtime.vault.current!!;val before=runtime.store.record(session.scope.key,id)!!
        rule.onNodeWithTag("finance-edit").performScrollTo().performClick();rule.onNodeWithTag("finance-logout").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        val after=runtime.store.record(session.scope.key,id)!!
        assertEquals(before.revision,after.revision);assertEquals(com.inofarm.jogja.core.contracts.SyncState.ACCEPTED,after.syncState)
        assertEquals(before.serverVersion,after.serverVersion);assertEquals(1,runtime.store.outbox(session.scope.key).size)
    }
    @Test fun logoutDoesNotCreateAnUnchangedActivityCorrection() {unchangedAcceptedEditorLogout(false)}
    @Test fun logoutDoesNotCreateAnUnchangedHarvestCorrection() {unchangedAcceptedEditorLogout(true)}
    @Test fun failedLogoutFlushKeepsSessionThroughRecreationAndDoesNotRevokeServerToken() {
        lateinit var runtime:FinanceRuntime;lateinit var auth:com.inofarm.jogja.feature.auth.AuthViewModel
        rule.runOnIdle {runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java];auth=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.auth.AuthViewModel::class.java]}
        val session=runtime.vault.current!!;val generation=runtime.vault.generation;val originalRequests=logoutPosts.get()
        val entered=kotlinx.coroutines.CompletableDeferred<Unit>();val release=kotlinx.coroutines.CompletableDeferred<Unit>()
        rule.runOnIdle {auth.logout {entered.complete(Unit);release.await();throw java.io.IOException("Synthetic local persistence failure")}}
        rule.waitUntil(10000) {entered.isCompleted};rule.onNodeWithText("Menyimpan isian terakhir sebelum keluar…").assertExists()
        Espresso.pressBack();assertEquals(session,runtime.vault.current);assertEquals(generation,runtime.vault.generation)
        rule.activityRule.scenario.recreate();rule.onNodeWithText("Menyimpan isian terakhir sebelum keluar…").assertExists();assertEquals(originalRequests,logoutPosts.get())
        release.complete(Unit);rule.waitUntil(10000) {rule.onAllNodesWithText("Belum keluar").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(session,runtime.vault.current);assertEquals(generation,runtime.vault.generation);assertEquals(originalRequests,logoutPosts.get())
        rule.onNodeWithText("Kembali ke isian").performClick();rule.onNodeWithTag("finance-logout").assertExists()
    }
    private fun managerLogin() {
        rule.onNodeWithTag("finance-logout").performClick();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()};login("manager@example.invalid")
    }
    private fun financialForm(allocation:Boolean=false) {
        rule.onNodeWithTag("finance-tab-4").performClick();choose("expense-gh",gh)
        rule.onNodeWithTag(if(allocation) "allocation-new" else "expense-new").performClick()
    }
    private fun fillPayment() {
        choose("expense-category",category);choose("expense-payment-kind","operating")
        rule.onNodeWithTag("expense-amount").performScrollTo().performTextReplacement("100,01")
        rule.onNodeWithTag("expense-notes").performScrollTo().performTextReplacement("Pembayaran UI sintetis — tidak otomatis menjadi biaya musim")
        hideKeyboard()
    }
    private fun fillAllocation(source:String,amount:String="60,01") {
        choose("allocation-source",source);choose("allocation-season",season);choose("allocation-class","VC")
        rule.onNodeWithTag("allocation-basis").performScrollTo().performTextReplacement("Nominal eksplisit untuk musim sintetis")
        rule.onNodeWithTag("expense-amount").performScrollTo().performTextReplacement(amount);hideKeyboard()
    }
    private fun queueFinancial() {rule.onNodeWithTag("expense-review").performScrollTo().performClick();rule.onNodeWithTag("expense-queue").performScrollTo().performClick()}
    private fun runtime()=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]
    private fun acceptedFinancial(kind:String):String {
        rule.waitUntil(15000) {runtime().vault.current?.let {session -> runtime().store.records(session.scope.key,kind=kind).any {it.syncState==com.inofarm.jogja.core.contracts.SyncState.ACCEPTED}}==true}
        val session=runtime().vault.current!!
        return runtime().store.records(session.scope.key,kind=kind).first {it.syncState==com.inofarm.jogja.core.contracts.SyncState.ACCEPTED}.id
    }
    @Test fun financialOfflineDraftRecreationReceiptAllocationAndAuditKeepExactMoney() {
        managerLogin();financialForm();fillPayment();offline.set(true);queueFinancial()
        rule.waitUntil(10000) {runtime().store.outbox(runtime().vault.current!!.scope.key).isNotEmpty()}
        val session=runtime().vault.current!!;val original=runtime().store.outbox(session.scope.key).single()
        assertEquals(0,financePosts.get());rule.activityRule.scenario.recreate()
        assertEquals(original.hash,runtime().store.outbox(session.scope.key).single().hash)
        offline.set(false);Thread.sleep(1200);rule.onNodeWithTag("finance-sync").performClick()
        val source=acceptedFinancial("expense");assertEquals("100.01",runtime().store.record(session.scope.key,source)!!.body.getString("amount"))
        rule.onNodeWithTag("finance-tab-4").performClick();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$source").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-record-$source").performScrollTo().performClick();rule.onNodeWithTag("history-audit").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Audit biaya sintetis",substring=true).fetchSemanticsNodes().isNotEmpty()};Espresso.pressBack()
        financialForm(true);fillAllocation(source);queueFinancial();val allocationId=acceptedFinancial("cost_allocation")
        rule.onNodeWithTag("finance-tab-4").performClick();choose("expense-history-kind","cost_allocation")
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-$allocationId").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$allocationId").performScrollTo().performClick()
        rule.onNodeWithText("Sisa untuk alokasi:",substring=true).performScrollTo().assertTextContains("40,00",substring=true)
        assertEquals(2,financePosts.get());assertEquals(original.id,runtime().store.outbox(session.scope.key).first {it.entityId==source}.id)
    }
    @Test fun rejectedAllocationRequiresExplicitReviewAndKeepsOriginalEnvelope() {
        managerLogin();financialForm();fillPayment();queueFinancial();val source=acceptedFinancial("expense")
        financialForm(true);fillAllocation(source,"101");queueFinancial()
        val session=runtime().vault.current!!
        rule.waitUntil(15000) {runtime().store.outbox(session.scope.key).any {it.error?.optString("code")=="allocation_exceeds_source"}}
        val original=runtime().store.outbox(session.scope.key).single {it.error?.optString("code")=="allocation_exceeds_source"}
        rule.onNodeWithTag("finance-tab-4").performClick();choose("expense-history-kind","cost_allocation")
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-record-${original.entityId}").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-${original.entityId}").performScrollTo().performClick()
        rule.onNodeWithTag("history-fix-rejected").performScrollTo().performClick();rule.onNodeWithTag("expense-amount").performScrollTo().performTextReplacement("60,01");hideKeyboard()
        queueFinancial();acceptedFinancial("cost_allocation")
        val operations=runtime().store.outbox(session.scope.key).filter {it.entityId==original.entityId}
        assertEquals(2,operations.size);val old=operations.single {it.id==original.id};assertEquals("SUPERSEDED",old.state);assertEquals(original.hash,old.hash)
        assertEquals(original.envelope.toString(),old.envelope.toString());assertEquals("ACCEPTED",operations.single {it.id!=original.id}.state)
    }
    @Test fun logoutFlushesLatestInvalidFinancialInputWithoutCreatingAnOperation() {
        managerLogin();val session=runtime().vault.current!!
        lateinit var editor:com.inofarm.jogja.feature.expenses.FinancialEntryViewModel
        rule.runOnIdle {
            editor=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.expenses.FinancialEntryViewModel::class.java]
            editor.new(com.inofarm.jogja.core.contracts.RecordKind.EXPENSE,gh)
            editor.edit {it.copy(amount="1,2,3",date="belum",notes="Input terbaru sebelum logout")};editor.pauseAutosave()
        }
        val draft=editor.state.value.draft!!;assertNull(runtime().store.record(session.scope.key,draft.id))
        rule.onNodeWithTag("finance-logout").performClick();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        login("manager@example.invalid");val saved=runtime().store.record(session.scope.key,draft.id)!!
        assertEquals("1,2,3",saved.body.getJSONObject("_local_form").getString("amount"));assertEquals("belum",saved.body.getJSONObject("_local_form").getString("date"))
        assertTrue(runtime().store.outbox(session.scope.key).isEmpty());assertEquals(0,financePosts.get())
    }
    @Test fun unchangedAcceptedFinancialEditorLogoutDoesNotCreateCorrection() {
        managerLogin();financialForm();fillPayment();queueFinancial();val id=acceptedFinancial("expense");val session=runtime().vault.current!!
        val before=runtime().store.record(session.scope.key,id)!!
        rule.runOnIdle {ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.expenses.FinancialEntryViewModel::class.java].open(before)}
        rule.onNodeWithTag("finance-logout").performClick();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        login("manager@example.invalid");val after=runtime().store.record(session.scope.key,id)!!
        assertEquals(before.revision,after.revision);assertEquals(com.inofarm.jogja.core.contracts.SyncState.ACCEPTED,after.syncState);assertEquals(1,runtime().store.outbox(session.scope.key).size)
    }
    @Test fun financialFormAtTwoHundredPercentKeepsSaveReachableWithImeAndLandscape() {
        managerLogin();financialForm()
        fun layout(narrow:Boolean) {
            rule.runOnUiThread {
                val model=runtime();val density=rule.activity.resources.displayMetrics.density
                rule.activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(density,2f)) {
                        InofarmTheme {
                            Box(Modifier.fillMaxSize()) {
                                Box((if(narrow) Modifier.width(320.dp) else Modifier.fillMaxWidth()).fillMaxHeight()) {FinanceRoot(model)}
                            }
                        }
                    }
                }
            }
            rule.waitForIdle()
        }
        layout(true);rule.onNodeWithTag("expense-notes").performScrollTo().performTextReplacement("Catatan pembayaran font 200 persen")
        Thread.sleep(500);rule.waitForIdle();rule.onNodeWithTag("expense-save-draft").performScrollTo().assertIsDisplayed()
        File(rule.activity.getExternalFilesDir(null),"expense-narrow-ime.png").outputStream().use {rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        hideKeyboard();rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE};layout(false)
        rule.onNodeWithTag("expense-notes").performScrollTo().assertTextContains("Catatan pembayaran font 200 persen");rule.onNodeWithTag("expense-notes").performTextReplacement("Landscape tetap dapat disimpan")
        Thread.sleep(500);rule.waitForIdle();rule.onNodeWithTag("expense-save-draft").performScrollTo().assertIsDisplayed()
        File(rule.activity.getExternalFilesDir(null),"expense-landscape-ime.png").outputStream().use {rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        rule.onNodeWithTag("expense-save-draft").performClick();rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
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
