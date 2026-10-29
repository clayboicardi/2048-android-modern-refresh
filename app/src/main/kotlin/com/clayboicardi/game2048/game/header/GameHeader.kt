package com.clayboicardi.game2048.game.header

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.game.GameUiState
import com.clayboicardi.game2048.game.HeaderUi
import com.clayboicardi.game2048.game.LocalChromeProbe
import com.clayboicardi.game2048.game.board.BoardEventKind
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.ContentEffects
import com.clayboicardi.game2048.game.board.HeaderStyle
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.LocalMoveTimeline
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.MoveTimeline
import com.clayboicardi.game2048.game.motion.ProbeBestChip
import com.clayboicardi.game2048.game.motion.ProbeHeader
import com.clayboicardi.game2048.ui.components.ScoreChip
import com.clayboicardi.game2048.ui.components.ScoreChipLabel
import com.clayboicardi.game2048.ui.components.SizeChip
import com.clayboicardi.game2048.ui.components.rememberScoreAdvance14
import com.clayboicardi.game2048.ui.components.rememberScoreValueSize
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameFonts
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.LocalTileSkin
import com.clayboicardi.game2048.ui.theme.MotionLevel
import com.clayboicardi.game2048.ui.theme.MotionPlan
import com.clayboicardi.game2048.ui.theme.NewBestMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Test tags of the header (relied on by the board, input and motion tests). */
const val HEADER_TAG = "game:header"
const val SCORES_TAG = "game:scores"
const val SCORE_TAG = "game:score"
const val BEST_TAG = "game:best"
const val SIZE_CHIP_TAG = "game:sizeChip"
const val BEST_STAR_TAG = "game:bestStar"
const val TITLE_TAG = "game:title"

/** Where a [GameHeader] sits (DESIGN_SYSTEM.md §5.4, §6.1 header layouts; D-188, D-249). */
enum class HeaderForm {
    /** STACKED with `HeaderStyle.FULL`: the title row, then the size chip's row with More. */
    FULL,

    /** STACKED and SCROLL with `HeaderStyle.COMPACT`: no title; the size chip without its icon. */
    COMPACT,

    /** The THREE_COLUMN stats rail: the title, Score above Best, the size chip. */
    RAIL,

    /** The SIDE_PANEL's top: the title row with More, the chips, the size chip. */
    PANEL,
}

/** The STACKED and SCROLL header form of [style]. */
fun HeaderStyle.form(): HeaderForm =
    when (this) {
        HeaderStyle.FULL -> HeaderForm.FULL
        HeaderStyle.COMPACT -> HeaderForm.COMPACT
    }

/**
 * The Game header (DESIGN_SYSTEM.md §6.1 header layouts, §7.2, §7.3; D-084, D-188, D-249): the
 * "2048" title (a heading in `skin.titleColor`, auto-sized from `TitleMin` to [titleMax]; not in
 * the COMPACT [form], and in the rail only while [showTitle]), the Score and Best chips, the
 * board-size chip ([onSizeChipClick]; without its grid icon in COMPACT) and, given as [more], the
 * More button at the end of the size chip's row (FULL, COMPACT) or of the title row (PANEL). While
 * [ui] is not ready (Loading, DESIGN_SYSTEM.md §6.1) both chips show "–" and the size chip shows
 * [loadingSize].
 *
 * The chips take the `ChipLayout` that `scoreFit` gives for the width this header is measured at
 * and the live digit count: INLINE beside the title or the size chip and More, OWN_ROW side by side
 * on a row of their own, or STACKED on full-width rows, a value too long for its row wrapping
 * between digits. Their value size is `ScoreValue`'s one fitted size. More is the last item of the
 * header's traversal group (D-080, D-249).
 *
 * Header motion (ARCHITECTURE.md §4.11 item 4; MOTION_SPEC.md §3.4–§3.6, §4.2): [header] and
 * [board] are its arrangement scope's presented values; one `GameHeader` per arrangement slot keeps
 * its composition and running moments when [form] changes.
 * - The displayed score follows `awaitOffset(deltaMoveId, scoreAtMs)` after a move under Full and
 *   the true score at once otherwise; BEST shows `max(stored best, displayed score)`.
 * - "+N" floats: [ScoreFloats], composed inside `key(header.epoch, header.event)`.
 * - The once-per-game new-best moment runs in this header's own scope from
 *   `awaitOffset(deltaMoveId, newBestAtMs)`: label, container crossfade, [NewBestBadge] and the
 *   sparkles of [effects] (Full only, D-222). A new epoch cancels it unless it is an undo that
 *   advanced the epoch by exactly one. Until its label flip the chip keeps the BEST style;
 *   otherwise the NEW BEST style shows iff `newBestAnnounced`.
 * - [onHeaderPresented] reports `deltaMoveId` once the moments were launched and one frame passed.
 *
 * Under `LocalChromeProbe` (the never-placed measurement copy of `GameLayout`, DESIGN_SYSTEM.md
 * §5.3) it runs no effect, starts no moment or float, registers no probe reader and never calls
 * [onHeaderPresented]: it only lays out the values it is given.
 */
