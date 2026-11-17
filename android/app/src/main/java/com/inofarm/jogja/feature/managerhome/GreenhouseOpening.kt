package com.inofarm.jogja.feature.managerhome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.inofarm.jogja.core.design.FarmMotion

/**
 * The one authored moment on the manager side: the tapped greenhouse card opens into the forest hero of its detail page
 * (Material container transform). Only a card tap starts it; a finding, a deep link or Back still change the scope at
 * once. The detail never shows data of the list it came from: the list is gone in the first frame, and only the card's
 * outline travels.
 *
 * Lives above the manager home because the home is recomposed from scratch for every greenhouse scope.
 */
@Stable
internal class GreenhouseOpening {
    private var id by mutableStateOf<String?>(null)
    private var origin: Rect? = null
    private var running by mutableStateOf(false)
    private val progress = Animatable(1f)
    /** The hero's own place, measured before it is first drawn. */
    private var target: Rect? = null

    fun start(greenhouse: String, from: Rect?) {
        if (from == null || from.isEmpty) { clear(); return }
        id = greenhouse; origin = from; running = false
    }

    fun clear() { id = null; origin = null; running = false }

    /** True from the tap until the hero has arrived: the detail waits, hidden, instead of showing a loading panel. */
    fun pending(selection: String?): Boolean = id != null && id == selection

    /** The cached summary did not answer in time: give up the transform and show the page the ordinary way. */
    fun abandonIfWaiting(selection: String?) { if (pending(selection) && !running) clear() }

    /** Called once the hero is in the composition. Ends at once with the system animator scale at zero. */
    suspend fun play(selection: String?) {
        if (!pending(selection) || running) return
        running = true
        try {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(FarmMotion.ContainerOpen, easing = FarmMotion.EmphasizedDecelerate))
        } finally {
            clear()
        }
    }

    private fun t(): Float = if (id == null) 1f else if (!running) 0f else progress.value

    /** Everything on the detail page except the hero: hidden while the card opens, then faded in over the second half. */
    fun trailingAlpha(): Float {
        if (id == null) return 1f
        return ((t() - 0.45f) / 0.55f).coerceIn(0f, 1f)
    }

    /**
     * Applied to the hero. The hero is laid out at its final size; while the card opens it is moved to where the card was
     * and clipped to the card's size, then both grow to the hero. The card's surface colour covers the forest at first and
     * clears in the first 40 %, so the white card turns into the forest hero instead of switching colour in one frame.
     */
    fun Modifier.openingHero(surface: Color): Modifier = this
        .onGloballyPositioned { target = it.boundsInRoot() }
        .graphicsLayer {
            val from = origin
            val to = target
            val t = t()
            if (id == null || from == null || to == null || t >= 1f) {
                translationX = 0f; translationY = 0f; clip = false; shape = androidx.compose.ui.graphics.RectangleShape
                return@graphicsLayer
            }
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = (from.left - to.left) * (1f - t)
            translationY = (from.top - to.top) * (1f - t)
            val width = from.width + (to.width - from.width) * t
            val height = from.height + (to.height - from.height) * t
            val radius = (20.dp.toPx() + (24.dp.toPx() - 20.dp.toPx()) * t)
            shape = ClipBox(width, height, radius)
            clip = true
        }
        .drawWithContent {
            drawContent()
            val cover = if (id == null) 0f else 1f - (t() / 0.4f).coerceIn(0f, 1f)
            if (cover > 0f) drawRect(surface, alpha = cover)
        }
}

/** A rounded box anchored at the top left of the layer, smaller than the layer while the card grows. */
private class ClipBox(private val width: Float, private val height: Float, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, width.coerceAtMost(size.width), height.coerceAtMost(size.height), CornerRadius(radius)))
}

@Composable
internal fun rememberGreenhouseOpening(): GreenhouseOpening = androidx.compose.runtime.remember { GreenhouseOpening() }
