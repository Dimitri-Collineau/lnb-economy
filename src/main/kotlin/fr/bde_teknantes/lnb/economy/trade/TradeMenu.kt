package fr.bde_teknantes.lnb.economy.trade

import fr.bde_teknantes.lnb.economy.ModContent
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * One player's side of a trade screen: their offer, the other player's offer, and their inventory.
 *
 * Offers are copies, not the items themselves: clicking an offer slot with an item shows that stack (a right click
 * adds one more), clicking it with an empty hand clears it, and shift-clicking a stack in the inventory offers it.
 * Nothing leaves the inventories until both players are ready and the countdown ends.
 */
class TradeMenu(
    containerId: Int,
    playerInventory: Inventory,
    private val mine: Container,
    theirs: Container,
    /** The other player's name, for the screen. */
    val partner: String,
    private val session: TradeSession?,
) : AbstractContainerMenu(ModContent.TRADE_MENU.get(), containerId) {
    /** The client side, from the server's extra data. */
    constructor(containerId: Int, playerInventory: Inventory, buf: RegistryFriendlyByteBuf) :
        this(containerId, playerInventory, SimpleContainer(SLOTS), SimpleContainer(SLOTS), buf.readUtf(16), null)

    private class OfferSlot(container: Container, index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        override fun mayPickup(player: Player): Boolean = false
        override fun mayPlace(stack: ItemStack): Boolean = false
    }

    init {
        for (i in 0 until SLOTS) addSlot(OfferSlot(mine, i, MINE_X + (i % 4) * 18, OFFER_Y + (i / 4) * 18))
        for (i in 0 until SLOTS) addSlot(OfferSlot(theirs, i, THEIRS_X + (i % 4) * 18, OFFER_Y + (i / 4) * 18))
        for (row in 0 until 3) {
            for (column in 0 until 9) addSlot(Slot(playerInventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18))
        }
        for (column in 0 until 9) addSlot(Slot(playerInventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58))
    }

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        when {
            slotId in 0 until SLOTS -> if (clickType == ClickType.PICKUP) offer(slotId, button)
            slotId in SLOTS until FIRST_INVENTORY_SLOT -> Unit
            clickType == ClickType.QUICK_MOVE && slotId >= FIRST_INVENTORY_SLOT -> {
                val stack = slots[slotId].item
                val free = (0 until SLOTS).firstOrNull { mine.getItem(it).isEmpty }
                if (!stack.isEmpty && free != null) mine.setItem(free, stack.copy())
            }
            else -> super.clicked(slotId, button, clickType, player)
        }
    }

    private fun offer(slot: Int, button: Int) {
        val carried = carried
        val current = mine.getItem(slot)
        mine.setItem(
            slot,
            when {
                carried.isEmpty -> ItemStack.EMPTY
                button == 1 && ItemStack.isSameItemSameComponents(current, carried) ->
                    current.copyWithCount(minOf(current.count + 1, current.maxStackSize))
                button == 1 -> carried.copyWithCount(1)
                else -> carried.copy()
            },
        )
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    /** Collecting a stack with a double click never pulls from the offers. */
    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot): Boolean = slot !is OfferSlot

    override fun stillValid(player: Player): Boolean = session?.isOpen ?: true

    override fun removed(player: Player) {
        super.removed(player)
        session?.onClosed(player)
    }

    companion object {
        const val SLOTS = 12
        const val FIRST_INVENTORY_SLOT = SLOTS * 2

        const val MINE_X = 16
        const val THEIRS_X = 110
        const val OFFER_Y = 39
        const val INVENTORY_X = 18
        const val INVENTORY_Y = 155
    }
}
