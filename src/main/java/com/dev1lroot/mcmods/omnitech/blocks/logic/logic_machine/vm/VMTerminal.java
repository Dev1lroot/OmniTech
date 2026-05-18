/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VT100/ANSI terminal emulator — 80×24.
 *
 * Cell packing (32 bits per cell):
 *   bits 31-28  attrs  (bold=31, underline=30, reverse=29, unused=28)
 *   bits 27-24  fg color index 0-15  (0-7 normal, 8-15 bright)
 *   bits 23-20  bg color index 0-15
 *   bits 19-16  (unused)
 *   bits 15-0   character codepoint
 *
 * Thread-safe: write() is called from the VM thread; render data is read
 * from the server tick thread via getCellSnapshot().
 */
public final class VMTerminal {

    public static final int COLS = 80;
    public static final int ROWS = 24;

    private final int[] cells = new int[COLS * ROWS];
    private int cursorRow = 0;
    private int cursorCol = 0;

    // ── Escape parser state ───────────────────────────────────────────────────

    private enum ParseState { NORMAL, ESC, CSI, ESC_SKIP1 }
    private ParseState parseState = ParseState.NORMAL;
    private boolean    csiPrivate = false;   // ESC[? prefix seen
    private final int[] csiParams    = new int[16];
    private int         csiParamCount = 0;

    // ── SGR attributes ────────────────────────────────────────────────────────

    private int     fgColor   = 7;     // 0-15
    private int     bgColor   = 0;     // 0-15
    private boolean bold      = false;
    private boolean underline = false;
    private boolean reverse   = false;

    // ── Scroll region (0-based, inclusive) ───────────────────────────────────

    private int scrollTop    = 0;
    private int scrollBottom = ROWS - 1;

    // ── Saved cursor state (ESC 7 / ESC 8 / CSI s / CSI u) ───────────────────

    private int     savedRow       = 0, savedCol     = 0;
    private int     savedFg        = 7, savedBg      = 0;
    private boolean savedBold      = false, savedUnderline = false, savedReverse = false;

    // ── Input queue (player → VM) ─────────────────────────────────────────────

    private final java.util.concurrent.ConcurrentLinkedQueue<Byte> inputQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    private final AtomicBoolean dirty = new AtomicBoolean(false);

    // ── Output path (VM writes, server tick reads) ────────────────────────────

    public synchronized void write(byte b) {
        int ch = b & 0xFF;
        switch (parseState) {
            case NORMAL    -> handleNormal(ch);
            case ESC       -> handleEsc(ch);
            case CSI       -> handleCsi(ch);
            case ESC_SKIP1 -> parseState = ParseState.NORMAL; // consume one byte then back
        }
        dirty.set(true);
    }

    public synchronized void write(ByteBuffer data) {
        while (data.hasRemaining()) write(data.get());
    }

    // ── Normal character handling ─────────────────────────────────────────────

    private void handleNormal(int ch) {
        switch (ch) {
            case 0x07 -> {}                                                       // BEL
            case 0x08 -> { if (cursorCol > 0) cursorCol--; }                     // BS
            case 0x09 -> cursorCol = Math.min((cursorCol / 8 + 1) * 8, COLS - 1);// HT
            case 0x0A, 0x0B, 0x0C -> lineFeed();                                  // LF/VT/FF
            case 0x0D -> cursorCol = 0;                                            // CR
            case 0x1B -> parseState = ParseState.ESC;
            default   -> { if (ch >= 0x20) putChar(ch); }
        }
    }

    // ── ESC sequence handling ─────────────────────────────────────────────────

    private void handleEsc(int ch) {
        parseState = ParseState.NORMAL;
        switch (ch) {
            case '[' -> {
                parseState    = ParseState.CSI;
                csiParamCount = 0;
                csiParams[0]  = 0;
                csiPrivate    = false;
            }
            case 'M' -> reverseIndex();             // RI — reverse linefeed
            case '7' -> saveCursor();               // DECSC
            case '8' -> restoreCursor();            // DECRC
            case 'c' -> resetTerminal();            // RIS — full reset
            case 'E' -> { cursorCol = 0; lineFeed(); } // NEL — next line
            case 'D' -> lineFeed();                 // IND — index (same as LF)
            // Charset designators ESC ( G0 and ESC ) G1 — consume one more byte
            case '(', ')' -> parseState = ParseState.ESC_SKIP1;
            // Application/normal keypad — ignore
            case '=', '>' -> {}
        }
    }

    // ── CSI sequence handling ─────────────────────────────────────────────────

