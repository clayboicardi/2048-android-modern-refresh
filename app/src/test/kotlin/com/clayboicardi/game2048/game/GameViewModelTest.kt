package com.clayboicardi.game2048.game

import app.cash.turbine.test
import com.clayboicardi.game2048.data.game.GameDocumentMapper
import com.clayboicardi.game2048.data.game.GameRepository
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.platform.SeedSource
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.RecordsRepository
import com.clayboicardi.game2048.data.records.SizeRecord
import com.clayboicardi.game2048.data.records.toFinalization
import com.clayboicardi.game2048.data.settings.Settings
import com.clayboicardi.game2048.data.settings.SettingsRepository
import com.clayboicardi.game2048.di.AppContainer
import com.clayboicardi.game2048.engine.Board
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import com.clayboicardi.game2048.engine.Game
import com.clayboicardi.game2048.engine.GameState
import com.clayboicardi.game2048.engine.GameStatus
import com.clayboicardi.game2048.engine.MoveOutcome
import com.clayboicardi.game2048.fixtures.GoldenFixtures
import com.clayboicardi.game2048.fixtures.fixtureGame
import com.clayboicardi.game2048.game.board.BoardEvent
import com.clayboicardi.game2048.game.board.BoardEventKind
import com.clayboicardi.game2048.game.board.BoardPresenter
import com.clayboicardi.game2048.game.board.BoardUi
import com.clayboicardi.game2048.game.board.MoveHaptic
import com.clayboicardi.game2048.game.board.TileKind
import com.clayboicardi.game2048.game.board.kind
import com.clayboicardi.game2048.testing.FakeClock
import com.clayboicardi.game2048.testing.FixedSeedSource
import com.clayboicardi.game2048.testing.MainDispatcherRule
import com.clayboicardi.game2048.testing.seedGame
import com.clayboicardi.game2048.testing.seedRecords
import com.clayboicardi.game2048.testing.seedSettings
import com.clayboicardi.game2048.testing.testAppContainer
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `GameViewModel` over real DataStore files (QUALITY.md §1.7). Every test drives the virtual clock
 * with `testScheduler` only (D-215) and counts saves with the repositories' `submissions` (D-221):
 * every submitted save, also one that the writer conflates with the next into a single file write.
 *
 * The scheduler is run with `runCurrent()`: the container's stores and writers live in a child of
 * `backgroundScope`, and `advanceUntilIdle()` stops as soon as only background work is left. No
 * work here is delayed, so `runCurrent()` runs everything that is pending.
 */
