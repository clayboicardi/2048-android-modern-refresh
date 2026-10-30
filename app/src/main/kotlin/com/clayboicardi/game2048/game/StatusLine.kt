package com.clayboicardi.game2048.game

import android.content.res.Resources
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.TileLabels
import com.clayboicardi.game2048.ui.components.formatCount
import com.clayboicardi.game2048.ui.theme.GameDimens
import java.util.Locale

/** The TalkBack status line (relied on by the accessibility tests). */
const val STATUS_LINE_TAG = "game:statusLine"

/**
 * The TalkBack status line (DESIGN_SYSTEM.md §6.6; FEATURES.md F17 item 5; D-081, D-202): a polite
 * live region in `bodyMedium`, `onSurfaceVariant`, at least two lines (`StatusLineMinHeight`) and
 * wrapping beyond them, composed only while touch exploration is on, and hidden from accessibility
 * while it is still blank (D-252). It reads the latest of
 * - an accepted move of [board] (its arrangement scope's presented board): "{moved} {merged}
 *   {spawned} {score}";
 * - an undo: `a11y_undone`; New game: `a11y_new_game` and one `a11y_spawned` per start tile; a size
 *   switch: `a11y_size_switched`, with the score of [header];
 * - an invalid move ([nudge], `GameViewModel.nudge`): `a11y_blocked_*`.
 *
 * Sentences are joined by one space (L10N_STORE_LEGAL.md §1.3 rule 3). A board composed already
 * settled (`Restored`: a load, a recreation, a new arrangement) and the nudge present at the first
 * composition announce nothing, so nothing already read is read again. A status whose text equals
 * the text shown (a second blocked move in the same direction, two undos to the same score) blanks
 * the line for one frame and then shows the text again, so the live region's text changes and the
 * repeat is read (D-252). Without the three states it observes nothing; the chrome probe
 * (DESIGN_SYSTEM.md §5.3) passes none, with [text] to show. The arrangements give it the probe's
 * reservation ([ReservedStatusLine]) as its minimum height in [modifier], so a longer announcement
 * moves neither the board nor the pad (D-265).
 */
@Composable
fun StatusLine(
    board: State<BoardUi>?,
    header: State<HeaderUi>?,
    nudge: State<Nudge?>?,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val status = remember { StatusState() }
    if (board != null && header != null && nudge != null) {
        ObserveStatus(status, board, header, nudge)
    }
    val shown = text ?: status.text(resources, locale)
    val hidden = shown.isEmpty() && !status.announced
    Text(
        text = shown,
        modifier =
            modifier
                .testTag(STATUS_LINE_TAG)
                .fillMaxWidth()
                .heightIn(min = GameDimens.StatusLineMinHeight)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    // Before its first announcement the line is blank: no TalkBack stop that reads
                    // nothing (ATF SpeakableTextPresentCheck). It is the same live region node
                    // throughout, so its first text is announced like every later one, and the
                    // blank frame before a repeat keeps it in the tree.
                    if (hidden) hideFromAccessibility()
                },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        minLines = STATUS_MIN_LINES,
    )
}

/**
 * Posts the status events of [board] (its arrangement scope's presented board, with [header]'s
 * score) and [nudge] to [status], and shows a held repeat on the frame after its blank one.
 */
@Composable
private fun ObserveStatus(
    status: StatusState,
    board: State<BoardUi>,
    header: State<HeaderUi>,
    nudge: State<Nudge?>,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val presented = board.value
    val firstNudge = remember { nudge.value?.id }
    LaunchedEffect(presented.moveId) {
        if (presented.event != BoardEvent.Restored) {
            status.post(StatusEvent.Board(presented, header.value.score), resources, locale)
        }
    }
    val blocked = nudge.value
    LaunchedEffect(blocked?.id) {
        if (blocked != null && blocked.id != firstNudge) {
            status.post(StatusEvent.Blocked(blocked.direction, blocked.id), resources, locale)
        }
    }
    // The blank frame was composed (this effect starts when it is applied); the next frame shows
    // the repeated text, so the accessibility layer sees the text leave and return.
    val repeated = status.repeated
    LaunchedEffect(repeated) {
        if (repeated != null) {
            withFrameNanos {}
            status.release(repeated)
        }
    }
}

/**
 * What the status line shows: the latest event, or nothing for one frame while [repeated] holds an
 * event whose text equals the one shown (D-252).
 */
@Stable
private class StatusState {
    private var latest by mutableStateOf<StatusEvent?>(null)

    /** A status with the text already shown, held back while the line is blank for one frame. */
    var repeated by mutableStateOf<StatusEvent?>(null)
        private set

