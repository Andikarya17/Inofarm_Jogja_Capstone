package com.inofarm.jogja.feature.assets

import com.inofarm.jogja.core.contracts.DepreciationAmounts
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AssetValidationFailure(val fields:Map<String,String>,val baseVersion:Long?):IllegalArgumentException(fields.values.joinToString("\n"))

class AssetRepository(val vault:FinanceSessionVault,private val store:LocalFinanceStore) {
    private val writes=Mutex()
    suspend fun flush(session:FinanceSession,ticket:Long,draft:AssetDraft)=save(session,ticket,draft,false,true)
    suspend fun save(session:FinanceSession,ticket:Long,draft:AssetDraft,queue:Boolean,onlyIfChanged:Boolean=false)=withContext(Dispatchers.IO) {writes.withLock {
        check(vault.matches(ticket,session.scope));require(session.role==UserRole.MANAGER) {"Aset hanya untuk pengelola."}
        var existing=store.record(session.scope.key,draft.id)
        require(existing==null || existing.kind==draft.kind.wire) {"Identitas draf tidak sesuai jenis aset."}
        val known=existing?.baseVersion ?: existing?.serverVersion
        if(!queue && onlyIfChanged && existing!=null && runCatching {AssetDraft.from(existing.id,existing.greenhouseId,draft.kind,existing.body,known)}.getOrNull()==draft.copy(baseVersion=draft.baseVersion ?: known)) return@withLock existing
        val effective=draft.copy(baseVersion=draft.baseVersion ?: known)
        if(!queue) return@withLock store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion,kind=draft.kind.wire)
        require(existing?.serverBody?.optString("record_state")!="cancelled") {"Catatan telah dibatalkan. Lihat riwayat."}
        existing=store.saveDraft(session.scope.key,draft.id,draft.greenhouseId,draft.localJson(),existing?.revision,draft.baseVersion,kind=draft.kind.wire)
        val errors=effective.validation(session.role!=UserRole.MANAGER);if(errors.isNotEmpty()) throw AssetValidationFailure(errors,effective.baseVersion)
        if(draft.kind==AssetKind.ASSET) {
            val payment=store.cachedOne(session.scope.key,"expense",effective.expenseId) ?: error("Pembayaran sumber belum tersedia di perangkat. Perbarui dulu.")
            require(payment.getString("greenhouse_id")==draft.greenhouseId && payment.getString("record_state")=="active" && payment.getString("payment_kind")=="asset_acquisition") {"Pilih pembayaran pembelian aset aktif pada greenhouse yang sama."}
            require(AssetDraft.money(effective.cost)<=AssetDraft.money(payment.getString("amount"))) {"Harga aset melebihi pembayaran sumber terakhir diketahui."}
        } else {
            val asset=store.cachedOne(session.scope.key,"asset",effective.assetId) ?: error("Aset sumber belum tersedia di perangkat. Perbarui dulu.")
            val season=store.cachedOne(session.scope.key,"seasons",effective.seasonId) ?: error("Musim belum tersedia di perangkat. Perbarui dulu.")
            require(asset.getString("greenhouse_id")==draft.greenhouseId && asset.getString("record_state")=="active" && season.getString("greenhouse_id")==draft.greenhouseId && season.getString("status")=="open") {"Aset aktif dan musim terbuka harus satu greenhouse."}
            val budget=DepreciationAmounts.year(asset.getString("cost"),asset.getString("residual"),asset.getInt("life_years"),AssetDraft.year(effective.usageYear),AssetDraft.date(asset.getString("in_service_date")))
            require(!AssetDraft.date(effective.periodStart).isBefore(budget.start) && !AssetDraft.date(effective.periodEnd).isAfter(budget.end)) {"Periode di luar tahun pemakaian aset."}
            require(AssetDraft.money(effective.amount)<=budget.amount) {"Nominal melebihi anggaran tahunan terakhir diketahui."}
        }
        store.saveAndQueue(session.scope.key,effective.id,effective.greenhouseId,effective.copy(lockedParents=true).localJson().put("completeness","ready"),existing.revision,effective.baseVersion,effective.replaceValidationRejected,kind=draft.kind.wire,queuePayload=effective.payload())
    }}
}
