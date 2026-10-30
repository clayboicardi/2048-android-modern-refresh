package com.clayboicardi.game2048.game.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import com.clayboicardi.game2048.engine.Board
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.game.OverlayEntry
import com.clayboicardi.game2048.game.OverlayUi
import com.clayboicardi.game2048.game.board.BoardOverlayLink
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.LocalBoardOverlayLink
import com.clayboicardi.game2048.game.feedback.HapticsController
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.LocalMoveTimeline
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.MoveTimeline
import com.clayboicardi.game2048.game.motion.ProbeEffects
import com.clayboicardi.game2048.game.motion.ProbeOverlay
import com.clayboicardi.game2048.game.motion.ProbeOverlayKind
import com.clayboicardi.game2048.game.motion.delayedSpec
import com.clayboicardi.game2048.game.restored
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.MotionLevel
import com.clayboicardi.game2048.ui.theme.MotionPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** The scrim over the board (`BoardScrim`), while a card is shown or exits. */
const val SCRIM_TAG = "overlay:scrim"

/** The win card (`WinCard`, Regular or Compact). */
const val WIN_CARD_TAG = "overlay:win"

/** The game-over card (`GameOverCard`, Regular or Compact). */
const val GAME_OVER_CARD_TAG = "overlay:over"

/** Keep going, on the win card. */
const val KEEP_GOING_TAG = "overlay:keepGoing"

/** New game, on either card (the controls bar's is `game:newGame`). */
const val CARD_NEW_GAME_TAG = "overlay:newGame"

/** Undo, on either card (the controls bar's is `game:undo`). */
const val CARD_UNDO_TAG = "overlay:undo"

/** Share, on either card (D-200; never in the menu, F10-AC7). */
const val CARD_SHARE_TAG = "overlay:share"

/**
 * What a card button does, and what the controls-bar Undo and New game and the U, Ctrl+Z,
 * Backspace, N and R keys do while a card is shown (D-199; MOTION_SPEC.md §3.12).
 */
enum class CardAction {
    KEEP_GOING,
    NEW_GAME,
    UNDO,
}

/**
 * The `GameViewModel` commands and the presentation report [OverlayController] calls: method
 * references of the screen's ViewModel (`onKeepGoing`, `onNewGame`, `onUndo`,
 * `onOverlayPresented`).
 */
@Immutable
class OverlayCommands(
    val onKeepGoing: () -> Unit,
    val onNewGame: () -> Unit,
    val onUndo: () -> Unit,
    val onOverlayPresented: (moveId: Long) -> Unit,
)

/**
 * Win and game-over sequencing (ARCHITECTURE.md §4.11, §5.8, §6.6; MOTION_SPEC.md §3.11–§3.14;
 * D-008, D-047, D-064, D-185, D-199, D-200). One per Game screen, in the **screen scope**:
 * `GameScreen` makes it with [rememberOverlayController] from the screen scope's presented overlay
 * and board, whether or not a card is shown, and composes the scrim, `ContentEffectsCanvas` and the
 * card from it outside the arrangement slots, so an arrangement switch leaves its sequence running
 * (MOTION_SPEC.md §3.16).
 *
 * Contract (M3.8, D-236):
 * - **Entry.** It reads a move's `Animated(moveId)` entry on that move's F0. It runs the entry
 *   choreography only for an entry that was `Animated` when its composition first read that
 *   overlay, timed from the origin through `MoveTimeline.awaitOffset` and specs delayed from the
 *   origin, never through the overlay flow (D-024). It reports the entry with
 *   `OverlayCommands.onOverlayPresented(entry.moveId)` from the `LaunchedEffect` of that overlay
 *   one frame later (the move's F1; after Keep going into `OVER`, the frame after the first
 *   composition of `GameOver`), and keeps running it after the ViewModel has switched the entry to
 *   `Restored`. A `Restored` entry, and a composition created later, shows the end state at once
 *   with [interactive] true, no ring, confetti or sparkles, and no haptic.
 * - **Card composition** ([shown]): Full win on ⌈680⌉, Full game over on ⌈800⌉, written inside the
 *   due frame's callback (`awaitOffset(moveId, ms) { … }`, D-224); Reduced on the origin frame,
 *   written when the entry starts on F0, so the origin frame composes it; under Off [shown] follows
 *   the presented overlay itself, so the card is composed on F0 (T5). Its overlay origin O
 *   (`MotionProbe.overlayOriginMs`) is the next frame, where the card, badge and button animations
 *   have play time 0. The scrim is mechanism (A): delayed in its spec from the origin.
 * - **Exits** ([act], MOTION_SPEC.md §3.12): Ft is the first frame after the action, captured with
 *   `withFrameNanos` and written to `MotionProbe.overlayActionOriginMs`; exit animations are
 *   started by the action, so they have play time 0 on Ft, and the card is removed inside the frame
 *   on which the scrim reaches 0. Keep going on a board with no moves runs the game-over sequence
 *   from T0 = Ft, with `HapticsController.onGameOverNow()` on Ft (D-185): the scrim goes on to
 *   0.78, the board desaturates and deflates from the winning move's spawn cell, and the game-over
 *   card composes on ⌈Ft + 500⌉. An overlay that leaves without an action (an undo from the board's
 *   accessibility action) exits from its first frame.
 * - **Probe.** While `LocalMotionProbe` is non-null it registers a `ProbeOverlay` reader (kind,
 *   scrim, card alpha and scale, button alphas in layout order with Share last, [interactive],
 *   badge morph, scale and rotation; `null` while no card is shown), the one `ProbeEffects` reader
 *   (the live board's ring and the confetti) and a running reader, and unregisters them on dispose.
 *
 * The animated values the scrim, card, badge and buttons draw with are its internal [visuals], read
 * only in draw and layout lambdas (MOTION_SPEC.md §7.2–§7.3). Main thread only.
 */