@Suppress("LargeClass") // FEATURES.md §8 puts every GameViewModel criterion in this one class
class GameViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    /** F01-AC21 */
    @Test
    fun invalidMoveEmitsNudgeOnly() = runTest {
        val rig =
            rig(tmp.newFolder()) {
                seedSettings(Settings(lastBoardSize = 3))
                seedGame(GoldenFixtures.FIX_MID_3)
            }
        val vm = rig.loadedViewModel()
        rig.awaitIdle()
        val flows = vm.snapshot()
        val saves = rig.saveCounts()
        val files = rig.fileBytes()

        vm.nudge.test {
            assertThat(awaitItem()).isNull()
            vm.onMove(Direction.LEFT)
            assertThat(awaitItem()).isEqualTo(Nudge(1, Direction.LEFT))
        }

        testScheduler.runCurrent()
        rig.awaitIdle()
        assertThat(vm.snapshot().copy(nudge = null)).isEqualTo(flows)
        assertThat(rig.saveCounts()).isEqualTo(saves)
        assertThat(rig.fileBytes()).isEqualTo(files)
    }

    /** F01-AC22 */
    @Test
    fun freshGameSeedComesFromSeedSource() = runTest {
        val rig = rig(tmp.newFolder())
        val vm = rig.loadedViewModel()
        val first = rig.latest(FOUR)
        vm.onNewGame()
        val second = rig.latest(FOUR)

        assertThat(first.state.seed).isEqualTo(0x2048L)
        assertThat(second.state.seed).isEqualTo(0x2049L)
        assertThat(first.state).isEqualTo(Game.newGame(FOUR, 0x2048))
        assertThat(second.state).isEqualTo(Game.newGame(FOUR, 0x2049))
        assertThat(vm.board.value.cells).isEqualTo(cellsOf(Game.newGame(FOUR, 0x2049)))
    }

    /** F02-AC3 */
    @Test
    fun sizeSwitchLoadsSavedGameWithoutConfirmation() = runTest {
        val clock = FakeClock()
        val rig =
            rig(tmp.newFolder(), clock) {
                seedGame(GoldenFixtures.FIX_MID_4)
                seedGame(OVER_6)
            }
        val vm = rig.loadedViewModel()
        vm.onResumed()
        clock.advance(5_000)
        rig.awaitIdle()
        val records = rig.records.state.value
        val recordSaves = rig.records.submissions

        vm.onSizeSelected(SIX)
        testScheduler.runCurrent()
        rig.awaitIdle()

        assertThat(rig.latest(SIX)).isEqualTo(OVER_6)
        assertThat(vm.board.value.cells).isEqualTo(cellsOf(OVER_6.state))
        assertThat(vm.board.value.event).isEqualTo(BoardEvent.SizeSwitched)
        val overlay = vm.overlay.value as OverlayUi.GameOver
        assertThat(overlay.entry).isEqualTo(OverlayEntry.Restored)
        assertThat(overlay.summary.size).isEqualTo(SIX)
        assertThat(vm.uiState.value.size).isEqualTo(SIX)
        assertThat(vm.uiState.value.busy).isFalse()
        assertThat(rig.records.state.value).isEqualTo(records)
        assertThat(rig.records.submissions).isEqualTo(recordSaves)
        assertThat(rig.fileSettings().lastBoardSize).isEqualTo(6)
        assertThat(rig.fileGame(FOUR)).isEqualTo(GoldenFixtures.FIX_MID_4.copy(elapsedMs = 603_000))
    }

    /** F02-AC4 */
    @Test
    fun sizeSwitchStartsFreshGameWhenNoneSaved() = runTest {
        val rig =
            rig(tmp.newFolder()) {
                seedRecords(Records(sizes = mapOf("5" to SizeRecord(bestScore = 777))))
            }
        val vm = rig.loadedViewModel()

        vm.onSizeSelected(FIVE)
        testScheduler.runCurrent()
        rig.awaitIdle()

        val fresh = checkNotNull(rig.fileGame(FIVE))
        assertThat(fresh.state.moveCount).isEqualTo(0)
        assertThat(fresh.state.board.tileCount()).isEqualTo(2)
        assertThat(fresh.state.seed).isEqualTo(0x2049L)
        assertThat(fresh.state).isEqualTo(Game.newGame(FIVE, 0x2049))
        assertThat(fresh.bestAtStart).isEqualTo(777)
        assertThat(vm.board.value.tiles).hasSize(2)
        assertThat(vm.uiState.value.size).isEqualTo(FIVE)
    }

    /** F02-AC5 */
    @Test
    fun selectingCurrentSizeIsNoOp() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(GoldenFixtures.FIX_MID_4) }
        val vm = rig.loadedViewModel()
        rig.awaitIdle()
        val flows = vm.snapshot()
        val saves = rig.saveCounts()

        vm.onSizeSelected(FOUR)
        testScheduler.runCurrent()
        rig.awaitIdle()

        assertThat(vm.snapshot()).isEqualTo(flows)
        assertThat(rig.saveCounts()).isEqualTo(saves)
    }

    /** F02-AC6 */
    @Test
    fun inputIgnoredWhileSizeSwitchLoads() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(GoldenFixtures.FIX_MID_4) }
        val vm = rig.loadedViewModel()

        vm.onSizeSelected(FIVE)
        assertThat(vm.uiState.value.busy).isTrue()
        val board = vm.board.value
        vm.onMove(Direction.LEFT)
        assertThat(vm.board.value).isSameInstanceAs(board)

        testScheduler.runCurrent()
        rig.awaitIdle()
        assertThat(vm.uiState.value.busy).isFalse()
        assertThat(vm.uiState.value.size).isEqualTo(FIVE)
        assertThat(rig.fileGame(FOUR)).isEqualTo(GoldenFixtures.FIX_MID_4)
        assertThat(checkNotNull(rig.fileGame(FIVE)).state.moveCount).isEqualTo(0)
    }

    /** F03-AC8 */
    @Test
    fun winningMoveShowsWinCard() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(ONE_MERGE_FROM_2048) }
        val vm = rig.loadedViewModel()

        vm.onMove(Direction.LEFT)

        val won = moved(ONE_MERGE_FROM_2048.state, Direction.LEFT).state
        val board = vm.board.value
        val event = board.event as BoardEvent.Moved
        assertThat(event.becameWon).isTrue()
        assertThat(event.haptic).isEqualTo(MoveHaptic.WIN)
        assertThat(vm.overlay.value)
            .isEqualTo(
                OverlayUi.Win(
                    targetExp = 11,
                    summary = summaryOf(won, best = won.score, elapsedMs = 0),
                    entry = OverlayEntry.Animated(board.moveId),
                ),
            )
        assertThat(rig.latest(FOUR).state.status).isEqualTo(GameStatus.WON)
        rig.awaitIdle()
        assertThat(checkNotNull(rig.fileGame(FOUR)).state.status).isEqualTo(GameStatus.WON)
    }

    /** F03-AC9 */
    @Test
    fun keepGoingClosesCard() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(ONE_MERGE_FROM_2048) }
        val vm = rig.loadedViewModel()
        vm.onMove(Direction.LEFT)
        testScheduler.runCurrent()
        val saves = rig.saveCounts()

        vm.onKeepGoing()
        testScheduler.runCurrent()

        assertThat(vm.overlay.value).isEqualTo(OverlayUi.None)
        assertThat(vm.uiState.value.status).isEqualTo(GameStatus.KEEP_PLAYING)
        assertThat(rig.saveCounts().gameDeltas(saves)).isEqualTo(mapOf(4 to 1))
        rig.awaitIdle()
        assertThat(checkNotNull(rig.fileGame(FOUR)).state.status).isEqualTo(GameStatus.KEEP_PLAYING)
    }

    /** F03-AC10 */
    @Test
    fun keepGoingWithoutMovesShowsGameOver() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(WON_LOCKED) }
        val vm = rig.loadedViewModel()
        assertThat((vm.overlay.value as OverlayUi.Win).entry).isEqualTo(OverlayEntry.Restored)

        vm.onKeepGoing()

        val over = Game.keepGoing(WON_LOCKED.state)
        assertThat(over.status).isEqualTo(GameStatus.OVER)
        assertThat(vm.overlay.value)
            .isEqualTo(
                OverlayUi.GameOver(
                    summary = summaryOf(over, best = over.score, elapsedMs = 0),
                    entry = OverlayEntry.Animated(vm.board.value.moveId),
                ),
            )
        rig.awaitIdle()
        assertThat(checkNotNull(rig.fileGame(FOUR)).state.status).isEqualTo(GameStatus.OVER)
    }

    /** F03-AC15 */
    @Test
    fun undoFromWinThenRepeatWinsAgain() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(ONE_MERGE_FROM_2048) }
        val vm = rig.loadedViewModel()
        vm.onMove(Direction.LEFT)
        val first = vm.board.value.event as BoardEvent.Moved
        val firstSpawn = vm.board.value.tiles.single { it.kind == TileKind.Spawned }
        val m = rig.latest(FOUR).state.moveCount
        assertThat(vm.overlay.value).isInstanceOf(OverlayUi.Win::class.java)

        vm.onUndo()
        assertThat(rig.latest(FOUR).state.moveCount).isEqualTo(m - 1)
        assertThat(vm.uiState.value.status).isEqualTo(GameStatus.PLAYING)
        assertThat(vm.overlay.value).isEqualTo(OverlayUi.None)

        vm.onMove(Direction.LEFT)
        val again = vm.board.value.event as BoardEvent.Moved
        val againSpawn = vm.board.value.tiles.single { it.kind == TileKind.Spawned }
        assertThat(again.spawnIndex).isEqualTo(first.spawnIndex)
        assertThat(againSpawn.exp).isEqualTo(firstSpawn.exp)
        assertThat(again.becameWon).isTrue()
        assertThat(vm.overlay.value).isInstanceOf(OverlayUi.Win::class.java)
    }

    /** F04-AC1 */
    @Test
    fun gameOverSummaryMatchesState() = runTest {
        val rig =
            rig(tmp.newFolder()) {
                seedRecords(GoldenFixtures.FIX_RECORDS)
                seedGame(ONE_MOVE_FROM_OVER)
            }
        val vm = rig.loadedViewModel()

        vm.onMove(Direction.LEFT)

        val over = moved(ONE_MOVE_FROM_OVER.state, Direction.LEFT).state
        val overlay = vm.overlay.value as OverlayUi.GameOver
        assertThat(overlay.entry).isEqualTo(OverlayEntry.Animated(vm.board.value.moveId))
        assertThat(overlay.summary)
            .isEqualTo(summaryOf(over, best = 24_680, elapsedMs = ONE_MOVE_FROM_OVER.elapsedMs))
        assertThat(overlay.summary.best).isAtLeast(overlay.summary.score)
        assertThat(overlay.summary.moves).isEqualTo(97)
        assertThat(overlay.summary.undos).isEqualTo(1)
        assertThat(overlay.summary.bestTileExp).isEqualTo(over.board.maxExp())
    }

    /** F04-AC9 */
    @Test
    fun undoFromGameOverResumesPlay() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(ONE_MOVE_FROM_OVER) }
        val vm = rig.loadedViewModel()
        vm.onMove(Direction.LEFT)
        assertThat(vm.overlay.value).isInstanceOf(OverlayUi.GameOver::class.java)

        vm.onUndo()
        assertThat(vm.overlay.value).isEqualTo(OverlayUi.None)
        val undone = vm.board.value.moveId

        vm.onMove(Direction.LEFT)
        assertThat(vm.board.value.moveId).isEqualTo(undone + 1)
        assertThat(vm.board.value.event).isInstanceOf(BoardEvent.Moved::class.java)
        assertThat(rig.latest(FOUR).state.moveCount).isEqualTo(97)
    }

    /** F04-AC11 */
    @Test
    fun newGameAfterOverCountsPlayedNotAbandoned() = runTest {
        val rig =
            rig(tmp.newFolder()) {
                seedRecords(GoldenFixtures.FIX_RECORDS)
                seedGame(GoldenFixtures.FIX_OVER_4)
            }
        val vm = rig.loadedViewModel()
        val before = recordOf(GoldenFixtures.FIX_RECORDS, FOUR)

        vm.onNewGame()

        val after = recordOf(rig.records.state.value, FOUR)
        assertThat(after.gamesPlayed).isEqualTo(before.gamesPlayed + 1)
        assertThat(after.gamesAbandoned).isEqualTo(before.gamesAbandoned)
        assertThat(after.totalScore).isEqualTo(before.totalScore + 3012)
        assertThat(rig.latest(FOUR).state.moveCount).isEqualTo(0)
        assertThat(vm.overlay.value).isEqualTo(OverlayUi.None)
        assertThat(vm.board.value.event).isEqualTo(BoardEvent.NewGame)
    }

    /** F05-AC8 */
    @Test
    fun undoPersistsIncrementsAndClosesCard() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(ONE_MERGE_FROM_2048) }
        val vm = rig.loadedViewModel()
        vm.onMove(Direction.LEFT)
        assertThat(vm.overlay.value).isInstanceOf(OverlayUi.Win::class.java)
        testScheduler.runCurrent()
        val saves = rig.saveCounts()

        vm.onUndo()

        assertThat(vm.overlay.value).isEqualTo(OverlayUi.None)
        assertThat(vm.board.value.event).isEqualTo(BoardEvent.Undo)
        testScheduler.runCurrent()
        assertThat(rig.saveCounts().gameDeltas(saves)).isEqualTo(mapOf(4 to 1))
        rig.awaitIdle()
        assertThat(checkNotNull(rig.fileGame(FOUR)).state.undoCount)
            .isEqualTo(ONE_MERGE_FROM_2048.state.undoCount + 1)
    }

    /** F06-AC7 */
    @Test
    fun newGameFinalizesThenStartsFresh() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(IN_PROGRESS_4000) }
        val vm = rig.loadedViewModel()
        testScheduler.runCurrent()
        val saves = rig.saveCounts()

        vm.onNewGame()
        testScheduler.runCurrent()
        rig.awaitIdle()

        val record = recordOf(rig.records.state.value, FOUR)
        assertThat(record.gamesPlayed).isEqualTo(1)
        assertThat(record.gamesAbandoned).isEqualTo(1)
        assertThat(record.totalScore).isEqualTo(4000)
        assertThat(record.totalMoves).isEqualTo(200)
        assertThat(record.totalUndos).isEqualTo(1)
        assertThat(rig.records.state.value.lastFinalizedGameId).containsExactly("4", 3L)
        assertThat(rig.saveCounts().recordDelta(saves)).isEqualTo(1)
        assertThat(rig.saveCounts().gameDeltas(saves)).isEqualTo(mapOf(4 to 1))
        val fresh = checkNotNull(rig.fileGame(FOUR))
        assertThat(fresh.state.seed).isNotEqualTo(IN_PROGRESS_4000.state.seed)
        assertThat(fresh.state.seed).isEqualTo(0x2048L)
        assertThat(fresh.state.moveCount).isEqualTo(0)
        assertThat(fresh.state.undoCount).isEqualTo(0)
        assertThat(fresh.elapsedMs).isEqualTo(0)
        assertThat(fresh.newBestAnnounced).isFalse()
        assertThat(fresh.gameId).isEqualTo(4)
    }

    /** F06-AC8 */
    @Test
    fun newGameWithZeroScoreIsNotCounted() = runTest {
        val rig =
            rig(tmp.newFolder()) {
                seedRecords(GoldenFixtures.FIX_RECORDS)
                seedGame(ZERO_SCORE)
            }
        val vm = rig.loadedViewModel()
        rig.awaitIdle()
        val saves = rig.saveCounts()

        vm.onNewGame()
        testScheduler.runCurrent()
        rig.awaitIdle()

        assertThat(rig.records.state.value).isEqualTo(GoldenFixtures.FIX_RECORDS)
        assertThat(rig.saveCounts().recordDelta(saves)).isEqualTo(0)
        assertThat(rig.fileRecords()).isEqualTo(GoldenFixtures.FIX_RECORDS)
    }

    /** F06-AC9 */
    @Test
    fun newGameBestAtStartReadAfterFinalization() = runTest {
        val rig =
            rig(tmp.newFolder()) {
                seedRecords(Records(sizes = mapOf("4" to SizeRecord(bestScore = 8000))))
                seedGame(OVER_9000)
            }
        val vm = rig.loadedViewModel()

        vm.onNewGame()

        assertThat(rig.latest(FOUR).bestAtStart).isEqualTo(9000)
        assertThat(vm.header.value.best).isEqualTo(9000)
    }

    /** F06-AC13 */
    @Test
    fun newGameAssignsIdentityAndRecoveryEntry() = runTest {
        val old = GoldenFixtures.FIX_OVER_4.copy(gameId = 7)
        val counted = rig(tmp.newFolder()) { seedGame(old) }
        counted.loadedViewModel().onNewGame()

        assertThat(counted.records.state.value.lastFinalizedGameId).containsExactly("4", 7L)
        val fresh = counted.latest(FOUR)
        assertThat(fresh.gameId).isEqualTo(8)
        assertThat(fresh.pendingFinalizations).containsExactly(old.toFinalization())
        testScheduler.runCurrent()
        counted.awaitIdle()
        val records = counted.records.state.value
        val saves = counted.saveCounts()
        counted.records.finalize(old)
        testScheduler.runCurrent()
        counted.awaitIdle()
        assertThat(counted.records.state.value).isEqualTo(records)
        assertThat(counted.saveCounts().recordDelta(saves)).isEqualTo(0)

        val zero = ZERO_SCORE.copy(gameId = 7)
        val uncounted = rig(tmp.newFolder()) { seedGame(zero) }
        uncounted.loadedViewModel().onNewGame()
        assertThat(uncounted.records.state.value).isEqualTo(Records())
        assertThat(uncounted.latest(FOUR).gameId).isEqualTo(8)
        assertThat(uncounted.latest(FOUR).pendingFinalizations).isEmpty()
    }

    /** F06-AC23 */
    @Test
    fun newGameDropsOnlyWrittenEntries() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(GoldenFixtures.FIX_OVER_4.copy(gameId = 7)) }
        val vm = rig.loadedViewModel()

        vm.onNewGame()
        assertThat(rig.latest(FOUR).pendingIds()).containsExactly(7L)
        testScheduler.runCurrent()
        rig.awaitIdle()
        playToScore(vm)
        vm.onNewGame()

        val game9 = rig.latest(FOUR)
        assertThat(game9.gameId).isEqualTo(9)
        assertThat(game9.pendingIds()).containsExactly(8L)
    }

    /** F07-AC2 */
    @Test
    fun everyStateChangeSubmitsOneSave() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(ONE_MERGE_FROM_2048) }
        val vm = rig.loadedViewModel()
        testScheduler.runCurrent()

        val one = mapOf(4 to 1)
        assertWithMessage("move").that(rig.gameSaves { vm.onMove(Direction.LEFT) }).isEqualTo(one)
        assertWithMessage("undo").that(rig.gameSaves { vm.onUndo() }).isEqualTo(one)
        assertWithMessage("win").that(rig.gameSaves { vm.onMove(Direction.LEFT) }).isEqualTo(one)
        assertWithMessage("keep going").that(rig.gameSaves { vm.onKeepGoing() }).isEqualTo(one)
        assertWithMessage("new game").that(rig.gameSaves { vm.onNewGame() }).isEqualTo(one)
        assertWithMessage("size switch")
            .that(rig.gameSaves { vm.onSizeSelected(FIVE) })
            .isEqualTo(mapOf(4 to 1, 5 to 1))
        assertWithMessage("pause").that(rig.gameSaves { vm.onPaused() }).isEqualTo(mapOf(5 to 1))
        // Control (D-221): two saves in one action, which the writer conflates into one file
        // write, count as two, so a duplicate save cannot pass the checks above.
        val five = rig.latest(FIVE)
        val twice = rig.gameSaves {
            rig.games.save(five)
            rig.games.save(five)
        }
        assertWithMessage("two saves in one action").that(twice).isEqualTo(mapOf(5 to 2))
    }

    /** F07-AC3 */
    @Test
    fun pauseFoldsElapsedAndSaves() = runTest {
        val clock = FakeClock()
        val rig = rig(tmp.newFolder(), clock) { seedGame(IN_PROGRESS_NO_TIME) }
        val vm = rig.loadedViewModel()
        vm.onResumed()

        clock.advance(30_000)
        vm.onPaused()
        rig.awaitIdle()

        assertThat(checkNotNull(rig.fileGame(FOUR)).elapsedMs).isEqualTo(30_000)
    }

    /** F07-AC10 */
    @Test
    fun loadEmitsNoScoreDelta() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(GoldenFixtures.FIX_MID_4) }
        val vm = rig.viewModel()

        vm.header.test {
            assertThat(awaitItem()).isEqualTo(HeaderUi())
            val first = awaitItem()
            assertThat(vm.ready.value).isTrue()
            assertThat(first.score).isEqualTo(10_220)
            assertThat(first.lastDelta).isEqualTo(0)
            assertThat(first.event).isEqualTo(BoardEventKind.Restored)
        }
    }

    /** F07-AC24 */
    @Test
    fun presentationReportsSettleWithoutStateChange() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(ONE_MERGE_FROM_2048) }
        val vm = rig.loadedViewModel()
        vm.onMove(Direction.LEFT)
        testScheduler.runCurrent()
        rig.awaitIdle()
        val b = vm.board.value
        val m = b.moveId
        val header = vm.header.value
        val overlay = vm.overlay.value as OverlayUi.Win
        assertThat((b.event as BoardEvent.Moved).mergeCount).isAtLeast(1)
        assertThat(header.lastDelta).isGreaterThan(0)
        val game = rig.latest(FOUR)
        val records = rig.records.state.value
        val ui = vm.uiState.value
        val saves = rig.saveCounts()

        vm.reportAll(m)
        val settled = vm.snapshot()
        assertThat(settled.board).isEqualTo(BoardPresenter.settled(b))
        assertThat(settled.header).isEqualTo(header.copy(lastDelta = 0))
        assertThat(settled.overlay).isEqualTo(overlay.copy(entry = OverlayEntry.Restored))

        vm.reportAll(m)
        vm.onBoardPresented(m - 1)
        assertThat(vm.snapshot()).isEqualTo(settled)
        testScheduler.runCurrent()
        rig.awaitIdle()
        assertThat(rig.latest(FOUR)).isEqualTo(game)
        assertThat(rig.records.state.value).isEqualTo(records)
        assertThat(vm.uiState.value).isEqualTo(ui)
        assertThat(rig.saveCounts()).isEqualTo(saves)
    }

    /** F07-AC32 */
    @Test
    fun engineCheckFailureIsCorruption() = runTest {
        val undoCase =
            rig(tmp.newFolder()) {
                seedRecords(GoldenFixtures.FIX_RECORDS)
                seedGame(INCONSISTENT_HISTORY)
            }
        undoCase.assertReplacedLikeCorruptFile { onUndo() }

        val moveCase =
            rig(tmp.newFolder()) {
                seedRecords(GoldenFixtures.FIX_RECORDS)
                seedGame(MAX_EXP_PAIR)
            }
        moveCase.assertReplacedLikeCorruptFile { onMove(Direction.LEFT) }
    }

    /** F08-AC8 */
    @Test
    fun headerBestTracksLiveScore() = runTest {
        val rig =
            rig(tmp.newFolder()) {
                seedRecords(BEST_1000)
                seedGame(at(996, listOf(2, 2, 0, 0)))
            }
        val vm = rig.loadedViewModel()

        vm.onMove(Direction.LEFT)

        assertThat(vm.header.value.score).isEqualTo(1004)
        assertThat(vm.header.value.best).isEqualTo(1004)
        assertThat(recordOf(rig.records.state.value, FOUR).bestScore).isEqualTo(1004)
        rig.awaitIdle()
        assertThat(recordOf(rig.fileRecords(), FOUR).bestScore).isEqualTo(1004)
    }

    /** F08-AC16 */
    @Test
    fun resetStatisticsLeavesCurrentGameUntouched() = runTest {
        val rig =
            rig(tmp.newFolder()) {
                seedRecords(GoldenFixtures.FIX_RECORDS)
                seedGame(GoldenFixtures.FIX_MID_4)
            }
        val vm = rig.loadedViewModel()
        val board = vm.board.value

        rig.records.resetAll()
        testScheduler.runCurrent()

        assertThat(rig.records.state.value.sizes).isEmpty()
        assertThat(vm.board.value).isSameInstanceAs(board)
        assertThat(rig.latest(FOUR)).isEqualTo(GoldenFixtures.FIX_MID_4)
        vm.onMove(Direction.LEFT)
        val saved = rig.latest(FOUR)
        assertThat(saved.bestAtStart).isEqualTo(24_680)
        assertThat(saved.newBestAnnounced).isEqualTo(GoldenFixtures.FIX_MID_4.newBestAnnounced)
        assertThat(saved.gameId).isEqualTo(GoldenFixtures.FIX_MID_4.gameId)
    }

    /** F09-AC1 */
    @Test
    fun newBestFiresOnceAboveBestAtStart() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(NEW_BEST_RUN) }
        val vm = rig.loadedViewModel()

        vm.onMove(Direction.LEFT)
        assertThat(vm.header.value.score).isEqualTo(996)
        assertThat(vm.header.value.newBestMoveId).isEqualTo(-1)
        assertThat(rig.latest(FOUR).newBestAnnounced).isFalse()

        vm.onMove(Direction.RIGHT)
        val moment = vm.board.value.moveId
        assertThat(vm.header.value.score).isEqualTo(1004)
        assertThat(vm.header.value.newBestMoveId).isEqualTo(moment)
        assertThat((vm.board.value.event as BoardEvent.Moved).haptic).isEqualTo(MoveHaptic.NEW_BEST)
        assertThat(rig.latest(FOUR).newBestAnnounced).isTrue()

        vm.onMove(Direction.UP)
        assertThat(vm.header.value.score).isEqualTo(1012)
        assertThat(vm.header.value.newBestMoveId).isEqualTo(moment)
        assertThat((vm.board.value.event as BoardEvent.Moved).newBest).isFalse()
        assertThat(rig.latest(FOUR).newBestAnnounced).isTrue()
    }

    /** F09-AC2 */
    @Test
    fun newBestSkippedWhenBestAtStartIsZero() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(at(4992, listOf(2, 2, 0, 0), best = 0)) }
        val vm = rig.loadedViewModel()

        vm.onMove(Direction.LEFT)

        assertThat(vm.header.value.score).isEqualTo(5000)
        assertThat(vm.header.value.newBestMoveId).isEqualTo(-1)
        assertThat((vm.board.value.event as BoardEvent.Moved).newBest).isFalse()
        assertThat(rig.latest(FOUR).newBestAnnounced).isFalse()
    }

    /** F09-AC3 */
    @Test
    fun newBestRequiresStrictlyGreaterScore() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(at(996, listOf(1, 1, 0, 0))) }
        val vm = rig.loadedViewModel()

        vm.onMove(Direction.LEFT)

        assertThat(vm.header.value.score).isEqualTo(1000)
        assertThat(vm.header.value.newBestMoveId).isEqualTo(-1)
        assertThat((vm.board.value.event as BoardEvent.Moved).newBest).isFalse()
        assertThat(rig.latest(FOUR).newBestAnnounced).isFalse()
    }

    /** F09-AC4 */
    @Test
    fun newBestNotRepeatedAfterUndo() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(UNDO_AFTER_NEW_BEST) }
        val vm = rig.loadedViewModel()
        vm.onMove(Direction.UP)
        val moment = vm.board.value.moveId
        assertThat(vm.header.value.score).isEqualTo(1004)
        assertThat(vm.header.value.newBestMoveId).isEqualTo(moment)

        vm.onUndo()
        assertThat(vm.header.value.score).isEqualTo(996)
        assertThat(vm.header.value.newBestAnnounced).isTrue()
        vm.onMove(Direction.LEFT)

        assertThat(vm.header.value.score).isEqualTo(1008)
        assertThat(vm.header.value.newBestMoveId).isEqualTo(moment)
        val event = vm.board.value.event as BoardEvent.Moved
        assertThat(event.newBest).isFalse()
        assertThat(event.haptic).isNotEqualTo(MoveHaptic.NEW_BEST)
    }

    /** F13-AC14 */
    @Test
    fun headerCarriesBoardEventAndEpoch() = runTest {
        val rig =
            rig(tmp.newFolder()) {
                seedGame(MID_4_REPLAYABLE)
                seedGame(GoldenFixtures.FIX_MID_3)
            }
        val vm = rig.loadedViewModel()
        val epoch = vm.board.value.epoch
        vm.assertHeaderCarries(BoardEventKind.Restored, epoch)

        vm.onMove(Direction.UP)
        assertThat((vm.board.value.event as BoardEvent.Moved).mergeCount).isAtLeast(1)
        vm.assertHeaderCarries(BoardEventKind.Moved, epoch)

        vm.onBoardPresented(vm.board.value.moveId)
        vm.onHeaderPresented(vm.board.value.moveId)
        assertThat(vm.board.value.event).isEqualTo(BoardEvent.Restored)
        vm.assertHeaderCarries(BoardEventKind.Moved, epoch)

        vm.onUndo()
        vm.assertHeaderCarries(BoardEventKind.Undo, epoch + 1)
        vm.onNewGame()
        vm.assertHeaderCarries(BoardEventKind.NewGame, epoch + 2)
        vm.onSizeSelected(THREE)
        testScheduler.runCurrent()
        vm.assertHeaderCarries(BoardEventKind.SizeSwitched, epoch + 3)
    }

    /** F22-AC1 */
    @Test
    fun hintShownOnFreshInstall() = runTest {
        val vm = rig(tmp.newFolder()).loadedViewModel()

        assertThat(vm.uiState.value.showFirstLaunchHint).isTrue()
    }

    /** F22-AC2 */
    @Test
    fun hintShownWhileStartingGameHasNoMove() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(savedAt(Game.newGame(FOUR, 0x2049))) }
        val vm = rig.loadedViewModel()

        assertThat(vm.uiState.value.showFirstLaunchHint).isTrue()
    }

    /** F22-AC3 */
    @Test
    fun firstAcceptedMoveDismissesHint() = runTest {
        val rig = rig(tmp.newFolder())
        val vm = rig.loadedViewModel()
        rig.awaitIdle()
        assertThat(vm.uiState.value.showFirstLaunchHint).isTrue()
        val saves = rig.saveCounts()

        vm.playAcceptedMove()

        assertThat(vm.uiState.value.showFirstLaunchHint).isFalse()
        testScheduler.runCurrent()
        assertThat(rig.saveCounts().settings - saves.settings).isEqualTo(1)
        rig.awaitIdle()
        assertThat(rig.fileSettings().onboardingDone).isTrue()
    }

    /** F22-AC4 */
    @Test
    fun invalidMoveKeepsHint() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(savedAt(LONE_TILE)) }
        val vm = rig.loadedViewModel()
        rig.awaitIdle()
        assertThat(vm.uiState.value.showFirstLaunchHint).isTrue()
        val saves = rig.saveCounts()

        vm.onMove(Direction.LEFT)

        assertThat(vm.nudge.value).isEqualTo(Nudge(1, Direction.LEFT))
        assertThat(vm.uiState.value.showFirstLaunchHint).isTrue()
        testScheduler.runCurrent()
        rig.awaitIdle()
        assertThat(rig.saveCounts().settings).isEqualTo(saves.settings)
        assertThat(rig.settings.state.value.onboardingDone).isFalse()
    }

    /** F22-AC10 */
    @Test
    fun hintHiddenAfterAMove() = runTest {
        val rig = rig(tmp.newFolder()) { seedGame(GoldenFixtures.FIX_MID_4) }
        val vm = rig.loadedViewModel()

        assertThat(rig.settings.state.value.onboardingDone).isFalse()
        assertThat(vm.uiState.value.showFirstLaunchHint).isFalse()
    }
}

