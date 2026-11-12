package com.inofarm.jogja.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.roundToInt

/** Colour roles for tiles and chips. Meaning is always written out; the colour only marks it. */
internal enum class FarmTone { Neutral, Good, Info, Amber, Error, Forest, Brand }

private val ForestTile = Color(0xFF203B30)

@Composable internal fun farmToneColors(tone: FarmTone): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return when (tone) {
        FarmTone.Neutral -> c.secondaryContainer to (if (dark) c.primary else FarmColors.Brand)
        FarmTone.Good -> (if (dark) c.primaryContainer else FarmColors.Soft) to (if (dark) c.primary else FarmColors.Brand)
        FarmTone.Info -> c.tertiaryContainer to c.onTertiaryContainer
        FarmTone.Amber -> (if (dark) Color(0xFF493519) else FarmColors.WarningSurface) to (if (dark) ForestScheme.secondary else FarmColors.Warning)
        FarmTone.Error -> c.errorContainer to c.onErrorContainer
        FarmTone.Forest -> ForestTile to FarmColors.Sage
        FarmTone.Brand -> c.primary to c.onPrimary
    }
}

/** Press scales the element with a spring; hover lifts it 2 dp. Both stop on their own when the pointer leaves. */
@Composable internal fun Modifier.farmPressable(
    interaction: MutableInteractionSource,
    scale: Float = FarmMotion.PressScaleCard,
    shape: Shape = FarmShape.Card
): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val factor by animateFloatAsState(if (pressed) scale else 1f, FarmMotion.pressSpring(), label = "Tekan")
    val lift by animateDpAsState(if (hovered) 2.dp else 0.dp, tween(FarmMotion.State), label = "Angkat")
    return this.hoverable(interaction).shadow(lift, shape, clip = false)
        .graphicsLayer { scaleX = factor; scaleY = factor }
}

private val playedEntrances = HashSet<Any>()

/** Fade and rise 12 dp once per process; items past the sixth appear at once. */
@Composable internal fun Modifier.farmStaggerEntrance(index: Int, key: Any = index, step: Int = FarmMotion.Stagger): Modifier {
    val immediate = index > 5 || key in playedEntrances
    val progress = remember { Animatable(if (immediate) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * step.toLong())
            playedEntrances.add(key)
            progress.animateTo(1f, tween(FarmMotion.Entrance))
        } else playedEntrances.add(key)
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = 12.dp.toPx() * (1f - progress.value)
    }
}

/** Stem with three leaf pairs in sage; decorative, so it carries no semantics. [shift] is the parallax offset in px. */
@Composable internal fun FarmLeafMotif(modifier: Modifier = Modifier, shift: Float = 0f, alpha: Float = 0.28f) {
    val color = FarmColors.Sage.copy(alpha = alpha)
    Canvas(modifier) {
        val u = size.minDimension / 100f
        val stroke = Stroke(1.8f * u)
        val base = Offset(size.width - 18 * u, size.height + shift)
        val stem = Path().apply {
            moveTo(base.x, base.y)
            cubicTo(base.x - 6 * u, base.y - 40 * u, base.x + 6 * u, base.y - 80 * u, base.x - 2 * u, base.y - 120 * u)
        }
        drawPath(stem, color, style = stroke)
        for ((i, dy) in listOf(30f, 62f, 94f).withIndex()) {
            val side = if (i % 2 == 0) -1f else 1f
            val sx = base.x + side * 1 * u
            val sy = base.y - dy * u
            val leaf = Path().apply {
                moveTo(sx, sy)
                quadraticTo(sx + side * 24 * u, sy - 6 * u, sx + side * 34 * u, sy - 26 * u)
                quadraticTo(sx + side * 8 * u, sy - 24 * u, sx, sy)
            }
            drawPath(leaf, color, style = stroke)
        }
    }
}

