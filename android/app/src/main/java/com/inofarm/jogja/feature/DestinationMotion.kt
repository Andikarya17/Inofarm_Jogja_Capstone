package com.inofarm.jogja.feature

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.FarmMotion

/**
 * Where the shell is, derived from the screen state that already exists. Only its identity matters: two values that are
 * equal never animate, so a greenhouse scope change on the manager home (still [Tab] 0) stays bound at once.
 */
internal sealed interface FinanceDestination {
    /** 0 = a tab, 1 = a page opened from a tab, 2 = a form or detail, 3 = the review of a form. */
    val depth: Int
    data class Tab(val index: Int) : FinanceDestination { override val depth = 0 }
    data class Page(val name: String) : FinanceDestination { override val depth = 1 }
    data class Detail(val kind: String) : FinanceDestination { override val depth = 2 }
    data class Form(val kind: String) : FinanceDestination { override val depth = 2 }
    data class Review(val kind: String) : FinanceDestination { override val depth = 3 }
}

internal enum class FinanceTransition { None, Forward, Backward, Tab, Fade }

/**
 * A dock tap ([dockChanged]) is always a fade-through, even from a page opened inside a tab. Otherwise deeper is forward,
 * shallower is back, tab to tab is a fade-through and anything else at the same depth a plain fade.
 */
internal fun financeTransition(from: FinanceDestination, to: FinanceDestination, dockChanged: Boolean = false): FinanceTransition = when {
    from == to -> FinanceTransition.None
    dockChanged -> FinanceTransition.Tab
    to.depth > from.depth -> FinanceTransition.Forward
    to.depth < from.depth -> FinanceTransition.Backward
    from is FinanceDestination.Tab && to is FinanceDestination.Tab -> FinanceTransition.Tab
    else -> FinanceTransition.Fade
}

/** Drawing state, read only in the draw phase so a running transition never recomposes the screen. */
private class DestinationLayers(var live: GraphicsLayer, var shown: FinanceDestination, var dock: Int) {
    var outgoing: GraphicsLayer? = null
    var transition = FinanceTransition.None
    // Where the leaving picture was when it started to leave: a half-arrived screen or a Back preview keeps its place.
    var outAlpha = 1f
    var outShift = 0f
    var outScale = 1f
    var outPivot = Offset.Unspecified
    /** Layers that left the screen; released a few frames later, once the render thread has finished with them. */
    val retired = ArrayList<GraphicsLayer>()
}

/**
 * Animates the shell body when [destination] changes. The leaving screen is the last frame it drew, held as a still
 * picture, so it never shows the state of the screen that replaced it. The arriving screen is live from its first frame.
 * [dock] is the selected dock tab: a change there is a tab switch whatever the depth. [backProgress] (0..1) previews a
 * predictive Back gesture on the current screen. With the system animator scale at zero the transition ends at once: a
 * cut that still shows the new state.
 */
