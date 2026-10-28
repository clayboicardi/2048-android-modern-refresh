# L10N_STORE_LEGAL.md: localization, store listing, identity assets, legal files and signing

Status: plan document for the execution PR (D-160), dated 2026-10-28.

`DECISIONS.md` is canonical. Where this file and `DECISIONS.md` disagree, `DECISIONS.md` wins. The planning-time readings and extensions this file relies on are DECISIONS entries (D-179, D-190, D-202, D-208, D-209 and technical corrections TC-1 and TC-3), cited by ID; [Open issues](#open-issues) keeps only what stays open.

Audience: the orchestrator and the implementer agents that execute milestones M0–M6 (D-162, D-194) with no other context. Read `DECISIONS.md`, then `ARCHITECTURE.md` (build and file map), `DESIGN_SYSTEM.md` (screens, tokens, goldens), `FEATURES.md` (behavior and acceptance criteria), then this file.

What this file owns:
- **§1 Localization:** the locale set, the legacy string migration, the complete v1.0 string inventory (every key with its English source, arguments and translator context), plurals, the glossary, style rules, the translation workflow, automated translation checks, digits, RTL, pseudo-locales, locale config and per-app language.
- **§2 Store listing:** the `fastlane/metadata/android/` tree, the English copy, translation rules, graphics rendered from code, the listing checks and F-Droid notes.
- **§3 Identity assets:** launcher icon files and geometry, the API 24–25 fallback, the splash and the store icon.
- **§4 Legal:** `LICENSE`, `NOTICE`, the in-app license data, credits and legacy history data, `PRIVACY.md`, the in-app privacy text, the permission wording and the README rewrite.
- **§5 Signing and distribution:** the public CI sideload key, installing CI APKs, update-in-place rules, and what changes when real signing secrets exist.

What it does not restate: build files and the manifest (`ARCHITECTURE.md` §2, §8), screen layouts and tokens (`DESIGN_SYSTEM.md` §6–§8), feature behavior (`FEATURES.md` §1). When a sibling already fixed a string key or its English text, this file uses it verbatim.

---

## 0. Conventions

### 0.1 IDs, tests and gates

- Acceptance criteria in this file are `L-ACn`. When `FEATURES.md` already has an AC for the same fact, this file cites that AC instead of adding one.
- Tests named here that `FEATURES.md` §8 did not list are listed there and in `ARCHITECTURE.md` §10.5. Every new test file raises `config/test-count-floor.txt` in the same commit (D-149).
- The `check-metadata.sh` assertions are D-190 (M5) and D-179 (M0); `QUALITY.md` §1.16 is the canonical script.
- In inventory tables, **✱** marks `translatable="false"`. Word-free format strings, which hold only placeholders and separators (`board_size_supporting`, `game_over_summary`, `stats_wins_value`), are ✱ (D-208).
- Names of classes, files, tests and string keys are the canonical ones of `PLAN.md` §2.
- Paths are repository-relative. "research/0N" means `docs/modernization/research/0N-*.md`.

### 0.2 Milestone map

| Work item | M | D-IDs | Section |
|---|---|---|---|
| Move the legacy `strings.xml` files; rename `action_settings`; delete dead keys, the cs BOM and every locale `app_name`; `check_no_bom` | M0 | D-101, D-179 | §1.2 |
| `resources.properties` and `generateLocaleConfig` | M0 | D-102 | §1.13 |
| `git mv metadata fastlane/metadata/android`; delete 16 listing-only locales and `promoGraphic.png`; move images under `en-US/images/` | M0 | D-113 | §2.1 |
| `LICENSE` copyright lines; minimal README edit | M0 | D-116, D-120 | §4.2, §4.9 |
| Launcher icon, colors, splash theme; `check_icon_glyphs` | M0 | D-111, D-112, D-179 | §3 |
| CI sideload keystore and `signing/README.md` | M0 | D-131 | §5 |
| `docs/modernization/legacy-credits.md` (before the submodule is removed) | M0 | D-124 | §4.5 |
| English strings land with each feature, in `values/strings_pending.xml` | M3, M4 | D-103, D-208 | §1.3, §1.4 |
| `res/raw/` license texts, `third_party_licenses.json`, `legacy_history.json`; `config/license-groups.txt` content | M0 (groups), M4 | D-122, D-124 | §4.4, §4.5 |
| `docs/modernization/i18n-glossary.md`, then all 20 translations; `strings_pending.xml` merged and deleted | M5 | D-100, D-105 | §1.4–§1.8 |
| `TranslationQualityTest`, `LocalizationTest`, locale goldens 23–26 | M5 | D-100, D-104, D-106 | §1.9–§1.12 |
| Store texts in 22 locales; store graphics from `StoreAssetsTest` through `OpaquePngWriter` | M5 | D-113–D-115, D-190 | §2 |
| `NOTICE`, `PRIVACY.md`, full README rewrite | M5 | D-116, D-121, D-123 | §4 |
| `check-metadata.sh` listing, image and legal functions (`check_store_listing`, `check_store_images`, `check_legal_and_privacy`, `check_legal_texts`, `check_strings_final`) | M5 | D-152, D-190 | §2.6, §4.10; `QUALITY.md` §1.16 |

### 0.3 Facts verified while writing this file (2026-10-28)

1. `ls -d res/values*` in the legacy tree prints `values`, the 20 locale directories of §1.1, and `values-sw720dp-land`. Every legacy `strings.xml` holds exactly 4 keys, one per line.
2. Only `res/values-cs/strings.xml` starts with the UTF-8 BOM `EF BB BF`. `res/values-da/strings.xml` has no XML declaration. `res/values-fa/strings.xml` sets `app_name` to "۲۰۴۸".
3. The M0 migration commands of §1.2 and §2.1 were dry-run on a scratch copy and a scratch clone. They produce 21 parseable `strings.xml` files and 13 kept listing directories (17 deletions, 18 renames).
4. `res/drawable/ic_2048.xml` has 4 paths of 345, 350, 255 and 721 characters. All are under Lint's 800-character `VectorPath` threshold, which `warningsAsErrors` would make fatal.
5. WCAG contrast: glyph `#3A2900` on `#E6A11A` is **6.33:1**. The `#E6A11A` disc against the splash background is 2.08:1 (light `#FAF8EF`) and 7.92:1 (dark `#1B1916`).
6. The group transform maps the glyph band (x 6.614–46.301, y 19.843–33.072) to x 26.0–82.0 and y 44.67–63.33 in the 108-unit viewport, centered at (54.0, 54.0).
7. `:app:dependencies --configuration releaseRuntimeClasspath` on the architecture scratch build (the exact D-016 catalog and `ARCHITECTURE.md` §2.6 dependency block) resolves **37 Maven groups**, listed in §4.4. `androidx.appcompat:appcompat-resources` arrives through `core-splashscreen` 1.2.0, and `com.squareup.okio` through DataStore. All 37 are Apache-2.0.
8. Google Sans Flex `OFL.txt` at `google/fonts` `main` = `9b59ea9f333c5b8a99fa44e2a698496d6cde5b8e`: 4,488 bytes, sha256 `fc13d69f63e36d284b6e383d4d1463d8ad404f0aa065e57cf961252d51063137`, first line "Copyright 2015 The Google Sans Flex Authors (https://github.com/googlefonts/googlesans-flex)". The variable TTF's name record 7 reads "Google Sans is a trademark of Google."
9. The Apache-2.0 text from `https://www.apache.org/licenses/LICENSE-2.0.txt`: 11,358 bytes, sha256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.
10. The final `LICENSE` of §4.2: 1,151 bytes, sha256 `ad96d837d59c50cb3c8e23a17d7ee19a396c5a1a6c4bc952a269896fcbdbfd19`.
11. The en-US store texts of §2.3 measure 4 / 63 / 2,295 / 345 characters (limits 30 / 80 / 4,000 / 500). The listing and image checker (now `check_store_listing` and `check_store_images` in `QUALITY.md` §1.16) passed on a synthetic 22-locale tree and failed, as intended, on an untranslated file, an RGBA feature graphic and Persian digits.
12. Legacy contributors (§4.5) come from `git log --format='%an' e097460` and `git -C assets/2048 log --format='%an' fc1ef4f..7a477eb`. Translators per locale come from `git log --follow` on each `res/values-xx/strings.xml`.
13. Android developer verification (developer.android.com, pages dated 2026-09-30 and 2026-10-23): since 2026-09-30, unregistered apps cannot be installed **from participating stores** on certified devices in Brazil, Indonesia, Singapore and Thailand. Direct sideloading is outside this first phase, `adb` installs are exempt, a one-time "advanced flow" lets power users install unregistered apps, and Google plans a global rollout from 2027 (§5.6).
14. The Kotlin snippets of §1.10, §1.13 and §2.5 and the store-icon code of §3.7 compiled under `allWarningsAsErrors` and passed a Robolectric SDK 36 test on a copy of the architecture scratch build:
    - `formatCount(24680, locale)` gave "24,680" (en), "24.680" (de), "24,680" (fa) and "24 680" with U+00A0 (ru);
    - `formatPercent(9, 37, locale)` gave "24%" (en) and "24 %" with U+00A0 (de);
    - `formatElapsed` gave the F28-AC8 values;
    - `OpaquePngWriter` wrote IHDR color type 2, and the file decoded back with the expected pixels;
    - `appLanguageSummary` returned "System default";
    - the store icon showed the glyph band at 396 × 131 px of solid glyph color (§3.5), and `Bitmap.compress` wrote color type 6 (§3.7 now writes the icon through `OpaquePngWriter` too, D-190).
15. Lint 32.4.1 (AGP 9.4.1), run on that copy with the §1.3 inventory of that date (160 translatable keys, before D-202 added 5) in which only `settings` and `moves_count` were translated:
    - with the §1.4 `strings_pending.xml`, `MissingTranslation` reported 0 errors; without its `tools:ignore`, 158;
    - plural sets without `many` failed `MissingQuantity` for exactly cs, es, fr, it, lt and pt, and the full §1.1 sets passed with 0 issues;
    - a `one` item without its number failed `ImpliedQuantity`, a locale-only key failed `ExtraTranslation`, and a translated `app_name` failed **`Untranslatable`** (not `ExtraTranslation`);
    - a leading UTF-8 BOM in `values-cs/strings.xml` was **not** reported by `ByteOrderMark`;
    - `PluralsCandidate`, `Typos`, `TypographyEllipsis` and `TypographyDashes` reported nothing on the English inventory.

---

## 1. Localization

### 1.1 Locale set (D-100)

The app ships English (`values/`, tagged `en-US` through `unqualifiedResLocale`) plus exactly the 20 legacy locales. This was re-verified from `res/` (§0.3 item 1).

| Resource dir | Tag | Language | Script, direction | CLDR cardinal categories | Register (§1.7) | Legacy `settings` (kept verbatim) |
|---|---|---|---|---|---|---|
| `values` | en-US | English | Latin, LTR | one, other | — | Settings |
| `values-cs` | cs | Czech | Latin, LTR | one, few, many, other | formal (vykání) | Nastavení |
| `values-da` | da | Danish | Latin, LTR | one, other | du | Indstillinger |
| `values-de` | de | German | Latin, LTR | one, other | du | Einstellungen |
| `values-el` | el | Greek | Greek, LTR | one, other | formal plural | Ρυθμίσεις |
| `values-eo` | eo | Esperanto | Latin, LTR | one, other | vi | Agordoj |
| `values-es` | es | Spanish | Latin, LTR | one, many, other | tú | Ajustes |
| `values-fa` | fa | Persian | Arabic, **RTL** | one, other | formal (شما) | تنظیمات |
| `values-fr` | fr | French | Latin, LTR | one, many, other | vous | Paramètres |
| `values-hu` | hu | Hungarian | Latin, LTR | one, other | formal (Ön) | Beállítások |
| `values-it` | it | Italian | Latin, LTR | one, many, other | tu | Impostazioni |
| `values-ko` | ko | Korean | Hangul, LTR | other | polite (-세요) | 설정 |
| `values-lt` | lt | Lithuanian | Latin, LTR | one, few, many, other | formal (jūs) | Nustatymai |
| `values-nl` | nl | Dutch | Latin, LTR | one, other | je | Instellingen |
| `values-pl` | pl | Polish | Latin, LTR | one, few, many, other | 2nd-person imperative | Ustawienia |
| `values-pt` | pt | Portuguese (generic) | Latin, LTR | one, many, other | neutral imperative | Configurações |
| `values-ru` | ru | Russian | Cyrillic, LTR | one, few, many, other | formal (вы) | Настройки |
| `values-sl` | sl | Slovenian | Latin, LTR | one, two, few, other | formal (vikanje) | Nastavitve |
| `values-sr` | sr | Serbian | **Cyrillic**, LTR | one, few, other | formal (ви) | Подешавања |
| `values-tr` | tr | Turkish | Latin, LTR | one, other | formal (-in) | Ayarlar |
| `values-uk` | uk | Ukrainian | Cyrillic, LTR | one, few, many, other | formal (ви) | Налаштування |

- `values-sw720dp-land/` is not a locale. M0 deletes it (D-001).
- Not shipped: `values-b+sr+Latn`, `values-pt-rBR`, `values-pt-rPT`, `values-ar`, and the 16 listing-only locales (D-100, D-113).
- Fallbacks: a device language outside these 21 gets English; `pt-BR` and `pt-PT` devices get `values-pt`; an `sr-Latn` device gets the Cyrillic `values-sr` (D-100).

### 1.2 Legacy string migration (M0, D-101)

Fate of the four legacy keys:

| Legacy key | Present in | Fate |
|---|---|---|
| `app_name` | default + 20 (`fa` = "۲۰۴۸") | Default kept as `translatable="false"` "2048". Deleted from all 20 locales; Lint `Untranslatable` (fatal under D-142) rejects any translation of it (§0.3 item 15) |
| `action_settings` | default + 20 | Renamed `settings`. All 20 values kept verbatim (§1.1) |
| `press_back_again_to_exit` | default + 20 | Deleted (the feature is gone, D-094; `UnusedResources` is fatal under D-142) |
| `toggle_fullscreen` | default + 20 | Deleted (the gesture is gone, D-093). Its nouns seed `setting_immersive` (§1.6) |

The typos listed in research/01 §5.1 (cs, pl, lt) are all in the two deleted keys. The surviving `settings` values need no fix.

Run from the repository root in the first M0 commit, after `legacy-credits.md` exists (verified, §0.3 item 3):

```bash
LOCALES="cs da de el eo es fa fr hu it ko lt nl pl pt ru sl sr tr uk"
for d in values $(printf 'values-%s ' $LOCALES); do
  mkdir -p "app/src/main/res/$d"
  git mv "res/$d/strings.xml" "app/src/main/res/$d/strings.xml"
done
R=app/src/main/res
sed -i '1s/^\xEF\xBB\xBF//' "$R/values-cs/strings.xml"                       # strip the BOM
for f in "$R"/values/strings.xml "$R"/values-*/strings.xml; do
  sed -i -e '/name="press_back_again_to_exit"/d' -e '/name="toggle_fullscreen"/d' \
         -e 's/name="action_settings"/name="settings"/' "$f"
  grep -q '^<?xml ' "$f" || sed -i '1i <?xml version="1.0" encoding="utf-8"?>' "$f"   # values-da
done
for f in "$R"/values-*/strings.xml; do sed -i '/name="app_name"/d' "$f"; done
sed -i 's|<string name="app_name">2048</string>|<string name="app_name" translatable="false">2048</string>|' \
  "$R/values/strings.xml"
```

Check (prints nothing when correct):

```bash
for f in app/src/main/res/values-*/strings.xml; do
  [ "$(grep -c '<string ' "$f")" = 1 ] && grep -q '<string name="settings">' "$f" \
    && [ "$(head -c3 "$f" | od -An -tx1 | tr -d ' ')" = 3c3f78 ] || echo "BAD $f"
done
```

Lint's `ByteOrderMark` does not report a leading BOM (§0.3 item 15), so `check-metadata.sh` gains the M0 function `check_no_bom` (D-179; listing in `QUALITY.md` §1.16): no tracked file starts with a UTF-8 BOM. At M0 the only such file in the tree is the legacy `values-cs/strings.xml`, which the commands above fix.

After M0, `values/strings.xml` holds exactly `app_name` (non-translatable) and `settings`, and each `values-xx/strings.xml` holds exactly `settings`. The M0 placeholder screen uses `R.string.settings`, so `UnusedResources` stays clean (`ARCHITECTURE.md` §1.2).

**Web-game seeds.** `assets/2048/js/i18n.js` disappears with the submodule in M0. Its ru and uk values are already preserved in research/01 §8.1 and research/02 §6, which stay in the repository. They seed these keys in M5:

| Web key | New key | ru | uk | Note |
|---|---|---|---|---|
| `new_game` | `new_game` | Новая игра | Нова гра | |
| `undo` | `undo` | Отменить ход | Скасувати хід | |
| `keep_going` | `keep_going` | Продолжайте | Продовжуйте | |
| `cancel` | `cancel` | Отмена | Скасувати | uk changed from "Відміна" (D-101) |
| `start_a_new_game` | `new_game_confirm_title` | Начать новую игру? | Почати нову гру? | |
| `you_win` | `win_title` | Вы победили! | Ви виграли! | |
| `game_over` | `game_over_title` | Игра закончена | Гра завершена | "!" dropped to match English "Game over" |
| `intro`, `try_again`, `ok`, `undo_the_current_move` | — | — | — | Not used: the hint text differs, the game-over card says "New game", there are no OK buttons, and undo has no confirmation (D-008, D-039) |

### 1.3 v1.0 string inventory

**Totals:** 176 keys in `values/`: 165 translatable (161 `<string>` + 4 `<plurals>`) and 11 `translatable="false"`. Every `values-xx/strings.xml` contains exactly the 165 translatable keys: the legacy `settings` plus 164 new translations, so M5 writes 3,280 translations in all.

Rules:
1. **Complete.** These are all the string resources of v1.0. A key is added only when code uses it, and it is added to this table in the same commit. From M5 on, a new key is added together with its 20 translations.
2. **Used.** Every key is referenced from main code in the commit that adds it, because Lint `UnusedResources` is fatal (D-142). A key whose last call site goes away is deleted from every locale in the same commit.
3. **No fragment concatenation** (D-103). Sentences come from format strings. The two allowed joins: the TalkBack status line joins whole sentences with one space (`FEATURES.md` F17), and the size label `"$n×$n"` is built in code.
4. **Arguments** are positional strings `%1$s`, `%2$s` … filled with pre-formatted text (§1.10). `%d` is never used.
5. **English apostrophes** are escaped as `\'`.
6. **Context comments.** In `values/strings.xml` (and `strings_pending.xml`), each key is preceded by an XML comment holding its context and arguments, exactly as in the tables below. Locale files carry no comments.
7. **Order.** Keys appear in the order of these tables, in every file.
8. **Origin column.** "legacy" = migrated in M0; "web" = seeded for ru/uk (§1.2); "DESIGN_SYSTEM §x" or "FEATURES Fxx" = key and English fixed by that file; "D-NNN" = key fixed by that decision; "added here" = key first named by this file, canonical in every document (`PLAN.md` §2.4). The six "added here" keys exist because a sibling text needed a resource it did not name: `setting_app_language_system` agrees with "language", not with `theme_system`'s "theme" (ru "Системный" vs "Системная"; the same in pl, cs, uk); `a11y_action_change` and `a11y_action_open` are the `onClickLabel`s of choice and link rows; `a11y_chip_value` replaces a concatenation that D-103 forbids; `a11y_best_tile` gives "Best tile, {full value}" a format string; `a11y_board_loading` covers the Loading state, which has no tiles.
9. **M column** = the milestone whose code first uses the key.
10. **Menu and title keys.** `menu_statistics`, `menu_how_to_play` and `menu_about` are separate keys from the top-bar titles `statistics`, `how_to_play` and `about`, as `DESIGN_SYSTEM.md` §6.1 specifies. Their context comments ask for identical wording; merging them later is a pure refactor.

#### A. App-wide and shared

| Key | English (source) | Arguments | Context for translators | Used in | M | Origin |
|---|---|---|---|---|---|---|
| `app_name` ✱ | 2048 | — | Launcher label and the title on the Game screen. Never translated (U2). Lives only in `values/`; the debug source set overrides it with "2048 Debug". | Manifest `android:label`; `GameTitle`; About header | M0 | legacy |
| `settings` | Settings | — | Name of the Settings screen: game-menu item and top-bar title. The 20 legacy translations are kept verbatim (D-101). | `GameMoreMenu`; Settings `TopAppBar`; M0 placeholder | M0 | legacy `action_settings` |
| `cancel` | Cancel | — | Dismiss button of every dialog. Never means "undo". | `ConfirmDialog`, `ChoiceDialog` | M3 | web `cancel` |
| `navigate_up` | Navigate up | — | Spoken label of the back arrow in every top app bar. | Sub-screen `TopAppBar` navigation icon | M4 | DESIGN_SYSTEM §6.8 |
| `more_options` | More options | — | Spoken label of the ⋮ button that opens the game menu. | `GameMoreMenu` icon button | M3 | DESIGN_SYSTEM §6.1 |
| `got_it` | Got it | — | Button that dismisses an informational card or sheet. Short acknowledgement. | `HintCard`; `WhatsNewSheet` | M4 | DESIGN_SYSTEM §6.2, §6.13 |
| `link_open_failed` | No app can open this link | — | Snackbar when no browser or settings app can handle a link. No final period (matches English). | About source link; Settings App language | M4 | DESIGN_SYSTEM §7.6 |
| `a11y_action_change` | Change | — | Spoken action for a settings row that opens a choice dialog. TalkBack says "Double-tap to change". Verb, infinitive or imperative per locale convention. | `SettingsChoiceRow` `onClickLabel` | M4 | added here |
| `a11y_action_open` | Open | — | Spoken action for a row that opens a link, a system page or the licenses screen. TalkBack says "Double-tap to open". | `SettingsLinkRow`; About rows | M4 | added here |

#### B. Game screen: header, controls, menu

| Key | English (source) | Arguments | Context for translators | Used in | M | Origin |
|---|---|---|---|---|---|---|
| `score_label` | Score | — | Label above the current score in the header chip. Code upper-cases it with the current locale; write sentence case. Keep ≤ 10 characters if possible. | `ScoreChip(SCORE)`; game-over card | M3 | web CSS `content:` |
| `best_label` | Best | — | Label above the best score for this board size. Code upper-cases it. Noun or adjective that means "best score / record". ≤ 10 characters. | `ScoreChip(BEST)`; game-over card | M3 | web CSS `content:` |
| `new_best_label` | New best | — | Replaces "Best" on the chip once this game beats the previous best. Code upper-cases it. ≤ 12 characters. | `ScoreChip(BEST)` NEW BEST state | M3 | DESIGN_SYSTEM §7.2 |
| `a11y_chip_value` | %1$s %2$s | %1$s = chip label in sentence case ("Score"); %2$s = value, Latin digits | Spoken text of a score chip, e.g. "Score 10220". Reorder or add punctuation if your language needs it. | `ScoreChip` semantics | M3 | added here |
| `undo` | Undo | — | Button and accessibility action that takes back the last move. Verb (button). Glossary term "Undo (button)". Must differ from "Cancel" where the language allows; see the glossary note for fr/it. | Controls bar; win and game-over cards; TalkBack pad; board custom action; How to play key table | M3 | web `undo` |
| `new_game` | New game | — | Button and accessibility action that starts a new game. Glossary term. | Controls bar; cards; new-game dialog confirm button; board custom action; How to play key table | M3 | web `new_game` |
| `board_size_cd` | Board size %1$s | %1$s = size label built in code, e.g. "4×4" (Latin digits, U+00D7) | Spoken label of the board-size chip, e.g. "Board size 4×4". | `SizeChip` contentDescription | M3 | DESIGN_SYSTEM §7.3 |
| `board_size_change` | Change board size | — | Spoken action of the board-size chip: "Double-tap to change board size". | `SizeChip` onClickLabel | M3 | DESIGN_SYSTEM §7.3 |
| `menu_statistics` | Statistics | — | Game-menu item that opens the Statistics screen. Use the same word as `statistics`. | `GameMoreMenu` | M3 | DESIGN_SYSTEM §6.1 |
| `menu_how_to_play` | How to play | — | Game-menu item that opens the rules screen. Use the same words as `how_to_play`. | `GameMoreMenu` | M3 | DESIGN_SYSTEM §6.1 |
| `menu_about` | About | — | Game-menu item that opens the About screen. Use the same word as `about`. | `GameMoreMenu` | M3 | DESIGN_SYSTEM §6.1 |
| `hint_first_launch` | Swipe to move all tiles. Equal tiles merge. Reach %1$s. | %1$s = target tile as a game numeral: "2048", or "256" on 3×3 | One-time hint on first launch, shown while the starting game has no move (above the board on phones). Three short imperative sentences. | `HintCard` | M4 | DESIGN_SYSTEM §6.2; D-201 |
| `hint_first_launch_talkback` | Use the arrow buttons to move all tiles. Equal tiles merge. Reach %1$s. | %1$s = target tile as a game numeral: "2048", or "256" on 3×3 | The same first-launch hint while TalkBack is on: TalkBack takes over swipes, so the player moves with the arrow buttons of the direction pad. Three short imperative sentences; "arrow buttons" are the four on-screen buttons, not keyboard keys. | `HintCard` (TalkBack variant) | M4 | D-201, D-202 |
| `undo_hint_no_undo_best` | Undo used. This game no longer counts for Best without undo. | — | Snackbar when a game's first undo happens; it stops appearing once a finished game has used undo. "Best without undo" is the statistic name (glossary); write it exactly as in `stats_best_no_undo`, capitalized as a name if your language allows. | Game snackbar | M3 | DESIGN_SYSTEM §7.6; D-186 |
| `nothing_to_undo` | Nothing to undo | — | Snackbar when an undo key is pressed at move 0 (at most once every 2 seconds). No final period. | Game snackbar | M3 | DESIGN_SYSTEM §7.6; D-186 |

#### C. Board-size sheet

| Key | English (source) | Arguments | Context for translators | Used in | M | Origin |
|---|---|---|---|---|---|---|
| `board_size_title` | Board size | — | Title of the sheet that lists 3×3 … 8×8. Glossary term. | `BoardSizeSheet` | M3 | DESIGN_SYSTEM §6.7 |
| `board_size_goal` | Goal %1$s | %1$s = "2048" or "256" (game numeral) | Target tile of a board size, e.g. "Goal 2048". Short. | `BoardSizeSheet` rows; How to play size table | M3 | DESIGN_SYSTEM §6.7 |
| `board_size_best` | Best %1$s | %1$s = best score from `formatCount` (Latin digits, locale grouping) | Best score on that size, e.g. "Best 24,680". | `BoardSizeSheet` rows | M3 | DESIGN_SYSTEM §6.7 |
| `board_size_no_best` | No best score yet | — | Shown instead of the best score when that size has never been scored. | `BoardSizeSheet` rows | M3 | DESIGN_SYSTEM §6.7 |
| `board_size_supporting` ✱ | %1$s · %2$s | %1$s = `board_size_goal`; %2$s = `board_size_best` or `board_size_no_best` | Joins the goal and best parts of a row. Contains no words. | `BoardSizeSheet` rows | M3 | DESIGN_SYSTEM §6.7 |
| `in_progress` | In progress | — | Small pill on a size whose saved game is unfinished. ≤ 14 characters. | `BoardSizeSheet` rows | M3 | DESIGN_SYSTEM §6.7 |

#### D. New-game dialog, win card, game-over card, share

| Key | English (source) | Arguments | Context for translators | Used in | M | Origin |
|---|---|---|---|---|---|---|
| `new_game_confirm_title` | Start a new game? | — | Dialog title shown only when a game is in progress. Buttons are `new_game` and `cancel`. | `NewGameDialog` | M3 | web `start_a_new_game` |
| `win_title` | You win! | — | Title of the win card when the target tile appears. Address the player without grammatical gender. | `WinCard` title and `paneTitle` | M3 | web `you_win` |
| `keep_going` | Keep going | — | Primary button of the win card: continue playing past the target. Glossary term. | `WinCard` | M3 | web `keep_going` |
| `game_over_title` | Game over | — | Title of the card when no move is left. No exclamation mark. | `GameOverCard` title and `paneTitle` | M3 | web `game_over` ("!" dropped) |
| `best_tile` | Best tile | — | Label of the box that shows the highest tile of the finished game. | `GameOverCard` | M3 | DESIGN_SYSTEM §6.4 |
| `a11y_best_tile` | Best tile, %1$s | %1$s = full decimal value, Latin digits | Spoken label of a mini tile that shows the best tile, e.g. "Best tile, 2048". | `GameOverCard`; Statistics hero `MiniTile` | M3 | added here |
| `share` | Share | — | Shares the result as text: a button on the game-over card, and the spoken label of the icon-only share button on the win card. | `GameOverCard`; `WinCard` | M3 | DESIGN_SYSTEM §6.4; D-200 |
| `share_chooser_title` | Share result | — | Title of the Android share sheet. | `Intent.createChooser` | M3 | DESIGN_SYSTEM §6.4 |
| `moves_count` (plurals) | `one`: %1$s move<br>`other`: %1$s moves | %1$s = the count, already formatted (do not use %d) | Number of moves in a game. Quantity = the move count. | Game-over summary (prose number); share text (game numeral) | M3 | DESIGN_SYSTEM §6.4 |
| `undos_count` (plurals) | `one`: %1$s undo<br>`other`: %1$s undos | %1$s = the count, already formatted | Number of undos used in a game. Quantity = the undo count (never 0; see `undos_none`). | Game-over summary; share text | M3 | DESIGN_SYSTEM §6.4 |
| `undos_none` | No undo | — | Shown instead of a count when the game used no undo. | Game-over summary; share text | M3 | DESIGN_SYSTEM §6.4 |
| `game_over_summary` ✱ | %1$s · %2$s · %3$s | %1$s = `moves_count`; %2$s = time "m:ss"; %3$s = `undos_count` or `undos_none` | Joins moves, time and undos. Contains no words. | `GameOverCard` | M3 | DESIGN_SYSTEM §6.4 |
| `share_summary` | 2048 · %1$s · %2$s · best tile %3$s · %4$s · %5$s · %6$s | %1$s = "4×4"; %2$s = score; %3$s = best tile value; %4$s = `moves_count`; %5$s = time; %6$s = `undos_count`/`undos_none`. All Latin digits, no grouping | First line of the shared text. Keep "2048" and every " · " separator. Translate only "best tile". | `ShareText` | M3 | FEATURES F10 |

#### E. Accessibility (TalkBack) and the direction pad

| Key | English (source) | Arguments | Context for translators | Used in | M | Origin |
|---|---|---|---|---|---|---|
| `move_up` | Move up | — | Accessibility action on the board and spoken label of the direction-pad button. Direction is the physical screen direction in every locale (the board never mirrors). | Board custom action; `DirectionPad` | M3 | DESIGN_SYSTEM §6.6 |
| `move_down` | Move down | — | Accessibility action on the board and spoken label of the direction-pad button. Direction is the physical screen direction in every locale (the board never mirrors). | Board custom action; `DirectionPad` | M3 | DESIGN_SYSTEM §6.6 |
| `move_left` | Move left | — | Accessibility action on the board and spoken label of the direction-pad button. Direction is the physical screen direction in every locale (the board never mirrors). | Board custom action; `DirectionPad` | M3 | DESIGN_SYSTEM §6.6 |
| `move_right` | Move right | — | Accessibility action on the board and spoken label of the direction-pad button. Direction is the physical screen direction in every locale (the board never mirrors). | Board custom action; `DirectionPad` | M3 | DESIGN_SYSTEM §6.6 |
| `a11y_board_description` | Game board, %1$s by %1$s. %2$s Highest tile %3$s. | %1$s = N (used twice); %2$s = the `a11y_tiles` sentence; %3$s = highest value | Spoken description of the whole board, e.g. "Game board, 4 by 4. 13 tiles. Highest tile 1024." | Board semantics | M3 | FEATURES F17 |
| `a11y_board_loading` | Game board, %1$s by %1$s. Loading. | %1$s = N (used twice) | Spoken description of the board while the saved game is still loading (empty cells). | Board semantics, Loading state | M3 | added here |
| `a11y_tiles` (plurals) | `one`: %1$s tile.<br>`other`: %1$s tiles. | %1$s = count from `formatCount` | A full sentence counting tiles on the board. Quantity = tile count (≥ 2 in practice). | Inside `a11y_board_description` | M3 | FEATURES F17 |
| `a11y_cell_tile` | Row %1$s, column %2$s, %3$s | %1$s = row (1-based); %2$s = column; %3$s = full tile value, Latin digits | Spoken label of one occupied cell. Rows count from the top, columns from the left, in every locale. | Cell semantics | M3 | FEATURES F17 |
| `a11y_cell_empty` | Row %1$s, column %2$s, empty | %1$s = row; %2$s = column | Spoken label of one empty cell. | Cell semantics | M3 | FEATURES F17 |
| `a11y_moved_up` | Moved up. | — | First sentence of the TalkBack status line after a move. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_moved_down` | Moved down. | — | First sentence of the TalkBack status line after a move. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_moved_left` | Moved left. | — | First sentence of the TalkBack status line after a move. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_moved_right` | Moved right. | — | First sentence of the TalkBack status line after a move. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_merged_1` | Merged %1$s. | %1$s = value of the created tile (prose number) | Status-line sentence when one merge happened. The value is the new tile. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_merged_2` | Merged %1$s and %2$s. | %1$s, %2$s = created tile values | Status-line sentence for two merges, largest first. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_merged_3` | Merged %1$s, %2$s and %3$s. | %1$s–%3$s = created tile values | Status-line sentence for three merges, largest first. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_merged_more` (plurals) | `one`: Merged %1$s, %2$s, %3$s and %4$s more.<br>`other`: Merged %1$s, %2$s, %3$s and %4$s more. | %1$s–%3$s = largest created values; %4$s = number of further merges | Status-line sentence for four or more merges: the three largest, then how many more. Quantity = the extra count in %4$s. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_spawned` | New %1$s at row %2$s, column %3$s. | %1$s = value of the new tile as a game numeral; %2$s, %3$s = 1-based row and column, in the same number format as `a11y_cell_tile` | Status-line sentence after an accepted move: where the new tile appeared. It comes after the merged sentence and before the score, e.g. "Moved left. Merged 64 and 16. New 2 at row 4, column 1. Score 1,240." Rows count from the top, columns from the left, in every locale. | `StatusLine` | M4 | D-202 |
| `a11y_score` | Score %1$s. | %1$s = score from `formatCount` | Last sentence of the status line. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_blocked_up` | Can't move up. | — | Status-line sentence after a swipe that changes nothing. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_blocked_down` | Can't move down. | — | Status-line sentence after a swipe that changes nothing. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_blocked_left` | Can't move left. | — | Status-line sentence after a swipe that changes nothing. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_blocked_right` | Can't move right. | — | Status-line sentence after a swipe that changes nothing. | `StatusLine` | M4 | FEATURES F17 |
| `a11y_undone` | Move undone. Score %1$s. | %1$s = score from `formatCount` | Status line after every undo. | `StatusLine` | M4 | D-202 |
| `a11y_new_game` | New game started. | — | Status line after New game; one `a11y_spawned` sentence per start tile follows it. | `StatusLine` | M4 | D-202 |
| `a11y_size_switched` | Board %1$s. Score %2$s. | %1$s = size label built in code, e.g. "5×5" (Latin digits, U+00D7); %2$s = score from `formatCount` | Status line after the board size changes, e.g. "Board 5×5. Score 0." | `StatusLine` | M4 | D-202 |

#### F. Statistics

| Key | English (source) | Arguments | Context for translators | Used in | M | Origin |
|---|---|---|---|---|---|---|
| `statistics` | Statistics | — | Title of the Statistics screen. | Stats `TopAppBar` | M4 | DESIGN_SYSTEM §6.9 |
| `stats_reset` | Reset statistics | — | Spoken label of the trash icon in the Statistics top bar. | Stats top-bar action | M4 | DESIGN_SYSTEM §6.9 |
| `stats_all_sizes` | All sizes | — | First tab: totals over every board size. Other tabs are "3×3" … "8×8" built in code. | Stats tabs | M4 | DESIGN_SYSTEM §6.9 |
| `stats_best_score` | Best score | — | Label of the hero value. | Stats hero card | M4 | DESIGN_SYSTEM §6.9 |
| `stats_best_tile` | Best tile | — | Label next to the hero mini tile. | Stats hero card | M4 | DESIGN_SYSTEM §6.9 |
| `stats_games_played` | Games played | — | Row label; the value is a plain number. | Stats rows | M4 | DESIGN_SYSTEM §6.9 |
| `stats_wins` | Wins | — | Row label; the value is "count (percent)". | Stats rows | M4 | DESIGN_SYSTEM §6.9 |
| `stats_wins_value` ✱ | %1$s (%2$s) | %1$s = `formatCount(gamesWon, locale)`; %2$s = Latin-digit percent | Win count and win rate, e.g. "9 (24%)". Contains no words. | Stats rows | M4 | DESIGN_SYSTEM §6.9 |
| `stats_best_no_undo` | Best score without undo | — | Row label: best score of games in which undo was never used. This is the statistic called "Best without undo" elsewhere; keep the wording consistent. | Stats rows | M4 | DESIGN_SYSTEM §6.9 |
| `stats_average_score` | Average score | — | Row label. | Stats rows | M4 | DESIGN_SYSTEM §6.9 |
| `stats_total_moves` | Total moves | — | Row label. | Stats rows | M4 | DESIGN_SYSTEM §6.9 |
| `stats_time_played` | Time played | — | Row label; the value is "h:mm:ss". | Stats rows | M4 | DESIGN_SYSTEM §6.9 |
| `stats_undos_used` | Undos used | — | Row label: total undos. | Stats rows | M4 | DESIGN_SYSTEM §6.9 |
| `stats_empty_title` | No games on %1$s yet | %1$s = size label, e.g. "5×5" | Empty state of one size tab. | Stats empty state | M4 | DESIGN_SYSTEM §6.9 |
| `stats_empty_title_all` | No games yet | — | Empty state of the All sizes tab. | Stats empty state | M4 | DESIGN_SYSTEM §6.9 |
| `stats_empty_body` | Finished and abandoned games appear here. | — | Explains when a game is counted. | Stats empty state | M4 | DESIGN_SYSTEM §6.9 |
| `stats_reset_title` | Reset statistics? | — | First reset dialog title. | Reset dialog 1 | M4 | DESIGN_SYSTEM §6.9 |
| `stats_reset_body` | Best scores and statistics for every board size will be cleared. Games in progress are kept. | — | First reset dialog body. | Reset dialog 1 | M4 | DESIGN_SYSTEM §6.9 |
| `reset` | Reset | — | Confirm button of the first reset dialog. Verb. | Reset dialog 1 | M4 | DESIGN_SYSTEM §6.9 |
| `stats_reset_confirm_title` | Reset everything? | — | Second, final reset dialog title. | Reset dialog 2 | M4 | DESIGN_SYSTEM §6.9 |
| `stats_reset_confirm_body` | This can't be undone. | — | Second reset dialog body. | Reset dialog 2 | M4 | DESIGN_SYSTEM §6.9 |
| `stats_reset_confirm_button` | Reset everything | — | Confirm button of the second reset dialog. | Reset dialog 2 | M4 | DESIGN_SYSTEM §6.9 |
| `stats_reset_done` | Statistics reset | — | Snackbar after the reset. Past participle ("have been reset"), no period. | Stats snackbar | M4 | DESIGN_SYSTEM §7.6 |

#### G. Settings

| Key | English (source) | Arguments | Context for translators | Used in | M | Origin |
|---|---|---|---|---|---|---|
| `settings_section_appearance` | Appearance | — | Section header. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_theme` | Theme | — | Row and dialog title: light or dark appearance. In languages with grammatical gender the values below agree with this noun. | Settings; Theme dialog | M4 | DESIGN_SYSTEM §6.8 |
| `theme_system` | System default | — | Theme value: follow the system dark-theme setting. Agrees with `setting_theme`. | Theme dialog and row summary | M4 | DESIGN_SYSTEM §6.8 |
| `theme_light` | Light | — | Theme value. Agrees with `setting_theme`. | Theme dialog | M4 | DESIGN_SYSTEM §6.8 |
| `theme_dark` | Dark | — | Theme value. Agrees with `setting_theme`. | Theme dialog | M4 | DESIGN_SYSTEM §6.8 |
| `setting_palette` | Palette | — | Row and dialog title: the tile color set. | Settings; Palette dialog | M4 | DESIGN_SYSTEM §6.8 |
| `palette_classic` ✱ | Classic Refined | — | Product name of the default palette. | Palette dialog and row summary | M4 | DESIGN_SYSTEM §6.8 |
| `palette_neon` ✱ | Neon Arcade | — | Product name of the second palette. | Palette dialog and row summary | M4 | DESIGN_SYSTEM §6.8 |
| `setting_high_contrast` | High-contrast tiles | — | Switch row. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_high_contrast_summary` | Black or white numbers and tile outlines | — | Supporting line of the high-contrast switch. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_high_contrast_forced` | On because the system contrast setting is high | — | Supporting line while Android's own contrast setting forces the switch on. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_dynamic_color` | Dynamic color | — | Switch row (Android 12+): use wallpaper colors for the interface. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_dynamic_color_summary` | Use wallpaper colors for buttons and panels. Tiles keep their palette. | — | Supporting line of the dynamic-color switch. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `settings_section_motion` | Motion and feedback | — | Section header. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_motion` | Motion | — | Row and dialog title: how much animation the game plays. Values below agree with this noun. | Settings; Motion dialog | M4 | DESIGN_SYSTEM §6.8 |
| `motion_full` | Full | — | Motion value: all animations. | Motion dialog and row summary | M4 | DESIGN_SYSTEM §6.8 |
| `motion_reduced` | Reduced | — | Motion value: calmer animations. | Motion dialog and row summary | M4 | DESIGN_SYSTEM §6.8 |
| `motion_off` | Off | — | Motion value: no animations. | Motion dialog and row summary | M4 | DESIGN_SYSTEM §6.8 |
| `motion_off_system` | Off (system animations are off) | — | Row summary while Android's "Remove animations" is on; the row is disabled. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_haptics` | Haptics | — | Switch row: vibration feedback. Glossary term. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_haptics_summary` | Feedback for merges, wins and blocked moves | — | Supporting line of the haptics switch. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `settings_section_controls` | Controls | — | Section header. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_move_timing` | Move timing | — | Row and dialog title: when a swipe turns into a move. | Settings; dialog | M4 | DESIGN_SYSTEM §6.8 |
| `move_timing_swipe` | As you swipe | — | Value: the move happens during the swipe. | Move timing dialog and row summary | M4 | DESIGN_SYSTEM §6.8 |
| `move_timing_release` | When you lift your finger | — | Value: the move happens when the finger leaves the screen. | Move timing dialog and row summary | M4 | DESIGN_SYSTEM §6.8 |
| `setting_swipe_sensitivity` | Swipe sensitivity | — | Row and dialog title. | Settings; dialog | M4 | DESIGN_SYSTEM §6.8 |
| `sensitivity_low` | Low | — | Value. Agrees with `setting_swipe_sensitivity`. | Sensitivity dialog | M4 | DESIGN_SYSTEM §6.8 |
| `sensitivity_medium` | Medium | — | Value (default). | Sensitivity dialog | M4 | DESIGN_SYSTEM §6.8 |
| `sensitivity_high` | High | — | Value. | Sensitivity dialog | M4 | DESIGN_SYSTEM §6.8 |
| `sensitivity_low_detail` | Needs a longer swipe | — | Supporting line under "Low". | Sensitivity dialog | M4 | DESIGN_SYSTEM §6.8 |
| `sensitivity_medium_detail` | Recommended | — | Supporting line under "Medium". | Sensitivity dialog | M4 | DESIGN_SYSTEM §6.8 |
| `sensitivity_high_detail` | Reacts to short swipes | — | Supporting line under "High". | Sensitivity dialog | M4 | DESIGN_SYSTEM §6.8 |
| `settings_section_display` | Display | — | Section header. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_immersive` | Immersive mode | — | Switch row that hides the status and navigation bars. Seeded from the legacy "full screen" nouns (glossary). | Settings | M4 | DESIGN_SYSTEM §6.8; seeds research/01 §8.1 |
| `setting_immersive_summary` | Hide the status and navigation bars | — | Supporting line of the immersive switch. Use Android's own names for the status bar and navigation bar. | Settings | M4 | D-006 |
| `setting_keep_screen_on` | Keep screen on while playing | — | Switch row. | Settings | M4 | D-006 |
| `setting_keep_screen_on_summary` | Only while a game is in progress | — | Supporting line of the keep-screen-on switch. | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `settings_section_language` | Language | — | Section header (Android 13+ only). | Settings | M4 | DESIGN_SYSTEM §6.8 |
| `setting_app_language` | App language | — | Link row that opens Android's per-app language page. | Settings | M4 | D-102 |
| `setting_app_language_system` | System default | — | Row summary when no app-specific language is chosen. Agrees with the word for "language" (it may differ from `theme_system`, e.g. ru "Системный" vs "Системная"). | Settings App language row | M4 | added here |

#### H. How to play

| Key | English (source) | Arguments | Context for translators | Used in | M | Origin |
|---|---|---|---|---|---|---|
| `how_to_play` | How to play | — | Title of the rules screen. | How to play `TopAppBar` | M4 | DESIGN_SYSTEM §6.10 |
| `htp_goal` | Goal | — | Section heading (noun). | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_goal_body` | Slide the tiles to merge equal numbers and build the %1$s tile. On a 3×3 board the goal is %2$s. | %1$s = "2048"; %2$s = "256" | Keep "3×3" exactly (Latin digits, U+00D7). | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_moving` | Moving | — | Section heading (noun or gerund). | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_moving_body` | Swipe up, down, left or right. Every tile slides as far as it can in that direction. | — | — | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_merging` | Merging | — | Section heading. | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_merging_body` | Two touching tiles with the same number merge into one tile with their sum. A tile merges only once per move. | — | — | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_merging_illustration_cd` | Example: 2, 2, 2, 2 moved left becomes 4, 4 | — | Spoken description of the left-to-right example picture. Keep the digits and their order. | `MergeIllustration` | M4 | DESIGN_SYSTEM §6.10 |
| `htp_new_tiles` | New tiles | — | Section heading. | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_new_tiles_body` | After every move that changes the board, a new tile appears in a random empty cell: a 2 nine times out of ten, otherwise a 4. | — | "2" and "4" are tile values: keep them as Latin digits. "nine times out of ten" is written in words. | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_board_sizes` | Board sizes | — | Section heading. | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_board_sizes_body` | Each size keeps its own game, best score and statistics. | — | — | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_undo` | Undo | — | Section heading (noun; may differ from the `undo` button verb). | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_undo_body` | Undo is unlimited and also works after game over. Undo brings back the same next tile, so repeating a move gives the same result. Undos are counted, and games with undo don't count toward Best without undo. | — | "Best without undo" is the statistic name; match `stats_best_no_undo`. | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_keys` | Keyboard and controllers | — | Section heading for the key table. | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_key_move` | Move | — | Key-table label: the keys that move tiles. Verb. | How to play key table | M4 | DESIGN_SYSTEM §6.10 |
| `htp_key_move_keys` | Arrow keys · W A S D · H J K L · D-pad | — | Translate only "Arrow keys" and "D-pad". Keep the letters, spaces and " · " separators. | How to play key table | M4 | DESIGN_SYSTEM §6.10 |
| `htp_key_undo_keys` | U · Ctrl+Z · Backspace | — | Translate only the key name "Backspace" if your keyboards label it differently; keep "U" and "Ctrl+Z". | How to play key table | M4 | DESIGN_SYSTEM §6.10 |
| `htp_key_new_game_keys` | N · R (asks first while a game is in progress) | — | Keep "N · R"; translate the parenthesis. | How to play key table | M4 | DESIGN_SYSTEM §6.10 |
| `htp_accessibility` ✱ | TalkBack | — | Section heading: Android's screen reader. Product name. | How to play | M4 | DESIGN_SYSTEM §6.10 |
| `htp_accessibility_body` | With TalkBack on, a direction pad appears below the board, and the board offers Move, Undo and New game in the TalkBack actions menu. | — | Keep "TalkBack". Use the same words as `htp_key_move`, `undo` and `new_game` for the three actions. | How to play | M4 | DESIGN_SYSTEM §6.10 |

#### I. About, Licenses, What's new

| Key | English (source) | Arguments | Context for translators | Used in | M | Origin |
|---|---|---|---|---|---|---|
| `about` | About | — | Title of the About screen. | About `TopAppBar` | M4 | DESIGN_SYSTEM §6.11 |
| `about_version` | Version %1$s (%2$s) | %1$s = versionName; %2$s = versionCode (Latin digits) | Version line, e.g. "Version 3.0.0 (30000)". | About header | M4 | DESIGN_SYSTEM §6.11 |
| `about_whats_new` | What's new | — | Section heading listing every release of this app. | About | M4 | DESIGN_SYSTEM §6.11 |
| `about_source_code` | Source code | — | Row that opens the GitHub repository. | About | M4 | DESIGN_SYSTEM §6.11 |
| `about_source_code_url` ✱ | github.com/clayboicardi/2048-android-modern-refresh | — | Supporting text of the source row. | About | M4 | DESIGN_SYSTEM §6.11 |
| `about_privacy` | Privacy | — | Section heading. | About | M4 | DESIGN_SYSTEM §6.11 |
| `privacy_text` | This app collects nothing. It has no network access, no accounts, no ads and no analytics. Game data stays on your device. Android's own backup may copy it to your Google account if you enabled device backup. | — | The privacy policy shown in the app (D-123). Translate faithfully; do not add or remove claims. Keep "Android" and "Google". | About | M4 | D-123 |
| `about_licenses` | Open-source licenses | — | Row that opens the licenses screen. | About | M4 | DESIGN_SYSTEM §6.11 |
| `about_credits` | Credits | — | Section heading; its rows are English names. | About | M4 | DESIGN_SYSTEM §6.11 |
| `about_legacy_history` | Legacy history | — | Section heading: release history of the original Android app (shown in English). | About | M4 | DESIGN_SYSTEM §6.11 |
| `licenses` | Open-source licenses | — | Title of the licenses screen. Same words as `about_licenses`. | Licenses `TopAppBar` | M4 | DESIGN_SYSTEM §6.12 |
| `license_mit_name` ✱ | MIT License | — | Official license name. | Licenses group header | M4 | DESIGN_SYSTEM §6.12 |
| `license_ofl_name` ✱ | SIL Open Font License 1.1 | — | Official license name. | Licenses group header | M4 | DESIGN_SYSTEM §6.12 |
| `license_apache_name` ✱ | Apache License 2.0 | — | Official license name. | Licenses group header | M4 | DESIGN_SYSTEM §6.12 |
| `license_show_text` | Show license text | — | Toggle row under a license group. | Licenses | M4 | DESIGN_SYSTEM §6.12 |
| `license_hide_text` | Hide license text | — | Same toggle while expanded. | Licenses | M4 | DESIGN_SYSTEM §6.12 |
| `whats_new_title` | What's new | — | Title of the sheet shown once after an update. | `WhatsNewSheet` | M4 | DESIGN_SYSTEM §6.13 |
| `whats_new_version` | Version %1$s | %1$s = versionName, e.g. "3.0.0" | Subtitle of the sheet. | `WhatsNewSheet` | M4 | DESIGN_SYSTEM §6.13 |
| `whatsnew_30000_1` | Rebuilt from scratch as a fast native app | — | Bullet in the 3.0.0 change list. No final period. | `Changelog.kt` → sheet and About | M4 | DESIGN_SYSTEM §6.13 |
| `whatsnew_30000_2` | Board sizes from 3×3 to 8×8, each with its own best score | — | Bullet. Keep "3×3" and "8×8". | `Changelog.kt` | M4 | DESIGN_SYSTEM §6.13 |
| `whatsnew_30000_3` | Unlimited undo, even after game over | — | Bullet. | `Changelog.kt` | M4 | DESIGN_SYSTEM §6.13 |
| `whatsnew_30000_4` | Light, dark, Neon Arcade and high-contrast themes | — | Bullet. "Neon Arcade" is a product name; keep it in Latin letters. | `Changelog.kt` | M4 | DESIGN_SYSTEM §6.13 |
| `whatsnew_30000_5` | Statistics, keyboard controls and TalkBack support | — | Bullet. Keep "TalkBack". | `Changelog.kt` | M4 | DESIGN_SYSTEM §6.13 |

**Format of `values/strings.xml`** (generated from the tables; excerpt):

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/values/strings.xml: English source (L10N_STORE_LEGAL.md §1.3). Comments are translator context. -->
<resources>
    <!-- … -->
    <!-- Launcher label and the title on the Game screen. Never translated (U2). Lives only in `values/`; the debug source set overrides it with "2048 Debug". -->
    <string name="app_name" translatable="false">2048</string>
    <!-- … -->
    <!-- Name of the Settings screen: game-menu item and top-bar title. The 20 legacy translations are kept verbatim (D-101). -->
    <string name="settings">Settings</string>
    <!-- … -->
    <!-- Spoken text of a score chip, e.g. "Score 10220". Reorder or add punctuation if your language needs it. Args: %1$s = chip label in sentence case ("Score"); %2$s = value, Latin digits -->
    <string name="a11y_chip_value">%1$s %2$s</string>
    <!-- … -->
    <!-- Number of moves in a game. Quantity = the move count. Args: %1$s = the count, already formatted (do not use %d) -->
    <plurals name="moves_count">
        <item quantity="one">%1$s move</item>
        <item quantity="other">%1$s moves</item>
    </plurals>
    <!-- … -->
    <!-- Joins the goal and best parts of a row. Contains no words. Args: %1$s = `board_size_goal`; %2$s = `board_size_best` or `board_size_no_best` -->
    <string name="board_size_supporting" translatable="false">%1$s · %2$s</string>
    <!-- … -->
    <!-- Status-line sentence after a swipe that changes nothing. -->
    <string name="a11y_blocked_up">Can\'t move up.</string>
    <!-- … -->
</resources>
```

**Visible text that is not a string resource:**

| Text | Where | Why |
|---|---|---|
| "3×3" … "8×8" | Size chip, sheet rows, Statistics tabs, How to play table | Built in code as `"$n×$n"`, Latin digits, U+00D7 (`DESIGN_SYSTEM.md` §3.7) |
| Tile labels, scores, "+N", "2048"/"256" arguments | Game | Game numerals, `Long.toString()` (D-103) |
| "–" | Loading chips; average score with 0 games | Typographic placeholder (`DESIGN_SYSTEM.md` §6.1, §6.9) |
| "m:ss", "h:mm:ss" | Game-over card, Statistics, share text | `formatElapsed`, `Locale.ROOT` (§1.10) |
| "3.0.0", "2.2 (25)" | About | `BuildConfig.VERSION_NAME` (compile-time, D-134); legacy data |
| Credits and legacy history rows | About | English static data in `res/raw/legacy_history.json` (D-124, §4.5) |
| License entry names, copyright lines and texts | Licenses | `res/raw/` data (D-122, §4.4) |
| Share-text emoji | Share | Code table (`FEATURES.md` F10) |

### 1.4 When strings land: `strings_pending.xml` (M3–M4) and the M5 switch

**Why.** D-142 makes `MissingTranslation` an error from M0, and D-163 requires a passing `scripts/verify.sh` before every push. D-162, however, schedules the translations in M5, while M3 and M4 add 164 English strings. A string added in M3 without its 20 translations would fail `:app:lintDebug`.

**Rule** (D-208; D-142 allows a `tools:ignore` with a same-line reason):
1. In M3 and M4, every new English key goes into `app/src/main/res/values/strings_pending.xml`, never into `strings.xml`. The integrator pre-creates one region per work package in it (`PLAN.md` §6.5):

   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <!-- Temporary, M3–M4 only: English strings awaiting their M5 translations (L10N_STORE_LEGAL.md §1.4). -->
   <resources xmlns:tools="http://schemas.android.com/tools"
       tools:ignore="MissingTranslation"><!-- reason: translated in M5 (D-162); check-metadata.sh asserts this file is gone from M5 on -->

       <!-- context comment from §1.3 -->
       <string name="cancel">Cancel</string>
   </resources>
   ```

   Lint 32.4.1 honors `tools:ignore` on `<resources>`: 0 errors with it and 158 without it (verified, §0.3 item 15). If a later Lint stops honoring it, put `tools:ignore="MissingTranslation"` on each element of this file instead, each with the same same-line reason comment.
2. Nothing else is suppressed. `ExtraTranslation`, `MissingQuantity`, `UnusedQuantity`, `UnusedResources` and the `StringFormat*` checks stay errors throughout.
3. M0–M2 add no string beyond the migrated `settings`.
4. In M5, the commit that adds the 20 translations also moves every entry of `strings_pending.xml` into `strings.xml` (in §1.3 order) and deletes `strings_pending.xml`. The same commit adds `check_strings_final` to `check-metadata.sh` (`QUALITY.md` §1.16, D-190), which fails while the file exists. From then on, a string arrives with all 20 translations in the same commit.

**Rejected:** translating each string in the commit that adds it. That spreads translation over M3–M4, before the glossary exists (D-105) and before the UI copy is final, and every later copy edit then costs 21 edits.

### 1.5 Plurals (D-103)

Rules:
1. Each locale defines **exactly** the CLDR cardinal categories of §1.1: no more (Lint `UnusedQuantity`) and no fewer (`MissingQuantity`).
2. **Lint decides.** Lint 32.4.1 requires exactly the §1.1 sets. It reports `MissingQuantity` for a missing `many` in cs, es, fr, it, lt and pt (verified, §0.3 item 15), even though `many` is never selected for whole numbers in cs and lt. If a future Lint disagrees with the table, follow its message, update §1.1 in the same commit, and note it in the PR body.
3. **Every item contains its placeholder.** In ru, uk, pl, cs, lt, sl and sr the `one` category also matches 21, 31, 101 …, so an item such as "одна плитка" without the number is wrong (Lint `ImpliedQuantity`). In fa, hu, tr and ko a noun after a number stays singular, so `one` and `other` read alike; both still carry `%1$s`.
4. **Call shape:** `pluralStringResource(R.plurals.moves_count, count, formatCount(count.toLong(), locale))`. The quantity and the displayed number are the same value. The share text passes `count.toString()` (a game numeral) instead of `formatCount`.
5. English never defines `zero`. `undos_none` covers 0 undos.
6. `a11y_merged_more` takes its quantity from the `%4$s` count.
7. There is no `games_count` plural: no v1.0 sentence counts games, and an unused `<plurals>` would fail `UnusedResources` (D-208). If a later string counts games, it uses a plural.
8. The `many` item that Lint requires in cs, es, fr, it, lt and pt (D-208) applies there only to fractions (cs, lt) or exact millions (es, fr, it, pt), so it rarely or never displays. It must still be grammatical: use the genitive or "de"/"di" form the locale needs, e.g. fr "%1$s de coups".

Which integers fall into each category (CLDR; "fractions" = never selected for whole numbers):

| Locale | one | two | few | many | other |
|---|---|---|---|---|---|
| en, da, de, el, eo, hu, nl, tr | 1 | — | — | — | everything else |
| fa | 0, 1 | — | — | — | everything else |
| fr | 0, 1 | — | — | 1,000,000, 2,000,000 … | everything else |
| pt (pt-BR rules) | 0, 1 | — | — | exact millions | everything else (pt-PT: `one` is 1 only) |
| es, it | 1 | — | — | exact millions | everything else |
| cs | 1 | — | 2–4 | fractions | 0, 5+ |
| lt | 1, 21, 31 … (not 11) | — | 2–9, 22–29 … (not 12–19) | fractions | 0, 10–20, 30, 40 … |
| pl | 1 | — | 2–4, 22–24 … (not 12–14) | 0, 5–21, 25–31 … | fractions |
| ru, uk | 1, 21, 31 … (not 11) | — | 2–4, 22–24 … (not 12–14) | 0, 5–20, 25–30 … | fractions |
| sl | 1, 101, 201 … | 2, 102 … | 3, 4, 103, 104 … | — | 0, 5–100 … |
| sr | 1, 21, 31 … (not 11) | — | 2–4, 22–24 … (not 12–14) | — | 0, 5–20 … |
| ko | — | — | — | — | everything |

### 1.6 Glossary (seed of `docs/modernization/i18n-glossary.md`, D-105)

The first M5 commit creates `docs/modernization/i18n-glossary.md` as a verbatim copy of this section, before any translation exists (D-105). Translations use exactly these terms. A term changes only by editing the glossary and every string that uses it, in one commit.

**Never translate:**
- "2048", every game numeral, and "3×3" … "8×8" (Latin digits and U+00D7).
- "TalkBack", "Android", "Google", "GitHub", "Ctrl", "Ctrl+Z", and the key letters W, A, S, D, H, J, K, L, U, Z, N, R. Languages that inflect foreign names may decline them ("Androida"), keeping the base word.
- "Classic Refined" and "Neon Arcade" (product names; inside a translated sentence they stay in Latin letters).
- License names, URLs and the package name.

The key names "Arrow keys", "Backspace" and "D-pad" may be translated or kept, whichever the language's keyboards and controllers use.

**A. Pieces and actions**

| Locale | tile | move (noun) | merge (verb) | Undo (button) | undos (counted) | Score (label) |
|---|---|---|---|---|---|---|
| en | tile | move | merge | Undo | undos | Score |
| cs | dlaždice | tah | spojit se | Vrátit tah | vrácení tahu | Skóre |
| da | brik | træk | smelte sammen | Fortryd | fortrydelser | Point |
| de | Kachel | Zug | verschmelzen | Rückgängig | Rücknahmen | Punkte |
| el | πλακίδιο | κίνηση | συγχωνεύομαι | Αναίρεση | αναιρέσεις | Σκορ |
| eo | kahelo | movo | kunfandiĝi | Malfari | malfaroj | Poentoj |
| es | ficha | movimiento | combinarse | Deshacer | movimientos deshechos | Puntos |
| fa | کاشی | حرکت | ادغام شدن | واگرد | واگرد (singular after numbers) | امتیاز |
| fr | tuile | coup | fusionner | Annuler | annulations | Score |
| hu | lapka | lépés | összeolvad | Visszavonás | visszavonás (singular after numbers) | Pontszám |
| it | tessera | mossa | unirsi | Annulla | annullamenti | Punteggio |
| ko | 타일 | 이동 | 합쳐지다 | 되돌리기 | 되돌리기 N회 | 점수 |
| lt | plytelė | ėjimas | susijungti | Atšaukti ėjimą | atšaukimai | Taškai |
| nl | tegel | zet | samenvoegen | Ongedaan maken | keer ongedaan gemaakt | Score |
| pl | kafelek | ruch | łączyć się | Cofnij | cofnięcia | Wynik |
| pt | peça | jogada | juntar-se | Desfazer | jogadas desfeitas | Pontuação |
| ru | плитка | ход | объединяться | Отменить ход | отмены хода | Счёт |
| sl | ploščica | poteza | združiti se | Razveljavi | razveljavitve | Rezultat |
| sr | плочица | потез | спојити се | Опозови | опозивања | Резултат |
| tr | karo | hamle | birleşmek | Geri al | geri alma (singular after numbers) | Puan |
| uk | плитка | хід | об’єднуватися | Скасувати хід | скасування | Рахунок |

**B. Records and game flow**

| Locale | Best (label) | New best | Best without undo | New game | Keep going | Game over | You win! |
|---|---|---|---|---|---|---|---|
| en | Best | New best | Best without undo | New game | Keep going | Game over | You win! |
| cs | Nejlepší | Nový rekord | Nejlepší bez vracení | Nová hra | Hrát dál | Konec hry | Vyhráli jste! |
| da | Bedste | Ny rekord | Bedste uden fortryd | Nyt spil | Fortsæt | Spillet er slut | Du vandt! |
| de | Rekord | Neuer Rekord | Rekord ohne Rücknahme | Neues Spiel | Weiterspielen | Spiel vorbei | Gewonnen! |
| el | Ρεκόρ | Νέο ρεκόρ | Ρεκόρ χωρίς αναίρεση | Νέο παιχνίδι | Συνέχεια | Τέλος παιχνιδιού | Κερδίσατε! |
| eo | Rekordo | Nova rekordo | Rekordo sen malfaro | Nova ludo | Daŭrigi | Ludo finiĝis | Vi venkis! |
| es | Récord | Nuevo récord | Récord sin deshacer | Nueva partida | Seguir jugando | Fin de la partida | ¡Has ganado! |
| fa | رکورد | رکورد جدید | رکورد بدون واگرد | بازی جدید | ادامه بازی | پایان بازی | برنده شدید! |
| fr | Record | Nouveau record | Record sans annulation | Nouvelle partie | Continuer | Partie terminée | Vous avez gagné ! |
| hu | Rekord | Új rekord | Rekord visszavonás nélkül | Új játék | Folytatás | Vége a játéknak | Győzelem! |
| it | Record | Nuovo record | Record senza annullamenti | Nuova partita | Continua | Partita finita | Hai vinto! |
| ko | 최고 | 신기록 | 되돌리기 없는 최고 점수 | 새 게임 | 계속하기 | 게임 오버 | 승리! |
| lt | Rekordas | Naujas rekordas | Rekordas be atšaukimų | Naujas žaidimas | Tęsti | Žaidimas baigtas | Laimėjote! |
| nl | Record | Nieuw record | Record zonder ongedaan maken | Nieuw spel | Doorspelen | Spel voorbij | Je hebt gewonnen! |
| pl | Rekord | Nowy rekord | Rekord bez cofania | Nowa gra | Graj dalej | Koniec gry | Wygrana! |
| pt | Recorde | Novo recorde | Recorde sem desfazer | Novo jogo | Continuar | Fim de jogo | Ganhou! |
| ru | Рекорд | Новый рекорд | Рекорд без отмен | Новая игра | Продолжайте | Игра закончена | Вы победили! |
| sl | Rekord | Nov rekord | Rekord brez razveljavitev | Nova igra | Nadaljuj | Konec igre | Zmagali ste! |
| sr | Рекорд | Нови рекорд | Рекорд без опозивања | Нова игра | Настави | Крај игре | Победили сте! |
| tr | Rekor | Yeni rekor | Geri almasız rekor | Yeni oyun | Devam et | Oyun bitti | Kazandınız! |
| uk | Рекорд | Новий рекорд | Рекорд без скасувань | Нова гра | Продовжуйте | Гра завершена | Ви виграли! |

**C. Board and settings**

| Locale | board | Board size | Immersive mode (seed) | Haptics | swipe (instruction) |
|---|---|---|---|---|---|
| en | board | Board size | Immersive mode | Haptics | Swipe |
| cs | hrací plocha | Velikost plochy | Celá obrazovka | Haptická odezva | Přejeďte prstem |
| da | bræt | Brætstørrelse | Fuld skærm | Haptisk feedback | Stryg |
| de | Spielfeld | Spielfeldgröße | Vollbild | Haptisches Feedback | Wische |
| el | ταμπλό | Μέγεθος ταμπλό | Πλήρης οθόνη | Απτική απόκριση | Σύρετε |
| eo | tabulo | Grandeco de la tabulo | Tutekrana reĝimo | Haptika reago | Ŝovu |
| es | tablero | Tamaño del tablero | Pantalla completa | Respuesta háptica | Desliza |
| fa | صفحهٔ بازی | اندازهٔ صفحه | تمام‌صفحه | بازخورد لمسی | بکشید |
| fr | grille | Taille de la grille | Plein écran | Retour haptique | Balayez |
| hu | tábla | Táblaméret | Teljes képernyő | Haptikus visszajelzés | Csúsztassa |
| it | griglia | Dimensione griglia | Schermo intero | Feedback aptico | Scorri |
| ko | 보드 | 보드 크기 | 전체 화면 | 햅틱 피드백 | 스와이프하세요 |
| lt | lenta | Lentos dydis | Visas ekranas | Haptinis atsakas | Braukite |
| nl | bord | Bordgrootte | Volledig scherm | Haptische feedback | Veeg |
| pl | plansza | Rozmiar planszy | Pełny ekran | Reakcja haptyczna | Przesuń |
| pt | tabuleiro | Tamanho do tabuleiro | Tela cheia | Resposta tátil | Deslize |
| ru | поле | Размер поля | Полноэкранный режим | Тактильный отклик | Проведите |
| sl | plošča | Velikost plošče | Celozaslonski način | Haptični odziv | Povlecite |
| sr | табла | Величина табле | Цео екран | Хаптички одзив | Превуците |
| tr | tahta | Tahta boyutu | Tam ekran | Dokunsal geri bildirim | Kaydırın |
| uk | поле | Розмір поля | Повноекранний режим | Тактильний відгук | Проведіть |

Notes:
- **Immersive mode** values are the research/01 §8.1 seeds (D-101), except fa, which adds the zero-width non-joiner U+200C ("تمام‌صفحه"). The seeds were normalized by research and are accepted as glossary terms.
- **ru and uk** keep the legacy web wording for Undo, New game, Keep going, Game over and You win! (§1.2).
- **fr and it:** Android uses "Annuler"/"Annulla" for both Undo and Cancel. They never share a dialog here, so both follow the platform. The How to play heading `htp_undo` uses the noun ("Annulation", "Annullamento").
- **Best without undo** is the name of a statistic. Write it identically in `stats_best_no_undo`, `undo_hint_no_undo_best` and `htp_undo_body` (inflected only where grammar forces it).
- **pt** is shared by Brazil and Portugal (§1.7). "Tela cheia" is the legacy Brazilian seed; the pt-PT store listing uses "ecrã" in its own text (§2.4).

### 1.7 Style rules

General rules for every locale:
1. Translate the meaning, not the words. Add or drop nothing. Keep every placeholder, digit and never-translate token.
2. Sentence case for labels, titles, buttons and menu items. German capitalizes nouns.
3. Punctuation parity with English: a translation ends with a period exactly when the English does. Each language's own question and exclamation marks apply (el `;`, fa `؟`, es `¿…?`/`¡…!`, fr no-break space).
4. Address the player in the register of §1.1, and never with a gendered form. Prefer the formal plural, an impersonal form or a noun ("Wygrana!" rather than "Wygrałeś/aś!").
5. A setting value agrees in gender and number with its setting noun. The context comment names the noun (`theme_*` with `setting_theme`, `motion_*` with `setting_motion`, `sensitivity_*` with `setting_swipe_sensitivity`, `setting_app_language_system` with "language").
6. Use Android's own terms for system UI: status bar, navigation bar, the "Remove animations" accessibility setting, TalkBack.
7. Length: `score_label`, `best_label`, `new_best_label` and `in_progress` stay at most 12 characters. Buttons and titles aim at no more than 1.3× the English length; longer text may wrap to two lines (D-084), and goldens 24 (`de`) and 25 (`en-XA`) show the result.
8. Escaping: write `\'` for an ASCII apostrophe, or use U+2019 ’. Never leave `'` or `"` unescaped (aapt2 rejects an unescaped apostrophe). Write no-break spaces as ` ` so reviewers can see them.
9. No HTML or `<xliff:g>` tags. Arguments are positional `%n$s` and may be reordered.
10. Latin digits only (0–9) in every string. No Persian or Arabic-Indic digits (D-103).

Per locale:

| Locale | Address | Quotes | Specific rules |
|---|---|---|---|
| cs | vykání: "Přejeďte", "Vyhráli jste" | „…“ | |
| da | du | »…« | |
| de | du (lowercase "du"/"dein") | „…“ | "Vollbild", not "Vollbildschirm"; avoid anglicisms other than "Feedback" |
| el | formal plural: "Σύρετε" | «…» | The question mark is `;`. Chip labels are upper-cased in code; check that "ΡΕΚΟΡ" shows without a stress mark (L-OI-13) |
| eo | vi | „…“ | Write ĉ ĝ ĥ ĵ ŝ ŭ; never the x- or h-system |
| es | tú | «…» | `¿…?` and `¡…!`; neutral Spanish (no vosotros); "partida" for one game |
| fa | شما | «…» | Persian ی (U+06CC) and ک (U+06A9), never Arabic ي (U+064A) or ك (U+0643); ZWNJ U+200C in compounds ("می‌شود", "تمام‌صفحه"); `؟` and `،`; Latin digits; isolate mis-ordered Latin runs (§1.11) |
| fr | vous | « … » with ` ` inside | ` ` before `?`, `!`, `:` and `;`; apostrophe ’ |
| hu | Ön (implied): "Csúsztassa" | „…” | A noun after a number stays singular |
| it | tu | «…» | Apostrophe ’ |
| ko | polite instructions (-세요); nouns for buttons | "…" | No plural forms; counter "회" for times |
| lt | jūs: "Braukite" | „…“ | |
| nl | je | "…" | |
| pl | 2nd-person imperative: "Przesuń" | „…” | No gendered past tense for the player |
| pt | neutral imperative: "Deslize" | "…" | Prefer wording valid in pt-BR and pt-PT. When forced to choose, use Brazilian Portuguese (legacy precedent: "Configurações", "Tela cheia"). Infinitive over gerund ("Continuar", not "Continuar jogando") |
| ru | вы, lowercase | «…» | Use ё where standard ("Счёт"); legacy seeds kept |
| sl | vikanje: "Povlecite" | „…“ | The `two` plural category is required |
| sr | ви | „…“ | Cyrillic only, with ђ ј љ њ ћ џ; never й щ ъ ы ь э ю я ё є і ї ґ |
| tr | formal: "Kaydırın" | "…" | A noun after a number stays singular; keep dotted and dotless i exact |
| uk | ви, lowercase | «…» | Apostrophe U+2019; і ї є ґ; never ы э ъ ё; legacy seeds kept (`cancel` = "Скасувати") |

### 1.8 Translation workflow (M5)

Nobody fluent reviews these translations before merge (U7 is hands-off), so the milestone integrator and the translation work packages (`PLAN.md` §5.5) follow this procedure, and README says the translations are machine-assisted (D-105).

1. **Freeze.** M4 is complete, and every English string in `strings_pending.xml` is final.
2. **Glossary commit.** Commit `docs/modernization/i18n-glossary.md`, a copy of §1.6, with the subject `M5: add i18n glossary`. Nothing is translated yet (D-105).
3. **Translate.** In the next commit, write each `values-xx/strings.xml`: XML declaration, `<resources>`, the legacy `settings` line unchanged, then the other 164 translatable keys in §1.3 order, without comments. Do de, ru and fa first: de has the longest strings, ru has four plural categories, and fa is RTL, so problems show up early. Seeds: `settings` (all locales), the seven web keys (ru, uk) and the immersive nouns (§1.6).
4. **Merge pending.** The same commit merges `strings_pending.xml` into `strings.xml` and deletes it (§1.4).
5. **Self-check each locale** against: placeholders; plural categories (§1.5); glossary terms; register and agreement (§1.7); never-translate tokens; script rules (§1.9); chip label length.
6. **Back-translation sample.** For every locale, translate these 8 keys back into English and compare the meaning with the source: `undo`, `keep_going`, `new_game_confirm_title`, `hint_first_launch`, `move_timing_release`, `undo_hint_no_undo_best`, `stats_reset_body`, `privacy_text`. Fix any mismatch before handing off. The integrator hands the 160 rows (8 keys × 20 locales) to the orchestrator, which puts them in a collapsed `<details>` table in the PR body (D-194).
7. **Gates** (locally under the build lock, `QUALITY.md` §3.1): `./gradlew :app:lintDebug`, then `:app:testDebugUnitTest --tests '*LocalizationTest' --tests '*TranslationQualityTest' --tests '*FormattingTest'`, then the full `scripts/verify.sh`.
8. **Goldens.** Record goldens 23–26 (`fa`, `de`, `en-XA`, `ar-XB`) with `-Proborazzi.test.record=true`. Read every PNG and fix clipped, overflowing or mis-ordered text. List the PNGs in the commit body (D-147, D-164).
9. **Store texts:** translate the store listing (§2.4) in the same commit (`PLAN.md` §5.5).
10. **Commit body:** the locales done, any plural-category change made under §1.5 rule 2, and the goldens.

### 1.9 Automated checks

Android Lint (all fatal under D-142 `warningsAsErrors`; "verified" marks issues exercised in §0.3 item 15):

| Lint issue | What it catches here |
|---|---|
| `MissingTranslation` | A translatable key missing in a locale (made an error by D-142) |
| `ExtraTranslation` | A locale key absent from `values/` (verified) |
| `Untranslatable` | A translation of a `translatable="false"` key such as `app_name` (a warning, fatal under D-142; verified) |
| `MissingQuantity` / `UnusedQuantity` | Plural categories missing for a locale, or present but unused by it (§1.5; `MissingQuantity` verified) |
| `ImpliedQuantity` | A `one` or `two` item without the number, in languages where those categories cover more numbers (verified) |
| `StringFormatMatches`, `StringFormatCount`, `StringFormatInvalid` | Placeholders that differ between locales or are malformed |
| `ByteOrderMark` | A BOM inside a file. It does **not** report a leading BOM (verified), hence the §1.2 assertion |
| `Typos` | Lint's built-in misspelling lists for several of the shipped languages |
| `PluralsCandidate` | An English string with a counted noun that should be a `<plurals>` |
| `UnusedResources` | A key that no code references |
| `TypographyEllipsis`, `TypographyDashes` | `...` instead of `…`, and number ranges written with `-` |

**`TranslationQualityTest`** (Robolectric, SDK 36; `app/src/test/kotlin/com/clayboicardi/game2048/TranslationQualityTest.kt`; M5; the class KDoc's first line is `/** L-AC4 */`).
- For each of the 20 locale tags, it builds `context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })`.
- It enumerates `R.string` and `R.plurals` by reflection, skips the 11 keys of `NON_TRANSLATABLE` (the ✱ rows of §1.3), and compares each value with the English value from an `en` context.
- Allowed Latin tokens: TalkBack, Android, Google, GitHub, Ctrl, Backspace, D-pad, Neon Arcade, Classic Refined, the single letters W A S D H J K L U Z N R, and `%n$s`.

| Method | Rule |
|---|---|
| `everyKeyResolvesNonBlankInEveryLocale` | Every translatable string and plural resolves to non-blank text in all 20 locales |
| `placeholdersMatchEnglish` | The multiset of `%\d+\$s` placeholders equals the English one, for strings and for every plural quantity |
| `noNonLatinDigits` | No character in U+0660–U+0669 or U+06F0–U+06F9 in any value |
| `neverTranslateTokensKept` | If the English value contains "2048", "3×3", "8×8", "TalkBack", "Android", "Google", "Ctrl+Z", "Neon Arcade", "W A S D", "H J K L" or "N · R", the translation contains it too |
| `scriptMatchesLocale` | After removing placeholders, allowed tokens, digits and punctuation, every letter belongs to the locale's script: Cyrillic (ru, uk, sr), Greek (el), Arabic block (fa), Hangul (ko), Latin incl. U+00C0–U+024F (others). Non-Latin locales need at least one letter of their script in every value that has any letter left |
| `alphabetRulesPerLocale` | ru has none of і ї є ґ; uk has none of ы э ъ ё; sr has none of й щ ъ ы ь э ю я ё є і ї ґ; fa has neither ي (U+064A) nor ك (U+0643); eo matches no `(?i)[cghjsu]x` |
| `notIdenticalToEnglish` | No translated value equals the English value, except pairs listed in `app/src/test/resources/i18n/identical-to-english.txt` (lines `<locale or *> <key>`, each with a `#` reason). Seed lines: `fr score_label`, `nl score_label`, `de setting_palette`, `fr setting_palette`, `* htp_key_undo_keys` |
| `localeHasDistinctiveLetters` | For Latin locales, the concatenated values contain at least one of: cs ěščřžýůďťň, da æøå, de äöüß, eo ĉĝĥĵŝŭ, es ñáéíóú¿¡, fr éèêàçùœ, hu őűáéíóöú, it àèéìòù, lt ąčęėįšųūž, pl ąćęłńóśźż, pt ãõçáéêó, sl čšž, tr ğışçöüİ (nl is covered by `notIdenticalToEnglish`) |
| `pluralsCarryTheNumber` | For every plural and n ∈ {0, 1, 2, 3, 4, 5, 11, 12, 21, 22, 25, 101, 102, 1000000}, `getQuantityString(id, n, "#N#")` contains `#N#` |

`FEATURES.md`'s `LocalizationTest` keeps its methods (F24-AC8, F28-AC2…AC6). The two classes do not overlap.

### 1.10 Digits and number formatting (D-103)

| Kind | Helper (`ui/components/Formatting.kt`, M3) | en | de | fa |
|---|---|---|---|---|
| Game numeral (tiles, score, best, "+N", share text) | `Long.toString()` | 24680 | 24680 | 24680 |
| Prose number (sheet "Best", Statistics, game-over summary, status line) | `formatCount(n, locale)` | 24,680 | 24.680 | Latin digits, fa grouping ("24,680" on the JVM test runtime) |
| Duration | `formatElapsed(ms)` | 6:41 / 1:02:05 | same | same |
| Percent | `formatPercent(part, whole, locale)` | 24% | 24 % (U+00A0) | Latin digits ("24%") |

```kotlin
// ui/components/Formatting.kt
fun latnLocale(locale: Locale): Locale =
    Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()

/** Maps U+0660–U+0669 and U+06F0–U+06F9 to ASCII digits, in case a runtime ignores "nu-latn". */
fun String.toLatinDigits(): String = buildString(length) {
    for (c in this@toLatinDigits) append(
        when (c) {
            in '٠'..'٩' -> '0' + (c - '٠')
            in '۰'..'۹' -> '0' + (c - '۰')
            else -> c
        }
    )
}

fun formatCount(n: Long, locale: Locale): String =
    NumberFormat.getIntegerInstance(latnLocale(locale)).format(n).toLatinDigits()

fun formatPercent(part: Long, whole: Long, locale: Locale): String =
    NumberFormat.getPercentInstance(latnLocale(locale))
        .format(if (whole == 0L) 0.0 else part.toDouble() / whole).toLatinDigits()

fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.ROOT, "%d:%02d", m, sec)
}
```

- Canonical signatures, in `ui/components/Formatting.kt`, created in M3 (D-211): `formatCount(n: Long, locale: Locale)`, `formatPercent(part: Long, whole: Long, locale: Locale)`, `formatElapsed(ms: Long)` and `String.toLatinDigits()`, plus the helper `latnLocale(locale)`.
- This code compiled under `allWarningsAsErrors` and produced the values above under Robolectric (§0.3 item 14).
- `toLatinDigits()` adds no behavior where `nu-latn` already works. It guarantees D-103 on API 24–25 runtimes, which the JVM tests cannot observe.
- Tests: `FormattingTest` F28-AC7…AC9 (M3), plus the method `FormattingTest.toLatinDigitsMapsArabicIndicAndPersian` (L-AC5, added in M5). `LocalizationTest.gameNumeralsAsciiInPersian` (F28-AC6, M5).

### 1.11 RTL: `fa` and the forced-LTR board (D-104)

- `android:supportsRtl="true"` (`ARCHITECTURE.md` §8.1). The chrome mirrors.
- Forced LTR through `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`: the board subtree including cell semantics order, the gesture detector and its direction mapping, the key map, the TalkBack move actions, the direction pad, and the How to play merge illustration. In every locale a physical left swipe is `Direction.LEFT`, and row 1 / column 1 in the cell labels are the top-left cell.
- Icons: `ic_arrow_back`, `ic_chevron_right`, `ic_open_in_new`, `ic_help` and `ic_undo` auto-mirror; `ic_pad_*` do not (`DESIGN_SYSTEM.md` §8.1).
- **Mixed-direction text.** fa strings contain Latin runs ("TalkBack", "Ctrl+Z", "3×3") and receive Latin-digit arguments. The Unicode bidi algorithm orders them correctly in almost all cases, and numbers and "4×4" read the same in either direction. If a golden or an agent Read shows a Latin run out of order, wrap that run inside the fa string in first-strong isolates, U+2066 … U+2069, written as `⁦…⁩`. Never scatter U+200E/U+200F marks.
- **Goldens:** 23 `game_mid-4x4_classic-light_fa` and 26 `game_mid-4x4_classic-light_ar-XB` (`DESIGN_SYSTEM.md` §10.4). On Read, check:
  - title and score chips mirrored; controls bar order [⋮][New game][Undo];
  - the board identical to the LTR golden 3;
  - all numerals Latin;
  - Persian text neither clipped nor overlapping;
  - the size chip reading "4×4".
- Tests: F17-AC13 (direction pad), F23-AC6 (illustration), F28-AC6, F28-AC10, F28-AC11.

### 1.12 Pseudo-locales (D-106)

- `buildTypes.debug { isPseudoLocalesEnabled = true }` (`ARCHITECTURE.md` §2.6). It compiled and produced `en-XA` and `ar-XB` in the architecture scratch build (`ARCHITECTURE.md` §0). If it fails later, D-106's fallback applies: drop it and goldens 25–26, and record this in the PR body.
- Goldens set the locale with `RuntimeEnvironment.setQualifiers("+en-rXA")` or `"+ar-rXB"` before composing.
- **Hard-coded text detector.** Pseudo-localization rewrites every translatable resource, but code literals stay plain. When Reading golden 25 (`settings_classic-light_en-XA`), every visible word must be bracketed and accented, except: ✱ keys ("2048", the palette names), game numerals, size labels and "–". Any plain English word is a hard-coded string: move it into §1.3 and translate it.
- **Expansion.** `en-XA` lengthens text by roughly a third. Nothing may clip; labels wrap to at most 2 lines (D-084).
- **Bidi.** In golden 26 (`ar-XB`), the chrome is mirrored, the board is not, and numerals are Latin.
- Pseudo-locales exist only in debug builds. They are not in the release APK, its locale config or the store listing (§1.13).

### 1.13 Locale config and per-app language (D-102)

- `app/src/main/res/resources.properties` contains exactly `unqualifiedResLocale=en-US` (smoke-verified, research/03 §6).
- `androidResources { generateLocaleConfig = true }` (`ARCHITECTURE.md` §2.6). AGP generates `res/xml/_generated_res_locale_config.xml` with `defaultLocale="en-US"` and one `<locale>` per resource locale, and injects `android:localeConfig` into the merged manifest. Do not write either by hand. The release APK lists exactly the 21 locales en-US, cs, da, de, el, eo, es, fa, fr, hu, it, ko, lt, nl, pl, pt, ru, sl, sr, tr, uk (F28-AC3). In the debug unit-test variant the generated config lists the 21 locales plus the D-106 pseudo-locales en-XA and ar-XB.
- **Settings row "App language"** (API 33+ only; hidden below):

```kotlin
// settings/AppLanguage.kt
@RequiresApi(33)
fun openAppLanguageSettings(context: Context, onFailure: () -> Unit) {
    val intent = Intent(
        Settings.ACTION_APP_LOCALE_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onFailure() // snackbar R.string.link_open_failed
    }
}

@RequiresApi(33)
fun appLanguageSummary(context: Context): String {
    val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
    if (locales.isEmpty) return context.getString(R.string.setting_app_language_system)
    val l = locales[0]
    return l.getDisplayName(l).replaceFirstChar { if (it.isLowerCase()) it.titlecase(l) else it.toString() }
}
```

- Both functions compiled, and `appLanguageSummary` returned "System default" under Robolectric SDK 36 (§0.3 item 14).
- Both are `@RequiresApi(33)`: `Settings.ACTION_APP_LOCALE_SETTINGS` is an API 33 constant that the compiler inlines, so Lint `InlinedApi` (fatal under D-142) flags any call outside an API 33 guard. Every caller sits inside the `Build.VERSION.SDK_INT >= 33` branch that also shows the row.
- Below API 33 the app follows the system language. There is no in-app picker and no `androidx.appcompat:appcompat` (D-102). The `appcompat-resources` artifact that `core-splashscreen` pulls in (§0.3 item 7) contains no `AppCompatDelegate` and does not change this (D-208).
- A language change recreates the activity, and the game restores as `Static` (`FEATURES.md` F07).
- Tests: F11-AC4, F11-AC5, F11-AC6, F28-AC3, plus `SettingsScreenTest.appLanguageSummaryDefaultsToSystem` (L-AC6, M4).

### 1.14 Acceptance criteria (localization)

| AC | Given | When | Then | Test or gate |
|---|---|---|---|---|
| L-AC1 | The M0 commit | The §1.2 check runs | It prints nothing; `values/strings.xml` holds exactly `app_name` (✱) and `settings` | Gate: `lintDebug` (`Untranslatable`, `UnusedResources`; technical correction TC-3); check-metadata.sh `check_no_bom` (D-179); F28-AC2, F28-AC4 |
| L-AC2 | Any commit from M5 on | `scripts/check-metadata.sh` | `app/src/main/res/values/strings_pending.xml` does not exist | Gate: check-metadata.sh `check_strings_final` (D-190) |
| L-AC3 | All 21 locale files after M5 | `:app:lintDebug` | No `MissingTranslation`, `ExtraTranslation`, `Untranslatable`, `MissingQuantity`, `UnusedQuantity`, `ImpliedQuantity` or `StringFormat*` issue | Gate: lintDebug (F28-AC1) |
| L-AC4 | All 20 translations | `TranslationQualityTest` runs | All 9 methods of §1.9 pass | `TranslationQualityTest.*` |
| L-AC5 | The strings "١٢٣" (U+0661–U+0663) and "۲۰۴۸" | `toLatinDigits()` | "123" and "2048"; ASCII input is returned unchanged | `FormattingTest.toLatinDigitsMapsArabicIndicAndPersian` |
| L-AC6 | SDK 36, no per-app locale set | Settings renders | The App language row reads `setting_app_language_system` ("System default") | `SettingsScreenTest.appLanguageSummaryDefaultsToSystem` |
| L-AC7 | Golden 25 (`en-XA`) recorded | The agent Reads it | No plain English word outside the §1.12 exceptions | Inspection, listed in the M5 commit body |

---

## 2. Store listing

### 2.1 Tree and legacy disposition (D-113)

Final tree (exactly 22 locale directories):

```text
fastlane/metadata/android/
├── en-US/
│   ├── title.txt
│   ├── short_description.txt
│   ├── full_description.txt
│   ├── changelogs/30000.txt
│   └── images/
│       ├── icon.png
│       ├── featureGraphic.png
│       ├── phoneScreenshots/1.png  2.png  3.png  4.png
│       ├── sevenInchScreenshots/1.png
│       └── tenInchScreenshots/1.png
└── cs da de el eo es fa fr hu it ko lt nl pl pt-BR pt-PT ru sl sr tr uk/
    ├── title.txt  short_description.txt  full_description.txt
    └── changelogs/30000.txt
```

Disposition of the 29 legacy `metadata/` directories (research/01 §6; DECISIONS A.1 C7):

| Group | Locales | When | What happens |
|---|---|---|---|
| Kept, every text rewritten | de, el, en-US, es, fr, it, ko, lt, pt-BR, pt-PT, ru, tr, uk (13) | M0 move, M5 rewrite | The legacy `short_description.txt` (machine-translated "port") is replaced; `title.txt`, `full_description.txt` and `changelogs/30000.txt` are added |
| Added | cs, da, eo, fa, hu, nl, pl, sl, sr (9) | M5 | Created with the four files |
| Deleted | af, am, ar, bg, ca, et, fi, hi, id, ja, lv, ro, sv, sw, th, vi (16) | M0 | Removed: the app has no strings in these languages, and their texts are mistranslations |

M0 commands (verified, §0.3 item 3). Deletions come first, because `git rm` refuses paths with a staged rename:

```bash
git rm -r -q metadata/{af,am,ar,bg,ca,et,fi,hi,id,ja,lv,ro,sv,sw,th,vi} metadata/en-US/promoGraphic.png
mkdir -p fastlane/metadata
git mv metadata fastlane/metadata/android
A=fastlane/metadata/android/en-US
mkdir -p "$A/images"
git mv "$A/icon.png" "$A/featureGraphic.png" "$A/phoneScreenshots" "$A/sevenInchScreenshots" "$A/images/"
```

In M5, `StoreAssetsTest` overwrites `icon.png` and `featureGraphic.png`. The legacy `images/phoneScreenshots/screen1.png` and `images/sevenInchScreenshots/screen_tablet.png` are `git rm`ed, and the new files of §2.5 are added. The image checks (`check_store_images`, §2.6) are active only from that M5 commit (D-152 phase-in, D-190), because the legacy feature graphic has an alpha channel.

### 2.2 File format rules

- UTF-8 without BOM, LF line endings, exactly one trailing newline, no trailing spaces.
- Limits count Unicode code points of the content, without the final newline:
  - `title.txt` ≤ 30 (Play's app-name limit; F-Droid allows 50);
  - `short_description.txt` ≤ 80;
  - `full_description.txt` ≤ 4,000;
  - `changelogs/30000.txt` ≤ 500.
- `title.txt` is `2048` in all 22 directories (U2: the name is not translated).
- Plain text only: no HTML and no Markdown. Bullets are "• " (U+2022 + space); paragraphs are separated by one blank line; headings are plain lines.
- The changelog file name is the literal versionCode, `30000.txt`, without padding, in every locale.
- Latin digits only. Never `com.uberspot.a2048`. Every full description contains `com.clayboicardi.game2048` and the repository URL.
- No `video.txt`, no `promoGraphic.png`, and no per-locale `images/`. F-Droid and fastlane fall back to the en-US graphics.

### 2.3 en-US source copy (D-114)

`title.txt` (4 characters):

```text
2048
```

`short_description.txt` (63 characters; D-114 verbatim):

```text
Classic 2048 sliding-tile puzzle. Offline, no ads, no tracking.
```

`full_description.txt` (2,295 characters, leaving room for longer languages):

```text
2048 is the classic sliding-tile puzzle, rebuilt from scratch as a fast native Android app. Swipe to move every tile at once. Two tiles with the same number merge into one. Reach the 2048 tile, then keep going as far as you can.

Private by design
• Works completely offline: the app has no internet access.
• No ads, no tracking, no analytics and no accounts.
• No runtime permissions. Your games and statistics stay on your device.

Play your way
• Board sizes from 3×3 to 8×8. Each size keeps its own saved game, best score and statistics. On 3×3 the goal is 256.
• Unlimited undo, even after game over. Undo brings back the same next tile, so it fixes slips without changing your luck. Undos are counted, and a separate best score tracks games played without undo.
• Your game is saved after every move and continues exactly where you left it.
• Statistics for every board size: games played, wins, best tile, average score, total moves, time played and undos used.

Look and feel
• Two palettes, Classic Refined and Neon Arcade, in light and dark themes, plus a high-contrast option.
• Smooth animations with Full, Reduced and Off settings. Turning off animations in Android's settings turns them off here too.
• Optional haptic feedback, adjustable swipe sensitivity and move timing, and an immersive mode that hides the system bars.
• Layouts for phones, tablets, foldables, landscape and desktop windows.

Accessibility
• Full TalkBack support: every cell can be read, each move is announced, and a direction pad appears while TalkBack is on.
• Play with a keyboard, D-pad or game controller: arrow keys, WASD or HJKL to move; U, Ctrl+Z or Backspace to undo.
• Text that stays readable at large font sizes, and tile colors checked for contrast and color-vision deficiencies.

A new app
This is a new app with a new package name (com.clayboicardi.game2048); the old app can stay installed side by side; scores do not carry over.

Open source
Free and open source under the MIT License: https://github.com/clayboicardi/2048-android-modern-refresh

Credits
The original 2048 game is by Gabriele Cirulli. The original 2048 Android app is by Paul Sarbinowski (uberspot) and its contributors and translators. This version's translations are machine-assisted; corrections are welcome on GitHub.
```

`changelogs/30000.txt` (345 characters, short enough for a 45 % longer translation):

```text
3.0.0 is a full rewrite as a native app with a new package name. It installs next to the original 2048 app; scores from the original app do not carry over.
• Board sizes 3×3 to 8×8
• Unlimited undo, even after game over
• Neon Arcade, dark and high-contrast themes
• Statistics, keyboard and TalkBack support
• Still offline: no ads, no tracking
```

D-114 coverage of the full description:

| D-114 requirement | Where |
|---|---|
| Offline; no ads; no tracking; no runtime permissions | "Private by design" bullets 1–3 |
| Board sizes 3×3–8×8 | "Play your way" bullet 1 |
| Unlimited undo | "Play your way" bullet 2 |
| Themes and accessibility | "Look and feel"; "Accessibility" |
| MIT, with the repository link | "Open source" |
| Credits | "Credits" |
| Side-by-side note (D-005, verbatim) | "A new app" |
| Contains none of "port", "WebView", "long touch", uberspot store links | Checked by `check_store_listing` (§2.6) |

The changelog says plainly that 3.0.0 is a fresh start: a new package, a separate install, and no scores carried over (D-005).

### 2.4 Translating the listing (M5)

- Translate short, full and changelog texts for the 21 other directories with the glossary (§1.6) and style rules (§1.7). `title.txt` stays `2048`.
- **pt-BR** uses Brazilian Portuguese and **pt-PT** European Portuguese ("ecrã", "telemóvel", "descarregar"). The two texts differ; the app's own strings stay the shared generic `pt` (§1.7).
- Keep verbatim: the package name, the repository URL, "2048", "3×3", "8×8", "TalkBack", "Classic Refined", "Neon Arcade", "WASD", "HJKL", "Ctrl+Z". "MIT License" may be written as the language's usual form ("MIT-Lizenz"), keeping "MIT".
- **Over a limit?** Shorten in this order:
  - full description: drop "Look and feel" bullet 4, then bullet 3;
  - changelog: drop the last bullet, then the one before it;
  - short description: drop the leading adjective.

  Never drop the offline / no ads / no tracking statement, the side-by-side note, or the license and repository line.
- `check_store_listing` (§2.6) fails on any non-en-US text identical to en-US, on a missing package name or URL, on non-Latin digits, on missing native script in ru, uk, sr, el, fa and ko, and on a D-114 deny-word in any locale.

### 2.5 Graphics produced from code (D-115)

| File | Rendered by | Qualifier → pixels | Content | PNG type |
|---|---|---|---|---|
| `icon.png` | `StoreAssetsTest.icon` (bitmap draw, §3.7) | — → 512×512 | Adaptive icon, full square, no mask | 2 (`OpaquePngWriter`; the check also accepts 6) |
| `featureGraphic.png` | `StoreAssetsTest.featureGraphic` | `w512dp-h250dp-xhdpi` → 1024×500 | `FeatureGraphic()` composable, `FIX_FEATURE` (`DESIGN_SYSTEM.md` §8.5) | **2** (no alpha) |
| `phoneScreenshots/1.png` | `StoreAssetsTest.phone1` | `w360dp-h640dp-xxhdpi` → 1080×1920 | `FIX_MID_4`, Classic light | 2 |
| `phoneScreenshots/2.png` | `StoreAssetsTest.phone2` | same | `FIX_WON_4`, win card | 2 |
| `phoneScreenshots/3.png` | `StoreAssetsTest.phone3` | same | `FIX_MID_4`, Neon dark | 2 |
| `phoneScreenshots/4.png` | `StoreAssetsTest.phone4` | same | Statistics, 4×4 tab, `FIX_RECORDS` | 2 |
| `sevenInchScreenshots/1.png` | `StoreAssetsTest.sevenInch` | `w1067dp-h600dp-land-xhdpi` → 2134×1200 | `FIX_MID_4`, Classic light, side panel | 2 |
| `tenInchScreenshots/1.png` | `StoreAssetsTest.tenInch` | `w1280dp-h720dp-land-xhdpi` → 2560×1440 | `FIX_MID_4`, Neon dark, side panel | 2 |

**`StoreAssetsTest`** (`app/src/test/kotlin/com/clayboicardi/game2048/store/`):
- Excluded from every normal run. Run it with `./gradlew :app:testDebugUnitTest -PrecordStoreAssets=true --tests 'com.clayboicardi.game2048.store.*'` (`ARCHITECTURE.md` §2.6, §2.11). The output directory is `System.getProperty("game2048.storeAssetsDir")`.
- `@RunWith(AndroidJUnit4::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, SDK 36, one test method per file.
- Conditions: motion Off, dynamic color off, locale en-US, `onboardingDone = true`, `lastSeenVersionCode = 30000`, the bundled font, fixtures from `DESIGN_SYSTEM.md` §10.3.
- Composable captures run inside `Box(Modifier.requiredSize(widthDp, heightDp).testTag("store:frame"))`. The capture size is therefore exact whatever the window decor. Each test captures with `composeRule.onNodeWithTag("store:frame").captureToImage().asAndroidBitmap()` and passes the bitmap to `OpaquePngWriter.write(bitmap, background, file)` (below), with `background` = the frame's opaque background color.
- The pixel size comes from the density of the test's `@Config(qualifiers = …)`: `w360dp-h640dp-xxhdpi` → 1080×1920, and likewise for the other rows of the table.
- Every store PNG, the icon included (§3.7), is written by `OpaquePngWriter`: an 8-bit PNG of color type 2, which Play requires for the feature graphic and screenshots ("JPEG or 24-bit PNG, no alpha"). There is no Roborazzi API, no opt-in, no temporary PNG and no `BitmapFactory.decodeFile` (D-190). The file names stay those of D-115, and the images stay lossless.

```kotlin
// app/src/test/kotlin/com/clayboicardi/game2048/store/OpaquePngWriter.kt
package com.clayboicardi.game2048.store

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Writes [source] composited over [background] as an 8-bit RGB PNG (IHDR color type 2, no alpha). */
internal object OpaquePngWriter {
    fun write(source: Bitmap, background: Int, file: File) {
        val w = source.width
        val h = source.height
        val opaque = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        opaque.eraseColor(background)
        Canvas(opaque).drawBitmap(source, 0f, 0f, null)
        val argb = IntArray(w * h)
        opaque.getPixels(argb, 0, w, 0, 0, w, h)
        val raw = ByteArray(h * (1 + 3 * w))
        var i = 0
        for (y in 0 until h) {
            raw[i++] = 0 // filter type: None
            for (x in 0 until w) {
                val p = argb[y * w + x]
                raw[i++] = (p shr 16).toByte()
                raw[i++] = (p shr 8).toByte()
                raw[i++] = p.toByte()
            }
        }
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val idat =
            try {
                ByteArrayOutputStream()
                    .also { bytes -> DeflaterOutputStream(bytes, deflater).use { it.write(raw) } }
                    .toByteArray()
            } finally {
                deflater.end()
            }
        val ihdr =
            ByteBuffer.allocate(13)
                .putInt(w)
                .putInt(h)
                .put(8.toByte()) // bit depth
                .put(2.toByte()) // color type: RGB
                .put(0.toByte()) // compression
                .put(0.toByte()) // filter method
                .put(0.toByte()) // interlace: none
                .array()
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            fun chunk(type: String, data: ByteArray) {
                val t = type.toByteArray(Charsets.US_ASCII)
                out.writeInt(data.size)
                out.write(t)
                out.write(data)
                out.writeInt(CRC32().apply { update(t); update(data) }.value.toInt())
            }
            chunk("IHDR", ihdr)
            chunk("IDAT", idat)
            chunk("IEND", ByteArray(0))
        }
    }
}
```

This code compiled under `allWarningsAsErrors` and ran under Robolectric (§0.3 item 14). Its output had IHDR color type 2 and decoded back with the expected pixels, including a half-transparent pixel correctly composited over the background. A Python port of the same algorithm produced files that `file` identifies as "PNG image data, 1080 x 1920, 8-bit/color RGB".

**After generation:** the milestone integrator Reads all 8 images. It confirms that no status bar, debug label or placeholder text is visible, and that the win card, the Neon palette and the statistics are legible. Then it commits the images and lists them in the commit body. A second run must reproduce byte-identical files (`git diff --exit-code fastlane/`), because the fixtures are static and motion is Off (L-AC9). The images are not Roborazzi goldens and CI does not compare them; `check_store_images` (§2.6) checks the file set, sizes, bit depth and color types.

### 2.6 `check-metadata.sh`: the M5 listing, image and legal block

The M5 listing, image and legal assertions are implemented in `QUALITY.md` §1.16 (`check_store_listing`, `check_store_images`, `check_legal_and_privacy`, `check_legal_texts`, `check_strings_final`; D-190). That listing is the only one to edit. Each function is added in the M5 commit that adds the content it checks (D-152 phase-in). They assert:

- **`check_store_listing`** (F30-AC8):
  - exactly the 22 locale directories of D-113, and no `images/` directory outside en-US;
  - `title.txt` is exactly `2048` and at most 30 characters (Google Play's title limit);
  - short description ≤ 80, full description ≤ 4,000 and `changelogs/30000.txt` ≤ 500 characters, counted as code points without the final newline;
  - no BOM or CR, and exactly one trailing newline;
  - every non-en-US short description, full description and changelog differs from en-US;
  - every full description contains `com.clayboicardi.game2048` and `https://github.com/clayboicardi/2048-android-modern-refresh`;
  - the ru, uk, sr, el, fa and ko texts contain letters of their script;
  - no non-Latin digits (U+0660–U+0669, U+06F0–U+06F9);
  - none of D-114's deny-words (`\bport\b`, `WebView`, `long touch`, case-insensitive) in any locale, and no `com.uberspot.a2048`.
- **`check_store_images`** (F30-AC9): exactly the D-115 image set under `en-US/images/` (so no `promoGraphic.png`); every file an 8-bit PNG at its exact pixel size; color type 2 for every screenshot and `featureGraphic.png`, 2 or 6 for `icon.png`.
- **`check_legal_and_privacy`** (F26-AC5, F27-AC5): the `NOTICE` items and lineage line of §4.3 (the Google Sans Flex item only when the font ships, D-076); the D-123 text in `PRIVACY.md`; the README links to `PRIVACY.md` and `NOTICE`; the self-permission name in README and `PRIVACY.md`.
- **`check_legal_texts`** (F26-AC5, L-AC13): `LICENSE` has sha256 `ad96d837d59c50cb3c8e23a17d7ee19a396c5a1a6c4bc952a269896fcbdbfd19` (§4.2); `app/src/main/res/raw/license_mit.txt` is byte-identical to `LICENSE`; `app/src/main/res/raw/license_apache_2_0.txt` has sha256 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` (§0.3 item 9).
- **`check_strings_final`** (L-AC2): `app/src/main/res/values/strings_pending.xml` is absent (§1.4, D-208).

The listing and image logic was run on a synthetic 22-locale tree (§0.3 item 11).

### 2.7 F-Droid compatibility

No F-Droid submission is part of this overhaul (D-004 item 10; D-133 forbids tags). The repository is kept ready for one.

| F-Droid expectation | v1.0 status | Evidence |
|---|---|---|
| Free licenses only | MIT, OFL-1.1 and Apache-2.0 only | §4.1; `check-licenses.sh` (D-122) |
| No proprietary or tracking libraries (GMS, Firebase, Play Core, crash SDKs) | Enforced | D-148 deny-list |
| No anti-features (Ads, Tracking, NonFreeNet, NonFreeDep, NonFreeAssets) | None expected | D-002; the font is OFL and the icons Apache-2.0 |
| No encrypted dependency-info block | `dependenciesInfo { includeInApk = false; includeInBundle = false }` | D-134 |
| Metadata in-repo | `fastlane/metadata/android/<locale>/`, with `title.txt`, `changelogs/<versionCode>.txt` and `images/` | §2.1, research/01 §6 |
| Parseable versions | `versionCode = 30000` and `versionName = "3.0.0"` as literals | D-130 |
| Reproducible-build hygiene | One JDK (21); no timestamps; BuildConfig with AGP's standard fields only (no `buildConfigField`); `vcsInfo` default; R8 from AGP 9.4.1. Watch `assets/dexopt/baseline.prof` and resource-shrinker order (research/06 §4.3) | D-134 |

Items for a later submission (not done now):
1. A `v3.0.0` tag on the release commit, created when the owner decides on releases (D-133).
2. **Signing.** F-Droid signs with its own key, so its APK cannot update a CI sideload install, and the reverse is also true; users switch by uninstalling. The recipe strips the release signing line in `prebuild`, e.g. `sed -i '/signingConfig = signingConfigs.getByName("ciSideload")/d' app/build.gradle.kts`, which leaves debug untouched because the recipe builds release only. Reproducible builds with a developer signature would need the real key of §5.7, never the public sideload key.
3. `signing/ci-sideload.p12` is a committed binary. If `fdroid scanner` flags it, the recipe lists it under `scandelete`.
4. The listing shows androidx.core's signature permission under "Permissions"; the "no runtime permissions" wording (§4.8) stays accurate.

### 2.8 Acceptance criteria (store listing)

| AC | Given | When | Then | Test or gate |
|---|---|---|---|---|
| L-AC8 | `fastlane/metadata/android/` after M5 | `check-metadata.sh` | The §2.6 listing and image checks pass: 22 directories, limits, newline rules, title "2048", translated texts, needles, scripts, digits, deny-words, image set, sizes, bit depth and PNG color types | Gate: check-metadata.sh `check_store_listing`, `check_store_images` (D-152, D-190; F30-AC8, F30-AC9) |
| L-AC9 | A clean tree after M5 | `StoreAssetsTest` runs twice | `git diff --exit-code fastlane/` is clean after the second run | Inspection, recorded in the M5 commit body |
| L-AC10 | The M0 commit | `ls fastlane/metadata/android \| wc -l`; `test ! -e metadata` | 13; `metadata/` is gone; `en-US/images/` holds the moved legacy images; no `promoGraphic.png` | Inspection in the M0 commit body (`ARCHITECTURE.md` §1.3) |

---

## 3. App identity assets

### 3.1 Identity colors

| Resource | Value | Use | Contrast |
|---|---|---|---|
| `values/colors.xml` `ic_launcher_background` | `#E6A11A` | Adaptive background; splash icon disc; About icon disc; store icon | Classic 2048 tile fill (D-070) |
| `values/colors.xml` `ic_launcher_glyph` | `#3A2900` | Glyph fill | 6.33:1 on `#E6A11A` (§0.3 item 5) |
| `values/colors.xml` `window_background` | `#FAF8EF` | Splash and window background, light | Disc vs background 2.08:1; the disc is decorative, and the glyph carries the contrast |
| `values-night/colors.xml` `window_background` | `#1B1916` | Same, dark | Disc vs background 7.92:1 |

### 3.2 Launcher icon foreground (exact)

`app/src/main/res/drawable/ic_launcher_foreground.xml`. The four `pathData` values are copied verbatim from the legacy `res/drawable/ic_2048.xml` in M0, before that file is deleted (D-111). The legacy stroke attributes are dropped: the stroke color was fully transparent.

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- "2048" glyph paths from the legacy res/drawable/ic_2048.xml (uberspot/2048-android, MIT, see NOTICE item 2). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="1.411"
        android:scaleY="1.411"
        android:translateX="16.667"
        android:translateY="16.667">
        <path
            android:fillColor="@color/ic_launcher_glyph"
            android:pathData="m6.6917,32.8316l8.7099,0l0,-2.6097l-2.6265,0c-0.5601,0 -1.4098,0.0803 -2.0471,0.1405 2.0664,-2.0877 4.0749,-4.1554 4.0749,-6.4439 0,-2.4491 -1.6802,-4.0751 -4.1135,-4.0751 -1.7381,0 -2.8775,0.7026 -4.0749,2.1078l1.545,1.6059c0.5987,-0.6625 1.2746,-1.2647 2.1244,-1.2647 1.0236,0 1.6609,0.6223 1.6609,1.8268 0,1.7665 -2.2402,4.0349 -5.253,6.8052z" />
        <path
            android:fillColor="@color/ic_launcher_glyph"
            android:pathData="m21.3885,33.0725c2.6651,0 4.4419,-2.3286 4.4419,-6.6647 0,-4.3361 -1.7767,-6.5643 -4.4419,-6.5643 -2.6651,0 -4.4419,2.2082 -4.4419,6.5643 0,4.3361 1.7767,6.6647 4.4419,6.6647zM21.3885,30.6836c-0.9077,0 -1.6222,-0.8431 -1.6222,-4.2758 0,-3.4528 0.7146,-4.1554 1.6222,-4.1554 0.8884,0 1.6222,0.7026 1.6222,4.1554 0,3.4327 -0.7339,4.2758 -1.6222,4.2758z" />
        <path
            android:fillColor="@color/ic_launcher_glyph"
            android:pathData="m29.7508,27.4918 l1.4484,-2.5896c0.3476,-0.7428 0.6952,-1.4654 1.0042,-2.2082l0.0773,0c-0.0386,0.8431 -0.0966,2.0877 -0.0966,2.9108l0,1.887zM32.1841,32.8316l2.781,0l0,-2.971l1.3905,0l0,-2.3688l-1.3905,0l0,-7.4074l-3.6887,0l-4.3646,7.6082l0,2.168l5.2723,0z" />
        <path
            android:fillColor="@color/ic_launcher_glyph"
            android:pathData="m41.9176,33.0725c2.6265,0 4.3839,-1.5257 4.3839,-3.5532 0,-1.6863 -0.9463,-2.6699 -2.1244,-3.3324l0,-0.0802c0.8497,-0.5822 1.6029,-1.5457 1.6029,-2.7502 0,-2.1279 -1.5064,-3.513 -3.7659,-3.513 -2.3175,0 -3.9784,1.3651 -3.9784,3.5532 0,1.3249 0.6759,2.2684 1.6416,3.0111l0,0.0803c-1.1587,0.6424 -2.0664,1.5658 -2.0664,3.0914 0,2.0677 1.8347,3.4929 4.3067,3.4929zM42.6708,25.3639c-1.2167,-0.5019 -2.0085,-0.9836 -2.0085,-1.9673 0,-0.9435 0.5794,-1.4052 1.2746,-1.4052 0.8884,0 1.4098,0.6223 1.4098,1.5859 0,0.6223 -0.2317,1.2045 -0.6759,1.7866zM41.9755,30.9245c-0.9656,0 -1.7961,-0.6223 -1.7961,-1.7063 0,-0.7428 0.3669,-1.4454 0.8497,-1.9271 1.5064,0.6625 2.4913,1.0639 2.4913,2.1279 0,1.0439 -0.6759,1.5056 -1.545,1.5056z" />
    </group>
</vector>
```

`ARCHITECTURE.md` §8.4 uses the `@color/ic_launcher_glyph` reference and `DESIGN_SYSTEM.md` §8.2 writes the literal `#3A2900`; both are the same color, and this file follows ARCHITECTURE.

### 3.3 Adaptive icon

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` are identical:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Themed icons (API 33+) use only the monochrome layer's alpha, which gives the glyph silhouette. Lint `MonochromeLauncherIcon` is satisfied.

### 3.4 API 24–25 fallback icons

API 24–25 launchers do not mask icons, so the fallback draws its own shape. Both files are 48 dp vectors over the same 108-unit viewport:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/mipmap-anydpi/ic_launcher.xml (API 24–25; API 26+ uses mipmap-anydpi-v26) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@color/ic_launcher_background"
        android:pathData="M24,6h60a18,18 0 0 1 18,18v60a18,18 0 0 1 -18,18h-60a18,18 0 0 1 -18,-18v-60a18,18 0 0 1 18,-18z" />
    <group
        android:scaleX="1.411"
        android:scaleY="1.411"
        android:translateX="16.667"
        android:translateY="16.667">
        <!-- the same four <path> elements as ic_launcher_foreground.xml, copied verbatim -->
    </group>
</vector>
```

`ic_launcher_round.xml` is the same file with the background path `M54,6a48,48 0 1 1 0,96a48,48 0 1 1 0,-96z` (a 96-unit circle). Background paths start with an upper-case `M` and glyph paths with a lower-case `m`, so a check can tell them apart. Each glyph path appears in three files, and the M0 function `check_icon_glyphs` (D-179; listing in `QUALITY.md` §1.16) fails if they ever diverge or if the foreground does not hold exactly 4 glyph paths.

The fallback vectors reuse the adaptive glyph group (`DESIGN_SYSTEM.md` §8.2, `ARCHITECTURE.md` §8.4), so on unmasked API 24–25 launchers the glyph spans 58 % of the shape, versus 78 % of the visible adaptive icon. This is cosmetic, limited to two old API levels, and accepted; a scale of about 1.9 would match if it is ever wanted.

### 3.5 Geometry checks

| Surface | Mapping | Glyph band | Constraint |
|---|---|---|---|
| Adaptive layer (108 dp) | scale 1.411, translate 16.667 | 56.0 × 18.67 dp, centered at (54, 54), diagonal 59.0 dp | Inside the 66 dp safe circle |
| Visible adaptive icon (72 dp crop) | — | 78 % of the icon width | Launcher mask |
| Legacy fallback (48 dp, 96-unit shape) | same group | 58 % of the shape width | Accepted (§3.4) |
| Splash icon with background (240 dp) | 2.222 dp per unit | 124.4 × 41.5 dp, diagonal 131.2 dp | Inside the 160 dp circle |
| Store icon (512 px, 72-unit crop) | 7.111 px per unit | 398 × 133 px (measured 396 × 131 px of solid glyph color, §0.3 item 14) | Full square, Play applies its mask |

### 3.6 Splash (D-112)

Themes and colors are exactly `ARCHITECTURE.md` §8.3: `Theme.Game2048.Starting` (parent `Theme.SplashScreen`) with `windowSplashScreenBackground = @color/window_background`, `windowSplashScreenAnimatedIcon = @drawable/ic_launcher_foreground`, `windowSplashScreenIconBackgroundColor = @color/ic_launcher_background`, and `postSplashScreenTheme = @style/Theme.Game2048`. `values-night/themes.xml` redefines only `Theme.Game2048` (parent `android:Theme.Material.NoActionBar`), and `values-night/colors.xml` switches `window_background` to `#1B1916`.

- On API 31+ the platform draws the splash; on API 24–30 `core-splashscreen` 1.2.0 emulates it with the same attributes.
- The splash follows only the system night mode, not the in-app theme or palette, because it is drawn before any state loads (`DESIGN_SYSTEM.md` §8.3).
- The keep-on-screen gate (≤ 1,000 ms) is `ARCHITECTURE.md` §4.12 and F07-AC11…AC13.
- Tests: F30-AC7 (`ManifestPolicyTest.splashThemeUsesIdentityColors`).

### 3.7 Store icon and the About icon

`StoreAssetsTest.icon`:

```kotlin
val fg = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
Canvas(bmp).apply {
    drawColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
    fg.setBounds(-128, -128, 640, 640) // the 108-unit layer at 768 px; its central 72 units fill 512 px
    fg.draw(this)
}
val background = ContextCompat.getColor(context, R.color.ic_launcher_background)
OpaquePngWriter.write(bmp, background, File(outDir, "icon.png")) // color type 2, like every store PNG (D-190)
```

The store icon therefore shows exactly what a launcher shows inside its mask (research/07 G4). It is a full opaque square, and Play applies its own rounding.

The About header composes the same layers at 72 dp: a `#E6A11A` circle and the foreground drawable (`DESIGN_SYSTEM.md` §6.11), because Compose cannot load an `<adaptive-icon>`.

### 3.8 Acceptance criteria (identity)

| AC | Given | When | Then | Test or gate |
|---|---|---|---|---|
| — | SDK 36 | `applicationInfo.loadIcon` | An `AdaptiveIconDrawable` with a monochrome layer | F30-AC6 |
| — | Splash theme, notnight and night | Attributes resolved | `#FAF8EF` / `#1B1916`; icon background `#E6A11A` | F30-AC7 |
| L-AC11 | The three icon vectors | `check_icon_glyphs` runs | 4 identical glyph paths in each | Gate: check-metadata.sh `check_icon_glyphs` (M0, D-179) |
| L-AC12 | `@Config(sdk = [24])` | `applicationInfo.loadIcon` | A drawable whose intrinsic size is 48 dp (the `mipmap-anydpi` vector, not the adaptive icon) | `ManifestPolicyTest.legacyLauncherIconOnApi24` (M4) |

---

## 4. Legal

### 4.1 License and attribution matrix

| Component | License | Copyright line | What this app reuses | Noticed in |
|---|---|---|---|---|
| This app | MIT | Copyright (c) 2026 clayboicardi | — | `LICENSE` |
| gabrielecirulli/2048 | MIT | Copyright (c) 2014 Gabriele Cirulli | Game rules and their behavior as golden vectors; the palette that Classic Refined derives from | `LICENSE`, `NOTICE` 1, in-app MIT group |
| uberspot/2048-android and its web fork uberspot/2048 | MIT | Copyright (c) 2014 Paul Sarbinowski (web fork: Copyright (c) 2014 Gabriele Cirulli) | 20 `settings` translations; ru/uk game strings; the 4 icon glyph paths; legacy release history | `LICENSE`, `NOTICE` 2, in-app MIT group, About credits |
| Google Sans Flex subset | OFL-1.1, no Reserved Font Name | Copyright 2015 The Google Sans Flex Authors | `res/font/google_sans_flex_numerals.ttf` | `NOTICE` 3, in-app OFL group, `res/raw/ofl_google_sans_flex.txt` |
| Material Symbols Rounded | Apache-2.0 | Copyright Google LLC | `res/drawable/ic_*.xml` | `NOTICE` 4, in-app Apache group |
| AndroidX, Compose, Kotlin, kotlinx, Guava ListenableFuture, Okio, JSpecify, JetBrains annotations | Apache-2.0 | Per §4.4 | Runtime classpath (37 groups) | `NOTICE` 5, in-app Apache group |

Under the D-076 font fallback, the font, its OFL file, its JSON entry and `NOTICE` item 3 are all absent, and the remaining items keep their numbers.

### 4.2 `LICENSE` (final text; D-120)

M0 inserts two lines under the existing copyright line and adds the missing final newline. Result: 1,151 bytes, sha256 `ad96d837d59c50cb3c8e23a17d7ee19a396c5a1a6c4bc952a269896fcbdbfd19`.

```text
The MIT License (MIT)

Copyright (c) 2014 Paul Sarbinowski
Copyright (c) 2014 Gabriele Cirulli
Copyright (c) 2026 clayboicardi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

`app/src/main/res/raw/license_mit.txt` (M4) is a byte-identical copy, enforced from M5 by `check_legal_texts` (§2.6).

### 4.3 `NOTICE` (final text; D-121, M5)

```text
2048 for Android (com.clayboicardi.game2048)
https://github.com/clayboicardi/2048-android-modern-refresh
Copyright (c) 2026 clayboicardi. Licensed under the MIT License; see LICENSE.

This app is a native rewrite. It contains or is derived from the third-party
material below. Each item keeps its own license and copyright notice.

1. 2048, the original web game
   https://github.com/gabrielecirulli/2048
   Copyright (c) 2014 Gabriele Cirulli. MIT License (text in LICENSE).
   "A small clone of 1024, based on Saming's 2048 (also a clone)."
   Used: the game rules, reproduced as golden test vectors, and the tile
   palette from which the Classic Refined palette is derived. No source code
   of the web game is included.

2. 2048-android, the original Android app, and its web-game fork
   https://github.com/uberspot/2048-android
   https://github.com/uberspot/2048
   Copyright (c) 2014 Paul Sarbinowski. MIT License (text in LICENSE).
   The web-game fork carries the MIT notice Copyright (c) 2014 Gabriele Cirulli.
   Used: the "Settings" translations in 20 languages, the Russian and
   Ukrainian game strings, the vector paths of the "2048" glyphs in the
   launcher icon, and the release history shown under About > Legacy history.
   Translators of the reused strings: Mongata (cs), dark159123 (da),
   uberspot and alexara (de), uberspot (el, fr, pl), Robin van der Vliet
   (eo, nl), Laura Arjona Reina (es), SMahdiFaghih (fa), Balázs Meskó (hu),
   RyuzakiKK (it), amoseui (ko), zygimantus (lt), Caio Melo (pt), Igor A
   (ru; Russian and Ukrainian game strings), Matej (sl), djura-san (sr),
   TeknoCepni (tr), Vladyslav Pohrebniakov (uk). Every legacy contributor is
   credited in the app under About > Credits.

3. Google Sans Flex (numeral subset)
   https://github.com/googlefonts/googlesans-flex
   Copyright 2015 The Google Sans Flex Authors.
   SIL Open Font License, Version 1.1 (OFL-1.1), with no Reserved Font Name.
   The full license text is app/src/main/res/raw/ofl_google_sans_flex.txt and
   is shown in the app. The app bundles a subset of digits and a few capital
   letters. Google Sans is a trademark of Google.

4. Material Symbols (Rounded) icons
   https://github.com/google/material-design-icons
   Copyright Google LLC. Apache License, Version 2.0 (Apache-2.0).

5. AndroidX, Jetpack Compose, Kotlin, kotlinx, and their dependencies
   Guava ListenableFuture, Okio, JSpecify and JetBrains Java Annotations.
   Copyright The Android Open Source Project; Copyright JetBrains s.r.o. and
   contributors; Copyright The Guava Authors; Copyright Square, Inc.;
   Copyright The JSpecify Authors.
   Apache License, Version 2.0 (Apache-2.0):
   https://www.apache.org/licenses/LICENSE-2.0
   The full list is shown in the app under About > Open-source licenses.
```

The lineage line is quoted verbatim from upstream `README.md` line 2 (D-121). The SPDX ids in items 3–5 (`OFL-1.1`, `Apache-2.0`) are the ones D-121 names; `check_legal_and_privacy` looks for them (§2.6). Under the D-076 font fallback, item 3 is absent and the check does not require it.

### 4.4 In-app open-source licenses (D-122)

Raw files under `app/src/main/res/raw/` (M4):

| File | Content | Pin |
|---|---|---|
| `license_mit.txt` | Byte copy of `LICENSE` (§4.2) | `check_legal_texts` (§2.6) |
| `license_apache_2_0.txt` | `https://www.apache.org/licenses/LICENSE-2.0.txt` verbatim | 11,358 bytes, sha256 `cfc7749b…3d30` (`check_legal_texts`, §2.6) |
| `ofl_google_sans_flex.txt` | `google/fonts` `ofl/googlesansflex/OFL.txt` verbatim | 4,488 bytes, sha256 `fc13d69f…3137` at `9b59ea9f…` (§0.3 item 8). `scripts/fonts/subset-google-sans-flex.sh` copies it and records its sha256 next to the TTF's |
| `third_party_licenses.json` | Below | `LicensesCoverageTest` (F26-AC1) |

**`third_party_licenses.json`** is a JSON array. Entry fields: `name` (display), `groupOrPath` (Maven group, repository URL or file path), `licenseId` (SPDX: `MIT`, `OFL-1.1`, `Apache-2.0`) and `copyright`. The screen groups entries by `licenseId` in the order MIT, OFL-1.1, Apache-2.0, keeping file order inside each group (`DESIGN_SYSTEM.md` §6.12). There is one entry per Maven group, so `LicensesCoverageTest` can match `groupOrPath` against `config/license-groups.txt` exactly.

```json
[
  {
    "name": "2048",
    "groupOrPath": "https://github.com/gabrielecirulli/2048",
    "licenseId": "MIT",
    "copyright": "Copyright (c) 2014 Gabriele Cirulli"
  },
  {
    "name": "2048-android",
    "groupOrPath": "https://github.com/uberspot/2048-android",
    "licenseId": "MIT",
    "copyright": "Copyright (c) 2014 Paul Sarbinowski"
  },
  {
    "name": "Google Sans Flex (numeral subset)",
    "groupOrPath": "res/font/google_sans_flex_numerals.ttf",
    "licenseId": "OFL-1.1",
    "copyright": "Copyright 2015 The Google Sans Flex Authors"
  },
  {
    "name": "Material Symbols (Rounded)",
    "groupOrPath": "res/drawable/ic_*.xml",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright Google LLC"
  },
  {
    "name": "AndroidX Activity",
    "groupOrPath": "androidx.activity",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Annotation",
    "groupOrPath": "androidx.annotation",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX AppCompat Resources",
    "groupOrPath": "androidx.appcompat",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Arch Core",
    "groupOrPath": "androidx.arch.core",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Autofill",
    "groupOrPath": "androidx.autofill",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Collection",
    "groupOrPath": "androidx.collection",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "Jetpack Compose BOM",
    "groupOrPath": "androidx.compose",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "Jetpack Compose Animation",
    "groupOrPath": "androidx.compose.animation",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "Jetpack Compose Foundation",
    "groupOrPath": "androidx.compose.foundation",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "Jetpack Compose Material Ripple",
    "groupOrPath": "androidx.compose.material",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "Jetpack Compose Material 3",
    "groupOrPath": "androidx.compose.material3",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "Jetpack Compose Material 3 Adaptive",
    "groupOrPath": "androidx.compose.material3.adaptive",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "Jetpack Compose Runtime",
    "groupOrPath": "androidx.compose.runtime",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "Jetpack Compose UI",
    "groupOrPath": "androidx.compose.ui",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Concurrent",
    "groupOrPath": "androidx.concurrent",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Core",
    "groupOrPath": "androidx.core",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Custom View",
    "groupOrPath": "androidx.customview",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX DataStore",
    "groupOrPath": "androidx.datastore",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Emoji2",
    "groupOrPath": "androidx.emoji2",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Graphics",
    "groupOrPath": "androidx.graphics",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Interpolator",
    "groupOrPath": "androidx.interpolator",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Lifecycle",
    "groupOrPath": "androidx.lifecycle",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Navigation 3",
    "groupOrPath": "androidx.navigation3",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Navigation Event",
    "groupOrPath": "androidx.navigationevent",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Profile Installer",
    "groupOrPath": "androidx.profileinstaller",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX SavedState",
    "groupOrPath": "androidx.savedstate",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Startup",
    "groupOrPath": "androidx.startup",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Tracing",
    "groupOrPath": "androidx.tracing",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX VectorDrawable",
    "groupOrPath": "androidx.vectordrawable",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX VersionedParcelable",
    "groupOrPath": "androidx.versionedparcelable",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "AndroidX Window",
    "groupOrPath": "androidx.window",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Android Open Source Project"
  },
  {
    "name": "Guava ListenableFuture",
    "groupOrPath": "com.google.guava",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The Guava Authors"
  },
  {
    "name": "Okio",
    "groupOrPath": "com.squareup.okio",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright Square, Inc."
  },
  {
    "name": "JetBrains Java Annotations",
    "groupOrPath": "org.jetbrains",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright JetBrains s.r.o."
  },
  {
    "name": "Kotlin Standard Library",
    "groupOrPath": "org.jetbrains.kotlin",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors"
  },
  {
    "name": "kotlinx.coroutines, kotlinx.serialization, kotlinx.collections.immutable",
    "groupOrPath": "org.jetbrains.kotlinx",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright JetBrains s.r.o. and contributors"
  },
  {
    "name": "JSpecify",
    "groupOrPath": "org.jspecify",
    "licenseId": "Apache-2.0",
    "copyright": "Copyright The JSpecify Authors"
  }
]
```

**`config/license-groups.txt`** (written in M0, because the M0 `app/build.gradle.kts` already declares the full dependency block). The format is one `<group> <SPDX id>` per line, sorted, with `#` comments allowed. `scripts/check-licenses.sh` extracts the groups of every resolved module on `:app:releaseRuntimeClasspath`, including the `androidx.compose` BOM platform and excluding `project(":core:engine")`, and fails on any group missing here.

```text
# Maven group on :app:releaseRuntimeClasspath -> SPDX license id (D-122; L10N_STORE_LEGAL.md §4.4)
androidx.activity Apache-2.0
androidx.annotation Apache-2.0
androidx.appcompat Apache-2.0
androidx.arch.core Apache-2.0
androidx.autofill Apache-2.0
androidx.collection Apache-2.0
androidx.compose Apache-2.0
androidx.compose.animation Apache-2.0
androidx.compose.foundation Apache-2.0
androidx.compose.material Apache-2.0
androidx.compose.material3 Apache-2.0
androidx.compose.material3.adaptive Apache-2.0
androidx.compose.runtime Apache-2.0
androidx.compose.ui Apache-2.0
androidx.concurrent Apache-2.0
androidx.core Apache-2.0
androidx.customview Apache-2.0
androidx.datastore Apache-2.0
androidx.emoji2 Apache-2.0
androidx.graphics Apache-2.0
androidx.interpolator Apache-2.0
androidx.lifecycle Apache-2.0
androidx.navigation3 Apache-2.0
androidx.navigationevent Apache-2.0
androidx.profileinstaller Apache-2.0
androidx.savedstate Apache-2.0
androidx.startup Apache-2.0
androidx.tracing Apache-2.0
androidx.vectordrawable Apache-2.0
androidx.versionedparcelable Apache-2.0
androidx.window Apache-2.0
com.google.guava Apache-2.0
com.squareup.okio Apache-2.0
org.jetbrains Apache-2.0
org.jetbrains.kotlin Apache-2.0
org.jetbrains.kotlinx Apache-2.0
org.jspecify Apache-2.0
```

When a dependency change adds a group, the same commit adds it here and in the JSON (F26 edge case). The 37 groups match the resolution in §0.3 item 7; if M0's resolution differs, the M0 output wins, and the commit body lists the difference.

### 4.5 Credits, translators and legacy history (D-124)

**M0, before the submodule is removed:** write `docs/modernization/legacy-credits.md` with these sections:
1. "Git authors", with the exact commands and their raw output:

   ```bash
   git log --format='%an' e097460 | sort -u
   git -C assets/2048 log --format='%an' fc1ef4f..7a477eb | sort -u
   ```

2. "Credited in commit messages": `f889036` "added night mode button (credits to gurshabad)".
3. "Translators of reused strings": the table below.
4. "Legacy releases": `res/raw/changelog_master.xml` verbatim in a code block.

Expected deduplicated contributor list (§0.3 item 12):
- Alias merges: uberspot = Paul Sarbinowski, who gets his own credit row; Taco = TacoTheDank.
- Gabriele Cirulli and Paul Sarbinowski are excluded from this list because they have their own rows.
- gurshabad is added: legacy commit `f889036` credits the night-mode idea to him (D-209; research/02 §7 item 3).
- Sorted case-insensitively.

Result, 31 names: alexara, amoseui, Balázs Meskó, BValeo, Caio Melo, dark159123, Diogo Pereira, djura-san, fraggerfox, gurshabad, Hans-Christoph Steiner, Igor A, Laura Arjona Reina, lishoujun, Matej, Mauro Scomparin, Mongata, Oluwaleke Fakorede, Paul Verest, Poussinou, princessruthie, Robin van der Vliet, RyuzakiKK, shubham1701, SMahdiFaghih, Steven Lee, TacoTheDank, TeknoCepni, Vladyslav Pohrebniakov, xin, zygimantus.

**Translators of reused strings** (`git log --follow res/values-xx/strings.xml`, earliest content author):

| Locale | Translator | Commit |
|---|---|---|
| cs | Mongata | `72f7890` (2018) |
| da | dark159123 | `db1e4a3` (2016) |
| de | uberspot; grammar fix by alexara | `800b80c` (2014), `c7e456d` (2016) |
| el | uberspot | `8a04f5d` (2014) |
| eo | Robin van der Vliet | `238b152` (2016) |
| es | Laura Arjona Reina | `f7b3ff4` (2014) |
| fa | SMahdiFaghih | `e097460` (2021) |
| fr | uberspot | `8a04f5d` (2014) |
| hu | Balázs Meskó | `825e39f` (2019) |
| it | RyuzakiKK | `ea3b08d` (2018) |
| ko | amoseui | `d9acf52` (2017) |
| lt | zygimantus | `5b6bda4` (2016) |
| nl | Robin van der Vliet | `8eb1bc8` (2016) |
| pl | uberspot | `800b80c` (2014) |
| pt | Caio Melo | `06b893e` (2017) |
| ru | Igor A; web game strings by Igor A (a4vi2r) | `c4f26fc` (2017); uberspot/2048 `e22c42e` |
| sl | Matej (urmatej) | `6a790ad` (2018) |
| sr | djura-san | `fc15f58` (2014) |
| tr | TeknoCepni | `5432be9` (2021) |
| uk | Vladyslav Pohrebniakov; web game strings by Igor A (a4vi2r) | `8d76788` (2017); uberspot/2048 `e22c42e` |

**`app/src/main/res/raw/legacy_history.json`** (M4, built from `legacy-credits.md`). `credits[]` renders as "name — role" rows (`DESIGN_SYSTEM.md` §6.11; F25-AC6). `releases[]` renders newest first, in file order (F25-AC7). The text is English static data, never translated (D-124). The change texts are verbatim from `changelog_master.xml`, except that the 2.1 entry loses its `~~` strike-through markup and its line breaks.

```json
{
  "credits": [
    {
      "name": "Gabriele Cirulli",
      "role": "original game"
    },
    {
      "name": "Paul Sarbinowski (uberspot)",
      "role": "Android port"
    },
    {
      "name": "Contributors",
      "role": "alexara, amoseui, Balázs Meskó, BValeo, Caio Melo, dark159123, Diogo Pereira, djura-san, fraggerfox, gurshabad, Hans-Christoph Steiner, Igor A, Laura Arjona Reina, lishoujun, Matej, Mauro Scomparin, Mongata, Oluwaleke Fakorede, Paul Verest, Poussinou, princessruthie, Robin van der Vliet, RyuzakiKK, shubham1701, SMahdiFaghih, Steven Lee, TacoTheDank, TeknoCepni, Vladyslav Pohrebniakov, xin, zygimantus"
    },
    {
      "name": "clayboicardi",
      "role": "native rewrite"
    }
  ],
  "releases": [
    {
      "version": "2.2",
      "versionCode": 25,
      "changes": [
        "(2.2) Bumped minSdkVersion from 8 (Froyo) to 14 (ICS)",
        "(2.2) Update changelog library that had breaking changes",
        "(2.1) Detect and utilise the phone's system language (thank you BValeo)",
        "(2.1) Added Slovenian translation (thank you urmatej)",
        "(2.1) Added Czech translation (thank you Mongata)",
        "(2.1) Added Italian translation (thank you RyuzakiKK)"
      ]
    },
    {
      "version": "2.1",
      "versionCode": 24,
      "changes": [
        "Bad build. This version of the app crashed on startup due to a user-made (*cough* Taco *cough*) error that occurred when rearranging the project. Changes introduced in 2.1 will be mentioned in the 2.2 changelog."
      ]
    },
    {
      "version": "2.08",
      "versionCode": 23,
      "changes": [
        "Added Esperanto and Dutch (thx Robin van der Vliet)",
        "Fix bug with floating windows (thx diogopereira)"
      ]
    },
    {
      "version": "2.06",
      "versionCode": 22,
      "changes": [
        "Limit undo button to last 50 moves to avoid using too much memory"
      ]
    },
    {
      "version": "2.05",
      "versionCode": 21,
      "changes": [
        "Added changelog in app"
      ]
    },
    {
      "version": "2.0",
      "versionCode": 20,
      "changes": [
        "Added undo button (thx to fraggerfox on GitHub)"
      ]
    },
    {
      "version": "1.96",
      "versionCode": 19,
      "changes": [
        "Rebuilt APK with new buildTools"
      ]
    },
    {
      "version": "1.95",
      "versionCode": 18,
      "changes": [
        "Added German, Spanish, Polish, Serbian translations (thx to contributors on GitHub)",
        "Added experimental night mode button",
        "Minor optimizations to responsiveness"
      ]
    },
    {
      "version": "1.91",
      "versionCode": 18,
      "changes": [
        "Added Greek and French app translation; minor build improvements"
      ]
    }
  ]
}
```

### 4.6 `PRIVACY.md` (final text; D-123, M5)

```markdown
# Privacy policy

Applies to the Android app "2048" (package `com.clayboicardi.game2048`, and its debug build `com.clayboicardi.game2048.debug`), version 3.0.0 and later, built from https://github.com/clayboicardi/2048-android-modern-refresh.

Last updated: 2026-10-28.

## Summary

This app collects nothing. It has no network access, no accounts, no ads and no analytics. Game data stays on your device. Android's own backup may copy it to your Google account if you enabled device backup.

## What the app stores on your device

The app keeps only these files in its private storage:

- `settings.json`: your settings, such as theme, palette, motion, haptics, controls and display options;
- `records.json`: best scores and statistics for each board size;
- `game-3.json` to `game-8.json`: the saved game for each board size, including its move history.

They contain no personal information. The app does not read your contacts, location, files, accounts or device identifiers. Uninstalling the app deletes these files.

## What leaves your device

The app itself sends nothing. It does not declare Android's internet permission, so it cannot open network connections.

- **Android backup.** If backup is turned on in Android's settings, Android, not this app, may include the files above in your device backup or in a transfer to a new device. You control this in Android's settings.
- **Sharing.** If you tap Share on the game-over card, the app passes a short text summary of that game to the app you choose. Nothing is shared unless you choose to.
- **Links.** "Source code" opens the project page in your browser, and "App language" opens Android's settings. Those apps have their own privacy policies.

## Permissions

The app requests no runtime permissions and never shows a permission prompt. Its manifest contains one permission, `com.clayboicardi.game2048.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which the AndroidX Core library adds automatically. It is a signature-level permission: only apps signed with the same key can hold it, and on Android versions before 13 it keeps the app's own internal broadcast receivers private. It gives the app no access to your data, your device or other apps. The debug build carries the same permission under its own package name, `com.clayboicardi.game2048.debug`.

## Children

The app collects no data from anyone, including children.

## Changes and contact

Changes to this policy are made in this file, and its history is public in the repository. Questions: https://github.com/clayboicardi/2048-android-modern-refresh/issues
```

`check_legal_and_privacy` (F27-AC5, §2.6) asserts that the D-123 paragraph appears verbatim and that the file names `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

### 4.7 In-app privacy text

- `privacy_text` (§1.3, section I) is D-123's paragraph verbatim in English. About → Privacy shows it localized in all 21 locales.
- Translators render it faithfully, adding or removing no claim, and keep "Android" and "Google".
- Tests: F27-AC4 (`AboutScreenTest.privacyTextMatchesPolicy`, en); `TranslationQualityTest.neverTranslateTokensKept` (Android, Google in every locale).

### 4.8 Permission disclosure wording

Canonical paragraph, used verbatim in README and PRIVACY.md:

> The app requests no runtime permissions and never shows a permission prompt. Its manifest contains one permission, `com.clayboicardi.game2048.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which the AndroidX Core library adds automatically. It is a signature-level permission: only apps signed with the same key can hold it, and on Android versions before 13 it keeps the app's own internal broadcast receivers private. It gives the app no access to your data, your device or other apps. The debug build carries the same permission under its own package name, `com.clayboicardi.game2048.debug`.

Short forms:
- Store listings and the What's new copy: "No runtime permissions." Never "no permissions" or "zero permissions" (research/07 C25).
- README "Exported components" line: "`MainActivity` (the launcher entry) and AndroidX `ProfileInstallReceiver`, which only the system shell can call (it requires `android.permission.DUMP`) and which installs ahead-of-time performance profiles. The app itself requests no such permission."

These sentences match the D-148 allowlists and are true for both APKs.

### 4.9 README rewrite (D-116)

**M0 (minimal edit):**
- delete the Travis badge;
- delete the Google Play and F-Droid badge links to `com.uberspot.a2048`;
- replace the "Building" section with "Build with JDK 21: `./scripts/verify.sh`";
- leave the rest until M5 (`ARCHITECTURE.md` §1.3).

**M5 (full rewrite)**, sections in this order. The quoted sentences are required verbatim; other wording is free.

1. **2048** with a one-paragraph description. Optional CI badge for this repository only: `![CI](https://github.com/clayboicardi/2048-android-modern-refresh/actions/workflows/ci.yml/badge.svg?branch=master)`. No store badges.
2. **Features:** a short list (sizes, unlimited undo, themes, accessibility, 21 languages, offline).
3. **A new app:** "This is a new app with a new package name (`com.clayboicardi.game2048`); the old app can stay installed side by side; scores do not carry over." (D-005)
4. **Privacy and permissions:** a link to [PRIVACY.md](PRIVACY.md), the §4.8 paragraph verbatim, and the exported-components line.
5. **Install a CI build:** the steps of §5.4 in short form, and the sentence "CI builds are signed with a key whose password is public (see `signing/README.md`); install them only from this repository's Actions runs."
6. **Build from source:** JDK 21; Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`; `./scripts/verify.sh` (the same gate as CI); `./gradlew :app:assembleDebug`; recording goldens; store graphics (`ARCHITECTURE.md` §2.11).
7. **Saved games:** "The game is saved after every move. If Android stops the app while a save is being written, at most that one change is lost." (D-043)
8. **Translations:** "The 20 translations were produced with machine assistance and checked automatically; corrections are welcome as pull requests to `app/src/main/res/values-*/strings.xml`. Please follow `docs/modernization/i18n-glossary.md`." (D-105)
9. **Credits:** Gabriele Cirulli (original game), Paul Sarbinowski (uberspot) and contributors (original Android app), the legacy translators (link to [NOTICE](NOTICE)).
10. **License:** "MIT; see [LICENSE](LICENSE). Third-party notices: [NOTICE](NOTICE)."

The README never contains `com.uberspot.a2048` store links, "WebView" or "port". F27-AC5 checks its links to `PRIVACY.md` and `NOTICE` and the permission name.

### 4.10 Acceptance criteria (legal)

| AC | Given | When | Then | Test or gate |
|---|---|---|---|---|
| — | `LICENSE`, `NOTICE`, OFL file | `check-metadata.sh` | Three copyright lines in order; the five `NOTICE` items and the lineage line; `ofl_google_sans_flex.txt` iff the TTF exists | F26-AC5 (`check_license_file`, `check_legal_and_privacy`, `check_font`) |
| L-AC13 | Any commit from M5 on | `check-metadata.sh` | `sha256sum LICENSE` = `ad96d837…fd19`; `license_mit.txt` equals `LICENSE`; the Apache text has its canonical sha256 | Gate: check-metadata.sh `check_legal_texts` (D-190) |
| — | `config/license-groups.txt` and the JSON | Coverage | Every group has an entry with the mapped license | F26-AC1, F26-AC2 |
| — | `legacy_history.json` | About renders | Four credit rows; nine releases in order | F25-AC6, F25-AC7 |
| — | `PRIVACY.md`, README | `check-metadata.sh` | D-123 text verbatim; links; permission name | F27-AC5 (`check_legal_and_privacy`) |

---

## 5. Signing and distribution

### 5.1 Policy (U5, D-131, D-133)

- One key, `signing/ci-sideload.p12`, signs **both** the debug and the release build types. The key is committed on purpose, and its password is public.
- Purpose: every CI-built APK installs over the previous CI build of the same variant, keeping the player's data.
- It is **never** used for Google Play, F-Droid or any other store, and never registered with Android developer verification (§5.6).
- There is no release workflow, no tag, no GitHub Release and no store upload in this overhaul (D-133). The deliverables are the CI artifacts: debug APK, release APK and `mapping.txt` (D-151).

### 5.2 Key material (M0)

| Item | Value |
|---|---|
| File | `signing/ci-sideload.p12` (PKCS12, committed; `.gitattributes` marks it binary) |
| Alias | `ci-sideload` |
| Store and key password | `game2048-ci-sideload` |
| Key | RSA 4096, validity 10,000 days |
| Subject | `CN="2048 CI sideload key (public; not for store uploads)", O=clayboicardi` |
| Certificate digest | `signing/ci-sideload.sha256`: lowercase hex SHA-256 of the DER certificate, one line |
| Generator | `scripts/signing/generate-ci-sideload-keystore.sh` (`ARCHITECTURE.md` §2.9, including the `\;` escaping of technical correction TC-1) |
| Gradle | `signingConfigs.create("ciSideload")` used by debug and release (`ARCHITECTURE.md` §2.6) |
| Gate | `check-apk.sh` step 6: both APKs' signer digest equals `signing/ci-sideload.sha256` (D-148) |

`signing/README.md` contains, in this order:
1. the alias and password;
2. "This key is intentionally public. Anyone can sign an APK with it. It exists only so that every CI-built APK installs over the previous one.";
3. "It is never used for Google Play or any other store. Real release signing is out of scope until the repository owner adds secrets.";
4. "Do not register this key or the package name with Android developer verification." (§5.6);
5. the regeneration command (`ARCHITECTURE.md` §2.9).

If GitHub push protection rejects the keystore, D-172 condition 5 applies: stop and report.

### 5.3 What a public key means for players

- **Integrity across CI builds:** Android installs an update only if its signer matches, so a CI APK always replaces a CI APK, and the installed data stays.
- **No authenticity:** anyone can build an APK with package `com.clayboicardi.game2048` signed by this key. Android would accept it as an update and give it the app's private files (game state, records and settings, nothing else). This is why README says to install CI builds only from this repository's Actions runs.
- **Low-value data.** The app holds no secrets, accounts or personal data (D-123), so a malicious same-key update could read only scores.
- A store build, signed with a different key, can never update a CI install (and vice versa). Switching channels means uninstalling, which deletes local data unless Android backup restores it (D-048).

### 5.4 Installing CI APKs

1. Open `https://github.com/clayboicardi/2048-android-modern-refresh/actions/workflows/ci.yml` and choose a green run on `master`, or on a pull request.
2. Download the artifact `game2048-apks-<sha>`. It is kept for 30 days, and GitHub requires a signed-in account to download it. Unzip it. `upload-artifact` strips the common path prefix `app/build/outputs/`, so the zip contains:
   - `apk/release/app-release.apk`: package `com.clayboicardi.game2048`, label "2048", R8-optimized; recommended for playing.
   - `apk/debug/app-debug.apk`: package `com.clayboicardi.game2048.debug`, label "2048 Debug". It installs next to release and has separate data.
   - `mapping/release/mapping.txt`: R8 mapping, for decoding release stack traces.
3. Optionally check the signer: `apksigner verify --print-certs apk/release/app-release.apk` (build-tools 37.0.0). The "certificate SHA-256 digest" equals the content of `signing/ci-sideload.sha256`.
4. Install with either method:
   - computer: `adb install -r apk/release/app-release.apk`;
   - device: copy the APK, open it in a file manager, and allow "Install unknown apps" for that file manager when Android asks. Play Protect may show a scan prompt for an app from an unknown developer; choosing to install anyway is the user's decision.

### 5.5 Update in place

| From → to | Result |
|---|---|
| CI release → newer CI release (same versionCode 30000) | Updates in place. Android accepts an equal versionCode; only a lower one is a downgrade |
| CI debug → newer CI debug | Updates in place (`.debug` package) |
| CI release ↔ CI debug | Independent apps; both can be installed |
| CI build → a build with a higher versionCode | Updates in place; going back needs an uninstall (or `adb install -r -d` for the debuggable debug variant only) |
| CI build ↔ any store build | Signature mismatch: uninstall first |
| `com.uberspot.a2048` ↔ this app | Different packages: both stay installed; no data moves (D-005) |

Every CI build of this overhaul carries versionCode 30000 (D-130), so any CI APK can replace any other of the same variant, including an older commit's.

### 5.6 Android developer verification (sideloading, 2026–2027)

Status on 2026-10-28 (§0.3 item 13):
- Since 2026-09-30, apps whose package is not registered by a verified developer cannot be installed **from participating stores** on certified devices in Brazil, Indonesia, Singapore and Thailand.
- Direct APK sideloading is outside this first phase. `adb` installs are exempt. A one-time "advanced flow" lets power users install unregistered apps.
- Google plans to extend the requirement globally from 2027. A free "limited distribution" account (no ID check, up to 20 devices) exists for hobbyists.

Consequences:
- §5.4 works today everywhere. `adb install` remains the path that is guaranteed to keep working.
- **Never register `com.clayboicardi.game2048` together with the public sideload key.** Registration binds a package and signing key to a verified identity, and with a public key anyone could sign malware that passes that check. Registration, if wanted, belongs with the real key of §5.7.
- When the global rollout covers sideloading, the README's install section gains a note about `adb` or the advanced flow. That is a docs change, not a signing change.

### 5.7 When real signing secrets are added (future; needs D-173 entries)

None of this is part of the execution PR (U5, D-133). When the owner adds secrets, a follow-up PR:
1. Adds an upload/release key held only in GitHub secrets, for example `GAME2048_RELEASE_KEYSTORE_BASE64`, `GAME2048_RELEASE_STORE_PASSWORD`, `GAME2048_RELEASE_KEY_ALIAS` and `GAME2048_RELEASE_KEY_PASSWORD`. It is never committed.
2. Adds `signingConfigs.create("release")`, read from Gradle properties or environment variables. It is used only by a new release-only path, a `bundleRelease` (AAB) for Play App Signing or a signed release APK. Debug and the CI `assembleRelease` artifact keep `ciSideload`, so sideload users keep updating in place.
3. Changes `check-apk.sh` step 6 for the store artifact only: its digest is checked against a new committed public certificate digest, while the CI APKs keep checking `signing/ci-sideload.sha256`.
4. Adds `release.yml` (tag `v*`, tag == versionName, D-130), and raises versionCode per D-130's scheme.
5. Registers the package with Android developer verification using the real key (§5.6), and fills in the Play Console privacy policy link (`PRIVACY.md`) and a Data safety form that declares no data collected.
6. Tells users plainly that store builds cannot update CI sideload installs (§5.3). Players move by Android backup restore or start fresh.

### 5.8 Acceptance criteria (signing)

| AC | Given | When | Then | Test or gate |
|---|---|---|---|---|
| — | Both APKs | `apksigner verify --print-certs` | Digest = `signing/ci-sideload.sha256` | F30-AC2 (D-148 step 6) |
| — | Both APKs | `aapt2 dump badging` | Release `com.clayboicardi.game2048` / "2048"; debug `…game2048.debug` / "2048 Debug" | F30-AC1 |
| L-AC14 | `signing/README.md` | Read in M0 | Contains the 5 items of §5.2 in order | Inspection, M0 commit body |

---

## Open issues

- **L-OI-13 (Greek upper-case chip labels; verification at first use).** `DESIGN_SYSTEM.md` §3.7 upper-cases chip labels with `uppercase(locale)`. Greek typography drops stress marks in capitals ("ΡΕΚΟΡ"), and whether the runtime's case mapping does so for `el` cannot be verified on the JVM. The el glossary keeps "Σκορ" (no stress mark). Checked in M5 when the integrator Reads the el captures: one capture of the Game header in `el`, written under `app/build/` and not committed. Fallback: if it shows "ΡΕΚΌΡ", `ScoreChip` in `ui/components/ScoreCard.kt` strips U+0301 after upper-casing when `locale.language == "el"`, a one-line change with no decision impact, recorded in the M5 commit body.

Resolved during planning: L-OI-1, L-OI-3, L-OI-6, L-OI-7, L-OI-12 → D-208; L-OI-2, L-OI-4 → canonical keys (`PLAN.md` §2); L-OI-5 → D-190; L-OI-8, L-OI-14 → out of scope (D-004); L-OI-9 → D-209; L-OI-10, L-OI-11 → accepted as specified in §1.3 and §3.4.
