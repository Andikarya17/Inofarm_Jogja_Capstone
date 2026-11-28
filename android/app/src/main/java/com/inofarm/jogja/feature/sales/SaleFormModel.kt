package com.inofarm.jogja.feature.sales

import com.inofarm.jogja.core.cultivation.ManualTime
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

/**
 * One card of the sale form: a planting group and a grade (variety and Layak/Cacat), its stock and the lines that sell
 * from it. A mitra buys Layak fruit as graded lines A, B and C, so such a card can hold up to three lines.
 */
data class SaleCard(
    val groupId: String, val gradeId: String, val varietyName: String, val quality: String?, val availableKg: BigDecimal,
    val lines: List<SaleLine>, val afterKg: BigDecimal, val short: Boolean, val takes: List<FifoTake>, val canAddGrade: Boolean,
) {
    val soldKg: BigDecimal get() = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(SaleFormModel.kgOf(line)) }

    /** "Sesudah dijual sisa 290 kg", or what is left when the lines ask for more than the stock. */
    fun after() = if (short) "Sisa hanya ${SaleFormModel.kg(availableKg)} kg" else "Sesudah dijual sisa ${SaleFormModel.kg(afterKg)} kg"

    /** "50 kg · 38 buah"; the fruit count only when every line has one. */
    fun sum() = SaleFormModel.amountLine(lines)

    /** Which harvests the server takes the kg from, oldest first, summed per harvest. */
    fun fifoText(): String? {
        val parts = takes.groupBy { it.harvestId }.map { (_, rows) -> rows.first().occurredAt to rows.fold(BigDecimal.ZERO) { s, t -> s.add(t.kg) } }
            .map { (at, kg) -> "${SaleFormModel.dayMonth(at)} (${SaleFormModel.kg(kg)} kg)" }
        if (parts.isEmpty()) return null
        val joined = if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " dan " + parts.last()
        return "Diambil dari panen $joined" + if (parts.size > 1) ", yang paling lama lebih dulu" else ""
    }
}

data class SaleTotals(val total: BigDecimal?, val kg: BigDecimal, val fruit: Int?, val varieties: Int, val average: BigDecimal?) {
    /** "70 kg · 54 buah · 2 varietas". */
    fun meta() = listOfNotNull("${SaleFormModel.kg(kg)} kg", fruit?.let { "$it buah" }, "$varieties varietas").joinToString(" · ")
}

object SaleFormModel {
    val PARTNER_GRADES = listOf("A", "B", "C")

    fun kgOf(line: SaleLine): BigDecimal = runCatching { SaleDraft.decimal(line.quantity, true) }.getOrDefault(BigDecimal.ZERO)
    private fun priceOf(line: SaleLine): BigDecimal? = runCatching { SaleDraft.decimal(line.price, false) }.getOrNull()

    /** "290", "12,5". */
    fun kg(value: BigDecimal): String = value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString().replace('.', ',')

    fun money(value: BigDecimal) = com.inofarm.jogja.feature.financehome.rupiah(value)

    /** "17 Nov" in WIB for a harvest time or a payment time. */
    fun dayMonth(at: String): String = runCatching {
        val day = OffsetDateTime.parse(at).atZoneSameInstant(ManualTime.WIB).toLocalDate()
        com.inofarm.jogja.core.design.dayMonthLabel(day)
    }.getOrDefault(at)

    fun qualityLabel(quality: String?) = when (quality) { "saleable" -> "Layak"; "defective" -> "Cacat"; else -> "" }
    fun optionName(option: StockOption) = listOf(option.varietyName.ifBlank { "Buah" }, qualityLabel(option.quality).ifBlank { option.gradeName }).filter { it.isNotBlank() }.joinToString(" ")

    /** The mitra grades only Layak fruit; Cacat goes as one ungraded line. */
    fun graded(type: String, quality: String?) = type == "partner" && quality != "defective"

