package fr.bde_teknantes.lnb.economy.shop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ShopQuoteTest {
    private val quote = ShopQuote(lotSize = 16, sellPrice = 40, buyPrice = 25, stock = 70, room = 40, carried = 100)

    @Test
    fun `whole lots only, from the stock`() {
        assertEquals(4, quote.maxBuyLots)
        assertEquals(160, quote.buyTotal(4))
    }

    @Test
    fun `selling back is limited by what the player carries and by the shop's room`() {
        assertEquals(2, quote.maxSellLots)
        assertEquals(1, quote.copy(carried = 20).maxSellLots)
        assertEquals(50, quote.sellTotal(2))
    }

    @Test
    fun `a shop that doesn't buy back takes nothing`() {
        val noBuyBack = quote.copy(buyPrice = 0)
        assertFalse(noBuyBack.buysBack)
        assertEquals(0, noBuyBack.maxSellLots)
    }

    @Test
    fun `lots stay between one and the most possible`() {
        assertEquals(1, quote.clamp(0))
        assertEquals(4, quote.clamp(99))
        assertEquals(1, quote.copy(stock = 0, carried = 0).clamp(5))
    }
}
