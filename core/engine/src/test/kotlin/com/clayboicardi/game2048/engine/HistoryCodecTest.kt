package com.clayboicardi.game2048.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** D-039, D-042: move-log and snapshot bytes, GameState checks (ARCHITECTURE.md §3.4, §3.5). */
class HistoryCodecTest {
    /** F07-AC21 */
    @Test
    fun rejectsMalformedHistoryBytes() {
        val fresh = Game.newGame(THREE, SEED)
        assertNoViolations(fresh)
        val good = hex(SNAPSHOT_3X3)
        assertEquals(SNAPSHOT_3X3_BYTES, good.size)
        assertEquals(fresh.snapshots[0], Snapshot.fromBytes(good, THREE))
        val badSnapshots =
            listOf(
                good.copyOf(SNAPSHOT_3X3_BYTES - 1),
                good.copyOf(SNAPSHOT_3X3_BYTES + 1),
                ByteArray(0),
                good.with(STATUS_OFFSET, 4),
                good.with(STATUS_OFFSET, 9),
                good.with(STATUS_OFFSET, -1),
                good.with(FIRST_CELL, 128),
                good.with(LAST_CELL, 255),
            )
        for ((i, bytes) in badSnapshots.withIndex()) {
            assertThrows<IllegalArgumentException>("snapshot case $i") {
                Snapshot.fromBytes(bytes, THREE)
            }
        }
        // The 30 bytes of a 3×3 snapshot have the wrong length for 4×4 (37 bytes).
        assertThrows<IllegalArgumentException> { Snapshot.fromBytes(good, FOUR) }
        // Exponent 127 is the largest legal byte.
        assertEquals(Board.MAX_EXP, Snapshot.fromBytes(good.with(FIRST_CELL, 127), THREE).board[0])

        val log = hex("E4 03")
        val badLogs: List<() -> MoveLog> =
            listOf(
                { MoveLog.fromPackedBytes(log, 4) },
                { MoveLog.fromPackedBytes(log, 9) },
                { MoveLog.fromPackedBytes(hex("E4"), 5) },
                { MoveLog.fromPackedBytes(hex("00"), 0) },
                { MoveLog.fromPackedBytes(hex("E4 07"), 5) },
                { MoveLog.fromPackedBytes(hex("E4 83"), 5) },
                { MoveLog.fromPackedBytes(hex("E4 C3"), 7) },
                { MoveLog.fromPackedBytes(ByteArray(0), -1) },
                { MoveLog.fromPackedBytes(ByteArray(0), -4) },
            )
        for ((i, decode) in badLogs.withIndex()) {
            assertThrows<IllegalArgumentException>("move log case $i") { decode() }
        }
        // Bits 0–5 of the last byte hold moves 4–6 of a 7-move log; only bits 6–7 must be 0.
        assertEquals(
            listOf(UP, RIGHT, DOWN, LEFT, LEFT, LEFT, LEFT),
            MoveLog.fromPackedBytes(hex("E4 3F"), 7).toList(),
        )
    }

