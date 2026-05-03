package com.dev1lroot.mcmods.omnitech.blocks.logic.vm;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.*;
import java.util.Locale;

/**
 * Tiny assembler + virtual machine for the LogicMachine block.
 *
 * Instruction set:
 *   MOV  Rx, Ry|imm           — Rx = value
 *   SLP  Rx|imm               — sleep N ticks
 *   IN   Rx, portId           — Rx = GPIO input signal (0‑15)
 *   OUT  portId, Rx|imm       — GPIO output signal = value (0‑15)
 *   SET  dispId, x, y, color  — set display pixel (x,y) to 0xRRGGBB color
 *   RST  dispId               — clear all display pixels to black
 *   ADD  Rx, Ry|imm           — Rx = Rx + value
 *   SUB  Rx, Ry|imm           — Rx = Rx - value
 *   MUL  Rx, Ry|imm           — Rx = Rx * value
 *   DIV  Rx, Ry|imm           — Rx = Rx / value  (integer; ignored if value = 0)
 *   MOD  Rx, Ry|imm           — Rx = Rx % value  (ignored if value = 0)
 *   AND  Rx, Ry|imm           — Rx = Rx & value
 *   OR   Rx, Ry|imm           — Rx = Rx | value
 *   XOR  Rx, Ry|imm           — Rx = Rx ^ value
 *   NOT  Rx                   — Rx = ~Rx
 *   SHL  Rx, Ry|imm           — Rx = Rx << value
 *   SHR  Rx, Ry|imm           — Rx = Rx >> value  (arithmetic)
 *   INC  Rx                   — Rx = Rx + 1
 *   DEC  Rx                   — Rx = Rx - 1
 *   CMP  Rx, Ry|imm           — set flags
 *   JEQ/JNE/JGT/JLT label
 *   JMP  label
 *   HALT
 *   PEEK Rx, Ry|imm           — Rx = RAM[address]  (byte read)
 *   POKE Rx|imm, Ry|imm       — RAM[address] = value & 0xFF  (byte write)
 *   LDSC driveId, sector, dst — Load 512-byte sector from floppy drive into RAM at dst
 *
 * Labels:  name:   (standalone token ending with colon)
 * Comments: ;
 * Registers: R0..R3
 */
public class LogicVM {

    public interface GPIOAccess {
        int read(int portId);
        void write(int portId, int value);
    }

    public interface DisplayAccess {
        void setPixel(int displayId, int x, int y, int color);
        void reset(int displayId);
    }

    /** Flat byte-addressable RAM backed by connected RAM cards. */
    public interface RAMAccess {
        /** Read byte at address; returns 0 if out of range. */
        int read(int address);
        /** Write byte at address; ignored if out of range. */
        void write(int address, int value);
        /** Total RAM capacity in bytes. */
        int capacity();
    }

    /** Access to connected Floppy Drive blocks. */
    public interface FloppyAccess {
        /**
         * Load 512 bytes of sector {@code sector} from drive {@code driveId} into RAM
         * starting at {@code dstAddr}.  Returns {@code true} on success.
         */
        boolean loadSector(int driveId, int sector, int dstAddr);
    }

    public enum Op { MOV, SLP, IN, OUT, SET, RST,
                     ADD, SUB, MUL, DIV, MOD, AND, OR, XOR, NOT, SHL, SHR, INC, DEC,
                     CMP, JEQ, JNE, JGT, JLT, JMP, HALT,
                     PEEK, POKE, LDSC }

    public record Instruction(Op op, String a1, String a2, String a3, String a4) {
        public Instruction(Op op, String a1, String a2) { this(op, a1, a2, "", ""); }
    }

    // Runtime state
    public int[] regs = new int[4];
    public int  pc           = 0;
    public int  executingLine = 0;
    public int  sleepTicks   = 0;
    public int  cmpFlag      = 0;   // -1 / 0 / +1
    public boolean halted    = false;

    private List<Instruction>    program = new ArrayList<>();
    private Map<String, Integer> labels  = new HashMap<>();

