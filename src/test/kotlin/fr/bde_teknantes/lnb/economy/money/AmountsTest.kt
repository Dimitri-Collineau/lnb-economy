package fr.bde_teknantes.lnb.economy.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountsTest {
    @Test
    fun `whole amounts drop the decimals`() {
        assertEquals("0", Amounts.format(0, "en_us"))
        assertEquals("1", Amounts.format(100, "en_us"))
        assertEquals("1,250", Amounts.format(125_000, "en_us"))
        assertEquals("1,000,000,000,000", Amounts.format(100_000_000_000_000, "en_gb"))
    }

    @Test
    fun `cents always show two digits`() {
        assertEquals("0.01", Amounts.format(1, "en_us"))
        assertEquals("0.10", Amounts.format(10, "en_us"))
        assertEquals("0.99", Amounts.format(99, "en_us"))
        assertEquals("1.05", Amounts.format(105, "en_us"))
        assertEquals("1,000,000.12", Amounts.format(100_000_012, "en_us"))
        assertEquals("1,234.55", Amounts.format(123_455, "en_us"))
    }

    @Test
    fun `french groups with no-break spaces and a decimal comma`() {
        assertEquals("1 250", Amounts.format(125_000, "fr_fr"))
        assertEquals("12 345 678,90", Amounts.format(1_234_567_890, "fr_ca"))
        assertEquals("0,05", Amounts.format(5, "fr_fr"))
    }

    @Test
    fun `negative amounts keep their sign`() {
        assertEquals("-1,250", Amounts.format(-125_000, "en_us"))
        assertEquals("-0.50", Amounts.format(-50, "en_us"))
    }

    @Test
    fun `parse reads LNBits with up to two decimals`() {
        assertEquals(10, Amounts.parse("0.1"))
        assertEquals(10, Amounts.parse("0.10"))
        assertEquals(10, Amounts.parse("0,1"))
        assertEquals(1200, Amounts.parse("12"))
        assertEquals(1234, Amounts.parse("12.34"))
        assertEquals(1, Amounts.parse("0.01"))
        assertEquals(500, Amounts.parse("5."))
        assertEquals(100_000_000_000_000, Amounts.parse("1000000000000"))
    }

    @Test
    fun `parse never rounds`() {
        for (bad in listOf("12.345", "-1", "1e3", "", ".", ".5", "abc", "1 000", "1.2.3", "99999999999999")) {
            assertNull(Amounts.parse(bad), bad)
        }
    }

    @Test
    fun `zero parses, callers refuse it`() {
        assertEquals(0, Amounts.parse("0"))
    }

    @Test
    fun `plain is what parse reads back`() {
        for (cents in listOf(1L, 10L, 100L, 105L, 123_456L, 100_000_012L)) assertEquals(cents, Amounts.parse(Amounts.plain(cents)))
        assertEquals("1.5", Amounts.plain(150))
        assertEquals("1.05", Amounts.plain(105))
        assertEquals("2", Amounts.plain(200))
    }

    @Test
    fun `typing filter allows a partial amount`() {
        for (ok in listOf("", "1", "1.", "1.2", "1,25", "0.01")) assertEquals(true, Amounts.isTypable(ok), ok)
        for (no in listOf("1.234", "a", "-1", "1..2")) assertEquals(false, Amounts.isTypable(no), no)
    }

    @Test
    fun `whole LNBits convert exactly`() {
        assertEquals(500, Amounts.fromLnbits(5))
        assertEquals(5, Amounts.toWholeLnbits(599))
        assertEquals(true, runCatching { Amounts.fromLnbits(Long.MAX_VALUE) }.exceptionOrNull() is ArithmeticException)
    }
}
