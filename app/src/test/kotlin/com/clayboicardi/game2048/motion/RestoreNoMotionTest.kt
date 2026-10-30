package com.clayboicardi.game2048.motion

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.PaletteId
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.OverlayEntry
import com.clayboicardi.game2048.game.OverlayUi
import com.clayboicardi.game2048.game.board.BoardPresenter
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.motion.MotionProbe
import com.clayboicardi.game2048.game.motion.MotionSnapshot
import com.clayboicardi.game2048.game.motion.ProbeOverlayKind
import com.clayboicardi.game2048.game.motion.delayedSpec
import com.clayboicardi.game2048.testing.FakeAnimatorScaleSource
import com.clayboicardi.game2048.testing.FakeHapticFeedback
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.TEST_VERSION_CODE
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.testing.testAppViewModel
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Restore, recreation and the one-shot presentations (MOTION_SPEC.md §3.14, §9.9; D-047,
 * ARCHITECTURE.md §4.11): a restored board is static on its first frame, and the presentation
 * reports of a live move change the ViewModel's values without touching what the composition shows.
 * A cold start whose settings and game load land in one frame shows the saved appearance without a
 * crossfade (D-251, D-255).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class RestoreNoMotionTest {
    @get:Rule val rule = MotionRule()

    /** AC-RESTORE-1 */
    @Test
    fun restoredGameIsStaticOnItsFirstFrame() {
        assertRestoredStatic(GoldenFixtures.FIX_MID_4, scale = 1f, saturation = 1f)
    }

    /** AC-RESTORE-1 */
    @Test
    fun restoredWonBoardIsStaticOnItsFirstFrame() {
        // The board case; the win card's end state is restoredWinCardIsInItsEndState's.
        assertRestoredStatic(GoldenFixtures.FIX_WON_4, scale = 1f, saturation = 1f)
    }

    /** AC-RESTORE-1 */
    @Test
    fun restoredOverBoardIsDeflatedAndDesaturated() {
        assertRestoredStatic(
            GoldenFixtures.FIX_OVER_4,
            scale = GameMotion.GameOver.DEFLATE_SCALE,
            saturation = GameMotion.GameOver.SATURATION_END,
        )
    }

    /** AC-RESTORE-2 */
    @Test
    fun reportsDoNotDisturbLivePresentation() {
        MotionFixtures.FILM_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(GoldenFixtures.FIX_FILM), FULL)
        h.recompositions.reset()

        h.commitMove(Direction.LEFT)

        val move = checkNotNull(h.committed)
        assertWithMessage("t = 0: vm.board is the settled board")
            .that(h.vm.board.value)
            .isEqualTo(BoardPresenter.settled(move))
        assertWithMessage("t = 0: vm.header.lastDelta")
            .that(h.vm.header.value.lastDelta)
            .isEqualTo(0)
        assertWithMessage("t = 0: the probe still shows the move's kinds")
            .that(h.snapshot().tiles.map { it.kind }.toSet())
            .containsExactly(TileKind.Moved, TileKind.Ghost, TileKind.Merged, TileKind.Spawned)
        val film = FilmLeft(move)
        assertMoveOrigin(h, move, film)
        assertWithMessage("F0: tiles composed more than once")
            .that(h.recompositions.tiles.filterValues { it > 1 })
            .isEmpty()
        h.recompositions.clearTiles()

        while (h.t() < BUDGET_END_MS) {
            h.advanceFrames(1)
            assertMoveFrame(h, film)
            if (h.t() == TILE_BUDGET_END_MS) {
                assertWithMessage("F1..t = 432: tile recompositions")
                    .that(h.recompositions.tiles)
                    .isEmpty()
            }
        }
        assertWithMessage("F0..t = 448: BoardView recompositions")
            .that(h.recompositions.board)
            .isAtMost(2)
        assertWithMessage("F0..t = 448: GameScreen recompositions")
            .that(h.recompositions.screen)
            .isEqualTo(0)
        assertWithMessage("haptics")
            .that(h.hapticsFrom())
            .containsExactly(HapticFeedbackType.SegmentTick to REVEAL_MS)
    }

    /** AC-RESTORE-1 */
    @Test
    fun restoredWinCardIsInItsEndState() {
        assertRestoredCard(
            GoldenFixtures.FIX_WON_4,
            ProbeOverlayKind.WIN,
            GameMotion.Win.SCRIM_ALPHA,
        )
    }

    /** AC-RESTORE-1 */
    @Test
    fun restoredGameOverCardIsInItsEndState() {
        assertRestoredCard(
            GoldenFixtures.FIX_OVER_4,
            ProbeOverlayKind.OVER,
            GameMotion.GameOver.SCRIM_ALPHA,
        )
    }

    /** AC-RESTORE-2 */
    @Test
    fun reportsDoNotDisturbLiveWinCard() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        val h = MotionHarness.launch(rule, GameFixture(MotionFixtures.FIX_WIN_PRE_4), FULL)

        h.commitMove(Direction.LEFT)

        val overlay = h.vm.overlay.value
        assertWithMessage("t = 0: vm.overlay").that(overlay).isInstanceOf(OverlayUi.Win::class.java)
        assertWithMessage("t = 0: vm.overlay's entry")
            .that((overlay as OverlayUi.Win).entry)
            .isEqualTo(OverlayEntry.Restored)
        h.assertWinCardEntry()
        h.assertWinButtonsGate()
    }

    /** AC-RESTORE-1 */
    @Test
    fun savedAppearanceIsNotCrossfadedWhenLoadsLandInOneFrame() {
        // A cold start (MOTION_SPEC.md §3.14; D-251, D-255): the first composition shows the
        // defaults (Classic) before DataStore has read anything; the settings (Neon, Full) and the
        // game then become ready before the next frame. That frame shows Neon at once: no
        // crossfade starts and nothing runs. A later palette change crossfades (the gate opened).
        val compose = rule.compose
        val dir = rule.folder.newFolder()
        seedNeonAndDie(dir)
        val gate = CountDownLatch(1)
        val rig = heldColdStart(dir, gate)
        compose.mainClock.autoAdvance = false
        compose.setContent { rig.Content() }
        compose.mainClock.advanceTimeByFrame()
        assertWithMessage("before the loads: settings loaded")
            .that(rig.container.settingsRepository.loaded.value)
            .isFalse()
        assertWithMessage("before the loads: palette")
            .that(rig.appVm.appearance.value.palette)
            .isEqualTo(PaletteId.CLASSIC)

        gate.countDown()
        // No frame runs while the loads finish (autoAdvance is off): both land in one frame.
        compose.waitUntil(LOAD_TIMEOUT_MS) {
            ShadowLooper.idleMainLooper()
            rig.vm.ready.value
        }
        Snapshot.sendApplyNotifications()
        assertWithMessage("loaded: palette")
            .that(rig.appVm.appearance.value.palette)
            .isEqualTo(PaletteId.NEON)
        repeat(SETTLE_FRAMES) { frame ->
            compose.mainClock.advanceTimeByFrame()
            assertNoCrossfade(rig.probe, "frame $frame")
        }
        assertWithMessage("board loaded").that(rig.probe.boardLoaded).isTrue()

        rig.container.settingsRepository.update { it.copy(palette = PaletteId.CLASSIC) }
        ShadowLooper.idleMainLooper()
        Snapshot.sendApplyNotifications()
        repeat(MAX_ORIGIN_FRAMES) {
            if (rig.probe.paletteOriginMs == null) compose.mainClock.advanceTimeByFrame()
        }
        assertWithMessage("a later palette change: crossfade origin")
            .that(rig.probe.paletteOriginMs)
            .isNotNull()
        // The crossfade ends before the content is disposed.
        repeat(SETTLE_FRAMES) { compose.mainClock.advanceTimeByFrame() }
    }

    /** An earlier process wrote Neon (light, Full) and `FIX_MID_4` into [dir], then died. */
    private fun seedNeonAndDie(dir: File) {
        val scope = CoroutineScope(SupervisorJob())
        runBlocking {
            val dead =
                AppContainer(
                    files = { name -> File(dir, name) },
                    versionCode = TEST_VERSION_CODE,
                    applicationScope = scope,
                    animatorScale = FakeAnimatorScaleSource(),
                )
            dead.seedSettings(
                Settings(
                    themeMode = ThemeMode.LIGHT,
                    palette = PaletteId.NEON,
                    motion = FULL,
                    lastBoardSize = GoldenFixtures.FIX_MID_4.state.size.n,
                    lastSeenVersionCode = TEST_VERSION_CODE,
                    onboardingDone = true,
                ),
            )
            dead.seedGame(GoldenFixtures.FIX_MID_4)
            checkNotNull(scope.coroutineContext[Job]).cancelAndJoin()
        }
    }

    /**
     * The next process over [dir]: its container's one I/O thread waits for [gate], so nothing is
     * read until the test opens it; the ViewModels exist, nothing is composed yet.
     */
    private fun heldColdStart(dir: File, gate: CountDownLatch): Rig {
        val compose = rule.compose
        val executor = Executors.newSingleThreadExecutor()
        executor.execute { gate.await() }
        val scope = CoroutineScope(SupervisorJob())
        val container =
            AppContainer(
                files = { name -> File(dir, name) },
                versionCode = TEST_VERSION_CODE,
                applicationScope = scope,
                ioDispatcher = executor.asCoroutineDispatcher(),
                clock = MonotonicClock { compose.mainClock.currentTime },
                seedSource = FixedSeedSource(SEED),
                animatorScale = FakeAnimatorScaleSource(),
            )
        val store = ViewModelStore()
        rule.onClose {
            gate.countDown()
            store.clear()
            scope.cancel()
            executor.shutdownNow()
        }
        return Rig(
            probe = MotionProbe(),
            haptics = FakeHapticFeedback { compose.mainClock.currentTime },
            vm =
                ViewModelProvider(store, container.gameViewModelFactory())[
                    GameViewModel::class.java],
            appVm = testAppViewModel(container, store),
            container = container,
            counts = RecompositionCounts(),
            host = RecreationHost(),
        )
    }

    /** On this frame the palette fraction is 1, no crossfade has started and nothing runs. */
    private fun assertNoCrossfade(probe: MotionProbe, label: String) {
        val snapshot = probe.snapshot()
        val chrome = checkNotNull(snapshot.chrome) { "$label: no chrome readers" }
        assertWithMessage("$label: palette fraction").that(chrome.paletteFraction).isEqualTo(1f)
        assertWithMessage("$label: crossfade origin").that(probe.paletteOriginMs).isNull()
        assertWithMessage("$label: running").that(snapshot.isAnyRunning).isFalse()
    }

    /**
     * AC-RESTORE-1's card case: on the first frame and on R0 after a recreation, [kind]'s card is
     * in its end state (MOTION_SPEC.md §3.11–§3.14) with interactive buttons, no ring, no confetti,
     * no haptic and nothing running.
     */
    private fun assertRestoredCard(game: SavedGame, kind: ProbeOverlayKind, scrim: Float) {
        val h = MotionHarness.launch(rule, GameFixture(game), FULL)
        assertCardEnd(h.firstFrame, "first frame", kind, scrim)
        assertWithMessage("first frame: haptics").that(h.haptics.events).isEmpty()

        h.recreate()

        assertCardEnd(checkNotNull(h.r0), "R0", kind, scrim)
        assertWithMessage("R0: haptics").that(h.haptics.events).isEmpty()
    }

    private fun assertCardEnd(
        frame: MotionSnapshot,
        label: String,
        kind: ProbeOverlayKind,
        scrim: Float,
    ) {
        val card = checkNotNull(frame.overlay) { "$label: no card" }
        assertWithMessage("$label: overlay kind").that(card.kind).isEqualTo(kind)
        assertWithMessage("$label: interactive").that(card.interactive).isTrue()
        assertWithMessage("$label: card alpha").that(card.cardAlpha).isWithin(Tol.ALPHA).of(1f)
        assertWithMessage("$label: card scale").that(card.cardScale).isWithin(Tol.SCALE).of(1f)
        assertWithMessage("$label: scrim").that(card.scrimAlpha).isWithin(Tol.ALPHA).of(scrim)
        for ((i, alpha) in card.buttonAlphas.withIndex()) {
            assertWithMessage("$label: button $i alpha").that(alpha).isWithin(Tol.ALPHA).of(1f)
        }
        if (kind == ProbeOverlayKind.WIN) {
            assertWithMessage("$label: badge morph").that(card.badgeMorph).isEqualTo(1f)
            assertWithMessage("$label: badge rotation")
                .that(card.badgeRotationDeg)
                .isEqualTo(GameMotion.Win.BADGE_ROTATION_DEG)
        }
        assertWithMessage("$label: ring").that(frame.effects.ring).isNull()
        assertWithMessage("$label: confetti").that(frame.effects.confettiAlive).isEqualTo(0)
        assertWithMessage("$label: running").that(frame.isAnyRunning).isFalse()
    }

    private fun assertRestoredStatic(game: SavedGame, scale: Float, saturation: Float) {
        val h = MotionHarness.launch(rule, GameFixture(game), FULL)
        val board = h.vm.board.value
        assertStaticEnd(h.firstFrame, "first frame", board, scale, saturation)
        assertWithMessage("first frame: haptics").that(h.haptics.events).isEmpty()

        h.recreate()

        assertStaticEnd(checkNotNull(h.r0), "R0", board, scale, saturation)
        assertWithMessage("R0: haptics").that(h.haptics.events).isEmpty()
    }

    private fun assertStaticEnd(
        frame: MotionSnapshot,
        label: String,
        board: BoardUi,
        scale: Float,
        saturation: Float,
    ) {
        assertWithMessage("$label: tiles")
            .that(frame.tiles.map { it.id })
            .containsExactlyElementsIn(board.tiles.map { it.id })
        val cells = board.tiles.associateBy { it.id }
        for (tile in frame.tiles) {
            val cell = checkNotNull(cells[tile.id])
            assertWithMessage("$label: tile ${tile.id} kind")
                .that(tile.kind)
                .isEqualTo(TileKind.Static)
            assertWithMessage("$label: tile ${tile.id} outgoing").that(tile.outgoing).isFalse()
            assertWithMessage("$label: tile ${tile.id} cell")
                .that(tile.x to tile.y)
                .isEqualTo(cell.col.toFloat() to cell.row.toFloat())
            assertWithMessage("$label: tile ${tile.id} scale")
                .that(tile.scale)
                .isWithin(Tol.SCALE)
                .of(scale)
            assertWithMessage("$label: tile ${tile.id} alpha").that(tile.alpha).isEqualTo(1f)
        }
        assertWithMessage("$label: floats").that(frame.floats).isEmpty()
        assertWithMessage("$label: running").that(frame.isAnyRunning).isFalse()
        assertWithMessage("$label: saturation")
            .that(checkNotNull(frame.board).saturation)
            .isWithin(Tol.FRACTION)
            .of(saturation)
    }

    /** AC-MOVE-1 at the origin. */
    private fun assertMoveOrigin(h: MotionHarness, move: BoardUi, film: FilmLeft) {
        for (tile in move.tiles) {
            assertWithMessage("t = 0: tile ${tile.id} at from")
                .that(h.tile(tile.id).x to h.tile(tile.id).y)
                .isEqualTo(tile.fromCol.toFloat() to tile.fromRow.toFloat())
        }
        assertWithMessage("t = 0: merged alpha").that(h.tile(film.merged.id).alpha).isEqualTo(0f)
        assertWithMessage("t = 0: spawn alpha").that(h.tile(film.spawn.id).alpha).isEqualTo(0f)
        assertWithMessage("t = 0: spawn scale")
            .that(h.tile(film.spawn.id).scale)
            .isWithin(Tol.SCALE)
            .of(0.6f)
    }

    /** AC-MOVE-2 to AC-MOVE-5 and the "+4" float, on the frames they name. */
    private fun assertMoveFrame(h: MotionHarness, film: FilmLeft) {
        val t = h.t()
        when (t) {
            16L,
            48L -> assertMovedX(h, film, t)
            64L ->
                assertWithMessage("t = 64: merged alpha")
                    .that(h.tile(film.merged.id).alpha)
                    .isEqualTo(0f)
            REVEAL_MS -> {
                assertMovedX(h, film, t)
                assertWithMessage("t = 80: merged alpha")
                    .that(h.tile(film.merged.id).alpha)
                    .isEqualTo(1f)
                assertWithMessage("t = 80: spawn alpha")
                    .that(h.tile(film.spawn.id).alpha)
                    .isEqualTo(0f)
                assertWithMessage("t = 80: the +4 float")
                    .that(h.snapshot().floats.map { it.text })
                    .containsExactly("+4")
            }
            96L,
            112L,
            128L -> {
                val impulse = expectedPopImpulse(film.merged.exp, h.boardGeometry())
                assertWithMessage("t = $t: merged scale")
                    .that(h.tile(film.merged.id).scale)
                    .isWithin(Tol.SCALE)
                    .of(expectedPopScale(t, impulse))
                if (t == SPAWN_FRAME_MS) assertSpawnStart(h, film)
            }
            192L,
            208L -> {
                val alpha = if (t < GameMotion.GHOST_REMOVE_MS) 1f else 0f
                for (ghost in film.ghosts) {
                    assertWithMessage("t = $t: ghost alpha")
                        .that(h.tile(ghost.id).alpha)
                        .isEqualTo(alpha)
                }
            }
        }
    }

    /** AC-MOVE-4 at t = 96: 6 ms into the spawn (D = 90). */
    private fun assertSpawnStart(h: MotionHarness, film: FilmLeft) {
        val spawn = h.tile(film.spawn.id)
        val delay = GameMotion.SPAWN_DELAY_MS
        val alpha = delayedSpec(GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD), delay)
        val scale = delayedSpec(GameMotion.popSpatial(GameMotion.SCALE_THRESHOLD), delay)
        assertWithMessage("t = 96: spawn alpha")
            .that(spawn.alpha)
            .isWithin(Tol.ALPHA)
            .of(Curves.value(alpha, 0f, 1f, SPAWN_FRAME_MS))
        assertWithMessage("t = 96: spawn scale")
            .that(spawn.scale)
            .isWithin(Tol.SCALE)
            .of(Curves.value(scale, 0.6f, 1f, SPAWN_FRAME_MS))
    }

    private fun assertMovedX(h: MotionHarness, film: FilmLeft, t: Long) {
        assertWithMessage("t = $t: the 4's x")
            .that(h.tile(film.moved.id).x)
            .isWithin(Tol.POSITION)
            .of(Curves.value(GameMotion.slide(), film.moved.fromCol.toFloat(), 1f, t))
    }

    private companion object {
        val FULL = MotionPreference.FULL

        /** A bound on the real-thread DataStore loads, not a timing under test. */
        const val LOAD_TIMEOUT_MS = 10_000L

        /** Frames checked after the loads; 30 frames (480 ms) also outlast a crossfade (299). */
        const val SETTLE_FRAMES = 30
        const val MAX_ORIGIN_FRAMES = 3
        const val SEED = 0x2048L
        const val REVEAL_MS = 80L
        const val SPAWN_FRAME_MS = 96L
        const val TILE_BUDGET_END_MS = 432L
        const val BUDGET_END_MS = 448L
    }
}
