package com.clayboicardi.game2048.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.fixtures.GameFixture
import com.clayboicardi.game2048.fixtures.MotionFixtures
import com.clayboicardi.game2048.game.UNDO_TAG
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.board.TileUi
import com.clayboicardi.game2048.game.motion.ProbeOverlay
import com.clayboicardi.game2048.game.motion.ProbeOverlayKind
import com.clayboicardi.game2048.game.overlay.CARD_NEW_GAME_TAG
import com.clayboicardi.game2048.game.overlay.CARD_SHARE_TAG
import com.clayboicardi.game2048.game.overlay.CARD_UNDO_TAG
import com.clayboicardi.game2048.game.overlay.KEEP_GOING_TAG
import com.clayboicardi.game2048.ui.theme.GameMotion
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The win celebration under Full motion (MOTION_SPEC.md §3.11, §9.6; D-064, D-200): `FIX_WIN_PRE_4`
 * and LEFT make the 2048, which pops at the reveal; the glow ring, the confetti burst, the scrim,
 * the card with its badge and the interactive flip follow at their offsets from the origin. Times
 * are ms from the winning move's origin on the 16 ms grid; curve values are the library's
 * evaluation of the token specs at the play time the event frames imply (D-210), with the
 * MOTION_SPEC.md reference value in a comment.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class WinSequenceTest {
    @get:Rule val rule = MotionRule()

    /** AC-WIN-1 */
    @Test
    fun winningTileRevealsAndPops() {
        val h = launchWin()
        val target = winningTile(h)

        h.advanceTo(64)
        assertWithMessage("t = 64: the 2048's alpha").that(h.tile(target.id).alpha).isEqualTo(0f)
        h.advanceTo(80)
        assertWithMessage("t = 80: the 2048's alpha").that(h.tile(target.id).alpha).isEqualTo(1f)
        h.advanceTo(128)
        // Reference 1.2078 (v0 = 12; peak 1.2116 at t = 121).
        val impulse = expectedPopImpulse(target.exp, h.boardGeometry())
        assertWithMessage("t = 128: the 2048's scale")
            .that(h.tile(target.id).scale)
            .isWithin(Tol.SCALE)
            .of(expectedPopScale(128, impulse))
    }

    /** AC-WIN-2 */
    @Test
    fun glowRingExpandsAndFades() {
        val h = launchWin()

        h.advanceTo(128)
        assertWithMessage("t = 128: the ring").that(h.snapshot().effects.ring).isNull()
        // Reference alpha 0.830, scale 1.062 (4 ms of ring time).
        assertRing(h, 144)
        // Reference alpha 0.408 (52 ms of ring time).
        assertRing(h, 192)
        h.advanceTo(RING_GONE_MS)
        assertWithMessage("t = 848: the ring").that(h.snapshot().effects.ring).isNull()
    }

    /** AC-WIN-3 */
    @Test
    fun confettiBurstsOnceAndEnds() {
        val h = launchWin()
        assertConfetti(h, GameMotion.Confetti.COUNT_COMPACT)
    }

    /** AC-WIN-3 */
    @Test
    fun expandedWindowBurstsMoreConfetti() {
        val h = launchWin(widthDp = EXPANDED_WIDTH_DP)
        // MOTION_SPEC.md §8.1 marks the width qualifier unverified: the app must see 900 dp.
        assertWithMessage("the window width the app sees (dp)")
            .that(h.windowWidthDp())
            .isWithin(WIDTH_TOL_DP)
            .of(EXPANDED_WIDTH_DP.toFloat())
        assertConfetti(h, GameMotion.Confetti.COUNT_EXPANDED)
    }

    /** AC-WIN-4 */
    @Test
    fun cardComposesOnItsFrame() {
        val h = launchWin()
        h.assertWinCardEntry()
    }

    /** AC-WIN-5 */
    @Test
    fun buttonsBecomeInteractiveTogether() {
        val h = launchWin()
        h.assertWinButtonsGate()
    }

    /** AC-WIN-6 */
    @Test
    fun badgeMorphsAndItsRotationStops() {
        MotionFixtures.WIN_PRE_LEFT.verify()
        val h = launch(MotionFixtures.FIX_WIN_PRE_4)
        h.filmstrip("win_sequence", FilmRegion.ROOT, STRIP_FRAMES)
        h.commitMove(Direction.LEFT)

        h.advanceTo(BADGE_MORPH_DONE_MS)
        assertWithMessage("t = 1184: badge morph").that(card(h).badgeMorph).isEqualTo(1f)
        h.advanceFrames(1)
        assertWithMessage("t = 1200: badge morph").that(card(h).badgeMorph).isEqualTo(1f)
        h.advanceTo(5696)
        // Reference 59.9° (O + 4992 of 5000 ms).
        val rotation = Curves.value(BADGE_ROTATION, 0f, BADGE_DEG, 5696 - O_MS)
        assertWithMessage("t = 5696: badge rotation")
            .that(card(h).badgeRotationDeg)
            .isWithin(DEG_TOL)
            .of(rotation)
        h.advanceTo(BADGE_STOP_MS)
        assertWithMessage("t = 5712: badge rotation")
            .that(card(h).badgeRotationDeg)
            .isEqualTo(BADGE_DEG)
        assertWithMessage("t = 5712: running").that(h.snapshot().isAnyRunning).isFalse()
        h.writeFilmstrip()
    }

    /** AC-WIN-7 */
    @Test
    fun twoConfirmsOnTheirFrames() {
        val h = launchWin()
        h.advanceTo(QUIET_MS)
        assertWithMessage("haptics of the win")
            .that(h.hapticsFrom())
            .containsExactly(CONFIRM to WIN_FIRST_MS, CONFIRM to WIN_SECOND_MS)
            .inOrder()
    }

    /** AC-WIN-8 */
    @Test
    fun undoBeforeTheCardEndsTheSequence() {
        MotionFixtures.WIN_PRE_UNDO_LEFT.verify()
        val h = launch(MotionFixtures.FIX_WIN_PRE_UNDO_4)
        val win = h.commitMove(Direction.LEFT)
        h.advanceTo(UNDO_AT_MS)
        assertWithMessage("t = 320: confetti before the undo")
            .that(h.snapshot().effects.confettiAlive)
            .isGreaterThan(0)

        h.commit { h.rule.onNodeWithTag(UNDO_TAG).performClick() }

        val f1 = h.snapshot()
        assertWithMessage("the undo's F1: confetti").that(f1.effects.confettiAlive).isEqualTo(0)
        assertWithMessage("the undo's F1: the ring").that(f1.effects.ring).isNull()
        assertWithMessage("the undo's F1: the overlay").that(f1.overlay).isNull()
        while (h.now() < win + QUIET_MS) {
            h.advanceFrames(1)
            assertWithMessage("${h.now() - win} ms after the win: the overlay")
                .that(h.snapshot().overlay)
                .isNull()
        }
        assertWithMessage("haptics from the win's origin")
            .that(h.hapticsFrom(win))
            .containsExactly(CONFIRM to WIN_FIRST_MS, CONFIRM to WIN_SECOND_MS)
            .inOrder()
    }

    private fun launchWin(widthDp: Int? = null): MotionHarness {
        MotionFixtures.WIN_PRE_LEFT.verify()
        val h = launch(MotionFixtures.FIX_WIN_PRE_4, widthDp)
        h.commitMove(Direction.LEFT)
        return h
    }

    private fun launch(game: SavedGame, widthDp: Int? = null): MotionHarness =
        MotionHarness.launch(rule, GameFixture(game), MotionPreference.FULL, widthDp = widthDp)

    /** The 2048 the winning move merged (the committed `BoardUi`'s `Merged` tile). */
    private fun winningTile(h: MotionHarness): TileUi =
        checkNotNull(h.committed).tiles.single { it.kind == TileKind.Merged }

    /** The ring at [tMs]: one fraction f drives scale 1 → 1.8, stroke 6 dp → 0, alpha 0.9 → 0. */
    private fun assertRing(h: MotionHarness, tMs: Long) {
        h.advanceTo(tMs)
        val ring = checkNotNull(h.snapshot().effects.ring) { "t = $tMs: no ring is drawn" }
        val f = Curves.value(RING, 0f, 1f, tMs)
        val win = GameMotion.Win
        assertWithMessage("t = $tMs: ring alpha")
            .that(ring.alpha)
            .isWithin(Tol.ALPHA)
            .of(win.RING_START_ALPHA * (1 - f))
        assertWithMessage("t = $tMs: ring scale")
            .that(ring.scale)
            .isWithin(Tol.SCALE)
            .of(1 + (win.RING_END_SCALE - 1) * f)
        assertWithMessage("t = $tMs: ring stroke (dp)")
            .that(ring.strokeDp)
            .isWithin(Tol.DP)
            .of(win.RING_STROKE_DP * (1 - f))
    }

    /** None at 192, [count] emitted on 208, never more, and none from 2416 on (the loop ended). */
    private fun assertConfetti(h: MotionHarness, count: Int) {
        h.advanceTo(192)
        assertWithMessage("t = 192: confetti").that(h.snapshot().effects.confettiAlive).isEqualTo(0)
        h.advanceTo(CONFETTI_AT_MS)
        assertWithMessage("t = 208: confetti")
            .that(h.snapshot().effects.confettiAlive)
            .isEqualTo(count)
        var alive = count
        while (h.t() < CONFETTI_DONE_MS) {
            h.advanceFrames(1)
            val now = h.snapshot().effects.confettiAlive
            assertWithMessage("t = ${h.t()}: confetti alive").that(now).isAtMost(alive)
            alive = now
        }
        assertWithMessage("t = 2416: confetti").that(alive).isEqualTo(0)
        h.advanceFrames(2)
        assertWithMessage("t = ${h.t()}: confetti")
            .that(h.snapshot().effects.confettiAlive)
            .isEqualTo(0)
    }

    private fun card(h: MotionHarness): ProbeOverlay =
        checkNotNull(h.snapshot().overlay) { "t = ${h.t()}: no card" }

    private companion object {
        const val EXPANDED_WIDTH_DP = 900
        const val WIDTH_TOL_DP = 0.5f
        const val RING_GONE_MS = 848L
        const val CONFETTI_AT_MS = 208L
        const val CONFETTI_DONE_MS = 2416L
        const val O_MS = 704L
        const val BADGE_MORPH_DONE_MS = 1184L
        const val BADGE_STOP_MS = 5712L
        const val BADGE_DEG = GameMotion.Win.BADGE_ROTATION_DEG
        const val DEG_TOL = 0.01f
        const val UNDO_AT_MS = 320L
        const val QUIET_MS = 1200L
        const val WIN_FIRST_MS = 128L
        const val WIN_SECOND_MS = 272L
        val CONFIRM = HapticFeedbackType.Confirm
        val STRIP_FRAMES =
            listOf(0L, 80, 128, 144, 208, 320, 480, 688, 720, 784, 880, 1200, 2416, 5712)

        /** The ring fraction: `tween(700, delayMillis = T0 + 60, EmphasizedDecelerate)`. */
        val RING: FiniteAnimationSpec<Float> =
            tween(
                GameMotion.Win.RING_MS,
                delayMillis = GameMotion.MERGE_REVEAL_MS + GameMotion.Win.RING_AT_MS,
                easing = GameMotion.EmphasizedDecelerate,
            )

        /** Badge rotation from O: `tween(5000, LinearEasing)`. */
        val BADGE_ROTATION: FiniteAnimationSpec<Float> =
            tween(GameMotion.Win.BADGE_ROTATION_MS, easing = LinearEasing)
    }
}

