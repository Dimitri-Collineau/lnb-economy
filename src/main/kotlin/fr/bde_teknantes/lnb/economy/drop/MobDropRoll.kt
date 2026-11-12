package fr.bde_teknantes.lnb.economy.drop

/**
 * What a killed mob gives, without Minecraft: nothing most of the time, else a whole number of LNBits between
 * [min] and [max], both included. [chance] is from 0 (never) to 1 (always).
 */
class MobDropRoll(private val chance: Double, private val min: Int, private val max: Int) {
    /** [luck] and [spread] are two independent draws in [0, 1). Returns 0 for no drop. */
    fun amount(luck: Double, spread: Double): Long {
        if (chance <= 0.0 || luck >= chance) return 0
        val low = maxOf(1, minOf(min, max))
        val high = maxOf(low, maxOf(min, max))
        return (low + (spread * (high - low + 1)).toInt().coerceIn(0, high - low)).toLong()
    }
}
