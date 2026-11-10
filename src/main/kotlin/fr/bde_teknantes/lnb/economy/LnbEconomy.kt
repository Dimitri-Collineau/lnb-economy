package fr.bde_teknantes.lnb.economy

import com.mojang.logging.LogUtils
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.slf4j.Logger

/**
 * Common entrypoint, loaded on both the physical client and the dedicated server.
 *
 * Only the logical server talks to LNB's API. Client-only code lives in [fr.bde_teknantes.lnb.economy.client.LnbEconomyClient].
 */
@Mod(LnbEconomy.ID)
class LnbEconomy(modBus: IEventBus, container: ModContainer) {
    companion object {
        const val ID = "lnbeconomy"

        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()

        fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ID, path)
    }
}
