@file:Suppress("MatchingDeclarationName") // PLAN.md §2.1 file name (D-248)

package com.clayboicardi.game2048.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.em
import com.clayboicardi.game2048.R

/**
 * The game-numeral type (D-076; DESIGN_SYSTEM.md §3.1–§3.2, §3.5): the only source of the numeral
 * styles of tiles, mini tiles, the win badge, the Score and Best values (header and game-over
 * card), "+N" and the "2048" title. They use the bundled Google Sans Flex digit subset
 * (`res/font/google_sans_flex_numerals.ttf`), one `FontFamily` per (weight, width, ROND) instance
 * ([numerals]); every style sets `FontWeight(weight)` and `FontSynthesis.None`, and weight and ROND
 * follow the [TileSkin] (Classic wght 800, ROND 0; Neon wght 900, ROND 100). Every style is laid
 * out left to right in every locale (D-104, DESIGN_SYSTEM.md §3.7): in an RTL paragraph the leading
 * "+" of "+N", a neutral, would otherwise be drawn after the digits.
 *
 * API 24–25 ignore variation settings and draw the subset's default instance, wght 700, wdth 100,
 * ROND 0 (DESIGN_SYSTEM.md §3.2). Main thread only: the memo tables are plain maps.
 */
object GameFonts {
    private val families = HashMap<Instance, FontFamily>()
    private val tileStyles = HashMap<Instance, TextStyle>()
    private val chromeStyles = HashMap<Pair<Use, Float>, TextStyle>()

    /** Score and Best values, header and game-over card (§3.2: wght 750, `"tnum, lnum"`). */
    fun score(skin: TileSkin): TextStyle = chrome(Use.SCORE, skin.numeralRond)

    /** The "+N" float (§3.2: wght 800, `"tnum"`). */
    fun plusN(skin: TileSkin): TextStyle = chrome(Use.PLUS_N, skin.numeralRond)

    /** The "2048" title (§3.2: wght 850, letterSpacing −0.01 em, no features). */
    fun title(skin: TileSkin): TextStyle = chrome(Use.TITLE, skin.numeralRond)

    /**
     * Tile labels, compact labels, mini tiles and the win badge (§3.2 rows 1–2, §3.3): the skin's
     * weight and ROND, width 100 / 87.5 / 75 for 1–3 / 4 / ≥ 5 [chars], `"lnum"`.
     */
    fun tile(skin: TileSkin, chars: Int): TextStyle {
        val instance = Instance(skin.numeralWeight, tileWidth(chars), skin.numeralRond)
        return tileStyles.getOrPut(instance) { style(instance, TILE_FEATURES) }
    }

    /**
     * The numeral family of one instance (§3.2), memoized per ([weight], [width], [rond]): the
     * variation settings of a `Font` are fixed.
     */
    fun numerals(weight: Int, width: Float, rond: Float): FontFamily =
        families.getOrPut(Instance(weight, width, rond)) {
            FontFamily(
                Font(
                    resId = R.font.google_sans_flex_numerals,
                    weight = FontWeight(weight),
                    variationSettings =
                        FontVariation.Settings(
                            FontVariation.weight(weight),
                            FontVariation.width(width),
                            FontVariation.Setting(ROND_AXIS, rond),
                        ),
                ),
            )
        }

    private fun chrome(use: Use, rond: Float): TextStyle =
        chromeStyles.getOrPut(use to rond) {
            val base = style(Instance(use.weight, FULL_WIDTH, rond), use.features)
            if (use == Use.TITLE) base.copy(letterSpacing = TITLE_TRACKING_EM.em) else base
        }

    private fun style(instance: Instance, features: String?): TextStyle =
        TextStyle(
            fontFamily = numerals(instance.weight, instance.width, instance.rond),
            fontWeight = FontWeight(instance.weight),
            fontSynthesis = FontSynthesis.None,
            fontFeatureSettings = features,
            textDirection = TextDirection.Ltr,
        )

    /** The `wdth` of a tile label of [chars] characters (DESIGN_SYSTEM.md §3.3 step 2). */
    private fun tileWidth(chars: Int): Float =
        when {
            chars <= FULL_WIDTH_MAX_CHARS -> FULL_WIDTH
            chars == NARROW_CHARS -> NARROW_WIDTH
            else -> CONDENSED_WIDTH
        }

    /** One (weight, width, ROND) instance of the variable subset. */
    private data class Instance(val weight: Int, val width: Float, val rond: Float)

    /** The chrome numerals of §3.2: their weight and OpenType features. */
    private enum class Use(val weight: Int, val features: String?) {
        SCORE(750, "tnum, lnum"),
        PLUS_N(800, "tnum"),
        TITLE(850, null),
    }

    private const val ROND_AXIS = "ROND"
    private const val TILE_FEATURES = "lnum"
    private const val TITLE_TRACKING_EM = -0.01f
    private const val FULL_WIDTH = 100f
    private const val NARROW_WIDTH = 87.5f
    private const val CONDENSED_WIDTH = 75f
    private const val FULL_WIDTH_MAX_CHARS = 3
    private const val NARROW_CHARS = 4
}
