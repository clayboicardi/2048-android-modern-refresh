package com.clayboicardi.game2048.stats

import androidx.compose.runtime.Immutable
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.RecordsMath
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.ui.components.formatCount
import com.clayboicardi.game2048.ui.components.formatElapsed
import com.clayboicardi.game2048.ui.components.formatPercent
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The Statistics tabs in order (DESIGN_SYSTEM.md §6.9): index 0 is "All sizes" (`null`), then 3×3 …
 * 8×8. A tab is saved by its index.
 */
val STATS_TABS: ImmutableList<BoardSize?> = (listOf(null) + BoardSize.ALL).toImmutableList()

/** The index in [STATS_TABS] of the N×N tab, N in 3..8. */
fun statsTabIndex(n: Int): Int = STATS_TABS.indexOfFirst { it?.n == n }

/**
 * What the Statistics screen shows (ARCHITECTURE.md §4.7; D-198): the tab it opens on (the current
 * board size, `settings.lastBoardSize`) and one `SizeRecord` per [STATS_TABS] entry, taken from the
 * records with the display-time fold of every size's saved game applied (never persisted): the
 * aggregate first (`RecordsMath.aggregate`), then each size's record, all-zero when absent.
 */
@Immutable
data class StatsUi(val initialTab: Int, val tabs: ImmutableList<SizeRecord>) {
    /** Tab [index] formatted for [locale] (DESIGN_SYSTEM.md §6.9; FEATURES.md §3.2). */
    fun tab(index: Int, locale: Locale): StatsTabUi = statsTabUi(tabs[index], locale)

    companion object {
        /** The tabs of [displayed], the folded records, opening on size [currentSize]. */
        fun of(displayed: Records, currentSize: Int): StatsUi =
            StatsUi(
                initialTab = statsTabIndex(currentSize),
                tabs =
                    (listOf(RecordsMath.aggregate(displayed)) +
                            BoardSize.ALL.map {
                                displayed.sizes[RecordsMath.key(it)] ?: SizeRecord()
                            })
                        .toImmutableList(),
            )
    }
}

/**
 * One Statistics tab as shown (DESIGN_SYSTEM.md §6.9): [empty] when every field of its record is 0,
 * and otherwise the hero (best score, best tile exponent) and the rows' values, each already
 * formatted: prose counts with `formatCount`, the win rate with `formatPercent`, the play time with
 * `formatElapsed`, all in Latin digits (D-103). The Wins row joins [wins] and [winRate] with
 * `stats_wins_value`; [averageScore] is "–" before any finalized game.
 */
@Immutable
data class StatsTabUi(
    val empty: Boolean,
    val bestScore: String,
    val bestTileExp: Int,
    val gamesPlayed: String,
    val wins: String,
    val winRate: String,
    val bestNoUndo: String,
    val averageScore: String,
    val totalMoves: String,
    val timePlayed: String,
    val undosUsed: String,
)

/**
 * [record] formatted for [locale] (FEATURES.md §3.2): the win rate is `gamesWon / gamesPlayed`
 * ("0%" with no game) and the average `totalScore / gamesPlayed`, floored ("–" with no game).
 */
fun statsTabUi(record: SizeRecord, locale: Locale): StatsTabUi {
    val played = record.gamesPlayed.toLong()
    return StatsTabUi(
        empty = record.isEmpty(),
        bestScore = formatCount(record.bestScore, locale),
        bestTileExp = record.bestTileExp,
        gamesPlayed = formatCount(played, locale),
        wins = formatCount(record.gamesWon.toLong(), locale),
        winRate = formatPercent(record.gamesWon.toLong(), played, locale),
        bestNoUndo = formatCount(record.bestScoreNoUndo, locale),
        averageScore =
            if (played > 0) formatCount(record.totalScore / played, locale) else NO_AVERAGE,
        totalMoves = formatCount(record.totalMoves, locale),
        timePlayed = formatElapsed(record.totalTimeMs),
        undosUsed = formatCount(record.totalUndos, locale),
    )
}

/** The empty-state test (FEATURES.md §3.2): every field 0; `tileReach` holds only zeros. */
private fun SizeRecord.isEmpty(): Boolean =
    copy(tileReach = emptyList()) == SizeRecord() && tileReach.all { it == 0 }

/** The Average score with no finalized game (U+2013, FEATURES.md §3.2). */
private const val NO_AVERAGE = "–"
