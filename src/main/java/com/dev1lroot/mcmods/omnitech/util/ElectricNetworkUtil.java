/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_wire.ElectricWireBlock;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BFS propagation utility for the Electric network.
 *
 * <p>Electricity always flows to every reachable consumer — the EU output is
 * divided equally among all {@link IElectricReceiver} nodes found in the BFS.
 * Float division prevents energy loss when there are many receivers.
 *
 * <p>Returns the total EU actually accepted by all receivers so that the
 * source (e.g. a capacitor) can drain exactly that amount — no more, no less.
 * This prevents the source from losing EU that receivers could not store.
 */
public final class ElectricNetworkUtil {
    private ElectricNetworkUtil() {}

    /**
     * Propagate EU from {@code source} through the electric wire network,
     * delivering an equal share of {@code euAmount} to every reachable
     * {@link IElectricReceiver}.
     *
     * @param level            server-side level
     * @param source           position of the EU source (engine / capacitor)
     * @param euAmount         total EU to distribute this network clock
     * @param outputDirections faces from which the source may output EU
     * @return total EU actually accepted by all receivers (may be less than
     *         {@code euAmount} if some buffers were full)
     */
    public static float propagateElectricity(Level level, BlockPos source,
            float euAmount, Direction[] outputDirections) {

        if (euAmount <= 0f) return 0f;

        Set<BlockPos>           visited   = new HashSet<>();
        ArrayDeque<BlockPos>    queue     = new ArrayDeque<>();
        List<IElectricReceiver> receivers = new ArrayList<>();

        // ── Phase 1: BFS through wires, collect terminal receivers ────────────
        visited.add(source);

        for (Direction dir : outputDirections) {
            BlockPos neighbor = source.relative(dir);
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof ElectricWireBlock) {
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            } else {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof IElectricReceiver receiver) {
                    receivers.add(receiver);
                }
            }
        }

        if (receivers.isEmpty()) return 0f;

        // ── Phase 2: divide EU equally, sum what is actually accepted ─────────
        float share = euAmount / receivers.size();
        float totalAccepted = 0f;

        for (IElectricReceiver receiver : receivers) {
            totalAccepted += receiver.addElectricity(share);
        }

        return totalAccepted;
    }
}
