package com.clayboicardi.game2048.game.motion

import com.clayboicardi.game2048.ui.theme.GameMotion
import com.clayboicardi.game2048.ui.theme.GameMotion.Confetti
import com.clayboicardi.game2048.ui.theme.GameMotion.NewBest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** A rounded 6 × 10 dp rectangle (60 % of confetti). */
internal const val SHAPE_RECT: Byte = 0

/** A circle of radius 3 dp (25 % of confetti). */
internal const val SHAPE_CIRCLE: Byte = 1

/** An 8 dp four-point star (15 % of confetti). */
internal const val SHAPE_STAR: Byte = 2

/** A 6 dp four-point new-best sparkle: no gravity, drag, spin, flutter, flip or scale-in. */
internal const val SHAPE_SPARKLE: Byte = 3

/** The confetti colours: `palette.fill(7..11)`, `primary`, `tertiary` (MOTION_SPEC.md §3.11). */
internal const val CONFETTI_COLOR_COUNT = 7

/** Milliseconds per second. */
private const val MS_PER_S = 1000f

/** Degrees in a half turn, for the degree-to-radian conversion and the sparkle fan. */
private const val HALF_TURN_DEG = 180f

/**
 * The particle engine of confetti and the new-best sparkles (MOTION_SPEC.md §3.6, §3.11; D-064,
 * D-187), shared by `BoardEffectsCanvas` and `ContentEffectsCanvas`. Pure Kotlin with no Compose
 * types: structure-of-arrays `FloatArray(capacity)` fields (x, y, vx, vy, rotationDeg, spinDegS,
 * flutterPhase, flutterOmega, flipOmega, age, life, sizeScale) and `ByteArray(capacity)` fields
 * (shape, colorIndex), allocated once here; nothing is allocated after construction (MOTION_SPEC.md
 * §7.3). Positions are dp relative to the canvas that draws the system, times are seconds. Main
 * thread only. The fields are `internal`, read by `drawParticles` (`ParticleDraw.kt`).
 *
 * The alive particles are the slots `0 until aliveCount`; a particle that dies is replaced by the
 * last alive one, so a step touches only alive slots. `sizeScale` is the confetti scale-in `s =
 * lerp(0.4, 1, min(age / 0.08, 1))` (1 for sparkles), updated by [step].
 */
class ConfettiSystem(val capacity: Int = GameMotion.Confetti.CAP) {
    internal val x = FloatArray(capacity)
    internal val y = FloatArray(capacity)
    internal val vx = FloatArray(capacity)
    internal val vy = FloatArray(capacity)
    internal val rotationDeg = FloatArray(capacity)
    internal val spinDegS = FloatArray(capacity)
    internal val flutterPhase = FloatArray(capacity)
    internal val flutterOmega = FloatArray(capacity)
    internal val flipOmega = FloatArray(capacity)
    internal val age = FloatArray(capacity)
    internal val life = FloatArray(capacity)
    internal val sizeScale = FloatArray(capacity)
    internal val shape = ByteArray(capacity)
    internal val colorIndex = ByteArray(capacity)

    /** The number of alive slots. */
    private var alive = 0

    /** Particles whose `age < life`. */
    val aliveCount: Int
        get() = alive

    /**
     * One confetti burst of [count] particles (96 compact, 144 expanded; at most [capacity] alive)
     * from ([originXDp], [originYDp]) with the §3.11 emission distributions drawn from [random].
     */
    fun emitConfetti(originXDp: Float, originYDp: Float, count: Int, random: ParticleRandom) {
        val end = min(alive + count.coerceAtLeast(0), capacity)
        for (i in alive until end) {
            val angle = random.uniform(Confetti.ANGLE_MIN_DEG, Confetti.ANGLE_MAX_DEG) * DEG_TO_RAD
            val speed = random.uniform(Confetti.SPEED_MIN_DP_S, Confetti.SPEED_MAX_DP_S)
            x[i] = originXDp
            y[i] = originYDp
            vx[i] = cos(angle) * speed
            vy[i] = sin(angle) * speed
            rotationDeg[i] = 0f
            spinDegS[i] = random.uniform(-Confetti.SPIN_MAX_DEG_S, Confetti.SPIN_MAX_DEG_S)
            flutterOmega[i] = random.uniform(Confetti.FLUTTER_OMEGA_MIN, Confetti.FLUTTER_OMEGA_MAX)
            flutterPhase[i] = random.uniform(0f, TWO_PI)
            flipOmega[i] = random.uniform(Confetti.FLIP_OMEGA_MIN, Confetti.FLIP_OMEGA_MAX)
            life[i] = random.uniform(Confetti.LIFE_MIN_S, Confetti.LIFE_MAX_S)
            colorIndex[i] = (random.nextFloat() * CONFETTI_COLOR_COUNT).toInt().toByte()
            shape[i] = confettiShape(random.nextFloat())
            age[i] = 0f
            sizeScale[i] = Confetti.SCALE_IN_FROM
        }
        alive = end
    }

