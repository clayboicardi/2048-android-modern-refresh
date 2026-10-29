package com.clayboicardi.game2048.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clayboicardi.game2048.data.game.GameRepository
import com.clayboicardi.game2048.data.game.SavedGame
import com.clayboicardi.game2048.data.records.Records
import com.clayboicardi.game2048.data.records.RecordsMath
import com.clayboicardi.game2048.data.records.RecordsRepository
import com.clayboicardi.game2048.data.settings.SettingsRepository
import com.clayboicardi.game2048.engine.BoardSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Statistics screen's state (ARCHITECTURE.md §4.7, §4.9; DESIGN_SYSTEM.md §6.9; FEATURES.md
 * F08; D-044, D-198). Entry-scoped: `container.statsViewModelFactory()` inside the `StatsKey`
 * entry.
 *
 * [ui] is `null` until the settings and records have loaded and every size's saved game has been
 * read once (`GameRepository.peek` for 3..8; the reads run on the stores' own dispatcher, and the
 * ViewModel resumes on the main thread, where the repositories expect their callers). From then on
 * it follows `records.state`: each value is folded with the saved games,
 * `BoardSize.ALL.fold(records) { r, n -> RecordsMath.displayed(r, saved[n]) }`, so every recovery
 * entry not yet counted and every `OVER` game not yet finalized shows once, and the result is
 * displayed only, never persisted (D-198). The tab it opens on is the current board size,
 * `settings.lastBoardSize`, read once.
 *
 * Only [reset] writes: `RecordsRepository.resetAll()` (D-044), which the screen calls after both
 * confirmations; the fold then shows the reset records with the same saved games.
 *
 * M4.1 contract (D-248): the constructor is fixed.
 */
class StatsViewModel(
    private val records: RecordsRepository,
    private val games: GameRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val state = MutableStateFlow<StatsUi?>(null)

    /** What the screen shows; `null` until the first fold (see the class KDoc). */
    val ui: StateFlow<StatsUi?> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val currentSize = settings.awaitLoaded().lastBoardSize
            records.awaitLoaded()
            val saved = BoardSize.ALL.associateWith { games.peek(it) }
            records.state.collect { r -> state.value = StatsUi.of(fold(r, saved), currentSize) }
        }
    }

    /** Reset statistics, after the second confirmation (D-044; ARCHITECTURE.md §4.9). */
    fun reset() {
        records.resetAll()
    }

    private companion object {
        /** The D-198 display-time fold over every size, in size order. */
        fun fold(records: Records, saved: Map<BoardSize, SavedGame?>): Records =
            BoardSize.ALL.fold(records) { r, size -> RecordsMath.displayed(r, saved[size]) }
    }
}
