/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.vm;

import com.dev1lroot.mcmods.omnitech.blocks.logic.display.DisplayBlockEntity;

/**
 * Renders a VMTerminal's cell grid as pixel graphics onto a DisplayBlockEntity.
 *
 * Cell format (see VMTerminal):
 *   bits 31-28  attrs  (bold=31, underline=30, reverse=29)
 *   bits 27-24  fg color index 0-15
 *   bits 23-20  bg color index 0-15
 *   bits 15-0   character codepoint
 */
public final class TerminalDisplay {

    // 16-color CGA/ANSI palette — indices 0-7 normal, 8-15 bright
    private static final int[] ANSI_RGB = {
        0x000000, // 0  black
        0xAA0000, // 1  red
        0x00AA00, // 2  green
        0xAA5500, // 3  brown / dark yellow
        0x0000AA, // 4  blue
        0xAA00AA, // 5  magenta
        0x00AAAA, // 6  cyan
        0xAAAAAA, // 7  light gray
        0x555555, // 8  dark gray  (bright black)
        0xFF5555, // 9  bright red
        0x55FF55, // 10 bright green
        0xFFFF55, // 11 bright yellow
        0x5555FF, // 12 bright blue
        0xFF55FF, // 13 bright magenta
        0x55FFFF, // 14 bright cyan
        0xFFFFFF, // 15 white
    };

    public static void render(VMTerminal terminal, DisplayBlockEntity master) {
        int canvasW = master.getDisplayWidth();
        int canvasH = master.getDisplayHeight();
        if (canvasW <= 0 || canvasH <= 0) return;

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

                int  attrs     = (cell >> 28) & 0xF;
                int  fgIdx     = (cell >> 24) & 0xF;
                int  bgIdx     = (cell >> 20) & 0xF;
                int  ch        = cell & 0xFFFF;
                boolean isBold      = (attrs & 0x8) != 0;
                boolean isUnderline = (attrs & 0x4) != 0;
                boolean isReverse   = (attrs & 0x2) != 0;

                // Bold: promote normal foreground to its bright variant
                if (isBold && fgIdx < 8) fgIdx += 8;

                // Reverse video: swap fg and bg
                if (isReverse) { int tmp = fgIdx; fgIdx = bgIdx; bgIdx = tmp; }

                // Cursor: invert the cell
                boolean isCursor = (row == cursorRow && col == cursorCol);
                int fg = ansiRgb(isCursor ? bgIdx : fgIdx);
                int bg = ansiRgb(isCursor ? fgIdx : bgIdx);

                drawGlyph(canvas, renderW, col * TerminalFont.CHAR_W, row * TerminalFont.CHAR_H,
                          ch, fg, bg, isUnderline);
            }
        }

        master.blit(0, 0, renderW, renderH, canvas);
    }

    private static void drawGlyph(int[] canvas, int pitch,
                                  int x, int y, int ch,
                                  int fg, int bg, boolean underline) {
        for (int r = 0; r < TerminalFont.CHAR_H; r++) {
            int base = (y + r) * pitch + x;
            boolean underlineRow = underline && (r == TerminalFont.CHAR_H - 1);
            for (int c = 0; c < TerminalFont.CHAR_W; c++) {
                canvas[base + c] = (TerminalFont.getPixel(ch, r, c) || underlineRow) ? fg : bg;
            }
        }
    }

    private static int ansiRgb(int idx) { return ANSI_RGB[idx & 0xF]; }

    private TerminalDisplay() {}
}