    /** D-039, D-042: golden move-log and snapshot bytes */
    @Test
    fun goldenBytes() {
        val log = logOf(UP, RIGHT, DOWN, LEFT, LEFT)
        assertEquals(5, log.size)
        assertEquals(hex("E4 03").toList(), log.toPackedBytes().toList())
        assertEquals(log, MoveLog.fromPackedBytes(hex("E4 03"), 5))
        assertEquals(
            listOf(UP, RIGHT, DOWN, LEFT, LEFT),
            MoveLog.fromPackedBytes(hex("E4 03"), 5).toList(),
        )

        val fresh = Game.newGame(THREE, SEED)
        assertNoViolations(fresh)
        val snapshot = fresh.snapshots[0]
        val board = Board.of(THREE, byteArrayOf(0, 0, 1, 2, 0, 0, 0, 0, 0))
        assertEquals(Snapshot(0, 0L, NEW_GAME_RNG, GameStatus.PLAYING, board), snapshot)
        assertEquals(hex(SNAPSHOT_3X3).toList(), snapshot.toBytes().toList())
        assertEquals(snapshot, Snapshot.fromBytes(hex(SNAPSHOT_3X3), THREE))

        // Every field big-endian at its ARCHITECTURE.md §3.4 offset.
        val wide =
            Snapshot(
                moveCount = 0x01020304,
                score = 0x0102030405060708L,
                rngState = -2L,
                status = GameStatus.OVER,
                board = Board.of(THREE, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)),
            )
        val wideHex = "01020304 0102030405060708 FFFFFFFFFFFFFFFE 03 010203040506070809"
        assertEquals(hex(wideHex).toList(), wide.toBytes().toList())
        assertEquals(wide, Snapshot.fromBytes(hex(wideHex), THREE))
        for (status in GameStatus.entries) {
            val bytes = snapshot.copy(status = status).toBytes()
            assertEquals(status.ordinal, bytes[STATUS_OFFSET].toInt())
            assertEquals(snapshot.copy(status = status), Snapshot.fromBytes(bytes, THREE))
        }
        // 21 + N² bytes: 85 for 8×8.
        val eight = Game.newGame(EIGHT, SEED)
        assertNoViolations(eight)
        assertEquals(EIGHT_BY_EIGHT_BYTES, eight.snapshots[0].toBytes().size)
    }

    /** D-039: move-log get, append, packing, equality and truncate */
    @Test
    fun moveLogOperations() {
        assertEquals(0, MoveLog.EMPTY.size)
        assertEquals(emptyList<Direction>(), MoveLog.EMPTY.toList())
        assertEquals(0, MoveLog.EMPTY.toPackedBytes().size)
        assertEquals(MoveLog.EMPTY, MoveLog.fromPackedBytes(ByteArray(0), 0))
        val packedSizes = listOf(0 to 0, 1 to 1, 3 to 1, 4 to 1, 5 to 2, 8 to 2, 9 to 3)
        for ((moves, bytes) in packedSizes + (LONG_LOG to LONG_LOG_BYTES)) {
            assertEquals(bytes, MoveLog.packedSize(moves), "packedSize($moves)")
        }

        val one = MoveLog.EMPTY.append(UP)
        val two = one.append(LEFT)
        assertEquals(listOf(UP), one.toList())
        assertEquals(listOf(UP, LEFT), two.toList())
        assertEquals(LEFT, two[1])
        assertEquals(logOf(UP, LEFT), two)
        assertEquals(logOf(UP, LEFT).hashCode(), two.hashCode())
        // Same packed byte 0x00, different sizes.
        assertNotEquals(one, logOf(UP, UP))
        assertNotEquals(one, logOf(RIGHT))
        assertNotEquals(MoveLog.EMPTY, one)

        val moves = List(LONG_LOG) { Direction.entries[(it * 7 + it / 5) % 4] }
        val log = logOf(moves)
        assertEquals(LONG_LOG, log.size)
        assertEquals(moves, log.toList())
        for (i in moves.indices) {
            assertEquals(moves[i], log[i], "move $i")
        }
        assertEquals(LONG_LOG_BYTES, log.toPackedBytes().size)
        assertEquals(log, MoveLog.fromPackedBytes(log.toPackedBytes(), LONG_LOG))
        assertEquals(logOf(moves.take(LONG_LOG - 1)), log.truncate(LONG_LOG - 1))
        checkTruncate()
    }

    /** D-039: GameState init rejects inconsistent counts */
    @Test
    fun gameStateInitRejectsInconsistentCounts() {
        val fresh = Game.newGame(FOUR, SEED)
        val played = playDlru(EIGHT, Game.SNAPSHOT_INTERVAL)
        assertNoViolations(fresh)
        assertNoViolations(played)
        assertEquals(2, played.snapshots.size)
        val inconsistent: List<() -> GameState> =
            listOf(
                { fresh.copy(board = Board.empty(THREE)) },
                { fresh.copy(moveCount = -1) },
                { fresh.copy(moveCount = 1) },
                { fresh.copy(moveLog = MoveLog.EMPTY.append(UP)) },
                { fresh.copy(snapshots = emptyList()) },
                { fresh.copy(snapshots = fresh.snapshots + fresh.snapshots) },
                { fresh.copy(keepGoingAtMove = -2) },
                { fresh.copy(keepGoingAtMove = 1) },
                { fresh.copy(score = -1L) },
                { fresh.copy(undoCount = -1) },
                { played.copy(snapshots = played.snapshots.take(1)) },
                { played.copy(keepGoingAtMove = Game.SNAPSHOT_INTERVAL + 1) },
                { played.copy(moveLog = played.moveLog.truncate(Game.SNAPSHOT_INTERVAL - 1)) },
            )
        for ((i, build) in inconsistent.withIndex()) {
            assertThrows<IllegalArgumentException>("case $i") { build() }
        }
    }

    /** D-039: GameInvariants reports history violations (I3, I7) */
    @Test
    fun invariantsReportHistoryViolations() {
        val fresh = Game.newGame(FOUR, SEED)
        val played = playDlru(EIGHT, Game.SNAPSHOT_INTERVAL)
        assertNoViolations(fresh)
        assertNoViolations(played)
        val start = fresh.snapshots[0]
        val (first, second) = played.snapshots
        assertEquals(Game.SNAPSHOT_INTERVAL, second.moveCount)
        val broken =
            listOf(
                // I3: snapshots[k] must be the state at move 32k.
                played.copy(snapshots = listOf(first, second.copy(moveCount = 31))),
                played.copy(snapshots = listOf(first, second.copy(moveCount = 64))),
                played.copy(snapshots = listOf(first.copy(moveCount = 1), second)),
                // I7: snapshots[0] must be newGame(size, seed)'s.
                fresh.copy(snapshots = listOf(start.copy(rngState = start.rngState + 1))),
                fresh.copy(snapshots = listOf(start.copy(board = Board.empty(FOUR)))),
                fresh.copy(snapshots = listOf(start.copy(score = 4L))),
                fresh.copy(snapshots = listOf(start.copy(status = GameStatus.KEEP_PLAYING))),
                fresh.copy(seed = SEED + 1),
                played.copy(seed = SEED + 1),
            )
        for ((i, state) in broken.withIndex()) {
            assertTrue(GameInvariants.violations(state).isNotEmpty(), "case $i")
        }
    }

    /** D-214: GameInvariants reports later snapshots that undo would rebuild wrong (I10–I12) */
    @Test
    fun invariantsReportSnapshotViolations() {
        // The 40-move 4×4 game of the M1 review: undo from move 40 rebuilds from snapshots[1].
        val played = playDlru(FOUR, FORTY)
        val eight = playDlru(EIGHT, SEVENTY)
        assertNoViolations(played)
        assertNoViolations(eight)
        val second = played.snapshots[1]
        val (start, at32, at64) = eight.snapshots
        assertTrue(at32.score > 0)
        // Each case breaks exactly the one invariant named with it.
        val broken =
            listOf(
                // A snapshot board of another size.
                "I10" to played.withSecond(second.copy(board = Game.newGame(THREE, SEED).board)),
                // Undo added the deltas to -100, which reads as an overflow: Long.MAX_VALUE.
                "I11" to played.withSecond(second.copy(score = -100L)),
                "I11" to played.withSecond(second.copy(score = played.score + 1)),
                "I11" to
                    eight.copy(snapshots = listOf(start, at32, at64.copy(score = at32.score - 1))),
                // Undo returned WON without the target (I5), KEEP_PLAYING with keepGoingAtMove -1
                // (I4; with Keep going at move 32, once undo reaches move 32), or OVER on a board
                // that can move (I8); an OVER snapshot with moves after it makes undo throw.
                "I12" to played.withSecond(second.copy(status = GameStatus.WON)),
                "I12" to played.withSecond(second.copy(status = GameStatus.KEEP_PLAYING)),
                "I12" to
                    played
                        .copy(keepGoingAtMove = Game.SNAPSHOT_INTERVAL)
                        .withSecond(second.copy(status = GameStatus.KEEP_PLAYING)),
                "I12" to played.withSecond(second.copy(status = GameStatus.OVER)),
                "I12" to
                    played.withSecond(second.copy(status = GameStatus.OVER, board = board(LOCKED))),
                "I12" to
                    endsAt32().let {
                        it.withSecond(it.snapshots[1].copy(status = GameStatus.OVER))
                    },
            )
        for ((i, case) in broken.withIndex()) {
            val (id, state) = case
            val ids = GameInvariants.violations(state).map { it.substringBefore(':') }
            assertEquals(listOf(id), ids, "case $i")
        }
    }

    /** D-214: snapshot statuses and scores at the bounds of I11 and I12 are consistent */
    @Test
    fun invariantsAcceptSnapshotsAtTheirBounds() {
        val played = playDlru(FOUR, FORTY)
        val second = played.snapshots[1]
        val kept = played.copy(status = GameStatus.KEEP_PLAYING)
        val over = endsAt32().copy(status = GameStatus.OVER, board = board(LOCKED))
        val valid =
            listOf(
                // Equal scores along the chain, and a snapshot score of 0.
                played.withSecond(second.copy(score = played.score)),
                played.withSecond(second.copy(score = 0L)),
                // Keep going one move before the snapshot, or at its own move after a WON one.
                kept
                    .copy(keepGoingAtMove = Game.SNAPSHOT_INTERVAL - 1)
                    .withSecond(second.copy(status = GameStatus.KEEP_PLAYING)),
                kept
                    .copy(keepGoingAtMove = Game.SNAPSHOT_INTERVAL)
                    .withSecond(second.copy(status = GameStatus.WON, board = board(WIN_TILE))),
                // OVER at the last move, on a board without a move.
                over.withSecond(
                    over.snapshots[1].copy(status = GameStatus.OVER, board = over.board),
                ),
            )
        for (state in valid) {
            assertNoViolations(state)
        }
    }

    private fun checkTruncate() {
        val five = logOf(UP, RIGHT, DOWN, LEFT, LEFT)
        val three = five.truncate(3)
        assertEquals(logOf(UP, RIGHT, DOWN), three)
        assertEquals(3, three.size)
        // 0x24: moves 0–2 in bits 0–5, the unused high bits cleared.
        assertEquals(hex("24").toList(), three.toPackedBytes().toList())
        assertEquals(three, MoveLog.fromPackedBytes(three.toPackedBytes(), 3))
        assertEquals(hex("E4").toList(), five.truncate(4).toPackedBytes().toList())
        assertEquals(five, five.truncate(5))
        assertEquals(MoveLog.EMPTY, five.truncate(0))
        assertEquals(0, five.truncate(0).toPackedBytes().size)
        assertEquals(listOf(UP, RIGHT, DOWN, LEFT, LEFT), five.toList())
        assertEquals(logOf(UP, RIGHT, DOWN, DOWN), three.append(DOWN))
    }

    companion object {
        private const val SEED = 0x2048L
        private const val NEW_GAME_RNG = 0x78DDE6E5FD2A109CL
        private const val SNAPSHOT_3X3 =
            "00000000 0000000000000000 78DDE6E5FD2A109C 00 000001020000000000"
        private const val SNAPSHOT_3X3_BYTES = 30
        private const val EIGHT_BY_EIGHT_BYTES = 85
        private const val STATUS_OFFSET = 20
        private const val FIRST_CELL = 21
        private const val LAST_CELL = 29
        private const val LONG_LOG = 10_000
        private const val LONG_LOG_BYTES = 2_500
        private const val FORTY = 40
        private const val SEVENTY = 70
        private const val LOCKED = "1 2 1 2 / 2 1 2 1 / 1 2 1 2 / 2 1 2 1"
        private const val WIN_TILE = "11 0 0 0 / 0 0 0 0 / 0 0 0 0 / 0 0 0 0"
        private val THREE = BoardSize.of(3)
        private val FOUR = BoardSize.of(4)
        private val EIGHT = BoardSize.of(8)
        private val UP = Direction.UP
        private val RIGHT = Direction.RIGHT
        private val DOWN = Direction.DOWN
        private val LEFT = Direction.LEFT

        /** ARCHITECTURE.md §9.4 policy: the first accepted of DOWN, LEFT, RIGHT, UP. */
        private val DLRU = listOf(DOWN, LEFT, RIGHT, UP)

        private fun hex(text: String): ByteArray =
            text.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        private fun ByteArray.with(index: Int, value: Int): ByteArray =
            copyOf().also { it[index] = value.toByte() }

        private fun logOf(moves: List<Direction>): MoveLog =
            moves.fold(MoveLog.EMPTY) { log, direction -> log.append(direction) }

        private fun logOf(vararg moves: Direction): MoveLog = logOf(moves.asList())

        /** Row-major exponents from "a b c / d e f / …"; the size follows from the cell count. */
        private fun board(text: String): Board {
            val cells = text.split(' ').filter { it != "/" }.map(String::toByte).toByteArray()
            val n = (BoardSize.MIN_N..BoardSize.MAX_N).first { it * it == cells.size }
            return Board.of(BoardSize.of(n), cells)
        }

        /** This state with snapshots[1] replaced; snapshots[0] and the rest are kept. */
        private fun GameState.withSecond(snapshot: Snapshot): GameState =
            copy(snapshots = snapshots.toMutableList().also { it[1] = snapshot })

        /** The 4×4 DLRU game at move 32, so its last snapshot is its own state. */
        private fun endsAt32(): GameState = playDlru(FOUR, Game.SNAPSHOT_INTERVAL)

        /** A real game: DLRU moves from newGame(size, 0x2048), keeping going on a win. */
        private fun playDlru(size: BoardSize, moves: Int): GameState {
            var state = Game.newGame(size, SEED)
            while (state.moveCount < moves) {
                val moved =
                    checkNotNull(
                        DLRU.firstNotNullOfOrNull { Game.move(state, it) as? MoveOutcome.Moved },
                    ) {
                        "no accepted move at ${state.moveCount}"
                    }
                state = if (moved.becameWon) Game.keepGoing(moved.state) else moved.state
            }
            return state
        }

        private fun assertNoViolations(state: GameState) {
            assertEquals(emptyList<String>(), GameInvariants.violations(state))
        }
    }
}
