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
 * <p><b>Supply/demand accounting:</b> the BFS first collects every node reachable
 * from {@code source}, sums up total supply (own + all other generators found) and
 * total demand (from all {@link IKineticReceiver} nodes).  If supply ≥ demand,
 * every receiver gets {@link IKineticReceiver#addKineticForce} called and every
 * pipe's rotation speed is set proportional to the total KF.  If supply &lt;
 * demand, receivers are <em>not</em> powered (the network stalls) but pipes still
 * animate so the player can see the generator is running.
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
     * <p>Phase 1 collects all reachable nodes and computes total supply vs demand.
     * Phase 2 dispatches force if supply ≥ demand, or withholds it to stall
     * machines when the network is overloaded.
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
        float totalDemand = 0;

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
                    totalDemand += receiver.getKfDemand();
                }
            }
        }

        // ── Phase 2: dispatch ──────────────────────────────────────────────────

        boolean powered = totalSupply >= totalDemand;

        // Always refresh pipe animations (so they spin whenever a generator is running,
        // even if the network is stalled — player can see supply is available)
        for (PipeNode pn : pipes) {
            BlockState pipeState = level.getBlockState(pn.pos());
            pn.be().refreshPoweredTimer(level, pn.pos(), pipeState, powered ? totalSupply : 0);
        }

        // Reductors animate whenever KF passes through them
        for (ReductorNode rn : reductors) {
            BlockState rState = level.getBlockState(rn.pos());
            rn.be().refreshPoweredTimer(level, rn.pos(), rState);
        }

        // Only deliver force to machines if supply is sufficient
        if (powered) {
            for (IKineticReceiver receiver : receivers) {
                receiver.addKineticForce(totalSupply);
            }
        }
    }
}
