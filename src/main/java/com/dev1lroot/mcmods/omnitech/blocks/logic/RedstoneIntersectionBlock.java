package com.dev1lroot.mcmods.omnitech.blocks.logic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class RedstoneIntersectionBlock extends Block {

    public static final MapCodec<RedstoneIntersectionBlock> CODEC = simpleCodec(RedstoneIntersectionBlock::new);

    private static final VoxelShape BASE      = Block.box( 0,  0,  0, 16, 2, 16);
    private static final VoxelShape JUMP      = Block.box( 4,  2,  4, 12, 8, 12);

    /** Visual: N-S channel is carrying signal. */
    public static final BooleanProperty CHANNEL_A  = BooleanProperty.create("a");
    /** Visual: E-W channel is carrying signal. */
    public static final BooleanProperty CHANNEL_B  = BooleanProperty.create("b");

    /** Stored output level for the N-S channel (0–15). Both N and S faces emit this. */
    public static final IntegerProperty NS_LEVEL = IntegerProperty.create("ns_level", 0, 15);
    /** Stored output level for the E-W channel (0–15). Both E and W faces emit this. */
    public static final IntegerProperty EW_LEVEL = IntegerProperty.create("ew_level", 0, 15);

    public RedstoneIntersectionBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CHANNEL_A, false)
                .setValue(CHANNEL_B, false)
                .setValue(NS_LEVEL, 0)
                .setValue(EW_LEVEL, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }


    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        VoxelShape shape = BASE;
        shape = Shapes.or(shape, JUMP);
        return shape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHANNEL_A, CHANNEL_B, NS_LEVEL, EW_LEVEL);
    }

    // ── Redstone ──────────────────────────────────────────────────────────────

    @Override
    protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return switch (dir) {
            case NORTH, SOUTH -> state.getValue(NS_LEVEL);
            case EAST,  WEST  -> state.getValue(EW_LEVEL);
            default           -> 0;
        };
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        if (!level.isClientSide()) updateChannels(state, level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()) updateChannels(state, level, pos);
    }

    private void updateChannels(BlockState state, Level level, BlockPos pos) {
        // N-S channel: pass-through from whichever end is stronger
        int northIn = level.getSignal(pos.north(), Direction.SOUTH);
        int southIn = level.getSignal(pos.south(), Direction.NORTH);
        int nsLevel = Math.max(0, Math.max(northIn, southIn) - 1);

        // E-W channel
        int eastIn  = level.getSignal(pos.east(),  Direction.WEST);
        int westIn  = level.getSignal(pos.west(),  Direction.EAST);
        int ewLevel = Math.max(0, Math.max(eastIn, westIn) - 1);

        boolean a = nsLevel > 0;
        boolean b = ewLevel > 0;

        BlockState newState = state
                .setValue(NS_LEVEL, nsLevel)
                .setValue(EW_LEVEL, ewLevel)
                .setValue(CHANNEL_A, a)
                .setValue(CHANNEL_B, b);

        if (!newState.equals(state)) {
            level.setBlock(pos, newState, 2);
            level.updateNeighborsAt(pos, this);
        }
    }
}
