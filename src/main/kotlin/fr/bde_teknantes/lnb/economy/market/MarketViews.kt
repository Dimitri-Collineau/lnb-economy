package fr.bde_teknantes.lnb.economy.market

import com.mojang.brigadier.arguments.StringArgumentType
import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.money.Amounts
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.money.Texts
import fr.bde_teknantes.lnb.economy.network.MarketCatalogItem
import fr.bde_teknantes.lnb.economy.network.MarketCatalogPayload
import fr.bde_teknantes.lnb.economy.network.MarketCatalogTier
import fr.bde_teknantes.lnb.economy.network.MarketDeltaPayload
import fr.bde_teknantes.lnb.economy.network.MarketHistoryPayload
import fr.bde_teknantes.lnb.economy.network.MarketOrderPayload
import fr.bde_teknantes.lnb.economy.network.MarketRequestPayload
import fr.bde_teknantes.lnb.economy.network.MarketResultPayload
import fr.bde_teknantes.lnb.economy.network.MarketWindowEntry
import fr.bde_teknantes.lnb.economy.network.MarketWindowPayload
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * Who has the Market screen open, and what they are sent: the catalog when it opens or the site's configuration
 * changes, stock deltas after orders, the player's windows, price histories (cached a minute or two) and the toast
 * for each order's result. Clients never call the site. Server thread only.
 */
object MarketViews {
    private const val HISTORY_TTL_MS = 90_000L
    private const val HISTORY_CACHE_MAX = 500

    private val viewers = HashSet<UUID>()
    private val windows = HashMap<UUID, MarketPlayerWindow>()

    private class Cached(val at: Long, val prices: List<Float>)

    private val history = HashMap<String, Cached>()

