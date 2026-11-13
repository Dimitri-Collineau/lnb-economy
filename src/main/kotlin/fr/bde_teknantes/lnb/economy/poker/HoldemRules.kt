package fr.bde_teknantes.lnb.economy.poker

/**
 * Casino hold'em, the house's rules. Each player antes and gets two cards; the dealer gets two, face down; three board
 * cards come. A player then folds (the ante is lost) or calls for twice the ante. Two more board cards, and each hand
 * that called is compared with the dealer's, both using the board.
 *
 * The dealer **qualifies** with a pair of fours or better. If he doesn't, the ante is paid from the table below and
 * the call comes back. If he does and loses, the ante is paid from the table and the call is paid 1 to 1; on a tie
 * everything comes back; if he wins, the player loses both.
 */
object HoldemRules {
    /** A call costs this many antes. */
    const val CALL_ANTES = 2

    /** The most a seat can get back, in antes: the best hand, called, against a dealer who qualifies. */
    const val MAX_RETURN_ANTES = 3 + CALL_ANTES + 100

    /** The most chip value a seat can add to those in circulation: what it can get back, less what it staked. */
    const val MAX_NET_ANTES = MAX_RETURN_ANTES - (1 + CALL_ANTES)

    enum class Result { FOLD, LOSE, PUSH, NO_QUALIFY, WIN }

    /** The rank of a four, the lowest pair the dealer plays with. */
    private const val FOUR = 2

    /** What the ante wins on top of itself, in antes, for the player's final hand. */
    fun antePays(score: Long): Int = when {
        Hands.isRoyal(score) -> 100
        Hands.category(score) == Hands.STRAIGHT_FLUSH -> 20
        Hands.category(score) == Hands.QUADS -> 10
        Hands.category(score) == Hands.FULL_HOUSE -> 3
        Hands.category(score) == Hands.FLUSH -> 2
        else -> 1
    }

    /** Whether the dealer's hand plays: a pair of fours or better. */
    fun qualifies(dealerScore: Long): Boolean =
        Hands.category(dealerScore) > Hands.PAIR || (Hands.category(dealerScore) == Hands.PAIR && Hands.topRank(dealerScore) >= FOUR)

    fun result(playerScore: Long, dealerScore: Long): Result = when {
        !qualifies(dealerScore) -> Result.NO_QUALIFY
        playerScore > dealerScore -> Result.WIN
        playerScore < dealerScore -> Result.LOSE
        else -> Result.PUSH
    }

    /** Everything a seat gets back, stakes included, for an [ante]. A fold or a loss gets nothing. */
    fun payout(ante: Long, result: Result, playerScore: Long): Long = when (result) {
        Result.FOLD, Result.LOSE -> 0
        Result.PUSH -> ante * (1 + CALL_ANTES)
        Result.NO_QUALIFY -> ante * (1 + CALL_ANTES + antePays(playerScore))
        Result.WIN -> ante * (1 + CALL_ANTES + CALL_ANTES + antePays(playerScore))
    }
}
