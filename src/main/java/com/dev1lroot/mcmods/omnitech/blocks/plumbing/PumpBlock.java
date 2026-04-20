package com.dev1lroot.mcmods.omnitech.blocks.plumbing;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IFluidContainer;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Fluid pump — consumes Kinetic Force to move up to
 * {@link PumpBlockEntity#TRANSFER_RATE} mb/tick from its input face
 * (opposite of {@code FACING}) to its output face ({@code FACING}).
 *
 * <p>The pump separates fluid pipe networks: pipes on the input side and
 * output side do NOT equalize through the pump, only through it.
 */
public class PumpBlock extends BaseEntityBlock implements IFluidContainer {
    public static final MapCodec<PumpBlock> CODEC = simpleCodec(PumpBlock::new);

    /** Direction the pump outputs toward (= "front" face). */
    public static final EnumProperty<Direction> FACING  = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty         POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public PumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING,  Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    public boolean isConnectable(BlockState state, Direction face)
    {
        Direction facing = state.getValue(FACING);

        // только перед и зад
        return face == facing || face == facing.getOpposite();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    /** Output faces the direction the player is looking when placing. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PumpBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, OmniTechBlockEntities.PUMP.get(),
                PumpBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }
}
