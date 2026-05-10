package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm;

import li.cil.sedna.api.Interrupt;
import li.cil.sedna.api.Sizes;
import li.cil.sedna.api.device.InterruptSource;
import li.cil.sedna.api.device.MemoryMappedDevice;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Custom MMIO device exposing OmniTech peripherals to the Linux guest.
 *
 * Memory map (base = 0x40000000):
 *
 *   Header (0x000 – 0x01F):
 *     0x000  [R]    Magic: 0x4F544543 ("OTEC")
 *     0x004  [R]    Version: 1
 *     0x008  [R]    GPIO port count
 *     0x00C  [R]    Display slot count (portId > 0 displays)
 *     0x010  [R/W1C] IRQ status — bitmask of GPIO indices that changed
 *     0x014  [R/W]  IRQ mask  — set bits to enable IRQ for those GPIO indices
 *
 *   GPIO data (0x100 – 0x1FF):
 *     0x100 + portId*4  [RW]  GPIO port value (0..15)
 *
 *   Display slots (0x10000 – 0xFFFFF), one slot per portId (1-based):
 *     slot_base = 0x10000 + (portId-1) * DISP_STRIDE
 *       +0x000  [R]  pixel width  (clusterCols * blockSize, capped at MAX_DISP_W)
 *       +0x004  [R]  pixel height (clusterRows * blockSize, capped at MAX_DISP_H)
 *       +0x008  [W]  flush — write any value to push staged pixels to the display block
 *       +0x00C  [W]  clear — write any value to reset all pixels to black
 *       +0x010  [RW] pixel[0]  (0x00RRGGBB, row-major: index = y*width + x)
 *       +0x014  [RW] pixel[1]
 *       ...
 */
public final class OmniTechBusDevice implements MemoryMappedDevice, InterruptSource {

    // ---- Header registers ----
    private static final int MAGIC   = 0x4F544543;
    private static final int VERSION = 1;

    private static final int REG_MAGIC      = 0x000;
    private static final int REG_VERSION    = 0x004;
    private static final int REG_GPIO_COUNT = 0x008;
    private static final int REG_DISP_COUNT = 0x00C;
    private static final int REG_IRQ_STATUS = 0x010;
    private static final int REG_IRQ_MASK   = 0x014;

    // ---- GPIO region ----
    private static final int GPIO_BASE      = 0x100;
    private static final int MAX_GPIO_PORTS = 64;

    // ---- Display region ----
    private static final int DISP_REGION_BASE = 0x10000;
    private static final int DISP_STRIDE      = 0x10100; // bytes per slot
    private static final int DISP_HEADER_SIZE = 0x10;    // 4 regs × 4 bytes

    // Slot-header register offsets:
    private static final int DREG_WIDTH  = 0x00; // [R]
    private static final int DREG_HEIGHT = 0x04; // [R]
    private static final int DREG_FLUSH  = 0x08; // [W]
    private static final int DREG_CLEAR  = 0x0C; // [W]

    public static final int MAX_DISP_SLOTS = 8;
    public static final int MAX_DISP_W     = 128;
    public static final int MAX_DISP_H     = 128;

    // Total device size = 1 MB
    private static final int DEVICE_LENGTH = 0x100000;

    // ---- GPIO state ----
    private final IntSupplier[] gpioReaders     = new IntSupplier[MAX_GPIO_PORTS];
    private final IntConsumer[]  gpioWriters     = new IntConsumer[MAX_GPIO_PORTS];
    private final int[]          lastGpioValues  = new int[MAX_GPIO_PORTS];
    private int gpioCount = 0;

    // ---- IRQ state ----
    private volatile int irqStatus = 0;
    private int irqMask = 0;
    private final Interrupt interrupt = new Interrupt();

    // ---- Display state ----
    @FunctionalInterface
    public interface PixelFlusher { void apply(int[] pixels, int w, int h); }

    private final int[][]         dispPixelBufs   = new int[MAX_DISP_SLOTS][MAX_DISP_W * MAX_DISP_H];
    private final Object[]        dispLocks       = new Object[MAX_DISP_SLOTS];
    private final int[]           dispWidths      = new int[MAX_DISP_SLOTS];
    private final int[]           dispHeights     = new int[MAX_DISP_SLOTS];
    private final AtomicBoolean[] dispFlushPending = new AtomicBoolean[MAX_DISP_SLOTS];
    private final AtomicBoolean[] dispClearPending = new AtomicBoolean[MAX_DISP_SLOTS];
    private final PixelFlusher[]  dispFlushers    = new PixelFlusher[MAX_DISP_SLOTS];
    private final Runnable[]      dispClearers    = new Runnable[MAX_DISP_SLOTS];
    private int dispCount = 0;

