/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.plumbing;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.io.IFluidContainer;
import com.dev1lroot.mcmods.omnitech.items.FlaskItem;
import com.dev1lroot.mcmods.omnitech.items.PipetteItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Fluid tank — stores up to {@link FluidTankBlockEntity#CAPACITY} mb.
 *
 * <p>Right-click with a filled bucket to add fluid; right-click with an empty
 * bucket when the tank has fluid to drain into the bucket.
 * Right-click without a bucket opens the GUI (bucket slots + fluid gauge).
 *
 * <p>All six faces are valid attachment points for fluid pipes and pumps.
 */
public class FluidTankBlock extends BaseEntityBlock implements IFluidContainer
{

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST  = BooleanProperty.create("east");
    public static final BooleanProperty WEST  = BooleanProperty.create("west");
    public static final BooleanProperty UP    = BooleanProperty.create("up");
    public static final BooleanProperty DOWN  = BooleanProperty.create("down");

    public FluidTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST,  false).setValue(WEST,  false)
                .setValue(UP,    false).setValue(DOWN,  false));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidTankBlockEntity(pos, state);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F; // Убираем тени внутри блока
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level,
                                  ScheduledTickAccess scheduledTickAccess, BlockPos pos, Direction direction,
                                  BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        // Проверяем, является ли сосед таким же танком
        return state.setValue(propertyFor(direction), neighborState.is(this));
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

//    @Override
//    protected net.minecraft.world.phys.shapes.VoxelShape getVisualShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
//        // Это позволяет свету и рендеру понимать, что блок прозрачный,
//        // но при этом участвует в расчетах окклюзии
//        return net.minecraft.world.phys.shapes.Shapes.empty();
//    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.FLUID_TANK.get(),
                        FluidTankBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = this.defaultBlockState();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();

        for (Direction dir : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(dir));
            // Если сосед — это тоже FluidTankBlock, ставим true
            if (neighbor.is(this)) {
                state = state.setValue(propertyFor(dir), true);
            }
        }
        return state;
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    /**
     * A flask that holds something is emptied into the tank (falling back to topping it up from the
     * tank if the tank can't take it); an empty flask is filled from the tank. The generic
     * {@link FluidUtil#interactWithFluidHandler} always tries to fill first, which would top up a
     * half-full flask from a tank of the same fluid instead of unloading it.
     */
    private static boolean interactWithFlask(Player player, InteractionHand hand, Level level,
            BlockPos pos, Direction side) {
        ResourceHandler<FluidResource> tank = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        ResourceHandler<FluidResource> flask =
                ItemAccess.forPlayerInteraction(player, hand).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (tank == null || flask == null) return false;

        if (!FlaskItem.isEmpty(player.getItemInHand(hand))) {
            return moveWithSound(flask, tank, level, pos, player, false)
                    || moveWithSound(tank, flask, level, pos, player, true);
        }
        return moveWithSound(tank, flask, level, pos, player, true);
    }

    private static boolean moveWithSound(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to,
            Level level, BlockPos pos, Player player, boolean pickup) {
        var moved = ResourceHandlerUtil.moveFirst(from, to, fr -> true, Integer.MAX_VALUE, null);
        if (moved == null) return false;
        FluidUtil.triggerSoundAndGameEvent(moved.resource(), level, Vec3.atCenterOf(pos), player, pickup);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        // Bucket or flask in hand → fluid interaction
        for (InteractionHand hand : InteractionHand.values()) {
            var held = player.getItemInHand(hand).getItem();
            if (held instanceof BucketItem || held instanceof FlaskItem) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                boolean interacted = held instanceof FlaskItem
                        ? interactWithFlask(player, hand, level, pos, hit.getDirection())
                        : FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection());
                return interacted ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
        }
        // Empty pipette → draw its configured amount straight from the tank
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof PipetteItem && PipetteItem.isEmpty(held)) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof FluidTankBlockEntity tank) {
                    boolean filled = PipetteItem.tryFillFromTank(held, tank);
                    return filled ? InteractionResult.SUCCESS : InteractionResult.FAIL;
                }
            }
        }
        // No bucket → open GUI
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FluidTankBlockEntity tank) {
                ((ServerPlayer) player).openMenu(tank, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
