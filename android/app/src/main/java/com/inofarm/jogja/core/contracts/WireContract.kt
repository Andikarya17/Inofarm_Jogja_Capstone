package com.inofarm.jogja.core.contracts

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID

enum class UserRole(val wire: String) { WORKER("worker"), MANAGER("manager");
    companion object { fun parse(value: String) = entries.single { it.wire == value } }
}
enum class WorkState(val wire: String) { COMPLETED("completed"), SKIPPED("skipped") }
enum class SyncState { LOCAL, QUEUED, ACCEPTED, CONFLICT, REJECTED, AUTH_REQUIRED, SCOPE_REVOKED, EPOCH_REVIEW }

object WireContract {
    const val SCHEMA_VERSION = "2"
    fun uuid(value: String): String {
        require(UUID.fromString(value).toString() == value) { "UUID harus canonical huruf kecil." }
        return value
    }
    fun decimal(value: String, scale: Int = 6, precision: Int = 20): BigDecimal {
        require(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?").matches(value)) { "Gunakan desimal tanpa pemisah ribuan." }
        val parts = value.removePrefix("-").split('.')
        require(parts[0].length <= precision - scale && (parts.getOrNull(1)?.length ?: 0) <= scale) { "Angka melebihi presisi/skala." }
        return BigDecimal(value)
    }
    fun canonical(value: Any?): String = when(value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { canonical(it) + ":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> quote(value)
        is Boolean, is Int, is Long -> value.toString()
        else -> error("Desimal harus string; tipe canonical tidak didukung.")
    }
    /** org.json quote differs across JVM/Android; wire escaping must not depend on that implementation. */
    private fun quote(value:String):String=buildString {
        append('"');var index=0
        while(index<value.length) {
            val char=value[index++]
            when(char) {
                '"' -> append("\\\"");'\\' -> append("\\\\");'\b' -> append("\\b");'\t' -> append("\\t");'\n' -> append("\\n");'\u000c' -> append("\\f");'\r' -> append("\\r")
                else -> when {
                    char.code<32 -> append("\\u").append(char.code.toString(16).padStart(4,'0'))
                    char.isHighSurrogate() -> {require(index<value.length && value[index].isLowSurrogate()) {"Teks memuat pasangan Unicode yang tidak lengkap."};append(char);append(value[index++])}
                    char.isLowSurrogate() -> throw IllegalArgumentException("Teks memuat pasangan Unicode yang tidak lengkap.")
                    else -> append(char)
                }
            }
        }
        append('"')
    }
    fun hash(operation: JSONObject): String = MessageDigest.getInstance("SHA-256")
        .digest(canonical(operation).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
