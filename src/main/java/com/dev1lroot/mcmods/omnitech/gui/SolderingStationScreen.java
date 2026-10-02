/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.SolderingStationBlockEntity;
import com.dev1lroot.mcmods.omnitech.gui.pcb.PcbRenderer;
import com.dev1lroot.mcmods.omnitech.network.PcbStationPacket;
import com.dev1lroot.mcmods.omnitech.pcb.CircuitGraph;
import com.dev1lroot.mcmods.omnitech.pcb.PartKey;
import com.dev1lroot.mcmods.omnitech.pcb.PartSpec;
import com.dev1lroot.mcmods.omnitech.pcb.PcbDesign;
import com.dev1lroot.mcmods.omnitech.pcb.PlacedPart;
import com.dev1lroot.mcmods.omnitech.pcb.mc.TestBenchLoader;
import com.dev1lroot.mcmods.omnitech.pcb.sim.TestBenchRunner;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.dev1lroot.mcmods.omnitech.blocks.electrical.pcb.SolderingStationBlockEntity.*;
import static com.dev1lroot.mcmods.omnitech.gui.SolderingStationMenu.*;
import static com.dev1lroot.mcmods.omnitech.gui.pcb.PcbRenderer.CELL;

/**
 * Soldering Station: pick a part from the palette, rotate it (R or mouse wheel) and
 * click its centre onto the board — every leg must land in a pad. Right click removes
 * a part. "Test" simulates the board in each test bench and shows the scope trace.
 */
public class SolderingStationScreen extends AbstractContainerScreen<SolderingStationMenu> {

    private static final int PALETTE_Y = 134, PALETTE_PITCH = 14;
    private static final int PANEL_W = PcbDesign.MAX_W * CELL, PANEL_H = PcbDesign.MAX_H * CELL;
    private static final int SCOPE_Y = 14, SCOPE_H = 50;

    /** Fixed parts, then one entry per distinct painted resistor in the parts slots. */
    private final List<PartKey> palette = new ArrayList<>();
    private static final int PALETTE_MAX = 12;
    private PartKey selectedKey;
    private int rot = 0;

    private Button testButton;
    private Button referenceButton;
    private boolean showResults;
    private int benchIndex;
    private CompletableFuture<List<TestBenchRunner.Outcome>> pending;
    private List<TestBenchRunner.Outcome> results;
    private int testedKey;

    public SolderingStationScreen(SolderingStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelY = 9999;
    }

