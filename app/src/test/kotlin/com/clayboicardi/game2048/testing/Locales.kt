package com.clayboicardi.game2048.testing

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** en (the default resources) and the 20 translations (D-100), as the sweep tests iterate them. */
val APP_LOCALES =
    listOf(
        "en",
        "cs",
        "da",
        "de",
        "el",
        "eo",
        "es",
        "fa",
        "fr",
        "hu",
        "it",
        "ko",
        "lt",
        "nl",
        "pl",
        "pt",
        "ru",
        "sl",
        "sr",
        "tr",
        "uk",
    )

/** [app] in the configuration of the locale [tag]. */
fun localizedContext(app: Context, tag: String): Context {
    val configuration =
        Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
    return app.createConfigurationContext(configuration)
}

/**
 * [content] in the context, resources, configuration and layout direction of the locale [tag], so
 * one composition can be shown in each locale in turn (D-265, D-268).
 */
@Composable
fun Localized(app: Context, tag: String, content: @Composable () -> Unit) {
    val context = remember(app, tag) { localizedContext(app, tag) }
    val configuration = context.resources.configuration
    val rtl = configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides configuration,
        LocalResources provides context.resources,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}