    /**
     * The 8 new-best sparkles from ([originXDp], [originYDp]) (MOTION_SPEC.md §3.6): 6 dp
     * four-point stars, direction i·45° + 22.5° plus U(−10°, 10°), speed U(120, 220) dp/s, no
     * gravity or drag, life 600 ms, alpha 1 then → 0 over the last 250 ms.
     */
    fun emitSparkles(originXDp: Float, originYDp: Float, random: ParticleRandom) {
        val end = min(alive + NewBest.SPARKLE_COUNT, capacity)
        for (i in alive until end) {
            val k = i - alive
            val jitter = random.uniform(-NewBest.SPARKLE_JITTER_DEG, NewBest.SPARKLE_JITTER_DEG)
            val angle = ((k + HALF) * SPARKLE_STEP_DEG + jitter) * DEG_TO_RAD
            val speed =
                random.uniform(NewBest.SPARKLE_SPEED_MIN_DP_S, NewBest.SPARKLE_SPEED_MAX_DP_S)
            x[i] = originXDp
            y[i] = originYDp
            vx[i] = cos(angle) * speed
            vy[i] = sin(angle) * speed
            rotationDeg[i] = 0f
            spinDegS[i] = 0f
            flutterOmega[i] = 0f
            flutterPhase[i] = 0f
            flipOmega[i] = 0f
            life[i] = NewBest.SPARKLE_LIFE_MS / MS_PER_S
            colorIndex[i] = 0
            shape[i] = SHAPE_SPARKLE
            age[i] = 0f
            sizeScale[i] = 1f
        }
        alive = end
    }

    /** Advances every alive particle by [dtSeconds] (MOTION_SPEC.md §3.11 step; T3 for dt). */
    fun step(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0f, Confetti.MAX_DT_S)
        val drag = exp(-Confetti.DRAG_PER_S * dt)
        val gravity = Confetti.GRAVITY_DP_S2 * dt
        var i = 0
        while (i < alive) {
            if (shape[i] == SHAPE_SPARKLE) {
                x[i] += vx[i] * dt
                y[i] += vy[i] * dt
            } else {
                vx[i] *= drag
                vy[i] = (vy[i] + gravity) * drag
                x[i] += vx[i] * dt
                y[i] += vy[i] * dt
                rotationDeg[i] += spinDegS[i] * dt
            }
            age[i] += dt
            if (age[i] >= life[i]) {
                moveLastInto(i)
            } else {
                if (shape[i] != SHAPE_SPARKLE) sizeScale[i] = scaleIn(age[i])
                i++
            }
        }
    }

    /**
     * Kills every particle at once (scale 0, level Off, or the removals of MOTION_SPEC.md §4.2).
     */
    fun clear() {
        alive = 0
    }

    /** Removes slot [i] by moving the last alive particle into it. */
    private fun moveLastInto(i: Int) {
        val last = --alive
        if (i == last) return
        x[i] = x[last]
        y[i] = y[last]
        vx[i] = vx[last]
        vy[i] = vy[last]
        rotationDeg[i] = rotationDeg[last]
        spinDegS[i] = spinDegS[last]
        flutterPhase[i] = flutterPhase[last]
        flutterOmega[i] = flutterOmega[last]
        flipOmega[i] = flipOmega[last]
        age[i] = age[last]
        life[i] = life[last]
        sizeScale[i] = sizeScale[last]
        shape[i] = shape[last]
        colorIndex[i] = colorIndex[last]
    }

    private companion object {
        const val TWO_PI = (2 * PI).toFloat()
        const val DEG_TO_RAD = (PI / HALF_TURN_DEG).toFloat()
        const val HALF = 0.5f

        /** i·45° + 22.5° for the 8 sparkles: an even fan, offset by half a step. */
        const val SPARKLE_STEP_DEG = 2 * HALF_TURN_DEG / NewBest.SPARKLE_COUNT

        /** 60 % rounded rects, 25 % circles, the remaining 15 % stars, from a uniform [u]. */
        fun confettiShape(u: Float): Byte =
            when {
                u < Confetti.SHARE_RECT -> SHAPE_RECT
                u < Confetti.SHARE_RECT + Confetti.SHARE_CIRCLE -> SHAPE_CIRCLE
                else -> SHAPE_STAR
            }

        /** `lerp(SCALE_IN_FROM, 1, min(age / SCALE_IN_S, 1))` (MOTION_SPEC.md §3.11). */
        fun scaleIn(age: Float): Float {
            val from = Confetti.SCALE_IN_FROM
            return from + (1f - from) * min(age / Confetti.SCALE_IN_S, 1f)
        }
    }
}
