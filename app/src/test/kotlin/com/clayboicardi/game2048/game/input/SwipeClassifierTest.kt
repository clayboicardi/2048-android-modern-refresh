package com.clayboicardi.game2048.game.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.clayboicardi.game2048.engine.Direction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The pure swipe decisions (D-050; ARCHITECTURE.md §6.1). JVM, no Robolectric. */
class SwipeClassifierTest {
    /** F15-AC1 */
    @Test
    fun thresholdAndDominance() {
        val t = 24f

        assertThat(SwipeClassifier.classify(Offset(24f, 0f), t)).isEqualTo(Direction.RIGHT)
        assertThat(SwipeClassifier.classify(Offset(23.9f, 0f), t)).isNull()
        assertThat(SwipeClassifier.classify(Offset(24f, 17.1f), t)).isEqualTo(Direction.RIGHT)
        assertThat(SwipeClassifier.classify(Offset(24f, 17.2f), t)).isNull()
        assertThat(SwipeClassifier.classify(Offset(-30f, 30f), t)).isNull()
        // A ratio of exactly 1.4 is accepted; every axis and sign maps physically (D-104).
        assertThat(SwipeClassifier.classify(Offset(-28f, 20f), t)).isEqualTo(Direction.LEFT)
        assertThat(SwipeClassifier.classify(Offset(0f, -24f), t)).isEqualTo(Direction.UP)
        assertThat(SwipeClassifier.classify(Offset(5f, 30f), t)).isEqualTo(Direction.DOWN)
    }

    /** F15-AC2 */
    @Test
    fun flingFallback() {
        val slop = 8f
        val vMin = 600f
        fun fling(dx: Float, vx: Float) =
            SwipeClassifier.classifyFling(Offset(dx, 0f), Velocity(vx, 0f), slop, vMin)

        assertThat(fling(10f, 700f)).isEqualTo(Direction.RIGHT)
        assertThat(fling(10f, -700f)).isNull()
        assertThat(fling(10f, 500f)).isNull()
        assertThat(fling(6f, 900f)).isNull()
        // The ratio condition holds for flings too, and vertical flings read the y velocity.
        assertThat(
                SwipeClassifier.classifyFling(Offset(10f, 9f), Velocity(900f, 0f), slop, vMin),
            )
            .isNull()
        assertThat(
                SwipeClassifier.classifyFling(Offset(0f, -10f), Velocity(0f, -600f), slop, vMin),
            )
            .isEqualTo(Direction.UP)
    }
}
