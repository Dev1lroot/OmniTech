/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm;

import li.cil.ceres.BinarySerialization;
import li.cil.ceres.api.SerializationException;
import li.cil.sedna.api.Interrupt;
import li.cil.sedna.api.device.Device;
import li.cil.sedna.api.device.InterruptSource;
import li.cil.sedna.api.device.MemoryMappedDevice;
import li.cil.sedna.api.device.PhysicalMemory;
import li.cil.sedna.api.devicetree.DeviceTree;
import li.cil.sedna.api.devicetree.DeviceTreeProvider;
import li.cil.sedna.api.memory.MemoryAccessException;
import li.cil.sedna.api.memory.MemoryMap;
import li.cil.sedna.buildroot.Buildroot;
import li.cil.sedna.device.block.ByteBufferBlockDevice;
import li.cil.sedna.device.block.NullBlockDevice;
import li.cil.sedna.device.memory.ByteBufferMemory;
import li.cil.sedna.device.rtc.GoldfishRTC;
import li.cil.sedna.device.rtc.SystemTimeRealTimeCounter;
import li.cil.sedna.device.serial.UART16550A;
import li.cil.sedna.device.virtio.VirtIOBlockDevice;
import li.cil.sedna.devicetree.DeviceTreeRegistry;
import li.cil.sedna.devicetree.provider.GoldfishRTCProvider;
import li.cil.sedna.devicetree.provider.MemoryMappedDeviceProvider;
import li.cil.sedna.devicetree.provider.PhysicalMemoryProvider;
import li.cil.sedna.devicetree.provider.UART16550AProvider;
import li.cil.sedna.memory.MemoryMaps;
import li.cil.sedna.riscv.R5Board;
import li.cil.sedna.riscv.device.R5CoreLocalInterrupter;
import li.cil.sedna.riscv.device.R5PlatformLevelInterruptController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.util.Arrays;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Wraps a Sedna R5Board running Buildroot Linux.
 *
 * Lifecycle:
 *   new SednaVM()             — allocates board + devices
 *   start()                   — loads firmware + kernel, starts VM thread
 *   tick()                    — call every server tick to pump state checks
 *   stop()                    — graceful stop (joins thread)
 *   resume()                  — restart thread after loadFromDirectory()
 *   saveToDirectory(Path)     — save each device state to separate files
 *   loadFromDirectory(Path)   — restore from files, then call resume()
 */
public final class SednaVM {

    private static final Logger LOGGER = LoggerFactory.getLogger(SednaVM.class);

    static {
        DeviceTreeRegistry.putProvider(MemoryMappedDevice.class,                    new MemoryMappedDeviceProvider());
        DeviceTreeRegistry.putProvider(UART16550A.class,                            new UART16550AProvider());
        DeviceTreeRegistry.putProvider(GoldfishRTC.class,                           new GoldfishRTCProvider());
        DeviceTreeRegistry.putProvider(PhysicalMemory.class,                        new PhysicalMemoryProvider());
        // Full PLIC node with interrupts-extended — OpenSBI v1.3 requires it, Linux needs it for IRQ routing
        DeviceTreeRegistry.putProvider(R5PlatformLevelInterruptController.class,    new PlicDtProvider());
        // Full VirtIO node with reg + interrupts-extended so Linux discovers and interrupts block devices
        DeviceTreeRegistry.putProvider(VirtIOBlockDevice.class,                     new VirtIODtProvider());
        // CLINT node — OpenSBI needs it to configure the timer; without it SBI_SET_TIMER is a no-op
        // and Linux WFI idles forever because no timer interrupts ever fire
        DeviceTreeRegistry.putProvider(R5CoreLocalInterrupter.class,                new ClintDtProvider());
        // OmniTech bus device — custom MMIO peripheral, must come after MemoryMappedDevice catch-all
        DeviceTreeRegistry.putProvider(OmniTechBusDevice.class,                     new OmniTechBusDtProvider());
    }

