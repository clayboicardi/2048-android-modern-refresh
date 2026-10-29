package com.clayboicardi.game2048.game.feedback

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.game.Nudge
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.MoveHaptic
import com.clayboicardi.game2048.game.motion.LocalMoveTimeline
import com.clayboicardi.game2048.game.motion.MoveTimeline
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.MotionLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The only caller of `performHapticFeedback` (D-065, D-066, D-185; ARCHITECTURE.md §5.9,
 * MOTION_SPEC.md §6). It schedules what `GameViewModel` resolved (`BoardEvent.Moved.haptic`) and
 * the separate events, never re-resolves priority, applies the interruption rules and the
 * invalid-move rate limit, and fires nothing while [enabled] is false. Offsets come from
 * [timeline]: Full waits with `awaitOffset` (scaled), Reduced and Off with `awaitRealTime`
 * (unscaled), by [level] at scheduling time (MOTION_SPEC.md §2.3 T6). Work runs in [scope], the
 * screen scope's composition scope, so haptics still pending when the screen leaves composition are
 * cancelled and never fired later (D-047). Below API 30 an invalid move fires nothing; the
 * game-over `Reject` fires on every API level. Main thread only.
 *
 * At most the pulses of the newest board change are pending (a newer change resolves every older
 * one at once), so the pending list never holds more than a handful of entries.
 */
@Stable
class HapticsController(
    private val feedback: HapticFeedback,
    private val timeline: MoveTimeline,
    private val clock: MonotonicClock,
    private val scope: CoroutineScope,
) {
    /** The Haptics setting (D-006). */
    var enabled: Boolean = true

    /** The effective motion level the next scheduled haptic uses. */
    var level: MotionLevel = MotionLevel.FULL

    /** The newest board change seen; older or repeated ones schedule nothing. */
    private var latestMoveId = Long.MIN_VALUE

    /** The clock time of the last invalid-move `Reject`; null before the first. */
    private var lastNudgeRejectMs: Long? = null

    private val pending = mutableListOf<Scheduled>()

    /**
     * Schedules the haptics of a newly presented board (MOTION_SPEC.md §6.2 `onBoardChange`): for a
     * `Moved` board its `haptic` (WIN `Confirm` ×2 at +120/+270, NEW_BEST, BIG_MERGE and MERGE at
     * +80; Reduced and Off at +0, the second win pulse at +150) and, when `becameOver`, `Reject` at
     * +300 (Reduced and Off +0). Nothing for any other event. A newer change interrupts: pending
     * priority 1–2 haptics fire on its F1, priority 3–4 and a pending game-over `Reject` are
     * dropped.
     */
    fun onBoardChange(board: BoardUi) {
        if (board.moveId > latestMoveId) {
            latestMoveId = board.moveId
            interruptPending(board.moveId)
            val moved = board.event as? BoardEvent.Moved
            if (moved != null && enabled) {
                val full = level == MotionLevel.FULL
                pulsesOf(moved).forEach { schedule(board.moveId, it, full) }
            }
        }
    }

    /**
     * An invalid move: `Reject` on the nudge's start frame, API 30+ only, at most once per
     * `GameMotion.Nudge.REJECT_MIN_INTERVAL_MS` of [clock] time.
     */
    @Suppress("UNUSED_PARAMETER") // every invalid move fires the same Reject (MOTION_SPEC.md §6.1)
    fun onNudge(nudge: Nudge) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val now = clock.nowMs()
            val last = lastNudgeRejectMs
            if (last == null || now - last >= GameMotion.Nudge.REJECT_MIN_INTERVAL_MS) {
                lastNudgeRejectMs = now
                feedback.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }
    }

    /**
     * The game-over `Reject` now: Keep going into `OVER`, called by `OverlayController` on the card
     * exit's origin Ft (MOTION_SPEC.md §3.12; M3 part 2). The Keep going tap itself is silent.
     */
    fun onGameOverNow() {
        fire(HapticFeedbackType.Reject)
    }

    /**
     * Resolves every pending pulse of an older change when [moveId] arrives (D-185): a carried one
     * (win, new best) is moved to [moveId]'s origin F1, every other one is dropped.
     */
    private fun interruptPending(moveId: Long) {
        val interrupted = pending.toList()
        pending.clear()
        interrupted.forEach { entry ->
            entry.job.cancel()
            if (entry.pulse.carriesOver) schedule(moveId, entry.pulse.atOrigin(), full = false)
        }
    }

    /** Waits for [pulse]'s offset from [moveId]'s origin, then fires it (MOTION_SPEC.md §2.3). */
    private fun schedule(moveId: Long, pulse: Pulse, full: Boolean) {
        lateinit var entry: Scheduled
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                val due =
                    if (full) timeline.awaitOffset(moveId, pulse.fullMs)
                    else timeline.awaitRealTime(moveId, pulse.reducedMs)
                pending.remove(entry)
                // A newer change published first: a carried pulse fires on its F1 (D-185).
                if (due || pulse.carriesOver) fire(pulse.type)
            }
        entry = Scheduled(pulse, job)
        pending += entry
        job.start()
    }

    private fun fire(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }

    /** One haptic of a board change and its offsets from the origin (D-065, D-185). */
    private class Pulse(
        val type: HapticFeedbackType,
        val fullMs: Int,
        val reducedMs: Int,
        /** Priority 1–2 (win, new best): fires on a newer change's F1 instead of being dropped. */
        val carriesOver: Boolean,
    ) {
        /** The same pulse at a newer change's origin, under any level. */
        fun atOrigin(): Pulse = Pulse(type, AT_ORIGIN_MS, AT_ORIGIN_MS, carriesOver)
    }

    private class Scheduled(val pulse: Pulse, val job: Job)

    private companion object {
        /** Reduced and Off fire at the origin (MOTION_SPEC.md §6.1). */
        const val AT_ORIGIN_MS = 0

        val WIN_FIRST =
            Pulse(
                HapticFeedbackType.Confirm,
                GameMotion.Win.HAPTIC_1_FROM_ORIGIN_MS,
                AT_ORIGIN_MS,
                carriesOver = true,
            )
        val WIN_SECOND =
            Pulse(
                HapticFeedbackType.Confirm,
                GameMotion.Win.HAPTIC_2_FROM_ORIGIN_MS,
                GameMotion.Win.REDUCED_HAPTIC_GAP_MS,
                carriesOver = true,
            )
        val NEW_BEST =
            Pulse(
                HapticFeedbackType.GestureThresholdActivate,
                GameMotion.NewBest.AT_MS,
                AT_ORIGIN_MS,
                carriesOver = true,
            )
        val BIG_MERGE =
            Pulse(
                HapticFeedbackType.Confirm,
                GameMotion.MERGE_REVEAL_MS,
                AT_ORIGIN_MS,
                carriesOver = false,
            )
        val MERGE =
            Pulse(
                HapticFeedbackType.SegmentTick,
                GameMotion.MERGE_REVEAL_MS,
                AT_ORIGIN_MS,
                carriesOver = false,
            )
        val GAME_OVER =
            Pulse(
                HapticFeedbackType.Reject,
                GameMotion.GameOver.AT_MS,
                AT_ORIGIN_MS,
                carriesOver = false,
            )

        /** The pulses of an accepted move: its resolved haptic, then the separate game over. */
        fun pulsesOf(moved: BoardEvent.Moved): List<Pulse> = buildList {
            when (moved.haptic) {
                MoveHaptic.WIN -> addAll(listOf(WIN_FIRST, WIN_SECOND))
                MoveHaptic.NEW_BEST -> add(NEW_BEST)
                MoveHaptic.BIG_MERGE -> add(BIG_MERGE)
                MoveHaptic.MERGE -> add(MERGE)
                MoveHaptic.NONE -> Unit
            }
            if (moved.becameOver) add(GAME_OVER)
        }
    }
}

