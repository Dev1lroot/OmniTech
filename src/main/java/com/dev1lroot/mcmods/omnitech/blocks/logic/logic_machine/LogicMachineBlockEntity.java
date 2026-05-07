package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine;

import com.dev1lroot.mcmods.omnitech.BinStorage;
import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.LogicCableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot.ExpansionSlotBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive.FloppyDriveBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port.GPIOPortBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.vm.DtbBuilder;
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
import net.minecraft.world.ContainerHelper;
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
import java.util.UUID;

public class LogicMachineBlockEntity extends BaseContainerBlockEntity {

    public static final int BTN_RUN   = 0;
    public static final int BTN_STOP  = 1;
    public static final int BTN_RESET = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    private final LogicVM vm = new LogicVM();
    private boolean running = false;
    private String  compileError = null; // kept for GUI compat; always null now
    private byte[]  loadedBinary       = null; // re-applied after RAM cache rebuild
    private int     loadedBinaryOffset = 0;    // text_offset from RISC-V Image header
    private int     loadedDtbAddr      = 0;    // physical address of DTB in RAM
    private UUID    loadedBinaryId     = null; // UUID of the binary file in BinStorage

    // Console output buffer — flushed to MCU item CONSOLE_OUTPUT each tick when dirty
    private final StringBuilder consoleBuf = new StringBuilder(65536);
    private boolean consoleDirty = false;

    // Framebuffer pixels (320×240 XRGB, LE: bytes [B,G,R,A]) — blitted to display[0] when dirty
    private final byte[] fbPixels = new byte[320 * 240 * 4];
    private boolean fbDirty = false;

    private final Map<Integer, GPIOPortBlockEntity>  gpioCache    = new HashMap<>();
    private final Map<Integer, DisplayBlockEntity>   displayCache = new HashMap<>();
    private final Map<Integer, FloppyDriveBlockEntity> floppyCache = new HashMap<>();
    // Cached floppy contents keyed by drive ID — populated during network cache rebuild.
    // Avoids a file-read on every sector access (called thousands of times per tick).
    private final Map<Integer, byte[]> floppyDataCache = new HashMap<>();
    private final List<ExpansionSlotBlockEntity>     expansionSlotCache = new ArrayList<>();
    private long lastCacheRefresh = -100L;

    // Flat RAM address space built from connected RAM cards
    private byte[] ramBuffer = new byte[0];
    private record RamSlot(ExpansionSlotBlockEntity be, int slotIndex, int offset, int capacity) {}
    private List<RamSlot>   ramSlots       = new ArrayList<>();
    private boolean[]       ramSlotsDirty  = new boolean[0];
    // Cache of UUID → RAM bytes so buildRamAddressSpace() doesn't re-read files every tick.
    private final Map<UUID, byte[]> ramFileCache = new HashMap<>();

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

