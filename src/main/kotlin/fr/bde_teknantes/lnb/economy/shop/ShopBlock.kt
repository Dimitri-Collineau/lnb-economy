package fr.bde_teknantes.lnb.economy.shop

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * A player shop: sells one item from its own stock for an account, and may buy it back. Its owners and members set it
 * up and fill it; everyone else buys. Only they can break it, and pistons and Create's contraptions can't move it.
 */
class ShopBlock(properties: Properties) : BaseEntityBlock(properties) {
    override fun codec(): MapCodec<ShopBlock> = CODEC

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ShopBlockEntity(pos, state)

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (!level.isClientSide && placer is Player) (level.getBlockEntity(pos) as? ShopBlockEntity)?.placedBy = placer.uuid
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level is ServerLevel && player is ServerPlayer) Shops.use(player, pos)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (level is ServerLevel && !state.`is`(newState.block)) {
            (level.getBlockEntity(pos) as? ShopBlockEntity)?.let { shop ->
                Shops.onRemoved(shop)
                Containers.dropContents(level, pos, shop.stock)
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    companion object {
        val CODEC: MapCodec<ShopBlock> = simpleCodec(::ShopBlock)
    }
}
