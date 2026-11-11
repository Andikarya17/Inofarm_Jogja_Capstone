package com.inofarm.jogja.feature.exports

import android.content.Context
import android.net.Uri
import com.inofarm.jogja.core.contracts.*
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

data class ReportExportSelection(val scope:String,val id:String,val greenhouse:String,val kind:String,val hash:String,val format:String) {
    val mime get()=if(format=="pdf") "application/pdf" else "text/csv"
    val filename get()="inofarm-$kind-$id.$format"
    fun json()=JSONObject().put("scope",scope).put("id",id).put("greenhouse",greenhouse).put("kind",kind).put("hash",hash).put("format",format)
    companion object {
        fun from(scope:String,row:LocalRecord,format:String):ReportExportSelection {
            require(row.serverVersion==1L && row.kind in setOf(SeasonReportContract.KIND,InvestmentContract.REPORT) && format in setOf("pdf","csv")) {"Laporan bisa diunduh setelah terkirim."}
            val body=if(row.kind==SeasonReportContract.KIND) SeasonReportContract.validate(row.serverBody ?: row.body,true) else InvestmentContract.report(row.serverBody ?: row.body,true)
            require(body.getString("id")==row.id && body.getString("greenhouse_id")==row.greenhouseId)
            return ReportExportSelection(scope,row.id,row.greenhouseId,row.kind,body.getString("snapshot_hash"),format)
        }
        fun parse(body:JSONObject)=ReportExportSelection(body.getString("scope"),body.getString("id"),body.getString("greenhouse"),body.getString("kind"),body.getString("hash"),body.getString("format"))
    }
}

class ReportExportRepository(private val context:Context,val vault:FinanceSessionVault,private val store:LocalFinanceStore,private val api:FinanceApi) {
    private val downloads=Mutex()
    private fun check(session:FinanceSession,ticket:Long,selection:ReportExportSelection) {
        check(vault.matches(ticket,session.scope));require(session.role==UserRole.MANAGER && session.scope.key==selection.scope) {"Akun pengelola berubah. Buka kembali laporan."}
        require(selection.greenhouse in store.visibleGreenhouses(session.scope.key)) {"Akses ke greenhouse laporan ini berubah. Buka kembali laporan."}
        val row=store.record(session.scope.key,selection.id) ?: error("Laporan belum tersimpan.")
        require(row.syncState !in setOf(SyncState.EPOCH_REVIEW,SyncState.SCOPE_REVOKED) && ReportExportSelection.from(session.scope.key,row,selection.format)==selection) {"Salinan laporan berubah. Buka kembali sebelum mengunduh."}
    }
    suspend fun save(session:FinanceSession,ticket:Long,selection:ReportExportSelection,uri:Uri)=download(session,ticket,selection) {bytes->
        require(uri.scheme=="content") {"Pilih tujuan berkas melalui penyimpanan Android."}
        context.contentResolver.openOutputStream(uri,"wt")?.use {it.write(bytes);it.flush()} ?: throw IOException("Berkas tujuan tidak bisa dibuka. Pilih lokasi lain.")
    }
    /** A destination is invoked only after transport integrity and current session/scope checks. */
    suspend fun download(session:FinanceSession,ticket:Long,selection:ReportExportSelection,destination:(ByteArray)->Unit)=withContext(Dispatchers.IO) {downloads.withLock {
        check(session,ticket,selection)
        val file=api.exportReport(session.scope.server,session.token,selection.id,selection.format,selection.hash)
        check(session,ticket,selection);require(file.snapshotHash==selection.hash && file.mime==selection.mime)
        destination(file.bytes);check(vault.matches(ticket,session.scope)) {"Akun berubah saat menyimpan. Periksa berkas yang tersimpan."}
        selection.filename
    }}
}
