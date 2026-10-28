plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.detekt)
}

// Local-only hooks (D-019). CI sets neither property.
val robolectricRepoUrl: Provider<String> = providers.gradleProperty("robolectricRepoUrl")
val recordStoreAssets: Boolean =
    providers.gradleProperty("recordStoreAssets").map(String::toBoolean).getOrElse(false)
val storeAssetsDir: String =
    rootProject.layout.projectDirectory
        .dir("fastlane/metadata/android/en-US/images")
        .asFile
        .absolutePath

android {
    namespace = "com.clayboicardi.game2048"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.clayboicardi.game2048"
        minSdk = 24
        targetSdk = 37
        versionCode = 30000
        versionName = "3.0.0"
    }

    signingConfigs {
        // Intentionally public CI sideload key (D-131). Never used for store uploads.
        create("ciSideload") {
            storeFile = rootProject.file("signing/ci-sideload.p12")
            storePassword = "game2048-ci-sideload"
            keyAlias = "ci-sideload"
            keyPassword = "game2048-ci-sideload"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isPseudoLocalesEnabled = true
            signingConfig = signingConfigs.getByName("ciSideload")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("ciSideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // VERSION_CODE/VERSION_NAME as compile-time constants (D-134)
    }

    androidResources { generateLocaleConfig = true }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        checkDependencies = true
        disable +=
            setOf(
                "GradleDependency",
                "NewerVersionAvailable",
                "AndroidGradlePluginVersion",
                "OldTargetApi",
            )
        error += setOf("MissingTranslation", "ExtraTranslation")
        sarifReport = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )
                robolectricRepoUrl.orNull?.let { url ->
                    it.systemProperty("robolectric.dependency.repo.url", url)
                }
                if (recordStoreAssets) {
                    it.systemProperty("game2048.storeAssetsDir", storeAssetsDir)
                } else {
                    it.filter.excludeTestsMatching("com.clayboicardi.game2048.store.*")
                }
                // QUALITY.md §2.6: files that tests read by path are declared inputs, and test
                // results are never restored from the build cache.
                it.inputs
                    .files(fileTree("src/test/screenshots") { include("**/*.png") })
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("goldenScreenshots")
                it.inputs
                    .files(rootProject.layout.projectDirectory.file("config/license-groups.txt"))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("licenseGroups")
                it.outputs.cacheIf("test results are never reused from the build cache") { false }
            }
        }
    }
}

kotlin { compilerOptions { allWarningsAsErrors = true } }

detekt {
    buildUponDefaultConfig = true
    // One root file for every module (technical correction TC-5 of D-144).
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    parallel = true
}

dependencies {
    implementation(project(":core:engine"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    lintChecks(libs.compose.lint.checks)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.roborazzi.accessibility.check)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
