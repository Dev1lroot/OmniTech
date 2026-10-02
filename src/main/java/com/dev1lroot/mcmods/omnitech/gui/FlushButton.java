/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.gui.layout.GuiElementDef;
import com.dev1lroot.mcmods.omnitech.gui.layout.GuiLayout;
import com.dev1lroot.mcmods.omnitech.network.FlushTankPacket;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.function.Consumer;

/**
 * Small "drain" button under a fluid gauge. The first click arms it (it turns red),
 * a second click within {@link #CONFIRM_MS} flushes the tank — the fluid is destroyed,
 * so one stray click must not do it.
 */
public class FlushButton extends AbstractButton {

    private static final long CONFIRM_MS = 3000;

    private final int containerId;
    private final String tank;
    private long armedAt = -1;

    public FlushButton(int x, int y, int w, int h, int containerId, String tank) {
        super(x, y, w, h, Component.translatable("gui.omnitech.flush"));
        this.containerId = containerId;
        this.tank = tank;
        setTooltip(Tooltip.create(Component.translatable("gui.omnitech.flush.tooltip")));
    }

    /** Adds a button for every {@code fluid_flush} element of a layout. */
    public static void addAll(GuiLayout layout, int left, int top, int containerId, Consumer<AbstractWidget> adder) {
        for (GuiElementDef el : layout.getElementsByType("fluid_flush")) {
            adder.accept(new FlushButton(left + el.x, top + el.y, el.w, el.h > 0 ? el.h : 7, containerId, el.source));
        }
    }

    private boolean armed() {
        return armedAt >= 0 && Util.getMillis() - armedAt < CONFIRM_MS;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (armed()) {
            armedAt = -1;
            ClientPacketDistributor.sendToServer(new FlushTankPacket(containerId, tank));
            setTooltip(Tooltip.create(Component.translatable("gui.omnitech.flush.tooltip")));
        } else {
            armedAt = Util.getMillis();
            setTooltip(Tooltip.create(Component.translatable("gui.omnitech.flush.confirm")));
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        boolean armed = armed();
        if (!armed && armedAt >= 0) {
            armedAt = -1;
            setTooltip(Tooltip.create(Component.translatable("gui.omnitech.flush.tooltip")));
        }
        int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
        int face = armed ? 0xFFB02020 : isHoveredOrFocused() ? 0xFF7A8A9A : 0xFF5A6470;
        g.fill(x0, y0, x1, y1, 0xFF262A30);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, face);
        // drain glyph: a downward arrow over a bar
        int cx = x0 + width / 2, cy = y0 + height / 2;
        g.fill(cx - 2, cy - 2, cx + 3, cy - 1, 0xFFFFFFFF);
        g.fill(cx - 1, cy - 1, cx + 2, cy, 0xFFFFFFFF);
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);
        g.fill(cx - 2, cy + 2, cx + 3, cy + 3, 0xFFFFFFFF);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /** Convenience for screens that place buttons by hand (non-layout gauges). */
    public static FlushButton at(int x, int y, int w, int containerId, String tank) {
        return new FlushButton(x, y, w, 7, containerId, tank);
    }

    static Minecraft mc() { return Minecraft.getInstance(); }
}
