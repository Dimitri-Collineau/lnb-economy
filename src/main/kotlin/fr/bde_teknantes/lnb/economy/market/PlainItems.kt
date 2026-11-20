package fr.bde_teknantes.lnb.economy.market

import net.minecraft.world.item.ItemStack

/**
 * The Market only takes plain items: nothing that sets one stack apart from another of the same item. On 1.21.1 an
 * item's "NBT" is its data components, so plain means the stack has none of its own: no damage, enchantments, custom
 * name, lore, contents... Every component equals the item's default.
 */
object PlainItems {
    fun isPlain(stack: ItemStack): Boolean = !stack.isEmpty && stack.componentsPatch.isEmpty
}
