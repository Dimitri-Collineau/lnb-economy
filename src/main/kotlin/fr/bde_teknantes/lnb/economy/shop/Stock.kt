package fr.bde_teknantes.lnb.economy.shop

import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

/**
 * Counting, taking and storing a shop's item in a container (the shop's stock, or a player's main inventory). Only
 * stacks identical to the template, data components included, count: a damaged or renamed copy is another item.
 */
object Stock {
    fun matches(template: ItemStack, stack: ItemStack): Boolean =
        !template.isEmpty && !stack.isEmpty && ItemStack.isSameItemSameComponents(template, stack)

    fun count(container: Container, template: ItemStack, slots: IntRange = 0 until container.containerSize): Int =
        slots.sumOf { slot -> container.getItem(slot).let { if (matches(template, it)) it.count else 0 } }

    /** How many more of the template fit in [container]. */
    fun room(container: Container, template: ItemStack, slots: IntRange = 0 until container.containerSize): Int {
        val max = template.maxStackSize
        return slots.sumOf { slot ->
            val stack = container.getItem(slot)
            when {
                stack.isEmpty -> max
                matches(template, stack) -> (max - stack.count).coerceAtLeast(0)
                else -> 0
            }
        }
    }

    /** Takes [amount] of the template out of [container], or nothing when it holds fewer. */
    fun take(container: Container, template: ItemStack, amount: Int, slots: IntRange = 0 until container.containerSize): List<ItemStack>? {
        if (amount <= 0 || count(container, template, slots) < amount) return null
        var left = amount
        for (slot in slots.reversed()) {
            if (left == 0) break
            val stack = container.getItem(slot)
            if (!matches(template, stack)) continue
            val taken = minOf(left, stack.count)
            container.removeItem(slot, taken)
            left -= taken
        }
        container.setChanged()
        return split(template, amount)
    }

    /** Puts [stacks] in [container], filling matching stacks first. Returns what didn't fit. */
    fun insert(container: Container, stacks: List<ItemStack>, slots: IntRange = 0 until container.containerSize): List<ItemStack> {
        val left = mutableListOf<ItemStack>()
        for (original in stacks) {
            val stack = original.copy()
            for (slot in slots) {
                if (stack.isEmpty) break
                val there = container.getItem(slot)
                if (ItemStack.isSameItemSameComponents(there, stack) && there.count < there.maxStackSize) {
                    val moved = minOf(stack.count, there.maxStackSize - there.count)
                    there.grow(moved)
                    stack.shrink(moved)
                }
            }
            for (slot in slots) {
                if (stack.isEmpty) break
                if (container.getItem(slot).isEmpty) container.setItem(slot, stack.split(stack.maxStackSize))
            }
            if (!stack.isEmpty) left.add(stack)
        }
        container.setChanged()
        return left
    }

    /** [amount] of the template, in full stacks. */
    fun split(template: ItemStack, amount: Int): List<ItemStack> {
        val stacks = mutableListOf<ItemStack>()
        var left = amount
        while (left > 0) {
            val size = minOf(left, template.maxStackSize)
            stacks.add(template.copyWithCount(size))
            left -= size
        }
        return stacks
    }
}
