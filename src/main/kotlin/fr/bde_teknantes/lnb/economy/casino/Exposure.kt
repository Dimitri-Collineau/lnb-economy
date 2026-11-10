package fr.bde_teknantes.lnb.economy.casino

/**
 * The most chip value a game can create, net of the stakes it destroys: what the casino's free balance must cover
 * before the game starts. Payouts are MineVegas's, rounded up as with `roll = 0`, in the stake's own chips.
 */
object Exposure {
    /** [count] chips worth [value] each. */
    data class Stake(val value: Int, val count: Int) {
        val total: Long get() = value.toLong() * count
    }

    /** One Duck Derby participant: the lane they bet on, and their stakes. */
    data class Runner(val lane: Int, val stakes: List<Stake>)

    const val DERBY_LANES = 4

    /** Total paid back for [stake] at [numerator]/[denominator], stake included, rounded up. */
    fun payout(stake: Stake, numerator: Int, denominator: Int): Long =
        stake.value * Math.ceilDiv(stake.count.toLong() * numerator, denominator.toLong())

    /** Slots, roulette, wheel, Plinko, legacy Derby: every stake can win [numerator]/[denominator]. */
    fun perStake(stakes: List<Stake>, numerator: Int, denominator: Int): Long =
        stakes.sumOf { payout(it, numerator, denominator) - it.total }

    /** Derby races: the winner lane gets [win]x, the runner-up lane its stake back, the rest lose. Every pair is tried. */
    fun derby(runners: List<Runner>, win: Int, second: Int): Long {
        var worst = Long.MIN_VALUE
        for (winner in 0 until DERBY_LANES) {
            for (runnerUp in 0 until DERBY_LANES) {
                if (runnerUp == winner) continue
                val net = runners.sumOf { runner ->
                    runner.stakes.sumOf { stake ->
                        when (runner.lane) {
                            winner -> payout(stake, win, 1)
                            runnerUp -> payout(stake, second, 1)
                            else -> 0
                        } - stake.total
                    }
                }
                worst = maxOf(worst, net)
            }
        }
        return if (runners.isEmpty()) 0 else worst
    }

    /**
     * Blackjack and poker, settled at the end of the round. [raise] is what the player can still multiply the wager by
     * (2 for a double down, 3 for a poker call, 1 when nothing is left). [plain] pays the wager as it is (0 when not
     * raising can't win), [raised] the raised wager, both over [denominator].
     */
    fun table(wager: List<Stake>, raise: Int, plain: Int, raised: Int, denominator: Int): Long {
        val raisedWager = wager.map { it.copy(count = it.count * raise) }
        val raisedNet = raisedWager.sumOf { payout(it, raised, denominator) - it.total }
        if (plain <= 0) return raisedNet
        return maxOf(raisedNet, wager.sumOf { payout(it, plain, denominator) - it.total })
    }
}
