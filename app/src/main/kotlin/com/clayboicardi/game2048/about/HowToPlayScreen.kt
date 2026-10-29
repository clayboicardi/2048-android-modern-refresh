package com.clayboicardi.game2048.about

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.ui.components.MiniTile
import com.clayboicardi.game2048.ui.components.SubScreenScaffold
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import com.clayboicardi.game2048.ui.theme.LocalTileSkin
import com.clayboicardi.game2048.ui.theme.tileRadius

/** How to play's scrolling column. */
const val HOW_TO_PLAY_CONTENT_TAG = "htp:content"

/** The merge illustration: one semantics node, `htp_merging_illustration_cd`. */
const val HTP_MERGE_TAG = "htp:merge"

/**
 * The [index]-th mini tile of the merge illustration, in the unmerged tree: 0–3 are the "2 2 2 2"
 * before, 4–5 the "4 4" after.
 */
fun htpMergeTileTag(index: Int): String = "htp:mergeTile:$index"

/** The body text of the section headed by [header] (a How to play section key, e.g. `htp_goal`). */
fun htpBodyTag(@StringRes header: Int): String = "htp:body:$header"

/** The board-size table's row of the N×N size (merged: "N×N", "Goal …"). */
fun htpSizeRowTag(n: Int): String = "htp:size:$n"

/** The key table's [index]-th row (merged: label, keys): Move, Undo, New game. */
fun htpKeyRowTag(index: Int): String = "htp:key:$index"

/**
 * The How to play destination (`HowToPlayKey`; DESIGN_SYSTEM.md §6.10; FEATURES.md F23; D-009,
 * D-053): `SubScreenScaffold` titled `how_to_play`, whose back arrow calls [onBack], over one
 * scrolling column (at most `ContentMaxWidth` wide, the scaffold's column) of eight sections, each
 * a heading and `bodyLarge` text: Goal (2048, and 256 on 3×3), Moving, Merging with the forced-LTR
 * merge illustration, New tiles (the 90/10 spawn), Board sizes with the 3×3 … 8×8 goal table, Undo,
 * Keyboard and controllers (D-053's keys, `KeyMap`) and TalkBack. Every text is a string resource;
 * the sizes ("$n×$n") and the goals are game numerals built in code (D-103).
 */
@Composable
fun HowToPlayScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(
        title = stringResource(R.string.how_to_play),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag(HOW_TO_PLAY_CONTENT_TAG)
                .padding(bottom = GameDimens.Space24),
        ) {
            Section(
                R.string.htp_goal,
                stringResource(
                    R.string.htp_goal_body,
                    (1L shl BoardSize.TARGET_EXP_DEFAULT).toString(),
                    (1L shl BoardSize.TARGET_EXP_3X3).toString(),
                ),
            )
            Section(R.string.htp_moving, stringResource(R.string.htp_moving_body))
            Section(R.string.htp_merging, stringResource(R.string.htp_merging_body))
            MergeIllustration()
            Section(R.string.htp_new_tiles, stringResource(R.string.htp_new_tiles_body))
            // The board-size table (F23-AC4): "N×N" and `board_size_goal` for N = 3 … 8.
            InfoSectionHeader(stringResource(R.string.htp_board_sizes))
            for (size in BoardSize.ALL) {
                TableRow(
                    label = "${size.n}×${size.n}",
                    value =
                        stringResource(
                            R.string.board_size_goal,
                            (1L shl size.targetExp).toString(),
                        ),
                    modifier = Modifier.testTag(htpSizeRowTag(size.n)),
                )
            }
            InfoBody(
                stringResource(R.string.htp_board_sizes_body),
                Modifier.padding(top = GameDimens.Space8)
                    .testTag(htpBodyTag(R.string.htp_board_sizes)),
            )
            Section(R.string.htp_undo, stringResource(R.string.htp_undo_body))
            // D-053's key table (F23-AC5): the labels reuse `undo` and `new_game`; the key strings
            // keep their Latin letters in every locale.
            InfoSectionHeader(stringResource(R.string.htp_keys))
            KEY_ROWS.forEachIndexed { index, (label, keys) ->
                TableRow(
                    label = stringResource(label),
                    value = stringResource(keys),
                    modifier = Modifier.testTag(htpKeyRowTag(index)),
                    valueWeight = KEY_VALUE_WEIGHT,
                )
            }
            Section(R.string.htp_accessibility, stringResource(R.string.htp_accessibility_body))
        }
    }
}

