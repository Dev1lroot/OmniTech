package com.dev1lroot.mcmods.omnitech.blocks.logic.vm;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Two-pass RV32IMAFDC assembler — emits raw little-endian 32-bit machine code.
 *
 * Supported: all RV32I/M/A/F/D instructions, Zicsr, common pseudo-instructions,
 * and directives: .word .half .byte .space .zero .string .ascii .asciz
 *                 .align .p2align .balign .globl .type .size .set .equ
 *                 .text .data .rodata .bss (section markers, no-op)
 */
public final class RV32Assembler {

    public record AsmResult(byte[] code, String error) {
        public boolean ok() { return error == null; }
    }

    public static AsmResult assemble(String source) {
        if (source == null || source.isBlank()) return new AsmResult(new byte[0], null);
        try { return new RV32Assembler(source).run(); }
        catch (AsmException e) { return new AsmResult(null, e.getMessage()); }
    }

    // ─── state ────────────────────────────────────────────────────────────────

    private static final class AsmException extends RuntimeException {
        AsmException(String m) { super(m); }
        AsmException(int line, String m) { super("line " + line + ": " + m); }
    }

    private final String[] rawLines;
    private final Map<String, Integer> labels  = new LinkedHashMap<>();
    private final Map<String, Integer> symbols = new HashMap<>();

    private RV32Assembler(String src) {
        rawLines = src.replace('\r', '\n').split("\n", -1);
    }

    // ─── register/CSR tables ─────────────────────────────────────────────────

    private static final Map<String, Integer> REG_MAP  = new HashMap<>(64);
    private static final Map<String, Integer> FREG_MAP = new HashMap<>(64);
    private static final Map<String, Integer> CSR_MAP  = new HashMap<>(32);

    static {
        for (int i = 0; i < 32; i++) REG_MAP.put("x" + i, i);
        String[] iabi = {"zero","ra","sp","gp","tp","t0","t1","t2",
                         "s0","s1","a0","a1","a2","a3","a4","a5",
                         "a6","a7","s2","s3","s4","s5","s6","s7",
                         "s8","s9","s10","s11","t3","t4","t5","t6"};
        for (int i = 0; i < iabi.length; i++) REG_MAP.put(iabi[i], i);
        REG_MAP.put("fp", 8);

        for (int i = 0; i < 32; i++) FREG_MAP.put("f" + i, i);
        String[] fabi = {"ft0","ft1","ft2","ft3","ft4","ft5","ft6","ft7",
                         "fs0","fs1","fa0","fa1","fa2","fa3","fa4","fa5",
                         "fa6","fa7","fs2","fs3","fs4","fs5","fs6","fs7",
                         "fs8","fs9","fs10","fs11","ft8","ft9","ft10","ft11"};
        for (int i = 0; i < fabi.length; i++) FREG_MAP.put(fabi[i], i);

        CSR_MAP.put("fflags",   0x001); CSR_MAP.put("frm",     0x002);
        CSR_MAP.put("fcsr",     0x003); CSR_MAP.put("cycle",   0xC00);
        CSR_MAP.put("time",     0xC01); CSR_MAP.put("instret", 0xC02);
        CSR_MAP.put("mstatus",  0x300); CSR_MAP.put("misa",    0x301);
        CSR_MAP.put("medeleg",  0x302); CSR_MAP.put("mideleg", 0x303);
        CSR_MAP.put("mie",      0x304); CSR_MAP.put("mtvec",   0x305);
        CSR_MAP.put("mscratch", 0x340); CSR_MAP.put("mepc",    0x341);
        CSR_MAP.put("mcause",   0x342); CSR_MAP.put("mtval",   0x343);
        CSR_MAP.put("mip",      0x344); CSR_MAP.put("mhartid", 0xF14);
        CSR_MAP.put("sstatus",  0x100); CSR_MAP.put("sie",     0x104);
        CSR_MAP.put("stvec",    0x105); CSR_MAP.put("sscratch",0x140);
        CSR_MAP.put("sepc",     0x141); CSR_MAP.put("scause",  0x142);
        CSR_MAP.put("stval",    0x143); CSR_MAP.put("sip",     0x144);
        CSR_MAP.put("satp",     0x180);
    }

    // ─── encoding helpers ─────────────────────────────────────────────────────