/** The win card's buttons in layout order, Share last (`ProbeOverlay.buttonAlphas`). */
internal val WIN_BUTTONS = listOf(KEEP_GOING_TAG, CARD_NEW_GAME_TAG, CARD_UNDO_TAG, CARD_SHARE_TAG)

/** The game-over card's buttons in layout order, Share last. */
internal val OVER_BUTTONS = listOf(CARD_NEW_GAME_TAG, CARD_UNDO_TAG, CARD_SHARE_TAG)

/**
 * AC-WIN-4 after a winning move of `FIX_WIN_PRE_4` (MOTION_SPEC.md §3.11): no card and no scrim at
 * 672; the card composed on 688 at alpha 0 and scale 0.92 over a scrim of `tween(300, delayMillis =
 * 680, Standard)`; its overlay origin O on 704; card alpha `fastEffects` at O + 16 (720); the scrim
 * at 784. Also run by `RestoreNoMotionTest` (AC-RESTORE-2).
 */
internal fun MotionHarness.assertWinCardEntry() {
    advanceTo(672)
    val before = snapshot()
    assertWithMessage("t = 672: the overlay").that(before.overlay).isNull()
    assertWithMessage("t = 672: the scrim").that(before.scrimReading ?: 0f).isEqualTo(0f)

    advanceTo(WIN_CARD_AT_MS)
    val composed = checkNotNull(snapshot().overlay) { "t = 688: no card" }
    assertWithMessage("t = 688: overlay kind").that(composed.kind).isEqualTo(ProbeOverlayKind.WIN)
    assertWithMessage("t = 688: card alpha").that(composed.cardAlpha).isWithin(Tol.ALPHA).of(0f)
    assertWithMessage("t = 688: card scale")
        .that(composed.cardScale)
        .isWithin(Tol.SCALE)
        .of(GameMotion.Win.CARD_START_SCALE)
    // Reference 0.0042.
    assertWithMessage("t = 688: scrim")
        .that(composed.scrimAlpha)
        .isWithin(Tol.ALPHA)
        .of(Curves.value(WIN_SCRIM, 0f, GameMotion.Win.SCRIM_ALPHA, WIN_CARD_AT_MS))

    advanceTo(WIN_O_MS)
    assertWithMessage("the overlay origin O (ms from the origin)")
        .that(checkNotNull(snapshot().overlayOriginMs) { "no overlay origin" } - originMs)
        .isEqualTo(WIN_O_MS)
    advanceTo(720)
    // Reference 0.2592.
    assertWithMessage("t = 720: card alpha")
        .that(checkNotNull(snapshot().overlay).cardAlpha)
        .isWithin(Tol.ALPHA)
        .of(Curves.value(GameMotion.fastEffects(GameMotion.ALPHA_THRESHOLD), 0f, 1f, 16))
    advanceTo(784)
    // Reference 0.449.
    assertWithMessage("t = 784: scrim")
        .that(checkNotNull(snapshot().overlay).scrimAlpha)
        .isWithin(Tol.ALPHA)
        .of(Curves.value(WIN_SCRIM, 0f, GameMotion.Win.SCRIM_ALPHA, 784))
}