    /** True once the line has had a text: from then on it stays in the accessibility tree. */
    val announced: Boolean
        get() = latest != null

    fun post(event: StatusEvent, resources: Resources, locale: Locale) {
        val shownEvent = repeated ?: latest
        val same =
            shownEvent != null &&
                describe(event, resources, locale) == describe(shownEvent, resources, locale)
        if (same) {
            repeated = event
        } else {
            repeated = null
            latest = event
        }
    }

    /** Shows [event], held as a repeat, once its blank frame was drawn. */
    fun release(event: StatusEvent) {
        if (repeated == event) {
            latest = event
            repeated = null
        }
    }

    fun text(resources: Resources, locale: Locale): String {
        val event = latest
        return if (repeated != null || event == null) "" else describe(event, resources, locale)
    }
}

/**
 * What the status line describes: a presented board change, or an invalid move ([nudgeId] tells two
 * blocked moves in one direction apart).
 */
@Immutable
private sealed interface StatusEvent {
    data class Board(val board: BoardUi, val score: Long) : StatusEvent

    data class Blocked(val direction: Direction, val nudgeId: Long) : StatusEvent
}

/** The sentences of [event] in [resources]' locale, joined by one space. */
private fun describe(event: StatusEvent, resources: Resources, locale: Locale): String =
    when (event) {
        is StatusEvent.Blocked -> resources.getString(blockedText(event.direction))
        is StatusEvent.Board -> boardSentences(event.board, event.score, resources, locale)
    }

private fun boardSentences(
    board: BoardUi,
    score: Long,
    resources: Resources,
    locale: Locale,
): String {
    val n = board.size.n
    return when (val event = board.event) {
        is BoardEvent.Moved ->
            listOfNotNull(
                    resources.getString(movedText(event.direction)),
                    mergedSentence(event.mergedExps, resources, locale),
                    spawnedSentence(board, event.spawnIndex, resources),
                    resources.getString(R.string.a11y_score, formatCount(event.scoreAfter, locale)),
                )
                .joinToString(SENTENCE_GAP)
        BoardEvent.Undo -> resources.getString(R.string.a11y_undone, formatCount(score, locale))
        BoardEvent.NewGame ->
            (listOf(resources.getString(R.string.a11y_new_game)) +
                    board.cells.indices
                        .filter { board.cells[it] != 0 }
                        .map { spawnedSentence(board, it, resources) })
                .joinToString(SENTENCE_GAP)
        BoardEvent.SizeSwitched ->
            resources.getString(R.string.a11y_size_switched, "$n×$n", formatCount(score, locale))
        BoardEvent.Restored -> ""
    }
}

/**
 * `a11y_merged_*` for the created tiles [mergedExps], largest first as prose numbers: up to three
 * named, then the count of the rest (`a11y_merged_more`); `null` without a merge.
 */
private fun mergedSentence(mergedExps: List<Int>, resources: Resources, locale: Locale): String? {
    val values = mergedExps.sortedDescending().map { proseValue(it, locale) }
    return when (values.size) {
        0 -> null
        1 -> resources.getString(R.string.a11y_merged_1, values[0])
        2 -> resources.getString(R.string.a11y_merged_2, values[0], values[1])
        MERGES_NAMED -> resources.getString(R.string.a11y_merged_3, values[0], values[1], values[2])
        else -> {
            val more = values.size - MERGES_NAMED
            resources.getQuantityString(
                R.plurals.a11y_merged_more,
                more,
                values[0],
                values[1],
                values[2],
                formatCount(more.toLong(), locale),
            )
        }
    }
}

/** `a11y_spawned` for the tile at [index]: its value as a game numeral, 1-based row and column. */
private fun spawnedSentence(board: BoardUi, index: Int, resources: Resources): String {
    val n = board.size.n
    return resources.getString(
        R.string.a11y_spawned,
        TileLabels.fullDecimal(board.cells[index]),
        (index / n + 1).toString(),
        (index % n + 1).toString(),
    )
}

/** A tile value as a prose number (L10N_STORE_LEGAL.md §1.10): grouped, Latin digits. */
private fun proseValue(exp: Int, locale: Locale): String =
    if (exp < Long.SIZE_BITS - 1) formatCount(1L shl exp, locale) else TileLabels.fullDecimal(exp)

private fun movedText(direction: Direction): Int =
    when (direction) {
        Direction.UP -> R.string.a11y_moved_up
        Direction.DOWN -> R.string.a11y_moved_down
        Direction.LEFT -> R.string.a11y_moved_left
        Direction.RIGHT -> R.string.a11y_moved_right
    }

