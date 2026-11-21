package com.inofarm.jogja.feature.financehome

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/** One line of the farm-wide standard cost sheet; a null amount means "not filled yet", never zero. */
data class CostLine(val id: String, val name: String, val costClass: String, val amount: BigDecimal?, val required: Boolean, val sortOrder: Int)

/** Standard cost sheet for season number [sequence], the same for every greenhouse (Inofarm, 20-21 Nov 2026). */
/** [locked] while an active greenhouse has closed season [sequence]; [closedGreenhouses] names them so the screen can say so before editing. */
data class CostSheet(val id: String, val sequence: Int, val version: Int, val lines: List<CostLine>, val greenhouseCount: Int,
    val locked: Boolean = false, val closedGreenhouses: List<String> = emptyList()) {
    /** Per-greenhouse total of one class, or null while any of its lines is blank. */
    fun total(costClass: String): BigDecimal? {
        val own = lines.filter { it.costClass == costClass }
        if (own.any { it.amount == null }) return null
        return own.fold(BigDecimal.ZERO.setScale(2)) { sum, line -> sum + line.amount!! }.setScale(2, RoundingMode.UNNECESSARY)
    }

    fun tc(): BigDecimal? {
        val fc = total("FC") ?: return null
        val vc = total("VC") ?: return null
        return fc + vc
    }

    /** Cost for the whole farm in this season number: per-greenhouse TC times the greenhouses running it. */
    fun farmTotal(): BigDecimal? = tc()?.multiply(BigDecimal(greenhouseCount))?.setScale(2)

    fun withLine(line: CostLine): CostSheet = copy(lines = lines + line)

    fun withoutLine(id: String): CostSheet {
        val line = lines.single { it.id == id }
        require(!line.required) { "Baris wajib \"${line.name}\" tidak dapat dihapus." }
        return copy(lines = lines - line)
    }

    fun withAmount(id: String, amount: BigDecimal?): CostSheet =
        copy(lines = lines.map { if (it.id == id) it.copy(amount = amount?.setScale(2, RoundingMode.HALF_UP)) else it })

    fun withName(id: String, name: String): CostSheet {
        require(lines.single { it.id == id }.required.not()) { "Nama baris wajib tidak dapat diubah." }
        return copy(lines = lines.map { if (it.id == id) it.copy(name = name) else it })
    }

    /** Full line list for PATCH season-cost-sheets/{id}; amounts as 2-decimal strings or JSON null. */
    fun payloadLines(): JSONArray = JSONArray().also { array ->
        lines.forEach { line ->
            array.put(JSONObject().put("id", line.id).put("name", line.name.trim()).put("cost_class", line.costClass)
                .put("amount", line.amount?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: JSONObject.NULL)
                .put("is_required", line.required))
        }
    }

    companion object {
        fun from(body: JSONObject): CostSheet {
            val array = body.getJSONArray("lines")
            val lines = (0 until array.length()).map { index ->
                val line = array.getJSONObject(index)
                CostLine(line.getString("id"), line.getString("name"), line.getString("cost_class"),
                    if (line.isNull("amount")) null else BigDecimal(line.getString("amount")), line.getBoolean("is_required"), line.optInt("sort_order", index))
            }
            val closed = body.optJSONArray("closed_greenhouse_names")
            return CostSheet(body.getString("id"), body.getInt("sequence"), body.getInt("version"), lines, body.optInt("greenhouse_count", 0),
                body.optBoolean("locked", false), closed?.let { names -> (0 until names.length()).map { names.getString(it) } }.orEmpty())
        }
    }
}
