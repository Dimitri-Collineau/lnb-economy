package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.shop.ShopSettings
import fr.bde_teknantes.lnb.economy.shop.ShopSetup
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every payload must survive an encode and a decode: a codec that throws drops the player's connection. */
class PayloadCodecTest {
    private fun <T> roundTrip(codec: StreamCodec<FriendlyByteBuf, T>, value: T): T {
        val buf = FriendlyByteBuf(Unpooled.buffer())
        codec.encode(buf, value)
        val decoded = codec.decode(buf)
        assertEquals(0, buf.readableBytes(), "every byte is read back")
        return decoded
    }

    private fun registryBuf() = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))

    @Test
    fun `shop screens`() {
        val pos = BlockPos(10, 64, -3)
        val item = ItemStack(Items.DIAMOND_SWORD).apply { set(DataComponents.CUSTOM_NAME, Component.literal("Lame")) }
        val shop = OpenShopPayload(pos, item, 1, 400, 0, 3, 0, "#forge", refresh = true)
        val buf = registryBuf()
        OpenShopPayload.STREAM_CODEC.encode(buf, shop)
        val decoded = OpenShopPayload.STREAM_CODEC.decode(buf)
        assertEquals(0, buf.readableBytes(), "every byte is read back")
        assertTrue(ItemStack.matches(item, decoded.item))
        assertEquals(shop.copy(item = ItemStack.EMPTY), decoded.copy(item = ItemStack.EMPTY))

        for (setup in listOf(ShopSetup(pos, ShopSettings(16, 40, 25, 1728), "a", true, false), ShopSetup(pos, ShopSettings(1, 5, null, 0), null, false, true), ShopSetup(pos, null, null, false, false))) {
            val setupBuf = registryBuf()
            setup.write(setupBuf)
            assertEquals(setup, ShopSetup.read(setupBuf))
            assertEquals(0, setupBuf.readableBytes())
        }
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
        listOf(ShopOrderPayload(pos, true, 3, null, 40), ShopOrderPayload(pos, false, 1, "a", 25)).forEach {
            assertEquals(it, roundTrip(ShopOrderPayload.STREAM_CODEC, it))
        }
        val save = ShopSavePayload(pos, 16, 40, 0, 1728, "a")
        assertEquals(save, roundTrip(ShopSavePayload.STREAM_CODEC, save))
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
