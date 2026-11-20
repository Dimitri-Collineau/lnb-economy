package fr.bde_teknantes.lnb.economy.market

import kotlin.test.Test
import kotlin.test.assertEquals

class MarketViewsTest {
    @Test
    fun `a short history is sent as it is`() {
        assertEquals(listOf(1f, 2f, 3f), MarketViews.downsample(listOf(1f, 2f, 3f), 96))
    }

    @Test
    fun `a long history keeps its first and last point and its size`() {
        val long = List(1000) { it.toFloat() }
        val small = MarketViews.downsample(long, 96)
        assertEquals(96, small.size)
        assertEquals(0f, small.first())
        assertEquals(999f, small.last())
        assertEquals(small, small.sorted())
    }
}