    private static int rtype(int op, int f3, int f7, int rd, int rs1, int rs2) {
        return (f7 << 25) | (rs2 << 20) | (rs1 << 15) | (f3 << 12) | (rd << 7) | op;
    }
    private static int itype(int op, int f3, int rd, int rs1, int imm) {
        return ((imm & 0xFFF) << 20) | (rs1 << 15) | (f3 << 12) | (rd << 7) | op;
    }
    private static int stype(int op, int f3, int rs1, int rs2, int imm) {
        return (((imm >> 5) & 0x7F) << 25) | (rs2 << 20) | (rs1 << 15)
             | (f3 << 12) | ((imm & 0x1F) << 7) | op;
    }
    private static int btype(int f3, int rs1, int rs2, int imm) {
        return ((imm & 0x1000) << 19) | ((imm & 0x800) >> 4)
             | (((imm >> 5) & 0x3F) << 25) | (((imm >> 1) & 0xF) << 8)
             | (rs2 << 20) | (rs1 << 15) | (f3 << 12) | 0x63;
    }
    private static int utype(int op, int rd, int imm20) {
        return ((imm20 & 0xFFFFF) << 12) | (rd << 7) | op;
    }
    private static int jtype(int rd, int imm) {
        return ((imm & 0x100000) << 11) | (imm & 0xFF000)
             | ((imm & 0x800) << 9) | (((imm >> 1) & 0x3FF) << 21)
             | (rd << 7) | 0x6F;
    }
    private static int r4type(int op, int fmt, int rd, int rs1, int rs2, int rs3) {
        return (rs3 << 27) | (fmt << 25) | (rs2 << 20) | (rs1 << 15) | (7 << 12) | (rd << 7) | op;
    }
    private static int fpR(int f7, int f3, int rd, int rs1, int rs2) {
        return (f7 << 25) | (rs2 << 20) | (rs1 << 15) | (f3 << 12) | (rd << 7) | 0x53;
    }
    private static int amoW(int funct5, int rd, int rs1, int rs2) {
        return (funct5 << 27) | (rs2 << 20) | (rs1 << 15) | (2 << 12) | (rd << 7) | 0x2F;
    }

    private static void putLE32(byte[] buf, int pos, int word) {
        buf[pos]   = (byte)  word;
        buf[pos+1] = (byte) (word >> 8);
        buf[pos+2] = (byte) (word >> 16);
        buf[pos+3] = (byte) (word >> 24);
    }

    // ─── tokeniser ────────────────────────────────────────────────────────────

