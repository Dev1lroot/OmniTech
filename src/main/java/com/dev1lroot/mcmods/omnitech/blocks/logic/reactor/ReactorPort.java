package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

public class ReactorPort extends Block {

    public ReactorPort(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        openFromStructure(level, pos, (ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level,
                                                BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ReactorStructure.invalidateNearbyMaster(level, pos);
    }

    static void openFromStructure(Level level, BlockPos pos, ServerPlayer player) {
        Optional<ReactorStructure> structOpt = ReactorStructure.detect(level, pos);
        if (structOpt.isPresent()) {
            ReactorStructure s = structOpt.get();
            if (level.getBlockEntity(s.origin) instanceof ReactorBlockEntity master) {
                if (!master.isFormed()) master.form(s);
                ReactorBlock.openGui(master, player);
            }
        } else {
            player.sendSystemMessage(Component.translatable("block.omnitech.reactor_block.invalid"));
        }
    }
}
