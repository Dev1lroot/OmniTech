/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.blocks.electrical.electric_wire.ElectricWireBlock;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.power_relay.PowerRelayBlock;
import com.dev1lroot.mcmods.omnitech.io.IElectricReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * BFS propagation utility for the Electric network.
 *
 * <p>Electricity always flows to every reachable consumer — the energy output is
 * divided equally among all {@link IElectricReceiver} nodes found in the BFS.
 * Float division prevents energy loss when there are many receivers.
 * Energy is in kJ (see {@link ElectricUnits}); the source's line voltage is
 * passed along so receivers can report voltage and current.
 *
 * <p>Returns the total energy actually accepted by all receivers so that the
 * source (e.g. a capacitor) can drain exactly that amount — no more, no less.
 * This prevents the source from losing energy that receivers could not store.
 */
public final class ElectricNetworkUtil {
    private ElectricNetworkUtil() {}

    /**
     * Propagate energy from {@code source} through the electric wire network,
     * delivering an equal share of {@code euAmount} to every reachable
     * {@link IElectricReceiver}.
     *
     * @param level            server-side level
     * @param source           position of the energy source (engine / capacitor)
     * @param euAmount         total energy in kJ to distribute this network clock
     * @param volts            line voltage of the source
     * @param outputDirections faces from which the source may output energy
     * @return total energy in kJ actually accepted by all receivers (may be less
     *         than {@code euAmount} if some buffers were full)
     */
    public static float propagateElectricity(Level level, BlockPos source,
            float euAmount, float volts, Direction[] outputDirections) {

        if (euAmount <= 0f) return 0f;

        Set<BlockPos>          visited   = new HashSet<>();
        ArrayDeque<BlockPos>   queue     = new ArrayDeque<>();
        Set<IElectricReceiver> receivers = new LinkedHashSet<>();

        // ── Phase 1: BFS through wires, collect terminal receivers ────────────
        // Only conductors (wires, closed relays) are queued. A machine is collected
        // when a conductor touches it on a face it accepts energy through.
        visited.add(source);

        for (Direction dir : outputDirections) {
            visit(level, source, dir, visited, queue, receivers);
        }

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);

            Direction[] exits = state.getBlock() instanceof PowerRelayBlock
                    ? PowerRelayBlock.passthroughDirections(state)
                    : Direction.values();
            for (Direction dir : exits) {
                visit(level, pos, dir, visited, queue, receivers);
            }
        }

        if (receivers.isEmpty()) return 0f;

        // ── Phase 2: divide energy equally, sum what is actually accepted ─────────
        float share = euAmount / receivers.size();
        float totalAccepted = 0f;

        for (IElectricReceiver receiver : receivers) {
            totalAccepted += receiver.addElectricity(share, volts);
        }

        return totalAccepted;
    }

    /** Handles the block at {@code from + dir}: queue it if it conducts, collect it if it accepts. */
    private static void visit(Level level, BlockPos from, Direction dir, Set<BlockPos> visited,
            ArrayDeque<BlockPos> queue, Set<IElectricReceiver> receivers) {
        BlockPos next = from.relative(dir);
        if (visited.contains(next)) return;

        BlockState state = level.getBlockState(next);
        boolean conductor = state.getBlock() instanceof ElectricWireBlock
                || (state.getBlock() instanceof PowerRelayBlock && !state.getValue(PowerRelayBlock.POWERED));
        if (conductor) {
            visited.add(next);
            queue.add(next);
            return;
        }

        // Machines are not marked visited: another conductor may touch a different,
        // accepting face of the same block.
        BlockEntity be = level.getBlockEntity(next);
        if (be instanceof IElectricReceiver receiver && receiver.acceptsElectricityFrom(dir.getOpposite())) {
            receivers.add(receiver);
        }
    }
}
