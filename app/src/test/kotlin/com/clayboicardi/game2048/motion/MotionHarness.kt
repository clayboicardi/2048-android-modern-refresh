package com.clayboicardi.game2048.motion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.clayboicardi.game2048.App
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.board.BoardGeometry
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.LocalRecompositionCounter
import com.clayboicardi.game2048.game.board.RecompositionCounter
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.board.TileUi
import com.clayboicardi.game2048.game.board.boardGeometry
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.MotionSnapshot
import com.clayboicardi.game2048.game.motion.ProbeTile
import com.clayboicardi.game2048.game.motion.delayedSpec
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeHapticFeedback
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.ui.theme.ClassicLightSkin
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

/** The board node (`BoardView`): test tag `game:board`, `CollectionInfo(N, N)`. */
const val BOARD_TAG = "game:board"

/** The Score and Best row (`GameHeader`): the filmstrip header region. */
const val SCORES_TAG = "game:scores"

/** The 16 ms frame of the test clock (`TestMonotonicFrameClock.DefaultFrameDelay`). */
const val FRAME_MS = 16L

/**
 * The rule of one motion test class (MOTION_SPEC.md §8.1): the v2 compose rule (D-146) with
 * [effectContext] (a [TestDurationScale] for system-scale tests), the temporary folder of the
 * harness's DataStore files, and the cleanup of every harness launched in the test. It also knows
 * the test class, which names the filmstrip directory (MOTION_SPEC.md §8.4).
 */
class MotionRule(effectContext: CoroutineContext = EmptyCoroutineContext) : TestRule {
    val compose: ComposeContentTestRule = createComposeRule(effectContext)
    val folder = TemporaryFolder()

    /** The simple name of the running test class. */
    var testClass: String = "MotionTest"
        private set

    private val closers = ArrayList<() -> Unit>()

    internal fun onClose(close: () -> Unit) {
        closers += close
    }

    override fun apply(base: Statement, description: Description): Statement {
        testClass = description.testClass?.simpleName ?: description.className
        val withCleanup =
            object : Statement() {
                override fun evaluate() {
                    try {
                        base.evaluate()
                    } finally {
                        closers.asReversed().forEach { it() }
                        closers.clear()
                    }
                }
            }
        return folder.apply(compose.apply(withCleanup, description), description)
    }
}

/**
 * Compose's animation scale for a system-scale test (MOTION_SPEC.md §8.1): passed to [MotionRule]
 * as its effect context and set before the launch, together with the launch's `systemScale`, so
 * Compose's scale and the app's effective level agree.
 */
class TestDurationScale(initial: Float = 1f) : MotionDurationScale {
    override var scaleFactor: Float = initial
}

/** The capture region of an inspection filmstrip (MOTION_SPEC.md §8.4). */
enum class FilmRegion {
    /** The node with `CollectionInfo` ([BOARD_TAG]). */
    BOARD,

    /** The Score and Best row ([SCORES_TAG]). */
    HEADER,

    /** `onRoot()`, downscaled to 50 %. */
    ROOT,
}

/**
 * A test `RecompositionCounter` (D-150, MOTION_SPEC.md §7.2): counts per tile id, board, screen.
 */
class RecompositionCounts : RecompositionCounter {
    val tiles = HashMap<Long, Int>()

    var board = 0
        private set

    var screen = 0
        private set

    override fun onTile(id: Long) {
        tiles[id] = (tiles[id] ?: 0) + 1
    }

    override fun onBoard() {
        board++
    }

    override fun onScreen() {
        screen++
    }

    fun reset() {
        tiles.clear()
        board = 0
        screen = 0
    }

    /** Forgets the tile counts only; the board and screen counts go on. */
    fun clearTiles() {
        tiles.clear()
    }
}

/**
 * The expected value of an animated property at a play time (D-210): `spec.vectorize(converter)
 * .getValueFromNanos(playTimeNanos, initial, target, initialVelocity)`, the library's own
 * evaluation of the token spec. Play times are whole milliseconds on the 16 ms grid.
 */
object Curves {
    private const val NANOS_PER_MS = 1_000_000L

