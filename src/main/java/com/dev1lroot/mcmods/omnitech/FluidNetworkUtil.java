package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlock;
import com.dev1lroot.mcmods.omnitech.blocks.FluidPipeBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.*;

public final class FluidNetworkUtil {
    private FluidNetworkUtil() {}

    public static void syncNetwork(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        List<BlockEntity> nodes = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        queue.add(startPos);
        visited.add(startPos);

        long totalAmount = 0;
        FluidStack referenceStack = FluidStack.EMPTY;

        // 1. Сбор всей сети
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;

            FluidStack fs = FluidStack.EMPTY;
            if (be instanceof FluidPipeBlockEntity pipe) {
                fs = pipe.getFluid();
            } else if (be instanceof FluidTankBlockEntity tank) {
                fs = tank.getFluid();
            } else {
                continue;
            }

            // ПРОВЕРКА НА СМЕШИВАНИЕ:
            // Если в сети уже определена жидкость, а в текущем блоке другая (не пустая)
            if (!referenceStack.isEmpty() && !fs.isEmpty() && !FluidStack.isSameFluid(referenceStack, fs)) {
                // Пропускаем этот блок, не добавляем в узлы и не идем от него к соседям
                continue;
            }

            // Устанавливаем эталонную жидкость, если еще не нашли
            if (referenceStack.isEmpty() && !fs.isEmpty()) {
                referenceStack = fs;
            }

            totalAmount += fs.getAmount();
            nodes.add(be);

            // Рекурсивный поиск соседей (только если блок подошел по типу жидкости)
            BlockState state = level.getBlockState(pos);
            if (be instanceof FluidPipeBlockEntity) {
                for (Direction dir : Direction.values()) {
                    if (state.hasProperty(FluidPipeBlock.propertyFor(dir)) && state.getValue(FluidPipeBlock.propertyFor(dir))) {
                        BlockPos next = pos.relative(dir);
                        if (!visited.contains(next)) {
                            visited.add(next);
                            queue.add(next);
                        }
                    }
                }
            } else { // Для танков
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }

        if (nodes.isEmpty() || (totalAmount <= 0 && referenceStack.isEmpty())) return;

        // 2. Сортировка узлов по высоте (Y) для реализации гравитации
        // Сначала заполняем те, что ниже (минимальный Y)
        nodes.sort(Comparator.comparingInt(a -> a.getBlockPos().getY()));

        // 3. Распределение жидкости
        long remaining = totalAmount;

        // Сначала отдаем приоритет заполнению нижних блоков до краев (Гравитация)
        // Но чтобы сохранить "сообщающиеся сосуды", мы считаем общую емкость на каждом уровне Y
        Map<Integer, List<BlockEntity>> levels = new TreeMap<>();
        for (BlockEntity be : nodes) {
            levels.computeIfAbsent(be.getBlockPos().getY(), k -> new ArrayList<>()).add(be);
        }

        for (Map.Entry<Integer, List<BlockEntity>> entry : levels.entrySet()) {
            List<BlockEntity> levelNodes = entry.getValue();
            long levelCapacity = 0;
            for (BlockEntity be : levelNodes) {
                levelCapacity += (be instanceof FluidPipeBlockEntity) ? FluidPipeBlockEntity.CAPACITY : FluidTankBlockEntity.CAPACITY;
            }

            if (remaining <= 0) {
                clearBlocks(levelNodes, level);
                continue;
            }

            // Если жидкости меньше, чем емкость текущего слоя Y — распределяем пропорционально
            if (remaining < levelCapacity) {
                double ratio = (double) remaining / levelCapacity;
                for (BlockEntity be : levelNodes) {
                    int cap = (be instanceof FluidPipeBlockEntity) ? FluidPipeBlockEntity.CAPACITY : FluidTankBlockEntity.CAPACITY;
                    updateBlockFluid(be, (int) Math.round(cap * ratio), referenceStack, level);
                }
                remaining = 0;
            } else {
                // Заполняем слой полностью
                for (BlockEntity be : levelNodes) {
                    int cap = (be instanceof FluidPipeBlockEntity) ? FluidPipeBlockEntity.CAPACITY : FluidTankBlockEntity.CAPACITY;
                    updateBlockFluid(be, cap, referenceStack, level);
                }
                remaining -= levelCapacity;
            }
        }
    }

    private static void updateBlockFluid(BlockEntity be, int amount, FluidStack ref, Level level) {
        //if (level.isClientSide) return;

        FluidStack current = (be instanceof FluidPipeBlockEntity p) ? p.getFluid() : ((FluidTankBlockEntity) be).getFluid();

        // Если количество совпадает — выходим
        if (current.getAmount() == amount && !current.isEmpty()) return;

        // ВАЖНО: Заменяем объект полностью (иммутабельность)
        FluidStack nextStack = (amount <= 0) ? FluidStack.EMPTY : ref.copyWithAmount(amount);

        if (be instanceof FluidPipeBlockEntity p) {
            p.setFluid(nextStack);
        } else if (be instanceof FluidTankBlockEntity t) {
            t.setFluid(nextStack);
        }

        // Помечаем для сохранения (метод из твоего BlockEntity)
        be.setChanged();

        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();

        // Флаг 3 (1 | 2) — обновить блок и отправить пакет.
        // Если рендер всё равно не видит, используем флаг 11 (1 | 2 | 8)
        // 8 — это BLOCK_UPDATE_FORCE_RERENDER (принудительный рендер на клиенте)
        level.sendBlockUpdated(pos, state, state, 11);
    }

    private static void clearBlocks(List<BlockEntity> nodes, Level level) {
        for (BlockEntity be : nodes) {
            // Мы передаем 0 и пустой стак (или тот же ref),
            // чтобы updateBlockFluid честно отправил пакет об обнулении.
            updateBlockFluid(be, 0, FluidStack.EMPTY, level);
        }
    }

    private static void setFluid(BlockEntity be, FluidStack stack) {
        if (be instanceof FluidPipeBlockEntity p) p.setFluid(stack);
        else if (be instanceof FluidTankBlockEntity t) t.setFluid(stack);
    }
}