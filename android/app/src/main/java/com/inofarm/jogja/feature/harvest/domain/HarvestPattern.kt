package com.inofarm.jogja.feature.harvest.domain

import com.inofarm.jogja.core.cultivation.ManualTime
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime

data class HarvestDay(val date: LocalDate, val saleableKg: BigDecimal, val defectiveKg: BigDecimal)

/** One variety's harvest pattern in the open season; [unclassifiedKg] is older weight without layak/cacat. */
data class VarietyPattern(val groupId: String, val days: List<HarvestDay>, val totalKg: BigDecimal,
                          val defectivePercent: Int?, val unclassifiedKg: BigDecimal)

/** Inofarm 20 Nov 2026: harvests are recorded to see their pattern, kg layak and cacat per harvest date. */
object HarvestPattern {
    /** [harvests]: server bodies; only active, non-draft harvests of [groupIds] count. Days ascend by WIB date. */
    fun build(harvests: List<JSONObject>, groupIds: List<String>): List<VarietyPattern> = groupIds.map { group ->
        val byDay = sortedMapOf<LocalDate, Pair<BigDecimal, BigDecimal>>()
        var unclassified = BigDecimal.ZERO
        harvests.filter {
            it.optString("planting_group_id") == group && !it.has("_local_form") && it.optString("record_state", "active") == "active"
        }.forEach { harvest ->
            val date = runCatching { OffsetDateTime.parse(harvest.getString("occurred_at")).atZoneSameInstant(ManualTime.WIB).toLocalDate() }
                .getOrNull() ?: return@forEach
            val items = harvest.optJSONArray("items") ?: return@forEach
            for (index in 0 until items.length()) {
                val line = items.getJSONObject(index)
                val kg = runCatching { BigDecimal(line.getString("weight_kg")) }.getOrNull() ?: continue
                val quality = if (line.isNull("quality")) null else line.optString("quality")
                val (saleable, defective) = byDay[date] ?: (BigDecimal.ZERO to BigDecimal.ZERO)
                when (quality) {
                    HarvestQuality.SALEABLE.wire -> byDay[date] = saleable + kg to defective
                    HarvestQuality.DEFECTIVE.wire -> byDay[date] = saleable to defective + kg
                    else -> unclassified += kg
                }
            }
        }
        val days = byDay.map { (date, kg) -> HarvestDay(date, kg.first, kg.second) }
        val saleable = days.fold(BigDecimal.ZERO) { sum, day -> sum + day.saleableKg }
        val defective = days.fold(BigDecimal.ZERO) { sum, day -> sum + day.defectiveKg }
        val total = saleable + defective
        val percent = if (total.signum() == 0) null
            else defective.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
        VarietyPattern(group, days, total, percent, unclassified)
    }
}
