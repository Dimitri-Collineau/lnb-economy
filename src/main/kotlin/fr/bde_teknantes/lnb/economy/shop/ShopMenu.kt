package fr.bde_teknantes.lnb.economy.shop

import fr.bde_teknantes.lnb.economy.ModContent
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * What the owner's screen opens with, besides the slots: the shop's settings and account, and whether it reached the
 * site.
 */
data class ShopSetup(
    val pos: BlockPos,
    val settings: ShopSettings?,
    val accountId: String?,
    val registered: Boolean,
    val offOnSite: Boolean,
) {
    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
        buf.writeNullable(settings) { b, s ->
            b.writeVarInt(s.lotSize)
            b.writeVarLong(s.sellPrice)
            b.writeVarLong(s.buyPrice ?: 0)
            b.writeVarInt(s.buyCap)
            b.writeBoolean(s.buying)
        }
        buf.writeNullable(accountId) { b, v -> b.writeUtf(v, 64) }
        buf.writeBoolean(registered)
        buf.writeBoolean(offOnSite)
    }

    companion object {
        fun read(buf: RegistryFriendlyByteBuf) = ShopSetup(
            pos = buf.readBlockPos(),
            settings = buf.readNullable { b ->
                ShopSettings(b.readVarInt(), b.readVarLong(), b.readVarLong().takeIf { it > 0 }, b.readVarInt(), b.readBoolean())
            },
            accountId = buf.readNullable { it.readUtf(64) },
            registered = buf.readBoolean(),
            offOnSite = buf.readBoolean(),
        )
    }
}

/**
 * The owner's view of a shop: the item it sells (a template: clicking it with an item copies that item, without
 * taking it), its stock, and the player's inventory. Only items identical to the template go in the stock.
 */
class ShopMenu(
    containerId: Int,
    playerInventory: Inventory,
    private val stock: Container,
    private val template: Container,
    val setup: ShopSetup,
    private val access: ContainerLevelAccess,
) : AbstractContainerMenu(ModContent.SHOP_MENU.get(), containerId) {
    /** The client side, from the server's extra data. */
    constructor(containerId: Int, playerInventory: Inventory, buf: RegistryFriendlyByteBuf) :
        this(containerId, playerInventory, SimpleContainer(ShopBlockEntity.SLOTS), SimpleContainer(1), ShopSetup.read(buf), ContainerLevelAccess.NULL)

    init {
        addSlot(object : Slot(template, 0, TEMPLATE_X, TEMPLATE_Y) {
            override fun mayPickup(player: Player): Boolean = false
            override fun mayPlace(stack: ItemStack): Boolean = false
        })
        for (row in 0 until 3) {
            for (column in 0 until 9) {
                addSlot(object : Slot(stock, column + row * 9, SLOTS_X + column * 18, STOCK_Y + row * 18) {
                    override fun mayPlace(stack: ItemStack): Boolean = Stock.matches(template.getItem(0), stack)
                })
            }
        }
        for (row in 0 until 3) {
            for (column in 0 until 9) addSlot(Slot(playerInventory, column + row * 9 + 9, SLOTS_X + column * 18, INVENTORY_Y + row * 18))
        }
        for (column in 0 until 9) addSlot(Slot(playerInventory, column, SLOTS_X + column * 18, HOTBAR_Y))
    }

    val templateItem: ItemStack get() = template.getItem(0)

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        if (slotId != TEMPLATE_SLOT) {
            super.clicked(slotId, button, clickType, player)
            return
        }
        if (clickType != ClickType.PICKUP) return
        choose(carried, player)
    }

    /**
     * Makes [item] the shop's item (a copy of one: nothing is taken), or clears it when [item] is empty. From a click
     * on the template slot with an item, or from the item search of a shop that buys.
     */
    fun choose(item: ItemStack, player: Player) {
        // The stock must hold only the new item: change it once the stock is empty of the old one.
        if (!item.isEmpty && (0 until stock.containerSize).any { !stock.getItem(it).isEmpty && !ItemStack.isSameItemSameComponents(stock.getItem(it), item) }) {
            if (!player.level().isClientSide) player.displayClientMessage(Component.translatable("gui.lnbeconomy.shop.empty_stock_first"), true)
            return
        }
        template.setItem(0, if (item.isEmpty) ItemStack.EMPTY else item.copyWithCount(1))
        broadcastChanges()
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (index == TEMPLATE_SLOT || !slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item
        val original = stack.copy()
        val moved = if (index < FIRST_PLAYER_SLOT) {
            moveItemStackTo(stack, FIRST_PLAYER_SLOT, slots.size, true)
        } else {
            Stock.matches(templateItem, stack) && moveItemStackTo(stack, FIRST_STOCK_SLOT, FIRST_PLAYER_SLOT, false)
        }
        if (!moved) return ItemStack.EMPTY
        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        return original
    }

    override fun stillValid(player: Player): Boolean = stillValid(access, player, ModContent.SHOP.get())

    companion object {
        const val TEMPLATE_SLOT = 0
        const val FIRST_STOCK_SLOT = 1
        const val FIRST_PLAYER_SLOT = FIRST_STOCK_SLOT + ShopBlockEntity.SLOTS

        /** The template slot sits in the settings panel, right of the chest. */
        // Where the slots sit in the owner's screen (see ShopSettingsScreen).
        const val SLOTS_X = 11
        const val STOCK_Y = 22
        const val INVENTORY_Y = 100
        const val HOTBAR_Y = 159
        const val TEMPLATE_X = 190
        const val TEMPLATE_Y = 26
    }
}
