package com.clayboicardi.game2048.game.board

import com.clayboicardi.game2048.engine.Board
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.engine.Transition
import kotlin.math.abs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Turns committed engine states into `BoardUi`s with stable tile ids (ARCHITECTURE.md §5.2, D-025,
 * D-182). Pure Kotlin, owned by `GameViewModel`, main thread only.
 *
 * State: `idAt` (the tile id of each cell of the last emitted board, 0 = empty), `expAt` (its
 * exponents) and `nextId`, which is monotonic for the process, so ids are never reused. Both arrays
 * are reallocated when N changes. Every emission increments `moveId`; every [reset] increments
 * `epoch`.
 */
class BoardPresenter {
    private var boardSize: BoardSize? = null
    private var idAt = LongArray(0)
    private var expAt = IntArray(0)
    private var nextId = 1L
    private var moveId = 0L
    private var epoch = 0L

    /**
     * An epoch board for [state] (event `Restored`, `Undo`, `NewGame` or `SizeSwitched`): only
     * `Static` tiles. `Undo` keeps the id of every cell whose exponent is unchanged; every other
     * epoch assigns new ids (D-182).
     */
    fun reset(state: GameState, event: BoardEvent): BoardUi {
        require(event !is BoardEvent.Moved) { "reset() takes an epoch event, not $event" }
        val board = state.board
        val cells = cellsOf(board)
        val keepIds = event == BoardEvent.Undo && boardSize == state.size
        if (boardSize != state.size) {
            boardSize = state.size
            idAt = LongArray(cells.size)
            expAt = IntArray(cells.size)
        }
        epoch += 1
        val newIds = LongArray(cells.size)
        val tiles = ArrayList<TileUi>(cells.size)
        for (i in cells.indices) {
            val exp = cells[i]
            if (exp == 0) continue
            val keep = keepIds && idAt[i] != 0L && expAt[i] == exp
            newIds[i] = if (keep) idAt[i] else nextId++
            tiles += state.size.tile(newIds[i], exp, i, i, TileKind.Static)
        }
        idAt = newIds
        expAt = cells
        return BoardUi(
            moveId = ++moveId,
            epoch = epoch,
            size = state.size,
            tiles = tiles.toImmutableList(),
            cells = cells.toCellList(),
            scoreDelta = 0,
            event = event,
        )
    }

    /**
     * The board of an accepted move: `Ghost` merge sources, `Moved` tiles keeping their ids,
     * `Merged` tiles and the `Spawned` tile with new ids, in that z-order (ARCHITECTURE.md §5.1).
     */
    fun onMoved(outcome: MoveOutcome.Moved, event: BoardEvent.Moved): BoardUi {
        val state = outcome.state
        val size = state.size
        check(boardSize == size) { "onMoved() on $size before reset() for that size" }
        val cells = cellsOf(state.board)
        val newIds = LongArray(cells.size)
        val ghosts = ArrayList<TileUi>()
        val moved = ArrayList<TileUi>()
        val mergeCells = LinkedHashSet<Int>()
        for (t in outcome.transitions) {
            // One transition per occupied pre-move cell, so the id is set; a fresh one is only a
            // guard against a presenter that missed a reset.
            val id = idAt[t.fromIndex].takeIf { it != 0L } ?: nextId++
            val into = t.mergedIntoIndex
            if (into == null) {
                newIds[t.toIndex] = id
                moved += size.tile(id, cells[t.toIndex], t.toIndex, t.fromIndex, TileKind.Moved)
            } else {
                ghosts +=
                    size.tile(id, cells[t.toIndex] - 1, t.toIndex, t.fromIndex, TileKind.Ghost)
                mergeCells += into
            }
        }
        val merged = mergeCells.map { m ->
            newIds[m] = nextId++
            size.tile(newIds[m], cells[m], m, m, TileKind.Merged)
        }
        val spawn = outcome.spawn
        newIds[spawn.index] = nextId++
        val spawned =
            size.tile(newIds[spawn.index], spawn.exp, spawn.index, spawn.index, TileKind.Spawned)
        idAt = newIds
        expAt = cells
        return BoardUi(
            moveId = ++moveId,
            epoch = epoch,
            size = size,
            tiles = (ghosts + moved + merged + spawned).toImmutableList(),
            cells = cells.toCellList(),
            scoreDelta = outcome.scoreDelta,
            event = event,
        )
    }

    companion object {
        /**
         * The static form of a presented board (ARCHITECTURE.md §4.11, D-047): the same `moveId`,
         * `epoch`, `size`, `cells` and tile ids; Ghosts dropped; every tile `Static` with `from ==
         * to`, in row-major order; `scoreDelta = 0`; event `Restored`. Reads and changes no
         * presenter state, so it is not an epoch (D-182).
         */
        fun settled(board: BoardUi): BoardUi {
            if (board.event == BoardEvent.Restored) return board
            val n = board.size.n
            val tiles =
                board.tiles
                    .filter { it.kind != TileKind.Ghost }
                    .map { it.copy(fromRow = it.row, fromCol = it.col, kind = TileKind.Static) }
                    .sortedBy { it.row * n + it.col }
            return board.copy(
                tiles = tiles.toImmutableList(),
                scoreDelta = 0,
                event = BoardEvent.Restored,
            )
        }

        /** The longest slide of a move in cells, 0 without transitions (D-183). */
        fun maxSlideCells(transitions: List<Transition>, n: Int): Int =
            transitions.maxOfOrNull { t ->
                abs(t.toIndex / n - t.fromIndex / n) + abs(t.toIndex % n - t.fromIndex % n)
            } ?: 0

        private fun cellsOf(board: Board): IntArray = IntArray(board.cellCount) { board[it] }

        private fun IntArray.toCellList(): ImmutableList<Int> = asList().toImmutableList()

        /** A tile at cell index [at] that comes from cell index [from] (row-major, D-030). */
        private fun BoardSize.tile(id: Long, exp: Int, at: Int, from: Int, kind: TileKind) =
            TileUi(
                id = id,
                exp = exp,
                row = at / n,
                col = at % n,
                fromRow = from / n,
                fromCol = from % n,
                kind = kind,
            )
    }
}