    public int lineCount()       { return program.size(); }
    public int currentLine()     { return executingLine; }
    public boolean isEmpty()     { return program.isEmpty(); }
    public int registerCount()   { return regs.length; }

    public void setRegisterCount(int count) {
        count = Math.max(1, count);
        if (count != regs.length) regs = new int[count];
    }

    /** Compile source text; returns error string or {@code null} on success. */
    public String compile(String source) {
        List<Instruction>    newProg   = new ArrayList<>();
        Map<String, Integer> newLabels = new HashMap<>();

        if (source == null || source.isBlank()) {
            program = newProg;
            labels  = newLabels;
            reset();
            return null;
        }

        String[] lines = source.split("\n");
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            int ci = line.indexOf(';');
            if (ci >= 0) line = line.substring(0, ci);
            line = line.trim();
            if (line.isEmpty()) continue;

            // Label definition
            if (line.endsWith(":") && line.indexOf(' ') < 0) {
                newLabels.put(line.substring(0, line.length() - 1).toLowerCase(Locale.ROOT), newProg.size());
                continue;
            }

            String[] tok = line.split("[,\\s]+");
            if (tok.length == 0 || tok[0].isEmpty()) continue;

            Op op;
            try { op = Op.valueOf(tok[0].toUpperCase()); }
            catch (IllegalArgumentException e) {
                return "Line " + (n + 1) + ": unknown opcode '" + tok[0] + "'";
            }

            String a1 = tok.length > 1 ? tok[1] : "";
            String a2 = tok.length > 2 ? tok[2] : "";
            String a3 = tok.length > 3 ? tok[3] : "";
            String a4 = tok.length > 4 ? tok[4] : "";
            newProg.add(new Instruction(op, a1, a2, a3, a4));
        }

