package com.clayboicardi.game2048.motion

import com.clayboicardi.game2048.game.motion.ConfettiSystem
import com.clayboicardi.game2048.game.motion.ParticleRandom
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertWithMessage
import java.lang.management.ManagementFactory
import kotlin.math.atan2
import kotlin.math.hypot
import org.junit.Test

/**
 * The particle engine (MOTION_SPEC.md §3.6, §3.11, §7.3, §7.5; D-064, D-187): the discrete step,
 * determinism, the per-step budget and zero allocation. Plain JVM, no Robolectric. The step-time
 * check reads the test thread's CPU time only around its loop (D-230), so the Lint, R8 and other
 * test tasks that the full gate runs beside it do not count.
 */
class ConfettiSystemTest {
    /** AC-PERF-3 */
    @Test
    fun tenThousandStepsAt160ParticlesWithinBudget() {
        val threads = ManagementFactory.getThreadMXBean()
        assertWithMessage("thread CPU time is measurable on this JVM")
            .that(threads.isCurrentThreadCpuTimeSupported && threads.isThreadCpuTimeEnabled)
            .isTrue()
        val system = immortalBurst()
        repeat(WARM_UP_STEPS) { system.step(DT) }

        val wallStart = System.nanoTime()
        val start = threads.currentThreadCpuTime
        repeat(TIMED_STEPS) { system.step(DT) }
        val elapsed = threads.currentThreadCpuTime - start
        val wall = System.nanoTime() - wallStart

        val millis = elapsed / NANOS_PER_MS
        println(
            "ConfettiSystemTest: $TIMED_STEPS steps of $CAP particles in $millis ms CPU, " +
                "${wall / NANOS_PER_MS} ms wall",
        )
        assertWithMessage("alive after the timed steps").that(system.aliveCount).isEqualTo(CAP)
        assertWithMessage("$TIMED_STEPS steps took $millis ms of CPU time, budget 200 ms")
            .that(elapsed)
            .isAtMost(BUDGET_NANOS)
    }

    /** AC-PERF-3 */
    @Test
    fun stepsAllocateNothingAfterWarmUp() {
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val system = immortalBurst()
        repeat(WARM_UP_STEPS) { system.step(DT) }

        val before = threads.currentThreadAllocatedBytes
        repeat(TIMED_STEPS) { system.step(DT) }
        val allocated = threads.currentThreadAllocatedBytes - before

        assertWithMessage("bytes allocated by $TIMED_STEPS steps after warm-up")
            .that(allocated)
            .isAtMost(MAX_ALLOCATED_BYTES)
    }

    /** AC-PERF-3 */
    @Test
    fun discreteStepMatchesTheReference() {
        val system = ConfettiSystem()
        system.emitConfetti(0f, 0f, count = 1, random = ParticleRandom(SEED))
        system.vx[0] = 500f
        system.vy[0] = -1000f
        system.life[0] = Float.POSITIVE_INFINITY

        repeat(REFERENCE_STEPS) { system.step(DT) }

        // The discrete scheme of MOTION_SPEC.md §3.11 (the closed form gives y = -142.26).
        assertWithMessage("x").that(system.x[0]).isWithin(0.5f).of(148.86f)
        assertWithMessage("y").that(system.y[0]).isWithin(0.5f).of(-135.22f)
        assertWithMessage("vy").that(system.vy[0]).isWithin(0.5f).of(203.02f)
    }

