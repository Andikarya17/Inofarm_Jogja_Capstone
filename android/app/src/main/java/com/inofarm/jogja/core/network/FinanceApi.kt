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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FinanceApiFailure(val status: Int, val detail: JSONObject, val retryAfterSeconds: Long? = null) : Exception(detail.optString("message", "Permintaan gagal ($status).")) {
    val code: String get() = detail.optString("code")
}
interface FinanceApi {
    fun validateBase(base: String): String
    suspend fun request(base: String, token: String?, path: String, method: String = "GET", body: JSONObject? = null, query: Map<String,String> = emptyMap()): JSONObject
}

class FinanceHttpApi(private val allowHttp: Boolean, private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()) : FinanceApi {
    override fun validateBase(base: String): String {
        val normalized=ApiClient({base},{null},allowHttp).validateBase(base)
        require(normalized.toHttpUrl().encodedPath.endsWith("/v2/")) { "Gunakan URL API operasional yang berakhir /v2/." }
        return normalized
    }
    override suspend fun request(base: String, token: String?, path: String, method: String, body: JSONObject?, query: Map<String,String>): JSONObject = suspendCancellableCoroutine { continuation ->
        require(!path.startsWith('/') && !path.contains("..") && !path.contains("://")) { "Path API harus relatif." }
        val url=validateBase(base).toHttpUrl().resolve(path)!!.newBuilder().apply { query.forEach { (key,value) -> addQueryParameter(key,value) } }.build()
        val payload=if(method in setOf("POST","PATCH","PUT","DELETE")) (body?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()) else null
        val request=Request.Builder().url(url).header("Accept","application/json").apply { token?.let { header("Authorization","Bearer $it") }; method(method,payload) }.build()
        val call=client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call,e: IOException) { if(continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call,response: Response) {
                try {
                    response.use {
                        val source=response.body?.source()
                        if(source?.request(2L*1024*1024+1) == true) throw IOException("Respons terlalu besar; kurangi ukuran halaman.")
                        val text=source?.readUtf8().orEmpty()
                        val json=if(text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { throw IOException("Respons server tidak dapat dibaca.") }
                        if(!response.isSuccessful) throw FinanceApiFailure(response.code,json,response.header("Retry-After")?.toLongOrNull())
                        if(continuation.isActive) continuation.resume(json)
                    }
                } catch(error: Exception) { if(continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }
}
