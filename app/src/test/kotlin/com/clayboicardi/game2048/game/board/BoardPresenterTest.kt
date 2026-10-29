package com.clayboicardi.game2048.game.board

import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.engine.SplitMix64
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

/**
 * `BoardPresenter` (ARCHITECTURE.md §5.2, D-025, D-047, D-182). The property methods run seeded
 * random replays of moves, undos, new games, size switches and reloads (`SplitMix64(0x2048 + i)`;
 * `:app` has no kotest, D-016) and check every emitted `BoardUi`.
 */
class BoardPresenterTest {
    /** F05-AC9 */
    @Test
    fun undoEpochHasOnlyStaticTiles() {
        val presenter = BoardPresenter()
        val played = playMidGame(presenter)

        val undone = checkNotNull(Game.undo(played))
        val board = presenter.reset(undone, BoardEvent.Undo)

        assertThat(board.event).isEqualTo(BoardEvent.Undo)
        assertAllStatic(board)
        assertMatchesEngine(board, undone)
    }

    /** F06-AC11 */
    @Test
    fun newGameEpochHasOnlyStaticTiles() {
        val presenter = BoardPresenter()
        playMidGame(presenter)

        val fresh = Game.newGame(FOUR, SEED + 1)
        val board = presenter.reset(fresh, BoardEvent.NewGame)

        assertThat(board.event).isEqualTo(BoardEvent.NewGame)
        assertAllStatic(board)
        assertMatchesEngine(board, fresh)
    }

    /** F07-AC25 */
    @Test
    fun settledKeepsIdentityAndIsStatic() {
        for (i in 0 until REPLAYS) {
            val plain = replay(SEED + i)
            val settledAlong = replay(SEED + i) { BoardPresenter.settled(it) }
            assertWithMessage("replay $i: emissions with and without settled()")
                .that(settledAlong.map { it.board })
                .isEqualTo(plain.map { it.board })
            for (step in plain) assertSettled(step.board, "replay $i, moveId ${step.board.moveId}")
        }
    }

    /** Non-`Ghost` tiles are the engine board's non-empty cells; no id appears twice. */
    @Test
    fun tilesMatchEngineBoardWithUniqueIds() {
        forEachStep { step, label ->
            assertMatchesEngine(step.board, step.state, label)
            val ids = step.board.tiles.map { it.id }
            assertWithMessage("$label: ids").that(ids).containsNoDuplicates()
            assertWithMessage("$label: cells").that(step.board.cells).isEqualTo(cellsOf(step.state))
        }
    }

    /** The `Ghost` tiles of a move are exactly its merge sources, with their previous ids. */
    @Test
    fun ghostsAreExactlyMergeSources() {
        forEachStep { step, label ->
            val outcome = step.outcome ?: return@forEachStep
            val n = step.state.size.n
            val previous = idsByCell(checkNotNull(step.previous))
            val ghosts = step.board.tiles.filter { it.kind == TileKind.Ghost }
            val sources = outcome.transitions.filter { it.mergedIntoIndex != null }
            assertWithMessage("$label: ghost sources")
                .that(ghosts.map { it.fromRow * n + it.fromCol })
                .containsExactlyElementsIn(sources.map { it.fromIndex })
            for (g in ghosts) {
                val source = sources.single { it.fromIndex == g.fromRow * n + g.fromCol }
                assertWithMessage("$label: ghost id")
                    .that(g.id)
                    .isEqualTo(previous[source.fromIndex])
                assertWithMessage("$label: ghost cell")
                    .that(g.row * n + g.col)
                    .isEqualTo(source.mergedIntoIndex)
                assertWithMessage("$label: ghost exp")
                    .that(g.exp)
                    .isEqualTo(step.state.board[g.row * n + g.col] - 1)
            }
        }
    }

    /** Every epoch board (`Restored`, `Undo`, `NewGame`, `SizeSwitched`) is all `Static`. */
    @Test
    fun epochBoardsAreAllStatic() {
        forEachStep { step, label ->
            if (step.outcome == null) {
                assertWithMessage("$label: event")
                    .that(step.board.event)
                    .isNotInstanceOf(
                        BoardEvent.Moved::class.java,
                    )
                assertAllStatic(step.board, label)
                assertWithMessage("$label: scoreDelta").that(step.board.scoreDelta).isEqualTo(0)
            }
        }
    }

