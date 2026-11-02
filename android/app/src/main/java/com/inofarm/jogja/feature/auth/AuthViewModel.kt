package com.inofarm.jogja.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inofarm.jogja.core.network.FinanceApiFailure
import com.inofarm.jogja.core.session.FinanceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

fun userMessage(error:Throwable):String = when(error) {
    is android.database.sqlite.SQLiteException, is java.security.GeneralSecurityException -> "Penyimpanan lokal belum berhasil. Isian tetap tersedia; coba simpan kembali."
    is FinanceApiFailure -> {
        val messages=mutableListOf(error.message.orEmpty())
        error.detail.optJSONArray("reasons")?.let { reasons -> repeat(reasons.length()) { messages.add(reasons.getString(it)) } }
        error.detail.optJSONObject("field_errors")?.let { fields ->
            fields.keys().forEach { key -> fields.optJSONArray(key)?.let { values -> repeat(values.length()) { messages.add("$key: ${values.getString(it)}") } } }
        }
        messages.distinct().joinToString("\n")
    }
    is IOException -> "Server belum dapat dihubungi. Periksa koneksi; draf tetap disimpan di perangkat."
    else -> error.message ?: "Permintaan belum berhasil. Coba kembali."
}
data class AuthUiState(val loading:Boolean=true,val session:FinanceSession?=null,val error:String?=null)
class AuthViewModel(private val repository:AuthRepository):ViewModel() {
    private val mutable=MutableStateFlow(AuthUiState());val state=mutable.asStateFlow()
    init { viewModelScope.launch { try { mutable.value=AuthUiState(false,repository.restore()) } catch(error:Exception) { mutable.value=AuthUiState(false,error="Sesi tersimpan belum dapat dibuka. Masuk kembali; database lokal tetap dipertahankan.") } } }
    fun login(base:String,email:String,password:String) {
        if(state.value.loading) return
        mutable.value=AuthUiState(loading=true)
        viewModelScope.launch { try { mutable.value=AuthUiState(false,repository.login(base,email,password)) }
            catch(cancelled:CancellationException) { throw cancelled } catch(error:Exception) { mutable.value=AuthUiState(false,error=userMessage(error)) } }
    }
    fun clearError() {mutable.value=state.value.copy(error=null)}
    fun logout(beforeRelease:suspend ()->Unit={}) {
        if(state.value.loading) return
        val previous=state.value
        mutable.value=previous.copy(loading=true,error=null)
        viewModelScope.launch {
            var draftFlushed=false
            try {
                beforeRelease();draftFlushed=true
                val (previous,ticket)=repository.logoutLocal()
                mutable.value=AuthUiState(false,error="Keluar. Draf dan antrean akun ini tetap tersimpan.")
                if(previous!=null) try { repository.revoke(previous) } catch(cancelled:CancellationException) {throw cancelled}
                catch(error:Exception) { if(repository.vault.generation==ticket && repository.vault.current==null) mutable.value=AuthUiState(false,error="Keluar dari perangkat. Draf tetap tersimpan; pencabutan sesi server belum terkonfirmasi.") }
            } catch(cancelled:CancellationException) {throw cancelled} catch(error:Exception) {
                mutable.value=AuthUiState(false,repository.vault.current,error=if(!draftFlushed) "Isian terakhir belum berhasil disimpan. Anda tetap di akun ini; coba simpan draf kembali." else userMessage(error))
            }
        }
    }
}
