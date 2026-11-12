package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Server -> client: open a counter's screen. */
data class OpenCounterPayload(val pos: BlockPos, val handle: String, val casinoName: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OpenCounterPayload>(LnbEconomy.id("open_counter"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, OpenCounterPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeUtf(p.handle, 32)
                buf.writeUtf(p.casinoName, 128)
            },
            { buf -> OpenCounterPayload(buf.readBlockPos(), buf.readUtf(32), buf.readUtf(128)) },
        )
    }
}

/** Client -> server: buy [amount] of chips at the counter, paid from [accountId] (null: the default account). */
data class CounterBuyPayload(val pos: BlockPos, val accountId: String?, val amount: Long) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<CounterBuyPayload>(LnbEconomy.id("counter_buy"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CounterBuyPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeNullable(p.accountId) { b, v -> b.writeUtf(v, 64) }
                buf.writeVarLong(p.amount)
            },
            { buf -> CounterBuyPayload(buf.readBlockPos(), buf.readNullable { it.readUtf(64) }, buf.readVarLong()) },
        )
    }
}

/** Client -> server: cash every chip of the counter's casino in the inventory, to the personal account. */
data class CounterCashPayload(val pos: BlockPos) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<CounterCashPayload>(LnbEconomy.id("counter_cash"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CounterCashPayload> = StreamCodec.of(
            { buf, p -> buf.writeBlockPos(p.pos) },
            { buf -> CounterCashPayload(buf.readBlockPos()) },
        )
    }
}

/** Client -> server: change every chip of the counter's casino in the inventory into chips of at most [largest]. */
data class CounterChangePayload(val pos: BlockPos, val largest: Int) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<CounterChangePayload>(LnbEconomy.id("counter_change"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CounterChangePayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeVarInt(p.largest)
            },
            { buf -> CounterChangePayload(buf.readBlockPos(), buf.readVarInt()) },
        )
    }
}

/** A casino the player may link something to. */
data class LinkOption(val handle: String, val name: String)

/**
 * Server -> client: open the link screen for the machine or counter at [pos].
 * [game] is the machine's game, or null for a counter (which has no maximum bet).
 * [wood] is a counter's current wood when the player may change it, else null.
 */
data class OpenLinkPayload(
    val pos: BlockPos,
    val game: String?,
    val current: String?,
    val maxBet: Long?,
    val mayUnlink: Boolean,
    val options: List<LinkOption>,
    val wood: String?,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OpenLinkPayload>(LnbEconomy.id("open_link"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, OpenLinkPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeNullable(p.game) { b, v -> b.writeUtf(v, 32) }
                buf.writeNullable(p.current) { b, v -> b.writeUtf(v, 32) }
                buf.writeNullable(p.maxBet) { b, v -> b.writeVarLong(v) }
                buf.writeBoolean(p.mayUnlink)
                buf.writeCollection(p.options) { b, o ->
                    b.writeUtf(o.handle, 32)
                    b.writeUtf(o.name, 128)
                }
                buf.writeNullable(p.wood) { b, v -> b.writeUtf(v, 32) }
            },
            { buf ->
                OpenLinkPayload(
                    buf.readBlockPos(),
                    buf.readNullable { it.readUtf(32) },
                    buf.readNullable { it.readUtf(32) },
                    buf.readNullable { it.readVarLong() },
                    buf.readBoolean(),
                    buf.readCollection({ ArrayList(minOf(it, 256)) }) { b -> LinkOption(b.readUtf(32), b.readUtf(128)) },
                    buf.readNullable { it.readUtf(32) },
                )
            },
        )
    }
}

/** Client -> server: link the machine or counter at [pos] to [handle], or unlink it when [handle] is null. */
data class LinkPayload(val pos: BlockPos, val handle: String?, val maxBet: Long?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<LinkPayload>(LnbEconomy.id("link"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, LinkPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeNullable(p.handle) { b, v -> b.writeUtf(v, 32) }
                buf.writeNullable(p.maxBet) { b, v -> b.writeVarLong(v) }
            },
            { buf -> LinkPayload(buf.readBlockPos(), buf.readNullable { it.readUtf(32) }, buf.readNullable { it.readVarLong() }) },
        )
    }
}

/** Client -> server: build the counter whose master is at [pos] in [wood] (a [fr.bde_teknantes.lnb.economy.counter.CounterWood] id). */
data class CounterWoodPayload(val pos: BlockPos, val wood: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<CounterWoodPayload>(LnbEconomy.id("counter_wood"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CounterWoodPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeUtf(p.wood, 32)
            },
            { buf -> CounterWoodPayload(buf.readBlockPos(), buf.readUtf(32)) },
        )
    }
}

/** Server -> client: the casino of the casino menu [containerId], so the client only lets that casino's chips in. */
data class MenuCasinoPayload(val containerId: Int, val handle: String?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MenuCasinoPayload>(LnbEconomy.id("menu_casino"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MenuCasinoPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarInt(p.containerId)
                buf.writeNullable(p.handle) { b, v -> b.writeUtf(v, 32) }
            },
            { buf -> MenuCasinoPayload(buf.readVarInt(), buf.readNullable { it.readUtf(32) }) },
        )
    }
}
