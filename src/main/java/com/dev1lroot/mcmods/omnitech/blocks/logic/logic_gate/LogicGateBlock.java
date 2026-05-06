package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_gate;

import com.dev1lroot.mcmods.omnitech.items.LogicGateTemplateItem;
import com.dev1lroot.mcmods.omnitech.util.LogicGate;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class LogicGateBlock extends BaseEntityBlock {

    public static final MapCodec<LogicGateBlock> CODEC = simpleCodec(LogicGateBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public LogicGateBlock(Properties properties) {
        super(properties.noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogicGateBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    // ── Redstone ──────────────────────────────────────────────────────────────

    @Override
    protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        if (!state.getValue(POWERED)) return 0;
        // Emit only from the back face (opposite of where the block faces)
        return dir == state.getValue(FACING).getOpposite() ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighborBlock, @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        if (!level.isClientSide()) updateOutput(state, level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()) updateOutput(state, level, pos);
    }

    private void updateOutput(BlockState state, Level level, BlockPos pos) {
        boolean newPowered = false;

        if (level.getBlockEntity(pos) instanceof LogicGateBlockEntity be) {
            ItemStack template = be.getTemplate();
            if (!template.isEmpty() && template.getItem() instanceof LogicGateTemplateItem ti) {
                LogicGate gate = LogicGate.byId(ti.getGateId());
                if (gate != null) {
                    Direction facing = state.getValue(FACING);
                    Direction inputA = facing.getCounterClockWise();
                    Direction inputB = facing.getClockWise();
                    boolean a = level.getSignal(pos.relative(inputA), inputA) > 0;
                    boolean b = level.getSignal(pos.relative(inputB), inputB) > 0;
                    newPowered = gate.evaluate(a, b);
                }
            }
        }

        if (state.getValue(POWERED) != newPowered) {
            level.setBlock(pos, state.setValue(POWERED, newPowered), 2);
            level.updateNeighborsAt(pos, this);
        }
    }

    // ── Template interaction ──────────────────────────────────────────────────

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hitResult) {
        if (player.isCrouching()) {
            return removeTemplate(state, level, pos, player);
        }
        if (stack.getItem() instanceof LogicGateTemplateItem) {
            if (!level.isClientSide()) {
                if (level.getBlockEntity(pos) instanceof LogicGateBlockEntity be) {
                    ItemStack old = be.getTemplate();
                    be.setTemplate(stack.copyWithCount(1));
                    if (!old.isEmpty()) player.addItem(old);
                    stack.shrink(1);
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    updateOutput(state, level, pos);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (player.isCrouching()) {
            return removeTemplate(state, level, pos, player);
        }
        return InteractionResult.PASS;
    }

    private InteractionResult removeTemplate(BlockState state, Level level,
                                             BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof LogicGateBlockEntity be) {
                ItemStack template = be.getTemplate();
                if (!template.isEmpty()) {
                    be.setTemplate(ItemStack.EMPTY);
                    player.addItem(template);
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    updateOutput(state, level, pos);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Drop template when block is destroyed by player
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (!level.isClientSide() && blockEntity instanceof LogicGateBlockEntity be) {
            ItemStack template = be.getTemplate();
            if (!template.isEmpty()) {
                Block.popResource(level, pos, template);
            }
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }
}
