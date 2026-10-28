package com.clayboicardi.game2048.engine

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Engine purity guard of D-021 (ARCHITECTURE.md §3.7; its inputs are declared, QUALITY.md §2.6).
 */
class EngineImportsGuardTest {
    /** F01-AC19 */
    @Test
    fun noClockRngOrAndroidInEngine() {
        // The JUnit working directory is core/engine/. A plain text scan: comments count too.
        val root = File("src/main/kotlin")
        val files = root.walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()
        val kotlinFiles = files.filter { it.extension == "kt" }
        assertTrue(
            kotlinFiles.size >= MIN_KOTLIN_FILES,
            "scanned ${kotlinFiles.size} .kt files under ${root.absolutePath}",
        )
        assertTrue(kotlinFiles.any { it.name == "Game.kt" }, "Game.kt was not scanned")
        val hits = files.flatMap(::forbiddenHits)
        assertTrue(hits.isEmpty(), "Forbidden in :core:engine (D-021):\n" + hits.joinToString("\n"))
    }

    companion object {
        /** The engine files of ARCHITECTURE.md §10.1. */
        private const val MIN_KOTLIN_FILES = 13

        private val FORBIDDEN =
            listOf(
                "java.time",
                "System.currentTimeMillis",
                "System.nanoTime",
                "Math.random",
                "kotlin.random",
                "java.util.Random",
                "SecureRandom",
                "import android.",
                "import kotlinx.",
            )

        /** One "file:line: token" entry per forbidden token on each line of [file]. */
        private fun forbiddenHits(file: File): List<String> =
            file.readLines().flatMapIndexed { index, line ->
                FORBIDDEN.filter { it in line }.map { "${file.path}:${index + 1}: $it" }
            }
    }
}
