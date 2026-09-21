/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.chemistry.Atom;
import com.dev1lroot.mcmods.omnitech.chemistry.Bond;
import com.dev1lroot.mcmods.omnitech.chemistry.Molecule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared node-and-bond diagram rendering, used by both the Structure Table editor and the read-only viewer. */
public final class MoleculeRenderer {

    private MoleculeRenderer() {}

    public static final int ATOM_RADIUS = 7;

    /** Editor style: every atom drawn as a bordered, labelled square (so clicking/selecting is legible). */
    public static void render(GuiGraphicsExtractor graphics, Font font, Molecule molecule,
            int originX, int originY, int selectedAtomId) {
        render(graphics, font, molecule, originX, originY, selectedAtomId, false, 0);
    }

    /**
     * @param readOnly   when true, draws like a real skeletal structural formula instead of the
     *                   editor's clickable-square style: no borders, no marker at all for carbon
     *                   (implicit, just where bond lines meet), only heteroatoms get their
     *                   element letter (plain text on a small backdrop so the bond line doesn't
     *                   run through it).
     * @param backdropColor background colour to mask behind a heteroatom's letter in read-only
     *                       mode — pass whatever the canvas was filled with; unused otherwise.
     */
    public static void render(GuiGraphicsExtractor graphics, Font font, Molecule molecule,
            int originX, int originY, int selectedAtomId, boolean readOnly, int backdropColor) {
        for (Bond b : molecule.bonds()) {
            Atom a1 = molecule.atom(b.a());
            Atom a2 = molecule.atom(b.b());
            if (a1 == null || a2 == null) continue;
            drawBond(graphics, originX, originY, a1, a2, b.order());
        }
        for (Atom a : molecule.atoms()) {
            int x = originX + Math.round(a.x());
            int y = originY + Math.round(a.y());

            if (readOnly) {
                if (a.element().equals("C")) continue; // implicit — just the bond vertex, no marker
                String label = a.element();
                int tw = font.width(label);
                graphics.fill(x - tw / 2 - 1, y - 5, x + tw / 2 + 1, y + 5, backdropColor);
                graphics.text(font, label, x - tw / 2, y - 4, elementColor(a.element()), false);
                continue;
            }

            boolean selected = a.id() == selectedAtomId;
            int fill = selected ? 0xFF665500 : 0xFF303030;
            int border = elementColor(a.element());
            graphics.fill(x - ATOM_RADIUS, y - ATOM_RADIUS, x + ATOM_RADIUS, y + ATOM_RADIUS, border);
            graphics.fill(x - ATOM_RADIUS + 1, y - ATOM_RADIUS + 1, x + ATOM_RADIUS - 1, y + ATOM_RADIUS - 1, fill);
            String label = a.element();
            int tw = font.width(label);
            graphics.text(font, label, x - tw / 2, y - 4, 0xFFFFFFFF, false);
        }
    }

    private static void drawBond(GuiGraphicsExtractor graphics, int originX, int originY,
            Atom a1, Atom a2, int order) {
        int x1 = originX + Math.round(a1.x());
        int y1 = originY + Math.round(a1.y());
        int x2 = originX + Math.round(a2.x());
        int y2 = originY + Math.round(a2.y());

        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        double nx = len < 0.001 ? 0 : -dy / len;
        double ny = len < 0.001 ? 0 : dx / len;

        int spacing = 3;
        if (order <= 1) {
            drawLine(graphics, x1, y1, x2, y2);
        } else if (order == 2) {
            drawLine(graphics, (int) (x1 + nx * spacing), (int) (y1 + ny * spacing),
                    (int) (x2 + nx * spacing), (int) (y2 + ny * spacing));
            drawLine(graphics, (int) (x1 - nx * spacing), (int) (y1 - ny * spacing),
                    (int) (x2 - nx * spacing), (int) (y2 - ny * spacing));
        } else {
            drawLine(graphics, x1, y1, x2, y2);
            drawLine(graphics, (int) (x1 + nx * spacing * 1.6), (int) (y1 + ny * spacing * 1.6),
                    (int) (x2 + nx * spacing * 1.6), (int) (y2 + ny * spacing * 1.6));
            drawLine(graphics, (int) (x1 - nx * spacing * 1.6), (int) (y1 - ny * spacing * 1.6),
                    (int) (x2 - nx * spacing * 1.6), (int) (y2 - ny * spacing * 1.6));
        }
    }

    /** Bresenham-ish thin line via 1px fills — good enough for a small diagram canvas. */
    private static void drawLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        int x = x1, y = y1;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, 0xFFCCCCCC);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x += sx; }
            if (e2 <= dx) { err += dx; y += sy; }
        }
    }

    /** Finds the atom whose drawn circle contains the given canvas-relative point, or null. */
    public static Atom atomAt(Molecule molecule, int localX, int localY) {
        for (Atom a : molecule.atoms()) {
            double dx = localX - a.x(), dy = localY - a.y();
            if (dx * dx + dy * dy <= (ATOM_RADIUS + 2) * (ATOM_RADIUS + 2)) return a;
        }
        return null;
    }

    public static int elementColor(String element) {
        return switch (element) {
            case "C" -> 0xFFAAAAAA;
            case "H" -> 0xFFFFFFFF;
            case "O" -> 0xFFFF5555;
            case "N" -> 0xFF5599FF;
            case "S" -> 0xFFFFDD55;
            case "P" -> 0xFFFF9955;
            case "F" -> 0xFF55FFAA;
            case "Cl" -> 0xFF55DD55;
            case "Br" -> 0xFFAA6633;
            case "I" -> 0xFFAA55DD;
            default -> 0xFFFFFFFF;
        };
    }
}
