package com.inofarm.jogja

import android.content.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.*
import com.inofarm.jogja.core.network.*
import com.inofarm.jogja.core.session.*
import com.inofarm.jogja.feature.exports.*
import com.inofarm.jogja.core.design.InofarmTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FinanceReportExportTest {
    @get:Rule val rule=createComposeRule()
    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="finance-test-export-${UUID.randomUUID()}.db";private val prefs="finance-test-export-${UUID.randomUUID()}"
    private val epoch=UUID.randomUUID().toString();private val server=MockWebServer();private val status=AtomicInteger(200);private val revoke=AtomicBoolean(false)
    private lateinit var helper:FinanceDatabase;private lateinit var store:LocalFinanceStore;private lateinit var vault:FinanceSessionVault;private lateinit var session:FinanceSession;private lateinit var repository:ReportExportRepository;private lateinit var data:JSONObject;private lateinit var row:LocalRecord
    private val csv="\uFEFFfield,type,value,json_value,text_escaped\r\n/test,string,α,\"\"\"α\"\"\",false\r\n"
    private val ownedUris=mutableMapOf<Uri,String>()
    @Before fun setup() {
        assertTrue(context.packageName.endsWith(".finance"));data=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("investment-report-public.json").bufferedReader().use {it.readText()}).getJSONObject("receipt").getJSONObject("data")
        server.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
            if(status.get()!=200) return MockResponse().setResponseCode(status.get()).setBody("{\"message\":\"Akses fixture dicabut\"}")
            if(revoke.get()) vault.logout()
            val pdf=request.requestUrl!!.encodedPath.endsWith("/pdf");val body=if(pdf) "%PDF-1.4\nfixture\n%%EOF" else csv
            val digest=MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") {"%02x".format(it)}
            return MockResponse().setBody(body).addHeader("Content-Type",if(pdf) "application/pdf" else "text/csv; charset=UTF-8").addHeader("X-Report-Snapshot-Hash",data.getString("snapshot_hash")).addHeader("X-Content-SHA256",digest)
        }};server.start();session=FinanceSession(AccountScope(server.url("/api/v2/").toString(),data.getString("recorded_by")),"synthetic-export-token",Instant.now().plusSeconds(3600).toString(),"Pengelola sintetis",UserRole.MANAGER)
        helper=FinanceDatabase(context,name);store=LocalFinanceStore(helper,FixtureCipher());store.initialize(session.scope.key,epoch,UserRole.MANAGER)
        val gh=data.getString("greenhouse_id");store.applyPull(session.scope.key,JSONObject().put("sync_epoch",epoch).put("scope",JSONArray().put(JSONObject().put("id",gh))).put("data",JSONArray().put(JSONObject().put("entity_type",InvestmentContract.REPORT).put("entity_id",data.getString("id")).put("greenhouse_id",gh).put("version",1).put("tombstone",false).put("data",data))).put("has_more",false).put("next_cursor","export-fixture"));store.hydrateReportCache(session.scope.key,InvestmentContract.REPORT)
        row=store.record(session.scope.key,data.getString("id"))!!;vault=FinanceSessionVault(context,FixtureCipher(),prefs);assertTrue(vault.save(vault.generation,session));repository=ReportExportRepository(context,vault,store,FinanceHttpApi(true))
    }
    @After fun close() {
        for((uri,filename) in ownedUris) {
            val actual=context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null}
            check(actual==filename && filename.startsWith("finance-test-export-"));context.contentResolver.delete(uri,null,null)
        }
        server.shutdown();helper.close();assertTrue(name.startsWith("finance-test-"));context.deleteDatabase(name);context.deleteSharedPreferences(prefs)
    }
    private fun destination():Uri {
        val filename="finance-test-export-${UUID.randomUUID()}.csv"
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {put(MediaStore.MediaColumns.DISPLAY_NAME,filename);put(MediaStore.MediaColumns.MIME_TYPE,"text/csv");put(MediaStore.MediaColumns.IS_PENDING,1)}) ?: error("Owned test document unavailable")
        ownedUris[uri]=filename;return uri
    }
    @Test fun authenticatedCsvSavesExactVerifiedBytesThroughContentResolver()=runBlocking {
        val uri=destination();val selection=ReportExportSelection.from(session.scope.key,row,"csv");assertEquals(selection.filename,repository.save(session,vault.generation,selection,uri))
        assertEquals(csv,context.contentResolver.openInputStream(uri)!!.use {it.readBytes().toString(Charsets.UTF_8)});assertEquals("Bearer synthetic-export-token",server.takeRequest().getHeader("Authorization"))
    }
    @Test fun revokedSessionAndChangedSelectionNeverInvokeDestination()=runBlocking {
        val selection=ReportExportSelection.from(session.scope.key,row,"csv");val writes=AtomicInteger()
        try {repository.download(session,vault.generation,selection.copy(hash="0".repeat(64))) {writes.incrementAndGet()};fail("Changed selection saved")} catch(expected:IllegalArgumentException) { }
        assertEquals(0,server.requestCount);revoke.set(true)
        try {repository.download(session,vault.generation,selection) {writes.incrementAndGet()};fail("Revoked session saved")} catch(expected:IllegalStateException) { }
        assertEquals(0,writes.get())
    }
    @Test fun currentWorkerScopeAndServerDenialPreventWrites()=runBlocking {
        val selection=ReportExportSelection.from(session.scope.key,row,"csv");val writes=AtomicInteger()
        status.set(403);try {repository.download(session,vault.generation,selection) {writes.incrementAndGet()};fail("Denied response saved")} catch(expected:FinanceApiFailure) {assertEquals(403,expected.status)}
        store.initialize(session.scope.key,epoch,UserRole.WORKER);try {repository.download(session,vault.generation,selection) {writes.incrementAndGet()};fail("Worker cache saved")} catch(expected:IllegalArgumentException) { }
        assertEquals(0,writes.get())
    }
    @Test fun documentPickerUsesReviewedSnapshotAndReportsSuccessAfterWriting() {
        val uri=destination();var captured:Intent?=null
        val registry=object:ActivityResultRegistry() {override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {captured=input as Intent;Handler(Looper.getMainLooper()).post {dispatchResult(requestCode,android.app.Activity.RESULT_OK,Intent().setData(uri))}}}
        val owner=object:ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        rule.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {InofarmTheme {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,2f)) {Box(Modifier.width(320.dp)) {ReportExportHost(session,repository) {ReportExportActions(row)}}}}}}
        rule.onNodeWithTag("report-download-csv").performClick();rule.waitUntil(20000) {rule.onAllNodesWithText("Laporan tersimpan:",substring=true).fetchSemanticsNodes().isNotEmpty()}
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,captured!!.action);assertEquals("text/csv",captured!!.type);assertEquals(ReportExportSelection.from(session.scope.key,row,"csv").filename,captured!!.getStringExtra(Intent.EXTRA_TITLE));assertEquals(csv,context.contentResolver.openInputStream(uri)!!.use {it.readBytes().toString(Charsets.UTF_8)})
    }
    @Test fun cancellingDocumentPickerDoesNotDownloadOrClaimSaved() {
        val registry=object:ActivityResultRegistry() {override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {Handler(Looper.getMainLooper()).post {dispatchResult(requestCode,android.app.Activity.RESULT_CANCELED,null)}}}
        val owner=object:ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        rule.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {InofarmTheme {ReportExportHost(session,repository) {ReportExportActions(row)}}}}
        rule.onNodeWithTag("report-download-pdf").performClick();rule.waitForIdle();assertEquals(0,server.requestCount);rule.onNodeWithText("Laporan tersimpan:",substring=true).assertDoesNotExist()
    }
}
