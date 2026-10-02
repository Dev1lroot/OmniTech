/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui.pcb;

import com.dev1lroot.mcmods.omnitech.pcb.PartSpec;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.PlacedPart;
import com.dev1lroot.mcmods.omnitech.pcb.ResistorCode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Draws boards, copper and soldered parts for the PCB station screens. */
public final class PcbRenderer {

    public static final int CELL = 7;

    private static final int VOID      = 0xFF2B2B2B;
    private static final int MASK      = 0xFF1C5A2C;
    private static final int MASK_EDGE = 0xFF8A6A2A;
    private static final int GRID_DOT  = 0xFF2A7139;
    private static final int COPPER    = 0xFFD08A3E;
    private static final int COPPER_HI = 0xFFFFC27A;
    private static final int TIN       = 0xFFD8D8D8;
    private static final int GOLD      = 0xFFE2B84A;

    private PcbRenderer() {}

    /**
     * @param lit cells to draw brighter (the hovered net), or null
     */
    public static void drawBoard(GuiGraphicsExtractor g, Font font, PcbDesign d, int x0, int y0, boolean[] lit) {
        g.fill(x0 - 1, y0 - 1, x0 + PcbDesign.MAX_W * CELL + 1, y0 + PcbDesign.MAX_H * CELL + 1, 0xFF373737);
        g.fill(x0, y0, x0 + PcbDesign.MAX_W * CELL, y0 + PcbDesign.MAX_H * CELL, VOID);
        // Active area outline, so the size is visible even where the board is cut away
        g.outline(x0 - 1, y0 - 1, d.width() * CELL + 2, d.height() * CELL + 2, 0xFF5A5A5A);

        for (int y = 0; y < d.height(); y++) {
            for (int x = 0; x < d.width(); x++) {
                if (!d.isBoard(x, y)) continue;
                int px = x0 + x * CELL, py = y0 + y * CELL;
                g.fill(px, py, px + CELL, py + CELL, MASK);
                // laminate edge where the outline ends
                if (!d.isBoard(x, y - 1)) g.fill(px, py, px + CELL, py + 1, MASK_EDGE);
                if (!d.isBoard(x, y + 1)) g.fill(px, py + CELL - 1, px + CELL, py + CELL, MASK_EDGE);
                if (!d.isBoard(x - 1, y)) g.fill(px, py, px + 1, py + CELL, MASK_EDGE);
                if (!d.isBoard(x + 1, y)) g.fill(px + CELL - 1, py, px + CELL, py + CELL, MASK_EDGE);
                g.fill(px + 3, py + 3, px + 4, py + 4, GRID_DOT);
            }
        }

        // Traces: centre blob + bridge toward each copper neighbour
        for (int y = 0; y < d.height(); y++) {
            for (int x = 0; x < d.width(); x++) {
                if (!d.isTrace(x, y)) continue;
                boolean hi = lit != null && lit[PcbDesign.index(x, y)];
                int c = hi ? COPPER_HI : COPPER;
                int px = x0 + x * CELL, py = y0 + y * CELL;
                g.fill(px + 2, py + 2, px + 5, py + 5, c);
                if (d.isCopper(x + 1, y)) g.fill(px + 5, py + 2, px + CELL, py + 5, c);
                if (d.isCopper(x - 1, y)) g.fill(px, py + 2, px + 2, py + 5, c);
                if (d.isCopper(x, y + 1)) g.fill(px + 2, py + 5, px + 5, py + CELL, c);
                if (d.isCopper(x, y - 1)) g.fill(px + 2, py, px + 5, py + 2, c);
            }
        }

        // Pads: tinned ring with a drill hole; labelled pads are gold terminals
        for (int y = 0; y < d.height(); y++) {
            for (int x = 0; x < d.width(); x++) {
                if (!d.isPad(x, y)) continue;
                int px = x0 + x * CELL, py = y0 + y * CELL;
                boolean hi = lit != null && lit[PcbDesign.index(x, y)];
                String label = d.label(x, y);
                if (label != null) {
                    g.fill(px, py, px + CELL, py + CELL, hi ? 0xFFFFFFFF : 0xFF7A5A10);
                    g.fill(px + 1, py + 1, px + CELL - 1, py + CELL - 1, GOLD);
                } else {
                    g.fill(px + 1, py + 1, px + CELL - 1, py + CELL - 1, hi ? 0xFFFFFFFF : TIN);
                    g.fill(px + 3, py + 3, px + 4, py + 4, 0xFF141414);
                }
            }
        }
        for (var e : d.labels().entrySet()) {
            int x = e.getKey() % PcbDesign.MAX_W, y = e.getKey() / PcbDesign.MAX_W;
            smallText(g, font, e.getValue(), x0 + x * CELL + CELL / 2, y0 + y * CELL + 2, 0xFF2A1A00, 0.4f);
        }
    }

