package fr.bde_teknantes.lnb.economy

import com.mojang.logging.LogUtils
import fr.bde_teknantes.lnb.economy.account.PlayerAccounts
import fr.bde_teknantes.lnb.economy.ledger.TransferJournal
import fr.bde_teknantes.lnb.economy.network.EconomyNetwork
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge
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

        modBus.addListener(EconomyNetwork::register)

        NeoForge.EVENT_BUS.addListener(TransferJournal::onServerStarted)
        NeoForge.EVENT_BUS.addListener(TransferJournal::onServerStopped)
        NeoForge.EVENT_BUS.addListener(TransferJournal::onServerTick)
        NeoForge.EVENT_BUS.addListener(PlayerAccounts::onPlayerLoggedIn)
        NeoForge.EVENT_BUS.addListener(PlayerAccounts::onPlayerLoggedOut)
        NeoForge.EVENT_BUS.addListener(PlayerAccounts::onServerStopped)
    }

    companion object {
        const val ID = "lnbeconomy"

        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()

        fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ID, path)
    }
}
