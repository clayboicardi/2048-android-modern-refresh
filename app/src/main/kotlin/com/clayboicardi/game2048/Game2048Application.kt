package com.clayboicardi.game2048

import android.app.Application
import com.clayboicardi.game2048.di.AppContainer

/**
 * The application class: owns the one `AppContainer` (D-022). `AppContainer.create` does no I/O and
 * calls no `PackageManager` (D-134), so `onCreate` stays cheap on the main thread. A Robolectric
 * test application overrides [createContainer] to inject the system-bar and contrast fakes
 * (`testing/SeamsTestApplication.kt`, PLAN.md §2.6 E, D-248).
 */
open class Game2048Application : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    /** The production container; a test application passes its fakes to `AppContainer.create`. */
    protected open fun createContainer(): AppContainer = AppContainer.create(this)
}
