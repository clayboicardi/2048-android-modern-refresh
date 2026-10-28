package com.clayboicardi.game2048

import android.os.Bundle
import android.os.SystemClock
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
 * ms (D-112).
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
        setContent { App() }
    }
}
