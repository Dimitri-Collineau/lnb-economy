package fr.bde_teknantes.lnb.economy.market

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.ItemEnchantments
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlainItemsTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            net.minecraft.data.registries.VanillaRegistries.createLookup()
        }
    }

    @Test
    fun `a bare stack is plain`() {
        assertTrue(PlainItems.isPlain(ItemStack(Items.COBBLESTONE, 64)))
    }

    @Test
    fun `an empty stack is not`() {
        assertFalse(PlainItems.isPlain(ItemStack.EMPTY))
    }

    @Test
    fun `a name, damage or enchantments make a stack unplain`() {
        assertFalse(PlainItems.isPlain(ItemStack(Items.COBBLESTONE).apply { set(DataComponents.CUSTOM_NAME, Component.literal("Rock")) }))
        assertFalse(PlainItems.isPlain(ItemStack(Items.IRON_PICKAXE).apply { damageValue = 3 }))
        assertFalse(PlainItems.isPlain(ItemStack(Items.BOOK).apply { set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).let { } ; set(DataComponents.LORE, net.minecraft.world.item.component.ItemLore(listOf(Component.literal("x")))) }))
    }

    @Test
    fun `setting a component to its default value stays plain`() {
        val stack = ItemStack(Items.COBBLESTONE)
        stack.set(DataComponents.MAX_STACK_SIZE, 64)
        assertTrue(PlainItems.isPlain(stack))
    }
}
