package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.casino.MenuCasinos
import fr.bde_teknantes.lnb.economy.client.gui.AuctionScreen
import fr.bde_teknantes.lnb.economy.client.gui.CounterScreen
import fr.bde_teknantes.lnb.economy.client.gui.LicenseScreen
import fr.bde_teknantes.lnb.economy.client.gui.LinkScreen
import fr.bde_teknantes.lnb.economy.client.gui.ShopScreen
import fr.bde_teknantes.lnb.economy.network.AccountFeedbackPayload
import fr.bde_teknantes.lnb.economy.network.AccountMembersPayload
import fr.bde_teknantes.lnb.economy.network.AccountsSnapshotPayload
import fr.bde_teknantes.lnb.economy.network.AuctionListingsPayload
import fr.bde_teknantes.lnb.economy.network.AuctionMailboxPayload
import fr.bde_teknantes.lnb.economy.network.InvitationNoticePayload
import fr.bde_teknantes.lnb.economy.network.OpenAuctionPayload
import fr.bde_teknantes.lnb.economy.network.MenuCasinoPayload
import fr.bde_teknantes.lnb.economy.network.OpenCounterPayload
import fr.bde_teknantes.lnb.economy.network.OpenLicensePayload
import fr.bde_teknantes.lnb.economy.network.OpenLinkPayload
import fr.bde_teknantes.lnb.economy.network.OpenShopPayload
import fr.bde_teknantes.lnb.economy.network.PokerStatePayload
import fr.bde_teknantes.lnb.economy.network.TradeStatePayload
import fr.bde_teknantes.lnb.economy.client.gui.MarketClosedScreen
import fr.bde_teknantes.lnb.economy.client.gui.MarketEfficiencyScreen
import fr.bde_teknantes.lnb.economy.client.gui.MarketScreen
import fr.bde_teknantes.lnb.economy.client.gui.MarketUi
import fr.bde_teknantes.lnb.economy.network.MarketCatalogPayload
import fr.bde_teknantes.lnb.economy.network.MarketDeltaPayload
import fr.bde_teknantes.lnb.economy.network.MarketHistoryPayload
import fr.bde_teknantes.lnb.economy.network.MarketResultPayload
import fr.bde_teknantes.lnb.economy.network.MarketWindowPayload
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Payloads from the server. Only loaded on the physical client. */
object ClientPayloadHandler {
    fun handleAccounts(payload: AccountsSnapshotPayload, context: IPayloadContext) {
        ClientEconomy.update(payload)
    }

    fun handleAccountMembers(payload: AccountMembersPayload, context: IPayloadContext) {
        ClientAccounts.update(payload)
    }

    fun handleAccountFeedback(payload: AccountFeedbackPayload, context: IPayloadContext) {
        ClientAccounts.update(payload)
    }

    fun handleOpenLicense(payload: OpenLicensePayload, context: IPayloadContext) {
        Minecraft.getInstance().setScreen(LicenseScreen(payload.dealer))
    }

    fun handlePokerState(payload: PokerStatePayload, context: IPayloadContext) {
        ClientPoker.update(payload)
    }

    fun handleInvitationNotice(payload: InvitationNoticePayload, context: IPayloadContext) = InvitationBanner.handle(payload, context)

    fun handleOpenCounter(payload: OpenCounterPayload, context: IPayloadContext) {
        Minecraft.getInstance().setScreen(CounterScreen(payload))
    }

    /** Opens a shop, or updates the one on screen; a refresh for a shop that isn't on screen is dropped. */
    fun handleOpenShop(payload: OpenShopPayload, context: IPayloadContext) {
        val minecraft = Minecraft.getInstance()
        val current = minecraft.screen as? ShopScreen
        when {
            // A shop that changed from selling to buying (or back) gets its other screen.
            current != null && current.pos == payload.pos && current.buying == payload.buying -> current.update(payload)
            current != null && current.pos == payload.pos -> minecraft.setScreen(ShopScreen(payload))
            !payload.refresh -> minecraft.setScreen(ShopScreen(payload))
        }
    }

    fun handleOpenAuction(payload: OpenAuctionPayload, context: IPayloadContext) {
        ClientAuction.clear()
        Minecraft.getInstance().setScreen(AuctionScreen())
    }

    fun handleAuctionListings(payload: AuctionListingsPayload, context: IPayloadContext) {
        ClientAuction.update(payload)
    }

    fun handleAuctionMailbox(payload: AuctionMailboxPayload, context: IPayloadContext) {
        ClientAuction.update(payload)
    }

    /** The catalog answers `/market` (it opens the screen) or refreshes a Market screen that is already open. */
    fun handleMarketCatalog(payload: MarketCatalogPayload, context: IPayloadContext) {
        ClientMarket.update(payload)
        val minecraft = Minecraft.getInstance()
        val screen = minecraft.screen
        val onMarket = screen is MarketScreen || screen is MarketClosedScreen || screen is MarketEfficiencyScreen
        if (!onMarket && !payload.fresh) return
        // Moving between Market screens is not leaving the Market: the server keeps sending until the player closes it.
        MarketUi.switching = true
        when {
            payload.open && screen is MarketClosedScreen -> minecraft.setScreen(MarketScreen())
            payload.open && !onMarket -> minecraft.setScreen(MarketScreen())
            !payload.open && screen !is MarketClosedScreen -> minecraft.setScreen(MarketClosedScreen())
        }
        MarketUi.switching = false
    }

    fun handleMarketDelta(payload: MarketDeltaPayload, context: IPayloadContext) {
        ClientMarket.update(payload)
    }

    fun handleMarketWindow(payload: MarketWindowPayload, context: IPayloadContext) {
        ClientMarket.update(payload)
    }

    fun handleMarketHistory(payload: MarketHistoryPayload, context: IPayloadContext) {
        ClientMarket.update(payload)
    }

    fun handleMarketResult(payload: MarketResultPayload, context: IPayloadContext) {
        ClientMarket.update(payload)
    }

    fun handleTradeState(payload: TradeStatePayload, context: IPayloadContext) {
        ClientTrade.update(payload)
    }

    fun handleOpenLink(payload: OpenLinkPayload, context: IPayloadContext) {
        Minecraft.getInstance().setScreen(LinkScreen(payload))
    }

    fun handleMenuCasino(payload: MenuCasinoPayload, context: IPayloadContext) {
        MenuCasinos.set(payload.containerId, payload.handle)
    }
}
