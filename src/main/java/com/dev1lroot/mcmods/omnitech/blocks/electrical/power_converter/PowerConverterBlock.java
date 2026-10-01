/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.power_converter;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.CurrentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import org.jetbrains.annotations.Nullable;

/**
 * AC/DC converter — a {@link Mode#RECTIFIER} (AC → DC) or an
 * {@link Mode#INVERTER} (DC → AC).
 *
 * <p>Wired like the power transformer: the back face is the input, the front
 * ({@code FACING}) the output; the other four faces are insulated. The output
 * keeps the input voltage — use a transformer to change it.
 */
public class PowerConverterBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public enum Mode {
        /** Diode bridge: AC in, DC out. */
        RECTIFIER(CurrentType.AC, CurrentType.DC, 0.01f, 0.02f),
        /** Switching bridge: DC in, AC out. */
        INVERTER(CurrentType.DC, CurrentType.AC, 0.02f, 0.03f);

        public final CurrentType input;
        public final CurrentType output;
        /** Constant switching/conduction loss as a fraction of throughput. */
        public final float baseLoss;
        /** Additional loss at the full rating (scales with load). */
        public final float loadLoss;

        Mode(CurrentType input, CurrentType output, float baseLoss, float loadLoss) {
            this.input = input;
            this.output = output;
            this.baseLoss = baseLoss;
            this.loadLoss = loadLoss;
        }
    }

    private final Mode mode;

    public PowerConverterBlock(Properties properties, Mode mode) {
        super(properties);
        this.mode = mode;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public Mode mode() { return mode; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerConverterBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.POWER_CONVERTER.get(),
                        PowerConverterBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    /** Output faces away from the player, so the input faces the player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }
}
