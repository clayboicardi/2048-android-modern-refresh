package com.clayboicardi.game2048.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.clayboicardi.game2048.BuildConfig
import com.clayboicardi.game2048.data.DataStoreFileProvider
import com.clayboicardi.game2048.data.DataStoreFiles
import com.clayboicardi.game2048.data.JsonDocumentSerializer
import com.clayboicardi.game2048.data.game.GameDocument
import com.clayboicardi.game2048.data.game.GameDocumentSerializer
import com.clayboicardi.game2048.data.game.GameRepository
import com.clayboicardi.game2048.data.platform.MonotonicClock
import com.clayboicardi.game2048.data.platform.SecureRandomSeedSource
import com.clayboicardi.game2048.data.platform.SeedSource
import com.clayboicardi.game2048.data.platform.SystemMonotonicClock
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.RecordsRepository
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.SettingsRepository
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.ui.theme.AnimatorScaleSource
import com.clayboicardi.game2048.ui.theme.SystemAnimatorScaleSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

/**
 * Manual DI (D-022, ARCHITECTURE.md §4.2): the one owner of every DataStore (one per file per
 * process, D-040) and of the repositories, clock and seed source. `Game2048Application` creates it
 * with [create]; JVM tests build it through `testing/TestAppContainer.kt`. It owns the one
 * [AnimatorScaleSource] (M3 part 1, ARCHITECTURE.md §5.8); tests pass a fake.
 *
 * Constructing it does no I/O: `DataStoreFactory.create` opens its file only at the first read or
 * write, and the repositories are created on first use.
 */
@Suppress("LongParameterList") // the ARCHITECTURE.md §4.2 constructor: one parameter per seam
class AppContainer(
    private val files: DataStoreFileProvider,
    val versionCode: Int,
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    val clock: MonotonicClock = SystemMonotonicClock,
    val seedSource: SeedSource = SecureRandomSeedSource(),
    /** The live system animator duration scale (D-063); `SystemAnimatorScaleSource` in [create]. */
    val animatorScale: AnimatorScaleSource,
) {
    /** DataStore file I/O: `applicationScope` + the I/O dispatcher (D-040). */
    private val storeScope: CoroutineScope = applicationScope + ioDispatcher

    /** `settings.json`: `JsonDocumentSerializer`, `ReplaceFileCorruptionHandler { Settings() }`. */
    val settingsStore: DataStore<Settings> =
        DataStoreFactory.create(
            serializer = JsonDocumentSerializer(Settings.serializer(), Settings()),
            corruptionHandler = ReplaceFileCorruptionHandler { Settings() },
            migrations = emptyList(),
            scope = storeScope,
            produceFile = { files.file(DataStoreFiles.SETTINGS) },
        )

    /** `records.json`: `JsonDocumentSerializer`, `ReplaceFileCorruptionHandler { Records() }`. */
    val recordsStore: DataStore<Records> =
        DataStoreFactory.create(
            serializer = JsonDocumentSerializer(Records.serializer(), Records()),
            corruptionHandler = ReplaceFileCorruptionHandler { Records() },
            migrations = emptyList(),
            scope = storeScope,
            produceFile = { files.file(DataStoreFiles.RECORDS) },
        )

    /** One store per board side, keyed by `BoardSize.n`; guarded by its own monitor. */
    private val gameStores = HashMap<Int, DataStore<GameDocument>>()

    /**
     * `game-N.json`, created once per size on first use (thread-safe), `GameDocumentSerializer`.
     */
    fun gameStore(size: BoardSize): DataStore<GameDocument> =
        synchronized(gameStores) {
            gameStores.getOrPut(size.n) {
                DataStoreFactory.create(
                    serializer = GameDocumentSerializer,
                    corruptionHandler = ReplaceFileCorruptionHandler { GameDocument() },
                    migrations = emptyList(),
                    scope = storeScope,
                    produceFile = { files.file(DataStoreFiles.game(size)) },
                )
            }
        }

    /** Over [settingsStore], `applicationScope` and the main dispatcher; created on first use. */
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(settingsStore, applicationScope, mainDispatcher)
    }

    /** Over [recordsStore], `applicationScope` and the main dispatcher; created on first use. */
    val recordsRepository: RecordsRepository by lazy {
        RecordsRepository(recordsStore, applicationScope, mainDispatcher)
    }

    /** Over [gameStore] and `applicationScope`; created on first use. */
    val gameRepository: GameRepository by lazy { GameRepository(::gameStore, applicationScope) }

    companion object {
        /**
         * The production container. Runs on the main thread in `Application.onCreate`: no I/O and
         * no `PackageManager` call; `versionCode = BuildConfig.VERSION_CODE` (D-134). The files are
         * `files/datastore/<name>` (D-040), resolved only when a store first opens its file. The
         * one `SystemAnimatorScaleSource` shares the application scope.
         */
        @Suppress("InjectDispatcher") // the production root makes the one application scope (§4.2)
        fun create(context: Context): AppContainer {
            val app = context.applicationContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            return AppContainer(
                files = { name -> app.dataStoreFile(name) },
                versionCode = BuildConfig.VERSION_CODE,
                applicationScope = scope,
                animatorScale = SystemAnimatorScaleSource(app, scope),
            )
        }
    }
}
