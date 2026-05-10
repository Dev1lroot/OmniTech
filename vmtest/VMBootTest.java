import li.cil.sedna.api.Interrupt;
import li.cil.sedna.api.device.Device;
import li.cil.sedna.api.device.InterruptSource;
import li.cil.sedna.api.device.MemoryMappedDevice;
import li.cil.sedna.api.device.PhysicalMemory;
import li.cil.sedna.api.devicetree.DeviceTree;
import li.cil.sedna.api.devicetree.DeviceTreeProvider;
import li.cil.sedna.api.memory.MemoryMap;
import li.cil.sedna.buildroot.Buildroot;
import li.cil.sedna.device.block.ByteBufferBlockDevice;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Standalone VM boot test — no Minecraft dependencies.
 * Run via: ./run_vm_test.sh
 *
 * Mirrors SednaVM's device setup exactly:
 *   IRQ 1 = UART
 *   IRQ 2 = RTC
 *   IRQ 3 = BFS (bootfs.squashfs → /dev/vda)
 *   IRQ 4 = RFS (rootfs.cramfs  → /dev/vdb)
 */
public class VMBootTest {

    static final int  STEPS_PER_ITER = 50_000;
    static final long TIMEOUT_SECS   = 300;

    // Full PLIC node with interrupts-extended (OpenSBI v1.3 requires it, Linux needs it for IRQ routing)
    static final DeviceTreeProvider PLIC_PROVIDER = new DeviceTreeProvider() {
        @Override public Optional<String> getName(Device d) { return Optional.of("plic"); }

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
            node.addProp("phandle", new Object[]{node.getPHandle(device)});
            // MemoryMappedDeviceProvider (catch-all) adds "reg" via composite provider
            List<Object> ext = new ArrayList<>();
            for (Interrupt irq : ((InterruptSource) device).getInterrupts()) {
                if (irq.controller != null) { ext.add(node.getPHandle(irq.controller)); ext.add(irq.id); }
            }
            if (!ext.isEmpty()) node.addProp("interrupts-extended", ext.toArray());
        }
    };

    // CLINT node — provides compatible + interrupts-extended so OpenSBI sees the timer device
    // Without this, OpenSBI reports "Platform Timer Device: --- @ 0Hz" and SBI_SET_TIMER is a no-op,
    // causing Linux WFI to hang forever (no timer interrupts)
    static final DeviceTreeProvider CLINT_PROVIDER = new DeviceTreeProvider() {
        @Override public Optional<String> getName(Device d) { return Optional.of("clint"); }

        @Override
        public Optional<DeviceTree> createNode(DeviceTree root, MemoryMap mm, Device device, String name) {
            return mm.getMemoryRange((MemoryMappedDevice) device)
                .map(r -> root.find("/soc").getChild(name, r.address()));
        }

        @Override
        public void visit(DeviceTree node, MemoryMap mm, Device device) {
            node.addProp("compatible", new Object[]{"riscv,clint0"});
            // interrupts-extended = <cpu_intc_phandle MSIP(3) cpu_intc_phandle MTIP(7)>
            List<Object> ext = new ArrayList<>();
            for (Interrupt irq : ((InterruptSource) device).getInterrupts()) {
                if (irq.controller != null) { ext.add(node.getPHandle(irq.controller)); ext.add(irq.id); }
            }
            if (!ext.isEmpty()) node.addProp("interrupts-extended", ext.toArray());
        }
    };

    // Full VirtIO node with reg (via catch-all) + compatible + interrupts-extended
    static final DeviceTreeProvider VIRTIO_PROVIDER = new DeviceTreeProvider() {
        @Override public Optional<String> getName(Device d) { return Optional.of("virtio"); }

        @Override
        public Optional<DeviceTree> createNode(DeviceTree root, MemoryMap mm, Device device, String name) {
            return mm.getMemoryRange((MemoryMappedDevice) device)
                .map(r -> root.find("/soc").getChild(name, r.address()));
        }

        @Override
        public void visit(DeviceTree node, MemoryMap mm, Device device) {
            // MemoryMappedDeviceProvider (catch-all) adds "reg" via composite provider
            node.addProp("compatible", new Object[]{"virtio,mmio"});
            List<Object> ext = new ArrayList<>();
            for (Interrupt irq : ((InterruptSource) device).getInterrupts()) {
                if (irq.controller != null) { ext.add(node.getPHandle(irq.controller)); ext.add(irq.id); }
            }
            if (!ext.isEmpty()) node.addProp("interrupts-extended", ext.toArray());
        }
    };

    public static void main(String[] args) throws Exception {
        log("Registering device tree providers");
        DeviceTreeRegistry.putProvider(MemoryMappedDevice.class,                 new MemoryMappedDeviceProvider());
        DeviceTreeRegistry.putProvider(UART16550A.class,                         new UART16550AProvider());
        DeviceTreeRegistry.putProvider(GoldfishRTC.class,                        new GoldfishRTCProvider());
        DeviceTreeRegistry.putProvider(PhysicalMemory.class,                     new PhysicalMemoryProvider());
        DeviceTreeRegistry.putProvider(R5PlatformLevelInterruptController.class, PLIC_PROVIDER);
        DeviceTreeRegistry.putProvider(R5CoreLocalInterrupter.class,             CLINT_PROVIDER);
        DeviceTreeRegistry.putProvider(VirtIOBlockDevice.class,                  VIRTIO_PROVIDER);

        log("Creating board + devices");
        R5Board board = new R5Board();

        ByteBufferMemory ram = new ByteBufferMemory(32 * 1024 * 1024);
        board.addDevice(ram);

        UART16550A uart = new UART16550A();
        uart.getInterrupt().set(1, board.getInterruptController());
        java.util.OptionalLong uartAddr = board.addDevice(uart);
        log("UART at 0x" + Long.toHexString(uartAddr.orElse(-1)));
        board.setStandardOutputDevice(uart);

        GoldfishRTC rtc = new GoldfishRTC(SystemTimeRealTimeCounter.get());
        rtc.getInterrupt().set(2, board.getInterruptController());
        board.addDevice(rtc);

        // BFS = boot filesystem (squashfs) → /dev/vda
        VirtIOBlockDevice bfs = new VirtIOBlockDevice(board.getMemoryMap(),
                ByteBufferBlockDevice.createFromStream(Buildroot.getBootFilesystem(), true));
        bfs.getInterrupt().set(3, board.getInterruptController());
        board.addDevice(bfs);

        // RFS = root filesystem (cramfs) → /dev/vdb
        VirtIOBlockDevice rfs = new VirtIOBlockDevice(board.getMemoryMap(),
                ByteBufferBlockDevice.createFromStream(Buildroot.getRootFilesystem(), true));
        rfs.getInterrupt().set(4, board.getInterruptController());
        board.addDevice(rfs);

        log("Loading fw_jump.bin + Linux kernel");
        MemoryMaps.store(board.getMemoryMap(),
                board.getDefaultProgramStart(), Buildroot.getFirmware());
        MemoryMaps.store(board.getMemoryMap(),
                board.getDefaultProgramStart() + 0x200000L, Buildroot.getLinuxImage());

        board.setBootArguments("root=/dev/vda rw earlycon console=ttyS0 loglevel=8");
        board.reset();
        board.initialize();
        board.setRunning(true);

        log("VM running (timeout=" + TIMEOUT_SECS + "s) — UART output below:");
        log("---------------------------------------------------------------");

        long startMs  = System.currentTimeMillis();
        long iters    = 0;
        long lastDiag = 0;
        boolean detectedHang = false;
        boolean firstOutput  = false;

        while (board.isRunning()) {
            board.step(STEPS_PER_ITER);
            iters++;

            int b;
            StringBuilder sb = new StringBuilder();
            while ((b = uart.read()) != -1) {
                sb.append((char)(b & 0xFF));
            }
            if (sb.length() > 0) {
                if (!firstOutput) {
                    firstOutput = true;
                    err(">>> FIRST UART OUTPUT after iter=" + iters
                            + "  (" + (iters * STEPS_PER_ITER) + " steps)");
                }
                System.out.print(sb);
                System.out.flush();
            }

            long[] regs = board.getCpu().getDebugInterface().getGeneralRegisters();
            long   pc   = board.getCpu().getDebugInterface().getProgramCounter();

            if (!detectedHang && (pc == 0x8000a614L || pc == 0x8000a618L)) {
                detectedHang = true;
                err(String.format("HANG sbi_hart_hang() iter=%d PC=0x%x RA=0x%x a0=%d",
                        iters, pc, regs[1], (long)(int)regs[10]));
            }

            if (iters - lastDiag >= 200) {
                lastDiag = iters;
                err(String.format("diag iter=%d PC=0x%x a0=0x%x LSR=0x%x",
                        iters, pc, regs[10], (int) uart.load(5, 0) & 0xFF));
            }

            if (System.currentTimeMillis() - startMs > TIMEOUT_SECS * 1000) {
                log("Timeout (" + TIMEOUT_SECS + "s)");
                break;
            }
        }

        board.setRunning(false);
        log("---------------------------------------------------------------");
        log("Done. UART output received: " + firstOutput + "  hang detected: " + detectedHang);
    }

    static void log(String msg) { System.out.println("[VMBootTest] " + msg); }
    static void err(String msg) { System.err.println("[VMBootTest] " + msg); }
}
