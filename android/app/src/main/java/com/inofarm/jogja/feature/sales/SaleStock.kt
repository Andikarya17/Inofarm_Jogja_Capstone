package com.inofarm.jogja.feature.sales

import org.json.JSONObject
import java.math.BigDecimal
import java.time.OffsetDateTime

data class StockOption(val groupId: String, val gradeId: String, val varietyName: String, val quality: String?, val availableKg: BigDecimal, val gradeName: String = "")
data class FifoTake(val harvestId: String, val harvestItemId: String, val occurredAt: String, val kg: BigDecimal)
/** Preview of what the server will take for one line: oldest harvest first, [shortfallKg] > 0 when stock is not enough. */
data class StockAfter(val lineId: String, val groupId: String, val gradeId: String, val takes: List<FifoTake>, val shortfallKg: BigDecimal, val remainingKg: BigDecimal)

/**
 * Sellable stock per planting group and grade, built from the cached harvests and their balances. The sale form
 * picks a group and a grade; the server allocates harvest items FIFO (occurred_at, harvest id, item id), so this
 * class mirrors that order for the preview only.
 */
class SaleStock(harvests: List<JSONObject>, balances: List<JSONObject>, private val groups: List<JSONObject>, private val varieties: List<JSONObject>, private val grades: List<JSONObject>) {
    private class Item(val id: String, val harvestId: String, val groupId: String, val gradeId: String, val gradeName: String, val quality: String?, val occurredAt: String, val available: BigDecimal, val active: Boolean)

    private val items: List<Item> = run {
        val byItem = balances.associateBy { it.optString("id") }
        harvests.flatMap { harvest ->
            val rows = harvest.optJSONArray("items") ?: return@flatMap emptyList()
            (0 until rows.length()).map { rows.getJSONObject(it) }.map { item ->
                val balance = byItem[item.optString("id")]?.takeIf { it.optLong("harvest_version") == harvest.optLong("version") }
                val weight = amount(item.optString("weight_kg"))
                val stale = byItem[item.optString("id")] != null && balance == null
                // A balance row of another harvest version is unknown stock (0); the weight is assumed only when no row exists.
                val available = if (balance != null) amount(balance.optString("available_weight_kg")) else if (stale) BigDecimal.ZERO else weight
                Item(item.optString("id"), harvest.optString("id"), harvest.optString("planting_group_id"), item.optString("grade_id"), item.optString("grade_name"),
                    item.optString("quality").takeIf { it.isNotBlank() && !item.isNull("quality") }, harvest.optString("occurred_at"), available,
                    harvest.optString("record_state") == "active" && (balance == null || balance.optBoolean("active", true)))
            }
        }
    // Tiebreak on equal occurred_at uses the public harvest id; the server uses harvests.id, so only identical timestamps could differ.
    }.sortedWith(compareBy<Item>({ instant(it.occurredAt) }, { it.harvestId }, { it.id }))

    private fun seasonItems(seasonId: String): List<Item> {
        val inSeason = groups.filter { it.optString("season_id") == seasonId }.map { it.optString("id") }.toSet()
        return items.filter { it.active && it.groupId in inSeason }
    }

    fun options(seasonId: String): List<StockOption> = seasonItems(seasonId).groupBy { it.groupId to it.gradeId }.map { (key, rows) ->
        val group = groups.find { it.optString("id") == key.first }
        val variety = varieties.find { it.optString("id") == group?.optString("variety_id") }?.optString("name").orEmpty()
        val gradeName = grades.find { it.optString("id") == key.second }?.optString("name") ?: rows.first().gradeName
        StockOption(key.first, key.second, variety, rows.firstNotNullOfOrNull { it.quality }, rows.fold(BigDecimal.ZERO) { sum, row -> sum.add(row.available) }, gradeName)
    }.filter { it.availableKg.signum() > 0 }