    /** After `Undo` a cell keeps its tile id if and only if its exponent is unchanged (D-182). */
    @Test
    fun undoKeepsIdsExactlyWhereExponentIsUnchanged() {
        var undos = 0
        forEachStep { step, label ->
            if (step.board.event != BoardEvent.Undo) return@forEachStep
            undos++
            val previous = checkNotNull(step.previous)
            val previousIds = idsByCell(previous)
            val n = step.state.size.n
            for (tile in step.board.tiles) {
                val i = tile.row * n + tile.col
                val unchanged = previous.cells[i] == tile.exp
                if (unchanged) {
                    assertWithMessage("$label: kept id at $i")
                        .that(tile.id)
                        .isEqualTo(previousIds[i])
                } else {
                    assertWithMessage("$label: new id at $i").that(tile.id).isNotIn(step.seenIds)
                }
            }
        }
        assertThat(undos).isGreaterThan(0)
    }

    /** `Restored`, `NewGame` and `SizeSwitched` assign only ids never used before (D-182). */
    @Test
    fun otherEpochsAssignNewIds() {
        val kinds = mutableSetOf<BoardEventKind>()
        forEachStep { step, label ->
            val event = step.board.event
            if (step.outcome != null || event == BoardEvent.Undo) return@forEachStep
            kinds += event.kind
            for (tile in step.board.tiles) {
                assertWithMessage("$label: id ${tile.id}").that(tile.id).isNotIn(step.seenIds)
            }
        }
        assertThat(kinds)
            .containsExactly(
                BoardEventKind.Restored,
                BoardEventKind.NewGame,
                BoardEventKind.SizeSwitched,
            )
    }

    /** `moveId` grows by 1 per emission; `epoch` grows by 1 per reset and never on a move. */
    @Test
    fun moveIdAndEpochCount() {
        forEachStep { step, label ->
            val previous = step.previous ?: return@forEachStep
            assertWithMessage("$label: moveId")
                .that(step.board.moveId)
                .isEqualTo(previous.moveId + 1)
            val epochStep = if (step.outcome == null) 1 else 0
            assertWithMessage("$label: epoch")
                .that(step.board.epoch)
                .isEqualTo(previous.epoch + epochStep)
        }
    }

    /** `maxSlideCells` is the longest distance any tile of the move travels, in cells. */
    @Test
    fun maxSlideCellsIsLongestSlide() {
        forEachStep { step, _ ->
            val outcome = step.outcome ?: return@forEachStep
            val longest =
                step.board.tiles
                    .filter { it.kind == TileKind.Moved || it.kind == TileKind.Ghost }
                    .maxOf { abs(it.row - it.fromRow) + abs(it.col - it.fromCol) }
            assertThat(BoardPresenter.maxSlideCells(outcome.transitions, step.state.size.n))
                .isEqualTo(longest)
        }
        assertThat(BoardPresenter.maxSlideCells(emptyList(), 4)).isEqualTo(0)
    }
}

private val FOUR: BoardSize = BoardSize.of(4)
private const val SEED = 0x2048L
private const val REPLAYS = 40
private const val ACTIONS = 120

/** One emitted board with the engine state it presents. */
private class Step(
    val board: BoardUi,
    val state: GameState,
    /** The board emitted before this one; null for the first. */
    val previous: BoardUi?,
    /** The accepted move this board presents; null for an epoch. */
    val outcome: MoveOutcome.Moved?,
    /** Every tile id of the boards emitted before this one. */
    val seenIds: Set<Long>,
)

private fun forEachStep(check: (Step, String) -> Unit) {
    for (i in 0 until REPLAYS) {
        for (step in replay(SEED + i)) check(step, "replay $i, moveId ${step.board.moveId}")
    }
}

/**
 * A seeded random replay through one presenter: moves (Keep going when `WON` blocks), undos, new
 * games, size switches and reloads (`Restored`) of the current state. [onEmit] sees every board.
 */
private fun replay(seed: Long, onEmit: (BoardUi) -> Unit = {}): List<Step> {
    val rng = SplitMix64(seed)
    val presenter = BoardPresenter()
    val steps = ArrayList<Step>()
    val seen = HashSet<Long>()
    var state = Game.newGame(BoardSize.ALL[rng.nextInt(BoardSize.ALL.size)], rng.nextLong())
    fun emit(board: BoardUi, outcome: MoveOutcome.Moved? = null) {
        steps += Step(board, state, steps.lastOrNull()?.board, outcome, seen.toSet())
        seen += board.tiles.map { it.id }
        onEmit(board)
    }
    emit(presenter.reset(state, BoardEvent.Restored))
    repeat(ACTIONS) {
        when (rng.nextInt(ACTION_KINDS)) {
            in 0 until MOVE_WEIGHT -> {
                when (val outcome = Game.move(state, Direction.fromCode(rng.nextInt(4)))) {
                    is MoveOutcome.Moved -> {
                        state = outcome.state
                        emit(presenter.onMoved(outcome, eventOf(outcome)), outcome)
                    }
                    is MoveOutcome.Blocked ->
                        if (state.status == GameStatus.WON) state = Game.keepGoing(state)
                    is MoveOutcome.Invalid -> Unit
                }
            }
            in MOVE_WEIGHT until UNDO_WEIGHT ->
                Game.undo(state)?.let { undone ->
                    state = undone
                    emit(presenter.reset(state, BoardEvent.Undo))
                }
            UNDO_WEIGHT -> {
                state = Game.newGame(state.size, rng.nextLong())
                emit(presenter.reset(state, BoardEvent.NewGame))
            }
            UNDO_WEIGHT + 1 -> {
                val others = BoardSize.ALL - state.size
                state = Game.newGame(others[rng.nextInt(others.size)], rng.nextLong())
                emit(presenter.reset(state, BoardEvent.SizeSwitched))
            }
            else -> emit(presenter.reset(state, BoardEvent.Restored))
        }
    }
    return steps
}

