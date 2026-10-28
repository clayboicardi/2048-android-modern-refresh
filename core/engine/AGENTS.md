# AGENTS.md — `:core:engine`

Pure Kotlin 2048 rules. Kotlin stdlib only: no Android, kotlinx, clock or platform RNG.

## Code Review Rules

### Game rules
- Flag any change to these semantics (DECISIONS.md D-030–D-039), which are legacy parity and are pinned by
  golden tests: lines are processed from the wall the move goes toward; a tile merges at most once per move;
  score grows by exactly each merged value; a no-op move draws no random number and changes nothing; after an
  accepted move exactly one tile spawns, value first (4 when `nextInt(10) == 0`) then a uniform empty cell in
  row-major order; the win is reported once, on the exact target (256 on 3×3, else 2048); game over is
  evaluated after the spawn; undo restores the RNG state, so the same move gives the same spawn.
- Flag clock reads, `kotlin.random`, `java.util.Random`, `Math.random`, `SecureRandom`, `java.time`,
  `android.*` or `kotlinx.*` imports in this module. The safe path is `SplitMix64` with a stored seed.
- Flag changes to `MoveLog` or `Snapshot` byte layouts, or to `GameStatus` order, without a `schemaVersion`
  bump and a `DataMigration` in `:app` (D-041, D-042).
- Changing a golden vector needs a DECISIONS.md D-173 entry.
