package com.inofarm.jogja.feature.managerhome

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.activities.domain.ManualMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal fun JSONObject.text(key: String): String? = optString(key).takeUnless { isNull(key) || it.isBlank() }
internal fun JSONObject.rows(key: String): List<JSONObject> = optJSONArray(key)?.let { rows ->
    List(rows.length(), rows::getJSONObject)
}.orEmpty()

internal class HomeSnapshot(val json: JSONObject) {
    val selection = json.text("greenhouse_id")
    val epoch = json.getString("sync_epoch")
    val scope = json.getJSONArray("scope_ids").let { ids -> List(ids.length(), ids::getString).toSet() }
    val asOf = Instant.parse(json.getString("as_of"))
    val day = LocalDate.parse(json.getString("day"))
    val validUntil = json.text("access_until")?.let(Instant::parse)
    val cards = json.rows("greenhouses")
    val choices = json.rows("choices")
    val detail = json.optJSONObject("detail")
    val selected = cards.singleOrNull()?.takeIf { it.text("id") == selection }
    val period = selected?.optJSONObject("period")?.takeIf { it.text("state") == "ready" }
    val cash = json.getJSONObject("cash")
    val complete = json.getString("coverage") == "complete"

    init {
        require(json.getString("coverage") in setOf("complete", "partial") && json.getString("calendar") == "Asia/Jakarta")
        require(cards.mapNotNull { it.text("id") }.toSet() == (selection?.let(::setOf) ?: scope))
        require(choices.mapNotNull { it.text("id") }.toSet() == scope)
        require(selection == null || selection in scope && detail != null)
    }
}

internal data class ManagerHomeState(
    val snapshot: HomeSnapshot? = null,
    val measurement: ManualMeasurement? = null,
    val pendingPeriod: JSONObject? = null,
    val loading: Boolean = true,
    val cached: Boolean = false,
    val denied: Boolean = false,
    val sessionRejected: Boolean = false,
    val selectionRevoked: Boolean = false,
    val message: String? = null,
)