private val THREE: BoardSize = BoardSize.of(3)
private val FOUR: BoardSize = BoardSize.of(4)
private val FIVE: BoardSize = BoardSize.of(5)
private val SIX: BoardSize = BoardSize.of(6)

private val DLRU = listOf(Direction.DOWN, Direction.LEFT, Direction.RIGHT, Direction.UP)

private val BEST_1000 = Records(sizes = mapOf("4" to SizeRecord(bestScore = 1000)))

/** A 4×4 `PLAYING` game at move 32 with [score], `bestAtStart` [best] and [row0] on top. */
private fun at(score: Long, row0: List<Int>, best: Long = 1000, rest: List<Int> = EMPTY_ROWS) =
    fixtureGame(
        n = 4,
        cells = row0 + rest,
        score = score,
        best = best,
        moves = 32,
        undos = 0,
        elapsedMs = 0,
        status = GameStatus.PLAYING,
    )

private val EMPTY_ROWS = List(12) { 0 }

/** LEFT merges two 1024s: the next move wins (D-036). Move 32, so undo returns to it exactly. */
private val ONE_MERGE_FROM_2048 =
    at(20_000, listOf(10, 10, 0, 0), best = 0, rest = listOf(1, 2, 0, 0) + List(8) { 0 })

/** Full, no equal neighbours, a 2048 tile: `WON`, and Keep going leads straight to `OVER`. */
private val WON_LOCKED =
    fixtureGame(
        n = 4,
        cells = listOf(11, 2, 1, 2, 2, 1, 2, 1, 1, 2, 1, 2, 2, 1, 2, 1),
        score = 25_000,
        best = 0,
        moves = 500,
        undos = 0,
        elapsedMs = 0,
        status = GameStatus.WON,
    )