    /** AC-PERF-4 */
    @Test
    fun sameSeedGivesBitIdenticalArrays() {
        val a = ConfettiSystem()
        val b = ConfettiSystem()
        a.emitConfetti(ORIGIN_X, ORIGIN_Y, CAP, ParticleRandom(SEED))
        b.emitConfetti(ORIGIN_X, ORIGIN_Y, CAP, ParticleRandom(SEED))
        repeat(DETERMINISM_STEPS) {
            a.step(DT)
            b.step(DT)
        }

        assertWithMessage("aliveCount").that(a.aliveCount).isEqualTo(b.aliveCount)
        assertWithMessage("some particles still alive at 2 s").that(a.aliveCount).isGreaterThan(0)
        for ((fieldA, fieldB) in floatArrays(a).zip(floatArrays(b))) {
            assertWithMessage(fieldA.first)
                .that(fieldA.second.map { it.toRawBits() })
                .isEqualTo(fieldB.second.map { it.toRawBits() })
        }
        assertWithMessage("shape").that(a.shape.toList()).isEqualTo(b.shape.toList())
        assertWithMessage("colorIndex").that(a.colorIndex.toList()).isEqualTo(b.colorIndex.toList())
    }

    /**
     * D-064: every confetti particle starts inside the MOTION_SPEC.md §3.11 emission distributions,
     * with the 60/25/15 shape shares and the 7 colour indices.
     */
    @Test
    fun confettiEmissionFollowsTheSpec() {
        val system = ConfettiSystem()
        system.emitConfetti(ORIGIN_X, ORIGIN_Y, CAP, ParticleRandom(SEED))

        assertWithMessage("aliveCount").that(system.aliveCount).isEqualTo(CAP)
        for (i in 0 until CAP) {
            val angleDeg = Math.toDegrees(atan2(system.vy[i], system.vx[i]).toDouble())
            assertWithMessage("x[$i]").that(system.x[i]).isEqualTo(ORIGIN_X)
            assertWithMessage("y[$i]").that(system.y[i]).isEqualTo(ORIGIN_Y)
            assertWithMessage("angle[$i]").that(angleDeg).isIn(range(-150.0, -30.0))
            assertWithMessage("speed[$i]")
                .that(hypot(system.vx[i], system.vy[i]).toDouble())
                .isIn(range(700.0, 1400.0))
            assertWithMessage("spin[$i]").that(system.spinDegS[i]).isIn(range(-540f, 540f))
            assertWithMessage("flutterOmega[$i]").that(system.flutterOmega[i]).isIn(range(6f, 12f))
            assertWithMessage("flutterPhase[$i]").that(system.flutterPhase[i]).isIn(range(0f, 6.3f))
            assertWithMessage("flipOmega[$i]").that(system.flipOmega[i]).isIn(range(8f, 16f))
            assertWithMessage("life[$i]").that(system.life[i]).isIn(range(1.4f, 2.2f))
            assertWithMessage("age[$i]").that(system.age[i]).isEqualTo(0f)
            assertWithMessage("sizeScale[$i]").that(system.sizeScale[i]).isEqualTo(0.4f)
            assertWithMessage("colorIndex[$i]").that(system.colorIndex[i].toInt()).isIn(0..6)
        }
        val shares = system.shape.toList().groupingBy { it.toInt() }.eachCount()
        // 160 draws of the 0.60 / 0.25 / 0.15 split, each well within 4 standard deviations.
        assertWithMessage("shapes").that(shares.keys).containsExactly(0, 1, 2)
        assertWithMessage("rects").that(shares.getValue(0)).isIn(71..121)
        assertWithMessage("circles").that(shares.getValue(1)).isIn(18..62)
        assertWithMessage("stars").that(shares.getValue(2)).isIn(6..42)
    }

