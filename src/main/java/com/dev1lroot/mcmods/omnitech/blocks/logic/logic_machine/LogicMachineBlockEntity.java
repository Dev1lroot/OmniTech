package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import com.dev1lroot.mcmods.omnitech.gui.LogicMachineMenu;
import com.dev1lroot.mcmods.omnitech.blocks.logic.LogicCableBlock;
import com.dev1lroot.mcmods.omnitech.blocks.analog.speaker.SpeakerBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.floppy_drive.FloppyDriveBlockEntity;
import com.dev1lroot.mcmods.omnitech.blocks.logic.gpio_port.GPIOPortBlockEntity;
import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm.OmniTechBusDevice;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm.SednaVM;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm.TerminalDisplay;
import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm.VMTerminal;
import com.dev1lroot.mcmods.omnitech.items.RomItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class LogicMachineBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogicMachineBlockEntity.class);

    public static final int BTN_RUN   = 0;
    public static final int BTN_STOP  = 1;
    public static final int BTN_RESET = 2;

    // ── VM ────────────────────────────────────────────────────────────────────

    @Nullable private SednaVM vm = null;
    private boolean shouldBeRunning = false;

    private UUID vmId = UUID.randomUUID();
    private boolean pendingLoad = false;

    // ── Item slots: 0=CPU (Microcontroller), 1=ROM ────────────────────────────

    private final SimpleContainer itemSlots = new SimpleContainer(2) {
        @Override
        public void setItem(int slot, ItemStack stack) {
            ItemStack current = getItem(slot);
            super.setItem(slot, stack);
            if (!current.isEmpty() && stack.isEmpty()) {
                resetVM();
            }
        }
        @Override
        public void setChanged() {
            super.setChanged();
            LogicMachineBlockEntity.this.setChanged();
        }
    };

    // ── Peripheral cache ──────────────────────────────────────────────────────

    private final Map<Integer, GPIOPortBlockEntity>    gpioCache      = new HashMap<>();
    private final Map<Integer, DisplayBlockEntity>     displayCache   = new HashMap<>();
    private final Map<Integer, FloppyDriveBlockEntity> floppyCache    = new HashMap<>();
    private final Map<Integer, SpeakerBlockEntity>     speakerCache   = new HashMap<>();
    private final Map<Integer, OmniTechDataComponents.ByteData> floppySnapshot = new HashMap<>();
    private long lastCacheRefresh = -100L;

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

        // Drain output queue to prevent memory buildup — terminal is written in VM thread
        be.vm.drainOutput();

        VMTerminal term = be.vm.getTerminal();
        if (term.isDirty()) {
            DisplayBlockEntity display = be.displayCache.get(0);
            if (display != null) TerminalDisplay.render(term, display);
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
        busDevice.clearSpeakerPorts();

        int idx = 0;
        for (Map.Entry<Integer, GPIOPortBlockEntity> e : new TreeMap<>(gpioCache).entrySet()) {
            GPIOPortBlockEntity g = e.getValue();
            busDevice.setGpioPort(idx, g::getInputSignal, g::setOutputSignal);
            idx++;
        }

        for (Map.Entry<Integer, SpeakerBlockEntity> e : new TreeMap<>(speakerCache).entrySet()) {
            int speakerId = e.getKey();
            SpeakerBlockEntity s = e.getValue();
            busDevice.setSpeakerPort(speakerId, () ->
                    s.setMmio(busDevice.getSpeakerVolume(speakerId), busDevice.getSpeakerFreq(speakerId)));
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

            OmniTechDataComponents.ByteData currentData = disk.get(OmniTechDataComponents.FLOPPY_DATA.get());
            boolean alreadyInserted = floppySnapshot.containsKey(driveId);
            OmniTechDataComponents.ByteData prevData = floppySnapshot.get(driveId);

            if (alreadyInserted && currentData == prevData) continue;

            byte[] bytes = currentData != null ? currentData.data() : new byte[0];
            boolean readOnly = disk.getOrDefault(OmniTechDataComponents.READ_ONLY.get(), 0) == 1;
            vm.setFloppyDisk(driveId, bytes, readOnly);
            floppySnapshot.put(driveId, currentData);
        }

        Iterator<Map.Entry<Integer, OmniTechDataComponents.ByteData>> it = floppySnapshot.entrySet().iterator();
        while (it.hasNext()) {
            int driveId = it.next().getKey();
            if (!activeDriveIds.contains(driveId)) {
                vm.ejectFloppy(driveId);
                it.remove();
            }
        }
    }

    private void floppyWriteBack() {
        if (vm == null) return;
        for (Map.Entry<Integer, FloppyDriveBlockEntity> e : floppyCache.entrySet()) {
            int driveId = e.getKey();
            if (driveId < 0 || driveId >= SednaVM.MAX_FLOPPY_DRIVES) continue;
            byte[] live = vm.getFloppyData(driveId);
            if (live == null) continue;
            FloppyDriveBlockEntity drive = e.getValue();
            ItemStack disk = drive.getDisk();
            if (disk.isEmpty()) continue;
            OmniTechDataComponents.ByteData saved =
                    new OmniTechDataComponents.ByteData(Arrays.copyOf(live, live.length));
            disk.set(OmniTechDataComponents.FLOPPY_DATA.get(), saved);
            drive.setChanged();
            floppySnapshot.put(driveId, saved);
        }
    }

    // ── Network cache ────────────────────────────────────────────────────────

    private void rebuildNetworkCache(Level level, BlockPos origin) {
        gpioCache.clear();
        displayCache.clear();
        floppyCache.clear();
        speakerCache.clear();

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
                    } else if (be instanceof SpeakerBlockEntity s) {
                        speakerCache.put(speakerCache.size(), s);
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

        ItemStack romStack = itemSlots.getItem(1);
        if (romStack.isEmpty() || !(romStack.getItem() instanceof RomItem rom)) {
            LOGGER.warn("LogicMachine: cannot start — no ROM inserted");
            return;
        }

        try {
            if (vm == null) vm = new SednaVM();

            if (RomItem.TYPE_FIRMWARE.equals(rom.getRomType())) {
                OmniTechDataComponents.ByteData romData = romStack.get(OmniTechDataComponents.ROM_DATA.get());
                if (romData != null && romData.data().length > 0) {
                    vm.setCustomFirmware(romData.data());
                } else {
                    LOGGER.warn("LogicMachine: firmware ROM is empty");
                    vm = null;
                    return;
                }
            }
            // TYPE_LINUX: customFirmware stays null → SednaVM uses Buildroot

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

    // ── Terminal I/O (from Keyboard block) ───────────────────────────────────

    public void handleTerminalInput(byte[] data) {
        if (vm == null || !vm.isRunning()) return;
        VMTerminal t = vm.getTerminal();
        for (byte b : data) t.putInput(b);
    }

    // ── Item slot accessor for menu ──────────────────────────────────────────

    public SimpleContainer getItemSlots() { return itemSlots; }

    // ── Accessors for GUI ─────────────────────────────────────────────────────

    public boolean isRunning()    { return vm != null && vm.isRunning(); }
    public boolean isPoweredOff() { return vm != null && vm.isPoweredOff(); }

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

        ItemStack romStack = itemSlots.getItem(1);

        try {
            if (vm == null) vm = new SednaVM();

            if (!romStack.isEmpty() && romStack.getItem() instanceof RomItem rom
                    && RomItem.TYPE_FIRMWARE.equals(rom.getRomType())) {
                OmniTechDataComponents.ByteData romData = romStack.get(OmniTechDataComponents.ROM_DATA.get());
                if (romData != null && romData.data().length > 0) {
                    vm.setCustomFirmware(romData.data());
                }
            }

            if (vm.loadFromDirectory(getVMDir())) {
                vm.resume();
            } else {
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
            vm.stop();
            saveVMToDisk();
        }
    }

    // ── Serialization ────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("VmId",       vmId.toString());
        out.putBoolean("ShouldRun", shouldBeRunning);
        NonNullList<ItemStack> temp = NonNullList.withSize(2, ItemStack.EMPTY);
        for (int i = 0; i < 2; i++) temp.set(i, itemSlots.getItem(i));
        ContainerHelper.saveAllItems(out, temp);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);

        String idStr = in.getStringOr("VmId", "");
        vmId = idStr.isEmpty() ? UUID.randomUUID() : UUID.fromString(idStr);
        shouldBeRunning = in.getBooleanOr("ShouldRun", false);

        NonNullList<ItemStack> temp = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, temp);
        for (int i = 0; i < 2; i++) itemSlots.setItem(i, temp.get(i));

        if (shouldBeRunning) pendingLoad = true;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