/** LEFT fills the last cell with no merge left: `OVER` (D-037). Move 96, so undo returns. */
private val ONE_MOVE_FROM_OVER =
    fixtureGame(
        n = 4,
        cells = listOf(3, 4, 3, 4, 4, 3, 4, 3, 3, 4, 3, 4, 0, 5, 6, 5),
        score = 3000,
        best = 0,
        moves = 96,
        undos = 1,
        elapsedMs = 5_000,
        status = GameStatus.PLAYING,
    )

/** A 6×6 checkerboard of 2s and 4s: full with no equal neighbours, `OVER`. */
private val OVER_6 =
    fixtureGame(
        n = 6,
        cells = List(36) { i -> 1 + (i / 6 + i % 6) % 2 },
        score = 5000,
        best = 0,
        moves = 700,
        undos = 0,
        elapsedMs = 300_000,
        status = GameStatus.OVER,
    )

private val IN_PROGRESS_4000 =
    fixtureGame(
            n = 4,
            cells = listOf(1, 2, 3, 4, 5, 6, 7, 8, 1, 0, 0, 0, 0, 0, 0, 0),
            score = 4000,
            best = 0,
            moves = 200,
            undos = 1,
            elapsedMs = 50_000,
            status = GameStatus.PLAYING,
            seed = 0x2048 + 7,
        )
        .copy(newBestAnnounced = true, gameId = 3)