@Composable
internal fun FarmDestinationMotion(
    destination: FinanceDestination,
    dock: Int,
    modifier: Modifier = Modifier,
    backProgress: () -> Float = { 0f },
    backFromLeftEdge: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    val graphics = LocalGraphicsContext.current
    val layers = remember { DestinationLayers(graphics.createGraphicsLayer(), destination, dock) }
    var progress by remember { mutableFloatStateOf(1f) }
    var run by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val shift = with(density) { 30.dp.toPx() }
    val backShift = with(density) { FarmMotion.BackPreviewShift.toPx() }
    // Applied once the composition is committed and before the next draw, so the leaving layer still holds its last frame.
    SideEffect {
        if (destination == layers.shown) { layers.dock = dock; return@SideEffect }
        val transition = financeTransition(layers.shown, destination, dock != layers.dock)
        val old = layers.live
        val back = backProgress()
        layers.outgoing?.let(layers.retired::add)
        layers.outgoing = old
        if (back > 0f) {
            val left = backFromLeftEdge()
            layers.outAlpha = 1f
            layers.outScale = 1f - (1f - FarmMotion.BackPreviewScale) * back
            layers.outShift = (if (left) 1f else -1f) * backShift * back
            layers.outPivot = old.pivotOffset
        } else {
            layers.outAlpha = old.alpha
            layers.outScale = old.scaleX
            layers.outShift = old.translationX
            layers.outPivot = Offset.Unspecified
        }
        layers.live = graphics.createGraphicsLayer()
        layers.transition = transition
        layers.shown = destination
        layers.dock = dock
        progress = 0f
        run++
    }
    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        val duration = if (layers.transition == FinanceTransition.Fade) FarmMotion.State else FarmMotion.Depth
        animate(0f, 1f, animationSpec = tween(duration, easing = LinearEasing)) { value, _ -> progress = value }
        progress = 1f
        layers.outgoing?.let(layers.retired::add)
        layers.outgoing = null
        // Two frames: the last display list that drew the leaving picture is off the render thread before it is freed.
        withFrameNanos { }
        withFrameNanos { }
        layers.retired.forEach(graphics::releaseGraphicsLayer)
        layers.retired.clear()
    }
    DisposableEffect(Unit) {
        onDispose {
            graphics.releaseGraphicsLayer(layers.live)
            layers.outgoing?.let(graphics::releaseGraphicsLayer)
            layers.retired.forEach(graphics::releaseGraphicsLayer)
            layers.retired.clear()
        }
    }
    Box(modifier.drawWithContent {
        val live = layers.live
        live.record { this@drawWithContent.drawContent() }
        val t = progress
        val out = layers.outgoing
        val enter = FarmMotion.EmphasizedDecelerate.transform(t)
        out?.pivotOffset = layers.outPivot
        when (layers.transition) {
            FinanceTransition.Forward, FinanceTransition.Backward -> {
                val direction = if (layers.transition == FinanceTransition.Forward) 1f else -1f
                // The leaving screen is gone by 250 of the 300 ms, so exit stays quicker than entrance.
                val exit = FarmMotion.EmphasizedAccelerate.transform((t / FarmMotion.DepthExitShare).coerceIn(0f, 1f))
                out?.apply {
                    alpha = layers.outAlpha * (1f - exit); translationX = layers.outShift - direction * shift * exit
                    scaleX = layers.outScale; scaleY = layers.outScale
                }
                live.alpha = enter; live.translationX = direction * shift * (1f - enter)
                live.scaleX = 1f; live.scaleY = 1f
            }
            FinanceTransition.Tab -> {
                // Fade-through: the old tab fades out in the first 90 ms, the new one fades and scales in for 210 ms.
                val split = FarmMotion.TabExit.toFloat() / FarmMotion.Depth
                out?.apply {
                    alpha = layers.outAlpha * (1f - (t / split).coerceIn(0f, 1f)); translationX = layers.outShift
                    scaleX = layers.outScale; scaleY = layers.outScale
                }
                val arrive = FarmMotion.EmphasizedDecelerate.transform(((t - split) / (1f - split)).coerceIn(0f, 1f))
                val scale = FarmMotion.TabEnterScale + (1f - FarmMotion.TabEnterScale) * arrive
                live.alpha = arrive; live.translationX = 0f; live.scaleX = scale; live.scaleY = scale
            }
            else -> {
                out?.apply {
                    alpha = layers.outAlpha * (1f - t); translationX = layers.outShift
                    scaleX = layers.outScale; scaleY = layers.outScale
                }
                live.alpha = if (out == null) 1f else t; live.translationX = 0f; live.scaleX = 1f; live.scaleY = 1f
            }
        }
        // A held Back gesture shrinks the current screen toward the edge it will leave by.
        val back = backProgress()
        if (back > 0f && t >= 1f) {
            val left = backFromLeftEdge()
            val scale = 1f - (1f - FarmMotion.BackPreviewScale) * back
            live.pivotOffset = Offset(if (left) size.width else 0f, size.height / 2f)
            live.scaleX = scale; live.scaleY = scale
            live.translationX = (if (left) 1f else -1f) * backShift * back
        } else live.pivotOffset = Offset.Unspecified
        out?.let { drawLayer(it) }
        drawLayer(live)
    }) { content() }
}

/** Spring used to return a cancelled Back preview to rest. */
internal fun <T> backCancelSpring() = spring<T>(dampingRatio = 0.8f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium)
