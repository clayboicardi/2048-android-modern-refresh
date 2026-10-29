package com.clayboicardi.game2048.ui.components

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import org.junit.Test

/** Number and duration formatting (L10N_STORE_LEGAL.md §1.10; D-103). Plain JVM. */
class FormattingTest {
    /** F28-AC7 */
    @Test
    fun formatCountLatinDigitsWithLocaleGrouping() {
        assertThat(formatCount(24680, Locale.ENGLISH)).isEqualTo("24,680")
        assertThat(formatCount(24680, Locale.GERMAN)).isEqualTo("24.680")
        for (tag in listOf("fa", "ru")) {
            val text = formatCount(24680, Locale.forLanguageTag(tag))
            val digits = text.filter(Char::isDigit)
            assertWithMessage("$tag: $text").that(digits).isEqualTo("24680")
            assertWithMessage("$tag: $text only has ASCII digits")
                .that(digits.all { it in '0'..'9' })
                .isTrue()
        }
    }

    /** F28-AC8 */
    @Test
    fun formatElapsedFormats() {
        val saved = Locale.getDefault()
        try {
            for (tag in listOf("en", "fa")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                val shown = ELAPSED.map { formatElapsed(it) }
                assertWithMessage("default locale $tag")
                    .that(shown)
                    .containsExactly("0:00", "6:41", "1:00:00", "100:00:00")
                    .inOrder()
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    /** F28-AC9 */
    @Test
    fun percentUsesLatinDigits() {
        for (tag in listOf("en", "fa")) {
            val text = formatPercent(9, 37, Locale.forLanguageTag(tag))
            assertWithMessage("$tag: $text").that(text).contains("24")
        }
        assertThat(formatPercent(0, 0, Locale.ENGLISH)).isEqualTo("0%")
    }

    private companion object {
        /** 0 ms, 6:41, one hour and 100 hours. */
        val ELAPSED = listOf(0L, 401_000L, 3_600_000L, 360_000_000L)
    }
}
