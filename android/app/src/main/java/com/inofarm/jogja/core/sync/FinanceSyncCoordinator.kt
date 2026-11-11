package com.inofarm.jogja.core.sync

import com.inofarm.jogja.core.contracts.SyncState
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import com.inofarm.jogja.core.contracts.AttachmentContract
import com.inofarm.jogja.core.contracts.LocalAttachmentFailure

enum class ContextCheck { UNCHECKED, CHECKED, OFFLINE, FAILED, ACCESS_CHANGED }

data class SyncOutcome(val accepted: Int, val message: String, val contextCheck: ContextCheck = ContextCheck.UNCHECKED)

/** Shared foreground/job coordinator. Queue, retry timestamps and epoch holds survive process death. */
class FinanceSyncCoordinator(private val api: FinanceApi, private val vault: FinanceSessionVault, private val store: LocalFinanceStore,private val attachments:AttachmentTransfer?=null) {
    private val mutex=Mutex()
    private val completedRuns = MutableStateFlow(0L)
    val updates = completedRuns.asStateFlow()
    suspend fun run(waitForTurn: Boolean = false): SyncOutcome = withContext(Dispatchers.IO) {
        if (waitForTurn) mutex.lock()
        else if (!mutex.tryLock()) return@withContext SyncOutcome(0,"Sedang memperbarui.")
        var capturedSession: FinanceSession? = null
        var capturedTicket = -1L
        try {
            var session=vault.current ?: return@withContext SyncOutcome(0,"Masuk untuk mengirim catatan yang belum terkirim.")
            val ticket=vault.generation
            capturedSession=session;capturedTicket=ticket
            if(session.expired()) return@withContext SyncOutcome(0,"Sesi berakhir. Masuk lagi dengan akun yang sama. Catatan yang belum terkirim tetap tersimpan.", ContextCheck.ACCESS_CHANGED)
            val status=api.request(session.scope.server,session.token,"sync/status")
            if(!vault.matches(ticket,session.scope)) return@withContext SyncOutcome(0,"Akun berubah. Coba lagi.")
            require(status.optString("schema_version","2")=="2") {"Aplikasi perlu diperbarui agar bisa mengirim data."}
            status.optJSONObject("user")?.let {user ->
                require(WireContract.uuid(user.getString("id"))==session.scope.accountId) {"Akun tidak cocok. Masuk lagi."}
                val refreshed=session.copy(name=user.getString("name"),role=UserRole.parse(user.getString("role")))
                if(refreshed!=session) check(vault.save(ticket,refreshed)) {"Akun berubah. Coba lagi."}
                session=refreshed;capturedSession=refreshed
            }
            store.initialize(session.scope.key,status.getString("sync_epoch"),session.role)
            store.resumeAuthentication(session.scope.key)
            pull(session,ticket)
            var accepted=0
            repeat(50) {
                if(!vault.matches(ticket,session.scope)) return@withContext SyncOutcome(accepted,"Akun berubah. Catatan akun sebelumnya ditahan.")
                val op=store.next(session.scope.key) ?: run {
                    if(accepted>0) pull(session,ticket)
                    return@withContext SyncOutcome(accepted,if(accepted>0) "$accepted catatan terkirim." else "Data sudah diperbarui.", ContextCheck.CHECKED)
                }
                if(runCatching {WireContract.hash(op.envelope)}.getOrNull() != op.hash) {
                    store.mark(session.scope.key,op,SyncState.REJECTED,JSONObject().put("code","canonical_hash_mismatch").put("message","Catatan ini tidak bisa dikirim karena datanya berubah. Hubungi operator sistem."))
                    return@withContext SyncOutcome(accepted,"Ada catatan yang tidak bisa dikirim. Cek catatan yang belum terkirim.")
                }
                if(op.envelope.getString("sync_epoch") != store.epoch(session.scope.key)) {
                    store.mark(session.scope.key,op,SyncState.EPOCH_REVIEW,JSONObject().put("message","Data di server dipulihkan. Catatan ini perlu dicek."));return@withContext SyncOutcome(accepted,"Data di server dipulihkan. Beberapa catatan perlu dicek sebelum dikirim.")
                }
                try {
                    val route=runCatching {OperationRoute.forOperation(op.envelope.getString("entity_type"),op.envelope.getString("action"),op.entityId,session.role)}.getOrElse {
                        store.mark(session.scope.key,op,SyncState.REJECTED,JSONObject().put("code","unsupported_operation").put("message",it.message))
                        return@withContext SyncOutcome(accepted,"Jenis catatan ini belum bisa dikirim. Isinya tetap tersimpan.")
                    }
                    val result=if(op.envelope.getString("entity_type") in AttachmentContract.kinds && op.envelope.getString("action")=="create") {
                        (attachments ?: throw LocalAttachmentFailure("Pengiriman berkas belum tersedia. Berkas tetap tersimpan.")).upload(api,session,op)
                    } else api.request(session.scope.server,session.token,route.path,route.method,op.envelope)
                    if(!vault.matches(ticket,session.scope)) return@withContext SyncOutcome(accepted,"Akun berubah. Coba lagi.")
                    store.acknowledge(session.scope.key,op.id,result);accepted++
                } catch(cancelled: CancellationException) { throw cancelled }
                catch(error:LocalAttachmentFailure) {
                    if(!vault.matches(ticket,session.scope)) return@withContext SyncOutcome(accepted,"Akun berubah. Coba lagi.")
                    store.mark(session.scope.key,op,SyncState.REJECTED,JSONObject().put("code","local_attachment_unavailable").put("message",error.message))
                    return@withContext SyncOutcome(accepted,error.message ?: "Berkas perlu dicek.")
                }
                catch(error: FinanceApiFailure) {
                    if(!vault.matches(ticket,session.scope)) return@withContext SyncOutcome(accepted,"Akun berubah. Coba lagi.")
                    val state=RetryPolicy.state(error)
                    store.mark(session.scope.key,op,state,error.detail,if(state==SyncState.QUEUED) System.currentTimeMillis()+RetryPolicy.delayMillis(op.attempts,error.retryAfterSeconds) else 0)
                    if(state==SyncState.AUTH_REQUIRED || state==SyncState.EPOCH_REVIEW || state==SyncState.QUEUED) return@withContext SyncOutcome(accepted,error.message ?: "Pengiriman ditunda. Coba lagi nanti.")
                } catch(error: IOException) {
                    store.mark(session.scope.key,op,SyncState.QUEUED,JSONObject().put("message","Tidak ada koneksi. Akan dicoba lagi otomatis."),System.currentTimeMillis()+RetryPolicy.delayMillis(op.attempts))
                    return@withContext SyncOutcome(accepted,"Tidak ada koneksi. Catatan tetap tersimpan dan terkirim otomatis saat ada internet.")
                }
            }
            pull(session,ticket)
            SyncOutcome(accepted,"$accepted catatan terkirim. Perbarui lagi untuk mengirim sisanya.", ContextCheck.CHECKED)
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: FinanceApiFailure) {
            val state=RetryPolicy.state(error)
            val session=capturedSession
            if(session != null && vault.matches(capturedTicket,session.scope) && state in setOf(SyncState.AUTH_REQUIRED,SyncState.SCOPE_REVOKED,SyncState.EPOCH_REVIEW)) store.hold(session.scope.key,state,error.detail)
            SyncOutcome(0,error.message ?: "Belum berhasil memperbarui. Coba lagi.",
                if (state in setOf(SyncState.AUTH_REQUIRED, SyncState.SCOPE_REVOKED)) ContextCheck.ACCESS_CHANGED else ContextCheck.FAILED)
        } catch(error: IOException) {
            SyncOutcome(0,"Tidak ada koneksi. Catatan tetap tersimpan dan terkirim otomatis saat ada internet.", ContextCheck.OFFLINE)
        } finally {
            completedRuns.update { it + 1 }
            mutex.unlock()
        }
    }
    private suspend fun pull(session: FinanceSession,ticket: Long) {
        repeat(100) {
            if(!vault.matches(ticket,session.scope)) return
            val params=store.pullCursor(session.scope.key)?.let { mapOf("cursor" to it) } ?: emptyMap()
            val page=api.request(session.scope.server,session.token,"sync/changes",
                query=params + mapOf("activity_reviews" to "1", "measurement_review_status" to "1", "harvest_reviews" to "1"))
            if(!vault.matches(ticket,session.scope)) return
            store.applyPull(session.scope.key,page)
            if(!page.getBoolean("has_more")) return
        }
        error("Data belum selesai dimuat. Perbarui lagi.")
    }
}
