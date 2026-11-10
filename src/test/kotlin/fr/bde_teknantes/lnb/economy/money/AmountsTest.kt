package fr.bde_teknantes.lnb.economy.money

import kotlin.test.Test
import kotlin.test.assertEquals

class AmountsTest {
    @Test
    fun `english groups with commas`() {
        assertEquals("0", Amounts.format(0, "en_us"))
        assertEquals("999", Amounts.format(999, "en_us"))
        assertEquals("1,250", Amounts.format(1250, "en_us"))
        assertEquals("1,000,000,000,000", Amounts.format(1_000_000_000_000, "en_gb"))
    }

    @Test
    fun `french groups with no-break spaces`() {
        assertEquals("1 250", Amounts.format(1250, "fr_fr"))
        assertEquals("12 345 678", Amounts.format(12_345_678, "fr_ca"))
    }

    @Test
    fun `negative amounts keep their sign`() {
        assertEquals("-1,250", Amounts.format(-1250, "en_us"))
    }

    @Test
    fun `compact amounts start with the symbol`() {
        assertEquals("Ⓛ 1,250", Amounts.compact(1250, "en_us"))
    }
}
