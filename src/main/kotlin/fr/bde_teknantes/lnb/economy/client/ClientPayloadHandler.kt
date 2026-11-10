package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.client.gui.CounterScreen
import fr.bde_teknantes.lnb.economy.client.gui.LinkScreen
import fr.bde_teknantes.lnb.economy.network.AccountsSnapshotPayload
import fr.bde_teknantes.lnb.economy.network.MenuCasinoPayload
import fr.bde_teknantes.lnb.economy.network.OpenCounterPayload
import fr.bde_teknantes.lnb.economy.network.OpenLinkPayload
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

    fun handleOpenLink(payload: OpenLinkPayload, context: IPayloadContext) {
        Minecraft.getInstance().setScreen(LinkScreen(payload))
    }

    fun handleMenuCasino(payload: MenuCasinoPayload, context: IPayloadContext) {
        ClientCasinoMenus.set(payload.containerId, payload.handle)
    }
}
