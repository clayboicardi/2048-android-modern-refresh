package com.clayboicardi.game2048.game.feedback

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.engine.Spawn
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.NEW_GAME_TAG
import com.clayboicardi.game2048.game.UNDO_TAG
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.MoveHaptic
import com.clayboicardi.game2048.game.overlay.KEEP_GOING_TAG
import com.clayboicardi.game2048.motion.MotionHarness
import com.clayboicardi.game2048.motion.MotionRule
import com.clayboicardi.game2048.ui.components.DIALOG_CONFIRM_TAG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The haptic map (D-065, D-185) and `HapticsController`'s timing (MOTION_SPEC.md §6, §9.11;
 * FEATURES.md F14). Robolectric because M3 adds the `HapticsController` timing methods to this
 * class (FEATURES.md §8); the M2 method is pure. The timing methods drive the whole Game screen
 * with `MotionHarness` and read `FakeHapticFeedback`; times are ms from each move's origin.
 *
 * A method that needs several boards keeps one per board size and switches between them (a size
 * switch fires nothing), because a test method composes once.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HapticsMapTest {
    @get:Rule val rule = MotionRule()

    /** F14-AC1 */
    @Test
    fun priorityResolution() {
        for (won in BOOLEANS) {
            for (newBest in BOOLEANS) {
                for (merges in Merges.entries) {
                    val expected =
                        when {
                            won -> MoveHaptic.WIN
                            newBest -> MoveHaptic.NEW_BEST
                            merges.maxExp >= BIG_MERGE_EXP -> MoveHaptic.BIG_MERGE
                            merges.count >= 1 -> MoveHaptic.MERGE
                            else -> MoveHaptic.NONE
                        }
                    assertWithMessage("won=$won, newBest=$newBest, merges=$merges")
                        .that(HapticsMap.forMove(outcome(won, merges), newBest))
                        .isEqualTo(expected)
                }
            }
        }
    }

    /** F14-AC2 */
    @Test
    fun fullMotionTimings() {
        val h = launchSuite(MotionPreference.FULL)
        for (case in suite(newBestSize = NEW_BEST_N)) {
            assertWithMessage("Full ${case.haptic}")
                .that(play(h, case))
                .containsExactlyElementsIn(case.full)
                .inOrder()
        }
    }

    /** F14-AC3 */
    @Test
    fun reducedAndOffTimings() {
        val h = launchSuite(MotionPreference.REDUCED)
        for (case in suite(newBestSize = NEW_BEST_N)) {
            assertWithMessage("Reduced ${case.haptic}")
                .that(play(h, case))
                .containsExactlyElementsIn(case.immediate)
                .inOrder()
            undo(h)
        }
        h.container.settingsRepository.update { it.copy(motion = MotionPreference.OFF) }
        h.advanceFrames(2)
        // The first new best stays announced after its undo, so Off plays the 3×3 twin.
        for (case in suite(newBestSize = SECOND_NEW_BEST_N)) {
            assertWithMessage("Off ${case.haptic}")
                .that(play(h, case))
                .containsExactlyElementsIn(case.immediate)
                .inOrder()
        }
    }

    /** F14-AC4 */
    @Test
    fun gameOverRejectIsSeparateAndDelayed() {
        MotionFixtures.OVER_MERGE_LEFT.verify()
        val h = launchSingle(MotionFixtures.FIX_OVER_MERGE_4, MotionPreference.FULL)
        val over = Case(MotionFixtures.FIX_OVER_MERGE_4, Direction.LEFT, MoveHaptic.MERGE)

        assertWithMessage("Full: a losing move with one merge")
            .that(play(h, over))
            .containsExactly(SEGMENT to MERGE_AT_MS, REJECT to GAME_OVER_AT_MS)
            .inOrder()
        undo(h)
        h.container.settingsRepository.update { it.copy(motion = MotionPreference.OFF) }
        h.advanceFrames(2)
        assertWithMessage("Off: the same move")
            .that(play(h, over))
            .containsExactly(SEGMENT to 0L, REJECT to 0L)
    }

    /** F14-AC5 */
    @Test
    fun invalidMoveRejectRateLimited() {
        val h = launchSingle(MotionFixtures.FIX_INVALID_4, MotionPreference.FULL)

        val start = h.now()
        val first = h.invalidMove(Direction.LEFT)
        while (h.now() < start + SECOND_INVALID_MS) h.advanceFrames(1)
        h.invalidMove(Direction.UP)
        while (h.now() < start + THIRD_INVALID_MS) h.advanceFrames(1)
        val third = h.invalidMove(Direction.LEFT)
        h.advanceTo(QUIET_MS)

        val rejects = h.haptics.events.filter { it.type == REJECT }.map { it.atMs }
        assertWithMessage("Rejects of invalid moves at 0, 100 and 450 ms: count")
            .that(rejects)
            .hasSize(2)
        // MOTION_SPEC.md §6.1: each on its nudge's start frame (the nudge origin).
        assertWithMessage("Rejects of invalid moves at 0, 100 and 450 ms: frames")
            .that(rejects)
            .containsExactly(first, third)
            .inOrder()
        assertWithMessage("haptics other than Reject")
            .that(h.haptics.events.filter { it.type != REJECT })
            .isEmpty()
    }

    /** F14-AC6, AC-HAP-2 */
    @Test
    fun hapticsOffSuppressesEverything() {
        // The Settings switches of AC-HAP-2 arrive with the Settings screen (M4, AC-HAP-4).
        val over = Case(MotionFixtures.FIX_OVER_MERGE_4, Direction.LEFT, MoveHaptic.MERGE)
        val cases = listOf(over) + suite(newBestSize = NEW_BEST_N).drop(1)
        val fixture =
            GameFixture(
                over.game,
                saved = cases.drop(1).map { it.game },
                records = NEW_BEST_RECORDS,
                haptics = false,
            )
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL)

        repeat(2) {
            h.invalidMove(Direction.UP)
            h.advanceTo(QUIET_MS)
        }
        for (case in cases) play(h, case)

        assertWithMessage("haptics with Haptics off").that(h.haptics.events).isEmpty()
    }

    /** F14-AC9, AC-HAP-1 */
    @Test
    // Pixel7's qualifiers include a colour-mode one that Robolectric cannot apply below API 26.
    @Config(sdk = [24], qualifiers = "w411dp-h914dp-420dpi")
    fun invalidMoveSilentBelowApi30() {
        MotionFixtures.OVER_MERGE_LEFT.verify()
        val h = launchSingle(MotionFixtures.FIX_OVER_MERGE_4, MotionPreference.FULL)

        h.tryMove(Direction.UP)
        h.advanceFrames(QUIET_FRAMES)
        assertWithMessage("API 24: haptics of an invalid move").that(h.haptics.events).isEmpty()
        val over = Case(MotionFixtures.FIX_OVER_MERGE_4, Direction.LEFT, MoveHaptic.MERGE)
        assertWithMessage("API 24: the losing move keeps its game-over Reject")
            .that(play(h, over))
            .containsExactly(SEGMENT to MERGE_AT_MS, REJECT to GAME_OVER_AT_MS)
            .inOrder()
    }

    /** AC-HAP-1 */
    @Test
    fun onlyTheHighestPriorityFires() {
        val cases =
            listOf(
                // Rows 1–4: a win that is also a new best, with a 2048 merge.
                Case(
                    MotionFixtures.pairGame(8, 10, 18_000, 19_000),
                    Direction.LEFT,
                    MoveHaptic.WIN,
                ),
                // Rows 2–4: a new best with a 512 merge.
                Case(
                    MotionFixtures.pairGame(6, 8, 5000, 5100),
                    Direction.LEFT,
                    MoveHaptic.NEW_BEST,
                ),
                // Rows 3–4: a 512 merge.
                Case(
                    MotionFixtures.pairGame(7, 8, 5000, 100_000),
                    Direction.LEFT,
                    MoveHaptic.BIG_MERGE,
                ),
                // Row 4: a merge.
                Case(MotionFixtures.pairGame(5, 1, 0, 0), Direction.LEFT, MoveHaptic.MERGE),
            )
        val records =
            Records(
                sizes =
                    mapOf(
                        "8" to SizeRecord(bestScore = 19_000),
                        "6" to SizeRecord(bestScore = 5100),
                    ),
            )
        val fixture =
            GameFixture(
                MotionFixtures.FIX_SLIDE_4,
                saved = cases.map { it.game },
                records = records,
            )
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL)

        for (case in cases) {
            assertWithMessage("rows from ${case.haptic}")
                .that(play(h, case))
                .containsExactlyElementsIn(case.full)
                .inOrder()
        }
    }

    /** AC-HAP-3 */
    @Test
    fun pendingNewBestFiresOnTheNextCommit() {
        MotionFixtures.NEW_BEST_LEFT.verify()
        val fixture =
            GameFixture(MotionFixtures.FIX_NEW_BEST_4, records = MotionFixtures.NEW_BEST_RECORDS)
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL)

        h.commitMove(Direction.LEFT)
        h.advanceTo(INTERRUPT_AT_MS)
        val second = h.commitMove(Direction.RIGHT)
        assertResolved(h, MoveHaptic.NONE)
        h.advanceTo(QUIET_MS)

        assertWithMessage("the new best's haptic on the second move's F1")
            .that(h.haptics.events.map { it.type to it.atMs - second })
            .containsExactly(THRESHOLD to 0L)
    }

    /** AC-HAP-3 */
    @Test
    fun pendingMergeTickIsDropped() {
        MotionFixtures.FILM_LEFT.verify()
        val h = launchSingle(GoldenFixtures.FIX_FILM, MotionPreference.FULL)

        h.commitMove(Direction.LEFT)
        h.advanceTo(INTERRUPT_AT_MS)
        h.commitMove(Direction.DOWN)
        assertResolved(h, MoveHaptic.NONE)
        h.advanceTo(QUIET_MS)

        assertWithMessage("the merge's SegmentTick after a move at t = 48")
            .that(h.haptics.events)
            .isEmpty()
    }

    /** F14-AC7 */
    @Test
    fun buttonsAreSilent() {
        MotionFixtures.FILM_LEFT.verify()
        val win = MotionFixtures.pairGame(8, 10, 18_000, 24_680)
        val fixture =
            GameFixture(
                GoldenFixtures.FIX_FILM,
                saved = listOf(win),
                records = GoldenFixtures.FIX_RECORDS,
            )
        val h = MotionHarness.launch(rule, fixture, MotionPreference.FULL)
        h.commitMove(Direction.LEFT)
        h.advanceTo(QUIET_MS)

        silent(h, "Undo") { h.commit { h.rule.onNodeWithTag(UNDO_TAG).performClick() } }
        silent(h, "New game, confirmed through the dialog") {
            h.rule.onNodeWithTag(NEW_GAME_TAG).performClick()
            h.awaitNode(DIALOG_CONFIRM_TAG)
            h.commit { h.rule.onNodeWithTag(DIALOG_CONFIRM_TAG).performClick() }
        }
        silent(h, "New game, immediate") {
            h.commit { h.rule.onNodeWithTag(NEW_GAME_TAG).performClick() }
        }
        silent(h, "a size switch through the sheet") {
            h.commitSizeSwitchThroughSheet(win.state.size.n)
        }
        h.commitMove(Direction.LEFT)
        h.advanceTo(WIN_INTERACTIVE_MS)
        silent(h, "Keep going with moves left") { h.tapCardButtonTag(KEEP_GOING_TAG) }
    }

    /** AC-EXIT-2 */
    @Test
    fun keepGoingIntoOverRejectsOnce() {
        MotionFixtures.verifyWonNoMoves()
        val h = launchSingle(MotionFixtures.FIX_WON_NO_MOVES_4, MotionPreference.FULL)
        assertWithMessage("haptics of the restored win card").that(h.haptics.events).isEmpty()

        val ft = h.tapCardButtonTag(KEEP_GOING_TAG)
        h.advanceTo(CARD_QUIET_MS)

        assertWithMessage("Keep going into OVER: the game-over Reject on Ft, the tap silent")
            .that(h.hapticsFrom(ft))
            .containsExactly(REJECT to 0L)
    }

    /** Runs [action] and checks that nothing fires within [QUIET_MS] of its origin (F14 item 3). */
    private fun silent(h: MotionHarness, what: String, action: () -> Unit) {
        val before = h.haptics.events.size
        action()
        h.advanceTo(QUIET_MS)
        assertWithMessage("haptics of $what").that(h.haptics.events.drop(before)).isEmpty()
    }

    /** One accepted move of the timing suites and its expected haptics. */
    private data class Case(val game: SavedGame, val direction: Direction, val haptic: MoveHaptic) {
        /** Full timings (MOTION_SPEC.md §6.1): from the origin, on the 16 ms grid. */
        val full: List<Pair<HapticFeedbackType, Long>>
            get() =
                when (haptic) {
                    MoveHaptic.WIN -> listOf(CONFIRM to WIN_FIRST_MS, CONFIRM to WIN_SECOND_MS)
                    MoveHaptic.NEW_BEST -> listOf(THRESHOLD to MERGE_AT_MS)
                    MoveHaptic.BIG_MERGE -> listOf(CONFIRM to MERGE_AT_MS)
                    MoveHaptic.MERGE -> listOf(SEGMENT to MERGE_AT_MS)
                    MoveHaptic.NONE -> emptyList()
                }

        /** Reduced and Off timings: +0, the second win pulse +150 real time (frame 160). */
        val immediate: List<Pair<HapticFeedbackType, Long>>
            get() =
                when (haptic) {
                    MoveHaptic.WIN -> listOf(CONFIRM to 0L, CONFIRM to WIN_REDUCED_SECOND_MS)
                    else -> full.map { it.first to 0L }
                }
    }

    /** One board per `MoveHaptic`, each on its own size; [newBestSize] holds the new best. */
    private fun suite(newBestSize: Int): List<Case> =
        listOf(
            Case(MotionFixtures.FIX_SLIDE_4, Direction.RIGHT, MoveHaptic.NONE),
            Case(MotionFixtures.pairGame(5, 1, 0, 0), Direction.LEFT, MoveHaptic.MERGE),
            Case(
                MotionFixtures.pairGame(6, 8, 5000, 100_000),
                Direction.LEFT,
                MoveHaptic.BIG_MERGE,
            ),
            Case(MotionFixtures.newBestGame(newBestSize), Direction.LEFT, MoveHaptic.NEW_BEST),
            Case(MotionFixtures.pairGame(8, 10, 18_000, 24_680), Direction.LEFT, MoveHaptic.WIN),
        )

    /** The suite's boards and the 3×3 new-best twin, starting on the 4×4 board. */
    private fun launchSuite(motion: MotionPreference): MotionHarness {
        val boards =
            suite(NEW_BEST_N).map { it.game } + MotionFixtures.newBestGame(SECOND_NEW_BEST_N)
        val fixture =
            GameFixture(boards.first(), saved = boards.drop(1), records = NEW_BEST_RECORDS)
        return MotionHarness.launch(rule, fixture, motion)
    }

    private fun launchSingle(game: SavedGame, motion: MotionPreference): MotionHarness =
        MotionHarness.launch(rule, GameFixture(game), motion)

    /**
     * Switches to [case]'s board if needed, plays its move, checks the haptic the ViewModel
     * resolved, and returns the haptics of the next 500 ms from the move's origin.
     */
    private fun play(h: MotionHarness, case: Case): List<Pair<HapticFeedbackType, Long>> {
        val size = case.game.state.size
        if (h.vm.board.value.size != size) {
            h.commitSizeSwitch(size.n)
            h.settle()
        }
        val before = h.haptics.events.size
        val origin = h.commitMove(case.direction)
        assertResolved(h, case.haptic)
        h.advanceTo(QUIET_MS)
        return h.haptics.events.drop(before).map { it.type to it.atMs - origin }
    }

    private fun assertResolved(h: MotionHarness, haptic: MoveHaptic) {
        val event = checkNotNull(h.committed).event as BoardEvent.Moved
        assertWithMessage("the haptic GameViewModel resolved").that(event.haptic).isEqualTo(haptic)
    }

    /** Undo restores the board for the next pass; it fires nothing. */
    private fun undo(h: MotionHarness) {
        val before = h.haptics.events.size
        h.commitUndo()
        h.settle()
        assertWithMessage("haptics of an undo").that(h.haptics.events.drop(before)).isEmpty()
    }

    /** Merge shapes around the D-065 512 threshold (exponent 9). */
    private enum class Merges(val count: Int, val maxExp: Int) {
        NONE(0, 0),
        ONE_256(1, 8),
        ONE_512(1, 9),
        TWO_WITH_512(2, 9),
        ONE_4096(1, 12),
    }

    private companion object {
        val BOOLEANS = listOf(false, true)

        /** 512 = 2^9, written out here so the test does not reuse the constant it checks. */
        const val BIG_MERGE_EXP = 9

        const val NEW_BEST_N = 7
        const val SECOND_NEW_BEST_N = 3
        const val MERGE_AT_MS = 80L
        const val WIN_FIRST_MS = 128L
        const val WIN_SECOND_MS = 272L
        const val WIN_REDUCED_SECOND_MS = 160L
        const val GAME_OVER_AT_MS = 304L
        const val QUIET_MS = 500L
        const val QUIET_FRAMES = 30

        /** The win buttons' interactive frame ⌈880⌉ (MOTION_SPEC.md §3.11). */
        const val WIN_INTERACTIVE_MS = 880L

        /** Past the game-over card of Keep going into `OVER` (Ft + 704). */
        const val CARD_QUIET_MS = 1200L
        const val INTERRUPT_AT_MS = 48L
        const val SECOND_INVALID_MS = 100L
        const val THIRD_INVALID_MS = 450L
        val SEGMENT = HapticFeedbackType.SegmentTick
        val CONFIRM = HapticFeedbackType.Confirm
        val THRESHOLD = HapticFeedbackType.GestureThresholdActivate
        val REJECT = HapticFeedbackType.Reject
        val NEW_BEST_RECORDS =
            Records(
                sizes =
                    mapOf(
                        NEW_BEST_N.toString() to SizeRecord(bestScore = 100),
                        SECOND_NEW_BEST_N.toString() to SizeRecord(bestScore = 100),
                    ),
            )

        /** A synthetic accepted move: only the fields `HapticsMap` reads vary. */
        fun outcome(won: Boolean, merges: Merges) =
            MoveOutcome.Moved(
                state = Game.newGame(BoardSize.of(4), 0x2048),
                transitions = emptyList(),
                spawn = Spawn(index = 0, exp = 1),
                scoreDelta = 0,
                mergeCount = merges.count,
                maxMergedExp = merges.maxExp,
                becameWon = won,
                becameOver = false,
            )
    }
}