    /**
     * Draws a part over its pads.
     *
     * @param tint 0 for a soldered part, otherwise an ARGB wash (placement ghost)
     */
    public static void drawPart(GuiGraphicsExtractor g, PlacedPart p, int x0, int y0, int tint) {
        PartSpec s = p.spec().orElse(null);
        if (s == null) return;
        List<int[]> pins = p.pinCells();
        List<int[]> body = p.bodyCells();

        // Leads: pin centre → body centre
        int[] bc = centre(body);
        for (int[] pin : pins) {
            int px = x0 + pin[0] * CELL + CELL / 2, py = y0 + pin[1] * CELL + CELL / 2;
            int bx = x0 + bc[0], by = y0 + bc[1];
            g.fill(Math.min(px, bx), py - 0, Math.max(px, bx) + 1, py + 1, 0xFFB0B0B0);
            g.fill(bx, Math.min(py, by), bx + 1, Math.max(py, by) + 1, 0xFFB0B0B0);
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] b : body) {
            minX = Math.min(minX, b[0]); minY = Math.min(minY, b[1]);
            maxX = Math.max(maxX, b[0]); maxY = Math.max(maxY, b[1]);
        }
        int bx0 = x0 + minX * CELL, by0 = y0 + minY * CELL;
        int bx1 = x0 + (maxX + 1) * CELL, by1 = y0 + (maxY + 1) * CELL;
        boolean horizontal = pins.size() == 2 ? pins.get(0)[1] == pins.get(1)[1] : (p.rot() & 1) == 0;