private val IN_PROGRESS_NO_TIME = GoldenFixtures.FIX_MID_4.copy(elapsedMs = 0)

/** A `PLAYING` game with moves and score 0 (D-044: not counted at New game). */
private val ZERO_SCORE =
    fixtureGame(
        n = 4,
        cells = listOf(1, 0, 0, 0, 0, 2, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0),
        score = 0,
        best = 0,
        moves = 5,
        undos = 0,
        elapsedMs = 2_000,
        status = GameStatus.PLAYING,
    )

private val OVER_9000 =
    fixtureGame(
        n = 4,
        cells = GoldenFixtures.FIX_OVER_4.state.board.let { b -> List(16) { b[it] } },
        score = 9000,
        best = 8000,
        moves = 302,
        undos = 0,
        elapsedMs = 401_000,
        status = GameStatus.OVER,
    )

/** LEFT, RIGHT, UP reach 996, 1004 and 1012 from 992 with `bestAtStart` 1000 (F09-AC1). */
private val NEW_BEST_RUN =
    at(992, listOf(1, 1, 0, 2), rest = listOf(0, 0, 0, 2, 2, 0, 0, 0, 2, 0, 0, 0))

/** UP reaches 1004 (+8); from the same board LEFT reaches 1008 (+12) (F09-AC4). */
private val UNDO_AFTER_NEW_BEST =
    at(996, listOf(2, 0, 0, 0), rest = listOf(2, 0, 0, 0, 0, 0, 0, 0, 2, 2, 1, 1))

