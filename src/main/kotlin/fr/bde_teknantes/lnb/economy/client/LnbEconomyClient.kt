package fr.bde_teknantes.lnb.economy.client

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.ModContent
import fr.bde_teknantes.lnb.economy.client.gui.AuctioneerRenderer
import fr.bde_teknantes.lnb.economy.client.gui.CounterTellerRenderer
import fr.bde_teknantes.lnb.economy.client.gui.InventoryBalance
import fr.bde_teknantes.lnb.economy.client.gui.ShopShowcaseRenderer
import fr.bde_teknantes.lnb.economy.client.gui.ShopSettingsScreen
import fr.bde_teknantes.lnb.economy.client.gui.TradeScreen
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.common.NeoForge

/** Client entrypoint, never loaded on a dedicated server. */
@Mod(value = LnbEconomy.ID, dist = [Dist.CLIENT])
class LnbEconomyClient(modBus: IEventBus, container: ModContainer) {
    init {
        modBus.addListener<RegisterMenuScreensEvent> { event ->
            event.register(ModContent.SHOP_MENU.get(), ::ShopSettingsScreen)
            event.register(ModContent.TRADE_MENU.get(), ::TradeScreen)
        }
        modBus.addListener(ClientTrade::registerKeys)
        modBus.addListener<EntityRenderersEvent.RegisterRenderers> {
            it.registerEntityRenderer(ModContent.AUCTIONEER.get(), ::AuctioneerRenderer)
            it.registerBlockEntityRenderer(ModContent.COUNTER_ENTITY.get(), ::CounterTellerRenderer)
            it.registerBlockEntityRenderer(ModContent.SHOP_ENTITY.get(), ::ShopShowcaseRenderer)
        }
        NeoForge.EVENT_BUS.addListener(ClientTrade::onClientTick)
        NeoForge.EVENT_BUS.addListener(ClientEconomy::onLoggingOut)
        NeoForge.EVENT_BUS.addListener(InventoryBalance::onInit)
        NeoForge.EVENT_BUS.addListener(InventoryBalance::onRenderPre)
        NeoForge.EVENT_BUS.addListener(InventoryBalance::onRenderPost)
    }
}
