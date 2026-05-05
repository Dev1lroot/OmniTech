package com.dev1lroot.mcmods.omnitech.blocks.logic.vm;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.*;

/**
 * RISC-V RV32I virtual machine for the LogicMachine block.
 *
 * Registers: x0 (zero, hardwired 0) … x31, with standard ABI aliases.
 *   zero ra sp gp tp t0-t2 s0/fp s1 a0-a7 s2-s11 t3-t6
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
 * Supported assembly syntax: standard RV32I mnemonics + ABI register names.
 * Pseudo-instructions: nop mv neg not seqz snez sltz sgtz li la
 *   beqz bnez blez bgez bltz bgtz j jr ret call tail halt
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
        // R-type (RV32I)
        ADD, SUB, AND, OR, XOR, SLL, SRL, SRA, SLT, SLTU,
        // R-type (RV32M — multiply/divide extension)
        MUL, MULH, MULHSU, MULHU, DIV, DIVU, REM, REMU,
        // I-type arithmetic/shifts
        ADDI, ANDI, ORI, XORI, SLLI, SRLI, SRAI, SLTI, SLTIU,
        // Loads  (rd = mem[rs1+imm])
        LB, LH, LW, LBU, LHU,
        // Stores (mem[rs1+imm] = rs2)
        SB, SH, SW,
        // Branches (if rs1 op rs2: pc = imm)
        BEQ, BNE, BLT, BGE, BLTU, BGEU,
        // Jumps
        JAL,    // rd = return-addr, pc = imm (instruction index)
        JALR,   // rd = return-addr, pc = rs1+imm (instruction index)
        // Upper immediate
        LUI, AUIPC,
        // System
        ECALL, EBREAK,
        FENCE   // treated as NOP
    }

    /**
     * Compiled instruction. imm holds a full 32-bit value (no 12-bit limit);
     * for branches/JAL it holds the absolute target instruction index.
     */
    private record Instruction(Op op, int rd, int rs1, int rs2, int imm, int srcLine) {}

    // ── Register file ─────────────────────────────────────────────────────────

    public int[]   regs         = new int[32]; // x0 hardwired to 0
    public int     pc           = 0;            // next instruction index to execute
    public int     executingLine = 0;           // 0-based source line of running instruction
    public int     sleepTicks   = 0;
    public boolean halted       = false;

    private List<Instruction> program = new ArrayList<>();

    // ── ABI register name map ─────────────────────────────────────────────────

    private static final Map<String, Integer> REG_MAP;
    static {
        REG_MAP = new HashMap<>(64);
        for (int i = 0; i < 32; i++) REG_MAP.put("x" + i, i);
        String[] abi = {
            "zero","ra","sp","gp","tp","t0","t1","t2",
            "s0",  "s1","a0","a1","a2","a3","a4","a5",
            "a6",  "a7","s2","s3","s4","s5","s6","s7",
            "s8",  "s9","s10","s11","t3","t4","t5","t6"
        };
        for (int i = 0; i < abi.length; i++) REG_MAP.put(abi[i], i);
        REG_MAP.put("fp", 8); // fp = s0
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public int     lineCount()     { return program.size(); }
    public int     currentLine()   { return executingLine; }
    public boolean isEmpty()       { return program.isEmpty(); }
    public int     registerCount() { return 32; }
    /** No-op — RISC-V always has 32 registers. */
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
        // Every non-empty, non-comment, non-label line emits exactly 1 instruction.
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
        return switch (mn) {
            // ── R-type ────────────────────────────────────────────────────────
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

            // ── RV32M multiply / divide ───────────────────────────────────────
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
                if (tok.length == 2) {
                    // jal label  →  jal ra, label
                    yield new Instruction(Op.JAL, 1, 0, 0, resolveLabel(tok[1], labels, srcLine), srcLine);
                }
                yield new Instruction(Op.JAL, reg(tok[1], srcLine), 0, 0,
                        resolveLabel(tok[2], labels, srcLine), srcLine);
            }

            // ── JALR ──────────────────────────────────────────────────────────
            case "jalr" -> {
                if (tok.length == 2) {
                    // jalr rs  →  jalr x0, rs, 0
                    yield new Instruction(Op.JALR, 0, reg(tok[1], srcLine), 0, 0, srcLine);
                }
                if (tok.length == 3) {
                    if (tok[2].contains("(")) {
                        int[] ob = parseOffsetBase(tok[2], srcLine);
                        yield new Instruction(Op.JALR, reg(tok[1], srcLine), ob[1], 0, ob[0], srcLine);
                    }
                    yield new Instruction(Op.JALR, reg(tok[1], srcLine), reg(tok[2], srcLine), 0, 0, srcLine);
                }
                // jalr rd, rs1, imm
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

            // ── Pseudo-instructions ───────────────────────────────────────────
            case "nop"  -> new Instruction(Op.ADDI,  0, 0, 0,                    0,  srcLine);
            case "mv"   -> new Instruction(Op.ADDI,  reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 0,  srcLine);
            case "neg"  -> new Instruction(Op.SUB,   reg(tok[1],srcLine), 0, reg(tok[2],srcLine),  0,  srcLine);
            case "not"  -> new Instruction(Op.XORI,  reg(tok[1],srcLine), reg(tok[2],srcLine), 0, -1, srcLine);
            case "seqz" -> new Instruction(Op.SLTIU, reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 1,  srcLine);
            case "snez" -> new Instruction(Op.SLTU,  reg(tok[1],srcLine), 0, reg(tok[2],srcLine),  0,  srcLine);
            case "sltz" -> new Instruction(Op.SLT,   reg(tok[1],srcLine), reg(tok[2],srcLine), 0, 0,  srcLine);
            case "sgtz" -> new Instruction(Op.SLT,   reg(tok[1],srcLine), 0, reg(tok[2],srcLine),  0,  srcLine);

            case "li"   -> new Instruction(Op.ADDI, reg(tok[1],srcLine), 0, 0, parseImm(tok[2],srcLine), srcLine);
            case "la"   -> {
                // Load address of label as instruction-index * 4
                int target = resolveLabel(tok[2], labels, srcLine);
                yield new Instruction(Op.ADDI, reg(tok[1],srcLine), 0, 0, target * 4, srcLine);
            }

            // Pseudo branches  (rs1, rs2 encoded in rs1/rs2 fields; rd unused = 0)
            case "beqz" -> new Instruction(Op.BEQ, 0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);
            case "bnez" -> new Instruction(Op.BNE, 0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);
            case "bgez" -> new Instruction(Op.BGE, 0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);
            case "bltz" -> new Instruction(Op.BLT, 0, reg(tok[1],srcLine), 0, resolveLabel(tok[2],labels,srcLine), srcLine);
            case "blez" -> new Instruction(Op.BGE, 0, 0, reg(tok[1],srcLine), resolveLabel(tok[2],labels,srcLine), srcLine); // BGE x0, rs
            case "bgtz" -> new Instruction(Op.BLT, 0, 0, reg(tok[1],srcLine), resolveLabel(tok[2],labels,srcLine), srcLine); // BLT x0, rs

            // Jump pseudos
            case "j"    -> new Instruction(Op.JAL,  0, 0, 0, resolveLabel(tok[1],labels,srcLine), srcLine);
            case "jr"   -> new Instruction(Op.JALR, 0, reg(tok[1],srcLine), 0, 0, srcLine);
            case "ret"  -> new Instruction(Op.JALR, 0, 1, 0, 0, srcLine); // jalr x0, ra, 0
            case "call" -> new Instruction(Op.JAL,  1, 0, 0, resolveLabel(tok[1],labels,srcLine), srcLine);
            case "tail" -> new Instruction(Op.JAL,  0, 0, 0, resolveLabel(tok[1],labels,srcLine), srcLine);

            // Convenience halt — same as EBREAK
            case "halt" -> new Instruction(Op.EBREAK, 0, 0, 0, 0, srcLine);

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
        // lw rd, offset(base)
        int rd = reg(tok[1], src);
        int[] ob = parseOffsetBase(tok[2], src);
        return new Instruction(op, rd, ob[1], 0, ob[0], src);
    }

    private Instruction store(Op op, String[] tok, int src) throws AsmException {
        // sw rs2, offset(rs1)
        int rs2 = reg(tok[1], src);
        int[] ob = parseOffsetBase(tok[2], src);
        return new Instruction(op, 0, ob[1], rs2, ob[0], src);
    }

    private Instruction branch(Op op, String[] tok, Map<String,Integer> labels, int src) throws AsmException {
        return new Instruction(op, 0, reg(tok[1],src), reg(tok[2],src),
                resolveLabel(tok[3], labels, src), src);
    }

    private int reg(String s, int srcLine) throws AsmException {
        Integer r = REG_MAP.get(s.toLowerCase(Locale.ROOT));
        if (r == null) throw new AsmException("unknown register '" + s + "'");
        return r;
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

    /** Resolve a label name or integer literal to an instruction index. */
    private int resolveLabel(String s, Map<String,Integer> labels, int srcLine) throws AsmException {
        Integer t = labels.get(s.toLowerCase(Locale.ROOT));
        if (t != null) return t;
        // Fallback: try integer literal
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
        executingLine = inst.srcLine() - 1; // convert to 0-based for the debug editor

        int rd  = inst.rd();
        int rs1 = inst.rs1();
        int rs2 = inst.rs2();
        int imm = inst.imm();

        switch (inst.op()) {
            // ── R-type ───────────────────────────────────────────────────────
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

            // ── RV32M ────────────────────────────────────────────────────────
            case MUL    -> wr(rd, regs[rs1] * regs[rs2]);
            case MULH   -> wr(rd, (int)(((long)regs[rs1] * (long)regs[rs2]) >> 32));
            case MULHSU -> wr(rd, (int)(((long)regs[rs1] * Integer.toUnsignedLong(regs[rs2])) >> 32));
            case MULHU  -> wr(rd, (int)((Integer.toUnsignedLong(regs[rs1]) * Integer.toUnsignedLong(regs[rs2])) >> 32));
            case DIV    -> wr(rd, regs[rs2] == 0 ? -1 : regs[rs1] / regs[rs2]);
            case DIVU   -> wr(rd, regs[rs2] == 0 ? -1 : (int)(Integer.toUnsignedLong(regs[rs1]) / Integer.toUnsignedLong(regs[rs2])));
            case REM    -> wr(rd, regs[rs2] == 0 ? regs[rs1] : regs[rs1] % regs[rs2]);
            case REMU   -> wr(rd, regs[rs2] == 0 ? regs[rs1] : (int)(Integer.toUnsignedLong(regs[rs1]) % Integer.toUnsignedLong(regs[rs2])));

            // ── I-type arithmetic ────────────────────────────────────────────
            case ADDI  -> wr(rd, regs[rs1] + imm);
            case ANDI  -> wr(rd, regs[rs1] & imm);
            case ORI   -> wr(rd, regs[rs1] | imm);
            case XORI  -> wr(rd, regs[rs1] ^ imm);
            case SLLI  -> wr(rd, regs[rs1] << (imm & 31));
            case SRLI  -> wr(rd, regs[rs1] >>> (imm & 31));
            case SRAI  -> wr(rd, regs[rs1] >> (imm & 31));
            case SLTI  -> wr(rd, regs[rs1] < imm ? 1 : 0);
            case SLTIU -> wr(rd, Integer.compareUnsigned(regs[rs1], imm) < 0 ? 1 : 0);

            // ── Loads ────────────────────────────────────────────────────────
            case LB  -> { int a = regs[rs1]+imm; wr(rd, (byte)  memRdByte(a,gpio,ram)); }
            case LH  -> { int a = regs[rs1]+imm; wr(rd, (short) memRdHalf(a,gpio,ram)); }
            case LW  -> { int a = regs[rs1]+imm; wr(rd,         memRdWord(a,gpio,ram)); }
            case LBU -> { int a = regs[rs1]+imm; wr(rd, memRdByte(a,gpio,ram) & 0xFF); }
            case LHU -> { int a = regs[rs1]+imm; wr(rd, memRdHalf(a,gpio,ram) & 0xFFFF); }

            // ── Stores ───────────────────────────────────────────────────────
            case SB -> memWrByte(regs[rs1]+imm, regs[rs2], gpio, ram);
            case SH -> memWrHalf(regs[rs1]+imm, regs[rs2], gpio, ram);
            case SW -> memWrWord(regs[rs1]+imm, regs[rs2], gpio, ram);

            // ── Branches ─────────────────────────────────────────────────────
            case BEQ  -> { if (regs[rs1] == regs[rs2]) pc = imm; }
            case BNE  -> { if (regs[rs1] != regs[rs2]) pc = imm; }
            case BLT  -> { if (regs[rs1] <  regs[rs2]) pc = imm; }
            case BGE  -> { if (regs[rs1] >= regs[rs2]) pc = imm; }
            case BLTU -> { if (Integer.compareUnsigned(regs[rs1], regs[rs2]) <  0) pc = imm; }
            case BGEU -> { if (Integer.compareUnsigned(regs[rs1], regs[rs2]) >= 0) pc = imm; }

            // ── Jumps ────────────────────────────────────────────────────────
            // pc is already incremented (points to next instr); that is the return address.
            case JAL  -> { wr(rd, pc); pc = imm; }
            case JALR -> { int ret = pc; pc = regs[rs1] + imm; wr(rd, ret); }

            // ── Upper immediate ───────────────────────────────────────────────
            case LUI   -> wr(rd, imm << 12);
            case AUIPC -> wr(rd, (pc - 1) * 4 + (imm << 12));

            // ── System ────────────────────────────────────────────────────────
            case ECALL  -> ecall(gpio, display, ram, floppy);
            case EBREAK -> halted = true;
            case FENCE  -> { /* NOP */ }
        }

        return !halted;
    }

    /** Write register, enforcing x0 = 0. */
    private void wr(int rd, int val) {
        if (rd != 0) regs[rd] = val;
    }

    // ── ECALL dispatcher ──────────────────────────────────────────────────────

    private void ecall(GPIOAccess gpio, DisplayAccess display, RAMAccess ram, FloppyAccess floppy) {
        int sys = regs[17]; // a7
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

    // GPIO ports mapped at 0xF0000000 + portId*4  (up to 16384 ports)
    private static final int GPIO_BASE = 0xF0000000;
    private static final int GPIO_END  = 0xF0010000; // unsigned comparison

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

    private void memWrByte(int addr, int val, GPIOAccess gpio, RAMAccess ram) {
        if (isGpio(addr)) {
            if (gpio != null) gpio.write((addr - GPIO_BASE) / 4, Math.clamp(val & 0xFF, 0, 15));
        } else {
            if (ram != null) ram.write(addr, val & 0xFF);
        }
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void reset() {
        pc = 0; executingLine = 0; sleepTicks = 0; halted = false;
        Arrays.fill(regs, 0);
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    public void saveState(ValueOutput out) {
        out.putInt("PC", pc);
        out.putInt("ExecLine", executingLine);
        out.putInt("Sleep", sleepTicks);
        out.putBoolean("Halted", halted);
        for (int i = 1; i < 32; i++) out.putInt("x" + i, regs[i]);
    }

    public void loadState(ValueInput in) {
        pc            = in.getIntOr("PC", 0);
        executingLine = in.getIntOr("ExecLine", 0);
        sleepTicks    = in.getIntOr("Sleep", 0);
        halted        = in.getBooleanOr("Halted", false);
        regs[0] = 0;
        for (int i = 1; i < 32; i++) regs[i] = in.getIntOr("x" + i, 0);
    }
}
