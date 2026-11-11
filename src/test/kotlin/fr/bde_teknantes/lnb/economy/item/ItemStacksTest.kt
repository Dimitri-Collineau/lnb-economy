package fr.bde_teknantes.lnb.economy.item

import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemStacksTest {
    companion object {
        private lateinit var registries: HolderLookup.Provider

        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            registries = VanillaRegistries.createLookup()
        }
    }

    @Test
    fun `stacks come back from SNBT with their count and components`() {
        val named = ItemStack(Items.DIAMOND_PICKAXE).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Pioche"))
            set(DataComponents.DAMAGE, 12)
        }
        val stacks = listOf(ItemStack(Items.COBBLESTONE, 64), ItemStack.EMPTY, named)

        val read = ItemStacks.fromSnbt(ItemStacks.toSnbt(stacks, registries), registries)

        assertTrue(read.unreadable.isEmpty())
        assertEquals(2, read.stacks.size, "empty stacks are left out")
        assertEquals(64, read.stacks[0].count)
        assertTrue(ItemStack.isSameItemSameComponents(named, read.stacks[1]))
    }

    @Test
    fun `an item the game no longer knows is kept as it was saved`() {
        val snbt = """{items:[{id:"gone:widget",count:3},{id:"minecraft:stone",count:1}]}"""

        val read = ItemStacks.fromSnbt(snbt, registries)

        assertEquals(listOf(Items.STONE), read.stacks.map { it.item })
        assertEquals("gone:widget", read.unreadable.single().getString("id"))
    }

    @Test
    fun `text that is not SNBT reads as nothing`() {
        val read = ItemStacks.fromSnbt("not snbt {", registries)
        assertTrue(read.stacks.isEmpty() && read.unreadable.isEmpty())
    }
}
