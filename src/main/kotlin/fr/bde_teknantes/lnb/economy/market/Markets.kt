package fr.bde_teknantes.lnb.economy.market

import com.google.gson.JsonObject
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.event.TagsUpdatedEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/**
 * The server's side of the Market: it reads the site's state, keeps it current from the event stream and relays it
 * to players. Clients never call the site. Server thread, except [onEvent], which hops onto it.
 */
object Markets {
    val state = MarketState()

    private var server: MinecraftServer? = null
    private var ticks = 0
    private var minutes = 0
    private var loading = false

    /** How many players have the Market screen open: the state is read more often while one does. */
    @Volatile
    var viewers = 0

    fun onServerStarted(event: ServerStartedEvent) {
        server = event.server
        MarketRecipes.forget()
        onStreamConnected(event.server)
    }

    fun onServerStopped(event: ServerStoppedEvent) {
        server = null
        loading = false
    }

    /** A data-pack reload: the recipes may have changed. */
    fun onTagsUpdated(event: TagsUpdatedEvent) {
        if (event.updateCause != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return
        server?.let { MarketRecipes.send(it) }
    }

    /** At start and at each (re)connection of the event stream (section 3.3 of the spec). Server thread. */
    fun onStreamConnected(server: MinecraftServer) {
        refresh(server)
        MarketRecipes.send(server)
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        val server = server ?: return
        if (++ticks < 20 * 60) return
        ticks = 0
        minutes++
        // Flags send no event: read the state again, every minute while closed, every few while open.
        if (!state.isOpen || (viewers > 0 && minutes % 2 == 0) || minutes % 5 == 0) refresh(server)
        if (minutes % 5 == 0) MarketRecipes.send(server)
    }

    /** Reads `GET /state`; 503 `disabled` means closed. */
    fun refresh(server: MinecraftServer) {
        if (loading) return
        loading = true
        EconomyApi.marketState().thenAccept { result ->
            server.execute {
                loading = false
                when {
                    result is ApiResult.Ok -> {
                        val wasOpen = state.isOpen
                        state.apply(result.body)
                        MarketViews.onState(server)
                        if (!wasOpen) LnbEconomy.LOGGER.info("The Market is open: {} items", result.body.items.size)
                    }
                    result.isDisabled -> {
                        if (!state.closed) LnbEconomy.LOGGER.info("The Market is off on the site")
                        state.markClosed()
                        MarketViews.onState(server)
                    }
                    else -> LnbEconomy.LOGGER.debug("Couldn't read the Market's state: {}", result.describe())
                }
            }
        }
    }

    /** The `market` and `market-config` events of the stream, called from its thread. */
    fun onEvent(server: MinecraftServer, type: String, body: JsonObject) {
        val revision = body.get("revision")?.takeIf { it.isJsonPrimitive }?.asLong ?: -1
        when (type) {
            "market" -> {
                val updates = body.getAsJsonArray("items")?.mapNotNull {
                    runCatching { ApiClient.GSON.fromJson(it, MarketEventItem::class.java) }.getOrNull()
                }.orEmpty()
                server.execute {
                    if (state.onStocks(revision, updates)) refresh(server) else MarketViews.onStocks(server, updates.map { it.id })
                }
            }
            "market-config" -> server.execute { if (state.needsRefresh(revision)) refresh(server) }
        }
    }
}
