package fr.bde_teknantes.lnb.economy.casino

import fr.bde_teknantes.lnb.economy.casino.Exposure.Runner
import fr.bde_teknantes.lnb.economy.casino.Exposure.Stake
import kotlin.test.Test
import kotlin.test.assertEquals

class ExposureTest {
    @Test
    fun `payouts are rounded up in whole chips`() {
        // 3 chips of 25 at 5/2: 7.5 chips, rounded up to 8.
        assertEquals(200, Exposure.payout(Stake(25, 3), 5, 2))
        assertEquals(100, Exposure.payout(Stake(100, 1), 1, 1))
    }

    @Test
    fun `slots at 100x create 99 times the stake`() {
        assertEquals(99 * 50L, Exposure.perStake(listOf(Stake(5, 10)), 100, 1))
    }

    @Test
    fun `plinko balls each win the best pocket`() {
        // 32 quarters per ball, 3 balls of one 100 chip.
        assertEquals(3 * (800L - 100), Exposure.perStake(List(3) { Stake(100, 1) }, 32, 4))
    }

    @Test
    fun `derby worst case is the richest winner lane with the next richest refunded`() {
        val runners = listOf(Runner(0, listOf(Stake(100, 2))), Runner(1, listOf(Stake(25, 4))), Runner(2, listOf(Stake(1, 10))))
        // Lane 0 wins (3x 200 = 600, net +400), lane 1 runner-up (net 0), lane 2 loses (net -10).
        assertEquals(400 - 10L, Exposure.derby(runners, 3, 1))
    }

    @Test
    fun `a lone derby runner can lose everything but the worst case is a win`() {
        assertEquals(50L, Exposure.derby(listOf(Runner(3, listOf(Stake(25, 1)))), 3, 1))
    }

    @Test
    fun `blackjack worst case is the doubled wager winning 2x`() {
        // Natural: 5/2 of 100 = 250, net 150. Doubled: 200 at 4/2 = 400, net 200.
        assertEquals(200L, Exposure.table(listOf(Stake(100, 1)), 2, 5, 4, 2))
    }

    @Test
    fun `poker worst case is a royal flush on the called wager`() {
        // Ante 1x100, call makes 300, royal flush pays 105/3 of 300 = 10500, net 10200.
        assertEquals(10_200L, Exposure.table(listOf(Stake(100, 1)), 3, 0, 105, 3))
    }

    @Test
    fun `no stakes, no exposure`() {
        assertEquals(0, Exposure.perStake(emptyList(), 100, 1))
        assertEquals(0, Exposure.derby(emptyList(), 3, 1))
    }
}
