package com.inofarm.jogja

import com.inofarm.jogja.core.network.FinanceHttpApi
import com.inofarm.jogja.core.network.FinanceApiFailure
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID

class AttachmentHttpTest {
    @Test fun multipartBindsExactMetadataAndBinaryWithoutBase64InEnvelope():Unit=runBlocking {
        val fixture=JSONObject(File("../../contracts/v2/fixtures/attachment-operation.json").readText())
        val operation=fixture.getJSONObject("operation");val bytes=Base64.getDecoder().decode(fixture.getString("fixture_file_base64"))
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setBody("{\"version\":1}"));server.start()
            val result=FinanceHttpApi(true).upload(server.url("/api/v2/").toString(),"synthetic-token",operation) {ByteArrayInputStream(bytes)}
            assertEquals(1,result.getInt("version"));val request=server.takeRequest();assertEquals("/api/v2/attachments",request.path);assertEquals("Bearer synthetic-token",request.getHeader("Authorization"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="));val raw=request.body.readByteArray()
            assertTrue(raw.toList().windowed(bytes.size).any {it.toByteArray().contentEquals(bytes)})
            val text=raw.toString(Charsets.UTF_8);assertTrue(text.contains(operation.toString()));assertFalse(text.contains(fixture.getString("fixture_file_base64")))
        }
    }
    @Test fun streamingDownloadAllowsBinaryAboveJsonMetadataLimit():Unit=runBlocking {
        val bytes=("%PDF-1.4\n%"+"x".repeat(20000)+"\n%%EOF").toByteArray()
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setHeader("Content-Type","application/pdf").setBody(Buffer().write(bytes)));server.start()
            var downloaded:ByteArray?=null
            FinanceHttpApi(true).download(server.url("/api/v2/").toString(),"synthetic-token",UUID.randomUUID().toString(),bytes.size.toLong()) {downloaded=it.readBytes()}
            assertArrayEquals(bytes,downloaded);assertTrue(server.takeRequest().path!!.endsWith("/content"))
        }
    }
    @Test fun streamingDownloadRejectsExtraBytesAndSizeMismatchWithoutUnboundedRead():Unit=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setChunkedBody(Buffer().write(ByteArray(69)),8));server.enqueue(MockResponse().setBody("short"));server.start()
            val api=FinanceHttpApi(true);val base=server.url("/api/v2/").toString();val id=UUID.randomUUID().toString()
            assertThrows(IOException::class.java) {runBlocking {api.download(base,"synthetic-token",id,68) {it.readBytes()}}}
            var consumed=false
            assertThrows(IOException::class.java) {runBlocking {api.download(base,"synthetic-token",id,68) {consumed=true;it.readBytes()}}};assertFalse(consumed)
        }
    }
    @Test fun deniedDownloadKeepsStructuredErrorAndDoesNotFollowRedirect():Unit=runBlocking {
        MockWebServer().use {server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("{\"code\":\"access_denied\",\"message\":\"Akses ditolak\",\"reasons\":[\"Peran berubah\"]}"));server.enqueue(MockResponse().setResponseCode(302).setHeader("Location","https://example.invalid/private"));server.start()
            val api=FinanceHttpApi(true);val base=server.url("/api/v2/").toString();var consumed=false
            val denied=assertThrows(FinanceApiFailure::class.java) {runBlocking {api.download(base,"synthetic-token",UUID.randomUUID().toString(),68) {consumed=true}}}
            assertEquals(403,denied.status);assertEquals("Peran berubah",denied.detail.getJSONArray("reasons").getString(0));assertFalse(consumed)
            val redirected=assertThrows(FinanceApiFailure::class.java) {runBlocking {api.download(base,"synthetic-token",UUID.randomUUID().toString(),68) {consumed=true}}};assertEquals(302,redirected.status);assertFalse(consumed);assertEquals(2,server.requestCount)
        }
    }
}