@Stable
class OverlayController
internal constructor(
    private val overlay: State<OverlayUi>,
    private val commands: OverlayCommands,
    initial: OverlayUi,
    instant: Boolean,
    internal val env: ControllerEnv,
) {
    private val state = CardState(initial, instant)
    private val anims = CardAnims(initial)
    private val sequencer = CardSequencer(overlay, commands, state, anims, env)

    /** The draw-time values of the scrim, the card, its buttons and the badge. */
    internal val visuals: CardVisuals = AnimatedVisuals(anims, { atEnd }) { shown }

    /**
     * The card composed now: `None` before the card's composition frame, and still the exiting card
     * after the ViewModel's overlay became `None` until its exit ends (MOTION_SPEC.md §3.11–§3.13).
     * A `Win` or `GameOver` here is in `Restored` form; the entry kind is the controller's own
     * state. Under Off it is the presented overlay itself (MOTION_SPEC.md §2.3 T5), and so is a
     * `Restored` overlay while no sequence runs: it is composed at its end state on the frame it is
     * presented, before the driver takes it over (MOTION_SPEC.md §3.14, D-047).
     */
    val shown: OverlayUi
        get() =
            when {
                state.instant -> overlay.value.restored()
                state.card != OverlayUi.None || state.active -> state.card
                else ->
                    overlay.value.takeIf { it.entryOrNull() == OverlayEntry.Restored }
                        ?: OverlayUi.None
            }

    /** Every visual is at its end value: under Off, and for a `Restored` overlay not yet taken. */
    private val atEnd: Boolean
        get() = state.instant || (state.card == OverlayUi.None && !state.active)

    /**
     * D-200: whether the card's buttons, including Share, accept clicks, carry no `disabled()`
     * semantics and can take focus. The buttons are composed with `enabled = true` from their first
     * frame either way. False from the card's composition until D-008's time: Full win ⌈880⌉ (T0 +
     * 800), Full game over ⌈1000⌉ (T0 + 700), Keep going into `OVER` ⌈Ft + 700⌉, each through
     * `MoveTimeline.awaitOffset` (so it scales with the animator scale, F13-AC4) or the same wait
     * from Ft; Reduced at 150 ms (frame 160); at once under Off and for a `Restored` entry. On the
     * flip focus moves to Keep going (win) or New game (game over) (D-081). True when no card is
     * shown.
     */
    val interactive: Boolean
        get() = state.instant || state.card == OverlayUi.None || state.interactive

    /**
     * True from the frame of a card action until the card's exit has ended: the card then takes no
     * pointer input and no focus, while input reaches the board at once (MOTION_SPEC.md §3.12, I6).
     */
    val exiting: Boolean
        get() = !state.instant && state.exiting

    /**
     * A card button, or the controls bar or a key acting like one while a card is shown (D-199):
     * runs the ViewModel command at once (so the next frame's move is applied, I6) and the exit of
     * MOTION_SPEC.md §3.12 from Ft. Ignored while the card is not [interactive] or is [exiting];
     * the controls bar and keys call it only while a card is shown.
     */
    fun act(action: CardAction) {
        if (shown == OverlayUi.None || !interactive || exiting) return
        sequencer.act(action, shown)
    }

    /** True while a card is shown and not exiting: the chrome and keys then act through [act]. */
    internal val cardActs: Boolean
        get() = shown != OverlayUi.None && !exiting

    /**
     * True while the scrim is composed: from the start of a card's sequence (its scrim may fade in
     * before the card, MOTION_SPEC.md §3.13) until its exit has ended; under Off, while a card is
     * shown.
     */
    internal val scrimActive: Boolean
        get() = state.active || shown != OverlayUi.None

    /** A new presented overlay, on its first frame (the driver's `LaunchedEffect`). */
    internal fun onPresented(next: OverlayUi) = sequencer.onPresented(next)

    /** The level changed: under Off [shown] follows the overlay; back from Off, catch up. */
    internal fun onLevel(instant: Boolean) {
        if (state.instant == instant) return
        state.instant = instant
        if (!instant) sequencer.resync()
    }

    /** Reports an `Animated` entry presented (`OverlayCommands.onOverlayPresented`). */
    internal fun report(moveId: Long) {
        commands.onOverlayPresented(moveId)
    }

    /** `MotionProbe`'s overlay (MOTION_SPEC.md §8.2), `null` while no card is shown. */
    internal fun probeOverlay(): ProbeOverlay? {
        val card = shown
        if (card == OverlayUi.None) return null
        val win = card is OverlayUi.Win
        return ProbeOverlay(
            kind = if (win) ProbeOverlayKind.WIN else ProbeOverlayKind.OVER,
            scrimAlpha = visuals.scrim(),
            cardAlpha = visuals.alpha(),
            cardScale = visuals.scale(),
            buttonAlphas = List(if (win) WIN_BUTTONS else OVER_BUTTONS) { visuals.button(it) },
            interactive = interactive,
            badgeMorph = visuals.morph(),
            badgeScale = visuals.badgeScale(),
            badgeRotationDeg = visuals.rotation(),
        )
    }

    /** True while an animation of the scrim, card, badge or buttons runs. */
    internal val isRunning: Boolean
        get() = anims.isRunning
}

