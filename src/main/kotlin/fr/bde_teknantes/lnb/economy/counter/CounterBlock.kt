package fr.bde_teknantes.lnb.economy.counter

import fr.bde_teknantes.lnb.economy.casino.CasinoLinks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A casino's counter: players buy chips with LNBits, cash them, or get change. Linked to its casino with the link
 * wand, like the machines.
 *
 * One item places a wall 3 blocks wide and 2 high facing the player, with the teller behind it. The wall is six blocks:
 * [PART] = (column + 1) + 3 * row, where column -1..1 grows toward [FACING]'s clockwise side (the left of a player
 * standing in front of the counter) and row 0 is the bottom. Each part draws its own slice of the wall, so the world
 * lights it where it stands. The bottom middle one is the [MASTER]: it also draws the teller, holds the casino link and
 * the block entity. Breaking any part breaks the whole counter; only the master drops the item.
 */
class CounterBlock(properties: Properties) : Block(properties), EntityBlock {
    companion object {
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val PART: IntegerProperty = IntegerProperty.create("part", 0, 5)
        val WOOD: EnumProperty<CounterWood> = EnumProperty.create("wood", CounterWood::class.java)
        const val MASTER = 1

        private fun column(part: Int) = part % 3 - 1
        private fun row(part: Int) = part / 3
        private fun part(column: Int, row: Int) = column + 1 + 3 * row

        /** Where the master of the counter part [state] at [pos] is. */
        fun masterPos(state: BlockState, pos: BlockPos): BlockPos {
            val part = state.getValue(PART)
            return pos.relative(state.getValue(FACING).clockWise, -column(part)).below(row(part))
        }

        private fun partPos(master: BlockPos, facing: Direction, part: Int): BlockPos =
            master.relative(facing.clockWise, column(part)).above(row(part))

        /** Every part's position, master included. */
        fun allParts(master: BlockPos, facing: Direction): List<Pair<Int, BlockPos>> = (0..5).map { it to partPos(master, facing, it) }

        // Shapes for a counter facing north (front at z = 0), in pixels; see the wall model. The middle column also holds
        // the teller standing behind the wall, so players can click him to open the counter.
        private val TELLER_BODY = doubleArrayOf(4.0, 0.0, 9.0, 12.0, 16.0, 16.0)
        private val TELLER_HEAD = doubleArrayOf(4.0, 0.0, 9.0, 12.0, 15.0, 16.0)
        private val NORTH_SHAPES: Map<Int, List<DoubleArray>> = (0..5).associateWith { part ->
            val wall = when {
                row(part) == 0 -> listOf(doubleArrayOf(0.0, 0.0, 0.0, 16.0, 15.0, 4.0), doubleArrayOf(0.0, 15.0, 0.0, 16.0, 16.0, 7.0))
                column(part) == 0 -> listOf(doubleArrayOf(0.0, 13.5, 0.0, 16.0, 16.0, 5.0)) // the beam above the window
                else -> listOf(doubleArrayOf(0.0, 0.0, 0.0, 16.0, 15.0, 4.0), doubleArrayOf(0.0, 15.0, 0.0, 16.0, 16.0, 5.0))
            }
            when {
                column(part) != 0 -> wall
                row(part) == 0 -> wall + listOf(TELLER_BODY)
                else -> wall + listOf(TELLER_HEAD)
            }
        }

        private val SHAPES: Map<Direction, Map<Int, VoxelShape>> = Direction.Plane.HORIZONTAL.associateWith { facing ->
            NORTH_SHAPES.mapValues { (_, boxes) -> boxes.map { box(rotate(it, facing)) }.reduce(Shapes::or) }
        }

        private fun box(b: DoubleArray): VoxelShape = Block.box(b[0], b[1], b[2], b[3], b[4], b[5])

        /** Turns a north-facing box (x0 y0 z0 x1 y1 z1, pixels) to [facing], around the block's center. */
        private fun rotate(b: DoubleArray, facing: Direction): DoubleArray {
            val (x0, y0, z0, x1) = b.toList()
            val (y1, z1) = b.toList().drop(4)
            return when (facing) {
                Direction.SOUTH -> doubleArrayOf(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0)
                Direction.EAST -> doubleArrayOf(16 - z1, y0, x0, 16 - z0, y1, x1)
                Direction.WEST -> doubleArrayOf(z0, y0, 16 - x1, z1, y1, 16 - x0)
                else -> b
            }
        }
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, MASTER).setValue(WOOD, CounterWood.DEFAULT))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, PART, WOOD)
    }

    /** The counter faces the player and needs its whole 3 x 2 wall free. */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val facing = context.horizontalDirection.opposite
        val level = context.level
        val free = allParts(context.clickedPos, facing).all { (_, p) ->
            level.isInWorldBounds(p) && level.worldBorder.isWithinBounds(p) && level.getBlockState(p).canBeReplaced(context)
        }
        return if (free) defaultBlockState().setValue(FACING, facing) else null
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (level.isClientSide) return
        for ((part, p) in allParts(pos, state.getValue(FACING))) {
            if (part != MASTER) level.setBlock(p, state.setValue(PART, part), UPDATE_ALL)
        }
    }

    /** A part whose neighbor in the wall is gone goes too, so the counter never stays half built. */
    override fun updateShape(
        state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos,
    ): BlockState {
        val facing = state.getValue(FACING)
        val part = state.getValue(PART)
        val (dc, dr) = when (direction) {
            facing.clockWise -> 1 to 0
            facing.counterClockWise -> -1 to 0
            Direction.UP -> 0 to 1
            Direction.DOWN -> 0 to -1
            else -> return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
        }
        val c = column(part) + dc
        val r = row(part) + dr
        if (c !in -1..1 || r !in 0..1) return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
        val intact = neighborState.`is`(this) && neighborState.getValue(PART) == part(c, r) && neighborState.getValue(FACING) == facing
        return if (intact) state else Blocks.AIR.defaultBlockState()
    }

    /** In creative mode nothing drops; the item drops from the master only (see its loot table). */
    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide && player.isCreative && state.getValue(PART) != MASTER) {
            val master = masterPos(state, pos)
            if (level.getBlockState(master).`is`(this)) {
                level.setBlock(master, Blocks.AIR.defaultBlockState(), UPDATE_ALL or UPDATE_SUPPRESS_DROPS)
                level.levelEvent(player, 2001, master, getId(state))
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level is ServerLevel && player is ServerPlayer) Counters.open(player, masterPos(state, pos))
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (level is ServerLevel && !state.`is`(newState.block) && state.getValue(PART) == MASTER) {
            CasinoLinks.get(level.server).remove(level.dimension(), pos)
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        SHAPES.getValue(state.getValue(FACING)).getValue(state.getValue(PART))

    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? =
        if (state.getValue(PART) == MASTER) CounterBlockEntity(pos, state) else null
}
