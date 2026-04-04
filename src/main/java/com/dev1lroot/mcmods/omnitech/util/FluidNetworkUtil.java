package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.FluidTankBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.StirlingEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.*;

public final class FluidNetworkUtil {
    private FluidNetworkUtil() {}

    /**
     * Контейнер для результатов сканирования сети
     */
    private record NetworkData(List<BlockEntity> nodes, long totalAmount, FluidStack reference) {}

    private static boolean isValidNode(BlockEntity be, FluidStack reference) {
        if (be instanceof FluidPipeBlockEntity || be instanceof FluidTankBlockEntity) {
            return true;
        }

        if (be instanceof StirlingEngineBlockEntity) {
            // Двигатель участвует в сети только если мы распределяем воду
            // Если reference пуст, мы позволяем ему войти в сеть, чтобы он стал источником воды
            return reference.isEmpty() || reference.is(OmniTechFluids.STEAM);
        }

        return false;
    }

    private static FluidStack getFluidFromEntity(BlockEntity be) {
        if (be instanceof FluidPipeBlockEntity p) return p.getFluid();
        if (be instanceof FluidTankBlockEntity t) return t.getFluid();
        if (be instanceof StirlingEngineBlockEntity s) return s.getFluid(); // ДОБАВИТЬ ЭТО
        return FluidStack.EMPTY;
    }

    private static int getCapacity(BlockEntity be) {
        if (be instanceof FluidPipeBlockEntity) return FluidPipeBlockEntity.CAPACITY;
        if (be instanceof FluidTankBlockEntity) return FluidTankBlockEntity.CAPACITY;
        if (be instanceof StirlingEngineBlockEntity) return StirlingEngineBlockEntity.MAX_WATER; // ДОБАВИТЬ ЭТО
        return 0;
    }

    public static void syncNetwork(Level level, BlockPos startPos) {
        // 1. Сбор данных о сети
        NetworkData data = collectNetwork(level, startPos);

        if (data.nodes().isEmpty() || (data.totalAmount() <= 0 && data.reference().isEmpty())) {
            return;
        }

        // 2. Группировка узлов по уровням Y (для гравитации)
        Map<Integer, List<BlockEntity>> levels = groupNodesByHeight(data.nodes());

        // 3. Распределение жидкости по уровням
        distributeFluids(level, levels, data.totalAmount(), data.reference());
    }

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

            if (!isValidNode(be,referenceStack)) continue;

            FluidStack fs = getFluidFromEntity(be);

            // Проверка на смешивание
            if (!referenceStack.isEmpty() && !fs.isEmpty() && !FluidStack.isSameFluid(referenceStack, fs)) {
                continue;
            }

            if (referenceStack.isEmpty() && !fs.isEmpty()) {
                referenceStack = fs;
            }

            totalAmount += fs.getAmount();
            nodes.add(be);

            // Поиск соседей
            for (BlockPos nextPos : getConnectedNeighbors(level, pos, be)) {
                if (!visited.contains(nextPos)) {
                    visited.add(nextPos);
                    queue.add(nextPos);
                }
            }
        }
        return new NetworkData(nodes, totalAmount, referenceStack);
    }

    private static Map<Integer, List<BlockEntity>> groupNodesByHeight(List<BlockEntity> nodes) {
        Map<Integer, List<BlockEntity>> levels = new TreeMap<>();
        for (BlockEntity be : nodes) {
            levels.computeIfAbsent(be.getBlockPos().getY(), k -> new ArrayList<>()).add(be);
        }
        return levels;
    }

    private static void distributeFluids(Level level, Map<Integer, List<BlockEntity>> levels, long totalAmount, FluidStack ref) {
        long remaining = totalAmount;

        for (Map.Entry<Integer, List<BlockEntity>> entry : levels.entrySet()) {
            List<BlockEntity> levelNodes = entry.getValue();
            long levelCapacity = calculateTotalCapacity(levelNodes);

            if (remaining <= 0) {
                clearBlocks(levelNodes, level);
                continue;
            }

            if (remaining < levelCapacity) {
                double ratio = (double) remaining / levelCapacity;
                for (BlockEntity be : levelNodes) {
                    int amount = (int) Math.round(getCapacity(be) * ratio);
                    updateBlockFluid(be, amount, ref, level);
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

    private static long calculateTotalCapacity(List<BlockEntity> nodes) {
        long total = 0;
        for (BlockEntity be : nodes) total += getCapacity(be);
        return total;
    }

    private static List<BlockPos> getConnectedNeighbors(Level level, BlockPos pos, BlockEntity be) {
        List<BlockPos> neighbors = new ArrayList<>();
        BlockState state = level.getBlockState(pos);

        if (be instanceof FluidPipeBlockEntity) {
            for (Direction dir : Direction.values()) {
                if (state.hasProperty(FluidPipeBlock.propertyFor(dir)) && state.getValue(FluidPipeBlock.propertyFor(dir))) {
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
        else if (be instanceof StirlingEngineBlockEntity s) {
            // Здесь нужна проверка, что мы не заливаем в двигатель лаву, если там была вода
            // Но syncNetwork уже проверяет это через referenceStack
            // Тебе нужно будет добавить метод setFluid в StirlingEngineBlockEntity или
            // просто сделать поле fluid доступным (или через сеттер)
            s.setFluid(nextStack);
        }

        be.setChanged();
        BlockState state = be.getBlockState();
        level.sendBlockUpdated(be.getBlockPos(), state, state, 11);
    }

    private static void clearBlocks(List<BlockEntity> nodes, Level level) {
        for (BlockEntity be : nodes) {
            updateBlockFluid(be, 0, FluidStack.EMPTY, level);
        }
    }
}