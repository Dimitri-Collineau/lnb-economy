package fr.bde_teknantes.lnb.economy.auction

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * An auction house desk, placed by operators where players meet. Unbreakable outside creative mode, like bedrock, and
 * not craftable: it is in the operators' creative tab.
 */
class AuctionBlock(properties: Properties) : Block(properties) {
    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (!level.isClientSide && player is ServerPlayer) Auctions.open(player)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
