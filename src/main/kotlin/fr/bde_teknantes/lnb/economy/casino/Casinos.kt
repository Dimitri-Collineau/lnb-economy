package fr.bde_teknantes.lnb.economy.casino

import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.account.KnownHandles
import fr.bde_teknantes.lnb.economy.account.SpendLimits
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.economy.api.TransferResult
import fr.bde_teknantes.lnb.economy.chip.ChipLedger
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.concurrent.CompletableFuture

/**
 * The accounts of the casinos linked to a machine or a counter: their balance read from the site, and their free
 * balance once the chips in circulation and the rounds in progress are kept back. Server thread only.
 */
object Casinos {
    val book = CasinoBook(System::currentTimeMillis)

    private var server: MinecraftServer? = null
    private var ticks = 0
    private val loading = HashMap<String, CompletableFuture<CasinoBook.Status>>()
    private val names = HashMap<String, String>()

    /** The casino's name on the site, or its handle until the site has answered. */
    fun name(handle: String): String = names[handle] ?: "#$handle"

    /** Every casino linked to something in the world. */
    @Volatile
    var linked: (MinecraftServer) -> Set<String> = { emptySet() }

    fun status(server: MinecraftServer, handle: String, except: Any? = null): CasinoBook.Status {
        val maxAge = EconomyConfig.CASINO_STALE_MINUTES.get() * 60_000L
        val status = book.status(handle, ChipLedger.get(server).outstanding(handle), maxAge, except)
        if (status == CasinoBook.Status.Unknown) refresh(server, handle)
        return status
    }

    /** Reads the account from the site. Several calls for one handle share the same request. */
    fun refresh(server: MinecraftServer, handle: String): CompletableFuture<CasinoBook.Status> {
        loading[handle]?.let { return it }
        val done = CompletableFuture<CasinoBook.Status>()
        loading[handle] = done
        EconomyApi.account(handle).thenAccept { result ->
            server.execute {
                loading.remove(handle)
                when {
                    result is ApiResult.Ok -> {
                        book.update(handle, result.body.balance, result.body.frozen, result.body.isCasino)
                        names[handle] = result.body.name
                    }
                    result is ApiResult.Http && result.problem == Problems.NOT_FOUND -> book.markMissing(handle)
                    else -> LnbEconomy.LOGGER.debug("Couldn't read casino #{}: {}", handle, result.describe())
                }
                val maxAge = EconomyConfig.CASINO_STALE_MINUTES.get() * 60_000L
                done.complete(book.status(handle, ChipLedger.get(server).outstanding(handle), maxAge))
            }
        }
        return done
    }

    fun onTransfer(result: TransferResult) {
        result.from.handle?.let { book.updateBalance(it, result.from.balance) }
        result.to.handle?.let { book.updateBalance(it, result.to.balance) }
    }

    fun onServerStarted(event: ServerStartedEvent) {
        val server = event.server
        this.server = server
        KnownHandles.extra = { this.server?.let(linked).orEmpty() }
        // A casino's account can only pay out what its chips don't need.
        SpendLimits.available = { handle ->
            val current = this.server
            if (current == null || (handle !in linked(current) && ChipLedger.get(current).outstanding(handle) == 0L)) null
            else when (val status = status(current, handle)) {
                is CasinoBook.Status.Open -> status.free
                else -> 0
            }
        }
        linked(server).forEach { refresh(server, it) }
    }

    fun onServerStopped(event: ServerStoppedEvent) {
        server = null
        loading.clear()
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        val server = server ?: return
        if (++ticks < EconomyConfig.REFRESH_MINUTES.get() * 60 * 20) return
        ticks = 0
        linked(server).forEach { refresh(server, it) }
    }
}
