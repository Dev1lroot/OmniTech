/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Hex dump editor widget. Shows binary data as 8-bytes-per-row hex dump with ASCII preview.
 * Click a byte to select it; type two hex digits to overwrite it.
 */
public class HexEditorWidget {

    public static final int BYTES_PER_ROW = 8;
    private static final int CHAR_W = 6;
    private static final int CHAR_H = 9;

    // Column layout: OFFSET(8ch+2sp=60) HEX(8×3ch-1=143) SPACE(6) ASCII(8ch=48)
    private static final int OFFSET_W = 10 * CHAR_W;   // "00000000  "
    private static final int HEX_W    = (BYTES_PER_ROW * 3 - 1) * CHAR_W; // "XX XX..."
    private static final int GAP      = CHAR_W;
    private static final int ASCII_W  = BYTES_PER_ROW * CHAR_W;

    private byte[] data;
    private int rowOffset   = 0;
    private int selectedIdx = -1;   // selected byte absolute index (-1 = none)
    private int nibblePend  = -1;   // first hex nibble typed (0-15), -1 = none

    public HexEditorWidget(byte[] data) {
        this.data = data != null ? data : new byte[0];
    }

    public void setData(byte[] data) {
        this.data      = data != null ? data : new byte[0];
        rowOffset      = 0;
        selectedIdx    = -1;
        nibblePend     = -1;
    }

    public byte[] getData() { return data; }

    private int totalRows() {
        return data.length == 0 ? 0 : (data.length + BYTES_PER_ROW - 1) / BYTES_PER_ROW;
    }

    private int visibleRows(int h) { return h / CHAR_H; }

    // ── Rendering ─────────────────────────────────────────────────────────────

    public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF0D0D0D);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF050505);

        if (data.length == 0) {
            g.text(font, Component.literal("No ROM data  —  insert a Firmware ROM"),
                    x + 4, y + 4, 0xFF555555, false);
            return;
        }

        int visRows = visibleRows(h);
        for (int row = 0; row < visRows; row++) {
            int byteRow = row + rowOffset;
            if (byteRow >= totalRows()) break;
            int ry      = y + row * CHAR_H;
            int baseIdx = byteRow * BYTES_PER_ROW;

            // Offset
            String off = String.format("%08X  ", baseIdx);
            g.text(font, Component.literal(off), x + 2, ry, 0xFF666666, false);

            // Hex + ASCII
            StringBuilder ascii = new StringBuilder(BYTES_PER_ROW);
            for (int col = 0; col < BYTES_PER_ROW; col++) {
                int idx = baseIdx + col;
                int hx  = x + 2 + OFFSET_W + col * 3 * CHAR_W;

                if (idx < data.length) {
                    int b   = data[idx] & 0xFF;
                    String hex = String.format("%02X", b);
                    boolean sel = (idx == selectedIdx);

                    if (sel) {
                        g.fill(hx - 1, ry, hx + 2 * CHAR_W + 1, ry + CHAR_H, 0xFF224466);
                    }
                    int col6 = sel ? 0xFFFFFF44 : (b == 0 ? 0xFF333333 : 0xFF44AAFF);
                    g.text(font, Component.literal(hex), hx, ry, col6, false);
                    ascii.append(b >= 0x20 && b < 0x7F ? (char) b : '.');
                } else {
                    g.text(font, Component.literal("  "), hx, ry, 0xFF222222, false);
                    ascii.append(' ');
                }
            }

            int ax = x + 2 + OFFSET_W + BYTES_PER_ROW * 3 * CHAR_W + GAP;
            g.text(font, Component.literal(ascii.toString()), ax, ry, 0xFF447744, false);
        }

        // Scrollbar hint if data overflows
        if (totalRows() > visibleRows(h)) {
            String hint = (rowOffset * BYTES_PER_ROW) + "/" + data.length;
            g.text(font, Component.literal(hint), x + w - font.width(hint) - 2,
                    y + h - CHAR_H, 0xFF444444, false);
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    public boolean mouseScrolled(double scrollY, int h) {
        int delta = scrollY > 0 ? -3 : 3;
        rowOffset = clampOffset(rowOffset + delta, h);
        return true;
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int h) {
        int row = (int)((my - y) / CHAR_H);
        if (row < 0 || row >= visibleRows(h)) return false;
        int byteRow = row + rowOffset;
        if (byteRow >= totalRows()) return false;

        int hexStartX = x + 2 + OFFSET_W;
        if (mx < hexStartX) return false;
        int relX = (int)(mx - hexStartX);
        int col  = relX / (3 * CHAR_W);
        if (col < 0 || col >= BYTES_PER_ROW) return false;

        int idx = byteRow * BYTES_PER_ROW + col;
        if (idx < data.length) {
            selectedIdx = idx;
            nibblePend  = -1;
            return true;
        }
        return false;
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    public boolean keyPressed(int key, int h) {
        if (selectedIdx < 0) return false;
        switch (key) {
            case InputConstants.KEY_RIGHT -> { advance(+1); return true; }
            case InputConstants.KEY_LEFT  -> { advance(-1); return true; }
            case InputConstants.KEY_DOWN  -> { advance(+BYTES_PER_ROW); ensureVisible(h); return true; }
            case InputConstants.KEY_UP    -> { advance(-BYTES_PER_ROW); ensureVisible(h); return true; }
            case InputConstants.KEY_PAGEDOWN -> {
                int step = visibleRows(h) * BYTES_PER_ROW;
                advance(+step); ensureVisible(h); return true;
            }
            case InputConstants.KEY_PAGEUP -> {
                int step = visibleRows(h) * BYTES_PER_ROW;
                advance(-step); ensureVisible(h); return true;
            }
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (selectedIdx < 0 || selectedIdx >= data.length) return false;
        int digit = hexDigit(c);
        if (digit < 0) return false;

        if (nibblePend < 0) {
            nibblePend = digit;
        } else {
            data[selectedIdx] = (byte)((nibblePend << 4) | digit);
            nibblePend = -1;
            if (selectedIdx + 1 < data.length) selectedIdx++;
        }
        return true;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void advance(int delta) {
        if (data.length == 0) return;
        selectedIdx = Math.max(0, Math.min(data.length - 1, selectedIdx + delta));
        nibblePend  = -1;
    }

    private void ensureVisible(int h) {
        int selRow = selectedIdx / BYTES_PER_ROW;
        int vis    = visibleRows(h);
        if (selRow < rowOffset)            rowOffset = selRow;
        if (selRow >= rowOffset + vis)     rowOffset = selRow - vis + 1;
        rowOffset = clampOffset(rowOffset, h);
    }

    private int clampOffset(int off, int h) {
        return Math.max(0, Math.min(Math.max(0, totalRows() - visibleRows(h)), off));
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }
}