/** A section: the heading [header] over [body], tagged [htpBodyTag]. */
@Composable
private fun Section(@StringRes header: Int, body: String) {
    InfoSectionHeader(stringResource(header))
    InfoBody(body, Modifier.testTag(htpBodyTag(header)))
}

/** A two-column table row, spoken as one item: [label], then [value]. */
@Composable
private fun TableRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueWeight: Float = 1f,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = GameDimens.Space16, vertical = GameDimens.Space4),
        horizontalArrangement = Arrangement.spacedBy(GameDimens.Space16),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(valueWeight),
        )
    }
}

/**
 * The merge example (DESIGN_SYSTEM.md §6.10; F23-AC6): "2 2 2 2", `ic_pad_right`, "4 4 · ·" in
 * `MiniTile`s at `MiniTileMedium` on the palette's board colour, laid out left to right in every
 * locale (the board never mirrors, D-104) and spoken as one node, `htp_merging_illustration_cd`.
 * The two rows sit side by side when the column is wide enough, and otherwise one above the other
 * with the arrow turned to point down.
 */
@Composable
private fun MergeIllustration() {
    val description = stringResource(R.string.htp_merging_illustration_cd)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = GameDimens.Space16, vertical = GameDimens.Space16)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .testTag(HTP_MERGE_TAG),
            contentAlignment = Alignment.Center,
        ) {
            val tile = GameDimens.MiniTileMedium
            val gap = GameDimens.Space4
            val rowWidth = tile * BEFORE.size + gap * (BEFORE.size + 1)
            val wide = maxWidth >= rowWidth * 2 + GameDimens.HowToPlayArrow + GameDimens.Space8 * 2
            val parts: @Composable () -> Unit = {
                TileRow(BEFORE, firstTag = 0)
                Icon(
                    painter = painterResource(R.drawable.ic_pad_right),
                    contentDescription = null,
                    modifier =
                        Modifier.size(GameDimens.HowToPlayArrow)
                            .rotate(if (wide) 0f else ARROW_DOWN_DEGREES),
                )
                TileRow(AFTER, firstTag = BEFORE.size)
            }
            if (wide) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameDimens.Space8),
                ) {
                    parts()
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GameDimens.Space8),
                ) {
                    parts()
                }
            }
        }
    }
}

/**
 * One board row of the illustration: [exps] as `MiniTile`s (0 = an empty cell in `palette.empty`),
 * on `palette.board` with 4 dp padding and gaps, like `PaletteSwatch` (§7.8). The tiles are tagged
 * [htpMergeTileTag] from [firstTag] on and carry no semantics of their own (the illustration speaks
 * for them).
 */
@Composable
private fun TileRow(exps: IntArray, firstTag: Int) {
    val palette = LocalTilePalette.current
    val skin = LocalTileSkin.current
    val tile = GameDimens.MiniTileMedium
    Row(
        modifier = Modifier.drawBehind { drawRect(palette.board) }.padding(GameDimens.Space4),
        horizontalArrangement = Arrangement.spacedBy(GameDimens.Space4),
    ) {
        var tag = firstTag
        for (exp in exps) {
            if (exp == 0) {
                Spacer(
                    Modifier.size(tile).drawBehind {
                        drawRoundRect(
                            palette.empty,
                            cornerRadius = CornerRadius(tileRadius(tile, skin).toPx()),
                        )
                    },
                )
            } else {
                Box(Modifier.testTag(htpMergeTileTag(tag++)).clearAndSetSemantics {}) {
                    MiniTile(exp, tile)
                }
            }
        }
    }
}

/** The key table's rows: label and keys (D-053). */
private val KEY_ROWS =
    listOf(
        R.string.htp_key_move to R.string.htp_key_move_keys,
        R.string.undo to R.string.htp_key_undo_keys,
        R.string.new_game to R.string.htp_key_new_game_keys,
    )

/** "2 2 2 2": four tiles of exponent 1. */
private val BEFORE = intArrayOf(1, 1, 1, 1)

/** "4 4 · ·": two tiles of exponent 2, two empty cells. */
private val AFTER = intArrayOf(2, 2, 0, 0)

/** The arrow between the stacked rows points down. */
private const val ARROW_DOWN_DEGREES = 90f

/** The key table's keys column is twice as wide as its labels. */
private const val KEY_VALUE_WEIGHT = 2f