/**
 * The screen scope's [OverlayController] (ARCHITECTURE.md §4.11 item 2): [overlay] and [board] are
 * the screen scope's `rememberPresented` states of `GameViewModel.overlay` and `board`; [haptics]
 * is the screen's `HapticsController` (the Keep-going-into-`OVER` `Reject`). It reads
 * `LocalMoveTimeline`, `LocalEffectiveMotion`, `LocalMotionProbe`, `LocalWindowInfo` (the confetti
 * count) and the screen's board link (`LocalBoardOverlayLink`: the live board and the
 * `ContentEffects`). It composes its driver, which reads [overlay] in a scope of its own; the
 * caller's composition reads it only through [OverlayController.shown].
 */
@Composable
fun rememberOverlayController(
    overlay: State<OverlayUi>,
    board: State<BoardUi>,
    haptics: HapticsController,
    commands: OverlayCommands,
): OverlayController {
    val timeline = LocalMoveTimeline.current
    val level = LocalEffectiveMotion.current
    val probe = LocalMotionProbe.current
    val link = LocalBoardOverlayLink.current
    val windowInfo = LocalWindowInfo.current
    val scope = rememberCoroutineScope()
    val instant = level == MotionLevel.OFF
    val controller =
        remember(overlay, commands) {
            val env = ControllerEnv(scope, timeline, haptics, board)
            env.plan = GameMotion.plan(level)
            val initial = Snapshot.withoutReadObservation { overlay.value }
            OverlayController(overlay, commands, initial, instant, env)
        }
    SideEffect {
        val env = controller.env
        env.timeline = timeline
        env.haptics = haptics
        env.board = board
        env.plan = GameMotion.plan(level)
        env.probe = probe
        env.link = link
        env.windowInfo = windowInfo
    }
    OverlayDriver(controller, overlay, instant)
    return controller
}

/**
 * Reads the presented [overlay] in a recomposition scope of its own: its `LaunchedEffect` starts
 * each new overlay on its first frame and reports an `Animated` entry one frame later
 * (ARCHITECTURE.md §4.11); it registers the probe readers of MOTION_SPEC.md §8.2.
 */