/** `FIX_MID_4`'s board at move 416 (a multiple of 32), so an undo replays nothing. */
private val MID_4_REPLAYABLE =
    fixtureGame(
        n = 4,
        cells = GoldenFixtures.FIX_MID_4.state.board.let { b -> List(16) { b[it] } },
        score = 10_220,
        best = 24_680,
        moves = 416,
        undos = 2,
        elapsedMs = 598_000,
        status = GameStatus.PLAYING,
    )

/** A move-0 4×4 state on [cells] whose `snapshots[0]` is the new game of 0x2048 (I7). */
private fun moveZero(cells: List<Int>): GameState =
    Game.newGame(FOUR, 0x2048)
        .copy(board = Board.of(FOUR, ByteArray(16) { cells.getOrElse(it) { 0 }.toByte() }))

/** One tile in the top-left corner: LEFT and UP are no-ops (D-035). */
private val LONE_TILE = moveZero(listOf(1))

/** D-214: two exponent-127 tiles that LEFT would merge past `Board.MAX_EXP`. */
private val MAX_EXP_PAIR = savedAt(moveZero(listOf(Board.MAX_EXP, Board.MAX_EXP)))

/**
 * D-214 / `UndoTest.undoOfInconsistentHistoryThrows`: the 40-move DLRU game of 0x2048 with
 * `snapshots[1].board` replaced by a lone tile, so the undo's replay of move 32 (LEFT) fails.
 */
