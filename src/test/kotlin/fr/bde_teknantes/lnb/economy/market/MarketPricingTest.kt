package fr.bde_teknantes.lnb.economy.market

import fr.bde_teknantes.lnb.economy.market.MarketPricing.Curve
import fr.bde_teknantes.lnb.economy.market.MarketPricing.Window
import kotlin.test.Test
import kotlin.test.assertEquals

/** The vectors of section 8 of the site's market-mod.md. */
class MarketPricingTest {
    private val curve = Curve(p0 = 0.05, pMin = 0.0025, pMax = 0.15, s0 = 120_000.0, sMin = 12_000.0, sMax = 1_200_000.0, k = 1.3, alpha = 0.05)
    private val log = curve.copy(k = 1.0)
    private val sigma = 0.15

    private fun window(nominal: Double) = Window(nominal, q1 = 5000.0, q2 = 15000.0, mid = 0.5, tail = 0.1)

    private fun near(expected: Double, actual: Double, tolerance: Double = 1e-6) =
        assertEquals(expected, actual, tolerance, "expected $expected, was $actual")

    @Test
    fun `thresholds of the flat pieces`() {
        near(51542.430769, curve.sA)
        near(1202191.216004, curve.sB)
        near(40_000.0, log.sA, 1e-6)
        near(2_400_000.0, log.sB, 1e-3)
    }

    @Test
    fun `reference price`() {
        near(0.050000, curve.pRef(120_000.0))
        near(0.123114, curve.pRef(60_000.0))
        near(0.025738, curve.pRef(200_000.0))
        near(0.045059, curve.pRef(130_000.0))
        near(0.150000, curve.pRef(12_000.0))
        near(0.150000, curve.pRef(30_000.0))
        near(0.002506, curve.pRef(1_200_000.0))
        near(0.002500, curve.pRef(1_500_000.0))
    }

    private fun sale(quantity: Int, stock: Double, nominal: Double, c: Curve = curve) = MarketPricing.sell(c, sigma, stock, quantity, window(nominal))

    @Test
    fun `sales`() {
        sale(1, 120_000.0, 0.0).let { assertEquals(4, it.totalCents); near(120_001.0, it.newStock); near(0.05, it.windowAfter) }
        sale(1000, 120_000.0, 0.0).let { assertEquals(4227, it.totalCents); near(121_000.0, it.newStock); near(49.730885, it.windowAfter) }
        sale(2304, 120_000.0, 0.0).let { assertEquals(9671, it.totalCents); near(122_304.0, it.newStock); near(113.783137, it.windowAfter) }
        sale(10_000, 120_000.0, 0.0).let { assertEquals(40_335, it.totalCents); near(130_000.0, it.newStock); near(474.535973, it.windowAfter) }
        sale(100_000, 120_000.0, 0.0).let { assertEquals(282_652, it.totalCents); near(220_000.0, it.newStock); near(3325.317811, it.windowAfter) }
        sale(20_000, 120_000.0, 4000.0).let { assertEquals(76_826, it.totalCents); near(140_000.0, it.newStock); near(4903.843783, it.windowAfter) }
        sale(100_000, 120_000.0, 4000.0).let { assertEquals(197_672, it.totalCents); near(181_187.853730, it.newStock); near(7651.121393, it.windowAfter) }
        sale(2304, 120_000.0, 16_000.0).let { assertEquals(977, it.totalCents); near(120_230.4, it.newStock); near(16_115.056442, it.windowAfter) }
        sale(10_000, 1_150_000.0, 0.0).let { assertEquals(2238, it.totalCents); near(1_160_000.0, it.newStock); near(26.336199, it.windowAfter) }
        sale(10_000, 1_195_000.0, 0.0).let { assertEquals(2132, it.totalCents); near(1_200_000.0, it.newStock); near(25.093405, it.windowAfter) }
        sale(10_000, 1_200_000.0, 0.0).let { assertEquals(2130, it.totalCents); near(1_200_000.0, it.newStock); near(25.059362, it.windowAfter) }
        sale(1000, 1_200_000.0, 16_000.0).let { assertEquals(21, it.totalCents); near(1_200_000.0, it.newStock); near(16_002.505936, it.windowAfter) }
    }

