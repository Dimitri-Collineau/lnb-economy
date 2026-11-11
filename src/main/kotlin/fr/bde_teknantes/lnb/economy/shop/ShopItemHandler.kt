package fr.bde_teknantes.lnb.economy.shop

import fr.bde_teknantes.lnb.economy.ModContent
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.wrapper.InvWrapper

/**
 * What hoppers, Create's funnels and other automation see of a shop: they can fill its stock with the shop's item,
 * never take anything out. Only the owners and members take items out, through the shop's screen.
 */
class ShopItemHandler(private val shop: ShopBlockEntity) : IItemHandler {
    private val stock = InvWrapper(shop.stock)

    override fun getSlots(): Int = stock.slots

    override fun getStackInSlot(slot: Int): ItemStack = stock.getStackInSlot(slot)

    override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack =
        if (isItemValid(slot, stack)) stock.insertItem(slot, stack, simulate) else stack

    override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY

    override fun getSlotLimit(slot: Int): Int = stock.getSlotLimit(slot)

    override fun isItemValid(slot: Int, stack: ItemStack): Boolean = Stock.matches(shop.template, stack)

    companion object {
        fun register(event: RegisterCapabilitiesEvent) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModContent.SHOP_ENTITY.get()) { shop, _ -> ShopItemHandler(shop) }
        }
    }
}
