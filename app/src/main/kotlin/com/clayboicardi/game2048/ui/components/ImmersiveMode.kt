package com.clayboicardi.game2048.ui.components

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The system-bar calls of Immersive mode (D-093; FEATURES.md F19 item 2; PLAN.md §2.6 E), the seam
 * behind [ImmersiveMode.apply]: [WindowSystemBarsController] wraps `WindowInsetsControllerCompat`;
 * tests inject a recording fake (`testing/FakeSystemBarsController.kt`) through `AppContainer`.
 * [types] is a `WindowInsetsCompat.Type` mask; [behavior] a `WindowInsetsControllerCompat` behavior
 * constant.
 */
interface SystemBarsController {
    fun setSystemBarsBehavior(window: Window, behavior: Int)

    fun hide(window: Window, types: Int)

    fun show(window: Window, types: Int)
}

/** The production [SystemBarsController]: `WindowCompat.getInsetsController(window, decorView)`. */
object WindowSystemBarsController : SystemBarsController {
    override fun setSystemBarsBehavior(window: Window, behavior: Int) {
        controller(window).systemBarsBehavior = behavior
    }

    override fun hide(window: Window, types: Int) {
        controller(window).hide(types)
    }

    override fun show(window: Window, types: Int) {
        controller(window).show(types)
    }

    private fun controller(window: Window): WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(window, window.decorView)
}

/**
 * Immersive mode (D-093; FEATURES.md F19; ARCHITECTURE.md §4.12). On: the system bars are hidden
 * with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, so an edge swipe shows them transiently. Off: they
 * are shown. `App()` applies the setting whenever it changes, on every destination, and
 * `MainActivity` re-applies it whenever the window regains focus; both pass the container's
 * [SystemBarsController]. There is no gesture that toggles the bars.
 */
object ImmersiveMode {
    fun apply(window: Window, on: Boolean, controller: SystemBarsController) {
        val bars = WindowInsetsCompat.Type.systemBars()
        if (on) {
            controller.setSystemBarsBehavior(
                window,
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
            )
            controller.hide(window, bars)
        } else {
            controller.show(window, bars)
        }
    }
}
