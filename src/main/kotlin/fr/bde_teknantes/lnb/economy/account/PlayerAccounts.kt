package fr.bde_teknantes.lnb.economy.account

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.EconomyConfig
import fr.bde_teknantes.lnb.economy.api.AccountView
import fr.bde_teknantes.lnb.economy.api.EconomyApi
import fr.bde_teknantes.lnb.economy.api.Problems
import fr.bde_teknantes.lnb.economy.api.ServerPlayer as SitePlayer
import fr.bde_teknantes.lnb.economy.api.TransferResult
import fr.bde_teknantes.lnb.economy.casino.Casinos
import fr.bde_teknantes.lnb.economy.network.AccountEntry
import fr.bde_teknantes.lnb.economy.network.AccountsSnapshotPayload
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * What the server knows about each online player's accounts, read from the site when they join and kept up to date by
 * every transfer the server makes. Only touched on the server thread.
 */
object PlayerAccounts {
    data class Entry(val status: EconomyStatus, val player: SitePlayer?, val fetchedAt: Long)

    /** A player can't make the server read the site more often than this. */
    private const val MIN_REFRESH_MS = 10_000L

    private val entries = HashMap<UUID, Entry>()
    private val loading = HashMap<UUID, CompletableFuture<Entry>>()

    fun get(uuid: UUID): Entry? = entries[uuid]

    /** The player's accounts, when they have an economy and the site answered. */
    fun accounts(uuid: UUID): SitePlayer? = entries[uuid]?.player

    /**
     * Reads the player's accounts from the site, then sends them to the player.
     * With [force] false, an answer younger than [MIN_REFRESH_MS] is reused.
     */
    fun refresh(server: MinecraftServer, uuid: UUID, force: Boolean = true): CompletableFuture<Entry> {
        val current = entries[uuid]
        if (!force && current != null && current.status != EconomyStatus.LOADING &&
            System.currentTimeMillis() - current.fetchedAt < MIN_REFRESH_MS
        ) {
            return CompletableFuture.completedFuture(current)
        }
        loading[uuid]?.let { return it }

        val done = CompletableFuture<Entry>()
        loading[uuid] = done
        EconomyApi.player(uuid).thenAccept { result ->
            server.execute {
                loading.remove(uuid)
                val entry = entryOf(result)
                // The player may have left while the site answered.
                if (server.playerList.getPlayer(uuid) != null) {
                    // Keep the last good accounts on a failed refresh: the inventory goes on showing them.
                    if (entry.status == EconomyStatus.UNAVAILABLE && current?.player != null) {
                        entries[uuid] = current.copy(fetchedAt = entry.fetchedAt)
                    } else {
                        entries[uuid] = entry
                    }
                    send(server, uuid)
                }
                done.complete(entry)
            }
        }
        return done
    }

    /** Reads again when the cache is older than the configured refresh time. */
    fun refreshIfOld(server: MinecraftServer, uuid: UUID) {
        val entry = entries[uuid]
        val maxAge = EconomyConfig.REFRESH_MINUTES.get() * 60_000L
        if (entry == null || System.currentTimeMillis() - entry.fetchedAt >= maxAge) refresh(server, uuid, force = false)
    }

    /** The site's answer to a default account change: the accounts again. */
    fun update(server: MinecraftServer, uuid: UUID, player: SitePlayer) {
        entries[uuid] = Entry(EconomyStatus.READY, player, System.currentTimeMillis())
        send(server, uuid)
    }

    /** A transfer gives the balance of both accounts right after it: update everyone who holds one of them. */
    fun applyTransfer(server: MinecraftServer, result: TransferResult) {
        Casinos.onTransfer(result)
        val balances = mapOf(result.from.accountId to result.from.balance, result.to.accountId to result.to.balance)
        for ((uuid, entry) in entries.toMap()) {
            val player = entry.player ?: continue
            if (player.accounts.none { it.id in balances }) continue
            val accounts = player.accounts.map { account -> balances[account.id]?.let { account.copy(balance = it) } ?: account }
            entries[uuid] = entry.copy(player = player.copy(accounts = accounts))
            send(server, uuid)
        }
    }

    /** Online players who hold [accountId] (all the Minecraft accounts of its members who are on). */
    fun holdersOf(server: MinecraftServer, accountId: String): List<ServerPlayer> =
        entries.filterValues { entry -> entry.player?.accounts?.any { it.id == accountId } == true }
            .keys.mapNotNull(server.playerList::getPlayer)

    /** Online Minecraft accounts of the LNB account [userId]. */
    fun onlineOf(server: MinecraftServer, userId: String): List<ServerPlayer> =
        entries.filterValues { it.player?.userId == userId }.keys.mapNotNull(server.playerList::getPlayer)

    fun send(server: MinecraftServer, uuid: UUID) {
        val player = server.playerList.getPlayer(uuid) ?: return
        PacketDistributor.sendToPlayer(player, snapshot(entries[uuid]))
    }

    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        entries[player.uuid] = Entry(EconomyStatus.LOADING, null, 0)
        refresh(player.server, player.uuid)
    }

    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        entries.remove(event.entity.uuid)
    }

    fun onServerStopped(event: ServerStoppedEvent) {
        entries.clear()
        loading.clear()
    }

    private fun entryOf(result: ApiResult<SitePlayer>): Entry {
        val now = System.currentTimeMillis()
        return when {
            result is ApiResult.Ok -> Entry(EconomyStatus.READY, result.body, now)
            result is ApiResult.Http && result.problem == Problems.NOT_LINKED -> Entry(EconomyStatus.NOT_LINKED, null, now)
            result is ApiResult.Http && result.problem == Problems.NOT_ELIGIBLE -> Entry(EconomyStatus.NOT_ELIGIBLE, null, now)
            else -> {
                LnbEconomy.LOGGER.debug("Couldn't read a player's accounts: {}", result.describe())
                Entry(EconomyStatus.UNAVAILABLE, null, now)
            }
        }
    }

    private fun snapshot(entry: Entry?): AccountsSnapshotPayload {
        val player = entry?.player
        val status = when {
            entry == null -> EconomyStatus.LOADING
            player != null -> EconomyStatus.READY
            else -> entry.status
        }
        return AccountsSnapshotPayload(status, player?.accounts.orEmpty().map(::entryOf), player?.defaultAccountId)
    }

    private fun entryOf(account: AccountView) =
        AccountEntry(account.id, account.handle, account.name, account.balance, account.role, account.frozen, account.canSpend)

    /** The message for a player who has no economy, or null when they have one (or it's still loading). */
    fun noEconomyMessage(uuid: UUID) = when (entries[uuid]?.status) {
        EconomyStatus.NOT_LINKED -> Component.translatable("economy.lnbeconomy.problem.notLinked")
        EconomyStatus.NOT_ELIGIBLE -> Component.translatable("economy.lnbeconomy.problem.notEligible")
        else -> null
    }
}
