package com.clayboicardi.game2048.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.clayboicardi.game2048.R
import kotlinx.coroutines.launch

/**
 * Shares [text] as plain text (D-002, D-008; FEATURES.md F10 item 4): `Intent(ACTION_SEND)` of type
 * `text/plain` with `EXTRA_TEXT`, wrapped in `Intent.createChooser(intent, chooserTitle)`
 * (`share_chooser_title`). No file, no `FileProvider` and no permission; the system chooser handles
 * a device with no target. A context that is not an activity starts it in a new task.
 */
fun shareText(context: Context, text: String, chooserTitle: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = PLAIN_TEXT
            putExtra(Intent.EXTRA_TEXT, text)
        }
    val chooser = Intent.createChooser(send, chooserTitle)
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** The `ACTION_VIEW` intent of [url] (About's source link, DESIGN_SYSTEM.md §6.11). */
fun viewUrlIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, url.toUri())

/**
 * Starts [intent] from [context] and returns `true`, or returns `false` and starts nothing when no
 * activity handles it (`ActivityNotFoundException`; D-009, FEATURES.md F11 item 6, F25). A context
 * that is not an activity starts it in a new task. It never resolves the intent through the package
 * manager first (D-134).
 */
fun startIntent(context: Context, intent: Intent): Boolean {
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Opens [url] with `ACTION_VIEW` ([startIntent]); `false` when no app can open it. */
fun openUrl(context: Context, url: String): Boolean = startIntent(context, viewUrlIntent(url))

/**
 * The link opener of a sub-screen with a snackbar host (DESIGN_SYSTEM.md §7.6; ARCHITECTURE.md
 * §10.3 `openUrl` with its snackbar fallback; D-248): the returned function starts an intent
 * ([startIntent]), and when no activity handles it shows `link_open_failed` = "No app can open this
 * link" in [snackbars], replacing the snackbar shown (at most one at a time). About's source link
 * passes [viewUrlIntent]; Settings' App language passes `ACTION_APP_LOCALE_SETTINGS`.
 */
@Composable
fun rememberIntentLauncher(snackbars: SnackbarHostState): (Intent) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.link_open_failed)
    return remember(context, scope, snackbars, failed) {
        { intent ->
            if (!startIntent(context, intent)) {
                scope.launch {
                    snackbars.currentSnackbarData?.dismiss()
                    snackbars.showSnackbar(failed)
                }
            }
        }
    }
}

/** The MIME type of the share text. */
private const val PLAIN_TEXT = "text/plain"