    fun value(
        spec: FiniteAnimationSpec<Float>,
        from: Float,
        to: Float,
        playMs: Long,
        velocity: Float = 0f,
    ): Float =
        spec
            .vectorize(Float.VectorConverter)
            .getValueFromNanos(
                playMs.coerceAtLeast(0) * NANOS_PER_MS,
                AnimationVector1D(from),
                AnimationVector1D(to),
                AnimationVector1D(velocity),
            )
            .value
}

/**
 * Drives the whole Game screen on the JVM with a manual clock (MOTION_SPEC.md §8.1): one
 * `AppContainer` over the test's temporary folder, the activity-scoped `GameViewModel` it builds,
 * and `App()` composed with the test's [MotionProbe], [FakeHapticFeedback] and
 * [RecompositionCounts]. `mainClock.autoAdvance` is false from the launch on; every frame is
 * advanced here.
 *
 * Harness plumbing (D-210), noted for the commit body:
 * - [launch] takes the class's [MotionRule] (the compose rule plus the temporary folder) instead of
 *   the bare compose rule;
 * - [recreate] runs the save, dispose and restore cycle of `StateRestorationTester` itself, one
 *   frame per step, because `emulateSavedInstanceStateRestore()` waits for idle between its steps
 *   and, with `autoAdvance = false`, no frame runs, so its children would never be disposed;
 * - the static render that AC-CONS-3 calls `presenter.reemitStatic()` is [staticRender]: a
 *   [recreate] after the settle bound, which composes the ViewModel's settled (`Restored`) board.
 */
class MotionHarness private constructor(private val owner: MotionRule, private val rig: Rig) {
    val rule: ComposeContentTestRule
        get() = owner.compose

    val probe: MotionProbe
        get() = rig.probe

    val haptics: FakeHapticFeedback
        get() = rig.haptics

    val vm: GameViewModel
        get() = rig.vm

    val container: AppContainer
        get() = rig.container

    val recompositions: RecompositionCounts
        get() = rig.counts

    /** The origin (F1) of the latest commit, in test-clock ms. */
    var originMs = 0L
        private set

    /** The `BoardUi` the latest board commit emitted, read before any frame: its animated form. */
    var committed: BoardUi? = null
        private set

    /** The probe on the latest commit's F0, the first frame after its action. */
    var f0: MotionSnapshot? = null
        private set

    /** When true, every commit also captures the full-size root bitmap of its F0 ([f0Root]). */
    var captureF0Root = false

    /** The root bitmap of the latest commit's F0, while [captureF0Root] is set. */
    var f0Root: Bitmap? = null
        private set

    /** The probe on the first frame that composed a loaded board after [launch]. */
    lateinit var firstFrame: MotionSnapshot
        private set

    /** The probe on R0, the first frame of the content [recreate] composed. */
    var r0: MotionSnapshot? = null
        private set

    private val strips = FilmstripRecorder(this)
    private var republishPending = false

    /** Applies the move synchronously (D-052), then advances to its origin F1 (§0.1). */
    fun commitMove(direction: Direction): Long = commit { vm.onMove(direction) }

    fun commitUndo(): Long = commit { vm.onUndo() }

    /** Starts the new game directly; the screen's confirmation dialog is not involved (§3.8). */
    fun commitNewGame(): Long = commit { vm.onNewGame() }

    /** Any board change in [action] (for example an undo and a move with no frame between). */
    fun commit(action: () -> Unit): Long = act(board = true, action) { probe.originMs }

    /**
     * Selects the [n]×[n] board (D-007). The ViewModel loads that size's game off the main thread
     * first, so this polls the main looper (no frame advances) until the `SizeSwitched` board is
     * emitted, then advances to its origin like a synchronous commit.
     */
    fun commitSizeSwitch(n: Int): Long {
        flushRepublish()
        val before = probe.originMs
        val previous = vm.board.value.moveId
        vm.onSizeSelected(BoardSize.of(n))
        rule.waitUntil(LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            vm.board.value.moveId != previous
        }
        committed = vm.board.value
        return awaitOrigin(before) { probe.originMs }
    }

    /** Invalid moves change no board, so the origin is the nudge's first animation frame. */
    fun invalidMove(direction: Direction): Long =
        act(board = false, { vm.onMove(direction) }) { probe.nudgeOriginMs }