    public OmniTechBusDevice() {
        for (int i = 0; i < MAX_DISP_SLOTS; i++) {
            dispLocks[i]        = new Object();
            dispFlushPending[i] = new AtomicBoolean(false);
            dispClearPending[i] = new AtomicBoolean(false);
        }
    }

    // ---- MemoryMappedDevice ----

    @Override public int getLength() { return DEVICE_LENGTH; }

    @Override
    public int getSupportedSizes() {
        return (1 << Sizes.SIZE_8_LOG2) | (1 << Sizes.SIZE_16_LOG2) | (1 << Sizes.SIZE_32_LOG2);
    }

    @Override
    public long load(int offset, int sizeLog2) {
        if (sizeLog2 == Sizes.SIZE_32_LOG2) return loadWord(offset);
        int word = (int) loadWord(offset & ~3);
        int shift = (offset & 3) * 8;
        if (sizeLog2 == Sizes.SIZE_8_LOG2)  return (word >> shift) & 0xFF;
        if (sizeLog2 == Sizes.SIZE_16_LOG2) return (word >> shift) & 0xFFFF;
        return 0;
    }

    @Override
    public void store(int offset, long value, int sizeLog2) {
        if (sizeLog2 == Sizes.SIZE_32_LOG2) {
            storeWord(offset, (int) value);
        } else if (sizeLog2 == Sizes.SIZE_8_LOG2
                && offset >= GPIO_BASE && offset < DISP_REGION_BASE) {
            // Byte writes to GPIO output
            int portId = (offset - GPIO_BASE) / 4;
            if (portId < gpioCount && gpioWriters[portId] != null) {
                gpioWriters[portId].accept((int) value & 0xFF);
            }
        }
    }

    private long loadWord(int offset) {
        if (offset == REG_MAGIC)      return MAGIC;
        if (offset == REG_VERSION)    return VERSION;
        if (offset == REG_GPIO_COUNT) return gpioCount;
        if (offset == REG_DISP_COUNT) return dispCount;
        if (offset == REG_IRQ_STATUS) {
            int status = irqStatus;
            irqStatus = 0;
            if (status != 0) interrupt.lowerInterrupt();
            return status & 0xFFFF_FFFFL;
        }
        if (offset == REG_IRQ_MASK) return irqMask & 0xFFFF_FFFFL;

        if (offset >= GPIO_BASE && offset < DISP_REGION_BASE) {
            int portId = (offset - GPIO_BASE) / 4;
            if (portId < gpioCount && gpioReaders[portId] != null) {
                return gpioReaders[portId].getAsInt() & 0xFFFF_FFFFL;
            }
        }

        if (offset >= DISP_REGION_BASE) {
            int rel     = offset - DISP_REGION_BASE;
            int slot    = rel / DISP_STRIDE;
            int slotOff = rel % DISP_STRIDE;
            if (slot < dispCount && dispFlushers[slot] != null) {
                if (slotOff == DREG_WIDTH)  return dispWidths[slot];
                if (slotOff == DREG_HEIGHT) return dispHeights[slot];
                if (slotOff >= DISP_HEADER_SIZE) {
                    int pixelIdx = (slotOff - DISP_HEADER_SIZE) / 4;
                    int w = dispWidths[slot], h = dispHeights[slot];
                    if (pixelIdx < w * h) {
                        synchronized (dispLocks[slot]) {
                            return dispPixelBufs[slot][pixelIdx] & 0xFFFF_FFFFL;
                        }
                    }
                }
            }
        }
        return 0;
    }

