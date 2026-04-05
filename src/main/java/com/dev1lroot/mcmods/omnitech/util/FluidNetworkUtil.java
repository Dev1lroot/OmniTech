package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class FluidNetworkUtil {
    private FluidNetworkUtil() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Collects the fluid network reachable from {@code startPos} and
     * re-distributes its total fluid according to gravity:
     * liquids fill from the lowest Y upward; gases (density ≤ 0) fill from
     * the highest Y downward.
     */
    public static void syncNetwork(Level level, BlockPos startPos) {
        NetworkData data = collectNetwork(level, startPos);

        if (data.nodes().isEmpty() || (data.totalAmount() <= 0 && data.reference().isEmpty())) {
            return;
        }

        Map<Integer, List<BlockEntity>> levels = groupNodesByHeight(data.nodes());
        distributeFluids(level, levels, data.totalAmount(), data.reference());
    }

    /**
     * BFS through the fluid network starting at {@code startPos} to find the
     * first node that has capacity available for {@code resource}.
     *
     * <p>Traversal respects pipe connection properties and stops at blocks that
     * are not pipes or tanks (including other pumps), so each pump's output
     * network remains isolated. Tanks are preferred over pipes as targets: the
     * BFS visits pipes but only returns a pipe as the target when no tank with
     * space is reachable.
     *
     * <p>Called every tick by a powered pump so it can route fluid past full
     * nodes to reach empty space anywhere in the output network.
     *
     * @param level    the server level
     * @param startPos the block directly in front of the pump's output face
     * @param resource the fluid type to insert
     * @return the {@link ResourceHandler} of the best target node, or
     *         {@code null} if the entire output network is full or incompatible
     */
    public static @Nullable ResourceHandler<FluidResource> findOutputTarget(
            Level level, BlockPos startPos, FluidResource resource) {

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(startPos);
        queue.add(startPos);

        // Collect all reachable nodes that can accept the fluid, split by type
        // so we can prefer tanks over pipes.
        List<ResourceHandler<FluidResource>> tankTargets = new ArrayList<>();
        List<ResourceHandler<FluidResource>> pipeTargets = new ArrayList<>();

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof FluidPipeBlockEntity pipe) {
                if (hasSpace(pipe.fluidHandler, resource)) {
                    pipeTargets.add(pipe.fluidHandler);
                }
                for (BlockPos next : getConnectedNeighbors(level, pos, be)) {
                    if (visited.add(next)) queue.add(next);
                }
            } else if (be instanceof FluidTankBlockEntity tank) {
                if (hasSpace(tank.fluidHandler, resource)) {
                    tankTargets.add(tank.fluidHandler);
                }
                for (BlockPos next : getConnectedNeighbors(level, pos, be)) {
                    if (visited.add(next)) queue.add(next);
                }
            }
            // Any other block (other pump, machine, air) — stop traversal on this path
        }

        // Prefer the first tank with space; fall back to the first pipe with space
        if (!tankTargets.isEmpty()) return tankTargets.get(0);
        if (!pipeTargets.isEmpty()) return pipeTargets.get(0);
        return null;
    }

    // ── Network collection ────────────────────────────────────────────────────

    private record NetworkData(List<BlockEntity> nodes, long totalAmount, FluidStack reference) {}

    private static NetworkData collectNetwork(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        List<BlockEntity> nodes = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        queue.add(startPos);
        visited.add(startPos);

        long totalAmount = 0;
        FluidStack referenceStack = FluidStack.EMPTY;

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockEntity be = level.getBlockEntity(pos);

            if (!isValidNode(be, referenceStack)) continue;

            FluidStack fs = getFluidFromEntity(be);

            if (!referenceStack.isEmpty() && !fs.isEmpty() && !FluidStack.isSameFluid(referenceStack, fs)) {
                continue;
            }

            if (referenceStack.isEmpty() && !fs.isEmpty()) {
                referenceStack = fs;
            }

            totalAmount += fs.getAmount();
            nodes.add(be);

            for (BlockPos nextPos : getConnectedNeighbors(level, pos, be)) {
                if (!visited.contains(nextPos)) {
                    visited.add(nextPos);
                    queue.add(nextPos);
                }
            }
        }
        return new NetworkData(nodes, totalAmount, referenceStack);
    }

    // ── Gravity distribution ──────────────────────────────────────────────────

    private static Map<Integer, List<BlockEntity>> groupNodesByHeight(List<BlockEntity> nodes) {
        Map<Integer, List<BlockEntity>> levels = new TreeMap<>();
        for (BlockEntity be : nodes) {
            levels.computeIfAbsent(be.getBlockPos().getY(), k -> new ArrayList<>()).add(be);
        }
        return levels;
    }

    private static void distributeFluids(Level level, Map<Integer, List<BlockEntity>> levels,
            long totalAmount, FluidStack ref) {
        if (ref.isEmpty()) return;

        long remaining = totalAmount;

        // Gas = lighter than air (density ≤ 0); fills from the top downward.
        boolean isGas = ref.getFluid().getFluidType().isLighterThanAir();

        List<Integer> keys = new ArrayList<>(levels.keySet());
        if (isGas) {
            keys.sort(Comparator.reverseOrder()); // gas rises — highest Y fills first
        } else {
            keys.sort(Comparator.naturalOrder());  // liquid falls — lowest Y fills first
        }

        for (int y : keys) {
            List<BlockEntity> levelNodes = levels.get(y);
            long levelCapacity = calculateTotalCapacity(levelNodes);

            if (remaining <= 0) {
                clearBlocks(levelNodes, level);
                continue;
            }

            if (remaining < levelCapacity) {
                double ratio = (double) remaining / levelCapacity;
                for (BlockEntity be : levelNodes) {
                    updateBlockFluid(be, (int) Math.round(getCapacity(be) * ratio), ref, level);
                }
                remaining = 0;
            } else {
                for (BlockEntity be : levelNodes) {
                    updateBlockFluid(be, getCapacity(be), ref, level);
                }
                remaining -= levelCapacity;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isValidNode(BlockEntity be, FluidStack reference) {
        return be instanceof FluidPipeBlockEntity || be instanceof FluidTankBlockEntity;
    }

    private static FluidStack getFluidFromEntity(BlockEntity be) {
        if (be instanceof FluidPipeBlockEntity p) return p.getFluid();
        if (be instanceof FluidTankBlockEntity t) return t.getFluid();
        return FluidStack.EMPTY;
    }

    private static int getCapacity(BlockEntity be) {
        if (be instanceof FluidPipeBlockEntity) return FluidPipeBlockEntity.CAPACITY;
        if (be instanceof FluidTankBlockEntity) return FluidTankBlockEntity.CAPACITY;
        return 0;
    }

    /**
     * Returns {@code true} if {@code handler} has at least 1 mb of room for
     * {@code resource} in any of its slots.
     */
    private static boolean hasSpace(ResourceHandler<FluidResource> handler, FluidResource resource) {
        for (int i = 0; i < handler.size(); i++) {
            if (!handler.isValid(i, resource)) continue;
            long space = handler.getCapacityAsLong(i, resource) - handler.getAmountAsLong(i);
            if (space > 0) return true;
        }
        return false;
    }

    private static List<BlockPos> getConnectedNeighbors(Level level, BlockPos pos, BlockEntity be) {
        List<BlockPos> neighbors = new ArrayList<>();
        BlockState state = level.getBlockState(pos);

        if (be instanceof FluidPipeBlockEntity) {
            for (Direction dir : Direction.values()) {
                if (state.hasProperty(FluidPipeBlock.propertyFor(dir))
                        && state.getValue(FluidPipeBlock.propertyFor(dir))) {
                    neighbors.add(pos.relative(dir));
                }
            }
        } else if (be instanceof FluidTankBlockEntity) {
            for (Direction dir : Direction.values()) {
                neighbors.add(pos.relative(dir));
            }
        }
        return neighbors;
    }

    private static void updateBlockFluid(BlockEntity be, int amount, FluidStack ref, Level level) {
        FluidStack current = getFluidFromEntity(be);
        if (current.getAmount() == amount && !current.isEmpty()) return;

        FluidStack nextStack = (amount <= 0) ? FluidStack.EMPTY : ref.copyWithAmount(amount);

        if (be instanceof FluidPipeBlockEntity p) p.setFluid(nextStack);
        else if (be instanceof FluidTankBlockEntity t) t.setFluid(nextStack);

        be.setChanged();
        BlockState state = be.getBlockState();
        level.sendBlockUpdated(be.getBlockPos(), state, state, 11);
    }

    private static void clearBlocks(List<BlockEntity> nodes, Level level) {
        for (BlockEntity be : nodes) {
            updateBlockFluid(be, 0, FluidStack.EMPTY, level);
        }
    }

    private static long calculateTotalCapacity(List<BlockEntity> nodes) {
        long total = 0;
        for (BlockEntity be : nodes) total += getCapacity(be);
        return total;
    }
}