    /**
     * [direction] whatever its outcome: an accepted move advances to its origin and returns true;
     * an invalid move advances to its nudge origin (at most 3 frames; Off publishes none) and a
     * blocked one advances one frame, and both return false.
     */
    fun tryMove(direction: Direction): Boolean {
        flushRepublish()
        val moveId = vm.board.value.moveId
        val nudgeId = vm.nudge.value?.id
        val origin = probe.originMs
        val nudgeOrigin = probe.nudgeOriginMs
        vm.onMove(direction)
        val accepted = vm.board.value.moveId != moveId
        when {
            accepted -> {
                committed = vm.board.value
                awaitOrigin(origin) { probe.originMs }
            }
            vm.nudge.value?.id != nudgeId -> awaitNudge(nudgeOrigin)
            else -> step()
        }
        return accepted
    }

    /**
     * Card buttons: the origin is Ft, OverlayController's play-time-0 frame after the tap (§3.12).
     */
    fun tapCardButton(label: String): Long =
        act(board = false, { rule.onNodeWithText(label).performClick() }) {
            probe.overlayActionOriginMs
        }

    /**
     * Runs [action] and advances exactly one frame (F0), for Off-level checks of F0 itself
     * (MOTION_SPEC.md §8.1) and invalid moves under Off, which publish no nudge origin.
     */
    fun actOneFrame(action: () -> Unit): MotionSnapshot {
        flushRepublish()
        action()
        step()
        return probe.snapshot().also { f0 = it }
    }

    /** Advances to the first 16 ms grid frame at or after origin + [tMs], one frame at a time. */
    fun advanceTo(tMs: Long) {
        val target = originMs + (tMs + FRAME_MS - 1) / FRAME_MS * FRAME_MS
        while (now() < target) step()
    }

    /** Advances [frames] frames. */
    fun advanceFrames(frames: Int) {
        repeat(frames) { step() }
    }

    /** Advances frame by frame until nothing runs, at most [maxMs]; returns the frames advanced. */
    fun settle(maxMs: Long = SETTLE_LIMIT_MS): Int {
        var frames = 0
        while (probe.snapshot().isAnyRunning) {
            check(frames * FRAME_MS < maxMs) { "still running after $maxMs ms" }
            step()
            frames++
        }
        return frames
    }

    /** The current frame in ms from the latest origin. */
    fun t(): Long = now() - originMs

    fun now(): Long = rule.mainClock.currentTime

    fun snapshot(): MotionSnapshot = probe.snapshot()

    /** The latest board's probe tile [id], drawn (not outgoing). */
    fun tile(id: Long): ProbeTile =
        checkNotNull(snapshot().tiles.firstOrNull { it.id == id && !it.outgoing }) {
            "tile $id is not drawn at t = ${t()}"
        }

    /** The current size's last saved game (`GameRepository.peek`). */
    fun peekGame(): SavedGame = runBlocking {
        checkNotNull(container.gameRepository.peek(vm.board.value.size)) { "no saved game" }
    }

    /** Every haptic since launch as `(type, ms from [from])`. */
    fun hapticsFrom(from: Long = originMs): List<Pair<HapticFeedbackType, Long>> =
        haptics.relativeTo(from)

    /**
     * The geometry of the composed board: `boardGeometry` (DESIGN_SYSTEM.md §4.2) at the board
     * node's measured side, Classic skin.
     */
    fun boardGeometry(): BoardGeometry {
        val node = rule.onNodeWithTag(BOARD_TAG).fetchSemanticsNode()
        val density = rule.density.density
        val n = vm.board.value.size.n
        return boardGeometry(n, (node.size.width / density).dp, ClassicLightSkin, density)
    }

    /**
     * Disposes the whole content and composes it again over the same vm, container, probe and
     * haptics fake, which is what `ActivityScenario.recreate()` does to the activity-scoped
     * `GameViewModel` (ARCHITECTURE.md §4.11, MOTION_SPEC.md §3.14). `rememberSaveable` state is
     * saved and restored through the content's own `SaveableStateRegistry`. One frame disposes, the
     * next composes; returns on R0, the first frame of the new content ([r0]). The new `BoardView`
     * publishes its first origin on R1; the next commit advances to it first.
     */
    fun recreate() {
        val host = rig.host
        host.saved = host.registry?.performSave().orEmpty()
        host.shown = false
        rule.mainClock.advanceTimeByFrame()
        host.generation++
        host.shown = true
        rule.mainClock.advanceTimeByFrame()
        r0 = probe.snapshot()
        republishPending = true
    }