    private static final class PlicDtProvider implements DeviceTreeProvider {
        @Override public Optional<String> getName(Device device) { return Optional.of("plic"); }

        @Override
        public Optional<DeviceTree> createNode(DeviceTree root, MemoryMap mm, Device device, String name) {
            return mm.getMemoryRange((MemoryMappedDevice) device)
                .map(r -> root.find("/soc").getChild(name, r.address()));
        }

        @Override
        public void visit(DeviceTree node, MemoryMap mm, Device device) {
            node.addProp("#address-cells",     new Object[]{0});
            node.addProp("#interrupt-cells",   new Object[]{1});
            node.addProp("interrupt-controller", new Object[0]);
            node.addProp("compatible",         new Object[]{"riscv,plic0"});
            node.addProp("riscv,ndev",         new Object[]{31});
            node.addProp("phandle",            new Object[]{node.getPHandle(device)});
            // interrupts-extended = <cpu_intc_phandle M_EXT cpu_intc_phandle S_EXT>
            // MemoryMappedDeviceProvider (catch-all) adds "reg" via the composite provider mechanism
            List<Object> ext = new ArrayList<>();
            for (Interrupt irq : ((InterruptSource) device).getInterrupts()) {
                if (irq.controller != null) { ext.add(node.getPHandle(irq.controller)); ext.add(irq.id); }
            }
            if (!ext.isEmpty()) node.addProp("interrupts-extended", ext.toArray());
        }
    }

    private static final class VirtIODtProvider implements DeviceTreeProvider {
        @Override public Optional<String> getName(Device device) { return Optional.of("virtio"); }

        @Override
        public Optional<DeviceTree> createNode(DeviceTree root, MemoryMap mm, Device device, String name) {
            return mm.getMemoryRange((MemoryMappedDevice) device)
                .map(r -> root.find("/soc").getChild(name, r.address()));
        }

        @Override
        public void visit(DeviceTree node, MemoryMap mm, Device device) {
            // MemoryMappedDeviceProvider (catch-all) adds "reg" via the composite provider mechanism
            node.addProp("compatible", new Object[]{"virtio,mmio"});
            // interrupts-extended = <plic_phandle irq_id>
            List<Object> ext = new ArrayList<>();
            for (Interrupt irq : ((InterruptSource) device).getInterrupts()) {
                if (irq.controller != null) { ext.add(node.getPHandle(irq.controller)); ext.add(irq.id); }
            }
            if (!ext.isEmpty()) node.addProp("interrupts-extended", ext.toArray());
        }
    }

    private static final class ClintDtProvider implements DeviceTreeProvider {
        @Override public Optional<String> getName(Device device) { return Optional.of("clint"); }

        @Override
        public Optional<DeviceTree> createNode(DeviceTree root, MemoryMap mm, Device device, String name) {
            return mm.getMemoryRange((MemoryMappedDevice) device)
                .map(r -> root.find("/soc").getChild(name, r.address()));
        }

        @Override
        public void visit(DeviceTree node, MemoryMap mm, Device device) {
            // MemoryMappedDeviceProvider (catch-all) adds "reg" via the composite provider mechanism
            node.addProp("compatible", new Object[]{"riscv,clint0"});
            // interrupts-extended = <cpu_intc_phandle MSIP(3) cpu_intc_phandle MTIP(7)>
            List<Object> ext = new ArrayList<>();
            for (Interrupt irq : ((InterruptSource) device).getInterrupts()) {
                if (irq.controller != null) { ext.add(node.getPHandle(irq.controller)); ext.add(irq.id); }
            }
            if (!ext.isEmpty()) node.addProp("interrupts-extended", ext.toArray());
        }
    }

    private static final class OmniTechBusDtProvider implements DeviceTreeProvider {
        @Override public Optional<String> getName(Device device) { return Optional.of("omnitech-bus"); }