/**
 * AC-WIN-5 (D-200): from the card's frame (688) through 864 `interactive` is false and every
 * button, Share included, is composed and carries `disabled()` semantics; on 864 none can take
 * focus. On 880 all are interactive together and Keep going has the focus (D-081). Also run by
 * `RestoreNoMotionTest` (AC-RESTORE-2).
 */
internal fun MotionHarness.assertWinButtonsGate() {
    assertButtonsLocked(WIN_BUTTONS, WIN_CARD_AT_MS, WIN_LOCKED_MS)
    advanceTo(WIN_INTERACTIVE_MS)
    assertButtonsInteractive(WIN_BUTTONS, KEEP_GOING_TAG)
}

/**
 * From [fromMs] (or the current frame, if later) through [lockedMs]: a card is shown, its
 * `interactive` is false and every one of [tags] is composed with `disabled()` semantics; on
 * [lockedMs] none can take focus (D-200).
 */
internal fun MotionHarness.assertButtonsLocked(tags: List<String>, fromMs: Long, lockedMs: Long) {
    advanceTo(fromMs)
    while (true) {
        assertLockedNow(tags)
        if (t() >= lockedMs) break
        advanceFrames(1)
    }
    for (tag in tags) button(tag).assertCannotTakeFocus()
}

/**
 * On the current frame: a card is shown, its `interactive` is false, and every one of [tags] is
 * composed with `disabled()` semantics (D-200).
 */