    /**
     * The static render of the current state (AC-CONS-3's `presenter.reemitStatic()`, which main
     * code does not have): the full-size [region] after [recreate], once the new composition is
     * idle.
     */
    fun staticRender(region: FilmRegion = FilmRegion.ROOT): Bitmap {
        recreate()
        flushRepublish()
        settle()
        advanceFrames(2)
        return capture(region, downscale = false)
    }

    /** [region] of the current frame; [FilmRegion.ROOT] at 50 % unless [downscale] is false. */
    fun capture(region: FilmRegion, downscale: Boolean = true): Bitmap {
        val image =
            when (region) {
                FilmRegion.BOARD -> rule.onNodeWithTag(BOARD_TAG).captureToImage()
                FilmRegion.HEADER -> rule.onNodeWithTag(SCORES_TAG).captureToImage()
                FilmRegion.ROOT -> rule.onRoot().captureToImage()
            }
        val bitmap = image.asAndroidBitmap()
        return if (region == FilmRegion.ROOT && downscale) {
            Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
        } else {
            bitmap
        }
    }

    /**
     * Arms the inspection filmstrip [name] (MOTION_SPEC.md §8.4): every later frame captures
     * [region] on each of [framesMs], which are ms from the first origin published after this call
     * (or from the current origin when [fromCurrentOrigin]). [withF0] also captures the next
     * commit's F0. [writeFilmstrip] completes and writes it.
     */
    fun filmstrip(
        name: String,
        region: FilmRegion,
        framesMs: List<Long>,
        withF0: Boolean = false,
        fromCurrentOrigin: Boolean = false,
    ) {
        strips.arm(name, region, framesMs, withF0, if (fromCurrentOrigin) originMs else null)
    }

    /**
     * Advances to the armed strip's remaining frames, captures them and writes the strip to
     * `app/build/reports/filmstrips/<TestClass>/<name>.png`: frames left to right with 8 px gaps,
     * each labelled "t=NNN" (MOTION_SPEC.md §8.4). Returns the file.
     */
    fun writeFilmstrip(): File = strips.finish(owner.testClass)

    internal fun step() {
        rule.mainClock.advanceTimeByFrame()
        strips.onFrame()
    }

    private fun act(board: Boolean, action: () -> Unit, published: () -> Long?): Long {
        flushRepublish()
        val before = published()
        action()
        if (board) committed = vm.board.value
        return awaitOrigin(before, published)
    }

    /**
     * Advances one frame at a time (at most 3) until [published] reports a new origin equal to the
     * current frame time. Never edit expectations to fit a different origin.
     */
    private fun awaitOrigin(before: Long?, published: () -> Long?): Long {
        repeat(MAX_ORIGIN_FRAMES) { frame ->
            step()
            val origin = published()
            if (origin != null && origin != before) {
                check(origin == now()) { "origin $origin is not the current frame ${now()}" }
                originMs = origin
                strips.onOrigin(origin)
                return origin
            }
            if (frame == 0) {
                f0 = probe.snapshot()
                if (captureF0Root) f0Root = capture(FilmRegion.ROOT, downscale = false)
                strips.onF0()
            }
        }
        error("no origin published within $MAX_ORIGIN_FRAMES frames")
    }

    /** A nudge's first frame, or one frame when the level shows none (Off). */
    private fun awaitNudge(before: Long?) {
        repeat(MAX_ORIGIN_FRAMES) {
            step()
            val origin = probe.nudgeOriginMs
            if (origin != null && origin != before) {
                originMs = origin
                return
            }
        }
    }

    /** After [recreate], advances until the new `BoardView` has published (R1). */
    private fun flushRepublish() {
        if (!republishPending) return
        republishPending = false
        val before = probe.originMs
        var frames = 0
        while (probe.originMs == before && frames < MAX_ORIGIN_FRAMES) {
            step()
            frames++
        }
        probe.originMs?.let { originMs = it }
    }

