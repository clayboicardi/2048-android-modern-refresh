package com.clayboicardi.game2048

import android.app.Application
import com.clayboicardi.game2048.di.AppContainer

/**
 * The application class: owns the one `AppContainer` (D-022). `AppContainer.create` does no I/O and
 * calls no `PackageManager` (D-134), so `onCreate` stays cheap on the main thread.
 */
class Game2048Application : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
    }
}
