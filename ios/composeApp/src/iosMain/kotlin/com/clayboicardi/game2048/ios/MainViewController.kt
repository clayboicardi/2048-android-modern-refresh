package com.clayboicardi.game2048.ios

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point called from Swift (`iosApp`): hosts the shared Compose UI in a UIViewController. */
fun MainViewController(): UIViewController = ComposeUIViewController { MainView() }
