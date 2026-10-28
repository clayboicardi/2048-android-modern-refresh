package com.clayboicardi.game2048.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The D-041 migration path that later versions will use, proven now (QUALITY.md §1.6). v1.0 ships
 * no migration (`migrations = emptyList()`, ARCHITECTURE.md §4.4); this test passes a test-only
 * `DataMigration<Settings>` to a store over a copy of the v1 `settings.json` fixture.
 */
class DataMigrationSeamTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    /** `shouldMigrate = schemaVersion < 2`, `migrate = copy(schemaVersion = 2)`, no clean-up. */
    private class ToSchemaTwo : DataMigration<Settings> {
        var migrations = 0
            private set

        override suspend fun shouldMigrate(currentData: Settings): Boolean =
            currentData.schemaVersion < 2

        override suspend fun migrate(currentData: Settings): Settings {
            migrations++
            return currentData.copy(schemaVersion = 2)
        }

        override suspend fun cleanUp() = Unit
    }

    @Test
    fun migrationRunsOnceAndRewritesTheFile() = runTest {
        val v1 = V1Fixtures.text(V1Fixtures.SETTINGS_FILE)
        val file = File(tmp.newFolder(), DataStoreFiles.SETTINGS)
        file.writeText(v1)
        val migration = ToSchemaTwo()

        // First store: the read migrates; every other v1 value is intact; the file is rewritten.
        val job1 = SupervisorJob(backgroundScope.coroutineContext[Job])
        val first = store(file, migration, CoroutineScope(backgroundScope.coroutineContext + job1))
        val migrated = first.data.first()
        assertThat(migrated.schemaVersion).isEqualTo(2)
        assertThat(migrated).isEqualTo(V1Fixtures.SETTINGS.copy(schemaVersion = 2))
        assertThat(migration.migrations).isEqualTo(1)
        testScheduler.advanceUntilIdle()
        val onDisk = AppJson.decodeFromString(Settings.serializer(), file.readText())
        assertThat(onDisk).isEqualTo(V1Fixtures.SETTINGS.copy(schemaVersion = 2))
        assertThat(file.readText()).isNotEqualTo(v1)

        // A second store on the same file, after the first scope is cancelled and has completed,
        // reads schema 2 and does not migrate again.
        job1.cancelAndJoin()
        assertThat(job1.isCompleted).isTrue()
        val job2 = SupervisorJob(backgroundScope.coroutineContext[Job])
        val second = store(file, migration, CoroutineScope(backgroundScope.coroutineContext + job2))
        assertThat(second.data.first()).isEqualTo(V1Fixtures.SETTINGS.copy(schemaVersion = 2))
        testScheduler.advanceUntilIdle()
        assertThat(migration.migrations).isEqualTo(1)
        job2.cancelAndJoin()
    }

    private fun store(
        file: File,
        migration: DataMigration<Settings>,
        scope: CoroutineScope,
    ): DataStore<Settings> =
        DataStoreFactory.create(
            serializer = JsonDocumentSerializer(Settings.serializer(), Settings()),
            corruptionHandler = ReplaceFileCorruptionHandler { Settings() },
            migrations = listOf(migration),
            scope = scope,
            produceFile = { file },
        )
}
