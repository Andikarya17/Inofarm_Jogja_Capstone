package com.inofarm.jogja

import androidx.compose.ui.test.*
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import com.inofarm.jogja.feature.FinanceRoot
import com.inofarm.jogja.core.design.InofarmTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
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
    private val agenda = AtomicBoolean(false)
    private val secondGreenhouse = AtomicBoolean(false)
    private val gh2 = UUID.randomUUID().toString()
    private val season2 = UUID.randomUUID().toString()
    private val group2 = UUID.randomUUID().toString()
    /** A third active greenhouse with no season or planting yet, for the "Mulai tanam" path. */
    private val unplantedGreenhouse = AtomicBoolean(false)
    /** A second sown variety in the main greenhouse's season (two varieties in one greenhouse, 17 Nov 2026). */
    private val secondVariety = AtomicBoolean(false)
    private val groupV2 = UUID.randomUUID().toString()
    private val gh3 = UUID.randomUUID().toString()
    private val agendaSchedule = UUID.randomUUID().toString()
    private val agendaNotes = List(3) { UUID.randomUUID().toString() }
    private val seasonState=java.util.concurrent.atomic.AtomicReference("open");private val seasonVersion=AtomicInteger(1)
    private val longNames = AtomicBoolean(false)
    private val expiredLogin = AtomicBoolean(false)
    private val longGreenhouseName = "Greenhouse pembesaran melon hidroponik Blok Barat dengan nama lengkap"
    private val epoch=UUID.randomUUID().toString();private val worker=UUID.randomUUID().toString();private val manager=UUID.randomUUID().toString()
    private val gh=UUID.randomUUID().toString();private val season=UUID.randomUUID().toString();private val group=UUID.randomUUID().toString()
    private val extraGreenhouses = List(4) { UUID.randomUUID().toString() }
    private val overviewFixture = AtomicBoolean(false)
    private val managerWalkthroughFixture = AtomicBoolean(false)
    /** Master writes the wizard sends (path, operation), and greenhouses it created, so later reads see them. */
    private val wizardPosts = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, JSONObject>>()
    private val wizardGreenhouses = java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
    private val failedLogin = AtomicBoolean(false)
    private val managerLogins = AtomicInteger()
    private val designationFixture = AtomicBoolean(true)
    private val rejectManagerSession = AtomicBoolean(false)
    private val delayedScopeArrived = java.util.concurrent.CountDownLatch(1)
    private val releaseDelayedScope = java.util.concurrent.CountDownLatch(1)
    @Volatile private var delayedGreenhouse: String? = null
    @Volatile private var revokedGreenhouse: String? = null
    private val unit=UUID.randomUUID().toString();private val materialA=UUID.randomUUID().toString();private val materialB=UUID.randomUUID().toString()
    private val saleRejectNext=AtomicBoolean(false)
    /** Golden and Inthanon in the main season, Layak and Cacat stock, price list and buyers (sale form, 25 Nov 2026). */
    private val saleStockFixture=AtomicBoolean(false)
    /** An earlier sale of Golden to the retail buyer, for the last-price hint. */
    private val pastRetailSale=AtomicBoolean(false)
    private val goldenGroup=UUID.randomUUID().toString();private val inthanonGroup=UUID.randomUUID().toString()
    private val varGolden=UUID.randomUUID().toString();private val varInthanon=UUID.randomUUID().toString()
    private val gradeLayak=UUID.randomUUID().toString();private val gradeCacat=UUID.randomUUID().toString()
    private val custRetail=UUID.randomUUID().toString();private val custPartner=UUID.randomUUID().toString()
    private val saleHarvests=List(3) {UUID.randomUUID().toString()};private val saleItems=List(5) {UUID.randomUUID().toString()}
    private val assetConflictNext=AtomicBoolean(false)
    private val category=UUID.randomUUID().toString();private val financeReceipts=ConcurrentHashMap<String,JSONObject>();private val financeKinds=ConcurrentHashMap<String,String>();private val financePosts=AtomicInteger()
    @Before fun prepare() {
        assertTrue(rule.activity.packageName.endsWith(".finance") || rule.activity.packageName.endsWith(".finance.performance"))
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
        server.start()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(offline.get()) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                val path=request.requestUrl!!.encodedPath.substringAfter("/api/v2/")
                val response=MockResponse().setHeader("Content-Type","application/json")
                if((path.startsWith("expenses") || path.startsWith("cost-allocations") || path.startsWith("sales") || path.startsWith("assets") || path.startsWith("depreciation-allocations")) && request.method in setOf("POST","PATCH")) return financeMutation(request,response)
                if((path.startsWith("expenses/") || path.startsWith("cost-allocations/") || path.startsWith("sales/") || path.startsWith("assets/") || path.startsWith("depreciation-allocations/")) && path.endsWith("/audit")) return financeAudit(path,response)
                if(path.startsWith("harvests") && request.method in setOf("POST","PATCH")) return harvestMutation(request,response)
                if(path.startsWith("harvests/") && path.endsWith("/audit")) return harvestAudit(path,response)
                return when(path) {
                    "auth/login" -> {
                        val isManager = JSONObject(request.body.readUtf8()).getString("email").startsWith("manager")
                        if (isManager) managerLogins.incrementAndGet()
                        if (isManager && failedLogin.compareAndSet(true, false)) {
                            response.setResponseCode(422).setBody("{\"message\":\"Login uji ditolak. Coba masuk kembali.\"}")
                                .setBodyDelay(1800, java.util.concurrent.TimeUnit.MILLISECONDS)
                        } else {
                            response.setBody(JSONObject().put("token", if (isManager) "manager-fixture" else "worker-fixture")
                                .put("expires_at", if (expiredLogin.get()) "2020-01-01T00:00:00Z" else "2030-01-01T00:00:00Z")
                                .put("user", JSONObject().put("id", if (isManager) manager else worker)
                                    .put("name", if (isManager) "Pengelola sintetis" else "Pekerja sintetis")
                                    .put("role", if (isManager) "manager" else "worker")).toString())
                        }
                    }
                    "auth/logout" -> {logoutPosts.incrementAndGet();response.setResponseCode(204)}
                    "sync/status" -> response.setBody(JSONObject().put("schema_version","2").put("sync_epoch",epoch).toString())
                    "sync/changes" -> response.setBody(page(request.getHeader("Authorization").orEmpty().contains("manager-fixture")).toString())
                    "recovery/policy" -> response.setBody("{\"data\":{\"available\":false}}")
                    "consumer-prices" -> response.setBody(JSONObject().put("data",JSONArray().apply {
                        if(saleStockFixture.get()) put(JSONObject().put("id",UUID.randomUUID().toString()).put("variety_name","golden").put("quality","saleable").put("price_per_kg","34000.00").put("version",1).put("updated_at","2026-11-20T00:00:00Z"))
                    }).toString())
                    "customers" -> {
                        val type=request.requestUrl!!.queryParameter("type")
                        val rows=JSONArray()
                        if(saleStockFixture.get() && type=="retail") rows.put(JSONObject().put("id",custRetail).put("type","retail").put("name","Toko Buah Segar").put("whatsapp_display","0813-2222-1010").put("purchase_count",4).put("total_kg","60").put("source","manual"))
                        if(saleStockFixture.get() && type=="partner") rows.put(JSONObject().put("id",custPartner).put("type","partner").put("name","Mitra 1").put("whatsapp_display","0857-1111-2020").put("purchase_count",6).put("total_kg","0").put("source","manual"))
                        response.setBody(JSONObject().put("data",rows).put("next_cursor",JSONObject.NULL).toString())
                    }
                    "manager-home" -> {
                        val selection = request.requestUrl!!.queryParameter("greenhouse_id")
                        if (selection != null && selection == delayedGreenhouse) {
                            delayedScopeArrived.countDown()
                            releaseDelayedScope.await(10, java.util.concurrent.TimeUnit.SECONDS)
                        }
                        if (request.getHeader("Authorization") != "Bearer manager-fixture" || rejectManagerSession.get()) response.setResponseCode(403).setBody("{}")
                        else if (selection != null && selection == revokedGreenhouse) response.setResponseCode(404).setBody("{}")
                        else response.setBody(managerHome(selection).toString())
                    }
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
                    else -> if (managerWalkthroughFixture.get() && request.method == "GET" &&
                        com.inofarm.jogja.feature.admin.AdminResource.entries.any { it.path == path }) {
                        val rows = when (path) {
                            "greenhouses" -> JSONArray(managerGreenhouses().map { it.put("version", 1) } + wizardGreenhouses)
                            "planting-groups" -> JSONArray().put(JSONObject().put("id", group).put("name", "Kelompok uji"))
                            else -> JSONArray()
                        }
                        response.setBody(JSONObject().put("data", rows).put("next_cursor", JSONObject.NULL).toString())
                    } else if (managerWalkthroughFixture.get() && request.method == "POST" &&
                        (path in setOf("greenhouses", "seasons", "varieties", "planting-groups", "users", "assignments") || path.endsWith("/running-period"))) {
                        val op = JSONObject(request.body.readUtf8())
                        wizardPosts.add(path to op)
                        val data = JSONObject(op.getJSONObject("payload").toString()).put("id", op.getString("entity_id")).put("version", 1)
                        if (path == "greenhouses") wizardGreenhouses.add(JSONObject(data.toString()).put("active", true))
                        response.setResponseCode(if (op.getString("action") == "create") 201 else 200).setBody(JSONObject()
                            .put("operation_id", op.getString("operation_id")).put("entity_id", op.getString("entity_id")).put("version", 1).put("data", data).toString())
                    } else if(path.endsWith("/audit")) {
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
        rule.waitUntil(10000) {
            listOf("finance-login", "finance-account", "manager-home-relogin").any {
                rule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isEmpty()) rule.logoutFromAccount()
        login("worker@example.invalid")
    }
    @After fun close() { server.shutdown() }
    private fun managerGreenhouses(): List<JSONObject> {
        val main = JSONObject().put("id", gh).put("name", if (longNames.get()) longGreenhouseName else "GH UI sintetis")
        val extra = if (overviewFixture.get()) extraGreenhouses.mapIndexed { index, id ->
            JSONObject().put("id", id).put("name", "Greenhouse sintetis ${index + 2}")
        }.filter { it.getString("id") != revokedGreenhouse } else emptyList()
        // Fed greenhouses must be in the manager's scope too, or the pull is refused as out of scope.
        val unplanted = if (unplantedGreenhouse.get()) listOf(JSONObject().put("id", gh3).put("name", "GH belum tanam"),
            JSONObject().put("id", gh2).put("name", "Greenhouse kedua sintetis")) else emptyList()
        return listOf(main) + extra + unplanted
    }

    private fun managerHome(selection: String?): JSONObject {
        val today = java.time.LocalDate.now(com.inofarm.jogja.feature.activities.domain.ActivityDraft.WIB)
        val month = java.time.YearMonth.from(today)
        val at = java.time.Instant.now().toString()
        val choices = managerGreenhouses()
        val candidate = JSONObject().put("season_id", season).put("group_id", group).put("season_name", "Musim UI sintetis")
            .put("variety", "Melon sintetis").put("sowing_date", "2026-10-01").put("transplant_date", JSONObject.NULL).put("eligible", true)
        val ready = designationFixture.get() && seasonState.get() == "open"
        val cards = choices.filter { selection == null || it.getString("id") == selection }.map { option ->
            val period = if (ready && option.getString("id") == gh) JSONObject(candidate.toString()).put("state", "ready")
                .put("age", JSONObject().put("hss", 39)).put("phase", JSONObject().put("value", "Pembesaran buah"))
            else JSONObject().put("state", if (option.getString("id") == gh) "needs_review" else "no_active_period")
            JSONObject(option.toString()).put("active", true).put("version", 1).put("period", period)
        }
        val detail = if (selection == null) JSONObject.NULL else JSONObject()
            .put("periods", if (selection == gh) JSONArray().put(candidate) else JSONArray())
            .put("seasons", if (selection == gh) JSONArray().put(JSONObject().put("id", season).put("greenhouse_id", gh)
                .put("name", "Musim UI sintetis").put("start_date", "2026-10-01").put("status", seasonState.get()).put("version", seasonVersion.get())) else JSONArray())
            .put("measurement", if (ready && selection == gh) JSONObject().put("id", group).put("work_state", "completed")
                .put("manual_ec", "1.45").put("manual_ppm", "725").put("occurred_at", at)
                .put("recorded_by_name", "Pekerja sintetis").put("verification_status", "pending") else JSONObject.NULL)
            .put("harvest", if (ready && selection == gh) JSONObject().put("records", 2).put("weight_kg", "12.50").put("fruit_count", 8) else JSONObject.NULL)
            .put("schedules", if (managerWalkthroughFixture.get() && selection == gh && ready) {
                JSONArray().put(JSONObject().put("id", group).put("activity_type", "general")
                    .put("planned_at", at).put("notes", "Periksa talang sintetis"))
            } else JSONArray())
        return JSONObject().put("coverage", "complete").put("calendar", "Asia/Jakarta").put("account_id", manager).put("sync_epoch", epoch)
            .put("as_of", at).put("day", today.toString()).put("scope_ids", JSONArray(choices.map { it.getString("id") }))
            .put("choices", JSONArray(choices)).put("greenhouse_id", selection ?: JSONObject.NULL).put("greenhouses", JSONArray(cards))
            .put("detail", detail).put("recent", JSONArray()).put("attention", JSONArray())
            .put("cash", JSONObject().put("from", month.atDay(1).toString()).put("until", month.atEndOfMonth().toString())
                .put("payments", JSONObject().put("amount", "123456789012.50").put("records", 2))
                .put("receipts", JSONObject().put("amount", "300000.00").put("records", 1)))
    }

    private fun page(managerAccess:Boolean=false):JSONObject {
        val rows=JSONArray()
        fun row(kind:String,id:String,data:JSONObject,greenhouseId:String=gh) { rows.put(JSONObject().put("sequence",rows.length()+1).put("entity_type",kind).put("entity_id",id).put("greenhouse_id",greenhouseId).put("version",data.optInt("version",1)).put("tombstone",false).put("data",data.put("id",id).put("version",data.optInt("version",1)))) }
        row("greenhouses",gh,JSONObject().put("name",if (longNames.get()) longGreenhouseName else "GH UI sintetis").put("active",true))
        row("seasons",season,JSONObject().put("greenhouse_id",gh).put("name","Musim UI sintetis").put("status",seasonState.get()).put("version",seasonVersion.get()))
        row("planting_groups",group,JSONObject().put("season_id",season).put("name","Kelompok UI sintetis").put("active",true).put("sowing_date","2026-10-01").put("transplant_date",JSONObject.NULL))
        if (secondGreenhouse.get()) {
            row("greenhouses", gh2, JSONObject().put("name", "Greenhouse kedua sintetis").put("active", true), gh2)
            row("seasons", season2, JSONObject().put("greenhouse_id", gh2).put("name", "Musim kedua sintetis").put("status", "open").put("version", 1), gh2)
            row("planting_groups", group2, JSONObject().put("season_id", season2).put("name", "Kelompok kedua sintetis").put("active", true)
                .put("sowing_date", "2026-10-05").put("transplant_date", JSONObject.NULL), gh2)
        }
        if (secondVariety.get()) row("planting_groups", groupV2, JSONObject().put("season_id", season).put("name", "Varietas kedua sintetis")
            .put("active", true).put("sowing_date", "2026-10-01").put("transplant_date", JSONObject.NULL))
        if (unplantedGreenhouse.get()) row("greenhouses", gh3, JSONObject().put("name", "GH belum tanam").put("active", true), gh3)
        if (agenda.get()) {
            val at = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta")).toString() + "T08:00:00+07:00"
            row("schedules", agendaSchedule, JSONObject().put("planting_group_id", group).put("activity_type", "general")
                .put("planned_at", at).put("notes", "Cek kebun hari ini").put("active", true))
            agendaNotes.forEachIndexed { index, id ->
                row("activity", id, JSONObject().put("planting_group_id", group).put("schedule_id", agendaSchedule)
                    .put("activity_type", "general").put("occurred_at", at).put("record_state", "active")
                    .put("work_state", if (index == 2) "skipped" else "completed").put("notes", "Catatan agenda sintetis")
                    .put("recorded_by", worker).put("recorded_by_role", "worker").put("materials", JSONArray()))
            }
            row("activity", UUID.randomUUID().toString(), JSONObject().put("planting_group_id", group)
                .put("activity_type", "general").put("occurred_at", at).put("record_state", "active").put("work_state", "completed")
                .put("notes", "Catatan orang lain sintetis").put("recorded_by", "someone-else").put("recorded_by_role", "worker").put("materials", JSONArray()))
            row("activity_review", UUID.randomUUID().toString(), JSONObject().put("activity_id", agendaNotes[2]).put("activity_version", 1)
                .put("decision", "invalid").put("reason", "Foto belum jelas"))
        }
        for((id,name) in listOf(gradeA to "Grade A sintetis",gradeB to "Grade B sintetis")) row("grades",id,JSONObject().put("greenhouse_id",gh).put("name",name).put("active",true))
        if(saleStockFixture.get()) {
            row("varieties",varGolden,JSONObject().put("greenhouse_id",gh).put("name","Golden").put("active",true))
            row("varieties",varInthanon,JSONObject().put("greenhouse_id",gh).put("name","Inthanon").put("active",true))
            for((id,variety) in listOf(goldenGroup to varGolden,inthanonGroup to varInthanon)) row("planting_groups",id,JSONObject().put("season_id",season).put("variety_id",variety)
                .put("name",if(variety==varGolden) "Golden" else "Inthanon").put("active",true).put("sowing_date","2026-09-01").put("transplant_date",JSONObject.NULL))
            row("grades",gradeLayak,JSONObject().put("greenhouse_id",gh).put("name","Layak").put("active",true))
            row("grades",gradeCacat,JSONObject().put("greenhouse_id",gh).put("name","Cacat").put("active",true))
            fun item(id:String,grade:String,kg:String)=JSONObject().put("id",id).put("grade_id",grade).put("grade_name",if(grade==gradeLayak) "Layak" else "Cacat").put("grade_version",1)
                .put("weight_kg",kg).put("fruit_count",JSONObject.NULL).put("quality",if(grade==gradeLayak) "saleable" else "defective")
            val harvests=listOf(Triple(saleHarvests[0],goldenGroup,"2026-11-17T08:00:00+07:00") to listOf(item(saleItems[0],gradeLayak,"32.000000"),item(saleItems[1],gradeCacat,"40.000000")),
                Triple(saleHarvests[1],goldenGroup,"2026-11-20T08:00:00+07:00") to listOf(item(saleItems[2],gradeLayak,"308.000000")),
                Triple(saleHarvests[2],inthanonGroup,"2026-11-18T08:00:00+07:00") to listOf(item(saleItems[3],gradeLayak,"210.000000"),item(saleItems[4],gradeCacat,"25.000000")))
            harvests.forEach {(head,items) ->
                row("harvest",head.first,JSONObject().put("planting_group_id",head.second).put("occurred_at",head.third).put("record_state","active").put("notes","")
                    .put("recorded_by",worker).put("recorded_by_name","Pekerja sintetis").put("completeness","ready").put("items",JSONArray(items)))
                items.forEach {row("harvest_balance",it.getString("id"),JSONObject().put("harvest_id",head.first).put("harvest_version",1).put("active",true)
                    .put("weight_kg",it.getString("weight_kg")).put("sold_weight_kg","0.000000").put("available_weight_kg",it.getString("weight_kg")))}
            }
            if(managerAccess && pastRetailSale.get()) row("sale",UUID.randomUUID().toString(),JSONObject().put("season_id",season).put("paid_at","2026-11-12T10:00:00+07:00").put("payment_status","paid")
                .put("customer_name","Toko Buah Segar").put("customer_id",custRetail).put("customer_type","retail").put("notes","").put("record_state","active")
                .put("total_amount","1680000.00").put("total_weight_kg","60.000000").put("items",JSONArray())
                .put("lines",JSONArray().put(JSONObject().put("id",UUID.randomUUID().toString()).put("planting_group_id",goldenGroup).put("grade_id",gradeLayak)
                    .put("variety_name","Golden").put("quality","saleable").put("quantity_kg","60.000000").put("price_per_kg","28000.000000").put("allocations",JSONArray()))))
        }
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
                val sold=financeReceipts.values.filter {financeKinds[it.getString("entity_id")]=="sale"}.groupBy {it.getString("entity_id")}.values.map {it.maxBy {r -> r.getInt("version")}.getJSONObject("data")}.filter {it.optString("record_state")=="active"}.fold(java.math.BigDecimal.ZERO) {sum,sale -> val lines=sale.optJSONArray("items") ?: JSONArray();sum+(0 until lines.length()).map {lines.getJSONObject(it)}.filter {it.getString("harvest_item_id")==item.getString("id")}.fold(java.math.BigDecimal.ZERO) {subtotal,line -> subtotal+java.math.BigDecimal(line.getString("quantity_kg"))}}
                row("harvest_balance",item.getString("id"),JSONObject().put("version",receipt.getInt("version")+financeReceipts.values.count {financeKinds[it.getString("entity_id")]=="sale"}).put("harvest_id",receipt.getString("entity_id")).put("harvest_version",receipt.getInt("version")).put("active",active).put("weight_kg",item.getString("weight_kg")).put("sold_weight_kg",sold.setScale(6).toPlainString()).put("available_weight_kg",if(active) (java.math.BigDecimal(item.getString("weight_kg"))-sold).setScale(6).toPlainString() else "0.000000"))}
        }
        val scope = if (managerAccess) JSONArray(managerGreenhouses()) else JSONArray().put(JSONObject().put("id",gh).put("active",true)).also { if (secondGreenhouse.get()) it.put(JSONObject().put("id",gh2).put("active",true)) }
        if (unplantedGreenhouse.get() && !managerAccess) scope.put(JSONObject().put("id", gh3).put("active", true))
        return JSONObject().put("schema_version","2").put("sync_epoch",epoch).put("scope",scope).put("data",rows).put("next_cursor","fixture").put("has_more",false)
    }
    private fun financeMutation(request:RecordedRequest,response:MockResponse):MockResponse {
        if(!request.getHeader("Authorization").orEmpty().contains("manager-fixture")) return response.setResponseCode(403).setBody("{\"code\":\"access_denied\"}")
        val op=JSONObject(request.body.readUtf8());val key=op.getString("operation_id");val id=op.getString("entity_id");val kind=op.getString("entity_type");val action=op.getString("action")
        financeReceipts[key]?.let {return response.setResponseCode(if(action=="create") 201 else 200).setBody(it.toString())}
        val previous=financeReceipts.values.filter {it.getString("entity_id")==id}.maxByOrNull {it.getInt("version")}
        if(kind=="asset" && action=="update" && assetConflictNext.compareAndSet(true,false)) {
            val remote=JSONObject(previous!!.getJSONObject("data").toString()).put("version",2).put("name","Nama aset pada server")
            val remoteId=UUID.randomUUID().toString();financeReceipts[remoteId]=JSONObject().put("operation_id",remoteId).put("entity_id",id).put("version",2).put("data",remote)
            return response.setResponseCode(409).setBody(JSONObject().put("code","version_conflict").put("message","Versi aset berubah.").put("current_version",2).put("current_data",remote).toString())
        }
        val payload=op.getJSONObject("payload");assertFalse(payload.has("_local_form"));if(action!="cancel" && kind !in setOf("sale","asset")) assertTrue(payload.get("amount") is String)
        if(kind=="sale" && action!="cancel" && saleRejectNext.compareAndSet(true,false)) return response.setResponseCode(409).setBody("{\"code\":\"harvest_exceeded\",\"message\":\"Saldo sumber berubah pada server sintetis.\"}")
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
            .put("created_at","2026-10-30T00:00:00+07:00").put("updated_at","2026-10-30T00:00:00+07:00")
        if(kind !in setOf("sale","asset")) data.put("amount",java.math.BigDecimal(data.getString("amount")).setScale(2).toPlainString())
        else if(kind=="sale") {
            val lines=data.optJSONArray("lines") ?: data.getJSONArray("items");if(data.has("lines")) data.put("items",JSONArray());var exact=java.math.BigDecimal.ZERO;var weight=java.math.BigDecimal.ZERO
            repeat(lines.length()) {index -> val line=lines.getJSONObject(index);val q=java.math.BigDecimal(line.getString("quantity_kg"));val price=java.math.BigDecimal(line.getString("price_per_kg"));exact=exact+q*price;weight=weight+q;line.put("quantity_kg",q.setScale(6).toPlainString()).put("price_per_kg",price.setScale(6).toPlainString()).put("grade_name","Grade penjualan sintetis").put("harvest_version",1)}
            data.put("total_amount",exact.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString()).put("total_weight_kg",weight.setScale(6).toPlainString()).put("exact_total",exact.setScale(12).toPlainString())
        }
        if(kind in setOf("asset","depreciation_allocation")) {data.put("greenhouse_id",gh).put("formula_version","anniversary-cumulative-half-up-v1");if(kind=="depreciation_allocation") data.put("year_budget","33.33").put("asset_version",1)}
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
            val items=data.getJSONArray("items");repeat(items.length()) {index -> val item=items.getJSONObject(index);assertTrue(item.isNull("fruit_count") || item.get("fruit_count") is Int)
                // Like the server since 20 Nov 2026: a line with only its quality is filed under the system grade "Layak"/"Cacat".
                if(item.optString("grade_id").isBlank()) item.put("grade_id",if(item.optString("quality")=="defective") gradeB else gradeA)
                    .put("grade_name",if(item.optString("quality")=="defective") "Cacat" else "Layak")
                else item.put("grade_name",if(item.getString("grade_id")==gradeA) "Grade A sintetis" else "Grade B sintetis")
                item.put("grade_version",1).put("weight_kg",java.math.BigDecimal(item.getString("weight_kg")).setScale(6).toPlainString())}
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
    @Test fun workerSelectsGreenhouseDirectlyAndOpensReadyRecordingContext() {
        capture("worker-alignment-picker")
        rule.waitUntil(15_000) { runCatching { rule.onNodeWithTag("finance-home-list").performScrollToNode(hasTestTag("worker-greenhouse-$gh")) }.isSuccess }
        readyNode("worker-greenhouse-$gh").performClick()
        capture("worker-alignment-selected")
        rule.openRecord("activity")
        readyNode("entry-ec").assertExists()
    }

    @Test fun workerNativePickersAndImeKeepDraftOnNavigation() {
        val home = rule.onNodeWithTag("finance-home-list")
        rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasTestTag("worker-greenhouse-$gh")) }.isSuccess }
        readyNode("worker-greenhouse-$gh").performClick()
        captureWindow("worker-home-ready")
        rule.openRecord("activity")
        readyNode("entry-date-picker").performScrollTo().assertIsDisplayed().performClick()
        Espresso.onView(isAssignableFrom(android.widget.DatePicker::class.java)).check(matches(isDisplayed()))
        captureWindow("worker-native-date")
        Espresso.pressBack()
        readyNode("entry-time-picker").performScrollTo().assertIsDisplayed().performClick()
        Espresso.onView(isAssignableFrom(android.widget.TimePicker::class.java)).check(matches(isDisplayed()))
        captureWindow("worker-native-time")
        Espresso.pressBack()
        // The greenhouse entry form (F4) picks the time with the native picker only; typed readings survive recreation.
        readyNode("entry-ec").performScrollTo().performTextReplacement("1,25")
        captureWindow("worker-measurement-ime")
        hideKeyboard()
        rule.activityRule.scenario.recreate()
        readyNode("entry-ec").performScrollTo().assertTextContains("1,25")
        readyNode("finance-save-draft").scrollIfScrollable().assertIsEnabled().performClick()
        rule.onNodeWithTag("finance-unsent").assertDoesNotExist()
        rule.selectFinanceTab(1)
        readyText("Draf").assertExists()
        rule.selectFinanceTab(0)
        readyNode("manager-back").performClick()
        rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasTestTag("worker-greenhouse-$gh")) }.isSuccess }
        readyNode("worker-greenhouse-$gh").assertIsDisplayed()
    }

    @Test fun workerNavigationHasTwoTabsRecordButtonAndUnsentSheet() {
        rule.onNodeWithTag("finance-tab-0").assertIsSelected()
        rule.onNodeWithTag("finance-tab-1").assertExists()
        rule.onNodeWithTag("finance-tab-2").assertDoesNotExist()
        rule.onNodeWithTag("finance-unsent").assertDoesNotExist()
        for (tab in 0..1) {
            rule.selectFinanceTab(tab)
            readyNode("finance-record-fab").performClick()
            // Readings live in the Kegiatan form (17 Nov 2026), so there is no separate Pengukuran tile.
            for (tag in listOf("record-activity", "record-harvest")) readyNode(tag).assertIsDisplayed()
            rule.onNodeWithTag("record-measurement").assertDoesNotExist()
            if (tab == 0) captureSecondary("worker-record-sheet")
            rule.onNodeWithTag("record-expense").assertDoesNotExist()
            rule.onNodeWithTag("record-sale").assertDoesNotExist()
            Espresso.pressBack()
        }
        rule.selectFinanceTab(0)
        offline.set(true)
        chooseContext()
        rule.onNodeWithTag("entry-notes").performScrollTo().performTextReplacement("Catatan belum terkirim sintetis")
        hideKeyboard()
        rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick()
        readyNode("finance-queue").scrollIfScrollable().performClick()
        rule.openFinanceOutbox()
        readyNode("queue-send-now").assertIsDisplayed()
        captureSecondary("worker-unsent-sheet")
        rule.onNodeWithText("ID kiriman").assertDoesNotExist()
        rule.onNodeWithText("Rincian").assertDoesNotExist()
        val reviewButton = androidx.compose.ui.test.SemanticsMatcher("Buka catatan") { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.TestTag) && it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith("finance-review-") }
        rule.onAllNodes(reviewButton).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyText("Kembali ke belum terkirim").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("queue-send-now").assertIsDisplayed()
        offline.set(false)
        readyNode("queue-send-now").performClick()
        rule.waitUntil(15_000) { posts.get() == 1 }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("finance-unsent").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun workerHomeShowsGreenhouseCardsAndCorrectionsCardWithoutOpeningAGreenhouse() {
        agenda.set(true)
        readyNode("finance-sync").performClick()
        val home = rule.onNodeWithTag("finance-home-list")
        // Cards appear once the device cache has been read, so wait for the first one.
        rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasTestTag("worker-greenhouse-$gh")) }.isSuccess }
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText("1 dari 1 tugas").fetchSemanticsNodes().isNotEmpty() ||
                run { runCatching { rule.onNodeWithTag("finance-sync").performClick() }; false }
        }
        readyText("Nutrisi belum dicek").assertExists()
        rule.onAllNodesWithText("Pembaruan data").assertCountEquals(0)
        home.performScrollToIndex(0)
        captureSecondary("worker-home-cards")
        home.performScrollToNode(hasTestTag("worker-corrections-card"))
        readyText("1 catatan perlu diperbaiki").assertExists()
        readyNode("worker-corrections-card").performClick()
        readyNode("worker-corrections-list").assertIsDisplayed()
        captureSecondary("worker-corrections-list")
        Espresso.pressBack()
        readyNode("worker-corrections-card").assertExists()
    }

    @Test fun workerHomeCardShowsLastMeasurementAndSaveMessageAfterRecording() {
        offline.set(true)
        rule.openRecord("activity")
        readyNode("entry-age-basis-option-hst").performScrollTo().performClick();readyNode("entry-age-day").performScrollTo().performTextReplacement("30");hideKeyboard()
        readyNode("entry-ec").performScrollTo().performTextReplacement("1,25")
        readyNode("entry-ppm").performScrollTo().performTextReplacement("625,5")
        hideKeyboard()
        rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick()
        readyNode("finance-queue").scrollIfScrollable().performClick()
        readyText("Tersimpan. Terkirim otomatis saat ada internet.").assertIsDisplayed()
        readyNode("manager-back").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitUntil(15_000) { runCatching { rule.onNodeWithTag("finance-home-list").performScrollToNode(hasTestTag("worker-greenhouse-$gh")) }.isSuccess }
        // The card now shows the check as a status chip; the EC and PPM values live on the greenhouse page.
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Nutrisi dicek", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("1 belum terkirim", substring = true).onFirst().assertExists()
        captureSecondary("worker-home-after-record")
    }

    @Test fun workerHistoryShowsCheckStatusFiltersToOwnRecordsAndPicksDates() {
        agenda.set(true)
        readyNode("finance-sync").performClick()
        rule.selectFinanceTab(1)
        val list = rule.onNodeWithTag("finance-history-list")
        fun listed(text: String) = runCatching { list.performScrollToNode(hasText(text)) }.isSuccess
        rule.waitUntil(15_000) { listed("Belum dicek") && listed("Perlu diperbaiki") }
        list.performScrollToIndex(0)
        captureSecondary("worker-history-rows")
        fun listed() = listed("Catatan orang lain sintetis")
        assertFalse(listed())
        list.performScrollToIndex(0)
        readyNode("history-chip-mine").performClick()
        rule.waitUntil(10_000) { listed() }
        list.performScrollToIndex(0)
        readyNode("history-chip-mine").performClick()
        rule.waitUntil(10_000) { !listed() }
        list.performScrollToNode(hasTestTag("finance-history-filters"))
        readyNode("finance-history-filters").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        captureSecondary("worker-history-filter-sheet")
        readyNode("history-from").performScrollTo().performClick()
        Espresso.onView(isAssignableFrom(android.widget.DatePicker::class.java)).check(matches(isDisplayed()))
        captureSecondary("worker-history-date-picker")
        Espresso.pressBack()
        rule.onNodeWithTag("history-from-clear").assertDoesNotExist()
    }

    @org.junit.Ignore("Pilihan jenis kegiatan dihapus (K4, 17 Nov 2026); EntryDraftTest dan entryFormSendsOneRecordPerSectionForBothRoles menggantikannya.")
    @Test fun workerActivityFormNeverOffersMeasurementAndScheduleLocksTheType() {
        chooseContext()
        captureSecondary("worker-form-activity")
        // The type is a row of chips now, so the options are on screen without opening anything.
        readyNode("finance-type")
        rule.onNodeWithTag("finance-type-option-nutrition").assertExists()
        rule.onAllNodesWithTag("finance-type-option-measurement").assertCountEquals(0)
        rule.onAllNodesWithText("Pengukuran manual").assertCountEquals(0)
        readyNode("finance-save-draft").scrollIfScrollable().assertTextContains("Simpan sebagai draf")
        Espresso.pressBack()
        rule.activityRule.scenario.recreate()
    }

    @Test fun workerScheduleEntryHasNoTypeChoice() {
        agenda.set(true)
        readyNode("finance-sync").performClick()
        val home = rule.onNodeWithTag("finance-home-list")
        rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasTestTag("worker-greenhouse-$gh")) }.isSuccess }
        readyNode("worker-greenhouse-$gh").performClick()
        // The first sync can finish before the schedule exists on the server, so ask again until it arrives.
        rule.waitUntil(30_000) {
            runCatching { home.performScrollToNode(hasTestTag("worker-schedule-$agendaSchedule")) }.isSuccess ||
                run { runCatching { rule.onNodeWithTag("finance-sync").performClick() }; false }
        }
        readyNode("worker-schedule-$agendaSchedule").performScrollTo().performClick()
        readyNode("finance-pick-date").assertExists()
        rule.onNodeWithTag("finance-type").assertDoesNotExist()
    }

    @Test fun workerCorrectionHasNoReferenceChoiceAndUsesPlainButtons() {
        createAccepted("Catatan yang akan dikoreksi")
        // The save message sits over the bottom of the detail for a few seconds and would take the tap.
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Tersimpan. Terkirim otomatis saat ada internet.").fetchSemanticsNodes().isEmpty() }
        readyNode("finance-edit").performScrollTo().performClick()
        captureSecondary("worker-form-correction")
        rule.onNodeWithTag("finance-reference-policy").assertDoesNotExist()
        rule.onNodeWithTag("finance-type").assertDoesNotExist()
        readyNode("finance-save-draft").scrollIfScrollable().assertTextContains("Simpan sebagai draf")
        readyNode("finance-reason").performScrollTo().performTextReplacement("Salah ketik keterangan")
        hideKeyboard()
        rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick()
        readyNode("finance-queue").scrollIfScrollable().assertTextContains("Kirim")
        rule.onAllNodesWithText("Acuan", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("versi", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test fun nutritionRunSavesOneMeasurementPerGreenhouseSkipsOneAndSendsSeparately() {
        secondGreenhouse.set(true)
        readyNode("finance-sync").performClick()
        val home = rule.onNodeWithTag("finance-home-list")
        rule.waitUntil(30_000) {
            runCatching { home.performScrollToNode(hasTestTag("worker-greenhouse-$gh2")) }.isSuccess ||
                run { runCatching { rule.onNodeWithTag("finance-sync").performClick() }; false }
        }
        readyNode("finance-record-fab").performClick()
        captureSecondary("worker-nutrition-run-sheet")
        readyNode("record-nutrition-run").performScrollTo().performClick()
        // Greenhouses are listed by name, so the first step is "GH UI sintetis". Each step is the greenhouse entry form.
        readyNode("entry-age-basis-option-hst").performScrollTo().performClick()
        readyNode("entry-age-day").performScrollTo().performTextReplacement("30");hideKeyboard()
        readyNode("entry-ec").performScrollTo().performTextReplacement("2,4")
        readyNode("entry-ppm").performScrollTo().performTextReplacement("1250")
        hideKeyboard()
        captureSecondary("worker-nutrition-run-step1")
        rule.onNodeWithTag("nutrition-run-next").assertTextContains("Simpan dan lanjut ke Greenhouse kedua sintetis")
        rule.onNodeWithTag("nutrition-run-next").scrollIfScrollable().performClick()
        // Second greenhouse: skip it.
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nutrition-run-skip").fetchSemanticsNodes().isNotEmpty() &&
            rule.onAllNodesWithText("Greenhouse 2 dari 2").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("nutrition-run-next").assertTextContains("Simpan dan periksa semua")
        captureSecondary("worker-nutrition-run-step2")
        rule.onNodeWithTag("nutrition-run-skip").scrollIfScrollable().performClick()
        readyNode("nutrition-run-send").assertIsDisplayed()
        rule.onAllNodesWithTag("nutrition-run-item").assertCountEquals(1)
        readyText("Greenhouse kedua sintetis dilewati.").assertExists()
        captureSecondary("worker-nutrition-run-review")
        // The run and the saved draft survive the activity being recreated.
        rule.activityRule.scenario.recreate()
        readyNode("nutrition-run-send").assertIsDisplayed()
        rule.onAllNodesWithTag("nutrition-run-item").assertCountEquals(1)
        readyNode("nutrition-run-send").performClick()
        rule.waitUntil(20_000) { posts.get() == 1 }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nutrition-run-send").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun workerAgendaDistinguishesRealizationsAndSkippedRecords() {
        agenda.set(true)
        readyNode("finance-sync").performClick()
        val home = rule.onNodeWithTag("finance-home-list")
        rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasTestTag("worker-greenhouse-$gh")) }.isSuccess }
        readyNode("worker-greenhouse-$gh").performClick()
        // The agenda card appears once the device snapshot has been read back ("Memeriksa catatan tersimpan…" first).
        rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasText("Jadwal hari ini")) }.isSuccess }
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText("1 dilewati", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                run { runCatching { rule.onNodeWithTag("finance-sync").performClick() }; false }
        }
        captureWindow("worker-agenda-filled")
        readyNode("worker-schedule-$agendaSchedule").performScrollTo().performClick()
        readyNode("finance-pick-date").assertExists()
    }

    private fun login(email:String, waitForSync: Boolean = true) {
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        rule.configureFinanceEndpoint(server.url("/api/v2/").toString())
        rule.onNodeWithTag("finance-email").performScrollTo().performTextReplacement(email)
        rule.onNodeWithTag("finance-password").performScrollTo().performTextReplacement("synthetic-ui-password")
        hideKeyboard();rule.onNodeWithTag("finance-login").performScrollTo().performClick()
        val managerLogin = email.startsWith("manager")
        if (managerLogin) readyListNode("manager-add-greenhouse") else readyNode("worker-home-summary")
        if (waitForSync) {
            if (managerLogin) {
                rule.waitUntil(10_000) { runtime().store.lastSyncedAt(runtime().vault.current!!.scope.key) != null }
                readyListNode("manager-greenhouse-$gh")
                return
            }
            rule.waitUntil(10_000) { runtime().store.lastSyncedAt(runtime().vault.current!!.scope.key) != null }
            rule.onNodeWithTag("finance-home-list").performScrollToIndex(0)
        }
    }
    private fun chooseContext(harvest:Boolean=false) {
        if (runtime().vault.current?.role == com.inofarm.jogja.core.contracts.UserRole.MANAGER) selectManagerGreenhouse()
        else { choose("finance-gh",gh);choose("finance-season",season);choose("finance-group",group) }
        rule.openRecord(if(harvest) "harvest" else "activity")
        rule.waitUntil(10000) {rule.onAllNodesWithTag(if(harvest) "harvest-notes" else "entry-notes").fetchSemanticsNodes().isNotEmpty()}
        // New activity records come from the greenhouse entry form (17 Nov 2026), which needs the typed plant age.
        if(!harvest) {readyNode("entry-age-basis-option-hst").performScrollTo().performClick();readyNode("entry-age-day").performScrollTo().performTextReplacement("30");hideKeyboard()}
    }
    private fun selectManagerGreenhouse(id: String = gh) {
        rule.waitUntil(15_000) {
            runCatching { rule.onNodeWithTag("finance-home-list").performScrollToNode(hasTestTag("manager-greenhouse-$id")) }.isSuccess
        }
        readyNode("manager-greenhouse-$id").performClick()
        readyNode("manager-back")
    }
    private fun readyText(text:String,substring:Boolean=false):SemanticsNodeInteraction {
        rule.waitUntil(10000) {rule.onAllNodesWithText(text,substring=substring).fetchSemanticsNodes().size==1}
        return rule.onNodeWithText(text,substring=substring)
    }
    private fun awaitRecord(tag:String) {
        rule.waitUntil(10000) {
            runCatching {rule.onNodeWithTag("finance-history-list").performScrollToNode(hasTestTag(tag))}.isSuccess
        }
    }
    /** The Beranda list is lazy, so a node below the fold is composed only after scrolling to it. */
    private fun readyListNode(tag:String):SemanticsNodeInteraction {
        rule.waitUntil(15000) {runCatching {rule.onNodeWithTag("finance-home-list").performScrollToNode(hasTestTag(tag))}.isSuccess}
        return readyNode(tag)
    }
    private fun readyNode(tag:String):SemanticsNodeInteraction {
        rule.waitUntil(10000) {rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size==1}
        return rule.onNodeWithTag(tag)
    }
    private fun choose(tag:String,id:String,keepContextOpen:Boolean=false) {
        // Recording asks only for a greenhouse (17 Nov 2026); season and planting are resolved, not chosen.
        if(tag=="finance-season" || tag=="finance-group") return
        rule.prepareChoice(tag)
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        val option="$tag-option-$id"
        rule.waitUntil(10000) {rule.onAllNodesWithTag(option).fetchSemanticsNodes().size==1}
        rule.onNodeWithTag(option).performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag(option).fetchSemanticsNodes().let {nodes -> nodes.isEmpty() || nodes.all {node -> (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected) && node.config[androidx.compose.ui.semantics.SemanticsProperties.Selected])}}}
        if(!keepContextOpen) rule.finishChoice(tag)
    }
    private fun hideKeyboard() {
        rule.runOnUiThread {
            val root = focusedWindow()
            val input = root.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            input.hideSoftInputFromWindow(root.windowToken, 0)
        }
        rule.waitUntil(10000) {
            var hidden = false
            rule.runOnUiThread {
                hidden = androidx.core.view.ViewCompat.getRootWindowInsets(focusedWindow())
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true
            }
            hidden
        }
        rule.waitForIdle()
    }

    private fun focusedWindow() = android.view.inspector.WindowInspector.getGlobalWindowViews()
        .lastOrNull { it.hasWindowFocus() } ?: rule.activity.window.decorView

    private fun frontendEvidence(name: String) = File(botanicalEvidenceDirectory(rule.activity), name)

    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        frontendEvidence("$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun captureWindow(name: String) {
        rule.captureBotanicalWindow(name)
    }

    private fun captureSecondary(name: String) {
        if (InstrumentationRegistry.getArguments().getString("secondaryEvidence") == "true") {
            captureWindow("secondary-$name")
        }
    }

    private fun deviceShell(command: String): String {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor = automation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun gfxInfo(arguments: String) = deviceShell("dumpsys gfxinfo ${rule.activity.packageName} $arguments")

    @Test fun twoFieldLoginHasAccessiblePasswordToggleAndHandlesMissingConfiguration() {
        rule.logoutFromAccount()
        readyNode("finance-login").assertIsNotEnabled()
        capture("login")
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        rule.onNodeWithTag("finance-server").assertDoesNotExist()
        val toggle = readyNode("finance-password-toggle").performScrollTo()
        toggle.assert(hasContentDescription("Tampilkan password")).performClick()
        toggle.assert(hasContentDescription("Sembunyikan password")).performClick()
        readyNode("finance-email").performScrollTo().performClick().performImeAction()
        readyNode("finance-password").assertIsFocused()
        hideKeyboard()
        rule.configureFinanceEndpoint("")
        readyText("Alamat aplikasi belum diatur. Hubungi pengelola.").assertIsDisplayed()
        readyNode("finance-login").assertIsNotEnabled()
        rule.configureFinanceEndpoint(server.url("/api/v2/").toString())
        val emailField = readyNode("finance-email").performScrollTo().performClick()
        emailField.performTextReplacement("worker@example.invalid")
        emailField.performImeAction()
        readyNode("finance-password").assertIsFocused().performTextReplacement("synthetic-ui-password")
        rule.waitUntil(10_000) {
            var visible = false
            rule.runOnUiThread {
                visible = androidx.core.view.ViewCompat.getRootWindowInsets(focusedWindow())
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            }
            visible
        }
        readyNode("finance-login").performScrollTo().assertIsDisplayed().assertIsEnabled()
        captureWindow("login-ime")
        hideKeyboard()
        readyNode("finance-login").performScrollTo()
        captureWindow("login-ready")
        readyNode("finance-password").performScrollTo().performClick().performImeAction()
        readyNode("worker-home-summary").assertIsDisplayed()
        offline.set(true)
        rule.logoutFromAccount()
        readyText("Akses akun belum dipastikan tercabut karena tidak ada koneksi.", true).assertIsDisplayed()
    }

    @Test fun managerLoginShowsRequestErrorAndAcceptsOneRetry() {
        rule.logoutFromAccount()
        readyNode("finance-email").performScrollTo().performTextReplacement("manager@example.invalid")
        readyNode("finance-password").performScrollTo().performTextReplacement("synthetic-ui-password")
        hideKeyboard()
        val submit = readyNode("finance-login").performScrollTo()
        captureWindow("manager-login-ready")
        failedLogin.set(true)
        submit.performClick()
        submit.assertIsNotEnabled().performTouchInput { click() }
        readyText("Login uji ditolak. Coba masuk kembali.").assertIsDisplayed()
        assertEquals(1, managerLogins.get())
        readyNode("finance-email").assertTextContains("manager@example.invalid")
        captureWindow("manager-login-error")
        submit.performScrollTo().performClick()
        readyListNode("manager-add-greenhouse").assertIsDisplayed()
        assertEquals(2, managerLogins.get())
        captureWindow("manager-login-success")
    }

    @Test fun navigationBackdropScrollFrameSample() {
        choose("finance-gh", gh)
        choose("finance-season", season)
        choose("finance-group", group)
        val home = rule.onNodeWithTag("finance-home-list")
        home.performScrollToNode(hasTestTag("measurement-card-ec"))
        rule.waitForIdle()
        val bounds = home.fetchSemanticsNode().boundsInWindow
        val x = bounds.center.x.toInt()
        val upper = (bounds.top + bounds.height * .25f).toInt()
        val lower = (bounds.top + bounds.height * .65f).toInt()
        gfxInfo("reset")
        repeat(3) {
            deviceShell("input swipe $x $lower $x $upper 420")
            deviceShell("input swipe $x $upper $x $lower 420")
        }
        rule.waitForIdle()
        frontendEvidence("navigation-scroll-frames.txt").writeText(gfxInfo("framestats"))
    }

    @Test fun longContextAndExpiredSessionKeepNavigationReachable() {
        longNames.set(true)
        rule.logoutFromAccount()
        login("manager@example.invalid")
        selectManagerGreenhouse()
        readyText(longGreenhouseName).assertIsDisplayed()
        capture("manager-long-context")

        rule.logoutFromAccount()
        expiredLogin.set(true)
        login("manager@example.invalid", waitForSync = false)
        val expiredMessage = "Sesi berakhir. Masuk kembali untuk melihat ringkasan."
        rule.onNodeWithTag("finance-home-list").performScrollToNode(hasText(expiredMessage))
        readyText(expiredMessage).assertIsDisplayed()
        rule.openFinanceOutbox()
        readyNode("finance-queue-list").assertIsDisplayed()
        capture("manager-expired-session")
        if (rule.activity.resources.configuration.fontScale > 1.5f) {
            try {
                rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                readyNode("finance-queue-list").assertIsDisplayed()
                readyNode("finance-account").assertIsDisplayed()
                captureWindow("manager-expired-landscape")
            } finally {
                rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            }
        }
    }

    /** F0: light is the default whatever the system theme; the avatar menu switches both roles and the choice survives logout. */
    @Test fun themeMenuSwitchesLightAndDarkForBothRoles() {
        val theme = (rule.activity.application as InofarmApplication).theme
        rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        try {
            captureInternal("theme-worker-light")
            rule.onNodeWithTag("finance-account").performClick()
            readyNode("theme-light").assertIsSelected()
            rule.onNodeWithTag("theme-dark").assertIsNotSelected()
            captureInternal("theme-worker-menu")
            rule.onNodeWithTag("theme-dark").performClick()
            rule.onNodeWithTag("theme-dark").assertIsSelected()
            assertEquals(com.inofarm.jogja.core.design.ThemeMode.DARK, theme.mode.value)
            Espresso.pressBack()
            captureInternal("theme-worker-dark")
            managerLogin()
            assertEquals(com.inofarm.jogja.core.design.ThemeMode.DARK, theme.mode.value)
            captureInternal("theme-manager-dark")
            rule.onNodeWithTag("finance-account").performClick()
            readyNode("theme-dark").assertIsSelected()
            rule.onNodeWithTag("theme-light").performClick()
            rule.onNodeWithTag("theme-light").assertIsSelected()
            Espresso.pressBack()
            captureInternal("theme-manager-light")
        } finally {
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        }
    }

    /** Internal storage, so `adb exec-out run-as ... cat files/evidence/<name>.png` can read it on Android 11. */
    private fun captureInternal(name: String) {
        rule.waitForIdle()
        Thread.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(File(rule.activity.filesDir, "evidence").apply { mkdirs() }, "$name.png").outputStream()
            .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun glassFrontendCapturesWorkerManagerAndPreservesDraftNavigation() {
        rule.onNodeWithTag("finance-tab-2").assertDoesNotExist()
        rule.onNodeWithTag("finance-account").performClick()
        rule.onNodeWithTag("finance-tab-3").assertDoesNotExist()
        Espresso.pressBack()
        capture("worker-home")
        gfxInfo("reset")
        // The context sheet opens from a greenhouse card; only the greenhouse is chosen and its season and variety are shown.
        rule.openHomeContext()
        rule.onNodeWithTag("finance-season").assertDoesNotExist()
        choose("finance-gh", gh)
        rule.openHomeContext()
        rule.onNodeWithTag("finance-context-target").assertTextContains("Musim UI sintetis", substring = true)
        rule.onNodeWithTag("finance-group").assertDoesNotExist()
        rule.onNodeWithTag("finance-context-close").performScrollTo().performClick()
        rule.onNodeWithTag("finance-home-list").performScrollToNode(hasTestTag("measurement-card-ec"))
        if (rule.activity.resources.configuration.fontScale > 1.3f) {
            rule.onNodeWithTag("finance-home-list").performTouchInput {
                swipeUp(startY = height * .65f, endY = height * .2f, durationMillis = 400)
            }
        }
        capture("worker-measurements")
        rule.onNodeWithTag("finance-home-list").performTouchInput { swipeUp() }
        rule.openRecord("activity")
        capture("worker-form-start")
        rule.onNodeWithTag("entry-notes").performScrollTo().performTextReplacement("Draf verifikasi tampilan botani")
        hideKeyboard();capture("worker-form")
        rule.onNodeWithTag("finance-save-draft").scrollIfScrollable().performClick()
        rule.selectFinanceTab(1);capture("worker-history")
        rule.logoutFromAccount();login("manager@example.invalid")
        capture("manager-home")
        selectManagerGreenhouse()
        capture("manager-home-selected")
        rule.onNodeWithTag("finance-account").performClick()
        rule.onNodeWithTag("finance-tab-3").assertExists()
        Espresso.pressBack()
        rule.selectFinanceTab(2);choose("expense-gh",gh);capture("manager-finance")
        rule.openFinanceTools()
        readyText("Catat dan alokasi").assertExists()
        readyNode("sale-new").performScrollTo().assertIsEnabled().assertIsDisplayed()
        captureWindow("manager-finance-tools")
        rule.onNodeWithTag("finance-tools-close").performScrollTo().performClick()
        readyNode("expense-new").performScrollTo().performClick()
        readyNode("expense-amount").performTextReplacement("1234567890,50")
        hideKeyboard();capture("manager-payment")
        frontendEvidence("frame-timing.txt").writeText(gfxInfo("framestats"))
        rule.activityRule.scenario.recreate()
        // The field shows thousands separators; the review below proves the stored value is the one typed.
        readyNode("expense-amount").performScrollTo().assertTextContains("1.234.567.890,50")
        choose("expense-category", category)
        choose("expense-payment-kind", "operating")
        readyNode("expense-notes").performScrollTo().performTextReplacement("Nominal panjang sintetis untuk tinjauan")
        hideKeyboard()
        readyNode("expense-review").scrollIfScrollable().performClick()
        readyNode("expense-amount-fact").performScrollTo()
        readyText("1.234.567.890,50", substring = true).assertIsDisplayed()
        capture("manager-payment-review")
        Espresso.pressBack()
        // The field shows thousands separators; the review below proves the stored value is the one typed.
        readyNode("expense-amount").performScrollTo().assertTextContains("1.234.567.890,50")
    }

    @Test fun defaultLauncherOpensFinanceWithExistingApplicationIdentity() {
        assertEquals("com.inofarm.jogja.finance",rule.activity.packageName)
        assertEquals(MainActivity::class.java.name,rule.activity.packageManager.getLaunchIntentForPackage(rule.activity.packageName)!!.component!!.className)
        rule.onNodeWithTag("worker-home-summary").assertExists()
        rule.onNodeWithTag("finance-tab-1").assertExists()
    }

    @Test fun compactNavigationKeepsEveryTabReachableAtLargeFont() {
        fun useCompactLayout() {
            val model = runtime()
            rule.runOnUiThread {
                rule.activity.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                        InofarmTheme {
                            Box(Modifier.width(320.dp).fillMaxHeight()) { FinanceRoot(model) }
                        }
                    }
                }
            }
            readyNode("finance-tab-0").assertIsDisplayed()
        }
        useCompactLayout()
        rule.onNodeWithTag("finance-tab-0").assertIsSelected()
        rule.onNodeWithTag("finance-tab-1").assertIsDisplayed()
        rule.onNodeWithTag("finance-tab-2").assertDoesNotExist()
        rule.onNodeWithTag("finance-record-fab").assertIsDisplayed()
        rule.selectFinanceTab(1)
        readyNode("finance-history-list").assertIsDisplayed()
        rule.onNodeWithTag("finance-tab-1").assertIsSelected()
        rule.openWorkerHarvestHistory()
        readyNode("finance-history-list").assertIsDisplayed()
        rule.selectFinanceTab(0)
        readyNode("worker-home-summary").assertIsDisplayed()

        rule.logoutFromAccount()
        login("manager@example.invalid")
        for (index in 0..3) readyNode("finance-tab-$index").assertIsDisplayed()
        rule.onNodeWithTag("finance-tab-4").assertDoesNotExist()
        captureWindow("manager-compact-navigation")
        readyListNode("manager-add-greenhouse").assertIsDisplayed()
    }

    @Test fun twoManualCardsKeepLegacyEcUnavailableAtNarrowLargeFont() {
        val today=java.time.LocalDate.now(com.inofarm.jogja.feature.activities.domain.ActivityDraft.WIB)
        val pair=com.inofarm.jogja.feature.activities.domain.ManualMeasurement(UUID.randomUUID().toString(),java.time.Instant.now().minusSeconds(172800),null,"625.5","Pencatat sintetis",false)
        rule.runOnUiThread {rule.activity.setContent {InofarmTheme {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {
            androidx.compose.foundation.layout.Column(Modifier.width(280.dp).safeDrawingPadding().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.inofarm.jogja.feature.activities.MeasurementCards(pair)
                com.inofarm.jogja.feature.activities.MeasurementSource(pair,today,false,null,null)
            }
        }}}}
        rule.onNodeWithTag("measurement-card-ec").assertExists();rule.onNodeWithTag("measurement-card-ppm").assertExists()
        rule.onNodeWithText("EC belum tersedia").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("625.5").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("pH").assertDoesNotExist()
        rule.onNodeWithText("Belum ada pengukuran hari ini.",substring=true).performScrollTo().assertIsDisplayed()
    }

    @Test fun manualMeasurementAutosaveBackRecreationQueueAndDashboardKeepOnePair() {
        choose("finance-gh",gh);choose("finance-season",season);choose("finance-group",group)
        // Readings are a section of the greenhouse entry form (F4), with the typed plant age.
        rule.openRecord("activity")
        readyNode("entry-age-basis-option-hst").performScrollTo().performClick();readyNode("entry-age-day").performScrollTo().performTextReplacement("30");hideKeyboard()
        rule.onNodeWithTag("entry-ec").performScrollTo().performTextReplacement("1,25")
        rule.onNodeWithTag("entry-ppm").performScrollTo().performTextReplacement("625,5")
        hideKeyboard();rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("entry-ec").performScrollTo().assertTextContains("1,25")
        Espresso.pressBack()
        rule.selectFinanceTab(1)
        readyText("Draf kegiatan",true).performScrollTo().performClick()
        readyNode("finance-edit").performScrollTo().performClick()
        offline.set(true)
        rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick()
        readyNode("finance-queue").scrollIfScrollable().performClick()
        rule.selectFinanceTab(0)
        choose("finance-gh",gh);choose("finance-season",season);choose("finance-group",group)
        rule.onNodeWithTag("finance-home-list").performScrollToNode(hasTestTag("measurement-card-ec"))
        rule.onNodeWithTag("measurement-card-ec").performScrollTo().assertExists()
        rule.onNodeWithText("1,25").assertExists();rule.onNodeWithText("625,5").assertExists()
        capture("worker-measurement-pair-queued")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Tersimpan. Terkirim otomatis saat ada internet.").fetchSemanticsNodes().isEmpty() }
        // The Catat button floats over the list, so a coordinate click at the scrolled edge can land on it.
        rule.onNodeWithTag("measurement-source").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyText("Belum terkirim · pencatat:",true).performScrollTo().assertExists()
        assertEquals(0,posts.get())
        offline.set(false);readyNode("finance-sync").performClick()
        rule.waitUntil(15000) {posts.get()==1}
        readyText("Terkirim · pencatat:",true).performScrollTo().assertExists()
        rule.onNodeWithTag("measurement-source").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.onNodeWithTag("finance-home-list").performScrollToIndex(0)
        capture("worker-measurement-pair-accepted")
        val payload=receipts.values.single().getJSONObject("data")
        assertEquals("1.25",payload.getString("manual_ec"));assertEquals("625.5",payload.getString("manual_ppm"));assertEquals(0,payload.getJSONArray("materials").length())
    }

    @Test fun offlineQueueRecreationReconnectAndManagerHistoryKeepHonestStatus() {
        chooseContext();offline.set(true)
        rule.onNodeWithTag("entry-notes").performScrollTo().performTextReplacement("Catatan offline UI sintetis")
        hideKeyboard()
        captureSecondary("activity-form")
        rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick()
        captureSecondary("activity-review")
        readyNode("finance-queue").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-1").fetchSemanticsNodes().isNotEmpty()}
        rule.openFinanceOutbox()
        readyNode("queue-send-now").assertIsDisplayed()
        rule.onAllNodesWithText("ID kiriman").assertCountEquals(0)
        captureSecondary("queue-waiting")
        Espresso.pressBack()
        rule.onNodeWithTag("finance-tab-1").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Belum terkirim").fetchSemanticsNodes().isNotEmpty()}
        captureSecondary("activities")
        assertEquals(0,posts.get())
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("finance-tab-1").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Catatan offline UI sintetis").fetchSemanticsNodes().isNotEmpty()}
        offline.set(false)
        rule.waitUntil(10000) {rule.onNodeWithTag("finance-sync").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}
        rule.onNodeWithTag("finance-sync").performClick()
        rule.waitUntil(10000) {posts.get()==1}
        rule.waitUntil(10000) {rule.onAllNodesWithText("Terkirim", substring = true).fetchSemanticsNodes().isNotEmpty()}
        assertEquals(1,posts.get())
        rule.logoutFromAccount();login("manager@example.invalid")
        rule.onNodeWithTag("finance-tab-1").performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Catatan offline UI sintetis").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(worker,receipts.values.single().getJSONObject("data").getString("recorded_by"))
    }
    @org.junit.Ignore("Jenis nutrisi tunggal dengan PPM/pH wajib diganti bagian Nutrisi pada form gabungan (K4, 17 Nov 2026).")
    @Test fun multiMaterialDraftRotationAndBackRetainRawFieldsBeforeReview() {
        chooseContext();choose("finance-type","nutrition")
        rule.onNodeWithTag("finance-ppm").assertTextContains("")
        rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick()
        rule.onAllNodesWithText("Isi PPM manual.").onFirst().assertExists()
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
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-tab-1").performClick();rule.onNodeWithText("Draf dua bahan tetap ada").performScrollTo().performClick()
        readyNode("finance-edit").performScrollTo().performClick()
        rule.onNodeWithTag("finance-ph").performScrollTo().assertTextContains("6,1")
        hideKeyboard();rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick();readyNode("finance-queue").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {posts.get()==1}
        val body=receipts.values.single().getJSONObject("data");assertEquals("6.1",body.getString("manual_ph"));assertEquals(2,body.getJSONArray("materials").length())
    }
    private fun createAccepted(note:String):String {
        chooseContext();rule.onNodeWithTag("entry-notes").performScrollTo().performTextReplacement(note);hideKeyboard()
        rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick();readyNode("finance-queue").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {posts.get()==1}
        rule.selectFinanceTab(1)
        val id=receipts.values.single().getString("entity_id")
        awaitRecord("finance-record-$id")
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-edit").fetchSemanticsNodes().isNotEmpty()}
        return id
    }
    private fun attachmentDraft():Pair<com.inofarm.jogja.feature.attachments.AttachmentViewModel,ByteArray> {
        val id=createAccepted("Foto kegiatan sintetis")
        readyNode("history-attachments").performScrollTo().performClick()
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
        captureSecondary("attachment-detail")
        rule.onNodeWithTag("attachment-review").performScrollTo().performClick()
        readyNode("attachment-queue").performScrollTo().assertIsNotEnabled()
        captureSecondary("attachment-review")
        assertTrue(runtime().store.outbox(runtime().vault.current!!.scope.key).none {it.entityId==vm.state.value.selected!!.id})
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("attachment-queue").fetchSemanticsNodes().isNotEmpty()}
        readyNode("attachment-queue").performScrollTo().assertIsNotEnabled()
        rule.logoutFromAccount();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
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
        rule.logoutFromAccount();rule.waitUntil(10000) {model.vault.current==null}
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
        hideKeyboard();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty()};rule.logoutFromAccount();rule.waitUntil(10000) {model.vault.current==null}
        assertEquals("Alasan terbaru — belum dibatalkan",model.store.attachmentForm(scope,row.id)!!.getString("reason"))
        assertEquals(com.inofarm.jogja.core.contracts.SyncState.ACCEPTED,model.store.record(scope,row.id)!!.syncState)
        assertTrue(model.store.outbox(scope).filter {it.entityId==row.id}.all {it.state=="ACCEPTED"})
    }
    @Test fun attachmentReasonFailedFlushRetainsSessionAndTextThroughRecreation() {
        acceptedAttachmentReason();val model=runtime();val session=model.vault.current!!;val previousLogoutPosts=logoutPosts.get()
        val trigger="finance_test_reason_"+UUID.randomUUID().toString().replace("-","")
        fun sql(statement:String) {android.database.sqlite.SQLiteDatabase.openDatabase(rule.activity.getDatabasePath("inofarm_finance_v2.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {it.execSQL(statement)}}
        assertTrue(rule.activity.packageName.endsWith(".finance") || rule.activity.packageName.endsWith(".finance.performance"))
        try {
            sql("CREATE TRIGGER $trigger BEFORE INSERT ON local_attachment_forms WHEN NEW.scope='${session.scope.key}' BEGIN SELECT RAISE(ABORT,'Synthetic form write failure'); END")
            rule.onNodeWithTag("attachment-reason").performScrollTo().performTextReplacement("Alasan yang wajib tetap ada")
            hideKeyboard();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty()};rule.logoutFromAccount()
            rule.waitUntil(10000) {rule.onAllNodesWithText("Belum keluar").fetchSemanticsNodes().isNotEmpty()}
            assertNotNull(model.vault.current);assertEquals(previousLogoutPosts,logoutPosts.get())
            rule.onNodeWithText("Kembali ke isian").performClick();rule.activityRule.scenario.recreate()
            rule.onNodeWithTag("attachment-reason").performScrollTo().assertTextContains("Alasan yang wajib tetap ada")
        } finally {sql("DROP TRIGGER IF EXISTS $trigger")}
        hideKeyboard();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-account").fetchSemanticsNodes().isNotEmpty()};rule.logoutFromAccount();rule.waitUntil(10000) {model.vault.current==null}
    }
    @Test fun attachmentReviewRemainsReachableAtLargeFontNarrowAndLandscape() {
        attachmentDraft();val model=runtime()
        rule.activity.setContent {InofarmTheme {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {Box(Modifier.widthIn(max=320.dp)) {FinanceRoot(model)}}}}
        rule.onNodeWithTag("attachment-review").performScrollTo().performClick();readyNode("attachment-queue").performScrollTo().assertIsNotEnabled()
        rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        rule.activity.setContent {InofarmTheme {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {Box(Modifier.widthIn(max=320.dp)) {FinanceRoot(model)}}}}
        readyNode("attachment-queue").performScrollTo().assertIsNotEnabled()
        rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    private fun makeConflict() {
        conflictNext.set(true)
        readyNode("finance-edit").performScrollTo().performClick()
        rule.onNodeWithTag("finance-notes").performScrollTo().performTextReplacement("Koreksi lokal sintetis");hideKeyboard()
        rule.onNodeWithTag("finance-reason").performScrollTo().performTextReplacement("Perbaikan lokal");hideKeyboard()
        rule.onNodeWithTag("finance-review").scrollIfScrollable().performClick();readyNode("finance-queue").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {receipts.size==2}
        val id=receipts.values.first().getString("entity_id")
        awaitRecord("finance-record-$id")
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitUntil(10000) {rule.onAllNodesWithTag("history-compare").fetchSemanticsNodes().isNotEmpty()}
        readyNode("history-compare").performScrollTo().performClick()
        try {rule.waitUntil(10000) {rule.onAllNodesWithText("Data terbaru · versi 2").fetchSemanticsNodes().isNotEmpty()}}
        catch(error:AssertionError) {throw AssertionError(rule.onRoot().printToString(),error)}
        rule.onNodeWithText("Data terbaru · versi 2").performScrollTo().assertExists()
        val image=rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.getExternalFilesDir(null),"finance-conflict-review.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun conflictUseServerPreservesLocalCopyAndWorkerHasNoCancel() {
        createAccepted("Catatan awal konflik");rule.onNodeWithTag("history-cancel").assertDoesNotExist()
        captureSecondary("activity-detail")
        makeConflict()
        captureSecondary("activity-conflict")
        rule.onNodeWithTag("conflict-use-server").performScrollTo().performClick();rule.onNodeWithTag("conflict-confirm-server").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("history-archive-0").fetchSemanticsNodes().isNotEmpty()}
        readyNode("history-archive-0").performScrollTo().performClick()
        rule.onNodeWithText("Koreksi lokal sintetis").performScrollTo().assertExists()
        assertEquals(1,posts.get())
    }
    @Test fun queueReviewOpensConflictedRecordAndBackReturnsToSameQueue() {
        createAccepted("Catatan dari antrean")
        makeConflict()
        Espresso.pressBack()
        Espresso.pressBack()
        rule.openFinanceOutbox()

        val session = runtime().vault.current!!
        val operation = runtime().store.outbox(session.scope.key).single { it.state == "CONFLICT" }
        val queue = rule.onNodeWithTag("finance-queue-list")
        queue.performScrollToNode(hasTestTag("finance-review-${operation.id}"))
        captureSecondary("queue-attention")
        readyNode("queue-operation-${operation.id}").performClick()
        queue.performScrollToNode(hasText(operation.id))
        readyText(operation.id).assertIsDisplayed()

        queue.performScrollToNode(hasTestTag("finance-review-${operation.id}"))
        readyNode("finance-review-${operation.id}").performClick()
        readyNode("history-compare").assertExists()
        readyText("Koreksi lokal sintetis").assertExists()
        queue.assertDoesNotExist()
        Espresso.pressBack()
        queue.assertIsDisplayed()
        queue.performScrollToNode(hasText(operation.id))
        readyText(operation.id).assertIsDisplayed()
    }
    @Test fun conflictKeepLocalRequiresReasonAndManagerCanAuditAndCancel() {
        val id=createAccepted("Catatan awal penyelesaian");makeConflict()
        rule.onNodeWithTag("conflict-keep-local").performScrollTo().assertIsNotEnabled()
        readyNode("conflict-reason").performScrollTo().performTextReplacement("Isi lokal sudah ditinjau");hideKeyboard();choose("conflict-policy","preserve")
        rule.onNodeWithTag("conflict-keep-local").performScrollTo().performClick()
        rule.waitUntil(10000) {posts.get()==2}
        val latest=receipts.values.maxBy {it.getInt("version")};assertEquals(3,latest.getInt("version"));assertEquals("Koreksi lokal sintetis",latest.getJSONObject("data").getString("notes"))
        rule.logoutFromAccount();login("manager@example.invalid")
        rule.onNodeWithTag("finance-tab-1").performClick();awaitRecord("finance-record-$id")
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("history-audit").performScrollTo().performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Audit sintetis",substring=true).fetchSemanticsNodes().isNotEmpty()}
        offline.set(true);readyNode("history-audit").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Audit terbaru belum tersedia.",substring=true).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Audit terakhir tersimpan; pemeriksaan terbaru belum selesai atau koneksi terputus.").assertExists();offline.set(false)
        readyNode("history-cancel").performScrollTo().performClick()
        rule.onNodeWithTag("history-action-confirm").assertIsNotEnabled();rule.onNodeWithTag("history-action-reason").performTextReplacement("Pembatalan sintetis");hideKeyboard()
        rule.onNodeWithTag("history-action-confirm").performClick();rule.waitUntil(10000) {posts.get()==3}
        rule.waitUntil(10000) {rule.onAllNodesWithText("Dibatalkan").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun managerSeasonCloseAndReopenUseReasonedVersionedQueue() {
        designationFixture.set(false)
        rule.logoutFromAccount();login("manager@example.invalid");selectManagerGreenhouse()
        rule.onNodeWithTag("finance-home-list").performScrollToNode(hasTestTag("manager-period-history"))
        readyNode("manager-period-history").performClick()
        rule.onNodeWithTag("manager-period-list").performScrollToNode(hasTestTag("finance-season-transition"))
        rule.onNodeWithTag("finance-season-transition").performScrollTo().performClick()
        rule.onNodeWithTag("history-action-confirm").assertIsNotEnabled();rule.onNodeWithTag("history-action-reason").performTextReplacement("Penutupan sintetis");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(10000) {seasonState.get()=="closed"}
        rule.waitUntil(10000) {rule.onAllNodesWithText("Buka kembali musim").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-season-transition").performScrollTo().performClick()
        rule.onNodeWithTag("history-action-reason").performTextReplacement("Buka untuk koreksi");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(10000) {seasonState.get()=="open" && seasonVersion.get()==3}
    }

    @Test fun managerHomeOneTapFiltersRotationAndCachedSnapshot() {
        overviewFixture.set(true)
        longNames.set(true)
        managerLogin()
        val home = rule.onNodeWithTag("finance-home-list")
        readyText("Ringkasan kebun").assertIsDisplayed()
        rule.onNodeWithText("Pilih greenhouse").assertDoesNotExist()
        captureWindow("overview")
        home.performScrollToNode(hasTestTag("manager-greenhouse-${extraGreenhouses.last()}"))
        selectManagerGreenhouse(extraGreenhouses.last())
        home.performScrollToNode(hasText("Belum ada periode tanam aktif"))
        rule.onNodeWithTag("finance-new").assertDoesNotExist()
        captureWindow("no-active")
        home.performScrollToIndex(0)
        readyNode("manager-back").performClick()
        selectManagerGreenhouse(gh)
        home.performScrollToNode(hasTestTag("measurement-card-ec"))
        readyNode("measurement-card-ppm").assertExists()
        readyText("Belum dicek").assertExists()
        captureWindow("single-greenhouse")
        home.performScrollToNode(hasTestTag("measurement-source"))
        readyNode("measurement-source").performClick()
        captureWindow("measurement-source")
        home.performScrollToNode(hasTestTag("manager-home-finance"))
        captureWindow("cash")
        readyNode("manager-home-finance").performClick()
        readyNode("finance-history-list").assertIsDisplayed()
        val history = ViewModelProvider(rule.activity)["expense-history", com.inofarm.jogja.feature.history.HistoryViewModel::class.java]
        assertEquals(gh, history.state.value.filter.greenhouseId)
        assertEquals(java.time.YearMonth.now(com.inofarm.jogja.feature.activities.domain.ActivityDraft.WIB).atDay(1).toString(), history.state.value.filter.from)
        Espresso.pressBack()
        readyNode("manager-home-finance").assertExists()
        rule.activityRule.scenario.recreate()
        readyNode("manager-home-finance").assertIsDisplayed()
        home.performScrollToIndex(0)
        readyText(longGreenhouseName).assertIsDisplayed()
        val session = runtime().vault.current!!
        rule.waitUntil(10_000) {
            val month = java.time.YearMonth.now(com.inofarm.jogja.feature.activities.domain.ActivityDraft.WIB).toString()
            runtime().store.managerHomeCache(session.scope.key, gh, month) != null
        }
        offline.set(true)
        readyNode("finance-sync").performClick()
        rule.waitUntil(15_000) {
            runCatching { home.performScrollToNode(hasTestTag("manager-measurement-day-status")) }.isSuccess &&
                rule.onAllNodesWithText("Belum dapat memeriksa pencatatan hari ini").fetchSemanticsNodes().isNotEmpty()
        }
        readyText("Belum dapat memeriksa pencatatan hari ini").assertIsDisplayed()
        rule.onNodeWithText("Belum ada pencatatan hari ini").assertDoesNotExist()
        captureWindow("offline")
        offline.set(false)
        rule.logoutFromAccount()
        login("manager@example.invalid")
        readyText("Ringkasan kebun").assertIsDisplayed()
    }

    @Test fun managerHomeFallsBackWhenSelectedGreenhouseIsRevoked() {
        overviewFixture.set(true)
        managerLogin()
        val id = extraGreenhouses.first()
        selectManagerGreenhouse(id)
        readyText("Greenhouse sintetis 2").assertIsDisplayed()
        revokedGreenhouse = id
        readyNode("finance-sync").performClick()
        rule.waitUntil(15_000) {
            runCatching { rule.onNodeWithTag("finance-home-list").performScrollToNode(hasText("Ringkasan kebun")) }.isSuccess
        }
        readyText("Ringkasan kebun").assertIsDisplayed()
        rule.onNodeWithText("Greenhouse sintetis 2").assertDoesNotExist()
        rule.onNodeWithTag("manager-greenhouse-$id").assertDoesNotExist()
        rejectManagerSession.set(true)
        val sync = readyNode("finance-sync")
        rule.waitUntil(10_000) { runCatching { sync.assertIsEnabled() }.isSuccess }
        sync.performClick()
        readyNode("manager-home-relogin").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("finance-tab-2").assertDoesNotExist()
        rule.activityRule.scenario.recreate()
        readyNode("manager-home-relogin").performScrollTo().assertIsDisplayed()
        rule.logoutFromAccount()
    }

    @Test fun managerHomeLandscapeKeepsScopeAndFinancialLinkReachable() {
        longNames.set(true)
        managerLogin()
        selectManagerGreenhouse()
        val home = rule.onNodeWithTag("finance-home-list")
        try {
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            rule.waitUntil(15_000) { runCatching { home.performScrollToNode(hasTestTag("measurement-card-ec")) }.isSuccess }
            readyNode("measurement-card-ec").assertIsDisplayed()
            captureWindow("landscape-measurement")
            home.performScrollToNode(hasTestTag("manager-home-finance"))
            readyNode("manager-home-finance").assertIsDisplayed()
            captureWindow("landscape-cash")
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }

    @Test fun managerHomeLateResponseDoesNotReplaceNewSelection() {
        overviewFixture.set(true)
        managerLogin()
        val old = extraGreenhouses.first()
        delayedGreenhouse = old
        selectManagerGreenhouse(old)
        assertTrue(delayedScopeArrived.await(5, java.util.concurrent.TimeUnit.SECONDS))
        try {
            readyNode("manager-back").performClick()
            selectManagerGreenhouse()
            readyText("GH UI sintetis").assertIsDisplayed()
        } finally {
            releaseDelayedScope.countDown()
        }
        rule.onNodeWithTag("finance-home-list").performScrollToNode(hasTestTag("measurement-card-ec"))
        readyNode("measurement-card-ec").assertIsDisplayed()
        rule.onNodeWithText("Greenhouse sintetis 2").assertDoesNotExist()
    }
    @Test fun historyFilterShowsEmptyThenRestoresMatchingRecord() {
        val id=createAccepted("Catatan filter UI")
        Espresso.pressBack()
        rule.onNodeWithTag("finance-history-filters").performClick();choose("history-type","nutrition")
        captureSecondary("activities-filter")
        readyNode("history-apply").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Belum ada catatan yang cocok dengan filter ini.").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("finance-record-$id").assertDoesNotExist()
        rule.onNodeWithTag("finance-history-filters").performClick();rule.onNodeWithText("Hapus semua filter").performScrollTo().performClick()
        awaitRecord("finance-record-$id")
    }

    @Test fun secondaryMenusReflowAndKeepSheetActionsReachable() {
        val historyList = rule.onNodeWithTag("finance-history-list")
        rule.openFinanceOutbox()
        readyNode("finance-queue-list").assertIsDisplayed()
        captureSecondary("queue-responsive")
        rule.selectFinanceTab(1)
        historyList.assertIsDisplayed()
        captureSecondary("activities-responsive")
        historyList.performScrollToNode(hasTestTag("finance-history-filters"))
        readyNode("finance-history-filters").performClick()
        readyNode("history-apply").performScrollTo().assertIsDisplayed()
        captureSecondary("filter-responsive")
        Espresso.pressBack()
        rule.openWorkerHarvestHistory()
        historyList.assertIsDisplayed()
        captureSecondary("harvest-responsive")
        managerLogin()
        rule.selectFinanceTab(2)
        historyList.assertIsDisplayed()
        captureSecondary("finance-responsive")
        rule.openFinanceTools()
        readyText("Catat dan alokasi").assertExists()
        captureSecondary("tools-responsive")
        readyNode("investment-open").performScrollTo().assertIsDisplayed()
        Espresso.pressBack()
        historyList.assertIsDisplayed()
    }

    /** F5: a greenhouse with two varieties harvests both in one form, one harvest per variety; the fruit count may stay empty. */
    @Test fun harvestTwoVarietiesSideBySideSendsOneHarvestEach() {
        val theme = (rule.activity.application as InofarmApplication).theme
        secondVariety.set(true)
        rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        try {
            readyNode("finance-sync").performClick()
            val vm = ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.context.ContextViewModel::class.java]
            rule.waitUntil(30_000) { vm.state.value.groups.any { it.optString("id") == groupV2 } ||
                run { runCatching { rule.onNodeWithTag("finance-sync").performClick() }; false } }
            rule.onNodeWithTag("finance-home-list").performScrollToIndex(0)
            readyNode("finance-record-fab").performClick()
            readyNode("record-greenhouse-option-$gh").performScrollTo().performClick()
            readyNode("record-harvest").performClick()
            // Inofarm 20 Nov 2026: fixed Layak (0) and Cacat (1) lines per variety, no grade.
            rule.onNodeWithTag("harvest-grade-0").assertDoesNotExist()
            readyNode("harvest-weight-0").performScrollTo().performTextReplacement("15,5");hideKeyboard()
            readyNode("harvest-v2-weight-0").performScrollTo().performTextReplacement("8");hideKeyboard()
            assertTrue(rule.onAllNodesWithText("Varietas kedua sintetis", substring = true).fetchSemanticsNodes().isNotEmpty())
            readyNode("harvest-v2-weight-0").performScrollTo()
            captureInternal("harvest-two-light")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.DARK) }
            readyNode("harvest-v2-weight-0").performScrollTo()
            captureInternal("harvest-two-dark")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
            readyNode("harvest-review").performClick()
            readyNode("harvest-queue").assertExists()
            captureInternal("harvest-two-review")
            val before = harvestPosts.get()
            readyNode("harvest-queue").performClick()
            rule.waitUntil(20_000) { harvestPosts.get() - before == 2 }
            val sent = harvestReceipts.values.map { it.getJSONObject("data") }
            assertEquals(setOf(group, groupV2), sent.map { it.getString("planting_group_id") }.toSet())
            assertTrue(sent.all { it.getJSONArray("items").getJSONObject(0).isNull("fruit_count") })
            assertTrue(sent.all { it.getJSONArray("items").length() == 1 })
            assertEquals(1, sent.map { it.getString("occurred_at") }.toSet().size)
        } finally {
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        }
    }

    /** F4: one "Catat kegiatan" form per greenhouse, sent as one record per section with a shared batch, for both roles. */
    @Test fun entryFormSendsOneRecordPerSectionForBothRoles() {
        val theme = (rule.activity.application as InofarmApplication).theme
        managerWalkthroughFixture.set(true)
        rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        try {
            // Worker: Catat, the greenhouse, Kegiatan; no type choice, no season or group.
            rule.onNodeWithTag("finance-home-list").performScrollToIndex(0)
            readyNode("finance-record-fab").performClick()
            rule.onNodeWithTag("record-measurement").assertDoesNotExist()
            readyNode("record-greenhouse-option-$gh").performScrollTo().performClick()
            readyNode("record-activity").performClick()
            readyNode("entry-age-day").assertExists()
            rule.onNodeWithTag("finance-type").assertDoesNotExist()
            rule.onNodeWithTag("finance-skipped").assertDoesNotExist()
            captureInternal("entry-worker-empty-light")
            readyNode("finance-review").performClick()
            readyText("Isi sedikitnya satu bagian", substring = true).assertExists()
            readyNode("entry-age-basis-option-hst").performScrollTo().performClick()
            readyNode("entry-age-day").performScrollTo().performTextReplacement("30");hideKeyboard()
            readyNode("entry-ec").performScrollTo().performTextReplacement("1,8");hideKeyboard()
            // Inofarm 20 Nov 2026: EC alone is a complete reading; PPM is optional.
            readyNode("entry-ppm").performScrollTo().performTextReplacement("900");hideKeyboard()
            readyNode("entry-moment-option-after_injection").performScrollTo().performClick()
            readyNode("entry-spray-open").performScrollTo().performClick()
            readyNode("entry-spray-period-option-morning").performScrollTo().performClick()
            choose("entry-spray-material-0", "__typed")
            readyNode("entry-spray-name-0").performScrollTo().performTextReplacement("Bion M");hideKeyboard()
            readyNode("entry-stage").performScrollTo().performTextReplacement("Pembesaran buah");hideKeyboard()
            captureInternal("entry-worker-filled-light")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.DARK) }
            readyNode("entry-spray-name-0").performScrollTo()
            captureInternal("entry-worker-filled-dark")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
            readyNode("finance-review").performClick()
            readyNode("finance-queue").assertExists()
            captureInternal("entry-worker-review-light")
            val before = posts.get()
            readyNode("finance-queue").performClick()
            rule.waitUntil(15_000) { posts.get() - before == 3 }
            val sent = receipts.values.map { it.getJSONObject("data") }.filter { it.has("entry_batch_id") && !it.isNull("entry_batch_id") }
            assertEquals(setOf("measurement", "spray", "stage"), sent.map { it.getString("activity_type") }.toSet())
            assertEquals(1, sent.map { it.getString("entry_batch_id") }.toSet().size)
            assertTrue(sent.all { it.getString("season_id") == season && it.getString("age_basis") == "hst" && it.getInt("age_day") == 30 })
            assertTrue(sent.single { it.getString("activity_type") == "measurement" }.isNull("planting_group_id"))
            val spray = sent.single { it.getString("activity_type") == "spray" }
            assertEquals(group, spray.getString("planting_group_id"))
            assertEquals("morning", spray.getString("spray_period"))
            assertEquals("Bion M", spray.getJSONArray("materials").getJSONObject(0).getString("material_name"))

            // Manager: same form; Simpan opens the summary, and Batal from the form leaves nothing behind.
            managerLogin()
            readyNode("finance-record-fab").performClick()
            readyNode("record-greenhouse-option-$gh").performScrollTo().performClick()
            readyNode("record-activity").performClick()
            readyNode("entry-age-basis-option-hss").performScrollTo().performClick()
            readyNode("entry-age-day").performScrollTo().performTextReplacement("48");hideKeyboard()
            readyNode("entry-notes").performScrollTo().performTextReplacement("Cek talang");hideKeyboard()
            readyNode("finance-review").performClick()
            readyText("Ringkasan kegiatan", substring = true).assertExists()
            captureInternal("entry-manager-summary-light")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.DARK) }
            readyText("Ringkasan kegiatan", substring = true).assertExists()
            captureInternal("entry-manager-summary-dark")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
            readyNode("finance-back").performClick()
            readyNode("finance-cancel").performClick()
            runCatching { readyText("Buang").performClick() }
            val vm = ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.activities.ActivityViewModel::class.java]
            rule.waitUntil(10_000) { vm.state.value.entry == null }
            rule.waitUntil(10_000) { vm.state.value.records.none { com.inofarm.jogja.feature.activities.domain.EntryDraft.isEntry(it.body) } }
        } finally {
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        }
    }

    /** F3: Catat asks only for a greenhouse for both roles; an unplanted greenhouse offers "Mulai tanam" to the manager. */
    @Test fun catatAsksOnlyForTheGreenhouseForBothRoles() {
        val theme = (rule.activity.application as InofarmApplication).theme
        managerWalkthroughFixture.set(true)
        secondGreenhouse.set(true)
        unplantedGreenhouse.set(true)
        rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        try {
            // Worker: pick a greenhouse, read its season and planting, never choose season or group.
            readyNode("finance-sync").performClick()
            val home = rule.onNodeWithTag("finance-home-list")
            rule.waitUntil(30_000) {
                runCatching { home.performScrollToNode(hasTestTag("worker-greenhouse-$gh3")) }.isSuccess ||
                    run { runCatching { rule.onNodeWithTag("finance-sync").performClick() }; false }
            }
            rule.onNodeWithTag("finance-home-list").performScrollToIndex(0)
            readyNode("finance-record-fab").performClick()
            readyNode("record-greenhouse-option-$gh3").performScrollTo().performClick()
            readyNode("record-greenhouse-caption").assertTextContains("Minta pengelola", substring = true)
            rule.onNodeWithTag("record-start-planting").assertDoesNotExist()
            captureInternal("catat-worker-unplanted")
            readyNode("record-greenhouse-option-$gh2").performScrollTo().performClick()
            readyNode("record-greenhouse-caption").assertTextContains("Musim kedua sintetis", substring = true)
            captureInternal("catat-worker-ready")
            readyNode("record-activity").performClick()
            val worker = ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.activities.ActivityViewModel::class.java]
            // Kegiatan opens the greenhouse entry (F4): filed under the greenhouse's open season, no variety chosen.
            rule.waitUntil(10_000) { worker.state.value.entry != null }
            assertEquals(gh2, worker.state.value.entry!!.greenhouseId)
            assertEquals(season2, worker.state.value.entry!!.seasonId)
            rule.onNodeWithTag("finance-season").assertDoesNotExist()
            rule.runOnIdle { worker.discard() }

            // Manager: the same greenhouse choice, plus "Mulai tanam" for the unplanted greenhouse.
            managerLogin()
            readyNode("finance-record-fab").performClick()
            readyNode("record-greenhouse-option-$gh3").performScrollTo().performClick()
            readyNode("record-start-planting").assertIsDisplayed()
            captureInternal("catat-manager-unplanted-light")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.DARK) }
            readyNode("record-start-planting").assertIsDisplayed()
            captureInternal("catat-manager-unplanted-dark")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
            readyNode("record-greenhouse-option-$gh").performScrollTo().performClick()
            readyNode("record-greenhouse-caption").assertTextContains("Musim UI sintetis", substring = true)
            rule.onNodeWithTag("record-start-planting").assertDoesNotExist()
            readyNode("record-greenhouse-option-$gh3").performScrollTo().performClick()
            readyNode("record-start-planting").performClick()
            readyNode("wizard-season").assertExists()
            readyNode("wizard-variety-0").assertExists()
            rule.onNodeWithTag("wizard-name").assertDoesNotExist()
            rule.onNodeWithTag("wizard-skip").assertDoesNotExist()
            readyNode("wizard-step").assertTextContains("GH belum tanam")
            captureInternal("catat-manager-start-planting")
        } finally {
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        }
    }

    /** Inofarm 20 Nov 2026: a manager picking Kegiatan on a greenhouse not yet sown is asked to start sowing first. */
    @Test fun managerPickingActivityOnUnsownGreenhouseAsksToStartSowing() {
        managerWalkthroughFixture.set(true)
        secondGreenhouse.set(true)
        unplantedGreenhouse.set(true)
        managerLogin()
        readyNode("finance-record-fab").performClick()
        readyNode("record-greenhouse-option-$gh3").performScrollTo().performClick()
        readyNode("record-activity").performClick()
        readyNode("start-sowing-dialog").assertIsDisplayed()
        readyText("Greenhouse belum dimulai semai").assertIsDisplayed()
        readyText("Apakah mau mulai semai?").assertIsDisplayed()
        captureInternal("catat-manager-start-sowing-dialog")
        readyNode("start-sowing-no").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("start-sowing-dialog").fetchSemanticsNodes().isEmpty() }
        readyNode("record-greenhouse-caption").assertExists()
        val activity = ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.activities.ActivityViewModel::class.java]
        assertEquals(null, activity.state.value.entry)
        readyNode("record-activity").performClick()
        readyNode("start-sowing-continue").performClick()
        readyNode("wizard-season").assertExists()
        readyText("Mulai semai").assertExists()
    }

    /** F2: two steps, one or two varieties planted together, each saved as a group named after it, and no worker step. */
    @Test fun greenhouseWizardSavesTwoVarietiesInTwoStepsWithoutAssignments() {
        val theme = (rule.activity.application as InofarmApplication).theme
        managerWalkthroughFixture.set(true)
        managerLogin()
        rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        try {
            readyNode("finance-tab-3").performClick()
            readyNode("manage-greenhouse").performClick()
            readyNode("admin-new").performScrollTo().performClick()
            readyNode("wizard-step").assertTextContains("Langkah 1 dari 2")
            readyNode("wizard-name").performTextReplacement("GH Wizard sintetis");hideKeyboard()
            readyNode("wizard-next").performClick()
            readyNode("wizard-step").assertTextContains("Langkah 2 dari 2", substring = true)
            rule.onNodeWithTag("wizard-group").assertDoesNotExist()
            rule.onNodeWithTag("wizard-variety-1").assertDoesNotExist()
            // Inofarm 20 Nov 2026: the season is named by the app, "<GH> Musim <n>".
            readyText("GH Wizard sintetis Musim 1").assertExists()
            readyNode("wizard-variety-0").performScrollTo().performTextReplacement("Inthanon");hideKeyboard()
            readyNode("wizard-population-0").performScrollTo().performTextReplacement("120");hideKeyboard()
            readyNode("wizard-add-variety").performScrollTo().performClick()
            readyNode("wizard-variety-1").performScrollTo().performTextReplacement("inthanon ");hideKeyboard()
            readyNode("wizard-population-1").performScrollTo().performTextReplacement("80");hideKeyboard()
            readyNode("wizard-next").performClick()
            readyText("Nama varietas kedua harus berbeda dari yang pertama.").assertExists()
            readyNode("wizard-variety-1").performScrollTo().performTextReplacement("Sweetnet");hideKeyboard()
            rule.onAllNodesWithText("Nama varietas kedua harus berbeda dari yang pertama.").assertCountEquals(0)
            readyNode("wizard-season").performScrollTo()
            captureInternal("wizard-step2-light")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.DARK) }
            readyNode("wizard-variety-1").performScrollTo()
            captureInternal("wizard-step2-dark")
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
            readyNode("wizard-next").performClick()
            readyNode("wizard-open").assertExists()
            readyText("Mulai semai Inthanon dan Sweetnet. Semua pekerja sudah bisa mencatat di sini.").assertExists()
            captureInternal("wizard-done-light")

            val sent = wizardPosts.toList()
            assertEquals(listOf("greenhouses", "seasons", "varieties", "planting-groups", "varieties", "planting-groups"),
                sent.map { it.first }.filterNot { it.endsWith("/running-period") })
            val groups = sent.filter { it.first == "planting-groups" }.map { it.second }
            assertEquals(listOf("Inthanon", "Sweetnet"), groups.map { it.getJSONObject("payload").getString("name") })
            assertEquals(listOf("120", "80"), groups.map { it.getJSONObject("payload").get("population").toString() })
            val period = sent.single { it.first.endsWith("/running-period") }.second
            assertEquals(groups.first().getString("entity_id"), period.getJSONObject("payload").getString("planting_group_id"))
            assertTrue(sent.none { it.first == "users" || it.first == "assignments" })
        } finally {
            rule.runOnIdle { theme.select(com.inofarm.jogja.core.design.ThemeMode.LIGHT) }
        }
    }

    @Test fun managerWalkthroughVisitsPickersScheduleToolsAndAdminWithoutSubmitting() {
        managerWalkthroughFixture.set(true)
        overviewFixture.set(true)
        managerLogin()
        captureWindow("manager-walk-overview")
        selectManagerGreenhouse(gh)
        val home = readyNode("finance-home-list")
        home.performScrollToNode(hasTestTag("manager-period-history"))
        captureWindow("manager-walk-greenhouse")
        readyNode("manager-period-history").performClick()
        captureWindow("manager-walk-period")
        val periods = readyNode("manager-period-list")
        periods.performScrollToNode(hasText("Akhiri periode berjalan"))
        readyText("Akhiri periode berjalan").performClick()
        periods.performScrollToNode(hasTestTag("manager-period-confirm"))
        readyNode("manager-period-confirm").assertIsNotEnabled()
        captureWindow("manager-walk-period-review")
        periods.performScrollToNode(hasText("Kembali ke daftar periode"))
        readyText("Kembali ke daftar periode").performClick()
        Espresso.pressBack()
        home.performScrollToNode(hasText("Catat hasil"))
        captureWindow("manager-walk-schedule")
        readyText("Catat hasil").performClick()
        readyNode("finance-type").performScrollTo().performClick()
        readyNode("finance-type-option-general").assertIsSelected()
        captureWindow("manager-walk-choice")
        Espresso.pressBack()
        readyNode("finance-type").assertIsDisplayed()
        choose("finance-type", "nutrition")
        readyNode("finance-type").performScrollTo().performClick()
        readyNode("finance-type-option-nutrition").assertIsSelected()
        Espresso.pressBack()
        Espresso.pressBack()

        rule.selectFinanceTab(2)
        rule.openFinanceTools()
        readyNode("allocation-new").assertIsNotEnabled()
        captureWindow("manager-walk-tools-no-scope")
        Espresso.pressBack()
        choose("expense-gh", gh)
        for ((tool, destination) in listOf(
            "report-open" to "report-list",
            "investment-open" to "investment-list",
            "recovery-open" to "recovery"
        )) {
            rule.clickFinanceTool(tool)
            if (destination == "recovery") readyText("Cadangan sistem").assertIsDisplayed()
            else readyNode(destination).assertIsDisplayed()
            captureWindow("manager-walk-$destination")
            Espresso.pressBack()
        }

        readyNode("finance-tab-3").performClick()
        captureWindow("manager-walk-account")
        readyNode("manage-users").performClick()
        readyText("Kelola akun").assertIsDisplayed()
        readyNode("admin-resource").assertTextContains("Akun")
        captureWindow("manager-walk-admin-accounts")
        Espresso.pressBack()
        readyNode("finance-tab-3").performClick()
        readyNode("manage-greenhouse").performClick()
        readyText("Kelola greenhouse").assertIsDisplayed()
        captureWindow("manager-walk-admin")
        readyNode("admin-resource").performScrollTo().performClick()
        captureWindow("manager-walk-admin-resources")
        Espresso.pressBack()
        // Assignments no longer decide access (17 Nov 2026), so they are not offered as a data type.
        val resources = com.inofarm.jogja.feature.admin.AdminResource.entries.filter { it != com.inofarm.jogja.feature.admin.AdminResource.ASSIGNMENT }
        for (resource in resources.drop(1) + resources.first()) {
            readyNode("admin-resource").performScrollTo().performClick()
            val option = "admin-resource-option-${resource.name}"
            readyNode("farm-picker-options").performScrollToNode(hasTestTag(option))
            readyNode(option).performClick()
            readyNode("admin-resource").assertTextContains(resource.label)
        }
        // Adding a greenhouse from the list opens the same two-step wizard as Beranda and Kelola.
        readyNode("admin-new").performScrollTo().performClick()
        readyNode("wizard-name").assertExists()
        captureWindow("manager-walk-admin-editor")
        readyNode("wizard-next").scrollIfScrollable().assertExists()
        Espresso.pressBack()
        assertEquals(0, posts.get() + financePosts.get() + harvestPosts.get())
    }

    @org.junit.Ignore("\"Kegiatan dilewati\" dihapus dari form (K4, 17 Nov 2026).")
    @Test fun activityMenuEntryKeepsSkippedReasonAndReturnsToHistory() {
        rule.selectFinanceTab(1)
        readyNode("finance-record-fab").performClick()
        readyNode("record-activity").performClick()
        choose("finance-gh",gh,keepContextOpen=true)
        choose("finance-season",season,keepContextOpen=true)
        choose("finance-group",group,keepContextOpen=true)
        readyNode("finance-context-continue").performScrollTo().performClick()
        readyNode("finance-skipped").performScrollTo().performClick()
        readyNode("finance-skipped-reason").performScrollTo().performTextReplacement("Pekerjaan ditunda karena akses bedeng")
        hideKeyboard()
        readyNode("finance-review").scrollIfScrollable().performClick()
        readyText("Pekerjaan ditunda karena akses bedeng").performScrollTo().assertIsDisplayed()
        captureSecondary("activity-skipped-review")
        readyNode("finance-queue").scrollIfScrollable().performClick()
        readyNode("finance-history-list").assertIsDisplayed()
        rule.waitUntil(10_000) { receipts.isNotEmpty() }
        assertEquals("skipped", receipts.values.single().getJSONObject("data").getString("work_state"))
    }

    @Test fun managerRecordsObservationsAndEditsMeasurementTarget() {
        managerWalkthroughFixture.set(true)
        overviewFixture.set(true)
        managerLogin()
        selectManagerGreenhouse()
        val home = readyNode("finance-home-list")
        home.performScrollToNode(hasText("Catat hasil"))
        readyText("Catat hasil").performClick()
        readyNode("activity-observations").performScrollTo().performClick()
        readyNode("activity-dissolved_oxygen_mg_l").performScrollTo().performTextReplacement("6,5")
        readyNode("activity-leaf_count").performScrollTo().performTextReplacement("0")
        readyNode("finance-notes").performScrollTo().performTextReplacement("Pengamatan tanaman sampel")
        hideKeyboard()
        captureWindow("manager-observations")
        readyNode("finance-review").scrollIfScrollable().performClick()
        readyNode("finance-queue").scrollIfScrollable().performClick()
        rule.waitUntil(10_000) { receipts.isNotEmpty() }
        val observations = receipts.values.single().getJSONObject("data").getJSONObject("observations")
        assertEquals("6.5", observations.getString("dissolved_oxygen_mg_l"))
        assertEquals(0, observations.getInt("leaf_count"))
        assertFalse(observations.has("plant_height_cm"))

        readyNode("finance-tab-3").performClick()
        readyNode("manage-greenhouse").performClick()
        readyNode("admin-resource").performScrollTo().performClick()
        readyNode("farm-picker-options").performScrollToNode(hasTestTag("admin-resource-option-TARGET"))
        readyNode("admin-resource-option-TARGET").performClick()
        choose("admin-greenhouse", gh)
        readyNode("admin-new").performScrollTo().performClick()
        choose("admin-planting_group_id", group)
        choose("admin-activity_type", "measurement")
        choose("admin-parameter", "manual_ec")
        choose("admin-unit", "mS/cm")
        readyNode("admin-effective_from").performScrollTo().performTextReplacement("2026-10-30T08:00:00+07:00")
        readyNode("admin-target_value").performScrollTo().performTextReplacement("1,3")
        hideKeyboard()
        readyNode("admin-submit").scrollIfScrollable().assertExists()
        captureWindow("manager-ec-target-review")
        Espresso.pressBack()
    }
    /** Layak (line 0) and, with [twoGrades], Cacat (line 1): the fixed lines of the 20 Nov 2026 form. */
    private fun fillHarvest(note:String,twoGrades:Boolean=false) {
        chooseContext(true)
        for(index in if(twoGrades) 0..1 else 0..0) {
            rule.onNodeWithTag("harvest-count-$index").performScrollTo().performTextReplacement(if(index==0) "10" else "0");hideKeyboard()
            rule.onNodeWithTag("harvest-weight-$index").performScrollTo().performTextReplacement(if(index==0) "15,25" else "0");hideKeyboard()
        }
        rule.onNodeWithTag("harvest-notes").performScrollTo().performTextReplacement(note);hideKeyboard()
    }
    private fun queueHarvest() {
        rule.onNodeWithTag("harvest-review").scrollIfScrollable().performClick();rule.onNodeWithTag("harvest-queue").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-1").fetchSemanticsNodes().isNotEmpty()};rule.openWorkerHarvestHistory()
    }
    @Test fun offlineMultigradeHarvestRecreationReconnectManagerAuditAndCancel() {
        fillHarvest("Panen offline UI sintetis",true);offline.set(true)
        rule.activityRule.scenario.recreate();readyNode("harvest-weight-0").performScrollTo().assertTextContains("15,25")
        rule.onNodeWithTag("harvest-count-1").performScrollTo().assertTextContains("0")
        captureSecondary("harvest-form")
        rule.onNodeWithTag("harvest-review").scrollIfScrollable().performClick()
        captureSecondary("harvest-review")
        rule.onNodeWithTag("harvest-queue").scrollIfScrollable().performClick()
        rule.openWorkerHarvestHistory()
        rule.waitForIdle()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Belum terkirim").fetchSemanticsNodes().isNotEmpty()};assertEquals(0,harvestPosts.get())
        rule.onNodeWithText("10 buah · 15,25 kg", substring=true).assertIsDisplayed()
        captureSecondary("harvest")
        offline.set(false);rule.onNodeWithTag("finance-sync").performClick();rule.waitUntil(10000) {harvestPosts.get()==1}
        val id=harvestReceipts.values.single().getString("entity_id");val data=harvestReceipts.values.single().getJSONObject("data")
        assertEquals(2,data.getJSONArray("items").length());assertEquals("15.250000",data.getJSONArray("items").getJSONObject(0).getString("weight_kg"))
        rule.onNodeWithTag("finance-tab-1").performClick();rule.onNodeWithTag("history-chip-activity").performClick();rule.onNodeWithText("Panen offline UI sintetis").assertDoesNotExist();rule.openWorkerHarvestHistory()
        awaitRecord("finance-record-$id");rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.onNodeWithTag("history-cancel").assertDoesNotExist();readyText("Pencatat: Pekerja sintetis").performScrollTo().assertExists()
        captureSecondary("harvest-detail")
        rule.logoutFromAccount();login("manager@example.invalid");rule.openWorkerHarvestHistory()
        awaitRecord("finance-record-$id");rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("history-audit").performScrollTo().performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Audit panen sintetis",substring=true).fetchSemanticsNodes().isNotEmpty()}
        readyNode("history-cancel").performScrollTo().performClick();rule.onNodeWithTag("history-action-reason").performTextReplacement("Pembatalan panen UI");hideKeyboard();rule.onNodeWithTag("history-action-confirm").performClick()
        rule.waitUntil(10000) {harvestPosts.get()==2};rule.waitUntil(10000) {rule.onAllNodesWithText("Dibatalkan").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(2,harvestReceipts.values.maxBy {it.getInt("version")}.getJSONObject("data").getJSONArray("items").length())
    }
    @Test fun harvestOwnCorrectionConflictUsesNewVersionAndRetainsLocalArchive() {
        fillHarvest("Panen awal konflik");queueHarvest();rule.waitUntil(10000) {harvestPosts.get()==1}
        val id=harvestReceipts.values.single().getString("entity_id")
        awaitRecord("finance-record-$id");rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("finance-edit").performScrollTo().performClick();rule.onNodeWithTag("harvest-grade-0").assertDoesNotExist()
        readyNode("harvest-weight-0").performScrollTo().performTextReplacement("16,5");hideKeyboard();rule.onNodeWithTag("harvest-reason").performScrollTo().performTextReplacement("Timbang ulang UI");hideKeyboard()
        harvestConflictNext.set(true);queueHarvest();rule.waitUntil(10000) {harvestReceipts.size==2}
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick);rule.waitUntil(10000) {rule.onAllNodesWithTag("history-compare").fetchSemanticsNodes().isNotEmpty()}
        readyNode("history-compare").performScrollTo().performClick();readyNode("conflict-reason").performScrollTo().performTextReplacement("Bobot lokal sudah diperiksa");hideKeyboard();choose("conflict-policy","preserve")
        rule.onNodeWithTag("conflict-keep-local").performScrollTo().performClick();rule.waitUntil(10000) {harvestPosts.get()==2}
        val latest=harvestReceipts.values.maxBy {it.getInt("version")};assertEquals(3,latest.getInt("version"));assertEquals("16.500000",latest.getJSONObject("data").getJSONArray("items").getJSONObject(0).getString("weight_kg"))
        rule.waitUntil(10000) {rule.onAllNodesWithTag("history-archive-0").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun harvestBalanceRejectionCanBeCorrectedAfterExplicitReview() {
        fillHarvest("Panen perbaikan saldo UI");queueHarvest();rule.waitUntil(10000) {harvestPosts.get()==1}
        val id=harvestReceipts.values.single().getString("entity_id")
        awaitRecord("finance-record-$id");rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("finance-edit").performScrollTo().performClick();readyNode("harvest-weight-0").performScrollTo().performTextReplacement("1");hideKeyboard()
        rule.onNodeWithTag("harvest-reason").performScrollTo().performTextReplacement("Koreksi saldo UI");hideKeyboard();harvestUseNext.set(true);queueHarvest()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Perlu diperbaiki").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("history-fix-rejected").performScrollTo().performClick();readyNode("harvest-weight-0").performScrollTo().performTextReplacement("17,25");hideKeyboard();queueHarvest()
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
        fun capture(name:String) {val bitmap=rule.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap();File(rule.activity.getExternalFilesDir(null),name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        content(true);readyNode("harvest-weight-0").performScrollTo().performTextReplacement("belum selesai")
        Thread.sleep(500);rule.waitForIdle();rule.onNodeWithTag("harvest-save-draft").scrollIfScrollable();capture("finance-harvest-narrow-ime.png")
        rule.onNodeWithTag("harvest-save-draft").assertIsDisplayed();hideKeyboard()
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE};rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        content(false);readyNode("harvest-weight-0").performScrollTo().performClick();Thread.sleep(500);rule.waitForIdle()
        rule.onNodeWithTag("harvest-save-draft").scrollIfScrollable();capture("finance-harvest-landscape-ime.png");rule.onNodeWithTag("harvest-save-draft").assertIsDisplayed();hideKeyboard()
        rule.onNodeWithTag("harvest-save-draft").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("finance-tab-1").fetchSemanticsNodes().isNotEmpty() }
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
        rule.logoutFromAccount()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(latest,runtime.store.record(session.scope.key,id)!!.body.getString("notes"));assertTrue(runtime.store.outbox(session.scope.key).isEmpty())
        login("worker@example.invalid");rule.onNodeWithTag("finance-tab-1").performClick()
        if(harvest) rule.onNodeWithTag("history-chip-harvest").performClick()
        awaitRecord("finance-record-$id");rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("finance-edit").performScrollTo().performClick();rule.onNodeWithTag(if(harvest) "harvest-notes" else "finance-notes").performScrollTo().assertTextContains(latest)
        if(harvest) readyNode("harvest-weight-0").performScrollTo().assertTextContains("belum")
        assertEquals(record.id,id)
    }
    @Test fun logoutFlushesLatestActivityEditorSnapshotBeforeClearingCredentials() {editorSnapshotLogout(false)}
    @Test fun logoutFlushesIncompleteHarvestEditorSnapshotBeforeClearingCredentials() {editorSnapshotLogout(true)}
    private fun unchangedAcceptedEditorLogout(harvest:Boolean) {
        val id=if(harvest) {
            fillHarvest("Panen diterima tanpa edit");queueHarvest();rule.waitUntil(10000) {harvestPosts.get()==1}
            val id=harvestReceipts.values.single().getString("entity_id")
            awaitRecord("finance-record-$id");rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick);id
        } else createAccepted("Kegiatan diterima tanpa edit")
        lateinit var runtime:FinanceRuntime;rule.runOnIdle {runtime=ViewModelProvider(rule.activity)[FinanceRuntime::class.java]}
        val session=runtime.vault.current!!;val before=runtime.store.record(session.scope.key,id)!!
        readyNode("finance-edit").performScrollTo().performClick();rule.logoutFromAccount()
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
        rule.onNodeWithText("Kembali ke isian").performClick();rule.onNodeWithTag("finance-account").assertExists()
    }
    private fun managerLogin() {
        rule.logoutFromAccount();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()};login("manager@example.invalid")
    }
    /** Two consumer lines of 1 kg at 0,004 (Golden Layak and Cacat from the stock fixture), paid; the exact total rounds to 0,01. */
    private fun saleFormWithSources() {
        saleStockFixture.set(true);managerLogin();openSaleForm()
        rule.onNodeWithTag("sale-type-consumer").performScrollTo().performClick()
        listOf(gradeLayak,gradeCacat).forEach {grade -> addSaleLine(goldenGroup,grade,"1",null,"0,004")}
        rule.onNodeWithTag("sale-paid").performScrollTo().performClick()
    }
    /** Opens a sale's detail the way Riwayat does; the ledger list now sits behind the Keuangan eye toggle. */
    private fun openSaleRecord(id:String) {
        rule.waitUntil(10000) {runtime().vault.current?.let {runtime().store.record(it.scope.key,id)}!=null}
        rule.runOnIdle {ViewModelProvider(rule.activity)["sale-history",com.inofarm.jogja.feature.history.HistoryViewModel::class.java].select(id)}
    }
    private fun openSaleForm() {
        // The Keuangan tab lists sales per season now; the form is the one the Catat sheet opens for this greenhouse.
        rule.waitUntil(15000) {runtime().vault.current?.role==com.inofarm.jogja.core.contracts.UserRole.MANAGER && rule.onAllNodesWithTag("finance-tab-2").fetchSemanticsNodes().isNotEmpty()}
        val editor=saleEditor();rule.runOnIdle {editor.new(gh)}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("sale-review").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun saleEditor():com.inofarm.jogja.feature.sales.SaleEntryViewModel {
        lateinit var editor:com.inofarm.jogja.feature.sales.SaleEntryViewModel
        rule.runOnIdle {editor=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.sales.SaleEntryViewModel::class.java]}
        return editor
    }
    private fun saleLines()=saleEditor().state.value.draft!!.items
    /** Taps the "Tambah buah" chip of a fruit and fills the new line; returns its id, which the line's fields are tagged with. */
    private fun addSaleLine(group:String,grade:String,kg:String,fruit:String?,price:String?):String {
        readyNode("sale-add-$group-$grade").performScrollTo().performClick()
        rule.waitUntil(10000) {saleLines().any {it.groupId==group && it.gradeId==grade}}
        val id=saleLines().last {it.groupId==group && it.gradeId==grade}.id
        fillSaleLine(id,kg,fruit,price);return id
    }
    private fun fillSaleLine(id:String,kg:String,fruit:String?,price:String?) {
        readyNode("sale-kg-$id").performScrollTo().performTextReplacement(kg)
        fruit?.let {rule.onNodeWithTag("sale-fruit-$id").performScrollTo().performTextReplacement(it)}
        price?.let {rule.onNodeWithTag("sale-price-$id").performScrollTo().performTextReplacement(it)}
        hideKeyboard()
    }
    private fun pickBuyer(type:String,id:String) {
        rule.onNodeWithTag("sale-type-$type").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("sale-buyer-hit-$id").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("sale-buyer-hit-$id").performScrollTo().performClick()
        readyNode("sale-buyer-change")
    }
    @Test fun saleRetailTwoVarietiesShowsTotalAndStock() {
        saleStockFixture.set(true);pastRetailSale.set(true);managerLogin();openSaleForm()
        pickBuyer("retail",custRetail)
        val golden=addSaleLine(goldenGroup,gradeLayak,"50","38","28000")
        rule.waitUntil(10000) {runCatching {rule.onNodeWithTag("sale-price-hint-$golden").assertTextEquals("Harga ditawar. Terakhir ke Toko Buah Segar: Rp28.000/kg untuk 60 kg, 12 Nov.")}.isSuccess}
        val inthanon=addSaleLine(inthanonGroup,gradeLayak,"20","16","26000")
        rule.onNodeWithTag("sale-price-hint-$inthanon").performScrollTo().assertTextEquals("Harga ditawar. Belum ada penjualan sebelumnya ke pembeli ini.")
        rule.onNodeWithTag("sale-after-$goldenGroup-$gradeLayak").performScrollTo().assertTextEquals("Sesudah dijual sisa 290 kg")
        rule.onNodeWithTag("sale-total").performScrollTo().assertTextEquals("Rp1.920.000")
        captureSecondary("sale-form-retail")
        rule.onNodeWithTag("sale-paid").performScrollTo().performClick()
        rule.onNodeWithTag("sale-review").performClick()
        readyNode("sale-summary-stock-$goldenGroup-$gradeLayak").performScrollTo()
            .assertTextContains("290 kg",substring=true).assertTextContains("Diambil dari panen 17 Nov (32 kg) dan 20 Nov (18 kg), yang paling lama lebih dulu")
        rule.onNodeWithTag("sale-summary-stock-$inthanonGroup-$gradeLayak").performScrollTo().assertTextContains("Diambil dari panen 18 Nov (20 kg)")
        rule.onNodeWithTag("sale-total-fact").assertTextEquals("Rp1.920.000")
        captureSecondary("sale-summary-retail")
    }
    /** The real entry point: Catat → Penjualan for the greenhouse, then Simpan opens the Ringkasan. */
    @Test fun saleFromCatatSheetReachesRingkasan() {
        saleStockFixture.set(true);managerLogin()
        rule.openRecordIn("sale",gh)
        rule.waitUntil(15000) {rule.onAllNodesWithTag("sale-review").fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(10000) {saleEditor().state.value.draft?.seasonId==season}
        assertEquals(gh,saleEditor().state.value.draft!!.greenhouseId)
        rule.onNodeWithTag("sale-type-consumer").performScrollTo().performClick()
        val id=addSaleLine(inthanonGroup,gradeCacat,"5",null,"10000")
        rule.onNodeWithTag("sale-price-hint-$id").performScrollTo().assertTextEquals("Belum ada harga perorangan Inthanon Cacat di Kelola. Ketik harganya.")
        rule.onNodeWithTag("sale-paid").performScrollTo().performClick()
        rule.onNodeWithTag("sale-review").performClick()
        readyNode("sale-summary-stock-$inthanonGroup-$gradeCacat").performScrollTo().assertTextContains("20 kg",substring=true)
        rule.onNodeWithText("Data transaksi").assertExists()
        rule.onNodeWithTag("sale-total-fact").assertTextEquals("Rp50.000")
    }
    @Test fun salePartnerSplitsGrades() {
        saleStockFixture.set(true);managerLogin();openSaleForm()
        pickBuyer("partner",custPartner)
        val a=addSaleLine(goldenGroup,gradeLayak,"70","52","26000")
        assertEquals("A",saleLines().single {it.id==a}.buyerGrade)
        readyNode("sale-price-hint-$a").performScrollTo().assertTextEquals("Grade dinilai mitra, harga ditawar.")
        rule.onNodeWithTag("sale-add-grade-$goldenGroup").performScrollTo().performClick()
        rule.waitUntil(10000) {saleLines().size==2}
        val b=saleLines().single {it.buyerGrade=="B"}.id
        fillSaleLine(b,"40","31","22000")
        rule.onNodeWithTag("sale-after-$goldenGroup-$gradeLayak").performScrollTo().assertTextEquals("Sesudah dijual sisa 230 kg")
        rule.onNodeWithTag("sale-total").performScrollTo().assertTextEquals("Rp2.700.000")
        captureSecondary("sale-form-partner")
        rule.onNodeWithTag("sale-paid").performScrollTo().performClick()
        rule.onNodeWithTag("sale-review").performClick();readyNode("sale-queue").performClick()
        val session=runtime().vault.current!!
        rule.waitUntil(10000) {runtime().store.outbox(session.scope.key).any {it.envelope.optString("entity_type")=="sale"}}
        val payload=runtime().store.outbox(session.scope.key).single {it.envelope.optString("entity_type")=="sale"}.envelope.getJSONObject("payload")
        val lines=payload.getJSONArray("lines").let {rows -> (0 until rows.length()).map {rows.getJSONObject(it)}}
        assertEquals(listOf("A","B"),lines.map {it.getString("buyer_grade")});assertEquals(listOf("70.000000","40.000000"),lines.map {it.getString("quantity_kg")})
        assertEquals(listOf(52,31),lines.map {it.getInt("fruit_count")});assertEquals("partner",payload.getString("customer_type"));assertEquals(custPartner,payload.getJSONObject("customer").getString("id"))
    }
    /** Leaving Mitra asks first, because grade B and C lines and their kg are removed. */
    @Test fun saleLeavingPartnerConfirmsBeforeDroppingGrades() {
        saleStockFixture.set(true);managerLogin();openSaleForm()
        pickBuyer("partner",custPartner)
        addSaleLine(goldenGroup,gradeLayak,"70",null,"26000")
        rule.onNodeWithTag("sale-add-grade-$goldenGroup").performScrollTo().performClick()
        rule.waitUntil(10000) {saleLines().size==2}
        fillSaleLine(saleLines().single {it.buyerGrade=="B"}.id,"40",null,"22000")
        rule.onNodeWithTag("sale-type-retail").performScrollTo().performClick()
        readyNode("sale-switch-loss").assertTextEquals("Grade B hanya untuk mitra. Baris grade B dihapus beserta 40 kg.")
        captureSecondary("sale-switch-partner-confirm")
        rule.onNodeWithTag("sale-switch-cancel").performClick()
        rule.waitForIdle();assertEquals("partner",saleEditor().state.value.draft!!.buyerType);assertEquals(2,saleLines().size)
        rule.onNodeWithTag("sale-type-retail").performScrollTo().performClick()
        readyNode("sale-switch-confirm").performClick()
        rule.waitUntil(10000) {saleEditor().state.value.draft!!.buyerType=="retail"}
        assertEquals(1,saleLines().size);assertEquals("",saleLines().single().buyerGrade);assertEquals("70",saleLines().single().quantity)
    }
    @Test fun saleConsumerPrefillsPrice() {
        saleStockFixture.set(true);managerLogin();openSaleForm()
        rule.onNodeWithTag("sale-type-consumer").performScrollTo().performClick()
        readyNode("sale-add-$goldenGroup-$gradeLayak").performScrollTo().performClick()
        // The price list arrives from the server; an empty Perorangan price is filled as soon as it does.
        rule.waitUntil(10000) {saleLines().singleOrNull()?.price=="34000"}
        val id=saleLines().single().id
        rule.onNodeWithTag("sale-price-hint-$id").performScrollTo().assertTextEquals("Harga perorangan Golden Layak, diisi dari Kelola. Masih bisa diubah.")
        fillSaleLine(id,"3","2",null)
        rule.onNodeWithTag("sale-total").performScrollTo().assertTextEquals("Rp102.000")
        captureSecondary("sale-form-consumer")
        // Still editable: a typed price replaces the list price.
        fillSaleLine(id,"3",null,"30000");rule.onNodeWithTag("sale-total").performScrollTo().assertTextEquals("Rp90.000")
        rule.onNodeWithTag("sale-price-hint-$id").performScrollTo().assertTextEquals("Harga perorangan bisa diubah.")
    }
    @Test fun saleOfflineDraftRecreationExactTotalAndAuditUseDedicatedFlow() {
        saleFormWithSources();offline.set(true)
        rule.activityRule.scenario.recreate();rule.onNodeWithTag("sale-price-${saleLines().first().id}").performScrollTo().assertTextContains("0,004")
        captureSecondary("sale-form")
        rule.onNodeWithTag("sale-review").performClick()
        captureSecondary("sale-review")
        rule.onNodeWithTag("sale-queue").scrollIfScrollable().performClick()
        val session=runtime().vault.current!!
        rule.waitUntil(10000) {runtime().store.outbox(session.scope.key).any {it.envelope.optString("entity_type")=="sale"}}
        val op=runtime().store.outbox(session.scope.key).single {it.envelope.optString("entity_type")=="sale"};assertEquals("QUEUED",op.state)
        offline.set(false);rule.sendQueuedNow();val id=acceptedFinancial("sale")
        assertEquals("0.01",runtime().store.record(session.scope.key,id)!!.body.getString("total_amount"));assertEquals(op.hash,runtime().store.outbox(session.scope.key).single {it.id==op.id}.hash)
        openSaleRecord(id)
        readyNode("sale-accepted-total").performScrollTo().assertTextContains("0,01",substring=true)
        captureSecondary("sale-detail")
        readyNode("history-audit").performScrollTo().performClick();rule.waitUntil(10000) {rule.onAllNodesWithText("Audit biaya sintetis",substring=true).fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun saleRejectedSourceReviewRetainsOldOperationAndRequiresNewExplicitCorrection() {
        saleFormWithSources();saleRejectNext.set(true)
        rule.onNodeWithTag("sale-review").scrollIfScrollable().performClick();rule.onNodeWithTag("sale-queue").scrollIfScrollable().performClick()
        val session=runtime().vault.current!!;rule.waitUntil(15000) {runtime().store.outbox(session.scope.key).any {it.error?.optString("code")=="harvest_exceeded"}}
        val original=runtime().store.outbox(session.scope.key).single {it.error?.optString("code")=="harvest_exceeded"}
        openSaleRecord(original.entityId)
        rule.waitUntil(10000) {rule.onAllNodesWithTag("history-fix-rejected").fetchSemanticsNodes().isNotEmpty()}
        readyNode("history-fix-rejected").performScrollTo().performClick()
        rule.waitUntil(10000) {saleEditor().state.value.draft!=null}
        readyNode("sale-kg-${saleLines().first().id}").performScrollTo().performTextReplacement("0,5");hideKeyboard()
        rule.onNodeWithTag("sale-review").scrollIfScrollable().performClick();rule.onNodeWithTag("sale-queue").scrollIfScrollable().performClick();acceptedFinancial("sale")
        val operations=runtime().store.outbox(session.scope.key).filter {it.entityId==original.entityId};assertEquals(2,operations.size)
        assertEquals("SUPERSEDED",operations.single {it.id==original.id}.state);assertEquals(original.hash,operations.single {it.id==original.id}.hash)
    }
    @Test fun logoutPreservesLatestIncompleteSaleWithoutInventingPaymentConfirmation() {
        managerLogin();val session=runtime().vault.current!!
        lateinit var editor:com.inofarm.jogja.feature.sales.SaleEntryViewModel
        rule.runOnIdle {editor=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.sales.SaleEntryViewModel::class.java];editor.new(gh);editor.edit {it.copy(date="belum",items=listOf(com.inofarm.jogja.feature.sales.SaleLine(quantity="1,2,3",price="")))};editor.pauseAutosave()}
        val draft=editor.state.value.draft!!
        // The open form hides the account menu; this is the flush its Keluar runs before the session ends.
        rule.runOnIdle {val ticket=runtime().vault.generation;ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.auth.AuthViewModel::class.java].logout {runtime().sales.flush(session,ticket,draft)}}
        rule.waitUntil(10000) {runtime().vault.current==null}
        val saved=runtime().store.record(session.scope.key,draft.id)!!;val raw=saved.body.getJSONObject("_local_form")
        assertEquals("belum",raw.getString("date"));assertFalse(raw.getBoolean("paid"));assertEquals("1,2,3",raw.getJSONArray("lines").getJSONObject(0).getString("quantity"));assertTrue(runtime().store.outbox(session.scope.key).isEmpty())
    }
    private fun financialForm(allocation:Boolean=false) {
        rule.selectFinanceTab(2);choose("expense-gh",gh)
        if(allocation) rule.clickFinanceTool("allocation-new") else rule.onNodeWithTag("expense-new").performClick()
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
    private fun queueFinancial() {rule.onNodeWithTag("expense-review").scrollIfScrollable().performClick();rule.onNodeWithTag("expense-queue").scrollIfScrollable().performClick()}
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
        offline.set(false);Thread.sleep(1200);rule.sendQueuedNow()
        val source=acceptedFinancial("expense");assertEquals("100.01",runtime().store.record(session.scope.key,source)!!.body.getString("amount"))
        rule.onNodeWithTag("finance-tab-2").performClick();awaitRecord("finance-record-$source")
        captureSecondary("finance-records")
        rule.onNodeWithTag("finance-record-$source").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick);readyNode("history-audit").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Audit biaya sintetis",substring=true).fetchSemanticsNodes().isNotEmpty()};Espresso.pressBack()
        financialForm(true);fillAllocation(source)
        captureSecondary("allocation-form")
        rule.onNodeWithTag("expense-review").scrollIfScrollable().performClick()
        captureSecondary("allocation-review")
        rule.onNodeWithTag("expense-queue").scrollIfScrollable().performClick()
        val allocationId=acceptedFinancial("cost_allocation")
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-history-kind","cost_allocation")
        awaitRecord("finance-record-$allocationId");rule.onNodeWithTag("finance-record-$allocationId").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("expense-remaining-fact").performScrollTo().assertTextContains("Rp40",substring=true)
        captureSecondary("allocation-detail")
        assertEquals(2,financePosts.get());assertEquals(original.id,runtime().store.outbox(session.scope.key).first {it.entityId==source}.id)
    }
    @Test fun rejectedAllocationRequiresExplicitReviewAndKeepsOriginalEnvelope() {
        managerLogin();financialForm();fillPayment();queueFinancial();val source=acceptedFinancial("expense")
        financialForm(true);fillAllocation(source,"101");queueFinancial()
        val session=runtime().vault.current!!
        rule.waitUntil(15000) {runtime().store.outbox(session.scope.key).any {it.error?.optString("code")=="allocation_exceeds_source"}}
        val original=runtime().store.outbox(session.scope.key).single {it.error?.optString("code")=="allocation_exceeds_source"}
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-history-kind","cost_allocation")
        awaitRecord("finance-record-${original.entityId}");rule.onNodeWithTag("finance-record-${original.entityId}").performScrollTo().performClick()
        readyNode("history-fix-rejected").performScrollTo().performClick();rule.onNodeWithTag("expense-amount").performScrollTo().performTextReplacement("60,01");hideKeyboard()
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
        rule.logoutFromAccount();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        login("manager@example.invalid");val saved=runtime().store.record(session.scope.key,draft.id)!!
        assertEquals("1,2,3",saved.body.getJSONObject("_local_form").getString("amount"));assertEquals("belum",saved.body.getJSONObject("_local_form").getString("date"))
        assertTrue(runtime().store.outbox(session.scope.key).isEmpty());assertEquals(0,financePosts.get())
    }
    @Test fun unchangedAcceptedFinancialEditorLogoutDoesNotCreateCorrection() {
        managerLogin();financialForm();fillPayment();queueFinancial();val id=acceptedFinancial("expense");val session=runtime().vault.current!!
        val before=runtime().store.record(session.scope.key,id)!!
        rule.runOnIdle {ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.expenses.FinancialEntryViewModel::class.java].open(before)}
        rule.logoutFromAccount();rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
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
        layout(true);rule.onNodeWithTag("expense-notes").performScrollTo().performClick().performTextReplacement("Catatan pembayaran font 200 persen")
        Thread.sleep(500);rule.waitForIdle();rule.onNodeWithTag("expense-cancel").scrollIfScrollable().assertIsDisplayed()
        File(rule.activity.getExternalFilesDir(null),"expense-narrow-ime.png").outputStream().use {rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        captureWindow("payment-narrow-ime")
        hideKeyboard();rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE};layout(false)
        rule.onNodeWithTag("expense-notes").performScrollTo().assertTextContains("Catatan pembayaran font 200 persen")
            .performClick().performTextReplacement("Landscape tetap dapat disimpan")
        Thread.sleep(500);rule.waitForIdle();rule.onNodeWithTag("expense-cancel").scrollIfScrollable().assertIsDisplayed()
        File(rule.activity.getExternalFilesDir(null),"expense-landscape-ime.png").outputStream().use {rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        captureWindow("payment-landscape-ime")
        rule.onNodeWithTag("expense-cancel").performClick();rule.onNodeWithTag("entry-discard-confirm").performClick();rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
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
        rule.onNodeWithTag("entry-notes").performScrollTo().performTextReplacement("Uji font 200 persen")
        Thread.sleep(500);rule.waitForIdle()
        rule.onNodeWithTag("finance-save-draft").scrollIfScrollable().assertIsDisplayed()
        capture("finance-narrow-ime.png")
        hideKeyboard()
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
        rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        overrideContent(false)
        rule.onNodeWithTag("entry-notes").performScrollTo().assertTextContains("Uji font 200 persen")
        rule.onNodeWithTag("entry-notes").performTextReplacement("Landscape tetap tersimpan")
        Thread.sleep(500);rule.waitForIdle()
        rule.onNodeWithTag("finance-save-draft").scrollIfScrollable()
        capture("finance-landscape-ime.png")
        rule.onNodeWithTag("finance-save-draft").assertIsDisplayed()
        rule.onNodeWithTag("finance-save-draft").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-tab-1").fetchSemanticsNodes().isNotEmpty()}
        rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
    }

    private fun assetSources(includeAsset:Boolean=false):String {
        val expense=UUID.randomUUID().toString()
        fun seed(kind:String,id:String,data:JSONObject) {financeKinds[id]=kind;financeReceipts[UUID.randomUUID().toString()]=JSONObject().put("entity_id",id).put("version",1).put("data",data.put("id",id).put("version",1).put("greenhouse_id",gh).put("record_state","active").put("created_at","2026-10-30T00:00:00Z"))}
        seed("expense",expense,JSONObject().put("amount","100.00").put("payment_kind","asset_acquisition").put("notes","Pembelian pompa sintetis").put("paid_at","2026-10-30T00:00:00Z").put("balance_id",UUID.randomUUID().toString()))
        if(includeAsset) {
            val asset=UUID.randomUUID().toString();seed("asset",asset,JSONObject().put("expense_id",expense).put("name","Pompa sumber sintetis").put("cost","100.00").put("residual","0.00").put("life_years",3).put("in_service_date","2026-01-01").put("usage_basis","Pemakaian sintetis"));return asset
        }
        return expense
    }
    @Test fun assetFormReviewRecreationAndAcceptedHistoryUseDedicatedFlow() {
        val expense=assetSources();rule.logoutFromAccount();login("manager@example.invalid")
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",gh);rule.clickFinanceTool("asset-new");choose("asset-expense",expense)
        for((tag,value) in listOf("asset-name" to "Pompa UI sintetis","asset-cost" to "100,00","asset-residual" to "0","asset-life" to "3","asset-start" to "2026-01-01","asset-basis" to "Basis manual eksplisit")) {rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
        hideKeyboard();rule.activityRule.scenario.recreate();rule.onNodeWithTag("asset-name").performScrollTo().assertTextContains("Pompa UI sintetis")
        captureSecondary("asset-form")
        rule.onNodeWithTag("asset-review").scrollIfScrollable().performClick()
        captureSecondary("asset-review")
        rule.onNodeWithTag("asset-queue").scrollIfScrollable().performClick()
        rule.waitUntil(15000) {financeKinds.values.count {it=="asset"}==1};val id=financeKinds.entries.single {it.value=="asset"}.key
        choose("expense-history-kind","asset")
        awaitRecord("finance-record-$id");rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("asset-cost-fact").performScrollTo().assertExists()
        rule.onNode(hasAnyAncestor(hasTestTag("asset-cost-fact")) and hasText("Rp100", substring=true)).assertExists()
        captureSecondary("asset-detail")
    }
    @Test fun depreciationRequiresExplicitUsagePeriodBasisAndDoesNotInventAmount() {
        val asset=assetSources(true);rule.logoutFromAccount();login("manager@example.invalid")
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",gh);rule.clickFinanceTool("depreciation-new");choose("asset-source",asset);choose("asset-season",season)
        assertEquals("",rule.onNodeWithTag("asset-amount").performScrollTo().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text)
        for((tag,value) in listOf("asset-usage-year" to "1","asset-period-start" to "2026-01-01","asset-period-end" to "2027-01-01","asset-amount" to "33,33","asset-basis" to "Pemakaian musim sintetis")) {rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
        hideKeyboard();rule.onNodeWithTag("asset-new-usage").performScrollTo().performClick()
        captureSecondary("depreciation-form")
        rule.onNodeWithTag("asset-review").scrollIfScrollable().performClick()
        captureSecondary("depreciation-review")
        rule.onNodeWithTag("asset-queue").scrollIfScrollable().performClick()
        rule.waitUntil(15000) {financeKinds.values.any {it=="depreciation_allocation"}}
        val receipt=financeReceipts.values.single {financeKinds[it.getString("entity_id")]=="depreciation_allocation"};assertEquals("33.33",receipt.getJSONObject("data").getString("amount"))
        choose("expense-history-kind","depreciation_allocation")
        val id=receipt.getString("entity_id")
        awaitRecord("finance-record-$id")
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("finance-edit").assertExists()
        captureSecondary("depreciation-detail")
    }
    @Test fun assetImmediateLogoutFlushesLatestIncompleteRawInput() {
        assetSources();rule.logoutFromAccount();login("manager@example.invalid");rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",gh);rule.clickFinanceTool("asset-new")
        rule.onNodeWithTag("asset-name").performScrollTo().performTextReplacement("Terakhir sebelum logout");hideKeyboard();rule.logoutFromAccount()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        login("manager@example.invalid");val model=runtime();val session=model.vault.current!!
        val row=model.store.records(session.scope.key,kind="asset").single();assertEquals("Terakhir sebelum logout",row.body.getJSONObject("_local_form").getString("name"));assertTrue(model.store.outbox(session.scope.key).none {it.envelope.optString("entity_type")=="asset"})
    }
    @Test fun assetHubRemainsScrollableWithFont200Percent() {
        assetSources();rule.logoutFromAccount();login("manager@example.invalid")
        rule.runOnUiThread {
            val model=ViewModelProvider(rule.activity)[FinanceRuntime::class.java];val density=rule.activity.resources.displayMetrics.density
            rule.activity.setContent {CompositionLocalProvider(LocalDensity provides Density(density,2f)) {InofarmTheme {Box(Modifier.width(320.dp).fillMaxHeight()) {FinanceRoot(model)}}}}
        }
        rule.waitForIdle();rule.selectFinanceTab(2);choose("expense-gh",gh)
        rule.openFinanceTools()
        readyNode("asset-new").performScrollTo().assertIsDisplayed();rule.onNodeWithTag("depreciation-new").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("finance-tools-close").performScrollTo().performClick()
        choose("expense-history-kind","asset");rule.clickFinanceTool("asset-new")
        rule.onNodeWithTag("asset-name").performScrollTo().performTextReplacement("Isian 200 persen")
        rule.onNodeWithTag("asset-basis").performScrollTo().performTextReplacement("Basis tetap bisa dijangkau");hideKeyboard()
        rule.onNodeWithTag("asset-cancel").scrollIfScrollable().assertIsDisplayed().performClick();rule.onNodeWithTag("entry-discard-confirm").performClick()
    }

    @Test fun assetConflictReviewPreservesRawLocalNameAndOldOperation() {
        val id=assetSources(true);rule.logoutFromAccount();login("manager@example.invalid")
        val model=runtime();val session=model.vault.current!!
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-history-kind","asset")
        awaitRecord("finance-record-$id");rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        readyNode("finance-edit").performScrollTo().performClick();rule.onNodeWithTag("asset-name").performScrollTo().performTextReplacement("Nama aset lokal diperiksa")
        rule.onNodeWithTag("asset-reason").performScrollTo().performTextReplacement("Koreksi nama aset");hideKeyboard();assetConflictNext.set(true)
        rule.onNodeWithTag("asset-review").scrollIfScrollable().performClick();rule.onNodeWithTag("asset-queue").scrollIfScrollable().performClick()
        rule.waitUntil(15000) {model.store.outbox(session.scope.key).any {it.entityId==id && it.state=="CONFLICT"}}
        val original=model.store.outbox(session.scope.key).single {it.entityId==id}
        choose("expense-history-kind","asset")
        awaitRecord("finance-record-$id")
        rule.onNodeWithTag("finance-record-$id").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick);readyNode("history-compare").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Data terbaru · versi 2").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("conflict-keep-local").performScrollTo().assertIsNotEnabled()
        readyNode("conflict-reason").performScrollTo().performTextReplacement("Nama lokal dipertahankan setelah peninjauan");hideKeyboard()
        rule.onNodeWithTag("conflict-policy").assertDoesNotExist();rule.onNodeWithTag("conflict-keep-local").performScrollTo().performClick()
        rule.waitUntil(15000) {model.store.record(session.scope.key,id)?.serverVersion==3L}
        assertEquals("Nama aset lokal diperiksa",model.store.record(session.scope.key,id)!!.body.getString("name"))
        val operations=model.store.outbox(session.scope.key).filter {it.entityId==id};assertEquals(2,operations.size)
        val old=operations.single {it.id==original.id};assertEquals("SUPERSEDED",old.state);assertEquals(original.hash,old.hash);assertEquals(original.envelope.toString(),old.envelope.toString())
        assertEquals(2,operations.single {it.id!=original.id}.envelope.getInt("base_version"));assertTrue(model.store.archived(session.scope.key,id).isNotEmpty())
    }

    @Test fun assetQueueRechecksPaymentAndReturnsFailureReasonWithoutLosingRawInput() {
        val expense=assetSources();rule.logoutFromAccount();login("manager@example.invalid")
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",gh);rule.clickFinanceTool("asset-new");choose("asset-expense",expense)
        for((tag,value) in listOf("asset-name" to "Aset sumber berubah","asset-cost" to "100","asset-residual" to "0","asset-life" to "3","asset-start" to "2026-01-01","asset-basis" to "Basis manual")) {rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)}
        hideKeyboard();rule.onNodeWithTag("asset-review").scrollIfScrollable().performClick()
        val model=runtime();val session=model.vault.current!!;val cancelled=JSONObject(model.store.cachedOne(session.scope.key,"expense",expense)!!.toString()).put("version",2).put("record_state","cancelled")
        model.store.applyPull(session.scope.key,JSONObject().put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh)))
            .put("data",JSONArray().put(JSONObject().put("entity_type","expense").put("entity_id",expense).put("greenhouse_id",gh).put("version",2).put("tombstone",false).put("data",cancelled))).put("next_cursor","changed-source").put("has_more",false))
        rule.onNodeWithTag("asset-queue").scrollIfScrollable().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("asset-name").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("asset-name").performScrollTo().assertTextContains("Aset sumber berubah")
        rule.onNodeWithText("Pilih pembayaran pembelian aset aktif pada greenhouse yang sama.").assertExists()
        assertTrue(model.store.outbox(session.scope.key).none {it.envelope.optString("entity_type")=="asset"})
        assertEquals("Aset sumber berubah",model.store.records(session.scope.key,kind="asset").single().body.getJSONObject("_local_form").getString("name"))
    }

    @Test fun investmentWorkspaceRecreationAndLogoutKeepLatestIncompleteRawInput() {
        rule.logoutFromAccount();login("manager@example.invalid")
        rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",gh);rule.clickFinanceTool("investment-open")
        rule.waitUntil(10000) {rule.onAllNodesWithTag("investment-list").fetchSemanticsNodes().isNotEmpty()}
        fun scroll(tag:String) {rule.onNodeWithTag("investment-list").performScrollToNode(hasTestTag(tag))}
        // The list can re-lay out after a scroll (IME inset or top bar change arriving outside Compose idling), so a field
        // scrolled into view may briefly leave the tree before it is typed into. Scroll and type again until it lands.
        fun fill(tag:String,value:String) {rule.waitUntil(10000) {runCatching {scroll(tag);rule.onNodeWithTag(tag).performTextReplacement(value)}.isSuccess};hideKeyboard()}
        scroll("investment-new");rule.onNodeWithTag("investment-new").performClick()
        fill("investment-name","  Skenario root α  ")
        rule.onNodeWithTag("investment-name").assertTextContains("  Skenario root α  ")
        fill("investment-horizon","2")
        val model=runtime();val session=model.vault.current!!;val vm=ViewModelProvider(rule.activity)[com.inofarm.jogja.feature.investments.InvestmentViewModel::class.java];val id=vm.state.value.scenarioDraft!!.id
        rule.activityRule.scenario.recreate();readyNode("investment-name").performScrollTo().assertTextContains("  Skenario root α  ")
        fill("investment-initial","1,");rule.logoutFromAccount()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("finance-login").fetchSemanticsNodes().isNotEmpty()}
        val saved=model.store.record(session.scope.key,id)!!;assertEquals("1,",saved.body.getJSONObject("_local_form").getString("initial"));assertEquals("2",saved.body.getJSONObject("_local_form").getString("horizon"));assertNull(saved.serverVersion)
        assertTrue(model.store.outbox(session.scope.key).none {it.entityId==id})
        login("manager@example.invalid");rule.onNodeWithTag("finance-tab-2").performClick();choose("expense-gh",gh);rule.clickFinanceTool("investment-open")
        rule.waitUntil(10000) {rule.onAllNodesWithTag("investment-record-$id").fetchSemanticsNodes().isNotEmpty()};scroll("investment-record-$id");rule.onNodeWithTag("investment-record-$id").performClick()
        rule.waitUntil(10000) {runCatching {scroll("investment-initial")}.isSuccess}
        rule.onNodeWithTag("investment-initial").assertTextContains("1,")
    }

}
