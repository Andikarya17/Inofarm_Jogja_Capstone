package com.inofarm.jogja.core.network

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

data class ExportFile(val bytes: ByteArray, val fileName: String)

/** Bounded authenticated download of the season finance workbook (GET reports/season-export). */
class SeasonExportTransport(client: OkHttpClient) {
    private val downloadClient = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun fetch(base: String, token: String, sequence: String, parts: Set<String>): ExportFile = suspendCancellableCoroutine { continuation ->
        require(SEQUENCE.matches(sequence)) { "Musim tidak dikenal." }
        require(parts.isNotEmpty() && parts.all { it in PARTS }) { "Pilih minimal satu isi file." }
        val ordered = PARTS.filter { it in parts }.joinToString(",")
        val url = base.toHttpUrl().resolve("reports/season-export")!!.newBuilder()
            .addQueryParameter("sequence", sequence).addQueryParameter("parts", ordered).build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", MIME).build()
        val call = downloadClient.newCall(request); continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try { response.use {
                    val body = response.body ?: throw IOException("Unduhan belum tersedia.")
                    if (!response.isSuccessful) {
                        val detail = runCatching { JSONObject(body.byteStream().use { input ->
                            val buffer = ByteArray(65536); var count = 0
                            while (count < buffer.size) { val read = input.read(buffer, count, buffer.size - count); if (read < 0) break; count += read }
                            String(buffer, 0, count, Charsets.UTF_8) }) }.getOrElse { JSONObject().put("message", "Excel belum bisa disiapkan. Coba lagi nanti.") }
                        throw FinanceApiFailure(response.code, detail)
                    }
                    require(response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase() == MIME) { "Jenis berkas tidak sesuai. Coba unduh lagi." }
                    val expected = response.header("X-Content-SHA256").orEmpty()
                    require(Regex("[0-9a-f]{64}").matches(expected)) { "Berkas tidak bisa dipastikan utuh. Coba unduh lagi." }
                    val length = body.contentLength()
                    require(length == -1L || length in 1..MAX_BYTES.toLong()) { "Berkas terlalu besar untuk diunduh." }
                    val digest = MessageDigest.getInstance("SHA-256"); val output = ByteArrayOutputStream(); var count = 0
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            val read = input.read(buffer); if (read < 0) break
                            count += read; if (count > MAX_BYTES) throw IOException("Berkas terlalu besar untuk diunduh (maksimal 8 MB).")
                            digest.update(buffer, 0, read); output.write(buffer, 0, read)
                        }
                    }
                    require(count > 0 && (length < 0 || count.toLong() == length)) { "Unduhan terputus. Coba lagi." }
                    require(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Berkas rusak saat diunduh dan tidak disimpan. Coba lagi." }
                    if (continuation.isActive) continuation.resume(ExportFile(output.toByteArray(), fileName(response.header("Content-Disposition"))))
                } } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
        })
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val FALLBACK_NAME = "inofarm-keuangan.xlsx"
        /** Display order of the workbook sheets; the query always lists parts in this order. */
        val PARTS = listOf("summary", "costs", "sales")
        private val SEQUENCE = Regex("all|[1-9][0-9]{0,8}")
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}\\.xlsx")
        private val DISPOSITION = Regex("filename=\"([^\"]*)\"")

        /** The server's name when it is a plain file name; anything with a path or odd characters falls back. */
        internal fun fileName(disposition: String?): String =
            disposition?.let { DISPOSITION.find(it)?.groupValues?.get(1) }?.takeIf { SAFE_NAME.matches(it) } ?: FALLBACK_NAME
    }
}
