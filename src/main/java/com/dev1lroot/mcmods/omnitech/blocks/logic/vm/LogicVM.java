package com.dev1lroot.mcmods.omnitech.blocks.logic.vm;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;

/**
 * RISC-V RV32GC binary emulator for the LogicMachine block.
 *
 * Executes real 32-bit (and 16-bit C-extension) machine code loaded into RAM.
 * PC is a byte address. Programs are loaded by the LogicMachineBlockEntity
 * from the MCU's PROGRAM_BINARY data component.
 *
 * Memory map:
 *   0x00000000 … RAM top  : flat RAM from connected RAM cards (program + data)
 *   0x02000000 … +0xC000  : CLINT (msip@0, mtimecmp@+0x4000, mtime@+0xBFF8)
 *   0x10000000 … +0x4B000 : Framebuffer 320×240 XRGB (x8r8g8b8 LE)
 *   0x10001000 … +0x1000  : UART ns16550a (TX only, 8-bit stride)
 *   0xF0000000 … +0x10000 : GPIO MMIO  (LW/SW portId = (addr-base)/4)
 *
 * ECALL (a7 = syscall number, args in a0-a5, return in a0):
 *   0  HALT
 *   1  SLP        a0=ticks
 *   2  RND        a0=min, a1=max → a0
 *  10  GPIO_OUT   a0=portId, a1=value(0-15)
 *  11  GPIO_IN    a0=portId → a0
 *  20  DISP_SET   a0=id, a1=x, a2=y, a3=0xRRGGBB
 *  21  DISP_RST   a0=id
 *  22  DISP_DIM   a0=id → a0=width, a1=height
 *  23  DISP_LINE  a0=id, a1=x1, a2=y1, a3=x2, a4=y2, a5=color
 *  24  DISP_RECT  a0=id, a1=x, a2=y, a3=w, a4=h, a5=color
 *  25  DISP_BLIT  a0=id, a1=x, a2=y, a3=w, a4=h, a5=ramAddr
 *  30  FLOPPY     a0=driveId, a1=sector, a2=dstAddr → a0=1 on success
 */
public class LogicVM {

    // ── Peripheral interfaces ─────────────────────────────────────────────────

    public interface GPIOAccess {
        int  read(int portId);
        void write(int portId, int value);
    }
    public interface ConsoleAccess {
        void putchar(int ch);
    }
    public interface FramebufferAccess {
        int  fbRdByte(int offset);
        void fbWrByte(int offset, int val);
    }
    public interface DisplayAccess {
        void setPixel(int id, int x, int y, int color);
        void reset(int id);
        int  getWidth(int id);
        int  getHeight(int id);
        void drawLine(int id, int x1, int y1, int x2, int y2, int color);
        void fillRect(int id, int x, int y, int w, int h, int color);
        void blit(int id, int x, int y, int w, int h, int[] pixels);
    }
    public interface RAMAccess {
        int  read(int address);
        void write(int address, int value);
        int  capacity();
    }
    public interface FloppyAccess {
        boolean loadSector(int driveId, int sector, int dstAddr);
    }

    // ── Registers and state ───────────────────────────────────────────────────

    // ── Console / Framebuffer peripherals (set by block entity) ──────────────

    public ConsoleAccess    console     = null;
    public FramebufferAccess framebuffer = null;

    // ── Registers and state ───────────────────────────────────────────────────

    public int[]    regs   = new int[32];
    public double[] fregs  = new double[32];
    public int      fcsr   = 0;
    public int      pc     = 0;
    public int      executingPC = 0;
    public int      sleepTicks  = 0;
    public boolean  halted      = false;
    public boolean  loaded      = false;

    // CLINT timer (incremented by block entity each game tick)
    public long mtime    = 0L;
    public long mtimecmp = Long.MAX_VALUE;

    private int lrReservation = -1;

    // Minimal UART state (ns16550a at UART_BASE)
    private int uartLcr     = 0;
    private int uartScratch = 0;

    // ── TLB ───────────────────────────────────────────────────────────────────

    private static final int TLB_BITS = 9;               // 512 entries
    private static final int TLB_SIZE = 1 << TLB_BITS;
    private static final int TLB_MASK = TLB_SIZE - 1;
    private final int[]  tlbVPN  = new int[TLB_SIZE];    // 22-bit VPN, -1 = invalid
    private final int[]  tlbPhys = new int[TLB_SIZE];    // physical page base address
    private final byte[] tlbPerm = new byte[TLB_SIZE];   // bit0=R bit1=W bit2=X bit3=U

    private static final class PFE extends RuntimeException {
        final int cause, tval;
        PFE(int c, int t) { super(null, null, true, false); cause = c; tval = t; }
    }

    // ── Privilege levels ──────────────────────────────────────────────────────

    public int priv = 3; // 3=M, 1=S, 0=U

    // M-mode CSRs
    private int mstatus  = 0;
    private int mtvec    = 0;
    private int mepc     = 0;
    private int mcause   = 0;
    private int mtval    = 0;
    private int mie      = 0;
    private int mip      = 0;
    private int mscratch = 0;
    private int medeleg  = 0;
    private int mideleg  = 0;
    // RV32IMAFDC with S+U modes: MXL=01 | A|C|D|F|I|M|S|U
    private static final int MISA_FIXED = 0x40000000 | (1<<20)|(1<<18)|(1<<12)|(1<<8)|(1<<5)|(1<<3)|(1<<2)|(1<<0);

    // S-mode CSRs
    private int stvec    = 0;
    private int sepc     = 0;
    private int scause   = 0;
    private int stval    = 0;
    private int sscratch = 0;
    private int satp     = 0;

    // mstatus / sstatus bit masks
    private static final int MSTATUS_MIE  = 1 << 3;
    private static final int MSTATUS_MPIE = 1 << 7;
    private static final int MSTATUS_MPP  = 3 << 11;
    private static final int MSTATUS_SIE  = 1 << 1;
    private static final int MSTATUS_SPIE = 1 << 5;
    private static final int MSTATUS_SPP  = 1 << 8;
    private static final int MSTATUS_FS   = 3 << 13;
    // Bits visible to S-mode via sstatus
    private static final int SSTATUS_MASK = MSTATUS_SIE | MSTATUS_SPIE | MSTATUS_SPP | (3<<13) | (1<<18) | (1<<19);
    // S-mode writable bits in mie/mip
    private static final int SIE_MASK     = (1<<1) | (1<<5) | (1<<9);

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isEmpty()     { return !loaded; }
    public int     currentLine() { return executingPC >>> 2; }

    public void unload() { loaded = false; reset(); }

    /**
     * Called once per game tick by the block entity to advance the CLINT timer.
     * Increments mtime by 50 000 (1 MHz timebase / 20 ticks per second) and
     * sets STIP in mip when mtime >= mtimecmp.
     */
    public void advanceClock() {
        mtime += 50_000L;
        if (Long.compareUnsigned(mtime, mtimecmp) >= 0) {
            mip |= (1 << 5); // STIP – supervisor timer interrupt pending
        }
    }

