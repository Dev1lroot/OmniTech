/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.chemistry.Atom;
import com.dev1lroot.mcmods.omnitech.chemistry.Bond;
import com.dev1lroot.mcmods.omnitech.chemistry.IupacNamer;
import com.dev1lroot.mcmods.omnitech.chemistry.Molecule;
import com.dev1lroot.mcmods.omnitech.chemistry.StructureLayout;
import com.dev1lroot.mcmods.omnitech.network.PrintFormulaPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structure Table editor: click the palette to pick an element, click empty canvas space to
 * place an atom, click one atom then another to add/cycle a bond between them (single → double
 * → triple → gone), right-click an atom to delete it. Hydrogen is never placed explicitly — see
 * {@link Molecule#implicitH(int)}. The whole sketch lives only in this screen until "Print" sends
 * it to the server as one packet.
 */
public class StructureTableScreen extends Screen {

    private static final int W = 320;
    private static final int H = 240;
    private static final String[] ELEMENTS = { "C", "N", "O", "S", "P", "F", "Cl", "Br", "I" };

    private final List<Atom> atoms = new ArrayList<>();
    private final List<Bond> bonds = new ArrayList<>();
    private int nextId = 0;
    private String selectedElement = "C";
    private int pendingBondFrom = -1;

    private int canvasX, canvasY, canvasW, canvasH;
    private EditBox nameBox;
    private boolean nameAutoTrack = true;
    private String lastAutoName = "";
    private String statusMessage = "";

    public StructureTableScreen() {
        super(Component.literal("Structure Table"));
    }

    @Override
    protected void init() {
        int lx = (this.width - W) / 2;
        int ty = (this.height - H) / 2;

        // canvasX/Y is both the top-left corner of the visible canvas rect AND the coordinate
        // origin atoms are placed/rendered relative to (Atom.x()/y() are offsets from here).
        canvasX = lx + 4;
        canvasY = ty + 22;
        canvasW = W - 8;
        canvasH = 130;

        for (int i = 0; i < ELEMENTS.length; i++) {
            String el = ELEMENTS[i];
            int bw = (W - 20) / ELEMENTS.length;
            addRenderableWidget(Button.builder(Component.literal(el),
                    b -> this.selectedElement = el).bounds(lx + 10 + i * bw, ty + 2, bw - 2, 16).build());
        }

        this.nameBox = new EditBox(this.font, lx + 10, ty + H - 40, W - 20, 14, Component.literal("Name"));
        this.nameBox.setMaxLength(64);
        this.nameBox.setResponder(this::onNameTyped);
        addRenderableWidget(this.nameBox);

        addRenderableWidget(Button.builder(Component.literal("Print (1 paper)"), b -> doPrint())
                .bounds(lx + 10, ty + H - 20, 100, 16).build());
        addRenderableWidget(Button.builder(Component.literal("As Compound"), b -> doPrintCompound())
                .bounds(lx + 116, ty + H - 20, 74, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> clearAll())
                .bounds(lx + 196, ty + H - 20, 50, 16).build());

        refreshAutoName();
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    private void onNameTyped(String text) {
        // setValue() below fires this same responder for our OWN programmatic updates, not just
        // real user edits — ignore the echo (lastAutoName is set before setValue() is called, so
        // by the time this callback runs for our own change, text already equals it).
        if (text.equals(lastAutoName)) return;
        nameAutoTrack = text.isBlank();
    }

    private void refreshAutoName() {
        Molecule m = currentMolecule();
        String auto = IupacNamer.name(m);
        if (nameAutoTrack) {
            lastAutoName = auto;
            nameBox.setValue(auto);
        }
    }

    private Molecule currentMolecule() {
        return new Molecule(List.copyOf(atoms), List.copyOf(bonds));
    }

    private void placeAtom(int x, int y) {
        atoms.add(new Atom(nextId++, selectedElement, x, y));
        pendingBondFrom = -1;
        refreshAutoName();
    }

    private void clickAtom(Atom a) {
        if (pendingBondFrom == -1) {
            pendingBondFrom = a.id();
            return;
        }
        if (pendingBondFrom == a.id()) {
            pendingBondFrom = -1;
            return;
        }
        cycleBond(pendingBondFrom, a.id());
        pendingBondFrom = -1;
        refreshAutoName();
    }

    private void cycleBond(int a, int b) {
        Bond existing = null;
        for (Bond bond : bonds) {
            if ((bond.a() == a && bond.b() == b) || (bond.a() == b && bond.b() == a)) { existing = bond; break; }
        }
        if (existing == null) {
            bonds.add(new Bond(a, b, 1));
        } else {
            bonds.remove(existing);
            if (existing.order() < 3) bonds.add(new Bond(a, b, existing.order() + 1));
        }
    }

    private void deleteAtom(int atomId) {
        atoms.removeIf(a -> a.id() == atomId);
        bonds.removeIf(bond -> bond.touches(atomId));
        if (pendingBondFrom == atomId) pendingBondFrom = -1;
        refreshAutoName();
    }

    private void clearAll() {
        atoms.clear();
        bonds.clear();
        pendingBondFrom = -1;
        nextId = 0;
        nameAutoTrack = true;
        refreshAutoName();
    }

    private void doPrint() {
        Molecule sketch = currentMolecule();
        if (sketch.isEmpty()) { statusMessage = "Nothing sketched."; return; }
        // Print the structure, not the sketch: discard wherever the player happened to click and
        // regenerate a clean, deterministic depiction from the connectivity alone (see
        // StructureLayout) — what gets stored/viewed later is that regenerated drawing, not the
        // free-form canvas positions.
        Molecule canonical = StructureLayout.layout(sketch);
        String name = nameBox.getValue().isBlank() ? IupacNamer.name(canonical) : nameBox.getValue();
        ClientPacketDistributor.sendToServer(new PrintFormulaPacket(canonical, name));
        statusMessage = "Sent to print.";
    }

    /**
     * Alternative to {@link #doPrint()}: instead of a paper {@code ChemicalFormulaItem}, sends
     * back a generic {@code chemical_compound_dust} carrying this sketch's SMILES code — a
     * compound the mod has no hand-authored fluid/item for, named/rendered entirely from that
     * SMILES from then on (see {@link com.dev1lroot.mcmods.omnitech.items.ChemicalCompoundDustItem}).
     */
    private void doPrintCompound() {
        Molecule sketch = currentMolecule();
        if (sketch.isEmpty()) { statusMessage = "Nothing sketched."; return; }
        Molecule canonical = StructureLayout.layout(sketch);
        ClientPacketDistributor.sendToServer(new com.dev1lroot.mcmods.omnitech.network.PrintCompoundPacket(canonical));
        statusMessage = "Sent to print.";
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int lx = (this.width - W) / 2;
        int ty = (this.height - H) / 2;
        graphics.fill(lx, ty, lx + W, ty + H, 0xFFC6C6C6);
        graphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF1A1A1A);

        int localX = mouseX - canvasX;
        int localY = mouseY - canvasY;
        Atom hovered = inCanvas(mouseX, mouseY) ? MoleculeRenderer.atomAt(currentMolecule(), localX, localY) : null;

        MoleculeRenderer.render(graphics, this.font, currentMolecule(), canvasX, canvasY, pendingBondFrom);
        if (hovered != null) {
            int hx = canvasX + Math.round(hovered.x());
            int hy = canvasY + Math.round(hovered.y());
            graphics.fill(hx - MoleculeRenderer.ATOM_RADIUS - 2, hy - MoleculeRenderer.ATOM_RADIUS - 2,
                    hx + MoleculeRenderer.ATOM_RADIUS + 2, hy + MoleculeRenderer.ATOM_RADIUS + 2, 0x55FFFFFF);
        }

        Molecule m = currentMolecule();
        String formula = m.isEmpty() ? "—" : m.molecularFormula();
        graphics.text(this.font, "Formula: " + formula, lx + 10, ty + H - 52, 0xFF44CCFF, false);
        if (!statusMessage.isEmpty()) {
            graphics.text(this.font, statusMessage, lx + 130, ty + H - 20, 0xFFAAAAAA, false);
        }
        graphics.text(this.font,
                Component.literal("Click: select/place · click 2nd atom: bond · right-click: delete")
                        .withStyle(ChatFormatting.DARK_GRAY),
                lx + 10, ty + H - 5, 0xFF888888, false);
    }

    private boolean inCanvas(double x, double y) {
        return x >= canvasX && x < canvasX + canvasW && y >= canvasY && y < canvasY + canvasH;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (inCanvas(event.x(), event.y())) {
            int localX = (int) event.x() - canvasX;
            int localY = (int) event.y() - canvasY;
            Atom hit = MoleculeRenderer.atomAt(currentMolecule(), localX, localY);

            // This build numbers mouse buttons 1-indexed (LEFT=1, MIDDLE=2, RIGHT=3), not the
            // usual 0-indexed GLFW scheme — see com.mojang.blaze3d.platform.InputConstants.
            if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
                if (hit != null) deleteAtom(hit.id());
                return true;
            }
            if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                if (hit != null) clickAtom(hit);
                else placeAtom(localX, localY);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) { this.onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
