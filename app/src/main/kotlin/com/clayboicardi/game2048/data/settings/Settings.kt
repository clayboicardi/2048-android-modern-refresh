package com.clayboicardi.game2048.data.settings

import kotlinx.serialization.Serializable

/** Theme setting (D-006, D-042). Persisted by name: never rename an entry. */
@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Tile palette (D-071, D-042). Persisted by name. */
@Serializable
enum class PaletteId {
    CLASSIC,
    NEON,
}

/** The Motion setting (D-063, D-042). Persisted by name. */
@Serializable
enum class MotionPreference {
    FULL,
    REDUCED,
    OFF,
}

/** When a swipe commits its move (D-050, D-042). Persisted by name. */
@Serializable
enum class MoveTiming {
    DURING_SWIPE,
    ON_RELEASE,
}

/**
 * Swipe sensitivity (D-050, D-042). Only the entry name is persisted; the commit distance lives
 * here, in code (FEATURES.md §2.1 row 9), so changing a value needs no migration.
 */
@Serializable
enum class SwipeSensitivity(val thresholdDp: Int) {
    LOW(40),
    MEDIUM(24),
    HIGH(12),
}

/**
 * `settings.json` (D-042). Every field has a valid default (D-041); unknown keys are ignored and an
 * unknown enum name decodes to the field default (`AppJson`, ARCHITECTURE.md §4.3). No named
 * companion object (D-134).
 */
@Serializable
data class Settings(
    val schemaVersion: Int = 1,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: PaletteId = PaletteId.CLASSIC,
    val highContrast: Boolean = false,
    val dynamicChrome: Boolean = false,
    val motion: MotionPreference = MotionPreference.FULL,
    val haptics: Boolean = true,
    val moveTiming: MoveTiming = MoveTiming.DURING_SWIPE,
    val swipeSensitivity: SwipeSensitivity = SwipeSensitivity.MEDIUM,
    val immersive: Boolean = false,
    val keepScreenOn: Boolean = false,
    /** Board side N; a value outside 3..8 reads as 4 (`SettingsRepository`, D-042). */
    val lastBoardSize: Int = 4,
    /** 0 = fresh install: no What's new (D-009). */
    val lastSeenVersionCode: Int = 0,
    val onboardingDone: Boolean = false,
)
