package com.clayboicardi.game2048.testing

import android.view.Window
import com.clayboicardi.game2048.ui.components.SystemBarsController

/**
 * The recording `SystemBarsController` (PLAN.md §2.6 E; FEATURES.md F19-AC2–AC5): every call in
 * order, with its `WindowInsetsCompat.Type` mask or behavior constant. It changes no window.
 */
class FakeSystemBarsController : SystemBarsController {
    enum class Kind {
        BEHAVIOR,
        HIDE,
        SHOW,
    }

    /** One recorded call: its [kind] and its mask or behavior [value]. */
    data class Call(val kind: Kind, val value: Int)

    /** Every call since the last [clear], oldest first. Main thread only. */
    val calls: MutableList<Call> = mutableListOf()

    override fun setSystemBarsBehavior(window: Window, behavior: Int) {
        calls += Call(Kind.BEHAVIOR, behavior)
    }

    override fun hide(window: Window, types: Int) {
        calls += Call(Kind.HIDE, types)
    }

    override fun show(window: Window, types: Int) {
        calls += Call(Kind.SHOW, types)
    }

    fun clear() {
        calls.clear()
    }
}
