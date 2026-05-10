package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal VT100/ANSI terminal emulator — 80x24.
 * Handles the subset of escape sequences that Buildroot's Linux shell emits.
 * Thread-safe: write() is called from the VM thread, render data read from server tick.
 */
public final class VMTerminal {

    public static final int COLS = 80;
    public static final int ROWS = 24;

    // Packed: high 8 bits = fg color index (0-7), next 8 = bg, low 16 = char codepoint
    private final int[] cells = new int[COLS * ROWS];
    private int cursorRow = 0;
    private int cursorCol = 0;

    // ANSI escape parser state
    private enum ParseState { NORMAL, ESC, CSI }
    private ParseState parseState = ParseState.NORMAL;
    private final int[] csiParams = new int[8];
    private int csiParamCount = 0;

    // Current SGR attributes
    private int fgColor = 7; // white
    private int bgColor = 0; // black

    // Pending input from player (server → VM direction)
    private final java.util.concurrent.ConcurrentLinkedQueue<Byte> inputQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    private final AtomicBoolean dirty = new AtomicBoolean(false);

    // --- Output path (VM writes, server tick reads) ---

    public synchronized void write(byte b) {
        int ch = b & 0xFF;

        switch (parseState) {
            case NORMAL -> handleNormal(ch);
            case ESC    -> handleEsc(ch);
            case CSI    -> handleCsi(ch);
        }
        dirty.set(true);
    }

    public synchronized void write(ByteBuffer data) {
        while (data.hasRemaining()) write(data.get());
    }

    private void handleNormal(int ch) {
        switch (ch) {
            case 0x07 -> {} // BEL — ignore
            case 0x08 -> { if (cursorCol > 0) cursorCol--; }    // BS
            case 0x09 -> cursorCol = Math.min((cursorCol / 8 + 1) * 8, COLS - 1); // HT
            case 0x0A, 0x0B, 0x0C -> lineFeed();  // LF/VT/FF
            case 0x0D -> cursorCol = 0;            // CR
            case 0x1B -> parseState = ParseState.ESC;
            default -> {
                if (ch >= 0x20) putChar(ch);
            }
        }
    }

    private void handleEsc(int ch) {
        parseState = ParseState.NORMAL;
        switch (ch) {
            case '[' -> { parseState = ParseState.CSI; csiParamCount = 0; csiParams[0] = 0; }
            case 'c' -> resetTerminal();  // RIS — full reset
            default  -> {}               // ignore unknowns
        }
    }

    private void handleCsi(int ch) {
        if (ch >= '0' && ch <= '9') {
            if (csiParamCount < csiParams.length) {
                csiParams[csiParamCount] = csiParams[csiParamCount] * 10 + (ch - '0');
            }
        } else if (ch == ';') {
            csiParamCount = Math.min(csiParamCount + 1, csiParams.length - 1);
            csiParams[csiParamCount] = 0;
        } else {
            int totalParams = csiParamCount + 1;
            parseState = ParseState.NORMAL;
            switch (ch) {
                case 'A' -> cursorRow = Math.max(0, cursorRow - Math.max(1, csiParams[0]));
                case 'B' -> cursorRow = Math.min(ROWS - 1, cursorRow + Math.max(1, csiParams[0]));
                case 'C' -> cursorCol = Math.min(COLS - 1, cursorCol + Math.max(1, csiParams[0]));
                case 'D' -> cursorCol = Math.max(0, cursorCol - Math.max(1, csiParams[0]));
                case 'H', 'f' -> {
                    cursorRow = Math.max(0, Math.min(ROWS - 1, (csiParams[0] < 1 ? 1 : csiParams[0]) - 1));
                    cursorCol = totalParams > 1 ? Math.max(0, Math.min(COLS - 1, (csiParams[1] < 1 ? 1 : csiParams[1]) - 1)) : 0;
                }
                case 'J' -> eraseDisplay(csiParams[0]);
                case 'K' -> eraseLine(csiParams[0]);
                case 'L' -> insertLines(Math.max(1, csiParams[0]));
                case 'M' -> deleteLines(Math.max(1, csiParams[0]));
                case 'P' -> deleteChars(Math.max(1, csiParams[0]));
                case 'm' -> applySGR(csiParams, totalParams);
                default  -> {}
            }
            csiParamCount = 0;
            csiParams[0] = 0;
        }
    }

