/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.keyboard;

import com.dev1lroot.mcmods.omnitech.blocks.logic.LogicCableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import com.dev1lroot.mcmods.omnitech.network.KeyboardModePacket;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class KeyboardBlock extends Block {

    private static final Map<UUID, BlockPos> ACTIVE_SESSIONS = new HashMap<>();

    public static final MapCodec<KeyboardBlock> CODEC = simpleCodec(KeyboardBlock::new);

    public KeyboardBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        UUID uuid = sp.getUUID();

        if (ACTIVE_SESSIONS.containsKey(uuid)) {
            ACTIVE_SESSIONS.remove(uuid);
            PacketDistributor.sendToPlayer(sp, new KeyboardModePacket(false, BlockPos.ZERO));
        } else {
            BlockPos machinePos = findLogicMachine(level, pos);
            if (machinePos == null) return InteractionResult.FAIL;
            ACTIVE_SESSIONS.put(uuid, machinePos);
            PacketDistributor.sendToPlayer(sp, new KeyboardModePacket(true, machinePos));
        }
        return InteractionResult.SUCCESS;
    }

    private static BlockPos findLogicMachine(Level level, BlockPos origin) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos nb = cur.relative(dir);
                if (!visited.add(nb)) continue;
                BlockState st = level.getBlockState(nb);
                if (st.getBlock() instanceof LogicCableBlock) {
                    if (visited.size() <= 4096) queue.add(nb);
                } else {
                    BlockEntity be = level.getBlockEntity(nb);
                    if (be instanceof LogicMachineBlockEntity) return nb.immutable();
                }
            }
        }
        return null;
    }

    public static void clearSession(UUID playerUuid) {
        ACTIVE_SESSIONS.remove(playerUuid);
    }

    public static void clearAllSessions() {
        ACTIVE_SESSIONS.clear();
    }
}
