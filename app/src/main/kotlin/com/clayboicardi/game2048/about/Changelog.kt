package com.clayboicardi.game2048.about

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.clayboicardi.game2048.R
import kotlinx.collections.immutable.ImmutableList

/**
 * The What's new entries (D-009; ARCHITECTURE.md §4.13): each versionCode of this app that has a
 * change list, mapped to its bullet lines, string resources in order. v1.0 ships 30000, the first
 * versionCode of this application id, with `whatsnew_30000_1` … `whatsnew_30000_5`. A later release
 * adds its own entry, with its strings in every locale.
 */
val CHANGELOG: Map<Int, List<Int>> =
    mapOf(
        30000 to
            listOf(
                R.string.whatsnew_30000_1,
                R.string.whatsnew_30000_2,
                R.string.whatsnew_30000_3,
                R.string.whatsnew_30000_4,
                R.string.whatsnew_30000_5,
            ),
    )

/**
 * One version of the What's new list (D-009; ARCHITECTURE.md §4.13): its [versionCode] and its
 * bullet [lines], string resources in order (`whatsnew_30000_1` … `whatsnew_30000_5` for 30000).
 * `AppViewModel.whatsNew` carries the entries newer than `lastSeenVersionCode`; the About screen
 * lists every entry of [CHANGELOG].
 */
@Immutable
data class ChangelogEntry(val versionCode: Int, @param:StringRes val lines: ImmutableList<Int>)

/**
 * The versionName of [versionCode] under D-130's scheme `MAJOR·10000 + MINOR·100 + PATCH`, in Latin
 * digits: "3.0.0" for 30000. The About screen titles each [CHANGELOG] entry with it.
 */
fun versionNameOf(versionCode: Int): String {
    val major = versionCode / MAJOR_STEP
    val minor = versionCode % MAJOR_STEP / MINOR_STEP
    val patch = versionCode % MINOR_STEP
    return "$major.$minor.$patch"
}

/** D-130: one major version is 10000 version codes. */
private const val MAJOR_STEP = 10_000

/** D-130: one minor version is 100 version codes. */
private const val MINOR_STEP = 100
