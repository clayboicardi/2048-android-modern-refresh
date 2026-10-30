package com.clayboicardi.game2048

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/** The Game screen, the start destination (D-023; ARCHITECTURE.md §7.1, §7.2). */
@Serializable data object GameKey : NavKey

/** Statistics. */
@Serializable data object StatsKey : NavKey

/** Settings. */
@Serializable data object SettingsKey : NavKey

/** How to play. */
@Serializable data object HowToPlayKey : NavKey

/** About. */
@Serializable data object AboutKey : NavKey

/** Licenses, reached from About. */
@Serializable data object LicensesKey : NavKey

/**
 * The back stack's serialization (ARCHITECTURE.md §7.1): the explicit `SavedStateConfiguration`
 * overload of `rememberNavBackStack` avoids reflection-based key lookup, so the back stack restores
 * identically in the R8-minified release build.
 */
val NavKeysConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(GameKey::class, GameKey.serializer())
            subclass(StatsKey::class, StatsKey.serializer())
            subclass(SettingsKey::class, SettingsKey.serializer())
            subclass(HowToPlayKey::class, HowToPlayKey.serializer())
            subclass(AboutKey::class, AboutKey.serializer())
            subclass(LicensesKey::class, LicensesKey.serializer())
        }
    }
}

/**
 * The test tag of [key]'s destination root (`SubScreenScaffold` for the sub-screens, D-236), relied
 * on by the navigation and motion tests.
 */
fun screenTag(key: NavKey): String = "screen:${tagName(key)}"

/** The test tag of the game-menu item that pushes [key] (Stats, Settings, How to play, About). */
fun menuItemTag(key: NavKey): String = "menu:${tagName(key)}"

/** The back arrow of every sub-screen's top app bar (`navigate_up`). */
const val NAVIGATE_UP_TAG = "nav:up"

/** The About row that pushes `LicensesKey`. */
const val ABOUT_LICENSES_TAG = "about:licenses"

private fun tagName(key: NavKey): String =
    when (key) {
        GameKey -> "game"
        StatsKey -> "stats"
        SettingsKey -> "settings"
        HowToPlayKey -> "howToPlay"
        AboutKey -> "about"
        LicensesKey -> "licenses"
        else -> key.toString()
    }
