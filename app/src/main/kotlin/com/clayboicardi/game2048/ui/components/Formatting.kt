package com.clayboicardi.game2048.ui.components

import java.text.NumberFormat
import java.util.Locale

// Number and duration formatting (L10N_STORE_LEGAL.md §1.10; D-103). Game numerals (tiles, score,
// best, "+N", share text) use `Long.toString()` and never come here. Prose numbers, percentages and
// durations use these helpers, which always produce Latin (ASCII) digits.

/** [locale] with the Unicode `nu-latn` keyword: Latin digits with the locale's grouping. */
fun latnLocale(locale: Locale): Locale =
    Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()

/** Maps U+0660–U+0669 and U+06F0–U+06F9 to ASCII digits, in case a runtime ignores "nu-latn". */
fun String.toLatinDigits(): String =
    buildString(length) {
        for (c in this@toLatinDigits) {
            append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                },
            )
        }
    }

/** A prose count: Latin digits with the grouping of [locale] ("24,680", "24.680"). */
fun formatCount(n: Long, locale: Locale): String =
    NumberFormat.getIntegerInstance(latnLocale(locale)).format(n).toLatinDigits()

/** [part] of [whole] as a whole percentage in [locale], Latin digits; 0 % when [whole] is 0. */
fun formatPercent(part: Long, whole: Long, locale: Locale): String =
    NumberFormat.getPercentInstance(latnLocale(locale))
        .format(if (whole == 0L) 0.0 else part.toDouble() / whole)
        .toLatinDigits()

/** A play time: `m:ss` under one hour, else `h:mm:ss`, with `Locale.ROOT` (Latin digits). */
fun formatElapsed(ms: Long): String {
    val s = ms / MS_PER_SECOND
    val h = s / SECONDS_PER_HOUR
    val m = s % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val sec = s % SECONDS_PER_MINUTE
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, sec)
    }
}

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