@Composable
fun GameHeader(
    header: State<HeaderUi>,
    board: State<BoardUi>,
    ui: State<GameUiState>,
    loadingSize: Int,
    form: HeaderForm,
    titleMax: TextUnit,
    effects: ContentEffects,
    onHeaderPresented: (deltaMoveId: Long) -> Unit,
    onSizeChipClick: () -> Unit,
    modifier: Modifier = Modifier,
    more: (@Composable () -> Unit)? = null,
    showTitle: Boolean = true,
) {
    val chromeProbe = LocalChromeProbe.current
    val presented = header.value
    val state = ui.value
    val level = LocalEffectiveMotion.current
    val plan =
        remember(level, chromeProbe) {
            GameMotion.plan(if (chromeProbe) MotionLevel.OFF else level)
        }
    val timeline = LocalMoveTimeline.current
    val scope = rememberCoroutineScope()
    val motion = remember { HeaderMotion(presented.score, presented.best) }

    if (!chromeProbe) {
        LaunchedEffect(presented.deltaMoveId) {
            motion.onHeader(presented, plan, timeline, scope, effects)
            withFrameNanos {}
            if (presented.lastDelta != 0L) onHeaderPresented(presented.deltaMoveId)
        }
    }

    val view = motion.view(presented, plan)
    if (!chromeProbe) ProbeRegistration(motion, view, effects)

    val loading = !state.ready
    val digits = if (loading) 1 else maxOf(presented.score, presented.best).toString().length
    val titleLead = rememberTitleLead()
    val hasTitle = form != HeaderForm.COMPACT && (form != HeaderForm.RAIL || showTitle)
    val railTitleMax = if (form == HeaderForm.RAIL) GameDimens.RailTitleMax else titleMax
    HeaderLayout(
        form = form,
        digits = digits,
        advance14 = rememberScoreAdvance14(),
        titleLead = titleLead,
        title = if (hasTitle) ({ GameTitle(railTitleMax) }) else null,
        sizeChip = {
            SizeChip(
                n = if (state.ready) state.size.n else loadingSize,
                onClick = onSizeChipClick,
                modifier = Modifier.testTag(SIZE_CHIP_TAG),
                gridIcon = form != HeaderForm.COMPACT,
                enabled = !chromeProbe,
            )
        },
        more =
            more?.let { button ->
                {
                    // More is read last in the header group (D-080, D-249), also where it sits
                    // in the title row (SIDE_PANEL): a group of its own, ordered by its index.
                    Box(
                        Modifier.semantics {
                            isTraversalGroup = true
                            traversalIndex = MORE_TRAVERSAL_INDEX
                        },
                    ) {
                        button()
                    }
                }
            },
        chips = { fit -> ScoreRow(presented, board, view, motion, plan, fit, digits, loading) },
        modifier = modifier.testTag(HEADER_TAG).semantics { isTraversalGroup = true },
    )
}

