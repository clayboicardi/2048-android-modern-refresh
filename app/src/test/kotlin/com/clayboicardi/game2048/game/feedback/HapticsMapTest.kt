package com.clayboicardi.game2048.game.feedback

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.engine.Spawn
import com.clayboicardi.game2048.game.board.MoveHaptic
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The haptic map (D-065, D-185). Robolectric because M3 adds the `HapticsController` timing methods
 * to this class (FEATURES.md §8); the M2 method is pure.
 */
@RunWith(AndroidJUnit4::class)
class HapticsMapTest {
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
