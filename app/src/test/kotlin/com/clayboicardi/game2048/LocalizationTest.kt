package com.clayboicardi.game2048

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.about.CHANGELOG
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.game.board.TileLabels
import com.clayboicardi.game2048.game.header.BEST_TAG
import com.clayboicardi.game2048.game.header.SCORE_TAG
import com.clayboicardi.game2048.screenshots.GoldenRig
import com.clayboicardi.game2048.ui.components.chipLabelText
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.google.common.truth.Truth.assertWithMessage
import java.text.Normalizer
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

/**
 * The 21 locales as the app resolves them (L10N_STORE_LEGAL.md §1.1, §1.2, §1.5, §1.10, §1.13;
 * D-100–D-103, D-009): the What's new lines, the untranslated `app_name`, the generated locale
 * config, the legacy `settings` translations, the Russian plural categories, the Greek chip labels
 * without stress marks (L-OI-13) and the Latin game numerals of the Persian Game screen.
 * `TranslationQualityTest` checks the translations' text; the two classes do not overlap.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LocalizationTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val app: Context = ApplicationProvider.getApplicationContext()

    /** F24-AC8 */
    @Test
    fun changelogStringsResolveInEveryLocale() {
        val ids = CHANGELOG.values.flatten()
        assertWithMessage("CHANGELOG string ids").that(ids).isNotEmpty()
        val problems = buildList {
            for (tag in LOCALES) {
                val res = resourcesFor(tag)
                for (id in ids) {
                    if (res.getString(id).isBlank())
                        add("[$tag] ${app.resources.getResourceEntryName(id)}")
                }
            }
        }
        assertWithMessage("blank What's new lines").that(problems).isEmpty()
    }

    /** F28-AC2 */
    @Test
    fun appNameUntranslatedInEveryLocale() {
        // The default value is "2048" ("2048 Debug" in this debug variant, ARCHITECTURE.md §8.5).
        val default = app.resources.getString(R.string.app_name)
        assertWithMessage("the default app_name").that(default).startsWith("2048")
        for (tag in LOCALES) {
            assertWithMessage("app_name in $tag")
                .that(resourcesFor(tag).getString(R.string.app_name))
                .isEqualTo(default)
        }
    }

    /** F28-AC3 */
    @Test
    fun localeConfigListsTwentyOneLocales() {
        val parser = app.resources.getXml(R.xml._generated_res_locale_config)
        val locales = buildList {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    add(checkNotNull(parser.getAttributeValue(ANDROID_NS, "name")))
                }
            }
        }
        assertWithMessage("<locale> entries").that(locales).containsNoDuplicates()
        assertWithMessage("the generated locale config of the debug unit-test variant")
            .that(locales)
            .containsExactlyElementsIn(CONFIG_LOCALES + PSEUDO_LOCALES)
    }

    /** F28-AC4 */
    @Test
    fun legacySettingsTranslationsKept() {
        val expected =
            mapOf(
                "de" to "Einstellungen",
                "ru" to "Настройки",
                "fa" to "تنظیمات",
                "cs" to "Nastavení",
            )
        for ((tag, text) in expected) {
            val value = resourcesFor(tag).getString(R.string.settings)
            assertWithMessage("settings in $tag").that(value).isEqualTo(text)
            assertWithMessage("U+FEFF in $tag's settings").that(value).doesNotContain("﻿")
        }
    }

    /** F28-AC5 */
    @Test
    fun pluralsDistinguishRussianCategories() {
        val res = resourcesFor("ru")
        // The same argument for every quantity, so only the category's item tells them apart:
        // 1 is "one", 3 "few" and 5 "many" in Russian (L10N_STORE_LEGAL.md §1.5).
        val texts = listOf(1, 3, 5).map { res.getQuantityString(R.plurals.moves_count, it, "#") }
        for (text in texts) assertWithMessage("a moves_count item").that(text).contains("#")
        assertWithMessage("moves_count for 1, 3 and 5 in ru").that(texts.toSet()).hasSize(3)
    }

    /** L-OI-13 (D-261): Greek chip labels are upper-cased without stress marks. */
    @Test
    fun greekChipLabelsDropStressMarks() {
        // The labels that ScoreChipLabel and the game-over card upper-case: the JVM keeps the
        // tonos when it upper-cases Greek ("Ρεκόρ" → "ΡΕΚΌΡ"), so chipLabelText removes U+0301.
        val greek = Locale.forLanguageTag("el")
        val res = resourcesFor("el")
        val expected =
            mapOf(
                R.string.score_label to "ΣΚΟΡ",
                R.string.best_label to "ΡΕΚΟΡ",
                R.string.new_best_label to "ΝΕΟ ΡΕΚΟΡ",
                R.string.best_tile to "ΚΑΛΥΤΕΡΟ ΠΛΑΚΙΔΙΟ",
            )
        for ((id, text) in expected) {
            val key = app.resources.getResourceEntryName(id)
            val label = chipLabelText(res.getString(id), greek)
            assertWithMessage("el $key").that(label).isEqualTo(text)
            assertWithMessage("U+0301 in el $key after NFD")
                .that(Normalizer.normalize(label, Normalizer.Form.NFD))
                .doesNotContain(COMBINING_ACUTE)
        }

        // Outside el the upper-cased label keeps its accents.
        val spanish = resourcesFor("es").getString(R.string.new_best_label)
        assertWithMessage("es new_best_label")
            .that(chipLabelText(spanish, Locale.forLanguageTag("es")))
            .isEqualTo("NUEVO RÉCORD")
    }

    /** F28-AC6 */
    @Test
    @Config(qualifiers = "+fa")
    fun gameNumeralsAsciiInPersian() {
        assertWithMessage("the test's locale")
            .that(app.resources.configuration.locales[0].language)
            .isEqualTo("fa")
        val game = GoldenFixtures.FIX_MID_4
        GoldenRig(tmp.newFolder(), game).use { rig ->
            compose.setContent { rig.Content() }
            rig.awaitReady(compose)
            compose.waitForIdle()

            // The Score and Best chips draw their values as text (Motion Off: one text each).
            for ((tag, value) in
                listOf(SCORE_TAG to game.state.score, BEST_TAG to game.bestAtStart)) {
                val numerals = textsUnder(tag).filter { text -> text.any(Char::isDigit) }
                assertWithMessage("the numerals of $tag").that(numerals).isNotEmpty()
                for (text in numerals) {
                    assertWithMessage("a numeral of $tag").that(text).matches(GAME_NUMERAL)
                }
                assertWithMessage("the value of $tag")
                    .that(numerals.joinToString(""))
                    .isEqualTo(value.toString())
            }

            // Tiles carry no semantics (D-080): their label is TileLabels.label at the cell's
            // side, which the board's text cache measures and draws; the cell nodes speak the
            // full decimal.
            val cells = cellNodes()
            val board = game.state.board
            val n = game.state.size.n
            assertWithMessage("cell nodes").that(cells).hasSize(n * n)
            val density = compose.density.density
            var tiles = 0
            for (cell in cells) {
                val item = cell.config[SemanticsProperties.CollectionItemInfo]
                val exp = board[item.rowIndex * n + item.columnIndex]
                val description = cell.config[SemanticsProperties.ContentDescription].single()
                val digits = description.filter(Char::isDigit)
                assertWithMessage("the digits of \"$description\"").that(digits).matches("[0-9]+")
                if (exp == 0) continue
                tiles++
                val label = TileLabels.label(exp, cell.size.width / density).text
                assertWithMessage("the label of 2^$exp").that(label).matches(GAME_NUMERAL)
                assertWithMessage("cell \"$description\"")
                    .that(description)
                    .contains(TileLabels.fullDecimal(exp))
            }
            assertWithMessage("tiles on the board")
                .that(tiles)
                .isEqualTo((0 until board.cellCount).count { board[it] != 0 })
        }
    }

    private fun resourcesFor(tag: String): Resources {
        val configuration =
            Configuration(app.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
        return app.createConfigurationContext(configuration).resources
    }

    /** The texts of every text node inside the node tagged [tag], in the unmerged tree. */
    private fun textsUnder(tag: String): List<String> =
        compose
            .onAllNodes(hasAnyAncestor(hasTestTag(tag)) and HAS_TEXT, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced }
            .map { it.config[SemanticsProperties.Text].joinToString("") }

    /** The N² board cell nodes: each carries its `CollectionItemInfo` (D-080). */
    private fun cellNodes(): List<SemanticsNode> =
        compose
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionItemInfo),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()

    private companion object {
        /** en (the default resources) and the 20 translations (D-100). */
        val LOCALES =
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

        /** The locale config's 21 entries: the default locale is en-US (D-102). */
        val CONFIG_LOCALES = listOf("en-US") + LOCALES.drop(1)

        /** The D-106 pseudo-locales of the debug variant. */
        val PSEUDO_LOCALES = listOf("en-XA", "ar-XB")

        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        /** The combining acute accent: the Greek tonos after NFD decomposition. */
        const val COMBINING_ACUTE = "\u0301"

        /** A game numeral: ASCII digits and the compact suffixes (DESIGN_SYSTEM.md §3.4). */
        const val GAME_NUMERAL = "[0-9KMGTPE]+"

        val HAS_TEXT = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
    }
}
