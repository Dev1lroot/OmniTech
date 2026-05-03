package com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Direction;

public class GPIOPortBlock extends BaseEntityBlock {

    public static final MapCodec<GPIOPortBlock> CODEC = simpleCodec(GPIOPortBlock::new);

    public GPIOPortBlock(Properties props) { super(props); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GPIOPortBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.GPIO_PORT.get(),
                        GPIOPortBlockEntity::serverTick);
    }

    // Emit redstone signal
    @Override
    public boolean isSignalSource(BlockState state) { return true; }

    @Override
    public int getSignal(BlockState state, BlockGetter level,
            BlockPos pos, Direction dir) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof GPIOPortBlockEntity g ? g.getOutputSignal() : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level,
            BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof GPIOPortBlockEntity g) {
                ((ServerPlayer) player).openMenu(g, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeInt(g.getPortId());
                    buf.writeInt(g.getInputSignal());
                    buf.writeInt(g.getOutputSignal());
                });
            }
        }
        return InteractionResult.SUCCESS;
    }
}