    /** Walks [lines] in order and takes kg from the oldest harvest items; lines of the same group and grade share one pool. */
    fun fifoPreview(seasonId: String, lines: List<SaleLine>): List<StockAfter> {
        val pool = seasonItems(seasonId).associate { it.id to it.available }.toMutableMap()
        return lines.map { line ->
            var need = runCatching { SaleDraft.decimal(line.quantity, true) }.getOrDefault(BigDecimal.ZERO)
            val takes = mutableListOf<FifoTake>()
            if (line.groupId.isNotBlank() && line.gradeId.isNotBlank()) seasonItems(seasonId).filter { it.groupId == line.groupId && it.gradeId == line.gradeId }.forEach { item ->
                val left = pool[item.id] ?: BigDecimal.ZERO
                if (need.signum() > 0 && left.signum() > 0) {
                    val take = need.min(left);takes += FifoTake(item.harvestId, item.id, item.occurredAt, take);pool[item.id] = left.subtract(take);need = need.subtract(take)
                }
            }
            val remaining = seasonItems(seasonId).filter { it.groupId == line.groupId && it.gradeId == line.gradeId }.fold(BigDecimal.ZERO) { sum, item -> sum.add(pool[item.id] ?: BigDecimal.ZERO) }
            StockAfter(line.id, line.groupId, line.gradeId, takes, need, remaining)
        }
    }

    /** Group and grade of a harvest item, used to upgrade drafts saved before lines existed. */
    fun sourceOf(harvestItemId: String): Pair<String, String>? = items.find { it.id == harvestItemId }?.let { it.groupId to it.gradeId }

    private fun amount(text: String) = runCatching { BigDecimal(text) }.getOrDefault(BigDecimal.ZERO)
    private fun instant(text: String) = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrDefault(Long.MAX_VALUE)

    companion object { val EMPTY = SaleStock(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()) }
}

data class RetailHint(val pricePerKg: String, val kg: String, val paidAt: String)

object SalePricing {
    /** Same normalisation as the server's ConsumerPriceService::key: lower case, trimmed, inner whitespace collapsed. */
    fun key(name: String) = name.trim().replace(Regex("\\s+"), " ").lowercase()

    fun consumerPrice(prices: List<JSONObject>, varietyName: String, quality: String?): String? {
        if (quality == null) return null
        return prices.find { key(it.optString("variety_name")) == key(varietyName) && it.optString("quality") == quality && it.optString("record_state", "active") == "active" }
            ?.optString("price_per_kg")?.takeIf { it.isNotBlank() }
    }

    /** Last active sale of [varietyName] to [customerId], read from the cached sale bodies. */
    fun lastRetailPrice(sales: List<JSONObject>, customerId: String, varietyName: String): RetailHint? = sales
        .filter { it.optString("customer_id") == customerId && it.optString("record_state", "active") == "active" }
        .mapNotNull { sale ->
            val rows = sale.optJSONArray("lines") ?: return@mapNotNull null
            val line = (0 until rows.length()).map { rows.getJSONObject(it) }.lastOrNull { key(it.optString("variety_name")) == key(varietyName) } ?: return@mapNotNull null
            Triple(sale.optString("paid_at"), line.optString("price_per_kg"), line.optString("quantity_kg"))
        }.maxByOrNull { (paidAt, _, _) -> runCatching { OffsetDateTime.parse(paidAt).toInstant().toEpochMilli() }.getOrDefault(0L) }
        ?.let { (paidAt, price, kg) -> RetailHint(price, kg, paidAt) }
}

object SaleErrors {
    private val lineKey = Regex("""(?:^|\.)lines\.(\d+)\.([A-Za-z_]+)$""")
    /** Server field key such as `operation.payload.lines.2.fruit_count` to line index and field name. */
    fun lineField(key: String): Pair<Int, String>? = lineKey.find(key)?.let { it.groupValues[1].toInt() to it.groupValues[2] }
}

object BuyerTypes {
    /** Label shown for a buyer type: consumer is Perorangan, retail is Retail, partner is Mitra. */
    fun label(type: String) = when (type) { "partner" -> "Mitra"; "retail" -> "Retail"; else -> "Perorangan" }
}
