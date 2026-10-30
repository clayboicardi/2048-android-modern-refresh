package com.clayboicardi.game2048.game.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.ui.components.HintCard
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.MotionLevel

/** The first-launch hint card (relied on by the screen and motion tests). */
const val HINT_TAG = "game:hint"

/**
 * The one-time first-launch hint (DESIGN_SYSTEM.md §6.2; FEATURES.md F22; D-009, D-188, D-201,
 * D-202): a [HintCard] reading `hint_first_launch`, or `hint_first_launch_talkback` while touch
 * exploration is on ([talkBack]), with the target of [size] as a game numeral ("2048", or "256" on
 * 3×3); "Got it" calls [onDismiss] (`GameViewModel.onHintDismissed`). It is shown while [visible]
 * (`GameUiState.showFirstLaunchHint`).
 *
 * Under Full motion it leaves with `shrinkVertically(overlaySpatial) + fadeOut(fastEffects)`, so
 * the board grows back with its bounds animation; under Reduced and Off it disappears on the next
 * frame. [padding] is the gap around the card (8 dp in STACKED and SCROLL), which shrinks with it.
 * The arrangements place it (DESIGN_SYSTEM.md §5.4): above the board in STACKED and SCROLL, in the
 * actions rail in THREE_COLUMN, above the buttons in the panel.
 */
@Composable
fun FirstLaunchHint(
    visible: Boolean,
    size: BoardSize,
    talkBack: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(),
) {
    if (LocalEffectiveMotion.current == MotionLevel.FULL) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = EnterTransition.None,
            exit =
                shrinkVertically(GameMotion.overlaySpatial(IntSize.VisibilityThreshold)) +
                    fadeOut(GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD)),
            label = "firstLaunchHint",
        ) {
            HintContent(size, talkBack, onDismiss, Modifier.padding(padding))
        }
    } else if (visible) {
        HintContent(size, talkBack, onDismiss, modifier.padding(padding))
    }
}

/** The hint card itself, also what the chrome probe measures (DESIGN_SYSTEM.md §5.3). */
@Composable
fun HintContent(
    size: BoardSize,
    talkBack: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = (1L shl size.targetExp).toString()
    val text =
        stringResource(
            if (talkBack) R.string.hint_first_launch_talkback else R.string.hint_first_launch,
            target,
        )
    HintCard(text = text, onDismiss = onDismiss, modifier = modifier.testTag(HINT_TAG))
}
