package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm;

import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;

/**
 * Renders a VMTerminal's cell grid as pixel graphics onto a DisplayBlockEntity.
 *
 * Each cell is drawn as an 8×8 pixel glyph using the ANSI foreground/background colors.
 * Characters that exceed the display canvas are silently clipped.
 * The display's existing pixel-art content at (x, y) positions beyond the rendered text
 * area is left untouched, so displays with other portIds work entirely independently.
 */
public final class TerminalDisplay {

    // Standard CGA/ANSI 8-color palette, matching the SGR 30-37 range in VMTerminal.
    private static final int[] ANSI_RGB = {
        0x000000, // 0  black
        0xAA0000, // 1  red
        0x00AA00, // 2  green
        0xAA5500, // 3  brown/yellow
        0x0000AA, // 4  blue
        0xAA00AA, // 5  magenta
        0x00AAAA, // 6  cyan
        0xAAAAAA, // 7  light gray (white)
    };

    /**
     * Render the terminal onto {@code master} (the display block at portId=0).
     * Called from the server tick whenever the terminal's dirty flag is set.
     *
     * @param terminal  server-side VMTerminal whose getCellSnapshot() to read
     * @param master    master DisplayBlockEntity for portId=0
     */
    public static void render(VMTerminal terminal, DisplayBlockEntity master) {
        int canvasW = master.getDisplayWidth();
        int canvasH = master.getDisplayHeight();
        if (canvasW <= 0 || canvasH <= 0) return;

        // How many full character cells fit in the display canvas
        int charCols = Math.min(VMTerminal.COLS, canvasW / TerminalFont.CHAR_W);
        int charRows = Math.min(VMTerminal.ROWS, canvasH / TerminalFont.CHAR_H);
        if (charCols <= 0 || charRows <= 0) return;

        int renderW = charCols * TerminalFont.CHAR_W;
        int renderH = charRows * TerminalFont.CHAR_H;

        int[] cells     = terminal.getCellSnapshot();
        int   cursorRow = terminal.getCursorRow();
        int   cursorCol = terminal.getCursorCol();
        int[] canvas    = new int[renderW * renderH];

        for (int row = 0; row < charRows; row++) {
            for (int col = 0; col < charCols; col++) {
                int cell = cells[row * VMTerminal.COLS + col];
                int fg   = ansiRgb((cell >> 24) & 0x07);
                int bg   = ansiRgb((cell >> 16) & 0x07);
                int ch   = cell & 0xFFFF;
                // Draw cursor as inverted cell
                if (row == cursorRow && col == cursorCol) {
                    drawGlyph(canvas, renderW, col * TerminalFont.CHAR_W, row * TerminalFont.CHAR_H, ch, bg, fg);
                } else {
                    drawGlyph(canvas, renderW, col * TerminalFont.CHAR_W, row * TerminalFont.CHAR_H, ch, fg, bg);
                }
            }
        }

        // blit() internally skips unchanged pixels and only syncs blocks that actually changed
        master.blit(0, 0, renderW, renderH, canvas);
    }

    private static void drawGlyph(int[] canvas, int pitch, int x, int y, int ch, int fg, int bg) {
        for (int r = 0; r < TerminalFont.CHAR_H; r++) {
            int base = (y + r) * pitch + x;
            for (int c = 0; c < TerminalFont.CHAR_W; c++) {
                canvas[base + c] = TerminalFont.getPixel(ch, r, c) ? fg : bg;
            }
        }
    }

    private static int ansiRgb(int idx) {
        return ANSI_RGB[idx & 7];
    }

    private TerminalDisplay() {}
}
