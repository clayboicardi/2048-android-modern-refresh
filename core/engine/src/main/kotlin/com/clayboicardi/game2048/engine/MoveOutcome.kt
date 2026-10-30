package com.clayboicardi.game2048.engine

/** One occupied pre-move cell: where it went and, for a merge source, the merge cell (D-021). */
data class Transition(val fromIndex: Int, val toIndex: Int, val mergedIntoIndex: Int?)

/** The tile spawned after an accepted move (D-034). */
data class Spawn(val index: Int, val exp: Int)

/** Result of [Game.move] (D-021). */
sealed interface MoveOutcome {
    data class Moved(
        val state: GameState,
        /** One per occupied cell of the pre-move board. */
        val transitions: List<Transition>,
        /** Always present after an accepted move (ARCHITECTURE.md §3.5 I9). */
        val spawn: Spawn,
        val scoreDelta: Long,
        val mergeCount: Int,
        /** 0 when mergeCount == 0. */
        val maxMergedExp: Int,
        val becameWon: Boolean,
        val becameOver: Boolean,
    ) : MoveOutcome

    data class Invalid(val direction: Direction) : MoveOutcome

    data class Blocked(val status: GameStatus) : MoveOutcome
}
