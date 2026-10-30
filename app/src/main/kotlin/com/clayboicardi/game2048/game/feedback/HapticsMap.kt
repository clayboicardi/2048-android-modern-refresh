package com.clayboicardi.game2048.game.feedback

import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.game.board.MoveHaptic

/**
 * D-065's haptic priority for an accepted move, resolved once in `GameViewModel` and shipped as
 * `BoardEvent.Moved.haptic` (D-185). Pure; `HapticsController` (M3) only schedules the result.
 */
object HapticsMap {
    /** D-065 priority 3: a merge that produces 512 (2^9) or more. */
    const val BIG_MERGE_MIN_EXP = 9

    /** The first match of WIN > NEW_BEST > BIG_MERGE > MERGE, else NONE (D-065). */
    fun forMove(outcome: MoveOutcome.Moved, newBest: Boolean): MoveHaptic =
        when {
            outcome.becameWon -> MoveHaptic.WIN
            newBest -> MoveHaptic.NEW_BEST
            outcome.maxMergedExp >= BIG_MERGE_MIN_EXP -> MoveHaptic.BIG_MERGE
            outcome.mergeCount >= 1 -> MoveHaptic.MERGE
            else -> MoveHaptic.NONE
        }
}
