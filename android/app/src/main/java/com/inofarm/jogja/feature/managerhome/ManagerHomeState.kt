package com.inofarm.jogja.feature.managerhome

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.inofarm.jogja.core.session.FinanceSession
import com.inofarm.jogja.feature.activities.domain.ActivityDraft
import com.inofarm.jogja.feature.auth.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Composable
internal fun rememberManagerHome(
    repository: ManagerHomeRepository,
    session: FinanceSession,
    ticket: Long,
    selection: String?,
    syncUpdate: Long,
    localChange: Any?,
    refresh: Long,
): ManagerHomeState {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember(session.token, selection) { mutableStateOf(ManagerHomeState()) }
    LaunchedEffect(session.token, selection, syncUpdate, localChange, refresh, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (state.sessionRejected) return@repeatOnLifecycle
                val cached = try {
                    repository.cached(session, ticket, selection)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                val previous = state
                state = if (previous.snapshot != null && cached?.snapshot?.epoch == previous.snapshot.epoch &&
                    cached.snapshot.scope == previous.snapshot.scope) previous.copy(loading = true)
                else ManagerHomeState()
                state = try {
                    repository.load(session, ticket, selection)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    (cached ?: ManagerHomeState()).copy(loading = false, message = userMessage(error))
                }
                if (state.sessionRejected) return@repeatOnLifecycle
                val now = Instant.now()
                val midnight = LocalDate.now(ActivityDraft.WIB).plusDays(1).atStartOfDay(ActivityDraft.WIB).toInstant()
                val next = listOfNotNull(midnight, state.snapshot?.validUntil, Instant.parse(session.expiresAt).takeIf { it > now }).min()
                delay(Duration.between(now, next).toMillis().coerceAtLeast(1_000))
            }
        }
    }
    return state
}
