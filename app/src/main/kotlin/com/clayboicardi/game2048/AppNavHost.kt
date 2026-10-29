package com.clayboicardi.game2048

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.clayboicardi.game2048.about.AboutScreen
import com.clayboicardi.game2048.about.HowToPlayScreen
import com.clayboicardi.game2048.about.LicensesScreen
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.game.GameScreen
import com.clayboicardi.game2048.game.GameViewModel
import com.clayboicardi.game2048.game.motion.LocalMotionProbe
import com.clayboicardi.game2048.settings.SettingsScreen
import com.clayboicardi.game2048.stats.StatsScreen
import com.clayboicardi.game2048.ui.theme.LocalEffectiveMotion
import com.clayboicardi.game2048.ui.theme.MotionLevel
import com.clayboicardi.game2048.ui.theme.rememberGameNavTransitions

/**
 * The Navigation 3 host (ARCHITECTURE.md §7; D-023, D-094): `NavDisplay` over
 * `rememberNavBackStack(NavKeysConfiguration, GameKey)` with the
 * `rememberGameNavTransitions(level)` specs of `LocalEffectiveMotion` (MOTION_SPEC.md §3.17,
 * §3.19), back handled only while the stack has more than one entry, the saveable-state and
 * ViewModel-store entry decorators, and the six entries of §7.1. `navigate(key)` adds [key] unless
 * it is already on top (FEATURES.md F21 edge cases); `GameScreen` gets it as `onNavigate`. While
 * `LocalMotionProbe` is non-null it writes `MotionProbe.navOriginMs` on the first frame after the
 * back stack's top changes, the transition's play-time-0 frame.
 *
 * M3 form of §7.1 (D-236): until M4.1 creates `AppViewModel`, the host takes [settings]
 * (`SettingsRepository.state`'s current value, which `GameScreen` reads) and [clock] (the
 * container's `MonotonicClock`) in its place.
 *
 * Back on the Game root is never intercepted (D-054, D-094): `NavDisplay` enables its back handler
 * only while a previous entry exists, and nothing here registers another. A sub-screen's back arrow
 * pops only while its own entry is on top, so a second tap during the pop transition never pops the
 * entry below it. Each destination root is tagged `screenTag(key)`.
 */
@Suppress("ComposeViewModelForwarding") // the activity's GameViewModel serves the Game entry (§4.7)
@Composable
fun AppNavHost(
    gameViewModel: GameViewModel,
    settings: Settings,
    clock: MonotonicClock,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(NavKeysConfiguration, GameKey)
    val actions = remember(backStack) { NavActions(backStack) }
    val nav = rememberGameNavTransitions(LocalEffectiveMotion.current)
    NavOriginReport(backStack.lastOrNull())
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = actions::back,
        entryDecorators =
            listOf(
                rememberOffLeaveDecorator(backStack),
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        transitionSpec = nav.push,
        popTransitionSpec = nav.pop,
        predictivePopTransitionSpec = nav.predictivePop,
        entryProvider =
            entryProvider {
                entry<GameKey>(clazzContentKey = ::navContentKey) {
                    GameScreen(
                        gameViewModel,
                        settings,
                        clock,
                        onNavigate = actions.navigate,
                        modifier = Modifier.testTag(screenTag(GameKey)),
                    )
                }
                entry<StatsKey>(clazzContentKey = ::navContentKey) {
                    StatsScreen(
                        onBack = { actions.up(StatsKey) },
                        modifier = Modifier.testTag(screenTag(StatsKey)),
                    )
                }
                entry<SettingsKey>(clazzContentKey = ::navContentKey) {
                    SettingsScreen(
                        onBack = { actions.up(SettingsKey) },
                        modifier = Modifier.testTag(screenTag(SettingsKey)),
                    )
                }
                entry<HowToPlayKey>(clazzContentKey = ::navContentKey) {
                    HowToPlayScreen(
                        onBack = { actions.up(HowToPlayKey) },
                        modifier = Modifier.testTag(screenTag(HowToPlayKey)),
                    )
                }
                entry<AboutKey>(clazzContentKey = ::navContentKey) {
                    AboutScreen(
                        onBack = { actions.up(AboutKey) },
                        onOpenLicenses = { actions.navigate(LicensesKey) },
                        modifier = Modifier.testTag(screenTag(AboutKey)),
                    )
                }
                entry<LicensesKey>(clazzContentKey = ::navContentKey) {
                    LicensesScreen(
                        onBack = { actions.up(LicensesKey) },
                        modifier = Modifier.testTag(screenTag(LicensesKey)),
                    )
                }
            },
    )
}

/** The back stack's operations (ARCHITECTURE.md §7.1, §7.3); main thread only. */
private class NavActions(private val backStack: NavBackStack<NavKey>) {
    /** Pushes a key unless it is already on top: opening the current destination adds nothing. */
    val navigate: (NavKey) -> Unit = { key ->
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    /** `NavDisplay`'s Back: pops the top entry, never the Game root. */
    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** A sub-screen's back arrow: pops [key] only while it is on top. */
    fun up(key: NavKey) {
        if (backStack.lastOrNull() == key) back()
    }
}

/**
 * Under Off, the destination being left leaves the composition on the transition's first frame, the
 * frame that composes the new one, so only the new destination is composed from then on (F21-AC10;
 * MOTION_SPEC.md §3.17, AC-NAV-1). With `EnterTransition.None togetherWith ExitTransition.None`
 * alone, `NavDisplay` would keep it composed, hidden under the new one, until the transition has
 * ended two frames later. An entry leaves only once its transition is exiting and it is no longer
 * the back stack's top: a predictive-back gesture changes the back stack only when it completes, so
 * during the gesture, and after a cancelled one, the top destination stays. This decorator is the
 * outermost: its content holds the entry's saveable-state provider, which saves the entry's state
 * when it leaves (`SaveableStateHolder`).
 */
@Composable
private fun rememberOffLeaveDecorator(backStack: NavBackStack<NavKey>): NavEntryDecorator<NavKey> =
    remember(backStack) {
        NavEntryDecorator { entry ->
            val exiting =
                LocalNavAnimatedContentScope.current.transition.targetState ==
                    EnterExitState.PostExit
            val top = backStack.lastOrNull()?.let(::navContentKey)
            val leaving =
                LocalEffectiveMotion.current == MotionLevel.OFF &&
                    exiting &&
                    entry.contentKey != top
            if (!leaving) entry.Content()
        }
    }

/**
 * Writes `MotionProbe.navOriginMs` (MOTION_SPEC.md §8.2) while a probe is provided: the frame time
 * of the first frame after [top] changes, on which `NavDisplay`'s transition has play time 0. The
 * top this composition starts with (a launch or a recreation) is no transition and writes nothing.
 */
@Composable
private fun NavOriginReport(top: NavKey?) {
    val probe = LocalMotionProbe.current
    val reported = remember { TopMark(top) }
    LaunchedEffect(top, probe) {
        if (probe != null && reported.key != top) {
            reported.key = top
            val frameNanos = withFrameNanos { it }
            probe.navOriginMs = frameNanos / NANOS_PER_MS
        }
    }
}

/**
 * The content key of [key]'s entry (its saveable-state and ViewModel-store key): the key's name,
 * which the Off leave decorator compares with the back stack's top.
 */
private fun navContentKey(key: NavKey): String = key.toString()

/** The last back-stack top the origin report has seen; only its effect writes it. */
private class TopMark(var key: NavKey?)

private const val NANOS_PER_MS = 1_000_000L
