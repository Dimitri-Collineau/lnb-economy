package fr.bde_teknantes.lnb.economy.chip

/** The chip values, in LNBits. Greedy change is optimal for these. */
object Denominations {
    val ALL: List<Int> = listOf(1, 5, 25, 100, 500, 1000)

    /**
     * The fewest chips worth [amount], using no chip above [largest]: pairs of (value, count), largest first.
     */
    fun breakdown(amount: Long, largest: Int = ALL.last()): List<Pair<Int, Long>> {
        require(amount >= 0) { "Negative amount $amount" }
        require(largest in ALL) { "Not a chip value: $largest" }
        var left = amount
        val result = mutableListOf<Pair<Int, Long>>()
        for (value in ALL.filter { it <= largest }.sortedDescending()) {
            val count = left / value
            if (count > 0) {
                result.add(value to count)
                left -= count * value
            }
        }
        return result
    }
}
