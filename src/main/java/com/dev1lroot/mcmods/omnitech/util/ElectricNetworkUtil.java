package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.blocks.ElectricWireBlock;
import com.dev1lroot.mcmods.omnitech.blocks.IElectricReceiver;
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
 * <p>The BFS seed is restricted to {@code outputDirections}, so an engine can
 * limit propagation to its 5 non-front faces and a capacitor can restrict
 * discharge to its single front face.
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
     * @return {@code true} if at least one receiver accepted the energy
     */
    public static boolean propagateElectricity(Level level, BlockPos source,
            float euAmount, Direction[] outputDirections) {

        if (euAmount <= 0f) return false;

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
                // Wire: continue BFS in all 6 directions
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            } else {
                // Terminal node — collect receiver if present
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof IElectricReceiver receiver) {
                    receivers.add(receiver);
                }
            }
        }

        if (receivers.isEmpty()) return false;

        // ── Phase 2: divide EU equally among all receivers (float precision) ──
        float share = euAmount / receivers.size();

        boolean anyAccepted = false;
        for (IElectricReceiver receiver : receivers) {
            if (receiver.addElectricity(share)) anyAccepted = true;
        }
        return anyAccepted;
    }
}