        program = newProg;
        labels  = newLabels;
        reset();
        return null;
    }

    /**
     * Execute one tick. Returns {@code true} if execution is ongoing,
     * {@code false} if halted.
     */
    public boolean tick(GPIOAccess gpio) { return tick(gpio, null, null, null); }

    public boolean tick(GPIOAccess gpio, DisplayAccess display) { return tick(gpio, display, null, null); }

    public boolean tick(GPIOAccess gpio, DisplayAccess display, RAMAccess ram, FloppyAccess floppy) {
        if (halted || program.isEmpty()) return false;

        if (pc >= program.size()) { halted = true; return false; }

        executingLine = pc;
        Instruction inst = program.get(pc++);
        switch (inst.op()) {
            case MOV  -> setReg(inst.a1(), val(inst.a2()));
            case SLP  -> { int t = val(inst.a1()); if (t > 0) sleepTicks = t; }
            case IN   -> {
                int sig = gpio != null ? gpio.read(parseId(inst.a2())) : 0;
                setReg(inst.a1(), sig);
            }
            case OUT  -> {
                if (gpio != null)
                    gpio.write(parseId(inst.a1()), Math.clamp(val(inst.a2()), 0, 15));
            }
            case SET  -> {
                if (display != null) {
                    int dispId = parseId(inst.a1());
                    int x      = Math.clamp(val(inst.a2()), 0, 15);
                    int y      = Math.clamp(val(inst.a3()), 0, 15);
                    int color  = valHex(inst.a4()) & 0xFFFFFF;
                    display.setPixel(dispId, x, y, color);
                }
            }
            case RST  -> {
                if (display != null) display.reset(parseId(inst.a1()));
            }
            case ADD  -> setReg(inst.a1(), val(inst.a1()) + val(inst.a2()));
            case SUB  -> setReg(inst.a1(), val(inst.a1()) - val(inst.a2()));
            case MUL  -> setReg(inst.a1(), val(inst.a1()) * val(inst.a2()));
            case DIV  -> { int d = val(inst.a2()); if (d != 0) setReg(inst.a1(), val(inst.a1()) / d); }
            case MOD  -> { int m = val(inst.a2()); if (m != 0) setReg(inst.a1(), val(inst.a1()) % m); }
            case AND  -> setReg(inst.a1(), val(inst.a1()) & val(inst.a2()));
            case OR   -> setReg(inst.a1(), val(inst.a1()) | val(inst.a2()));
            case XOR  -> setReg(inst.a1(), val(inst.a1()) ^ val(inst.a2()));
            case NOT  -> setReg(inst.a1(), ~val(inst.a1()));
            case SHL  -> setReg(inst.a1(), val(inst.a1()) << val(inst.a2()));
            case SHR  -> setReg(inst.a1(), val(inst.a1()) >> val(inst.a2()));
            case INC  -> setReg(inst.a1(), val(inst.a1()) + 1);
            case DEC  -> setReg(inst.a1(), val(inst.a1()) - 1);
            case CMP  -> cmpFlag = Integer.compare(val(inst.a1()), val(inst.a2()));
            case JEQ  -> { if (cmpFlag == 0) jump(inst.a1()); }
            case JNE  -> { if (cmpFlag != 0) jump(inst.a1()); }
            case JGT  -> { if (cmpFlag >  0) jump(inst.a1()); }
            case JLT  -> { if (cmpFlag <  0) jump(inst.a1()); }
            case JMP  -> jump(inst.a1());
            case HALT -> halted = true;
            case PEEK -> {
                int addr = val(inst.a2());
                setReg(inst.a1(), ram != null ? ram.read(addr) : 0);
            }
            case POKE -> {
                int addr = val(inst.a1());
                int bval = val(inst.a2()) & 0xFF;
                if (ram != null) ram.write(addr, bval);
            }
            case LDSC -> {
                int driveId = val(inst.a1());
                int sector  = val(inst.a2());
                int dstAddr = val(inst.a3());
                if (floppy != null) floppy.loadSector(driveId, sector, dstAddr);
            }
        }
        return !halted;
    }

    public void reset() {
        pc = 0; executingLine = 0; sleepTicks = 0; cmpFlag = 0; halted = false;
        Arrays.fill(regs, 0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int val(String s) {
        if (s == null || s.isEmpty()) return 0;
        if (s.length() >= 2 && Character.toUpperCase(s.charAt(0)) == 'R') {
            try {
                int i = Integer.parseInt(s.substring(1));
                return (i >= 0 && i < regs.length) ? regs[i] : 0;
            } catch (NumberFormatException ignored) {}
        }
        try {
            if (s.startsWith("0x") || s.startsWith("0X"))
                return (int) Long.parseLong(s.substring(2), 16);
            return Integer.parseInt(s);
        } catch (NumberFormatException e) { return 0; }
    }

    private int valHex(String s) { return val(s); }

    private void setReg(String s, int v) {
        if (s == null || s.isEmpty()) return;
        if (s.length() >= 2 && Character.toUpperCase(s.charAt(0)) == 'R') {
            try {
                int i = Integer.parseInt(s.substring(1));
                if (i >= 0 && i < regs.length) regs[i] = v;
            } catch (NumberFormatException ignored) {}
        }
    }

    private int parseId(String s) {
        try { return Integer.parseUnsignedInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private void jump(String label) {
        Integer t = labels.get(label.toLowerCase(Locale.ROOT));
        if (t != null) pc = t;
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    public void saveState(ValueOutput out) {
        out.putInt("PC", pc);
        out.putInt("ExecLine", executingLine);
        out.putInt("Sleep", sleepTicks);
        out.putInt("CmpFlag", cmpFlag);
        out.putBoolean("Halted", halted);
        for (int i = 0; i < regs.length; i++) out.putInt("R" + i, regs[i]);
    }

    public void loadState(ValueInput in) {
        pc            = in.getIntOr("PC", 0);
        executingLine = in.getIntOr("ExecLine", 0);
        sleepTicks    = in.getIntOr("Sleep", 0);
        cmpFlag       = in.getIntOr("CmpFlag", 0);
        halted        = in.getBooleanOr("Halted", false);
        for (int i = 0; i < regs.length; i++) regs[i] = in.getIntOr("R" + i, 0);
    }
}
