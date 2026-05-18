/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.vm;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.*;

/**
 * RISC-V RV32GC virtual machine for the LogicMachine block.
 * Extensions: I (base integer), M (multiply/divide), A (atomics),
 *             F (single-precision float), D (double-precision float),
 *             C (compressed aliases), Zicsr.
 *
 * Integer registers: x0 (zero, hardwired 0) … x31, ABI aliases.
 *   zero ra sp gp tp t0-t2 s0/fp s1 a0-a7 s2-s11 t3-t6
 *
 * Float/double registers: f0-f31, ABI aliases.
 *   ft0-ft7, fs0-fs11, fa0-fa7, ft8-ft11
 *   Stored as 64-bit doubles. Single-precision values are NaN-boxed
 *   (upper 32 bits = 0xFFFFFFFF); F instructions that read a register
 *   whose upper 32 bits are not all-ones return the canonical float NaN.
 *
 * CSRs (Zicsr): fflags=0x001  frm=0x002  fcsr=0x003
 *
 * Memory: flat byte-addressed RAM from connected RAM cards.
 *   GPIO MMIO at 0xF0000000 + portId*4  (accessible via LW/SW).
 *
 * Peripherals via ECALL — set a7 to syscall number, args in a0-a5:
 *   0  HALT
 *   1  SLP        a0=ticks
 *   2  RND        a0=min, a1=max  →  a0=result
 *  10  GPIO_OUT   a0=portId, a1=value(0-15)
 *  11  GPIO_IN    a0=portId  →  a0=signal
 *  20  DISP_SET   a0=id, a1=x, a2=y, a3=0xRRGGBB
 *  21  DISP_RST   a0=id
 *  22  DISP_DIM   a0=id  →  a0=width, a1=height
 *  23  DISP_LINE  a0=id, a1=x1, a2=y1, a3=x2, a4=y2, a5=color
 *  24  DISP_RECT  a0=id, a1=x, a2=y, a3=w, a4=h, a5=color
 *  25  DISP_BLIT  a0=id, a1=x, a2=y, a3=w, a4=h, a5=ramAddr
 *  30  FLOPPY     a0=driveId, a1=sector, a2=dstAddr  →  a0=1 on success
 *
 * Assembly syntax: standard RV32I/M/A/F/D mnemonics + ABI names.
 * C extension: c.add c.mv c.lw c.jal c.beqz c.fld c.fsd … aliases.
 * Pseudo-instructions: nop mv neg not seqz snez sltz sgtz li la
 *   beqz bnez blez bgez bltz bgtz j jr ret call tail halt
 *   fmv.s fabs.s fneg.s  fmv.d fabs.d fneg.d
 *   frcsr fscsr frrm fsrm frflags fsflags fsrmi
 * AMO ordering suffixes (.aq .rl .aqrl) accepted and ignored.
 * Comments: # or ;   Labels: name:
 */
public class LogicVM {

    // ── Peripheral interfaces ─────────────────────────────────────────────────

    public interface GPIOAccess {
        int  read(int portId);
        void write(int portId, int value);
    }

    public interface DisplayAccess {
        void setPixel(int displayId, int x, int y, int color);
        void reset(int displayId);
        int  getWidth(int displayId);
        int  getHeight(int displayId);
        void drawLine(int displayId, int x1, int y1, int x2, int y2, int color);
        void fillRect(int displayId, int x, int y, int w, int h, int color);
        /** pixels[row*w+col] = 0x00RRGGBB */
        void blit(int displayId, int x, int y, int w, int h, int[] pixels);
    }

    /** Flat byte-addressable RAM backed by connected RAM cards. */
    public interface RAMAccess {
        int  read(int address);
        void write(int address, int value);
        int  capacity();
    }

    /** Access to connected Floppy Drive blocks. */
    public interface FloppyAccess {
        boolean loadSector(int driveId, int sector, int dstAddr);
    }

    // ── Internal opcode set ───────────────────────────────────────────────────

    private enum Op {
        // RV32I — R-type
        ADD, SUB, AND, OR, XOR, SLL, SRL, SRA, SLT, SLTU,
        // RV32M — multiply/divide
        MUL, MULH, MULHSU, MULHU, DIV, DIVU, REM, REMU,
        // RV32I — I-type arithmetic
        ADDI, ANDI, ORI, XORI, SLLI, SRLI, SRAI, SLTI, SLTIU,
        // RV32I — Loads
        LB, LH, LW, LBU, LHU,
        // RV32I — Stores
        SB, SH, SW,
        // RV32I — Branches
        BEQ, BNE, BLT, BGE, BLTU, BGEU,
        // RV32I — Jumps
        JAL, JALR,
        // RV32I — Upper immediate
        LUI, AUIPC,
        // RV32I — System
        ECALL, EBREAK, FENCE,
        // RV32A — Atomics
        LR_W, SC_W,
        AMOSWAP_W, AMOADD_W, AMOXOR_W, AMOAND_W, AMOOR_W,
        AMOMIN_W,  AMOMAX_W,  AMOMINU_W, AMOMAXU_W,
        // RV32F — Float load/store  (rd/rs2=freg index, rs1=int base, imm=offset)
        FLW, FSW,
        // RV32F — FMA  (rd/rs1/rs2=freg, imm=rs3 freg index)
        FMADD_S, FMSUB_S, FNMSUB_S, FNMADD_S,
        // RV32F — Arithmetic
        FADD_S, FSUB_S, FMUL_S, FDIV_S, FSQRT_S,
        // RV32F — Sign injection
        FSGNJ_S, FSGNJN_S, FSGNJX_S,
        // RV32F — Min/max
        FMIN_S, FMAX_S,
        // RV32F — Conversions  (W.S: rd=int rs1=freg; S.W: rd=freg rs1=int)
        FCVT_W_S, FCVT_WU_S, FCVT_S_W, FCVT_S_WU,
        // RV32F — Move  (X.W: rd=int rs1=freg; W.X: rd=freg rs1=int)
        FMV_X_W, FMV_W_X,
        // RV32F — Compare  (rd=int, rs1/rs2=freg)
        FEQ_S, FLT_S, FLE_S,
        // RV32F — Classify  (rd=int, rs1=freg)
        FCLASS_S,
        // RV32D — Double load/store  (rd/rs2=freg, rs1=int base, imm=offset)
        FLD, FSD,
        // RV32D — FMA  (rd/rs1/rs2=freg, imm=rs3 freg index)
        FMADD_D, FMSUB_D, FNMSUB_D, FNMADD_D,
        // RV32D — Arithmetic
        FADD_D, FSUB_D, FMUL_D, FDIV_D, FSQRT_D,
        // RV32D — Sign injection
        FSGNJ_D, FSGNJN_D, FSGNJX_D,
        // RV32D — Min/max
        FMIN_D, FMAX_D,
        // RV32D — Conversions
        FCVT_W_D, FCVT_WU_D,   // double→int  (rd=int, rs1=freg)
        FCVT_D_W, FCVT_D_WU,   // int→double  (rd=freg, rs1=int)
        FCVT_S_D,               // double→single NaN-boxed  (rd=freg, rs1=freg)
        FCVT_D_S,               // NaN-unboxed single→double (rd=freg, rs1=freg)
        // RV32D — Compare  (rd=int, rs1/rs2=freg)
        FEQ_D, FLT_D, FLE_D,
        // RV32D — Classify  (rd=int, rs1=freg)
        FCLASS_D,
        // Zicsr  (imm=CSR addr; for *I forms rs2=uimm5)
        CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI
    }

    /**
     * Compiled instruction. imm stores:
     *   - FMA ops: rs3 freg index.
     *   - CSR ops: CSR address; *I forms use rs2 for uimm5.
     *   - Branches/JAL: absolute target instruction index.
     *   - Otherwise: sign-extended immediate.
     */
    private record Instruction(Op op, int rd, int rs1, int rs2, int imm, int srcLine) {}

    // ── Register files ────────────────────────────────────────────────────────

    public int[]    regs          = new int[32];    // x0 hardwired to 0
    public double[] fregs         = new double[32]; // f0-f31, 64-bit; F ops use NaN-boxing
    public int      fcsr          = 0;              // bits 0-4: fflags, bits 5-7: frm
    public int      pc            = 0;
    public int      executingLine = 0;
    public int      sleepTicks    = 0;
    public boolean  halted        = false;

    private int lrReservation = -1; // LR/SC reservation address (-1 = none)

    private List<Instruction> program = new ArrayList<>();

    // ── ABI register name maps ────────────────────────────────────────────────

    private static final Map<String, Integer> REG_MAP;
    private static final Map<String, Integer> FREG_MAP;
    private static final Map<String, Integer> CSR_MAP;

