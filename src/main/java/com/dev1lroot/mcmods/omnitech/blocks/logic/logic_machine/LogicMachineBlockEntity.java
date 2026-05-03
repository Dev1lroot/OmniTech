package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.LogicCableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port.GPIOPortBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.vm.LogicVM;
import com.dev1lroot.mcmods.omnitech.gui.LogicMachineMenu;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LogicMachineBlockEntity extends BaseContainerBlockEntity {

    // Button IDs for clickMenuButton
    public static final int BTN_RUN   = 0;
    public static final int BTN_STOP  = 1;
    public static final int BTN_RESET = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    private final LogicVM vm = new LogicVM();
    private boolean running = false;
    private String  compileError = null;

    // GPIO port and Display cache (refreshed every 20 ticks)
    private final Map<Integer, GPIOPortBlockEntity> gpioCache    = new HashMap<>();
    private final Map<Integer, DisplayBlockEntity>  displayCache = new HashMap<>();
    private long lastCacheRefresh = -100L;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> running ? 1 : 0;
                case 1 -> vm.halted ? 1 : 0;
                case 2 -> vm.currentLine();
                case 3 -> (!items.get(0).isEmpty()) ? 1 : 0;
                case 4 -> compileError != null ? 1 : 0;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 5; }
    };

    public LogicMachineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.LOGIC_MACHINE.get(), pos, state);
    }

    // ── Ticking ───────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            LogicMachineBlockEntity be) {
        if (!be.running || be.vm.isEmpty()) return;

        // Refresh GPIO/Display cache periodically
        if (level.getGameTime() - be.lastCacheRefresh > 20) {
            be.rebuildNetworkCache(level, pos);
            be.lastCacheRefresh = level.getGameTime();
        }

        be.vm.tick(new LogicVM.GPIOAccess() {
            @Override public int read(int portId) {
                GPIOPortBlockEntity g = be.gpioCache.get(portId);
                return g != null ? g.getInputSignal() : 0;
            }
            @Override public void write(int portId, int value) {
                GPIOPortBlockEntity g = be.gpioCache.get(portId);
                if (g != null) g.setOutputSignal(value);
            }
        }, new LogicVM.DisplayAccess() {
            @Override public void setPixel(int displayId, int x, int y, int color) {
                DisplayBlockEntity d = be.displayCache.get(displayId);
                if (d != null) d.setPixel(x, y, color);
            }
            @Override public void reset(int displayId) {
                DisplayBlockEntity d = be.displayCache.get(displayId);
                if (d != null) d.resetPixels();
            }
        });

        if (be.vm.halted) be.running = false;
        be.setChanged();
    }

    private void rebuildNetworkCache(Level level, BlockPos origin) {
        gpioCache.clear();
        displayCache.clear();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos nb = cur.relative(dir);
                if (!visited.add(nb)) continue;
                BlockState nbState = level.getBlockState(nb);
                if (nbState.getBlock() instanceof LogicCableBlock) {
                    queue.add(nb);
                } else {
                    BlockEntity be = level.getBlockEntity(nb);
                    if (be instanceof GPIOPortBlockEntity g) {
                        gpioCache.put(g.getPortId(), g);
                    } else if (be instanceof DisplayBlockEntity d) {
                        displayCache.put(d.getPortId(), d);
                    }
                }
            }
        }
    }

    // ── Button handling ───────────────────────────────────────────────────────

    public boolean handleButton(int id) {
        return switch (id) {
            case BTN_RUN   -> { loadAndRun(); yield true; }
            case BTN_STOP  -> { running = false; setChanged(); yield true; }
            case BTN_RESET -> { running = false; vm.reset(); compileError = null; setChanged(); yield true; }
            default -> false;
        };
    }

    private void loadAndRun() {
        ItemStack stack = items.get(0);
        if (stack.isEmpty() || !(stack.getItem() instanceof MicrocontrollerItem)) return;
        String prog = stack.getOrDefault(OmniTechDataComponents.PROGRAM.get(), "");
        compileError = vm.compile(prog);
        if (compileError == null && !vm.isEmpty()) {
            running = true;
        }
        setChanged();
    }

    // ── Item slot ─────────────────────────────────────────────────────────────

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        // Stop and reset when microcontroller is swapped out
        running = false;
        vm.reset();
        compileError = null;
        setChanged();
    }

    // ── BaseContainerBlockEntity ──────────────────────────────────────────────

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.omnitech.logic_machine");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> items) { this.items = items; }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return new LogicMachineMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    public int getContainerSize() { return 1; }

    @Override
    public boolean stillValid(Player player) { return true; }

    // ── Serialization ────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("Running", running);
        vm.saveState(out);
        // Re-compile program on load: don't persist compiled form
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        running = in.getBooleanOr("Running", false);
        vm.loadState(in);
        // Re-compile if we have a microcontroller
        ItemStack stack = items.get(0);
        if (!stack.isEmpty() && stack.getItem() instanceof MicrocontrollerItem) {
            String prog = stack.getOrDefault(OmniTechDataComponents.PROGRAM.get(), "");
            compileError = vm.compile(prog);
            // Restore pc/sleep after compile (compile resets them, so reload)
            vm.loadState(in);
        }
    }
}