    private fun finishLaunch() {
        var frames = 0
        while (!probe.boardLoaded) {
            check(frames < MAX_LOAD_FRAMES) { "no board loaded within $MAX_LOAD_FRAMES frames" }
            rule.mainClock.advanceTimeByFrame()
            frames++
        }
        firstFrame = probe.snapshot()
        while (probe.originMs == null && frames < MAX_LOAD_FRAMES) {
            rule.mainClock.advanceTimeByFrame()
            frames++
        }
        originMs = checkNotNull(probe.originMs) { "the loaded board published no origin" }
        settle()
    }

    companion object {
        /** A bound on the real-thread DataStore loads, not a timing under test. */
        private const val LOAD_TIMEOUT_MS = 10_000L
        private const val MAX_ORIGIN_FRAMES = 3
        private const val MAX_LOAD_FRAMES = 20
        private const val SETTLE_LIMIT_MS = 6_000L
        private const val SEED = 0x2048L

        /**
         * Builds the `AppContainer` over [rule]'s temporary folder with fakes (`MonotonicClock` =
         * the test clock, a fixed `SeedSource`, `FakeAnimatorScaleSource(systemScale)`), writes
         * [fixture] and the settings (Classic light, [motion], `onboardingDone`, the current
         * `versionCode`), loads the `GameViewModel`, composes `App()` with the probe, the haptics
         * fake and the counter provided, sets `mainClock.autoAdvance = false` and advances until
         * the board is loaded, its origin published and nothing runs. [widthDp] sets the window
         * width qualifier before composing.
         */
        fun launch(
            rule: MotionRule,
            fixture: GameFixture,
            motion: MotionPreference,
            systemScale: Float = 1f,
            widthDp: Int? = null,
        ): MotionHarness {
            widthDp?.let { RuntimeEnvironment.setQualifiers("+w${it}dp") }
            val compose = rule.compose
            val scope = CoroutineScope(SupervisorJob())
            val dir = rule.folder.newFolder()
            val container =
                AppContainer(
                    files = { name -> File(dir, name) },
                    versionCode = TEST_VERSION_CODE,
                    applicationScope = scope,
                    clock = MonotonicClock { compose.mainClock.currentTime },
                    seedSource = FixedSeedSource(SEED),
                    animatorScale = FakeAnimatorScaleSource(systemScale),
                )
            runBlocking { seed(container, fixture, motion) }
            val store = ViewModelStore()
            rule.onClose {
                store.clear()
                scope.cancel()
            }
            val vm =
                ViewModelProvider(store, container.gameViewModelFactory())[
                    GameViewModel::class.java]
            // The load reads DataStore on real I/O threads and resumes on the main looper.
            compose.waitUntil(LOAD_TIMEOUT_MS) {
                ShadowLooper.idleMainLooper()
                vm.ready.value
            }
            val rig =
                Rig(
                    probe = MotionProbe(),
                    haptics = FakeHapticFeedback { compose.mainClock.currentTime },
                    vm = vm,
                    container = container,
                    counts = RecompositionCounts(),
                    host = RecreationHost(),
                )
            compose.mainClock.autoAdvance = false
            compose.setContent { rig.Content() }
            return MotionHarness(rule, rig).also { it.finishLaunch() }
        }

        private suspend fun seed(
            container: AppContainer,
            fixture: GameFixture,
            motion: MotionPreference,
        ) {
            container.seedSettings(
                Settings(
                    themeMode = ThemeMode.LIGHT,
                    motion = motion,
                    haptics = fixture.haptics,
                    lastBoardSize = fixture.game.state.size.n,
                    lastSeenVersionCode = TEST_VERSION_CODE,
                    onboardingDone = true,
                ),
            )
            container.seedRecords(fixture.records)
            container.seedGame(fixture.game)
            for (game in fixture.saved) container.seedGame(game)
        }
    }
}

