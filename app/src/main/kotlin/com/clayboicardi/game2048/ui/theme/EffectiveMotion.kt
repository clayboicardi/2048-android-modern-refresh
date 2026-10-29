package com.clayboicardi.game2048.ui.theme

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.clayboicardi.game2048.data.settings.MotionPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The system animator duration scale, live (D-063; MOTION_SPEC.md §2.1). `AppContainer` owns the
 * one production instance ([SystemAnimatorScaleSource]); tests pass a fake whose `scale` is a
 * `MutableStateFlow<Float>`.
 */
interface AnimatorScaleSource {
    val scale: StateFlow<Float>
}

/**
 * Reads `Settings.Global.ANIMATOR_DURATION_SCALE` (default 1f) and observes it with a
 * `ContentObserver` on `Settings.Global.getUriFor(ANIMATOR_DURATION_SCALE)` (live, D-063;
 * ARCHITECTURE.md §5.8). Created once, by `AppContainer.create`, on the main thread inside
 * `Application.onCreate`, so construction stays cheap; [scope] is the container's
 * `applicationScope`, which outlives every activity.
 *
 * Construction reads the setting once (the read Compose's own `MotionDurationScale` makes for each
 * window) and registers the observer; nothing else, and never `PackageManager`. The observer has no
 * handler, so a change notification re-reads the setting on the notifying thread and updates
 * [scale] at once. The observer is unregistered when [scope] completes.
 */
class SystemAnimatorScaleSource(context: Context, scope: CoroutineScope) : AnimatorScaleSource {
    private val resolver: ContentResolver = context.contentResolver
    private val current = MutableStateFlow(read())

    override val scale: StateFlow<Float> = current.asStateFlow()

    private val observer =
        object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                current.value = read()
            }
        }

    init {
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        scope.coroutineContext[Job]?.invokeOnCompletion {
            resolver.unregisterContentObserver(observer)
        }
    }

    private fun read(): Float =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}

/**
 * The effective motion level (D-063; MOTION_SPEC.md §2.1): `OFF` when [systemScale] is 0, else the
 * persisted Motion [setting] (D-042) mapped to `FULL`, `REDUCED` or `OFF`.
 */
fun effectiveMotion(setting: MotionPreference, systemScale: Float): MotionLevel =
    if (systemScale == 0f) {
        MotionLevel.OFF
    } else {
        when (setting) {
            MotionPreference.FULL -> MotionLevel.FULL
            MotionPreference.REDUCED -> MotionLevel.REDUCED
            MotionPreference.OFF -> MotionLevel.OFF
        }
    }

/**
 * The effective motion level of the tree below `GameTheme`, which provides it from
 * `effectiveMotion(settings.motion, scale)` together with `gameMotionScheme(level)` (D-184).
 */
@Suppress("ComposeCompositionLocalUsage") // D-063: the one effective level (MOTION_SPEC.md §2.1)
val LocalEffectiveMotion: ProvidableCompositionLocal<MotionLevel> = compositionLocalOf {
    MotionLevel.FULL
}
