package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.IKineticReceiver;
import com.dev1lroot.mcmods.omnitech.blocks.KineticPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.KineticPipeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.KineticReductorBlock;
import com.dev1lroot.mcmods.omnitech.blocks.KineticReductorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
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
 * </ul>
 */
public final class KineticNetworkUtil {
    private KineticNetworkUtil() {}

    /**
     * BFS from {@code source} through the kinetic pipe network, delivering
     * {@code forcePerReceiver} to every reachable {@link IKineticReceiver}.
     *
     * <p>Pipes and reductors in the path have their powered timers refreshed so
     * that their animations stay active.
     *
     * @param level           the server-side level
     * @param source          position of the KF source (generator / stirling engine)
     * @param forcePerReceiver kinetic force units delivered to each terminal receiver
     */
    public static void propagateKineticForce(Level level, BlockPos source, int forcePerReceiver) {
        record Step(BlockPos pos, Direction.Axis entryAxis) {}

        Set<BlockPos>    visited = new HashSet<>();
        ArrayDeque<Step> queue   = new ArrayDeque<>();

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

                BlockEntity pipeEntity = level.getBlockEntity(step.pos());
                if (pipeEntity instanceof KineticPipeBlockEntity pipe) {
                    pipe.refreshPoweredTimer(level, step.pos(), state);
                }

                for (Direction dir : Direction.values()) {
                    if (dir.getAxis() != pipeAxis) continue;
                    BlockPos next = step.pos().relative(dir);
                    if (!visited.contains(next)) {
                        queue.add(new Step(next, pipeAxis));
                    }
                }

            }
            else if (state.getBlock() instanceof KineticReductorBlock)
            {
                if (state.getValue(KineticReductorBlock.SIGNALED)) {
                    // Опционально: гасим анимацию сразу, если BFS дошел сюда
                    BlockEntity reductorBe = level.getBlockEntity(step.pos());
                    if (reductorBe instanceof KineticReductorBlockEntity reductor) {
                        // Вызываем метод, который мы обновили ранее, чтобы он сбросил таймер
                        reductor.refreshPoweredTimer(level, step.pos(), state);
                    }
                    continue; // ПРЕРЫВАЕМ цепь: соседи этого редуктора не попадут в очередь
                }

                // Reductors accept from any direction and output to all six faces
                BlockEntity reductorBe = level.getBlockEntity(step.pos());
                if (reductorBe instanceof KineticReductorBlockEntity reductor) {
                    reductor.refreshPoweredTimer(level, step.pos(), state);
                }
                for (Direction dir : Direction.values()) {
                    BlockPos next = step.pos().relative(dir);
                    if (!visited.contains(next)) {
                        queue.add(new Step(next, dir.getAxis()));
                    }
                }

            } else {
                // Terminal node — deliver force if the block accepts it
                BlockEntity be = level.getBlockEntity(step.pos());
                if (be instanceof IKineticReceiver receiver) {
                    receiver.addKineticForce(forcePerReceiver);
                }
            }
        }
    }
}
