package com.inofarm.jogja

import com.inofarm.jogja.core.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.security.MessageDigest

class SeasonExportTransportTest {
    private val server = MockWebServer()
    private val xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    @Before fun start() { server.start() }
    @After fun close() { server.shutdown() }

    private fun response(bytes: String = "PK-fixture", mime: String = xlsx, digest: String? = null,
                         disposition: String? = "attachment; filename=\"inofarm-keuangan-musim-3-20261125.xlsx\""): MockResponse {
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
        return MockResponse().setBody(bytes).addHeader("Content-Type", mime).addHeader("X-Content-SHA256", digest ?: sha)
            .apply { disposition?.let { addHeader("Content-Disposition", it) } }
    }
    private fun fetch(sequence: String = "3", parts: Set<String> = setOf("summary", "costs", "sales")) =
        runBlocking { FinanceHttpApi(true).exportSeason(server.url("/api/v2/").toString(), "synthetic-token", sequence, parts) }

    @Test fun buildsQueryWithParts() {
        server.enqueue(response())
        val file = fetch("3", setOf("sales", "summary"))
        assertEquals("PK-fixture", file.bytes.toString(Charsets.UTF_8))
        val request = server.takeRequest()
        assertEquals("Bearer synthetic-token", request.getHeader("Authorization"))
        assertEquals("/api/v2/reports/season-export", request.requestUrl!!.encodedPath)
        assertEquals("3", request.requestUrl!!.queryParameter("sequence"))
        assertEquals("summary,sales", request.requestUrl!!.queryParameter("parts"))
        server.enqueue(response()); fetch("all", setOf("costs"))
        assertEquals("all", server.takeRequest().requestUrl!!.queryParameter("sequence"))
    }

    @Test fun rejectsEmptyParts() {
        assertThrows(IllegalArgumentException::class.java) { fetch(parts = emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { fetch(parts = setOf("summary", "profit")) }
        assertThrows(IllegalArgumentException::class.java) { fetch(sequence = "0") }
        assertThrows(IllegalArgumentException::class.java) { fetch(sequence = "3&x=1") }
        assertEquals(0, server.requestCount)
    }

    @Test fun rejectsWrongContentType() {
        server.enqueue(response(mime = "text/html"))
        assertThrows(IllegalArgumentException::class.java) { fetch() }
    }

    @Test fun rejectsHashMismatch() {
        server.enqueue(response(digest = "0".repeat(64)))
        assertThrows(IllegalArgumentException::class.java) { fetch() }
        server.enqueue(response(digest = "nothex"))
        assertThrows(IllegalArgumentException::class.java) { fetch() }
    }

    @Test fun usesServerFileName() {
        server.enqueue(response())
        assertEquals("inofarm-keuangan-musim-3-20261125.xlsx", fetch().fileName)
        server.enqueue(response(disposition = "attachment; filename=\"../../evil.xlsx\""))
        assertEquals("inofarm-keuangan.xlsx", fetch().fileName)
        server.enqueue(response(disposition = null))
        assertEquals("inofarm-keuangan.xlsx", fetch().fileName)
    }

    @Test fun denialKeepsStructuredErrorAndRedirectIsNotFollowed() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"message\":\"Akses dicabut\"}"))
        assertEquals(403, assertThrows(FinanceApiFailure::class.java) { fetch() }.status)
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/unwanted")))
        assertThrows(FinanceApiFailure::class.java) { fetch() }
        assertEquals(2, server.requestCount)
    }

    @Test fun oversizedFileIsRejected() {
        server.enqueue(response().setHeader("Content-Length", SeasonExportTransport.MAX_BYTES.toLong() + 1))
        assertThrows(IllegalArgumentException::class.java) { fetch() }
    }
}
