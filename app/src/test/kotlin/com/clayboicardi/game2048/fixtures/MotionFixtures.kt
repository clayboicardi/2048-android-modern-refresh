package com.clayboicardi.game2048.fixtures

import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.google.common.truth.Truth.assertWithMessage

/**
 * The motion fixtures of MOTION_SPEC.md §8.6, next to `GoldenFixtures` (which supplies `FIX_FILM`,
 * `FIX_MID_4` and `FIX_MID_3`). Cells are row-major exponents, 0 = empty. Spawns follow from each
 * fixture's `rngState` with the D-034/D-038 algorithm; [ENGINE_SPAWN_CHECK] lists the computed
 * spawn of every fixture action, and each motion test calls [SpawnCheck.verify] for the actions it
 * plays, so a mismatch fails loudly instead of shifting expectations.
 *
 * Values the §8.6 table leaves open are chosen here and named in each KDoc. A fixture that a test
 * undoes has a move count that is a multiple of 32, so the undo restores it exactly (D-216).
 */
object MotionFixtures {
    /**
     * 4×4 `KEEP_PLAYING` (`keepGoingAtMove` 400, 500 moves), score 30000, best 40000, `rngState`
     * 0x2048: LEFT merges 64, 512, 1024 and 4096 in column 0 (+5696), spawn exponent 1 at index 3.
     */
    val FIX_POP_TIERS_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(5, 5, 0, 0, 8, 8, 0, 0, 9, 9, 0, 0, 11, 11, 0, 0),
            score = 30_000,
            best = 40_000,
            moves = 500,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.KEEP_PLAYING,
            keepGoingAtMove = 400,
            rngState = 0x2048,
        )

    /**
     * 8×8 `PLAYING`, a single 2 at index 0, `rngState` 0x207E: RIGHT slides it 7 cells to index 7
     * and spawns exponent 1 at index 6, right behind it. 32 moves and score 0 (the table gives
     * none).
     */
    val FIX_LONG_SLIDE_8: SavedGame =
        fixtureGame(
            n = 8,
            cells = listOf(1) + List(63) { 0 },
            score = 0,
            best = 0,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x207E,
        )

    /**
     * 4×4 `PLAYING`, 10 moves, a 2 above a 4 in column 0: LEFT and UP are invalid, RIGHT and DOWN
     * are accepted without a merge. Score and best 0 (the table gives none).
     */
    val FIX_INVALID_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(1, 0, 0, 0, 2, 0, 0, 0) + List(8) { 0 },
            score = 0,
            best = 0,
            moves = 10,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
        )

    /**
     * 4×4 `PLAYING`, row 0 = `2,2,0,0` (exponents), score 96, `bestAtStart` 100, `rngState` 0x2048;
     * seed [NEW_BEST_RECORDS] with it. LEFT: +8 → 104 > 100, the game's first new best, spawn
     * exponent 1 at index 12. 32 moves (the table gives none), so an undo after LEFT restores it
     * exactly (AC-SCORE-5).
     */
    val FIX_NEW_BEST_4: SavedGame = newBestGame(4)

    /** The records of [FIX_NEW_BEST_4] and its larger-board twins: best 100 on sizes 4–7. */
    val NEW_BEST_RECORDS: Records =
        Records(sizes = (4..7).associate { it.toString() to SizeRecord(bestScore = 100) })

    /**
     * 4×4 `PLAYING`, score 18000, best 24680, 900 moves, `rngState` 0x2048: LEFT makes the 2048 at
     * index 0 (`becameWon`); rows 2 and 3 slide 2 and 3 cells; spawn exponent 1 at index 3.
     */
    val FIX_WIN_PRE_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(10, 10, 0, 0, 1, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 1),
            score = 18_000,
            best = 24_680,
            moves = 900,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x2048,
        )

    /**
     * [FIX_WIN_PRE_4] at 896 moves (AC-WIN-8; not in the §8.6 table): a multiple of 32, so an undo
     * of the winning move restores it exactly ([WIN_PRE_UNDO_LEFT] checks both). 900 moves would
     * undo through the synthetic history, which does not replay (D-216).
     */
    val FIX_WIN_PRE_UNDO_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(10, 10, 0, 0, 1, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 1),
            score = 18_000,
            best = 24_680,
            moves = 896,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x2048,
        )

    /**
     * 4×4 `PLAYING`, score 3012, best 24680, `rngState` 0x2048: LEFT moves only row 3 (→
     * `1,2,3,0`), spawns exponent 1 at index 15 and `becameOver`. 32 moves (the table gives none).
     */
    val FIX_OVER_PRE_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(1, 2, 1, 2, 2, 3, 2, 1, 4, 5, 6, 7, 0, 1, 2, 3),
            score = 3012,
            best = 24_680,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x2048,
        )

    /**
     * 4×4 `WON` with no move left (MOTION_SPEC.md §8.6): the 2048 at index 0 and no equal
     * neighbours, so Keep going sets `OVER` at once (D-036; [verifyWonNoMoves]). Score 20480, best
     * 24680, 992 moves (the table gives none). The table's "last spawn index 15" is not a field a
     * `SavedGame` has, and no winning move leaves this board with that spawn: the 2048 at index 0
     * can only have merged in row 0 or column 0, which are full, so that move's spawn was in its
     * own line. No assertion reads the deflate origin of Keep going into `OVER`.
     */
    val FIX_WON_NO_MOVES_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(11, 1, 2, 1, 1, 2, 1, 2, 2, 1, 2, 1, 1, 2, 1, 2),
            score = 20_480,
            best = 24_680,
            moves = 992,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.WON,
        )

    /**
     * The engine check of [FIX_WON_NO_MOVES_4] (like [ENGINE_SPAWN_CHECK]): no move is left and
     * Keep going makes it `OVER`.
     */
    fun verifyWonNoMoves() {
        val state = FIX_WON_NO_MOVES_4.state
        assertWithMessage("FIX_WON_NO_MOVES_4: a move is left").that(Game.canMove(state)).isFalse()
        assertWithMessage("FIX_WON_NO_MOVES_4: Keep going")
            .that(Game.keepGoing(state).status)
            .isEqualTo(GameStatus.OVER)
    }

    /**
     * A losing move with one merge (FEATURES.md F14-AC4, F14-AC9; not in the §8.6 table): 4×4
     * `PLAYING`, score 3012, best 24680, 32 moves, `rngState` 0x2048. Row 3 is `2,2,4,2`: LEFT
     * merges the two 4s (+8), spawns exponent 1 at index 15 and `becameOver`. UP is invalid.
     */
    val FIX_OVER_MERGE_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(1, 2, 1, 2, 2, 3, 2, 1, 4, 5, 6, 7, 2, 2, 4, 2),
            score = 3012,
            best = 24_680,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x2048,
        )

    /**
     * The flash limiter (AC-MOVE-10; not in the §8.6 table): 4×4 `PLAYING`, three 128 pairs in rows
     * 0–2, score 4000, best 100000, 32 moves, `rngState` 0x2048. LEFT makes three 256s (three
     * flashes); UP then merges the top two into a 512 within the same 1000 ms.
     */
    val FIX_FLASH_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(7, 7, 0, 0, 7, 7, 0, 0, 7, 7, 0, 0, 0, 0, 0, 0),
            score = 4000,
            best = 100_000,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x2048,
        )

    /**
     * The pop cap on a large 3×3 board (AC-MOVE-12; not in the §8.6 table): `KEEP_PLAYING`
     * (`keepGoingAtMove` 10, as 1024 is past the 3×3 target), two 1024s at indices 0 and 1, score
     * 50000, best 100000, 32 moves, `rngState` 0x2048. LEFT merges them into a 2048.
     */
    val FIX_POP_CAP_3: SavedGame =
        fixtureGame(
            n = 3,
            cells = listOf(10, 10, 0, 0, 0, 0, 0, 0, 0),
            score = 50_000,
            best = 100_000,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.KEEP_PLAYING,
            keepGoingAtMove = 10,
            rngState = 0x2048,
        )

    /**
     * Four merging moves in a row with distinct deltas (AC-SCORE-3; not in the §8.6 table): 8×8
     * `PLAYING`, row 0 = `4,3,2,1,1,0,0,0`, score 1000, best 100000, 32 moves, `rngState`
     * [CHAIN_RNG]. LEFT, RIGHT, LEFT, RIGHT score +4, +8, +16 and +32 ([CHAIN_MOVES]).
     */
    val FIX_CHAIN_8: SavedGame =
        fixtureGame(
            n = 8,
            cells = listOf(4, 3, 2, 1, 1, 0, 0, 0) + List(56) { 0 },
            score = 1000,
            best = 100_000,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = CHAIN_RNG,
        )

    /** [FIX_CHAIN_8]'s moves and the score delta of each. */
    val CHAIN_MOVES: List<Pair<Direction, Long>> =
        listOf(
            Direction.LEFT to 4L,
            Direction.RIGHT to 8L,
            Direction.LEFT to 16L,
            Direction.RIGHT to 32L,
        )

    /**
     * An accepted move with no merge (`MoveHaptic.NONE`) that undoes exactly: [FIX_INVALID_4]'s
     * board with 32 moves and `rngState` 0x2048; RIGHT slides both tiles.
     */
    val FIX_SLIDE_4: SavedGame =
        fixtureGame(
            n = 4,
            cells = listOf(1, 0, 0, 0, 2, 0, 0, 0) + List(8) { 0 },
            score = 0,
            best = 0,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x2048,
        )

    /**
     * [FIX_NEW_BEST_4]'s board, score and best on an [n]×[n] board (n = 4…7): the "separate runs"
     * of AC-SCORE-5 in one composition, one board size per run.
     */
    fun newBestGame(n: Int): SavedGame =
        pairGame(n, exp = 2, score = NEW_BEST_SCORE, best = NEW_BEST_BEST)

    /**
     * An [n]×[n] `PLAYING` board whose only tiles are two [exp] tiles at indices 0 and 1, so LEFT
     * merges them; score [score], `bestAtStart` [best], 32 moves, `rngState` 0x2048. The haptic
     * suites of `HapticsMapTest` and `MotionOffTest` put one per board size.
     */
    fun pairGame(n: Int, exp: Int, score: Long, best: Long): SavedGame =
        fixtureGame(
            n = n,
            cells = listOf(exp, exp) + List(n * n - 2) { 0 },
            score = score,
            best = best,
            moves = 32,
            undos = 0,
            elapsedMs = 0,
            status = GameStatus.PLAYING,
            rngState = 0x2048,
        )

    /** FIX_FILM LEFT (MOTION_SPEC.md §8.3). */
    val FILM_LEFT: SpawnCheck =
        SpawnCheck("FIX_FILM", GoldenFixtures.FIX_FILM, Direction.LEFT, 4, 1, scoreDelta = 4)

    /** FIX_POP_TIERS_4 LEFT. */
    val POP_TIERS_LEFT: SpawnCheck =
        SpawnCheck("FIX_POP_TIERS_4", FIX_POP_TIERS_4, Direction.LEFT, 3, 1, scoreDelta = 5696)

    /** FIX_LONG_SLIDE_8 RIGHT. */
    val LONG_SLIDE_RIGHT: SpawnCheck =
        SpawnCheck("FIX_LONG_SLIDE_8", FIX_LONG_SLIDE_8, Direction.RIGHT, 6, 1, scoreDelta = 0)

    /** FIX_NEW_BEST_4 LEFT. */
    val NEW_BEST_LEFT: SpawnCheck =
        SpawnCheck("FIX_NEW_BEST_4", FIX_NEW_BEST_4, Direction.LEFT, 12, 1, scoreDelta = 8)

    /** FIX_WIN_PRE_4 LEFT. */
    val WIN_PRE_LEFT: SpawnCheck =
        SpawnCheck("FIX_WIN_PRE_4", FIX_WIN_PRE_4, Direction.LEFT, 3, 1, becameWon = true)

    /** FIX_WIN_PRE_UNDO_4 LEFT, whose undo restores the fixture. */
    val WIN_PRE_UNDO_LEFT: SpawnCheck =
        SpawnCheck(
            "FIX_WIN_PRE_UNDO_4",
            FIX_WIN_PRE_UNDO_4,
            Direction.LEFT,
            3,
            1,
            becameWon = true,
            undoRestores = true,
        )

    /** FIX_OVER_PRE_4 LEFT. */
    val OVER_PRE_LEFT: SpawnCheck =
        SpawnCheck("FIX_OVER_PRE_4", FIX_OVER_PRE_4, Direction.LEFT, 15, 1, becameOver = true)

    /** FIX_OVER_MERGE_4 LEFT. */
    val OVER_MERGE_LEFT: SpawnCheck =
        SpawnCheck(
            "FIX_OVER_MERGE_4",
            FIX_OVER_MERGE_4,
            Direction.LEFT,
            15,
            1,
            scoreDelta = 8,
            becameOver = true,
        )

    /** FIX_INVALID_4 RIGHT and DOWN (accepted; spawns not used by any assertion). */
    val INVALID_ACCEPTED: List<Direction> = listOf(Direction.RIGHT, Direction.DOWN)

    /** FIX_INVALID_4 LEFT and UP (`MoveOutcome.Invalid`). */
    val INVALID_REJECTED: List<Direction> = listOf(Direction.LEFT, Direction.UP)

    /** MOTION_SPEC.md §8.6's computed spawns of every fixture action of the §8.6 table. */
    val ENGINE_SPAWN_CHECK: List<SpawnCheck> =
        listOf(
            FILM_LEFT,
            POP_TIERS_LEFT,
            LONG_SLIDE_RIGHT,
            NEW_BEST_LEFT,
            WIN_PRE_LEFT,
            WIN_PRE_UNDO_LEFT,
            OVER_PRE_LEFT,
            OVER_MERGE_LEFT,
        )

    /**
     * `FIX_CHAIN_8`'s `rngState`: its three spawns stay out of row 0 (computed with the engine).
     */
    private const val CHAIN_RNG = 0x2048L
    private const val NEW_BEST_SCORE = 96L
    private const val NEW_BEST_BEST = 100L
}

