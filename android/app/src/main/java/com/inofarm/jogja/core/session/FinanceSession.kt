package com.inofarm.jogja.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.inofarm.jogja.core.contracts.UserRole
import com.inofarm.jogja.core.contracts.WireContract
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface PayloadCipher {
    fun encode(scope: String, value: String): String
    fun decode(scope: String, value: String): String
}

class AndroidPayloadCipher : PayloadCipher {
    private var cachedKey: SecretKey? = null
    @Synchronized private fun key(): SecretKey {
        cachedKey?.let { return it }
        val alias = "inofarm_finance_payload_v2"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return ((store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()).also { cachedKey = it }
    }
    override fun encode(scope: String, value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(scope.toByteArray(Charsets.UTF_8)) }
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    override fun decode(scope: String, value: String): String {
        val parts = value.split(':'); require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            updateAAD(scope.toByteArray(Charsets.UTF_8))
        }
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
}

data class AccountScope(val server: String, val accountId: String) {
    init { WireContract.uuid(accountId) }
    val key: String get() = MessageDigest.getInstance("SHA-256").digest("$server\n$accountId".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
data class FinanceSession(val scope: AccountScope, val token: String, val expiresAt: String, val name: String, val role: UserRole) {
    override fun toString() = "FinanceSession(account=${scope.accountId}, role=$role, expiresAt=$expiresAt)"
    fun expired(now: Instant = Instant.now()) = !now.isBefore(Instant.parse(expiresAt))
    fun json() = JSONObject().put("server", scope.server).put("account_id", scope.accountId).put("token", token).put("expires_at", expiresAt).put("name", name).put("role", role.wire)
    companion object {
        fun parse(json: JSONObject) = FinanceSession(AccountScope(json.getString("server"), json.getString("account_id")), json.getString("token"), json.getString("expires_at"), json.getString("name"), UserRole.parse(json.getString("role")))
    }
}

/** Only session credentials are cleared on logout. This class never deletes the local database. */
class FinanceSessionVault(context: Context, private val cipher: PayloadCipher, preferenceName: String = "inofarm_finance_session_v2") {
    private val preferences = context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val counter = AtomicLong()
    @Volatile private var snapshot: FinanceSession? = null
    @Volatile private var loaded = false
    val generation get() = counter.get()
    val current get() = snapshot
    @Synchronized fun restore(): FinanceSession? {
        if (!loaded) {
            snapshot = preferences.getString("session", null)?.let { FinanceSession.parse(JSONObject(cipher.decode("session-v2", it))) }
            loaded = true
        }
        return snapshot
    }
    @Synchronized fun beginLogin(): Long {
        check(preferences.edit().remove("session").commit()) { "Sesi lama gagal dilepas dari perangkat." }
        snapshot = null; loaded = true
        return counter.incrementAndGet()
    }
    @Synchronized fun save(ticket: Long, session: FinanceSession): Boolean {
        if(ticket != generation) return false
        check(preferences.edit().putString("session", cipher.encode("session-v2", session.json().toString())).commit()) { "Sesi gagal disimpan." }
        snapshot = session; loaded = true
        return true
    }
    @Synchronized fun logout(): FinanceSession? {
        check(preferences.edit().remove("session").commit()) { "Sesi gagal dihapus dari perangkat." }
        val old = snapshot; counter.incrementAndGet(); snapshot = null; loaded = true
        return old
    }
    fun matches(ticket: Long, scope: AccountScope) = ticket == generation && snapshot?.scope == scope
}
