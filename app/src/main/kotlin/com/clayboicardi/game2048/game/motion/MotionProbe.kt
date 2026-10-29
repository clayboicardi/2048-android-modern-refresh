package com.clayboicardi.game2048.game.motion

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.geometry.Offset
import com.clayboicardi.game2048.game.board.TileKind

/**
 * The motion test hook (MOTION_SPEC.md §8.2; like D-150's recomposition counter): `null` in
 * production, so the production cost is one null check at composition. A component with values to
 * report registers a reader in a `DisposableEffect` only while [LocalMotionProbe] is non-null, and
 * unregisters it in `onDispose`; origins are written by their owners on the frame they occur. Tests
 * read [MotionProbe.snapshot].
 */
@Suppress("ComposeCompositionLocalUsage") // MOTION_SPEC.md §8.2: test hook, null in production
val LocalMotionProbe = staticCompositionLocalOf<MotionProbe?> { null }

/**
 * Collects what the composition reports (MOTION_SPEC.md §8.2). Main thread only. Times are
 * frame-time milliseconds (`frameTimeNanos / 1_000_000`), which equal the test clock's
 * `mainClock.currentTime` on the frame they describe.
 *
 * List readers ([registerTiles], [registerFloats]) are concatenated in registration order; for the
 * other kinds the most recently registered live reader answers, and with none the snapshot holds
 * the field's neutral value (`null`, 0 or empty).
 */
class MotionProbe {
    private val tiles = Readers<List<ProbeTile>>()
    private val board = Readers<ProbeBoard>()
    private val floats = Readers<List<ProbeFloat>>()
    private val header = Readers<ProbeHeader>()
    private val effects = Readers<ProbeEffects>()
    private val overlay = Readers<ProbeOverlay?>()
    private val chrome = Readers<ProbeChrome>()
    private val running = Readers<Boolean>()

    /** The latest `MoveTimeline` origin; written by `BoardView` right after it publishes. */
    var originMs: Long? = null

    /**
     * The first frame of the latest nudge (Full) or outline pulse (Reduced), from the `animateTo`
     * block's `startTimeNanos`; written by `BoardView`.
     */
    var nudgeOriginMs: Long? = null

    /**
     * Ft of the latest card action (MOTION_SPEC.md §3.12); written by `OverlayController` (part 2).
     */
    var overlayActionOriginMs: Long? = null

    /** O of the current overlay (§3.11, §3.13); written by `OverlayController` (part 2). */
    var overlayOriginMs: Long? = null

    /** The first frame of the latest palette crossfade (§3.15); written by `GameTheme` (M4). */
    var paletteOriginMs: Long? = null

    /** The first frame of the latest `NavDisplay` transition (§3.17); written in M3 part 2. */
    var navOriginMs: Long? = null

    /** True once `BoardView` has composed a board of a ready game; written by `BoardView`. */
    var boardLoaded: Boolean = false

    /** Board tiles, including a previous epoch's outgoing ones (`outgoing = true`). */
    fun registerTiles(read: () -> List<ProbeTile>): ProbeRegistration = tiles.add(read)

    /** Board-level values of `BoardView`. */
    fun registerBoard(read: () -> ProbeBoard): ProbeRegistration = board.add(read)

    /** The live "+N" floats of the header (`ScoreFloats`). */
    fun registerFloats(read: () -> List<ProbeFloat>): ProbeRegistration = floats.add(read)

    /** The header's displayed values and BEST chip (`GameHeader`). */
    fun registerHeader(read: () -> ProbeHeader): ProbeRegistration = header.add(read)

    /** Ring and confetti (`BoardEffectsCanvas`, `ContentEffectsCanvas`; part 2). */
    fun registerEffects(read: () -> ProbeEffects): ProbeRegistration = effects.add(read)

    /** The win or game-over card (`OverlayController`; part 2). */
    fun registerOverlay(read: () -> ProbeOverlay?): ProbeRegistration = overlay.add(read)

    /** Palette crossfade fraction (M4) and the Undo icon rotation (`GameControls`). */
    fun registerChrome(read: () -> ProbeChrome): ProbeRegistration = chrome.add(read)

    /**
     * One "is anything of mine running" reader per owner: any registered `Animatable.isRunning`,
     * running transition or alive particle. [MotionSnapshot.isAnyRunning] is their OR.
     */
    fun registerRunning(read: () -> Boolean): ProbeRegistration = running.add(read)

    /** Reads every live reader now. */
    fun snapshot(): MotionSnapshot =
        MotionSnapshot(
            originMs = originMs,
            nudgeOriginMs = nudgeOriginMs,
            overlayActionOriginMs = overlayActionOriginMs,
            overlayOriginMs = overlayOriginMs,
            paletteOriginMs = paletteOriginMs,
            navOriginMs = navOriginMs,
            boardLoaded = boardLoaded,
            isAnyRunning = running.readAll().any { it },
            tiles = tiles.readAll().flatten(),
            board = board.readLatest(),
            floats = floats.readAll().flatten(),
            header = header.readLatest(),
            effects = effects.readLatest() ?: NoEffects,
            overlay = overlay.readLatest(),
            chrome = chrome.readLatest(),
        )