/**
 * Places the header's parts for [form] (DESIGN_SYSTEM.md §6.1): it measures the size chip and More
 * first, so the COMPACT `lead` (the size chip, 8 dp and More, D-249) is exact; the FULL `lead` is
 * [titleLead], the title's "2048" at 32 sp; then `scoreFit` at the header's width decides the
 * chips' layout, and [chips] is composed and measured for that fit. Rows follow each other with no
 * extra gap: the title row is at least `HeaderRow1`, the size chip's row `HeaderRow2`, the COMPACT
 * INLINE row `HeaderCompact`.
 */
@Composable
private fun HeaderLayout(
    form: HeaderForm,
    digits: Int,
    advance14: Float,
    titleLead: Float,
    title: (@Composable () -> Unit)?,
    sizeChip: @Composable () -> Unit,
    more: (@Composable () -> Unit)?,
    chips: @Composable (ScoreFit) -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val gap = GameDimens.Space8.roundToPx()
        val row =
            SizeChipRow(
                size = subcompose(HeaderSlot.SIZE_CHIP, sizeChip).map { it.measure(loose) },
                more = more?.let { m -> subcompose(HeaderSlot.MORE, m).map { it.measure(loose) } },
                gap = gap,
            )
        val lead =
            when (form) {
                HeaderForm.FULL -> titleLead
                HeaderForm.COMPACT -> row.lead.toDp().value
                HeaderForm.RAIL,
                HeaderForm.PANEL -> null
            }
        val fit =
            scoreFit(digits, width.toDp().value, lead, advance14, ownRow = form != HeaderForm.RAIL)
        val inline = fit.layout == ChipLayout.INLINE
        val chipsMax =
            when {
                !inline -> width
                form == HeaderForm.COMPACT -> width - row.lead - gap
                else -> width - titleLead.dp.roundToPx() - gap
            }.coerceAtLeast(0)
        val chipsConstraints =
            if (inline) Constraints(maxWidth = chipsMax) else Constraints.fixedWidth(chipsMax)
        val chipParts =
            subcompose(HeaderSlot.CHIPS) { chips(fit) }.map { it.measure(chipsConstraints) }
        val chipsW = chipParts.maxOfOrNull { it.width } ?: 0
        val titleMaxW = if (inline && form == HeaderForm.FULL) width - chipsW - gap else width
        val titleConstraints = Constraints(maxWidth = titleMaxW.coerceAtLeast(0))
        val titleParts = title?.let { t ->
            subcompose(HeaderSlot.TITLE, t).map { it.measure(titleConstraints) }
        }
        val parts = HeaderParts(titleParts.orEmpty(), chipParts, row, inline)
        val rows =
            HeaderRows(
                form,
                parts,
                row1Min = GameDimens.HeaderRow1.roundToPx(),
                row2 = maxOf(GameDimens.HeaderRow2.roundToPx(), row.rowHeight(form)),
            )
        layout(width, rows.height) { placeHeader(form, width, parts, rows) }
    }
}

/**
 * The measured size chip and More of one header (D-249): their widths and heights, and the COMPACT
 * `lead`, the size chip, 8 dp and More.
 */
private class SizeChipRow(val size: List<Placeable>, val more: List<Placeable>?, gap: Int) {
    val sizeH = size.maxOfOrNull { it.height } ?: 0
    val moreW = more?.maxOfOrNull { it.width } ?: 0
    val moreH = more?.maxOfOrNull { it.height } ?: 0

    /** More and the gap before it, or 0 without More. */
    val moreSpan = if (more == null) 0 else gap + moreW

    val lead = (size.maxOfOrNull { it.width } ?: 0) + moreSpan

    /** The height the size chip's row needs: More counts except in the PANEL title row. */
    fun rowHeight(form: HeaderForm): Int =
        if (form == HeaderForm.PANEL) sizeH else maxOf(sizeH, moreH)
}

/** One header's measured parts. */
private class HeaderParts(
    val title: List<Placeable>,
    val chips: List<Placeable>,
    val row: SizeChipRow,
    val inline: Boolean,
) {
    val titleH = title.maxOfOrNull { it.height } ?: 0
    val chipsW = chips.maxOfOrNull { it.width } ?: 0
    val chipsH = chips.maxOfOrNull { it.height } ?: 0
}

