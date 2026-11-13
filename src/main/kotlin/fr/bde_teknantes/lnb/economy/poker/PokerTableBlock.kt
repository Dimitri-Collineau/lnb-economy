package fr.bde_teknantes.lnb.economy.poker

import fr.bde_teknantes.lnb.economy.ModContent
import fr.bde_teknantes.lnb.economy.casino.CasinoLinks
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A poker table of the house: casino hold'em for up to three players at once, against the dealer (see [HoldemRules]).
 * Linked to a casino with the link wand like a counter; a right click opens the table. Breaking it mid-round gives
 * every seat its stakes back.
 */
class PokerTableBlock(properties: Properties) : Block(properties), EntityBlock {
    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level is ServerLevel && player is ServerPlayer) PokerTables.open(player, pos)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (level is ServerLevel && !state.`is`(newState.block)) {
            (level.getBlockEntity(pos) as? PokerTableBlockEntity)?.abort(level)
            CasinoLinks.get(level.server).remove(level.dimension(), pos)
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = SHAPE

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = PokerTableBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide || type != ModContent.POKER_ENTITY.get()) return null
        return BlockEntityTicker { tickLevel, _, _, entity -> (entity as PokerTableBlockEntity).serverTick(tickLevel as ServerLevel) }
    }

    companion object {
        /** The game's name for the config switch, the link screen and the logs. */
        const val GAME = "holdem"

        // A foot, a column, and the top overhanging them.
        private val SHAPE: VoxelShape = Shapes.or(box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0), box(6.0, 2.0, 6.0, 10.0, 12.0, 10.0), box(0.0, 12.0, 0.0, 16.0, 16.0, 16.0))
    }
}
