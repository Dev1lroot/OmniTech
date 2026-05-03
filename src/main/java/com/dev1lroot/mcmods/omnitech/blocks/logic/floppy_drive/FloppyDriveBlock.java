package com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FloppyDriveBlock extends BaseEntityBlock {

    public static final MapCodec<FloppyDriveBlock> CODEC = simpleCodec(FloppyDriveBlock::new);

    public FloppyDriveBlock(Properties props) { super(props); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloppyDriveBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FloppyDriveBlockEntity fd) {
                ((ServerPlayer) player).openMenu(fd, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeInt(fd.getDriveId());
                });
            }
        }
        return InteractionResult.SUCCESS;
    }
}
