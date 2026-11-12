package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.ItemStack

/** Server -> client: open the auction house screen. */
object OpenAuctionPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<OpenAuctionPayload>(LnbEconomy.id("open_auction"))
    val STREAM_CODEC: StreamCodec<FriendlyByteBuf, OpenAuctionPayload> = StreamCodec.unit(OpenAuctionPayload)

    override fun type() = TYPE
}

/**
 * A listing or a mailbox item, as the screen shows it. [stack] is empty when the game can't read the item any more;
 * [name] is then all there is. [detail] is the seller for a listing, the reason for a mailbox item; [date] is when a
 * listing expires, or when an item reached the mailbox.
 */
data class AuctionEntry(
    val id: String,
    val stack: ItemStack,
    val name: String,
    val count: Int,
    val price: Long,
    val detail: String,
    val date: String,
) {
    companion object {
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AuctionEntry> = StreamCodec.of(
            { buf, e ->
                buf.writeUtf(e.id, 64)
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.stack)
                buf.writeUtf(e.name, 64)
                buf.writeVarInt(e.count)
                buf.writeVarLong(e.price)
                buf.writeUtf(e.detail, 64)
                buf.writeUtf(e.date, 64)
            },
            { buf ->
                AuctionEntry(
                    buf.readUtf(64), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(64), buf.readVarInt(), buf.readVarLong(),
                    buf.readUtf(64), buf.readUtf(64),
                )
            },
        )
    }
}

/**
 * Server -> client: a page of listings ([tab] [AuctionRequestPayload.BROWSE]), or the player's own with the limits for
 * new ones ([AuctionRequestPayload.MINE]: [max], [taxPercent], [days]).
 */
data class AuctionListingsPayload(
    val tab: Int,
    val entries: List<AuctionEntry>,
    val cursor: String?,
    val nextCursor: String?,
    val max: Int,
    val taxPercent: Int,
    val days: Int,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AuctionListingsPayload>(LnbEconomy.id("auction_listings"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AuctionListingsPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.tab)
                buf.writeVarInt(p.entries.size)
                p.entries.forEach { AuctionEntry.STREAM_CODEC.encode(buf, it) }
                buf.writeNullable(p.cursor) { b, v -> b.writeUtf(v, 32) }
                buf.writeNullable(p.nextCursor) { b, v -> b.writeUtf(v, 32) }
                buf.writeVarInt(p.max)
                buf.writeVarInt(p.taxPercent)
                buf.writeVarInt(p.days)
            },
            { buf ->
                val tab = buf.readVarInt()
                val entries = List(buf.readVarInt()) { AuctionEntry.STREAM_CODEC.decode(buf) }
                AuctionListingsPayload(
                    tab, entries, buf.readNullable { it.readUtf(32) }, buf.readNullable { it.readUtf(32) }, buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(),
                )
            },
        )
    }
}

/** Server -> client: the items waiting for the player on the site. */
data class AuctionMailboxPayload(val entries: List<AuctionEntry>) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AuctionMailboxPayload>(LnbEconomy.id("auction_mailbox"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AuctionMailboxPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.entries.size)
                p.entries.forEach { AuctionEntry.STREAM_CODEC.encode(buf, it) }
            },
            { buf -> AuctionMailboxPayload(List(buf.readVarInt()) { AuctionEntry.STREAM_CODEC.decode(buf) }) },
        )
    }
}

/** Client -> server: show a tab of the auction house. [query], [sort] and [cursor] only matter when browsing. */
data class AuctionRequestPayload(val tab: Int, val query: String?, val sort: String, val cursor: String?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val BROWSE = 0
        const val MINE = 1
        const val MAILBOX = 2

        val SORTS = listOf("newest", "price", "unitPrice")

        val TYPE = CustomPacketPayload.Type<AuctionRequestPayload>(LnbEconomy.id("auction_request"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AuctionRequestPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.tab)
                buf.writeNullable(p.query) { b, v -> b.writeUtf(v, 64) }
                buf.writeUtf(p.sort, 16)
                buf.writeNullable(p.cursor) { b, v -> b.writeUtf(v, 32) }
            },
            { buf ->
                AuctionRequestPayload(buf.readVarInt(), buf.readNullable { it.readUtf(64) }, buf.readUtf(16), buf.readNullable { it.readUtf(32) })
            },
        )
    }
}

/**
 * Client -> server: list [count] items of the stack in the player's inventory [slot] (0..35: the main inventory and the
 * hotbar) for [price], sold for [accountId] (null: the default account).
 */
data class AuctionSellPayload(val slot: Int, val count: Int, val price: Long, val accountId: String?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AuctionSellPayload>(LnbEconomy.id("auction_sell"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AuctionSellPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.slot)
                buf.writeVarInt(p.count)
                buf.writeVarLong(p.price)
                buf.writeNullable(p.accountId) { b, v -> b.writeUtf(v, 64) }
            },
            { buf -> AuctionSellPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readNullable { it.readUtf(64) }) },
        )
    }
}

/** Client -> server: buy a listing, paid from [accountId] (null: the default account). */
data class AuctionBuyPayload(val listingId: String, val accountId: String?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AuctionBuyPayload>(LnbEconomy.id("auction_buy"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AuctionBuyPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.listingId, 64)
                buf.writeNullable(p.accountId) { b, v -> b.writeUtf(v, 64) }
            },
            { buf -> AuctionBuyPayload(buf.readUtf(64), buf.readNullable { it.readUtf(64) }) },
        )
    }
}

/** Client -> server: take one of the player's listings back. */
data class AuctionCancelPayload(val listingId: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<AuctionCancelPayload>(LnbEconomy.id("auction_cancel"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AuctionCancelPayload> = StreamCodec.of(
            { buf, p -> buf.writeUtf(p.listingId, 64) },
            { buf -> AuctionCancelPayload(buf.readUtf(64)) },
        )
    }
}

/** Client -> server: collect everything waiting in the player's mailbox on the site. */
object AuctionCollectPayload : CustomPacketPayload {
    val TYPE = CustomPacketPayload.Type<AuctionCollectPayload>(LnbEconomy.id("auction_collect"))
    val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AuctionCollectPayload> = StreamCodec.unit(AuctionCollectPayload)

    override fun type() = TYPE
}