    @Override
    protected void init() {
        super.init();
        refreshPalette();
        addRenderableWidget(Button.builder(Component.translatable("gui.omnitech.pcb.rotate"), b -> rot = (rot + 1) & 3)
                .bounds(leftPos + CANVAS_X, topPos + 150, 40, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.omnitech.pcb.rotate.tooltip"))).build());
        testButton = addRenderableWidget(Button.builder(Component.translatable("gui.omnitech.pcb.test"), b -> toggleTest())
                .bounds(leftPos + CANVAS_X + 44, topPos + 150, 46, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.omnitech.pcb.test.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.omnitech.pcb.solder"), b -> ClientPacketDistributor.sendToServer(
                        PcbStationPacket.soldering(menu.getPos(), Minecraft.getInstance().hasShiftDown()
                                ? PcbStationPacket.SOLDER_BATCH : PcbStationPacket.SOLDER_ONE, plan())))
                .bounds(leftPos + CANVAS_X + 94, topPos + 150, 48, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.omnitech.pcb.solder.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.omnitech.pcb.clear"), b -> setPlan(List.of()))
                .bounds(leftPos + 8, topPos + 150, 30, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.omnitech.pcb.unplace.tooltip"))).build());
        referenceButton = addRenderableWidget(Button.builder(Component.translatable("gui.omnitech.pcb.reference"), b -> {
                    PcbDesign d = design();
                    if (d != null) setPlan(referencePlan(d, menu.getContainer().getItem(SLOT_BOARD)));
                })
                .bounds(leftPos + 40, topPos + 150, 22, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.omnitech.pcb.reference.tooltip"))).build());
    }

    // ── Board / plan access ───────────────────────────────────────────────────

    private PcbDesign design() {
        return menu.getContainer().getItem(SLOT_BOARD).get(OmniTechDataComponents.PCB_DESIGN.get());
    }

    private List<PlacedPart> plan() {
        PcbDesign d = design();
        if (d == null) return List.of();
        List<PlacedPart> stored = menu.getStoredPlan(d.geometryHash());
        return stored != null ? stored : referencePlan(d, menu.getContainer().getItem(SLOT_BOARD));
    }

    private void setPlan(List<PlacedPart> parts) {
        PcbDesign d = design();
        if (d == null) return;
        menu.setPlan(d.geometryHash(), parts);
        ClientPacketDistributor.sendToServer(PcbStationPacket.soldering(menu.getPos(), PcbStationPacket.SOLDER_PLAN, parts));
    }

    private void toggleTest() {
        if (showResults) {
            showResults = false;
            return;
        }
        PcbDesign d = design();
        if (d == null) return;
        showResults = true;
        List<PlacedPart> parts = plan();
        int key = 31 * d.hashCode() + parts.hashCode();
        if (results == null || key != testedKey) {
            results = null;
            testedKey = key;
            pending = CompletableFuture.supplyAsync(() -> TestBenchLoader.test(d, parts));
        }
    }

    /** Rebuilds the palette; resistor entries follow what is loaded in the parts slots. */
    private void refreshPalette() {
        palette.clear();
        for (PartSpec spec : PartSpec.all().values()) {
            if (spec.kind() != PartSpec.Kind.RESISTOR) palette.add(new PartKey(spec.itemId(), List.of()));
        }
        for (int i = PARTS_START; i < SLOT_OUTPUT && palette.size() < PALETTE_MAX; i++) {
            PartKey k = keyOf(menu.getContainer().getItem(i));
            if (k != null && k.itemId().equals(PartSpec.RESISTOR) && !palette.contains(k)) palette.add(k);
        }
        if (selectedKey == null || !palette.contains(selectedKey)) selectedKey = palette.getFirst();
    }

    private PartKey selected() {
        if (palette.isEmpty()) refreshPalette();
        return selectedKey;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshPalette();
        if (pending != null && pending.isDone()) {
            results = pending.getNow(List.of());
            pending = null;
            // jump to the bench that passed, else the first one the board is wired for
            benchIndex = 0;
            for (int i = 0; i < results.size(); i++) if (results.get(i).passed()) { benchIndex = i; break; }
            if (TestBenchRunner.firstPass(results) == null) {
                for (int i = 0; i < results.size(); i++) if (results.get(i).applicable()) { benchIndex = i; break; }
            }
        }
        PcbDesign d = design();
        if (d == null) showResults = false;
        else if (showResults && results != null && 31 * d.hashCode() + plan().hashCode() != testedKey) showResults = false;
        testButton.setMessage(Component.translatable(showResults ? "gui.omnitech.pcb.board" : "gui.omnitech.pcb.test"));
        testButton.active = d != null;
        referenceButton.active = d != null && !referencePlan(d, menu.getContainer().getItem(SLOT_BOARD)).isEmpty();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);
        PcbRenderer.panel(g, leftPos, topPos, W, H);
        PcbRenderer.slotFrame(g, leftPos + 8, topPos + 18);
        PcbRenderer.slotFrame(g, leftPos + 26, topPos + 18);
        for (int i = 0; i < PARTS_SLOTS; i++) PcbRenderer.slotFrame(g, leftPos + PARTS_X + (i % 3) * 18, topPos + PARTS_Y + (i / 3) * 18);
        g.fill(leftPos + OUTPUT_X - 3, topPos + OUTPUT_Y - 3, leftPos + OUTPUT_X + 19, topPos + OUTPUT_Y + 19, 0xFF8B8B8B);
        PcbRenderer.slotFrame(g, leftPos + OUTPUT_X, topPos + OUTPUT_Y);
        PcbRenderer.playerSlots(g, leftPos + INV_X, topPos + INV_Y);

        int cx = leftPos + CANVAS_X, cy = topPos + CANVAS_Y;
        PcbDesign d = design();
        if (d == null) {
            g.fill(cx - 1, cy - 1, cx + PANEL_W + 1, cy + PANEL_H + 1, 0xFF373737);
            g.fill(cx, cy, cx + PANEL_W, cy + PANEL_H, 0xFF2B2B2B);
        } else if (showResults) {
            drawResults(g, cx, cy, mouseX, mouseY);
        } else {
            drawBoard(g, d, cx, cy, mouseX, mouseY);
        }
        drawPalette(g, mouseX, mouseY);
    }

    private void drawBoard(GuiGraphicsExtractor g, PcbDesign d, int cx, int cy, int mouseX, int mouseY) {
        List<PlacedPart> parts = plan();
        int[] hover = PcbRenderer.cellAt(mouseX, mouseY, cx, cy);
        boolean[] lit = null;
        if (hover != null && d.isCopper(hover[0], hover[1])) {
            int[] nets = CircuitGraph.of(d, List.of()).netOfCell();
            int net = nets[PcbDesign.index(hover[0], hover[1])];
            lit = new boolean[PcbDesign.CELLS];
            for (int i = 0; i < lit.length; i++) lit[i] = nets[i] == net;
        }
        PcbRenderer.drawBoard(g, font, d, cx, cy, lit);
        for (PlacedPart p : parts) PcbRenderer.drawPart(g, p, cx, cy, 0);

        if (hover != null && PlacedPart.partAt(parts, hover[0], hover[1]) < 0) {
            PlacedPart ghost = selected().at(hover[0], hover[1], rot);
            boolean ok = PlacedPart.canPlace(d, parts, ghost);
            PcbRenderer.drawPart(g, ghost, cx, cy, ok ? 0x5530FF30 : 0x55FF3030);
        }
    }

    private void drawPalette(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (int i = 0; i < palette.size(); i++) {
            int x = leftPos + CANVAS_X + i * PALETTE_PITCH, y = topPos + PALETTE_Y;
            if (palette.get(i).equals(selectedKey)) g.fill(x - 1, y - 1, x + 13, y + 13, 0xFF3A7A3A);
            else if (mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12) g.fill(x - 1, y - 1, x + 13, y + 13, 0xFF9A9A9A);
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(0.75f, 0.75f);
            g.item(stackOf(palette.get(i)), 0, 0);
            g.pose().popMatrix();
        }
    }

    private void drawResults(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY) {
        g.fill(x0 - 1, y0 - 1, x0 + PANEL_W + 1, y0 + PANEL_H + 1, 0xFF373737);
        g.fill(x0, y0, x0 + PANEL_W, y0 + PANEL_H, 0xFF0E1512);
        if (results == null) {
            g.centeredText(font, Component.translatable("gui.omnitech.pcb.simulating"), x0 + PANEL_W / 2, y0 + PANEL_H / 2 - 4, 0xFF8FFF8F);
            return;
        }

        // Tabs, one per bench
        int tabW = PANEL_W / Math.max(1, results.size());
        for (int i = 0; i < results.size(); i++) {
            TestBenchRunner.Outcome o = results.get(i);
            int tx = x0 + i * tabW;
            int bg = i == benchIndex ? 0xFF24402C : 0xFF16201A;
            g.fill(tx + 1, y0 + 1, tx + tabW - 1, y0 + 12, bg);
            String mark = !o.applicable() ? "–" : o.passed() ? "✔" : "✘";
            int col = !o.applicable() ? 0xFF7A7A7A : o.passed() ? 0xFF55FF55 : 0xFFFF5555;
            Component name = Component.literal(mark + " ").append(Component.translatable("circuit_test.omnitech." + o.bench().id()));
            g.pose().pushMatrix();
            g.pose().translate(tx + 3, y0 + 3);
            g.pose().scale(0.7f, 0.7f);
            g.text(font, name, 0, 0, col, false);
            g.pose().popMatrix();
        }
        if (results.isEmpty()) return;
        TestBenchRunner.Outcome o = results.get(Math.min(benchIndex, results.size() - 1));

        // Oscilloscope
        int sx = x0 + 2, sy = y0 + SCOPE_Y, sw = PANEL_W - 4;
        g.fill(sx, sy, sx + sw, sy + SCOPE_H, 0xFF06100A);
        for (int i = 1; i < 4; i++) g.fill(sx, sy + i * SCOPE_H / 4, sx + sw, sy + i * SCOPE_H / 4 + 1, 0xFF173322);
        for (int i = 1; i < 8; i++) g.fill(sx + i * sw / 8, sy, sx + i * sw / 8 + 1, sy + SCOPE_H, 0xFF173322);
        float[] tr = o.trace();
        if (tr.length > 1) {
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (float v : tr) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            lo = Math.min(lo, 0);
            hi = Math.max(hi, lo + 1);
            int prevY = -1;
            for (int i = 0; i < tr.length; i++) {
                int px = sx + i * (sw - 1) / (tr.length - 1);
                int py = sy + SCOPE_H - 2 - Math.round((tr[i] - lo) / (hi - lo) * (SCOPE_H - 4));
                int a = prevY < 0 ? py : prevY;
                g.fill(px, Math.min(a, py), px + 1, Math.max(a, py) + 1, 0xFF5CFF7A);
                prevY = py;
            }
            PcbRenderer.smallText(g, font, String.format("%.1f V", hi), sx + 14, sy + 2, 0xFF5C9A6A, 0.6f);
            PcbRenderer.smallText(g, font, String.format("%.1f V", lo), sx + 14, sy + SCOPE_H - 7, 0xFF5C9A6A, 0.6f);
            PcbRenderer.smallText(g, font, String.format("%.2f s", o.bench().duration()), sx + sw - 14, sy + SCOPE_H - 7, 0xFF5C9A6A, 0.6f);
        } else {
            g.centeredText(font, Component.translatable("gui.omnitech.pcb.no_signal"), sx + sw / 2, sy + SCOPE_H / 2 - 4, 0xFF5C9A6A);
        }

        // Findings
        int ly = sy + SCOPE_H + 3;
        for (TestBenchRunner.Note n : o.notes()) {
            if (ly > y0 + PANEL_H - 6) break;
            g.pose().pushMatrix();
            g.pose().translate(x0 + 3, ly);
            g.pose().scale(0.6f, 0.6f);
            g.text(font, note(n), 0, 0, n.ok() ? 0xFF7FE08F : 0xFFFF7A7A, false);
            g.pose().popMatrix();
            ly += 6;
        }
    }

    private static Component note(TestBenchRunner.Note n) {
        return Component.literal(n.ok() ? "✔ " : "✘ ").append(Component.translatable(n.key(), (Object[]) n.args()));
    }

    private static ItemStack stackOf(PartKey k) {
        return SolderingStationBlockEntity.stackOf(k, 1);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        PcbDesign d = design();
        if (d == null) {
            g.text(font, Component.translatable("gui.omnitech.pcb.insert_board"), CANVAS_X + 4, CANVAS_Y + 4, 0xFFB0B0B0, false);
            return;
        }
        if (!d.name().isEmpty()) g.text(font, d.name(), CANVAS_X + PANEL_W - font.width(d.name()), titleLabelY, 0xFF606060, false);

        List<PlacedPart> parts = plan();
        boolean partsOk = true;
        for (Map.Entry<PartKey, Integer> e : billOfMaterials(parts).entrySet()) partsOk &= count(e.getKey()) >= e.getValue();
        int solder = solderNeeded(parts);
        ItemStack tin = menu.getContainer().getItem(SLOT_SOLDER);
        boolean solderOk = tin.is(tinWire()) && tin.getCount() >= solder;
        g.text(font, Component.translatable("gui.omnitech.pcb.parts_count", parts.size()), 8, 98, partsOk ? 0xFF206020 : 0xFF902020, false);
        g.text(font, Component.translatable("gui.omnitech.pcb.solder_count", solder), 8, 110, solderOk ? 0xFF206020 : 0xFF902020, false);
        String rotLabel = "↻ " + rot * 90 + "°";
        g.text(font, rotLabel, 8, 122, 0xFF404040, false);
    }

    private int count(PartKey key) {
        int n = 0;
        for (int i = PARTS_START; i < SLOT_OUTPUT; i++) {
            ItemStack s = menu.getContainer().getItem(i);
            if (matches(key, s)) n += s.getCount();
        }
        return n;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null || !menu.getCarried().isEmpty()) return;

        // Palette
        for (int i = 0; i < palette.size(); i++) {
            int x = leftPos + CANVAS_X + i * PALETTE_PITCH, y = topPos + PALETTE_Y;
            if (mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12) {
                PartKey key = palette.get(i);
                PartSpec s = key.spec().orElseThrow();
                List<Component> lines = new ArrayList<>();
                lines.add(stackOf(key).getHoverName());
                if (!s.valueLabel().isEmpty()) lines.add(Component.literal(s.valueLabel()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.omnitech.pcb.pins", String.join(" ", s.pinNames())).withStyle(ChatFormatting.DARK_GRAY));
                g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                return;
            }
        }

        // Missing parts
        if (mouseX >= leftPos + 8 && mouseX < leftPos + 66 && mouseY >= topPos + 96 && mouseY < topPos + 120) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.omnitech.pcb.bom").withStyle(ChatFormatting.YELLOW));
            for (Map.Entry<PartKey, Integer> e : billOfMaterials(plan()).entrySet()) {
                int have = count(e.getKey());
                boolean ok = have >= e.getValue();
                MutableComponent line = Component.literal((ok ? "✔ " : "✘ ") + have + "/" + e.getValue() + " ")
                        .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
                lines.add(line.append(stackOf(e.getKey()).getHoverName()));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }

        PcbDesign d = design();
        if (d == null || showResults) return;
        int[] c = PcbRenderer.cellAt(mouseX, mouseY, leftPos + CANVAS_X, topPos + CANVAS_Y);
        if (c == null) return;
        List<PlacedPart> parts = plan();
        int idx = PlacedPart.partAt(parts, c[0], c[1]);
        List<Component> lines = new ArrayList<>();
        if (idx >= 0) {
            PlacedPart p = parts.get(idx);
            PartSpec s = p.spec().orElseThrow();
            lines.add(stackOf(PartKey.of(p)).getHoverName());
            if (!s.valueLabel().isEmpty()) lines.add(Component.literal(s.valueLabel()).withStyle(ChatFormatting.GRAY));
            List<int[]> pins = p.pinCells();
            for (int i = 0; i < pins.size(); i++) {
                if (pins.get(i)[0] == c[0] && pins.get(i)[1] == c[1]) {
                    lines.add(Component.translatable("gui.omnitech.pcb.pin." + s.pinNames()[i]).withStyle(ChatFormatting.AQUA));
                }
            }
        }
        String label = d.label(c[0], c[1]);
        if (label != null) lines.add(Component.translatable("gui.omnitech.pcb.terminal", label).withStyle(ChatFormatting.GOLD));
        if (!lines.isEmpty()) g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int px = leftPos + CANVAS_X, py = topPos + PALETTE_Y;
        if (my >= py && my < py + 12 && mx >= px && mx < px + palette.size() * PALETTE_PITCH) {
            selectedKey = palette.get(Math.min(palette.size() - 1, (int) ((mx - px) / PALETTE_PITCH)));
            return true;
        }

        PcbDesign d = design();
        int cx = leftPos + CANVAS_X, cy = topPos + CANVAS_Y;
        if (d != null && showResults && results != null && !results.isEmpty()
                && mx >= cx && mx < cx + PANEL_W && my >= cy && my < cy + 12) {
            benchIndex = Math.min(results.size() - 1, (int) ((mx - cx) / (PANEL_W / results.size())));
            return true;
        }
        int[] c = PcbRenderer.cellAt(mx, my, cx, cy);
        if (d != null && !showResults && c != null && menu.getCarried().isEmpty()) {
            List<PlacedPart> parts = new ArrayList<>(plan());
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                int idx = PlacedPart.partAt(parts, c[0], c[1]);
                if (idx >= 0) {
                    parts.remove(idx);
                    setPlan(parts);
                }
                return true;
            }
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                PlacedPart p = selected().at(c[0], c[1], rot);
                if (PlacedPart.canPlace(d, parts, p)) {
                    parts.add(p);
                    setPlan(parts);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (PcbRenderer.cellAt(x, y, leftPos + CANVAS_X, topPos + CANVAS_Y) != null && !showResults) {
            rot = (rot + (scrollY > 0 ? 1 : 3)) & 3;
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_R) {
            rot = (rot + 1) & 3;
            return true;
        }
        return super.keyPressed(event);
    }
}
