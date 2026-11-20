package com.inofarm.jogja.feature.financehome

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/**
 * Online reads and writes for the manager's Keuangan screen. Cost sheets are farm-wide (no greenhouse), so they never
 * enter the sync feed; like admin forms they need a connection.
 */
class FinanceHomeRepository(private val vault: FinanceSessionVault, private val api: FinanceApi) {
    private fun check(session: FinanceSession, ticket: Long) {
        check(vault.matches(ticket, session.scope)) { "Akun yang masuk berubah. Muat ulang Keuangan." }
        require(session.role == UserRole.MANAGER) { "Keuangan hanya untuk pengelola." }
    }

    suspend fun sheets(session: FinanceSession, ticket: Long): List<CostSheet> = withContext(Dispatchers.IO) {
        check(session, ticket)
        val data = api.request(session.scope.server, session.token, "season-cost-sheets").getJSONArray("data")
        (0 until data.length()).map { CostSheet.from(data.getJSONObject(it)) }
    }

    suspend fun save(session: FinanceSession, ticket: Long, sheet: CostSheet): CostSheet = withContext(Dispatchers.IO) {
        check(session, ticket)
        val epoch = api.request(session.scope.server, session.token, "sync/status").getString("sync_epoch")
        WireContract.uuid(epoch)
        val operation = JSONObject().put("schema_version", "2").put("sync_epoch", epoch).put("operation_id", UUID.randomUUID().toString())
            .put("entity_id", sheet.id).put("entity_type", "season_cost_sheet").put("action", "update").put("base_version", sheet.version)
            .put("payload", JSONObject().put("lines", sheet.payloadLines()))
        CostSheet.from(api.request(session.scope.server, session.token, "season-cost-sheets/${sheet.id}", "PATCH", operation).getJSONObject("data"))
    }

    suspend fun income(session: FinanceSession, ticket: Long, period: IncomePeriod): List<IncomeBar> = withContext(Dispatchers.IO) {
        check(session, ticket)
        val (from, until) = period.range()
        val body = api.request(session.scope.server, session.token, "reports/income",
            query = mapOf("granularity" to period.wire, "from" to from.toString(), "until" to until.toString()))
        IncomeBar.list(body.getJSONObject("data"))
    }

    suspend fun profitSummary(session: FinanceSession, ticket: Long): JSONObject = withContext(Dispatchers.IO) {
        check(session, ticket)
        api.request(session.scope.server, session.token, "reports/profit-summary").getJSONObject("data")
    }

    companion object {
        /** Typed amount (dots are shown as thousands separators, a comma is the decimal mark); blank means not filled. */
        fun amount(raw: String): BigDecimal? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val value = text.replace(',', '.')
            require(!value.startsWith("-")) { "Nominal tidak boleh negatif." }
            return WireContract.decimal(value, 2).setScale(2)
        }
    }
}
