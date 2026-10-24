package com.inofarm.jogja

import androidx.test.platform.app.InstrumentationRegistry
import com.inofarm.jogja.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Opt-in test against the real Laravel HTTP server and dedicated MySQL fixtures. */
class RealBackendTest {
    @Test fun androidRepositoryReadsPersistedDeviceRecordAndHistoricalAssignment() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realBackend") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = SessionStore(context)
        session.clear()
        val api = ApiClient({ session.baseUrl }, { session.readToken() }, true)
        val repository = OwnerRepository(api, session)
        try {
            val base = InstrumentationRegistry.getArguments().getString("backendUrl") ?: "http://127.0.0.1:8001/api/v1/"
            repository.login(base, "integration@example.invalid", "integration-password-only")
            assertNull(repository.config().body)
            assertTrue(repository.history(emptyMap()).items.isEmpty())
            repository.saveConfig(null, com.inofarm.jogja.ui.ConfigDraft().json())
            assertEquals(0, repository.config().body!!.getJSONObject("ppmRanges").length())
            repository.savePhase(null, PlantPhase.Vegetatif)
            assertEquals(PlantPhase.Vegetatif, repository.config().phase)
            repository.saveWorker("Integration worker TEST ONLY")
            val worker = repository.workers().single()
            val clock = OffsetDateTime.parse(api.json("dashboard").getString("serverTime"))
            val local = clock.atZoneSameInstant(ZoneId.of("Asia/Jakarta"))
            val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
            repository.saveShift(JSONObject().put("workerId", worker.id)
                .put("days", JSONArray(listOf(local.dayOfWeek.value)))
                .put("startTime", local.format(timeFormat)).put("endTime", local.plusHours(1).format(timeFormat))
                .put("startDate", local.toLocalDate().toString()).put("endDate", JSONObject.NULL), null)
            assertEquals(1, repository.shifts().size)
            val sourceTime = api.json("dashboard").getString("serverTime")
            val snapshot = JSONObject().put("capturedAt", sourceTime)
                .put("readings", JSONObject().apply { metrics.forEach { put(it.key, JSONObject.NULL) } })
                .put("quality", JSONObject().apply { metrics.forEach { put(it.key, "missing") } })
            val event = JSONObject().put("schemaVersion", "1").put("eventId", UUID.randomUUID().toString())
                .put("deviceId", "11111111-1111-4111-8111-111111111111")
                .put("clockSource", "ntp").put("clockStatus", "trusted")
                .put("confirmedAt", sourceTime).put("measuredAt", JSONObject.NULL)
                .put("phase", "generatif").put("manualPpm", 900).put("sensorSnapshot", snapshot)
            val deviceApi = ApiClient({ session.baseUrl }, { "integration-device-only" }, true)
            val ack = deviceApi.json("device/events/record", "POST", event)
            assertTrue(ack.getBoolean("persisted"))
            assertTrue(deviceApi.json("device/events/record", "POST", event).getBoolean("duplicate"))
            val record = repository.history(emptyMap()).items.single()
            assertEquals(PlantPhase.Generatif, record.phase)
            assertEquals(ValidationStatus.Incomplete, record.status)
            assertEquals(worker.name, record.workerName)
            assertEquals("scheduled", record.assignmentStatus)
            assertEquals(900.0, record.manualPpm, 0.0)
            assertTrue(record.checks.count { it.result == "incomplete" } >= 7)
            repository.saveWorker("Renamed TEST ONLY", worker)
            assertEquals(worker.name, repository.history(emptyMap()).items.single().workerName)
            assertEquals(record.id, repository.dashboard().latestRecord?.id)
            assertTrue(repository.export(emptyMap()).contains(worker.name))
            repository.logout()
            assertFalse(repository.signedIn)
        } finally { session.clear() }
    }
}