/** What one harness composes and reads. */
internal data class Rig(
    val probe: MotionProbe,
    val haptics: FakeHapticFeedback,
    val vm: GameViewModel,
    val container: AppContainer,
    val counts: RecompositionCounts,
    val host: RecreationHost,
) {
    @Composable
    fun Content() {
        host.Content {
            CompositionLocalProvider(
                LocalMotionProbe provides probe,
                LocalHapticFeedback provides haptics,
                LocalRecompositionCounter provides counts,
            ) {
                App(
                    gameViewModel = vm,
                    settings = container.settingsRepository.state,
                    animatorScale = container.animatorScale,
                    clock = container.clock,
                )
            }
        }
    }
}

/**
 * The content host of [MotionHarness.recreate]: removing and re-adding the content at the same call
 * site keeps its `rememberSaveable` keys, and the [SaveableStateRegistry] made for each generation
 * restores what the previous one saved.
 */
internal class RecreationHost {
    var shown by mutableStateOf(true)
    var generation by mutableIntStateOf(0)

    /** What the last generation saved; read when the next generation's registry is made. */
    var saved: Map<String, List<Any?>> = emptyMap()

    /** The current generation's registry; written after composition. */
    var registry: SaveableStateRegistry? = null

    @Composable
    fun Content(content: @Composable () -> Unit) {
        val parent = LocalSaveableStateRegistry.current
        val current = generation
        val registry =
            remember(current) { SaveableStateRegistry(saved) { parent?.canBeSaved(it) ?: true } }
        SideEffect { this.registry = registry }
        if (shown) {
            CompositionLocalProvider(
                LocalSaveableStateRegistry provides registry,
                content = content,
            )
        }
    }
}

/** Captures the armed inspection filmstrip frame by frame and writes it (MOTION_SPEC.md §8.4). */
internal class FilmstripRecorder(private val harness: MotionHarness) {
    private class Strip(
        val name: String,
        val region: FilmRegion,
        val pending: ArrayDeque<Long>,
        var wantsF0: Boolean,
        var base: Long?,
    ) {
        val frames = ArrayList<Pair<String, Bitmap>>()
    }

    private var strip: Strip? = null

    fun arm(name: String, region: FilmRegion, framesMs: List<Long>, withF0: Boolean, base: Long?) {
        check(strip == null) { "filmstrip ${strip?.name} is still armed" }
        strip = Strip(name, region, ArrayDeque(framesMs.sorted()), withF0, base)
        if (base != null) onFrame()
    }

    fun onF0() {
        val s = strip ?: return
        if (s.wantsF0 && s.base == null) {
            s.frames += "F0" to harness.capture(s.region)
            s.wantsF0 = false
        }
    }

    fun onOrigin(origin: Long) {
        val s = strip ?: return
        if (s.base == null) {
            s.base = origin
            onFrame()
        }
    }

    fun onFrame() {
        val s = strip ?: return
        val base = s.base ?: return
        while (s.pending.isNotEmpty() && base + s.pending.first() <= harness.now()) {
            val t = s.pending.removeFirst()
            check(base + t == harness.now()) { "filmstrip ${s.name}: frame t = $t was skipped" }
            s.frames += "t=${t.toString().padStart(3, '0')}" to harness.capture(s.region)
        }
    }

    fun finish(testClass: String): File {
        val s = checkNotNull(strip) { "no filmstrip armed" }
        val base = checkNotNull(s.base) { "filmstrip ${s.name}: no origin was published" }
        check(s.pending.all { base + it >= harness.now() }) {
            "filmstrip ${s.name}: frames skipped"
        }
        onFrame()
        while (s.pending.isNotEmpty()) harness.step()
        strip = null
        val dir = File("build/reports/filmstrips/$testClass").apply { mkdirs() }
        val file = File(dir, "${s.name}.png")
        FileOutputStream(file).use { out ->
            compose(s.frames).compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        }
        return file
    }

    private fun compose(frames: List<Pair<String, Bitmap>>): Bitmap {
        val width = frames.sumOf { it.second.width } + GAP_PX * (frames.size - 1).coerceAtLeast(0)
        val height = LABEL_PX + frames.maxOf { it.second.height }
        val out = Bitmap.createBitmap(width.coerceAtLeast(1), height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = LABEL_TEXT_PX
            }
        var x = 0
        for ((label, bitmap) in frames) {
            canvas.drawText(label, x + LABEL_INSET_PX, LABEL_BASELINE_PX, paint)
            canvas.drawBitmap(bitmap, x.toFloat(), LABEL_PX.toFloat(), null)
            x += bitmap.width + GAP_PX
        }
        return out
    }

