package fr.bde_teknantes.lnb.economy.shop

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StockTest {
    private val diamond = ItemStack(Items.DIAMOND)

    private fun container(vararg stacks: ItemStack) = SimpleContainer(4).apply { stacks.forEachIndexed { i, s -> setItem(i, s) } }

    @Test
    fun `only identical items count, components included`() {
        val renamed = ItemStack(Items.DIAMOND, 5).apply { set(DataComponents.CUSTOM_NAME, Component.literal("Fake")) }
        val stock = container(ItemStack(Items.DIAMOND, 10), renamed, ItemStack(Items.DIAMOND, 3))
        assertEquals(13, Stock.count(stock, diamond))
    }

    @Test
    fun `takes exactly the amount asked, or nothing`() {
        val stock = container(ItemStack(Items.DIAMOND, 64), ItemStack(Items.DIAMOND, 10))

        assertNull(Stock.take(stock, diamond, 75))
        assertEquals(74, Stock.count(stock, diamond))

        val taken = Stock.take(stock, diamond, 70)
        assertEquals(listOf(64, 6), taken?.map { it.count })
        assertEquals(4, Stock.count(stock, diamond))
    }

    @Test
    fun `room counts empty slots and what matching stacks can still take`() {
        val stock = container(ItemStack(Items.DIAMOND, 60), ItemStack(Items.STONE, 1))
        assertEquals(4 + 64 + 64, Stock.room(stock, diamond))
    }

    @Test
    fun `insert fills matching stacks first and hands back what doesn't fit`() {
        val stock = container(ItemStack(Items.DIAMOND, 60), ItemStack(Items.STONE, 1), ItemStack(Items.STONE, 1))
        val left = Stock.insert(stock, Stock.split(diamond, 80))
        assertEquals(64, stock.getItem(0).count)
        assertEquals(64, stock.getItem(3).count)
        assertEquals(listOf(12), left.map { it.count })
    }
}