    static {
        REG_MAP = new HashMap<>(64);
        for (int i = 0; i < 32; i++) REG_MAP.put("x" + i, i);
        String[] iabi = {
            "zero","ra","sp","gp","tp","t0","t1","t2",
            "s0","s1","a0","a1","a2","a3","a4","a5",
            "a6","a7","s2","s3","s4","s5","s6","s7",
            "s8","s9","s10","s11","t3","t4","t5","t6"
        };
        for (int i = 0; i < iabi.length; i++) REG_MAP.put(iabi[i], i);
        REG_MAP.put("fp", 8);

        FREG_MAP = new HashMap<>(64);
        for (int i = 0; i < 32; i++) FREG_MAP.put("f" + i, i);
        String[] fabi = {
            "ft0","ft1","ft2","ft3","ft4","ft5","ft6","ft7",
            "fs0","fs1","fa0","fa1","fa2","fa3","fa4","fa5",
            "fa6","fa7","fs2","fs3","fs4","fs5","fs6","fs7",
            "fs8","fs9","fs10","fs11","ft8","ft9","ft10","ft11"
        };
        for (int i = 0; i < fabi.length; i++) FREG_MAP.put(fabi[i], i);

        CSR_MAP = new HashMap<>(16);
        CSR_MAP.put("fflags",  0x001);
        CSR_MAP.put("frm",     0x002);
        CSR_MAP.put("fcsr",    0x003);
        CSR_MAP.put("cycle",   0xC00);
        CSR_MAP.put("time",    0xC01);
        CSR_MAP.put("instret", 0xC02);
    }

    // ── NaN-boxing helpers (F ops on the shared 64-bit register file) ─────────

    /** Read a single-precision value from a 64-bit register (NaN-unbox). */
    private float fread(int r) {
        long bits = Double.doubleToRawLongBits(fregs[r]);
        // Upper 32 bits must all be 1s for a valid NaN-boxed float
        return (bits >>> 32) == 0xFFFFFFFFL
            ? Float.intBitsToFloat((int) bits)
            : Float.intBitsToFloat(0x7FC00000); // canonical quiet NaN
    }