@Composable
private fun OverlayDriver(
    controller: OverlayController,
    overlay: State<OverlayUi>,
    instant: Boolean,
) {
    val presented = overlay.value
    LaunchedEffect(instant) { controller.onLevel(instant) }
    // A new overlay restarts the effect; the report's Restored replacement is the same key.
    LaunchedEffect(controller, presented.restored()) {
        controller.onPresented(presented)
        val entry = presented.entryOrNull()
        if (entry is OverlayEntry.Animated) {
            withFrameNanos {}
            controller.report(entry.moveId)
        }
    }
    val probe = LocalMotionProbe.current
    DisposableEffect(probe, controller) {
        val registrations = if (probe == null) emptyList() else controller.register(probe)
        onDispose { registrations.forEach { it.unregister() } }
    }
}

/**
 * The probe readers of MOTION_SPEC.md §8.2: ring, confetti and the scrim through one effects reader
 * (the scrim also while no card is composed, D-242).
 */
private fun OverlayController.register(probe: MotionProbe) =
    listOf(
        probe.registerOverlay { probeOverlay() },
        probe.registerEffects {
            val link = env.link
            ProbeEffects(
                ring = link?.board?.ring(),
                confettiAlive = link?.effects?.confettiAlive ?: 0,
                scrimAlpha = if (scrimActive) visuals.scrim() else null,
            )
        },
        probe.registerRunning { isRunning || (env.link?.effects?.confettiAlive ?: 0) > 0 },
    )

/** This overlay's entry; `null` for `None`. */
private fun OverlayUi.entryOrNull(): OverlayEntry? =
    when (this) {
        OverlayUi.None -> null
        is OverlayUi.Win -> entry
        is OverlayUi.GameOver -> entry
    }

/**
 * What the controller reads from its composition (screen scope): the scope its sequences run in
 * (the composition's frame clock and `MotionDurationScale`), the timeline, the haptics controller,
 * the presented board, the plan of the effective level, the probe, the board link and the window.
 * Written by [rememberOverlayController]'s `SideEffect`. Main thread only.
 */
@Stable
internal class ControllerEnv(
    val scope: CoroutineScope,
    var timeline: MoveTimeline,
    var haptics: HapticsController,
    var board: State<BoardUi>,
) {
    var plan: MotionPlan = GameMotion.plan(MotionLevel.FULL)
    var probe: MotionProbe? = null
    var link: BoardOverlayLink? = null
    var windowInfo: WindowInfo? = null
}

/** The controller's snapshot state: the composed card, its interactivity and exit, the level. */
@Stable
private class CardState(initial: OverlayUi, instant: Boolean) {
    private val restored = initial.entryOrNull() == OverlayEntry.Restored

    /** The card composed now (`Restored` form), or `None`. */
    var card by mutableStateOf(if (restored) initial.restored() else OverlayUi.None)

    var interactive by mutableStateOf(restored)
    var exiting by mutableStateOf(false)

    /** A sequence or a card is on screen: the scrim is composed. */
    var active by mutableStateOf(restored)

    /** The effective level is Off: everything follows the presented overlay on its F0 (T5). */
    var instant by mutableStateOf(instant)
}

/**
 * The scrim, card, button and badge values (MOTION_SPEC.md §3.11–§3.13). A controller created for a
 * `Restored` card starts at its end state. Main thread only.
 */
@Stable
private class CardAnims(initial: OverlayUi) {
    val scrim =
        Animatable(
            if (initial.entryOrNull() == OverlayEntry.Restored) endScrim(initial) else 0f,
            GameMotion.ALPHA_THRESHOLD,
        )
    val alpha = Animatable(1f, GameMotion.ALPHA_THRESHOLD)
    val scale = Animatable(1f, GameMotion.SCALE_THRESHOLD)
    val buttons = Array(WIN_BUTTONS) { Animatable(1f, GameMotion.ALPHA_THRESHOLD) }
    val morph = Animatable(1f, GameMotion.MORPH_THRESHOLD)
    val badgeScale = Animatable(1f, GameMotion.SCALE_THRESHOLD)
    val rotation = Animatable(GameMotion.Win.BADGE_ROTATION_DEG)

    val isRunning: Boolean
        get() =
            scrim.isRunning ||
                alpha.isRunning ||
                scale.isRunning ||
                buttons.any { it.isRunning } ||
                morph.isRunning ||
                badgeScale.isRunning ||
                rotation.isRunning

