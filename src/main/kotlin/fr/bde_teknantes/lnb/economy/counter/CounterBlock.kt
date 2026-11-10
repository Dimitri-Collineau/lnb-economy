package fr.bde_teknantes.lnb.economy.counter

import fr.bde_teknantes.lnb.economy.casino.CasinoLinks
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * A casino's counter: players buy chips with LNBits, cash them, or get change. Linked to its casino with the link
 * wand, like the machines.
 */
class CounterBlock(properties: Properties) : Block(properties) {
    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level is ServerLevel && player is ServerPlayer) Counters.open(player, pos)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (level is ServerLevel && !state.`is`(newState.block)) CasinoLinks.get(level.server).remove(level.dimension(), pos)
        super.onRemove(state, level, pos, newState, movedByPiston)
    }
}
