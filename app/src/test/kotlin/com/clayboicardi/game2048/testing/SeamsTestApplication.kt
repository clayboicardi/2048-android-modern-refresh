package com.clayboicardi.game2048.testing

import com.clayboicardi.game2048.Game2048Application
import com.clayboicardi.game2048.di.AppContainer

/**
 * The application of activity-level tests that need the system seams (PLAN.md §2.6 E; D-248):
 * `@Config(application = SeamsTestApplication::class)`. Its production container (real files, real
 * animator scale) takes [systemBars] and [contrastLevel] instead of the system ones; a test reads
 * them through `ApplicationProvider.getApplicationContext<SeamsTestApplication>()`.
 */
class SeamsTestApplication : Game2048Application() {
    val systemBars = FakeSystemBarsController()
    val contrastLevel = FakeContrastLevelSource()

    override fun createContainer(): AppContainer =
        AppContainer.create(this, systemBars = systemBars, contrastLevel = contrastLevel)
}
