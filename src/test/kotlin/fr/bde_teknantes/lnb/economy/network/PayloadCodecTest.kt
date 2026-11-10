package fr.bde_teknantes.lnb.economy.network

import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every payload must survive an encode and a decode: a codec that throws drops the player's connection. */
class PayloadCodecTest {
    private fun <T> roundTrip(codec: StreamCodec<FriendlyByteBuf, T>, value: T): T {
        val buf = FriendlyByteBuf(Unpooled.buffer())
        codec.encode(buf, value)
        val decoded = codec.decode(buf)
        assertEquals(0, buf.readableBytes(), "every byte is read back")
        return decoded
    }

    @Test
    fun `accounts snapshot`() {
        val snapshot = AccountsSnapshotPayload(
            EconomyStatus.READY,
            listOf(
                AccountEntry("a", null, null, 1250, "owner", frozen = false, canSpend = true),
                AccountEntry("b", "casino", "Casino Royal", 0, "viewer", frozen = true, canSpend = false),
            ),
            "a",
        )
        assertEquals(snapshot, roundTrip(AccountsSnapshotPayload.STREAM_CODEC, snapshot))
        val empty = AccountsSnapshotPayload(EconomyStatus.LOADING, emptyList(), null)
        assertEquals(empty, roundTrip(AccountsSnapshotPayload.STREAM_CODEC, empty))
    }

    @Test
    fun `payloads without fields`() {
        assertEquals(RefreshAccountsPayload, roundTrip(RefreshAccountsPayload.STREAM_CODEC, RefreshAccountsPayload))
    }

    @Test
    fun `requests from the client`() {
        val pos = BlockPos(1, -60, 3)
        listOf(
            SetDefaultAccountPayload("a") to SetDefaultAccountPayload.STREAM_CODEC,
        ).forEach { (value, codec) -> assertEquals(value, roundTrip(codec, value)) }
        assertEquals(CounterBuyPayload(pos, null, 25), roundTrip(CounterBuyPayload.STREAM_CODEC, CounterBuyPayload(pos, null, 25)))
        assertEquals(CounterBuyPayload(pos, "a", 1), roundTrip(CounterBuyPayload.STREAM_CODEC, CounterBuyPayload(pos, "a", 1)))
        assertEquals(CounterCashPayload(pos), roundTrip(CounterCashPayload.STREAM_CODEC, CounterCashPayload(pos)))
        assertEquals(CounterChangePayload(pos, 25), roundTrip(CounterChangePayload.STREAM_CODEC, CounterChangePayload(pos, 25)))
        assertEquals(LinkPayload(pos, "casino", 100), roundTrip(LinkPayload.STREAM_CODEC, LinkPayload(pos, "casino", 100)))
        assertEquals(LinkPayload(pos, null, null), roundTrip(LinkPayload.STREAM_CODEC, LinkPayload(pos, null, null)))
    }

    @Test
    fun `screens opened by the server`() {
        val pos = BlockPos(1, 2, 3)
        assertEquals(OpenCounterPayload(pos, "casino", "Casino Royal"), roundTrip(OpenCounterPayload.STREAM_CODEC, OpenCounterPayload(pos, "casino", "Casino Royal")))
        val link = OpenLinkPayload(pos, "slots", "casino", 100, true, listOf(LinkOption("casino", "Casino Royal")))
        assertEquals(link, roundTrip(OpenLinkPayload.STREAM_CODEC, link))
        val counter = OpenLinkPayload(pos, null, null, null, false, emptyList())
        assertEquals(counter, roundTrip(OpenLinkPayload.STREAM_CODEC, counter))
        assertEquals(MenuCasinoPayload(7, "casino"), roundTrip(MenuCasinoPayload.STREAM_CODEC, MenuCasinoPayload(7, "casino")))
        assertEquals(MenuCasinoPayload(7, null), roundTrip(MenuCasinoPayload.STREAM_CODEC, MenuCasinoPayload(7, null)))
    }
}
