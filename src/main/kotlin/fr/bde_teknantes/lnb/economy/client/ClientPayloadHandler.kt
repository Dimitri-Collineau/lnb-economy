package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.casino.MenuCasinos
import fr.bde_teknantes.lnb.economy.client.gui.AuctionScreen
import fr.bde_teknantes.lnb.economy.client.gui.CounterScreen
import fr.bde_teknantes.lnb.economy.client.gui.LinkScreen
import fr.bde_teknantes.lnb.economy.client.gui.ShopScreen
import fr.bde_teknantes.lnb.economy.network.AccountsSnapshotPayload
import fr.bde_teknantes.lnb.economy.network.AuctionListingsPayload
import fr.bde_teknantes.lnb.economy.network.AuctionMailboxPayload
import fr.bde_teknantes.lnb.economy.network.OpenAuctionPayload
import fr.bde_teknantes.lnb.economy.network.MenuCasinoPayload
import fr.bde_teknantes.lnb.economy.network.OpenCounterPayload
import fr.bde_teknantes.lnb.economy.network.OpenLinkPayload
import fr.bde_teknantes.lnb.economy.network.OpenShopPayload
import fr.bde_teknantes.lnb.economy.network.TradeStatePayload
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Payloads from the server. Only loaded on the physical client. */
object ClientPayloadHandler {
    fun handleAccounts(payload: AccountsSnapshotPayload, context: IPayloadContext) {
        ClientEconomy.update(payload)
    }

    fun handleOpenCounter(payload: OpenCounterPayload, context: IPayloadContext) {
        Minecraft.getInstance().setScreen(CounterScreen(payload))
    }

    /** Opens a shop, or updates the one on screen; a refresh for a shop that isn't on screen is dropped. */
    fun handleOpenShop(payload: OpenShopPayload, context: IPayloadContext) {
        val minecraft = Minecraft.getInstance()
        val current = minecraft.screen as? ShopScreen
        when {
            current != null && current.pos == payload.pos -> current.update(payload)
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
