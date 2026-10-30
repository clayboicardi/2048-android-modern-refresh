package com.clayboicardi.game2048.data.records

import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.GameStatus.OVER

/**
 * All record arithmetic, pure (ARCHITECTURE.md §4.9; D-044, D-198; FEATURES.md §3). No function
 * lowers a best except [reset], and none lowers `lastFinalizedGameId`; [coerceBestTiles] drops only
 * a best tile that play cannot reach (D-270).
 */
object RecordsMath {
    /**
     * The map key of [size] in `Records.sizes` and `Records.lastFinalizedGameId`: `"${size.n}"`.
     */
    fun key(size: BoardSize): String = "${size.n}"

    /**
     * Live bests of an accepted move: `bestScore = max`, `bestScoreNoUndo = max` only while
     * `undoCount == 0`, `bestTileExp = max`. Nothing else changes; returns [records] itself when no
     * best increases.
     */
    fun progress(records: Records, game: SavedGame): Records {
        val s = game.state
        val k = key(s.size)
        val old = records.sizes[k] ?: SizeRecord()
        val new = old.withBests(s.score, s.undoCount, s.board.maxExp())
        return if (new == old) records else records.copy(sizes = records.sizes + (k to new))
    }

    /** `gameId <= (records.lastFinalizedGameId[key(size)] ?: 0)` (D-044). */
    fun isFinalized(records: Records, size: BoardSize, gameId: Long): Boolean =
        gameId <= (records.lastFinalizedGameId[key(size)] ?: 0)

    /**
     * D-044 finalization, identity-guarded: returns [records] itself when `f.gameId` is already
     * finalized; otherwise counts the game, applies the `progress` arithmetic on `f.score`,
     * `f.undoCount` and `f.maxExp`, and sets `lastFinalizedGameId[key(f.size)] = f.gameId`.
     *
     * A win is `f.maxExp >= f.size.targetExp`, judged on the final board; a finalized non-win
     * resets `currentWinStreak` to 0; any status but `OVER` is also abandoned, so a `KEEP_PLAYING`
     * game replaced by New game counts as won and abandoned (D-198). `tileReach[k]` grows by one
     * for every `0 <= k <= f.maxExp`, the list growing as needed (D-218).
     */
    fun finalize(records: Records, f: Finalization): Records {
        if (isFinalized(records, f.size, f.gameId)) return records
        val k = key(f.size)
        val old = records.sizes[k] ?: SizeRecord()
        val won = f.maxExp >= f.size.targetExp
        val streak = if (won) sum(old.currentWinStreak, 1) else 0
        val counted =
            old.copy(
                    gamesPlayed = sum(old.gamesPlayed, 1),
                    gamesWon = sum(old.gamesWon, if (won) 1 else 0),
                    gamesAbandoned = sum(old.gamesAbandoned, if (f.status != OVER) 1 else 0),
                    totalScore = sum(old.totalScore, f.score),
                    totalMoves = sum(old.totalMoves, f.moveCount.toLong()),
                    totalTimeMs = sum(old.totalTimeMs, f.elapsedMs),
                    totalUndos = sum(old.totalUndos, f.undoCount.toLong()),
                    tileReach = reached(old.tileReach, f.maxExp),
                    currentWinStreak = streak,
                    longestWinStreak = maxOf(old.longestWinStreak, streak),
                )
                .withBests(f.score, f.undoCount, f.maxExp)
        return records.copy(
            sizes = records.sizes + (k to counted),
            lastFinalizedGameId = records.lastFinalizedGameId + (k to f.gameId),
        )
    }

    /**
     * The D-198 display-time fold for one size: every entry of `game.pendingFinalizations` through
     * [finalize], in list order, then `game.toFinalization()` if the game is `OVER`. `null` = no
     * game.
     */
    fun displayed(records: Records, game: SavedGame?): Records {
        if (game == null) return records
        val entries = game.pendingFinalizations.fold(records, ::finalize)
        return if (game.state.status == OVER) {
            finalize(entries, game.toFinalization())
        } else {
            entries
        }
    }