    private void handleCsi(int ch) {
        // DEC private mode prefix (must be first character after '[')
        if (ch == '?' && csiParamCount == 0 && csiParams[0] == 0 && !csiPrivate) {
            csiPrivate = true;
            return;
        }

        if (ch >= '0' && ch <= '9') {
            if (csiParamCount < csiParams.length)
                csiParams[csiParamCount] = csiParams[csiParamCount] * 10 + (ch - '0');
            return;
        }
        if (ch == ';') {
            csiParamCount = Math.min(csiParamCount + 1, csiParams.length - 1);
            csiParams[csiParamCount] = 0;
            return;
        }

        // Intermediate bytes (space, !, ", #, $, %, &, ', *, +, ,, -, ., /) — skip
        if (ch >= 0x20 && ch <= 0x2F) return;

        // Final byte — capture params, reset parser state, then dispatch.
        // csiParams[] must stay intact until after dispatch (applySGR reads the full array).
        boolean priv        = csiPrivate;
        int     totalParams = csiParamCount + 1;
        int     p0          = csiParams[0];
        int     p1          = totalParams > 1 ? csiParams[1] : 0;

        // Reset parser state for the next sequence (before dispatch so re-entrant
        // writes from DSR response injection don't corrupt our read of csiParams).
        parseState    = ParseState.NORMAL;
        csiPrivate    = false;
        csiParamCount = 0;
        // csiParams NOT zeroed yet — applySGR still needs [0..totalParams-1].

        if (priv) {
            handleDecPrivate(ch, p0);
            csiParams[0] = 0;
            return;
        }

        switch (ch) {
            // ── Cursor motion ──────────────────────────────────────────────────
            case 'A' -> cursorRow = Math.max(scrollTop,    cursorRow - Math.max(1, p0));  // CUU
            case 'B' -> cursorRow = Math.min(scrollBottom, cursorRow + Math.max(1, p0));  // CUD
            case 'C' -> cursorCol = Math.min(COLS - 1,    cursorCol + Math.max(1, p0));   // CUF
            case 'D' -> cursorCol = Math.max(0,           cursorCol - Math.max(1, p0));   // CUB
            case 'E' -> { cursorCol = 0; cursorRow = Math.min(ROWS - 1, cursorRow + Math.max(1, p0)); } // CNL
            case 'F' -> { cursorCol = 0; cursorRow = Math.max(0,        cursorRow - Math.max(1, p0)); } // CPL
            case 'G' -> cursorCol = Math.max(0, Math.min(COLS - 1, Math.max(1, p0) - 1));              // CHA
            case 'd' -> cursorRow = Math.max(0, Math.min(ROWS - 1, Math.max(1, p0) - 1));              // VPA
            case 'H', 'f' -> {  // CUP / HVP — cursor position (1-based)
                cursorRow = Math.max(0, Math.min(ROWS - 1, Math.max(1, p0) - 1));
                cursorCol = Math.max(0, Math.min(COLS - 1, Math.max(1, p1) - 1));
            }
            // ── Scroll region ─────────────────────────────────────────────────
            case 'r' -> {  // DECSTBM — set scrolling region (1-based)
                int top = Math.max(0, Math.min(ROWS - 1, Math.max(1, p0) - 1));
                int bot = Math.max(0, Math.min(ROWS - 1, (p1 <= 0 ? ROWS : p1) - 1));
                if (top < bot) { scrollTop = top; scrollBottom = bot; }
                else           { scrollTop = 0;   scrollBottom = ROWS - 1; }
                cursorRow = 0; cursorCol = 0;
            }
            // ── Erase ─────────────────────────────────────────────────────────
            case 'J' -> eraseDisplay(p0);
            case 'K' -> eraseLine(p0);
            case 'X' -> {  // ECH — erase chars at cursor without moving it
                int n = Math.min(Math.max(1, p0), COLS - cursorCol);
                for (int c = cursorCol; c < cursorCol + n; c++)
                    cells[cursorRow * COLS + c] = packCell(' ');
            }
            // ── Line insert / delete ──────────────────────────────────────────
            case 'L' -> insertLines(Math.max(1, p0));
            case 'M' -> deleteLines(Math.max(1, p0));
            // ── Character insert / delete ─────────────────────────────────────
            case '@' -> insertChars(Math.max(1, p0));
            case 'P' -> deleteChars(Math.max(1, p0));
            // ── Scroll ───────────────────────────────────────────────────────
            case 'S' -> scrollUp(Math.max(1, p0));
            case 'T' -> scrollDown(Math.max(1, p0));
            // ── SGR — reads full csiParams[] array, so dispatch before clearing ──
            case 'm' -> applySGR(totalParams);
            // ── Save / restore cursor ─────────────────────────────────────────
            case 's' -> saveCursor();
            case 'u' -> restoreCursor();
            // ── Device status report ──────────────────────────────────────────
            case 'n' -> {
                if (p0 == 6) {  // CPR — cursor position report
                    String resp = "\033[" + (cursorRow + 1) + ";" + (cursorCol + 1) + "R";
                    for (byte b : resp.getBytes(StandardCharsets.US_ASCII)) inputQueue.add(b);
                }
            }
        }
        // Params no longer needed; clear slot 0 so the next sequence starts clean.
        csiParams[0] = 0;
    }

