package com.inofarm.jogja.feature.customers

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One buyer as the list and the sale form show it. */
data class Customer(val id: String, val type: String, val name: String, val whatsappDisplay: String?, val purchases: Int, val totalKg: String, val source: String) {
    companion object {
        fun from(row: JSONObject) = Customer(row.getString("id"), row.getString("type"), row.getString("name"),
            row.optString("whatsapp_display").takeIf { !row.isNull("whatsapp_display") && it.isNotBlank() },
            row.optInt("purchase_count"), row.optString("total_kg", "0"), row.optString("source"))
    }
}

data class ImportRow(val row: Int, val name: String, val whatsapp: String?)

data class ImportOutcome(val created: Int, val existing: Int, val invalid: Int, val problems: List<Pair<Int, String>>)

/** Farm-wide buyer list (online; buyers are not in the greenhouse sync feed). */
class CustomerRepository(private val vault: FinanceSessionVault, private val api: FinanceApi) {
    private fun check(session: FinanceSession, ticket: Long) {
        check(vault.matches(ticket, session.scope)) { "Akun yang masuk berubah. Muat ulang daftar pembeli." }
        require(session.role == UserRole.MANAGER) { "Daftar pembeli hanya untuk pengelola." }
    }

    suspend fun search(session: FinanceSession, ticket: Long, type: String, q: String, limit: Int = 20): List<Customer> = withContext(Dispatchers.IO) {
        check(session, ticket)
        val query = mutableMapOf("type" to type, "limit" to limit.toString())
        if (q.isNotBlank()) query["q"] = q.trim()
        val data = api.request(session.scope.server, session.token, "customers", query = query).getJSONArray("data")
        (0 until data.length()).map { Customer.from(data.getJSONObject(it)) }
    }

    /** Sends rows in chunks of [chunk] (the server takes up to 1.000 / 128 KiB per request) and sums the outcome. */
    suspend fun importRows(session: FinanceSession, ticket: Long, type: String, rows: List<ImportRow>, dryRun: Boolean, chunk: Int = 200): ImportOutcome =
        withContext(Dispatchers.IO) {
            check(session, ticket)
            var created = 0; var existing = 0; var invalid = 0
            val problems = mutableListOf<Pair<Int, String>>()
            rows.chunked(chunk).forEach { part ->
                val body = JSONObject().put("dry_run", dryRun).put("type", type).put("rows", JSONArray().also { array ->
                    part.forEach { array.put(JSONObject().put("row", it.row).put("name", it.name).put("whatsapp", it.whatsapp ?: JSONObject.NULL)) }
                })
                val result = api.request(session.scope.server, session.token, "customers/import", "POST", body)
                val summary = result.getJSONObject("summary")
                created += summary.getInt("created"); existing += summary.getInt("existing"); invalid += summary.getInt("invalid")
                val lines = result.getJSONArray("rows")
                for (i in 0 until lines.length()) lines.getJSONObject(i).takeIf { it.getString("status") == "invalid" }
                    ?.let { problems += it.getInt("row") to it.optString("reason") }
            }
            ImportOutcome(created, existing, invalid, problems)
        }
}
