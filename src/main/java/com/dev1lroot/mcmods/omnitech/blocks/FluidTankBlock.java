package com.dev1lroot.mcmods.omnitech.blocks;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
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
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jetbrains.annotations.Nullable;

import static com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlock.propertyFor;

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
    public static final MapCodec<FluidTankBlock> CODEC = simpleCodec(FluidTankBlock::new);

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
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        // Off-hand bucket → fluid interaction
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof BucketItem) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                boolean interacted = FluidUtil.interactWithFluidHandler(
                        player, hand, level, pos, hit.getDirection());
                return interacted ? InteractionResult.SUCCESS : InteractionResult.FAIL;
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
