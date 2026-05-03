package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.LogicCableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot.ExpansionSlotBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive.FloppyDriveBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port.GPIOPortBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.vm.LogicVM;
import com.dev1lroot.mcmods.omnitech.gui.LogicMachineMenu;
import com.dev1lroot.mcmods.omnitech.items.FloppyDiskItem;
import com.dev1lroot.mcmods.omnitech.items.MicrocontrollerItem;
import com.dev1lroot.mcmods.omnitech.items.RamCardItem;
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

import java.nio.ByteBuffer;
import java.util.*;

public class LogicMachineBlockEntity extends BaseContainerBlockEntity {

    public static final int BTN_RUN   = 0;
    public static final int BTN_STOP  = 1;
    public static final int BTN_RESET = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    private final LogicVM vm = new LogicVM();
    private boolean running = false;
    private String  compileError = null;

    private final Map<Integer, GPIOPortBlockEntity>  gpioCache    = new HashMap<>();
    private final Map<Integer, DisplayBlockEntity>   displayCache = new HashMap<>();
    private final Map<Integer, FloppyDriveBlockEntity> floppyCache = new HashMap<>();
    private final List<ExpansionSlotBlockEntity>     expansionSlotCache = new ArrayList<>();
    private long lastCacheRefresh = -100L;

    // Flat RAM address space built from connected RAM cards
    private byte[] ramBuffer = new byte[0];
    private record RamSlot(ExpansionSlotBlockEntity be, int slotIndex, int offset, int capacity) {}
    private List<RamSlot>   ramSlots       = new ArrayList<>();
    private boolean[]       ramSlotsDirty  = new boolean[0];

