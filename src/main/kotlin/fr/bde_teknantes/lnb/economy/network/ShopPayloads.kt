package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.ItemStack

/**
 * Server -> client: a shop as a customer sees it. [buyPrice] 0 means it doesn't buy back; [room] is how many items it
 * would still buy. With [refresh], only updates a shop screen already open.
 */
data class OpenShopPayload(
    val pos: BlockPos,
    val item: ItemStack,
    val lotSize: Int,
    val sellPrice: Long,
    val buyPrice: Long,
    val stock: Int,
    val room: Int,
    val seller: String,
    val refresh: Boolean,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OpenShopPayload>(LnbEconomy.id("open_shop"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, OpenShopPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                ItemStack.STREAM_CODEC.encode(buf, p.item)
                buf.writeVarInt(p.lotSize)
                buf.writeVarLong(p.sellPrice)
                buf.writeVarLong(p.buyPrice)
                buf.writeVarInt(p.stock)
                buf.writeVarInt(p.room)
                buf.writeUtf(p.seller, 64)
                buf.writeBoolean(p.refresh)
            },
            { buf ->
                OpenShopPayload(
                    buf.readBlockPos(), ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readBoolean(),
                )
            },
        )
    }
}

/**
 * Client -> server: buy [lots] from the shop at [pos] ([buy]), or sell them back to it, at [expectedLotPrice].
 * [accountId] pays for a purchase, or is paid for a sale; null for the default (purchase) or personal (sale) account.
 */
data class ShopOrderPayload(
    val pos: BlockPos,
    val buy: Boolean,
    val lots: Int,
    val accountId: String?,
    val expectedLotPrice: Long,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ShopOrderPayload>(LnbEconomy.id("shop_order"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ShopOrderPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeBoolean(p.buy)
                buf.writeVarInt(p.lots)
                buf.writeNullable(p.accountId) { b, v -> b.writeUtf(v, 64) }
                buf.writeVarLong(p.expectedLotPrice)
            },
            { buf -> ShopOrderPayload(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readNullable { it.readUtf(64) }, buf.readVarLong()) },
        )
    }
}

/** Client -> server: save the settings of the shop at [pos], with the item in its template slot. [buyPrice] 0: no buy-back. */
data class ShopSavePayload(
    val pos: BlockPos,
    val lotSize: Int,
    val sellPrice: Long,
    val buyPrice: Long,
    val buyCap: Int,
    val accountId: String,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ShopSavePayload>(LnbEconomy.id("shop_save"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ShopSavePayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeVarInt(p.lotSize)
                buf.writeVarLong(p.sellPrice)
                buf.writeVarLong(p.buyPrice)
                buf.writeVarInt(p.buyCap)
                buf.writeUtf(p.accountId, 64)
            },
            { buf -> ShopSavePayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readUtf(64)) },
        )
    }
}