@Composable internal fun FarmIconTile(
    icon: FarmIcon, modifier: Modifier = Modifier, tone: FarmTone = FarmTone.Neutral,
    size: androidx.compose.ui.unit.Dp = 44.dp, description: String? = null, shape: Shape = FarmShape.Tile
) {
    val (container, content) = farmToneColors(tone)
    Box(modifier.size(size).background(container, shape), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides content) { FarmControlIcon(icon, description) }
    }
}

@Composable internal fun FarmStatusChip(text: String, modifier: Modifier = Modifier, tone: FarmTone = FarmTone.Neutral) {
    val (container, content) = farmToneColors(tone)
    Text(text, modifier.background(container, FarmShape.Chip).padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = content)
}

/** Forest surface with the leaf motif in a corner. Content is drawn with the forest colour scheme in both themes. */
@Composable internal fun FarmHeroCard(
    modifier: Modifier = Modifier, motifShift: Float = 0f, content: @Composable ColumnScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = ForestScheme, typography = MaterialTheme.typography) {
        Surface(modifier.fillMaxWidth(), shape = FarmShape.Hero,
            color = if (dark) ForestTile else ForestScheme.background, contentColor = ForestScheme.onBackground,
            border = if (dark) BorderStroke(1.dp, ForestScheme.outlineVariant) else null) {
            Box {
                FarmLeafMotif(Modifier.matchParentSize(), shift = motifShift)
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(FarmSpace.Medium), content = content)
            }
        }
    }
}

