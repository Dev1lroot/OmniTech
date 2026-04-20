package com.dev1lroot.mcmods.omnitech.blocks.labware.chemical_reactor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IFluidContainer;
import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
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
 * Chemical Reactor block.
 *
 * <ul>
 *   <li>Front  ({@code FACING}):                 input fluid A.</li>
 *   <li>Left   ({@code FACING.getCounterClockWise()}): input fluid B (or A).</li>
 *   <li>Right  ({@code FACING.getClockWise()}):       input fluid B (or A).</li>
 *   <li>Back   ({@code FACING.getOpposite()}):         output fluid.</li>
 * </ul>
 * Heat is supplied by an adjacent {@link IHeatReceiver} provider (e.g. Heater).
 * {@code LIT} is true while the machine is actively processing.
 */
public class ChemicalReactorBlock extends BaseEntityBlock implements IFluidContainer {

    public static final MapCodec<ChemicalReactorBlock> CODEC = simpleCodec(ChemicalReactorBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ChemicalReactorBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 7 : 0));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    /** Front, left, right and back all carry fluid — all four horizontal faces are connectable. */
    @Override
    public boolean isConnectable(BlockState state, Direction face) {
        if (face == Direction.UP || face == Direction.DOWN) return false;
        return true;
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
        return new ChemicalReactorBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.CHEMICAL_REACTOR.get(),
                        ChemicalReactorBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ChemicalReactorBlockEntity reactor) {
                ((ServerPlayer) player).openMenu(reactor, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
