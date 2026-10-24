package com.inofarm.jogja

import com.inofarm.jogja.data.*
import com.inofarm.jogja.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ContractTest {
    @Test fun unfinishedDraftSurvivesSerializationAndTechnicalLimitsMatchBackend() {
        val draft=ConfigDraft(early="unfinished",schedules=listOf(ScheduleDraft(setOf(1,2),"08:")),ppm=mapOf("vegetatif" to BoundDraft("-","1000")))
        assertEquals(draft,ConfigDraft.restore(draft.serialize()))
        for(d in listOf(ConfigDraft(early="1441"),ConfigDraft(late="-1"),ConfigDraft(stale="0"),ConfigDraft(cadence="1"),ConfigDraft(alarm="3601"),ConfigDraft(sensorAge="604801"),ConfigDraft(ppm=mapOf("vegetatif" to BoundDraft("-1","1000"))),ConfigDraft(schedules=List(15) { ScheduleDraft(setOf(1),"08:00") }))) assertThrows(Exception::class.java) { d.json() }
        val sameDays=ConfigDraft(schedules=listOf(ScheduleDraft(setOf(1),"08:00"),ScheduleDraft(setOf(1),"16:00")))
        assertEquals(2,sameDays.json().getJSONArray("schedules").length())
        assertEquals("60 detik",ageAt("2026-10-10T00:00:00Z","2026-10-10T00:01:00Z"))
        assertEquals("Belum dapat ditentukan",ageAt(null,"2026-10-10T00:01:00Z"))
    }
    @Test fun slowRequestsCancelAndTimeoutWithoutBlockingNextRequest() = runBlocking {
        val server=MockWebServer();server.start()
        try {
            val api=ApiClient({server.url("/api/v1/").toString()},{null},true)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job=launch(Dispatchers.IO) { api.json("dashboard") }
            assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))
            val start=System.nanoTime();job.cancelAndJoin()
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            try { withTimeout(150) { api.json("config") }; fail("Expected timeout") } catch(_:TimeoutCancellationException) { }
            server.enqueue(MockResponse().setBody("{\"ok\":true}"))
            assertTrue(api.json("dashboard").getBoolean("ok"))
        } finally { server.shutdown() }
        Unit
    }
    @Test fun emptyConfigurationContainsNoProductionThresholds() {
        val json = ConfigDraft().json()
        assertEquals(0,json.getJSONArray("schedules").length()); assertEquals(0,json.getJSONObject("ppmRanges").length())
        assertTrue(json.isNull("earlyMinutes")); assertTrue(json.isNull("sensorMaxAgeSeconds")); assertFalse(json.has("activePhase"))
    }
    @Test fun phaseParameterEditingDoesNotSetActivePhase() {
        val json = ConfigDraft(ppm = mapOf("vegetatif" to BoundDraft("800","1000"), "generatif" to BoundDraft())).json()
        assertEquals(800.0,json.getJSONObject("ppmRanges").getJSONObject("vegetatif").getDouble("min"),0.0)
        assertFalse(json.has("phase")); assertFalse(json.has("activePhase")); assertFalse(json.getJSONObject("ppmRanges").has("generatif"))
    }
    @Test fun invalidBoundsAndTimesCannotBeSent() {
        assertThrows(IllegalArgumentException::class.java) { ConfigDraft(sensors=mapOf("solutionPh" to BoundDraft("7","5"))).json() }
        assertThrows(IllegalArgumentException::class.java) { ConfigDraft(schedules=listOf(ScheduleDraft(emptySet(),"08:00"))).json() }
        assertThrows(Exception::class.java) { ConfigDraft(schedules=listOf(ScheduleDraft(setOf(1),"25:00"))).json() }
        assertThrows(IllegalArgumentException::class.java) { finite(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { PlantPhase.parse("flowering") }
        assertThrows(IllegalArgumentException::class.java) { ValidationStatus.parse("success") }
    }
    @Test fun timestampAndMultipleReasonsArePreserved() {
        val json=JSONObject().put("id",1).put("clock_trusted",true).put("phase","generatif").put("manual_ppm",500).put("received_at","2026-10-11T01:00:00Z").put("config_version_id",2).put("phase_version_id",3)
            .put("body",JSONObject().put("confirmedAt","2026-10-10T23:59:59+07:00"))
            .put("assignment",JSONObject().put("status","scheduled").put("workerNameSnapshot","Old name"))
            .put("validation_result",JSONObject().put("status","incomplete").put("ownerPhaseSnapshot",JSONObject().put("phase","vegetatif")).put("checks",JSONArray().put(JSONObject().put("result","warning").put("code","phase_mismatch").put("message","Mismatch")).put(JSONObject().put("result","issue").put("code","ppm_low").put("message","Low PPM"))))
        val record=ApiModels.record(json)
        assertEquals(2,record.checks.size); assertEquals("Old name",record.workerName); assertEquals(PlantPhase.Generatif,record.phase); assertEquals(PlantPhase.Vegetatif,record.ownerPhase)
        assertTrue(displayTime(record.sourceAt).contains("23:59:59 WIB"))
    }
    @Test fun qualityAndMissingNumbersAreNotNormal() {
        val readings=JSONObject(); val quality=JSONObject()
        metrics.forEach { readings.put(it.key,JSONObject.NULL); quality.put(it.key,"missing") }
        val json=JSONObject().put("clock_trusted",false).put("body",JSONObject().put("sensorSnapshot",JSONObject().put("capturedAt",JSONObject.NULL).put("readings",readings).put("quality",quality))).put("received_at","2026-10-10T00:00:00Z")
        assertNull(ApiModels.telemetry(json).readings.getValue("solutionPh").value)
        quality.put("solutionPh","ok")
        assertThrows(IllegalArgumentException::class.java) { ApiModels.telemetry(json) }
    }
    @Test fun publicBaseAndAccessErrorsAreExplicit() = runBlocking {
        val server=MockWebServer(); server.start()
        try {
            val api=ApiClient({server.url("/nested/api/v1/").toString()},{"test-only-token"},true)
            server.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"Sesi berakhir\"}"))
            try { api.json("dashboard"); fail("Expected unauthorized") } catch(error:ApiException) { assertEquals(401,error.code) }
            val request=server.takeRequest(); assertEquals("/nested/api/v1/dashboard",request.path); assertEquals("Bearer test-only-token",request.getHeader("Authorization"))
            assertThrows(IllegalArgumentException::class.java) { api.validateBase("https://user:password@example.invalid/api/") }
            val release=ApiClient({""},{null},false); assertThrows(IllegalArgumentException::class.java) { release.validateBase("http://example.invalid/api/") }
        } finally { server.shutdown() }
        Unit
    }
}
