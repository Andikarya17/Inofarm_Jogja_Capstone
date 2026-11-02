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
        is String -> JSONObject.quote(value).replace("\\/", "/").replace("\\u2028", "\u2028").replace("\\u2029", "\u2029")
        is Boolean, is Int, is Long -> value.toString()
        else -> error("Desimal harus string; tipe canonical tidak didukung.")
    }
    fun hash(operation: JSONObject): String = MessageDigest.getInstance("SHA-256")
        .digest(canonical(operation).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
