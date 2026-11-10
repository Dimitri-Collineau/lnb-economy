package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.client.gui.InventoryBalance
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge

/** Client entrypoint, never loaded on a dedicated server. */
@Mod(value = LnbEconomy.ID, dist = [Dist.CLIENT])
class LnbEconomyClient(modBus: IEventBus, container: ModContainer) {
    init {
        NeoForge.EVENT_BUS.addListener(ClientEconomy::onLoggingOut)
        NeoForge.EVENT_BUS.addListener(InventoryBalance::onInit)
        NeoForge.EVENT_BUS.addListener(InventoryBalance::onRenderPre)
        NeoForge.EVENT_BUS.addListener(InventoryBalance::onRenderPost)
    }
}
