package com.clayboicardi.game2048.about

import kotlinx.collections.immutable.toImmutableList

/**
 * The What's new decision (D-009; ARCHITECTURE.md §4.13; FEATURES.md F24 item 1), pure. [lastSeen]
 * is `Settings.lastSeenVersionCode`, [current] the compile-time `BuildConfig.VERSION_CODE` (D-134)
 * and [changelog] maps a versionCode to its bullet lines ([CHANGELOG]):
 * - `lastSeen == 0` (fresh install, or a `settings.json` reset to its defaults): no entries, store
 *   [current];
 * - `lastSeen < current` (an update): the entries of every versionCode in `(lastSeen, current]`,
 *   oldest first, and store [current];
 * - otherwise (the same version, or a downgrade): no entries and nothing stored, so a downgrade
 *   keeps the higher value.
 */
fun decideWhatsNew(lastSeen: Int, current: Int, changelog: Map<Int, List<Int>>): WhatsNewDecision =
    when {
        lastSeen == 0 -> WhatsNewDecision(emptyList(), storeVersionCode = current)
        lastSeen < current ->
            WhatsNewDecision(
                changelog
                    .filterKeys { it in (lastSeen + 1)..current }
                    .toSortedMap()
                    .map { (code, lines) -> ChangelogEntry(code, lines.toImmutableList()) },
                storeVersionCode = current,
            )
        else -> WhatsNewDecision(emptyList(), storeVersionCode = null)
    }

/**
 * What [decideWhatsNew] decided: the [entries] the sheet shows (empty: no sheet) and the
 * versionCode to store as `lastSeenVersionCode` at once, before the sheet is visible
 * ([storeVersionCode]; `null`: write nothing).
 */
data class WhatsNewDecision(val entries: List<ChangelogEntry>, val storeVersionCode: Int?)
