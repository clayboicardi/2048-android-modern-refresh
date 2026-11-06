plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Desktop/JVM target: lets commonMain + the shared engine compile and the parity test run
    // on Linux/CI where the iOS Kotlin/Native targets are unavailable.
    jvm()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            // Single source of truth: the game engine is shared from :core:engine's sources,
            // not copied (D-272). The engine is import-free pure Kotlin and compiles on every
            // target; its one @JvmInline needs `import kotlin.jvm.JvmInline` in commonMain.
            kotlin.srcDir("../../core/engine/src/main/kotlin")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.okio)
            }
        }
        commonTest { dependencies { implementation(kotlin("test")) } }
    }
}
