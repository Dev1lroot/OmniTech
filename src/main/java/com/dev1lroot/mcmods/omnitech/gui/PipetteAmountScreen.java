/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.items.PipetteItem;
import com.dev1lroot.mcmods.omnitech.network.SetPipetteAmountPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Small popup opened by shift + right-clicking a {@link PipetteItem}: sets the 1–20 mB draw amount. */
public class PipetteAmountScreen extends Screen {

    private static final int W = 160;
    private static final int H = 56;

    private static final int BTN_W = 16;
    private static final int BTN_H = 12;
    private static final int BTN_Y = 22;
    private static final int BX_MINUS_5 = 6;
    private static final int BX_MINUS_1 = 24;
    private static final int DISP_X     = 46;
    private static final int DISP_W     = 68;
    private static final int BX_PLUS_1  = 120;
    private static final int BX_PLUS_5  = 138;

    private final InteractionHand hand;
    private int amount;
    private EditBox amountBox;

    public PipetteAmountScreen(int amount, InteractionHand hand) {
        super(Component.translatable("screen.omnitech.pipette_amount"));
        this.amount = Math.clamp(amount, 1, PipetteItem.MAX_AMOUNT);
        this.hand = hand;
    }

    @Override
    protected void init() {
        int lx = (this.width  - W) / 2;
        int ty = (this.height - H) / 2;
        int by = ty + BTN_Y;

        addRenderableWidget(Button.builder(Component.literal("-5"),
                b -> adjust(-5)).bounds(lx + BX_MINUS_5, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("-1"),
                b -> adjust(-1)).bounds(lx + BX_MINUS_1, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+1"),
                b -> adjust(+1)).bounds(lx + BX_PLUS_1, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+5"),
                b -> adjust(+5)).bounds(lx + BX_PLUS_5, by, BTN_W, BTN_H).build());

        this.amountBox = new EditBox(this.font, lx + DISP_X, by, DISP_W, BTN_H,
                Component.literal("Amount"));
        this.amountBox.setMaxLength(3);
        this.amountBox.setValue(Integer.toString(amount));
        addRenderableWidget(this.amountBox);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (amountBox.isFocused() && event.isConfirmation()) {
            applyText();
            return true;
        }
        if (event.isEscape()) {
            this.onClose();
            return true;
        }
        return !amountBox.keyPressed(event) && !amountBox.canConsumeInput()
                ? super.keyPressed(event) : true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int lx = (this.width  - W) / 2;
        int ty = (this.height - H) / 2;
        graphics.fill(lx, ty, lx + W, ty + H, 0xFFC6C6C6);
        graphics.fill(lx + 2, ty + 2, lx + W - 2, ty + H - 2, 0xFF8B8B8B);

        int titleX = (W - this.font.width(this.title)) / 2;
        graphics.text(this.font, this.title, lx + titleX, ty + 6, 0xFF404040, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void adjust(int delta) {
        amount = Math.clamp(amount + delta, 1, PipetteItem.MAX_AMOUNT);
        amountBox.setValue(Integer.toString(amount));
        send();
    }

    private void applyText() {
        try {
            amount = Math.clamp(Integer.parseInt(amountBox.getValue().trim()), 1, PipetteItem.MAX_AMOUNT);
        } catch (NumberFormatException ignored) {
            // keep prior amount
        }
        amountBox.setValue(Integer.toString(amount));
        send();
    }

    private void send() {
        ClientPacketDistributor.sendToServer(new SetPipetteAmountPacket(amount, hand.ordinal()));
    }
}
