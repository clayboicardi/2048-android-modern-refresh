package com.clayboicardi.game2048

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Auto Backup and device transfer (D-048, ARCHITECTURE.md §8.2): the merged manifest references
 * both rule files, and each includes exactly `files/datastore/`, the DataStore directory (D-040).
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** F29-AC1 */
    @Test
    fun manifestReferencesBothRuleFiles() {
        val info = context.applicationInfo

        assertThat(info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isNotEqualTo(0)
        // Public in the framework, hidden from the SDK stubs.
        assertThat(hiddenIntField(info, "fullBackupContent")).isEqualTo(R.xml.backup_rules)

        // Robolectric 4.17 parses the manifest with the legacy PackageParser, which never sets
        // ApplicationInfo.dataExtractionRulesRes (it stays 0), so the reference is read from the
        // compiled merged manifest that Robolectric loads, through the same AssetManager.
        val application = compiledManifestApplication()
        assertThat(application.getValue("allowBackup")).isEqualTo("true")
        assertThat(application.getValue("fullBackupContent")).isEqualTo(ref(R.xml.backup_rules))
        assertThat(application.getValue("dataExtractionRules"))
            .isEqualTo(ref(R.xml.data_extraction_rules))
    }

    /** F29-AC2 */
    @Test
    fun dataExtractionRulesIncludeDatastore() {
        val root = parse(R.xml.data_extraction_rules)

        assertThat(root)
            .isEqualTo(
                Element(
                    "data-extraction-rules",
                    children =
                        listOf(
                            Element("cloud-backup", children = listOf(DATASTORE_INCLUDE)),
                            Element("device-transfer", children = listOf(DATASTORE_INCLUDE)),
                        ),
                ),
            )
    }

    /** F29-AC3 */
    @Test
    fun fullBackupContentIncludesDatastore() {
        val root = parse(R.xml.backup_rules)

        assertThat(root)
            .isEqualTo(Element("full-backup-content", children = listOf(DATASTORE_INCLUDE)))
    }

    /** An XML element: its name, its attributes by name, and its child elements in order. */
    private data class Element(
        val name: String,
        val attributes: Map<String, String> = emptyMap(),
        val children: List<Element> = emptyList(),
    )

    /** The element tree of the compiled XML resource [id]. */
    private fun parse(id: Int): Element {
        val parser = context.resources.getXml(id)
        try {
            while (parser.next() != XmlPullParser.START_TAG) {
                check(parser.eventType != XmlPullParser.END_DOCUMENT) { "No root element" }
            }
            return element(parser)
        } finally {
            parser.close()
        }
    }

    /** Reads the element whose start tag [parser] is on, up to and including its end tag. */
    private fun element(parser: XmlResourceParser): Element {
        val name = parser.name
        val attributes =
            (0 until parser.attributeCount).associate { i ->
                parser.getAttributeName(i) to parser.getAttributeValue(i)
            }
        val children = mutableListOf<Element>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) children += element(parser)
        }
        return Element(name, attributes, children)
    }

    /**
     * The `android:` attributes of `<application>` in the compiled `AndroidManifest.xml` of this
     * app's resource APK, after checking that its `<manifest>` names this package. Resource
     * references read as `@<id>`.
     */
    private fun compiledManifestApplication(): Map<String, String> {
        val parser = context.assets.openXmlResourceParser("AndroidManifest.xml")
        try {
            var attributes: Map<String, String>? = null
            while (attributes == null && parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name == "manifest") {
                    assertThat(attributesIn(parser, namespace = "")["package"])
                        .isEqualTo(context.packageName)
                }
                if (parser.name == "application") attributes = attributesIn(parser, ANDROID_NS)
            }
            return checkNotNull(attributes) { "No <application> in the merged manifest" }
        } finally {
            parser.close()
        }
    }

    /** The attributes of the current start tag in [namespace] ("" for none), by name. */
    private fun attributesIn(parser: XmlResourceParser, namespace: String): Map<String, String> =
        (0 until parser.attributeCount)
            .filter { parser.getAttributeNamespace(it).orEmpty() == namespace }
            .associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }

    private fun ref(id: Int): String = "@$id"

    private fun hiddenIntField(info: ApplicationInfo, name: String): Int =
        ApplicationInfo::class.java.getField(name).getInt(info)

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        val DATASTORE_INCLUDE =
            Element("include", attributes = mapOf("domain" to "file", "path" to "datastore/"))
    }
}
