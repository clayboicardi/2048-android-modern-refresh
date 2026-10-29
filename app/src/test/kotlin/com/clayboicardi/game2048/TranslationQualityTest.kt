package com.clayboicardi.game2048

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import java.lang.reflect.Method
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L-AC4
 *
 * Machine checks of the 20 translations (L10N_STORE_LEGAL.md §1.9; D-100, D-103, D-105): every
 * translatable `R.string` and `R.plurals` key, enumerated by reflection and compared with its
 * English value in each locale. Each method collects every problem as `[locale] key: problem` and
 * fails with one problem per line, so one run lists them all.
 */
@RunWith(AndroidJUnit4::class)
class TranslationQualityTest {
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val english: Resources = resourcesFor("en")

    /** L-AC4 */
    @Test
    fun everyKeyResolvesNonBlankInEveryLocale() {
        val problems = mutableListOf<String>()
        forEachLocale { tag, res ->
            for ((key, id) in STRINGS) {
                if (res.getString(id).isBlank()) problems += "[$tag] $key: blank"
            }
            for ((key, id) in PLURALS) {
                for (n in QUANTITIES) {
                    val text = res.getQuantityString(id, n, *pluralArgs(key, "#N#"))
                    if (text.isBlank()) problems += "[$tag] $key: blank for $n"
                }
                for ((quantity, item) in pluralItems(res, id)) {
                    if (item.isBlank()) problems += "[$tag] $key: blank $quantity item"
                }
            }
        }
        assertNoProblems(problems)
    }

    /** L-AC4 */
    @Test
    fun placeholdersMatchEnglish() {
        val problems = mutableListOf<String>()
        forEachLocale { tag, res ->
            forEachValue(res) { key, label, value ->
                val expected = placeholders(englishValue(key))
                val actual = placeholders(value)
                if (actual != expected) {
                    problems += "[$tag] $key$label: placeholders $actual, English has $expected"
                }
                if (STRAY_PERCENT.containsMatchIn(value.replace(PLACEHOLDER, ""))) {
                    problems += "[$tag] $key$label: a % outside a %n\$s placeholder"
                }
            }
        }
        assertNoProblems(problems)
    }

