package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Kinetic Pipe — a straight, non-bending shaft that transmits Kinetic Force
 * along one axis. Spins visually while KF is flowing through it.
 *
 * <p>Placement: the pipe's axis aligns with the face clicked when placing.
 * Power flows only along the axis — two pipes on different axes do not connect
 * to each other through their sides.
 */
public class KineticPipeBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<KineticPipeBlock> CODEC = simpleCodec(KineticPipeBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS    = BlockStateProperties.AXIS;
    public static final BooleanProperty              POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty              WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // 4×16×4 shaft shapes matching the block model, one per axis
    private static final VoxelShape SHAPE_Y = Block.box( 6,  0,  6, 10, 16, 10);
    private static final VoxelShape SHAPE_X = Block.box( 0,  6,  6, 16, 10, 10);
    private static final VoxelShape SHAPE_Z = Block.box( 6,  6,  0, 10, 10, 16);

    public KineticPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS,    Direction.Axis.Y)
                .setValue(POWERED, false)
                .setValue(WATERLOGGED, false)
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    /** All rendering is done by the BlockEntityRenderer. */
    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticPipeBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, OmniTechBlockEntities.KF_PIPE.get(),
                KineticPipeBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, POWERED, WATERLOGGED);
    }

    /** Axis aligns with the face that was clicked when placing the block. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState()
                .setValue(AXIS, context.getClickedFace().getAxis())
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /**
     * Returns the thin-shaft VoxelShape for the pipe's current axis.
     * Fixes the hitbox so it matches the rendered 4×16×4 model instead of
     * defaulting to a full block.  Combined with {@code noOcclusion()} on the
     * block properties this also lets sky-light and block-light pass freely
     * through the space that the pipe does not occupy.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Z -> SHAPE_Z;
            default -> SHAPE_Y;
        };
    }
}