    private void putChar(int ch) {
        if (cursorCol >= COLS) {
            cursorCol = 0;
            lineFeed();
        }
        cells[cursorRow * COLS + cursorCol] = packCell(ch);
        cursorCol++;
    }

    private void lineFeed() {
        cursorRow++;
        if (cursorRow >= ROWS) {
            // Scroll up
            System.arraycopy(cells, COLS, cells, 0, COLS * (ROWS - 1));
            clearLine(ROWS - 1);
            cursorRow = ROWS - 1;
        }
    }

    private void eraseDisplay(int mode) {
        switch (mode) {
            case 0 -> { // cursor to end
                clearFrom(cursorRow * COLS + cursorCol);
            }
            case 1 -> { // beginning to cursor
                for (int i = 0; i <= cursorRow * COLS + cursorCol; i++) cells[i] = packCell(' ');
            }
            case 2, 3 -> { // whole screen
                for (int i = 0; i < cells.length; i++) cells[i] = packCell(' ');
                cursorRow = 0; cursorCol = 0;
            }
        }
    }

    private void eraseLine(int mode) {
        switch (mode) {
            case 0 -> { for (int c = cursorCol; c < COLS; c++) cells[cursorRow * COLS + c] = packCell(' '); }
            case 1 -> { for (int c = 0; c <= cursorCol; c++) cells[cursorRow * COLS + c] = packCell(' '); }
            case 2 -> clearLine(cursorRow);
        }
    }

    private void insertLines(int n) {
        int src = cursorRow;
        int dst = Math.min(cursorRow + n, ROWS);
        int move = ROWS - dst;
        if (move > 0) System.arraycopy(cells, src * COLS, cells, dst * COLS, move * COLS);
        for (int r = src; r < dst; r++) clearLine(r);
    }

    private void deleteLines(int n) {
        int src = Math.min(cursorRow + n, ROWS);
        int move = ROWS - src;
        if (move > 0) System.arraycopy(cells, src * COLS, cells, cursorRow * COLS, move * COLS);
        for (int r = ROWS - n; r < ROWS; r++) clearLine(r);
    }

    private void deleteChars(int n) {
        int row = cursorRow * COLS;
        int remaining = COLS - cursorCol - n;
        if (remaining > 0) System.arraycopy(cells, row + cursorCol + n, cells, row + cursorCol, remaining);
        for (int c = COLS - n; c < COLS; c++) cells[row + c] = packCell(' ');
    }

    private void applySGR(int[] params, int count) {
        for (int i = 0; i < count; i++) {
            int p = params[i];
            switch (p) {
                case 0  -> { fgColor = 7; bgColor = 0; }
                case 1  -> {} // bold — ignore for now
                case 30, 31, 32, 33, 34, 35, 36, 37 -> fgColor = p - 30;
                case 39 -> fgColor = 7;
                case 40, 41, 42, 43, 44, 45, 46, 47 -> bgColor = p - 40;
                case 49 -> bgColor = 0;
                case 90, 91, 92, 93, 94, 95, 96, 97 -> fgColor = p - 90; // bright fg
                default -> {}
            }
        }
    }

    private void clearLine(int row) {
        for (int c = 0; c < COLS; c++) cells[row * COLS + c] = packCell(' ');
    }

    private void clearFrom(int idx) {
        for (int i = idx; i < cells.length; i++) cells[i] = packCell(' ');
    }

    private int packCell(int ch) {
        return (fgColor << 24) | (bgColor << 16) | (ch & 0xFFFF);
    }

    private void resetTerminal() {
        for (int i = 0; i < cells.length; i++) cells[i] = packCell(' ');
        cursorRow = 0; cursorCol = 0;
        fgColor = 7; bgColor = 0;
        parseState = ParseState.NORMAL;
    }

    // --- Input path (player keystrokes → VM UART) ---

    public void putInput(byte b) {
        inputQueue.add(b);
    }

    /** Drain pending player input; call from VM thread before board.step(). */
    public java.util.Iterator<Byte> drainInput() {
        return inputQueue.iterator();
    }

    public byte pollInput() {
        Byte b = inputQueue.poll();
        return b != null ? b : -1;
    }

    public boolean hasInput() {
        return !inputQueue.isEmpty();
    }

    // --- Render API ---

    /** Returns a snapshot of the cell grid for rendering. Each int: fg<<24 | bg<<16 | char. */
    public synchronized int[] getCellSnapshot() {
        return cells.clone();
    }

    public synchronized int getCursorRow() { return cursorRow; }
    public synchronized int getCursorCol() { return cursorCol; }

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