    fun registerCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("market").executes { context ->
            open(context.source.playerOrException)
            1
        })
    }

    fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        close(event.entity.uuid)
    }

    private fun close(uuid: UUID) {
        viewers.remove(uuid)
        windows.remove(uuid)
        Markets.viewers = viewers.size
    }

    /** `/market`: nobody without an economy gets in; a closed Market opens the "closed" screen. */
    fun open(player: ServerPlayer) {
        PlayerAccounts.noEconomyMessage(player.uuid)?.let {
            player.sendSystemMessage(it.copy().withStyle(ChatFormatting.RED))
            return
        }
        viewers.add(player.uuid)
        Markets.viewers = viewers.size
        if (!Markets.state.isOpen) {
            send(player, MarketCatalogPayload.closed(fresh = true))
            Markets.refresh(player.server)
            return
        }
        loadWindow(player) { sendCatalog(player, fresh = true) }
    }

    fun handleRequest(payload: MarketRequestPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        when (payload.kind) {
            MarketRequestPayload.OPEN -> open(player)
            MarketRequestPayload.CLOSE -> close(player.uuid)
            MarketRequestPayload.REFRESH -> if (player.uuid in viewers) loadWindow(player) { send(player, windowPayload(player.uuid)) }
            MarketRequestPayload.HISTORY -> if (player.uuid in viewers) sendHistory(player, payload.item, payload.period)
        }
    }

    fun handleOrder(payload: MarketOrderPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        // Spec 9.1: the player is at the screen.
        if (player.uuid !in viewers) return
        val account = payload.account?.takeIf { it.isNotBlank() }
        if (payload.sell) {
            MarketOrders.sell(player, payload.item, payload.quantity, payload.expectedTotal, account.takeIf { Markets.state.settings?.saleAccount == true })
        } else {
            MarketOrders.buy(player, payload.item, payload.quantity, payload.expectedTotal, account)
        }
    }

    /** A new state was read: everyone watching gets a fresh catalog (or the closed screen). */
    fun onState(server: MinecraftServer) {
        for (uuid in viewers.toList()) {
            val player = server.playerList.getPlayer(uuid)
            if (player == null) { close(uuid); continue }
            if (!Markets.state.isOpen) send(player, MarketCatalogPayload.closed())
            else if (windows.containsKey(uuid)) sendCatalog(player)
            else loadWindow(player) { sendCatalog(player) }
        }
    }

    /** Stocks changed (a `market` event or an order): watchers get the new stocks. */
    fun onStocks(server: MinecraftServer, ids: Collection<String>) {
        if (viewers.isEmpty()) return
        val stocks = ids.mapNotNull { id -> Markets.state.stockNow(id)?.let { id to it } }.toMap()
        if (stocks.isEmpty()) return
        val delta = MarketDeltaPayload(Markets.state.revision, Markets.state.view?.mintRemaining ?: 0, stocks)
        for (uuid in viewers) server.playerList.getPlayer(uuid)?.let { send(it, delta) }
    }

    /** After an order of [uuid]'s: their windows changed too. */
    fun afterOrder(server: MinecraftServer, uuid: UUID) {
        val player = server.playerList.getPlayer(uuid) ?: return
        if (uuid in viewers) loadWindow(player) { send(player, windowPayload(uuid)) }
    }

    /** The toast over the screen, when the player has it open. */
    fun toast(player: ServerPlayer, ok: Boolean, message: Component) {
        if (player.uuid in viewers) send(player, MarketResultPayload(ok, message))
    }

    private fun send(player: ServerPlayer, payload: net.minecraft.network.protocol.common.custom.CustomPacketPayload) =
        PacketDistributor.sendToPlayer(player, payload)

    private fun loadWindow(player: ServerPlayer, then: () -> Unit) {
        val server = player.server
        EconomyApi.marketPlayer(player.uuid).thenAccept { result ->
            server.execute {
                if (player.hasDisconnected() || player.uuid !in viewers) return@execute
                if (result is ApiResult.Ok) {
                    windows[player.uuid] = result.body
                    then()
                } else if (result.isDisabled) {
                    Markets.state.markClosed()
                    send(player, MarketCatalogPayload.closed())
                } else {
                    player.sendSystemMessage(Component.translatable("economy.lnbeconomy.market.refused", Texts.problem(result)).withStyle(ChatFormatting.RED))
                    if (windows[player.uuid] == null) close(player.uuid)
                }
            }
        }
    }

    private fun entries(window: MarketPlayerWindow?): List<MarketWindowEntry> =
        window?.tiers?.map { MarketWindowEntry(it.tier, it.nominal, it.rate, it.toNextBracket, it.q1, it.q2, it.nextRate) }.orEmpty()

    private fun windowPayload(uuid: UUID) = windows[uuid].let { MarketWindowPayload(entries(it), it?.capped == true) }

    private fun sendCatalog(player: ServerPlayer, fresh: Boolean = false) {
        val state = Markets.state
        val view = state.view
        if (!state.isOpen || view == null) return send(player, MarketCatalogPayload.closed())
        val window = windows[player.uuid]
        val items = view.items.map {
            MarketCatalogItem(it.id, it.tier, it.sellable, it.buyable, it.p0, it.pMin, it.pMax, it.s0, it.sMin, it.sMax, it.k, it.alpha, state.stockNow(it.id) ?: it.stock)
        }
        val tiers = view.tiers.map { MarketCatalogTier(it.tier, it.q1, it.q2, it.midPercent, it.tailPercent) }
        val s = view.settings
        send(
            player,
            MarketCatalogPayload(
                true, view.revision, s.sell, s.buy, s.saleAccount, s.sigma, s.maxOrderItems, view.mintRemaining,
                tiers, items, entries(window), window?.capped == true,
                Amounts.fromLnbits(EconomyConfig.PAY_CONFIRM_ABOVE.get()), fresh,
            ),
        )
    }

    private fun sendHistory(player: ServerPlayer, item: String, period: String) {
        if (Markets.state.item(item) == null || period !in MarketRequestPayload.PERIODS) return
        val key = "$item|$period"
        val now = System.currentTimeMillis()
        history[key]?.takeIf { now - it.at < HISTORY_TTL_MS }?.let {
            return send(player, MarketHistoryPayload(item, period, it.prices))
        }
        val server = player.server
        EconomyApi.marketHistory(item, period).thenAccept { result ->
            val points = (result as? ApiResult.Ok)?.body?.points?.map { it.price.toFloat() } ?: return@thenAccept
            val prices = downsample(points, MarketHistoryPayload.MAX_POINTS)
            server.execute {
                if (history.size >= HISTORY_CACHE_MAX) history.clear()
                history[key] = Cached(System.currentTimeMillis(), prices)
                if (!player.hasDisconnected() && player.uuid in viewers) send(player, MarketHistoryPayload(item, period, prices))
            }
        }
    }

    /** At most [max] points, evenly spread, keeping the first and the last. */
    fun downsample(points: List<Float>, max: Int): List<Float> {
        if (points.size <= max) return points
        return List(max) { i -> points[(i.toLong() * (points.size - 1) / (max - 1)).toInt()] }
    }
}
