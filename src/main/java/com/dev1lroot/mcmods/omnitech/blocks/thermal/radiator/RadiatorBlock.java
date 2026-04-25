package com.dev1lroot.mcmods.omnitech.blocks.thermal.radiator;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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
import org.jetbrains.annotations.Nullable;

/**
 * Radiator — passive heat/cold sink that dissipates thermal energy to ambient.
 *
 * <p>Implements {@link com.dev1lroot.mcmods.omnitech.io.IHeatReceiver} and
 * {@link com.dev1lroot.mcmods.omnitech.io.IColdReceiver} so adjacent Heat
 * Exchangers and Decompressors automatically push their excess thermal load into
 * connected radiator networks each tick.
 *
 * <p>Radiators also propagate thermal load to adjacent radiators (at a
 * diminishing rate per hop) so a network of them extends the effective
 * dissipation range, with each tile further from the source contributing less.
 */
public class RadiatorBlock extends BaseEntityBlock {

    public static final MapCodec<RadiatorBlock> CODEC = simpleCodec(RadiatorBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public RadiatorBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 4 : 0));
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadiatorBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.RADIATOR.get(),
                        RadiatorBlockEntity::serverTick);
    }
}
