package com.inofarm.jogja.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat

/**
 * The only three vibrations Inofarm uses, each tied to a moment that means something. The system's touch-feedback setting
 * still decides whether they are felt. Turning [enabled] off silences all of them in one place.
 */
internal class FarmHaptics(private val feedback: HapticFeedback, private val enabled: Boolean) {
    /** The server accepted what was sent, or a check was decided "Sesuai". */
    fun confirm() = perform(HapticFeedbackType.Confirm)
    /** The login was refused, or a form was rejected by validation. */
    fun reject() = perform(HapticFeedbackType.Reject)
    /** A dock tab or a filter chip changed. */
    fun segmentTick() = perform(HapticFeedbackType.SegmentTick)

    private fun perform(type: HapticFeedbackType) { if (enabled) feedback.performHapticFeedback(type) }
}

@Composable
internal fun rememberFarmHaptics(enabled: Boolean = true): FarmHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback, enabled) { FarmHaptics(feedback, enabled) }
}

/**
 * Turns the content while it is in composition. Callers add it only while a sync is running, so it stops with the sync;
 * Compose tests and the system "remove animations" setting stop it too.
 */
@Composable
internal fun androidx.compose.ui.Modifier.farmSpinning(): androidx.compose.ui.Modifier {
    val turn = androidx.compose.animation.core.rememberInfiniteTransition(label = "Sinkron")
    val angle = turn.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(FarmMotion.SyncTurn, easing = androidx.compose.animation.core.LinearEasing)), label = "Sudut sinkron")
    return this.then(androidx.compose.ui.Modifier.graphicsLayer { rotationZ = angle.value })
}