    /** L-AC4 */
    @Test
    fun noNonLatinDigits() {
        val problems = mutableListOf<String>()
        forEachLocale { tag, res ->
            forEachValue(res) { key, label, value ->
                val bad = value.filter { it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9' }
                if (bad.isNotEmpty()) problems += "[$tag] $key$label: non-Latin digits \"$bad\""
            }
        }
        assertNoProblems(problems)
    }

    /** L-AC4 */
    @Test
    fun neverTranslateTokensKept() {
        val problems = mutableListOf<String>()
        forEachLocale { tag, res ->
            forEachValue(res) { key, label, value ->
                for (token in KEPT_TOKENS) {
                    if (token in englishValue(key) && token !in value) {
                        problems += "[$tag] $key$label: \"$token\" is missing"
                    }
                }
            }
        }
        assertNoProblems(problems)
    }

    /** L-AC4 */
    @Test
    fun scriptMatchesLocale() {
        val problems = mutableListOf<String>()
        forEachLocale { tag, res ->
            val script = SCRIPTS.getValue(tag)
            forEachValue(res) { key, label, value ->
                val letters = lettersToCheck(value)
                val foreign = letters.filterNot(script::contains).toSet()
                if (foreign.isNotEmpty()) {
                    problems += "[$tag] $key$label: letters outside ${script.name}: $foreign"
                }
                if (script != LATIN && letters.isNotEmpty() && letters.none(script::contains)) {
                    problems += "[$tag] $key$label: no ${script.name} letter"
                }
            }
        }
        assertNoProblems(problems)
    }

    /** L-AC4 */
    @Test
    fun alphabetRulesPerLocale() {
        val problems = mutableListOf<String>()
        forEachLocale { tag, res ->
            val forbidden = FORBIDDEN_LETTERS[tag]
            forEachValue(res) { key, label, value ->
                if (forbidden != null) {
                    val bad = value.filter { it.lowercaseChar() in forbidden }.toSet()
                    if (bad.isNotEmpty()) problems += "[$tag] $key$label: forbidden letters $bad"
                }
                if (tag == "eo" && ESPERANTO_X_SYSTEM.containsMatchIn(value)) {
                    problems += "[$tag] $key$label: x-system spelling (write ĉ ĝ ĥ ĵ ŝ ŭ)"
                }
            }
        }
        assertNoProblems(problems)
    }

    /** L-AC4 */
    @Test
    fun notIdenticalToEnglish() {
        val allowed = identicalToEnglish()
        val problems = mutableListOf<String>()
        for ((scope, key) in allowed.keys) {
            if (scope != "*" && scope !in LOCALES) {
                problems += "identical-to-english: unknown locale $scope"
            }
            if (key !in STRINGS && key !in PLURALS) {
                problems += "identical-to-english: unknown key $key"
            }
        }
        val used = mutableSetOf<Pair<String, String>>()
        forEachLocale { tag, res ->
            val identical =
                STRINGS.filter { (_, id) -> res.getString(id) == english.getString(id) }.keys +
                    PLURALS.filter { (_, id) -> pluralItems(res, id) == pluralItems(english, id) }
                        .keys
            for (key in identical) {
                val exemption = listOf(tag to key, "*" to key).firstOrNull { it in allowed }
                if (exemption == null) {
                    problems += "[$tag] $key: identical to English \"${englishValue(key)}\""
                } else {
                    used += exemption
                }
            }
        }
        for ((scope, key) in allowed.keys - used) {
            if (scope != "*") problems += "identical-to-english: $scope $key is no longer identical"
        }
        assertNoProblems(problems)
    }

    /** L-AC4 */
    @Test
    fun localeHasDistinctiveLetters() {
        val problems = mutableListOf<String>()
        forEachLocale { tag, res ->
            val letters = DISTINCTIVE_LETTERS[tag] ?: return@forEachLocale
            val all = buildString { forEachValue(res) { _, _, value -> append(value) } }
            if (all.none { it in letters }) problems += "[$tag]: none of \"$letters\" in any value"
        }
        assertNoProblems(problems)
    }

    /** L-AC4 */
    @Test
    fun pluralsCarryTheNumber() {
        val problems = mutableListOf<String>()
        forEachLocale { tag, res ->
            for ((key, id) in PLURALS) {
                for (n in QUANTITIES) {
                    val text = res.getQuantityString(id, n, *pluralArgs(key, "#N#"))
                    if ("#N#" !in text) problems += "[$tag] $key: no number for $n: \"$text\""
                }
                val placeholder = "%${QUANTITY_ARGUMENT[key] ?: 1}\$s"
                for ((quantity, item) in pluralItems(res, id)) {
                    if (placeholder !in item) {
                        problems += "[$tag] $key: the $quantity item lacks $placeholder"
                    }
                }
            }
        }
        assertNoProblems(problems)
    }

    /** L-AC4 */
    @Test
    fun chipLabelsAtMostTwelveCharacters() {
        val problems = mutableListOf<String>()
        forEachLocale { tag, res ->
            for (key in CHIP_LABELS) {
                val value = res.getString(STRINGS.getValue(key))
                val length = value.codePointCount(0, value.length)
                if (length > CHIP_LABEL_MAX) {
                    problems +=
                        "[$tag] $key: \"$value\" has $length characters, at most $CHIP_LABEL_MAX"
                }
            }
        }
        assertNoProblems(problems)
    }

    private fun resourcesFor(tag: String): Resources {
        val configuration =
            Configuration(app.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
        return app.createConfigurationContext(configuration).resources
    }

    private inline fun forEachLocale(block: (tag: String, res: Resources) -> Unit) {
        for (tag in LOCALES) block(tag, resourcesFor(tag))
    }

    /** Every translatable string, then every item of every plural, with a label for the item. */
    private inline fun forEachValue(
        res: Resources,
        block: (key: String, label: String, value: String) -> Unit,
    ) {
        for ((key, id) in STRINGS) block(key, "", res.getString(id))
        for ((key, id) in PLURALS) {
            for ((quantity, item) in pluralItems(res, id)) block(key, " ($quantity)", item)
        }
    }

    /** The English source a translated value is compared with: a plural's `other` item. */
    private fun englishValue(key: String): String =
        STRINGS[key]?.let(english::getString)
            ?: pluralItems(english, PLURALS.getValue(key)).getValue("other")

    private fun identicalToEnglish(): Map<Pair<String, String>, String> {
        val stream =
            checkNotNull(javaClass.classLoader?.getResourceAsStream(IDENTICAL_FILE)) {
                "$IDENTICAL_FILE is not on the test classpath"
            }
        return stream.bufferedReader().useLines { lines ->
            lines
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .associate { line ->
                    val reason = line.substringAfter('#', "").trim()
                    val fields = line.substringBefore('#').trim().split(Regex("\\s+"))
                    check(fields.size == 2 && reason.isNotEmpty()) {
                        "$IDENTICAL_FILE: \"$line\" is not \"<locale or *> <key> # reason\""
                    }
                    (fields[0] to fields[1]) to reason
                }
        }
    }

    /** Fails with one problem per line, so a run can be filtered by its `[locale]` prefix. */
    private fun assertNoProblems(problems: List<String>) {
        if (problems.isNotEmpty()) {
            assertWithMessage(problems.joinToString("\n", prefix = "${problems.size} problems:\n"))
                .fail()
        }
    }

    /** A Unicode script as the ranges whose letters belong to it. */
    private class Script(val name: String, vararg val ranges: CharRange) {
        fun contains(c: Char): Boolean = ranges.any { c in it }
    }

    private companion object {
        val LOCALES =
            listOf(
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

        /** The ✱ keys of L10N_STORE_LEGAL.md §1.3: `translatable="false"`, only in `values/`. */
        val NON_TRANSLATABLE =
            setOf(
                "app_name",
                "board_size_supporting",
                "game_over_summary",
                "stats_wins_value",
                "palette_classic",
                "palette_neon",
                "htp_accessibility",
                "about_source_code_url",
                "license_mit_name",
                "license_ofl_name",
                "license_apache_name",
            )

        val STRINGS: Map<String, Int> = resourceIds(R.string::class.java) - NON_TRANSLATABLE
        val PLURALS: Map<String, Int> = resourceIds(R.plurals::class.java)

        /** L10N_STORE_LEGAL.md §1.9 `pluralsCarryTheNumber`. */
        val QUANTITIES = listOf(0, 1, 2, 3, 4, 5, 11, 12, 21, 22, 25, 101, 102, 1_000_000)

        /** The argument that carries a plural's quantity, where it is not `%1$s` (§1.5 rule 6). */
        val QUANTITY_ARGUMENT = mapOf("a11y_merged_more" to 4)

        /**
         * `R.attr` ids of the quantity items of a plurals bag
         * (`android.content.res.ResourcesImpl`).
         */
        val QUANTITY_ATTRIBUTES =
            linkedMapOf(
                "zero" to 0x01000005,
                "one" to 0x01000006,
                "two" to 0x01000007,
                "few" to 0x01000008,
                "many" to 0x01000009,
                "other" to 0x01000004,
            )

        /** `AssetManager.getResourceBagText`, public in the framework and hidden from the SDK. */
        val BAG_TEXT: Method =
            AssetManager::class
                .java
                .getDeclaredMethod(
                    "getResourceBagText",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )
                .apply { isAccessible = true }

        val PLACEHOLDER = Regex("%\\d+\\\$s")
        val STRAY_PERCENT = Regex("%")

        val KEPT_TOKENS =
            listOf(
                "2048",
                "3×3",
                "8×8",
                "TalkBack",
                "Android",
                "Google",
                "Ctrl+Z",
                "Neon Arcade",
                "W A S D",
                "H J K L",
                "N · R",
            )

        /** Latin tokens allowed in every locale (§1.9), longest first. */
        val ALLOWED_TOKENS =
            Regex(
                "Classic Refined|Neon Arcade|Backspace|TalkBack|Android|Google|GitHub|D-pad|Ctrl" +
                    "|(?<!\\p{L})[WASDHJKLUZNR](?!\\p{L})",
            )

        val LATIN = Script("Latin", 'A'..'Z', 'a'..'z', '\u00C0'..'\u024F')
        val CYRILLIC = Script("Cyrillic", '\u0400'..'\u04FF')
        val SCRIPTS: Map<String, Script> = LOCALES.associateWith { tag ->
            when (tag) {
                "ru",
                "uk",
                "sr" -> CYRILLIC
                "el" -> Script("Greek", '\u0370'..'\u03FF', '\u1F00'..'\u1FFF')
                "fa" -> Script("Arabic", '\u0600'..'\u06FF')
                "ko" -> Script("Hangul", '\uAC00'..'\uD7A3', '\u1100'..'\u11FF', '\u3130'..'\u318F')
                else -> LATIN
            }
        }

        val FORBIDDEN_LETTERS =
            mapOf(
                "ru" to "іїєґ",
                "uk" to "ыэъё",
                "sr" to "йщъыьэюяёєіїґ",
                "fa" to "يك",
            )
        val ESPERANTO_X_SYSTEM = Regex("(?i)[cghjsu]x")

        /** At least one of these letters appears in a Latin locale's values (nl: not checked). */
        val DISTINCTIVE_LETTERS =
            mapOf(
                "cs" to "ěščřžýůďťň",
                "da" to "æøå",
                "de" to "äöüß",
                "eo" to "ĉĝĥĵŝŭ",
                "es" to "ñáéíóú¿¡",
                "fr" to "éèêàçùœ",
                "hu" to "őűáéíóöú",
                "it" to "àèéìòù",
                "lt" to "ąčęėįšųūž",
                "pl" to "ąćęłńóśźż",
                "pt" to "ãõçáéêó",
                "sl" to "čšž",
                "tr" to "ğışçöüİ",
            )

        /** The chip and pill labels of L10N_STORE_LEGAL.md §1.7 rule 7 (D-259). */
        val CHIP_LABELS = listOf("score_label", "best_label", "new_best_label", "in_progress")
        const val CHIP_LABEL_MAX = 12

        const val IDENTICAL_FILE = "i18n/identical-to-english.txt"

        fun resourceIds(type: Class<*>): Map<String, Int> =
            type.fields.associate { it.name to it.getInt(null) }.toSortedMap()

        fun placeholders(value: String): List<String> =
            PLACEHOLDER.findAll(value).map { it.value }.sorted().toList()

        /** The letters left after placeholders, allowed tokens, digits and punctuation go. */
        fun lettersToCheck(value: String): List<Char> =
            value
                .replace(PLACEHOLDER, " ")
                .replace(ALLOWED_TOKENS, " ")
                .filter(Char::isLetter)
                .toList()

        /** The quantity items present in the locale's plurals bag, in CLDR category order. */
        fun pluralItems(res: Resources, id: Int): Map<String, String> =
            QUANTITY_ATTRIBUTES.mapNotNull { (quantity, attribute) ->
                    (BAG_TEXT.invoke(res.assets, id, attribute) as CharSequence?)?.let {
                        quantity to it.toString()
                    }
                }
                .toMap()

        /** Format arguments for [key]: the quantity argument is [number], the others fillers. */
        fun pluralArgs(key: String, number: String): Array<Any> {
            val quantityArgument = QUANTITY_ARGUMENT[key] ?: 1
            return Array(quantityArgument) { index ->
                if (index + 1 == quantityArgument) number else "#A${index + 1}#"
            }
        }
    }
}
