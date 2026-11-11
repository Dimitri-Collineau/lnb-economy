package fr.bde_teknantes.lnb.economy.chip

import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class DenominationsTest {
    @Test
    fun `largest chips first`() {
        assertEquals(listOf(1000 to 1L, 500 to 1L, 100 to 2L, 25 to 1L, 5 to 1L, 1 to 3L), Denominations.breakdown(1733))
    }

    @Test
    fun `a ceiling on the chip value`() {
        assertEquals(listOf(25 to 4L), Denominations.breakdown(100, largest = 25))
        assertEquals(listOf(5 to 2L, 1 to 2L), Denominations.breakdown(12, largest = 5))
    }

    @Test
    fun `nothing for zero`() {
        assertEquals(emptyList(), Denominations.breakdown(0))
    }

    @Test
    fun `the total is always kept`() {
        for (amount in 0L..3000L) {
            for (largest in Denominations.ALL) {
                assertEquals(amount, Denominations.breakdown(amount, largest).sumOf { (value, count) -> value * count })
            }
        }
    }

    @Test
    fun `only chip values as a ceiling`() {
        assertThrows(IllegalArgumentException::class.java) { Denominations.breakdown(10, largest = 10) }
    }
}
