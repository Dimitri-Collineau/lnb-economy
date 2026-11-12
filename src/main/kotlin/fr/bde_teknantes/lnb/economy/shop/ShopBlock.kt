package fr.bde_teknantes.lnb.economy.shop

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A player shop: sells one item from its own stock for an account, and may buy it back. Its owners and members set it
 * up and fill it; everyone else buys. Only they can break it, and pistons and Create's contraptions can't move it.
 *
 * It stands two blocks tall, like a door: the [DoubleBlockHalf.LOWER] half is the pedestal and holds the stock and the
 * block entity; the upper half is the glass cloche, where the item sold is shown. Either half opens the shop, and
 * breaking either breaks both; only the lower half drops the item. Each half draws its own part of the model, so the
 * world lights it where it stands. The pedestal is made of [MATERIAL], picked by its owners.
 */
class ShopBlock(properties: Properties) : BaseEntityBlock(properties) {
    override fun codec(): MapCodec<ShopBlock> = CODEC

    init {
        registerDefaultState(
            stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(FACING, Direction.NORTH).setValue(MATERIAL, ShopMaterial.DEFAULT),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HALF, FACING, MATERIAL)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? =
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) ShopBlockEntity(pos, state) else null

    /** The shop faces the player and needs the block above it free for its cloche. */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val above = context.clickedPos.above()
        val level = context.level
        if (!level.isInWorldBounds(above) || !level.getBlockState(above).canBeReplaced(context)) return null
        return defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level.isClientSide) return
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), UPDATE_ALL)
        if (placer is Player) (level.getBlockEntity(pos) as? ShopBlockEntity)?.placedBy = placer.uuid
    }

    /** A half whose other half is gone goes too. */
    override fun updateShape(
        state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos,
    ): BlockState {
        val half = state.getValue(HALF)
        val towardOther = if (half == DoubleBlockHalf.LOWER) Direction.UP else Direction.DOWN
        if (direction != towardOther) return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
        val intact = neighborState.`is`(this) && neighborState.getValue(HALF) != half
        return if (intact) state else Blocks.AIR.defaultBlockState()
    }

    /** In creative mode nothing drops: remove the pedestal quietly when the cloche is broken. */
    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide && player.isCreative && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            val lower = pos.below()
            if (level.getBlockState(lower).`is`(this)) {
                level.setBlock(lower, Blocks.AIR.defaultBlockState(), UPDATE_ALL or UPDATE_SUPPRESS_DROPS)
                level.levelEvent(player, 2001, lower, getId(state))
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level is ServerLevel && player is ServerPlayer) Shops.use(player, lowerPos(state, pos))
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (level is ServerLevel && !state.`is`(newState.block) && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            (level.getBlockEntity(pos) as? ShopBlockEntity)?.let { shop ->
                Shops.onRemoved(shop)
                Containers.dropContents(level, pos, shop.stock)
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) PEDESTAL else CLOCHE

    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    companion object {
        val CODEC: MapCodec<ShopBlock> = simpleCodec(::ShopBlock)
        val HALF: EnumProperty<DoubleBlockHalf> = BlockStateProperties.DOUBLE_BLOCK_HALF
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val MATERIAL: EnumProperty<ShopMaterial> = EnumProperty.create("material", ShopMaterial::class.java)

        // See the shop model. Both are symmetric around the vertical axis, so they don't turn with the shop.
        private val PEDESTAL: VoxelShape = Shapes.or(
            box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0),
            box(2.0, 3.0, 2.0, 14.0, 7.0, 14.0),
            box(4.0, 7.0, 4.0, 12.0, 14.0, 12.0),
            box(2.5, 14.0, 2.5, 13.5, 16.0, 13.5),
        )
        private val CLOCHE: VoxelShape = Shapes.or(box(2.0, 0.0, 2.0, 14.0, 9.0, 14.0), box(7.0, 9.0, 7.0, 9.0, 11.5, 9.0))

        /** The pedestal's position, where the block entity is, for either half at [pos]. */
        fun lowerPos(state: BlockState, pos: BlockPos): BlockPos = if (state.getValue(HALF) == DoubleBlockHalf.UPPER) pos.below() else pos
    }
}
