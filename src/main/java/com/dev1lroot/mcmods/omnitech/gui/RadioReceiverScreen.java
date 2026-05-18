/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_receiver.RadioReceiverBlockEntity;
import com.dev1lroot.mcmods.omnitech.network.SetRadioFrequencyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** GUI for the Radio Receiver — mirrors {@link RadioTransmitterScreen} but shows received signal. */
public class RadioReceiverScreen extends AbstractContainerScreen<RadioReceiverMenu> {

    private static final int W = 200;
    private static final int H = 96;

    private static final int TAB_Y = 18;
    private static final int TAB_H = 11;
    private static final int TAB_W = 22;

    private static final int BTN_Y      = 34;
    private static final int BTN_H      = 12;
    private static final int ARROW_W    = 14;
    private static final int SLIDER_X   = 4 + ARROW_W + 2;   // 20
    private static final int SLIDER_W   = W - 4 - ARROW_W - 2 - 2 - ARROW_W - 4; // 160
    private static final int BTN_PREV_X = 4;
    private static final int BTN_NEXT_X = SLIDER_X + SLIDER_W + 2; // 182

    private EditBox freqBox;
    private FreqSlider freqSlider;
    private int lastBandOrd     = -1;
    private int lastChannelIndex = -1;

    public RadioReceiverScreen(RadioReceiverMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (W - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 9999;

        FrequencyBand[] bands = FrequencyBand.values();
        for (int i = 0; i < bands.length; i++) {
            final int bandId = RadioReceiverBlockEntity.BTN_BAND_BASE + i;
            addRenderableWidget(Button.builder(Component.literal(bands[i].displayName()),
                    b -> sendBtn(bandId))
                    .bounds(this.leftPos + 1 + i * TAB_W, this.topPos + TAB_Y, TAB_W, TAB_H)
                    .build());
        }

        int by = this.topPos + BTN_Y;

        addRenderableWidget(Button.builder(Component.literal("<"),
                b -> sendBtn(RadioReceiverBlockEntity.BTN_FREQ_MINUS_1))
                .bounds(this.leftPos + BTN_PREV_X, by, ARROW_W, BTN_H).build());

        freqSlider = new FreqSlider(this.leftPos + SLIDER_X, by, SLIDER_W, BTN_H);
        addRenderableWidget(freqSlider);

        addRenderableWidget(Button.builder(Component.literal(">"),
                b -> sendBtn(RadioReceiverBlockEntity.BTN_FREQ_PLUS_1))
                .bounds(this.leftPos + BTN_NEXT_X, by, ARROW_W, BTN_H).build());

        this.freqBox = new EditBox(this.font,
                this.leftPos + 40, this.topPos + 50, 120, 12,
                Component.literal("Frequency"));
        this.freqBox.setMaxLength(10);
        this.freqBox.setBordered(true);
        this.freqBox.setCanLoseFocus(true);
        this.freqBox.setValue(String.valueOf(menu.getFreqValue()));
        addRenderableWidget(this.freqBox);

        lastBandOrd      = -1;
        lastChannelIndex = -1;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int bandOrd      = menu.getBand().ordinal();
        int channelIndex = menu.getChannelIndex();
        if (bandOrd != lastBandOrd || channelIndex != lastChannelIndex) {
            lastBandOrd      = bandOrd;
            lastChannelIndex = channelIndex;
            freqSlider.syncFromMenu();
            syncFreqBox();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (freqBox.isFocused() && event.isConfirmation()) {
            applyFreqText();
            return true;
        }
        if (event.isEscape()) {
            this.minecraft.player.closeContainer();
            return true;
        }
        return !freqBox.keyPressed(event) && !freqBox.canConsumeInput()
                ? super.keyPressed(event) : true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFFC6C6C6);

        int activeOrd = menu.getBand().ordinal();
        graphics.fill(this.leftPos + 1 + activeOrd * TAB_W, this.topPos + TAB_Y,
                this.leftPos + 1 + activeOrd * TAB_W + TAB_W, this.topPos + TAB_Y + TAB_H,
                0xFF888888);

        graphics.fill(this.leftPos + 38, this.topPos + 48, this.leftPos + 162, this.topPos + 64, 0xFF000000);
        graphics.fill(this.leftPos + 39, this.topPos + 49, this.leftPos + 161, this.topPos + 63, 0xFFFFFFFF);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);

        graphics.text(this.font, "Direct:", 6, 53, 0xFF888888, false);

        FrequencyBand band = menu.getBand();
        float sig = menu.getCurrentSignal();
        int rs = Math.clamp((int) sig, 0, 15);
        String sigStr = String.format("Signal: %.2f  RS out: %d", sig, rs);
        graphics.text(this.font, sigStr, 6, 68, signalColor(sig), false);

        String note = bandNote(band);
        graphics.text(this.font, note, 6, 78, 0xFF777777, false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendBtn(int btnId) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, btnId);
    }

    private void syncFreqBox() {
        FrequencyBand band = menu.getBand();
        float v = band.freqMin() + menu.getChannelIndex() * band.freqStep();
        freqBox.setValue(band.freqStep() < 1f ? String.format("%.1f", v) : String.format("%.0f", v));
    }

    private void applyFreqText() {
        try {
            FrequencyBand band = menu.getBand();
            float freq = Float.parseFloat(freqBox.getValue().trim());
            int ch = band.channelFromFreq(freq);
            int globalKey = band.globalKey(ch);
            ClientPacketDistributor.sendToServer(
                    new SetRadioFrequencyPacket(menu.getBlockPos(), globalKey));
        } catch (NumberFormatException ignored) {
            syncFreqBox();
        }
    }

    // ── Slider ────────────────────────────────────────────────────────────────

    private class FreqSlider extends AbstractSliderButton {

        FreqSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), 0.0);
            syncFromMenu();
        }

        void syncFromMenu() {
            FrequencyBand band = menu.getBand();
            int channels = band.channels();
            int ch = menu.getChannelIndex();
            this.value = channels <= 1 ? 0.0 : (double) ch / (channels - 1);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            FrequencyBand band = menu.getBand();
            int channels = band.channels();
            int ch = Math.clamp((int) Math.round(this.value * (channels - 1)), 0, channels - 1);
            setMessage(Component.literal("[" + band.displayName() + "] " + band.freqDisplay(ch)));
        }

        @Override
        protected void applyValue() {
            FrequencyBand band = menu.getBand();
            int channels = band.channels();
            int ch = Math.clamp((int) Math.round(this.value * (channels - 1)), 0, channels - 1);
            ClientPacketDistributor.sendToServer(
                    new SetRadioFrequencyPacket(menu.getBlockPos(), band.globalKey(ch)));
            float v = band.freqMin() + ch * band.freqStep();
            freqBox.setValue(band.freqStep() < 1f ? String.format("%.1f", v) : String.format("%.0f", v));
            lastChannelIndex = ch;
        }
    }

    private static int signalColor(float sig) {
        if (sig >= 12) return 0xFFFFFFFF;
        if (sig >= 8)  return 0xFFFFCC00;
        if (sig >= 4)  return 0xFF44CC44;
        if (sig > 0)   return 0xFF44AA44;
        return 0xFF888888;
    }

    private static String bandNote(FrequencyBand band) {
        if (!band.audioAllowed())    return "Redstone only";
        if (band.noisy())            return "Audio + noise";
        if (band.maxRange() > 0)     return "Range: " + band.maxRange() + " blocks";
        if (band.interdimensional()) return "Interdimensional";
        return "Full audio";
    }
}