    /**
     * Ends every running value where it is. Compose starts an `animateTo` that interrupts a running
     * `Animatable` on the interrupted animation's frame timeline (its last frame time), so without
     * this a card exit that cuts the entry short would have play time 16 ms on Ft, not 0
     * (MOTION_SPEC.md §3.12); a stopped value starts its next animation on that animation's own
     * first frame.
     */
    suspend fun stopAll() {
        scrim.stop()
        alpha.stop()
        scale.stop()
        buttons.forEach { it.stop() }
        morph.stop()
        badgeScale.stop()
        rotation.stop()
    }

    /** The card's first-frame values under [plan] (the scrim is the caller's). */
    suspend fun snapStart(plan: MotionPlan) {
        alpha.snapTo(plan.cardStartAlpha)
        scale.snapTo(plan.cardStartScale)
        val buttonStart = if (plan.buttonAlpha == null) 1f else 0f
        buttons.forEach { it.snapTo(buttonStart) }
        morph.snapTo(if (plan.badgeMorph == null) 1f else 0f)
        badgeScale.snapTo(if (plan.badgeScale == null) 1f else 0f)
        rotation.snapTo(if (plan.badgeRotation == null) GameMotion.Win.BADGE_ROTATION_DEG else 0f)
    }

    /** The end state of [card] (`Restored`, MOTION_SPEC.md §3.14). */
    suspend fun snapEnd(card: OverlayUi) {
        scrim.snapTo(endScrim(card))
        alpha.snapTo(1f)
        scale.snapTo(1f)
        buttons.forEach { it.snapTo(1f) }
        morph.snapTo(1f)
        badgeScale.snapTo(1f)
        rotation.snapTo(GameMotion.Win.BADGE_ROTATION_DEG)
    }

    /**
     * The card's entry from O, the frame after the one that composed it (MOTION_SPEC.md §3.11,
     * §3.13): card alpha and scale, the staggered buttons (Share with the first on the win card)
     * and the win badge. Returns once every value has reached its end.
     */
    suspend fun playCard(
        plan: MotionPlan,
        win: Boolean,
        scope: CoroutineScope,
        onOrigin: (Long) -> Unit,
    ) {
        val jobs = ArrayList<Job>()
        jobs +=
            scope.launch(start = CoroutineStart.UNDISPATCHED) { onOrigin(withFrameNanos { it }) }
        jobs += scope.go { alpha.animateOrSnap(1f, plan.cardAlpha) }
        jobs += scope.go { scale.animateOrSnap(1f, plan.cardScale) }
        val stagger = if (win) WIN_STAGGER else OVER_STAGGER
        for (i in stagger.indices) {
            val delay = plan.buttonStaggerMs * stagger[i]
            jobs += scope.go {
                buttons[i].animateOrSnap(1f, plan.buttonAlpha?.let { delayedSpec(it, delay) })
            }
        }
        if (win) {
            jobs += scope.go { morph.animateOrSnap(1f, plan.badgeMorph) }
            jobs += scope.go { badgeScale.animateOrSnap(1f, plan.badgeScale) }
            jobs += scope.go {
                rotation.animateOrSnap(GameMotion.Win.BADGE_ROTATION_DEG, plan.badgeRotation)
            }
        }
        jobs.joinAll()
    }
}

/** Launches [block] undispatched, so an animation it starts has play time 0 on the next frame. */
private fun CoroutineScope.go(block: suspend CoroutineScope.() -> Unit): Job =
    launch(start = CoroutineStart.UNDISPATCHED, block = block)

/**
 * Animates to [target] with [spec], or snaps when [spec] is `null` (this level does not animate the
 * element). [onFrame] runs inside every animation frame, the last one included.
 */
private suspend fun Animatable<Float, AnimationVector1D>.animateOrSnap(
    target: Float,
    spec: FiniteAnimationSpec<Float>?,
    onFrame: (Float) -> Unit = {},
) {
    if (spec == null) {
        snapTo(target)
    } else {
        animateTo(target, spec) { onFrame(value) }
    }
}

/**
 * Waits for the first frame at or after [originNanos] + [ms] × the animator scale and runs [onDue]
 * inside it (MOTION_SPEC.md §2.3 T2, D-224): the waits of a sequence timed from Ft, which no board
 * change publishes (Keep going into `OVER`).
 */
private suspend fun awaitFrameAt(originNanos: Long, ms: Int, onDue: () -> Unit) {
    var due = false
    while (!due) {
        val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        val at = originNanos + (ms * scale * NANOS_PER_MS).toLong()
        due = withFrameNanos { frame ->
            val now = frame >= at
            if (now) onDue()
            now
        }
    }
}

