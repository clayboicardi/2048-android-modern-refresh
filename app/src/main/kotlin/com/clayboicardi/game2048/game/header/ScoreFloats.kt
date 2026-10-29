package com.clayboicardi.game2048.game.header

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.clayboicardi.game2048.game.HeaderUi
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardEventKind
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.game.motion.LocalMoveTimeline
import com.clayboicardi.game2048.game.motion.ProbeFloat
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.GameFonts
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.LocalTilePalette
import com.clayboicardi.game2048.ui.theme.LocalTileSkin
import com.clayboicardi.game2048.ui.theme.MotionPlan
import com.clayboicardi.game2048.ui.theme.PlusNMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The "+N" floats of the Score chip (MOTION_SPEC.md §3.4; DESIGN_SYSTEM.md §7.2; D-062, D-064,
 * D-187). `GameHeader` composes this host inside `key(header.epoch, header.event)`, so any header
 * that is not a move, or of another epoch, disposes every running float at once with no fade
 * (ARCHITECTURE.md §4.11 item 4); consecutive moves keep the host and their floats.
 *
 * The host starts a float from its own `LaunchedEffect(header.deltaMoveId)` when the presented
 * [header] is a move with `lastDelta > 0` and [plan] has a float (mechanism B): the float is
 * created on `awaitOffset(deltaMoveId, plusNAtMs)` (or at once on a newer move's origin, which
 * keeps it running) at its initial values, and its animations run from the next frame in the host's
 * own scope. A started float counts towards `PLUS_N_MAX_ALIVE` from its start: starting a 4th
 * removes the oldest at once. A float whose move merged an exponent ≥ 9 is a pill in that tile's
 * fill and ink, read from [board], the arrangement's presented board, whose event is still the
 * `Moved` of the header's `deltaMoveId` while it is presented (D-187). Its text is `"+" +
 * lastDelta` in Latin digits.
 *
 * The floats are stacked on one first baseline, so the chip's overlay places them on the value's.
 */
@Composable
fun ScoreFloats(
    header: HeaderUi,
    board: State<BoardUi>,
    plan: MotionPlan,
    modifier: Modifier = Modifier,
) {
    val timeline = LocalMoveTimeline.current
    val probe = LocalMotionProbe.current
    val scope = rememberCoroutineScope()
    val floats = remember { mutableStateListOf<ScoreFloat>() }
    val ids = remember { IdCounter() }

    LaunchedEffect(header.deltaMoveId) {
        if (!startsFloat(header, plan)) return@LaunchedEffect
        val presented = board.value
        val moved = presented.event as? BoardEvent.Moved
        val exp = if (presented.moveId == header.deltaMoveId) moved?.maxMergedExp ?: 0 else 0
        val float =
            ScoreFloat(
                id = ids.next(),
                text = "+" + header.lastDelta.toString(),
                pillExp = exp.takeIf { it >= PILL_MIN_EXP },
                plan = plan,
            )
        while (floats.size >= GameMotion.Score.PLUS_N_MAX_ALIVE) floats.removeAt(0).job?.cancel()
        floats.add(float)
        float.job = scope.launch {
            // Created inside the frame callback of ⌈plusNAtMs⌉, so that frame draws it. A newer
            // move's publish ends the wait early: the float is created then.
            if (
                !timeline.awaitOffset(header.deltaMoveId, plan.plusNAtMs) { float.visible = true }
            ) {
                float.visible = true
            }
            float.run(plan)
            floats.remove(float)
        }
    }

    DisposableEffect(probe) {
        val registrations =
            listOfNotNull(
                probe?.registerFloats { floats.filter { it.visible }.map { it.probe() } },
                probe?.registerRunning { floats.any { it.visible } },
            )
        onDispose { registrations.forEach { it.unregister() } }
    }

    Layout(
        content = {
            for (float in floats) {
                if (float.visible) key(float.id) { PlusN(float) }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val baselines = placeables.map { p ->
            p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: 0
        }
        val top = baselines.maxOrNull() ?: 0
        val width = placeables.maxOfOrNull { it.width } ?: 0
        val height =
            placeables.indices.maxOfOrNull { top - baselines[it] + placeables[it].height } ?: 0
        // The host's first baseline is stated, not inherited from the placed floats: an inherited
        // line includes each float's layer translation, so aligning it to the value's baseline
        // would cancel the rise whenever the chip is laid out again (an odometer roll).
        layout(width, height, mapOf(FirstBaseline to top)) {
            placeables.forEachIndexed { i, p -> p.place((width - p.width) / 2, top - baselines[i]) }
        }
    }
}

/** One float's text, in the pill of its tile or in `primary`, moved in the layer phase. */
@Composable
private fun PlusN(float: ScoreFloat, modifier: Modifier = Modifier) {
    val palette = LocalTilePalette.current
    val skin = LocalTileSkin.current
    val exp = float.pillExp
    val ink = if (exp != null) palette.ink(exp) else MaterialTheme.colorScheme.primary
    val layer = modifier.graphicsLayer {
        translationY = float.translationYDp.value.dp.toPx()
        alpha = float.alpha.value
        scaleX = float.scale.value
        scaleY = float.scale.value
    }
    val shaped =
        if (exp != null) {
            layer
                .background(palette.fill(exp), CircleShape)
                .padding(
                    horizontal = GameDimens.PlusNPillPaddingH,
                    vertical = GameDimens.PlusNPillPaddingV,
                )
        } else {
            layer
        }
    BasicText(
        text = float.text,
        modifier = shaped,
        style = GameFonts.plusN(skin).copy(fontSize = GameDimens.PlusNText, color = ink),
        softWrap = false,
        maxLines = 1,
    )
}

/** True when the presented [header] is a move that scored and [plan] shows floats. */
private fun startsFloat(header: HeaderUi, plan: MotionPlan): Boolean =
    plan.plusN != PlusNMotion.NONE && header.event == BoardEventKind.Moved && header.lastDelta > 0

/**
 * One "+N" float: its animated values (dp, alpha, scale) at the initial values of [plan]
 * (MOTION_SPEC.md §3.4), [visible] from its creation frame.
 */
private class ScoreFloat(val id: Long, val text: String, val pillExp: Int?, plan: MotionPlan) {
    val translationYDp = Animatable(0f)
    val alpha = Animatable(1f)
    val scale =
        Animatable(if (plan.plusN == PlusNMotion.RISE) GameMotion.Score.PLUS_N_START_SCALE else 1f)
    var visible by mutableStateOf(false)
    var job: Job? = null

    /** Rises, scales and fades per [plan]; returns when the alpha animation has ended. */
    suspend fun run(plan: MotionPlan) {
        coroutineScope {
            plan.plusNRise?.let { spec ->
                launch { translationYDp.animateTo(-GameMotion.Score.PLUS_N_RISE_DP, spec) }
            }
            plan.plusNScale?.let { spec -> launch { scale.animateTo(1f, spec) } }
            plan.plusNAlpha?.let { spec -> alpha.animateTo(0f, spec) }
        }
    }

    fun probe() = ProbeFloat(text, alpha.value, translationYDp.value, scale.value)
}

/** Float ids of one host, outside the snapshot system: only its effect draws them. */
private class IdCounter {
    private var last = 0L

    fun next(): Long = ++last
}

/** The smallest merged exponent (512) whose float is a pill (D-187). */
private const val PILL_MIN_EXP = 9
