package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.account.SharedAccounts
import fr.bde_teknantes.lnb.economy.auction.Auctions
import fr.bde_teknantes.lnb.economy.casino.Links
import fr.bde_teknantes.lnb.economy.client.ClientPayloadHandler
import fr.bde_teknantes.lnb.economy.counter.Counters
import fr.bde_teknantes.lnb.economy.shop.Shops
import fr.bde_teknantes.lnb.economy.trade.Trades
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * The mod is required on both sides, so every payload is required too.
 * Bump [PROTOCOL_VERSION] whenever a payload changes, so mismatched client and server versions refuse to connect.
 */
object EconomyNetwork {
    private const val PROTOCOL_VERSION = "8"

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(PROTOCOL_VERSION)

        // Client handlers are wrapped in lambdas so their client-only class only loads when a packet arrives,
        // which never happens on a dedicated server.
        registrar.playToClient(AccountsSnapshotPayload.TYPE, AccountsSnapshotPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleAccounts(payload, context)
        }
        registrar.playToClient(OpenCounterPayload.TYPE, OpenCounterPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleOpenCounter(payload, context)
        }
        registrar.playToClient(OpenLinkPayload.TYPE, OpenLinkPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleOpenLink(payload, context)
        }
        registrar.playToClient(MenuCasinoPayload.TYPE, MenuCasinoPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleMenuCasino(payload, context)
        }
        registrar.playToClient(OpenShopPayload.TYPE, OpenShopPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleOpenShop(payload, context)
        }
        registrar.playToClient(TradeStatePayload.TYPE, TradeStatePayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleTradeState(payload, context)
        }
        registrar.playToClient(OpenAuctionPayload.TYPE, OpenAuctionPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleOpenAuction(payload, context)
        }
        registrar.playToClient(AuctionListingsPayload.TYPE, AuctionListingsPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleAuctionListings(payload, context)
        }
        registrar.playToClient(AuctionMailboxPayload.TYPE, AuctionMailboxPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleAuctionMailbox(payload, context)
        }
        registrar.playToClient(AccountMembersPayload.TYPE, AccountMembersPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleAccountMembers(payload, context)
        }
        registrar.playToClient(AccountFeedbackPayload.TYPE, AccountFeedbackPayload.STREAM_CODEC) { payload, context ->
            ClientPayloadHandler.handleAccountFeedback(payload, context)
        }
        registrar.playToServer(AccountCreatePayload.TYPE, AccountCreatePayload.STREAM_CODEC, SharedAccounts::handleCreate)
        registrar.playToServer(AccountEditPayload.TYPE, AccountEditPayload.STREAM_CODEC, SharedAccounts::handleEdit)
        registrar.playToServer(AccountMembersRequestPayload.TYPE, AccountMembersRequestPayload.STREAM_CODEC, SharedAccounts::handleMembersRequest)
        registrar.playToServer(AccountInvitePayload.TYPE, AccountInvitePayload.STREAM_CODEC, SharedAccounts::handleInvite)
        registrar.playToServer(InvitationCancelPayload.TYPE, InvitationCancelPayload.STREAM_CODEC, SharedAccounts::handleCancel)
        registrar.playToServer(InvitationAnswerPayload.TYPE, InvitationAnswerPayload.STREAM_CODEC, SharedAccounts::handleAnswer)
        registrar.playToServer(CounterBuyPayload.TYPE, CounterBuyPayload.STREAM_CODEC, Counters::handleBuy)
        registrar.playToServer(CounterCashPayload.TYPE, CounterCashPayload.STREAM_CODEC, Counters::handleCash)
        registrar.playToServer(CounterChangePayload.TYPE, CounterChangePayload.STREAM_CODEC, Counters::handleChange)
        registrar.playToServer(ShopOrderPayload.TYPE, ShopOrderPayload.STREAM_CODEC, Shops::handleOrder)
        registrar.playToServer(ShopSavePayload.TYPE, ShopSavePayload.STREAM_CODEC, Shops::handleSave)
        registrar.playToServer(ShopMaterialPayload.TYPE, ShopMaterialPayload.STREAM_CODEC, Shops::handleMaterial)
        registrar.playToServer(TradeRequestPayload.TYPE, TradeRequestPayload.STREAM_CODEC, Trades::handleRequest)
        registrar.playToServer(TradeMoneyPayload.TYPE, TradeMoneyPayload.STREAM_CODEC, Trades::handleMoney)
        registrar.playToServer(TradeReadyPayload.TYPE, TradeReadyPayload.STREAM_CODEC, Trades::handleReady)
        registrar.playToServer(AuctionRequestPayload.TYPE, AuctionRequestPayload.STREAM_CODEC, Auctions::handleRequest)
        registrar.playToServer(AuctionSellPayload.TYPE, AuctionSellPayload.STREAM_CODEC, Auctions::handleSell)
        registrar.playToServer(AuctionBuyPayload.TYPE, AuctionBuyPayload.STREAM_CODEC, Auctions::handleBuy)
        registrar.playToServer(AuctionCancelPayload.TYPE, AuctionCancelPayload.STREAM_CODEC, Auctions::handleCancel)
        registrar.playToServer(AuctionCollectPayload.TYPE, AuctionCollectPayload.STREAM_CODEC, Auctions::handleCollect)
        registrar.playToServer(LinkPayload.TYPE, LinkPayload.STREAM_CODEC, Links::handleLink)
        registrar.playToServer(CounterWoodPayload.TYPE, CounterWoodPayload.STREAM_CODEC, Links::handleWood)
        registrar.playToServer(RefreshAccountsPayload.TYPE, RefreshAccountsPayload.STREAM_CODEC, ServerPayloadHandler::handleRefresh)
        registrar.playToServer(SetDefaultAccountPayload.TYPE, SetDefaultAccountPayload.STREAM_CODEC, ServerPayloadHandler::handleSetDefault)
    }
}