/**
 * The draw-time values of the overlay (read in draw and layer lambdas only, MOTION_SPEC.md §7.2):
 * the scrim, the card's alpha and scale, button [i]'s alpha in layout order (Share last) and the
 * win badge's morph progress (clamped to [0, 1]), scale and rotation in degrees.
 */
@Stable
internal interface CardVisuals {
    fun scrim(): Float

    fun alpha(): Float

    fun scale(): Float

    fun button(i: Int): Float

    fun morph(): Float

    fun badgeScale(): Float

    fun rotation(): Float
}

/**
 * [CardVisuals] from [anims]; while [atEnd] (Off, or a `Restored` card not yet taken over) every
 * value is its end value, the scrim's that of the [shown] card (D-008, T5, MOTION_SPEC.md §3.14).
 */
private class AnimatedVisuals(
    private val anims: CardAnims,
    private val atEnd: () -> Boolean,
    private val shown: () -> OverlayUi,
) : CardVisuals {
    override fun scrim(): Float = if (atEnd()) endScrim(shown()) else anims.scrim.value

    override fun alpha(): Float = if (atEnd()) 1f else anims.alpha.value

    override fun scale(): Float = if (atEnd()) 1f else anims.scale.value

    override fun button(i: Int): Float = if (atEnd()) 1f else anims.buttons[i].value

    override fun morph(): Float = if (atEnd()) 1f else anims.morph.value.coerceIn(0f, 1f)

    override fun badgeScale(): Float = if (atEnd()) 1f else anims.badgeScale.value

    override fun rotation(): Float =
        if (atEnd()) GameMotion.Win.BADGE_ROTATION_DEG else anims.rotation.value
}

/** The scrim of [card]'s end state: 0.60 for the win card, 0.78 for game over (D-008, D-064). */
private fun endScrim(card: OverlayUi): Float =
    when (card) {
        is OverlayUi.Win -> GameMotion.Win.SCRIM_ALPHA
        is OverlayUi.GameOver -> GameMotion.GameOver.SCRIM_ALPHA
        OverlayUi.None -> 0f
    }

/**
 * The sequences (MOTION_SPEC.md §3.11–§3.14): the entry of an `Animated` card, the end state of a
 * `Restored` one, a card exit, and Keep going into `OVER`. At most one sequence runs; a newer one
 * cancels it. Main thread only.
 */
