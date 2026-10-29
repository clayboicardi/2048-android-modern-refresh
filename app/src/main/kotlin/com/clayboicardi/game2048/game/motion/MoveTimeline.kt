package com.clayboicardi.game2048.game.motion

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Nanoseconds per millisecond. */
private const val NANOS_PER_MS = 1_000_000.0

/**
 * The one time base of every board change (D-183; MOTION_SPEC.md §0.1, §2.4). `BoardView` publishes
 * each `BoardUi`'s origin (F1, the first animation frame after the frame that composes it), and
 * every other consumer (score display, "+N", new best, haptics, overlays, effects) waits for
 * offsets from that published value, so separate state flows cannot drift apart. `GameScreen`
 * creates one per screen scope (`remember { MoveTimeline() }`) and provides it as
 * [LocalMoveTimeline]; an arrangement switch keeps it (ARCHITECTURE.md §4.11 item 3). Main thread
 * only.
 *
 * Only the newest move id and its origin are kept: an older id is superseded by definition, so the
 * timeline never grows. A wait for an id that is not published yet is resumed by its [publish] (not
 * by the next frame), so an offset of 0 lands on the origin frame itself.
 */
@Stable
class MoveTimeline {
    /** The newest published move id; [NONE] before any. Waiters observe it for publishes. */
    private val latestId = MutableStateFlow(NONE)

    /** The origin of [latestId]. */
    private var origin = 0L

    /**
     * Called by `BoardView` as `LaunchedEffect(board.moveId) { publish(board.moveId, withFrameNanos
     * { it }) }`, inside the same frame callback (F1) as the first frame of the tile animations. A
     * [moveId] that already has an origin keeps it: the `BoardView` an arrangement switch composes
     * anew publishes the current `moveId` again, and the waits of the screen scope go on unchanged
     * (MOTION_SPEC.md §3.16). Move ids only grow; an older id than the latest is ignored.
     */
    fun publish(moveId: Long, originNanos: Long) {
        if (moveId > latestId.value) {
            origin = originNanos
            latestId.value = moveId
        }
    }

    /**
     * Suspends until the first frame at or after origin([moveId]) + [ms] × scale, where scale is
     * `coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f` of the caller, and returns true on
     * that frame (MOTION_SPEC.md §0.1, §2.3 T2). If [moveId] has not been published yet, it first
     * waits for its publish. Returns false as soon as a newer move id has been published (the
     * caller then drops its work, MOTION_SPEC.md §4.1). Returns at once (true, after the publish)
     * when the scale is 0. Frames come from the caller's `MonotonicFrameClock` (`withFrameNanos`).
     */
    suspend fun awaitOffset(moveId: Long, ms: Int): Boolean =
        await(moveId, ms, scaled = true, onDue = null)

    /**
     * [awaitOffset] for a mechanism (B) flip (MOTION_SPEC.md §2.3 T2): [onDue] runs on the due
     * frame inside its frame callback, before that frame recomposes, so the state it writes is
     * composed and drawn on frame ⌈X⌉ itself; a write after a plain [awaitOffset] returns is
     * composed one frame later, because a frame resumes the coroutines it woke only after it has
     * recomposed. When the wait ends at once (an offset of 0 or a scale of 0), [onDue] runs at
     * once. [onDue] runs only when this returns true; on false (a newer id was published) the
     * caller decides. Animations that [onDue]'s state starts have play time 0 on the following
     * frame.
     */
    suspend fun awaitOffset(moveId: Long, ms: Int, onDue: () -> Unit): Boolean =
        await(moveId, ms, scaled = true, onDue = onDue)

    /**
     * Same as [awaitOffset], but [ms] is never multiplied by the animator scale (MOTION_SPEC.md
     * §2.3 T6): the Reduced and Off haptics, so the two win pulses stay 150 ms apart even at
     * scale 0.
     */
    suspend fun awaitRealTime(moveId: Long, ms: Int): Boolean =
        await(moveId, ms, scaled = false, onDue = null)

    /** The origin of the newest published move id, in frame-time nanoseconds; 0 before any. */
    val latestOriginNanos: Long
        get() = origin

    /**
     * Waits for [moveId]'s publish, then frame by frame until its offset [ms] is due; false once a
     * newer id is published. The scale is read again before every frame, so a change to 0 while
     * waiting makes the offset due on the next frame (MOTION_SPEC.md §2.1). [onDue] runs inside the
     * due frame's callback, or at once when the offset is due at the publish.
     */
    private suspend fun await(
        moveId: Long,
        ms: Int,
        scaled: Boolean,
        onDue: (() -> Unit)?,
    ): Boolean {
        var due = false
        if (latestId.first { it >= moveId } == moveId) {
            // Just after the publish the current frame is the origin frame (or a later one).
            if (offsetNanos(ms, scaled) <= 0L) {
                onDue?.invoke()
                due = true
            }
            while (!due && latestId.value == moveId) {
                val offset = offsetNanos(ms, scaled)
                due = withFrameNanos { frameNanos ->
                    val now = latestId.value == moveId && frameNanos >= origin + offset
                    if (now) onDue?.invoke()
                    now
                }
            }
        }
        return due
    }

    /** [ms] in nanoseconds, × the caller's animator scale if [scaled]. */
    private suspend fun offsetNanos(ms: Int, scaled: Boolean): Long {
        val scale =
            if (scaled) currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f else 1f
        return (ms * scale * NANOS_PER_MS).toLong()
    }

    private companion object {
        /** Below every move id. */
        const val NONE = Long.MIN_VALUE
    }
}

/** The screen scope's [MoveTimeline] (MOTION_SPEC.md §2.4). */
@Suppress("ComposeCompositionLocalUsage") // MOTION_SPEC.md §2.4: the screen's one time base
val LocalMoveTimeline =
    staticCompositionLocalOf<MoveTimeline> { error("MoveTimeline not provided") }
