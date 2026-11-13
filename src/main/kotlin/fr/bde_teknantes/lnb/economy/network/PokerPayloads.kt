package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.LnbEconomy
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * One seat of a poker table as everyone sees it. [choice] is a [fr.bde_teknantes.lnb.economy.poker.HoldemRound.Choice]
 * ordinal; [result] a [fr.bde_teknantes.lnb.economy.poker.HoldemRules.Result] ordinal, [payout] what the seat gets back
 * and [hand] its hand's category, all three known at the showdown only (-1, 0, -1 before).
 */
data class PokerSeatView(val name: String, val ante: Long, val cards: List<Int>, val choice: Int, val result: Int, val payout: Long, val hand: Int) {
    companion object {
        fun write(buf: FriendlyByteBuf, seat: PokerSeatView) {
            buf.writeUtf(seat.name, 64)
            buf.writeVarLong(seat.ante)
            buf.writeCollection(seat.cards) { b, c -> b.writeByte(c) }
            buf.writeByte(seat.choice)
            buf.writeByte(seat.result)
            buf.writeVarLong(seat.payout)
            buf.writeByte(seat.hand)
        }

        fun read(buf: FriendlyByteBuf) = PokerSeatView(
            buf.readUtf(64), buf.readVarLong(), buf.readCollection({ ArrayList(minOf(it, 8)) }) { b -> b.readByte().toInt() },
            buf.readByte().toInt(), buf.readByte().toInt(), buf.readVarLong(), buf.readByte().toInt(),
        )
    }
}

/**
 * Server -> client: a poker table right now. [phase] is a [fr.bde_teknantes.lnb.economy.poker.HoldemRound.Phase]
 * ordinal with [ticksLeft] in it; cards are numbers from 0 to 51, -1 face down; [dealerHand] is the dealer's hand
 * category at the showdown, else -1; [seats] has one entry per seat, null when it is free; [maxBet] 0 is no limit.
 * With [open], the table's screen opens; without, only a screen already on that table updates.
 */
data class PokerStatePayload(
    val pos: BlockPos,
    val handle: String,
    val casinoName: String,
    val phase: Int,
    val ticksLeft: Int,
    val board: List<Int>,
    val dealer: List<Int>,
    val dealerHand: Int,
    val seats: List<PokerSeatView?>,
    val maxBet: Long,
    val open: Boolean,
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PokerStatePayload>(LnbEconomy.id("poker_state"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PokerStatePayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeUtf(p.handle, 32)
                buf.writeUtf(p.casinoName, 128)
                buf.writeByte(p.phase)
                buf.writeVarInt(p.ticksLeft)
                buf.writeCollection(p.board) { b, c -> b.writeByte(c) }
                buf.writeCollection(p.dealer) { b, c -> b.writeByte(c) }
                buf.writeByte(p.dealerHand)
                buf.writeCollection(p.seats) { b, seat -> b.writeNullable(seat, PokerSeatView::write) }
                buf.writeVarLong(p.maxBet)
                buf.writeBoolean(p.open)
            },
            { buf ->
                PokerStatePayload(
                    buf.readBlockPos(), buf.readUtf(32), buf.readUtf(128), buf.readByte().toInt(), buf.readVarInt(),
                    buf.readCollection({ ArrayList(minOf(it, 8)) }) { b -> b.readByte().toInt() },
                    buf.readCollection({ ArrayList(minOf(it, 8)) }) { b -> b.readByte().toInt() },
                    buf.readByte().toInt(),
                    buf.readCollection({ ArrayList<PokerSeatView?>(minOf(it, 8)) }) { b -> b.readNullable(PokerSeatView::read) },
                    buf.readVarLong(), buf.readBoolean(),
                )
            },
        )
    }
}

/** Client -> server: at the poker table at [pos], sit at [seat] with [ante], call, fold, or stop watching. */
data class PokerActionPayload(val pos: BlockPos, val action: Int, val seat: Int = 0, val ante: Long = 0) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        const val SIT = 0
        const val CALL = 1
        const val FOLD = 2
        const val CLOSE = 3

        val TYPE = CustomPacketPayload.Type<PokerActionPayload>(LnbEconomy.id("poker_action"))
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PokerActionPayload> = StreamCodec.of(
            { buf, p ->
                buf.writeBlockPos(p.pos)
                buf.writeByte(p.action)
                buf.writeByte(p.seat)
                buf.writeVarLong(p.ante)
            },
            { buf -> PokerActionPayload(buf.readBlockPos(), buf.readByte().toInt(), buf.readByte().toInt(), buf.readVarLong()) },
        )
    }
}
