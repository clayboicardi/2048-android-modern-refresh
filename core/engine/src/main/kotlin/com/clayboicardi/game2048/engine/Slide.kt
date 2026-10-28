package com.clayboicardi.game2048.engine

/** Leading-wall traversal with merge-once (D-032, D-033). Pure: draws nothing. */
internal fun slide(board: Board, direction: Direction): SlideResult {
    val n = board.size.n
    val first = firstCell(direction, n)
    val lineStep = lineStep(direction, n)
    val stride = cellStride(direction, n)
    val pass = SlidePass(board)
    for (l in 0 until n) {
        pass.slideLine(first + l * lineStep, stride)
    }
    return pass.result()
}

/**
 * Result of one slide (ARCHITECTURE.md §3.3). [newCells] is a fresh row-major exponent array of the
 * post-slide, pre-spawn board; [transitions] holds one entry per occupied pre-move cell in
 * processing order; [mergedExps] holds the exponent produced by each merge, in processing order.
 */
internal class SlideResult(
    val newCells: ByteArray,
    val moved: Boolean,
    val scoreDelta: Long,
    val transitions: List<Transition>,
    val mergedExps: List<Int>,
)

// Line order of ARCHITECTURE.md §3.3: cell i of line l is first + l * lineStep + i * stride, so
// every line starts at the wall the move goes toward.

/** Wall cell of line 0. */
private fun firstCell(direction: Direction, n: Int): Int =
    when (direction) {
        Direction.LEFT,
        Direction.UP -> 0
        Direction.RIGHT -> n - 1
        Direction.DOWN -> (n - 1) * n
    }

/** Offset from the wall cell of line l to that of line l + 1. */
private fun lineStep(direction: Direction, n: Int): Int =
    when (direction) {
        Direction.LEFT,
        Direction.RIGHT -> n
        Direction.UP,
        Direction.DOWN -> 1
    }

/** Offset from cell i to cell i + 1 of a line, moving away from the wall. */
private fun cellStride(direction: Direction, n: Int): Int =
    when (direction) {
        Direction.LEFT -> 1
        Direction.RIGHT -> -1
        Direction.UP -> n
        Direction.DOWN -> -n
    }

/**
 * Mutable state of one slide. Transitions are built in parallel arrays while lines are processed
 * ([NO_MERGE] standing for a null mergedIntoIndex) and frozen into [Transition] values by [result].
 */
private class SlidePass(private val board: Board) {
    private val n = board.size.n
    private val out = ByteArray(board.cellCount)
    private val fromIndex = IntArray(board.cellCount)
    private val toIndex = IntArray(board.cellCount)
    private val mergedInto = IntArray(board.cellCount)
    private var transitionCount = 0

    /** Per line position: whether a merge produced the tile there in this move. */
    private val mergedAt = BooleanArray(n)

    /** Per line position: index into the transitions of the tile placed there. */
    private val transitionAt = IntArray(n)

    private val mergedExps = ArrayList<Int>()
    private var scoreDelta = 0L
    private var moved = false

    fun slideLine(origin: Int, stride: Int) {
        var target = 0
        for (i in 0 until n) {
            val src = origin + i * stride
            val e = board[src]
            if (e == 0) continue
            val previous = target - 1
            if (target > 0 && out[origin + previous * stride].toInt() == e && !mergedAt[previous]) {
                merge(src, origin + previous * stride, e + 1, previous)
            } else {
                place(src, origin + target * stride, e, target)
                target++
            }
        }
    }

    private fun merge(src: Int, dst: Int, exp: Int, position: Int) {
        // Play never gets past N² + 1 <= 65, but Board.of and I1 accept exponents up to MAX_EXP, so
        // a decoded board can hold two adjacent MAX_EXP tiles; storing their merge would wrap
        // to a negative byte (ARCHITECTURE.md §3.3).
        check(exp <= Board.MAX_EXP) { "Merging two ${exp - 1} tiles exceeds ${Board.MAX_EXP}" }
        out[dst] = exp.toByte()
        mergedAt[position] = true
        // The first source becomes a merge source too.
        mergedInto[transitionAt[position]] = dst
        record(src, dst, mergedIntoIndex = dst)
        scoreDelta = saturatingAdd(scoreDelta, tileValue(exp))
        mergedExps += exp
        moved = true
    }

    private fun place(src: Int, dst: Int, exp: Int, position: Int) {
        out[dst] = exp.toByte()
        mergedAt[position] = false
        transitionAt[position] = transitionCount
        record(src, dst, mergedIntoIndex = NO_MERGE)
        if (dst != src) moved = true
    }

    private fun record(src: Int, dst: Int, mergedIntoIndex: Int) {
        fromIndex[transitionCount] = src
        toIndex[transitionCount] = dst
        mergedInto[transitionCount] = mergedIntoIndex
        transitionCount++
    }

    fun result(): SlideResult {
        val transitions =
            List(transitionCount) {
                val merge = mergedInto[it]
                Transition(fromIndex[it], toIndex[it], if (merge == NO_MERGE) null else merge)
            }
        return SlideResult(out, moved, scoreDelta, transitions, mergedExps)
    }

    private companion object {
        const val NO_MERGE = -1
    }
}