    private void storeWord(int offset, int value) {
        if (offset == REG_IRQ_STATUS) {
            irqStatus &= ~value;
            if (irqStatus == 0) interrupt.lowerInterrupt();
        } else if (offset == REG_IRQ_MASK) {
            irqMask = value;
        } else if (offset >= GPIO_BASE && offset < DISP_REGION_BASE) {
            int portId = (offset - GPIO_BASE) / 4;
            if (portId < gpioCount && gpioWriters[portId] != null) {
                gpioWriters[portId].accept(value);
            }
        } else if (offset >= DISP_REGION_BASE) {
            int rel     = offset - DISP_REGION_BASE;
            int slot    = rel / DISP_STRIDE;
            int slotOff = rel % DISP_STRIDE;
            if (slot < dispCount && dispFlushers[slot] != null) {
                if (slotOff == DREG_FLUSH) {
                    dispFlushPending[slot].set(true);
                } else if (slotOff == DREG_CLEAR) {
                    dispClearPending[slot].set(true);
                } else if (slotOff >= DISP_HEADER_SIZE) {
                    int pixelIdx = (slotOff - DISP_HEADER_SIZE) / 4;
                    int w = dispWidths[slot], h = dispHeights[slot];
                    if (pixelIdx < w * h) {
                        synchronized (dispLocks[slot]) {
                            dispPixelBufs[slot][pixelIdx] = value & 0xFFFFFF;
                        }
                    }
                }
            }
        }
    }

    // ---- InterruptSource ----

    public Interrupt getInterrupt() { return interrupt; }

    @Override
    public Iterable<Interrupt> getInterrupts() {
        return java.util.Collections.singletonList(interrupt);
    }

    // ---- GPIO change detection (called server tick) ----

    public void pollGpioChanges() {
        int changed = 0;
        for (int i = 0; i < gpioCount; i++) {
            if (gpioReaders[i] == null) continue;
            int current = gpioReaders[i].getAsInt();
            if (current != lastGpioValues[i]) {
                lastGpioValues[i] = current;
                changed |= (1 << i);
            }
        }
        int masked = changed & irqMask;
        if (masked != 0) {
            irqStatus |= masked;
            interrupt.raiseInterrupt();
        }
    }

    public void raiseInterrupt()  { interrupt.raiseInterrupt(); }
    public void lowerInterrupt()  { interrupt.lowerInterrupt(); }

    // ---- Display flush (called server tick) ----

    /**
     * Applies any pending flush/clear operations from the VM thread onto the
     * actual DisplayBlockEntity objects. Must be called from the server thread.
     */
    public void flushPendingDisplays() {
        for (int slot = 0; slot < dispCount; slot++) {
            if (dispClearPending[slot].compareAndSet(true, false)) {
                Runnable c = dispClearers[slot];
                if (c != null) c.run();
            }
            if (dispFlushPending[slot].compareAndSet(true, false)) {
                PixelFlusher f = dispFlushers[slot];
                if (f == null) continue;
                int w = dispWidths[slot], h = dispHeights[slot];
                if (w <= 0 || h <= 0) continue;
                int[] snapshot;
                synchronized (dispLocks[slot]) {
                    snapshot = Arrays.copyOf(dispPixelBufs[slot], w * h);
                }
                f.apply(snapshot, w, h);
            }
        }
    }

    // ---- Configuration (called from server thread, sync with cache rebuild) ----

    public void setGpioPort(int portId, IntSupplier reader, IntConsumer writer) {
        if (portId < 0 || portId >= MAX_GPIO_PORTS) return;
        gpioReaders[portId] = reader;
        gpioWriters[portId] = writer;
        gpioCount = Math.max(gpioCount, portId + 1);
    }

    public void clearPorts() {
        for (int i = 0; i < MAX_GPIO_PORTS; i++) {
            gpioReaders[i] = null;
            gpioWriters[i] = null;
            // lastGpioValues preserved — avoids spurious IRQ when re-wiring same GPIO
        }
        gpioCount = 0;
        irqStatus = 0;
        interrupt.lowerInterrupt();
    }

    public void setDisplay(int slot, int w, int h, PixelFlusher flusher, Runnable clearer) {
        if (slot < 0 || slot >= MAX_DISP_SLOTS) return;
        dispWidths[slot]   = Math.min(w, MAX_DISP_W);
        dispHeights[slot]  = Math.min(h, MAX_DISP_H);
        dispFlushers[slot] = flusher;
        dispClearers[slot] = clearer;
        dispCount = Math.max(dispCount, slot + 1);
    }

    public void clearDisplaySlots() {
        for (int i = 0; i < MAX_DISP_SLOTS; i++) {
            dispWidths[i]  = 0;
            dispHeights[i] = 0;
            dispFlushers[i] = null;
            dispClearers[i] = null;
            dispFlushPending[i].set(false);
            dispClearPending[i].set(false);
        }
        dispCount = 0;
    }

    public void setGpioCount(int count) { this.gpioCount = count; }
}