    private static String stripComment(String line) {
        boolean inStr = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') inStr = !inStr;
            if (!inStr && (c == '#' || c == ';')) return line.substring(0, i);
        }
        return line;
    }

    /** Returns rest-of-line after consuming a leading label (if any). */
    private static String consumeLabel(String line, Map<String, Integer> dst, int offset) {
        int i = 0;
        char c0 = i < line.length() ? line.charAt(0) : 0;
        if (!Character.isLetter(c0) && c0 != '_' && c0 != '.') return line;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') i++;
            else break;
        }
        int j = i;
        while (j < line.length() && line.charAt(j) == ' ') j++;
        if (j >= line.length() || line.charAt(j) != ':') return line;
        String name = line.substring(0, i);
        if (dst != null) dst.put(name, offset);
        return line.substring(j + 1).trim();
    }

    private static String[] tokenize(String line) {
        // Split on commas and whitespace, respecting quoted strings
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inStr = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { inStr = !inStr; cur.append(c); }
            else if (!inStr && (c == ',' || c == ' ' || c == '\t')) {
                if (cur.length() > 0) { parts.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    // ─── immediate / register helpers ─────────────────────────────────────────

    private int parseImm(String s, int lineNo) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.startsWith("0x"))  return (int) Long.parseLong(t.substring(2), 16);
            if (t.startsWith("-0x")) return -(int) Long.parseLong(t.substring(3), 16);
            if (t.startsWith("'") && t.endsWith("'") && t.length() == 3) return t.charAt(1);
            // label reference
            Integer lv = labels.get(s.trim());
            if (lv != null) return lv;
            Integer sv = symbols.get(s.trim());
            if (sv != null) return sv;
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new AsmException(lineNo, "invalid immediate '" + s + "'");
        }
    }

    private int parseCsr(String s, int lineNo) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        Integer c = CSR_MAP.get(t);
        if (c != null) return c;
        return parseImm(s, lineNo);
    }

    private int reg(String s, int lineNo) {
        Integer r = REG_MAP.get(s.trim().toLowerCase(Locale.ROOT));
        if (r == null) throw new AsmException(lineNo, "unknown register '" + s + "'");
        return r;
    }
    private int freg(String s, int lineNo) {
        Integer r = FREG_MAP.get(s.trim().toLowerCase(Locale.ROOT));
        if (r == null) throw new AsmException(lineNo, "unknown float register '" + s + "'");
        return r;
    }

    /** Parse "offset(base)" or just a register name → {offset, baseReg}. */
    private int[] offsetBase(String s, int lineNo) {
        int p = s.indexOf('(');
        if (p < 0) return new int[]{0, reg(s, lineNo)};
        if (!s.endsWith(")")) throw new AsmException(lineNo, "malformed mem ref '" + s + "'");
        int off = p == 0 ? 0 : parseImm(s.substring(0, p), lineNo);
        int base = reg(s.substring(p + 1, s.length() - 1), lineNo);
        return new int[]{off, base};
    }

    private int labelOrImm(String s, int lineNo) {
        Integer lv = labels.get(s.trim());
        if (lv != null) return lv;
        return parseImm(s, lineNo);
    }

    // ─── pass 1: size calculation ─────────────────────────────────────────────

    /** Returns the number of bytes this line contributes (labels = 0). Pass 1 only. */
    private int sizeOf(String line, int lineNo, boolean pass1) {
        if (line.isEmpty()) return 0;
        String[] tok = tokenize(line);
        if (tok.length == 0) return 0;
        String mn = tok[0].toLowerCase(Locale.ROOT);

        if (mn.startsWith(".")) {
            return switch (mn) {
                case ".word"              -> 4;
                case ".half", ".short"    -> 2;
                case ".byte"              -> 1;
                case ".space", ".zero", ".skip" -> tok.length > 1 ? parseImm(tok[1], lineNo) : 0;
                case ".string", ".asciz"  -> tok.length > 1 ? unescapeString(tok[1], lineNo).length + 1 : 1;
                case ".ascii"             -> tok.length > 1 ? unescapeString(tok[1], lineNo).length : 0;
                case ".align", ".p2align" -> -1; // handled specially — alignment padding
                case ".balign"            -> -2;
                default -> 0; // .text .data .globl .type .size .set .equ etc.
            };
        }

        // Pseudo-instructions that expand to 2 words
        return switch (mn) {
            case "la", "call", "tail" -> 8;
            case "li" -> {
                if (tok.length < 3) yield 4;
                try {
                    int v = parseImm(tok[2], lineNo);
                    yield (v >= -2048 && v <= 2047) ? 4 : 8;
                } catch (AsmException e) { yield 8; }
            }
            default -> 4;
        };
    }

    // ─── two-pass driver ──────────────────────────────────────────────────────

    private AsmResult run() {
        // Pass 1 — collect label byte offsets
        int offset = 0;
        for (int i = 0; i < rawLines.length; i++) {
            String line = stripComment(rawLines[i]).trim();
            // consume all labels at start
            while (!line.isEmpty()) {
                String rest = consumeLabel(line, labels, offset);
                if (rest.equals(line)) break;
                line = rest.trim();
            }
            if (line.isEmpty()) continue;
            if (line.startsWith(".")) {
                int sz = sizeOf(line, i + 1, true);
                if (sz == -1) { // .align n → pad to 2^n
                    String[] tok = tokenize(line);
                    int n = tok.length > 1 ? parseImm(tok[1], i + 1) : 2;
                    int align = 1 << Math.min(n, 12);
                    offset = (offset + align - 1) & ~(align - 1);
                } else if (sz == -2) { // .balign n
                    String[] tok = tokenize(line);
                    int align = tok.length > 1 ? parseImm(tok[1], i + 1) : 4;
                    offset = (offset + align - 1) & ~(align - 1);
                } else if (sz >= 0) {
                    // .set / .equ
                    String[] tok = tokenize(line);
                    if ((tok[0].equals(".set") || tok[0].equals(".equ")) && tok.length >= 3) {
                        try { symbols.put(tok[1], parseImm(tok[2], i + 1)); } catch (AsmException ignored) {}
                    }
                    offset += sz;
                }
            } else {
                offset += sizeOf(line, i + 1, true);
            }
        }

        // Pass 2 — emit bytes
        byte[] buf = new byte[offset + 16]; // small guard
        int pos = 0;
        for (int i = 0; i < rawLines.length; i++) {
            String line = stripComment(rawLines[i]).trim();
            while (!line.isEmpty()) {
                String rest = consumeLabel(line, null, pos);
                if (rest.equals(line)) break;
                line = rest.trim();
            }
            if (line.isEmpty()) continue;
            if (line.startsWith(".")) {
                String[] tok = tokenize(line);
                String dn = tok[0].toLowerCase(Locale.ROOT);
                switch (dn) {
                    case ".word" -> {
                        if (tok.length > 1) putLE32(buf, pos, parseImm(tok[1], i + 1));
                        pos += 4;
                    }
                    case ".half", ".short" -> {
                        if (tok.length > 1) {
                            int v = parseImm(tok[1], i + 1);
                            buf[pos] = (byte)v; buf[pos+1] = (byte)(v>>8);
                        }
                        pos += 2;
                    }
                    case ".byte" -> {
                        if (tok.length > 1) buf[pos] = (byte) parseImm(tok[1], i + 1);
                        pos++;
                    }
                    case ".space", ".zero", ".skip" -> {
                        int n = tok.length > 1 ? parseImm(tok[1], i + 1) : 0;
                        pos += n; // buf already zero-initialized
                    }
                    case ".string", ".asciz" -> {
                        if (tok.length > 1) {
                            byte[] b = unescapeString(tok[1], i + 1);
                            System.arraycopy(b, 0, buf, pos, b.length);
                            pos += b.length;
                        }
                        pos++; // null terminator (buf is zero)
                    }
                    case ".ascii" -> {
                        if (tok.length > 1) {
                            byte[] b = unescapeString(tok[1], i + 1);
                            System.arraycopy(b, 0, buf, pos, b.length);
                            pos += b.length;
                        }
                    }
                    case ".align", ".p2align" -> {
                        int n = tok.length > 1 ? parseImm(tok[1], i + 1) : 2;
                        int align = 1 << Math.min(n, 12);
                        pos = (pos + align - 1) & ~(align - 1);
                    }
                    case ".balign" -> {
                        int align = tok.length > 1 ? parseImm(tok[1], i + 1) : 4;
                        pos = (pos + align - 1) & ~(align - 1);
                    }
                    case ".set", ".equ" -> {
                        if (tok.length >= 3) {
                            try { symbols.put(tok[1], parseImm(tok[2], i + 1)); } catch (AsmException ignored) {}
                        }
                    }
                    // .text .data .rodata .bss .globl .type .size → no-op
                }
            } else {
                pos += emitInstr(tokenize(line), buf, pos, i + 1);
            }
        }
        return new AsmResult(Arrays.copyOf(buf, pos), null);
    }

    // ─── string unescaping ────────────────────────────────────────────────────

    private static byte[] unescapeString(String tok, int lineNo) {
        if (!tok.startsWith("\"")) throw new AsmException(lineNo, "expected quoted string");
        String s = tok.endsWith("\"") ? tok.substring(1, tok.length() - 1) : tok.substring(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char e = s.charAt(++i);
                sb.append(switch (e) {
                    case 'n'  -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case '0'  -> '\0'; case '\\' -> '\\'; case '"' -> '"';
                    default -> e;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ─── instruction emission ─────────────────────────────────────────────────

    /** Returns bytes written (4 or 8). */
    private int emitInstr(String[] tok, byte[] buf, int pos, int lineNo) {
        if (tok.length == 0) return 0;
        String mn = tok[0].toLowerCase(Locale.ROOT);
        // strip memory-ordering suffixes from atomics
        if (mn.startsWith("lr.") || mn.startsWith("sc.") || mn.startsWith("amo"))
            mn = mn.replaceAll("\\.(aqrl|aq|rl)$", "");

        int word = encode(mn, tok, pos, lineNo);
        if (word == EXPAND_NEEDED) return expandPseudo(mn, tok, buf, pos, lineNo);
        putLE32(buf, pos, word);
        return 4;
    }

    private static final int EXPAND_NEEDED = Integer.MIN_VALUE;

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private int encode(String mn, String[] t, int pos, int ln) {
        return switch (mn) {
            // ── RV32I R-type ─────────────────────────────────────────────────
            case "add"   -> rtype(0x33, 0, 0x00, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "sub"   -> rtype(0x33, 0, 0x20, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "xor"   -> rtype(0x33, 4, 0x00, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "or"    -> rtype(0x33, 6, 0x00, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "and"   -> rtype(0x33, 7, 0x00, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "sll"   -> rtype(0x33, 1, 0x00, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "srl"   -> rtype(0x33, 5, 0x00, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "sra"   -> rtype(0x33, 5, 0x20, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "slt"   -> rtype(0x33, 2, 0x00, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "sltu"  -> rtype(0x33, 3, 0x00, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            // ── RV32M ────────────────────────────────────────────────────────
            case "mul"    -> rtype(0x33, 0, 0x01, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "mulh"   -> rtype(0x33, 1, 0x01, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "mulhsu" -> rtype(0x33, 2, 0x01, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "mulhu"  -> rtype(0x33, 3, 0x01, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "div"    -> rtype(0x33, 4, 0x01, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "divu"   -> rtype(0x33, 5, 0x01, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "rem"    -> rtype(0x33, 6, 0x01, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            case "remu"   -> rtype(0x33, 7, 0x01, reg(t[1],ln), reg(t[2],ln), reg(t[3],ln));
            // ── OP-IMM ───────────────────────────────────────────────────────
            case "addi"  -> itype(0x13, 0, reg(t[1],ln), reg(t[2],ln), parseImm(t[3],ln));
            case "slti"  -> itype(0x13, 2, reg(t[1],ln), reg(t[2],ln), parseImm(t[3],ln));
            case "sltiu" -> itype(0x13, 3, reg(t[1],ln), reg(t[2],ln), parseImm(t[3],ln));
            case "xori"  -> itype(0x13, 4, reg(t[1],ln), reg(t[2],ln), parseImm(t[3],ln));
            case "ori"   -> itype(0x13, 6, reg(t[1],ln), reg(t[2],ln), parseImm(t[3],ln));
            case "andi"  -> itype(0x13, 7, reg(t[1],ln), reg(t[2],ln), parseImm(t[3],ln));
            case "slli"  -> itype(0x13, 1, reg(t[1],ln), reg(t[2],ln), parseImm(t[3],ln) & 0x1F);
            case "srli"  -> itype(0x13, 5, reg(t[1],ln), reg(t[2],ln), parseImm(t[3],ln) & 0x1F);
            case "srai"  -> itype(0x13, 5, reg(t[1],ln), reg(t[2],ln), 0x400 | (parseImm(t[3],ln) & 0x1F));
            // ── Loads ────────────────────────────────────────────────────────
            case "lb"  -> { int[] ob = offsetBase(t[2],ln); yield itype(0x03, 0, reg(t[1],ln), ob[1], ob[0]); }
            case "lh"  -> { int[] ob = offsetBase(t[2],ln); yield itype(0x03, 1, reg(t[1],ln), ob[1], ob[0]); }
            case "lw"  -> { int[] ob = offsetBase(t[2],ln); yield itype(0x03, 2, reg(t[1],ln), ob[1], ob[0]); }
            case "lbu" -> { int[] ob = offsetBase(t[2],ln); yield itype(0x03, 4, reg(t[1],ln), ob[1], ob[0]); }
            case "lhu" -> { int[] ob = offsetBase(t[2],ln); yield itype(0x03, 5, reg(t[1],ln), ob[1], ob[0]); }
            // ── Stores ───────────────────────────────────────────────────────
            case "sb" -> { int[] ob = offsetBase(t[2],ln); yield stype(0x23, 0, ob[1], reg(t[1],ln), ob[0]); }
            case "sh" -> { int[] ob = offsetBase(t[2],ln); yield stype(0x23, 1, ob[1], reg(t[1],ln), ob[0]); }
            case "sw" -> { int[] ob = offsetBase(t[2],ln); yield stype(0x23, 2, ob[1], reg(t[1],ln), ob[0]); }
            // ── Branches ─────────────────────────────────────────────────────
            case "beq"  -> btype(0, reg(t[1],ln), reg(t[2],ln), labelOrImm(t[3],ln) - pos);
            case "bne"  -> btype(1, reg(t[1],ln), reg(t[2],ln), labelOrImm(t[3],ln) - pos);
            case "blt"  -> btype(4, reg(t[1],ln), reg(t[2],ln), labelOrImm(t[3],ln) - pos);
            case "bge"  -> btype(5, reg(t[1],ln), reg(t[2],ln), labelOrImm(t[3],ln) - pos);
            case "bltu" -> btype(6, reg(t[1],ln), reg(t[2],ln), labelOrImm(t[3],ln) - pos);
            case "bgeu" -> btype(7, reg(t[1],ln), reg(t[2],ln), labelOrImm(t[3],ln) - pos);
            // ── JAL ──────────────────────────────────────────────────────────
            case "jal" -> {
                if (t.length == 2) yield jtype(1, labelOrImm(t[1],ln) - pos);
                yield jtype(reg(t[1],ln), labelOrImm(t[2],ln) - pos);
            }
            // ── JALR ─────────────────────────────────────────────────────────
            case "jalr" -> {
                if (t.length == 2) yield itype(0x67, 0, 0, reg(t[1],ln), 0);
                if (t.length == 3) {
                    if (t[2].contains("(")) {
                        int[] ob = offsetBase(t[2],ln); yield itype(0x67, 0, reg(t[1],ln), ob[1], ob[0]);
                    }
                    yield itype(0x67, 0, reg(t[1],ln), reg(t[2],ln), 0);
                }
                yield itype(0x67, 0, reg(t[1],ln), reg(t[2],ln), parseImm(t[3],ln));
            }
            // ── Upper immediate ───────────────────────────────────────────────
            case "lui"   -> utype(0x37, reg(t[1],ln), parseImm(t[2],ln));
            case "auipc" -> utype(0x17, reg(t[1],ln), parseImm(t[2],ln));
            // ── System ───────────────────────────────────────────────────────
            case "ecall"  -> 0x00000073;
            case "ebreak" -> 0x00100073;
            case "fence"  -> 0x0FF0000F;
            case "fence.i"-> 0x0000100F;
            case "mret"   -> 0x30200073;
            case "sret"   -> 0x10200073;
            case "wfi"    -> 0x10500073;
            case "sfence.vma" -> rtype(0x73, 0, 0x09, 0, t.length > 1 ? reg(t[1],ln) : 0, t.length > 2 ? reg(t[2],ln) : 0);
            // ── CSR ──────────────────────────────────────────────────────────
            case "csrrw"  -> itype(0x73, 1, reg(t[1],ln),  reg(t[3],ln), parseCsr(t[2],ln));
            case "csrrs"  -> itype(0x73, 2, reg(t[1],ln),  reg(t[3],ln), parseCsr(t[2],ln));
            case "csrrc"  -> itype(0x73, 3, reg(t[1],ln),  reg(t[3],ln), parseCsr(t[2],ln));
            case "csrrwi" -> { int csr=parseCsr(t[2],ln); yield (csr<<20)|(parseImm(t[3],ln)<<15)|(5<<12)|(reg(t[1],ln)<<7)|0x73; }
            case "csrrsi" -> { int csr=parseCsr(t[2],ln); yield (csr<<20)|(parseImm(t[3],ln)<<15)|(6<<12)|(reg(t[1],ln)<<7)|0x73; }
            case "csrrci" -> { int csr=parseCsr(t[2],ln); yield (csr<<20)|(parseImm(t[3],ln)<<15)|(7<<12)|(reg(t[1],ln)<<7)|0x73; }
            // CSR pseudo-ops
            case "csrw"   -> itype(0x73, 1, 0, reg(t[2],ln), parseCsr(t[1],ln));
            case "csrr"   -> itype(0x73, 2, reg(t[1],ln), 0, parseCsr(t[2],ln));
            case "csrs"   -> itype(0x73, 2, 0, reg(t[2],ln), parseCsr(t[1],ln));
            case "csrc"   -> itype(0x73, 3, 0, reg(t[2],ln), parseCsr(t[1],ln));
            case "csrwi"  -> { int csr=parseCsr(t[1],ln); yield (csr<<20)|(parseImm(t[2],ln)<<15)|(5<<12)|0x73; }
            case "csrsi"  -> { int csr=parseCsr(t[1],ln); yield (csr<<20)|(parseImm(t[2],ln)<<15)|(6<<12)|0x73; }
            case "csrci"  -> { int csr=parseCsr(t[1],ln); yield (csr<<20)|(parseImm(t[2],ln)<<15)|(7<<12)|0x73; }
            // FP CSR
            case "frcsr"    -> itype(0x73, 2, reg(t[1],ln), 0, 0x003);
            case "fscsr"    -> t.length==2 ? itype(0x73,1,0,reg(t[1],ln),0x003) : itype(0x73,1,reg(t[1],ln),reg(t[2],ln),0x003);
            case "frrm"     -> itype(0x73, 2, reg(t[1],ln), 0, 0x002);
            case "fsrm"     -> t.length==2 ? itype(0x73,1,0,reg(t[1],ln),0x002) : itype(0x73,1,reg(t[1],ln),reg(t[2],ln),0x002);
            case "frflags"  -> itype(0x73, 2, reg(t[1],ln), 0, 0x001);
            case "fsflags"  -> t.length==2 ? itype(0x73,1,0,reg(t[1],ln),0x001) : itype(0x73,1,reg(t[1],ln),reg(t[2],ln),0x001);
            // ── RV32A Atomics ─────────────────────────────────────────────────
            case "lr.w" -> { int[] ob = offsetBase(t[2],ln); yield amoW(0x02, reg(t[1],ln), ob[1], 0); }
            case "sc.w" -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x03, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            case "amoswap.w" -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x01, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            case "amoadd.w"  -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x00, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            case "amoxor.w"  -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x04, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            case "amoand.w"  -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x0C, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            case "amoor.w"   -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x08, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            case "amomin.w"  -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x10, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            case "amomax.w"  -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x14, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            case "amominu.w" -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x18, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            case "amomaxu.w" -> { int[] ob = offsetBase(t[3],ln); yield amoW(0x1C, reg(t[1],ln), ob[1], reg(t[2],ln)); }
            // ── RV32F/D Loads/Stores ──────────────────────────────────────────
            case "flw" -> { int[] ob = offsetBase(t[2],ln); yield itype(0x07, 2, freg(t[1],ln), ob[1], ob[0]); }
            case "fld" -> { int[] ob = offsetBase(t[2],ln); yield itype(0x07, 3, freg(t[1],ln), ob[1], ob[0]); }
            case "fsw" -> { int[] ob = offsetBase(t[2],ln); yield stype(0x27, 2, ob[1], freg(t[1],ln), ob[0]); }
            case "fsd" -> { int[] ob = offsetBase(t[2],ln); yield stype(0x27, 3, ob[1], freg(t[1],ln), ob[0]); }
            // ── FMA ──────────────────────────────────────────────────────────
            case "fmadd.s"  -> r4type(0x43, 0, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln), freg(t[4],ln));
            case "fmsub.s"  -> r4type(0x47, 0, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln), freg(t[4],ln));
            case "fnmsub.s" -> r4type(0x4B, 0, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln), freg(t[4],ln));
            case "fnmadd.s" -> r4type(0x4F, 0, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln), freg(t[4],ln));
            case "fmadd.d"  -> r4type(0x43, 1, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln), freg(t[4],ln));
            case "fmsub.d"  -> r4type(0x47, 1, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln), freg(t[4],ln));
            case "fnmsub.d" -> r4type(0x4B, 1, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln), freg(t[4],ln));
            case "fnmadd.d" -> r4type(0x4F, 1, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln), freg(t[4],ln));
            // ── RV32F Arithmetic ──────────────────────────────────────────────
            case "fadd.s"   -> fpR(0x00, 7, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fsub.s"   -> fpR(0x04, 7, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fmul.s"   -> fpR(0x08, 7, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fdiv.s"   -> fpR(0x0C, 7, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fsqrt.s"  -> fpR(0x2C, 7, freg(t[1],ln), freg(t[2],ln), 0);
            case "fsgnj.s"  -> fpR(0x10, 0, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fsgnjn.s" -> fpR(0x10, 1, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fsgnjx.s" -> fpR(0x10, 2, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fmin.s"   -> fpR(0x14, 0, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fmax.s"   -> fpR(0x14, 1, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fcvt.w.s" -> fpR(0x60, 7, reg(t[1],ln),  freg(t[2],ln), 0);
            case "fcvt.wu.s"-> fpR(0x60, 7, reg(t[1],ln),  freg(t[2],ln), 1);
            case "fcvt.s.w" -> fpR(0x68, 7, freg(t[1],ln), reg(t[2],ln),  0);
            case "fcvt.s.wu"-> fpR(0x68, 7, freg(t[1],ln), reg(t[2],ln),  1);
            case "fmv.x.w"  -> fpR(0x70, 0, reg(t[1],ln),  freg(t[2],ln), 0);
            case "fmv.w.x"  -> fpR(0x78, 0, freg(t[1],ln), reg(t[2],ln),  0);
            case "fclass.s" -> fpR(0x70, 1, reg(t[1],ln),  freg(t[2],ln), 0);
            case "feq.s"    -> fpR(0x50, 2, reg(t[1],ln),  freg(t[2],ln), freg(t[3],ln));
            case "flt.s"    -> fpR(0x50, 1, reg(t[1],ln),  freg(t[2],ln), freg(t[3],ln));
            case "fle.s"    -> fpR(0x50, 0, reg(t[1],ln),  freg(t[2],ln), freg(t[3],ln));
            // ── RV32D Arithmetic ──────────────────────────────────────────────
            case "fadd.d"   -> fpR(0x01, 7, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fsub.d"   -> fpR(0x05, 7, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fmul.d"   -> fpR(0x09, 7, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fdiv.d"   -> fpR(0x0D, 7, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fsqrt.d"  -> fpR(0x2D, 7, freg(t[1],ln), freg(t[2],ln), 0);
            case "fsgnj.d"  -> fpR(0x11, 0, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fsgnjn.d" -> fpR(0x11, 1, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fsgnjx.d" -> fpR(0x11, 2, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fmin.d"   -> fpR(0x15, 0, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fmax.d"   -> fpR(0x15, 1, freg(t[1],ln), freg(t[2],ln), freg(t[3],ln));
            case "fcvt.w.d" -> fpR(0x61, 7, reg(t[1],ln),  freg(t[2],ln), 0);
            case "fcvt.wu.d"-> fpR(0x61, 7, reg(t[1],ln),  freg(t[2],ln), 1);
            case "fcvt.d.w" -> fpR(0x69, 7, freg(t[1],ln), reg(t[2],ln),  0);
            case "fcvt.d.wu"-> fpR(0x69, 7, freg(t[1],ln), reg(t[2],ln),  1);
            case "fcvt.s.d" -> fpR(0x20, 7, freg(t[1],ln), freg(t[2],ln), 1);
            case "fcvt.d.s" -> fpR(0x21, 7, freg(t[1],ln), freg(t[2],ln), 0);
            case "fclass.d" -> fpR(0x71, 1, reg(t[1],ln),  freg(t[2],ln), 0);
            case "feq.d"    -> fpR(0x51, 2, reg(t[1],ln),  freg(t[2],ln), freg(t[3],ln));
            case "flt.d"    -> fpR(0x51, 1, reg(t[1],ln),  freg(t[2],ln), freg(t[3],ln));
            case "fle.d"    -> fpR(0x51, 0, reg(t[1],ln),  freg(t[2],ln), freg(t[3],ln));
            // ── Pseudo-instructions (single-word) ─────────────────────────────
            case "nop"  -> 0x00000013;
            case "mv"   -> itype(0x13, 0, reg(t[1],ln), reg(t[2],ln), 0);
            case "neg"  -> rtype(0x33, 0, 0x20, reg(t[1],ln), 0, reg(t[2],ln));
            case "not"  -> itype(0x13, 4, reg(t[1],ln), reg(t[2],ln), -1);
            case "seqz" -> itype(0x13, 3, reg(t[1],ln), reg(t[2],ln), 1);
            case "snez" -> rtype(0x33, 3, 0x00, reg(t[1],ln), 0, reg(t[2],ln));
            case "sltz" -> rtype(0x33, 2, 0x00, reg(t[1],ln), reg(t[2],ln), 0);
            case "sgtz" -> rtype(0x33, 2, 0x00, reg(t[1],ln), 0, reg(t[2],ln));
            case "beqz" -> btype(0, reg(t[1],ln), 0, labelOrImm(t[2],ln) - pos);
            case "bnez" -> btype(1, reg(t[1],ln), 0, labelOrImm(t[2],ln) - pos);
            case "bgez" -> btype(5, reg(t[1],ln), 0, labelOrImm(t[2],ln) - pos);
            case "bltz" -> btype(4, reg(t[1],ln), 0, labelOrImm(t[2],ln) - pos);
            case "blez" -> btype(5, 0, reg(t[1],ln), labelOrImm(t[2],ln) - pos);
            case "bgtz" -> btype(4, 0, reg(t[1],ln), labelOrImm(t[2],ln) - pos);
            case "j"    -> jtype(0, labelOrImm(t[1],ln) - pos);
            case "jr"   -> itype(0x67, 0, 0, reg(t[1],ln), 0);
            case "ret"  -> 0x00008067;
            case "halt" -> 0x00100073;
            // FP pseudo
            case "fmv.s"  -> fpR(0x10, 0, freg(t[1],ln), freg(t[2],ln), freg(t[2],ln));
            case "fabs.s" -> fpR(0x10, 2, freg(t[1],ln), freg(t[2],ln), freg(t[2],ln));
            case "fneg.s" -> fpR(0x10, 1, freg(t[1],ln), freg(t[2],ln), freg(t[2],ln));
            case "fmv.d"  -> fpR(0x11, 0, freg(t[1],ln), freg(t[2],ln), freg(t[2],ln));
            case "fabs.d" -> fpR(0x11, 2, freg(t[1],ln), freg(t[2],ln), freg(t[2],ln));
            case "fneg.d" -> fpR(0x11, 1, freg(t[1],ln), freg(t[2],ln), freg(t[2],ln));
            // ── Multi-word pseudos ────────────────────────────────────────────
            case "li", "la", "call", "tail" -> EXPAND_NEEDED;
            default -> throw new AsmException(ln, "unknown instruction '" + mn + "'");
        };
    }

    private int expandPseudo(String mn, String[] t, byte[] buf, int pos, int ln) {
        switch (mn) {
            case "li" -> {
                int rd  = reg(t[1], ln);
                int imm = parseImm(t[2], ln);
                if (imm >= -2048 && imm <= 2047) {
                    putLE32(buf, pos, itype(0x13, 0, rd, 0, imm));
                    return 4;
                }
                int hi = (imm + 0x800) >> 12;
                int lo = imm - (hi << 12);
                putLE32(buf, pos,     utype(0x37, rd, hi));
                putLE32(buf, pos + 4, itype(0x13, 0, rd, rd, lo));
                return 8;
            }
            case "la" -> {
                int rd   = reg(t[1], ln);
                int addr = labelOrImm(t[2], ln);
                int delta = addr - pos;
                int hi = (delta + 0x800) >> 12;
                int lo = delta - (hi << 12);
                putLE32(buf, pos,     utype(0x17, rd, hi));
                putLE32(buf, pos + 4, itype(0x13, 0, rd, rd, lo));
                return 8;
            }
            case "call" -> {
                int addr  = labelOrImm(t[1], ln);
                int delta = addr - pos;
                int hi = (delta + 0x800) >> 12;
                int lo = delta - (hi << 12);
                putLE32(buf, pos,     utype(0x17, 1, hi));   // auipc ra, hi
                putLE32(buf, pos + 4, itype(0x67, 0, 1, 1, lo)); // jalr ra, ra, lo
                return 8;
            }
            case "tail" -> {
                int addr  = labelOrImm(t[1], ln);
                int delta = addr - pos;
                int hi = (delta + 0x800) >> 12;
                int lo = delta - (hi << 12);
                putLE32(buf, pos,     utype(0x17, 6, hi));   // auipc t1, hi (x6)
                putLE32(buf, pos + 4, itype(0x67, 0, 0, 6, lo)); // jalr x0, t1, lo
                return 8;
            }
            default -> throw new AsmException(ln, "unknown pseudo '" + mn + "'");
        }
    }
}
