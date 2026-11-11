package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Client -> server: the trade key, pointed at the player with entity ID [entityId]. */
data class TradeRequestPayload(val entityId: Int) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<TradeRequestPayload>(LnbEconomy.id("trade_request"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TradeRequestPayload> = StreamCodec.of(
            { buf, p -> buf.writeVarInt(p.entityId) },
            { buf -> TradeRequestPayload(buf.readVarInt()) },
        )
    }
}

/** Client -> server: the LNBits this player gives in the trade, from [accountId] (null: their default account). */
data class TradeMoneyPayload(val amount: Long, val accountId: String?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<TradeMoneyPayload>(LnbEconomy.id("trade_money"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TradeMoneyPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarLong(p.amount)
                buf.writeNullable(p.accountId) { b, v -> b.writeUtf(v, 64) }
            },
            { buf -> TradeMoneyPayload(buf.readVarLong(), buf.readNullable { it.readUtf(64) }) },
        )
    }
}

/** Client -> server: this player is ready, or no longer. */
data class TradeReadyPayload(val ready: Boolean) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<TradeReadyPayload>(LnbEconomy.id("trade_ready"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TradeReadyPayload> = StreamCodec.of(
            { buf, p -> buf.writeBoolean(p.ready) },
            { buf -> TradeReadyPayload(buf.readBoolean()) },
        )
    }
}

/**
 * Server -> client: the money and readiness of both sides of the open trade. [countdown] is in seconds, -1 while a side
 * isn't ready.
 */
data class TradeStatePayload(
    val myAmount: Long,
    val myAccountId: String?,
    val theirAmount: Long,
    val myReady: Boolean,
    val theirReady: Boolean,
    val countdown: Int,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<TradeStatePayload>(LnbEconomy.id("trade_state"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TradeStatePayload> = StreamCodec.of(
            { buf, p ->
                buf.writeVarLong(p.myAmount)
                buf.writeNullable(p.myAccountId) { b, v -> b.writeUtf(v, 64) }
                buf.writeVarLong(p.theirAmount)
                buf.writeBoolean(p.myReady)
                buf.writeBoolean(p.theirReady)
                buf.writeVarInt(p.countdown + 1)
            },
            { buf ->
                TradeStatePayload(buf.readVarLong(), buf.readNullable { it.readUtf(64) }, buf.readVarLong(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt() - 1)
            },
        )
    }
}