private fun blockedText(direction: Direction): Int =
    when (direction) {
        Direction.UP -> R.string.a11y_blocked_up
        Direction.DOWN -> R.string.a11y_blocked_down
        Direction.LEFT -> R.string.a11y_blocked_left
        Direction.RIGHT -> R.string.a11y_blocked_right
    }

/**
 * The status line at its reservation (DESIGN_SYSTEM.md §5.3, §6.6; D-265): as tall as the tallest
 * [StatusLine] showing one of [longestStatusTexts] for [digits] at the width it is given, so at
 * least two lines. It draws nothing; the chrome probe measures it.
 */
@Composable
internal fun ReservedStatusLine(digits: Int, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val texts =
        remember(resources, locale, digits) { longestStatusTexts(resources, locale, digits) }
    Layout(content = { texts.forEach { StatusLine(null, null, null, text = it) } }, modifier) {
        measurables,
        constraints ->
        val height = measurables.maxOf { it.measure(constraints).height }
        layout(constraints.minWidth, height) {}
    }
}

/**
 * The status texts that need the most room (DESIGN_SYSTEM.md §5.3, §6.6; D-265), built from
 * [resources]' strings for [locale] with the widest values a game whose larger of Score and Best
 * has [digits] digits can announce:
 * - every score, and every merged value (a created tile, so never above the score after its move),
 *   as the largest number of [digits] digits in prose format;
 * - a spawned 4 at row 8, column 8, and Board 8×8: the largest of every size, so a size switch
 *   keeps the reservation;
 * - after each of the four moved sentences, each merged sentence: `a11y_merged_1` to `_3` and
 *   `a11y_merged_more` in every plural form that 1 to 29 further merges select (an 8×8 move merges
 *   at most 32 pairs), each with the count 29;
 * - Move undone, New game with its two start tiles, the size switch and the four blocked moves.
 *
 * Digits are Latin and of one width in the status font, so no status of such a game wraps to more
 * lines than the tallest of these at the same width.
 */
internal fun longestStatusTexts(resources: Resources, locale: Locale, digits: Int): List<String> {
    // The largest number of `digits` digits, at most Long.MAX_VALUE.
    val count = digits.coerceAtLeast(1)
    val largest =
        if (count >= Long.MAX_VALUE.toString().length) Long.MAX_VALUE
        else (1..count).fold(0L) { number, _ -> number * DECIMAL + DIGIT_NINE }
    val value = formatCount(largest, locale)
    val side = BoardSize.MAX_N
    val row = side.toString()
    val spawned =
        resources.getString(
            R.string.a11y_spawned,
            TileLabels.fullDecimal(WIDEST_SPAWN_EXP),
            row,
            row,
        )
    val score = resources.getString(R.string.a11y_score, value)
    val furthest = side * (side / 2) - MERGES_NAMED
    val more =
        (1..furthest).map { quantity ->
            resources.getQuantityString(
                R.plurals.a11y_merged_more,
                quantity,
                value,
                value,
                value,
                formatCount(furthest.toLong(), locale),
            )
        }
    val merged =
        listOf(
            resources.getString(R.string.a11y_merged_1, value),
            resources.getString(R.string.a11y_merged_2, value, value),
            resources.getString(R.string.a11y_merged_3, value, value, value),
        ) + more.distinct()
    val moved =
        Direction.entries.flatMap { direction ->
            val sentence = resources.getString(movedText(direction))
            merged.map { listOf(sentence, it, spawned, score).joinToString(SENTENCE_GAP) }
        }
    val others =
        listOf(
            resources.getString(R.string.a11y_undone, value),
            (listOf(resources.getString(R.string.a11y_new_game)) +
                    List(Game.START_TILES) { spawned })
                .joinToString(SENTENCE_GAP),
            resources.getString(R.string.a11y_size_switched, "$side×$side", value),
        ) + Direction.entries.map { resources.getString(blockedText(it)) }
    return (moved + others).distinct()
}

/** The one join between whole sentences (L10N_STORE_LEGAL.md §1.3 rule 3). */
private const val SENTENCE_GAP = " "

/** The merges `a11y_merged_more` names before it counts the rest. */
private const val MERGES_NAMED = 3

/** The reserved two lines (DESIGN_SYSTEM.md §5.3, §6.6). */
private const val STATUS_MIN_LINES = 2

/** A spawned 4: as wide as a 2 in the status font. */
private const val WIDEST_SPAWN_EXP = 2

private const val DECIMAL = 10L
private const val DIGIT_NINE = 9L
