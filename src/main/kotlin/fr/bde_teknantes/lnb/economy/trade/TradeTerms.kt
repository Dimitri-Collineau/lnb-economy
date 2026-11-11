package fr.bde_teknantes.lnb.economy.trade

import fr.bde_teknantes.lnb.economy.shop.Stock
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

/** The rules of a trade, without players or the network around them. */
object TradeTerms {
    /** The money part of a trade: [payerIsFirst] pays [amount] to the other player. */
    data class Settlement(val payerIsFirst: Boolean, val amount: Long)

    /** What one player gave minus what the other gave, or null when the money cancels out. */
    fun settle(firstGives: Long, secondGives: Long): Settlement? = when {
        firstGives == secondGives -> null
        firstGives > secondGives -> Settlement(true, firstGives - secondGives)
        else -> Settlement(false, secondGives - firstGives)
    }

    /** The offered stacks merged by item and components, so each kind of item is checked once. */
    fun totals(offer: List<ItemStack>): List<ItemStack> {
        val totals = mutableListOf<ItemStack>()
        for (stack in offer) {
            if (stack.isEmpty) continue
            val same = totals.firstOrNull { ItemStack.isSameItemSameComponents(it, stack) }
            if (same == null) totals.add(stack.copy()) else same.grow(stack.count)
        }
        return totals
    }

    /** Whether [container] holds every item of [totals]. */
    fun holds(container: Container, totals: List<ItemStack>, slots: IntRange): Boolean =
        totals.all { Stock.count(container, it, slots) >= it.count }

    /**
     * Takes every item of [totals] out of [container], in full stacks, or nothing at all when one is missing. A total
     * can be above a stack's size (two stacks of the same item offered), so counts are not stack-limited here.
     */
    fun take(container: Container, totals: List<ItemStack>, slots: IntRange): List<ItemStack>? {
        if (!holds(container, totals, slots)) return null
        return totals.flatMap { total -> Stock.take(container, total.copyWithCount(1), total.count, slots).orEmpty() }
    }
}
