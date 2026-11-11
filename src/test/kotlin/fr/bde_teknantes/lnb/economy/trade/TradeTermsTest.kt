package fr.bde_teknantes.lnb.economy.trade

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TradeTermsTest {
    @Test
    fun `only the difference moves, from whoever gave more`() {
        assertEquals(TradeTerms.Settlement(true, 70), TradeTerms.settle(100, 30))
        assertEquals(TradeTerms.Settlement(false, 5), TradeTerms.settle(0, 5))
        assertNull(TradeTerms.settle(40, 40))
        assertNull(TradeTerms.settle(0, 0))
    }

    @Test
    fun `offers are merged by item and components`() {
        val named = ItemStack(Items.DIAMOND, 3).apply { set(DataComponents.CUSTOM_NAME, Component.literal("Rare")) }
        val totals = TradeTerms.totals(listOf(ItemStack(Items.DIAMOND, 64), ItemStack.EMPTY, named, ItemStack(Items.DIAMOND, 10)))
        assertEquals(listOf(74, 3), totals.map { it.count })
    }

    @Test
    fun `items are taken all together, or not at all`() {
        val inventory = SimpleContainer(ItemStack(Items.DIAMOND, 64), ItemStack(Items.DIAMOND, 20), ItemStack(Items.EMERALD, 5))
        val slots = 0 until inventory.containerSize

        assertNull(TradeTerms.take(inventory, TradeTerms.totals(listOf(ItemStack(Items.DIAMOND, 80), ItemStack(Items.EMERALD, 6))), slots))
        assertEquals(84, inventory.countItem(Items.DIAMOND), "nothing taken when one item is missing")

        val taken = TradeTerms.take(inventory, TradeTerms.totals(listOf(ItemStack(Items.DIAMOND, 80), ItemStack(Items.EMERALD, 5))), slots)
        assertEquals(listOf(64, 16, 5), taken?.map { it.count })
        assertEquals(4, inventory.countItem(Items.DIAMOND))
        assertEquals(0, inventory.countItem(Items.EMERALD))
    }
}
