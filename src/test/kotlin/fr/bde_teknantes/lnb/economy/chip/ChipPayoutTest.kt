package fr.bde_teknantes.lnb.economy.chip

import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class ChipPayoutTest {
    @Test
    fun `a chip of 100 at a quarter pays 25, whatever the roll`() {
        (0 until 4).forEach { roll -> assertEquals(25, ChipPayout.value(100, 1, 4, roll)) }
    }

    @Test
    fun `multipliers above one pay the stake and the winnings`() {
        assertEquals(2000, ChipPayout.value(100, 20, 1, 0))
        assertEquals(250, ChipPayout.value(100, 5, 2, 1))
    }

    @Test
    fun `a fraction of an LNBit is paid one time in so many, on average fairly`() {
        val paid = (0 until 4).map { roll -> ChipPayout.value(1, 1, 4, roll) }
        assertEquals(listOf(1L, 0L, 0L, 0L), paid)
        assertEquals(1, paid.sum())
    }

    @Test
    fun `nothing for a lost bet or no stake`() {
        assertEquals(0, ChipPayout.value(100, 0, 1, 0))
        assertEquals(0, ChipPayout.value(0, 2, 1, 0))
        assertThrows(IllegalArgumentException::class.java) { ChipPayout.value(100, 1, 0, 0) }
    }
}