/**
 * One fixture action and the spawn the engine computes for it (MOTION_SPEC.md §8.6). [scoreDelta]
 * `null` leaves the delta unchecked; [undoRestores] also checks that `Game.undo` of the move gives
 * the fixture's board, score and status back.
 */
data class SpawnCheck(
    val name: String,
    val game: SavedGame,
    val direction: Direction,
    val index: Int,
    val exp: Int,
    val scoreDelta: Long? = null,
    val becameWon: Boolean = false,
    val becameOver: Boolean = false,
    val undoRestores: Boolean = false,
) {
    /** Plays [direction] on [game] with the engine and asserts the computed outcome. */
    fun verify(): MoveOutcome.Moved {
        val outcome = Game.move(game.state, direction)
        assertWithMessage("ENGINE_SPAWN_CHECK $name $direction: accepted")
            .that(outcome)
            .isInstanceOf(MoveOutcome.Moved::class.java)
        val moved = outcome as MoveOutcome.Moved
        val label = "ENGINE_SPAWN_CHECK $name $direction"
        assertWithMessage("$label: spawn index").that(moved.spawn.index).isEqualTo(index)
        assertWithMessage("$label: spawn exponent").that(moved.spawn.exp).isEqualTo(exp)
        scoreDelta?.let {
            assertWithMessage("$label: score delta").that(moved.scoreDelta).isEqualTo(it)
        }
        assertWithMessage("$label: becameWon").that(moved.becameWon).isEqualTo(becameWon)
        assertWithMessage("$label: becameOver").that(moved.becameOver).isEqualTo(becameOver)
        if (undoRestores) {
            val undone = checkNotNull(Game.undo(moved.state)) { "$label: nothing to undo" }
            val state = game.state
            assertWithMessage("$label: undo restores the board")
                .that(undone.board)
                .isEqualTo(state.board)
            assertWithMessage("$label: undo restores the score")
                .that(undone.score)
                .isEqualTo(state.score)
            assertWithMessage("$label: undo restores the status")
                .that(undone.status)
                .isEqualTo(state.status)
        }
        return moved
    }
}

/**
 * What `MotionHarness.launch` writes before it composes (MOTION_SPEC.md §8.1): [game] is the
 * current game (its size becomes `lastBoardSize`), [saved] are games of other sizes on disk,
 * [records] the records file, and [haptics] the Haptics setting.
 */
data class GameFixture(
    val game: SavedGame,
    val saved: List<SavedGame> = emptyList(),
    val records: Records = Records(),
    val haptics: Boolean = true,
)
