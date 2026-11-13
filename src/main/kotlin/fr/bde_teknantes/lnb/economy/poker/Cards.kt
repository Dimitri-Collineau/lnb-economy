package fr.bde_teknantes.lnb.economy.poker

/**
 * Playing cards as numbers from 0 to 51: [rank] 0 is a two and 12 an ace, [suit] 0 to 3 is spades, hearts, diamonds,
 * clubs. [HIDDEN] stands for a card lying face down.
 */
object Cards {
    const val COUNT = 52
    const val RANKS = 13
    const val HIDDEN = -1
    const val ACE = 12

    private const val RANK_NAMES = "23456789TJQKA"
    private const val SUIT_NAMES = "shdc"

    fun rank(card: Int): Int = card % RANKS

    fun suit(card: Int): Int = card / RANKS

    fun of(rank: Int, suit: Int): Int = suit * RANKS + rank

    /** What a card's corner shows: `10` for a ten, else one character. */
    fun rankLabel(card: Int): String = if (rank(card) == 8) "10" else RANK_NAMES[rank(card)].toString()

    /** `As`, `Td`, `2c`: for tests and logs. */
    fun parse(text: String): Int = of(RANK_NAMES.indexOf(text[0]), SUIT_NAMES.indexOf(text[1])).also { require(it in 0 until COUNT) { text } }

    fun parseAll(text: String): List<Int> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::parse)

    fun deck(): List<Int> = (0 until COUNT).toList()
}
