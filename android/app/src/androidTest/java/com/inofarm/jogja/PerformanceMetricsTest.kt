package com.inofarm.jogja

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.io.File
import java.util.concurrent.TimeUnit

/** Shared unchanged harness for the pristine baseline and the optimized build. */
class PerformanceMetricsTest {
    @Test fun coldCacheMainOccupancyAndBitmapSample() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        check(context.packageName.endsWith(".performance"))
        val server=MockWebServer();server.start()
        server.dispatcher=object: Dispatcher() {override fun dispatch(request:RecordedRequest)=MockResponse().setResponseCode(503).setBody("{\"message\":\"offline test\"}")}
        val session=SessionStore(context);session.clear();val stores=mutableListOf<ViewModelStore>()
        try {
            session.baseUrl=server.url("/api/v1/").toString();session.saveToken("metrics-test-only")
            session.identity(JSONObject().put("id",1).put("tank_id",1),Instant.now().plusSeconds(3600).toString())
            val cache=ReadCache(context,session);val scope=session.cacheScope
            cache.save(scope,"config",JSONObject().put("config",JSONObject().put("id",1).put("body",JSONObject().put("padding","x".repeat(1_200_000)))))
            cache.save(scope,"dashboard",JSONObject().put("connectionState","unknown"))
            val rows=JSONArray()
            repeat(500) {index ->rows.put(JSONObject().put("id",index).put("body",JSONObject().put("confirmedAt","2026-10-10T00:00:00Z"))
                .put("received_at","2026-10-10T00:01:00Z").put("phase","vegetatif").put("manual_ppm",900).put("clock_trusted",true)
                .put("assignment",JSONObject().put("status","unscheduled"))
                .put("validation_result",JSONObject().put("status","incomplete").put("checks",JSONArray().put(JSONObject().put("result","incomplete").put("code","missing_config").put("message","Parameter belum lengkap")))))}
            cache.history(scope,JSONObject().put("data",rows),emptyMap(),false)
            val samples=mutableListOf<String>()
            repeat(5) {run ->
                val coldSession=SessionStore(context)
                val repository=OwnerRepository(ApiClient({coldSession.baseUrl},{coldSession.readToken()},true),coldSession,ReadCache(context,coldSession))
                val store=ViewModelStore();stores+=store;lateinit var vm: OwnerViewModel;var mainMs=0.0
                val start=System.nanoTime()
                instrumentation.runOnMainSync {
                    val onMain=System.nanoTime()
                    vm=ViewModelProvider(store,object:ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=OwnerViewModel(repository) as T
                    })[OwnerViewModel::class.java]
                    mainMs=(System.nanoTime()-onMain)/1e6
                }
                val deadline=start+TimeUnit.SECONDS.toNanos(15)
                while(vm.state.value.history.size!=500 || vm.state.value.dashboard==null) {check(System.nanoTime()<deadline);Thread.sleep(1)}
                val restoredMs=(System.nanoTime()-start)/1e6
                samples+="run=$run mainCreateMs=$mainMs restoreObservedMs=$restoredMs rows=${vm.state.value.history.size}"
                instrumentation.runOnMainSync {store.clear()}
            }
            for(resource in listOf(R.drawable.melon_hero,R.drawable.leaf_hero)) {
                val bitmap=BitmapFactory.decodeResource(context.resources,resource)
                samples+="defaultDecode=${context.resources.getResourceEntryName(resource)} ${bitmap.width}x${bitmap.height} ${bitmap.allocationByteCount}B"
                bitmap.recycle()
            }
            repeat(5) {run ->
                fun files()=context.noBackupFilesDir.listFiles()?.filter {it.name.startsWith("owner-read-cache") && it.isFile} ?: emptyList()
                val before=files().associate {it.name to it.readBytes()}
                val start=System.nanoTime()
                cache.save(scope,"dashboard",JSONObject().put("connectionState","unknown"))
                val writeMs=(System.nanoTime()-start)/1e6
                val changedBytes=files().filter {!(before[it.name]?.contentEquals(it.readBytes()) ?: false)}.sumOf {it.length()}
                samples+="writeRun=$run writeMs=$writeMs changedEncryptedBytes=$changedBytes committedBytes=${files().sumOf {it.length()}}"
            }
            File(context.getExternalFilesDir(null),"cold-cache-metrics.txt").writeText(samples.joinToString("\n"))
        } finally {instrumentation.runOnMainSync {stores.forEach {it.clear()}};session.clear();server.shutdown()}
    }
}