        @Override
        public Optional<DeviceTree> createNode(DeviceTree root, MemoryMap mm, Device device, String name) {
            return mm.getMemoryRange((MemoryMappedDevice) device)
                .map(r -> root.find("/soc").getChild(name, r.address()));
        }

        @Override
        public void visit(DeviceTree node, MemoryMap mm, Device device) {
            node.addProp("compatible", new Object[]{"omnitech,otec-bus-v1"});
            List<Object> ext = new ArrayList<>();
            for (Interrupt irq : ((InterruptSource) device).getInterrupts()) {
                if (irq.controller != null) { ext.add(node.getPHandle(irq.controller)); ext.add(irq.id); }
            }
            if (!ext.isEmpty()) node.addProp("interrupts-extended", ext.toArray());
        }
    }

    private static final int IRQ_UART        = 1;
    private static final int IRQ_RTC         = 2;
    private static final int IRQ_BFS         = 3; // boot filesystem  → /dev/vda
    private static final int IRQ_RFS         = 4; // root filesystem  → /dev/vdb
    private static final int IRQ_GPIO        = 5; // OmniTech bus device
    private static final int IRQ_FLOPPY_BASE = 6; // floppy drives 0-3 → /dev/vdc through /dev/vdf

    public  static final int MAX_FLOPPY_DRIVES = 4;
    // 1.44 MB standard floppy capacity; matches FloppyDiskItem.CAPACITY
    private static final int FLOPPY_CAPACITY   = 1_474_560;

    public static final int DEFAULT_RAM_SIZE    = 32 * 1024 * 1024;
    private static final int STEPS_PER_ITER    = 50_000;

    // ----- Sedna board + devices -----
    private final R5Board board;
    private final ByteBufferMemory ram;
    private final UART16550A uart;
    private final GoldfishRTC rtc;
    private final VirtIOBlockDevice bfsDevice; // bootfs.squashfs → /dev/vda
    private final VirtIOBlockDevice rfsDevice; // rootfs.cramfs   → /dev/vdb
    private final OmniTechBusDevice  busDevice;
    private final VirtIOBlockDevice[] floppyDevices  = new VirtIOBlockDevice[MAX_FLOPPY_DRIVES];
    // Backing byte arrays for writable floppy disks — written by VM thread, snapshotted by server thread
    private final byte[][]            floppyBacking  = new byte[MAX_FLOPPY_DRIVES][];
    private final boolean[]           floppyReadOnly = new boolean[MAX_FLOPPY_DRIVES];

    // ----- Terminal -----
    private final VMTerminal terminal = new VMTerminal();

    // ----- Configurable runtime params -----
    private byte[] customFirmware = null; // null = use Buildroot Linux

    // ----- Thread state -----
    private volatile boolean running   = false;
    private volatile boolean vmRunning = false;
    private Thread vmThread;

    // ----- Raw UART output for network broadcasting -----
    private final ConcurrentLinkedQueue<byte[]> rawOutputQueue = new ConcurrentLinkedQueue<>();

    // -----------------------------------------------------------------------