/** 8 of 13 actions are moves, 2 undos, then one each of new game, size switch and reload. */
private const val MOVE_WEIGHT = 8
private const val UNDO_WEIGHT = 10
private const val ACTION_KINDS = 13

private fun eventOf(outcome: MoveOutcome.Moved) =
    BoardEvent.Moved(
        direction = Direction.UP,
        mergedExps = persistentListOf(),
        mergeCount = outcome.mergeCount,
        maxMergedExp = outcome.maxMergedExp,
        maxSlideCells = BoardPresenter.maxSlideCells(outcome.transitions, outcome.state.size.n),
        spawnIndex = outcome.spawn.index,
        scoreAfter = outcome.state.score,
        haptic = MoveHaptic.NONE,
        newBest = false,
        becameWon = outcome.becameWon,
        becameOver = outcome.becameOver,
    )

/** Plays 30 accepted moves from the new game of 0x2048, starting with a `Restored` board. */
private fun playMidGame(presenter: BoardPresenter): GameState {
    var state = Game.newGame(FOUR, SEED)
    presenter.reset(state, BoardEvent.Restored)
    repeat(MID_GAME_MOVES) {
        val outcome =
            Direction.entries.firstNotNullOf { d -> Game.move(state, d) as? MoveOutcome.Moved }
        presenter.onMoved(outcome, eventOf(outcome))
        state = outcome.state
    }
    return state
}

private const val MID_GAME_MOVES = 30

private fun cellsOf(state: GameState): List<Int> = List(state.board.cellCount) { state.board[it] }

/** Cell index → tile id of [board]'s non-`Ghost` tiles. */
private fun idsByCell(board: BoardUi): Map<Int, Long> =
    board.tiles
        .filter { it.kind != TileKind.Ghost }
        .associate { it.row * board.size.n + it.col to it.id }

private fun assertAllStatic(board: BoardUi, label: String = "board") {
    for (tile in board.tiles) {
        assertWithMessage("$label: kind of ${tile.id}").that(tile.kind).isEqualTo(TileKind.Static)
        assertWithMessage("$label: from of ${tile.id}")
            .that(tile.fromRow to tile.fromCol)
            .isEqualTo(tile.row to tile.col)
    }
}

private fun assertMatchesEngine(board: BoardUi, state: GameState, label: String = "board") {
    val n = state.size.n
    val shown = board.tiles.filter { it.kind != TileKind.Ghost }
    assertWithMessage("$label: tile cells")
        .that(shown.associate { it.row * n + it.col to it.exp })
        .isEqualTo(
            cellsOf(state).withIndex().filter { it.value != 0 }.associate { it.index to it.value },
        )
    assertWithMessage("$label: one tile per cell")
        .that(shown.size)
        .isEqualTo(state.board.tileCount())
}

/** F07-AC25's properties of `settled(b)` for one emitted board. */
private fun assertSettled(b: BoardUi, label: String) {
    val s = BoardPresenter.settled(b)
    val n = b.size.n
    assertWithMessage("$label: identity")
        .that(listOf(s.moveId, s.epoch, s.size, s.cells))
        .isEqualTo(listOf(b.moveId, b.epoch, b.size, b.cells))
    // b's own z-order, Ghosts dropped (D-241).
    val kept = b.tiles.filter { it.kind != TileKind.Ghost }
    assertWithMessage("$label: tiles")
        .that(s.tiles.map { Triple(it.id, it.exp, it.row * n + it.col) })
        .isEqualTo(kept.map { Triple(it.id, it.exp, it.row * n + it.col) })
    assertAllStatic(s, label)
    assertWithMessage("$label: scoreDelta").that(s.scoreDelta).isEqualTo(0)
    assertWithMessage("$label: event").that(s.event).isEqualTo(BoardEvent.Restored)
    assertWithMessage("$label: idempotent").that(BoardPresenter.settled(s)).isEqualTo(s)
}