internal class ManagerHomeRepository(
    private val vault: FinanceSessionVault,
    private val store: LocalFinanceStore,
    private val api: FinanceApi,
) {
    private fun checkSession(session: FinanceSession, ticket: Long) {
        check(session.role == UserRole.MANAGER && vault.matches(ticket, session.scope) &&
            vault.current?.role == UserRole.MANAGER && vault.current?.token == session.token) { "Sesi pengelola berubah." }
    }

    suspend fun cached(session: FinanceSession, ticket: Long, selection: String?): ManagerHomeState? = withContext(Dispatchers.IO) {
        checkSession(session, ticket)
        if (session.expired()) return@withContext null
        val today = LocalDate.now(ActivityDraft.WIB)
        val key = session.scope.key
        if (store.epoch(key) == null) return@withContext null
        val json = store.managerHomeCache(key, selection.orEmpty(), YearMonth.from(today).toString()) ?: return@withContext null
        val snapshot = HomeSnapshot(json)
        if (snapshot.epoch != store.epoch(key) || snapshot.scope != store.visibleGreenhouses(key) ||
            snapshot.validUntil?.let { it <= Instant.now() } == true) return@withContext null
        checkSession(session, ticket)
        display(session, snapshot, cached = true)
    }

    suspend fun load(session: FinanceSession, ticket: Long, selection: String?): ManagerHomeState = withContext(Dispatchers.IO) {
        checkSession(session, ticket)
        if (session.expired()) return@withContext ManagerHomeState(loading = false, denied = true, message = "Sesi berakhir. Masuk kembali untuk melihat ringkasan.")
        val key = session.scope.key
        val previousEpoch = store.epoch(key)
        val previousScope = store.visibleGreenhouses(key)
        val json = try {
            api.request(session.scope.server, session.token, "manager-home",
                query = selection?.let { mapOf("greenhouse_id" to it) }.orEmpty())
        } catch (error: FinanceApiFailure) {
            checkSession(session, ticket)
            if (error.status in setOf(401, 403, 404)) {
                val sessionRejected = error.status == 401 || error.status == 403
                if (sessionRejected) runCatching { vault.save(ticket, session.copy(rejected = true)) }
                runCatching { store.invalidateManagerHome(key) }
                return@withContext ManagerHomeState(loading = false, denied = true,
                    sessionRejected = sessionRejected,
                    selectionRevoked = error.status == 404 && selection != null,
                    message = if (error.status == 404 && selection != null) "Greenhouse tidak tersedia dalam penugasan saat ini." else "Ringkasan pengelola belum dapat diakses.")
            }
            throw error
        }
        checkSession(session, ticket)
        val snapshot = HomeSnapshot(json)
        require(json.getString("account_id") == session.scope.accountId && snapshot.selection == selection) { "Cakupan respons ringkasan tidak sesuai." }
        val currentEpoch = store.epoch(key)
        val currentScope = store.visibleGreenhouses(key)
        if (currentEpoch != null && currentEpoch != snapshot.epoch ||
            currentScope != previousScope && currentScope != snapshot.scope ||
            previousEpoch != null && currentEpoch == null ||
            snapshot.validUntil?.let { it <= Instant.now() } == true) {
            store.invalidateManagerHome(key)
            return@withContext ManagerHomeState(loading = false, message = "Penugasan atau titik pemulihan berubah. Sinkronkan untuk memeriksa ringkasan.")
        }
        if (snapshot.complete && currentEpoch == snapshot.epoch && currentScope == snapshot.scope) {
            store.cacheManagerHome(key, selection.orEmpty(), YearMonth.from(snapshot.day).toString(), json, snapshot.epoch, snapshot.scope)
        }
        checkSession(session, ticket)
        display(session, snapshot, cached = false)
    }

    private fun display(session: FinanceSession, snapshot: HomeSnapshot, cached: Boolean): ManagerHomeState {
        val key = session.scope.key
        val today = LocalDate.now(ActivityDraft.WIB)
        val accepted = snapshot.detail?.optJSONObject("measurement")?.let { ManualMeasurement.parse(it, today) }
        val gh = snapshot.selection
        val group = snapshot.period?.text("group_id")
        val localGreenhouse = gh?.takeIf { it in store.visibleGreenhouses(key) && snapshot.epoch == store.epoch(key) }
        val queued = if (localGreenhouse != null && group != null) store.latestMeasurement(key, localGreenhouse, group, today, queuedOnly = true) else null
        val pair = listOfNotNull(accepted, queued).maxWithOrNull(compareBy({ it.at }, { it.id }, { it.queued }))
        return ManagerHomeState(snapshot, pair, localGreenhouse?.let { store.runningPeriodRequest(key, it) },
            loading = false, cached = cached)
    }

    suspend fun designate(session: FinanceSession, ticket: Long, snapshot: HomeSnapshot, group: String?, reason: String) = withContext(Dispatchers.IO) {
        checkSession(session, ticket)
        require(!session.expired()) { "Masuk kembali untuk menetapkan periode." }
        val gh = requireNotNull(snapshot.selection)
        val key = session.scope.key
        val epoch = snapshot.epoch
        require(epoch == store.epoch(key)) { "Epoch berubah. Sinkronkan dan tinjau pemulihan." }
        val pending = store.runningPeriodRequest(key, gh)
        val operation = pending ?: run {
            require(reason.isNotBlank() && reason.length <= 1000) { "Isi alasan penetapan, maksimal 1.000 karakter." }
            store.storeRunningPeriodRequest(key, gh, JSONObject().put("schema_version", "2").put("sync_epoch", epoch)
                .put("operation_id", UUID.randomUUID().toString()).put("entity_id", gh).put("entity_type", "greenhouses")
                .put("action", "designate_period").put("base_version", snapshot.selected!!.getInt("version"))
                .put("payload", JSONObject().put("planting_group_id", group ?: JSONObject.NULL).put("reason", reason.trim())))
        }
        require(operation.getString("sync_epoch") == epoch) { "Pengajuan periode lama perlu rekonsiliasi setelah pemulihan." }
        try {
            val receipt = api.request(session.scope.server, session.token, "greenhouses/$gh/running-period", "POST", operation)
            checkSession(session, ticket)
            check(receipt.getString("operation_id") == operation.getString("operation_id") && receipt.getString("entity_id") == gh)
            store.finishRunningPeriodRequest(key, gh, operation)
            store.invalidateManagerHome(key)
        } catch (error: FinanceApiFailure) {
            checkSession(session, ticket)
            if (error.status == 422 || error.code == "version_conflict") store.finishRunningPeriodRequest(key, gh, operation)
            if (error.status in setOf(401, 403, 404)) store.invalidateManagerHome(key)
            throw error
        }
    }
}
