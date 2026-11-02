package com.inofarm.jogja

import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.network.*
import com.inofarm.jogja.feature.exports.ReportExportSelection
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.security.MessageDigest
import java.util.UUID

class ReportExportTransportTest {
    private val server=MockWebServer();private val id=UUID.randomUUID().toString();private val hash="a".repeat(64)
    @Before fun start() {server.start()}
    @After fun close() {server.shutdown()}
    private fun response(bytes:String="%PDF-1.4\nfixture\n%%EOF",mime:String="application/pdf",snapshot:String=hash,digest:String?=null):MockResponse {
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") {"%02x".format(it)}
        return MockResponse().setBody(bytes).addHeader("Content-Type",mime).addHeader("X-Report-Snapshot-Hash",snapshot).addHeader("X-Content-SHA256",digest ?: sha)
    }
    private fun download(format:String="pdf")=runBlocking {FinanceHttpApi(true).exportReport(server.url("/api/v2/").toString(),"synthetic-token",id,format,hash)}
    @Test fun verifiedBytesBindAuthenticatedRequestToReviewedSnapshot() {
        server.enqueue(response());val result=download();assertEquals("application/pdf",result.mime);assertEquals(hash,result.snapshotHash)
        val request=server.takeRequest();assertEquals("Bearer synthetic-token",request.getHeader("Authorization"));assertEquals("/api/v2/report-exports/$id/pdf",request.requestUrl!!.encodedPath);assertEquals(hash,request.requestUrl!!.queryParameter("expected_snapshot_hash"))
    }
    @Test fun csvUtf8IsTransferredWithoutNumberOrTextRewriting() {
        val csv="\uFEFFfield,type,value,json_value\r\n/test,string,α,\"\"\"α\"\"\"\r\n";server.enqueue(response(csv,"text/csv; charset=UTF-8"));assertEquals(csv,download("csv").bytes.toString(Charsets.UTF_8))
    }
    @Test fun wrongSnapshotDigestOrMimeNeverYieldAFile() {
        for(response in listOf(response(snapshot="b".repeat(64)),response(digest="0".repeat(64)),response(mime="text/html"))) {server.enqueue(response);assertThrows(IllegalArgumentException::class.java) {download()}}
    }
    @Test fun denialKeepsStructuredErrorAndRedirectIsNotFollowed() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"message\":\"Akses dicabut\"}"));val denied=assertThrows(FinanceApiFailure::class.java) {download()};assertEquals(403,denied.status)
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location",server.url("/unwanted")));assertThrows(FinanceApiFailure::class.java) {download()};assertEquals(2,server.requestCount)
    }
    @Test fun oversizedHeaderAndInvalidPdfAreRejected() {
        server.enqueue(response().setHeader("Content-Length",ReportExportTransport.MAX_BYTES.toLong()+1));assertThrows(IllegalArgumentException::class.java) {download()}
        server.enqueue(response("not a PDF"));assertThrows(IllegalArgumentException::class.java) {download()}
    }
    @Test fun selectionIsAcceptedReportOnlyAndPersistsAcrossPickerRecreation() {
        val fixture=JSONObject(javaClass.getResource("/investment-report-public.json")!!.readText());val data=fixture.getJSONObject("receipt").getJSONObject("data")
        val row=LocalRecord(data.getString("id"),InvestmentContract.REPORT,data.getString("greenhouse_id"),1,1,1,data,data,null,SyncState.ACCEPTED,null)
        val selection=ReportExportSelection.from("scope-fixture",row,"pdf");assertEquals(selection,ReportExportSelection.parse(JSONObject(selection.json().toString())))
        assertEquals("inofarm-investment_report-${row.id}.pdf",selection.filename)
        assertThrows(IllegalArgumentException::class.java) {ReportExportSelection.from("scope-fixture",row.copy(serverVersion=null),"pdf")}
        assertThrows(IllegalArgumentException::class.java) {ReportExportSelection.from("scope-fixture",row.copy(kind=InvestmentContract.SCENARIO),"csv")}
    }
}
