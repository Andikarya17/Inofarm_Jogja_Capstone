package com.inofarm.jogja.core.network

import com.inofarm.jogja.data.ApiClient
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.RequestBody
import okhttp3.MultipartBody
import okio.BufferedSink
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.FilterInputStream
import com.inofarm.jogja.core.contracts.AttachmentContract
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FinanceApiFailure(val status: Int, val detail: JSONObject, val retryAfterSeconds: Long? = null) : Exception(detail.optString("message", "Permintaan gagal ($status).")) {
    val code: String get() = detail.optString("code")
}
interface FinanceApi {
    fun validateBase(base: String): String
    suspend fun request(base: String, token: String?, path: String, method: String = "GET", body: JSONObject? = null, query: Map<String,String> = emptyMap()): JSONObject
    suspend fun upload(base:String,token:String,envelope:JSONObject,source:()->InputStream):JSONObject = error("Transport lampiran belum tersedia.")
    suspend fun download(base:String,token:String,id:String,bytes:Long,consume:(InputStream)->Unit):Unit = error("Transport berkas belum tersedia.")
}

class FinanceHttpApi(private val allowHttp: Boolean, private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()) : FinanceApi {
    override fun validateBase(base: String): String {
        val normalized=ApiClient({base},{null},allowHttp).validateBase(base)
        require(normalized.toHttpUrl().encodedPath.endsWith("/v2/")) { "Gunakan URL API operasional yang berakhir /v2/." }
        return normalized
    }
    override suspend fun request(base: String, token: String?, path: String, method: String, body: JSONObject?, query: Map<String,String>): JSONObject {
        require(!path.startsWith('/') && !path.contains("..") && !path.contains("://")) { "Path API harus relatif." }
        val url=validateBase(base).toHttpUrl().resolve(path)!!.newBuilder().apply { query.forEach { (key,value) -> addQueryParameter(key,value) } }.build()
        val payload=if(method in setOf("POST","PATCH","PUT","DELETE")) (body?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()) else null
        val request=Request.Builder().url(url).header("Accept","application/json").apply { token?.let { header("Authorization","Bearer $it") }; method(method,payload) }.build()
        return json(request)
    }
    override suspend fun upload(base:String,token:String,envelope:JSONObject,source:()->InputStream):JSONObject {
        require(envelope.getString("entity_type") in AttachmentContract.kinds && envelope.getString("action")=="create")
        val metadata=envelope.getJSONObject("payload");val size=metadata.getLong("bytes");require(size in 1..AttachmentContract.MAX_SUPPORTED_BYTES)
        require(envelope.toString().toByteArray(Charsets.UTF_8).size<=16384)
        val file=object:RequestBody() {
            override fun contentType()=metadata.getString("mime").toMediaType()
            override fun contentLength()=size
            override fun writeTo(sink:BufferedSink) {
                source().use {input -> val buffer=ByteArray(32768);var sent=0L;while(true) {
                    val count=input.read(buffer);if(count<0) break;sent+=count;if(sent>size) throw IOException("Ukuran salinan berubah.");sink.write(buffer,0,count)
                };if(sent!=size) throw IOException("Salinan berkas belum lengkap.")}
            }
        }
        val multipart=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("operation",envelope.toString()).addFormDataPart("file","evidence.bin",file).build()
        return json(Request.Builder().url(validateBase(base)+"attachments").header("Accept","application/json").header("Authorization","Bearer $token").post(multipart).build())
    }
    override suspend fun download(base:String,token:String,id:String,bytes:Long,consume:(InputStream)->Unit):Unit = suspendCancellableCoroutine {continuation ->
        com.inofarm.jogja.core.contracts.WireContract.uuid(id);require(bytes in 1..AttachmentContract.MAX_SUPPORTED_BYTES)
        val request=Request.Builder().url(validateBase(base)+"attachments/$id/content").header("Authorization","Bearer $token").header("Accept","application/json, application/octet-stream").build()
        val call=client.newCall(request);continuation.invokeOnCancellation {call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {if(continuation.isActive) continuation.resumeWithException(e)}
            override fun onResponse(call:Call,response:Response) {
                try {response.use {
                    if(!response.isSuccessful) throw FinanceApiFailure(response.code,readJson(response),response.header("Retry-After")?.toLongOrNull())
                    val body=response.body ?: throw IOException("Berkas belum tersedia.")
                    if(body.contentLength()>=0 && body.contentLength()!=bytes) throw IOException("Ukuran unduhan tidak sesuai metadata.")
                    val bounded=object:FilterInputStream(body.byteStream()) {
                        var count=0L
                        private fun checked(size:Int):Int {if(size>0) {count+=size;if(count>bytes) throw IOException("Unduhan melebihi metadata.")};return size}
                        override fun read():Int=super.read().also {if(it>=0) checked(1)}
                        override fun read(buffer:ByteArray,offset:Int,length:Int)=checked(`in`.read(buffer,offset,length))
                    }
                    bounded.use {consume(it);if(it.count!=bytes) throw IOException("Unduhan belum diterima utuh.")}
                    if(continuation.isActive) continuation.resume(Unit)
                }} catch(error:Exception) {if(continuation.isActive) continuation.resumeWithException(error)}
            }
        })
    }
    private fun readJson(response:Response):JSONObject {
        val source=response.body?.source()
        if(source?.request(2L*1024*1024+1)==true) throw IOException("Respons terlalu besar; kurangi ukuran halaman.")
        val text=source?.readUtf8().orEmpty()
        return if(text.isBlank()) JSONObject() else runCatching {JSONObject(text)}.getOrElse {throw IOException("Respons server tidak dapat dibaca.")}
    }
    private suspend fun json(request:Request):JSONObject = suspendCancellableCoroutine {continuation ->
        val call=client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call,e: IOException) { if(continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call,response: Response) {
                try {
                    response.use {
                        val json=readJson(response)
                        if(!response.isSuccessful) throw FinanceApiFailure(response.code,json,response.header("Retry-After")?.toLongOrNull())
                        if(continuation.isActive) continuation.resume(json)
                    }
                } catch(error: Exception) { if(continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }
}