    @Override
    public void onLoad() {
        super.onLoad();
        // level is now set — safe to read from BinStorage
        if (loadedBinaryId != null && level != null && !level.isClientSide()) {
            byte[] data = BinStorage.read(level.getServer(), loadedBinaryId);
            if (data != null && data.length > 0) loadedBinary = data;
        }
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

        // Advance CLINT timer (50 000 mtime ticks per game tick = 1 MHz timebase at 20 TPS)
        be.vm.advanceClock();

        // Wire console putchar → consoleBuf
        be.vm.console = ch -> {
            if (be.consoleBuf.length() >= 131_072) {
                // Trim oldest half to cap memory usage
                be.consoleBuf.delete(0, 65_536);
            }
            be.consoleBuf.appendCodePoint(ch);
            be.consoleDirty = true;
        };

        // Wire framebuffer → fbPixels array
        be.vm.framebuffer = new LogicVM.FramebufferAccess() {
            @Override public int  fbRdByte(int off) { return be.fbPixels[off] & 0xFF; }
            @Override public void fbWrByte(int off, int val) {
                be.fbPixels[off] = (byte)(val & 0xFF);
                be.fbDirty = true;
            }
        };

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
            @Override public int getWidth(int displayId) {
                DisplayBlockEntity d = be.displayCache.get(displayId);
                return d != null ? d.getDisplayWidth() : 0;
            }
            @Override public int getHeight(int displayId) {
                DisplayBlockEntity d = be.displayCache.get(displayId);
                return d != null ? d.getDisplayHeight() : 0;
            }
            @Override public void drawLine(int displayId, int x1, int y1, int x2, int y2, int color) {
                DisplayBlockEntity d = be.displayCache.get(displayId);
                if (d != null) d.drawLine(x1, y1, x2, y2, color);
            }
            @Override public void fillRect(int displayId, int x, int y, int w, int h, int color) {
                DisplayBlockEntity d = be.displayCache.get(displayId);
                if (d != null) d.fillRect(x, y, w, h, color);
            }
            @Override public void blit(int displayId, int x, int y, int w, int h, int[] pixels) {
                DisplayBlockEntity d = be.displayCache.get(displayId);
                if (d != null) d.blit(x, y, w, h, pixels);
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
            byte[] data = be.floppyDataCache.get(driveId);
            if (data == null) return false;
            int srcOffset = sector * FloppyDiskItem.SECTOR_SIZE;
            if (srcOffset < 0 || srcOffset >= data.length) return false;
            int copyLen = Math.min(FloppyDiskItem.SECTOR_SIZE,
                    Math.min(data.length - srcOffset, be.ramBuffer.length - dstAddr));
            if (copyLen <= 0) return false;
            System.arraycopy(data, srcOffset, be.ramBuffer, dstAddr, copyLen);
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

        // Flush console text to MCU item so the client can display it
        if (be.consoleDirty) {
            ItemStack mcu = be.items.get(0);
            if (!mcu.isEmpty()) {
                mcu.set(OmniTechDataComponents.CONSOLE_OUTPUT.get(), be.consoleBuf.toString());
            }
            be.consoleDirty = false;
        }

        // Blit framebuffer to the display connected on port 0 (if any)
        if (be.fbDirty) {
            DisplayBlockEntity disp = be.displayCache.get(0);
            if (disp != null) {
                int dw = disp.getDisplayWidth(), dh = disp.getDisplayHeight();
                int bw = Math.min(dw, 320), bh = Math.min(dh, 240);
                int[] px = new int[bw * bh];
                for (int row = 0; row < bh; row++) {
                    for (int col = 0; col < bw; col++) {
                        int off = (row * 320 + col) * 4;
                        int b = be.fbPixels[off]     & 0xFF;
                        int g = be.fbPixels[off + 1] & 0xFF;
                        int r = be.fbPixels[off + 2] & 0xFF;
                        px[row * bw + col] = (r << 16) | (g << 8) | b;
                    }
                }
                disp.blit(0, 0, bw, bh, px);
            }
            be.fbDirty = false;
        }

        be.setChanged();
    }

    // ── Network cache ─────────────────────────────────────────────────────────

    private void rebuildNetworkCache(Level level, BlockPos origin) {
        gpioCache.clear();
        displayCache.clear();
        floppyCache.clear();
        floppyDataCache.clear();
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
                        // Any block in a cluster (even a slave) registers the master
                        DisplayBlockEntity master = d.isMaster() ? d : d.getMasterEntity(level);
                        if (master != null) displayCache.put(master.getPortId(), master);
                    } else if (be instanceof FloppyDriveBlockEntity fd) {
                        floppyCache.put(fd.getDriveId(), fd);
                    } else if (be instanceof ExpansionSlotBlockEntity es) {
                        expansionSlotCache.add(es);
                    }
                }
            }
        }

        // Cache floppy contents so the sector-read lambda doesn't hit disk every access
        if (!level.isClientSide()) {
            for (Map.Entry<Integer, FloppyDriveBlockEntity> e : floppyCache.entrySet()) {
                ItemStack disk = e.getValue().getDisk();
                if (disk.isEmpty() || !(disk.getItem() instanceof FloppyDiskItem)) continue;
                UUID fid = disk.get(OmniTechDataComponents.FLOPPY_DATA.get());
                if (fid == null) continue;
                byte[] data = BinStorage.read(level.getServer(), fid);
                if (data != null) floppyDataCache.put(e.getKey(), data);
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
                    int addrStart = totalBytes;
                    int addrEnd   = totalBytes + cap - 1;
                    ramSlots.add(new RamSlot(es, i, totalBytes, cap));
                    totalBytes += cap;
                    // Stamp address range onto the card if it changed
                    Integer curStart = stack.get(OmniTechDataComponents.RAM_ADDR_START.get());
                    Integer curEnd   = stack.get(OmniTechDataComponents.RAM_ADDR_END.get());
                    if (!Integer.valueOf(addrStart).equals(curStart) || !Integer.valueOf(addrEnd).equals(curEnd)) {
                        stack.set(OmniTechDataComponents.RAM_ADDR_START.get(), addrStart);
                        stack.set(OmniTechDataComponents.RAM_ADDR_END.get(), addrEnd);
                        es.setItem(i, stack);
                        es.setChanged();
                    }
                }
            }
        }

        byte[] newBuffer = new byte[totalBytes];
        if (level != null && !level.isClientSide()) {
            for (RamSlot rs : ramSlots) {
                UUID ramId = rs.be().getItem(rs.slotIndex()).get(OmniTechDataComponents.RAM_DATA.get());
                if (ramId == null) continue;
                byte[] data = ramFileCache.computeIfAbsent(ramId,
                        id -> BinStorage.read(level.getServer(), id));
                if (data == null) continue;
                int len = Math.min(data.length, rs.capacity());
                System.arraycopy(data, 0, newBuffer, rs.offset(), len);
            }
        }
        ramBuffer = newBuffer;
        ramSlotsDirty = new boolean[ramSlots.size()];
        // NOTE: do NOT re-apply loadedBinary here.
        // flushDirtyRamSlots() runs before every rebuild and writes the live kernel state
        // (page tables, heap, etc.) to BinStorage.  Reading it back gives the correct state.
        // Re-applying the original binary would overwrite those runtime modifications and
        // corrupt the running kernel.
    }

    private void flushDirtyRamSlots() {
        if (level == null || level.isClientSide()) return;
        for (int i = 0; i < ramSlots.size(); i++) {
            if (!ramSlotsDirty[i]) continue;
            RamSlot rs = ramSlots.get(i);
            byte[] data = Arrays.copyOfRange(ramBuffer, rs.offset(), rs.offset() + rs.capacity());

            ItemStack stack = rs.be().getItem(rs.slotIndex());
            UUID ramId = stack.get(OmniTechDataComponents.RAM_DATA.get());
            if (ramId == null) {
                // First write — assign a UUID and stamp it on the item
                ramId = BinStorage.allocate();
                ItemStack copy = stack.copy();
                copy.set(OmniTechDataComponents.RAM_DATA.get(), ramId);
                rs.be().setItem(rs.slotIndex(), copy);
                rs.be().setChanged();
            }

            BinStorage.write(level.getServer(), ramId, data);
            ramFileCache.put(ramId, data); // keep in-memory cache current
            ramSlotsDirty[i] = false;
        }
    }

    // ── Button handling ───────────────────────────────────────────────────────

    public boolean handleButton(int id) {
        return switch (id) {
            case BTN_RUN   -> {
                // Ensure RAM/peripheral cache is populated before loading the binary
                if (level != null && !level.isClientSide()) {
                    rebuildNetworkCache(level, getBlockPos());
                    lastCacheRefresh = level.getGameTime();
                }
                loadAndRun();
                yield true;
            }
            case BTN_STOP  -> { running = false; setChanged(); yield true; }
            case BTN_RESET -> {
                running = false;
                compileError = null;
                consoleBuf.setLength(0);
                consoleDirty = false;
                Arrays.fill(fbPixels, (byte) 0);
                fbDirty = false;
                if (loadedBinary != null &&
                        loadedBinaryOffset + loadedBinary.length <= ramBuffer.length) {
                    System.arraycopy(loadedBinary, 0, ramBuffer, loadedBinaryOffset, loadedBinary.length);
                    // Re-apply DTB so the VM sees it after reset
                    if (loadedDtbAddr != 0) {
                        byte[] dtb = DtbBuilder.build(ramBuffer.length);
                        if (loadedDtbAddr + dtb.length <= ramBuffer.length) {
                            System.arraycopy(dtb, 0, ramBuffer, loadedDtbAddr, dtb.length);
                        }
                    }
                    // Mark all slots dirty so the clean binary is flushed to BinStorage
                    Arrays.fill(ramSlotsDirty, true);
                    vm.reset();
                    vm.loaded   = true;
                    vm.pc       = loadedBinaryOffset;
                    vm.regs[10] = 0;
                    vm.regs[11] = loadedDtbAddr;
                } else {
                    loadedBinary       = null;
                    loadedBinaryOffset = 0;
                    loadedDtbAddr      = 0;
                    vm.unload();
                }
                setChanged();
                yield true;
            }
            default -> false;
        };
    }

    private void loadAndRun() {
        if (level == null || level.isClientSide()) return;
        ItemStack stack = items.get(0);
        if (stack.isEmpty() || !(stack.getItem() instanceof MicrocontrollerItem)) return;

        UUID binId = stack.get(OmniTechDataComponents.PROGRAM_BINARY.get());
        if (binId == null) {
            compileError = "No binary on MCU — use /loadbin";
            setChanged();
            return;
        }
        byte[] binary = BinStorage.read(level.getServer(), binId);
        if (binary == null || binary.length == 0) {
            compileError = "Binary file missing — re-run /loadbin";
            setChanged();
            return;
        }

        loadedBinaryId = binId;

        // Parse RISC-V Linux Image header to find text_offset.
        // Header: [0..7] code, [8..15] text_offset (LE u64), [48..55] magic "RISCV\0\0\0"
        int textOffset = 0;
        if (binary.length >= 64 &&
                binary[48] == 'R' && binary[49] == 'I' && binary[50] == 'S' &&
                binary[51] == 'C' && binary[52] == 'V') {
            textOffset = (binary[8] & 0xFF)
                    | ((binary[9]  & 0xFF) << 8)
                    | ((binary[10] & 0xFF) << 16)
                    | ((binary[11] & 0xFF) << 24);
        }

        if (textOffset + binary.length > ramBuffer.length) {
            compileError = "Not enough RAM: need " + (textOffset + binary.length)
                    + " bytes, have " + ramBuffer.length;
            setChanged();
            return;
        }

        loadedBinary       = binary;
        loadedBinaryOffset = textOffset;
        System.arraycopy(binary, 0, ramBuffer, textOffset, binary.length);

        // Build DTB and place 4 KB-aligned after the kernel image
        byte[] dtb  = DtbBuilder.build(ramBuffer.length);
        int dtbAddr = (textOffset + binary.length + 0xFFF) & ~0xFFF;
        if (dtbAddr + dtb.length <= ramBuffer.length) {
            System.arraycopy(dtb, 0, ramBuffer, dtbAddr, dtb.length);
        } else {
            dtbAddr = 0; // fallback: no DTB (bare-metal programs don't need it)
        }
        loadedDtbAddr = dtbAddr;

        // Mark all RAM slots covering the written region as dirty
        int written = Math.max(textOffset + binary.length, dtbAddr == 0 ? 0 : dtbAddr + dtb.length);
        for (int i = 0; i < ramSlots.size(); i++) {
            RamSlot rs = ramSlots.get(i);
            if (rs.offset() < written) ramSlotsDirty[i] = true;
        }

        compileError = null;
        // Clear console buffer for new run
        consoleBuf.setLength(0);
        consoleDirty = false;
        Arrays.fill(fbPixels, (byte) 0);
        fbDirty = false;

        vm.reset();
        vm.loaded = true;
        vm.pc        = textOffset; // entry point per RISC-V boot protocol
        vm.regs[10]  = 0;          // a0 = hart ID
        vm.regs[11]  = dtbAddr;    // a1 = DTB physical address
        running = true;
        setChanged();
    }

    // ── Item slot ─────────────────────────────────────────────────────────────

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        running = false;
        loadedBinary = null;
        loadedBinaryId = null;
        vm.unload();
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
        ContainerHelper.saveAllItems(out, items);
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
        ContainerHelper.loadAllItems(in, items);
        running = in.getBooleanOr("Running", false);
        vm.loadState(in);
        // Restore the flat RAM buffer (includes any loaded binary)
        in.read("RAMBuffer", OmniTechDataComponents.BYTE_ARRAY_CODEC)
          .ifPresent(b -> { if (b.data().length > 0) ramBuffer = b.data(); });
        // Remember the binary UUID so onLoad() can read the file once level is available
        ItemStack stack = items.get(0);
        if (!stack.isEmpty() && stack.getItem() instanceof MicrocontrollerItem) {
            UUID id = stack.get(OmniTechDataComponents.PROGRAM_BINARY.get());
            if (id != null) loadedBinaryId = id;
        }
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