        switch (s.kind()) {
            case RESISTOR -> {
                // the painted bands themselves, on a beige (carbon) or blue (metal film) body
                List<ResistorCode.Band> bands = ResistorCode.fromCodes(p.bands());
                int[] r = axial(bx0, by0, bx1, by1, horizontal);
                g.fill(r[0], r[1], r[2], r[3], 0xFF000000 | ResistorCode.bodyColor(bands));
                int len = horizontal ? r[2] - r[0] : r[3] - r[1];
                for (int i = 0; i < bands.size(); i++) {
                    int off = 1 + i * Math.max(1, (len - 2) / Math.max(1, bands.size()));
                    int col = 0xFF000000 | bands.get(i).rgb;
                    if (horizontal) g.fill(r[0] + off, r[1], r[0] + off + 1, r[3], col);
                    else            g.fill(r[0], r[1] + off, r[2], r[1] + off + 1, col);
                }
            }
            case CAPACITOR -> {
                int[] r = axial(bx0, by0, bx1, by1, horizontal);
                boolean big = s.value() >= 500e-6;
                g.fill(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, big ? 0xFF1A1A1A : 0xFF23449A);
                cathodeBand(g, r, pins, x0, y0, horizontal, big ? 0xFF8A8A8A : 0xFFAFC4E8);
            }
            case DIODE, ZENER -> {
                int[] r = axial(bx0, by0, bx1, by1, horizontal);
                g.fill(r[0], r[1], r[2], r[3], s.kind() == PartSpec.Kind.ZENER ? 0xFFB5482C : 0xFF262626);
                cathodeBand(g, r, pins, x0, y0, horizontal, s.kind() == PartSpec.Kind.ZENER ? 0xFF111111 : 0xFFCFCFCF);
            }
            case NPN, PNP -> {
                g.fill(bx0, by0 + 1, bx1, by1 - 1, 0xFF1E1E1E);
                if (!horizontal) g.fill(bx0 + 1, by0, bx1 - 1, by1, 0xFF1E1E1E);
                g.fill(bx0 + 1, by0 + 2, bx0 + 2, by0 + 3,
                        s.kind() == PartSpec.Kind.NPN ? 0xFF55AAFF : 0xFFFF7755);
            }
        }
        if (tint != 0) {
            for (int[] c : p.footprint()) {
                int px = x0 + c[0] * CELL, py = y0 + c[1] * CELL;
                g.fill(px, py, px + CELL, py + CELL, tint);
            }
        }
    }

    /** Body rectangle of an axial part, slimmer across the lead axis. */
    private static int[] axial(int x0, int y0, int x1, int y1, boolean horizontal) {
        return horizontal ? new int[]{x0 - 1, y0 + 2, x1 + 1, y1 - 2} : new int[]{x0 + 2, y0 - 1, x1 - 2, y1 + 1};
    }

    private static void cathodeBand(GuiGraphicsExtractor g, int[] r, List<int[]> pins, int x0, int y0,
            boolean horizontal, int col) {
        int[] k = pins.get(1), a = pins.get(0);
        if (horizontal) {
            int bx = k[0] > a[0] ? r[2] - 2 : r[0];
            g.fill(bx, r[1], bx + 2, r[3], col);
        } else {
            int by = k[1] > a[1] ? r[3] - 2 : r[1];
            g.fill(r[0], by, r[2], by + 2, col);
        }
    }

    private static int[] centre(List<int[]> cells) {
        int sx = 0, sy = 0;
        for (int[] c : cells) { sx += c[0] * CELL + CELL / 2; sy += c[1] * CELL + CELL / 2; }
        return new int[]{sx / cells.size(), sy / cells.size()};
    }

    public static void smallText(GuiGraphicsExtractor g, Font font, String s, int centerX, int y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(centerX, y);
        g.pose().scale(scale, scale);
        g.text(font, s, -font.width(s) / 2, 0, color, false);
        g.pose().popMatrix();
    }

    /** Cell under the mouse, or null outside the grid. */
    public static int[] cellAt(double mx, double my, int x0, int y0) {
        if (mx < x0 || my < y0) return null;
        int x = (int) ((mx - x0) / CELL), y = (int) ((my - y0) / CELL);
        return x < PcbDesign.MAX_W && y < PcbDesign.MAX_H ? new int[]{x, y} : null;
    }

    public static void slotFrame(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y, 0xFF373737);
        g.fill(x - 1, y - 1, x, y + 17, 0xFF373737);
        g.fill(x, y + 16, x + 17, y + 17, 0xFFFFFFFF);
        g.fill(x + 16, y, x + 17, y + 17, 0xFFFFFFFF);
        g.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
    }

    public static void panel(GuiGraphicsExtractor g, int x0, int y0, int w, int h) {
        g.fill(x0, y0, x0 + w, y0 + h, 0xFF373737);
        g.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + h - 1, 0xFFFFFFFF);
        g.fill(x0 + 2, y0 + 2, x0 + w - 1, y0 + h - 1, 0xFF555555);
        g.fill(x0 + 2, y0 + 2, x0 + w - 2, y0 + h - 2, 0xFFC6C6C6);
    }

    public static void playerSlots(GuiGraphicsExtractor g, int x0, int y0) {
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) slotFrame(g, x0 + c * 18, y0 + r * 18);
        for (int c = 0; c < 9; c++) slotFrame(g, x0 + c * 18, y0 + 58);
    }
}