private class CardSequencer(
    private val overlay: State<OverlayUi>,
    private val commands: OverlayCommands,
    private val state: CardState,
    private val anims: CardAnims,
    private val env: ControllerEnv,
) {
    private enum class Kind {
        NONE,
        ENTRY,
        EXIT,
        KEEP_GOING,
    }

    private var job: Job? = null
    private var kind = Kind.NONE

    /** The overlay handled last. */
    private var last: OverlayUi = OverlayUi.None

    /** [act] started Keep going into `OVER`: the presented `GameOver` entry is already running. */
    private var keepGoingAdopted = false

    fun act(action: CardAction, leaving: OverlayUi) {
        val intoOver = action == CardAction.KEEP_GOING && !canMove(env.board.value)
        when (action) {
            CardAction.KEEP_GOING -> commands.onKeepGoing()
            CardAction.NEW_GAME -> commands.onNewGame()
            CardAction.UNDO -> commands.onUndo()
        }
        when {
            intoOver -> keepGoing(fromAction = true, (leaving as? OverlayUi.Win)?.spawnIndex)
            state.instant -> {
                if (leaving is OverlayUi.GameOver) env.link?.board?.restoreSaturation()
                captureFt(env)
            }
            else -> exit(leaving, fromAction = true)
        }
    }

    fun onPresented(next: OverlayUi) {
        val previous = last
        last = next
        val entry = next.entryOrNull()
        when {
            next == OverlayUi.None -> leave()
            next is OverlayUi.GameOver &&
                entry is OverlayEntry.Animated &&
                previous is OverlayUi.Win -> {
                if (keepGoingAdopted) {
                    keepGoingAdopted = false
                } else {
                    keepGoing(fromAction = false, previous.spawnIndex)
                }
            }
            entry is OverlayEntry.Animated && !state.instant -> entry(next, entry.moveId)
            else -> restored(next)
        }
    }

    /** Back from Off: the composed card catches up with the presented overlay. */
    fun resync() {
        val current = overlay.value.restored()
        if (kind == Kind.NONE && state.card != current) {
            if (current == OverlayUi.None) remove() else restored(current)
        }
    }

    /** The overlay left: an exit unless one runs already; the confetti goes at once (§4.2). */
    private fun leave() {
        env.link?.effects?.clearConfetti()
        keepGoingAdopted = false
        when {
            kind == Kind.EXIT -> Unit
            state.card != OverlayUi.None && !state.instant -> exit(state.card, fromAction = false)
            else -> {
                cancel()
                remove()
                env.scope.go { anims.scrim.snapTo(0f) }
            }
        }
    }

    private fun entry(next: OverlayUi, moveId: Long) {
        cancel()
        kind = Kind.ENTRY
        state.card = OverlayUi.None
        state.interactive = false
        state.exiting = false
        state.active = true
        val plan = env.plan
        val timeline = env.timeline
        val win = next is OverlayUi.Win
        val scrimSpec = if (win) plan.winScrim else plan.overScrim
        val scrimAt = if (win) plan.winScrimAtMs else plan.overScrimAtMs
        val cardAt = if (win) plan.winCardAtMs else plan.overCardAtMs
        val interactiveAt = if (win) plan.winInteractiveAtMs else plan.overInteractiveAtMs
        job =
            env.scope.go {
                anims.snapStart(plan)
                anims.scrim.snapTo(0f)
                // Reduced: the card is composed on the origin frame, the first composition after
                // this entry starts on F0 (its card fades in from the next frame, below). Written
                // only once the publish has come, it would compose one frame later.
                if (cardAt <= 0) state.card = next.restored()
                // (A) from the origin: the scrim's delay is inside its spec.
                go {
                    anims.scrim.animateOrSnap(
                        endScrim(next),
                        scrimSpec?.let { delayedSpec(it, scrimAt) },
                    )
                }
                if (next is OverlayUi.Win) {
                    plan.winConfettiAtMs?.let { at ->
                        go { timeline.awaitOffset(moveId, at) { emitConfetti(env, next) } }
                    }
                }
                go { timeline.awaitOffset(moveId, interactiveAt) { state.interactive = true } }
                // (B) the card composes on ⌈cardAt⌉; its own animations start from the next frame.
                if (timeline.awaitOffset(moveId, cardAt) { state.card = next.restored() }) {
                    anims.playCard(plan, win, this) { origin ->
                        env.probe?.overlayOriginMs = origin / NANOS_PER_MS
                    }
                }
                kind = Kind.NONE
            }
    }

    private fun restored(next: OverlayUi) {
        val card = next.restored()
        val settled = state.interactive && !state.exiting
        if (kind == Kind.NONE && state.card == card && settled) return
        cancel()
        keepGoingAdopted = false
        state.card = card
        state.interactive = true
        state.exiting = false
        state.active = true
        env.scope.go { anims.snapEnd(card) }
    }

    /** A card exit (MOTION_SPEC.md §3.12): alpha → 0, scale → 0.96, scrim → 0, then removed. */
    private fun exit(leaving: OverlayUi, fromAction: Boolean) {
        cancel()
        kind = Kind.EXIT
        state.exiting = true
        val plan = env.plan
        if (leaving is OverlayUi.GameOver) env.link?.board?.restoreSaturation()
        job =
            env.scope.go {
                anims.stopAll()
                // Ft is the first frame of the exit animations (play time 0 there, §3.12).
                if (fromAction) captureFt(env)
                go { anims.alpha.animateOrSnap(0f, plan.exitCardAlpha) }
                plan.exitCardScale?.let { spec ->
                    go { anims.scale.animateTo(GameMotion.Card.EXIT_SCALE, spec) }
                }
                // Removed inside the frame on which the scrim reaches 0, so that frame composes it.
                anims.scrim.animateOrSnap(0f, plan.exitScrim) { if (it == 0f) remove() }
                remove()
            }
    }

    /**
     * Keep going on a board with no moves left (D-036; MOTION_SPEC.md §3.12): the win card fades
     * and shrinks, the scrim goes on to 0.78, the board desaturates and deflates, `Reject` fires on
     * Ft, and the game-over card composes on ⌈Ft + 500⌉ and is interactive on ⌈Ft + 700⌉ (the
     * game-over offsets less T0). The deflate ripple starts from [rippleFrom], the winning move's
     * spawn cell (`OverlayUi.Win.spawnIndex`, D-237). Under Off the card follows the overlay and
     * only Ft, the haptic and the board's end state remain.
     */
    private fun keepGoing(fromAction: Boolean, rippleFrom: Int?) {
        cancel()
        kind = Kind.KEEP_GOING
        keepGoingAdopted = fromAction
        state.exiting = true
        state.active = true
        val plan = env.plan
        val haptics = env.haptics
        env.link?.board?.keepGoingIntoOver(rippleFrom)
        val cardAt = plan.overCardAtMs - plan.overT0Ms
        val interactiveAt = plan.overInteractiveAtMs - plan.overT0Ms
        job =
            env.scope.go {
                anims.stopAll()
                val fade = go { anims.alpha.animateOrSnap(0f, plan.exitCardAlpha) }
                val shrink = go {
                    plan.exitCardScale?.let {
                        anims.scale.animateTo(GameMotion.Card.EXIT_SCALE, it)
                    }
                }
                go {
                    anims.scrim.animateOrSnap(GameMotion.GameOver.SCRIM_ALPHA, plan.keepGoingScrim)
                }
                val ft = withFrameNanos { frame ->
                    haptics.onGameOverNow()
                    frame
                }
                if (fromAction) env.probe?.overlayActionOriginMs = ft / NANOS_PER_MS
                if (state.instant) {
                    kind = Kind.NONE
                    return@go
                }
                go { awaitFrameAt(ft, interactiveAt) { state.interactive = true } }
                if (cardAt > 0) {
                    joinAll(fade, shrink)
                    anims.snapStart(plan)
                    awaitFrameAt(ft, cardAt) { showGameOver() }
                } else {
                    // Reduced: the game-over card replaces the win card right after Ft, faded in.
                    fade.cancel()
                    shrink.cancel()
                    anims.snapStart(plan)
                    showGameOver()
                }
                anims.playCard(plan, win = false, this) { origin ->
                    env.probe?.overlayOriginMs = origin / NANOS_PER_MS
                }
                kind = Kind.NONE
            }
    }

    /** The game-over card replaces the faded win card (Keep going into `OVER`). */
    private fun showGameOver() {
        val over = overlay.value as? OverlayUi.GameOver ?: return
        state.card = over.restored()
        state.interactive = false
        state.exiting = false
    }

    private fun remove() {
        state.card = OverlayUi.None
        state.exiting = false
        state.interactive = false
        state.active = false
        kind = Kind.NONE
    }

    private fun cancel() {
        job?.cancel()
        job = null
        kind = Kind.NONE
    }
}

