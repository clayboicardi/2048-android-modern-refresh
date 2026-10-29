package com.clayboicardi.game2048.game.overlay

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import com.clayboicardi.game2048.game.board.boardGeometryPx
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameTextStyles
import com.clayboicardi.game2048.ui.theme.LocalTileSkin
import kotlin.math.roundToInt

/**
 * The scrim of the win and game-over cards (D-008, fixes B19; DESIGN_SYSTEM.md §2.10, §4.4, §4.7
 * step 8): `surface` at [CardVisuals.scrim] (read in draw), clipped to the board's rounded shape,
 * the radius tile + gap of the [n]×[n] geometry at the board's side, placed on [board] (the board's
 * bounds in its parent). While [blocking] (a card is shown and not exiting) it takes every pointer
 * event on the board, so swipes and taps there do nothing (F03-AC16, F04-AC7); otherwise input
 * passes to the board (MOTION_SPEC.md §3.12, I6). Tagged [SCRIM_TAG]; it has no other semantics.
 */
@Composable
internal fun BoardScrim(
    visuals: CardVisuals,
    n: () -> Int,
    board: State<IntRect>,
    blocking: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.surface
    val skin = LocalTileSkin.current
    val density = LocalDensity.current.density
    Spacer(
        modifier
            .onBoard(board)
            .testTag(SCRIM_TAG)
            .then(if (blocking) Modifier.pointerInput(Unit) { consumeAll() } else Modifier)
            .drawWithCache {
                val g = boardGeometryPx(n(), size.width.roundToInt(), skin, density)
                val radius = CornerRadius(g.boardRadiusPx)
                onDrawBehind {
                    val alpha = visuals.scrim()
                    if (alpha > 0f) drawRoundRect(color, cornerRadius = radius, alpha = alpha)
                }
            },
    )
}

/** Takes every pointer event of each gesture on the scrim. */
private suspend fun PointerInputScope.consumeAll() {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Measures the node at [board]'s size and places it on [board]'s bounds inside a parent it fills;
 * the bounds are read in layout only, so a resize never recomposes.
 */
internal fun Modifier.onBoard(board: State<IntRect>): Modifier = layout { measurable, constraints ->
    val rect = board.value
    val placeable = measurable.measure(Constraints.fixed(rect.width, rect.height))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(rect.left, rect.top) }
}

/** The layout a card uses on its board (D-200). */
private enum class CardFit {
    REGULAR,
    COMPACT,
    SCROLL,
}

/**
 * The container of the win and game-over cards (D-200; DESIGN_SYSTEM.md §6.3, §6.3a, §6.4, §6.4a):
 * centred on [board], `min(clamp(board − 32 dp, 264 dp, 400 dp), board − 16 dp)` wide, with the
 * height budget board − 16 dp, so it never extends past the board. It measures a copy of the
 * [regular] layout (with `measuring` true: no semantics, interactivity or effects) and composes the
 * real one when it fits the budget, otherwise the [compact] layout, which scrolls inside itself
 * when even it is taller. The choice is measured once per [key] (the shown card) and board size.
 * The card's alpha and scale ([CardVisuals.alpha], [CardVisuals.scale], about its centre) are its
 * placement layer, read only in that layer (D-228: no `graphicsLayer` semantics node).
 */
