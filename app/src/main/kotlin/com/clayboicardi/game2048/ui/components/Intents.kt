package com.clayboicardi.game2048.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent

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

/** The MIME type of the share text. */
private const val PLAIN_TEXT = "text/plain"
