package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.LogicMachineMenu;
import com.dev1lroot.mcmods.omnitech.blocks.logic.LogicCableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.expansion_slot.ExpansionSlotBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive.FloppyDriveBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port.GPIOPortBlockEntity;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm.OmniTechBusDevice;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm.SednaVM;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm.TerminalDisplay;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm.VMTerminal;
import com.dev1lroot.mcmods.omnitech.network.TerminalOutputPacket;
import com.dev1lroot.mcmods.omnitech.network.TerminalSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Arrays;

public class LogicMachineBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogicMachineBlockEntity.class);

    public static final int BTN_RUN   = 0;
    public static final int BTN_STOP  = 1;
    public static final int BTN_RESET = 2;

    // ── VM ────────────────────────────────────────────────────────────────────

    @Nullable private SednaVM vm = null;
    private boolean shouldBeRunning = false;

    /** Stable identity for this machine's save directory under VMs/. */
    private UUID vmId = UUID.randomUUID();

    /** Set in loadAdditional(); cleared once setLevel() fires and we restore from disk. */
    private boolean pendingLoad = false;

    // Client-side terminal mirror
    private final VMTerminal clientTerminal = new VMTerminal();

    // ── Peripheral cache ──────────────────────────────────────────────────────

    private final Map<Integer, GPIOPortBlockEntity>    gpioCache      = new HashMap<>();
    private final Map<Integer, DisplayBlockEntity>     displayCache   = new HashMap<>();
    private final Map<Integer, FloppyDriveBlockEntity> floppyCache    = new HashMap<>();
    // Tracks which ByteData object is currently loaded per driveId — object identity detects disk swaps
    private final Map<Integer, OmniTechDataComponents.ByteData> floppySnapshot = new HashMap<>();
    private long lastCacheRefresh = -100L;

    // ── Viewers (players with GUI open) ───────────────────────────────────────

    private final Set<ServerPlayer> viewers = new HashSet<>();

    // ── Pending terminal output ──────────────────────────────────────────────

    private final ConcurrentLinkedQueue<byte[]> pendingOutput = new ConcurrentLinkedQueue<>();

    // -----------------------------------------------------------------------

    public LogicMachineBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.LOGIC_MACHINE.get(), pos, state);
    }

    // ── Server tick ──────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   LogicMachineBlockEntity be) {
        if (level.isClientSide()) return;

        if (level.getGameTime() - be.lastCacheRefresh > 20) {
            be.rebuildNetworkCache(level, pos);
            be.syncBusDevice();
            be.syncFloppyDrives();
            be.lastCacheRefresh = level.getGameTime();
        }

        if (level.getGameTime() % 100 == 0) {
            be.floppyWriteBack();
        }

        if (be.vm == null) return;

        be.vm.getBusDevice().pollGpioChanges();
        be.vm.getBusDevice().flushPendingDisplays();
        be.vm.tick();

        byte[] newOut = be.vm.drainOutput();
        if (newOut.length > 0) {
            LOGGER.info("[tick] drainOutput returned {} bytes, viewers={}", newOut.length, be.viewers.size());
            be.enqueueTerminalOutput(newOut);
        }

        // Render terminal text to the display block at portId=0 whenever there is new output.
        // Other portIds are unaffected — they remain purely pixel-art displays.
        VMTerminal term = be.vm.getTerminal();
        if (term.isDirty()) {
            DisplayBlockEntity display = be.displayCache.get(0);
            if (display != null) TerminalDisplay.render(term, display);
        }

        byte[] chunk;
        while ((chunk = be.pendingOutput.poll()) != null) {
            if (!be.viewers.isEmpty()) {
                LOGGER.info("[tick] sending {} bytes to {} viewer(s)", chunk.length, be.viewers.size());
                TerminalOutputPacket pkt = new TerminalOutputPacket(pos, chunk);
                for (ServerPlayer viewer : new ArrayList<>(be.viewers)) {
                    PacketDistributor.sendToPlayer(viewer, pkt);
                }
            } else {
                LOGGER.warn("[tick] output discarded — no viewers! {} bytes lost", chunk.length);
            }
        }

        if (be.shouldBeRunning && be.vm.isPoweredOff()) {
            be.shouldBeRunning = false;
            be.setChanged();
        }
    }

    // ── GPIO / peripheral wiring ─────────────────────────────────────────────

    private void syncBusDevice() {
        if (vm == null) return;
        var busDevice = vm.getBusDevice();
        busDevice.clearPorts();
        busDevice.clearDisplaySlots();

        int idx = 0;
        for (Map.Entry<Integer, GPIOPortBlockEntity> e : new TreeMap<>(gpioCache).entrySet()) {
            GPIOPortBlockEntity g = e.getValue();
            busDevice.setGpioPort(idx, g::getInputSignal, g::setOutputSignal);
            idx++;
        }

        for (Map.Entry<Integer, DisplayBlockEntity> e : new TreeMap<>(displayCache).entrySet()) {
            int portId = e.getKey();
            if (portId <= 0 || portId > OmniTechBusDevice.MAX_DISP_SLOTS) continue;
            DisplayBlockEntity display = e.getValue();
            if (!display.isMaster()) continue;
            int slot = portId - 1;
            int w = Math.min(display.getDisplayWidth(),  OmniTechBusDevice.MAX_DISP_W);
            int h = Math.min(display.getDisplayHeight(), OmniTechBusDevice.MAX_DISP_H);
            busDevice.setDisplay(slot, w, h,
                (pixels, pw, ph) -> display.blit(0, 0, pw, ph, pixels),
                display::resetPixels);
        }
    }

    // ── Floppy drive wiring ───────────────────────────────────────────────────

    /**
     * Called every 20 ticks alongside the cache rebuild.
     *
     * Change detection uses two independent checks:
     *   1. Whether the slot has a disk at all (ItemStack.isEmpty).
     *   2. Object identity of ByteData — a different reference means the disk was swapped.
     *
     * A blank disk (no FLOPPY_DATA component yet) has currentData == null but the slot
     * is NOT empty, so it is inserted with a freshly zeroed 1.44 MB buffer rather than
     * being treated as an ejection.
     */
    private void syncFloppyDrives() {
        if (vm == null) return;

        Set<Integer> activeDriveIds = new HashSet<>();

        for (Map.Entry<Integer, FloppyDriveBlockEntity> e : floppyCache.entrySet()) {
            int driveId = e.getKey();
            if (driveId < 0 || driveId >= SednaVM.MAX_FLOPPY_DRIVES) continue;
            FloppyDriveBlockEntity drive = e.getValue();
            ItemStack disk = drive.getDisk();
            activeDriveIds.add(driveId);

            if (disk.isEmpty()) {
                if (floppySnapshot.containsKey(driveId)) {
                    vm.ejectFloppy(driveId);
                    floppySnapshot.remove(driveId);
                }
                continue;
            }

            // Disk is physically present (may be blank — no FLOPPY_DATA yet)
            OmniTechDataComponents.ByteData currentData = disk.get(OmniTechDataComponents.FLOPPY_DATA.get());
            boolean alreadyInserted = floppySnapshot.containsKey(driveId);
            OmniTechDataComponents.ByteData prevData = floppySnapshot.get(driveId); // null if blank or not inserted

            if (alreadyInserted && currentData == prevData) continue; // no change

            // Insert or swap: blank disk (null) gets a zeroed buffer in setFloppyDisk
            byte[] bytes = currentData != null ? currentData.data() : new byte[0];
            boolean readOnly = disk.getOrDefault(OmniTechDataComponents.READ_ONLY.get(), 0) == 1;
            vm.setFloppyDisk(driveId, bytes, readOnly);
            floppySnapshot.put(driveId, currentData); // null OK — marks "blank disk inserted"
        }

        // Eject drives that have left the cable network
        Iterator<Map.Entry<Integer, OmniTechDataComponents.ByteData>> it = floppySnapshot.entrySet().iterator();
        while (it.hasNext()) {
            int driveId = it.next().getKey();
            if (!activeDriveIds.contains(driveId)) {
                vm.ejectFloppy(driveId);
                it.remove();
            }
        }
    }

    /**
     * Copies the VM's live floppy backing data back into each ItemStack so writes
     * survive after the world is saved. Also updates floppySnapshot to the new
     * ByteData reference so the next syncFloppyDrives() doesn't mistake the
     * write-back for a disk swap.
     */
    private void floppyWriteBack() {
        if (vm == null) return;
        for (Map.Entry<Integer, FloppyDriveBlockEntity> e : floppyCache.entrySet()) {
            int driveId = e.getKey();
            if (driveId < 0 || driveId >= SednaVM.MAX_FLOPPY_DRIVES) continue;
            byte[] live = vm.getFloppyData(driveId); // null if empty or read-only
            if (live == null) continue;
            FloppyDriveBlockEntity drive = e.getValue();
            ItemStack disk = drive.getDisk();
            if (disk.isEmpty()) continue;
            OmniTechDataComponents.ByteData saved =
                    new OmniTechDataComponents.ByteData(Arrays.copyOf(live, live.length));
            disk.set(OmniTechDataComponents.FLOPPY_DATA.get(), saved);
            drive.setChanged();
            floppySnapshot.put(driveId, saved); // keep snapshot in sync to avoid spurious re-insert
        }
    }

    // ── Network cache ────────────────────────────────────────────────────────

    private void rebuildNetworkCache(Level level, BlockPos origin) {
        gpioCache.clear();
        displayCache.clear();
        floppyCache.clear();

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
                        DisplayBlockEntity master = d.isMaster() ? d : d.getMasterEntity(level);
                        if (master != null) displayCache.put(master.getPortId(), master);
                    } else if (be instanceof FloppyDriveBlockEntity fd) {
                        floppyCache.put(fd.getDriveId(), fd);
                    }
                }
            }
        }
    }

    // ── Button handling ───────────────────────────────────────────────────────

    public boolean handleButton(int id) {
        return switch (id) {
            case BTN_RUN   -> { startVM(); yield true; }
            case BTN_STOP  -> { stopVM(); yield true; }
            case BTN_RESET -> { resetVM(); yield true; }
            default -> false;
        };
    }

    private void startVM() {
        if (vm != null && vm.isRunning()) return;
        try {
            if (vm == null) vm = new SednaVM();
            vm.start();
            shouldBeRunning = true;
            setChanged();
        } catch (IOException e) {
            LOGGER.error("Failed to start SednaVM", e);
        }
    }

    private void stopVM() {
        if (vm != null) vm.stop();
        shouldBeRunning = false;
        setChanged();
        clearAllDisplays();
    }

    private void resetVM() {
        if (vm != null) {
            vm.stop();
            vm = null;
        }
        shouldBeRunning = false;
        floppySnapshot.clear();
        setChanged();
        clearAllDisplays();
    }

    private void clearAllDisplays() {
        for (DisplayBlockEntity master : displayCache.values()) master.resetPixels();
    }

    // ── Terminal I/O ─────────────────────────────────────────────────────────

    public void handleTerminalInput(byte[] data) {
        if (vm == null || !vm.isRunning()) return;
        VMTerminal t = vm.getTerminal();
        for (byte b : data) t.putInput(b);
    }

    public void handleTerminalOutput(byte[] data) {
        for (byte b : data) clientTerminal.write(b);
    }

    public void handleTerminalSync(int[] cells, int cursorRow, int cursorCol) {
        clientTerminal.setSnapshot(cells, cursorRow, cursorCol);
    }

    public void enqueueTerminalOutput(byte[] data) {
        if (data.length == 0) return;
        // Split into ≤8192-byte chunks so the packet codec never overflows.
        for (int i = 0; i < data.length; i += 8192) {
            int end = Math.min(i + 8192, data.length);
            byte[] chunk = new byte[end - i];
            System.arraycopy(data, i, chunk, 0, chunk.length);
            pendingOutput.add(chunk);
        }
    }

    // ── Viewer tracking ───────────────────────────────────────────────────────

    public void addViewer(ServerPlayer player) {
        viewers.add(player);
        // Send the current terminal state so the player sees what's already on screen.
        if (vm != null && vm.isRunning()) {
            VMTerminal t = vm.getTerminal();
            PacketDistributor.sendToPlayer(player,
                    new TerminalSyncPacket(worldPosition,
                            t.getCellSnapshot(), t.getCursorRow(), t.getCursorCol()));
        }
    }

    public void removeViewer(ServerPlayer player) { viewers.remove(player); }

    // ── Accessors for GUI ─────────────────────────────────────────────────────

    public boolean isRunning()    { return vm != null && vm.isRunning(); }
    public boolean isPoweredOff() { return vm != null && vm.isPoweredOff(); }

    public VMTerminal getTerminal() {
        if (level != null && level.isClientSide()) return clientTerminal;
        return vm != null ? vm.getTerminal() : clientTerminal;
    }

    public int getGpioCount()     { return gpioCache.size(); }
    public int getFloppyCount()   { return floppyCache.size(); }
    public BlockPos getBlockPos2() { return worldPosition; }

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.omnitech.logic_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        if (player instanceof ServerPlayer sp) addViewer(sp);
        return new LogicMachineMenu(id, inv, this);
    }

    // ── Level assignment — trigger deferred VM load ───────────────────────────

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (!level.isClientSide() && pendingLoad) {
            pendingLoad = false;
            restoreVMFromDisk();
        }
    }

    // ── VM file persistence ───────────────────────────────────────────────────

    /** Absolute path: <world>/VMs/<vmId>/ */
    private Path getVMDir() {
        return ((ServerLevel) level).getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("VMs")
                .resolve(vmId.toString());
    }

    private void saveVMToDisk() {
        if (vm == null || level == null || level.isClientSide()) return;
        try {
            vm.saveToDirectory(getVMDir());
        } catch (IOException e) {
            LOGGER.error("Failed to save VM state to disk", e);
        }
    }

    private void restoreVMFromDisk() {
        if (!shouldBeRunning) return;
        try {
            if (vm == null) vm = new SednaVM();
            if (vm.loadFromDirectory(getVMDir())) {
                vm.resume();
            } else {
                // No saved state — boot fresh
                vm.start();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to restore VM from disk, booting fresh", e);
            try {
                if (vm == null) vm = new SednaVM();
                vm.start();
            } catch (IOException ex) {
                LOGGER.error("Fresh VM start also failed", ex);
            }
        }
    }

    // ── Chunk load / unload ───────────────────────────────────────────────────

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (vm != null) {
            vm.stop();          // stop the thread first so state is consistent
            saveVMToDisk();     // then write files (no resume needed, BE is gone)
        }
    }

    // ── Serialization ────────────────────────────────────────────────────────
    // Only the UUID and the running-intent flag go into NBT.
    // All VM binary state lives in <world>/VMs/<vmId>/.

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("VmId",       vmId.toString());
        out.putBoolean("ShouldRun", shouldBeRunning);
        // VM binary state is flushed to disk in setRemoved() only.
        // Writing 32MB+ on every autosave would block the server thread.
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);

        String idStr = in.getStringOr("VmId", "");
        vmId = idStr.isEmpty() ? UUID.randomUUID() : UUID.fromString(idStr);
        shouldBeRunning = in.getBooleanOr("ShouldRun", false);

        // We can't load VM files yet — level may be null at this point.
        // setLevel() will fire shortly and trigger restoreVMFromDisk().
        if (shouldBeRunning) pendingLoad = true;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
