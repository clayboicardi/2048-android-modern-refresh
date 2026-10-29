package com.clayboicardi.game2048.ui.theme

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.overlay.CARD_NEW_GAME_TAG
import com.clayboicardi.game2048.game.overlay.CARD_SHARE_TAG
import com.clayboicardi.game2048.game.overlay.CARD_UNDO_TAG
import com.clayboicardi.game2048.game.overlay.KEEP_GOING_TAG
import com.clayboicardi.game2048.motion.MotionHarness
import com.clayboicardi.game2048.motion.MotionRule
import com.clayboicardi.game2048.motion.TestDurationScale
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The effective motion level (MOTION_SPEC.md §2.1; D-063): `effectiveMotion()`, the live
 * `SystemAnimatorScaleSource` over `Settings.Global.ANIMATOR_DURATION_SCALE`, and
 * `LocalEffectiveMotion` as `GameTheme` provides it; plus the rule that nothing animates forever.
 * F13-AC4 (`delaysScaleWithAnimatorScale`) reads the win card's `OverlayController.interactive` and
 * every win-card button's enabled state.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class EffectiveMotionTest {
    /** Compose's animation scale, 1 unless a method sets it (MOTION_SPEC.md §8.1). */
    private val duration = TestDurationScale()

    @get:Rule val rule = MotionRule(duration)

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The source's scope; the observer is unregistered when it is cancelled. */
    private val scope = CoroutineScope(Job())

    @After
    fun cancelScope() {
        scope.cancel()
    }

    /** F13-AC1 */
    @Test
    fun systemScaleZeroForcesOff() {
        assertWithMessage("effectiveMotion(FULL, 0)")
            .that(effectiveMotion(MotionPreference.FULL, 0f))
            .isEqualTo(MotionLevel.OFF)
        putSystemScale(0f)
        val source = SystemAnimatorScaleSource(context, scope)
        assertWithMessage("the source's scale").that(source.scale.value).isEqualTo(0f)
        val seen = ArrayList<MotionLevel>()

        rule.compose.setContent {
            val scale by source.scale.collectAsState()
            GameTheme(darkTheme = false, motion = MotionPreference.FULL, animatorScale = scale) {
                val level = LocalEffectiveMotion.current
                SideEffect { seen += level }
            }
        }
        rule.compose.waitForIdle()

        assertWithMessage("LocalEffectiveMotion").that(seen.last()).isEqualTo(MotionLevel.OFF)
    }

    /** F13-AC2 */
    @Test
    fun settingAppliesWhenSystemAnimationsOn() {
        val expected =
            mapOf(
                MotionPreference.FULL to MotionLevel.FULL,
                MotionPreference.REDUCED to MotionLevel.REDUCED,
                MotionPreference.OFF to MotionLevel.OFF,
            )
        for ((setting, level) in expected) {
            assertWithMessage("effectiveMotion($setting, 1)")
                .that(effectiveMotion(setting, 1f))
                .isEqualTo(level)
        }
        putSystemScale(1f)
        val source = SystemAnimatorScaleSource(context, scope)
        val motion = mutableStateOf(MotionPreference.FULL)
        val seen = ArrayList<MotionLevel>()
        rule.compose.setContent {
            val scale by source.scale.collectAsState()
            GameTheme(darkTheme = false, motion = motion.value, animatorScale = scale) {
                val current = LocalEffectiveMotion.current
                SideEffect { seen += current }
            }
        }

        for ((setting, level) in expected) {
            motion.value = setting
            rule.compose.waitForIdle()
            assertWithMessage("LocalEffectiveMotion for $setting")
                .that(seen.last())
                .isEqualTo(level)
        }
    }

    /** F13-AC3 */
    @Test
    fun observesLiveScaleChange() {
        putSystemScale(1f)
        val source = SystemAnimatorScaleSource(context, scope)
        val seen = ArrayList<MotionLevel>()
        val roots = HashSet<Any>()
        rule.compose.setContent {
            val scale by source.scale.collectAsState()
            GameTheme(darkTheme = false, motion = MotionPreference.FULL, animatorScale = scale) {
                val root = remember { Any() }
                val level = LocalEffectiveMotion.current
                SideEffect {
                    seen += level
                    roots += root
                }
            }
        }
        rule.compose.waitForIdle()
        assertWithMessage("before: LocalEffectiveMotion")
            .that(seen.last())
            .isEqualTo(MotionLevel.FULL)
        rule.compose.mainClock.autoAdvance = false

        putSystemScale(0f)
        val uri: Uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.notifyChange(uri, null)
        assertWithMessage("the source's scale after the notification")
            .that(source.scale.value)
            .isEqualTo(0f)
        rule.compose.mainClock.advanceTimeByFrame()

        assertWithMessage("the next frame: LocalEffectiveMotion")
            .that(seen.last())
            .isEqualTo(MotionLevel.OFF)
        assertWithMessage("the composition was not recreated").that(roots).hasSize(1)
    }

    /** F13-AC11 */
    @Test
    fun noAnimationRunsSixSecondsAfterWin() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        val h =
            MotionHarness.launch(
                rule,
                GameFixture(MotionFixtures.FIX_WIN_PRE_4),
                MotionPreference.FULL,
            )
        val committedAt = h.now()
        h.commitMove(Direction.LEFT)

        h.rule.mainClock.autoAdvance = true
        h.rule.waitForIdle()

        assertWithMessage("virtual time to idle after the winning move (ms)")
            .that(h.now() - committedAt)
            .isAtMost(IDLE_WITHIN_MS)
    }

    /** F13-AC4 */
    @Test
    fun delaysScaleWithAnimatorScale() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        duration.scaleFactor = DOUBLE
        val h =
            MotionHarness.launch(
                rule,
                GameFixture(MotionFixtures.FIX_WIN_PRE_4),
                MotionPreference.FULL,
                systemScale = DOUBLE,
            )
        h.commitMove(Direction.LEFT)
        val buttons = WIN_BUTTON_TAGS.map { h.rule.onNodeWithTag(it) }

        h.advanceTo(LOCKED_AT_DOUBLE_MS)
        val locked = checkNotNull(h.snapshot().overlay) { "t = 1744: no win card" }
        assertWithMessage("t = 1744: interactive").that(locked.interactive).isFalse()
        buttons.forEach { it.assertIsNotEnabled() }
        h.advanceTo(INTERACTIVE_AT_DOUBLE_MS)
        val open = checkNotNull(h.snapshot().overlay) { "t = 1760: no win card" }
        assertWithMessage("t = 1760: interactive").that(open.interactive).isTrue()
        buttons.forEach { it.assertIsEnabled() }
    }

    private fun putSystemScale(scale: Float) {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            scale,
        )
    }

    private companion object {
        const val IDLE_WITHIN_MS = 6_000L
        const val DOUBLE = 2f

        /** ⌈2 × 880⌉ − 16 and ⌈2 × 880⌉: the win buttons' flip at animator scale 2. */
        const val LOCKED_AT_DOUBLE_MS = 1_744L
        const val INTERACTIVE_AT_DOUBLE_MS = 1_760L

        /**
         * Every button of the win card (DESIGN_SYSTEM.md §6.3): Keep going, New game, Undo, Share.
         */
        val WIN_BUTTON_TAGS =
            listOf(KEEP_GOING_TAG, CARD_NEW_GAME_TAG, CARD_UNDO_TAG, CARD_SHARE_TAG)
    }
}
