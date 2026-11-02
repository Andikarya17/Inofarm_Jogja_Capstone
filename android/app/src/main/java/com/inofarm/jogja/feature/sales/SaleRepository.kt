package com.inofarm.jogja.feature.sales

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SaleValidationFailure(val fields:Map<String,String>,val baseVersion:Long?):IllegalArgumentException(fields.values.joinToString("\n"))
class SaleRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore) {
    private val writes=Mutex()
    suspend fun flush(session:FinanceSession,ticket:Long,draft:SaleDraft)=save(session,ticket,draft,false,true)
    suspend fun save(session:FinanceSession,ticket:Long,draft:SaleDraft,queue:Boolean,onlyIfChanged:Boolean=false)=withContext(Dispatchers.IO) {writes.withLock {
        check(vault.matches(ticket,session.scope));require(session.role==UserRole.MANAGER) {"Penjualan hanya untuk pengelola."}
        var existing=store.record(session.scope.key,draft.id);val known=existing?.baseVersion ?: existing?.serverVersion
        if(!queue && onlyIfChanged && existing!=null && runCatching {SaleDraft.from(existing.id,existing.greenhouseId,existing.body,known)}.getOrNull()==draft.copy(baseVersion=draft.baseVersion ?: known)) return@withLock existing
        val effective=draft.copy(baseVersion=draft.baseVersion ?: known)
        if(queue) {
            require(existing?.serverBody?.optString("record_state") != "cancelled") {"Penjualan telah dibatalkan. Tinjau histori sebelum membuat catatan baru."}
            // Preserve the latest raw form even if source validation or queue transaction fails next.
            existing=store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion,kind="sale")
            val errors=effective.validation();if(errors.isNotEmpty()) throw SaleValidationFailure(errors,effective.baseVersion)
            val season=store.cachedOne(session.scope.key,"seasons",effective.seasonId) ?: error("Musim belum tersimpan. Sinkronkan dahulu.")
            require(season.getString("greenhouse_id")==draft.greenhouseId && season.getString("status")=="open") {"Pilih musim terbuka dari greenhouse yang sama."}
            effective.items.forEach {line ->
                val balance=store.cachedOne(session.scope.key,"harvest_balance",line.sourceId) ?: error("Saldo sumber panen belum tersedia. Simpan draf dan sinkronkan dahulu.")
                val harvest=store.cachedOne(session.scope.key,"harvest",balance.getString("harvest_id")) ?: error("Panen sumber belum diterima dan tersinkron.")
                val group=store.cachedOne(session.scope.key,"planting_groups",harvest.getString("planting_group_id")) ?: error("Kelompok sumber belum tersedia.")
                require(group.getString("season_id")==effective.seasonId && balance.optBoolean("active") && harvest.optString("record_state")=="active" && balance.getLong("harvest_version")==harvest.getLong("version")) {"Sumber panen harus aktif, satu musim, dan versinya sesuai saldo terakhir."}
                val own=existing?.serverBody?.optJSONArray("items")?.let {rows -> (0 until rows.length()).map {rows.getJSONObject(it)}.filter {it.optString("harvest_item_id")==line.sourceId}.fold(java.math.BigDecimal.ZERO) {sum,row -> sum.add(java.math.BigDecimal(row.getString("quantity_kg")))}} ?: java.math.BigDecimal.ZERO
                val weight=java.math.BigDecimal(balance.getString("weight_kg"));val available=java.math.BigDecimal(balance.getString("available_weight_kg"))
                require(available.signum()>=0 && available<=weight) {"Saldo tersimpan belum dapat direkonsiliasi. Sinkronkan dan tinjau sumber."}
                require(SaleDraft.decimal(line.quantity,true)<=available.add(own).min(weight)) {"Bobot melebihi saldo terakhir diketahui. Tinjau dan sinkronkan sumber panen; server memeriksa ulang saat menerima."}
            }
            val locked=effective.copy(lockedSeason=true,lockedSources=effective.lockedSources+effective.items.map {it.id})
            store.saveAndQueue(session.scope.key,effective.id,effective.greenhouseId,locked.localJson().put("completeness","ready"),existing?.revision,effective.baseVersion,effective.replaceValidationRejected,kind="sale",queuePayload=effective.payload())
        } else store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion,kind="sale")
    }}
}