/** Places [parts] in [rows] for [form] (DESIGN_SYSTEM.md §6.1), mirrored in RTL. */
private fun Placeable.PlacementScope.placeHeader(
    form: HeaderForm,
    width: Int,
    parts: HeaderParts,
    rows: HeaderRows,
) {
    // Each part at x, centred vertically in the row of rowHeight at rowTop.
    fun centred(placeables: List<Placeable>, x: Int, rowTop: Int, rowHeight: Int) {
        for (p in placeables) p.placeRelative(x, rowTop + (rowHeight - p.height) / 2)
    }
    // The chips' own rows, at the start of the row at top.
    fun below(placeables: List<Placeable>, top: Int) {
        for (p in placeables) p.placeRelative(0, top)
    }
    val row = parts.row
    val more = row.more.orEmpty()
    val moreX = width - row.moreW
    when (form) {
        HeaderForm.FULL -> {
            centred(parts.title, 0, 0, rows.first)
            if (parts.inline) {
                centred(parts.chips, width - parts.chipsW, 0, rows.first)
            } else {
                below(parts.chips, rows.first)
            }
            centred(row.size, 0, rows.sizeTop, rows.row2)
            centred(more, moreX, rows.sizeTop, rows.row2)
        }
        HeaderForm.COMPACT -> {
            centred(row.size, 0, 0, rows.first)
            centred(more, moreX, 0, rows.first)
            if (parts.inline) {
                centred(parts.chips, width - parts.chipsW - row.moreSpan, 0, rows.first)
            } else {
                below(parts.chips, rows.first)
            }
        }
        HeaderForm.RAIL -> {
            centred(parts.title, 0, 0, rows.first)
            below(parts.chips, rows.first)
            centred(row.size, 0, rows.sizeTop, rows.row2)
        }
        HeaderForm.PANEL -> {
            centred(parts.title, 0, 0, rows.first)
            centred(more, moreX, 0, rows.first)
            below(parts.chips, rows.first)
            centred(row.size, 0, rows.sizeTop, rows.row2)
        }
    }
}

/**
 * The row tops and heights of one header (DESIGN_SYSTEM.md §6.1): [first] is the title row (the
 * COMPACT size chip's row or INLINE row), [sizeTop] the top of the size chip's row where it comes
 * last, [height] the whole header. [row1Min] is `HeaderRow1`; [row2] the size chip's row.
 */
private class HeaderRows(form: HeaderForm, parts: HeaderParts, row1Min: Int, val row2: Int) {
    val first: Int
    val sizeTop: Int
    val height: Int

    init {
        val inline = parts.inline
        val chipsH = parts.chipsH
        when (form) {
            HeaderForm.FULL -> {
                first = maxOf(row1Min, parts.titleH, if (inline) chipsH else 0)
                sizeTop = first + if (inline) 0 else chipsH
                height = sizeTop + row2
            }
            HeaderForm.COMPACT -> {
                first = if (inline) maxOf(row1Min, chipsH, row2) else row2
                sizeTop = 0
                height = first + if (inline) 0 else chipsH
            }
            HeaderForm.RAIL -> {
                first = parts.titleH
                sizeTop = first + chipsH
                height = sizeTop + row2
            }
            HeaderForm.PANEL -> {
                first = maxOf(row1Min, parts.titleH, parts.row.moreH)
                sizeTop = first + chipsH
                height = sizeTop + row2
            }
        }
    }
}

/** The subcomposition slots of [HeaderLayout]. */
private enum class HeaderSlot {
    TITLE,
    SIZE_CHIP,
    MORE,
    CHIPS,
}

/**
 * The width in dp of the title "2048" at its `autoSize` minimum, `TitleMin` (DESIGN_SYSTEM.md
 * §7.2).
 */
@Composable
private fun rememberTitleLead(): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val skin = LocalTileSkin.current
    return remember(density, measurer, skin) {
        val style = GameFonts.title(skin).copy(fontSize = GameDimens.TitleMin)
        val width =
            measurer
                .measure(TITLE_VALUE.toString(), style, softWrap = false, maxLines = 1)
                .size
                .width
        width / density.density
    }
}

