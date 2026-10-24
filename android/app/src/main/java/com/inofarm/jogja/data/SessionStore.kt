package com.inofarm.jogja.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Blocking persistence methods run on IO. UI getters only inspect immutable memory. */
class SessionStore(context: Context) {
    private val context = context.applicationContext
    private val preferences by lazy { this.context.getSharedPreferences("inofarm_session", Context.MODE_PRIVATE) }
    private data class Snapshot(val token: String?, val expiry: String?, val started: Long, val scope: String?, val ticket: Long, val base: String)
    @Volatile private var snapshot: Snapshot? = null
    @Volatile private var loaded = false
    @Volatile private var server = ""
    private var secretKey: SecretKey? = null
    private val counter = AtomicLong()
    val generation: Long get() = counter.get()
    val expiresAt: String? get() = snapshot?.expiry
    val validToken: String? get() = snapshot?.takeIf { valid(it) }?.token
    val cacheScope: String? get() = snapshot?.takeIf { valid(it) && it.expiry != null }?.scope
    var baseUrl: String
        get() = server
        set(value) { server=value; preferences.edit().putString("baseUrl",value).commit() }
    private fun valid(value: Snapshot): Boolean = runCatching {
        val now=Instant.now()
        value.ticket==generation && value.base==server && now.toEpochMilli() >= value.started && (value.expiry == null || now.isBefore(Instant.parse(value.expiry)))
    }.getOrDefault(false)
    private fun key(): SecretKey {
        secretKey?.let { return it }
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias="inofarm_owner_session"
        return (store.getKey(alias,null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()).also { secretKey=it }
    }
    private fun scope(): String? {
        val account=preferences.getString("account",null) ?: return null
        val tank=preferences.getString("tank",null) ?: return null
        return MessageDigest.getInstance("SHA-256").digest("$server|$account|$tank".toByteArray()).joinToString("") { "%02x".format(it) }
    }
    @Synchronized private fun restoreToken(): String? {
        if(!loaded) {
            val ticket=generation
            val restored=runCatching {
                server=preferences.getString("baseUrl","") ?: ""
                val encoded=preferences.getString("token",null)
                Snapshot(encoded?.let { decrypt(preferences.getString("iv","")+":"+it) },preferences.getString("expiresAt",null),preferences.getLong("startedAt",0),scope(),ticket,server)
            }.getOrNull()
            if(ticket != generation) return null
            snapshot=restored; loaded=true
        }
        if(snapshot==null) return null
        if(snapshot?.let { !valid(it) } == true) return null
        return snapshot?.token
    }
    fun readToken(): String? {
        val ticket=generation
        val result=restoreToken()
        if(result==null && snapshot?.ticket==ticket && ticket==generation) clear()
        return result
    }
    @Synchronized fun saveToken(value: String) {
        val encrypted=encrypt(value).split(':')
        preferences.edit().putString("token",encrypted[1]).putString("iv",encrypted[0]).commit()
        loaded=true; snapshot=Snapshot(value,null,0,null,generation,server)
    }
    @Synchronized fun identity(user: JSONObject?, expiresAt: String?) {
        val started=Instant.now().toEpochMilli()
        preferences.edit().putString("account",user?.optLong("id",0)?.takeIf { it>0 }?.toString()).putString("tank",user?.optLong("tank_id",0)?.takeIf { it>0 }?.toString()).putString("expiresAt",expiresAt).putLong("startedAt",started).commit()
        snapshot=Snapshot(snapshot?.token,expiresAt,started,scope(),generation,server)
    }
    @Synchronized fun saveLogin(ticket: Long, token: String, user: JSONObject?, expiry: String?) {
        if(ticket != generation) return
        saveToken(token); identity(user,expiry)
        if(ticket != generation) snapshot=null
    }
    /** Immediate revocation, including restore/write tickets. No disk or lock on UI. */
    fun invalidate(): Long { val ticket=counter.incrementAndGet(); snapshot=null; loaded=true; return ticket }
    fun requestSession(): ApiSession {
        val current=snapshot
        return if(current!=null && valid(current)) ApiSession(current.base,current.token) else ApiSession(server,null)
    }
    @Synchronized private fun clearPreferences(ticket: Long) {
        if(ticket != generation) return
        preferences.edit().remove("token").remove("iv").remove("account").remove("tank").remove("expiresAt").remove("startedAt").commit()
    }
    fun clearIfCurrent(ticket: Long) {clearPreferences(ticket);ReadCache.clearFiles(context, this, ticket)}
    fun clear() { clearIfCurrent(invalidate()) }
    @Synchronized fun encrypt(text: String): String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        return Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
    }
    @Synchronized fun decrypt(text: String): String {
        val parts=text.split(':'); require(parts.size==2)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP))) }
        return cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
}
