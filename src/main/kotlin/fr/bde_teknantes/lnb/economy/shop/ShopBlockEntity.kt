package fr.bde_teknantes.lnb.economy.shop

import fr.bde_teknantes.lnb.economy.ModContent
import fr.bde_teknantes.lnb.economy.item.ItemStacks
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.SimpleContainer
import net.minecraft.world.level.block.Block
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * A shop's stock and settings. The settings are a copy of what the site has (the site prices every order); [shopId]
 * is null until the shop's first save reaches the site.
 */
class ShopBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModContent.SHOP_ENTITY.get(), pos, state) {
    val stock = object : SimpleContainer(SLOTS) {
        override fun setChanged() {
            super.setChanged()
            this@ShopBlockEntity.setChanged()
        }
    }

    /** The item sold, one of it, with every component. Empty until the owner picks one. */
    var template: ItemStack = ItemStack.EMPTY
    var settings: ShopSettings? = null
    var shopId: String? = null

    /** The account that sells: its ID, and its handle for shared accounts. */
    var accountId: String? = null
    var accountHandle: String? = null

    /** Who placed the block: the only one who can set it up before it has an account. */
    var placedBy: UUID? = null

    /** An admin turned the shop off on the site. Learned from the site's answers. */
    var offOnSite = false

    val isOpen: Boolean get() = shopId != null && settings != null && !template.isEmpty && !offOnSite

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.put(STOCK, ListTag().apply { addAll(stockTags(registries)) })
        ItemStacks.toTag(template, registries)?.let { tag.put(TEMPLATE, it) }
        settings?.let { tag.put(SETTINGS, it.toTag()) }
        shopId?.let { tag.putString(SHOP_ID, it) }
        accountId?.let { tag.putString(ACCOUNT_ID, it) }
        accountHandle?.let { tag.putString(ACCOUNT_HANDLE, it) }
        placedBy?.let { tag.putUUID(PLACED_BY, it) }
        tag.putBoolean(OFF_ON_SITE, offOnSite)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        stock.clearContent()
        val list = tag.getList(STOCK, Tag.TAG_COMPOUND.toInt())
        for (i in 0 until list.size) {
            val entry = list.getCompound(i)
            val slot = entry.getInt(SLOT)
            if (slot in 0 until SLOTS) ItemStacks.fromTag(entry.getCompound(ITEM), registries)?.let { stock.setItem(slot, it) }
        }
        template = if (tag.contains(TEMPLATE)) ItemStacks.fromTag(tag.getCompound(TEMPLATE), registries) ?: ItemStack.EMPTY else ItemStack.EMPTY
        settings = if (tag.contains(SETTINGS)) ShopSettings.fromTag(tag.getCompound(SETTINGS)) else null
        shopId = tag.getString(SHOP_ID).ifEmpty { null }
        accountId = tag.getString(ACCOUNT_ID).ifEmpty { null }
        accountHandle = tag.getString(ACCOUNT_HANDLE).ifEmpty { null }
        placedBy = if (tag.hasUUID(PLACED_BY)) tag.getUUID(PLACED_BY) else null
        offOnSite = tag.getBoolean(OFF_ON_SITE)
    }

    /** The item sold changed: show it under the cloche on every client that sees the shop. */
    fun showcaseChanged() {
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    /** Clients get the item sold only, to show it: never the stock, the prices or the account. */
    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        CompoundTag().apply { ItemStacks.toTag(template, registries)?.let { put(TEMPLATE, it) } }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    private fun stockTags(registries: HolderLookup.Provider): List<CompoundTag> =
        (0 until SLOTS).mapNotNull { slot ->
            ItemStacks.toTag(stock.getItem(slot), registries)?.let { item ->
                CompoundTag().apply {
                    putInt(SLOT, slot)
                    put(ITEM, item)
                }
            }
        }

    companion object {
        const val SLOTS = 27

        private const val STOCK = "stock"
        private const val SLOT = "slot"
        private const val ITEM = "item"
        private const val TEMPLATE = "template"
        private const val SETTINGS = "settings"
        private const val SHOP_ID = "shopId"
        private const val ACCOUNT_ID = "accountId"
        private const val ACCOUNT_HANDLE = "accountHandle"
        private const val PLACED_BY = "placedBy"
        private const val OFF_ON_SITE = "offOnSite"
    }
}

/**
 * A shop's prices: [sellPrice] for one lot of [lotSize] items, [buyPrice] for one lot bought back (null: the shop
 * doesn't buy), and [buyCap], the most items the shop holds before it stops buying.
 *
 * A shop either sells (and may buy the same item back), or, with [buying], only buys: players bring it the item and
 * it pays [buyPrice] per lot until it holds [buyCap]. The site knows one kind of shop, so a buying shop is saved there
 * with a [sellPrice] equal to its [buyPrice], and the server never lets anyone buy from it.
 */
data class ShopSettings(val lotSize: Int, val sellPrice: Long, val buyPrice: Long?, val buyCap: Int, val buying: Boolean = false) {
    fun toTag(): CompoundTag = CompoundTag().apply {
        putInt("lotSize", lotSize)
        putLong("sellPrice", sellPrice)
        buyPrice?.let { putLong("buyPrice", it) }
        putInt("buyCap", buyCap)
        putBoolean("buying", buying)
    }

    /** What is wrong with these settings for [template], or null when they are fine. */
    fun problem(template: ItemStack): String? = when {
        template.isEmpty -> "no_item"
        lotSize !in 1..minOf(MAX_LOT, template.maxStackSize) -> "lot_size"
        sellPrice !in 1..MAX_PRICE -> "sell_price"
        buying && buyPrice == null -> "buying_price"
        buyPrice != null && buyPrice !in 1..sellPrice -> "buy_price"
        buyCap < 0 || (buying && buyCap < lotSize) -> "buy_cap"
        else -> null
    }

    companion object {
        const val MAX_LOT = 99
        const val MAX_PRICE = 1_000_000_000_000L

        fun fromTag(tag: CompoundTag) = ShopSettings(
            lotSize = tag.getInt("lotSize"),
            sellPrice = tag.getLong("sellPrice"),
            buyPrice = if (tag.contains("buyPrice")) tag.getLong("buyPrice") else null,
            buyCap = tag.getInt("buyCap"),
            buying = tag.getBoolean("buying"),
        )

        /** A shop that only buys, at [price] per lot. */
        fun buying(lotSize: Int, price: Long, buyCap: Int) = ShopSettings(lotSize, price, price.takeIf { it > 0 }, buyCap, buying = true)
    }
}
