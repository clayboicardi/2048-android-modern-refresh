package com.clayboicardi.game2048

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.clayboicardi.game2048.di.gameViewModelFactory
import com.clayboicardi.game2048.game.GameViewModel

/**
 * The single activity (D-023, ARCHITECTURE.md §4.12). The splash is installed before
 * `super.onCreate` and kept until the activity-scoped `GameViewModel` is ready, for at most 1,000
 * ms (D-112). It lists the game keys in the system keyboard-shortcuts helper (D-203).
 */
class MainActivity : ComponentActivity() {
    private val container
        get() = (application as Game2048Application).container

    private val gameViewModel: GameViewModel by viewModels { container.gameViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val gate = SplashGate(timeoutMs = 1_000, uptimeMs = SystemClock::uptimeMillis)
        splash.setKeepOnScreenCondition { gate.keep(ready = gameViewModel.ready.value) }
        enableEdgeToEdge()
        setContent {
            App(
                gameViewModel = gameViewModel,
                settings = container.settingsRepository.state,
                animatorScale = container.animatorScale,
                clock = container.clock,
            )
        }
    }

    /**
     * The keyboard shortcuts helper (Meta+/, D-203; ARCHITECTURE.md §6.3): one group titled
     * `app_name` with the four arrow keys, Undo on U and Ctrl+Z, and New game on N. Reuses the
     * strings of the board actions and the controls.
     */
    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        val undo = getString(R.string.undo)
        val shortcuts =
            listOf(
                KeyboardShortcutInfo(getString(R.string.move_up), KeyEvent.KEYCODE_DPAD_UP, 0),
                KeyboardShortcutInfo(getString(R.string.move_down), KeyEvent.KEYCODE_DPAD_DOWN, 0),
                KeyboardShortcutInfo(getString(R.string.move_left), KeyEvent.KEYCODE_DPAD_LEFT, 0),
                KeyboardShortcutInfo(
                    getString(R.string.move_right),
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    0,
                ),
                KeyboardShortcutInfo(undo, KeyEvent.KEYCODE_U, 0),
                KeyboardShortcutInfo(undo, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.new_game), KeyEvent.KEYCODE_N, 0),
            )
        data += KeyboardShortcutGroup(getString(R.string.app_name), shortcuts)
    }
}
