package com.clayboicardi.game2048.settings

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.data.settings.MotionPreference
import com.clayboicardi.game2048.data.settings.MoveTiming
import com.clayboicardi.game2048.data.settings.PaletteId
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.SwipeSensitivity
import com.clayboicardi.game2048.data.settings.ThemeMode
import com.clayboicardi.game2048.game.feedback.HapticsController
import com.clayboicardi.game2048.game.feedback.LocalHapticsEnabled
import com.clayboicardi.game2048.ui.components.Choice
import com.clayboicardi.game2048.ui.components.ChoiceDialog
import com.clayboicardi.game2048.ui.components.PaletteSwatch
import com.clayboicardi.game2048.ui.components.SectionHeader
import com.clayboicardi.game2048.ui.components.SettingsChoiceRow
import com.clayboicardi.game2048.ui.components.SettingsLinkRow
import com.clayboicardi.game2048.ui.components.SettingsSwitchRow
import com.clayboicardi.game2048.ui.components.SubScreenScaffold
import com.clayboicardi.game2048.ui.components.rememberIntentLauncher
import com.clayboicardi.game2048.ui.theme.GameDimens
import com.clayboicardi.game2048.ui.theme.resolveTilePalette
import com.clayboicardi.game2048.ui.theme.systemForcesHighContrast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The test tag of [row] in the Settings list. */
fun settingRowTag(row: SettingRow): String = "settings:row:${row.name}"

/** The test tag of the Settings list (the `LazyColumn`). */
const val SETTINGS_LIST_TAG = "settings:list"

/** The rows of Settings in the D-006 order (DESIGN_SYSTEM.md §6.8). */
enum class SettingRow {
    THEME,
    PALETTE,
    HIGH_CONTRAST,
    DYNAMIC_COLOR,
    MOTION,
    HAPTICS,
    MOVE_TIMING,
    SWIPE_SENSITIVITY,
    IMMERSIVE,
    KEEP_SCREEN_ON,
    APP_LANGUAGE,
}

/**
 * The Settings destination (`SettingsKey`; DESIGN_SYSTEM.md §6.8; FEATURES.md F11; D-006): the
 * `SubScreenScaffold` titled `settings` above a `LazyColumn` of the D-006 rows under their section
 * headers, from [viewModel] (entry-scoped, D-248). Every change persists at once through
 * `SettingsViewModel.update` (D-043); there is no Save button and no Reset statistics row.
 * - **Switch rows** toggle in place and fire `ToggleOn`/`ToggleOff` through
 *   `HapticsController.toggle` (D-065, D-185). The Haptics setting after the change decides: that
 *   is `LocalHapticsEnabled` on every switch but Haptics, whose own new value it is, so turning
 *   Haptics off fires nothing.
 * - **Choice rows** open a `ChoiceDialog`; selecting applies, persists and closes it, Cancel keeps
 *   the value. Which dialog is open is `rememberSaveable`, so it survives recreation (F11-AC9).
 * - **Locks** (F11 item 5) never write: Motion is disabled and reads `motion_off_system` while the
 *   system animator scale is 0 (D-063); High-contrast tiles is checked, disabled and reads
 *   `setting_high_contrast_forced` while the system contrast forces it (D-073).
 * - **API-gated rows** (F11 item 4): Dynamic color on API 31+ (D-074); the Language section with
 *   App language on API 33+, which opens the system per-app language page and shows
 *   `link_open_failed` in this screen's snackbar host when nothing handles it (D-102).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scale by viewModel.animatorScale.collectAsStateWithLifecycle()
    val contrast by viewModel.systemContrast.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val launch = rememberIntentLauncher(snackbars)
    val feedback = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    var dialog by rememberSaveable { mutableStateOf<SettingRow?>(null) }
    val locks =
        SettingLocks(motion = scale == 0f, highContrast = systemForcesHighContrast(contrast))
    val dark =
        when (settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    SubScreenScaffold(
        title = stringResource(R.string.settings),
        onBack = onBack,
        modifier = modifier,
        snackbars = snackbars,
    ) {
        SettingsList(
            settings = settings,
            locks = locks,
            dark = dark,
            onChoose = { row -> dialog = row },
            onSwitch = { row, on ->
                viewModel.update { switched(it, row, on) }
                val enabledAfter = if (row == SettingRow.HAPTICS) on else hapticsEnabled
                HapticsController.toggle(feedback, checked = on, enabledAfter = enabledAfter)
            },
            launch = launch,
            modifier = Modifier.fillMaxSize(),
        )
    }
    dialog
        ?.takeUnless { it == SettingRow.MOTION && locks.motion }
        ?.let { row ->
            SettingDialog(
                row = row,
                settings = settings,
                dark = dark,
                onUpdate = viewModel::update,
                onDismiss = { dialog = null },
            )
        }
}

/** The system locks of F11 item 5: each disables its row and never writes the setting. */
private data class SettingLocks(val motion: Boolean, val highContrast: Boolean)

