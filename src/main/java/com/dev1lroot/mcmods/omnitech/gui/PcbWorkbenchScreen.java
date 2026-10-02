/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.PcbWorkbenchBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.pcb.PcbRenderer;
import com.dev1lroot.mcmods.omnitech.network.PcbStationPacket;
import com.dev1lroot.mcmods.omnitech.pcb.CircuitGraph;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

import static com.dev1lroot.mcmods.omnitech.gui.PcbWorkbenchMenu.*;
import static com.dev1lroot.mcmods.omnitech.gui.pcb.PcbRenderer.CELL;

/**
 * PCB Workbench: draw the board outline, copper traces and pads, label terminals,
 * then print the drawing as a blueprint.
 *
 * <p>Left mouse applies the selected tool, right mouse removes (copper, pad label or
 * board area). Drags paint 4-connected strokes. Keys T / P / L / B pick tools.
 */
public class PcbWorkbenchScreen extends AbstractContainerScreen<PcbWorkbenchMenu> {

    private enum Tool { TRACE, PAD, LABEL, BOARD }

    private Tool tool = Tool.TRACE;
    private final Button[] toolButtons = new Button[Tool.values().length];
    private EditBox nameBox;

    private int strokeButton = -1;
    private int lastX = -1, lastY = -1;
    private boolean dirty;

    public PcbWorkbenchScreen(PcbWorkbenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelY = 9999;
    }