    fun amountLine(lines: List<SaleLine>): String {
        val kg = lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(kgOf(line)) }
        val fruits = lines.map { it.fruitCount.trim().toIntOrNull() }
        return listOfNotNull("${kg(kg)} kg", if (fruits.isNotEmpty() && fruits.all { it != null }) "${fruits.sumOf { it!! }} buah" else null).joinToString(" · ")
    }

    /** Open season of [greenhouseId] that started last: the sale form picks it on its own. */
    fun openSeason(seasons: List<JSONObject>, greenhouseId: String): String? = seasons
        .filter { it.optString("greenhouse_id") == greenhouseId && it.optString("status") == "open" }
        .maxByOrNull { it.optString("start_date") }?.optString("id")

    /** A card selling more than its stock blocks Simpan in the form itself, not after the Ringkasan (QA 28 Nov 2026). */
    fun stockErrors(draft: SaleDraft, stock: SaleStock): Map<String, String> = cards(draft, stock).filter { it.short }
        .associate { card -> "stock.${card.groupId}.${card.gradeId}" to "${listOf(card.varietyName, qualityLabel(card.quality)).filter { it.isNotBlank() }.joinToString(" ")}: ${card.after()}, kurangi bobotnya." }

    fun cards(draft: SaleDraft, stock: SaleStock): List<SaleCard> {
        val options = stock.options(draft.seasonId).associateBy { it.groupId to it.gradeId }
        val after = stock.fifoPreview(draft.seasonId, draft.items).associateBy { it.lineId }
        return draft.items.filter { it.groupId.isNotBlank() && it.gradeId.isNotBlank() }.groupBy { it.groupId to it.gradeId }.map { (key, lines) ->
            val option = options[key]
            val previews = lines.mapNotNull { after[it.id] }
            val available = option?.availableKg ?: BigDecimal.ZERO
            val short = previews.any { it.shortfallKg.signum() > 0 }
            SaleCard(key.first, key.second, option?.varietyName.orEmpty(), option?.quality, available, lines,
                previews.lastOrNull()?.remainingKg ?: available, short, previews.flatMap { it.takes },
                graded(draft.buyerType, option?.quality) && lines.size < PARTNER_GRADES.size)
        }
    }

    /** Fruit of the season that has stock and no card yet: the "Tambah buah lain" chips. */
    fun more(draft: SaleDraft, stock: SaleStock): List<StockOption> {
        val used = draft.items.map { it.groupId to it.gradeId }.toSet()
        return stock.options(draft.seasonId).filter { (it.groupId to it.gradeId) !in used }
            .sortedWith(compareBy<StockOption>({ it.quality == "defective" }, { it.varietyName.lowercase() }))
    }

    /** Stored price "34000.00" as the form text "34000". */
    fun priceInput(wire: String): String = runCatching { kg(BigDecimal(wire)) }.getOrDefault("")

    fun consumerPrice(prices: List<JSONObject>, option: StockOption?): String? =
        option?.let { SalePricing.consumerPrice(prices, it.varietyName, it.quality) }?.let(::priceInput)?.takeIf { it.isNotBlank() }

    fun addLine(draft: SaleDraft, option: StockOption, price: String?): SaleDraft =
        draft.copy(items = draft.items + SaleLine(groupId = option.groupId, gradeId = option.gradeId,
            buyerGrade = if (graded(draft.buyerType, option.quality)) "A" else "", price = price.orEmpty()))

    /** Adds the next mitra grade not used yet on this card (A, then B, then C). */
    fun addGrade(draft: SaleDraft, groupId: String, gradeId: String): SaleDraft {
        val lines = draft.items.filter { it.groupId == groupId && it.gradeId == gradeId }
        val next = PARTNER_GRADES.firstOrNull { grade -> lines.none { it.buyerGrade == grade } } ?: return draft
        val at = draft.items.indexOfLast { it.groupId == groupId && it.gradeId == gradeId } + 1
        return draft.copy(items = draft.items.toMutableList().apply { add(at, SaleLine(groupId = groupId, gradeId = gradeId, buyerGrade = next)) })
    }

    fun removeCard(draft: SaleDraft, groupId: String, gradeId: String) = draft.copy(items = draft.items.filterNot { it.groupId == groupId && it.gradeId == gradeId })

    /**
     * New buyer type: other buyers get one ungraded line per fruit (the first of the mitra lines), a mitra gets grade A
     * on Layak lines, and Perorangan fills empty prices from the price list. The chosen buyer is cleared.
     */
    fun switchType(draft: SaleDraft, type: String, stock: SaleStock, prices: List<JSONObject>): SaleDraft {
        val options = stock.options(draft.seasonId).associateBy { it.groupId to it.gradeId }
        val lines = if (type == "partner") draft.items.map { line ->
            if (line.buyerGrade.isBlank() && graded(type, options[line.groupId to line.gradeId]?.quality)) line.copy(buyerGrade = "A") else line
        } else draft.items.filterIndexed { index, line -> keptOnSwitch(draft, index, line) }.map { it.copy(buyerGrade = "") }
        val switched = draft.copy(buyerType = type, buyerId = "", buyerExisting = false, customer = "", buyerWhatsapp = "", items = lines)
        return fillConsumerPrices(switched, stock, prices) ?: switched
    }

    private fun keptOnSwitch(draft: SaleDraft, index: Int, line: SaleLine) =
        line.groupId.isBlank() || draft.items.indexOfFirst { it.groupId == line.groupId && it.gradeId == line.gradeId } == index

    /** What leaving Mitra for [type] removes: the extra mitra grade lines and their kg, or null when nothing is lost. */
    fun switchLoss(draft: SaleDraft, type: String): String? {
        if (type == "partner") return null
        val dropped = draft.items.filterIndexed { index, line -> !keptOnSwitch(draft, index, line) }
        if (dropped.isEmpty()) return null
        val grades = dropped.map { it.buyerGrade }.filter { it.isNotBlank() }.distinct().sorted().joinToString(" dan ").ifBlank { "lain" }
        val weight = dropped.fold(BigDecimal.ZERO) { sum, line -> sum.add(kgOf(line)) }
        return "Grade $grades hanya untuk mitra. Baris grade $grades dihapus" + (if (weight.signum() > 0) " beserta ${kg(weight)} kg." else ".")
    }

    /** Perorangan lines with no price yet take the price list's price; null when nothing changes. */
    fun fillConsumerPrices(draft: SaleDraft, stock: SaleStock, prices: List<JSONObject>): SaleDraft? {
        if (draft.buyerType != "consumer" || prices.isEmpty()) return null
        val options = stock.options(draft.seasonId).associateBy { it.groupId to it.gradeId }
        var changed = false
        val lines = draft.items.map { line ->
            val price = if (line.price.isBlank()) consumerPrice(prices, options[line.groupId to line.gradeId]) else null
            if (price != null) { changed = true; line.copy(price = price) } else line
        }
        return if (changed) draft.copy(items = lines) else null
    }

    /** The line under a price field: where the price came from, or the last price paid by this retail buyer. */
    fun priceHint(draft: SaleDraft, option: StockOption?, prices: List<JSONObject>, sales: List<JSONObject>, line: SaleLine? = null): String {
        val variety = option?.varietyName.orEmpty()
        return when (draft.buyerType) {
            "consumer" -> {
                val listed = consumerPrice(prices, option)
                val typed = line?.let { runCatching { SaleDraft.decimal(it.price, false) }.getOrNull() }
                // The Kelola sentence only while the line still holds that price; a typed price gets a neutral note.
                if (listed != null && (line == null || (typed != null && typed.compareTo(SaleDraft.decimal(listed, false)) == 0)))
                    "Harga perorangan $variety ${qualityLabel(option?.quality)}, diisi dari Kelola. Masih bisa diubah."
                else if (listed != null) "Harga perorangan bisa diubah."
                else "Belum ada harga perorangan $variety ${qualityLabel(option?.quality)} di Kelola. Ketik harganya.".replace("  ", " ")
            }
            "partner" -> "Grade dinilai mitra, harga ditawar."
            "retail" -> {
                if (draft.buyerId.isBlank()) return "Harga ditawar."
                val hint = SalePricing.lastRetailPrice(sales.filter { it.optString("id") != draft.id }, draft.buyerId, variety)
                    ?: return "Harga ditawar. Belum ada penjualan sebelumnya ke pembeli ini."
                val price = runCatching { money(BigDecimal(hint.pricePerKg)) }.getOrDefault(hint.pricePerKg)
                val amount = runCatching { kg(BigDecimal(hint.kg)) }.getOrDefault(hint.kg)
                "Harga ditawar. Terakhir ke ${draft.customer}: $price/kg untuk $amount kg, ${dayMonth(hint.paidAt)}."
            }
            else -> "Pilih tipe pembeli dulu."
        }
    }

    /** "50 kg × Rp28.000" and the line amount, when both numbers are filled in. */
    fun lineCalc(line: SaleLine): Pair<String, String>? {
        val kg = runCatching { SaleDraft.decimal(line.quantity, true) }.getOrNull() ?: return null
        val price = priceOf(line) ?: return null
        return "${kg(kg)} kg × ${money(price)}" to money(kg.multiply(price).setScale(2, RoundingMode.HALF_UP))
    }

    /** Running total over the lines whose kg and price are filled in; the server's total is final. */
    fun totals(draft: SaleDraft, stock: SaleStock): SaleTotals {
        val ready = draft.items.filter { runCatching { SaleDraft.decimal(it.quantity, true) }.isSuccess && priceOf(it) != null }
        val total = ready.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO) { sum, line -> sum.add(kgOf(line).multiply(priceOf(line)!!)) }?.setScale(2, RoundingMode.HALF_UP)
        val kg = draft.items.fold(BigDecimal.ZERO) { sum, line -> sum.add(kgOf(line)) }
        val fruits = draft.items.map { it.fruitCount.trim().toIntOrNull() }
        val names = stock.options(draft.seasonId).associateBy { it.groupId to it.gradeId }
        val varieties = draft.items.filter { it.groupId.isNotBlank() }.map { names[it.groupId to it.gradeId]?.varietyName?.let(SalePricing::key) ?: it.groupId }.distinct().size
        val average = if (total != null && kg.signum() > 0 && ready.size == draft.items.size) total.divide(kg, 0, RoundingMode.HALF_UP) else null
        return SaleTotals(total, kg, if (fruits.isNotEmpty() && fruits.all { it != null }) fruits.sumOf { it!! } else null, varieties, average)
    }
}
