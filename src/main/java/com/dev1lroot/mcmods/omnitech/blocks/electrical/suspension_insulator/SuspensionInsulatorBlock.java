/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.electrical.suspension_insulator;

import com.dev1lroot.mcmods.omnitech.util.ConductorMetals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Suspension Insulator — a string of porcelain discs that carries an overhead
 * power line. It either hangs under a block ({@code HANGING}) or stands on top
 * of one, like a lantern.
 *
 * <p>Right-click two insulators with any metal coil (tag
 * {@code omnitech:conductor_coils}) to string a span between them; the span
 * conducts like a wire, with a resistance that depends on the metal and the
 * length (see {@link ConductorMetals}). Shears take down every span on an
 * insulator and return the coils. The insulator itself conducts to wires and
 * machines on all of its faces, so lines are fed and tapped by ordinary
 * electric wires.
 */
public class SuspensionInsulatorBlock extends BaseEntityBlock {
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;

    private static final VoxelShape SHAPE_HANGING  = Block.box(4, 1, 4, 12, 16, 12);
    private static final VoxelShape SHAPE_STANDING = Block.box(4, 0, 4, 12, 15, 12);

    public SuspensionInsulatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HANGING, true));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuspensionInsulatorBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HANGING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HANGING) ? SHAPE_HANGING : SHAPE_STANDING;
    }

    /** Where the line clamps on, in world coordinates. */
    public static Vec3 attachPoint(BlockPos pos, BlockState state) {
        boolean hanging = !state.hasProperty(HANGING) || state.getValue(HANGING);
        return new Vec3(pos.getX() + 0.5, pos.getY() + (hanging ? 0.125 : 0.875), pos.getZ() + 0.5);
    }

    // ── Placement / support ──────────────────────────────────────────────────

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        if (clicked.getAxis() == Direction.Axis.Y) {
            BlockState state = defaultBlockState().setValue(HANGING, clicked == Direction.DOWN);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) return state;
        }
        for (Direction dir : context.getNearestLookingDirections()) {
            if (dir.getAxis() != Direction.Axis.Y) continue;
            BlockState state = defaultBlockState().setValue(HANGING, dir == Direction.UP);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) return state;
        }
        return null;
    }

    private static Direction supportDirection(BlockState state) {
        return state.getValue(HANGING) ? Direction.UP : Direction.DOWN;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction toSupport = supportDirection(state);
        return Block.canSupportCenter(level, pos.relative(toSupport), toSupport.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos,
            BlockState neighbourState, RandomSource random) {
        return supportDirection(state) == directionToNeighbour && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    // ── Stringing lines ──────────────────────────────────────────────────────

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        boolean coil = ConductorMetals.isConductorCoil(stack);
        boolean shears = stack.is(Items.SHEARS);
        if (!coil && !shears) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof SuspensionInsulatorBlockEntity insulator)) {
            return InteractionResult.PASS;
        }
        ServerPlayer sp = (ServerPlayer) player;
        if (shears) {
            insulator.cutAllSpans(sp);
        } else {
            LineStringing.click(sp, stack, insulator);
        }
        return InteractionResult.SUCCESS;
    }
}
