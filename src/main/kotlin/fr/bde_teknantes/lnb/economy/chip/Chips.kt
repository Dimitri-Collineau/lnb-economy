package fr.bde_teknantes.lnb.economy.chip

import fr.bde_teknantes.lnb.economy.ModContent
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

/** Reading and making chip stacks. */
object Chips {
    fun isChip(stack: ItemStack): Boolean = stack.item is ChipItem

    /** The casino of a chip (handle without `#`), or null for anything else, blank chips included. */
    fun casinoOf(stack: ItemStack): String? = if (isChip(stack)) stack.get(ModContent.CASINO.get()) else null

    /** What a stack of chips is worth, 0 for anything else. */
    fun valueOf(stack: ItemStack): Long = (stack.item as? ChipItem)?.let { it.value.toLong() * stack.count } ?: 0

    /** Whether [stack] is a chip of the casino [handle]. */
    fun isChipOf(stack: ItemStack, handle: String): Boolean = casinoOf(stack) == handle

    /** Chips of [handle] worth exactly [amount], with no chip above [largest], in stacks of at most 64. */
    fun make(handle: String, amount: Long, largest: Int = Denominations.ALL.last()): List<ItemStack> {
        val stacks = mutableListOf<ItemStack>()
        for ((value, count) in Denominations.breakdown(amount, largest)) {
            val item = ModContent.CHIPS.getValue(value).get()
            var left = count
            while (left > 0) {
                val size = minOf(left, item.getDefaultMaxStackSize().toLong()).toInt()
                stacks.add(ItemStack(item, size).apply { set(ModContent.CASINO.get(), handle) })
                left -= size
            }
        }
        return stacks
    }

    /** The value of every chip of [handle] in the player's inventory. */
    fun valueIn(inventory: Inventory, handle: String): Long =
        (0 until inventory.containerSize).sumOf { slot -> inventory.getItem(slot).let { if (isChipOf(it, handle)) valueOf(it) else 0 } }

    /** Removes every chip of [handle] from the inventory and returns their value. */
    fun takeAll(inventory: Inventory, handle: String): Long {
        var total = 0L
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (isChipOf(stack, handle)) {
                total += valueOf(stack)
                inventory.setItem(slot, ItemStack.EMPTY)
            }
        }
        inventory.setChanged()
        return total
    }
}
