package fr.bde_teknantes.lnb.economy.market

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketStateTest {
    private val start = Instant.parse("2026-11-21T10:00:00Z")
    private var now = start.toEpochMilli()

    private fun item(id: String, stock: Double = 130_000.0, tier: Int = 1) = MarketItem(
        id, tier, sellable = true, buyable = true, p0 = 0.05, pMin = 0.0025, pMax = 0.15, s0 = 120_000.0, sMin = 12_000.0, sMax = 1_200_000.0,
        k = 1.3, alpha = 0.05, stock = stock, stockAt = start.toString(),
    )

    private fun state(revision: Long, vararg items: MarketItem) = MarketStateView(
        revision, start.toString(), MarketSettings(0.15, 1.0, 2304, sell = true, buy = true),
        listOf(MarketTier(1, 500_000, 1_500_000, 50.0, 10.0)), 83_000_000, items.toList(),
    )

    private fun market() = MarketState { now }

    @Test
    fun `the state replaces everything`() {
        val market = market()
        market.apply(state(1, item("a"), item("b")))
        market.apply(state(2, item("b")))
        assertNull(market.item("a"))
        assertNotNull(market.item("b"))
        assertEquals(2, market.revision)
        assertTrue(market.isOpen)
    }

    @Test
    fun `stock events replace the stock, not drifted`() {
        val market = market()
        market.apply(state(5, item("a")))
        assertFalse(market.onStocks(5, listOf(MarketEventItem("a", 150_000.0, start.toString()), MarketEventItem("gone", 1.0, start.toString()))))
        assertEquals(150_000.0, market.stockNow("a")!!, 1e-9)
    }

    @Test
    fun `an event ahead of our revision means read the state again`() {
        val market = market()
        assertTrue(market.onStocks(1, emptyList())) // no copy yet
        market.apply(state(5, item("a")))
        assertTrue(market.onStocks(6, listOf(MarketEventItem("a", 1.0, start.toString()))))
        assertEquals(130_000.0, market.stockNow("a")!!, 1e-9) // untouched
        assertTrue(market.needsRefresh(6))
        assertFalse(market.needsRefresh(5))
    }

    @Test
    fun `stock drifts toward s0 with time`() {
        val market = market()
        market.apply(state(1, item("a", stock = 180_000.0)))
        now += 24 * 3_600_000L
        assertEquals(137_519.341460, market.stockNow("a")!!, 1e-6)
    }

    @Test
    fun `closing forgets the catalog`() {
        val market = market()
        market.apply(state(1, item("a")))
        market.markClosed()
        assertFalse(market.isOpen)
        assertNull(market.item("a"))
        assertTrue(market.needsRefresh(0))
    }

    @Test
    fun `a tier's thresholds convert from cents to LNBits`() {
        val market = market()
        market.apply(state(1, item("a")))
        val window = market.window(1, 4000.0)!!
        assertEquals(5000.0, window.q1, 1e-9)
        assertEquals(15_000.0, window.q2, 1e-9)
        assertEquals(0.5, window.mid, 1e-9)
        assertEquals(0.1, window.tail, 1e-9)
        assertNull(market.window(9, 0.0))
    }
}