private val INCONSISTENT_HISTORY: SavedGame = run {
    var state = Game.newGame(FOUR, 0x2048)
    while (state.moveCount < 40) state = DLRU.firstNotNullOf { accepted(state, it) }.state
    val lone = Board.of(FOUR, ByteArray(16).also { it[0] = 1 })
    val snapshots = listOf(state.snapshots[0], state.snapshots[1].copy(board = lone))
    savedAt(state.copy(snapshots = snapshots)).copy(gameId = 3)
}

private fun savedAt(state: GameState) =
    SavedGame(
        state = state,
        elapsedMs = 0,
        bestAtStart = 0,
        newBestAnnounced = false,
        gameId = 1,
        pendingFinalizations = emptyList(),
    )

private fun accepted(state: GameState, direction: Direction): MoveOutcome.Moved? =
    Game.move(state, direction) as? MoveOutcome.Moved

private fun moved(state: GameState, direction: Direction): MoveOutcome.Moved =
    checkNotNull(accepted(state, direction)) { "$direction is not accepted on ${state.board}" }

private fun cellsOf(state: GameState): List<Int> = List(state.board.cellCount) { state.board[it] }

private fun summaryOf(state: GameState, best: Long, elapsedMs: Long) =
    GameSummary(
        size = state.size,
        score = state.score,
        best = best,
        bestTileExp = state.board.maxExp(),
        moves = state.moveCount,
        elapsedMs = elapsedMs,
        undos = state.undoCount,
        cells = cellsOf(state).toImmutableList(),
    )

private fun recordOf(records: Records, size: BoardSize): SizeRecord =
    records.sizes["${size.n}"] ?: SizeRecord()

private fun SavedGame.pendingIds(): List<Long> = pendingFinalizations.map { it.gameId }

