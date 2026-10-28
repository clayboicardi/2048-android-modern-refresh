# legacy-credits.md: legacy authors, translators and release history

Captured in M0, before the `assets/2048` submodule and `res/raw/changelog_master.xml` were removed (D-124, D-209; `L10N_STORE_LEGAL.md` §4.5). M4 builds `app/src/main/res/raw/legacy_history.json` from this file. Do not edit the raw outputs below.

## 1. Git authors

Commands, run on the full history: the first in a full-history clone of this repository, the second in the `assets/2048` submodule initialized at `7a477eb` (D-213):

```bash
git log --format='%an' e097460 | sort -u
git -C assets/2048 log --format='%an' fc1ef4f..7a477eb | sort -u
```

Raw output of `git log --format='%an' e097460 | sort -u`:

```text
BValeo
Balázs Meskó
Caio Melo
Hans-Christoph Steiner
Igor A
Laura Arjona Reina
Matej
Mauro Scomparin
Mongata
Oluwaleke Fakorede
Paul Sarbinowski
Paul Verest
Poussinou
Robin van der Vliet
RyuzakiKK
SMahdiFaghih
Steven Lee
Taco
TacoTheDank
TeknoCepni
Vladyslav Pohrebniakov
alexara
amoseui
dark159123
djura-san
lishoujun
pierre
princessruthie
shubham1701
uberspot
xin
zygimantus
```

Raw output of `git -C assets/2048 log --format='%an' fc1ef4f..7a477eb | sort -u`:

```text
Diogo Pereira
Igor A
Paul Sarbinowski
TacoTheDank
fraggerfox
uberspot
```

Both outputs come from full-history clones: this repository (`git rev-list --count e097460` prints 92) and `uberspot/2048`. The working checkout is shallow at `8a04f5d` (2014-08-20, uberspot), where `git rev-list --count e097460` prints 67 and the first command misses `pierre`, the French translator (commits `74990c4` and `60d22bd`, 2014-08-18). Corrected through D-213.

Deduplicated contributor list (rules of `L10N_STORE_LEGAL.md` §4.5: uberspot = Paul Sarbinowski and Taco = TacoTheDank are merged; Gabriele Cirulli and Paul Sarbinowski have their own credit rows and are excluded; gurshabad is added from section 2; sorted case-insensitively), 32 names:

alexara, amoseui, Balázs Meskó, BValeo, Caio Melo, dark159123, Diogo Pereira, djura-san, fraggerfox, gurshabad, Hans-Christoph Steiner, Igor A, Laura Arjona Reina, lishoujun, Matej, Mauro Scomparin, Mongata, Oluwaleke Fakorede, Paul Verest, pierre, Poussinou, princessruthie, Robin van der Vliet, RyuzakiKK, shubham1701, SMahdiFaghih, Steven Lee, TacoTheDank, TeknoCepni, Vladyslav Pohrebniakov, xin, zygimantus

## 2. Credited in commit messages

`git -C assets/2048 log -1 --format='%h %an %ad%n%s' --date=short f889036`:

```text
f889036 uberspot 2014-09-21
added night mode button (credits to gurshabad)
```

The night-mode idea is credited to **gurshabad**, who is not a git author (D-209).

## 3. Translators of reused strings

From `git log --follow res/values-xx/strings.xml` on the full history, earliest content author:

| Locale | Translator | Commit |
|---|---|---|
| cs | Mongata | `72f7890` (2018) |
| da | dark159123 | `db1e4a3` (2016) |
| de | uberspot; grammar fix by alexara | `800b80c` (2014), `c7e456d` (2016) |
| el | uberspot | `8a04f5d` (2014) |
| eo | Robin van der Vliet | `238b152` (2016) |
| es | Laura Arjona Reina | `f7b3ff4` (2014) |
| fa | SMahdiFaghih | `e097460` (2021) |
| fr | pierre | `74990c4` (2014) |
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

## 4. Legacy releases

`res/raw/changelog_master.xml`, verbatim:

```xml
<?xml version="1.0" encoding="utf-8"?>
<changelog xmlns:tools="http://schemas.android.com/tools"
    tools:ignore="MissingDefaultResource">
    <release
        version="2.2"
        versioncode="25">
        <change>(2.2) Bumped minSdkVersion from 8 (Froyo) to 14 (ICS)</change>
        <change>(2.2) Update changelog library that had breaking changes</change>
        <change>(2.1) Detect and utilise the phone's system language (thank you BValeo)</change>
        <change>(2.1) Added Slovenian translation (thank you urmatej)</change>
        <change>(2.1) Added Czech translation (thank you Mongata)</change>
        <change>(2.1) Added Italian translation (thank you RyuzakiKK)</change>
    </release>
    <release
        version="2.1"
        versioncode="24">
        <change>~~Bad build. This version of the app crashed on startup due to a user-made (*cough*
            Taco *cough*) error that occurred when rearranging the project. Changes introduced in
            2.1 will be mentioned in the 2.2 changelog.~~
        </change>
    </release>
    <release
        version="2.08"
        versioncode="23">
        <change>Added Esperanto and Dutch (thx Robin van der Vliet)</change>
        <change>Fix bug with floating windows (thx diogopereira)</change>
    </release>
    <release
        version="2.06"
        versioncode="22">
        <change>Limit undo button to last 50 moves to avoid using too much memory</change>
    </release>
    <release
        version="2.05"
        versioncode="21">
        <change>Added changelog in app</change>
    </release>
    <release
        version="2.0"
        versioncode="20">
        <change>Added undo button (thx to fraggerfox on GitHub)</change>
    </release>
    <release
        version="1.96"
        versioncode="19">
        <change>Rebuilt APK with new buildTools</change>
    </release>
    <release
        version="1.95"
        versioncode="18">
        <change>Added German, Spanish, Polish, Serbian translations (thx to contributors on GitHub)
        </change>
        <change>Added experimental night mode button</change>
        <change>Minor optimizations to responsiveness</change>
    </release>
    <release
        version="1.91"
        versioncode="18">
        <change>Added Greek and French app translation; minor build improvements</change>
    </release>
</changelog>
```
