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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.game.GameUiState
import com.clayboicardi.game2048.game.HeaderUi
import com.clayboicardi.game2048.game.board.BoardEventKind
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.ContentEffects
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.LocalMoveTimeline
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.MoveTimeline
import com.clayboicardi.game2048.game.motion.ProbeBestChip
import com.clayboicardi.game2048.game.motion.ProbeHeader
import com.clayboicardi.game2048.ui.components.GameNumeralStyle
import com.clayboicardi.game2048.ui.components.ScoreChip
import com.clayboicardi.game2048.ui.components.ScoreChipLabel
import com.clayboicardi.game2048.ui.components.SizeChip
import com.clayboicardi.game2048.ui.components.rememberScoreValueSize
import com.clayboicardi.game2048.ui.theme.GameDimens
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

/**
 * The FULL header of the STACKED arrangement (DESIGN_SYSTEM.md §6.1 INLINE; §7.2, §7.3): row 1 is
 * the "2048" title (heading, `skin.titleColor`, auto-sized from `TitleMin` to [titleMax]) and the
 * Score and Best chips; row 2 is the board-size chip ([onSizeChipClick]). While [ui] is not ready
 * (Loading, DESIGN_SYSTEM.md §6.1) both chips show "–" and the size chip shows [loadingSize].
 *
 * Header motion (ARCHITECTURE.md §4.11 item 4; MOTION_SPEC.md §3.4–§3.6, §4.2): [header] and
 * [board] are its arrangement scope's presented values.
 * - The displayed score follows `awaitOffset(deltaMoveId, scoreAtMs)` after a move under Full and
 *   the true score at once otherwise; BEST shows `max(stored best, displayed score)`.
 * - "+N" floats: [ScoreFloats], composed inside `key(header.epoch, header.event)`.
 * - The once-per-game new-best moment runs in this header's own scope from
 *   `awaitOffset(deltaMoveId, newBestAtMs)`: label, container crossfade, [NewBestBadge] and the
 *   sparkles of [effects] (Full only, D-222). A new epoch cancels it unless it is an undo that
 *   advanced the epoch by exactly one. Until its label flip the chip keeps the BEST style;
 *   otherwise the NEW BEST style shows iff `newBestAnnounced`.
 * - [onHeaderPresented] reports `deltaMoveId` once the moments were launched and one frame passed.
 */
@Composable
fun GameHeader(
    header: State<HeaderUi>,
    board: State<BoardUi>,
    ui: State<GameUiState>,
    loadingSize: Int,
    titleMax: TextUnit,
    effects: ContentEffects,
    onHeaderPresented: (deltaMoveId: Long) -> Unit,
    onSizeChipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presented = header.value
    val state = ui.value
    val level = LocalEffectiveMotion.current
    val plan = remember(level) { GameMotion.plan(level) }
    val timeline = LocalMoveTimeline.current
    val scope = rememberCoroutineScope()
    val motion = remember { HeaderMotion(presented.score, presented.best) }

    LaunchedEffect(presented.deltaMoveId) {
        motion.onHeader(presented, plan, timeline, scope, effects)
        withFrameNanos {}
        if (presented.lastDelta != 0L) onHeaderPresented(presented.deltaMoveId)
    }

    val view = motion.view(presented, plan)
    ProbeRegistration(motion, view, effects)

    Column(modifier.testTag(HEADER_TAG)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = GameDimens.HeaderRow1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameTitle(titleMax, Modifier.weight(1f))
            Spacer(Modifier.width(GameDimens.Space8))
            ScoreRow(presented, board, view, motion, plan, loading = !state.ready)
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = GameDimens.HeaderRow2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SizeChip(
                n = if (state.ready) state.size.n else loadingSize,
                onClick = onSizeChipClick,
                modifier = Modifier.testTag(SIZE_CHIP_TAG),
            )
        }
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
    val color = LocalTileSkin.current.titleColor
    BasicText(
        text = TITLE_VALUE.toString(),
        modifier = modifier.semantics { heading() },
        style = GameNumeralStyle.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(GameDimens.TitleMin, max, GameDimens.TitleStep),
    )
}

/** Score, 8 dp, Best: the filmstrip's header region (`game:scores`). */
@Composable
private fun ScoreRow(
    header: HeaderUi,
    board: State<BoardUi>,
    view: HeaderView,
    motion: HeaderMotion,
    plan: MotionPlan,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val digits = if (loading) 1 else maxOf(header.score, header.best).toString().length
    val fontSize = rememberScoreValueSize(digits)
    val placeholder = if (loading) LOADING_VALUE else null
    val scoreLabel = stringResource(R.string.score_label)
    val bestLabel =
        stringResource(if (view.newBestStyle) R.string.new_best_label else R.string.best_label)
    val onSurface = colors.onSurface
    val onSurfaceVariant = colors.onSurfaceVariant
    // Moves and undos roll the digits; new game, size switch and restore swap them (§4.2).
    val roll = header.event == BoardEventKind.Moved || header.event == BoardEventKind.Undo
    Row(
        modifier = modifier.testTag(SCORES_TAG),
        horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreChip(
            spokenLabel = scoreLabel,
            spokenValue = placeholder ?: header.score.toString(),
            label = { ScoreChipLabel(scoreLabel, { onSurfaceVariant }) },
            modifier = Modifier.testTag(SCORE_TAG),
            overlay = {
                key(header.epoch, header.event) { ScoreFloats(header, board, plan) }
            },
        ) {
            ScoreOdometer(
                view.score,
                fontSize,
                { onSurface },
                plan,
                roll,
                placeholder = placeholder,
            )
        }
        val container =
            bestColor(motion, view, colors.surfaceContainerHigh, colors.tertiaryContainer)
        val ink = bestColor(motion, view, onSurface, colors.onTertiaryContainer)
        val labelInk = bestColor(motion, view, onSurfaceVariant, colors.onTertiaryContainer)
        ScoreChip(
            spokenLabel = bestLabel,
            spokenValue = placeholder ?: header.best.toString(),
            label = { BestLabel(view, motion, plan, labelInk) },
            modifier = Modifier.testTag(BEST_TAG).onPlaced { motion.bestCoordinates = it },
            container = container,
        ) {
            ScoreOdometer(view.best, fontSize, ink, plan, roll, placeholder = placeholder)
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

/** What both chips show while the game loads (DESIGN_SYSTEM.md §6.1): an en dash. */
private const val LOADING_VALUE = "–"