    /**
     * The records as read (D-270; FEATURES.md §3.1): a size's `bestTileExp` outside
     * `0..size.maxReachableExp` (N² + 1), which no play stores and only a damaged or edited file
     * holds, reads as 0, its default, so Statistics never asks `MiniTile` for a tile it cannot
     * draw; the next `progress` or `finalize` on that size raises it again. Every other field, and
     * every key other than `"3"` … `"8"`, is kept. Returns [records] itself when nothing is out of
     * range.
     */
    fun coerceBestTiles(records: Records): Records {
        val sizes =
            records.sizes.mapValues { (k, record) ->
                val size = BoardSize.ALL.firstOrNull { key(it) == k }
                if (size == null || record.bestTileExp in 0..size.maxReachableExp) {
                    record
                } else {
                    record.copy(bestTileExp = 0)
                }
            }
        return if (sizes == records.sizes) records else records.copy(sizes = sizes)
    }

    /** Reset statistics: `Records(lastFinalizedGameId = records.lastFinalizedGameId)` (D-044). */
    fun reset(records: Records): Records =
        Records(lastFinalizedGameId = records.lastFinalizedGameId)

    /** `max(replaced?.gameId ?: 0, records.lastFinalizedGameId[key(size)] ?: 0) + 1` (D-044). */
    fun nextGameId(records: Records, size: BoardSize, replaced: SavedGame?): Long =
        maxOf(replaced?.gameId ?: 0, records.lastFinalizedGameId[key(size)] ?: 0) + 1

    /**
     * The "All sizes" tab over the keys `"3"` … `"8"` only (FEATURES.md §3.1–§3.2): counters,
     * totals and `tileReach` (element-wise) summed, bests and `bestTileExp` as maxima, streaks 0
     * (not aggregated). Any other key, such as a later `"daily:4"`, is ignored.
     */
    fun aggregate(records: Records): SizeRecord =
        BoardSize.ALL.mapNotNull { records.sizes[key(it)] }
            .fold(SizeRecord()) { acc, r ->
                SizeRecord(
                    bestScore = maxOf(acc.bestScore, r.bestScore),
                    bestScoreNoUndo = maxOf(acc.bestScoreNoUndo, r.bestScoreNoUndo),
                    bestTileExp = maxOf(acc.bestTileExp, r.bestTileExp),
                    gamesPlayed = sum(acc.gamesPlayed, r.gamesPlayed),
                    gamesWon = sum(acc.gamesWon, r.gamesWon),
                    gamesAbandoned = sum(acc.gamesAbandoned, r.gamesAbandoned),
                    totalScore = sum(acc.totalScore, r.totalScore),
                    totalMoves = sum(acc.totalMoves, r.totalMoves),
                    totalTimeMs = sum(acc.totalTimeMs, r.totalTimeMs),
                    totalUndos = sum(acc.totalUndos, r.totalUndos),
                    tileReach = elementSum(acc.tileReach, r.tileReach),
                )
            }
}

/** The `progress` arithmetic on one record: bests only ever rise (D-044). */
private fun SizeRecord.withBests(score: Long, undoCount: Int, maxExp: Int): SizeRecord =
    copy(
        bestScore = maxOf(bestScore, score),
        bestScoreNoUndo = if (undoCount == 0) maxOf(bestScoreNoUndo, score) else bestScoreNoUndo,
        bestTileExp = maxOf(bestTileExp, maxExp),
    )

/** [reach] with one more game at every index `0..maxExp`, grown to `maxExp + 1` entries (D-218). */
private fun reached(reach: List<Int>, maxExp: Int): List<Int> =
    List(maxOf(reach.size, maxExp + 1)) { k ->
        val n = reach.getOrElse(k) { 0 }
        if (k <= maxExp) sum(n, 1) else n
    }

/** Element-wise sum; the shorter list reads as 0 beyond its end. */
private fun elementSum(a: List<Int>, b: List<Int>): List<Int> =
    List(maxOf(a.size, b.size)) { k -> sum(a.getOrElse(k) { 0 }, b.getOrElse(k) { 0 }) }

/** Non-negative counters saturate instead of wrapping, like the engine's scores (D-033). */
private fun sum(a: Long, b: Long): Long {
    val s = a + b
    return if (s < 0 && a >= 0 && b >= 0) Long.MAX_VALUE else s
}

private fun sum(a: Int, b: Int): Int =
    (a.toLong() + b.toLong()).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