    @Override
    protected void init() {
        super.init();
        for (Tool t : Tool.values()) {
            String key = "gui.omnitech.pcb.tool." + t.name().toLowerCase();
            toolButtons[t.ordinal()] = addRenderableWidget(Button.builder(Component.translatable(key), b -> selectTool(t))
                    .bounds(leftPos + 8, topPos + 18 + t.ordinal() * 16, 54, 14)
                    .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                    .build());
        }
        selectTool(tool);

        addRenderableWidget(Button.builder(Component.literal("-"), b -> resizeBoard(-1, 0)).bounds(leftPos + 36, topPos + 84, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> resizeBoard(1, 0)).bounds(leftPos + 50, topPos + 84, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> resizeBoard(0, -1)).bounds(leftPos + 36, topPos + 98, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> resizeBoard(0, 1)).bounds(leftPos + 50, topPos + 98, 12, 12).build());

        nameBox = new EditBox(font, leftPos + CANVAS_X, topPos + 134, 96, 14, Component.translatable("gui.omnitech.pcb.name"));
        nameBox.setMaxLength(PcbDesign.MAX_NAME);
        nameBox.setHint(Component.translatable("gui.omnitech.pcb.name"));
        nameBox.setValue(menu.getDesign().name());
        nameBox.setResponder(s -> {
            if (!s.equals(menu.getDesign().name())) {
                menu.setDesign(menu.getDesign().withName(s));
                dirty = true;
            }
        });
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.omnitech.pcb.load"), b -> load())
                .bounds(leftPos + CANVAS_X + 100, topPos + 134, 32, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.omnitech.pcb.load.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.omnitech.pcb.print"), b -> send(PcbStationPacket.WORKBENCH_PRINT))
                .bounds(leftPos + CANVAS_X + 136, topPos + 134, 32, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.omnitech.pcb.print.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.omnitech.pcb.clear"), b -> clear())
                .bounds(leftPos + 8, topPos + 135, 54, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.omnitech.pcb.clear.tooltip"))).build());
    }

    private void selectTool(Tool t) {
        tool = t;
        for (Tool o : Tool.values()) toolButtons[o.ordinal()].active = o != t;
    }

    private void resizeBoard(int dw, int dh) {
        PcbDesign d = menu.getDesign();
        menu.setDesign(d.withSize(d.width() + dw, d.height() + dh));
        send(PcbStationPacket.WORKBENCH_SAVE);
    }

    private void clear() {
        PcbDesign d = menu.getDesign();
        menu.setDesign(PcbDesign.blank(d.width(), d.height()).withName(d.name()));
        send(PcbStationPacket.WORKBENCH_SAVE);
    }

    /** Copies the drawing from the blueprint in the source slot into the editor. */
    private void load() {
        PcbDesign d = menu.getContainer().getItem(PcbWorkbenchBlockEntity.SLOT_SOURCE)
                .get(OmniTechDataComponents.PCB_DESIGN.get());
        if (d == null) return;
        menu.setDesign(d);
        nameBox.setValue(d.name());
        send(PcbStationPacket.WORKBENCH_SAVE);
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(PcbStationPacket.workbench(menu.getPos(), action, menu.getDesign()));
        dirty = false;
    }

    @Override
    public void removed() {
        if (dirty) send(PcbStationPacket.WORKBENCH_SAVE);
        super.removed();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);
        PcbRenderer.panel(g, leftPos, topPos, W, H);
        for (int i = 0; i < 3; i++) PcbRenderer.slotFrame(g, leftPos + 8 + i * 18, topPos + SLOTS_Y);
        PcbRenderer.playerSlots(g, leftPos + INV_X, topPos + INV_Y);

        PcbDesign d = menu.getDesign();
        int cx = leftPos + CANVAS_X, cy = topPos + CANVAS_Y;
        int[] hover = PcbRenderer.cellAt(mouseX, mouseY, cx, cy);
        PcbRenderer.drawBoard(g, font, d, cx, cy, hover == null ? null : netCells(d, hover));

        if (hover != null && d.inBounds(hover[0], hover[1])) {
            g.outline(cx + hover[0] * CELL, cy + hover[1] * CELL, CELL, CELL, 0xA0FFFFFF);
        }
    }

    /** Cells of the copper island under {@code cell}, for highlighting. */
    private static boolean[] netCells(PcbDesign d, int[] cell) {
        if (!d.isCopper(cell[0], cell[1])) return null;
        int[] nets = CircuitGraph.of(d, List.of()).netOfCell();
        int net = nets[PcbDesign.index(cell[0], cell[1])];
        boolean[] lit = new boolean[PcbDesign.CELLS];
        for (int i = 0; i < lit.length; i++) lit[i] = nets[i] == net;
        return lit;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        PcbDesign d = menu.getDesign();
        g.text(font, "W " + d.width(), 8, 86, 0xFF404040, false);
        g.text(font, "H " + d.height(), 8, 100, 0xFF404040, false);

        int nets = 0;
        for (int n : CircuitGraph.of(d, List.of()).netOfCell()) nets = Math.max(nets, n + 1);
        Component info = Component.translatable("gui.omnitech.pcb.info", d.padCount(), nets, d.labels().size());
        g.text(font, info, CANVAS_X + PcbDesign.MAX_W * CELL - font.width(info), titleLabelY, 0xFF606060, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null || !menu.getCarried().isEmpty()) return;
        int[] c = PcbRenderer.cellAt(mouseX, mouseY, leftPos + CANVAS_X, topPos + CANVAS_Y);
        if (c == null) return;
        String label = menu.getDesign().label(c[0], c[1]);
        if (label != null) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.omnitech.pcb.terminal", label), mouseX, mouseY);
        } else if (tool == Tool.LABEL && menu.getDesign().isPad(c[0], c[1])) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.omnitech.pcb.tool.label.tooltip"), mouseX, mouseY);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int[] c = PcbRenderer.cellAt(event.x(), event.y(), leftPos + CANVAS_X, topPos + CANVAS_Y);
        if (c != null && menu.getCarried().isEmpty()
                && (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
            nameBox.setFocused(false);
            strokeButton = event.button();
            lastX = c[0];
            lastY = c[1];
            apply(c[0], c[1], true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (strokeButton >= 0) {
            int x = Math.max(0, Math.min(PcbDesign.MAX_W - 1, (int) Math.floor((event.x() - leftPos - CANVAS_X) / CELL)));
            int y = Math.max(0, Math.min(PcbDesign.MAX_H - 1, (int) Math.floor((event.y() - topPos - CANVAS_Y) / CELL)));
            // one orthogonal step at a time so fast strokes stay 4-connected
            while (lastX != x || lastY != y) {
                if (Math.abs(x - lastX) >= Math.abs(y - lastY)) lastX += Integer.signum(x - lastX);
                else lastY += Integer.signum(y - lastY);
                apply(lastX, lastY, false);
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (strokeButton >= 0) {
            strokeButton = -1;
            if (dirty) send(PcbStationPacket.WORKBENCH_SAVE);
            return true;
        }
        return super.mouseReleased(event);
    }

    private void apply(int x, int y, boolean click) {
        PcbDesign d = menu.getDesign();
        if (!d.inBounds(x, y)) return;
        boolean add = strokeButton == InputConstants.MOUSE_BUTTON_LEFT;
        int i = PcbDesign.index(x, y);
        byte cell = d.cells()[i];
        PcbDesign next = d;
        switch (tool) {
            case TRACE, PAD -> {
                if (add && d.isBoard(x, y)) next = d.withCell(x, y, (byte) (cell | (tool == Tool.TRACE ? PcbDesign.TRACE : PcbDesign.PAD)));
                else if (!add) next = d.withCell(x, y, (byte) (cell & PcbDesign.BOARD));
            }
            case BOARD -> next = d.withCell(x, y, add ? (byte) (cell | PcbDesign.BOARD) : 0);
            case LABEL -> {
                if (!click || !d.isPad(x, y)) return;
                if (!add) next = d.withLabel(x, y, null);
                else {
                    // cycle through the terminal names, ending back at "no label"
                    String cur = d.label(x, y);
                    int idx = cur == null ? -1 : PcbDesign.LABELS.indexOf(cur);
                    next = d.withLabel(x, y, idx + 1 < PcbDesign.LABELS.size() ? PcbDesign.LABELS.get(idx + 1) : null);
                }
            }
        }
        if (!next.equals(d)) {
            menu.setDesign(next);
            dirty = true;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (nameBox.isFocused()) {
            if (event.isEscape()) { nameBox.setFocused(false); return true; }
            return nameBox.keyPressed(event) || nameBox.canConsumeInput() || super.keyPressed(event);
        }
        switch (event.key()) {
            case InputConstants.KEY_T -> { selectTool(Tool.TRACE); return true; }
            case InputConstants.KEY_P -> { selectTool(Tool.PAD); return true; }
            case InputConstants.KEY_L -> { selectTool(Tool.LABEL); return true; }
            case InputConstants.KEY_B -> { selectTool(Tool.BOARD); return true; }
            default -> { return super.keyPressed(event); }
        }
    }
}