/** [settings] with switch [row] set to [on]. */
private fun switched(settings: Settings, row: SettingRow, on: Boolean): Settings =
    when (row) {
        SettingRow.HIGH_CONTRAST -> settings.copy(highContrast = on)
        SettingRow.DYNAMIC_COLOR -> settings.copy(dynamicChrome = on)
        SettingRow.HAPTICS -> settings.copy(haptics = on)
        SettingRow.IMMERSIVE -> settings.copy(immersive = on)
        SettingRow.KEEP_SCREEN_ON -> settings.copy(keepScreenOn = on)
        else -> settings
    }

/** The rows and section headers of DESIGN_SYSTEM.md §6.8, in the D-006 order. */
@Composable
private fun SettingsList(
    settings: Settings,
    locks: SettingLocks,
    dark: Boolean,
    onChoose: (SettingRow) -> Unit,
    onSwitch: (SettingRow, Boolean) -> Unit,
    launch: (Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatch = resolveTilePalette(settings.palette, dark)
    LazyColumn(modifier.testTag(SETTINGS_LIST_TAG)) {
        header(R.string.settings_section_appearance)
        item(key = SettingRow.THEME) {
            SettingsChoiceRow(
                headline = stringResource(R.string.setting_theme),
                summary = stringResource(settings.themeMode.label),
                onClick = { onChoose(SettingRow.THEME) },
                modifier = Modifier.testTag(settingRowTag(SettingRow.THEME)),
            )
        }
        item(key = SettingRow.PALETTE) {
            SettingsChoiceRow(
                headline = stringResource(R.string.setting_palette),
                summary = stringResource(settings.palette.label),
                onClick = { onChoose(SettingRow.PALETTE) },
                modifier = Modifier.testTag(settingRowTag(SettingRow.PALETTE)),
                trailingContent = { PaletteSwatch(swatch.palette, swatch.skin) },
            )
        }
        item(key = SettingRow.HIGH_CONTRAST) {
            SettingsSwitchRow(
                headline = stringResource(R.string.setting_high_contrast),
                summary =
                    stringResource(
                        if (locks.highContrast) {
                            R.string.setting_high_contrast_forced
                        } else {
                            R.string.setting_high_contrast_summary
                        },
                    ),
                checked = settings.highContrast || locks.highContrast,
                onCheckedChange = { onSwitch(SettingRow.HIGH_CONTRAST, it) },
                modifier = Modifier.testTag(settingRowTag(SettingRow.HIGH_CONTRAST)),
                enabled = !locks.highContrast,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            switchItem(
                row = SettingRow.DYNAMIC_COLOR,
                headline = R.string.setting_dynamic_color,
                summary = R.string.setting_dynamic_color_summary,
                checked = settings.dynamicChrome,
                onSwitch = onSwitch,
            )
        }
        header(R.string.settings_section_motion)
        item(key = SettingRow.MOTION) {
            SettingsChoiceRow(
                headline = stringResource(R.string.setting_motion),
                summary =
                    stringResource(
                        if (locks.motion) R.string.motion_off_system else settings.motion.label,
                    ),
                onClick = { onChoose(SettingRow.MOTION) },
                modifier = Modifier.testTag(settingRowTag(SettingRow.MOTION)),
                enabled = !locks.motion,
            )
        }
        switchItem(
            row = SettingRow.HAPTICS,
            headline = R.string.setting_haptics,
            summary = R.string.setting_haptics_summary,
            checked = settings.haptics,
            onSwitch = onSwitch,
        )
        header(R.string.settings_section_controls)
        choiceItem(
            row = SettingRow.MOVE_TIMING,
            headline = R.string.setting_move_timing,
            summary = settings.moveTiming.label,
            onChoose = onChoose,
        )
        choiceItem(
            row = SettingRow.SWIPE_SENSITIVITY,
            headline = R.string.setting_swipe_sensitivity,
            summary = settings.swipeSensitivity.label,
            onChoose = onChoose,
        )
        header(R.string.settings_section_display)
        switchItem(
            row = SettingRow.IMMERSIVE,
            headline = R.string.setting_immersive,
            summary = R.string.setting_immersive_summary,
            checked = settings.immersive,
            onSwitch = onSwitch,
        )
        switchItem(
            row = SettingRow.KEEP_SCREEN_ON,
            headline = R.string.setting_keep_screen_on,
            summary = R.string.setting_keep_screen_on_summary,
            checked = settings.keepScreenOn,
            onSwitch = onSwitch,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            header(R.string.settings_section_language)
            item(key = SettingRow.APP_LANGUAGE) {
                AppLanguageRow(
                    launch = launch,
                    modifier = Modifier.testTag(settingRowTag(SettingRow.APP_LANGUAGE)),
                )
            }
        }
    }
}

/** A section header item, keyed by its string. */
private fun LazyListScope.header(@StringRes text: Int) {
    item(key = text) { SectionHeader(stringResource(text)) }
}

/** A switch row item without a lock. */
private fun LazyListScope.switchItem(
    row: SettingRow,
    @StringRes headline: Int,
    @StringRes summary: Int,
    checked: Boolean,
    onSwitch: (SettingRow, Boolean) -> Unit,
) {
    item(key = row) {
        SettingsSwitchRow(
            headline = stringResource(headline),
            summary = stringResource(summary),
            checked = checked,
            onCheckedChange = { onSwitch(row, it) },
            modifier = Modifier.testTag(settingRowTag(row)),
        )
    }
}

/** A choice row item without a lock or trailing content. */
private fun LazyListScope.choiceItem(
    row: SettingRow,
    @StringRes headline: Int,
    @StringRes summary: Int,
    onChoose: (SettingRow) -> Unit,
) {
    item(key = row) {
        SettingsChoiceRow(
            headline = stringResource(headline),
            summary = stringResource(summary),
            onClick = { onChoose(row) },
            modifier = Modifier.testTag(settingRowTag(row)),
        )
    }
}

/**
 * The App language link row (API 33+, D-102): the current per-app locale or "System default", read
 * once per configuration (a language change recreates the activity), and a tap that opens the
 * system page through [launch].
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AppLanguageRow(launch: (Intent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val summary = remember(context, configuration) { appLanguageSummary(context) }
    SettingsLinkRow(
        headline = stringResource(R.string.setting_app_language),
        summary = summary,
        onClick = { openAppLanguageSettings(context, launch) },
        modifier = modifier,
    )
}

/**
 * The `ChoiceDialog` of choice [row] over the current [settings]; a selection writes through
 * [onUpdate]. The Palette options each show their own `PaletteSwatch` in the effective light or
 * [dark] mode (D-234).
 */
@Composable
private fun SettingDialog(
    row: SettingRow,
    settings: Settings,
    dark: Boolean,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onDismiss: () -> Unit,
) {
    when (row) {
        SettingRow.THEME ->
            ChoiceDialog(
                title = stringResource(R.string.setting_theme),
                options = choices(ThemeMode.entries) { it.label },
                selected = settings.themeMode,
                onSelect = { mode -> onUpdate { it.copy(themeMode = mode) } },
                onDismiss = onDismiss,
            )
        SettingRow.PALETTE ->
            ChoiceDialog(
                title = stringResource(R.string.setting_palette),
                options = choices(PaletteId.entries) { it.label },
                selected = settings.palette,
                onSelect = { id -> onUpdate { it.copy(palette = id) } },
                onDismiss = onDismiss,
                optionExtra = { id ->
                    val option = resolveTilePalette(id, dark)
                    PaletteSwatch(
                        option.palette,
                        option.skin,
                        Modifier.padding(top = GameDimens.Space4),
                    )
                },
            )
        SettingRow.MOTION ->
            ChoiceDialog(
                title = stringResource(R.string.setting_motion),
                options = choices(MotionPreference.entries) { it.label },
                selected = settings.motion,
                onSelect = { motion -> onUpdate { it.copy(motion = motion) } },
                onDismiss = onDismiss,
            )
        SettingRow.MOVE_TIMING ->
            ChoiceDialog(
                title = stringResource(R.string.setting_move_timing),
                options = choices(MoveTiming.entries) { it.label },
                selected = settings.moveTiming,
                onSelect = { timing -> onUpdate { it.copy(moveTiming = timing) } },
                onDismiss = onDismiss,
            )
        SettingRow.SWIPE_SENSITIVITY ->
            ChoiceDialog(
                title = stringResource(R.string.setting_swipe_sensitivity),
                options = choices(SwipeSensitivity.entries, { it.detail }) { it.label },
                selected = settings.swipeSensitivity,
                onSelect = { level -> onUpdate { it.copy(swipeSensitivity = level) } },
                onDismiss = onDismiss,
            )
        else -> Unit
    }
}

/** The options of [values] in order, labelled by [label] and, when given, detailed by [detail]. */
@Composable
@ReadOnlyComposable
private fun <T> choices(
    values: List<T>,
    detail: ((T) -> Int)? = null,
    label: (T) -> Int,
): ImmutableList<Choice<T>> =
    values
        .map { value ->
            Choice(
                value = value,
                label = stringResource(label(value)),
                supporting = detail?.let { stringResource(it(value)) },
            )
        }
        .toImmutableList()

private val ThemeMode.label: Int
    @StringRes
    get() =
        when (this) {
            ThemeMode.SYSTEM -> R.string.theme_system
            ThemeMode.LIGHT -> R.string.theme_light
            ThemeMode.DARK -> R.string.theme_dark
        }

private val PaletteId.label: Int
    @StringRes
    get() =
        when (this) {
            PaletteId.CLASSIC -> R.string.palette_classic
            PaletteId.NEON -> R.string.palette_neon
        }

private val MotionPreference.label: Int
    @StringRes
    get() =
        when (this) {
            MotionPreference.FULL -> R.string.motion_full
            MotionPreference.REDUCED -> R.string.motion_reduced
            MotionPreference.OFF -> R.string.motion_off
        }

private val MoveTiming.label: Int
    @StringRes
    get() =
        when (this) {
            MoveTiming.DURING_SWIPE -> R.string.move_timing_swipe
            MoveTiming.ON_RELEASE -> R.string.move_timing_release
        }

private val SwipeSensitivity.label: Int
    @StringRes
    get() =
        when (this) {
            SwipeSensitivity.LOW -> R.string.sensitivity_low
            SwipeSensitivity.MEDIUM -> R.string.sensitivity_medium
            SwipeSensitivity.HIGH -> R.string.sensitivity_high
        }

/** The option's supporting line (DESIGN_SYSTEM.md §6.8 row 8: 40 / 24 / 12 dp, D-050). */
private val SwipeSensitivity.detail: Int
    @StringRes
    get() =
        when (this) {
            SwipeSensitivity.LOW -> R.string.sensitivity_low_detail
            SwipeSensitivity.MEDIUM -> R.string.sensitivity_medium_detail
            SwipeSensitivity.HIGH -> R.string.sensitivity_high_detail
        }
