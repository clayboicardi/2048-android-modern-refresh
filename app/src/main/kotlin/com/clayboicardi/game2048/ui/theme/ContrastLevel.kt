package com.clayboicardi.game2048.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The system contrast level, live (D-073; FEATURES.md F11 item 5, F12 item 4; PLAN.md §2.6 E): the
 * value of `UiModeManager.getContrast()` on API 34+, in -1..1, and 0 below API 34. `AppContainer`
 * owns the one production instance ([SystemContrastLevelSource]); tests pass a fake whose
 * `contrast` is a `MutableStateFlow<Float>` (`testing/FakeContrastLevelSource.kt`).
 */
interface ContrastLevelSource {
    val contrast: StateFlow<Float>
}

/**
 * The lowest system contrast level that turns high contrast on whatever the setting (D-073): the
 * "Medium" step of Android 14's contrast setting.
 */
const val SYSTEM_HIGH_CONTRAST_MIN: Float = 0.5f

/**
 * Whether the system contrast [level] forces high contrast (D-073): `level ≥ 0.5`. The Settings row
 * then shows the switch checked and disabled (F11 item 5) and `GameTheme` applies high contrast
 * (F12 item 4); the stored setting is never rewritten.
 */
fun systemForcesHighContrast(level: Float): Boolean = level >= SYSTEM_HIGH_CONTRAST_MIN

/** A source that always reports the standard contrast 0: the default of test containers. */
object StandardContrastLevel : ContrastLevelSource {
    override val contrast: StateFlow<Float> = MutableStateFlow(0f).asStateFlow()
}

/**
 * Reads `UiModeManager.getContrast()` on API 34+ and observes it with `addContrastChangeListener`
 * on the main executor (D-073); below API 34 it reports 0 and registers nothing. Created once, by
 * `AppContainer.create`, on the main thread inside `Application.onCreate`, so construction stays
 * cheap: one read and one listener, never the package manager. [scope] is the container's
 * `applicationScope`; the listener is removed when it completes.
 */
class SystemContrastLevelSource(context: Context, scope: CoroutineScope) : ContrastLevelSource {
    private val current = MutableStateFlow(0f)

    override val contrast: StateFlow<Float> = current.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Api34.observe(context, scope) { level -> current.value = level }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34 {
        fun observe(context: Context, scope: CoroutineScope, onLevel: (Float) -> Unit) {
            val manager = context.getSystemService(UiModeManager::class.java) ?: return
            onLevel(manager.contrast)
            val listener = UiModeManager.ContrastChangeListener { level -> onLevel(level) }
            manager.addContrastChangeListener(ContextCompat.getMainExecutor(context), listener)
            scope.coroutineContext[Job]?.invokeOnCompletion {
                manager.removeContrastChangeListener(listener)
            }
        }
    }
}
