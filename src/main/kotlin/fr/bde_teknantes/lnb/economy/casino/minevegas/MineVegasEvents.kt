package fr.bde_teknantes.lnb.economy.casino.minevegas

import fr.bde_teknantes.lnb.economy.LnbEconomy
import fr.bde_teknantes.lnb.economy.casino.CasinoLinks
import fr.bde_teknantes.lnb.economy.network.MenuCasinoPayload
import fr.minevegas.casino.CasinoSettings
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.network.PacketDistributor

/** Only registered when MineVegas is installed. */
object MineVegasEvents {
    /**
     * Every class a casino mixin targets. Some (inner classes) would only load in the middle of the first game; loading
     * them at startup makes a changed MineVegas fail right away instead.
     */
    private val MIXIN_TARGETS = listOf(
        "fr.minevegas.casino.CasinoSettings",
        "fr.minevegas.casino.CasinoBets",
        "fr.minevegas.casino.SlotMachineBlockEntity",
        "fr.minevegas.casino.RouletteBlockEntity",
        "fr.minevegas.casino.FortuneWheelBlockEntity",
        "fr.minevegas.casino.PlinkoBlockEntity",
        "fr.minevegas.casino.DuckDerbyBlockEntity",
        "fr.minevegas.casino.DuckDerbyBlockEntity\$Participant",
        "fr.minevegas.casino.BlackjackTableBlockEntity",
        "fr.minevegas.casino.BlackjackTableBlockEntity\$Events",
        "fr.minevegas.casino.PokerTableBlockEntity",
        "fr.minevegas.casino.PokerTableBlockEntity\$Events",
        "fr.minevegas.casino.SlotMachineMenu",
        "fr.minevegas.casino.RouletteMenu",
        "fr.minevegas.casino.FortuneWheelMenu",
        "fr.minevegas.casino.PlinkoMenu",
        "fr.minevegas.casino.DuckDerbyMenu",
        "fr.minevegas.casino.BlackjackMenu",
        "fr.minevegas.casino.PokerMenu",
    )

    fun onCommonSetup(event: FMLCommonSetupEvent) {
        val loader = LnbEconomy::class.java.classLoader
        for (name in MIXIN_TARGETS) {
            try {
                // Loading without static initialisers is enough to apply the mixins.
                Class.forName(name, false, loader)
            } catch (e: ClassNotFoundException) {
                throw IllegalStateException("MineVegas class $name is missing: this version of LNB Economy doesn't support the installed MineVegas Casino", e)
            }
        }
        LnbEconomy.LOGGER.info("Casino mixins applied to {} MineVegas classes", MIXIN_TARGETS.size)
    }

    fun onServerStarted(event: ServerStartedEvent) {
        LnbEconomy.LOGGER.info("Casino machines take chips only. Best payouts, stake included: {}", MaxPayouts.describe())
    }

    /** Tells the client which casino an opened casino menu belongs to, for its bet slots. */
    fun onContainerOpen(event: PlayerContainerEvent.Open) {
        val player = event.entity as? ServerPlayer ?: return
        val machine = CasinoSettings.menuMachine(event.container) ?: return
        val level = machine.level as? ServerLevel ?: return
        val handle = CasinoLinks.get(level.server).get(level.dimension(), machine.blockPos)?.handle
        PacketDistributor.sendToPlayer(player, MenuCasinoPayload(event.container.containerId, handle))
    }
}
