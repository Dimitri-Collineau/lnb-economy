package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.network.AccountsSnapshotPayload
import fr.bde_teknantes.lnb.economy.network.EconomyStatus
import fr.bde_teknantes.lnb.economy.network.RefreshAccountsPayload
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.network.PacketDistributor

/** The player's accounts, as the server last sent them. */
object ClientEconomy {
    private val EMPTY = AccountsSnapshotPayload(EconomyStatus.LOADING, emptyList(), null)

    /** How often an open inventory asks the server for fresh balances. */
    private const val REFRESH_MS = 5 * 60_000L

    var snapshot: AccountsSnapshotPayload = EMPTY
        private set

    private var receivedAt = 0L

    /** Bumped on every snapshot, so open screens know when to rebuild. */
    var revision = 0
        private set

    fun update(snapshot: AccountsSnapshotPayload) {
        this.snapshot = snapshot
        receivedAt = System.currentTimeMillis()
        revision++
    }

    /** Asks the server to read the accounts again. [always] skips the age check (the server still rate-limits). */
    fun requestRefresh(always: Boolean) {
        if (Minecraft.getInstance().connection == null) return
        if (!always && System.currentTimeMillis() - receivedAt < REFRESH_MS) return
        PacketDistributor.sendToServer(RefreshAccountsPayload)
    }

    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        snapshot = EMPTY
        receivedAt = 0
        revision++
    }
}