    private companion object {
        /** The neutral [MotionSnapshot.effects]: no ring, no confetti. */
        val NoEffects = ProbeEffects(ring = null, confettiAlive = 0)
    }
}

/**
 * The live readers of one kind, in registration order. Each registration is its own entry, so
 * registering the same function twice and unregistering one leaves the other live.
 */
private class Readers<T> {
    private class Entry<T>(val read: () -> T)

    private val entries = ArrayList<Entry<T>>()

    fun add(read: () -> T): ProbeRegistration {
        val entry = Entry(read)
        entries.add(entry)
        return ProbeRegistration { entries.remove(entry) }
    }

    /** Every live reader's value, in registration order. */
    fun readAll(): List<T> = entries.map { it.read() }

    /** The most recently registered live reader's value; `null` with none. */
    fun readLatest(): T? = entries.lastOrNull()?.read?.invoke()
}

/** Returned by the `register…` calls; call [unregister] from the registering `onDispose`. */
fun interface ProbeRegistration {
    fun unregister()
}

/** One drawn tile (MOTION_SPEC.md §8.2). [x] is the column and [y] the row, in cells. */
@Immutable
data class ProbeTile(
    val id: Long,
    val exp: Int,
    val kind: TileKind,
    /** True for a previous epoch's tile drawn by `OutgoingTilesLayer` (D-182). */
    val outgoing: Boolean,
    val x: Float,
    val y: Float,
    val scale: Float,
    val alpha: Float,
    val sheen: Float,
    val glowBoost: Float,
)

/** Board-level values (MOTION_SPEC.md §8.2). */
@Immutable
data class ProbeBoard(
    /** The board nudge offset in dp (x, y). */
    val nudgeDp: Offset,
    val outlinePulseAlpha: Float,
    /** 1 = full colour; 0.2 at the `OVER` end state. */
    val saturation: Float,
    /** The `preferredFrameRate` category `BoardView` currently applies. */
    val frameRate: FrameRateCategory,
)

/** One "+N" float (MOTION_SPEC.md §8.2). */
@Immutable
data class ProbeFloat(
    val text: String,
    val alpha: Float,
    val translationYDp: Float,
    val scale: Float,
)

/** The BEST chip's new-best state (MOTION_SPEC.md §8.2). */
@Immutable
data class ProbeBestChip(
    /** The label's string resource name: "best_label" or "new_best_label". */
    val labelKey: String,
    /** 0 = BEST colours, 1 = NEW BEST colours. */
    val containerFraction: Float,
    /** `null` while the star badge is not composed. */
    val starScale: Float?,
    val starRotationDeg: Float,
    val starAlpha: Float,
    val sparklesAlive: Int,
)

/** The header's displayed values (MOTION_SPEC.md §8.2). */
@Immutable
data class ProbeHeader(
    val displayedScore: Long,
    val displayedBest: Long,
    val bestChip: ProbeBestChip,
)

/** The win glow ring (MOTION_SPEC.md §3.11). */
@Immutable data class ProbeRing(val scale: Float, val strokeDp: Float, val alpha: Float)

/** Ring and confetti (MOTION_SPEC.md §8.2). */
@Immutable
data class ProbeEffects(
    /** `null` while no ring is drawn. */
    val ring: ProbeRing?,
    val confettiAlive: Int,
)

/** Which card is shown. */
enum class ProbeOverlayKind {
    WIN,
    OVER,
}

/** The win or game-over card (MOTION_SPEC.md §8.2). */
@Immutable
data class ProbeOverlay(
    val kind: ProbeOverlayKind,
    val scrimAlpha: Float,
    val cardAlpha: Float,
    val cardScale: Float,
    /** In layout order: the card's buttons, Share last. */
    val buttonAlphas: List<Float>,
    /** `OverlayController.interactive` (D-200); the buttons are always composed enabled. */
    val interactive: Boolean,
    val badgeMorph: Float,
    val badgeScale: Float,
    val badgeRotationDeg: Float,
)

/** Chrome values (MOTION_SPEC.md §8.2). */
@Immutable data class ProbeChrome(val paletteFraction: Float, val undoIconRotationDeg: Float)

/** What [MotionProbe.snapshot] returns (MOTION_SPEC.md §8.2). */
@Immutable
data class MotionSnapshot(
    val originMs: Long?,
    val nudgeOriginMs: Long?,
    val overlayActionOriginMs: Long?,
    val overlayOriginMs: Long?,
    val paletteOriginMs: Long?,
    val navOriginMs: Long?,
    val boardLoaded: Boolean,
    val isAnyRunning: Boolean,
    val tiles: List<ProbeTile>,
    /** `null` while no `BoardView` is composed. */
    val board: ProbeBoard?,
    val floats: List<ProbeFloat>,
    /** `null` while no `GameHeader` is composed. */
    val header: ProbeHeader?,
    val effects: ProbeEffects,
    val overlay: ProbeOverlay?,
    /**
     * `null` while no chrome reader is registered; `paletteFraction` is 1 when no crossfade runs.
     */
    val chrome: ProbeChrome?,
)
