/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.labware;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.blocks.ComponentState;
import com.dev1lroot.mcmods.omnitech.blocks.ThermalState;
import com.dev1lroot.mcmods.omnitech.io.IFluidContainer;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * One segment of a Fractional Distiller multiblock.
 *
 * <p>Stack multiple instances vertically to form a taller structure.
 * The bottom block is the "master" that drives all processing.
 *
 * <ul>
 *   <li>Front face (FACING) — input fluid, bottom block only.</li>
 *   <li>Back face (FACING.opposite) — output fluid, each block outputs its own product.</li>
 *   <li>Bottom face — heat / cold input (IHeatReceiver / IColdReceiver).</li>
 * </ul>
 *
 * <h3>BlockState properties</h3>
 * <ul>
 *   <li>{@link #FACING} — horizontal direction of the input port.</li>
 *   <li>{@link #COMPONENT_STATE} — structural role within the column
 *       (SINGLE / BOTTOM / MIDDLE / TOP), updated automatically when neighbours change.</li>
 *   <li>{@link #THERMAL_STATE} — COOL when idle, HOT when actively processing.
 *       Enables the glow-overlay model variant and emits light level 7.</li>
 * </ul>
 */
public class FractionalDistillerBlock extends BaseEntityBlock implements IFluidContainer {

    public static final MapCodec<FractionalDistillerBlock> CODEC = simpleCodec(FractionalDistillerBlock::new);

    public static final EnumProperty<Direction>      FACING          = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ComponentState> COMPONENT_STATE = EnumProperty.create("component_state", ComponentState.class);
    public static final EnumProperty<ThermalState>   THERMAL_STATE   = EnumProperty.create("thermal_state",   ThermalState.class);

    public FractionalDistillerBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(THERMAL_STATE) == ThermalState.HOT ? 7 : 0));
        registerDefaultState(stateDefinition.any()
                .setValue(FACING,          Direction.NORTH)
                .setValue(COMPONENT_STATE, ComponentState.SINGLE)
                .setValue(THERMAL_STATE,   ThermalState.COOL));
    }

    // ── IFluidContainer ───────────────────────────────────────────────────────

    /** Front and back faces connect to fluid pipes. */
    @Override
    public boolean isConnectable(BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        return face == facing || face == facing.getOpposite();
    }

    // ── Block boilerplate ─────────────────────────────────────────────────────

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COMPONENT_STATE, THERMAL_STATE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level  = ctx.getLevel();
        return defaultBlockState()
                .setValue(FACING,          ctx.getHorizontalDirection().getOpposite())
                .setValue(COMPONENT_STATE, computeComponentState(level, pos))
                .setValue(THERMAL_STATE,   ThermalState.COOL);
    }

    // ── Structural updates ────────────────────────────────────────────────────

    /**
     * When any neighbour changes, recompute this segment's COMPONENT_STATE.
     * {@link #computeComponentState} only inspects the blocks above and below,
     * so horizontal neighbour updates are no-ops in practice.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block neighborBlock, @Nullable Orientation orientation, boolean moving) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, moving);
        if (!level.isClientSide()) {
            ComponentState cs = computeComponentState(level, pos);
            if (state.getValue(COMPONENT_STATE) != cs) {
                level.setBlock(pos, state.setValue(COMPONENT_STATE, cs), 3);
            }
        }
    }

    /**
     * Determines the structural role of this block based on whether adjacent
     * blocks above and below are also Fractional Distiller segments.
     */
    static ComponentState computeComponentState(Level level, BlockPos pos) {
        boolean hasBelow = level.getBlockState(pos.below()).getBlock() instanceof FractionalDistillerBlock;
        boolean hasAbove = level.getBlockState(pos.above()).getBlock() instanceof FractionalDistillerBlock;
        if (!hasBelow && !hasAbove) return ComponentState.SINGLE;
        if (!hasBelow)               return ComponentState.BOTTOM;
        if (!hasAbove)               return ComponentState.TOP;
        return ComponentState.MIDDLE;
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FractionalDistillerBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.FRACTIONAL_DISTILLER.get(),
                        FractionalDistillerBlockEntity::serverTick);
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FractionalDistillerBlockEntity fbe) {
                // Always open the GUI of the bottom (master) block
                FractionalDistillerBlockEntity master =
                        fbe.isBottomBlock() ? fbe : fbe.findBottomBlock();
                if (master != null) {
                    ((ServerPlayer) player).openMenu(master, master.getBlockPos());
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}
