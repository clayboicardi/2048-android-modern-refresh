package com.clayboicardi.game2048.data

import kotlinx.serialization.json.Json

/**
 * The one `Json` of every persisted document (D-041, ARCHITECTURE.md §4.3): unknown keys are
 * ignored, an unknown enum name or a `null` decodes to the field default (`coerceInputValues`),
 * `null` is never written, and every default is written so a file always holds every key.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}