    private companion object {
        const val GAP_PX = 8
        const val LABEL_PX = 32
        const val LABEL_TEXT_PX = 22f
        const val LABEL_INSET_PX = 4f
        const val LABEL_BASELINE_PX = 24f
        const val PNG_QUALITY = 100
    }
}

/** MOTION_SPEC.md §9's tolerances. */
object Tol {
    const val POSITION = 0.004f
    const val SCALE = 0.002f
    const val ALPHA = 0.01f
    const val DP = 0.05f
    const val FRACTION = 0.01f
}

/**
 * The pop impulse MOTION_SPEC.md §3.3 gives a merged tile of exponent [exp] (D-183): the tier of
 * its own value (≤ 64 → 6, 128–512 → 8, 1024 → 10, ≥ 2048 → 12 s⁻¹), capped by the neighbour rule
 * `(2 · gapPx / tilePx) / POP_PEAK_PER_VELOCITY` of [geometry].
 */
fun expectedPopImpulse(exp: Int, geometry: BoardGeometry): Float {
    val tier =
        when {
            exp <= EXP_64 -> 0
            exp <= EXP_512 -> 1
            exp == EXP_1024 -> 2
            else -> 3
        }
    val cap = 2f * geometry.gapPx / geometry.tilePx / GameMotion.Move.POP_PEAK_PER_VELOCITY
    return minOf(GameMotion.POP_IMPULSE[tier], cap)
}

/** The merged tile's scale at [tMs] from the origin under Full (MOTION_SPEC.md §3.1, §3.3). */
fun expectedPopScale(tMs: Long, impulse: Float): Float =
    Curves.value(
        delayedSpec(GameMotion.popSpatial(GameMotion.SCALE_THRESHOLD), GameMotion.MERGE_REVEAL_MS),
        1f,
        1f,
        tMs,
        impulse,
    )

private const val EXP_64 = 6
private const val EXP_512 = 9
private const val EXP_1024 = 10

/** FIX_FILM LEFT's tiles by role (MOTION_SPEC.md §8.3), read from the committed `BoardUi`. */
class FilmLeft(board: BoardUi) {
    /** The 4 that slides from column 3 to column 1. */
    val moved: TileUi = board.tiles.single { it.kind == TileKind.Moved }

    /** The merge sources, from column 0 and from column 1. */
    val ghosts: List<TileUi> =
        board.tiles.filter { it.kind == TileKind.Ghost }.sortedBy { it.fromCol }

    /** The merged 4 at row 0, column 0. */
    val merged: TileUi = board.tiles.single { it.kind == TileKind.Merged }

    /** The 2 spawned at row 1, column 0. */
    val spawn: TileUi = board.tiles.single { it.kind == TileKind.Spawned }
}

/**
 * The visual-consistency invariants of MOTION_SPEC.md §4.3 on one probe frame. Each returns the
 * violations it finds (empty when the invariant holds). Positions are cells; [tileFraction] is
 * `tilePx / (tilePx + gapPx)`, a tile's side in cells.
 */
object Invariants {
    /** The alpha from which a tile counts as drawn for I1. */
    private const val DRAWN_ALPHA = 0.05f

    /**
     * I1: drawn tiles (alpha ≥ 0.05) have disjoint rectangles: Moved, Static, Ghost and Merged
     * unscaled, Spawned scaled about the cell centre; a merged tile and its ghosts (one merge
     * group, the same target cell) may overlap, and so may a merged tile and a Moved or Ghost tile
     * of the same change that is still sliding towards its own cell (D-225: the reveal at +80
     * precedes the slide's end; the merged tile is drawn above it, I7).
     */
    fun overlaps(tiles: List<ProbeTile>, board: BoardUi, tileFraction: Float): List<String> {
        val drawn = tiles.filter { !it.outgoing && it.alpha >= DRAWN_ALPHA }
        val targets = board.tiles.associateBy { it.id }
        val found = ArrayList<String>()
        for (i in drawn.indices) {
            for (j in i + 1 until drawn.size) {
                val a = drawn[i]
                val b = drawn[j]
                val allowed = sameMergeGroup(a, b, targets) || mergedOverSlider(a, b, targets)
                if (allowed || !overlap(a, b, tileFraction)) continue
                found +=
                    "tiles ${a.id} (${a.kind} ${a.x},${a.y}) and ${b.id} (${b.kind} ${b.x},${b.y})"
            }
        }
        return found
    }