    private int tickAccum = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> running ? 1 : 0;
                case 1 -> vm.halted ? 1 : 0;
                case 2 -> vm.currentLine();
                case 3 -> (!items.get(0).isEmpty()) ? 1 : 0;
                case 4 -> compileError != null ? 1 : 0;
                case 5 -> Math.min(ramBuffer.length, 32767);
                case 6 -> gpioCache.size();
                case 7 -> floppyCache.size();
                case 8 -> ramSlots.size();
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 9; }
    };

    public LogicMachineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.LOGIC_MACHINE.get(), pos, state);
    }

    // ── Ticking ───────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            LogicMachineBlockEntity be) {
        if (!be.running || be.vm.isEmpty()) return;

        if (level.getGameTime() - be.lastCacheRefresh > 20) {
            be.flushDirtyRamSlots();
            be.rebuildNetworkCache(level, pos);
            be.lastCacheRefresh = level.getGameTime();
        }

        if (be.vm.sleepTicks > 0) {
            be.vm.sleepTicks--;
            return;
        }

        int speed = Math.clamp(
                be.items.get(0).getOrDefault(OmniTechDataComponents.MCU_SPEED.get(), 20),
                1, 1_000_000);
        be.tickAccum += speed;
        int instrsThisTick = be.tickAccum / 20;
        be.tickAccum %= 20;

        LogicVM.GPIOAccess gpio = new LogicVM.GPIOAccess() {
            @Override public int read(int portId) {
                GPIOPortBlockEntity g = be.gpioCache.get(portId);
                return g != null ? g.getInputSignal() : 0;
            }
            @Override public void write(int portId, int value) {
                GPIOPortBlockEntity g = be.gpioCache.get(portId);
                if (g != null) g.setOutputSignal(value);
            }
        };
        LogicVM.DisplayAccess display = new LogicVM.DisplayAccess() {
            @Override public void setPixel(int displayId, int x, int y, int color) {
                DisplayBlockEntity d = be.displayCache.get(displayId);
                if (d != null) d.setPixel(x, y, color);
            }
            @Override public void reset(int displayId) {
                DisplayBlockEntity d = be.displayCache.get(displayId);
                if (d != null) d.resetPixels();
            }
        };
        LogicVM.RAMAccess ram = new LogicVM.RAMAccess() {
            @Override public int read(int addr) {
                if (addr < 0 || addr >= be.ramBuffer.length) return 0;
                return be.ramBuffer[addr] & 0xFF;
            }
            @Override public void write(int addr, int value) {
                if (addr < 0 || addr >= be.ramBuffer.length) return;
                be.ramBuffer[addr] = (byte)(value & 0xFF);
                for (int i = 0; i < be.ramSlots.size(); i++) {
                    RamSlot rs = be.ramSlots.get(i);
                    if (addr >= rs.offset() && addr < rs.offset() + rs.capacity()) {
                        be.ramSlotsDirty[i] = true;
                        break;
                    }
                }
            }
            @Override public int capacity() { return be.ramBuffer.length; }
        };
        LogicVM.FloppyAccess floppy = (driveId, sector, dstAddr) -> {
            FloppyDriveBlockEntity fd = be.floppyCache.get(driveId);
            if (fd == null) return false;
            ItemStack disk = fd.getDisk();
            if (disk.isEmpty() || !(disk.getItem() instanceof FloppyDiskItem)) return false;
            OmniTechDataComponents.ByteData floppyData = disk.get(OmniTechDataComponents.FLOPPY_DATA.get());
            if (floppyData == null) return false;
            byte[] data = floppyData.data();
            int srcOffset = sector * FloppyDiskItem.SECTOR_SIZE;
            if (srcOffset < 0 || srcOffset >= data.length) return false;
            int copyLen = Math.min(FloppyDiskItem.SECTOR_SIZE,
                    Math.min(data.length - srcOffset, be.ramBuffer.length - dstAddr));
            if (copyLen <= 0) return false;
            System.arraycopy(data, srcOffset, be.ramBuffer, dstAddr, copyLen);
            // Mark dirty RAM slots in the destination range
            for (int i = 0; i < be.ramSlots.size(); i++) {
                RamSlot rs = be.ramSlots.get(i);
                int end = rs.offset() + rs.capacity();
                if (dstAddr < end && dstAddr + copyLen > rs.offset()) {
                    be.ramSlotsDirty[i] = true;
                }
            }
            return true;
        };

        for (int i = 0; i < instrsThisTick && !be.vm.halted; i++) {
            be.vm.tick(gpio, display, ram, floppy);
            if (be.vm.sleepTicks > 0) break;
        }

        if (be.vm.halted) be.running = false;
        be.setChanged();
    }

    // ── Network cache ─────────────────────────────────────────────────────────

    private void rebuildNetworkCache(Level level, BlockPos origin) {
        gpioCache.clear();
        displayCache.clear();
        floppyCache.clear();
        expansionSlotCache.clear();

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
                    } else if (be instanceof FloppyDriveBlockEntity fd) {
                        floppyCache.put(fd.getDriveId(), fd);
                    } else if (be instanceof ExpansionSlotBlockEntity es) {
                        expansionSlotCache.add(es);
                    }
                }
            }
        }

        buildRamAddressSpace();
    }

    private void buildRamAddressSpace() {
        ramSlots = new ArrayList<>();
        int totalBytes = 0;
        for (ExpansionSlotBlockEntity es : expansionSlotCache) {
            for (int i = 0; i < ExpansionSlotBlockEntity.SLOT_COUNT; i++) {
                ItemStack stack = es.getItem(i);
                if (!stack.isEmpty() && stack.getItem() instanceof RamCardItem) {
                    int cap = stack.getOrDefault(OmniTechDataComponents.RAM_CAPACITY.get(), 1024);
                    ramSlots.add(new RamSlot(es, i, totalBytes, cap));
                    totalBytes += cap;
                }
            }
        }

        byte[] newBuffer = new byte[totalBytes];
        for (RamSlot rs : ramSlots) {
            OmniTechDataComponents.ByteData ramData = rs.be().getItem(rs.slotIndex()).get(OmniTechDataComponents.RAM_DATA.get());
            if (ramData != null) {
                byte[] data = ramData.data();
                int len = Math.min(data.length, rs.capacity());
                System.arraycopy(data, 0, newBuffer, rs.offset(), len);
            }
        }
        ramBuffer = newBuffer;
        ramSlotsDirty = new boolean[ramSlots.size()];
    }

    private void flushDirtyRamSlots() {
        for (int i = 0; i < ramSlots.size(); i++) {
            if (!ramSlotsDirty[i]) continue;
            RamSlot rs = ramSlots.get(i);
            byte[] data = Arrays.copyOfRange(ramBuffer, rs.offset(), rs.offset() + rs.capacity());
            ItemStack stack = rs.be().getItem(rs.slotIndex()).copy();
            stack.set(OmniTechDataComponents.RAM_DATA.get(), new OmniTechDataComponents.ByteData(data));
            rs.be().setItem(rs.slotIndex(), stack);
            rs.be().setChanged();
            ramSlotsDirty[i] = false;
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
        int regCount = Math.clamp(stack.getOrDefault(OmniTechDataComponents.MCU_REGISTERS.get(), 4), 1, 256);
        vm.setRegisterCount(regCount);
        String prog = stack.getOrDefault(OmniTechDataComponents.PROGRAM.get(), "");
        compileError = vm.compile(prog);
        if (compileError == null && !vm.isEmpty()) running = true;
        setChanged();
    }

    // ── Item slot ─────────────────────────────────────────────────────────────

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
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
        // Flush RAM to items before save so RAM card items are portable
        flushDirtyRamSlots();
        // Also persist the flat buffer in block entity NBT for fast reload
        if (ramBuffer.length > 0) {
            out.store("RAMBuffer", OmniTechDataComponents.BYTE_ARRAY_CODEC, new OmniTechDataComponents.ByteData(ramBuffer));
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        running = in.getBooleanOr("Running", false);
        ItemStack stack = items.get(0);
        if (!stack.isEmpty() && stack.getItem() instanceof MicrocontrollerItem) {
            int regCount = Math.clamp(
                    stack.getOrDefault(OmniTechDataComponents.MCU_REGISTERS.get(), 4), 1, 256);
            vm.setRegisterCount(regCount);
            String prog = stack.getOrDefault(OmniTechDataComponents.PROGRAM.get(), "");
            compileError = vm.compile(prog);
        }
        vm.loadState(in);
        // Restore RAM buffer if stored (expansion slots may not be loaded yet)
        in.read("RAMBuffer", OmniTechDataComponents.BYTE_ARRAY_CODEC).ifPresent(b -> { if (b.data().length > 0) ramBuffer = b.data(); });
    }

    // ── Accessors for System tab ──────────────────────────────────────────────

    /** Sorted list of connected GPIO port IDs for the System tab. */
    public int[] getGpioPortIds() {
        return gpioCache.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    /** Sorted list of connected Floppy Drive IDs for the System tab. */
    public int[] getFloppyDriveIds() {
        return floppyCache.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
    }
}