    private void handleDecPrivate(int ch, int mode) {
        // We only need to track a few modes; the rest are silently absorbed so
        // the parser does not print the trailing mode characters as text.
        // (intentionally empty for now — key effect is preventing text corruption)
    }

    // ── Cursor save / restore ─────────────────────────────────────────────────

    private void saveCursor() {
        savedRow       = cursorRow;  savedCol       = cursorCol;
        savedFg        = fgColor;    savedBg        = bgColor;
        savedBold      = bold;       savedUnderline = underline;
        savedReverse   = reverse;
    }

    private void restoreCursor() {
        cursorRow  = Math.max(0, Math.min(ROWS - 1, savedRow));
        cursorCol  = Math.max(0, Math.min(COLS - 1, savedCol));
        fgColor    = savedFg;       bgColor    = savedBg;
        bold       = savedBold;     underline  = savedUnderline;
        reverse    = savedReverse;
    }

    // ── Character placement ───────────────────────────────────────────────────

    private void putChar(int ch) {
        if (cursorCol >= COLS) {
            cursorCol = 0;
            lineFeed();
        }
        cells[cursorRow * COLS + cursorCol] = packCell(ch);
        cursorCol++;
    }

    // ── Scroll / line operations ──────────────────────────────────────────────

    private void lineFeed() {
        if (cursorRow < scrollBottom) {
            cursorRow++;
        } else if (cursorRow == scrollBottom) {
            scrollUp(1);
        } else if (cursorRow < ROWS - 1) {
            // cursor below scroll region — advance without scrolling
            cursorRow++;
        }
    }

    private void reverseIndex() {
        if (cursorRow == scrollTop) {
            scrollDown(1);
        } else if (cursorRow > 0) {
            cursorRow--;
        }
    }

    // Move lines up within [scrollTop..scrollBottom]; blank new rows at bottom.
    void scrollUp(int n) {
        int lines = scrollBottom - scrollTop + 1;
        n = Math.min(n, lines);
        int move = lines - n;
        if (move > 0)
            System.arraycopy(cells, (scrollTop + n) * COLS, cells, scrollTop * COLS, move * COLS);
        for (int r = scrollBottom - n + 1; r <= scrollBottom; r++) clearLine(r);
    }

    // Move lines down within [scrollTop..scrollBottom]; blank new rows at top.
    void scrollDown(int n) {
        int lines = scrollBottom - scrollTop + 1;
        n = Math.min(n, lines);
        int move = lines - n;
        if (move > 0)
            System.arraycopy(cells, scrollTop * COLS, cells, (scrollTop + n) * COLS, move * COLS);
        for (int r = scrollTop; r < scrollTop + n; r++) clearLine(r);
    }

    // ── Erase operations ──────────────────────────────────────────────────────

    private void eraseDisplay(int mode) {
        switch (mode) {
            case 0 -> clearFrom(cursorRow * COLS + cursorCol);          // cursor → end
            case 1 -> {                                                  // start → cursor
                for (int i = 0; i <= cursorRow * COLS + cursorCol; i++) cells[i] = packCell(' ');
            }
            case 2, 3 -> {                                              // whole screen (cursor stays)
                for (int i = 0; i < cells.length; i++) cells[i] = packCell(' ');
            }
        }
    }

    private void eraseLine(int mode) {
        int row = cursorRow * COLS;
        switch (mode) {
            case 0 -> { for (int c = cursorCol; c < COLS; c++) cells[row + c] = packCell(' '); }
            case 1 -> { for (int c = 0; c <= cursorCol;   c++) cells[row + c] = packCell(' '); }
            case 2 -> clearLine(cursorRow);
        }
    }

    // ── Line insert / delete (respect scroll region) ─────────────────────────

