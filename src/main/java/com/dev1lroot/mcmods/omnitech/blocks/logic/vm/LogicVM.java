package com.dev1lroot.mcmods.omnitech.blocks.logic.vm;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.*;

/**
 * Tiny assembler + virtual machine for the LogicMachine block.
 *
 * Instruction set:
 *   MOV  Rx, Ry|imm      — Rx = value
 *   SLP  Rx|imm          — sleep N ticks
 *   IN   Rx, portId      — Rx = GPIO input signal (0‑15)
 *   OUT  portId, Rx|imm  — GPIO output signal = value (0‑15)
 *   CMP  Rx, Ry|imm      — set flags
 *   JEQ/JNE/JGT/JLT label
 *   JMP  label
 *   HALT
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

    public enum Op { MOV, SLP, IN, OUT, CMP, JEQ, JNE, JGT, JLT, JMP, HALT }

    public record Instruction(Op op, String a1, String a2) {}

    // Runtime state
    public final int[] regs = new int[4];
    public int  pc         = 0;
    public int  sleepTicks = 0;
    public int  cmpFlag    = 0;   // -1 / 0 / +1
    public boolean halted  = false;

    private List<Instruction>    program = new ArrayList<>();
    private Map<String, Integer> labels  = new HashMap<>();

    public int lineCount()   { return program.size(); }
    public int currentLine() { return pc; }
    public boolean isEmpty() { return program.isEmpty(); }

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
                newLabels.put(line.substring(0, line.length() - 1), newProg.size());
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
            newProg.add(new Instruction(op, a1, a2));
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
    public boolean tick(GPIOAccess gpio) {
        if (halted || program.isEmpty()) return false;

        if (sleepTicks > 0) { sleepTicks--; return true; }

        if (pc >= program.size()) { halted = true; return false; }

        Instruction inst = program.get(pc++);
        switch (inst.op()) {
            case MOV  -> setReg(inst.a1(), val(inst.a2()));
            case SLP  -> { int t = val(inst.a1()); if (t > 0) sleepTicks = t - 1; }
            case IN   -> {
                int sig = gpio != null ? gpio.read(parseId(inst.a2())) : 0;
                setReg(inst.a1(), sig);
            }
            case OUT  -> {
                if (gpio != null)
                    gpio.write(parseId(inst.a1()), Math.clamp(val(inst.a2()), 0, 15));
            }
            case CMP  -> cmpFlag = Integer.compare(val(inst.a1()), val(inst.a2()));
            case JEQ  -> { if (cmpFlag == 0) jump(inst.a1()); }
            case JNE  -> { if (cmpFlag != 0) jump(inst.a1()); }
            case JGT  -> { if (cmpFlag >  0) jump(inst.a1()); }
            case JLT  -> { if (cmpFlag <  0) jump(inst.a1()); }
            case JMP  -> jump(inst.a1());
            case HALT -> halted = true;
        }
        return !halted;
    }

    public void reset() {
        pc = 0; sleepTicks = 0; cmpFlag = 0; halted = false;
        Arrays.fill(regs, 0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int val(String s) {
        if (s == null || s.isEmpty()) return 0;
        if (Character.toUpperCase(s.charAt(0)) == 'R' && s.length() == 2) {
            int i = s.charAt(1) - '0';
            return (i >= 0 && i < 4) ? regs[i] : 0;
        }
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private void setReg(String s, int v) {
        if (s == null || s.isEmpty()) return;
        if (Character.toUpperCase(s.charAt(0)) == 'R' && s.length() == 2) {
            int i = s.charAt(1) - '0';
            if (i >= 0 && i < 4) regs[i] = v;
        }
    }

    private int parseId(String s) {
        try { return Integer.parseUnsignedInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private void jump(String label) {
        Integer t = labels.get(label);
        if (t != null) pc = t;
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    public void saveState(ValueOutput out) {
        out.putInt("PC", pc);
        out.putInt("Sleep", sleepTicks);
        out.putInt("CmpFlag", cmpFlag);
        out.putBoolean("Halted", halted);
        for (int i = 0; i < 4; i++) out.putInt("R" + i, regs[i]);
    }

    public void loadState(ValueInput in) {
        pc         = in.getIntOr("PC", 0);
        sleepTicks = in.getIntOr("Sleep", 0);
        cmpFlag    = in.getIntOr("CmpFlag", 0);
        halted     = in.getBooleanOr("Halted", false);
        for (int i = 0; i < 4; i++) regs[i] = in.getIntOr("R" + i, 0);
    }
}
