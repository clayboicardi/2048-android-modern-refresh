package com.clayboicardi.game2048.engine

/**
 * Full structural check (I1–I8 and I10–I12 of ARCHITECTURE.md §3.5, D-214). Used by tests and by
 * the :app decoder (ARCHITECTURE.md §4.4). No check replays the move log.
 */
object GameInvariants {
    /** Empty when the state is consistent; otherwise one human-readable message per violation. */
    fun violations(state: GameState): List<String> =
        listOfNotNull(
            boardShape(state),
            moveLogLength(state),
            snapshotSchedule(state),
            keepGoingMove(state),
            wonState(state),
            counters(state),
            startSnapshot(state),
            overState(state),
            LaterSnapshots.boards(state),
            LaterSnapshots.scores(state),
            LaterSnapshots.statuses(state),
        )

    // One condition per invariant, so a test can drive each check either way.

    /** I1. A Byte never exceeds Board.MAX_EXP, so the smallest exponent decides the range. */
    private fun boardShape(s: GameState): String? {
        val lowest = s.board.toByteArray().min().toInt()
        return if (s.board.size == s.size && lowest >= 0) {
            null
        } else {
            "I1: the board must be ${s.size} with every exponent in 0..${Board.MAX_EXP}, " +
                "is ${s.board.size} with a smallest exponent of $lowest"
        }
    }

    /** I2. Move count and packed length compared as one pair. */
    private fun moveLogLength(s: GameState): String? {
        val actual = s.moveLog.size to s.moveLog.toPackedBytes().size
        val expected = s.moveCount to MoveLog.packedSize(s.moveCount)
        return if (actual == expected) {
            null
        } else {
            "I2: the move log must be (moves, bytes) = $expected, is $actual"
        }
    }

    /** I3. Count and move numbers in one list: snapshots[k] is the state at move 32k. */
    private fun snapshotSchedule(s: GameState): String? {
        val expected =
            List(s.moveCount / Game.SNAPSHOT_INTERVAL + 1) { it * Game.SNAPSHOT_INTERVAL }
        val actual = s.snapshots.map { it.moveCount }
        return if (actual == expected) {
            null
        } else {
            "I3: snapshots must be at moves $expected, are at moves $actual"
        }
    }

    /** I4. -1 (unset) or a move in 0..moveCount; KEEP_PLAYING excludes -1. */
    private fun keepGoingMove(s: GameState): String? {
        val lowest = if (s.status == GameStatus.KEEP_PLAYING) 0 else -1
        return if (s.keepGoingAtMove >= lowest && s.keepGoingAtMove <= s.moveCount) {
            null
        } else {
            "I4: keepGoingAtMove must be in $lowest..${s.moveCount} in ${s.status}, " +
                "is ${s.keepGoingAtMove}"
        }
    }

    /** I5. */
    private fun wonState(s: GameState): String? =
        if (
            s.status != GameStatus.WON ||
                (s.keepGoingAtMove == -1 && s.board.maxExp() >= s.size.targetExp)
        ) {
            null
        } else {
            "I5: WON needs keepGoingAtMove -1 and an exponent of at least ${s.size.targetExp}; " +
                "keepGoingAtMove is ${s.keepGoingAtMove}, the largest exponent ${s.board.maxExp()}"
        }

    /** I6. */
    private fun counters(s: GameState): String? =
        if (minOf(s.score, s.undoCount.toLong()) >= 0) {
            null
        } else {
            "I6: score and undoCount must not be negative, are ${s.score} and ${s.undoCount}"
        }

    /** I7. Two spawns from the seed; the move log is never replayed. */
    private fun startSnapshot(s: GameState): String? {
        val expected = Game.newGame(s.size, s.seed).snapshots[0]
        return if (s.snapshots[0] == expected) {
            null
        } else {
            "I7: snapshots[0] must be the new game of seed ${s.seed}, $expected, " +
                "is ${s.snapshots[0]}"
        }
    }

    /** I8. */
    private fun overState(s: GameState): String? =
        if (s.status != GameStatus.OVER || !Game.canMove(s.board)) {
            null
        } else {
            "I8: OVER needs a board without a move, but ${s.board} can still move"
        }

    /**
     * I10–I12 (D-214): snapshots[k] for k >= 1, the snapshots undo rebuilds from. I7 pins
     * snapshots[0]. Each check costs O(N²) per snapshot.
     */
    private object LaterSnapshots {
        /** I10. Board.of already keeps every exponent in 0..Board.MAX_EXP. */
        fun boards(s: GameState): String? {
            val wrong = later(s).filter { k -> s.snapshots[k].board.size != s.size }
            return if (wrong.isEmpty()) {
                null
            } else {
                "I10: every snapshot board must be ${s.size}; ${list(s, wrong)} are not"
            }
        }

        /**
         * I11. 0 <= snapshots[1].score <= … <= score: the score falls only through undo, which
         * drops the later snapshots with it (D-033).
         */
        fun scores(s: GameState): String? {
            val chain = listOf(0L) + later(s).map { k -> s.snapshots[k].score } + s.score
            val fall = chain.zipWithNext().indexOfFirst { (before, after) -> before > after }
            if (fall < 0) return null
            // chain[i] is snapshots[i] for i in 1 until snapshots.size, then the state score.
            val at = if (fall + 1 == s.snapshots.size) "the state" else "snapshots[${fall + 1}]"
            return "I11: scores must not decrease from 0 through the snapshots to the state, " +
                "but fall from ${chain[fall]} to ${chain[fall + 1]} at $at"
        }

        /** I12. Only statuses an accepted move can store at move 32k. */
        fun statuses(s: GameState): String? {
            val wrong = later(s).filterNot { k -> storable(s, k) }
            return if (wrong.isEmpty()) {
                null
            } else {
                "I12: a WON snapshot needs an exponent of at least ${s.size.targetExp}, a " +
                    "KEEP_PLAYING one a keepGoingAtMove before it (is ${s.keepGoingAtMove}), an " +
                    "OVER one the last move (is ${s.moveCount}) and a board without a move; " +
                    "${list(s, wrong)} break this"
            }
        }

        /**
         * WON holds the target (I5). Keep going happens at a WON state, so it precedes every
         * KEEP_PLAYING snapshot; a WON snapshot keeps its status when Keep going follows at its own
         * move. OVER blocks every move (D-037), so an OVER snapshot is the current move.
         */
        private fun storable(s: GameState, k: Int): Boolean {
            val snapshot = s.snapshots[k]
            val move = k * Game.SNAPSHOT_INTERVAL
            return when (snapshot.status) {
                GameStatus.PLAYING -> true
                GameStatus.WON -> snapshot.board.maxExp() >= s.size.targetExp
                GameStatus.KEEP_PLAYING -> s.keepGoingAtMove in 0 until move
                GameStatus.OVER -> s.moveCount == move && !Game.canMove(snapshot.board)
            }
        }

        private fun later(s: GameState): IntRange = 1 until s.snapshots.size

        private fun list(s: GameState, ks: List<Int>): String = ks.joinToString { k ->
            "snapshots[$k] (${s.snapshots[k].status})"
        }
    }
}
