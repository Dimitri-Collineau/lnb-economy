package fr.bde_teknantes.lnb.economy.drop

import kotlin.test.Test
import kotlin.test.assertEquals

class MobDropRollTest {
    private val roll = MobDropRoll(chance = 0.1, min = 1, max = 10)

    @Test
    fun `drops nothing when luck is at or above the chance`() {
        assertEquals(0, roll.amount(luck = 0.1, spread = 0.5))
        assertEquals(0, roll.amount(luck = 0.99, spread = 0.5))
    }

    @Test
    fun `spreads evenly from min to max, both included`() {
        assertEquals(1, roll.amount(luck = 0.0, spread = 0.0))
        assertEquals(6, roll.amount(luck = 0.05, spread = 0.5))
        assertEquals(10, roll.amount(luck = 0.09, spread = 0.999999))
    }

    @Test
    fun `a chance of 0 never drops and a chance of 1 always does`() {
        assertEquals(0, MobDropRoll(0.0, 1, 10).amount(0.0, 0.0))
        assertEquals(3, MobDropRoll(1.0, 3, 3).amount(0.999, 0.7))
    }

    @Test
    fun `tolerates a min above the max and amounts below 1`() {
        assertEquals(5, MobDropRoll(1.0, 9, 5).amount(0.0, 0.0))
        assertEquals(1, MobDropRoll(1.0, -4, 0).amount(0.0, 0.9))
    }
}
