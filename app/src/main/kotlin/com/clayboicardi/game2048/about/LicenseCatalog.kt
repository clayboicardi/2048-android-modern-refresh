package com.clayboicardi.game2048.about

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.clayboicardi.game2048.R
import com.clayboicardi.game2048.data.AppJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * One entry of `res/raw/third_party_licenses.json` (D-122; L10N_STORE_LEGAL.md §4.4): its display
 * [name], its [groupOrPath] (a Maven group of `config/license-groups.txt`, a repository URL or a
 * file path), its SPDX [licenseId] and its [copyright] line. English static data.
 */
@Immutable
@Serializable
data class LicenseEntry(
    val name: String,
    val groupOrPath: String,
    val licenseId: String,
    val copyright: String,
)

/**
 * One group of the Licenses screen (DESIGN_SYSTEM.md §6.12): the [entries] of [licenseId] in file
 * order, and the license [text] its toggle reveals (`null`: no text, no toggle).
 */
@Immutable
data class LicenseGroup(
    val licenseId: String,
    val entries: List<LicenseEntry>,
    val text: String? = null,
)

/**
 * The license catalog (D-122; DESIGN_SYSTEM.md §6.12; L10N_STORE_LEGAL.md §4.4): the three SPDX ids
 * of the app's notices in the screen's [ORDER], their names and texts, and the parser of
 * `third_party_licenses.json`. Pure: the caller reads the raw files (`AboutViewModel`, on its I/O
 * dispatcher).
 */
object LicenseCatalog {
    /** gabrielecirulli/2048 and uberspot/2048-android (D-121). */
    const val MIT: String = "MIT"

    /** The Google Sans Flex numeral subset (D-076); absent under the font fallback. */
    const val OFL: String = "OFL-1.1"

    /** Material Symbols and the runtime classpath. */
    const val APACHE: String = "Apache-2.0"

    /** The group order of the screen: MIT, OFL-1.1, Apache-2.0. */
    val ORDER: List<String> = listOf(MIT, OFL, APACHE)

    /** Parses `third_party_licenses.json`; throws `SerializationException` on a malformed file. */
    fun parse(json: String): List<LicenseEntry> =
        AppJson.decodeFromString(ListSerializer(LicenseEntry.serializer()), json)

    /**
     * [entries] grouped by license in [ORDER], each group in file order; a license with no entry
     * has no group (the OFL group under the D-076 font fallback), and an id outside [ORDER] is left
     * out (`LicensesCoverageTest` keeps the file free of one).
     */
    fun group(entries: List<LicenseEntry>): List<LicenseGroup> = ORDER.mapNotNull { id ->
        entries
            .filter { it.licenseId == id }
            .takeIf { it.isNotEmpty() }
            ?.let { LicenseGroup(id, it) }
    }

    /**
     * The group header of [licenseId]: `license_mit_name`, `license_ofl_name` or
     * `license_apache_name`.
     */
    @StringRes
    fun nameOf(licenseId: String): Int =
        when (licenseId) {
            MIT -> R.string.license_mit_name
            OFL -> R.string.license_ofl_name
            else -> R.string.license_apache_name
        }

    /**
     * The raw license text of [licenseId]: `license_mit.txt` (a byte copy of `LICENSE`),
     * `ofl_google_sans_flex.txt` (the font's `OFL.txt`, verbatim) or `license_apache_2_0.txt`;
     * `null` for one without a text in this build.
     */
    @RawRes
    fun textOf(licenseId: String): Int? =
        when (licenseId) {
            MIT -> R.raw.license_mit
            OFL -> R.raw.ofl_google_sans_flex
            APACHE -> R.raw.license_apache_2_0
            else -> null
        }
}
