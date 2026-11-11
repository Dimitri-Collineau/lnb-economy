package fr.bde_teknantes.lnb.economy.chip

/**
 * A game's payout in LNBits rather than in items. MineVegas pays `stake count × numerator / denominator` items of
 * the stake's kind, which only works when every item is worth the same: one chip of 100 at ×1/4 would pay a whole chip
 * one time in four, and nothing otherwise. Chips pay their value instead, so 100 at ×1/4 pays 25.
 */
object ChipPayout {
    /**
     * [stakeValue] × [numerator] / [denominator] in whole LNBits. The fraction left over, if any, is paid as one more
     * LNBit when [roll] (0 until [denominator], from the game) falls under it: fair on average, as MineVegas does.
     */
    fun value(stakeValue: Long, numerator: Int, denominator: Int, roll: Int): Long {
        require(numerator >= 0 && denominator > 0) { "Invalid payout multiplier" }
        if (stakeValue <= 0) return 0
        val amount = Math.multiplyExact(stakeValue, numerator.toLong())
        val whole = amount / denominator
        val rest = amount % denominator
        return whole + if (Math.floorMod(roll, denominator) < rest) 1 else 0
    }
}
