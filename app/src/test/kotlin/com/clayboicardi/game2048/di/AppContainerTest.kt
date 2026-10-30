package com.clayboicardi.game2048.di

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clayboicardi.game2048.BuildConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The startup path of `AppContainer.create` (D-134, ARCHITECTURE.md §4.2): no `PackageManager`
 * call, and the version only from AGP's standard `BuildConfig` fields.
 */
@RunWith(AndroidJUnit4::class)
class AppContainerTest {
    /** F30-AC10 */
    @Test
    fun createDoesNotTouchPackageManager() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val wrapper =
            object : ContextWrapper(app) {
                override fun getPackageManager(): PackageManager =
                    throw AssertionError("PackageManager on the startup path")

                override fun getApplicationContext(): Context = this
            }

        val container = AppContainer.create(wrapper)

        assertThat(container.versionCode).isEqualTo(10000)
        container.applicationScope.cancel()
    }

    /** F30-AC11 */
    @Test
    fun versionComesFromBuildConfigStandardFields() {
        // Coverage agents add synthetic fields; only the declared ones count.
        val names =
            BuildConfig::class.java.declaredFields.filterNot { it.isSynthetic }.map { it.name }

        assertThat(names)
            .containsExactly(
                "DEBUG",
                "APPLICATION_ID",
                "BUILD_TYPE",
                "VERSION_CODE",
                "VERSION_NAME",
            )
        assertThat(BuildConfig.VERSION_CODE).isEqualTo(10000)
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("1.0.0")
    }
}