/**
 * The title "2048" (DESIGN_SYSTEM.md §3.5, §6.1): a heading in the game numerals,
 * `skin.titleColor`, one line. It is the game numeral [TITLE_VALUE] (`Long.toString()`, Latin
 * digits, D-076, D-103) rather than `app_name`, which reads "2048 Debug" in debug builds (D-110)
 * and has no glyphs in the numeral subset beyond the digits.
 */
@Composable
private fun GameTitle(max: TextUnit, modifier: Modifier = Modifier) {
    val skin = LocalTileSkin.current
    val color = skin.titleColor
    BasicText(
        text = TITLE_VALUE.toString(),
        modifier = modifier.testTag(TITLE_TAG).semantics { heading() },
        style = GameFonts.title(skin).copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(GameDimens.TitleMin, max, GameDimens.TitleStep),
    )
}

/**
 * Score, then Best, in [fit]'s layout (DESIGN_SYSTEM.md §6.1, §7.2): side by side 8 dp apart
 * (INLINE: content-sized from `ScoreChipMinWidth` to `chipSlot`; OWN_ROW: `chipSlot` each), or
 * stacked on full-width rows 8 dp apart. Tagged `game:scores`, the filmstrip's header region.
 */
@Composable
private fun ScoreRow(
    header: HeaderUi,
    board: State<BoardUi>,
    view: HeaderView,
    motion: HeaderMotion,
    plan: MotionPlan,
    fit: ScoreFit,
    digits: Int,
    loading: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val chromeProbe = LocalChromeProbe.current
    val fontSize = rememberScoreValueSize(digits, fit)
    val placeholder = if (loading) LOADING_VALUE else null
    val scoreLabel = stringResource(R.string.score_label)
    val bestLabel =
        stringResource(if (view.newBestStyle) R.string.new_best_label else R.string.best_label)
    val onSurface = colors.onSurface
    val onSurfaceVariant = colors.onSurfaceVariant
    // Moves and undos roll the digits; new game, size switch and restore swap them (§4.2).
    val roll = header.event == BoardEventKind.Moved || header.event == BoardEventKind.Undo
    val chipWidth =
        when (fit.layout) {
            ChipLayout.INLINE ->
                Modifier.widthIn(min = GameDimens.ScoreChipMinWidth, max = fit.chipSlot.dp)
            ChipLayout.OWN_ROW -> Modifier.width(fit.chipSlot.dp)
            ChipLayout.STACKED -> Modifier.fillMaxWidth()
        }
    val score: @Composable () -> Unit = {
        ScoreChip(
            spokenLabel = scoreLabel,
            spokenValue = placeholder ?: header.score.toString(),
            label = { ScoreChipLabel(scoreLabel, { onSurfaceVariant }) },
            modifier = chipWidth.testTag(SCORE_TAG),
            overlay = {
                if (!chromeProbe) {
                    key(header.epoch, header.event) { ScoreFloats(header, board, plan) }
                }
            },
        ) {
            ScoreOdometer(
                view.score,
                fontSize,
                { onSurface },
                plan,
                roll,
                placeholder = placeholder,
                digitsPerLine = fit.digitsPerLine,
            )
        }
    }
    val best: @Composable () -> Unit = {
        val container =
            bestColor(motion, view, colors.surfaceContainerHigh, colors.tertiaryContainer)
        val ink = bestColor(motion, view, onSurface, colors.onTertiaryContainer)
        val labelInk = bestColor(motion, view, onSurfaceVariant, colors.onTertiaryContainer)
        ScoreChip(
            spokenLabel = bestLabel,
            spokenValue = placeholder ?: header.best.toString(),
            label = { BestLabel(view, motion, plan, labelInk) },
            modifier = chipWidth.testTag(BEST_TAG).onPlaced { motion.bestCoordinates = it },
            container = container,
        ) {
            ScoreOdometer(
                view.best,
                fontSize,
                ink,
                plan,
                roll,
                placeholder = placeholder,
                digitsPerLine = fit.digitsPerLine,
            )
        }
    }
    if (fit.layout == ChipLayout.STACKED) {
        Column(
            modifier = Modifier.testTag(SCORES_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(GameDimens.Space8),
        ) {
            score()
            best()
        }
    } else {
        Row(
            modifier = Modifier.testTag(SCORES_TAG),
            horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            score()
            best()
        }
    }
}

/**
 * The BEST chip's label: `best_label` or `new_best_label`, with the star behind it while the moment
 * shows it. Under Full the moment's flip slides the label (MOTION_SPEC.md §3.6); every other change
 * is instant.
 */
@Composable
private fun BestLabel(
    view: HeaderView,
    motion: HeaderMotion,
    plan: MotionPlan,
    color: ColorProducer,
    modifier: Modifier = Modifier,
) {
    val slide = view.running && plan.newBest == NewBestMotion.MOMENT
    val shift = with(LocalDensity.current) { GameMotion.NewBest.LABEL_SHIFT_DP.dp.roundToPx() }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (view.starShown) {
            // Centred behind the label without taking part in its size, so the chip keeps its
            // height while the 20 dp star shows.
            Box(Modifier.matchParentSize().wrapContentSize(unbounded = true)) {
                NewBestBadge(
                    scale = { motion.starScale.value },
                    rotationDeg = { motion.starRotation.value },
                    alpha = { motion.starAlpha.value },
                    modifier = Modifier.testTag(BEST_STAR_TAG),
                )
            }
        }
        AnimatedContent(
            targetState = view.newBestStyle,
            transitionSpec = {
                if (slide) {
                    val fade = GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD)
                    val px = GameMotion.INT_OFFSET_THRESHOLD_PX
                    val move = GameMotion.slide(IntOffset(px, px))
                    // The width follows the longer label unclipped, so no frame cuts its letters.
                    ((fadeIn(fade) + slideInVertically(move) { shift }) togetherWith
                            (fadeOut(fade) + slideOutVertically(move) { -shift }))
                        .using(SizeTransform(clip = false))
                } else {
                    (EnterTransition.None togetherWith ExitTransition.None).using(null)
                }
            },
            contentAlignment = Alignment.Center,
            label = "bestLabel",
        ) { newBest ->
            val text = stringResource(if (newBest) R.string.new_best_label else R.string.best_label)
            ScoreChipLabel(text, color)
        }
    }
}

