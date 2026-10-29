package com.clayboicardi.game2048.settings

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.clayboicardi.game2048.R

// The Settings row "App language" (L10N_STORE_LEGAL.md §1.13; D-102; FEATURES.md F11 item 6), on
// API 33+ only: below API 33 the app follows the system language and the row is hidden. Both
// helpers are `@RequiresApi(33)`, because the compiler inlines the API 33 constant
// `Settings.ACTION_APP_LOCALE_SETTINGS`; every caller sits inside the `SDK_INT >= 33` branch that
// shows the row.

/**
 * Opens the system per-app language page of this app: `ACTION_APP_LOCALE_SETTINGS` with the data
 * `package:<packageName>` of [context], started through [launch], which shows the
 * `link_open_failed` snackbar when no activity handles it (`rememberIntentLauncher`, F11-AC6).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun openAppLanguageSettings(context: Context, launch: (Intent) -> Unit) {
    launch(
        Intent(
            Settings.ACTION_APP_LOCALE_SETTINGS,
            Uri.fromParts(PACKAGE_SCHEME, context.packageName, null),
        ),
    )
}

/**
 * The App language row's summary: `setting_app_language_system` ("System default") while no per-app
 * locale is set (L-AC6), else the display name of the chosen locale in that locale, its first
 * letter upper-cased (L10N_STORE_LEGAL.md §1.13).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun appLanguageSummary(context: Context): String {
    val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
    if (locales == null || locales.isEmpty) {
        return context.getString(R.string.setting_app_language_system)
    }
    val locale = locales[0]
    return locale.getDisplayName(locale).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
    }
}

/** The URI scheme of a package (`package:<name>`), as `Settings` pages expect. */
private const val PACKAGE_SCHEME = "package"