    /**
     * D-064: the 8 new-best sparkles of MOTION_SPEC.md §3.6 fan out at i·45° + 22.5° ± 10° with
     * 120–220 dp/s, keep their velocity (no gravity or drag), and are dead at 600 ms.
     */
    @Test
    fun sparklesFanOutAndDieAt600Ms() {
        val system = ConfettiSystem()
        system.emitSparkles(ORIGIN_X, ORIGIN_Y, ParticleRandom(SEED))

        assertWithMessage("aliveCount").that(system.aliveCount).isEqualTo(8)
        val vx = system.vx.copyOf(8)
        val vy = system.vy.copyOf(8)
        for (i in 0 until 8) {
            val angleDeg = Math.toDegrees(atan2(vy[i], vx[i]).toDouble())
            val expected = i * 45.0 + 22.5
            val delta = ((angleDeg - expected) % 360.0 + 540.0) % 360.0 - 180.0
            assertWithMessage("direction[$i] - $expected°").that(delta).isIn(range(-10.0, 10.0))
            assertWithMessage("speed[$i]")
                .that(hypot(vx[i], vy[i]).toDouble())
                .isIn(range(120.0, 220.0))
        }

        repeat(SPARKLE_ALIVE_STEPS) { system.step(DT) }
        assertWithMessage("alive at 583 ms").that(system.aliveCount).isEqualTo(8)
        for (i in 0 until 8) {
            assertWithMessage("vx[$i]").that(system.vx[i]).isEqualTo(vx[i])
            assertWithMessage("vy[$i]").that(system.vy[i]).isEqualTo(vy[i])
            assertWithMessage("x[$i]")
                .that(system.x[i])
                .isWithin(0.01f)
                .of(ORIGIN_X + vx[i] * SPARKLE_ALIVE_STEPS * DT)
        }

        repeat(2) { system.step(DT) }
        assertWithMessage("alive at 617 ms").that(system.aliveCount).isEqualTo(0)
    }

    /**
     * D-064: at most `capacity` (160) particles are alive (MOTION_SPEC.md §3.11); `clear` kills
     * all.
     */
    @Test
    fun capacityBoundsTheAliveParticles() {
        val system = ConfettiSystem()
        val random = ParticleRandom(SEED)
        system.emitConfetti(ORIGIN_X, ORIGIN_Y, count = 144, random = random)
        system.emitConfetti(ORIGIN_X, ORIGIN_Y, count = 144, random = random)
        system.emitSparkles(ORIGIN_X, ORIGIN_Y, random)
        assertWithMessage("after 2 × 144 + 8").that(system.aliveCount).isEqualTo(CAP)

        system.clear()
        assertWithMessage("after clear").that(system.aliveCount).isEqualTo(0)
        system.step(DT)
        assertWithMessage("a step after clear").that(system.aliveCount).isEqualTo(0)
    }

    /** A full burst whose particles never die, so every timed step moves all [CAP] of them. */
    private fun immortalBurst(): ConfettiSystem {
        val system = ConfettiSystem()
        system.emitConfetti(ORIGIN_X, ORIGIN_Y, CAP, ParticleRandom(SEED))
        system.life.fill(Float.POSITIVE_INFINITY)
        return system
    }

    private fun floatArrays(system: ConfettiSystem): List<Pair<String, FloatArray>> =
        listOf(
            "x" to system.x,
            "y" to system.y,
            "vx" to system.vx,
            "vy" to system.vy,
            "rotationDeg" to system.rotationDeg,
            "spinDegS" to system.spinDegS,
            "flutterPhase" to system.flutterPhase,
            "flutterOmega" to system.flutterOmega,
            "flipOmega" to system.flipOmega,
            "age" to system.age,
            "life" to system.life,
            "sizeScale" to system.sizeScale,
        )

    private fun <T : Comparable<T>> range(low: T, high: T): Range<T> = Range.closed(low, high)

    private companion object {
        const val SEED = 0x2048L
        const val CAP = 160
        const val DT = 1f / 60f
        const val ORIGIN_X = 180f
        const val ORIGIN_Y = 240f
        const val WARM_UP_STEPS = 100
        const val TIMED_STEPS = 10_000
        const val REFERENCE_STEPS = 30
        const val DETERMINISM_STEPS = 120
        const val SPARKLE_ALIVE_STEPS = 35
        const val NANOS_PER_MS = 1_000_000L
        const val BUDGET_NANOS = 200L * NANOS_PER_MS
        const val MAX_ALLOCATED_BYTES = 1024L
    }
}
