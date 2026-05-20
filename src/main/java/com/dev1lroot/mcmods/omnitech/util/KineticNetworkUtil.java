/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.io.IKineticReceiver;
import com.dev1lroot.mcmods.omnitech.io.IKineticSupplier;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticPipeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticReductorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.kinetic.KineticReductorBlockEntity;
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
 * Shared utility for kinetic-force BFS propagation.
 *
 * <p>Both {@code KineticGeneratorBlockEntity} and {@code StirlingEngineBlockEntity}
 * call {@link #propagateKineticForce} from their server tick so that all KF sources
 * share the same traversal rules without duplicating code.
 *
 * <p>Traversal rules:
 * <ul>
 *   <li>A {@link KineticPipeBlock} is only entered from a direction matching its axis.</li>
 *   <li>A {@link KineticReductorBlock} is entered from any direction and propagates
 *       to all six faces (omnidirectional junction).</li>
 *   <li>Any other block that implements {@link IKineticReceiver} receives the force.</li>
 *   <li>Any other block that implements {@link IKineticSupplier} contributes its
 *       supply to the shared network total.</li>
 * </ul>
 *
 * <p><b>Supply accounting:</b> the BFS sums up total supply (own source + all
 * other generators found), then delivers that total to every receiver unconditionally.
 * Machines accumulate KF tick-by-tick and fire when their buffer reaches the cycle
 * cost, so a low-supply network simply runs slower rather than stalling.
 *
 * <p>KF values use a fixed-point scale: <b>10 units = 1 KF</b>.
 */
public final class KineticNetworkUtil {
    private KineticNetworkUtil() {}

    // ── Collected-node records ─────────────────────────────────────────────────

    private record PipeNode(BlockPos pos, KineticPipeBlockEntity be) {}
    private record ReductorNode(BlockPos pos, KineticReductorBlockEntity be) {}

    /**
     * BFS from {@code source} through the kinetic pipe network.
     *
     * <p>Phase 1 collects all reachable nodes and sums total supply.
     * Phase 2 delivers that supply to every receiver unconditionally.
     *
     * @param level        the server-side level
     * @param source       position of the KF source (generator / stirling engine)
     * @param ownKfUnits   fixed-point KF units (10 = 1 KF) this source produces
     */
    public static void propagateKineticForce(Level level, BlockPos source, float ownKfUnits) {
        record Step(BlockPos pos, Direction.Axis entryAxis) {}

        Set<BlockPos>         visited   = new HashSet<>();
        ArrayDeque<Step>      queue     = new ArrayDeque<>();
        List<PipeNode>        pipes     = new ArrayList<>();
        List<ReductorNode>    reductors = new ArrayList<>();
        List<IKineticReceiver> receivers = new ArrayList<>();

        float totalSupply = ownKfUnits;

        // ── Phase 1: BFS collection ────────────────────────────────────────────

        visited.add(source);
        for (Direction dir : Direction.values()) {
            queue.add(new Step(source.relative(dir), dir.getAxis()));
        }

        while (!queue.isEmpty()) {
            Step step = queue.poll();
            if (visited.contains(step.pos())) continue;
            visited.add(step.pos());

            BlockState state = level.getBlockState(step.pos());

            if (state.getBlock() instanceof KineticPipeBlock) {
                Direction.Axis pipeAxis = state.getValue(KineticPipeBlock.AXIS);
                // Pipes only accept entry from the matching axis face
                if (step.entryAxis() != pipeAxis) continue;

                BlockEntity be = level.getBlockEntity(step.pos());
                if (be instanceof KineticPipeBlockEntity pipe) {
                    pipes.add(new PipeNode(step.pos(), pipe));
                }

                for (Direction dir : Direction.values()) {
                    if (dir.getAxis() != pipeAxis) continue;
                    BlockPos next = step.pos().relative(dir);
                    if (!visited.contains(next)) {
                        queue.add(new Step(next, pipeAxis));
                    }
                }

            } else if (state.getBlock() instanceof KineticReductorBlock) {
                if (state.getValue(KineticReductorBlock.SIGNALED)) {
                    // Blocked by redstone — do not traverse past this reductor
                    continue;
                }

                BlockEntity be = level.getBlockEntity(step.pos());
                if (be instanceof KineticReductorBlockEntity reductor) {
                    reductors.add(new ReductorNode(step.pos(), reductor));
                }

                // Reductors accept from any direction and propagate to all six faces
                for (Direction dir : Direction.values()) {
                    BlockPos next = step.pos().relative(dir);
                    if (!visited.contains(next)) {
                        queue.add(new Step(next, dir.getAxis()));
                    }
                }

            } else {
                // Terminal node — check if supplier or consumer (or both)
                BlockEntity be = level.getBlockEntity(step.pos());

                if (be instanceof IKineticSupplier supplier) {
                    totalSupply += supplier.getKfSupply();
                }

                if (be instanceof IKineticReceiver receiver) {
                    receivers.add(receiver);
                }
            }
        }

        // ── Phase 2: dispatch ──────────────────────────────────────────────────

        // Pipes and reductors animate whenever any KF is flowing
        for (PipeNode pn : pipes) {
            BlockState pipeState = level.getBlockState(pn.pos());
            pn.be().refreshPoweredTimer(level, pn.pos(), pipeState, totalSupply);
        }

        for (ReductorNode rn : reductors) {
            BlockState rState = level.getBlockState(rn.pos());
            rn.be().refreshPoweredTimer(level, rn.pos(), rState, totalSupply);
        }

        // Always deliver KF so machines can accumulate when supply < demand
        for (IKineticReceiver receiver : receivers) {
            receiver.addKineticForce(totalSupply);
        }
    }
}
