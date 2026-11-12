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
    fun `auction house`() {
        val entries = listOf(
            AuctionEntry("l1", ItemStack(Items.DIAMOND, 16), "Diamond", 16, 50, "Branlouis", "2026-11-18T12:00:00Z"),
            AuctionEntry("l2", ItemStack.EMPTY, "Gone widget", 1, 0, "expired", "2026-11-11T12:00:00Z"),
        )
        val listings = AuctionListingsPayload(AuctionRequestPayload.MINE, entries, null, "45", 20, 5, 7)
        val buf = registryBuf()
        AuctionListingsPayload.STREAM_CODEC.encode(buf, listings)
        val decoded = AuctionListingsPayload.STREAM_CODEC.decode(buf)
        assertEquals(0, buf.readableBytes())
        assertEquals(listings.copy(entries = emptyList()), decoded.copy(entries = emptyList()))
        assertEquals(entries.map { it.copy(stack = ItemStack.EMPTY) }, decoded.entries.map { it.copy(stack = ItemStack.EMPTY) })
        assertTrue(ItemStack.matches(entries[0].stack, decoded.entries[0].stack))
        assertTrue(decoded.entries[1].stack.isEmpty)

        val mailBuf = registryBuf()
        AuctionMailboxPayload.STREAM_CODEC.encode(mailBuf, AuctionMailboxPayload(entries.takeLast(1)))
        assertEquals(1, AuctionMailboxPayload.STREAM_CODEC.decode(mailBuf).entries.size)

        assertEquals(OpenAuctionPayload, roundTrip(OpenAuctionPayload.STREAM_CODEC, OpenAuctionPayload))
        assertEquals(AuctionCollectPayload, roundTrip(AuctionCollectPayload.STREAM_CODEC, AuctionCollectPayload))
        listOf(AuctionRequestPayload(AuctionRequestPayload.BROWSE, "diam", "unitPrice", "45"), AuctionRequestPayload(AuctionRequestPayload.MAILBOX, null, "newest", null))
            .forEach { assertEquals(it, roundTrip(AuctionRequestPayload.STREAM_CODEC, it)) }
        listOf(AuctionSellPayload(0, 16, 50, null), AuctionSellPayload(35, 1, 1_000_000_000, "a")).forEach { assertEquals(it, roundTrip(AuctionSellPayload.STREAM_CODEC, it)) }
        assertEquals(AuctionBuyPayload("l1", "a"), roundTrip(AuctionBuyPayload.STREAM_CODEC, AuctionBuyPayload("l1", "a")))
        assertEquals(AuctionCancelPayload("l1"), roundTrip(AuctionCancelPayload.STREAM_CODEC, AuctionCancelPayload("l1")))
    }

    @Test
    fun `trade state`() {
        listOf(TradeStatePayload(70, "a", 0, true, false, 3), TradeStatePayload(0, null, 25, false, false, -1)).forEach {
            assertEquals(it, roundTrip(TradeStatePayload.STREAM_CODEC, it))
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
        assertEquals(CounterWoodPayload(pos, "cherry"), roundTrip(CounterWoodPayload.STREAM_CODEC, CounterWoodPayload(pos, "cherry")))
        assertEquals(ShopMaterialPayload(pos, "calcite"), roundTrip(ShopMaterialPayload.STREAM_CODEC, ShopMaterialPayload(pos, "calcite")))
        listOf(ShopOrderPayload(pos, true, 3, null, 40), ShopOrderPayload(pos, false, 1, "a", 25)).forEach {
            assertEquals(it, roundTrip(ShopOrderPayload.STREAM_CODEC, it))
        }
        val save = ShopSavePayload(pos, 16, 40, 0, 1728, "a")
        assertEquals(save, roundTrip(ShopSavePayload.STREAM_CODEC, save))
        assertEquals(TradeRequestPayload(42), roundTrip(TradeRequestPayload.STREAM_CODEC, TradeRequestPayload(42)))
        listOf(TradeMoneyPayload(0, null), TradeMoneyPayload(70, "a")).forEach { assertEquals(it, roundTrip(TradeMoneyPayload.STREAM_CODEC, it)) }
        listOf(true, false).forEach { assertEquals(TradeReadyPayload(it), roundTrip(TradeReadyPayload.STREAM_CODEC, TradeReadyPayload(it))) }
    }

    @Test
    fun `screens opened by the server`() {
        val pos = BlockPos(1, 2, 3)
        assertEquals(OpenCounterPayload(pos, "casino", "Casino Royal"), roundTrip(OpenCounterPayload.STREAM_CODEC, OpenCounterPayload(pos, "casino", "Casino Royal")))
        val link = OpenLinkPayload(pos, "slots", "casino", 100, true, listOf(LinkOption("casino", "Casino Royal")), null)
        assertEquals(link, roundTrip(OpenLinkPayload.STREAM_CODEC, link))
        val counter = OpenLinkPayload(pos, null, null, null, false, emptyList(), "dark_oak")
        assertEquals(counter, roundTrip(OpenLinkPayload.STREAM_CODEC, counter))
        assertEquals(MenuCasinoPayload(7, "casino"), roundTrip(MenuCasinoPayload.STREAM_CODEC, MenuCasinoPayload(7, "casino")))
        assertEquals(MenuCasinoPayload(7, null), roundTrip(MenuCasinoPayload.STREAM_CODEC, MenuCasinoPayload(7, null)))
    }

    @Test
    fun `accounts and shared accounts`() {
        val snapshot = AccountsSnapshotPayload(
            EconomyStatus.READY,
            listOf(
                AccountEntry("p", null, null, 120, "owner", frozen = false, canSpend = true),
                AccountEntry("s", "casino-royal", "Casino Royal", 9_000, "member", frozen = true, canSpend = false, type = "casino"),
            ),
            "p",
            invitations = listOf(InvitationEntry("i1", "forge", "La Forge", "shop", "Branlouis"), InvitationEntry("i2", "old", "Old", null, "")),
            licensesFree = 1,
            licensesTotal = 3,
            licensePrice = 100_000,
        )
        assertEquals(snapshot, roundTrip(AccountsSnapshotPayload.STREAM_CODEC, snapshot))
        val none = AccountsSnapshotPayload(EconomyStatus.LOADING, emptyList(), null)
        assertEquals(none, roundTrip(AccountsSnapshotPayload.STREAM_CODEC, none))

        val create = AccountCreatePayload("casino-royal", "Casino Royal", "casino")
        assertEquals(create, roundTrip(AccountCreatePayload.STREAM_CODEC, create))
        for (edit in listOf(AccountEditPayload("forge", "La Forge", null), AccountEditPayload("forge", null, "shop"))) {
            assertEquals(edit, roundTrip(AccountEditPayload.STREAM_CODEC, edit))
        }
        val members = AccountMembersPayload("forge", listOf(MemberEntry("Branlouis", "owner"), MemberEntry("LnbTester", "viewer")), listOf(PendingEntry("i1", "Steve")))
        assertEquals(members, roundTrip(AccountMembersPayload.STREAM_CODEC, members))
        assertEquals(AccountMembersRequestPayload("forge"), roundTrip(AccountMembersRequestPayload.STREAM_CODEC, AccountMembersRequestPayload("forge")))
        assertEquals(AccountInvitePayload("forge", "Steve"), roundTrip(AccountInvitePayload.STREAM_CODEC, AccountInvitePayload("forge", "Steve")))
        assertEquals(InvitationCancelPayload("forge", "i1"), roundTrip(InvitationCancelPayload.STREAM_CODEC, InvitationCancelPayload("forge", "i1")))
        assertEquals(InvitationAnswerPayload("i1", true), roundTrip(InvitationAnswerPayload.STREAM_CODEC, InvitationAnswerPayload("i1", true)))
        val feedback = AccountFeedbackPayload(false, "economy.lnbeconomy.problem.other", listOf("handleTaken"))
        assertEquals(feedback, roundTrip(AccountFeedbackPayload.STREAM_CODEC, feedback))
    }
}
