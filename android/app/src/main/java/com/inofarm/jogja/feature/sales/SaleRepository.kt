package com.inofarm.jogja.feature.sales

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SaleValidationFailure(val fields:Map<String,String>,val baseVersion:Long?):IllegalArgumentException(fields.values.joinToString("\n"))
class SaleRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore) {
    private val writes=Mutex()
    suspend fun flush(session:FinanceSession,ticket:Long,draft:SaleDraft)=save(session,ticket,draft,false,true)
    /** Sales the server sent to this device, for the last price paid by a retail buyer. */
    suspend fun pastSales(session:FinanceSession,ticket:Long):List<org.json.JSONObject> = withContext(Dispatchers.IO) {
        check(vault.matches(ticket,session.scope)) {"Sesi berubah."};require(session.role==UserRole.MANAGER) {"Penjualan hanya untuk pengelola."}
        store.cachedAll(session.scope.key,"sale")
    }
    /** "Batal": undoes the autosaved edits of [id] back to [origin], the row as the form opened it (null when new). */
    suspend fun discard(session:FinanceSession,ticket:Long,id:String,origin:com.inofarm.jogja.core.database.LocalRecord?)=withContext(Dispatchers.IO) {writes.withLock {
        check(vault.matches(ticket,session.scope)) {"Sesi berubah."};store.restoreDraft(session.scope.key,id,origin)
    }}
    suspend fun save(session:FinanceSession,ticket:Long,draft:SaleDraft,queue:Boolean,onlyIfChanged:Boolean=false)=withContext(Dispatchers.IO) {writes.withLock {
        check(vault.matches(ticket,session.scope));require(session.role==UserRole.MANAGER) {"Penjualan hanya untuk pengelola."}
        var existing=store.record(session.scope.key,draft.id);val known=existing?.baseVersion ?: existing?.serverVersion
        if(!queue && onlyIfChanged && existing!=null && runCatching {SaleDraft.from(existing.id,existing.greenhouseId,existing.body,known)}.getOrNull()==draft.copy(baseVersion=draft.baseVersion ?: known)) return@withLock existing
        val effective=draft.copy(baseVersion=draft.baseVersion ?: known)
        if(queue) {
            require(existing?.serverBody?.optString("record_state") != "cancelled") {"Penjualan telah dibatalkan. Lihat riwayat sebelum membuat catatan baru."}
            // Preserve the latest raw form even if source validation or queue transaction fails next.
            existing=store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion,kind="sale")
            val errors=effective.validation(session.role!=UserRole.MANAGER);if(errors.isNotEmpty()) throw SaleValidationFailure(errors,effective.baseVersion)
            val season=store.cachedOne(session.scope.key,"seasons",effective.seasonId) ?: error("Musim belum tersedia di perangkat. Perbarui dulu.")
            require(season.getString("greenhouse_id")==draft.greenhouseId && season.getString("status")=="open") {"Pilih musim terbuka dari greenhouse yang sama."}
            val key=session.scope.key
            val stock=SaleStock(store.cachedAll(key,"harvest"),store.cachedAll(key,"harvest_balance"),store.cachedAll(key,"planting_groups"),store.cachedAll(key,"varieties"),store.cachedAll(key,"grades"))
            // What this sale already holds on the server is credited back, since the balance counts it as sold.
            val own=mutableMapOf<Pair<String,String>,java.math.BigDecimal>()
            existing?.serverBody?.let {body ->
                body.optJSONArray("lines")?.let {rows -> (0 until rows.length()).map {rows.getJSONObject(it)}.forEach {row -> own.merge(row.optString("planting_group_id") to row.optString("grade_id"),java.math.BigDecimal(row.getString("quantity_kg")),java.math.BigDecimal::add)}}
                    ?: body.optJSONArray("items")?.let {rows -> (0 until rows.length()).map {rows.getJSONObject(it)}.forEach {row -> stock.sourceOf(row.optString("harvest_item_id"))?.let {source -> own.merge(source,java.math.BigDecimal(row.getString("quantity_kg")),java.math.BigDecimal::add)}}}
            }
            val options=stock.options(effective.seasonId)
            effective.items.groupBy {it.groupId to it.gradeId}.forEach {(source,rows) ->
                val available=(options.find {it.groupId==source.first && it.gradeId==source.second}?.availableKg ?: java.math.BigDecimal.ZERO).add(own[source] ?: java.math.BigDecimal.ZERO)
                require(rows.fold(java.math.BigDecimal.ZERO) {sum,row -> sum.add(SaleDraft.decimal(row.quantity,true))}<=available) {"Bobot melebihi saldo terakhir. Perbarui lalu periksa buah dan grade."}
            }
            val locked=effective.copy(lockedSeason=true,lockedSources=effective.lockedSources+effective.items.map {it.id})
            store.saveAndQueue(session.scope.key,effective.id,effective.greenhouseId,locked.localJson().put("completeness","ready"),existing?.revision,effective.baseVersion,effective.replaceValidationRejected,kind="sale",queuePayload=effective.payload())
        } else store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion,kind="sale")
    }}
}
