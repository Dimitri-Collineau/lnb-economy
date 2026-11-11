package fr.bde_teknantes.lnb.economy

import com.mojang.logging.LogUtils
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.casino.CasinoLinks
import fr.bde_teknantes.lnb.economy.casino.Casinos
import fr.bde_teknantes.lnb.economy.casino.Links
import fr.bde_teknantes.lnb.economy.casino.Machines
import fr.bde_teknantes.lnb.economy.casino.minevegas.MineVegasEvents
import fr.bde_teknantes.lnb.economy.chip.LostChips
import fr.bde_teknantes.lnb.economy.command.EconomyCommands
import fr.bde_teknantes.lnb.economy.command.Payments
import fr.bde_teknantes.lnb.economy.counter.Counters
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.mail.ItemMailbox
import fr.bde_teknantes.lnb.economy.network.EconomyNetwork
import fr.bde_teknantes.lnb.economy.shop.ShopItemHandler
import fr.bde_teknantes.lnb.economy.shop.Shops
import fr.bde_teknantes.lnb.economy.trade.Trades
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.ModList
import net.neoforged.fml.config.ModConfig
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent
import net.neoforged.neoforge.event.level.BlockEvent
import org.slf4j.Logger

/**
 * Common entrypoint, loaded on both the physical client and the dedicated server.
 *
 * Only the logical server talks to LNB's API. Client-only code lives in [fr.bde_teknantes.lnb.economy.client.LnbEconomyClient].
 */
@Mod(LnbEconomy.ID)
class LnbEconomy(modBus: IEventBus, container: ModContainer) {
    init {
        // COMMON, not SERVER: SERVER configs are synced to every player.
        container.registerConfig(ModConfig.Type.COMMON, EconomyConfig.SPEC, "$ID-server.toml")

        ModContent.register(modBus)
        modBus.addListener(EconomyNetwork::register)

        Payments.register()
        Counters.register()
        Shops.register()
        Trades.register()
        modBus.addListener(ShopItemHandler::register)
        Casinos.linked = { server -> CasinoLinks.get(server).handles }
        NeoForge.EVENT_BUS.addListener(EconomyCommands::register)

        NeoForge.EVENT_BUS.addListener(TransferJournal::onServerStarted)
        NeoForge.EVENT_BUS.addListener(TransferJournal::onServerStopped)
        NeoForge.EVENT_BUS.addListener(TransferJournal::onServerTick)
        NeoForge.EVENT_BUS.addListener(PlayerAccounts::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(PlayerAccounts::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(PlayerAccounts::onServerStopped)
        NeoForge.EVENT_BUS.addListener(Casinos::onServerStarted)
        NeoForge.EVENT_BUS.addListener(Casinos::onServerStopped)
        NeoForge.EVENT_BUS.addListener(Casinos::onServerTick)
        NeoForge.EVENT_BUS.addListener(ItemMailbox::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(ItemMailbox::registerCommands)
        NeoForge.EVENT_BUS.addListener(Trades::registerCommands)
        NeoForge.EVENT_BUS.addListener(Trades::onServerTick)
        NeoForge.EVENT_BUS.addListener(Trades::onLoggedOut)
        NeoForge.EVENT_BUS.addListener(Trades::onServerStopped)
        NeoForge.EVENT_BUS.addListener<BlockEvent.BreakEvent>(EventPriority.HIGH, Shops::onBreak)
        NeoForge.EVENT_BUS.addListener<BlockEvent.BreakEvent>(EventPriority.LOWEST) { if (!it.isCanceled) Links.onBlockBroken(it) }
        if (ModList.get().isLoaded(Machines.MINEVEGAS)) {
            // MineVegas classes are only touched when it is installed.
            modBus.addListener(MineVegasEvents::onCommonSetup)
            NeoForge.EVENT_BUS.addListener(MineVegasEvents::onServerStarted)
            NeoForge.EVENT_BUS.addListener(MineVegasEvents::onContainerOpen)
        }
        NeoForge.EVENT_BUS.addListener<ItemExpireEvent>(EventPriority.LOWEST, LostChips::onExpire)
        NeoForge.EVENT_BUS.addListener(LostChips::onLeaveLevel)
    }

    companion object {
        const val ID = "lnbeconomy"

        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()

        fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ID, path)
    }
}
