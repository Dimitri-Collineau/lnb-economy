package fr.bde_teknantes.lnb.economy.poker

/**
 * Poker hands: the best five cards out of five to seven, as a score that compares like the hands do (a higher score
 * beats a lower one, equal scores tie).
 */
object Hands {
    const val HIGH_CARD = 0
    const val PAIR = 1
    const val TWO_PAIR = 2
    const val TRIPS = 3
    const val STRAIGHT = 4
    const val FLUSH = 5
    const val FULL_HOUSE = 6
    const val QUADS = 7
    const val STRAIGHT_FLUSH = 8

    /** Room for five ranks under the category. */
    private const val CATEGORY = 13L * 13 * 13 * 13 * 13

    fun category(score: Long): Int = (score / CATEGORY).toInt()

    /** The rank that leads the hand: the pair's for a pair, the highest card's for a straight... */
    fun topRank(score: Long): Int = (score / (CATEGORY / 13) % 13).toInt()

    /** A straight flush up to the ace. */
    fun isRoyal(score: Long): Boolean = score == encode(STRAIGHT_FLUSH, listOf(Cards.ACE))

    fun score(cards: List<Int>): Long {
        require(cards.size in 5..7) { "A hand is scored on 5 to 7 cards" }
        val bySuit = cards.groupBy(Cards::suit)
        val flush = bySuit.values.firstOrNull { it.size >= 5 }?.map(Cards::rank)?.sortedDescending()
        flush?.let { suited -> straightHigh(suited)?.let { return encode(STRAIGHT_FLUSH, listOf(it)) } }

        val ranks = cards.map(Cards::rank)
        // Ranks by how many cards hold them, then by height: [quads or trips..., pairs..., singles...].
        val groups = ranks.groupingBy { it }.eachCount().entries.sortedWith(compareBy({ -it.value }, { -it.key }))
        val distinct = ranks.distinct().sortedDescending()
        fun kickers(count: Int, vararg without: Int) = distinct.filter { it !in without }.take(count)

        val first = groups[0]
        val second = groups.getOrNull(1)
        if (first.value == 4) return encode(QUADS, listOf(first.key) + kickers(1, first.key))
        if (first.value == 3 && second != null && second.value >= 2) return encode(FULL_HOUSE, listOf(first.key, second.key))
        if (flush != null) return encode(FLUSH, flush.take(5))
        straightHigh(distinct)?.let { return encode(STRAIGHT, listOf(it)) }
        if (first.value == 3) return encode(TRIPS, listOf(first.key) + kickers(2, first.key))
        if (first.value == 2 && second != null && second.value == 2) {
            return encode(TWO_PAIR, listOf(first.key, second.key) + kickers(1, first.key, second.key))
        }
        if (first.value == 2) return encode(PAIR, listOf(first.key) + kickers(3, first.key))
        return encode(HIGH_CARD, distinct.take(5))
    }

    /** The top rank of the best straight in [ranks] (distinct, highest first), the five for A-2-3-4-5, or null. */
    private fun straightHigh(ranks: List<Int>): Int? {
        val distinct = ranks.distinct()
        val present = distinct.toSet()
        for (high in distinct) {
            if (high >= 4 && (1..4).all { high - it in present }) return high
        }
        return if (Cards.ACE in present && (0..3).all { it in present }) 3 else null
    }

    private fun encode(category: Int, ranks: List<Int>): Long {
        var score = category.toLong()
        for (i in 0 until 5) score = score * 13 + (ranks.getOrNull(i) ?: 0)
        return score
    }
}
