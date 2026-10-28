package com.clayboicardi.game2048.game.board

import androidx.compose.runtime.Immutable
import com.clayboicardi.game2048.engine.BoardSize
import com.clayboicardi.game2048.engine.Direction
import kotlinx.collections.immutable.ImmutableList

/** How a tile appears in a `BoardUi` (D-025). Epoch and settled boards hold only `Static`. */
enum class TileKind {
    Static,
    Moved,
    Merged,
    Ghost,
    Spawned,
}

/** One tile of a `BoardUi` with its stable id (D-025, ARCHITECTURE.md §5.1–§5.2). */
@Immutable
data class TileUi(
    val id: Long,
    val exp: Int,
    val row: Int,
    val col: Int,
    val fromRow: Int,
    val fromCol: Int,
    val kind: TileKind,
)

/**
 * The haptic of an accepted move in D-065 priority order, resolved by `HapticsMap` in
 * `GameViewModel` and only scheduled by `HapticsController` (D-185).
 */
enum class MoveHaptic {
    WIN,
    NEW_BEST,
    BIG_MERGE,
    MERGE,
    NONE,
}

/** Why a `BoardUi` was emitted (ARCHITECTURE.md §5.1, D-182). */
@Immutable
sealed interface BoardEvent {
    /**
     * Load, splash release, and every presented board after `BoardPresenter.settled` (§4.11):
     * `Static` tiles, no animation at all (D-047).
     */
    data object Restored : BoardEvent

    /** An accepted move. */
    data class Moved(
        val direction: Direction,
        val mergedExps: ImmutableList<Int>,
        val mergeCount: Int,
        /** Drives the >= 512 haptic and the "+N" pill (D-183, D-187); 0 without a merge. */
        val maxMergedExp: Int,
        /** Longest slide of the move in cells: selects the spawn delay (D-183). */
        val maxSlideCells: Int,
        val spawnIndex: Int,
        val scoreAfter: Long,
        /** Scheduled, never re-resolved, by `HapticsController` (D-185). */
        val haptic: MoveHaptic,
        val newBest: Boolean,
        val becameWon: Boolean,
        val becameOver: Boolean,
    ) : BoardEvent

    /** `Static` tiles; only changed cells crossfade (D-064, D-182). */
    data object Undo : BoardEvent

    /** `Static` tiles; the new-game cascade of outgoing and start tiles (D-064, D-182). */
    data object NewGame : BoardEvent

    /**
     * `Static` tiles; the board fades through `AnimatedContent(targetState = boardSize)` (D-182).
     */
    data object SizeSwitched : BoardEvent
}

/** `BoardEvent`'s cases by name, without the `Moved` payload: what `HeaderUi.event` carries. */
enum class BoardEventKind {
    Restored,
    Moved,
    Undo,
    NewGame,
    SizeSwitched,
}

/** The case of this event (ARCHITECTURE.md §4.7, §4.11). */
val BoardEvent.kind: BoardEventKind
    get() =
        when (this) {
            BoardEvent.Restored -> BoardEventKind.Restored
            is BoardEvent.Moved -> BoardEventKind.Moved
            BoardEvent.Undo -> BoardEventKind.Undo
            BoardEvent.NewGame -> BoardEventKind.NewGame
            BoardEvent.SizeSwitched -> BoardEventKind.SizeSwitched
        }

/**
 * The render model of one committed board (ARCHITECTURE.md §5.1). An epoch board (event `Restored`,
 * `Undo`, `NewGame` or `SizeSwitched`) and a settled board hold only `Static` tiles (D-182).
 * Invalid moves never produce a `BoardUi`.
 */
@Immutable
data class BoardUi(
    /** +1 for every `BoardUi` the presenter emits; `settled()` keeps it (§4.11). */
    val moveId: Long,
    /**
     * +1 for every reset (`Restored` / `Undo` / `NewGame` / `SizeSwitched`); `settled()` keeps it.
     */
    val epoch: Long,
    val size: BoardSize,
    /** Z-order: `Ghost`, `Static`/`Moved`, `Merged`, `Spawned`. */
    val tiles: ImmutableList<TileUi>,
    /** Row-major exponents after the move: semantics and share. */
    val cells: ImmutableList<Int>,
    val scoreDelta: Long,
    val event: BoardEvent,
)
