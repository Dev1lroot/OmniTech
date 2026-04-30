package com.dev1lroot.mcmods.omnitech.blocks.analog;

import com.dev1lroot.mcmods.omnitech.io.IAnalogInput;
import com.dev1lroot.mcmods.omnitech.io.IAnalogOutput;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Analog Cable — omnidirectional conductor for the analog signal network.
 *
 * <p>Connects to other cables and to block entities implementing
 * {@link IAnalogOutput} or {@link IAnalogInput}.
 * Carries a float signal (0.0–15.0) pushed by {@link com.dev1lroot.mcmods.omnitech.util.AnalogNetworkUtil}.
 * Has no block entity; it is a pure routing element.
 */
public class AnalogCableBlock extends Block {
    public static final MapCodec<AnalogCableBlock> CODEC = simpleCodec(AnalogCableBlock::new);

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST  = BooleanProperty.create("east");
    public static final BooleanProperty WEST  = BooleanProperty.create("west");
    public static final BooleanProperty UP    = BooleanProperty.create("up");
    public static final BooleanProperty DOWN  = BooleanProperty.create("down");

    private static final VoxelShape CORE      = Block.box( 5,  5,  5, 11, 11, 11);
    private static final VoxelShape ARM_NORTH = Block.box( 5,  5,  0, 11, 11,  5);
    private static final VoxelShape ARM_SOUTH = Block.box( 5,  5, 11, 11, 11, 16);
    private static final VoxelShape ARM_EAST  = Block.box(11,  5,  5, 16, 11, 11);
    private static final VoxelShape ARM_WEST  = Block.box( 0,  5,  5,  5, 11, 11);
    private static final VoxelShape ARM_UP    = Block.box( 5, 11,  5, 11, 16, 11);
    private static final VoxelShape ARM_DOWN  = Block.box( 5,  0,  5, 11,  5, 11);

    public AnalogCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST,  false).setValue(WEST,  false)
                .setValue(UP,    false).setValue(DOWN,  false));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return calculateState(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess scheduledTickAccess, BlockPos pos,
            Direction direction, BlockPos neighborPos, BlockState neighborState,
            RandomSource random) {
        return state.setValue(propertyFor(direction), canConnectTo(level, neighborPos));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) shape = Shapes.or(shape, ARM_NORTH);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, ARM_SOUTH);
        if (state.getValue(EAST))  shape = Shapes.or(shape, ARM_EAST);
        if (state.getValue(WEST))  shape = Shapes.or(shape, ARM_WEST);
        if (state.getValue(UP))    shape = Shapes.or(shape, ARM_UP);
        if (state.getValue(DOWN))  shape = Shapes.or(shape, ARM_DOWN);
        return shape;
    }

    private static boolean canConnectTo(LevelReader level, BlockPos neighborPos) {
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.getBlock() instanceof AnalogCableBlock) return true;
        BlockEntity be = level.getBlockEntity(neighborPos);
        return be instanceof IAnalogOutput || be instanceof IAnalogInput;
    }

    private static BlockState calculateState(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(propertyFor(dir), canConnectTo(level, pos.relative(dir)));
        }
        return state;
    }

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
