package com.clayboicardi.game2048.ios

import com.clayboicardi.game2048.engine.tileValue

/**
 * The number drawn on a tile of exponent [exp] (value 2^exp). Exact for every value that fits a
 * Long; "MAX" for the saturated range play never reaches. (Compact "128K"-style labels for very
 * large tiles are a follow-up; normal play tops out at 2048–131072.)
 */
fun tileLabel(exp: Int): String =
    when {
        exp <= 0 -> ""
        exp < Long.SIZE_BITS - 1 -> tileValue(exp).toString()
        else -> "MAX"
    }