/**
 * The BEST chip's colour: during the moment's crossfade [from] → [to] by the animated fraction,
 * read at draw time; otherwise [to] in the NEW BEST style and [from] in the BEST style.
 */
private fun bestColor(
    motion: HeaderMotion,
    view: HeaderView,
    from: Color,
    to: Color,
): ColorProducer =
    when {
        view.running -> ColorProducer { lerp(from, to, motion.fraction.value) }
        view.newBestStyle -> ColorProducer { to }
        else -> ColorProducer { from }
    }

/** Registers the header's probe readers (MOTION_SPEC.md §8.2) while a probe is provided. */
@Composable
private fun ProbeRegistration(motion: HeaderMotion, view: HeaderView, effects: ContentEffects) {
    val probe = LocalMotionProbe.current
    SideEffect { motion.probeView = view }
    DisposableEffect(probe, motion, effects) {
        val registrations = probe?.let { motion.register(it, effects) }.orEmpty()
        onDispose { registrations.forEach { it.unregister() } }
    }
}

/** What one composition of the header shows (no animated value: those are read at draw time). */
private data class HeaderView(
    val score: Long,
    val best: Long,
    /** NEW BEST label and colours (the end state when the moment has run). */
    val newBestStyle: Boolean,
    /** The moment is past its label flip and still animating. */
    val running: Boolean,
    val starShown: Boolean,
)

/** The moment's phase: none, waiting for its label flip, or animating after it. */
private enum class MomentPhase {
    NONE,
    PENDING,
    RUNNING,
}

/**
 * The header's displayed values and its new-best moment (ARCHITECTURE.md §4.11 item 4). Only the
 * header's `LaunchedEffect(deltaMoveId)` and the moment's own coroutine write it.
 */
