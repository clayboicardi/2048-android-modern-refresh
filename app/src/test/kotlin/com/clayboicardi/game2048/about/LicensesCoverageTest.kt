package com.clayboicardi.game2048.about

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * The in-app licenses against the runtime classpath (D-122; FEATURES.md F26 item 5;
 * L10N_STORE_LEGAL.md §4.4): `config/license-groups.txt`, which `scripts/check-licenses.sh` keeps
 * equal to the Maven groups of `:app:releaseRuntimeClasspath`, and
 * `res/raw/third_party_licenses.json`, both read from the project directory (the test task's
 * working directory is `app/`, and the groups file is a declared input of the task).
 */
class LicensesCoverageTest {
    /** F26-AC1 */
    @Test
    fun everyRuntimeGroupHasAnEntry() {
        val groups = licenseGroups()
        val entries = LicenseCatalog.parse(LICENSES_JSON.readText())

        assertThat(groups).isNotEmpty()
        for ((group, licenseId) in groups) {
            assertWithMessage("license ids of the entries for $group")
                .that(entries.filter { it.groupOrPath == group }.map { it.licenseId })
                .contains(licenseId)
        }
    }

    /**
     * D-122, L10N_STORE_LEGAL.md §4.4: one entry per Maven group or path, each with a name, a
     * copyright line and one of the screen's three licenses, so every entry has a group on the
     * Licenses screen; the MIT and Apache groups ship their texts.
     */
    @Test
    fun everyEntryHasAShownLicense() {
        val entries = LicenseCatalog.parse(LICENSES_JSON.readText())

        assertThat(entries.map { it.groupOrPath }).containsNoDuplicates()
        for (entry in entries) {
            assertWithMessage("licenseId of ${entry.groupOrPath}")
                .that(entry.licenseId)
                .isIn(LicenseCatalog.ORDER)
            assertWithMessage("name of ${entry.groupOrPath}").that(entry.name).isNotEmpty()
            assertWithMessage("copyright of ${entry.groupOrPath}")
                .that(entry.copyright)
                .isNotEmpty()
        }
        assertThat(LicenseCatalog.group(entries).map { it.licenseId })
            .containsAtLeast(LicenseCatalog.MIT, LicenseCatalog.APACHE)
            .inOrder()
        assertThat(LicenseCatalog.textOf(LicenseCatalog.MIT)).isNotNull()
        assertThat(LicenseCatalog.textOf(LicenseCatalog.APACHE)).isNotNull()
    }

    /**
     * The `<group> <SPDX id>` lines of `config/license-groups.txt`, comments and blanks skipped.
     */
    private fun licenseGroups(): List<Pair<String, String>> =
        LICENSE_GROUPS.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val fields = line.split(WHITESPACE)
                assertWithMessage("fields of \"$line\"").that(fields).hasSize(2)
                fields[0] to fields[1]
            }

    private companion object {
        val LICENSE_GROUPS = File("../config/license-groups.txt")
        val LICENSES_JSON = File("src/main/res/raw/third_party_licenses.json")
        val WHITESPACE = Regex("\\s+")
    }
}
