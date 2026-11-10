package fr.bde_teknantes.lnb.economy.network

import fr.bde_teknantes.lnb.economy.casino.Links
import fr.bde_teknantes.lnb.economy.client.ClientPayloadHandler
import fr.bde_teknantes.lnb.economy.counter.Counters
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * The mod is required on both sides, so every payload is required too.
 * Bump [PROTOCOL_VERSION] whenever a payload changes, so mismatched client and server versions refuse to connect.
 */
object EconomyNetwork {
    private const val PROTOCOL_VERSION = "1"

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
        registrar.playToServer(CounterBuyPayload.TYPE, CounterBuyPayload.STREAM_CODEC, Counters::handleBuy)
        registrar.playToServer(CounterCashPayload.TYPE, CounterCashPayload.STREAM_CODEC, Counters::handleCash)
        registrar.playToServer(CounterChangePayload.TYPE, CounterChangePayload.STREAM_CODEC, Counters::handleChange)
        registrar.playToServer(LinkPayload.TYPE, LinkPayload.STREAM_CODEC, Links::handleLink)
        registrar.playToServer(RefreshAccountsPayload.TYPE, RefreshAccountsPayload.STREAM_CODEC, ServerPayloadHandler::handleRefresh)
        registrar.playToServer(SetDefaultAccountPayload.TYPE, SetDefaultAccountPayload.STREAM_CODEC, ServerPayloadHandler::handleSetDefault)
    }
}