/**
 * The screen scope's [HapticsController] (ARCHITECTURE.md §4.11 item 2, §5.9), from
 * `LocalHapticFeedback.current`, `LocalMoveTimeline.current`, `LocalEffectiveMotion.current` and
 * `rememberCoroutineScope()`; keeps [enabled] and the level current.
 */
@Composable
fun rememberHapticsController(enabled: Boolean, clock: MonotonicClock): HapticsController {
    val feedback = LocalHapticFeedback.current
    val timeline = LocalMoveTimeline.current
    val level = LocalEffectiveMotion.current
    val scope = rememberCoroutineScope()
    val controller =
        remember(feedback, timeline, clock, scope) {
            HapticsController(feedback, timeline, clock, scope).also {
                it.enabled = enabled
                it.level = level
            }
        }
    SideEffect {
        controller.enabled = enabled
        controller.level = level
    }
    return controller
}

/**
 * The controller's board and nudge effects, composed once by `GameScreen` outside the arrangement
 * slots (ARCHITECTURE.md §4.11 items 2, 3, 6): `LaunchedEffect(board.moveId)` calls `onBoardChange`
 * for a board that is not `Restored`; `LaunchedEffect(nudge?.id)` calls `onNudge`, on the nudge's
 * first animation frame, for a nudge that arrived after this composition first read [nudge]
 * (`remember { nudge?.id }` is never played). [board] is the screen scope's presented board, so a
 * composition created after a presentation was reported sees a `Restored` board and schedules
 * nothing.
 */
@Composable
fun HapticsEffects(controller: HapticsController, board: State<BoardUi>, nudge: State<Nudge?>) {
    val presented = board.value
    LaunchedEffect(controller, presented.moveId) {
        if (presented.event != BoardEvent.Restored) controller.onBoardChange(presented)
    }
    val firstNudgeId = remember { nudge.value?.id }
    val latestNudge = nudge.value
    LaunchedEffect(controller, latestNudge?.id) {
        if (latestNudge != null && latestNudge.id != firstNudgeId) {
            // The nudge's start frame is its first animation frame: `BoardView` starts the nudge
            // from an effect of this same frame, so it animates from the next one (MOTION_SPEC.md
            // §6.1, §8.1 `nudgeOriginMs`).
            withFrameNanos {}
            controller.onNudge(latestNudge)
        }
    }
}