@Stable
private class HeaderMotion(initialScore: Long, initialBest: Long) {
    var displayedScore by mutableLongStateOf(initialScore)
    var displayedBest by mutableLongStateOf(initialBest)
    var momentId by mutableLongStateOf(NO_MOMENT)
    var momentEpoch by mutableLongStateOf(0L)
    var phase by mutableStateOf(MomentPhase.NONE)
    var starShown by mutableStateOf(false)
    val fraction = Animatable(0f)
    val starScale = Animatable(0f)
    val starRotation = Animatable(0f)
    val starAlpha = Animatable(1f)

    /** The BEST chip, for the sparkles' origin. Written on placement, read on emission. */
    var bestCoordinates: LayoutCoordinates? = null

    /** The latest composition's view, for the probe. */
    var probeView: HeaderView? = null

    /** The epoch of the header handled last; `null` before the first. */
    private var handledEpoch: Long? = null
    private var job: Job? = null
    private var scoreJob: Job? = null

    /** What the composition of [h] shows under [plan], from the moment's current phase. */
    fun view(h: HeaderUi, plan: MotionPlan): HeaderView {
        val fullMove = plan.scoreAtMs != null && h.event == BoardEventKind.Moved
        val live = phase != MomentPhase.NONE && momentId == h.newBestMoveId && keepsMoment(h)
        val startsNow = startsMoment(h, plan) && momentId != h.newBestMoveId
        val pending = startsNow || (live && phase == MomentPhase.PENDING)
        val running = live && phase == MomentPhase.RUNNING
        return HeaderView(
            score = if (fullMove) displayedScore else h.score,
            best = if (fullMove) maxOf(displayedBest, displayedScore) else h.best,
            newBestStyle = h.newBestAnnounced && !pending,
            running = running,
            starShown = running && starShown,
        )
    }

    /** True when [h] belongs to the moment's epoch, or is an undo one epoch after it. */
    private fun keepsMoment(h: HeaderUi): Boolean =
        h.epoch == momentEpoch || h.event == BoardEventKind.Undo && h.epoch == momentEpoch + 1

    /** The header effect's work for [h]: removals, the displayed score, the moment's start. */
    fun onHeader(
        h: HeaderUi,
        plan: MotionPlan,
        timeline: MoveTimeline,
        scope: CoroutineScope,
        effects: ContentEffects,
    ) {
        val last = handledEpoch
        handledEpoch = h.epoch
        if (last != null && last != h.epoch) {
            if (h.event == BoardEventKind.Undo && h.epoch == last + 1) {
                momentEpoch = h.epoch
            } else {
                // New game, size switch: the moment and its sparkles are removed (§4.2).
                cancelMoment()
                effects.clear()
            }
        }
        // A pending score update of an earlier move is dropped: this header decides (§3.5).
        scoreJob?.cancel()
        val scoreAt = plan.scoreAtMs
        if (scoreAt != null && h.event == BoardEventKind.Moved) {
            // Mechanism B: the values change on frame ⌈scoreAtMs⌉ of this move, unless a newer
            // move is published first (its own header then applies at its own +80).
            // The values are written inside that frame's callback, so it composes them.
            scoreJob = scope.launch {
                timeline.awaitOffset(h.deltaMoveId, scoreAt) {
                    displayedScore = h.score
                    displayedBest = h.best
                }
            }
        } else {
            displayedScore = h.score
            displayedBest = h.best
        }
        if (startsMoment(h, plan)) startMoment(h, plan, timeline, scope, effects)
    }

    private fun startMoment(
        h: HeaderUi,
        plan: MotionPlan,
        timeline: MoveTimeline,
        scope: CoroutineScope,
        effects: ContentEffects,
    ) {
        job?.cancel()
        momentId = h.newBestMoveId
        momentEpoch = h.epoch
        phase = MomentPhase.PENDING
        val id = h.deltaMoveId
        val seed = h.particleSeed
        job = scope.launch {
            val full = plan.newBest == NewBestMotion.MOMENT
            // The start values, while the chip still shows the BEST style (none of them is read).
            fraction.snapTo(0f)
            if (full) {
                starScale.snapTo(0f)
                starRotation.snapTo(0f)
                starAlpha.snapTo(1f)
            }
            // The label flip: inside the frame callback of ⌈newBestAtMs⌉, so that frame composes
            // it (mechanism B). A newer move's publish ends the wait early: the moment flips then
            // (it keeps running across later moves, ARCHITECTURE.md §4.11 item 4).
            val flip = {
                if (full) {
                    starShown = true
                    sparkleCenter()?.let { effects.emitSparkles(it, seed) }
                }
                phase = MomentPhase.RUNNING
            }
            if (!timeline.awaitOffset(id, plan.newBestAtMs, flip)) flip()
            coroutineScope {
                launch { crossfade(plan) }
                if (full) runStar()
            }
            starShown = false
            phase = MomentPhase.NONE
        }
    }