@Composable
internal fun OverlayCard(
    key: Any,
    board: State<IntRect>,
    visuals: CardVisuals,
    regular: @Composable (measuring: Boolean) -> Unit,
    compact: @Composable (measuring: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fit = remember(key) { FitCache() }
    val layer: GraphicsLayerScope.() -> Unit =
        remember(visuals) {
            {
                alpha = visuals.alpha()
                val s = visuals.scale()
                scaleX = s
                scaleY = s
            }
        }
    SubcomposeLayout(modifier) { constraints ->
        val rect = board.value
        val side = rect.width
        val inset = GameDimens.OverlayCardInset.roundToPx()
        val budget = (side - inset).coerceAtLeast(0)
        val width =
            minOf(
                (side - GameDimens.Space32.roundToPx()).coerceIn(
                    GameDimens.OverlayCardMinWidth.roundToPx(),
                    GameDimens.OverlayCardMaxWidth.roundToPx(),
                ),
                budget,
            )
        val choice =
            fit.choice(width, budget) {
                val probe = Constraints(minWidth = width, maxWidth = width)
                val regularHeight =
                    subcompose(PROBE_REGULAR) { if (fit.probing.value) Probe { regular(true) } }
                        .maxOf { it.measure(probe).height }
                val compactHeight =
                    if (regularHeight <= budget) {
                        0
                    } else {
                        subcompose(PROBE_COMPACT) { if (fit.probing.value) Probe { compact(true) } }
                            .maxOf { it.measure(probe).height }
                    }
                when {
                    regularHeight <= budget -> CardFit.REGULAR
                    compactHeight <= budget -> CardFit.COMPACT
                    else -> CardFit.SCROLL
                }
            }
        val cardConstraints = Constraints(minWidth = width, maxWidth = width, maxHeight = budget)
        val placeables =
            subcompose(choice) {
                    when (choice) {
                        CardFit.REGULAR -> regular(false)
                        CardFit.COMPACT -> compact(false)
                        CardFit.SCROLL ->
                            Box(Modifier.verticalScroll(rememberScrollState())) { compact(false) }
                    }
                }
                .map { it.measure(cardConstraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            for (p in placeables) {
                val x = rect.left + (side - p.width) / 2
                val y = rect.top + (side - p.height) / 2
                p.placeWithLayer(x, y, layerBlock = layer)
            }
        }
    }
}

/**
 * The measured copy of a layout: no semantics of its own or of its children. Its slot composes it
 * only while the [FitCache] is probing, and reads that flag in the slot's own lambda, which every
 * `subcompose` call runs again: a `Probe` that read the flag itself would be skipped with unchanged
 * arguments when the board rect changes right after a first probe, and leave its slot empty.
 */
@Composable
private fun Probe(content: @Composable () -> Unit) {
    Box(Modifier.clearAndSetSemantics {}) { content() }
}

/**
 * The fit chosen for one card and board size; measured again when the size changes. The probe slots
 * compose their measuring copies only while [probing], so once a pass has measured them they
 * recompose to nothing and leave no nodes in the semantics tree.
 */
private class FitCache {
    private var width = -1
    private var budget = -1
    private var fit = CardFit.REGULAR

    /** True only inside the pass that measures the copies. */
    val probing = mutableStateOf(false)

    inline fun choice(width: Int, budget: Int, measure: () -> CardFit): CardFit {
        if (width != this.width || budget != this.budget) {
            probing.value = true
            fit = measure()
            probing.value = false
            this.width = width
            this.budget = budget
        }
        return fit
    }
}

/**
 * A card's title (DESIGN_SYSTEM.md §3.5, §6.3, §6.4): `GameTextStyles.cardTitle`
 * (`displaySmallEmphasized`, Regular) or `cardTitleCompact` (`headlineSmallEmphasized`, Compact),
 * the 1.5-only emphasized styles read through their `ui/theme` wrappers (D-026), centred in
 * `onSurface`, [inset] clear of the win card's Share button on both sides. Unless [measuring], it
 * is the one assertive live region of its card (D-081).
 */
@Composable
internal fun CardTitle(text: String, compact: Boolean, inset: Dp, measuring: Boolean) {
    val style = if (compact) GameTextStyles.cardTitleCompact else GameTextStyles.cardTitle
    Text(
        text = text,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = inset)
                .then(
                    if (measuring) Modifier
                    else Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                ),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        style = style,
    )
}

/**
 * A card's root semantics (D-081; DESIGN_SYSTEM.md §6.3, §6.4): `paneTitle` = [title] and the test
 * [tag]; none for a [measuring] copy.
 */
internal fun Modifier.cardSemantics(title: String, tag: String, measuring: Boolean): Modifier =
    if (measuring) this else testTag(tag).semantics { paneTitle = title }

/**
 * A card button's fade (MOTION_SPEC.md §3.11, §3.13): [alpha] read in the node's placement layer,
 * so the fade recomposes nothing and adds no semantics node (D-228).
 */
internal fun Modifier.layerAlpha(alpha: () -> Float): Modifier {
    val block: GraphicsLayerScope.() -> Unit = { this.alpha = alpha() }
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = block)
        }
    }
}

/**
 * Moves focus to a card's primary button ([focus]: Keep going, New game) on its interactive frame
 * (D-081): the flip of an entry, or the first frame of a `Restored` card. None for a [measuring]
 * copy.
 */
@Composable
internal fun CardFocusEffect(interactive: Boolean, measuring: Boolean, focus: FocusRequester) {
    if (measuring) return
    LaunchedEffect(interactive) { if (interactive) focus.requestFocus() }
}

/** What the card buttons do (D-199, D-200): the controller's actions and the share call site. */
@Immutable
internal data class CardActions(
    val onKeepGoing: () -> Unit,
    val onNewGame: () -> Unit,
    val onUndo: () -> Unit,
    val onShare: () -> Unit,
)

/** The probe slots of [OverlayCard]'s measurement. */
private const val PROBE_REGULAR = "probe:regular"
private const val PROBE_COMPACT = "probe:compact"
