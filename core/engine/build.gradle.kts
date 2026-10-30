import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.property)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    // QUALITY.md §2.6: EngineImportsGuardTest reads the main sources as text.
    inputs
        .files(fileTree("src/main/kotlin"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("engineSources")
    outputs.cacheIf("test results are never reused from the build cache") { false }
}

kover {
    reports {
        verify {
            rule("line") { minBound(90, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE) }
            rule("branch") {
                minBound(85, CoverageUnit.BRANCH, AggregationType.COVERED_PERCENTAGE)
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    // One root file for every module (technical correction TC-5 of D-144).
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    parallel = true
}
