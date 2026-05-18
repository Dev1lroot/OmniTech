package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.radiation.NuclearExplosion;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ReactorBlock extends BaseEntityBlock {

    public static final MapCodec<ReactorBlock> CODEC = simpleCodec(ReactorBlock::new);

    public ReactorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, OmniTechBlockEntities.REACTOR.get(),
                        ReactorBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ReactorBlockEntity rbe && rbe.isFormed()) {
            openGui(rbe, (ServerPlayer) player);
            return InteractionResult.SUCCESS;
        }

        Optional<ReactorStructure> structOpt = ReactorStructure.detect(level, pos);
        if (structOpt.isPresent()) {
            ReactorStructure s = structOpt.get();
            if (level.getBlockEntity(s.origin) instanceof ReactorBlockEntity master) {
                if (!master.isFormed()) master.form(s);
                openGui(master, (ServerPlayer) player);
            }
        } else {
            player.sendSystemMessage(Component.translatable("block.omnitech.reactor_block.invalid"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof ReactorBlockEntity rbe
                && rbe.isFormed()
                && rbe.getCoreTemperature() >= 300
                && !rbe.hasExploded()) {
            rbe.markExploded();
            NuclearExplosion.trigger((ServerLevel) level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
                                                BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        // Notify any formed structure whose master relied on this block (non-master case).
        // The master itself handles cleanup via ReactorBlockEntity.setRemoved().
        ReactorStructure.invalidateNearbyMaster(level, pos);
    }

    static void openGui(ReactorBlockEntity master, ServerPlayer player) {
        ReactorStructure s = master.getStructure();
        if (s == null) return;
        player.openMenu(master, buf -> {
            buf.writeBlockPos(master.getBlockPos());
            buf.writeVarInt(s.width);
            buf.writeVarInt(s.depth);
            buf.writeVarInt(s.cells.size());
            for (BlockPos cell : s.cells) {
                buf.writeVarInt(cell.getX() - s.origin.getX());
                buf.writeVarInt(cell.getZ() - s.origin.getZ());
            }
        });
    }
}
