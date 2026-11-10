package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.network.AccountsSnapshotPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Payloads from the server. Only loaded on the physical client. */
object ClientPayloadHandler {
    fun handleAccounts(payload: AccountsSnapshotPayload, context: IPayloadContext) {
        ClientEconomy.update(payload)
    }
}
