package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Electrolysis Machine block.
 *
 * <p>Orientation: {@code FACING} is the fluid-input face (front).
 * <ul>
 *   <li>Front ({@code FACING}): pulls input fluid.</li>
 *   <li>Left ({@code FACING.getCounterClockWise()}): pushes anode output fluid.</li>
 *   <li>Right ({@code FACING.getClockWise()}): pushes cathode output fluid.</li>
 *   <li>Back ({@code FACING.getOpposite()}): pushes solution output fluid.</li>
 * </ul>
 * EU is received from any connected electric wire (via {@link IElectricReceiver}).
 * {@code LIT} is true while the machine is actively processing.
 */
public class ElectrolysisMachineBlock extends BaseEntityBlock implements IFluidContainer {

    public static final MapCodec<ElectrolysisMachineBlock> CODEC = simpleCodec(ElectrolysisMachineBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ElectrolysisMachineBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 7 : 0));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    public boolean isConnectable(BlockState state, Direction face) {
        if (face == Direction.UP || face == Direction.DOWN) return false;
        Direction facing = state.getValue(FACING);
        return face == facing
                || face == facing.getOpposite()
                || face == facing.getCounterClockWise()
                || face == facing.getClockWise();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectrolysisMachineBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.ELECTROLYSIS_MACHINE.get(),
                        ElectrolysisMachineBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ElectrolysisMachineBlockEntity machine) {
                ((ServerPlayer) player).openMenu(machine, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
