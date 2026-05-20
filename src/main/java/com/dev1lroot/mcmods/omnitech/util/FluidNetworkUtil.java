/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidPipeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.plumbing.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class FluidNetworkUtil {
    private FluidNetworkUtil() {}


    // Добавьте этот метод в раздел Helpers для удобства
    private static int getBlockColor(BlockState state) {
        // Предполагаем, что COLOR — это IntegerProperty.
        // Если свойства нет (например, у танка), возвращаем -1 или другое спец. значение
        if (state.hasProperty(FluidPipeBlock.COLOR)) {
            return state.getValue(FluidPipeBlock.COLOR);
        }
        return -1; // "Нейтральный" цвет
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Collects the fluid network reachable from {@code startPos} and
     * re-distributes its total fluid according to density:
     * <ul>
     *   <li>density &lt; 0 (steam): fills from highest Y downward — rises.</li>
     *   <li>density = 0 (pressurized gases): fills all nodes proportionally
     *       to their capacity regardless of height — equal pressure spread.</li>
     *   <li>density &gt; 0 (liquids, molten metals): fills from lowest Y upward — sinks.</li>
     * </ul>
     */
    public static void syncNetwork(Level level, BlockPos startPos) {
        NetworkData data = collectNetwork(level, startPos);

        if (data.nodes().isEmpty() || (data.totalAmount() <= 0 && data.reference().isEmpty())) {
            return;
        }

        // Bake the weighted-average temperature into the reference stack so every
        // node in the network ends up with the same blended temperature after redistribution.
        FluidStack ref = data.reference();
        if (!ref.isEmpty()) {
            ref = ref.copy();
            int temp = data.blendedTemp();
            if (temp != 20) ref.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), temp);
            else            ref.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        }

        Map<Integer, List<BlockEntity>> levels = groupNodesByHeight(data.nodes());
        distributeFluids(level, levels, data.totalAmount(), ref);
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
            } else {
                // Any other block (machine, etc.) — check if it can accept fluid via
                // the capability system. These are terminal insertion targets; BFS
                // does not traverse further from them (no pipe/network inside machines).
                ResourceHandler<FluidResource> capHandler =
                        level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
                if (capHandler != null && hasSpace(capHandler, resource)) {
                    tankTargets.add(capHandler); // prefer machines over pipes
                }
            }
        }

        // Prefer the first tank with space; fall back to the first pipe with space
        if (!tankTargets.isEmpty()) return tankTargets.get(0);
        if (!pipeTargets.isEmpty()) return pipeTargets.get(0);
        return null;
    }

    /**
     * BFS through the fluid network on the input side of a pump to find the
     * closest non-empty fluid source.
     *
     * <p>Traversal follows the same connectivity rules as the output BFS: pipe
     * connection properties gate pipe-to-pipe edges; tanks connect to all
     * neighbours; non-pipe/tank blocks are treated as terminal sources and are
     * NOT traversed further (they are machines or other mod containers).
     *
     * <p>The approach direction is tracked per-node so that directional machine
     * capabilities (e.g. the Solvation Machine that only exposes its output on
     * its back face) are queried from the correct face.
     *
     * @param level      the server level
     * @param startPos   the block directly behind the pump (its input-side neighbour)
     * @param inputDir   direction FROM the pump TOWARD {@code startPos}
     * @return the {@link ResourceHandler} of the closest non-empty source, or
     *         {@code null} if the whole input network is empty
     */
    public static @Nullable ResourceHandler<FluidResource> findInputSource(
            Level level, BlockPos startPos, Direction inputDir) {

        record Entry(BlockPos pos, Direction fromDir) {}

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<Entry> queue = new ArrayDeque<>();
        visited.add(startPos);
        queue.add(new Entry(startPos, inputDir));

        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            BlockPos pos = entry.pos();
            // fromDir: the direction we travelled to arrive at this node, i.e.
            // pos == prevPos.relative(fromDir). Therefore the face of this block
            // that looks back toward the network is fromDir.getOpposite().
            Direction fromDir = entry.fromDir();

            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof FluidPipeBlockEntity pipe) {
                if (!pipe.getFluid().isEmpty()) return pipe.fluidHandler;
                BlockState state = level.getBlockState(pos);
                for (Direction d : Direction.values()) {
                    if (state.hasProperty(FluidPipeBlock.propertyFor(d))
                            && state.getValue(FluidPipeBlock.propertyFor(d))) {
                        BlockPos next = pos.relative(d);
                        if (visited.add(next)) queue.add(new Entry(next, d));
                    }
                }
            } else if (be instanceof FluidTankBlockEntity tank) {
                if (!tank.getFluid().isEmpty()) return tank.fluidHandler;
                for (Direction d : Direction.values()) {
                    BlockPos next = pos.relative(d);
                    if (visited.add(next)) queue.add(new Entry(next, d));
                }
            } else {
                // External block (machine output tank, vanilla block, another mod's
                // container). Query its capability from the face that connects back
                // toward the network so directional machines are handled correctly.
                Direction faceBack = fromDir.getOpposite();
                ResourceHandler<FluidResource> cap =
                        level.getCapability(Capabilities.Fluid.BLOCK, pos, faceBack);
                if (cap != null) {
                    for (int i = 0; i < cap.size(); i++) {
                        if (!cap.getResource(i).isEmpty() && cap.getAmountAsInt(i) > 0) {
                            return cap;
                        }
                    }
                }
                // Do not BFS further through external blocks.
            }
        }
        return null;
    }

    // ── Network collection ────────────────────────────────────────────────────

    private record NetworkData(List<BlockEntity> nodes, long totalAmount, FluidStack reference, int blendedTemp) {}

    private static NetworkData collectNetwork(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        List<BlockEntity> nodes = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        queue.add(startPos);
        visited.add(startPos);

        long totalAmount = 0;
        long totalWeightedTemp = 0;
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

            int fsAmount = fs.getAmount();
            totalAmount += fsAmount;
            totalWeightedTemp += (long) getFluidTemp(fs) * fsAmount;
            nodes.add(be);

            for (BlockPos nextPos : getConnectedNeighbors(level, pos, be)) {
                if (!visited.contains(nextPos)) {
                    visited.add(nextPos);
                    queue.add(nextPos);
                }
            }
        }
        int blendedTemp = totalAmount > 0 ? (int) (totalWeightedTemp / totalAmount) : 20;
        return new NetworkData(nodes, totalAmount, referenceStack, blendedTemp);
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

        int density = ref.getFluid().getFluidType().getDensity();

        if (density == 0) {
            // Pressurized gas: spreads equally to all containers regardless of height.
            distributeEqually(level, levels, totalAmount, ref);
        } else {
            // Gravity-driven: steam (density < 0) rises; liquids/molten (density > 0) sink.
            distributeByGravity(level, levels, totalAmount, ref, density < 0);
        }
    }

    /**
     * Distributes fluid proportionally by node capacity across the entire network,
     * ignoring height. Used for pressurized gases (density == 0) that behave like
     * a pressure vessel — every connected container gets an equal share relative
     * to its volume.
     */
    private static void distributeEqually(Level level, Map<Integer, List<BlockEntity>> levels,
            long totalAmount, FluidStack ref) {
        long totalCapacity = 0;
        for (List<BlockEntity> nodes : levels.values()) {
            totalCapacity += calculateTotalCapacity(nodes);
        }
        if (totalCapacity <= 0) return;

        List<BlockEntity> allNodes = new ArrayList<>();
        for (List<BlockEntity> nodes : levels.values()) allNodes.addAll(nodes);

        if (totalAmount >= totalCapacity) {
            // Network is completely full — fill every node to its capacity.
            for (BlockEntity be : allNodes) {
                updateBlockFluid(be, getCapacity(be), ref, level);
            }
            return;
        }

        // Proportional share: each node receives (itsCapacity / totalCapacity) * totalAmount.
        // Integer rounding may cause a discrepancy of ±1 mb per node, which is acceptable.
        for (BlockEntity be : allNodes) {
            int share = (int) Math.round(getCapacity(be) * (double) totalAmount / totalCapacity);
            updateBlockFluid(be, share, ref, level);
        }
    }

    /**
     * Distributes fluid level-by-level according to gravity. Gases (density &lt; 0)
     * accumulate at the top; liquids and molten metals (density &gt; 0) sink to the
     * bottom. Within a single Y-level nodes share the available fluid proportionally.
     */
    private static void distributeByGravity(Level level, Map<Integer, List<BlockEntity>> levels,
            long totalAmount, FluidStack ref, boolean isGas) {
        long remaining = totalAmount;

        List<Integer> keys = new ArrayList<>(levels.keySet());
        if (isGas) {
            keys.sort(Comparator.reverseOrder()); // rises — highest Y fills first
        } else {
            keys.sort(Comparator.naturalOrder());  // sinks — lowest Y fills first
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

    private static int getFluidTemp(FluidStack fs) {
        if (fs.isEmpty()) return 20;
        Integer t = fs.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        return t != null ? t : 20;
    }

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
        int currentColor = getBlockColor(state);

        if (be instanceof FluidPipeBlockEntity) {
            for (Direction dir : Direction.values()) {
                // Проверяем визуальное соединение (North, South и т.д.)
                if (state.hasProperty(FluidPipeBlock.propertyFor(dir)) && state.getValue(FluidPipeBlock.propertyFor(dir))) {
                    BlockPos nextPos = pos.relative(dir);
                    BlockState nextState = level.getBlockState(nextPos);

                    // ЛОГИКА ЦВЕТА:
                    // Если следующий блок — тоже труба, их цвета должны совпадать.
                    // Если следующий блок не имеет цвета (например, танк), разрешаем соединение.
                    int nextColor = getBlockColor(nextState);
                    if (nextColor == -1 || nextColor == currentColor) {
                        neighbors.add(nextPos);
                    }
                }
            }
        } else if (be instanceof FluidTankBlockEntity) {
            for (Direction dir : Direction.values()) {
                // Танки соединяются со всеми соседями
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