@Composable internal fun FarmQuickAction(
    icon: FarmIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    badge: Int? = null, enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val described = if (badge != null && badge > 0) "$label, $badge" else label
    Column(
        modifier.farmPressable(interaction, FarmMotion.PressScaleCard, FarmShape.Card)
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = described }
            .heightIn(min = FarmSpace.Touch).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            FarmIconTile(icon, tone = FarmTone.Neutral, size = 56.dp, shape = RoundedCornerShapeLarge)
            if (badge != null && badge > 0) {
                Text(badgeText(badge), Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp)
                    .background(FarmColors.Warning, FarmShape.Chip).padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
            overflow = TextOverflow.Ellipsis, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val RoundedCornerShapeLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

/** Scrollable tab row; the selected pill slides between tabs. Tags are "$tagPrefix$id". */
@Composable internal fun FarmSegmentedTabs(
    tabs: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, tagPrefix: String = "hub-section-"
) {
    val colors = MaterialTheme.colorScheme
    val positions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    val target = positions[selected]
    val x by animateIntAsState(target?.first ?: 0, FarmMotion.pressSpring(), label = "Pill x")
    val w by animateIntAsState(target?.second ?: 0, FarmMotion.pressSpring(), label = "Pill lebar")
    Box(modifier.fillMaxWidth().background(colors.secondaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
        .horizontalScroll(rememberScrollState()).padding(4.dp)) {
        if (target != null) Box(Modifier.offset { IntOffset(x, 0) }.width(with(LocalDensity.current) { w.toDp() })
            .height(FarmSpace.Touch - 4.dp).background(colors.primary, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)))
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tabs.forEach { (id, label) ->
                val on = id == selected
                val text by animateColorAsState(if (on) colors.onPrimary else colors.onSecondaryContainer, tween(FarmMotion.State), label = "Teks tab")
                Box(Modifier.heightIn(min = FarmSpace.Touch - 4.dp).onGloballyPositioned {
                    positions[id] = it.positionInParent().x.roundToInt() to it.size.width
                }.selectable(on, onClick = { onSelect(id) }, role = Role.Tab).padding(horizontal = 14.dp)
                    .testTag("$tagPrefix$id"), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = text, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Same signature as the dropdown `Choice`. [tag] marks a 1 dp inert node so tests that tap the container first
 * do nothing; each chip is tagged "$tag-option-$id".
 */
@Composable internal fun FarmChoiceChips(
    label: String, selected: String?, options: List<JSONObject>, tag: String,
    loading: Boolean = false, scroll: Boolean = false, onSelect: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Box(Modifier.size(1.dp).testTag(tag))
        val chips: @Composable () -> Unit = {
            options.forEach { option ->
                val id = option.getString("id")
                val on = id == selected
                val name = option.optString("choice_label").ifBlank { option.getString("name") }
                Surface(
                    Modifier.heightIn(min = FarmSpace.Touch).testTag("$tag-option-$id")
                        .selectable(on, enabled = !loading, role = Role.RadioButton, onClick = { onSelect(id) }),
                    shape = FarmShape.Control,
                    color = if (on) colors.primary else colors.surface,
                    contentColor = if (on) colors.onPrimary else colors.onSurface,
                    border = if (on) null else BorderStroke(1.dp, colors.outline)
                ) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
        if (scroll) Row(Modifier.selectableGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
        else FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

/**
 * Manager bar: four tabs around a raised Catat button. Tab tags are finance-tab-0..3 and the button is
 * finance-record-fab; the button sits outside the selectable group.
 */
@Composable internal fun FarmDockNavigation(
    selected: Int, items: List<Pair<String, Int>>, onSelect: (Int) -> Unit,
    onRecord: () -> Unit, recordEnabled: Boolean, modifier: Modifier = Modifier
) {
    require(items.size == 4)
    val bar = Color(0xFF263F34)
    val pill = Color(0xFF385442)
    val positions = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    val target = positions[selected]
    val x by animateIntAsState(target?.first ?: 0, FarmMotion.pressSpring(), label = "Pill dock")
    val density = LocalDensity.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(top = 26.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = FarmShape.Dock, color = bar, contentColor = ForestScheme.onSurface, shadowElevation = FarmDepth.Navigation) {
            Box(Modifier.heightIn(min = 72.dp)) {
                if (target != null) Box(Modifier.offset { IntOffset(x, 0) }.width(with(density) { target.second.toDp() })
                    .height(72.dp).padding(vertical = 8.dp, horizontal = 4.dp).background(pill, FarmShape.Control))
                Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (slot in 0..4) {
                        if (slot == 2) { Spacer(Modifier.weight(1f)); continue }
                        val index = if (slot < 2) slot else slot - 1
                        val on = index == selected
                        val color by animateColorAsState(if (on) Color.White else FarmColors.Sage.copy(alpha = 0.78f), tween(FarmMotion.State), label = "Ikon dock")
                        Column(Modifier.weight(1f).heightIn(min = 72.dp).onGloballyPositioned {
                            positions[index] = it.positionInParent().x.roundToInt() to it.size.width
                        }.selectable(on, onClick = { onSelect(index) }, role = Role.Tab).testTag("finance-tab-$index").padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            val scale by animateFloatAsState(if (on) 1.08f else 1f, FarmMotion.pressSpring(), label = "Skala ikon")
                            CompositionLocalProvider(LocalContentColor provides color) {
                                Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { FarmNavigationIcon(items[index].second) }
                            }
                            CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.3f))) {
                                Text(items[index].first, style = MaterialTheme.typography.labelSmall, color = color,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center,
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        val interaction = remember { MutableInteractionSource() }
        Column(Modifier.align(Alignment.TopCenter).offset(y = (-26).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(66.dp).background(bar, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(56.dp).farmPressable(interaction, FarmMotion.PressScaleButton, androidx.compose.foundation.shape.CircleShape)
                    .background(if (recordEnabled) FarmColors.Sage else FarmColors.Sage.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                    .clickable(interaction, null, enabled = recordEnabled, role = Role.Button, onClick = onRecord)
                    .semantics { contentDescription = "Catat" }.testTag("finance-record-fab"), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides FarmColors.Ink) { FarmControlIcon(FarmIcon.Add, null) }
                }
            }
            CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.3f))) {
                Text("Catat", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    color = FarmColors.Sage.copy(alpha = if (recordEnabled) 1f else 0.5f))
            }
        }
    }
    }
}
