package com.inofarm.jogja.feature.admin

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.sales.SalePricing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/** One active per-kg price for the final consumer, as the server stores it ([quality] is `saleable` or `defective`). */
data class ConsumerPrice(val id: String, val varietyName: String, val quality: String, val pricePerKg: String, val version: Int, val updatedAt: String?) {
    companion object {
        fun from(row: JSONObject) = ConsumerPrice(row.getString("id"), row.getString("variety_name"), row.getString("quality"),
            row.getString("price_per_kg"), row.getInt("version"), row.optString("updated_at").takeIf { !row.isNull("updated_at") && it.isNotBlank() })
    }
}

/**
 * What the edit sheet holds: [price] is the text as typed (comma as the decimal mark). [version] is null for a new price.
 * [active] is always sent on an update, because leaving it out would turn a deactivated price back on.
 */
data class ConsumerPriceDraft(val id: String?, val varietyName: String, val quality: String, val price: String, val version: Int?, val active: Boolean = true) {
    /** Price as the wire string with two decimals; throws [IllegalArgumentException] with a message for the form. */
    fun wirePrice(): String {
        val text = price.trim().replace(',', '.')
        require(text.isNotEmpty()) { "Isi harga per kg." }
        require(!text.startsWith("-")) { "Harga tidak boleh negatif." }
        val value = runCatching { WireContract.decimal(text, 2, 16) }.getOrElse { throw IllegalArgumentException("Isi harga IDR, maksimal 2 angka di belakang koma.") }
        require(value.signum() > 0) { "Harga harus lebih dari nol." }
        return value.setScale(2).toPlainString()
    }
}

object ConsumerPrices {
    /** Unique variety names of every greenhouse (same normalisation as the server), first spelling kept, sorted. Inactive rows are skipped. */
    fun varietyNames(rows: List<JSONObject>): List<String> = rows.filter { it.optBoolean("active", true) }
        .map { it.optString("name").trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }
        .distinctBy(SalePricing::key).sortedBy(SalePricing::key)

    fun find(prices: List<ConsumerPrice>, varietyName: String, quality: String): ConsumerPrice? =
        prices.find { SalePricing.key(it.varietyName) == SalePricing.key(varietyName) && it.quality == quality }

    /** The money shown for a stored price, such as "Rp34.000/kg". */
    fun display(price: ConsumerPrice) = com.inofarm.jogja.feature.financehome.rupiah(BigDecimal(price.pricePerKg)) + "/kg"

    /** A 409 (duplicate price, or a version changed elsewhere): the list and its versions must be read again. */
    fun isConflict(failure: Throwable) = failure is com.inofarm.jogja.core.network.FinanceApiFailure && failure.status == 409

    fun operation(draft: ConsumerPriceDraft, epoch: String): JSONObject {
        val payload = JSONObject().put("variety_name", draft.varietyName.trim().replace(Regex("\\s+"), " ")).put("quality", draft.quality)
            .put("price_per_kg", draft.wirePrice())
        if (draft.version != null) payload.put("record_state", if (draft.active) "active" else "inactive")
        return JSONObject().put("schema_version", "2").put("sync_epoch", epoch).put("operation_id", UUID.randomUUID().toString())
            .put("entity_id", draft.id ?: UUID.randomUUID().toString()).put("entity_type", "consumer_price")
            .put("action", if (draft.version == null) "create" else "update").put("base_version", draft.version ?: JSONObject.NULL).put("payload", payload)
    }
}

/** Online reads and writes of the per-kg consumer prices; like other admin writes they need a connection. */
class ConsumerPriceRepository(private val vault: FinanceSessionVault, private val api: FinanceApi) {
    private fun check(session: FinanceSession, ticket: Long) {
        check(vault.matches(ticket, session.scope)) { "Akun yang masuk berubah. Muat ulang harga perorangan." }
        require(session.role == UserRole.MANAGER) { "Harga perorangan hanya untuk pengelola." }
    }

    suspend fun list(session: FinanceSession, ticket: Long): List<ConsumerPrice> = withContext(Dispatchers.IO) {
        check(session, ticket)
        val data = api.request(session.scope.server, session.token, "consumer-prices").getJSONArray("data")
        (0 until data.length()).map { ConsumerPrice.from(data.getJSONObject(it)) }
    }

    /** Variety names of all greenhouses, whole list read page by page. */
    suspend fun varietyNames(session: FinanceSession, ticket: Long): List<String> = withContext(Dispatchers.IO) {
        check(session, ticket)
        val rows = mutableListOf<JSONObject>(); val seen = mutableSetOf<String>(); var cursor: String? = null
        do {
            val query = mutableMapOf("limit" to "100"); cursor?.let { query["cursor"] = it }
            val page = api.request(session.scope.server, session.token, "varieties", query = query)
            val data = page.getJSONArray("data"); repeat(data.length()) { rows += data.getJSONObject(it) }
            cursor = page.optString("next_cursor").takeUnless { page.isNull("next_cursor") || it.isBlank() }
            check(cursor == null || seen.add(cursor)) { "Daftar varietas tidak bisa dimuat sampai habis. Coba lagi." }
        } while (cursor != null)
        ConsumerPrices.varietyNames(rows)
    }

    suspend fun save(session: FinanceSession, ticket: Long, draft: ConsumerPriceDraft): ConsumerPrice = withContext(Dispatchers.IO) {
        check(session, ticket)
        val epoch = api.request(session.scope.server, session.token, "sync/status").getString("sync_epoch")
        WireContract.uuid(epoch)
        val operation = ConsumerPrices.operation(draft, epoch)
        val path = if (draft.version == null) "consumer-prices" else "consumer-prices/${operation.getString("entity_id")}"
        ConsumerPrice.from(api.request(session.scope.server, session.token, path, if (draft.version == null) "POST" else "PATCH", operation).getJSONObject("data"))
    }
}
