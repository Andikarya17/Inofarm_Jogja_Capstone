package com.inofarm.jogja.feature.auth

import android.content.Context
import androidx.core.content.edit

/**
 * The email of the last account that signed in on this device, so a field worker does not retype it every session.
 * Only the email is kept, never the password, and "Bukan saya" on the login screen removes it for shared phones.
 */
internal object LastLoginEmail {
    private const val FILE = "inofarm_login_hint"
    private const val KEY = "email"

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(context: Context): String = preferences(context).getString(KEY, "").orEmpty()

    fun save(context: Context, email: String) = preferences(context).edit { putString(KEY, normalize(email)) }

    fun forget(context: Context) = preferences(context).edit { remove(KEY) }

    /** The same normalisation the login request applies, so the remembered value matches what the server accepted. */
    fun normalize(email: String): String = email.trim().lowercase()
}
