/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.chemistry.IupacNamer;
import com.dev1lroot.mcmods.omnitech.chemistry.Molecule;
import com.dev1lroot.mcmods.omnitech.chemistry.SmilesGenerator;
import com.dev1lroot.mcmods.omnitech.chemistry.SmilesParser;
import com.dev1lroot.mcmods.omnitech.chemistry.StructureLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Read-only structure diagram viewer — opened by right-clicking a finished Chemical Formula
 * item, or via {@code /formula <code>}. Renders the real-skeletal-formula style
 * ({@link MoleculeRenderer}'s {@code readOnly} mode) and shows both the molecular formula and
 * the full structural code the picture was regenerated from.
 */
public class FormulaViewerScreen extends Screen {

    private static final int W = 240;
    private static final int MIN_H = 210;
    private static final int CANVAS_BG = 0xFF1A1A1A;

    private int h = MIN_H;

    private final Molecule molecule;
    private final String chemicalName;
    private final String structuralCode;
    private List<FormattedCharSequence> codeLines;

    public FormulaViewerScreen(Molecule molecule, String chemicalName) {
        super(Component.literal(chemicalName));
        this.molecule = molecule;
        this.chemicalName = chemicalName;
        this.structuralCode = SmilesGenerator.generate(molecule);
    }

    /** Parses a structural code directly (e.g. from {@code /formula}) and lays it out fresh. */
    public static FormulaViewerScreen fromCode(String code) {
        Molecule parsed = SmilesParser.parse(code);
        Molecule laidOut = StructureLayout.layout(parsed);
        return new FormulaViewerScreen(laidOut, IupacNamer.name(laidOut));
    }

    @Override
    protected void init() {
        super.init();
        this.codeLines = this.font.split(Component.literal(structuralCode), W - 20);
        // MIN_H already fits ~2 lines of code below the canvas; grow the window for longer codes
        // rather than clipping them.
        int codeBlockHeight = Math.max(codeLines.size(), 1) * this.font.lineHeight;
        this.h = MIN_H + Math.max(0, codeBlockHeight - 20);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int lx = (this.width - W) / 2;
        int ty = (this.height - h) / 2;
        graphics.fill(lx, ty, lx + W, ty + h, 0xFFC6C6C6);
        graphics.fill(lx + 2, ty + 20, lx + W - 2, ty + h - 40, CANVAS_BG);

        int titleX = lx + (W - this.font.width(this.title)) / 2;
        graphics.text(this.font, this.title, titleX, ty + 6, 0xFFFFFFFF, false);

        int canvasCenterX = lx + W / 2;
        int canvasCenterY = ty + 20 + (h - 60) / 2;
        MoleculeRenderer.render(graphics, this.font, molecule, canvasCenterX, canvasCenterY, -1, true, CANVAS_BG);

        String formula = molecule.molecularFormula();
        graphics.text(this.font, formula, lx + 10, ty + h - 36, 0xFF44CCFF, false);

        int codeY = ty + h - 26;
        for (FormattedCharSequence line : codeLines) {
            graphics.text(this.font, line, lx + 10, codeY, 0xFF88DD88, false);
            codeY += this.font.lineHeight;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { this.onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
