package com.clayboicardi.game2048.engine

/** Square board side N in 3..8 and its win target (D-030, D-036). */
@JvmInline
value class BoardSize private constructor(val n: Int) {
    val cellCount: Int
        get() = n * n

    /** 8 (= 256) on 3×3, otherwise 11 (= 2048) (D-036). */
    val targetExp: Int
        get() = if (n == MIN_N) TARGET_EXP_3X3 else TARGET_EXP_DEFAULT

    override fun toString(): String = "${n}x$n"

    companion object {
        const val MIN_N = 3
        const val MAX_N = 8
        const val DEFAULT_N = 4
        const val TARGET_EXP_3X3 = 8
        const val TARGET_EXP_DEFAULT = 11
        val ALL: List<BoardSize> = (MIN_N..MAX_N).map(::BoardSize)
        val DEFAULT: BoardSize = BoardSize(DEFAULT_N)

        fun of(n: Int): BoardSize {
            require(n in MIN_N..MAX_N) { "Board size $n is outside $MIN_N..$MAX_N" }
            return BoardSize(n)
        }
    }
}