/** Every flow of the ViewModel at one moment. */
private data class Flows(
    val ready: Boolean,
    val uiState: GameUiState,
    val board: BoardUi,
    val header: HeaderUi,
    val overlay: OverlayUi,
    val nudge: Nudge?,
)

private fun GameViewModel.snapshot() =
    Flows(ready.value, uiState.value, board.value, header.value, overlay.value, nudge.value)

/** The three presentation reports of §4.11 for [moveId]. */
private fun GameViewModel.reportAll(moveId: Long) {
    onBoardPresented(moveId)
    onHeaderPresented(moveId)
    onOverlayPresented(moveId)
}

private fun GameViewModel.assertHeaderCarries(event: BoardEventKind, epoch: Long) {
    val header = header.value
    assertWithMessage("$event: deltaMoveId").that(header.deltaMoveId).isEqualTo(board.value.moveId)
    assertWithMessage("$event: header epoch").that(header.epoch).isEqualTo(board.value.epoch)
    assertWithMessage("$event: epoch").that(header.epoch).isEqualTo(epoch)
    assertWithMessage("$event: event").that(header.event).isEqualTo(event)
    if (event != BoardEventKind.Moved) {
        assertWithMessage("$event: board event").that(board.value.event.kind).isEqualTo(event)
    }
}

/** Plays the first of DOWN, LEFT, RIGHT, UP that changes the board. */
private fun GameViewModel.playAcceptedMove() {
    val accepted = DLRU.any { d ->
        val before = board.value.moveId
        onMove(d)
        board.value.moveId != before
    }
    check(accepted) { "No direction changes ${board.value.cells}" }
}

/** "Played to a score" (QUALITY.md §1.11 step 6): accepted moves until `score > 0`. */
private fun playToScore(vm: GameViewModel): Long {
    repeat(MAX_MOVES_TO_SCORE) {
        if (vm.header.value.score > 0) return vm.header.value.score
        vm.playAcceptedMove()
    }
    error("No score after $MAX_MOVES_TO_SCORE moves")
}

private const val MAX_MOVES_TO_SCORE = 100

/** Submitted saves per file (D-221): settings, records and each game size. */
private data class SaveCounts(val settings: Int, val records: Int, val games: Map<Int, Int>) {
    fun recordDelta(before: SaveCounts): Int = records - before.records

    /** The sizes with game saves since [before], with their counts. */
    fun gameDeltas(before: SaveCounts): Map<Int, Int> =
        games.mapValues { (n, count) -> count - (before.games[n] ?: 0) }.filterValues { it != 0 }
}

/** One container over [folder] and its own repositories (one document holder per file, D-022). */
private class Rig(
    private val scope: TestScope,
    val container: AppContainer,
    private val folder: File,
    private val clock: FakeClock,
) {
    val settings: SettingsRepository = container.settingsRepository
    val records: RecordsRepository = container.recordsRepository
    val games: GameRepository = container.gameRepository

    fun viewModel() = GameViewModel(games, records, settings, clock, container.seedSource)

    /** A ViewModel whose load has completed. */
    fun loadedViewModel(): GameViewModel {
        val vm = viewModel()
        scope.testScheduler.runCurrent()
        check(vm.ready.value) { "The ViewModel did not become ready" }
        return vm
    }

    fun saveCounts() =
        SaveCounts(
            settings = settings.submissions.toInt(),
            records = records.submissions.toInt(),
            games = BoardSize.ALL.associate { it.n to games.submissions(it).toInt() },
        )

    /** The game saves [action] submitted, per size (D-221), with the scheduler run around it. */
    fun gameSaves(action: () -> Unit): Map<Int, Int> {
        scope.testScheduler.runCurrent()
        val before = saveCounts()
        action()
        scope.testScheduler.runCurrent()
        return saveCounts().gameDeltas(before)
    }

    suspend fun awaitIdle() {
        settings.awaitIdle()
        records.awaitIdle()
        games.awaitIdle()
    }

    /** The last game submitted for [size] (or the file's): what the ViewModel saved last. */
    suspend fun latest(size: BoardSize): SavedGame = checkNotNull(games.peek(size))

    suspend fun fileGame(size: BoardSize): SavedGame? {
        val doc = container.gameStore(size).data.first()
        return if (doc.size == 0) null else GameDocumentMapper.toSavedGame(doc)
    }

    suspend fun fileRecords(): Records = container.recordsStore.data.first()

    suspend fun fileSettings(): Settings = container.settingsStore.data.first()

    /** Every file of the folder by name, as bytes. */
    fun fileBytes(): Map<String, List<Byte>> =
        folder
            .walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(folder).path to it.readBytes().toList() }

    /**
     * D-214: [action] meets an engine check failure; the game is replaced like a corrupt file: a
     * fresh game saved once, presented as `NewGame` with no card, and nothing finalized.
     */
    suspend fun assertReplacedLikeCorruptFile(action: GameViewModel.() -> Unit) {
        val vm = loadedViewModel()
        awaitIdle()
        val saves = saveCounts()
        val recordsFile = fileBytes()["records.json"]
        val recordsBefore = records.state.value

        vm.action()

        assertThat(vm.board.value.event).isEqualTo(BoardEvent.NewGame)
        assertThat(vm.overlay.value).isEqualTo(OverlayUi.None)
        assertThat(latest(FOUR).state.moveCount).isEqualTo(0)
        assertThat(latest(FOUR).pendingFinalizations).isEmpty()
        scope.testScheduler.runCurrent()
        awaitIdle()
        assertThat(saveCounts().gameDeltas(saves)).isEqualTo(mapOf(4 to 1))
        assertThat(saveCounts().recordDelta(saves)).isEqualTo(0)
        assertThat(records.state.value).isEqualTo(recordsBefore)
        assertThat(fileBytes()["records.json"]).isEqualTo(recordsFile)
    }
}

/** A [Rig] over [folder]; [seed] writes files through the container's stores first. */
private suspend fun TestScope.rig(
    folder: File,
    clock: FakeClock = FakeClock(),
    seedSource: SeedSource = FixedSeedSource(0x2048),
    seed: suspend AppContainer.() -> Unit = {},
): Rig {
    val container = testAppContainer(folder, clock, seedSource)
    container.seed()
    return Rig(this, container, folder, clock)
}
