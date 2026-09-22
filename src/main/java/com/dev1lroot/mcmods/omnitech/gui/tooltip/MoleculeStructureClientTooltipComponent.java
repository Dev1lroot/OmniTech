/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui.tooltip;

import com.dev1lroot.mcmods.omnitech.chemistry.Atom;
import com.dev1lroot.mcmods.omnitech.chemistry.Molecule;
import com.dev1lroot.mcmods.omnitech.gui.MoleculeRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * Renders a molecule's laid-out structure as a read-only skeletal diagram inside an item
 * tooltip — the same {@link MoleculeRenderer} drawing {@code FormulaViewerScreen} uses, just
 * sized to fit the tooltip box instead of a full screen.
 */
public class MoleculeStructureClientTooltipComponent implements ClientTooltipComponent {

    private static final int PADDING = 10;
    private static final int BACKDROP_COLOR = 0xFF100010;

    private final Molecule molecule;
    private final int minX, minY, width, height;

    public MoleculeStructureClientTooltipComponent(MoleculeStructureTooltipData data) {
        this.molecule = data.molecule();

        float lox = 0f, loy = 0f, hix = 0f, hiy = 0f;
        boolean first = true;
        for (Atom a : molecule.atoms()) {
            if (first) { lox = hix = a.x(); loy = hiy = a.y(); first = false; continue; }
            lox = Math.min(lox, a.x()); hix = Math.max(hix, a.x());
            loy = Math.min(loy, a.y()); hiy = Math.max(hiy, a.y());
        }

        this.minX = Math.round(lox) - PADDING;
        this.minY = Math.round(loy) - PADDING;
        this.width = Math.round(hix - lox) + PADDING * 2;
        this.height = Math.round(hiy - loy) + PADDING * 2;
    }

    @Override
    public int getWidth(Font font) { return Math.max(1, width); }

    @Override
    public int getHeight(Font font) { return Math.max(1, height); }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        graphics.fill(x, y, x + w, y + h, BACKDROP_COLOR);
        MoleculeRenderer.render(graphics, font, molecule, x - minX, y - minY, -1, true, BACKDROP_COLOR);
    }
}