    private void insertLines(int n) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return;
        n = Math.min(n, scrollBottom - cursorRow + 1);
        int move = scrollBottom - cursorRow + 1 - n;
        if (move > 0)
            System.arraycopy(cells, cursorRow * COLS, cells, (cursorRow + n) * COLS, move * COLS);
        for (int r = cursorRow; r < cursorRow + n; r++) clearLine(r);
        cursorCol = 0;
    }

    private void deleteLines(int n) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return;
        n = Math.min(n, scrollBottom - cursorRow + 1);
        int src  = cursorRow + n;
        int move = scrollBottom - src + 1;
        if (move > 0)
            System.arraycopy(cells, src * COLS, cells, cursorRow * COLS, move * COLS);
        for (int r = scrollBottom - n + 1; r <= scrollBottom; r++) clearLine(r);
        cursorCol = 0;
    }

    // ── Character insert / delete ─────────────────────────────────────────────

    private void insertChars(int n) {
        int row  = cursorRow * COLS;
        int avail = COLS - cursorCol;
        n = Math.min(n, avail);
        int move = avail - n;
        if (move > 0) System.arraycopy(cells, row + cursorCol, cells, row + cursorCol + n, move);
        for (int c = cursorCol; c < cursorCol + n; c++) cells[row + c] = packCell(' ');
    }

    private void deleteChars(int n) {
        int row  = cursorRow * COLS;
        int avail = COLS - cursorCol;
        n = Math.min(n, avail);
        int remaining = avail - n;
        if (remaining > 0) System.arraycopy(cells, row + cursorCol + n, cells, row + cursorCol, remaining);
        for (int c = COLS - n; c < COLS; c++) cells[row + c] = packCell(' ');
    }

    // ── SGR attribute handling ────────────────────────────────────────────────

    private void applySGR(int count) {
        for (int i = 0; i < count; i++) {
            int p = csiParams[i];
            switch (p) {
                case 0  -> { fgColor = 7; bgColor = 0; bold = false; underline = false; reverse = false; }
                case 1  -> bold      = true;
                case 4  -> underline = true;
                case 7  -> reverse   = true;
                case 22 -> bold      = false;
                case 24 -> underline = false;
                case 27 -> reverse   = false;
                // Normal foreground colors (0-7)
                case 30, 31, 32, 33, 34, 35, 36, 37 -> fgColor = p - 30;
                case 39 -> fgColor = 7;
                // Normal background colors (0-7)
                case 40, 41, 42, 43, 44, 45, 46, 47 -> bgColor = p - 40;
                case 49 -> bgColor = 0;
                // Bright foreground colors (8-15)
                case 90, 91, 92, 93, 94, 95, 96, 97 -> fgColor = (p - 90) + 8;
                // Bright background colors (8-15)
                case 100, 101, 102, 103, 104, 105, 106, 107 -> bgColor = (p - 100) + 8;
                default -> {}
            }
        }
    }

    // ── Cell helpers ──────────────────────────────────────────────────────────

    private int packCell(int ch) {
        int attrs = (bold      ? 0x8 : 0)
                  | (underline ? 0x4 : 0)
                  | (reverse   ? 0x2 : 0);
        return (attrs << 28) | (fgColor << 24) | (bgColor << 20) | (ch & 0xFFFF);
    }

    private void clearLine(int row) {
        for (int c = 0; c < COLS; c++) cells[row * COLS + c] = packCell(' ');
    }

    private void clearFrom(int idx) {
        for (int i = idx; i < cells.length; i++) cells[i] = packCell(' ');
    }

    private void resetTerminal() {
        fgColor = 7; bgColor = 0;
        bold = false; underline = false; reverse = false;
        scrollTop = 0; scrollBottom = ROWS - 1;
        cursorRow = 0; cursorCol = 0;
        parseState = ParseState.NORMAL;
        csiPrivate = false;
        for (int i = 0; i < cells.length; i++) cells[i] = packCell(' ');
    }

    // ── Input path (player keystrokes → VM UART) ──────────────────────────────

    public void putInput(byte b)           { inputQueue.add(b); }
    public byte pollInput()                { Byte b = inputQueue.poll(); return b != null ? b : -1; }
    public boolean hasInput()              { return !inputQueue.isEmpty(); }

    /** Drain pending player input; call from VM thread before board.step(). */
    public java.util.Iterator<Byte> drainInput() { return inputQueue.iterator(); }

    // ── Render API ────────────────────────────────────────────────────────────

    public synchronized int[] getCellSnapshot() { return cells.clone(); }
    public synchronized int getCursorRow()       { return cursorRow; }
    public synchronized int getCursorCol()       { return cursorCol; }

    /** Overwrite the entire cell grid with a snapshot received from the server. */
    public synchronized void setSnapshot(int[] snapshot, int row, int col) {
        int len = Math.min(snapshot.length, cells.length);
        System.arraycopy(snapshot, 0, cells, 0, len);
        cursorRow = Math.max(0, Math.min(ROWS - 1, row));
        cursorCol = Math.max(0, Math.min(COLS - 1, col));
        dirty.set(true);
    }

    public boolean isDirty() { return dirty.getAndSet(false); }
}
