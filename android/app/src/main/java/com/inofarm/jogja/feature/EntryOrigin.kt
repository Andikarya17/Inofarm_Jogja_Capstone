package com.inofarm.jogja.feature

import com.inofarm.jogja.core.database.LocalRecord
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.core.session.FinanceSessionVault
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What a manager form opened with, so "Batal" can tell whether anything changed and put the device copy back.
 * Forms autosave while typing (the copy survives the app being killed); cancelling undoes that autosave.
 */
internal class EntryOrigin<T> {
    private var origin: LocalRecord? = null
    private var initial: T? = null
    /** Set on the first edit: from then on an autosave may have written the device copy. */
    var touched = false

    fun start(record: LocalRecord?, draft: T?) { origin = record; initial = draft; touched = false }

    fun changed(draft: T?) = draft != null && draft != initial

    /** Puts the device copy back as it was when the form opened. [onDone] gets null, or a message when that failed. */
    fun undo(scope: CoroutineScope, vault: FinanceSessionVault, id: String,
             discard: suspend (FinanceSession, Long, String, LocalRecord?) -> Unit, onDone: (String?) -> Unit) {
        val session = vault.current
        val from = origin
        val wrote = touched
        start(null, null)
        if (!wrote || session == null) return
        val ticket = vault.generation
        scope.launch {
            val message = try { discard(session, ticket, id, from); null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { userMessage(error) }
            if (vault.matches(ticket, session.scope)) onDone(message)
        }
    }
}