    /**
     * I2: along [direction]'s axis, the sliding tiles of one line (Moved and Ghost, which share one
     * spring and one start frame) keep the order of their targets. Merged and Spawned tiles do not
     * slide: they appear in their own cell.
     */
    fun orderViolations(
        tiles: List<ProbeTile>,
        board: BoardUi,
        direction: Direction,
    ): List<String> {
        val horizontal = direction == Direction.LEFT || direction == Direction.RIGHT
        val targets = board.tiles.associateBy { it.id }
        val slides = setOf(TileKind.Moved, TileKind.Ghost)
        val sliding = tiles.filter { !it.outgoing && it.kind in slides && it.id in targets }
        val found = ArrayList<String>()
        for (a in sliding) {
            for (b in sliding) {
                val ta = checkNotNull(targets[a.id])
                val tb = checkNotNull(targets[b.id])
                val sameLine = if (horizontal) ta.row == tb.row else ta.col == tb.col
                val before = if (horizontal) ta.col < tb.col else ta.row < tb.row
                val ca = if (horizontal) a.x else a.y
                val cb = if (horizontal) b.x else b.y
                if (sameLine && before && ca > cb + Tol.POSITION) {
                    found += "tile ${a.id} at $ca passed tile ${b.id} at $cb"
                }
            }
        }
        return found
    }

    private fun sameMergeGroup(a: ProbeTile, b: ProbeTile, targets: Map<Long, TileUi>): Boolean {
        val group = setOf(TileKind.Merged, TileKind.Ghost)
        val ta = targets[a.id]
        val tb = targets[b.id]
        return a.kind in group &&
            b.kind in group &&
            ta != null &&
            tb != null &&
            ta.row == tb.row &&
            ta.col == tb.col
    }

    /** One of [a] and [b] is Merged and the other a Moved or Ghost tile not yet at its cell. */
    private fun mergedOverSlider(
        a: ProbeTile,
        b: ProbeTile,
        targets: Map<Long, TileUi>,
    ): Boolean {
        val slider =
            when (TileKind.Merged) {
                a.kind -> b
                b.kind -> a
                else -> return false
            }
        val target = targets[slider.id]
        return (slider.kind == TileKind.Moved || slider.kind == TileKind.Ghost) &&
            target != null &&
            (abs(slider.x - target.col) > Tol.POSITION || abs(slider.y - target.row) > Tol.POSITION)
    }

    private fun overlap(a: ProbeTile, b: ProbeTile, side: Float): Boolean {
        val ha = side / 2 * (if (a.kind == TileKind.Spawned) a.scale else 1f)
        val hb = side / 2 * (if (b.kind == TileKind.Spawned) b.scale else 1f)
        val dx = abs((a.x + side / 2) - (b.x + side / 2))
        val dy = abs((a.y + side / 2) - (b.y + side / 2))
        val reach = ha + hb - Tol.POSITION
        return dx < reach && dy < reach
    }
}

/**
 * Runs [commit] and checks its F0 (MOTION_SPEC.md §2.3 T5, AC-OFF-1): nothing runs there or on the
 * next 3 frames, and the full-size root bitmap of F0 equals the static render of the resulting
 * state.
 */
fun MotionHarness.assertEndStateOnF0(event: String, commit: () -> Unit) {
    captureF0Root = true
    commit()
    captureF0Root = false
    val f0 = checkNotNull(f0Root)
    assertWithMessage("$event F0: running").that(checkNotNull(this.f0).isAnyRunning).isFalse()
    repeat(END_STATE_CHECK_FRAMES) {
        assertWithMessage("$event t = ${t()}: running").that(snapshot().isAnyRunning).isFalse()
        advanceFrames(1)
    }
    assertWithMessage("$event F0: the root bitmap equals the static render")
        .that(f0.sameAs(staticRender()))
        .isTrue()
}

private const val END_STATE_CHECK_FRAMES = 3