    private suspend fun crossfade(plan: MotionPlan) {
        val spec = plan.newBestColor
        if (spec == null) {
            fraction.snapTo(1f)
        } else {
            fraction.animateTo(
                1f,
                spring(spec.dampingRatio, spec.stiffness, GameMotion.FRACTION_THRESHOLD),
            )
        }
    }

    /** Scale `popSpatial`, rotation and the delayed fade of the star (MOTION_SPEC.md §3.6). */
    private suspend fun runStar() = coroutineScope {
        launch { starScale.animateTo(1f, GameMotion.popSpatial(GameMotion.SCALE_THRESHOLD)) }
        launch {
            starRotation.animateTo(
                GameMotion.NewBest.STAR_ROTATION_DEG,
                tween(
                    GameMotion.NewBest.STAR_ROTATION_MS,
                    easing = GameMotion.EmphasizedDecelerate,
                ),
            )
        }
        starAlpha.animateTo(
            0f,
            tween(
                durationMillis = GameMotion.NewBest.STAR_FADE_MS,
                delayMillis = GameMotion.NewBest.STAR_FADE_DELAY_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    private fun cancelMoment() {
        job?.cancel()
        job = null
        phase = MomentPhase.NONE
        starShown = false
        momentId = NO_MOMENT
    }

    /** The BEST chip's centre in root px, while it is placed. */
    private fun sparkleCenter(): Offset? =
        bestCoordinates
            ?.takeIf { it.isAttached }
            ?.let {
                it.localToRoot(Offset(it.size.width / 2f, it.size.height / 2f))
            }

    /** The probe readers of MOTION_SPEC.md §8.2: the header values and a running reader. */
    fun register(probe: MotionProbe, effects: ContentEffects) =
        listOf(
            probe.registerHeader {
                val view = probeView
                ProbeHeader(
                    displayedScore = view?.score ?: displayedScore,
                    displayedBest = view?.best ?: displayedBest,
                    bestChip =
                        ProbeBestChip(
                            labelKey =
                                if (view?.newBestStyle == true) "new_best_label" else "best_label",
                            containerFraction =
                                when {
                                    view?.running == true -> fraction.value
                                    view?.newBestStyle == true -> 1f
                                    else -> 0f
                                },
                            starScale = if (view?.starShown == true) starScale.value else null,
                            starRotationDeg = starRotation.value,
                            starAlpha = starAlpha.value,
                            sparklesAlive = effects.sparklesAlive,
                        ),
                )
            },
            probe.registerRunning {
                phase != MomentPhase.NONE ||
                    fraction.isRunning ||
                    starScale.isRunning ||
                    effects.sparklesAlive > 0
            },
        )

    private companion object {
        const val NO_MOMENT = -1L
    }
}

/** True when [h] starts the once-per-game moment under [plan] (Off shows it statically). */
private fun startsMoment(h: HeaderUi, plan: MotionPlan): Boolean =
    plan.level != MotionLevel.OFF &&
        h.event == BoardEventKind.Moved &&
        h.lastDelta > 0 &&
        h.newBestMoveId == h.deltaMoveId

/** The game's name as a numeral: the title "2048" in every locale and build. */
private const val TITLE_VALUE = 2048L

/** More's traversal index in the header group: after every other item (D-249). */
private const val MORE_TRAVERSAL_INDEX = 1f

/** What both chips show while the game loads (DESIGN_SYSTEM.md §6.1): an en dash. */
private const val LOADING_VALUE = "–"
