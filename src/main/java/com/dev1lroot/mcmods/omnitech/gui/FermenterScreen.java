/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.FluidHazardUtil;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiDataContext;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutLoader;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayoutRenderer;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import com.dev1lroot.mcmods.omnitech.util.GuiUtil;
import com.dev1lroot.mcmods.omnitech.util.HudWriter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The fermenter shows its compound solution as one column made of stacked bands, one per
 * component, each in that fluid's own texture — so the player can watch the sugar band shrink
 * and the ethanol band grow. Hovering the column lists every component with its mB and share.
 */
public class FermenterScreen extends AbstractContainerScreen<FermenterMenu> {

    private static final GuiLayout LAYOUT = GuiLayoutLoader.load("fermenter");
    private static final GuiDataContext NO_DATA = new GuiDataContext();

    /** Solution column, in GUI-relative coordinates. */
    private static final int TANK_X = 62, TANK_Y = 17, TANK_W = 52, TANK_H = 52;

    public FermenterScreen(FermenterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, LAYOUT.width, LAYOUT.height);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (LAYOUT.width - this.font.width(this.title)) / 2;
        this.inventoryLabelY = LAYOUT.inventory.label_y;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiLayoutRenderer.renderBackground(graphics, LAYOUT, NO_DATA,
                this.leftPos, this.topPos, LAYOUT.width, LAYOUT.height, mouseX, mouseY);

        int x = leftPos + TANK_X;
        int y = topPos + TANK_Y;
        GuiUtil.renderFrame(graphics, x, y, TANK_W, TANK_H);

        // Bands stack from the bottom; every non-empty component gets at least 1 px so a lone
        // mB of yeast is still visible in a 4000 mB vessel.
        int capacity = Math.max(1, menu.getCapacity());
        int bottom = y + TANK_H;
        for (ResourceStack<FluidResource> c : menu.getSolution().components()) {
            int h = Math.max(1, Math.round((float) TANK_H * c.amount() / capacity));
            int top = Math.max(y, bottom - h);
            if (bottom <= top) break;
            FluidStack stack = c.resource().toStack(c.amount());
            GuiUtil.renderFluidBand(graphics, stack, x, top, TANK_W, bottom - top);
            bottom = top;
        }

        if (mouseX >= x && mouseX < x + TANK_W && mouseY >= y && mouseY < y + TANK_H)
            graphics.fill(x, y, x + TANK_W, y + TANK_H, 0x40FFFFFF);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        HudWriter status = new HudWriter(graphics, this.font, 8, 72, 10, false);
        if (menu.getTotalAmount() <= 0) {
            status.setColor(0xFF888888).write("Empty");
        } else if (menu.getLiveMicrobes() > 0) {
            int running = menu.getActiveReactions();
            status.setColor(0xFF44AA44).write(menu.getLiveMicrobes() + " mB alive")
                    .setColor(0xFF404040).write("  ")
                    .setColor(running > 0 ? 0xFF226622 : 0xFF888888)
                    .write(running + (running == 1 ? " reaction" : " reactions"));
        } else {
            int running = menu.getActiveReactions();
            status.setColor(0xFFAA6622).write("No live culture")
                    .setColor(0xFF404040).write("  ")
                    .setColor(running > 0 ? 0xFF226622 : 0xFF888888)
                    .write(running + (running == 1 ? " reaction" : " reactions"));
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null) return;

        int x = leftPos + TANK_X;
        int y = topPos + TANK_Y;
        if (mouseX < x || mouseX >= x + TANK_W || mouseY < y || mouseY >= y + TANK_H) return;

        Solution solution = menu.getSolution();
        int total = solution.totalAmount();
        List<Component> lines = new ArrayList<>();
        if (solution.isEmpty()) {
            lines.add(Component.literal("Empty").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal("0 / " + menu.getCapacity() + " mB").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.literal("Mixture: " + total + " / " + menu.getCapacity() + " mB")
                    .withStyle(ChatFormatting.GRAY));
            for (ResourceStack<FluidResource> c : solution.components()) {
                int pct = total > 0 ? Math.round(100f * c.amount() / total) : 0;
                FluidStack stack = c.resource().toStack(c.amount());
                lines.add(stack.getHoverName().copy()
                        .append(Component.literal(": " + c.amount() + " mB (" + pct + "%)"))
                        .withStyle(ChatFormatting.WHITE));
                FluidHazardUtil.appendHazardTooltip(stack, lines::add);
            }
        }
        graphics.setTooltipForNextFrame(font, lines, Optional.<TooltipComponent>empty(), mouseX, mouseY);
    }
}
