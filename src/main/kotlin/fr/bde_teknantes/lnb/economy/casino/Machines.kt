package fr.bde_teknantes.lnb.economy.casino

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.neoforged.fml.ModList

/** Casino machines from another mod. Only MineVegas Casino for now. */
interface MachineSupport {
    /** The anchor of the machine that has a block at [pos], or null when there is none. */
    fun anchorAt(level: Level, pos: BlockPos): BlockPos?

    /** The game of the machine anchored at [anchor]: `slots`, `roulette`... */
    fun gameAt(level: Level, anchor: BlockPos): String?
}

object Machines {
    const val MINEVEGAS = "roulettecasino"

    /** Null without MineVegas. Its classes are only loaded when it is installed. */
    val support: MachineSupport? by lazy {
        if (ModList.get().isLoaded(MINEVEGAS)) fr.bde_teknantes.lnb.economy.casino.minevegas.MineVegasMachines else null
    }
}
