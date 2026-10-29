package com.clayboicardi.game2048.about

import androidx.compose.runtime.Immutable
import com.clayboicardi.game2048.data.AppJson
import kotlinx.serialization.Serializable

/**
 * `res/raw/legacy_history.json` (D-124, D-209, D-213; L10N_STORE_LEGAL.md §4.5), built from
 * `docs/modernization/legacy-credits.md`: the About screen's [credits] rows ("name — role", in file
 * order) and the 9 [releases] of the legacy `changelog_master.xml`, newest first in file order.
 * English static data, never translated (D-124).
 */
@Immutable
@Serializable
data class LegacyHistory(val credits: List<Credit>, val releases: List<LegacyRelease>)

/** Parses `legacy_history.json`'s [json]; throws `SerializationException` on a malformed file. */
fun parseLegacyHistory(json: String): LegacyHistory =
    AppJson.decodeFromString(LegacyHistory.serializer(), json)

/** One credits row: [name] and [role], shown as "name — role" (DESIGN_SYSTEM.md §6.11). */
@Immutable @Serializable data class Credit(val name: String, val role: String)

/**
 * One legacy release: [version] and [versionCode] as `changelog_master.xml` gave them (1.95 and
 * 1.91 both carry 18), titled "2.2 (25)", and its [changes] in order.
 */
@Immutable
@Serializable
data class LegacyRelease(val version: String, val versionCode: Int, val changes: List<String>)