    public SednaVM() throws IOException {
        board = new R5Board();

        ram = new ByteBufferMemory(DEFAULT_RAM_SIZE);
        board.addDevice(ram);

        uart = new UART16550A();
        uart.getInterrupt().set(IRQ_UART, board.getInterruptController());
        java.util.OptionalLong uartAddr = board.addDevice(uart);
        if (uartAddr.isPresent()) {
            LOGGER.info("SednaVM: UART16550A at 0x{}", Long.toHexString(uartAddr.getAsLong()));
        } else {
            LOGGER.error("SednaVM: UART allocation FAILED");
        }
        board.setStandardOutputDevice(uart);

        rtc = new GoldfishRTC(SystemTimeRealTimeCounter.get());
        rtc.getInterrupt().set(IRQ_RTC, board.getInterruptController());
        board.addDevice(rtc);

        // Boot filesystem (squashfs) becomes /dev/vda — added first.
        bfsDevice = new VirtIOBlockDevice(board.getMemoryMap(),
                ByteBufferBlockDevice.createFromStream(Buildroot.getBootFilesystem(), true));
        bfsDevice.getInterrupt().set(IRQ_BFS, board.getInterruptController());
        board.addDevice(bfsDevice);

        // Root filesystem (cramfs) becomes /dev/vdb.
        rfsDevice = new VirtIOBlockDevice(board.getMemoryMap(),
                ByteBufferBlockDevice.createFromStream(Buildroot.getRootFilesystem(), true));
        rfsDevice.getInterrupt().set(IRQ_RFS, board.getInterruptController());
        board.addDevice(rfsDevice);

        busDevice = new OmniTechBusDevice();
        busDevice.getInterrupt().set(IRQ_GPIO, board.getInterruptController());
        // OmniTechBusDtProvider generates the 'compatible' node, so board.addDevice() is safe here.
        board.addDevice(0x40000000L, busDevice);

        // Floppy drives: 4 VirtIO block devices, initially empty (NullBlockDevice).
        // Linux sees them as /dev/vdc … /dev/vdf; disk data is swapped in per server tick.
        for (int i = 0; i < MAX_FLOPPY_DRIVES; i++) {
            floppyDevices[i] = new VirtIOBlockDevice(board.getMemoryMap(), NullBlockDevice.get(false));
            floppyDevices[i].getInterrupt().set(IRQ_FLOPPY_BASE + i, board.getInterruptController());
            board.addDevice(floppyDevices[i]);
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void setCustomFirmware(byte[] firmware) { this.customFirmware = firmware; }

    public void start() {
        if (running) return;
        try {
            if (customFirmware != null && customFirmware.length > 0) {
                MemoryMaps.store(board.getMemoryMap(), board.getDefaultProgramStart(),
                        new java.io.ByteArrayInputStream(customFirmware));
                board.setBootArguments("");
            } else {
                MemoryMaps.store(board.getMemoryMap(),
                        board.getDefaultProgramStart(), Buildroot.getFirmware());
                MemoryMaps.store(board.getMemoryMap(),
                        board.getDefaultProgramStart() + 0x200000L, Buildroot.getLinuxImage());
                board.setBootArguments("root=/dev/vda rw earlycon console=ttyS0 loglevel=8");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load firmware/kernel", e);
            return;
        }
        try {
            board.reset();
            board.initialize();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize R5Board", e);
            return;
        }
        board.setRunning(true);
        spawnThread();
    }

    /** Resume after loadFromDirectory() — board state already restored. */
    public void resume() {
        if (running) return;
        board.setRunning(true);
        spawnThread();
    }

    public void stop() {
        running = false;
        board.setRunning(false);
        if (vmThread != null && vmThread.isAlive()) {
            try { vmThread.join(2000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            vmThread = null;
        }
    }

    private void spawnThread() {
        running   = true;
        vmRunning = true;
        vmThread  = new Thread(this::runLoop, "OmniTech-VM");
        vmThread.setDaemon(true);
        vmThread.start();
    }

    // -----------------------------------------------------------------------
    // VM execution loop
    // -----------------------------------------------------------------------

    private boolean loggedFirstOutput = false;
    private boolean detectedHang = false;

    private void runLoop() {
        LOGGER.info("OmniTech-VM thread started, initial PC=0x{}",
                Long.toHexString(board.getCpu().getDebugInterface().getProgramCounter()));
        long iterations = 0;
        long lastDiagIter = -5_000_000L;
        while (running && board.isRunning()) {
            while (terminal.hasInput() && uart.canPutByte()) {
                uart.putByte(terminal.pollInput());
            }
            uart.flush();

            board.step(STEPS_PER_ITER);
            iterations++;

            long pc = board.getCpu().getDebugInterface().getProgramCounter();
            long[] regs = board.getCpu().getDebugInterface().getGeneralRegisters();

            if (!detectedHang && (pc == 0x8000a614L || pc == 0x8000a618L)) {
                detectedHang = true;
                LOGGER.error("[VM-HANG] sbi_hart_hang() at iter={}! " +
                        "PC=0x{} RA=0x{} a0=0x{}",
                        iterations,
                        Long.toHexString(pc),
                        Long.toHexString(regs[1]),
                        Long.toHexString(regs[10]));
            }

            if (iterations - lastDiagIter >= 5_000_000) {
                lastDiagIter = iterations;
                int lsr = (int) uart.load(5, 0) & 0xFF;
                LOGGER.info("[VM-diag] iter={} LSR=0x{} PC=0x{} a0=0x{}",
                        iterations, Integer.toHexString(lsr),
                        Long.toHexString(pc), Long.toHexString(regs[10]));
            }

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            int b;
            while ((b = uart.read()) != -1) {
                terminal.write((byte) b);
                bos.write(b);
            }
            byte[] raw = bos.toByteArray();
            if (raw.length > 0) {
                if (!loggedFirstOutput) {
                    LOGGER.info("OmniTech-VM: first UART output after {} iterations: {}",
                            iterations,
                            new String(raw, 0, Math.min(raw.length, 80), java.nio.charset.StandardCharsets.ISO_8859_1)
                                    .replace("\r", "\\r").replace("\n", "\\n").replace("", "ESC"));
                    loggedFirstOutput = true;
                }
                rawOutputQueue.add(raw);
            }

            if (board.isRestarting()) {
                handleRestart();
                break;
            }
        }
        LOGGER.info("OmniTech-VM thread stopped after {} iterations", iterations);
        vmRunning = false;
        running   = false;
    }

    private void handleRestart() {
        LOGGER.info("OmniTech VM: system reset requested");
        board.reset();
        try {
            board.initialize();
            spawnThread();
        } catch (Exception e) {
            LOGGER.error("VM restart failed", e);
        }
    }

    // -----------------------------------------------------------------------
    // Server-tick bridge
    // -----------------------------------------------------------------------

    public void tick() {
        if (!running && board.isRunning()) board.setRunning(false);
    }

    // -----------------------------------------------------------------------
    // File-based state persistence
    // -----------------------------------------------------------------------

    /**
     * Persist each device's state to separate binary files under {@code dir}.
     * Must be called while the VM is stopped (after stop()).
     */
    public void saveToDirectory(Path dir) throws IOException {
        Files.createDirectories(dir);
        try {
            serializeToFile(dir.resolve("cpu.bin"),  board.getCpu());
            serializeToFile(dir.resolve("uart.bin"), uart);
            serializeToFile(dir.resolve("rtc.bin"),  rtc);
            serializeToFile(dir.resolve("bfs.bin"),  bfsDevice);
            serializeToFile(dir.resolve("rfs.bin"),  rfsDevice);
            for (int i = 0; i < MAX_FLOPPY_DRIVES; i++) {
                serializeToFile(dir.resolve("floppy" + i + ".bin"), floppyDevices[i]);
            }
        } catch (SerializationException e) {
            throw new IOException("VM device serialization failed", e);
        }

        ByteBuffer ramBuf = ByteBuffer.allocate(DEFAULT_RAM_SIZE);
        try {
            ram.load(0, ramBuf);
        } catch (MemoryAccessException e) {
            throw new IOException("RAM dump failed", e);
        }
        Files.write(dir.resolve("ram.bin"), ramBuf.array());
    }

    /**
     * Restore device state from files previously written by saveToDirectory().
     * Returns true if all files were present and loaded successfully.
     * After a successful load, call resume() to start the VM thread.
     */
    public boolean loadFromDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return false;
        if (!Files.exists(dir.resolve("cpu.bin"))) return false;

        byte[] ramBytes = Files.readAllBytes(dir.resolve("ram.bin"));
        try {
            ram.store(0, ByteBuffer.wrap(ramBytes));
        } catch (MemoryAccessException e) {
            throw new IOException("RAM restore failed", e);
        }

        try {
            deserializeFromFile(dir.resolve("cpu.bin"),  board.getCpu());
            deserializeFromFile(dir.resolve("uart.bin"), uart);
            deserializeFromFile(dir.resolve("rtc.bin"),  rtc);
            deserializeFromFile(dir.resolve("bfs.bin"),  bfsDevice);
            deserializeFromFile(dir.resolve("rfs.bin"),  rfsDevice);
            for (int i = 0; i < MAX_FLOPPY_DRIVES; i++) {
                Path fp = dir.resolve("floppy" + i + ".bin");
                if (Files.exists(fp)) deserializeFromFile(fp, floppyDevices[i]);
            }
        } catch (SerializationException e) {
            throw new IOException("VM device deserialization failed", e);
        }

        return true;
    }

    private static void serializeToFile(Path file, Object obj)
            throws SerializationException, IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            BinarySerialization.serialize(out, obj);
        }
    }

    private static void deserializeFromFile(Path file, Object obj)
            throws SerializationException, IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            BinarySerialization.deserialize(in, obj);
        }
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Floppy drive management (called from server thread)
    // -----------------------------------------------------------------------

    /**
     * Insert a disk into the given VirtIO floppy slot.
     * The backing byte array is padded/truncated to exactly FLOPPY_CAPACITY bytes.
     * For writable disks the same array is kept for later write-back via getFloppyData().
     */
    public void setFloppyDisk(int driveId, byte[] data, boolean readOnly) {
        if (driveId < 0 || driveId >= MAX_FLOPPY_DRIVES) return;
        byte[] buf = Arrays.copyOf(data, FLOPPY_CAPACITY);
        floppyReadOnly[driveId] = readOnly;
        floppyBacking[driveId] = readOnly ? null : buf;
        try {
            floppyDevices[driveId].setBlock(ByteBufferBlockDevice.wrap(ByteBuffer.wrap(buf), readOnly));
            LOGGER.info("SednaVM: floppy {} inserted (readOnly={})", driveId, readOnly);
        } catch (IOException e) {
            LOGGER.error("SednaVM: failed to insert floppy {}", driveId, e);
        }
    }

    /**
     * Remove the disk from the given VirtIO floppy slot, replacing it with an empty device.
     */
    public void ejectFloppy(int driveId) {
        if (driveId < 0 || driveId >= MAX_FLOPPY_DRIVES) return;
        floppyBacking[driveId] = null;
        floppyReadOnly[driveId] = false;
        try {
            floppyDevices[driveId].setBlock(NullBlockDevice.get(false));
            LOGGER.info("SednaVM: floppy {} ejected", driveId);
        } catch (IOException e) {
            LOGGER.error("SednaVM: failed to eject floppy {}", driveId, e);
        }
    }

    /**
     * Returns the current byte array backing a writable floppy drive, for write-back
     * to the ItemStack. Returns null if the drive is empty or the disk is read-only.
     * The array is the live buffer being written to by the VM; copy it before storing.
     */
    public byte[] getFloppyData(int driveId) {
        if (driveId < 0 || driveId >= MAX_FLOPPY_DRIVES) return null;
        return floppyBacking[driveId];
    }

    public byte[] drainOutput() {
        List<byte[]> chunks = new ArrayList<>();
        byte[] chunk;
        while ((chunk = rawOutputQueue.poll()) != null) chunks.add(chunk);
        if (chunks.isEmpty()) return new byte[0];
        int total = 0;
        for (byte[] c : chunks) total += c.length;
        ByteBuffer buf = ByteBuffer.allocate(total);
        for (byte[] c : chunks) buf.put(c);
        return buf.array();
    }

    public VMTerminal getTerminal()         { return terminal; }
    public OmniTechBusDevice getBusDevice() { return busDevice; }

    public boolean isRunning()    { return running; }
    public boolean isPoweredOff() { return !running && !board.isRunning(); }
}
