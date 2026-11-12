package com.inofarm.jogja.core.network

import com.inofarm.jogja.core.contracts.WireContract
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ReportFile(val bytes:ByteArray,val mime:String,val snapshotHash:String,val contentHash:String)

/** Bounded authenticated transfer, tied to the exact accepted snapshot currently reviewed. */
class ReportExportTransport(client:OkHttpClient) {
    private val downloadClient=client.newBuilder().readTimeout(90,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    suspend fun fetch(base:String,token:String,id:String,format:String,hash:String):ReportFile=suspendCancellableCoroutine {continuation->
        WireContract.uuid(id);require(format in setOf("pdf","csv") && Regex("[0-9a-f]{64}").matches(hash))
        val mime=if(format=="pdf") "application/pdf" else "text/csv"
        val url=base.toHttpUrl().resolve("report-exports/$id/$format")!!.newBuilder().addQueryParameter("expected_snapshot_hash",hash).build()
        val request=Request.Builder().url(url).header("Authorization","Bearer $token").header("Accept",mime).build()
        val call=downloadClient.newCall(request);continuation.invokeOnCancellation {call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {if(continuation.isActive) continuation.resumeWithException(e)}
            override fun onResponse(call:Call,response:Response) {
                try {response.use {
                    val body=response.body ?: throw IOException("Unduhan belum tersedia.")
                    if(!response.isSuccessful) {
                        val detail=runCatching {JSONObject(body.byteStream().use {input->val buffer=ByteArray(65536);var count=0;while(count<buffer.size) {val read=input.read(buffer,count,buffer.size-count);if(read<0) break;count+=read};String(buffer,0,count,Charsets.UTF_8)})}.getOrElse {JSONObject().put("message","Unduhan belum bisa disiapkan. Coba lagi nanti.")}
                        throw FinanceApiFailure(response.code,detail)
                    }
                    require(response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()==mime) {"Jenis berkas tidak sesuai. Coba unduh lagi."}
                    require(response.header("X-Report-Snapshot-Hash")==hash) {"Berkas bukan dari laporan yang sedang dibuka. Coba unduh lagi."}
                    val expected=response.header("X-Content-SHA256").orEmpty();require(Regex("[0-9a-f]{64}").matches(expected)) {"Berkas tidak bisa dipastikan utuh. Coba unduh lagi."}
                    val length=body.contentLength();require(length==-1L || length in 1..MAX_BYTES.toLong()) {"Berkas terlalu besar untuk diunduh."}
                    val digest=MessageDigest.getInstance("SHA-256");val output=ByteArrayOutputStream();var count=0
                    body.byteStream().use {input->val buffer=ByteArray(32768);while(true) {val read=input.read(buffer);if(read<0) break;count+=read;if(count>MAX_BYTES) throw IOException("Berkas terlalu besar untuk diunduh (maksimal 16 MB).");digest.update(buffer,0,read);output.write(buffer,0,read)}}
                    require(count>0 && (length<0 || count.toLong()==length)) {"Unduhan terputus. Coba lagi."}
                    val actual=digest.digest().joinToString("") {b->"%02x".format(b)};require(actual==expected) {"Berkas rusak saat diunduh dan tidak disimpan. Coba lagi."}
                    val bytes=output.toByteArray();if(format=="pdf") require(bytes.size>=5 && bytes.copyOfRange(0,5).toString(Charsets.US_ASCII)=="%PDF-") {"Berkas PDF rusak. Coba unduh lagi."}
                    if(continuation.isActive) continuation.resume(ReportFile(bytes,mime,hash,actual))
                }} catch(error:Exception) {if(continuation.isActive) continuation.resumeWithException(error)}
            }
        })
    }
    companion object {const val MAX_BYTES=16*1024*1024}
}