    @Test
    fun `a sale across q1 has two brackets that add up`() {
        val result = sale(100_000, 120_000.0, 4000.0)
        assertEquals(2, result.brackets.size)
        assertEquals(1.0, result.brackets[0].rate)
        near(22_375.707459, result.brackets[0].quantity, 1e-4)
        assertEquals(85_000L, result.brackets[0].totalCents)
        assertEquals(0.5, result.brackets[1].rate)
        near(77_624.292541, result.brackets[1].quantity, 1e-4)
        assertEquals(112_672L, result.brackets[1].totalCents)
        assertEquals(result.totalCents, result.brackets.sumOf { it.totalCents })
    }

    @Test
    fun `the mint budget caps a payout`() {
        val result = MarketPricing.sell(curve, sigma, 120_000.0, 10_000, window(0.0), mintRemaining = 1000)
        assertEquals(1000, result.totalCents)
        assertEquals(true, result.capped)
        assertEquals(1000L, result.brackets.sumOf { it.totalCents })
        assertEquals(false, sale(10_000, 120_000.0, 0.0).capped)
    }

    private fun buy(quantity: Int, stock: Double, c: Curve = curve) = MarketPricing.buy(c, sigma, stock, quantity)

    @Test
    fun `purchases`() {
        buy(1, 120_000.0).let { assertEquals(6, it.totalCents); near(119_999.0, it.newStock) }
        buy(64, 120_000.0).let { assertEquals(369, it.totalCents); near(119_936.0, it.newStock) }
        buy(2304, 120_000.0).let { assertEquals(13_416, it.totalCents); near(117_696.0, it.newStock) }
        buy(10_000, 120_000.0).let { assertEquals(60_829, it.totalCents); near(110_000.0, it.newStock) }
        buy(10_000, 20_000.0).let { assertEquals(172_500, it.totalCents); near(12_000.0, it.newStock) }
        buy(100, 12_050.0).let { assertEquals(1725, it.totalCents); near(12_000.0, it.newStock) }
        buy(64, 12_000.0).let { assertEquals(1104, it.totalCents); near(12_000.0, it.newStock) }
        buy(100, 1_300_000.0).let { assertEquals(29, it.totalCents); near(1_299_900.0, it.newStock) }
    }

    @Test
    fun `drift`() {
        near(137_519.341460, curve.drift(180_000.0, 24.0))
        near(103_391.311169, curve.drift(12_000.0, 36.5))
        near(180_000.0, curve.drift(180_000.0, -5.0)) // never negative time
    }

    @Test
    fun `k equal to one uses the logarithm`() {
        near(0.050000, log.pRef(120_000.0))
        near(0.100000, log.pRef(60_000.0))
        near(0.030000, log.pRef(200_000.0))
        sale(10_000, 120_000.0, 0.0, log).let { assertEquals(40_821, it.totalCents); near(480.256246, it.windowAfter) }
        sale(100_000, 120_000.0, 0.0, log).let { assertEquals(309_129, it.totalCents); near(3636.814821, it.windowAfter) }
        assertEquals(60_038, buy(10_000, 120_000.0, log).totalCents)
        assertEquals(369, buy(64, 120_000.0, log).totalCents)
    }

    @Test
    fun `properties`() {
        val first = sale(50_000, 120_000.0, 0.0)
        val second = sale(50_000, first.newStock, first.windowAfter)
        assertEquals(sale(100_000, 120_000.0, 0.0).totalCents.toDouble(), (first.totalCents + second.totalCents).toDouble(), 2.0)
        for ((a, b) in listOf(10_000.0 to 40_000.0, 120_000.0 to 130_000.0, 60_000.0 to 1_500_000.0, 1_100_000.0 to 1_300_000.0)) {
            near(b, curve.inverse(a, curve.integral(a, b)), 1e-4)
        }
    }
}
