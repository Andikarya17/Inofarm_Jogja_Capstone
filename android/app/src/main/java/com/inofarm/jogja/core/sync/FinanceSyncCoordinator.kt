package com.inofarm.jogja.core.sync

import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

data class SyncOutcome(val accepted: Int, val message: String)

/** Foreground coordinator. Queue and retry timestamps survive process death; no background scheduler dependency. */
class FinanceSyncCoordinator(private val api: FinanceApi, private val vault: FinanceSessionVault, private val store: LocalFinanceStore) {
    private val mutex=Mutex()
    suspend fun run(): SyncOutcome = withContext(Dispatchers.IO) {
        if(!mutex.tryLock()) return@withContext SyncOutcome(0,"Sinkronisasi sedang berjalan.")
        var capturedSession: FinanceSession? = null
        var capturedTicket = -1L
        try {
            val session=vault.current ?: return@withContext SyncOutcome(0,"Masuk untuk mengirim antrean akun ini.")
            val ticket=vault.generation
            capturedSession=session;capturedTicket=ticket
            if(session.expired()) return@withContext SyncOutcome(0,"Sesi berakhir. Masuk kembali dengan akun yang sama; draf dan antrean tetap disimpan.")
            val status=api.request(session.scope.server,session.token,"sync/status")
            if(!vault.matches(ticket,session.scope)) return@withContext SyncOutcome(0,"Sesi berubah; respons lama diabaikan.")
            store.initialize(session.scope.key,status.getString("sync_epoch"),session.role)
            store.resumeAuthentication(session.scope.key)
            pull(session,ticket)
            var accepted=0
            repeat(50) {
                if(!vault.matches(ticket,session.scope)) return@withContext SyncOutcome(accepted,"Sesi berubah; antrean akun sebelumnya ditahan.")
                val op=store.next(session.scope.key) ?: return@withContext SyncOutcome(accepted,if(accepted>0) "$accepted catatan diterima server." else "Data server sudah diperiksa. Tinjau antrean yang ditahan.")
                require(WireContract.hash(op.envelope) == op.hash) { "Isi antrean berubah; pengiriman ditahan." }
                if(op.envelope.getString("sync_epoch") != store.epoch(session.scope.key)) {
                    store.mark(session.scope.key,op,SyncState.EPOCH_REVIEW,JSONObject().put("message","Epoch operasi lama. Rekonsiliasi diperlukan."));return@withContext SyncOutcome(accepted,"Antrean epoch lama ditahan.")
                }
                try {
                    val result=api.request(session.scope.server,session.token,"sync/operations","POST",op.envelope)
                    if(!vault.matches(ticket,session.scope)) return@withContext SyncOutcome(accepted,"Sesi berubah; respons lama diabaikan.")
                    store.acknowledge(session.scope.key,op.id,result);accepted++
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(error: FinanceApiFailure) {
                    if(!vault.matches(ticket,session.scope)) return@withContext SyncOutcome(accepted,"Sesi berubah.")
                    val state=RetryPolicy.state(error)
                    store.mark(session.scope.key,op,state,error.detail,if(state==SyncState.QUEUED) System.currentTimeMillis()+RetryPolicy.delayMillis(op.attempts,error.retryAfterSeconds) else 0)
                    if(state==SyncState.AUTH_REQUIRED || state==SyncState.EPOCH_REVIEW || state==SyncState.QUEUED) return@withContext SyncOutcome(accepted,error.message ?: "Pengiriman ditahan.")
                } catch(error: IOException) {
                    store.mark(session.scope.key,op,SyncState.QUEUED,JSONObject().put("message","Koneksi belum tersedia. Operasi yang sama akan dicoba lagi."),System.currentTimeMillis()+RetryPolicy.delayMillis(op.attempts))
                    return@withContext SyncOutcome(accepted,"Offline atau koneksi terputus. Draf dan antrean tetap di perangkat.")
                }
            }
            pull(session,ticket)
            SyncOutcome(accepted,"$accepted catatan diterima. Jalankan kembali untuk sisa antrean.")
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: FinanceApiFailure) {
            val state=RetryPolicy.state(error)
            val session=capturedSession
            if(session != null && vault.matches(capturedTicket,session.scope) && state in setOf(SyncState.AUTH_REQUIRED,SyncState.SCOPE_REVOKED,SyncState.EPOCH_REVIEW)) store.hold(session.scope.key,state,error.detail)
            SyncOutcome(0,error.message ?: "Sinkronisasi perlu ditinjau.")
        } catch(error: IOException) {
            SyncOutcome(0,"Offline atau server belum dapat dihubungi. Draf dan antrean tetap di perangkat.")
        } finally { mutex.unlock() }
    }
    private suspend fun pull(session: FinanceSession,ticket: Long) {
        repeat(100) {
            if(!vault.matches(ticket,session.scope)) return
            val params=store.pullCursor(session.scope.key)?.let { mapOf("cursor" to it) } ?: emptyMap()
            val page=api.request(session.scope.server,session.token,"sync/changes",query=params)
            if(!vault.matches(ticket,session.scope)) return
            store.applyPull(session.scope.key,page)
            if(!page.getBoolean("has_more")) return
        }
        error("Pull belum selesai setelah 100 halaman; lanjutkan pada sinkronisasi berikutnya.")
    }
}
