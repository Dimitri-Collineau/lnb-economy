package fr.bde_teknantes.lnb.economy.casino.minevegas

import fr.minevegas.casino.BlackjackRound
import fr.minevegas.casino.FortuneWheelGame
import fr.minevegas.casino.PlinkoGame
import fr.minevegas.casino.PokerHands
import fr.minevegas.casino.SlotMachineGame

/**
 * Each game's best payout, stake included, as a multiple of the stake over a denominator. Read from MineVegas's tables
 * where they are public, so a balance change there is picked up; the literals inside its methods are copied
 * (RECON.md, section 5).
 */
object MaxPayouts {
    /** Roulette pays 36x on green and 2x on red or black. Green is colour 2 in `RouletteMenu.stakedColor`. */
    const val ROULETTE_GREEN = 36
    const val ROULETTE_COLOR = 2
    const val GREEN = 2

    /** Derby: winner lane 3x, runner-up lane gets its stake back. */
    const val DERBY_WIN = 3
    const val DERBY_SECOND = 1

    /** A blackjack double down multiplies the wager by 2, a poker call by 3. */
    const val BLACKJACK_DOUBLE = 2
    const val POKER_CALL = 3

    private const val POKER_WIN_THIRDS = 5
    private const val POKER_CATEGORIES = 10
    private const val POKER_CATEGORY_SHIFT = 20

    /** Best row of the pay table (three sevens, 100x). Denominator 1. */
    val slots: Int get() = SlotMachineGame.PAYTABLE.maxOf { it.multiplier() }

    /** Best wheel segment in quarters (20, so 5x). Denominator 4. */
    val wheelQuarters: Int get() = (0 until FortuneWheelGame.SEGMENTS).maxOf { FortuneWheelGame.quarters(it) }

    /** Best Plinko pocket in quarters (32, so 8x) for each ball. Denominator 4. */
    val plinkoQuarters: Int get() = (0 until PlinkoGame.POCKETS).maxOf { PlinkoGame.quarters(it) }

    /** Natural blackjack, 5 halves. Denominator 2. */
    val blackjackNaturalHalves: Int get() = BlackjackRound.Result.BLACKJACK.halves

    /** After a double a natural is impossible: the best other result, 4 halves of the doubled wager. Denominator 2. */
    val blackjackDoubledHalves: Int
        get() = BlackjackRound.Result.entries.filter { it != BlackjackRound.Result.BLACKJACK }.maxOf { it.halves }

    /** Royal flush: 5 + 100 thirds of the called wager (105x the ante). Denominator 3. */
    val pokerThirds: Int
        get() = POKER_WIN_THIRDS + (0 until POKER_CATEGORIES).maxOf { PokerHands.antePays(it shl POKER_CATEGORY_SHIFT) }

    fun describe(): String =
        "slots ${slots}x, roulette ${ROULETTE_GREEN}x on green and ${ROULETTE_COLOR}x on a colour, wheel $wheelQuarters/4, " +
            "plinko $plinkoQuarters/4 per ball, derby ${DERBY_WIN}x, blackjack $blackjackNaturalHalves/2 or " +
            "$blackjackDoubledHalves/2 of the doubled wager, poker $pokerThirds/3 of the called wager"
}
