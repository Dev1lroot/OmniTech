package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioConstants;
import com.dev1lroot.mcmods.omnitech.network.SetRadioLocatorFreqPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class RadioLocatorScreen extends Screen {

    private static final int W = 200;
    private static final int H = 60;

    private static final int BTN_W = 16;
    private static final int BTN_H = 12;
    private static final int BTN_Y = 22;

    private static final int BX_MINUS_100 = 6;
    private static final int BX_MINUS_10  = 24;
    private static final int BX_MINUS_1   = 42;
    private static final int DISP_X       = 60;
    private static final int DISP_W       = 62;
    private static final int BX_PLUS_1    = 124;
    private static final int BX_PLUS_10   = 142;
    private static final int BX_PLUS_100  = 160;

    private final InteractionHand hand;
    private int freqX10;
    private EditBox freqBox;

    public RadioLocatorScreen(int freqX10, InteractionHand hand) {
        super(Component.translatable("screen.omnitech.radio_locator"));
        this.freqX10 = freqX10;
        this.hand    = hand;
    }

    @Override
    protected void init() {
        int lx = (this.width  - W) / 2;
        int ty = (this.height - H) / 2;

        int by = ty + BTN_Y;

        addRenderableWidget(Button.builder(Component.literal("-100"),
                b -> adjustFreq(-100)).bounds(lx + BX_MINUS_100, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("-10"),
                b -> adjustFreq(-10)).bounds(lx + BX_MINUS_10, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("-1"),
                b -> adjustFreq(-1)).bounds(lx + BX_MINUS_1, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+1"),
                b -> adjustFreq(+1)).bounds(lx + BX_PLUS_1, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+10"),
                b -> adjustFreq(+10)).bounds(lx + BX_PLUS_10, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+100"),
                b -> adjustFreq(+100)).bounds(lx + BX_PLUS_100, by, BTN_W, BTN_H).build());

        this.freqBox = new EditBox(this.font,
                lx + 40, ty + 42, 120, 12,
                Component.literal("Frequency"));
        this.freqBox.setMaxLength(7);
        this.freqBox.setBordered(true);
        this.freqBox.setCanLoseFocus(true);
        this.freqBox.setValue(formatMHz(freqX10));
        addRenderableWidget(this.freqBox);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (freqBox.isFocused() && event.isConfirmation()) {
            applyFreqText();
            return true;
        }
        if (event.isEscape()) {
            this.onClose();
            return true;
        }
        return !freqBox.keyPressed(event) && !freqBox.canConsumeInput()
                ? super.keyPressed(event) : true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int lx = (this.width  - W) / 2;
        int ty = (this.height - H) / 2;
        graphics.fill(lx, ty, lx + W, ty + H, 0xFFC6C6C6);
        graphics.fill(lx + 38, ty + 40, lx + 162, ty + 56, 0xFF000000);
        graphics.fill(lx + 39, ty + 41, lx + 161, ty + 55, 0xFFFFFFFF);

        int titleX = (W - this.font.width(this.title)) / 2;
        graphics.text(this.font, this.title, lx + titleX, ty + 6, 0xFF404040, false);

        String freqStr = String.format("%.1f MHz", freqX10 / 10f);
        int fw = this.font.width(freqStr);
        int fx = lx + DISP_X + (DISP_W - fw) / 2;
        graphics.text(this.font, freqStr, fx, ty + BTN_Y + 2, 0xFF00AAFF, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void adjustFreq(int delta) {
        freqX10 = Math.clamp(freqX10 + delta,
                RadioConstants.FREQ_MIN_X10, RadioConstants.FREQ_MAX_X10);
        freqBox.setValue(formatMHz(freqX10));
        sendFreq();
    }

    private void applyFreqText() {
        try {
            float mhz = Float.parseFloat(freqBox.getValue().trim());
            freqX10 = Math.clamp(Math.round(mhz * 10f),
                    RadioConstants.FREQ_MIN_X10, RadioConstants.FREQ_MAX_X10);
            freqBox.setValue(formatMHz(freqX10));
            sendFreq();
        } catch (NumberFormatException ignored) {
            freqBox.setValue(formatMHz(freqX10));
        }
    }

    private void sendFreq() {
        ClientPacketDistributor.sendToServer(
                new SetRadioLocatorFreqPacket(freqX10, hand.ordinal()));
    }

    private static String formatMHz(int x10) {
        return String.format("%.1f", x10 / 10f);
    }
}
