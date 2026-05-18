/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.logic.logic_machine.LogicMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class LogicMachineScreen extends AbstractContainerScreen<LogicMachineMenu> {

    private static final int W = 240;
    private static final int H = 126;

    // Slot frame colors
    private static final int SLOT_BORDER = 0xFF555555;
    private static final int SLOT_BG     = 0xFF333333;

    private Button runBtn, stopBtn, resetBtn;

    public LogicMachineScreen(LogicMachineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        this.inventoryLabelY = 9999;
        this.titleLabelY     = 4;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (W - this.font.width(this.title)) / 2;

        int bY = this.topPos + 14;
        runBtn   = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.run"),
                b -> clickBtn(LogicMachineBlockEntity.BTN_RUN))
                .bounds(this.leftPos + 10, bY, 60, 12).build());
        stopBtn  = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.stop"),
                b -> clickBtn(LogicMachineBlockEntity.BTN_STOP))
                .bounds(this.leftPos + 74, bY, 60, 12).build());
        resetBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.reset"),
                b -> clickBtn(LogicMachineBlockEntity.BTN_RESET))
                .bounds(this.leftPos + 138, bY, 60, 12).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean running = menu.isRunning();
        runBtn.active   = !running;
        stopBtn.active  = running;
        resetBtn.active = true;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        super.extractBackground(g, mouseX, mouseY, pt);

        // Window background
        g.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFF2D2D2D);
        g.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + W - 1, this.topPos + H - 1, 0xFF1A1A1A);

        // CPU slot frame (menu slot 0 is at x=44, y=36)
        int cpuX = this.leftPos + 43;
        int cpuY = this.topPos  + 35;
        g.fill(cpuX, cpuY, cpuX + 18, cpuY + 18, SLOT_BORDER);
        g.fill(cpuX + 1, cpuY + 1, cpuX + 17, cpuY + 17, SLOT_BG);

        // ROM slot frame (menu slot 1 is at x=100, y=36)
        int romX = this.leftPos + 99;
        int romY = this.topPos  + 35;
        g.fill(romX, romY, romX + 18, romY + 18, SLOT_BORDER);
        g.fill(romX + 1, romY + 1, romX + 17, romY + 17, SLOT_BG);

        // Player inventory background (3 rows + hotbar, matching menu slot positions)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = this.leftPos + 39 + col * 18;
                int sy = this.topPos  + 58 + row * 18;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BORDER);
                g.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
            }
        }
        // Hotbar (with slightly different shade to match vanilla convention)
        for (int col = 0; col < 9; col++) {
            int sx = this.leftPos + 39 + col * 18;
            int sy = this.topPos  + 102;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF666666);
            g.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Status indicator (top-right corner)
        boolean running = menu.isRunning();
        String status = running ? "RUNNING" : "STOPPED";
        int color = running ? 0xFF44FF44 : 0xFF888888;
        g.text(this.font, Component.literal(status),
                W - this.font.width(status) - 4, 4, color, false);

        // Slot labels below the buttons
        g.text(this.font, Component.translatable("gui.omnitech.cpu_slot"),
                44, 29, 0xFF999999, false);
        g.text(this.font, Component.translatable("gui.omnitech.rom_slot"),
                100, 29, 0xFF999999, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clickBtn(int id) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
}