internal fun MotionHarness.assertLockedNow(tags: List<String>) {
    val card = checkNotNull(snapshot().overlay) { "t = ${t()}: no card" }
    assertWithMessage("t = ${t()}: interactive").that(card.interactive).isFalse()
    for (tag in tags) {
        assertWithMessage("t = ${t()}: $tag composed").that(exists(tag)).isTrue()
        assertWithMessage("t = ${t()}: $tag carries disabled()")
            .that(SemanticsProperties.Disabled in button(tag).fetchSemanticsNode().config)
            .isTrue()
    }
}

/**
 * On the current frame: the card is `interactive`, every one of [tags] is enabled (no `disabled()`)
 * and [focused] has the focus (D-081, D-200).
 */
internal fun MotionHarness.assertButtonsInteractive(tags: List<String>, focused: String) {
    val card = checkNotNull(snapshot().overlay) { "t = ${t()}: no card" }
    assertWithMessage("t = ${t()}: interactive").that(card.interactive).isTrue()
    for (tag in tags) button(tag).assertIsEnabled()
    button(focused).assertIsFocused()
}

/** A card button by its tag. */
internal fun MotionHarness.button(tag: String): SemanticsNodeInteraction = rule.onNodeWithTag(tag)

/**
 * D-200: the node is not focused, and a focus request (its `RequestFocus` action, if it has one)
 * leaves it unfocused.
 */
internal fun SemanticsNodeInteraction.assertCannotTakeFocus() {
    assertIsNotFocused()
    if (SemanticsActions.RequestFocus in fetchSemanticsNode().config) {
        performSemanticsAction(SemanticsActions.RequestFocus)
        assertIsNotFocused()
    }
}

/** The win scrim (A): `tween(300, delayMillis = T0 + 600, Standard)` from the origin. */
internal val WIN_SCRIM: FiniteAnimationSpec<Float> =
    tween(
        GameMotion.Win.SCRIM_MS,
        delayMillis = GameMotion.MERGE_REVEAL_MS + GameMotion.Win.CARD_AT_MS,
        easing = GameMotion.Standard,
    )

/** The win card's composition frame ⌈680⌉ on the 16 ms grid. */
internal const val WIN_CARD_AT_MS = 688L

/** Its overlay origin O, the next frame. */
internal const val WIN_O_MS = 704L

/** The last frame before the interactive flip. */
internal const val WIN_LOCKED_MS = 864L

/** The interactive flip ⌈880⌉ (T0 + 800). */
internal const val WIN_INTERACTIVE_MS = 880L
