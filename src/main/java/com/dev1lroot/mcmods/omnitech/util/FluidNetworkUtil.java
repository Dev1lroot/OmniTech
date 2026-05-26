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
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class FluidNetworkUtil {
    private FluidNetworkUtil() {}


    // ── Public fluid attribute helpers ────────────────────────────────────────

    public static int fluidTemp(FluidStack fs) {
        if (fs.isEmpty()) return 20;
        Integer t = fs.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        return t != null ? t : 20;
    }

    public static int fluidPressure(FluidStack fs) {
        if (fs.isEmpty()) return 101;
        Integer p = fs.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        return p != null ? p : 101;
    }

    public static void applyAttributes(FluidStack fs, int temp, int pressure) {
        if (temp != 20) fs.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), temp);
        else            fs.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        if (pressure != 101) fs.set(OmniTechDataComponents.FLUID_PRESSURE.get(), pressure);
        else                 fs.remove(OmniTechDataComponents.FLUID_PRESSURE.get());
    }

    /**
     * Returns a FluidStack equal to {@code existing + toInsert} mB, with
     * temperature and pressure blended by volume. When {@code existing} is
     * empty the incoming resource's attributes are used unchanged.
     */
    public static FluidStack blendInto(FluidStack existing, FluidResource incoming, int toInsert) {
        int existingAmount   = existing.getAmount();
        int total            = existingAmount + toInsert;
        FluidStack sample    = incoming.toStack(1);
        int incomingTemp     = fluidTemp(sample);
        int incomingPressure = fluidPressure(sample);

        int blendedTemp = existingAmount > 0
                ? (fluidTemp(existing) * existingAmount + incomingTemp * toInsert) / total
                : incomingTemp;
        int blendedPressure = existingAmount > 0
                ? (fluidPressure(existing) * existingAmount + incomingPressure * toInsert) / total
                : incomingPressure;

        FluidStack result = existing.isEmpty()
                ? incoming.toStack(toInsert)
                : existing.copyWithAmount(total);

        if (blendedTemp != 20)      result.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), blendedTemp);
        else                        result.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        if (blendedPressure != 101) result.set(OmniTechDataComponents.FLUID_PRESSURE.get(), blendedPressure);
        else                        result.remove(OmniTechDataComponents.FLUID_PRESSURE.get());

        return result;
    }

    // ── Unified fluid transfer ─────────────────────────────────────────────────

    /** Transfers up to 1000 mB from any non-empty slot of {@code from} into {@code to}. */
    public static boolean tryPullFluid(ResourceHandler<FluidResource> from,
                                       ResourceHandler<FluidResource> to) {
        return tryPullFluid(from, to, null, 1000);
    }

    /**
     * Transfers up to {@code maxAmount} mB of a fluid matching {@code filter}
     * from {@code from} into {@code to}. Pass {@code null} to accept any fluid.
     */
    public static boolean tryPullFluid(ResourceHandler<FluidResource> from,
                                       ResourceHandler<FluidResource> to,
                                       @Nullable Fluid filter,
                                       int maxAmount) {
        try (var tx = Transaction.openRoot()) {
            for (int i = 0; i < from.size(); i++) {
                FluidResource res = from.getResource(i);
                if (res.isEmpty()) continue;
                if (filter != null && !res.is(filter)) continue;
                int avail = Math.min(maxAmount, (int) from.getAmountAsLong(i));
                int acc   = to.insert(res, avail, tx);
                if (acc > 0) {
                    from.extract(res, acc, tx);
                    tx.commit();
                    return true;
                }
            }
        }
        return false;
    }

    /** Transfers up to 1000 mB from slot 0 of {@code from} into {@code to}. */
    public static boolean tryPushFluid(ResourceHandler<FluidResource> from,
                                       ResourceHandler<FluidResource> to) {
        return tryPushFluid(from, to, 1000);
    }

    /** Transfers up to {@code maxAmount} mB from slot 0 of {@code from} into {@code to}. */
    public static boolean tryPushFluid(ResourceHandler<FluidResource> from,
                                       ResourceHandler<FluidResource> to,
                                       int maxAmount) {
        try (var tx = Transaction.openRoot()) {
            FluidResource res = from.getResource(0);
            if (res.isEmpty()) return false;
            int avail = Math.min(maxAmount, (int) from.getAmountAsLong(0));
            int acc   = to.insert(res, avail, tx);
            if (acc > 0) {
                from.extract(res, acc, tx);
                tx.commit();
                return true;
            }
        }
        return false;
    }

    /** Distributes up to 1000 mB evenly across all {@code targets} in one transaction. */
    public static boolean tryPushFluidEvenly(ResourceHandler<FluidResource> from,
                                             List<ResourceHandler<FluidResource>> targets) {
        return tryPushFluidEvenly(from, targets, 1000);
    }

    /**
     * Distributes up to {@code maxTotal} mB evenly across all {@code targets},
     * split by the number of targets. All transfers commit atomically.
     */
    public static boolean tryPushFluidEvenly(ResourceHandler<FluidResource> from,
                                             List<ResourceHandler<FluidResource>> targets,
                                             int maxTotal) {
        if (targets.isEmpty()) return false;
        FluidResource res = from.getResource(0);
        if (res.isEmpty()) return false;
        int available = Math.min(maxTotal, (int) from.getAmountAsLong(0));
        if (available <= 0) return false;

        int perTarget = Math.max(1, available / targets.size());
        int totalPushed = 0;
        try (var tx = Transaction.openRoot()) {
            for (var target : targets) {
                totalPushed += target.insert(res, perTarget, tx);
            }
            if (totalPushed > 0) {
                from.extract(res, totalPushed, tx);
                tx.commit();
                return true;
            }
        }
        return false;
    }

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

        // Bake the weighted-average temperature and pressure into the reference stack so every
        // node in the network ends up with the same blended values after redistribution.
        FluidStack ref = data.reference();
        if (!ref.isEmpty()) {
            ref = ref.copy();
            int temp = data.blendedTemp();
            if (temp != 20) ref.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), temp);
            else            ref.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
            int pressure = data.blendedPressure();
            if (pressure != 101) ref.set(OmniTechDataComponents.FLUID_PRESSURE.get(), pressure);
            else                 ref.remove(OmniTechDataComponents.FLUID_PRESSURE.get());
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
        return findOutputTarget(level, startPos, resource, null);
    }

    /**
     * Same as {@link #findOutputTarget(Level, BlockPos, FluidResource)} but the caller
     * supplies the direction the fluid is travelling when it arrives at {@code startPos}.
     * This direction is used to query directional machine capabilities (e.g. a Heat
     * Exchanger that only exposes its input on its front face) with the correct side.
     */
    public static @Nullable ResourceHandler<FluidResource> findOutputTarget(
            Level level, BlockPos startPos, FluidResource resource, @Nullable Direction startDir) {

        record Entry(BlockPos pos, Direction fromDir) {}

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<Entry> queue = new ArrayDeque<>();
        visited.add(startPos);
        queue.add(new Entry(startPos, startDir));

        // Collect all reachable nodes that can accept the fluid, split by type
        // so we can prefer tanks over pipes.
        List<ResourceHandler<FluidResource>> tankTargets = new ArrayList<>();
        List<ResourceHandler<FluidResource>> pipeTargets = new ArrayList<>();

        while (!queue.isEmpty()) {
            Entry entry = queue.poll();
            BlockPos pos = entry.pos();
            Direction fromDir = entry.fromDir();
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof FluidPipeBlockEntity pipe) {
                if (hasSpace(pipe.fluidHandler, resource)) {
                    pipeTargets.add(pipe.fluidHandler);
                }
                for (BlockPos next : getConnectedNeighbors(level, pos, be)) {
                    if (visited.add(next)) queue.add(new Entry(next, directionBetween(pos, next)));
                }
            } else if (be instanceof FluidTankBlockEntity tank) {
                if (hasSpace(tank.fluidHandler, resource)) {
                    tankTargets.add(tank.fluidHandler);
                }
                for (BlockPos next : getConnectedNeighbors(level, pos, be)) {
                    if (visited.add(next)) queue.add(new Entry(next, directionBetween(pos, next)));
                }
            } else {
                // Any other block (machine, etc.) — check if it can accept fluid via
                // the capability system. These are terminal insertion targets; BFS
                // does not traverse further from them (no pipe/network inside machines).
                // Query the face of the machine that is adjacent to our network: the face
                // looking back toward where we came from (fromDir.getOpposite()).
                Direction insertFace = fromDir != null ? fromDir.getOpposite() : null;
                ResourceHandler<FluidResource> capHandler =
                        level.getCapability(Capabilities.Fluid.BLOCK, pos, insertFace);
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

    private record NetworkData(List<BlockEntity> nodes, long totalAmount, FluidStack reference, int blendedTemp, int blendedPressure) {}

    private static NetworkData collectNetwork(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        List<BlockEntity> nodes = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        queue.add(startPos);
        visited.add(startPos);

        long totalAmount = 0;
        long totalWeightedTemp = 0;
        long totalWeightedPressure = 0;
        FluidStack referenceStack = FluidStack.EMPTY;

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockEntity be = level.getBlockEntity(pos);

            if (!isValidNode(be, referenceStack)) continue;

            FluidStack fs = getFluidFromEntity(be);

            if (!referenceStack.isEmpty() && !fs.isEmpty() && referenceStack.getFluid() != fs.getFluid()) {
                continue;
            }

            if (referenceStack.isEmpty() && !fs.isEmpty()) {
                referenceStack = fs;
            }

            int fsAmount = fs.getAmount();
            totalAmount += fsAmount;
            totalWeightedTemp     += (long) getFluidTemp(fs)     * fsAmount;
            totalWeightedPressure += (long) getFluidPressure(fs) * fsAmount;
            nodes.add(be);

            for (BlockPos nextPos : getConnectedNeighbors(level, pos, be)) {
                if (!visited.contains(nextPos)) {
                    visited.add(nextPos);
                    queue.add(nextPos);
                }
            }
        }
        int blendedTemp     = totalAmount > 0 ? (int) (totalWeightedTemp     / totalAmount) : 20;
        int blendedPressure = totalAmount > 0 ? (int) (totalWeightedPressure / totalAmount) : 101;
        return new NetworkData(nodes, totalAmount, referenceStack, blendedTemp, blendedPressure);
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

        var physics = com.dev1lroot.mcmods.omnitech.FluidPhysicsRegistry.get(ref.getFluid());
        Integer tempBox     = ref.get(com.dev1lroot.mcmods.omnitech.OmniTechDataComponents.FLUID_TEMPERATURE.get());
        Integer pressureBox = ref.get(com.dev1lroot.mcmods.omnitech.OmniTechDataComponents.FLUID_PRESSURE.get());
        int tempC       = tempBox     != null ? tempBox     : 20;
        int pressureKPa = pressureBox != null ? pressureBox : 101;

        com.dev1lroot.mcmods.omnitech.FluidPhase phase =
                com.dev1lroot.mcmods.omnitech.FluidPhaseUtil.getPhase(tempC, pressureKPa, physics.phaseDiagram());

        if (phase == com.dev1lroot.mcmods.omnitech.FluidPhase.GAS
                || phase == com.dev1lroot.mcmods.omnitech.FluidPhase.SUPERCRITICAL
                || phase == com.dev1lroot.mcmods.omnitech.FluidPhase.SOLID
                || phase == null) {
            // Pressurized gas / supercritical / frozen solid — spreads equally.
            distributeEqually(level, levels, totalAmount, ref);
        } else {
            // LIQUID sinks; VAPOUR and PLASMA rise.
            boolean rises = phase == com.dev1lroot.mcmods.omnitech.FluidPhase.VAPOUR
                         || phase == com.dev1lroot.mcmods.omnitech.FluidPhase.PLASMA;
            distributeByGravity(level, levels, totalAmount, ref, rises);
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
     * Distributes fluid level-by-level according to gravity. Vapour/plasma phases
     * accumulate at the top; liquids sink to the bottom. Within a single Y-level
     * nodes share the available fluid proportionally.
     */
    private static void distributeByGravity(Level level, Map<Integer, List<BlockEntity>> levels,
            long totalAmount, FluidStack ref, boolean rises) {
        long remaining = totalAmount;

        List<Integer> keys = new ArrayList<>(levels.keySet());
        if (rises) {
            keys.sort(Comparator.reverseOrder()); // vapour/plasma — highest Y fills first
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

    private static int getFluidTemp(FluidStack fs)      { return fluidTemp(fs);     }
    private static int getFluidPressure(FluidStack fs)  { return fluidPressure(fs); }

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
        if (!current.isEmpty() && current.getAmount() == amount
                && fluidTemp(current) == fluidTemp(ref)
                && fluidPressure(current) == fluidPressure(ref)) return;

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

    @Nullable
    private static Direction directionBetween(BlockPos from, BlockPos to) {
        for (Direction d : Direction.values()) {
            if (from.relative(d).equals(to)) return d;
        }
        return null;
    }
}
