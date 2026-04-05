package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 6-directional fluid pipe. Connects to neighboring pipes, fluid tanks, and
 * the front/back faces of pumps. Connection state is recalculated automatically
 * via {@link #updateShape} whenever a neighbor is placed or removed.
 *
 * <p>Each pipe stores up to {@link FluidPipeBlockEntity#CAPACITY} mb of fluid.
 * Connected pipes within the same network equalize their fluid levels every tick.
 * Pumps act as network separators — equalization does not cross a pump.
 */
public class FluidPipeBlock extends BaseEntityBlock implements IFluidContainer, SimpleWaterloggedBlock
{
    public static final MapCodec<FluidPipeBlock> CODEC = simpleCodec(FluidPipeBlock::new);

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST  = BooleanProperty.create("east");
    public static final BooleanProperty WEST  = BooleanProperty.create("west");
    public static final BooleanProperty UP    = BooleanProperty.create("up");
    public static final BooleanProperty DOWN  = BooleanProperty.create("down");

    // VoxelShapes: 4×4 pipe bore (6-10 in the two non-axis dimensions)
    private static final VoxelShape CORE      = Block.box( 4,  4,  4, 12, 12, 12);
    private static final VoxelShape ARM_NORTH = Block.box( 4,  4,  0, 12, 12,  4);
    private static final VoxelShape ARM_SOUTH = Block.box( 4,  4, 12, 12, 12, 16);
    private static final VoxelShape ARM_EAST  = Block.box(10,  4,  4, 16, 12, 12);
    private static final VoxelShape ARM_WEST  = Block.box( 0,  4,  4,  4, 12, 12);
    private static final VoxelShape ARM_UP    = Block.box( 4, 10,  4, 12, 16, 12);
    private static final VoxelShape ARM_DOWN  = Block.box( 4,  0,  4, 12,  4, 12);

    public FluidPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST,  false).setValue(WEST,  false)
                .setValue(UP,    false).setValue(DOWN,  false)
                .setValue(WATERLOGGED,  false)
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext ctx) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, ARM_NORTH);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, ARM_SOUTH);
        if (state.getValue(EAST))  shape = Shapes.or(shape, ARM_EAST);
        if (state.getValue(WEST))  shape = Shapes.or(shape, ARM_WEST);
        if (state.getValue(UP))    shape = Shapes.or(shape, ARM_UP);
        if (state.getValue(DOWN))  shape = Shapes.or(shape, ARM_DOWN);
        return shape;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(pos, state);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, OmniTechBlockEntities.FLUID_PIPE.get(),
                FluidPipeBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return calculateState(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    /**
     * Recalculates the connection property for the direction that just changed.
     * Called by the engine whenever a neighbor block is placed or removed.
     */
    @Override
    public BlockState updateShape(BlockState state, LevelReader level,
                                  ScheduledTickAccess scheduledTickAccess, BlockPos pos, Direction direction,
                                  BlockPos neighborPos, BlockState neighborState, RandomSource random) {

        // ЭТО КРИТИЧЕСКИ ВАЖНО:
        // Если блок помечен как WATERLOGGED, нужно запланировать тик жидкости
        if (state.getValue(WATERLOGGED)) {
            scheduledTickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        // Ваша существующая логика соединений
        return state.setValue(propertyFor(direction), canConnectTo(neighborState, direction));
    }

    // ── Connection logic ──────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this pipe should connect to {@code neighborState}
     * when the neighbor is in direction {@code fromPipe} relative to this pipe.
     */
    static boolean canConnectTo(BlockState neighborState, Direction fromPipe)
    {
        Block block = neighborState.getBlock();

        if (block instanceof IFluidContainer container)
        {
            // сторона соседа, которая смотрит на трубу
            Direction neighborFace = fromPipe.getOpposite();

            return container.isConnectable(neighborState, neighborFace);
        }

        return false;
    }

    /** Calculates all 6 connection properties from scratch using the given level. */
    private static BlockState calculateState(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(propertyFor(dir),
                    canConnectTo(level.getBlockState(pos.relative(dir)), dir));
        }
        // Проверяем наличие воды при установке
        FluidState fluidState = level.getFluidState(pos);
        state = state.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);

        return state;
    }

    /** Maps a Direction to the corresponding connection BooleanProperty. */
    public static BooleanProperty propertyFor(Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST  -> EAST;
            case WEST  -> WEST;
            case UP    -> UP;
            case DOWN  -> DOWN;
        };
    }
}
