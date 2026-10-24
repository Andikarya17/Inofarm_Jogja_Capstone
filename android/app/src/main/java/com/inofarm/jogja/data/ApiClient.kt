package com.inofarm.jogja.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String): Exception(message)
data class ApiSession(val base: String, val token: String?)
class ApiClient(private val baseUrl: () -> String, private val token: () -> String?, private val allowHttp: Boolean, private val testHttp: OkHttpClient? = null) {
    private val http by lazy { testHttp ?: OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).followRedirects(false).build() }
    fun validateBase(value: String): String {
        val url = value.trim().trimEnd('/').plus('/').toHttpUrl()
        require(url.scheme == "https" || (allowHttp && url.scheme == "http")) { "Gunakan HTTPS; HTTP hanya pada APK debug lokal" }
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "URL API tidak boleh mengandung credential/query" }
        return url.toString()
    }
    suspend fun raw(path: String, method: String = "GET", body: JSONObject? = null, query: Map<String, String> = emptyMap(), session: ApiSession? = null): String = withContext(Dispatchers.IO) { suspendCancellableCoroutine { continuation ->
        val base = validateBase(session?.base ?: baseUrl()).toHttpUrl()
        val url = base.resolve(path)!!.newBuilder().apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val payload = body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).header("Accept", "application/json").apply {
            (if(session != null) session.token else token())?.let { header("Authorization", "Bearer $it") }
            method(method, if (method in listOf("POST", "PUT", "DELETE")) payload ?: "{}".toRequestBody("application/json".toMediaType()) else null)
        }.build()
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
          override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(
                if (e is ConnectException) IOException("Server belum dapat dihubungi. Periksa koneksi dan coba lagi.", e) else e
            )
          }
          override fun onResponse(call: Call, response: Response) {
           try { response.use {
            val text = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val error = runCatching { JSONObject(text) }.getOrNull()
                val fieldErrors = error?.optJSONObject("errors")?.let { fields -> fields.keys().asSequence().joinToString("\n") { key -> "$key: ${fields.getJSONArray(key).join(", ")}" } }
                throw ApiException(response.code, fieldErrors ?: error?.optString("message")?.takeIf { it.isNotBlank() } ?: "API gagal (${response.code})")
            }
            if (continuation.isActive) continuation.resume(text)
           } } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
          }
        })
    } }
    suspend fun json(path: String, method: String = "GET", body: JSONObject? = null, query: Map<String, String> = emptyMap(), session: ApiSession? = null): JSONObject {
        val text = raw(path, method, body, query, session)
        return withContext(Dispatchers.Default) { JSONObject(text) }
    }
}