    public void reset() {
        pc = 0; executingPC = 0; sleepTicks = 0; halted = false;
        Arrays.fill(regs, 0);
        long nanBoxedZero = 0xFFFFFFFF00000000L;
        for (int i = 0; i < 32; i++) fregs[i] = Double.longBitsToDouble(nanBoxedZero);
        fcsr = 0; lrReservation = -1;
        priv = 3;
        mstatus = 0; mtvec = 0; mepc = 0; mcause = 0; mtval = 0;
        mie = 0; mip = 0; mscratch = 0; medeleg = 0; mideleg = 0;
        stvec = 0; sepc = 0; scause = 0; stval = 0; sscratch = 0; satp = 0;
        Arrays.fill(tlbVPN, -1);
        mtime = 0L; mtimecmp = Long.MAX_VALUE;
        uartLcr = 0; uartScratch = 0;
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public boolean tick(GPIOAccess gpio) { return tick(gpio, null, null, null); }
    public boolean tick(GPIOAccess gpio, DisplayAccess display) { return tick(gpio, display, null, null); }

    public boolean tick(GPIOAccess gpio, DisplayAccess display, RAMAccess ram, FloppyAccess floppy) {
        if (halted || !loaded) return false;

        // interrupt check (mip & mie, gated by global enable bits)
        int pending = mip & mie;
        if (pending != 0) {
            boolean mEnabled = priv < 3 || (mstatus & MSTATUS_MIE) != 0;
            boolean sEnabled = priv < 1 || (priv == 1 && (mstatus & MSTATUS_SIE) != 0);
            int mPending = pending & ~mideleg;
            int sPending = pending & mideleg;
            if (mEnabled && mPending != 0) {
                int bit = Integer.numberOfTrailingZeros(mPending);
                trapToM(0x80000000 | bit, pc);
            } else if (sEnabled && sPending != 0 && priv <= 1) {
                int bit = Integer.numberOfTrailingZeros(sPending);
                trapToS(0x80000000 | bit, pc);
            }
        }

        // fetch + execute (page faults throw PFE, caught below)
        int instrPC = pc;
        executingPC = instrPC;

        try {
            int fetchPhys = translateVA(pc, 2, gpio, ram);
            int lo  = physRdByte(fetchPhys,     gpio, ram);
            int hi  = physRdByte(fetchPhys + 1, gpio, ram);
            int w16 = lo | (hi << 8);

            if ((w16 & 0x3) != 0x3) {
                pc += 2;
                execC(w16, instrPC, gpio, display, ram, floppy);
            } else {
                int fetchPhys2 = translateVA(pc + 2, 2, gpio, ram);
                int lo2 = physRdByte(fetchPhys2,     gpio, ram);
                int hi2 = physRdByte(fetchPhys2 + 1, gpio, ram);
                int word = lo | (hi << 8) | (lo2 << 16) | (hi2 << 24);
                pc += 4;
                exec32(word, instrPC, gpio, display, ram, floppy);
            }
        } catch (PFE pf) {
            handleTrap(pf.cause, pf.tval);
        }
        return !halted;
    }

    // ── 32-bit decoder ────────────────────────────────────────────────────────

    private void exec32(int word, int instrPC,
            GPIOAccess gpio, DisplayAccess display, RAMAccess ram, FloppyAccess floppy) {
        int opcode = word & 0x7F;
        int rd     = (word >> 7)  & 0x1F;
        int funct3 = (word >> 12) & 0x7;
        int rs1    = (word >> 15) & 0x1F;
        int rs2    = (word >> 20) & 0x1F;
        int funct7 = (word >> 25) & 0x7F;

        switch (opcode) {
            case 0x37 -> wr(rd, word & 0xFFFFF000);                          // LUI
            case 0x17 -> wr(rd, instrPC + (word & 0xFFFFF000));              // AUIPC
            case 0x6F -> { wr(rd, pc); pc = instrPC + jImm(word); }         // JAL
            case 0x67 -> { int ret = pc; pc = (regs[rs1] + iImm(word)) & ~1; wr(rd, ret); } // JALR
            case 0x63 -> branch(funct3, rs1, rs2, instrPC, word);           // BRANCH
            case 0x03 -> doLoad(funct3, rd, rs1, iImm(word), gpio, ram);    // LOAD
            case 0x07 -> fpLoad(funct3, rd, rs1, iImm(word), gpio, ram);    // LOAD-FP
            case 0x23 -> doStore(funct3, rs1, rs2, sImm(word), gpio, ram);  // STORE
            case 0x27 -> fpStore(funct3, rs1, rs2, sImm(word), gpio, ram);  // STORE-FP
            case 0x13 -> opImm(funct3, funct7, rd, rs1, word);              // OP-IMM
            case 0x33 -> op(funct3, funct7, rd, rs1, rs2);                  // OP
            case 0x0F -> { /* FENCE / FENCE.I: NOP */ }
            case 0x73 -> sysInsn(funct3, rd, rs1, rs2, word, gpio, display, ram, floppy);
            case 0x2F -> atomic(funct7, rd, rs1, rs2, gpio, ram);           // AMO
            // FMA R4-type: fmt = bits[26:25]
            case 0x43 -> fma4(0, (word >> 25) & 3, rd, rs1, rs2, word >> 27);  // FMADD
            case 0x47 -> fma4(1, (word >> 25) & 3, rd, rs1, rs2, word >> 27);  // FMSUB
            case 0x4B -> fma4(2, (word >> 25) & 3, rd, rs1, rs2, word >> 27);  // FNMSUB
            case 0x4F -> fma4(3, (word >> 25) & 3, rd, rs1, rs2, word >> 27);  // FNMADD
            case 0x53 -> fpOp(funct7, funct3, rd, rs1, rs2);               // OP-FP
            // illegal / unimplemented: treated as NOP
        }
    }

    // ─── immediate extractors ─────────────────────────────────────────────────

    private static int iImm(int word) { return word >> 20; }

    private static int sImm(int word) {
        return (word >> 20 & 0xFFFFFFE0) | (word >> 7 & 0x1F);
    }

    private static int bImm(int word) {
        return (word >> 19 & 0xFFFFF000) | (word <<  4 & 0x800)
             | (word >> 20 & 0x7E0)      | (word >>  7 & 0x1E);
    }

    private static int jImm(int word) {
        return (word >> 11 & 0xFFF00000) | (word & 0xFF000)
             | (word >>  9 & 0x800)      | (word >> 20 & 0x7FE);
    }

    // ─── branches ─────────────────────────────────────────────────────────────

    private void branch(int f3, int rs1, int rs2, int instrPC, int word) {
        int off = bImm(word);
        boolean taken = switch (f3) {
            case 0 -> regs[rs1] == regs[rs2];
            case 1 -> regs[rs1] != regs[rs2];
            case 4 -> regs[rs1] <  regs[rs2];
            case 5 -> regs[rs1] >= regs[rs2];
            case 6 -> Integer.compareUnsigned(regs[rs1], regs[rs2]) <  0;
            case 7 -> Integer.compareUnsigned(regs[rs1], regs[rs2]) >= 0;
            default -> false;
        };
        if (taken) pc = instrPC + off;
    }

    // ─── integer ALU ──────────────────────────────────────────────────────────

    private void opImm(int f3, int f7, int rd, int rs1, int word) {
        int imm = iImm(word);
        int shamt = rs2Of(word); // bits[24:20]
        switch (f3) {
            case 0 -> wr(rd, regs[rs1] + imm);
            case 2 -> wr(rd, regs[rs1] <  imm ? 1 : 0);
            case 3 -> wr(rd, Integer.compareUnsigned(regs[rs1], imm) < 0 ? 1 : 0);
            case 4 -> wr(rd, regs[rs1] ^ imm);
            case 6 -> wr(rd, regs[rs1] | imm);
            case 7 -> wr(rd, regs[rs1] & imm);
            case 1 -> wr(rd, regs[rs1] << shamt);
            case 5 -> wr(rd, f7 == 0x20 ? regs[rs1] >> shamt : regs[rs1] >>> shamt);
        }
    }

    private void op(int f3, int f7, int rd, int rs1, int rs2) {
        if (f7 == 0x01) { // RV32M
            switch (f3) {
                case 0 -> wr(rd, regs[rs1] * regs[rs2]);
                case 1 -> wr(rd, (int)(((long)regs[rs1] * (long)regs[rs2]) >> 32));
                case 2 -> wr(rd, (int)(((long)regs[rs1] * Integer.toUnsignedLong(regs[rs2])) >> 32));
                case 3 -> wr(rd, (int)((Integer.toUnsignedLong(regs[rs1]) * Integer.toUnsignedLong(regs[rs2])) >> 32));
                case 4 -> wr(rd, regs[rs2] == 0 ? -1 : regs[rs1] / regs[rs2]);
                case 5 -> wr(rd, regs[rs2] == 0 ? -1 : (int)(Integer.toUnsignedLong(regs[rs1]) / Integer.toUnsignedLong(regs[rs2])));
                case 6 -> wr(rd, regs[rs2] == 0 ? regs[rs1] : regs[rs1] % regs[rs2]);
                case 7 -> wr(rd, regs[rs2] == 0 ? regs[rs1] : (int)(Integer.toUnsignedLong(regs[rs1]) % Integer.toUnsignedLong(regs[rs2])));
            }
            return;
        }
        switch (f3) {
            case 0 -> wr(rd, f7 == 0x20 ? regs[rs1] - regs[rs2] : regs[rs1] + regs[rs2]);
            case 1 -> wr(rd, regs[rs1] << (regs[rs2] & 31));
            case 2 -> wr(rd, regs[rs1] < regs[rs2] ? 1 : 0);
            case 3 -> wr(rd, Integer.compareUnsigned(regs[rs1], regs[rs2]) < 0 ? 1 : 0);
            case 4 -> wr(rd, regs[rs1] ^ regs[rs2]);
            case 5 -> wr(rd, f7 == 0x20 ? regs[rs1] >> (regs[rs2] & 31) : regs[rs1] >>> (regs[rs2] & 31));
            case 6 -> wr(rd, regs[rs1] | regs[rs2]);
            case 7 -> wr(rd, regs[rs1] & regs[rs2]);
        }
    }

    // ─── loads / stores ───────────────────────────────────────────────────────

    private void doLoad(int f3, int rd, int rs1, int imm, GPIOAccess g, RAMAccess ram) {
        int addr = regs[rs1] + imm;
        switch (f3) {
            case 0 -> wr(rd, (byte)  memRdByte(addr, g, ram));
            case 1 -> wr(rd, (short) memRdHalf(addr, g, ram));
            case 2 -> wr(rd,         memRdWord(addr, g, ram));
            case 4 -> wr(rd, memRdByte(addr, g, ram) & 0xFF);
            case 5 -> wr(rd, memRdHalf(addr, g, ram) & 0xFFFF);
        }
    }

    private void doStore(int f3, int rs1, int rs2, int imm, GPIOAccess g, RAMAccess ram) {
        int addr = regs[rs1] + imm;
        switch (f3) {
            case 0 -> memWrByte(addr, regs[rs2], g, ram);
            case 1 -> memWrHalf(addr, regs[rs2], g, ram);
            case 2 -> memWrWord(addr, regs[rs2], g, ram);
        }
    }

    private void fpLoad(int f3, int rd, int rs1, int imm, GPIOAccess g, RAMAccess ram) {
        int addr = regs[rs1] + imm;
        switch (f3) {
            case 2 -> fwrite(rd, Float.intBitsToFloat(memRdWord(addr, g, ram)));  // FLW
            case 3 -> fregs[rd] = Double.longBitsToDouble(memRdLong(addr, g, ram)); // FLD
        }
    }

    private void fpStore(int f3, int rs1, int rs2, int imm, GPIOAccess g, RAMAccess ram) {
        int addr = regs[rs1] + imm;
        switch (f3) {
            case 2 -> memWrWord(addr, Float.floatToRawIntBits(fread(rs2)), g, ram); // FSW
            case 3 -> memWrLong(addr, Double.doubleToRawLongBits(fregs[rs2]), g, ram); // FSD
        }
    }

    // ─── atomics ─────────────────────────────────────────────────────────────

    private void atomic(int f7, int rd, int rs1, int rs2, GPIOAccess g, RAMAccess ram) {
        int funct5 = f7 >> 2;
        int addr   = regs[rs1];
        switch (funct5) {
            case 0x02 -> { wr(rd, memRdWord(addr, g, ram)); lrReservation = addr; }        // LR.W
            case 0x03 -> {
                if (lrReservation == addr) { memWrWord(addr, regs[rs2], g, ram); wr(rd, 0); }
                else                       { wr(rd, 1); }
                lrReservation = -1;
            }
            default -> {
                int t = memRdWord(addr, g, ram);
                int v = switch (funct5) {
                    case 0x01 -> regs[rs2];
                    case 0x00 -> t + regs[rs2];
                    case 0x04 -> t ^ regs[rs2];
                    case 0x0C -> t & regs[rs2];
                    case 0x08 -> t | regs[rs2];
                    case 0x10 -> Math.min(t, regs[rs2]);
                    case 0x14 -> Math.max(t, regs[rs2]);
                    case 0x18 -> Integer.compareUnsigned(t, regs[rs2]) < 0 ? t : regs[rs2];
                    case 0x1C -> Integer.compareUnsigned(t, regs[rs2]) > 0 ? t : regs[rs2];
                    default -> regs[rs2];
                };
                memWrWord(addr, v, g, ram);
                wr(rd, t);
            }
        }
    }

    // ─── system / CSR ─────────────────────────────────────────────────────────

    private void sysInsn(int f3, int rd, int rs1, int rs2, int word,
            GPIOAccess gpio, DisplayAccess display, RAMAccess ram, FloppyAccess floppy) {
        if (f3 == 0) {
            int imm12 = word >>> 20;
            switch (imm12) {
                case 0x000 -> { // ECALL
                    if (priv == 3) {
                        ecall(gpio, display, ram, floppy);
                    } else if (priv == 1) {
                        // S-mode ECALL → SBI (intercepted at Java level, no trap needed)
                        sbiCall(gpio, ram);
                    } else {
                        // U-mode ECALL (cause=8)
                        if ((medeleg & (1 << 8)) != 0) trapToS(8, 0);
                        else trapToM(8, 0);
                    }
                }
                case 0x001 -> trapToM(3, pc);                           // EBREAK
                case 0x102 -> execSRET();                               // SRET
                case 0x302 -> execMRET();                               // MRET
                case 0x105 -> { /* WFI: NOP */ }
                default    -> { if ((word >> 25) == 0x09) Arrays.fill(tlbVPN, -1); } // SFENCE.VMA
            }
        } else {
            csrInsn(f3, rd, rs1, rs1, word >>> 20);
        }
    }

    private void execMRET() {
        pc    = mepc;
        int mpp = (mstatus & MSTATUS_MPP) >>> 11;
        priv  = mpp;
        // restore MIE = MPIE, MPIE = 1, MPP = U
        mstatus = (mstatus & ~(MSTATUS_MIE | MSTATUS_MPIE | MSTATUS_MPP))
                | ((mstatus & MSTATUS_MPIE) != 0 ? MSTATUS_MIE : 0)
                | MSTATUS_MPIE;
    }

    private void execSRET() {
        pc   = sepc;
        int spp = (mstatus & MSTATUS_SPP) != 0 ? 1 : 0;
        priv = spp;
        // restore SIE = SPIE, SPIE = 1, SPP = 0
        mstatus = (mstatus & ~(MSTATUS_SIE | MSTATUS_SPIE | MSTATUS_SPP))
                | ((mstatus & MSTATUS_SPIE) != 0 ? MSTATUS_SIE : 0)
                | MSTATUS_SPIE;
    }

    private void trapToM(int cause, int tval) {
        mepc   = (cause < 0) ? pc : executingPC; // interrupts use current PC
        mcause = cause;
        mtval  = tval;
        int prevPriv = priv;
        priv   = 3;
        // save MIE→MPIE, clear MIE, save priv→MPP
        mstatus = (mstatus & ~(MSTATUS_MPIE | MSTATUS_MIE | MSTATUS_MPP))
                | ((mstatus & MSTATUS_MIE) != 0 ? MSTATUS_MPIE : 0)
                | ((prevPriv & 3) << 11);
        int mode = mtvec & 3;
        int base = mtvec & ~3;
        pc = (mode == 1 && cause < 0) ? base + ((cause & 0x7FFFFFFF) << 2) : base;
    }

    private void trapToS(int cause, int tval) {
        sepc   = (cause < 0) ? pc : executingPC;
        scause = cause;
        stval  = tval;
        int prevPriv = priv;
        priv   = 1;
        // save SIE→SPIE, clear SIE, save priv→SPP
        mstatus = (mstatus & ~(MSTATUS_SPIE | MSTATUS_SIE | MSTATUS_SPP))
                | ((mstatus & MSTATUS_SIE) != 0 ? MSTATUS_SPIE : 0)
                | ((prevPriv == 1) ? MSTATUS_SPP : 0);
        int mode = stvec & 3;
        int base = stvec & ~3;
        pc = (mode == 1 && cause < 0) ? base + ((cause & 0x7FFFFFFF) << 2) : base;
    }

    private void handleTrap(int cause, int tval) {
        int exCode = cause & 0x7FFFFFFF;
        boolean isInterrupt = cause < 0;
        boolean delegate = priv < 3 &&
            (isInterrupt ? (mideleg & (1 << exCode)) != 0 : (medeleg & (1 << exCode)) != 0);
        if (delegate) trapToS(cause, tval);
        else          trapToM(cause, tval);
    }

    private void csrInsn(int f3, int rd, int rs1Idx, int uimm5, int csr) {
        int old = csrRead(csr);
        int rs1v = (f3 <= 3) ? regs[rs1Idx] : uimm5; // immediate forms use rs1 field as uimm5
        switch (f3) {
            case 1 -> { csrWrite(csr, rs1v);           wr(rd, old); } // CSRRW
            case 2 -> { if (rs1Idx != 0) csrWrite(csr, old | rs1v); wr(rd, old); } // CSRRS
            case 3 -> { if (rs1Idx != 0) csrWrite(csr, old & ~rs1v); wr(rd, old); } // CSRRC
            case 5 -> { csrWrite(csr, rs1v);           wr(rd, old); } // CSRRWI
            case 6 -> { if (uimm5 != 0)  csrWrite(csr, old | rs1v); wr(rd, old); } // CSRRSI (uimm5=rs1 field)
            case 7 -> { if (uimm5 != 0)  csrWrite(csr, old & ~rs1v); wr(rd, old); } // CSRRCI
        }
    }

    private int csrRead(int csr) {
        return switch (csr) {
            // FP CSRs
            case 0x001 -> fcsr & 0x1F;
            case 0x002 -> (fcsr >>> 5) & 0x7;
            case 0x003 -> fcsr & 0xFF;
            // S-mode CSRs (restricted view)
            case 0x100 -> mstatus & SSTATUS_MASK;
            case 0x104 -> mie & SIE_MASK;
            case 0x105 -> stvec;
            case 0x140 -> sscratch;
            case 0x141 -> sepc;
            case 0x142 -> scause;
            case 0x143 -> stval;
            case 0x144 -> mip & SIE_MASK;
            case 0x180 -> satp;
            // M-mode CSRs
            case 0x300 -> mstatus;
            case 0x301 -> MISA_FIXED;
            case 0x302 -> medeleg;
            case 0x303 -> mideleg;
            case 0x304 -> mie;
            case 0x305 -> mtvec;
            case 0x340 -> mscratch;
            case 0x341 -> mepc;
            case 0x342 -> mcause;
            case 0x343 -> mtval;
            case 0x344 -> mip;
            case 0xF14 -> 0; // mhartid = 0 (single hart)
            // counters (read-only, return 0)
            case 0xC00, 0xC01, 0xC02 -> 0;
            default -> 0;
        };
    }

    private void csrWrite(int csr, int val) {
        switch (csr) {
            // FP CSRs
            case 0x001 -> fcsr = (fcsr & ~0x1F) | (val & 0x1F);
            case 0x002 -> fcsr = (fcsr & ~0xE0) | ((val & 0x7) << 5);
            case 0x003 -> fcsr = val & 0xFF;
            // S-mode CSRs
            case 0x100 -> mstatus = (mstatus & ~SSTATUS_MASK) | (val & SSTATUS_MASK);
            case 0x104 -> mie = (mie & ~SIE_MASK) | (val & SIE_MASK);
            case 0x105 -> stvec    = val & ~2; // clear reserved bit 1
            case 0x140 -> sscratch = val;
            case 0x141 -> sepc     = val & ~1;
            case 0x142 -> scause   = val;
            case 0x143 -> stval    = val;
            case 0x144 -> mip = (mip & ~SIE_MASK) | (val & SIE_MASK & (1<<1)); // only SSIP writable
            case 0x180 -> { satp = val; Arrays.fill(tlbVPN, -1); }
            // M-mode CSRs
            case 0x300 -> mstatus  = val;
            case 0x302 -> medeleg  = val & 0xB35D; // standard delegatable exceptions
            case 0x303 -> mideleg  = val & SIE_MASK;
            case 0x304 -> mie      = val;
            case 0x305 -> mtvec    = val & ~2;
            case 0x340 -> mscratch = val;
            case 0x341 -> mepc     = val & ~1;
            case 0x342 -> mcause   = val;
            case 0x343 -> mtval    = val;
            case 0x344 -> mip      = val & ((1<<1)|(1<<3)); // only SSIP/MSIP software-writable
        }
    }

    // ─── FP operations ────────────────────────────────────────────────────────

    private void fma4(int type, int fmt, int rd, int rs1, int rs2, int rs3) {
        if (fmt == 1) { // double
            fregs[rd] = switch (type) {
                case 0 ->  Math.fma( fregs[rs1],  fregs[rs2],  fregs[rs3]);
                case 1 ->  Math.fma( fregs[rs1],  fregs[rs2], -fregs[rs3]);
                case 2 ->  Math.fma(-fregs[rs1],  fregs[rs2],  fregs[rs3]);
                default->  Math.fma(-fregs[rs1],  fregs[rs2], -fregs[rs3]);
            };
        } else { // single (NaN-box)
            fwrite(rd, switch (type) {
                case 0 ->  Math.fma( fread(rs1),  fread(rs2),  fread(rs3));
                case 1 ->  Math.fma( fread(rs1),  fread(rs2), -fread(rs3));
                case 2 ->  Math.fma(-fread(rs1),  fread(rs2),  fread(rs3));
                default->  Math.fma(-fread(rs1),  fread(rs2), -fread(rs3));
            });
        }
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private void fpOp(int f7, int f3, int rd, int rs1, int rs2) {
        int funct5 = f7 >> 2;
        int fmt    = f7 & 3; // 0=S, 1=D
        if (fmt == 0) { // single
            switch (funct5) {
                case 0x00 -> fwrite(rd, fread(rs1) + fread(rs2));
                case 0x01 -> fwrite(rd, fread(rs1) - fread(rs2));
                case 0x02 -> fwrite(rd, fread(rs1) * fread(rs2));
                case 0x03 -> fwrite(rd, fread(rs1) / fread(rs2));
                case 0x0B -> fwrite(rd, (float) Math.sqrt(fread(rs1)));
                case 0x04 -> { // FSGNJ
                    int b1 = Float.floatToRawIntBits(fread(rs1));
                    int b2 = Float.floatToRawIntBits(fread(rs2));
                    fwrite(rd, Float.intBitsToFloat(switch (f3) {
                        case 0 -> (b1 & 0x7FFFFFFF) | (b2 & 0x80000000);
                        case 1 -> (b1 & 0x7FFFFFFF) | (~b2 & 0x80000000);
                        default-> (b1 & 0x7FFFFFFF) | ((b1 ^ b2) & 0x80000000);
                    }));
                }
                case 0x05 -> { float a=fread(rs1),b=fread(rs2);
                    fwrite(rd, f3==0 ? (Float.isNaN(a)?b:Float.isNaN(b)?a:Math.min(a,b))
                                     : (Float.isNaN(a)?b:Float.isNaN(b)?a:Math.max(a,b))); }
                case 0x18 -> { float fv=fread(rs1);
                    wr(rd, rs2==0 ? (Float.isNaN(fv)||fv>=2.147483648e9f ? Integer.MAX_VALUE : fv<-2.147483648e9f ? Integer.MIN_VALUE : (int)fv)
                                  : (Float.isNaN(fv)||fv>=4.294967296e9f ? 0xFFFFFFFF : fv<=0 ? 0 : (int)(long)fv)); }
                case 0x1A -> { if (rs2==0) fwrite(rd,(float)regs[rs1]); else fwrite(rd,(float)Integer.toUnsignedLong(regs[rs1])); }
                case 0x1C -> { if (f3==0) wr(rd, Float.floatToRawIntBits(fread(rs1))); else wr(rd, fclassF(fread(rs1))); }
                case 0x1E -> fwrite(rd, Float.intBitsToFloat(regs[rs1]));
                case 0x14 -> { float a=fread(rs1),b=fread(rs2);
                    wr(rd, !Float.isNaN(a)&&!Float.isNaN(b) ? switch(f3){case 2->a==b?1:0;case 1->a<b?1:0;default->a<=b?1:0;} : 0); }
                case 0x08 -> fwrite(rd, (float) fregs[rs1]); // FCVT.S.D (rs2=1)
            }
        } else { // double
            switch (funct5) {
                case 0x00 -> fregs[rd] = fregs[rs1] + fregs[rs2];
                case 0x01 -> fregs[rd] = fregs[rs1] - fregs[rs2];
                case 0x02 -> fregs[rd] = fregs[rs1] * fregs[rs2];
                case 0x03 -> fregs[rd] = fregs[rs1] / fregs[rs2];
                case 0x0B -> fregs[rd] = Math.sqrt(fregs[rs1]);
                case 0x04 -> {
                    long b1 = Double.doubleToRawLongBits(fregs[rs1]);
                    long b2 = Double.doubleToRawLongBits(fregs[rs2]);
                    fregs[rd] = Double.longBitsToDouble(switch (f3) {
                        case 0 -> (b1 & 0x7FFFFFFFFFFFFFFFL) | (b2 & 0x8000000000000000L);
                        case 1 -> (b1 & 0x7FFFFFFFFFFFFFFFL) | (~b2 & 0x8000000000000000L);
                        default-> (b1 & 0x7FFFFFFFFFFFFFFFL) | ((b1 ^ b2) & 0x8000000000000000L);
                    });
                }
                case 0x05 -> fregs[rd] = f3==0 ? (Double.isNaN(fregs[rs1])?fregs[rs2]:Double.isNaN(fregs[rs2])?fregs[rs1]:Math.min(fregs[rs1],fregs[rs2]))
                                                 : (Double.isNaN(fregs[rs1])?fregs[rs2]:Double.isNaN(fregs[rs2])?fregs[rs1]:Math.max(fregs[rs1],fregs[rs2]));
                case 0x18 -> { double dv=fregs[rs1];
                    wr(rd, rs2==0 ? (Double.isNaN(dv)||dv>=2.147483648e9 ? Integer.MAX_VALUE : dv<-2.147483648e9 ? Integer.MIN_VALUE : (int)dv)
                                  : (Double.isNaN(dv)||dv>=4.294967296e9 ? 0xFFFFFFFF : dv<=0 ? 0 : (int)(long)dv)); }
                case 0x1A -> { if (rs2==0) fregs[rd]=(double)regs[rs1]; else fregs[rd]=(double)Integer.toUnsignedLong(regs[rs1]); }
                case 0x1C -> wr(rd, fclassD(fregs[rs1]));
                case 0x14 -> { double a=fregs[rs1],b=fregs[rs2];
                    wr(rd, !Double.isNaN(a)&&!Double.isNaN(b) ? switch(f3){case 2->a==b?1:0;case 1->a<b?1:0;default->a<=b?1:0;} : 0); }
                case 0x08 -> fregs[rd] = (double) fread(rs1); // FCVT.D.S (rs2=0)
            }
        }
    }

    // ─── ECALL ────────────────────────────────────────────────────────────────

    private void ecall(GPIOAccess gpio, DisplayAccess display, RAMAccess ram, FloppyAccess floppy) {
        int sys = regs[17];
        int a0=regs[10],a1=regs[11],a2=regs[12],a3=regs[13],a4=regs[14],a5=regs[15];
        switch (sys) {
            case 0  -> halted = true;
            case 1  -> { if (a0 > 0) sleepTicks = a0; }
            case 2  -> regs[10] = a0 + (int)(Math.random() * ((long)(a1 - a0) + 1));
            case 10 -> { if (gpio != null) gpio.write(a0, Math.clamp(a1, 0, 15)); }
            case 11 -> regs[10] = gpio != null ? gpio.read(a0) : 0;
            case 20 -> { if (display != null) display.setPixel(a0, a1, a2, a3 & 0xFFFFFF); }
            case 21 -> { if (display != null) display.reset(a0); }
            case 22 -> { if (display != null) { regs[10]=display.getWidth(a0); regs[11]=display.getHeight(a0); } }
            case 23 -> { if (display != null) display.drawLine(a0, a1, a2, a3, a4, a5 & 0xFFFFFF); }
            case 24 -> { if (display != null) display.fillRect(a0, a1, a2, a3, a4, a5 & 0xFFFFFF); }
            case 25 -> {
                if (display != null && a3 * a4 > 0) {
                    int total = a3 * a4;
                    int[] px = new int[total];
                    for (int i = 0; i < total; i++) {
                        int base = a5 + i * 3;
                        px[i] = ((physRdByte(base,   gpio, ram)&0xFF)<<16)
                              | ((physRdByte(base+1, gpio, ram)&0xFF)<<8)
                              |  (physRdByte(base+2, gpio, ram)&0xFF);
                    }
                    display.blit(a0, a1, a2, a3, a4, px);
                }
            }
            case 30 -> regs[10] = (floppy != null && floppy.loadSector(a0, a1, a2)) ? 1 : 0;
        }
    }

    // ─── SBI (Supervisor Binary Interface) — intercepts S-mode ECALLs ────────

    private void sbiCall(GPIOAccess gpio, RAMAccess ram) {
        int a0 = regs[10], a1 = regs[11], a2 = regs[12];
        int a6 = regs[16]; // SBI function ID (v0.2)
        int a7 = regs[17]; // SBI extension ID

        regs[10] = 0; // default: SBI_SUCCESS
        regs[11] = 0;

        switch (a7) {
            case 0 -> { // legacy: set_timer(stime_lo, stime_hi)
                mtimecmp = ((long) a1 << 32) | Integer.toUnsignedLong(a0);
                mip &= ~(1 << 5); // clear STIP
            }
            case 1 -> { // legacy: console_putchar(ch)
                if (console != null) console.putchar(a0 & 0xFF);
            }
            case 2 -> regs[10] = -1; // legacy: console_getchar → -1 (no input)
            case 3 -> { /* legacy: clear_ipi — NOP */ }
            case 8 -> halted = true; // legacy: shutdown
            case 0x10 -> { // SBI Base extension
                switch (a6) {
                    case 0 -> { regs[10] = 0; regs[11] = 0x20000; } // spec_version 2.0
                    case 1 -> { regs[10] = 0; regs[11] = 0; }       // impl_id
                    case 2 -> { regs[10] = 0; regs[11] = 0; }       // impl_version
                    case 3 -> { // probe_extension
                        boolean sup = switch (a0) {
                            case 0x10, 0x54494D45, 0x53525354, 0x4442434E -> true;
                            default -> false;
                        };
                        regs[10] = 0; regs[11] = sup ? 1 : 0;
                    }
                    default -> { regs[10] = 0; regs[11] = 0; }
                }
            }
            case 0x54494D45 -> { // TIME: set_timer
                if (a6 == 0) {
                    mtimecmp = ((long) a1 << 32) | Integer.toUnsignedLong(a0);
                    mip &= ~(1 << 5); // clear STIP
                }
            }
            case 0x53525354 -> { // SRST: system_reset
                if (a6 == 0) halted = true;
            }
            case 0x4442434E -> { // DBCN: debug console
                switch (a6) {
                    case 0 -> { // console_write(num_bytes, base_lo, base_hi)
                        int n = a0, base = a1;
                        if (console != null) {
                            for (int i = 0; i < n; i++)
                                console.putchar(physRdByte(base + i, gpio, ram));
                        }
                        regs[10] = 0; regs[11] = a0; // wrote all bytes
                    }
                    case 1 -> { regs[10] = 0; regs[11] = 0; } // console_read → 0 bytes
                    case 2 -> { // console_write_byte
                        if (console != null) console.putchar(a0 & 0xFF);
                    }
                }
            }
            default -> { regs[10] = -2; regs[11] = 0; } // SBI_ERR_NOT_SUPPORTED
        }
    }

    // ── C extension decoder ───────────────────────────────────────────────────

    private void execC(int w, int instrPC,
            GPIOAccess gpio, DisplayAccess display, RAMAccess ram, FloppyAccess floppy) {
        int quad  = w & 3;
        int funct3 = (w >> 13) & 7;

        if (quad == 0) { // Quadrant 0
            int rdp  = ((w >> 2) & 7) + 8;
            int rs1p = ((w >> 7) & 7) + 8;
            switch (funct3) {
                case 0 -> { // C.ADDI4SPN
                    int nzuimm = ((w >> 1) & 0x3C0) | ((w >> 7) & 0x30) | ((w >> 2) & 0x8) | ((w >> 4) & 0x4);
                    if (nzuimm != 0) wr(rdp, regs[2] + nzuimm); // sp
                }
                case 1 -> { // C.FLD
                    int off = ((w << 1) & 0xC0) | ((w >> 7) & 0x38);
                    fregs[rdp] = Double.longBitsToDouble(memRdLong(regs[rs1p] + off, gpio, ram));
                }
                case 2 -> { // C.LW
                    int off = ((w << 1) & 0x40) | ((w >> 7) & 0x38) | ((w >> 4) & 0x4);
                    wr(rdp, memRdWord(regs[rs1p] + off, gpio, ram));
                }
                case 5 -> { // C.FSD
                    int rs2p = rdp;
                    int off  = ((w << 1) & 0xC0) | ((w >> 7) & 0x38);
                    memWrLong(regs[rs1p] + off, Double.doubleToRawLongBits(fregs[rs2p]), gpio, ram);
                }
                case 6 -> { // C.SW
                    int rs2p = rdp;
                    int off  = ((w << 1) & 0x40) | ((w >> 7) & 0x38) | ((w >> 4) & 0x4);
                    memWrWord(regs[rs1p] + off, regs[rs2p], gpio, ram);
                }
            }
        } else if (quad == 1) { // Quadrant 1
            int rd  = (w >> 7) & 0x1F;
            int rdp = ((w >> 7) & 7) + 8;
            switch (funct3) {
                case 0 -> { // C.ADDI / C.NOP
                    int imm = cImm6(w);
                    if (rd != 0) wr(rd, regs[rd] + imm);
                }
                case 1 -> { // C.JAL (RV32)
                    wr(1, pc);
                    pc = instrPC + cJImm(w);
                }
                case 2 -> { // C.LI
                    if (rd != 0) wr(rd, cImm6(w));
                }
                case 3 -> {
                    if (rd == 2) { // C.ADDI16SP
                        int imm = cAddi16spImm(w);
                        wr(2, regs[2] + imm);
                    } else if (rd != 0) { // C.LUI
                        int imm6 = cImm6(w);
                        wr(rd, imm6 << 12);
                    }
                }
                case 4 -> { // C.SRLI / C.SRAI / C.ANDI / arithmetic
                    int f2 = (w >> 10) & 3;
                    int rdpA = ((w >> 7) & 7) + 8;
                    int rs2p = ((w >> 2) & 7) + 8;
                    int shamt = ((w >> 7) & 0x20) | ((w >> 2) & 0x1F);
                    switch (f2) {
                        case 0 -> wr(rdpA, regs[rdpA] >>> (shamt & 31));  // C.SRLI
                        case 1 -> wr(rdpA, regs[rdpA] >>  (shamt & 31));  // C.SRAI
                        case 2 -> wr(rdpA, regs[rdpA] &   cImm6(w));     // C.ANDI
                        case 3 -> {
                            int bit12 = (w >> 12) & 1;
                            int op2   = (w >> 5) & 3;
                            if (bit12 == 0) switch (op2) {
                                case 0 -> wr(rdpA, regs[rdpA] - regs[rs2p]);  // C.SUB
                                case 1 -> wr(rdpA, regs[rdpA] ^ regs[rs2p]);  // C.XOR
                                case 2 -> wr(rdpA, regs[rdpA] | regs[rs2p]);  // C.OR
                                case 3 -> wr(rdpA, regs[rdpA] & regs[rs2p]);  // C.AND
                            }
                            // bit12=1: RV64 ops (SUBW/ADDW) — ignore on RV32
                        }
                    }
                }
                case 5 -> { // C.J
                    pc = instrPC + cJImm(w);
                }
                case 6 -> { // C.BEQZ
                    int rs1p = ((w >> 7) & 7) + 8;
                    if (regs[rs1p] == 0) pc = instrPC + cBImm(w);
                }
                case 7 -> { // C.BNEZ
                    int rs1p = ((w >> 7) & 7) + 8;
                    if (regs[rs1p] != 0) pc = instrPC + cBImm(w);
                }
            }
        } else { // Quadrant 2
            int rd  = (w >> 7) & 0x1F;
            int rs2 = (w >> 2) & 0x1F;
            int bit12 = (w >> 12) & 1;
            switch (funct3) {
                case 0 -> { // C.SLLI
                    int shamt = ((bit12) << 5) | rs2;
                    if (rd != 0) wr(rd, regs[rd] << (shamt & 31));
                }
                case 1 -> { // C.FLDSP
                    int off = ((w << 4) & 0x1C0) | ((w >> 7) & 0x20) | ((w >> 2) & 0x18);
                    fregs[rd] = Double.longBitsToDouble(memRdLong(regs[2] + off, gpio, ram));
                }
                case 2 -> { // C.LWSP
                    int off = ((w << 4) & 0x0C0) | ((w >> 7) & 0x20) | ((w >> 2) & 0x1C);
                    if (rd != 0) wr(rd, memRdWord(regs[2] + off, gpio, ram));
                }
                case 4 -> {
                    if (bit12 == 0) {
                        if (rs2 == 0) { // C.JR
                            if (rd != 0) pc = regs[rd] & ~1;
                        } else { // C.MV
                            if (rd != 0) wr(rd, regs[rs2]);
                        }
                    } else {
                        if (rd == 0 && rs2 == 0) { halted = true; } // C.EBREAK
                        else if (rs2 == 0) { // C.JALR
                            int ret = pc;
                            pc = regs[rd] & ~1;
                            wr(1, ret);
                        } else { // C.ADD
                            if (rd != 0) wr(rd, regs[rd] + regs[rs2]);
                        }
                    }
                }
                case 5 -> { // C.FSDSP
                    int off = ((w >> 1) & 0x1C0) | ((w >> 7) & 0x38);
                    memWrLong(regs[2] + off, Double.doubleToRawLongBits(fregs[rs2]), gpio, ram);
                }
                case 6 -> { // C.SWSP
                    int off = ((w >> 1) & 0x0C0) | ((w >> 7) & 0x3C);
                    memWrWord(regs[2] + off, regs[rs2], gpio, ram);
                }
            }
        }
    }

    // ─── C extension immediate helpers ───────────────────────────────────────

    private static int cImm6(int w) {
        int raw = ((w >> 7) & 0x20) | ((w >> 2) & 0x1F);
        return (raw << 26) >> 26; // sign-extend 6 bits
    }

    private static int cJImm(int w) {
        int raw = ((w >> 1) & 0x800) | ((w << 2) & 0x400) | ((w >> 1) & 0x300)
                | ((w << 1) & 0x080) | ((w >> 1) & 0x040) | ((w << 3) & 0x020)
                | ((w >> 7) & 0x010) | ((w >> 2) & 0x00E);
        return (raw << 20) >> 20; // sign-extend 12 bits
    }

    private static int cBImm(int w) {
        int raw = ((w >> 4) & 0x100) | ((w << 1) & 0x0C0) | ((w << 3) & 0x020)
                | ((w >> 7) & 0x018) | ((w >> 2) & 0x006);
        return (raw << 23) >> 23; // sign-extend 9 bits
    }

    private static int cAddi16spImm(int w) {
        int raw = ((w >> 3) & 0x200) | ((w << 4) & 0x180) | ((w << 1) & 0x040)
                | ((w << 3) & 0x020) | ((w >> 2) & 0x010);
        return (raw << 22) >> 22; // sign-extend 10 bits
    }

    // ─── NaN-boxing ───────────────────────────────────────────────────────────

    private float fread(int r) {
        long bits = Double.doubleToRawLongBits(fregs[r]);
        return (bits >>> 32) == 0xFFFFFFFFL
            ? Float.intBitsToFloat((int) bits)
            : Float.intBitsToFloat(0x7FC00000);
    }
    private void fwrite(int r, float f) {
        fregs[r] = Double.longBitsToDouble(
            0xFFFFFFFF00000000L | Integer.toUnsignedLong(Float.floatToRawIntBits(f)));
    }

    // ─── FCLASS ───────────────────────────────────────────────────────────────

    private static int fclassF(float f) {
        int bits = Float.floatToRawIntBits(f);
        boolean neg = (bits & 0x80000000) != 0;
        int exp = (bits >>> 23) & 0xFF, man = bits & 0x7FFFFF;
        if (exp == 0xFF) return man == 0 ? (neg ? 1 : 1<<7) : ((man & 0x400000) != 0 ? 1<<9 : 1<<8);
        if (exp == 0)   return man == 0 ? (neg ? 1<<3 : 1<<4) : (neg ? 1<<2 : 1<<5);
        return neg ? 1<<1 : 1<<6;
    }
    private static int fclassD(double d) {
        long bits = Double.doubleToRawLongBits(d);
        boolean neg = (bits & 0x8000000000000000L) != 0;
        int exp = (int)((bits >>> 52) & 0x7FFL);
        long man = bits & 0x000FFFFFFFFFFFFFL;
        if (exp == 0x7FF) return man == 0 ? (neg ? 1 : 1<<7) : ((man & (1L<<51)) != 0 ? 1<<9 : 1<<8);
        if (exp == 0)     return man == 0 ? (neg ? 1<<3 : 1<<4) : (neg ? 1<<2 : 1<<5);
        return neg ? 1<<1 : 1<<6;
    }

    // ─── register write ───────────────────────────────────────────────────────

    private void wr(int rd, int val) { if (rd != 0) regs[rd] = val; }
    private static int rs2Of(int word) { return (word >> 20) & 0x1F; }

    // ─── Physical memory (bypasses MMU, used by page table walker + M-mode direct) ──

    // GPIO: 0xF0000000 – 0xF0010000
    private static final int GPIO_BASE    = 0xF0000000;
    private static final int GPIO_END     = 0xF0010000;
    // CLINT: 0x02000000 – 0x0200C000
    private static final int CLINT_BASE   = 0x02000000;
    private static final int CLINT_END    = 0x0200C000;
    private static final int CLINT_MSIP   = 0x02000000;
    private static final int CLINT_TIMECMP= 0x02004000;
    private static final int CLINT_MTIME  = 0x0200BFF8;
    // Framebuffer: 0x10000000, 320×240×4 bytes
    private static final int FB_BASE      = 0x10000000;
    private static final int FB_SIZE      = 320 * 240 * 4; // 307200
    // UART ns16550a: 0x10001000 – 0x10002000
    private static final int UART_BASE    = 0x10001000;
    private static final int UART_END     = 0x10002000;

    private boolean isGpio(int addr) {
        return Integer.compareUnsigned(addr, GPIO_BASE) >= 0
            && Integer.compareUnsigned(addr, GPIO_END)  <  0;
    }
    private boolean isClint(int addr) {
        return Integer.compareUnsigned(addr, CLINT_BASE) >= 0
            && Integer.compareUnsigned(addr, CLINT_END)  <  0;
    }
    private boolean isFb(int addr) {
        return framebuffer != null
            && Integer.compareUnsigned(addr, FB_BASE) >= 0
            && Integer.compareUnsigned(addr, FB_BASE + FB_SIZE) < 0;
    }
    private boolean isUart(int addr) {
        return Integer.compareUnsigned(addr, UART_BASE) >= 0
            && Integer.compareUnsigned(addr, UART_END)  <  0;
    }

    // ── CLINT byte access ─────────────────────────────────────────────────────

    private int clintRdByte(int addr) {
        if (Integer.compareUnsigned(addr - CLINT_MSIP, 4) < 0) {
            // msip: bit 3 of mip (MSIP)
            return (addr == CLINT_MSIP) ? ((mip >>> 3) & 1) : 0;
        }
        if (Integer.compareUnsigned(addr - CLINT_TIMECMP, 8) < 0) {
            int shift = (addr - CLINT_TIMECMP) * 8;
            return (int) ((mtimecmp >>> shift) & 0xFF);
        }
        if (Integer.compareUnsigned(addr - CLINT_MTIME, 8) < 0) {
            int shift = (addr - CLINT_MTIME) * 8;
            return (int) ((mtime >>> shift) & 0xFF);
        }
        return 0;
    }

    private void clintWrByte(int addr, int val) {
        if (Integer.compareUnsigned(addr - CLINT_MSIP, 4) < 0) {
            if (addr == CLINT_MSIP) {
                if ((val & 1) != 0) mip |= (1 << 3); else mip &= ~(1 << 3);
            }
            return;
        }
        if (Integer.compareUnsigned(addr - CLINT_TIMECMP, 8) < 0) {
            int shift = (addr - CLINT_TIMECMP) * 8;
            long mask = 0xFFL << shift;
            mtimecmp = (mtimecmp & ~mask) | ((long)(val & 0xFF) << shift);
            mip &= ~(1 << 5); // clear STIP on any mtimecmp write
        }
        // mtime is read-only (hardware increments it)
    }

    // ── UART ns16550a byte access (minimal: TX only) ──────────────────────────

    private int uartRdByte(int addr) {
        return switch (addr - UART_BASE) {
            case 0 -> 0;    // RBR / DLL: no received data
            case 1 -> 0;    // IER / DLM
            case 2 -> 1;    // IIR: no interrupt pending
            case 3 -> uartLcr;
            case 4 -> 0;    // MCR
            case 5 -> 0x60; // LSR: THRE + TEMT (TX ready)
            case 6 -> 0;    // MSR
            case 7 -> uartScratch;
            default -> 0;
        };
    }

    private void uartWrByte(int addr, int val) {
        switch (addr - UART_BASE) {
            case 0 -> { // THR (or DLL when DLAB set)
                if ((uartLcr & 0x80) == 0 && console != null) console.putchar(val & 0xFF);
            }
            case 3 -> uartLcr     = val & 0xFF;
            case 7 -> uartScratch = val & 0xFF;
        }
    }

    // ── physRd / physWr ───────────────────────────────────────────────────────

    private int physRdByte(int addr, GPIOAccess g, RAMAccess ram) {
        if (isGpio(addr))  return g != null ? g.read((addr - GPIO_BASE) / 4) & 0xFF : 0;
        if (isClint(addr)) return clintRdByte(addr);
        if (isFb(addr))    return framebuffer.fbRdByte(addr - FB_BASE) & 0xFF;
        if (isUart(addr))  return uartRdByte(addr);
        return ram != null ? ram.read(addr) & 0xFF : 0;
    }
    private int physRdHalf(int addr, GPIOAccess g, RAMAccess ram) {
        return physRdByte(addr, g, ram) | (physRdByte(addr + 1, g, ram) << 8);
    }
    private int physRdWord(int addr, GPIOAccess g, RAMAccess ram) {
        return physRdByte(addr, g, ram)         | (physRdByte(addr+1, g, ram) << 8)
             | (physRdByte(addr+2, g, ram) << 16) | (physRdByte(addr+3, g, ram) << 24);
    }
    private long physRdLong(int addr, GPIOAccess g, RAMAccess ram) {
        return Integer.toUnsignedLong(physRdWord(addr, g, ram))
             | (Integer.toUnsignedLong(physRdWord(addr + 4, g, ram)) << 32);
    }
    private void physWrByte(int addr, int val, GPIOAccess g, RAMAccess ram) {
        if (isGpio(addr)) {
            if (g != null) g.write((addr - GPIO_BASE) / 4, Math.clamp(val & 0xFF, 0, 15));
        } else if (isClint(addr)) {
            clintWrByte(addr, val);
        } else if (isFb(addr)) {
            framebuffer.fbWrByte(addr - FB_BASE, val & 0xFF);
        } else if (isUart(addr)) {
            uartWrByte(addr, val);
        } else {
            if (ram != null) ram.write(addr, val & 0xFF);
        }
    }
    private void physWrHalf(int addr, int val, GPIOAccess g, RAMAccess ram) {
        physWrByte(addr, val, g, ram); physWrByte(addr+1, val>>>8, g, ram);
    }
    private void physWrWord(int addr, int val, GPIOAccess g, RAMAccess ram) {
        physWrByte(addr, val, g, ram); physWrByte(addr+1, val>>>8, g, ram);
        physWrByte(addr+2, val>>>16, g, ram); physWrByte(addr+3, val>>>24, g, ram);
    }
    private void physWrLong(int addr, long val, GPIOAccess g, RAMAccess ram) {
        physWrWord(addr, (int) val, g, ram); physWrWord(addr+4, (int)(val>>>32), g, ram);
    }

    // ─── Sv32 address translation ─────────────────────────────────────────────
    // accessType: 0=load, 1=store, 2=fetch

    private int translateVA(int va, int accessType, GPIOAccess g, RAMAccess ram) {
        // Determine effective privilege for data accesses (MPRV)
        int effPriv = priv;
        if (accessType != 2 && (mstatus & (1 << 17)) != 0 && priv == 3) {
            effPriv = (mstatus >> 11) & 3;
        }
        // Bare mode or M-mode: physical = virtual
        if ((satp >>> 31) == 0 || effPriv == 3) return va;

        boolean mxr = (mstatus & (1 << 19)) != 0; // make execute-only readable
        boolean sum = (mstatus & (1 << 18)) != 0; // S-mode access to U pages

        int vpn = va >>> 12;
        int idx = vpn & TLB_MASK;
        if (tlbVPN[idx] == vpn) {
            sv32CheckPerm(tlbPerm[idx] & 0xFF, accessType, effPriv, sum, mxr, va);
            return tlbPhys[idx] | (va & 0xFFF);
        }

        // Level-1 page table walk (VPN[1] = vpn >> 10)
        int rootPPN = satp & 0x3FFFFF;
        int pteAddr = (rootPPN << 12) + ((vpn >>> 10) << 2);
        int pte     = physRdWord(pteAddr, g, ram);
        if ((pte & 1) == 0 || (pte & 6) == 4) sv32Fault(accessType, va); // !V or W&!R

        int physBase;
        int pteFlags;
        if ((pte & 0xE) != 0) {
            // Leaf at level 1: 4 MB superpage
            int ppn0 = (pte >>> 10) & 0x3FF;
            if (ppn0 != 0) sv32Fault(accessType, va); // misaligned superpage
            physBase = ((pte >>> 20) << 22) | ((vpn & 0x3FF) << 12);
            pteFlags = (pte >>> 1) & 0xF;
        } else {
            // Non-leaf: descend to level 0 (VPN[0] = vpn & 0x3FF)
            int ppn1  = pte >>> 10;
            pteAddr   = (ppn1 << 12) + ((vpn & 0x3FF) << 2);
            pte       = physRdWord(pteAddr, g, ram);
            if ((pte & 1) == 0 || (pte & 6) == 4) sv32Fault(accessType, va);
            if ((pte & 0xE) == 0) sv32Fault(accessType, va); // non-leaf at level 0
            physBase = (pte >>> 10) << 12;
            pteFlags = (pte >>> 1) & 0xF;
        }
        // Require A bit set (raise fault rather than update — simpler, BBB-safe for Linux)
        if ((pte & (1 << 6)) == 0) sv32Fault(accessType, va);
        if (accessType == 1 && (pte & (1 << 7)) == 0) sv32Fault(accessType, va); // D bit for stores

        sv32CheckPerm(pteFlags, accessType, effPriv, sum, mxr, va);

        // Fill TLB
        tlbVPN[idx]  = vpn;
        tlbPhys[idx] = physBase;
        tlbPerm[idx] = (byte) pteFlags;

        return physBase | (va & 0xFFF);
    }

    private static void sv32Fault(int accessType, int va) {
        throw new PFE(switch (accessType) { case 2 -> 12; case 1 -> 15; default -> 13; }, va);
    }

    private static void sv32CheckPerm(int perm, int accessType, int effPriv,
                                      boolean sum, boolean mxr, int va) {
        boolean uPage = (perm & 8) != 0;
        if (effPriv == 0 && !uPage) sv32Fault(accessType, va);            // U-mode: must be U page
        if (effPriv == 1 && uPage && (!sum || accessType == 2)) sv32Fault(accessType, va);
        boolean canRead  = (perm & 1) != 0 || (mxr && (perm & 4) != 0);
        boolean canWrite = (perm & 2) != 0;
        boolean canExec  = (perm & 4) != 0;
        switch (accessType) {
            case 0 -> { if (!canRead)  sv32Fault(accessType, va); }
            case 1 -> { if (!canWrite) sv32Fault(accessType, va); }
            case 2 -> { if (!canExec)  sv32Fault(accessType, va); }
        }
    }

    // ─── Virtual memory access (translates via MMU when Sv32 active) ──────────

    private int memRdByte(int addr, GPIOAccess g, RAMAccess ram) {
        return physRdByte(translateVA(addr, 0, g, ram), g, ram);
    }
    private int memRdHalf(int addr, GPIOAccess g, RAMAccess ram) {
        int phys = translateVA(addr, 0, g, ram);
        return physRdByte(phys, g, ram) | (physRdByte(phys+1, g, ram) << 8);
    }
    private int memRdWord(int addr, GPIOAccess g, RAMAccess ram) {
        int phys = translateVA(addr, 0, g, ram);
        return physRdByte(phys, g, ram)       | (physRdByte(phys+1, g, ram) << 8)
             | (physRdByte(phys+2, g, ram) << 16) | (physRdByte(phys+3, g, ram) << 24);
    }
    private long memRdLong(int addr, GPIOAccess g, RAMAccess ram) {
        return Integer.toUnsignedLong(memRdWord(addr, g, ram))
             | (Integer.toUnsignedLong(memRdWord(addr+4, g, ram)) << 32);
    }
    private void memWrByte(int addr, int val, GPIOAccess g, RAMAccess ram) {
        physWrByte(translateVA(addr, 1, g, ram), val, g, ram);
    }
    private void memWrHalf(int addr, int val, GPIOAccess g, RAMAccess ram) {
        int phys = translateVA(addr, 1, g, ram);
        physWrByte(phys, val, g, ram); physWrByte(phys+1, val>>>8, g, ram);
    }
    private void memWrWord(int addr, int val, GPIOAccess g, RAMAccess ram) {
        int phys = translateVA(addr, 1, g, ram);
        physWrByte(phys, val, g, ram); physWrByte(phys+1, val>>>8, g, ram);
        physWrByte(phys+2, val>>>16, g, ram); physWrByte(phys+3, val>>>24, g, ram);
    }
    private void memWrLong(int addr, long val, GPIOAccess g, RAMAccess ram) {
        memWrWord(addr, (int)val, g, ram); memWrWord(addr+4, (int)(val>>>32), g, ram);
    }

    // ─── NBT persistence ──────────────────────────────────────────────────────

    public void saveState(ValueOutput out) {
        out.putInt("PC", pc);
        out.putInt("ExecPC", executingPC);
        out.putInt("Sleep", sleepTicks);
        out.putBoolean("Halted", halted);
        out.putBoolean("Loaded", loaded);
        for (int i = 1; i < 32; i++) out.putInt("x" + i, regs[i]);
        for (int i = 0; i < 32; i++) {
            long bits = Double.doubleToRawLongBits(fregs[i]);
            out.putInt("f" + i + "lo", (int) bits);
            out.putInt("f" + i + "hi", (int)(bits >>> 32));
        }
        out.putInt("FCSR", fcsr);
        out.putInt("Priv",     priv);
        out.putInt("MStatus",  mstatus);  out.putInt("MTVec",    mtvec);
        out.putInt("MEPC",     mepc);     out.putInt("MCause",   mcause);
        out.putInt("MTVal",    mtval);    out.putInt("MIE",      mie);
        out.putInt("MIP",      mip);      out.putInt("MScratch", mscratch);
        out.putInt("MEDeleg",  medeleg);  out.putInt("MIDeleg",  mideleg);
        out.putInt("STVec",    stvec);    out.putInt("SEPC",     sepc);
        out.putInt("SCause",   scause);   out.putInt("STVal",    stval);
        out.putInt("SScratch", sscratch); out.putInt("SATP",     satp);
        out.putInt("MTimeLo",  (int) mtime);         out.putInt("MTimeHi",    (int)(mtime    >>> 32));
        out.putInt("MTimeCmpLo",(int) mtimecmp);     out.putInt("MTimeCmpHi", (int)(mtimecmp >>> 32));
    }

    public void loadState(ValueInput in) {
        pc          = in.getIntOr("PC", 0);
        executingPC = in.getIntOr("ExecPC", 0);
        sleepTicks  = in.getIntOr("Sleep", 0);
        halted      = in.getBooleanOr("Halted", false);
        loaded      = in.getBooleanOr("Loaded", false);
        regs[0] = 0;
        for (int i = 1; i < 32; i++) regs[i] = in.getIntOr("x" + i, 0);
        long nanBoxedZero = 0xFFFFFFFF00000000L;
        for (int i = 0; i < 32; i++) {
            long lo = Integer.toUnsignedLong(in.getIntOr("f" + i + "lo", 0));
            long hi = Integer.toUnsignedLong(in.getIntOr("f" + i + "hi", (int)(nanBoxedZero >>> 32)));
            fregs[i] = Double.longBitsToDouble((hi << 32) | lo);
        }
        fcsr = in.getIntOr("FCSR", 0);
        lrReservation = -1;
        priv     = in.getIntOr("Priv",     3);
        mstatus  = in.getIntOr("MStatus",  0); mtvec    = in.getIntOr("MTVec",    0);
        mepc     = in.getIntOr("MEPC",     0); mcause   = in.getIntOr("MCause",   0);
        mtval    = in.getIntOr("MTVal",    0); mie      = in.getIntOr("MIE",      0);
        mip      = in.getIntOr("MIP",      0); mscratch = in.getIntOr("MScratch", 0);
        medeleg  = in.getIntOr("MEDeleg",  0); mideleg  = in.getIntOr("MIDeleg",  0);
        stvec    = in.getIntOr("STVec",    0); sepc     = in.getIntOr("SEPC",     0);
        scause   = in.getIntOr("SCause",   0); stval    = in.getIntOr("STVal",    0);
        sscratch = in.getIntOr("SScratch", 0); satp     = in.getIntOr("SATP",     0);
        mtime    = Integer.toUnsignedLong(in.getIntOr("MTimeLo", 0))
                 | (Integer.toUnsignedLong(in.getIntOr("MTimeHi", 0)) << 32);
        mtimecmp = Integer.toUnsignedLong(in.getIntOr("MTimeCmpLo", -1))
                 | (Integer.toUnsignedLong(in.getIntOr("MTimeCmpHi", -1)) << 32);
        uartLcr = 0; uartScratch = 0;
    }
}