/** Emits the win confetti from the target tile's centre (MOTION_SPEC.md §3.11). */
private fun emitConfetti(env: ControllerEnv, win: OverlayUi.Win) {
    val link = env.link ?: return
    val center = link.board?.targetCenterInRoot() ?: return
    val width = env.windowInfo?.containerDpSize?.width
    val count =
        if (width != null && width >= GameDimens.WidthExpandedMin) {
            GameMotion.Confetti.COUNT_EXPANDED
        } else {
            GameMotion.Confetti.COUNT_COMPACT
        }
    link.effects.emitConfetti(center, count, win.particleSeed)
}

/** Ft of a card action: the first frame after it (MOTION_SPEC.md §3.12, §8.2). */
private fun captureFt(env: ControllerEnv) {
    env.scope.go {
        val ft = withFrameNanos { it }
        env.probe?.overlayActionOriginMs = ft / NANOS_PER_MS
    }
}

/**
 * Whether [board] has a move left, by the engine's rule (`Game.canMove`, D-037): Keep going goes on
 * to `OVER` exactly when it has none (D-036).
 */
private fun canMove(board: BoardUi): Boolean {
    val cells = board.cells
    return Game.canMove(Board.of(board.size, ByteArray(cells.size) { cells[it].toByte() }))
}

/** The win card's buttons: Keep going, New game, Undo and Share (layout order, Share last). */
private const val WIN_BUTTONS = 4

/** The game-over card's buttons: New game, Undo and Share. */
private const val OVER_BUTTONS = 3

/** Each win button's stagger multiple: Keep going 1, New game 2, Undo 3, Share with Keep going. */
private val WIN_STAGGER = intArrayOf(1, 2, 3, 1)

/** Each game-over button's stagger multiple: New game 1, Undo 2, Share 3. */
private val OVER_STAGGER = intArrayOf(1, 2, 3)

private const val NANOS_PER_MS = 1_000_000L
