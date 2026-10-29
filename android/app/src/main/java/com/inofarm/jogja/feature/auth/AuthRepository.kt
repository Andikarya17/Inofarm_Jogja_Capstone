package com.inofarm.jogja.feature.auth

import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.WireContract
import com.inofarm.jogja.core.database.LocalFinanceStore
import com.inofarm.jogja.core.network.FinanceApi
import com.inofarm.jogja.core.session.AccountScope
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

class AuthRepository(private val api:FinanceApi, val vault:FinanceSessionVault,private val store:LocalFinanceStore) {
    suspend fun restore() = withContext(Dispatchers.IO) { vault.restore() }
    suspend fun login(base:String,email:String,password:String):FinanceSession = withContext(Dispatchers.IO) {
        val normalized=api.validateBase(base);val ticket=vault.beginLogin()
        val result=api.request(normalized,null,"auth/login","POST",JSONObject().put("email",email.trim().lowercase()).put("password",password))
        val user=result.getJSONObject("user");val id=WireContract.uuid(user.getString("id"));val expires=result.getString("expires_at");Instant.parse(expires)
        val session=FinanceSession(AccountScope(normalized,id),result.getString("token"),expires,user.getString("name"),UserRole.parse(user.getString("role")))
        val status=api.request(normalized,session.token,"sync/status");require(status.getString("schema_version")=="2") { "Versi API tidak sesuai." }
        check(ticket==vault.generation) { "Sesi telah berubah." }
        store.initialize(session.scope.key,status.getString("sync_epoch"),session.role)
        check(vault.save(ticket,session)) { "Respons login lama diabaikan." }
        session
    }
    suspend fun logoutLocal() = withContext(Dispatchers.IO) { vault.logout() to vault.generation }
    suspend fun revoke(previous:FinanceSession) = withContext(Dispatchers.IO) { api.request(previous.scope.server,previous.token,"auth/logout","POST") }
}
