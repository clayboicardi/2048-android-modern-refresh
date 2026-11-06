package com.clayboicardi.game2048.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityAnnouncementNotification
import platform.UIKit.UIAccessibilityIsVoiceOverRunning
import platform.UIKit.UIAccessibilityPostNotification
import platform.UIKit.UIAccessibilityVoiceOverStatusDidChangeNotification

@Composable
actual fun rememberScreenReaderOn(): Boolean {
    var running by remember { mutableStateOf(UIAccessibilityIsVoiceOverRunning()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer =
            center.addObserverForName(
                name = UIAccessibilityVoiceOverStatusDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                running = UIAccessibilityIsVoiceOverRunning()
            }
        onDispose { center.removeObserver(observer) }
    }
    return running
}

actual fun announceForAccessibility(message: String) {
    UIAccessibilityPostNotification(UIAccessibilityAnnouncementNotification, message)
}
