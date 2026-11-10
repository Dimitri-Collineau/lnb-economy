package fr.bde_teknantes.lnb.economy.casino.minevegas

import fr.bde_teknantes.lnb.economy.casino.MachineSupport
import fr.minevegas.casino.CasinoMachine
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

/** MineVegas machines are multi-blocks: every part leads to the block entity of the anchor. */
object MineVegasMachines : MachineSupport {
    override fun anchorAt(level: Level, pos: BlockPos): BlockPos? {
        val machine = CasinoMachine.at(level, pos) ?: return null
        return if (CasinoMachine.type(machine).isNullOrEmpty()) null else machine.blockPos
    }

    override fun gameAt(level: Level, anchor: BlockPos): String? =
        CasinoMachine.at(level, anchor)?.let(CasinoMachine::type)?.takeIf { it.isNotEmpty() }
}
