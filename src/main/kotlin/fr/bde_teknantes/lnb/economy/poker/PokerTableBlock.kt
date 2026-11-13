package fr.bde_teknantes.lnb.economy.poker

import fr.bde_teknantes.lnb.economy.ModContent
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
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
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
 * A poker table of the house: casino hold'em for up to three players at once, against the dealer (see [HoldemRules]).
 * Linked to a casino with the link wand like a counter; a right click opens the table. Breaking it mid-round gives
 * every seat its stakes back.
 *
 * The table looks like MineVegas Casino's poker table: its models and textures are MineVegas's (see
 * `LICENSES/MineVegas-Casino.txt`), and so are its size and layout. It is [COLUMNS] blocks wide and [ROWS] deep, one
 * block per [PART] = column + 4 * row; each part draws its own slice of the table. The [ANCHOR] part holds the round,
 * the casino link and the block entity. Breaking any part breaks the whole table; only the anchor drops the item.
 */
class PokerTableBlock(properties: Properties) : Block(properties), EntityBlock {
    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PART, ANCHOR))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, PART)
    }

    /** The table faces the player and needs its whole 4 x 3 floor free. */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val facing = context.horizontalDirection.opposite
        val level = context.level
        val free = (0 until PARTS).all { part ->
            val p = partPos(context.clickedPos, facing, part)
            level.isInWorldBounds(p) && level.worldBorder.isWithinBounds(p) && level.getBlockState(p).canBeReplaced(context)
        }
        return if (free) defaultBlockState().setValue(FACING, facing) else null
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (level.isClientSide) return
        val facing = state.getValue(FACING)
        for (part in 0 until PARTS) {
            if (part != ANCHOR) level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), UPDATE_ALL)
        }
    }

    /** A part whose neighbor in the table is gone goes too, so the table never stays half built. */
    override fun updateShape(
        state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos,
    ): BlockState {
        val facing = state.getValue(FACING)
        val part = state.getValue(PART)
        // Which neighbor of the table lies that way: one column or one row over.
        val step = STEPS.firstOrNull { (dc, dr) -> rotatedOffset(facing, dc, dr) == BlockPos(direction.stepX, 0, direction.stepZ) }
            ?: return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
        val column = part % COLUMNS + step.first
        val row = part / COLUMNS + step.second
        if (direction.axis.isVertical || column !in 0 until COLUMNS || row !in 0 until ROWS) {
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
        }
        val intact = neighborState.`is`(this) && neighborState.getValue(PART) == column + COLUMNS * row && neighborState.getValue(FACING) == facing
        return if (intact) state else Blocks.AIR.defaultBlockState()
    }

    /** In creative mode nothing drops; the item drops from the anchor only (see its loot table). */
    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide && player.isCreative && state.getValue(PART) != ANCHOR) {
            val anchor = anchorOf(pos, state)
            if (level.getBlockState(anchor).`is`(this)) {
                level.setBlock(anchor, Blocks.AIR.defaultBlockState(), UPDATE_ALL or UPDATE_SUPPRESS_DROPS)
                level.levelEvent(player, 2001, anchor, getId(state))
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level is ServerLevel && player is ServerPlayer) PokerTables.open(player, anchorOf(pos, state))
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (level is ServerLevel && !state.`is`(newState.block) && state.getValue(PART) == ANCHOR) {
            (level.getBlockEntity(pos) as? PokerTableBlockEntity)?.abort(level)
            CasinoLinks.get(level.server).remove(level.dimension(), pos)
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(PART)]

    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState {
        if (mirror == Mirror.NONE) return state
        val part = state.getValue(PART)
        return rotate(state, mirror.getRotation(state.getValue(FACING))).setValue(PART, part / COLUMNS * COLUMNS + COLUMNS - 1 - part % COLUMNS)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? =
        if (state.getValue(PART) == ANCHOR) PokerTableBlockEntity(pos, state) else null

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide || type != ModContent.POKER_ENTITY.get()) return null
        return BlockEntityTicker { tickLevel, _, _, entity -> (entity as PokerTableBlockEntity).serverTick(tickLevel as ServerLevel) }
    }

    companion object {
        /** The game's name for the config switch, the link screen and the logs. */
        const val GAME = "holdem"

        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        const val COLUMNS = 4
        const val ROWS = 3
        const val PARTS = COLUMNS * ROWS

        /** Second column, middle row: the block the item is placed on. */
        const val ANCHOR = 5
        val PART: IntegerProperty = IntegerProperty.create("part", 0, PARTS - 1)

        private val STEPS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        /** [du] columns and [dv] rows from the anchor, turned to [facing] (a table facing south runs along +x and +z). */
        fun rotatedOffset(facing: Direction, du: Int, dv: Int): BlockPos {
            var x = du
            var z = dv
            repeat(facing.get2DDataValue()) {
                val nx = -z
                z = x
                x = nx
            }
            return BlockPos(x, 0, z)
        }

        fun partPos(anchor: BlockPos, facing: Direction, part: Int): BlockPos =
            anchor.offset(rotatedOffset(facing, part % COLUMNS - ANCHOR % COLUMNS, part / COLUMNS - ANCHOR / COLUMNS))

        /** Where the anchor of the table part [state] at [pos] is. */
        fun anchorOf(pos: BlockPos, state: BlockState): BlockPos =
            pos.subtract(partPos(BlockPos.ZERO, state.getValue(FACING), state.getValue(PART)))

        /**
         * The table's outline for a table facing south, in pixels over its whole 64 x 40 footprint (x0 y0 z0 x1 y1 z1):
         * a base, then the top with its rounded player side. Each part keeps the slice that falls in its block.
         */
        private val BOXES = arrayOf(
            intArrayOf(6, 0, 1, 58, 10, 2), intArrayOf(5, 0, 2, 59, 10, 28), intArrayOf(6, 0, 28, 58, 10, 31),
            intArrayOf(7, 0, 31, 57, 10, 33), intArrayOf(9, 0, 33, 55, 10, 34), intArrayOf(12, 0, 34, 52, 10, 35),
            intArrayOf(2, 10, 0, 62, 15, 1), intArrayOf(1, 10, 1, 63, 15, 2), intArrayOf(0, 10, 2, 64, 15, 28),
            intArrayOf(1, 10, 28, 63, 15, 31), intArrayOf(2, 10, 31, 62, 15, 33), intArrayOf(3, 10, 33, 61, 15, 34),
            intArrayOf(4, 10, 34, 60, 15, 35), intArrayOf(5, 10, 35, 59, 15, 36), intArrayOf(6, 10, 36, 58, 15, 37),
            intArrayOf(7, 10, 37, 57, 15, 38), intArrayOf(9, 10, 38, 55, 15, 39), intArrayOf(12, 10, 39, 52, 15, 40),
        )

        private val SHAPES: Array<Array<VoxelShape>> = Array(4) { turns -> Array(PARTS) { part -> shapeOf(part, turns) } }

        private fun shapeOf(part: Int, turns: Int): VoxelShape {
            val column = part % COLUMNS
            val row = part / COLUMNS
            var shape = Shapes.empty()
            for (b in BOXES) {
                var x0 = maxOf(b[0], column * 16) - column * 16
                var x1 = minOf(b[3], column * 16 + 16) - column * 16
                var z0 = maxOf(b[2], row * 16) - row * 16
                var z1 = minOf(b[5], row * 16 + 16) - row * 16
                if (x0 >= x1 || z0 >= z1) continue
                repeat(turns) {
                    val nx0 = 16 - z1
                    val nx1 = 16 - z0
                    z0 = x0
                    z1 = x1
                    x0 = nx0
                    x1 = nx1
                }
                shape = Shapes.or(shape, box(x0.toDouble(), b[1].toDouble(), z0.toDouble(), x1.toDouble(), b[4].toDouble(), z1.toDouble()))
            }
            // A part the outline misses (there is none today) must still be something to click.
            return if (shape.isEmpty) box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0) else shape
        }
    }
}