    /** Write a single-precision value into a 64-bit register (NaN-box it). */
    private void fwrite(int r, float f) {
        fregs[r] = Double.longBitsToDouble(
            0xFFFFFFFF00000000L | Integer.toUnsignedLong(Float.floatToRawIntBits(f)));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public int     lineCount()     { return program.size(); }
    public int     currentLine()   { return executingLine; }
    public boolean isEmpty()       { return program.isEmpty(); }
    public int     registerCount() { return 32; }
    /** No-op — RISC-V always has 32 integer + 32 float/double registers. */
    public void    setRegisterCount(int n) {}

    // ── Assembler ─────────────────────────────────────────────────────────────

    /** Assemble source text. Returns an error string, or null on success. */
    public String compile(String source) {
        if (source == null || source.isBlank()) {
            program = new ArrayList<>();
            reset();
            return null;
        }

        String[] lines = source.replace(';', '\n').split("\n");

        // Pass 1 – collect label → instruction-index mapping.
        Map<String, Integer> labels = new HashMap<>();
        int idx = 0;
        for (String raw : lines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;
            if (isLabel(line)) {
                labels.put(line.substring(0, line.length() - 1).toLowerCase(Locale.ROOT), idx);
            } else {
                idx++;
            }
        }

        // Pass 2 – compile instructions.
        List<Instruction> newProg = new ArrayList<>(idx);
        for (int n = 0; n < lines.length; n++) {
            String line = stripComment(lines[n]).trim();
            if (line.isEmpty() || isLabel(line)) continue;
            String[] tok = line.split("[,\\s]+");
            if (tok.length == 0 || tok[0].isEmpty()) continue;
            try {
                newProg.add(compileLine(tok[0].toLowerCase(Locale.ROOT), tok, labels, n + 1));
            } catch (AsmException e) {
                return "Line " + (n + 1) + ": " + e.getMessage();
            }
        }

        program = newProg;
        reset();
        return null;
    }

    private static boolean isLabel(String line) {
        return line.endsWith(":") && line.indexOf(' ') < 0 && line.indexOf(',') < 0;
    }

    private static String stripComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '#') return line.substring(0, i);
        }
        return line;
    }

    private static class AsmException extends Exception {
        AsmException(String msg) { super(msg); }
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private Instruction compileLine(String mn, String[] tok,
            Map<String, Integer> labels, int srcLine) throws AsmException {

        // Strip memory-ordering suffixes from atomics (.aq .rl .aqrl)
        String nm = mn;
        if (nm.startsWith("lr.") || nm.startsWith("sc.") || nm.startsWith("amo")) {
            nm = nm.replaceAll("\\.(aqrl|aq|rl)$", "");
        }

        return switch (nm) {
            // ── RV32I R-type ──────────────────────────────────────────────────
            case "add"   -> rtype(Op.ADD,  tok, srcLine);
            case "sub"   -> rtype(Op.SUB,  tok, srcLine);
            case "and"   -> rtype(Op.AND,  tok, srcLine);
            case "or"    -> rtype(Op.OR,   tok, srcLine);
            case "xor"   -> rtype(Op.XOR,  tok, srcLine);
            case "sll"   -> rtype(Op.SLL,  tok, srcLine);
            case "srl"   -> rtype(Op.SRL,  tok, srcLine);
            case "sra"   -> rtype(Op.SRA,  tok, srcLine);
            case "slt"   -> rtype(Op.SLT,  tok, srcLine);
            case "sltu"  -> rtype(Op.SLTU, tok, srcLine);

            // ── RV32M ─────────────────────────────────────────────────────────
            case "mul"    -> rtype(Op.MUL,    tok, srcLine);
            case "mulh"   -> rtype(Op.MULH,   tok, srcLine);
            case "mulhsu" -> rtype(Op.MULHSU, tok, srcLine);
            case "mulhu"  -> rtype(Op.MULHU,  tok, srcLine);
            case "div"    -> rtype(Op.DIV,    tok, srcLine);
            case "divu"   -> rtype(Op.DIVU,   tok, srcLine);
            case "rem"    -> rtype(Op.REM,    tok, srcLine);
            case "remu"   -> rtype(Op.REMU,   tok, srcLine);

            // ── I-type arithmetic ─────────────────────────────────────────────
            case "addi"  -> itype(Op.ADDI,  tok, srcLine);
            case "andi"  -> itype(Op.ANDI,  tok, srcLine);
            case "ori"   -> itype(Op.ORI,   tok, srcLine);
            case "xori"  -> itype(Op.XORI,  tok, srcLine);
            case "slli"  -> itype(Op.SLLI,  tok, srcLine);
            case "srli"  -> itype(Op.SRLI,  tok, srcLine);
            case "srai"  -> itype(Op.SRAI,  tok, srcLine);
            case "slti"  -> itype(Op.SLTI,  tok, srcLine);
            case "sltiu" -> itype(Op.SLTIU, tok, srcLine);

            // ── Loads ─────────────────────────────────────────────────────────
            case "lb"  -> load(Op.LB,  tok, srcLine);
            case "lh"  -> load(Op.LH,  tok, srcLine);
            case "lw"  -> load(Op.LW,  tok, srcLine);
            case "lbu" -> load(Op.LBU, tok, srcLine);
            case "lhu" -> load(Op.LHU, tok, srcLine);

            // ── Stores ────────────────────────────────────────────────────────
            case "sb" -> store(Op.SB, tok, srcLine);
            case "sh" -> store(Op.SH, tok, srcLine);
            case "sw" -> store(Op.SW, tok, srcLine);

            // ── Branches ──────────────────────────────────────────────────────
            case "beq"  -> branch(Op.BEQ,  tok, labels, srcLine);
            case "bne"  -> branch(Op.BNE,  tok, labels, srcLine);
            case "blt"  -> branch(Op.BLT,  tok, labels, srcLine);
            case "bge"  -> branch(Op.BGE,  tok, labels, srcLine);
            case "bltu" -> branch(Op.BLTU, tok, labels, srcLine);
            case "bgeu" -> branch(Op.BGEU, tok, labels, srcLine);

            // ── JAL ───────────────────────────────────────────────────────────
            case "jal" -> {
                if (tok.length == 2)
                    yield new Instruction(Op.JAL, 1, 0, 0, resolveLabel(tok[1], labels, srcLine), srcLine);
                yield new Instruction(Op.JAL, reg(tok[1], srcLine), 0, 0,
                        resolveLabel(tok[2], labels, srcLine), srcLine);
            }

            // ── JALR ──────────────────────────────────────────────────────────
            case "jalr" -> {
                if (tok.length == 2)
                    yield new Instruction(Op.JALR, 0, reg(tok[1], srcLine), 0, 0, srcLine);
                if (tok.length == 3) {
                    if (tok[2].contains("(")) {
                        int[] ob = parseOffsetBase(tok[2], srcLine);
                        yield new Instruction(Op.JALR, reg(tok[1], srcLine), ob[1], 0, ob[0], srcLine);
                    }
                    yield new Instruction(Op.JALR, reg(tok[1], srcLine), reg(tok[2], srcLine), 0, 0, srcLine);
                }
                yield new Instruction(Op.JALR, reg(tok[1], srcLine), reg(tok[2], srcLine), 0,
                        parseImm(tok[3], srcLine), srcLine);
            }

            // ── Upper immediate ───────────────────────────────────────────────
            case "lui"   -> new Instruction(Op.LUI,   reg(tok[1], srcLine), 0, 0, parseImm(tok[2], srcLine), srcLine);
            case "auipc" -> new Instruction(Op.AUIPC, reg(tok[1], srcLine), 0, 0, parseImm(tok[2], srcLine), srcLine);

            // ── System ────────────────────────────────────────────────────────
            case "ecall"  -> new Instruction(Op.ECALL,  0, 0, 0, 0, srcLine);
            case "ebreak" -> new Instruction(Op.EBREAK, 0, 0, 0, 0, srcLine);
            case "fence"  -> new Instruction(Op.FENCE,  0, 0, 0, 0, srcLine);

            // ── RV32I Pseudo-instructions ─────────────────────────────────────
            case "nop"  -> new Instruction(Op.ADDI,  0, 0, 0, 0,   srcLine);
            case "mv"   -> new Instruction(Op.ADDI,  reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 0,  srcLine);
            case "neg"  -> new Instruction(Op.SUB,   reg(tok[1],srcLine), 0, reg(tok[2],srcLine), 0,  srcLine);
            case "not"  -> new Instruction(Op.XORI,  reg(tok[1],srcLine), reg(tok[2],srcLine), 0, -1, srcLine);
            case "seqz" -> new Instruction(Op.SLTIU, reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 1,  srcLine);
            case "snez" -> new Instruction(Op.SLTU,  reg(tok[1],srcLine), 0, reg(tok[2],srcLine), 0,  srcLine);
            case "sltz" -> new Instruction(Op.SLT,   reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 0,  srcLine);
            case "sgtz" -> new Instruction(Op.SLT,   reg(tok[1],srcLine), 0, reg(tok[2],srcLine), 0,  srcLine);
            case "li"   -> new Instruction(Op.ADDI,  reg(tok[1],srcLine), 0, 0, parseImm(tok[2],srcLine), srcLine);
            case "la"   -> {
                int target = resolveLabel(tok[2], labels, srcLine);
                yield new Instruction(Op.ADDI, reg(tok[1],srcLine), 0, 0, target * 4, srcLine);
            }
            case "beqz" -> new Instruction(Op.BEQ, 0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);
            case "bnez" -> new Instruction(Op.BNE, 0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);
            case "bgez" -> new Instruction(Op.BGE, 0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);
            case "bltz" -> new Instruction(Op.BLT, 0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);
            case "blez" -> new Instruction(Op.BGE, 0, 0, reg(tok[1],srcLine), resolveLabel(tok[2],labels,srcLine), srcLine);
            case "bgtz" -> new Instruction(Op.BLT, 0, 0, reg(tok[1],srcLine), resolveLabel(tok[2],labels,srcLine), srcLine);
            case "j"    -> new Instruction(Op.JAL,  0, 0, 0, resolveLabel(tok[1],labels,srcLine), srcLine);
            case "jr"   -> new Instruction(Op.JALR, 0, reg(tok[1],srcLine), 0, 0, srcLine);
            case "ret"  -> new Instruction(Op.JALR, 0, 1, 0, 0, srcLine);
            case "call" -> new Instruction(Op.JAL,  1, 0, 0, resolveLabel(tok[1],labels,srcLine), srcLine);
            case "tail" -> new Instruction(Op.JAL,  0, 0, 0, resolveLabel(tok[1],labels,srcLine), srcLine);
            case "halt" -> new Instruction(Op.EBREAK, 0, 0, 0, 0, srcLine);

            // ── RV32A — Atomics (ordering suffixes already stripped) ──────────
            case "lr.w" -> {
                int[] ob = parseOffsetBase(tok[2], srcLine);
                yield new Instruction(Op.LR_W, reg(tok[1],srcLine), ob[1], 0, 0, srcLine);
            }
            case "sc.w" -> {
                int[] ob = parseOffsetBase(tok[3], srcLine);
                yield new Instruction(Op.SC_W, reg(tok[1],srcLine), ob[1], reg(tok[2],srcLine), 0, srcLine);
            }
            case "amoswap.w" -> amoInstr(Op.AMOSWAP_W, tok, srcLine);
            case "amoadd.w"  -> amoInstr(Op.AMOADD_W,  tok, srcLine);
            case "amoxor.w"  -> amoInstr(Op.AMOXOR_W,  tok, srcLine);
            case "amoand.w"  -> amoInstr(Op.AMOAND_W,  tok, srcLine);
            case "amoor.w"   -> amoInstr(Op.AMOOR_W,   tok, srcLine);
            case "amomin.w"  -> amoInstr(Op.AMOMIN_W,  tok, srcLine);
            case "amomax.w"  -> amoInstr(Op.AMOMAX_W,  tok, srcLine);
            case "amominu.w" -> amoInstr(Op.AMOMINU_W, tok, srcLine);
            case "amomaxu.w" -> amoInstr(Op.AMOMAXU_W, tok, srcLine);

            // ── RV32F — Float load/store ──────────────────────────────────────
            case "flw" -> {
                int fd = freg(tok[1], srcLine);
                int[] ob = parseOffsetBase(tok[2], srcLine);
                yield new Instruction(Op.FLW, fd, ob[1], 0, ob[0], srcLine);
            }
            case "fsw" -> {
                int fs2 = freg(tok[1], srcLine);
                int[] ob = parseOffsetBase(tok[2], srcLine);
                yield new Instruction(Op.FSW, 0, ob[1], fs2, ob[0], srcLine);
            }

            // ── RV32F — FMA (optional 5th rm token ignored) ───────────────────
            case "fmadd.s"  -> fma(Op.FMADD_S,  tok, srcLine);
            case "fmsub.s"  -> fma(Op.FMSUB_S,  tok, srcLine);
            case "fnmsub.s" -> fma(Op.FNMSUB_S, tok, srcLine);
            case "fnmadd.s" -> fma(Op.FNMADD_S, tok, srcLine);

            // ── RV32F — Arithmetic ────────────────────────────────────────────
            case "fadd.s"  -> frrr(Op.FADD_S,  tok, srcLine);
            case "fsub.s"  -> frrr(Op.FSUB_S,  tok, srcLine);
            case "fmul.s"  -> frrr(Op.FMUL_S,  tok, srcLine);
            case "fdiv.s"  -> frrr(Op.FDIV_S,  tok, srcLine);
            case "fsqrt.s" -> new Instruction(Op.FSQRT_S, freg(tok[1],srcLine), freg(tok[2],srcLine), 0, 0, srcLine);

            // ── RV32F — Sign injection ────────────────────────────────────────
            case "fsgnj.s"  -> frrr(Op.FSGNJ_S,  tok, srcLine);
            case "fsgnjn.s" -> frrr(Op.FSGNJN_S, tok, srcLine);
            case "fsgnjx.s" -> frrr(Op.FSGNJX_S, tok, srcLine);

            // ── RV32F — Min/max ───────────────────────────────────────────────
            case "fmin.s" -> frrr(Op.FMIN_S, tok, srcLine);
            case "fmax.s" -> frrr(Op.FMAX_S, tok, srcLine);

            // ── RV32F — Conversions ───────────────────────────────────────────
            case "fcvt.w.s"  -> new Instruction(Op.FCVT_W_S,  reg(tok[1],srcLine),  freg(tok[2],srcLine), 0, 0, srcLine);
            case "fcvt.wu.s" -> new Instruction(Op.FCVT_WU_S, reg(tok[1],srcLine),  freg(tok[2],srcLine), 0, 0, srcLine);
            case "fcvt.s.w"  -> new Instruction(Op.FCVT_S_W,  freg(tok[1],srcLine), reg(tok[2],srcLine),  0, 0, srcLine);
            case "fcvt.s.wu" -> new Instruction(Op.FCVT_S_WU, freg(tok[1],srcLine), reg(tok[2],srcLine),  0, 0, srcLine);

            // ── RV32F — Move ──────────────────────────────────────────────────
            case "fmv.x.w" -> new Instruction(Op.FMV_X_W, reg(tok[1],srcLine),  freg(tok[2],srcLine), 0, 0, srcLine);
            case "fmv.w.x" -> new Instruction(Op.FMV_W_X, freg(tok[1],srcLine), reg(tok[2],srcLine),  0, 0, srcLine);

            // ── RV32F — Compare ───────────────────────────────────────────────
            case "feq.s" -> new Instruction(Op.FEQ_S, reg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[3],srcLine), 0, srcLine);
            case "flt.s" -> new Instruction(Op.FLT_S, reg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[3],srcLine), 0, srcLine);
            case "fle.s" -> new Instruction(Op.FLE_S, reg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[3],srcLine), 0, srcLine);

            // ── RV32F — Classify ──────────────────────────────────────────────
            case "fclass.s" -> new Instruction(Op.FCLASS_S, reg(tok[1],srcLine), freg(tok[2],srcLine), 0, 0, srcLine);

            // ── RV32F — Pseudo-instructions ───────────────────────────────────
            case "fmv.s"  -> new Instruction(Op.FSGNJ_S,  freg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[2],srcLine), 0, srcLine);
            case "fabs.s" -> new Instruction(Op.FSGNJX_S, freg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[2],srcLine), 0, srcLine);
            case "fneg.s" -> new Instruction(Op.FSGNJN_S, freg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[2],srcLine), 0, srcLine);

            // ── RV32D — Double load/store ─────────────────────────────────────
            case "fld" -> {
                int fd = freg(tok[1], srcLine);
                int[] ob = parseOffsetBase(tok[2], srcLine);
                yield new Instruction(Op.FLD, fd, ob[1], 0, ob[0], srcLine);
            }
            case "fsd" -> {
                int fs2 = freg(tok[1], srcLine);
                int[] ob = parseOffsetBase(tok[2], srcLine);
                yield new Instruction(Op.FSD, 0, ob[1], fs2, ob[0], srcLine);
            }

            // ── RV32D — FMA ───────────────────────────────────────────────────
            case "fmadd.d"  -> fma(Op.FMADD_D,  tok, srcLine);
            case "fmsub.d"  -> fma(Op.FMSUB_D,  tok, srcLine);
            case "fnmsub.d" -> fma(Op.FNMSUB_D, tok, srcLine);
            case "fnmadd.d" -> fma(Op.FNMADD_D, tok, srcLine);

            // ── RV32D — Arithmetic ────────────────────────────────────────────
            case "fadd.d"  -> frrr(Op.FADD_D,  tok, srcLine);
            case "fsub.d"  -> frrr(Op.FSUB_D,  tok, srcLine);
            case "fmul.d"  -> frrr(Op.FMUL_D,  tok, srcLine);
            case "fdiv.d"  -> frrr(Op.FDIV_D,  tok, srcLine);
            case "fsqrt.d" -> new Instruction(Op.FSQRT_D, freg(tok[1],srcLine), freg(tok[2],srcLine), 0, 0, srcLine);

            // ── RV32D — Sign injection ────────────────────────────────────────
            case "fsgnj.d"  -> frrr(Op.FSGNJ_D,  tok, srcLine);
            case "fsgnjn.d" -> frrr(Op.FSGNJN_D, tok, srcLine);
            case "fsgnjx.d" -> frrr(Op.FSGNJX_D, tok, srcLine);

            // ── RV32D — Min/max ───────────────────────────────────────────────
            case "fmin.d" -> frrr(Op.FMIN_D, tok, srcLine);
            case "fmax.d" -> frrr(Op.FMAX_D, tok, srcLine);

            // ── RV32D — Conversions ───────────────────────────────────────────
            case "fcvt.w.d"  -> new Instruction(Op.FCVT_W_D,  reg(tok[1],srcLine),  freg(tok[2],srcLine), 0, 0, srcLine);
            case "fcvt.wu.d" -> new Instruction(Op.FCVT_WU_D, reg(tok[1],srcLine),  freg(tok[2],srcLine), 0, 0, srcLine);
            case "fcvt.d.w"  -> new Instruction(Op.FCVT_D_W,  freg(tok[1],srcLine), reg(tok[2],srcLine),  0, 0, srcLine);
            case "fcvt.d.wu" -> new Instruction(Op.FCVT_D_WU, freg(tok[1],srcLine), reg(tok[2],srcLine),  0, 0, srcLine);
            case "fcvt.s.d"  -> new Instruction(Op.FCVT_S_D,  freg(tok[1],srcLine), freg(tok[2],srcLine), 0, 0, srcLine);
            case "fcvt.d.s"  -> new Instruction(Op.FCVT_D_S,  freg(tok[1],srcLine), freg(tok[2],srcLine), 0, 0, srcLine);

            // ── RV32D — Compare ───────────────────────────────────────────────
            case "feq.d" -> new Instruction(Op.FEQ_D, reg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[3],srcLine), 0, srcLine);
            case "flt.d" -> new Instruction(Op.FLT_D, reg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[3],srcLine), 0, srcLine);
            case "fle.d" -> new Instruction(Op.FLE_D, reg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[3],srcLine), 0, srcLine);

            // ── RV32D — Classify ──────────────────────────────────────────────
            case "fclass.d" -> new Instruction(Op.FCLASS_D, reg(tok[1],srcLine), freg(tok[2],srcLine), 0, 0, srcLine);

            // ── RV32D — Pseudo-instructions ───────────────────────────────────
            case "fmv.d"  -> new Instruction(Op.FSGNJ_D,  freg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[2],srcLine), 0, srcLine);
            case "fabs.d" -> new Instruction(Op.FSGNJX_D, freg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[2],srcLine), 0, srcLine);
            case "fneg.d" -> new Instruction(Op.FSGNJN_D, freg(tok[1],srcLine), freg(tok[2],srcLine), freg(tok[2],srcLine), 0, srcLine);

            // ── Zicsr ─────────────────────────────────────────────────────────
            case "csrrw"  -> new Instruction(Op.CSRRW,  reg(tok[1],srcLine), reg(tok[3],srcLine), 0, parseCsr(tok[2],srcLine), srcLine);
            case "csrrs"  -> new Instruction(Op.CSRRS,  reg(tok[1],srcLine), reg(tok[3],srcLine), 0, parseCsr(tok[2],srcLine), srcLine);
            case "csrrc"  -> new Instruction(Op.CSRRC,  reg(tok[1],srcLine), reg(tok[3],srcLine), 0, parseCsr(tok[2],srcLine), srcLine);
            case "csrrwi" -> new Instruction(Op.CSRRWI, reg(tok[1],srcLine), 0, parseImm(tok[3],srcLine) & 0x1F, parseCsr(tok[2],srcLine), srcLine);
            case "csrrsi" -> new Instruction(Op.CSRRSI, reg(tok[1],srcLine), 0, parseImm(tok[3],srcLine) & 0x1F, parseCsr(tok[2],srcLine), srcLine);
            case "csrrci" -> new Instruction(Op.CSRRCI, reg(tok[1],srcLine), 0, parseImm(tok[3],srcLine) & 0x1F, parseCsr(tok[2],srcLine), srcLine);

            // ── CSR / float pseudo-instructions ───────────────────────────────
            case "frcsr"   -> new Instruction(Op.CSRRS, reg(tok[1],srcLine), 0, 0, 0x003, srcLine);
            case "fscsr"   -> {
                if (tok.length >= 3) yield new Instruction(Op.CSRRW, reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 0x003, srcLine);
                yield new Instruction(Op.CSRRW, 0, reg(tok[1],srcLine), 0, 0x003, srcLine);
            }
            case "frrm"    -> new Instruction(Op.CSRRS, reg(tok[1],srcLine), 0, 0, 0x002, srcLine);
            case "fsrm"    -> {
                if (tok.length >= 3) yield new Instruction(Op.CSRRW, reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 0x002, srcLine);
                yield new Instruction(Op.CSRRW, 0, reg(tok[1],srcLine), 0, 0x002, srcLine);
            }
            case "frflags" -> new Instruction(Op.CSRRS, reg(tok[1],srcLine), 0, 0, 0x001, srcLine);
            case "fsflags" -> {
                if (tok.length >= 3) yield new Instruction(Op.CSRRW, reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 0x001, srcLine);
                yield new Instruction(Op.CSRRW, 0, reg(tok[1],srcLine), 0, 0x001, srcLine);
            }
            case "fsrmi" -> {
                if (tok.length >= 3) yield new Instruction(Op.CSRRWI, reg(tok[1],srcLine), 0, parseImm(tok[2],srcLine) & 0x1F, 0x002, srcLine);
                yield new Instruction(Op.CSRRWI, 0, 0, parseImm(tok[1],srcLine) & 0x1F, 0x002, srcLine);
            }

            // ── RV32C — Compressed aliases (Quadrant 0) ───────────────────────
            case "c.addi4spn" -> new Instruction(Op.ADDI, reg(tok[1],srcLine), 2, 0, parseImm(tok[2],srcLine), srcLine);
            case "c.lw"  -> load(Op.LW, tok, srcLine);
            case "c.sw"  -> store(Op.SW, tok, srcLine);
            case "c.flw" -> {
                int fd = freg(tok[1], srcLine);
                int[] ob = parseOffsetBase(tok[2], srcLine);
                yield new Instruction(Op.FLW, fd, ob[1], 0, ob[0], srcLine);
            }
            case "c.fsw" -> {
                int fs2 = freg(tok[1], srcLine);
                int[] ob = parseOffsetBase(tok[2], srcLine);
                yield new Instruction(Op.FSW, 0, ob[1], fs2, ob[0], srcLine);
            }
            case "c.fld" -> {
                int fd = freg(tok[1], srcLine);
                int[] ob = parseOffsetBase(tok[2], srcLine);
                yield new Instruction(Op.FLD, fd, ob[1], 0, ob[0], srcLine);
            }
            case "c.fsd" -> {
                int fs2 = freg(tok[1], srcLine);
                int[] ob = parseOffsetBase(tok[2], srcLine);
                yield new Instruction(Op.FSD, 0, ob[1], fs2, ob[0], srcLine);
            }

            // ── RV32C — Compressed aliases (Quadrant 1) ───────────────────────
            case "c.nop"      -> new Instruction(Op.ADDI, 0, 0, 0, 0, srcLine);
            case "c.addi"     -> new Instruction(Op.ADDI, reg(tok[1],srcLine), reg(tok[1],srcLine), 0, parseImm(tok[2],srcLine), srcLine);
            case "c.jal"      -> new Instruction(Op.JAL,  1, 0, 0, resolveLabel(tok[1],labels,srcLine), srcLine);
            case "c.li"       -> new Instruction(Op.ADDI, reg(tok[1],srcLine), 0, 0, parseImm(tok[2],srcLine), srcLine);
            case "c.addi16sp" -> new Instruction(Op.ADDI, 2, 2, 0, parseImm(tok[1],srcLine), srcLine);
            case "c.lui"      -> new Instruction(Op.LUI,  reg(tok[1],srcLine), 0, 0, parseImm(tok[2],srcLine), srcLine);
            case "c.srli"     -> new Instruction(Op.SRLI, reg(tok[1],srcLine), reg(tok[1],srcLine), 0, parseImm(tok[2],srcLine), srcLine);
            case "c.srai"     -> new Instruction(Op.SRAI, reg(tok[1],srcLine), reg(tok[1],srcLine), 0, parseImm(tok[2],srcLine), srcLine);
            case "c.andi"     -> new Instruction(Op.ANDI, reg(tok[1],srcLine), reg(tok[1],srcLine), 0, parseImm(tok[2],srcLine), srcLine);
            case "c.sub"      -> new Instruction(Op.SUB,  reg(tok[1],srcLine), reg(tok[1],srcLine), reg(tok[2],srcLine), 0, srcLine);
            case "c.xor"      -> new Instruction(Op.XOR,  reg(tok[1],srcLine), reg(tok[1],srcLine), reg(tok[2],srcLine), 0, srcLine);
            case "c.or"       -> new Instruction(Op.OR,   reg(tok[1],srcLine), reg(tok[1],srcLine), reg(tok[2],srcLine), 0, srcLine);
            case "c.and"      -> new Instruction(Op.AND,  reg(tok[1],srcLine), reg(tok[1],srcLine), reg(tok[2],srcLine), 0, srcLine);
            case "c.j"        -> new Instruction(Op.JAL,  0, 0, 0, resolveLabel(tok[1],labels,srcLine), srcLine);
            case "c.beqz"     -> new Instruction(Op.BEQ,  0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);
            case "c.bnez"     -> new Instruction(Op.BNE,  0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);

            // ── RV32C — Compressed aliases (Quadrant 2) ───────────────────────
            case "c.slli"  -> new Instruction(Op.SLLI, reg(tok[1],srcLine), reg(tok[1],srcLine), 0, parseImm(tok[2],srcLine), srcLine);
            case "c.lwsp"  -> {
                int rd2 = reg(tok[1], srcLine);
                if (tok[2].contains("(")) { int[] ob = parseOffsetBase(tok[2],srcLine); yield new Instruction(Op.LW, rd2, ob[1], 0, ob[0], srcLine); }
                yield new Instruction(Op.LW, rd2, 2, 0, parseImm(tok[2],srcLine), srcLine);
            }
            case "c.flwsp" -> {
                int fd = freg(tok[1], srcLine);
                if (tok[2].contains("(")) { int[] ob = parseOffsetBase(tok[2],srcLine); yield new Instruction(Op.FLW, fd, ob[1], 0, ob[0], srcLine); }
                yield new Instruction(Op.FLW, fd, 2, 0, parseImm(tok[2],srcLine), srcLine);
            }
            case "c.fldsp" -> {
                int fd = freg(tok[1], srcLine);
                if (tok[2].contains("(")) { int[] ob = parseOffsetBase(tok[2],srcLine); yield new Instruction(Op.FLD, fd, ob[1], 0, ob[0], srcLine); }
                yield new Instruction(Op.FLD, fd, 2, 0, parseImm(tok[2],srcLine), srcLine);
            }
            case "c.jr"    -> new Instruction(Op.JALR, 0, reg(tok[1],srcLine), 0, 0, srcLine);
            case "c.mv"    -> new Instruction(Op.ADDI, reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 0, srcLine);
            case "c.ebreak"-> new Instruction(Op.EBREAK, 0, 0, 0, 0, srcLine);
            case "c.jalr"  -> new Instruction(Op.JALR, 1, reg(tok[1],srcLine), 0, 0, srcLine);
            case "c.add"   -> new Instruction(Op.ADD,  reg(tok[1],srcLine), reg(tok[1],srcLine), reg(tok[2],srcLine), 0, srcLine);
            case "c.swsp"  -> {
                int rs2v = reg(tok[1], srcLine);
                if (tok[2].contains("(")) { int[] ob = parseOffsetBase(tok[2],srcLine); yield new Instruction(Op.SW, 0, ob[1], rs2v, ob[0], srcLine); }
                yield new Instruction(Op.SW, 0, 2, rs2v, parseImm(tok[2],srcLine), srcLine);
            }
            case "c.fswsp" -> {
                int fs2v = freg(tok[1], srcLine);
                if (tok[2].contains("(")) { int[] ob = parseOffsetBase(tok[2],srcLine); yield new Instruction(Op.FSW, 0, ob[1], fs2v, ob[0], srcLine); }
                yield new Instruction(Op.FSW, 0, 2, fs2v, parseImm(tok[2],srcLine), srcLine);
            }
            case "c.fsdsp" -> {
                int fs2v = freg(tok[1], srcLine);
                if (tok[2].contains("(")) { int[] ob = parseOffsetBase(tok[2],srcLine); yield new Instruction(Op.FSD, 0, ob[1], fs2v, ob[0], srcLine); }
                yield new Instruction(Op.FSD, 0, 2, fs2v, parseImm(tok[2],srcLine), srcLine);
            }

            default -> throw new AsmException("unknown instruction '" + tok[0] + "'");
        };
    }

    // ── Assembler helpers ─────────────────────────────────────────────────────

    private Instruction rtype(Op op, String[] tok, int src) throws AsmException {
        return new Instruction(op, reg(tok[1],src), reg(tok[2],src), reg(tok[3],src), 0, src);
    }
    private Instruction itype(Op op, String[] tok, int src) throws AsmException {
        return new Instruction(op, reg(tok[1],src), reg(tok[2],src), 0, parseImm(tok[3],src), src);
    }
    private Instruction load(Op op, String[] tok, int src) throws AsmException {
        int rd = reg(tok[1], src);
        int[] ob = parseOffsetBase(tok[2], src);
        return new Instruction(op, rd, ob[1], 0, ob[0], src);
    }
    private Instruction store(Op op, String[] tok, int src) throws AsmException {
        int rs2 = reg(tok[1], src);
        int[] ob = parseOffsetBase(tok[2], src);
        return new Instruction(op, 0, ob[1], rs2, ob[0], src);
    }
    private Instruction branch(Op op, String[] tok, Map<String,Integer> labels, int src) throws AsmException {
        return new Instruction(op, 0, reg(tok[1],src), reg(tok[2],src),
                resolveLabel(tok[3], labels, src), src);
    }
    /** amoX.w rd, rs2, (rs1) */
    private Instruction amoInstr(Op op, String[] tok, int src) throws AsmException {
        int rd  = reg(tok[1], src);
        int rs2 = reg(tok[2], src);
        int[] ob = parseOffsetBase(tok[3], src);
        return new Instruction(op, rd, ob[1], rs2, 0, src);
    }
    /** fmadd.x fd, fs1, fs2, fs3  (optional 5th rm token ignored) */
    private Instruction fma(Op op, String[] tok, int src) throws AsmException {
        return new Instruction(op, freg(tok[1],src), freg(tok[2],src), freg(tok[3],src), freg(tok[4],src), src);
    }
    /** 3-register float/double instruction */
    private Instruction frrr(Op op, String[] tok, int src) throws AsmException {
        return new Instruction(op, freg(tok[1],src), freg(tok[2],src), freg(tok[3],src), 0, src);
    }

    private int reg(String s, int srcLine) throws AsmException {
        Integer r = REG_MAP.get(s.toLowerCase(Locale.ROOT));
        if (r == null) throw new AsmException("unknown register '" + s + "'");
        return r;
    }
    private int freg(String s, int srcLine) throws AsmException {
        Integer r = FREG_MAP.get(s.toLowerCase(Locale.ROOT));
        if (r == null) throw new AsmException("unknown float register '" + s + "'");
        return r;
    }
    private int parseCsr(String s, int srcLine) throws AsmException {
        Integer c = CSR_MAP.get(s.toLowerCase(Locale.ROOT));
        if (c != null) return c;
        return parseImm(s, srcLine);
    }
    private int parseImm(String s, int srcLine) throws AsmException {
        try {
            if (s.startsWith("0x") || s.startsWith("0X"))
                return (int) Long.parseLong(s.substring(2), 16);
            if (s.startsWith("-0x") || s.startsWith("-0X"))
                return -(int) Long.parseLong(s.substring(3), 16);
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new AsmException("invalid immediate '" + s + "'");
        }
    }
    /** Parse "offset(base)" or "(base)" or plain number. Returns {offset, baseReg}. */
    private int[] parseOffsetBase(String s, int srcLine) throws AsmException {
        int paren = s.indexOf('(');
        if (paren < 0) return new int[]{ parseImm(s, srcLine), 0 };
        if (!s.endsWith(")")) throw new AsmException("malformed offset(base): '" + s + "'");
        int offset = paren == 0 ? 0 : parseImm(s.substring(0, paren), srcLine);
        int base   = reg(s.substring(paren + 1, s.length() - 1), srcLine);
        return new int[]{ offset, base };
    }
    private int resolveLabel(String s, Map<String,Integer> labels, int srcLine) throws AsmException {
        Integer t = labels.get(s.toLowerCase(Locale.ROOT));
        if (t != null) return t;
        try { return parseImm(s, srcLine); }
        catch (AsmException ignored) {}
        throw new AsmException("undefined label '" + s + "'");
    }

    // ── Execution ─────────────────────────────────────────────────────────────

    public boolean tick(GPIOAccess gpio) { return tick(gpio, null, null, null); }
    public boolean tick(GPIOAccess gpio, DisplayAccess display) { return tick(gpio, display, null, null); }

    public boolean tick(GPIOAccess gpio, DisplayAccess display, RAMAccess ram, FloppyAccess floppy) {
        if (halted || program.isEmpty()) return false;
        if (pc >= program.size()) { halted = true; return false; }

        Instruction inst = program.get(pc++);
        executingLine = inst.srcLine() - 1;

        int rd  = inst.rd();
        int rs1 = inst.rs1();
        int rs2 = inst.rs2();
        int imm = inst.imm();

        switch (inst.op()) {
            // ── RV32I R-type ──────────────────────────────────────────────────
            case ADD  -> wr(rd, regs[rs1] + regs[rs2]);
            case SUB  -> wr(rd, regs[rs1] - regs[rs2]);
            case AND  -> wr(rd, regs[rs1] & regs[rs2]);
            case OR   -> wr(rd, regs[rs1] | regs[rs2]);
            case XOR  -> wr(rd, regs[rs1] ^ regs[rs2]);
            case SLL  -> wr(rd, regs[rs1] << (regs[rs2] & 31));
            case SRL  -> wr(rd, regs[rs1] >>> (regs[rs2] & 31));
            case SRA  -> wr(rd, regs[rs1] >> (regs[rs2] & 31));
            case SLT  -> wr(rd, regs[rs1] < regs[rs2] ? 1 : 0);
            case SLTU -> wr(rd, Integer.compareUnsigned(regs[rs1], regs[rs2]) < 0 ? 1 : 0);

            // ── RV32M ─────────────────────────────────────────────────────────
            case MUL    -> wr(rd, regs[rs1] * regs[rs2]);
            case MULH   -> wr(rd, (int)(((long)regs[rs1] * (long)regs[rs2]) >> 32));
            case MULHSU -> wr(rd, (int)(((long)regs[rs1] * Integer.toUnsignedLong(regs[rs2])) >> 32));
            case MULHU  -> wr(rd, (int)((Integer.toUnsignedLong(regs[rs1]) * Integer.toUnsignedLong(regs[rs2])) >> 32));
            case DIV    -> wr(rd, regs[rs2] == 0 ? -1 : regs[rs1] / regs[rs2]);
            case DIVU   -> wr(rd, regs[rs2] == 0 ? -1 : (int)(Integer.toUnsignedLong(regs[rs1]) / Integer.toUnsignedLong(regs[rs2])));
            case REM    -> wr(rd, regs[rs2] == 0 ? regs[rs1] : regs[rs1] % regs[rs2]);
            case REMU   -> wr(rd, regs[rs2] == 0 ? regs[rs1] : (int)(Integer.toUnsignedLong(regs[rs1]) % Integer.toUnsignedLong(regs[rs2])));

            // ── I-type arithmetic ─────────────────────────────────────────────
            case ADDI  -> wr(rd, regs[rs1] + imm);
            case ANDI  -> wr(rd, regs[rs1] & imm);
            case ORI   -> wr(rd, regs[rs1] | imm);
            case XORI  -> wr(rd, regs[rs1] ^ imm);
            case SLLI  -> wr(rd, regs[rs1] << (imm & 31));
            case SRLI  -> wr(rd, regs[rs1] >>> (imm & 31));
            case SRAI  -> wr(rd, regs[rs1] >> (imm & 31));
            case SLTI  -> wr(rd, regs[rs1] < imm ? 1 : 0);
            case SLTIU -> wr(rd, Integer.compareUnsigned(regs[rs1], imm) < 0 ? 1 : 0);

            // ── Loads ─────────────────────────────────────────────────────────
            case LB  -> { int a = regs[rs1]+imm; wr(rd, (byte)  memRdByte(a,gpio,ram)); }
            case LH  -> { int a = regs[rs1]+imm; wr(rd, (short) memRdHalf(a,gpio,ram)); }
            case LW  -> { int a = regs[rs1]+imm; wr(rd,         memRdWord(a,gpio,ram)); }
            case LBU -> { int a = regs[rs1]+imm; wr(rd, memRdByte(a,gpio,ram) & 0xFF); }
            case LHU -> { int a = regs[rs1]+imm; wr(rd, memRdHalf(a,gpio,ram) & 0xFFFF); }

            // ── Stores ────────────────────────────────────────────────────────
            case SB -> memWrByte(regs[rs1]+imm, regs[rs2], gpio, ram);
            case SH -> memWrHalf(regs[rs1]+imm, regs[rs2], gpio, ram);
            case SW -> memWrWord(regs[rs1]+imm, regs[rs2], gpio, ram);

            // ── Branches ──────────────────────────────────────────────────────
            case BEQ  -> { if (regs[rs1] == regs[rs2]) pc = imm; }
            case BNE  -> { if (regs[rs1] != regs[rs2]) pc = imm; }
            case BLT  -> { if (regs[rs1] <  regs[rs2]) pc = imm; }
            case BGE  -> { if (regs[rs1] >= regs[rs2]) pc = imm; }
            case BLTU -> { if (Integer.compareUnsigned(regs[rs1], regs[rs2]) <  0) pc = imm; }
            case BGEU -> { if (Integer.compareUnsigned(regs[rs1], regs[rs2]) >= 0) pc = imm; }

            // ── Jumps ─────────────────────────────────────────────────────────
            case JAL  -> { wr(rd, pc); pc = imm; }
            case JALR -> { int ret = pc; pc = regs[rs1] + imm; wr(rd, ret); }

            // ── Upper immediate ───────────────────────────────────────────────
            case LUI   -> wr(rd, imm << 12);
            case AUIPC -> wr(rd, (pc - 1) * 4 + (imm << 12));

            // ── System ────────────────────────────────────────────────────────
            case ECALL  -> ecall(gpio, display, ram, floppy);
            case EBREAK -> halted = true;
            case FENCE  -> { /* NOP */ }

            // ── RV32A — Atomics ───────────────────────────────────────────────
            case LR_W -> { int addr = regs[rs1]; wr(rd, memRdWord(addr,gpio,ram)); lrReservation = addr; }
            case SC_W -> {
                if (lrReservation == regs[rs1]) { memWrWord(regs[rs1], regs[rs2], gpio, ram); wr(rd, 0); }
                else                            { wr(rd, 1); }
                lrReservation = -1;
            }
            case AMOSWAP_W -> { int a=regs[rs1]; int t=memRdWord(a,gpio,ram); memWrWord(a,regs[rs2],gpio,ram); wr(rd,t); }
            case AMOADD_W  -> { int a=regs[rs1]; int t=memRdWord(a,gpio,ram); memWrWord(a,t+regs[rs2],gpio,ram); wr(rd,t); }
            case AMOXOR_W  -> { int a=regs[rs1]; int t=memRdWord(a,gpio,ram); memWrWord(a,t^regs[rs2],gpio,ram); wr(rd,t); }
            case AMOAND_W  -> { int a=regs[rs1]; int t=memRdWord(a,gpio,ram); memWrWord(a,t&regs[rs2],gpio,ram); wr(rd,t); }
            case AMOOR_W   -> { int a=regs[rs1]; int t=memRdWord(a,gpio,ram); memWrWord(a,t|regs[rs2],gpio,ram); wr(rd,t); }
            case AMOMIN_W  -> { int a=regs[rs1]; int t=memRdWord(a,gpio,ram); memWrWord(a,Math.min(t,regs[rs2]),gpio,ram); wr(rd,t); }
            case AMOMAX_W  -> { int a=regs[rs1]; int t=memRdWord(a,gpio,ram); memWrWord(a,Math.max(t,regs[rs2]),gpio,ram); wr(rd,t); }
            case AMOMINU_W -> { int a=regs[rs1]; int t=memRdWord(a,gpio,ram); int v=Integer.compareUnsigned(t,regs[rs2])<0?t:regs[rs2]; memWrWord(a,v,gpio,ram); wr(rd,t); }
            case AMOMAXU_W -> { int a=regs[rs1]; int t=memRdWord(a,gpio,ram); int v=Integer.compareUnsigned(t,regs[rs2])>0?t:regs[rs2]; memWrWord(a,v,gpio,ram); wr(rd,t); }

            // ── RV32F — Float load/store (NaN-boxing) ─────────────────────────
            case FLW -> fwrite(rd, Float.intBitsToFloat(memRdWord(regs[rs1]+imm, gpio, ram)));
            case FSW -> memWrWord(regs[rs1]+imm, Float.floatToRawIntBits(fread(rs2)), gpio, ram);

            // ── RV32F — FMA ───────────────────────────────────────────────────
            case FMADD_S  -> fwrite(rd, Math.fma( fread(rs1),  fread(rs2),  fread(imm)));
            case FMSUB_S  -> fwrite(rd, Math.fma( fread(rs1),  fread(rs2), -fread(imm)));
            case FNMSUB_S -> fwrite(rd, Math.fma(-fread(rs1),  fread(rs2),  fread(imm)));
            case FNMADD_S -> fwrite(rd, Math.fma(-fread(rs1),  fread(rs2), -fread(imm)));

            // ── RV32F — Arithmetic ────────────────────────────────────────────
            case FADD_S  -> fwrite(rd, fread(rs1) + fread(rs2));
            case FSUB_S  -> fwrite(rd, fread(rs1) - fread(rs2));
            case FMUL_S  -> fwrite(rd, fread(rs1) * fread(rs2));
            case FDIV_S  -> fwrite(rd, fread(rs1) / fread(rs2));
            case FSQRT_S -> fwrite(rd, (float) Math.sqrt(fread(rs1)));

            // ── RV32F — Sign injection ────────────────────────────────────────
            case FSGNJ_S -> {
                int b1 = Float.floatToRawIntBits(fread(rs1));
                int b2 = Float.floatToRawIntBits(fread(rs2));
                fwrite(rd, Float.intBitsToFloat((b1 & 0x7FFFFFFF) | (b2 & 0x80000000)));
            }
            case FSGNJN_S -> {
                int b1 = Float.floatToRawIntBits(fread(rs1));
                int b2 = Float.floatToRawIntBits(fread(rs2));
                fwrite(rd, Float.intBitsToFloat((b1 & 0x7FFFFFFF) | (~b2 & 0x80000000)));
            }
            case FSGNJX_S -> {
                int b1 = Float.floatToRawIntBits(fread(rs1));
                int b2 = Float.floatToRawIntBits(fread(rs2));
                fwrite(rd, Float.intBitsToFloat((b1 & 0x7FFFFFFF) | ((b1 ^ b2) & 0x80000000)));
            }

            // ── RV32F — Min/max ───────────────────────────────────────────────
            case FMIN_S -> { float a=fread(rs1),b=fread(rs2); fwrite(rd, Float.isNaN(a)?b : Float.isNaN(b)?a : Math.min(a,b)); }
            case FMAX_S -> { float a=fread(rs1),b=fread(rs2); fwrite(rd, Float.isNaN(a)?b : Float.isNaN(b)?a : Math.max(a,b)); }

            // ── RV32F — Conversions ───────────────────────────────────────────
            case FCVT_W_S -> {
                float fv = fread(rs1);
                if (Float.isNaN(fv) || fv >= 2147483648.0f) wr(rd, Integer.MAX_VALUE);
                else if (fv < -2147483648.0f)               wr(rd, Integer.MIN_VALUE);
                else                                         wr(rd, (int) fv);
            }
            case FCVT_WU_S -> {
                float fv = fread(rs1);
                if (Float.isNaN(fv) || fv >= 4294967296.0f) wr(rd, 0xFFFFFFFF);
                else if (fv <= 0.0f)                         wr(rd, 0);
                else                                         wr(rd, (int)(long) fv);
            }
            case FCVT_S_W  -> fwrite(rd, (float) regs[rs1]);
            case FCVT_S_WU -> fwrite(rd, (float) Integer.toUnsignedLong(regs[rs1]));

            // ── RV32F — Move ──────────────────────────────────────────────────
            case FMV_X_W -> wr(rd, Float.floatToRawIntBits(fread(rs1)));
            case FMV_W_X -> fwrite(rd, Float.intBitsToFloat(regs[rs1]));

            // ── RV32F — Compare ───────────────────────────────────────────────
            case FEQ_S -> { float a=fread(rs1),b=fread(rs2); wr(rd, !Float.isNaN(a)&&!Float.isNaN(b)&&a==b ? 1:0); }
            case FLT_S -> { float a=fread(rs1),b=fread(rs2); wr(rd, !Float.isNaN(a)&&!Float.isNaN(b)&&a< b ? 1:0); }
            case FLE_S -> { float a=fread(rs1),b=fread(rs2); wr(rd, !Float.isNaN(a)&&!Float.isNaN(b)&&a<=b ? 1:0); }

            // ── RV32F — Classify ──────────────────────────────────────────────
            case FCLASS_S -> wr(rd, fclassF(fread(rs1)));

            // ── RV32D — Double load/store ─────────────────────────────────────
            case FLD -> fregs[rd] = Double.longBitsToDouble(memRdLong(regs[rs1]+imm, gpio, ram));
            case FSD -> memWrLong(regs[rs1]+imm, Double.doubleToRawLongBits(fregs[rs2]), gpio, ram);

            // ── RV32D — FMA ───────────────────────────────────────────────────
            case FMADD_D  -> fregs[rd] = Math.fma( fregs[rs1],  fregs[rs2],  fregs[imm]);
            case FMSUB_D  -> fregs[rd] = Math.fma( fregs[rs1],  fregs[rs2], -fregs[imm]);
            case FNMSUB_D -> fregs[rd] = Math.fma(-fregs[rs1],  fregs[rs2],  fregs[imm]);
            case FNMADD_D -> fregs[rd] = Math.fma(-fregs[rs1],  fregs[rs2], -fregs[imm]);

            // ── RV32D — Arithmetic ────────────────────────────────────────────
            case FADD_D  -> fregs[rd] = fregs[rs1] + fregs[rs2];
            case FSUB_D  -> fregs[rd] = fregs[rs1] - fregs[rs2];
            case FMUL_D  -> fregs[rd] = fregs[rs1] * fregs[rs2];
            case FDIV_D  -> fregs[rd] = fregs[rs1] / fregs[rs2];
            case FSQRT_D -> fregs[rd] = Math.sqrt(fregs[rs1]);

            // ── RV32D — Sign injection ────────────────────────────────────────
            case FSGNJ_D -> {
                long b1 = Double.doubleToRawLongBits(fregs[rs1]);
                long b2 = Double.doubleToRawLongBits(fregs[rs2]);
                fregs[rd] = Double.longBitsToDouble((b1 & 0x7FFFFFFFFFFFFFFFL) | (b2 & 0x8000000000000000L));
            }
            case FSGNJN_D -> {
                long b1 = Double.doubleToRawLongBits(fregs[rs1]);
                long b2 = Double.doubleToRawLongBits(fregs[rs2]);
                fregs[rd] = Double.longBitsToDouble((b1 & 0x7FFFFFFFFFFFFFFFL) | (~b2 & 0x8000000000000000L));
            }
            case FSGNJX_D -> {
                long b1 = Double.doubleToRawLongBits(fregs[rs1]);
                long b2 = Double.doubleToRawLongBits(fregs[rs2]);
                fregs[rd] = Double.longBitsToDouble((b1 & 0x7FFFFFFFFFFFFFFFL) | ((b1 ^ b2) & 0x8000000000000000L));
            }

            // ── RV32D — Min/max ───────────────────────────────────────────────
            case FMIN_D -> fregs[rd] = Double.isNaN(fregs[rs1]) ? fregs[rs2] : Double.isNaN(fregs[rs2]) ? fregs[rs1] : Math.min(fregs[rs1], fregs[rs2]);
            case FMAX_D -> fregs[rd] = Double.isNaN(fregs[rs1]) ? fregs[rs2] : Double.isNaN(fregs[rs2]) ? fregs[rs1] : Math.max(fregs[rs1], fregs[rs2]);

            // ── RV32D — Conversions ───────────────────────────────────────────
            case FCVT_W_D -> {
                double dv = fregs[rs1];
                if (Double.isNaN(dv) || dv >= 2147483648.0) wr(rd, Integer.MAX_VALUE);
                else if (dv < -2147483648.0)                 wr(rd, Integer.MIN_VALUE);
                else                                          wr(rd, (int) dv);
            }
            case FCVT_WU_D -> {
                double dv = fregs[rs1];
                if (Double.isNaN(dv) || dv >= 4294967296.0) wr(rd, 0xFFFFFFFF);
                else if (dv <= 0.0)                          wr(rd, 0);
                else                                         wr(rd, (int)(long) dv);
            }
            case FCVT_D_W  -> fregs[rd] = (double) regs[rs1];
            case FCVT_D_WU -> fregs[rd] = (double) Integer.toUnsignedLong(regs[rs1]);
            case FCVT_S_D  -> fwrite(rd, (float) fregs[rs1]);    // double → NaN-boxed single
            case FCVT_D_S  -> fregs[rd] = (double) fread(rs1);   // NaN-unboxed single → double

            // ── RV32D — Compare ───────────────────────────────────────────────
            case FEQ_D -> wr(rd, !Double.isNaN(fregs[rs1])&&!Double.isNaN(fregs[rs2])&&fregs[rs1]==fregs[rs2] ? 1:0);
            case FLT_D -> wr(rd, !Double.isNaN(fregs[rs1])&&!Double.isNaN(fregs[rs2])&&fregs[rs1]< fregs[rs2] ? 1:0);
            case FLE_D -> wr(rd, !Double.isNaN(fregs[rs1])&&!Double.isNaN(fregs[rs2])&&fregs[rs1]<=fregs[rs2] ? 1:0);

            // ── RV32D — Classify ──────────────────────────────────────────────
            case FCLASS_D -> wr(rd, fclassD(fregs[rs1]));

            // ── Zicsr ─────────────────────────────────────────────────────────
            case CSRRW  -> { int old = csrRead(imm); csrWrite(imm, regs[rs1]); wr(rd, old); }
            case CSRRS  -> { int old = csrRead(imm); if (rs1 != 0) csrWrite(imm, old | regs[rs1]); wr(rd, old); }
            case CSRRC  -> { int old = csrRead(imm); if (rs1 != 0) csrWrite(imm, old & ~regs[rs1]); wr(rd, old); }
            case CSRRWI -> { int old = csrRead(imm); csrWrite(imm, rs2); wr(rd, old); }
            case CSRRSI -> { int old = csrRead(imm); if (rs2 != 0) csrWrite(imm, old | rs2); wr(rd, old); }
            case CSRRCI -> { int old = csrRead(imm); if (rs2 != 0) csrWrite(imm, old & ~rs2); wr(rd, old); }
        }

        return !halted;
    }

    private void wr(int rd, int val) {
        if (rd != 0) regs[rd] = val;
    }

    // ── CSR access ────────────────────────────────────────────────────────────

    private int csrRead(int csr) {
        return switch (csr) {
            case 0x001 -> fcsr & 0x1F;        // fflags
            case 0x002 -> (fcsr >>> 5) & 0x7; // frm
            case 0x003 -> fcsr & 0xFF;         // fcsr
            default    -> 0;
        };
    }
    private void csrWrite(int csr, int val) {
        switch (csr) {
            case 0x001 -> fcsr = (fcsr & ~0x1F) | (val & 0x1F);
            case 0x002 -> fcsr = (fcsr & ~0xE0) | ((val & 0x7) << 5);
            case 0x003 -> fcsr = val & 0xFF;
        }
    }

    // ── FCLASS helpers ────────────────────────────────────────────────────────

    private static int fclassF(float f) {
        int bits = Float.floatToRawIntBits(f);
        boolean neg = (bits & 0x80000000) != 0;
        int exp = (bits >>> 23) & 0xFF;
        int man =  bits & 0x7FFFFF;
        if (exp == 0xFF) {
            if (man == 0) return neg ? 1 : 1 << 7;
            return (man & 0x400000) != 0 ? 1 << 9 : 1 << 8;
        }
        if (exp == 0) {
            if (man == 0) return neg ? 1 << 3 : 1 << 4;
            return neg ? 1 << 2 : 1 << 5;
        }
        return neg ? 1 << 1 : 1 << 6;
    }

    private static int fclassD(double d) {
        long bits = Double.doubleToRawLongBits(d);
        boolean neg = (bits & 0x8000000000000000L) != 0;
        int  exp = (int)((bits >>> 52) & 0x7FFL);
        long man =       bits & 0x000FFFFFFFFFFFFFL;
        if (exp == 0x7FF) {
            if (man == 0) return neg ? 1 : 1 << 7;
            return (man & (1L << 51)) != 0 ? 1 << 9 : 1 << 8;
        }
        if (exp == 0) {
            if (man == 0) return neg ? 1 << 3 : 1 << 4;
            return neg ? 1 << 2 : 1 << 5;
        }
        return neg ? 1 << 1 : 1 << 6;
    }

    // ── ECALL dispatcher ──────────────────────────────────────────────────────

    private void ecall(GPIOAccess gpio, DisplayAccess display, RAMAccess ram, FloppyAccess floppy) {
        int sys = regs[17];
        int a0 = regs[10], a1 = regs[11], a2 = regs[12];
        int a3 = regs[13], a4 = regs[14], a5 = regs[15];
        switch (sys) {
            case 0  -> halted = true;
            case 1  -> { if (a0 > 0) sleepTicks = a0; }
            case 2  -> regs[10] = a0 + (int)(Math.random() * ((long)(a1 - a0) + 1));
            case 10 -> { if (gpio != null) gpio.write(a0, Math.clamp(a1, 0, 15)); }
            case 11 -> regs[10] = gpio != null ? gpio.read(a0) : 0;
            case 20 -> { if (display != null) display.setPixel(a0, a1, a2, a3 & 0xFFFFFF); }
            case 21 -> { if (display != null) display.reset(a0); }
            case 22 -> {
                if (display != null) {
                    regs[10] = display.getWidth(a0);
                    regs[11] = display.getHeight(a0);
                }
            }
            case 23 -> { if (display != null) display.drawLine(a0, a1, a2, a3, a4, a5 & 0xFFFFFF); }
            case 24 -> { if (display != null) display.fillRect(a0, a1, a2, a3, a4, a5 & 0xFFFFFF); }
            case 25 -> {
                if (display != null && ram != null) {
                    int total = a3 * a4;
                    if (total > 0) {
                        int[] px = new int[total];
                        for (int i = 0; i < total; i++) {
                            int base = a5 + i * 3;
                            px[i] = ((ram.read(base)   & 0xFF) << 16)
                                  | ((ram.read(base+1)  & 0xFF) << 8)
                                  |  (ram.read(base+2)  & 0xFF);
                        }
                        display.blit(a0, a1, a2, a3, a4, px);
                    }
                }
            }
            case 30 -> regs[10] = (floppy != null && floppy.loadSector(a0, a1, a2)) ? 1 : 0;
        }
    }

    // ── Memory access (RAM + GPIO MMIO) ──────────────────────────────────────

    private static final int GPIO_BASE = 0xF0000000;
    private static final int GPIO_END  = 0xF0010000;

    private boolean isGpio(int addr) {
        return Integer.compareUnsigned(addr, GPIO_BASE) >= 0
            && Integer.compareUnsigned(addr, GPIO_END) < 0;
    }

    private int memRdByte(int addr, GPIOAccess gpio, RAMAccess ram) {
        if (isGpio(addr)) return gpio != null ? gpio.read((addr - GPIO_BASE) / 4) & 0xFF : 0;
        return ram != null ? ram.read(addr) & 0xFF : 0;
    }
    private int memRdHalf(int addr, GPIOAccess gpio, RAMAccess ram) {
        return  memRdByte(addr,   gpio, ram)
             | (memRdByte(addr+1, gpio, ram) << 8);
    }
    private int memRdWord(int addr, GPIOAccess gpio, RAMAccess ram) {
        return  memRdByte(addr,   gpio, ram)
             | (memRdByte(addr+1, gpio, ram) << 8)
             | (memRdByte(addr+2, gpio, ram) << 16)
             | (memRdByte(addr+3, gpio, ram) << 24);
    }
    private long memRdLong(int addr, GPIOAccess gpio, RAMAccess ram) {
        return Integer.toUnsignedLong(memRdWord(addr,   gpio, ram))
             | (Integer.toUnsignedLong(memRdWord(addr+4, gpio, ram)) << 32);
    }
    private void memWrByte(int addr, int val, GPIOAccess gpio, RAMAccess ram) {
        if (isGpio(addr)) { if (gpio != null) gpio.write((addr - GPIO_BASE) / 4, Math.clamp(val & 0xFF, 0, 15)); }
        else              { if (ram  != null) ram.write(addr, val & 0xFF); }
    }
    private void memWrHalf(int addr, int val, GPIOAccess gpio, RAMAccess ram) {
        memWrByte(addr,   val,       gpio, ram);
        memWrByte(addr+1, val >>> 8, gpio, ram);
    }
    private void memWrWord(int addr, int val, GPIOAccess gpio, RAMAccess ram) {
        memWrByte(addr,   val,        gpio, ram);
        memWrByte(addr+1, val >>> 8,  gpio, ram);
        memWrByte(addr+2, val >>> 16, gpio, ram);
        memWrByte(addr+3, val >>> 24, gpio, ram);
    }
    private void memWrLong(int addr, long val, GPIOAccess gpio, RAMAccess ram) {
        memWrWord(addr,   (int)  val,        gpio, ram);
        memWrWord(addr+4, (int) (val >>> 32), gpio, ram);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void reset() {
        pc = 0; executingLine = 0; sleepTicks = 0; halted = false;
        Arrays.fill(regs, 0);
        // Initialise float regs as NaN-boxed +0.0f so F ops see 0 and D ops see NaN
        long nanBoxedZero = 0xFFFFFFFF00000000L;
        for (int i = 0; i < 32; i++) fregs[i] = Double.longBitsToDouble(nanBoxedZero);
        fcsr = 0;
        lrReservation = -1;
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    public void saveState(ValueOutput out) {
        out.putInt("PC", pc);
        out.putInt("ExecLine", executingLine);
        out.putInt("Sleep", sleepTicks);
        out.putBoolean("Halted", halted);
        for (int i = 1; i < 32; i++) out.putInt("x" + i, regs[i]);
        // Store each 64-bit float register as two 32-bit ints (little-endian)
        for (int i = 0; i < 32; i++) {
            long bits = Double.doubleToRawLongBits(fregs[i]);
            out.putInt("f" + i + "lo", (int)  bits);
            out.putInt("f" + i + "hi", (int) (bits >>> 32));
        }
        out.putInt("FCSR", fcsr);
    }

    public void loadState(ValueInput in) {
        pc            = in.getIntOr("PC", 0);
        executingLine = in.getIntOr("ExecLine", 0);
        sleepTicks    = in.getIntOr("Sleep", 0);
        halted        = in.getBooleanOr("Halted", false);
        regs[0] = 0;
        for (int i = 1; i < 32; i++) regs[i] = in.getIntOr("x" + i, 0);
        long nanBoxedZero = 0xFFFFFFFF00000000L;
        for (int i = 0; i < 32; i++) {
            long lo = Integer.toUnsignedLong(in.getIntOr("f" + i + "lo", 0));
            long hi = Integer.toUnsignedLong(in.getIntOr("f" + i + "hi", (int)(nanBoxedZero >>> 32)));
            fregs[i] = Double.longBitsToDouble((hi << 32) | lo);
        }
        fcsr          = in.getIntOr("FCSR", 0);
        lrReservation = -1;
    }
}
