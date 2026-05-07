package com.dev1lroot.mcmods.omnitech.blocks.logic.vm;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a minimal Flattened Device Tree (FDT/DTB) binary for the LogicMachine
 * RISC-V emulator so that Linux can discover CPUs, memory, CLINT, UART, and framebuffer.
 *
 * Memory map described:
 *   0x00000000  : RAM (size = ramBytes)
 *   0x02000000  : CLINT (riscv,clint0)
 *   0x10000000  : Framebuffer 320×240 XRGB (simple-framebuffer)
 *   0x10001000  : UART ns16550a
 */
public final class DtbBuilder {

    private static final int FDT_BEGIN_NODE = 1;
    private static final int FDT_END_NODE   = 2;
    private static final int FDT_PROP       = 3;
    private static final int FDT_END        = 9;

    private final ByteArrayOutputStream structBuf = new ByteArrayOutputStream(4096);
    private final ByteArrayOutputStream stringBuf = new ByteArrayOutputStream(512);
    private final Map<String, Integer>  strOffsets = new LinkedHashMap<>();

    private DtbBuilder() {}

    /** Returns a complete DTB binary for the given RAM size. */
    public static byte[] build(int ramBytes) {
        return new DtbBuilder().buildInternal(ramBytes);
    }

    private byte[] buildInternal(int ramBytes) {
        beginNode("");
        propU32("#address-cells", 1);
        propU32("#size-cells",    1);
        propStr("compatible",     "riscv-virtio");
        propStr("model",          "OmniTech RISC-V");

        // cpus
        beginNode("cpus");
        propU32("#address-cells", 1);
        propU32("#size-cells",    0);
          beginNode("cpu@0");
          propStr("device_type",       "cpu");
          propU32("reg",               0);
          propStr("status",            "okay");
          propStr("compatible",        "riscv");
          propStr("riscv,isa",         "rv32imafdc");
          propStr("mmu-type",          "riscv,sv32");
          propU32("timebase-frequency", 1_000_000);
            // cpu-intc phandle=1
            beginNode("interrupt-controller");
            propU32("#interrupt-cells", 1);
            propEmpty("interrupt-controller");
            propStr("compatible", "riscv,cpu-intc");
            propU32("phandle", 1);
            endNode();
          endNode(); // cpu@0
        endNode(); // cpus

        // memory@0
        beginNode("memory@0");
        propStr("device_type", "memory");
        propU32s("reg", 0x00000000, ramBytes);
        endNode();

        // chosen
        beginNode("chosen");
        propStr("bootargs",    "console=ttyS0,115200n8 earlycon=uart8250,mmio,0x10001000 nokaslr");
        propStr("stdout-path", "/soc/serial@10001000");
        endNode();

        // soc
        beginNode("soc");
        propU32("#address-cells", 1);
        propU32("#size-cells",    1);
        propStr("compatible",     "simple-bus");
        propEmpty("ranges");

          // CLINT
          beginNode("clint@2000000");
          propStr("compatible", "riscv,clint0");
          propU32s("reg", 0x02000000, 0x10000);
          // interrupts-extended: <cpu0_intc 3(MSIP) cpu0_intc 7(MTIP)>
          propU32s("interrupts-extended", 1, 3, 1, 7);
          endNode();

          // UART ns16550a
          beginNode("serial@10001000");
          propStr("compatible",     "ns16550a");
          propU32s("reg",           0x10001000, 0x1000);
          propU32("clock-frequency", 1_843_200);
          propU32("reg-shift",      0);
          propU32("reg-io-width",   1);
          endNode();

          // Simple framebuffer 320×240 XRGB8888
          beginNode("framebuffer@10000000");
          propStr("compatible", "simple-framebuffer");
          propU32s("reg",  0x10000000, 320 * 240 * 4);
          propU32("width",  320);
          propU32("height", 240);
          propU32("stride", 320 * 4);
          propStr("format", "x8r8g8b8");
          endNode();

        endNode(); // soc
        endNode(); // root

        writeU32be(structBuf, FDT_END);

        // Assemble header + rsvmap + struct + strings
        byte[] structBytes = structBuf.toByteArray();
        byte[] stringBytes = stringBuf.toByteArray();

        int rsvmapOff  = 40;        // header is 40 bytes
        int rsvmapSize = 16;        // one null reservation entry
        int structOff  = rsvmapOff + rsvmapSize; // 56
        int stringsOff = structOff + structBytes.length;
        int totalSize  = (stringsOff + stringBytes.length + 3) & ~3;

        ByteArrayOutputStream out = new ByteArrayOutputStream(totalSize);
        writeU32be(out, 0xD00DFEED);           // magic
        writeU32be(out, totalSize);
        writeU32be(out, structOff);
        writeU32be(out, stringsOff);
        writeU32be(out, rsvmapOff);
        writeU32be(out, 17);                   // version
        writeU32be(out, 16);                   // last_comp_version
        writeU32be(out, 0);                    // boot_cpuid_phys
        writeU32be(out, stringBytes.length);   // size_dt_strings
        writeU32be(out, structBytes.length);   // size_dt_struct
        out.write(new byte[16], 0, 16);        // memory reservation map (null entry)
        out.write(structBytes, 0, structBytes.length);
        out.write(stringBytes, 0, stringBytes.length);
        while (out.size() < totalSize) out.write(0);
        return out.toByteArray();
    }

    // ── Structure helpers ─────────────────────────────────────────────────────

    private void beginNode(String name) {
        writeU32be(structBuf, FDT_BEGIN_NODE);
        for (byte b : name.getBytes(StandardCharsets.US_ASCII)) structBuf.write(b);
        structBuf.write(0);
        pad4(structBuf);
    }

    private void endNode() {
        writeU32be(structBuf, FDT_END_NODE);
    }

    private void propU32(String name, int value) {
        writeU32be(structBuf, FDT_PROP);
        writeU32be(structBuf, 4);
        writeU32be(structBuf, strOff(name));
        writeU32be(structBuf, value);
    }

    private void propU32s(String name, int... values) {
        writeU32be(structBuf, FDT_PROP);
        writeU32be(structBuf, values.length * 4);
        writeU32be(structBuf, strOff(name));
        for (int v : values) writeU32be(structBuf, v);
    }

    private void propStr(String name, String value) {
        byte[] bytes = (value + "\0").getBytes(StandardCharsets.UTF_8);
        writeU32be(structBuf, FDT_PROP);
        writeU32be(structBuf, bytes.length);
        writeU32be(structBuf, strOff(name));
        for (byte b : bytes) structBuf.write(b);
        pad4(structBuf);
    }

    private void propEmpty(String name) {
        writeU32be(structBuf, FDT_PROP);
        writeU32be(structBuf, 0);
        writeU32be(structBuf, strOff(name));
    }

    private int strOff(String name) {
        return strOffsets.computeIfAbsent(name, k -> {
            int off = stringBuf.size();
            for (byte b : k.getBytes(StandardCharsets.US_ASCII)) stringBuf.write(b);
            stringBuf.write(0);
            return off;
        });
    }

    // ── Binary helpers ────────────────────────────────────────────────────────

    private static void writeU32be(ByteArrayOutputStream buf, int val) {
        buf.write((val >>> 24) & 0xFF);
        buf.write((val >>> 16) & 0xFF);
        buf.write((val >>>  8) & 0xFF);
        buf.write( val         & 0xFF);
    }

    private static void pad4(ByteArrayOutputStream buf) {
        while (buf.size() % 4 != 0) buf.write(0);
    }
}
