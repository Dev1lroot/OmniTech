package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.blocks.radio.radio_transmitter.RadioTransmitterBlockEntity;
import com.dev1lroot.mcmods.omnitech.network.SetRadioFrequencyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * GUI for the Radio Transmitter.
 *
 * <p>Layout (panel-relative):
 * <ul>
 *   <li>y=6   – title</li>
 *   <li>y=18  – band tabs [ELF][VLF][LF][MF][HF][VHF][UHF][SHF][EHF]</li>
 *   <li>y=34  – ±step frequency buttons + current frequency display</li>
 *   <li>y=50  – EditBox for direct frequency entry (Enter to confirm)</li>
 *   <li>y=66  – signal readout</li>
 * </ul>
 */
public class RadioTransmitterScreen extends AbstractContainerScreen<RadioTransmitterMenu> {

    private static final int W = 200;
    private static final int H = 96;

    // Band tab row
    private static final int TAB_Y = 18;
    private static final int TAB_H = 11;
    private static final int TAB_W = 22; // 9 × 22 = 198 px, fits in W=200

    // Frequency button row
    private static final int BTN_W        = 16;
    private static final int BTN_H        = 12;
    private static final int BTN_Y        = 34;
    private static final int BX_MINUS_100 = 6;
    private static final int BX_MINUS_10  = 24;
    private static final int BX_MINUS_1   = 42;
    private static final int DISP_X       = 60;
    private static final int DISP_W       = 62;
    private static final int BX_PLUS_1    = 124;
    private static final int BX_PLUS_10   = 142;
    private static final int BX_PLUS_100  = 160;

    private EditBox freqBox;

    public RadioTransmitterScreen(RadioTransmitterMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (W - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 9999;

        // Band tabs
        FrequencyBand[] bands = FrequencyBand.values();
        for (int i = 0; i < bands.length; i++) {
            final int bandId = RadioTransmitterBlockEntity.BTN_BAND_BASE + i;
            addRenderableWidget(Button.builder(Component.literal(bands[i].displayName()),
                    b -> sendBtn(bandId))
                    .bounds(this.leftPos + 1 + i * TAB_W, this.topPos + TAB_Y, TAB_W, TAB_H)
                    .build());
        }

        int by = this.topPos + BTN_Y;
        addRenderableWidget(Button.builder(Component.literal("-100"),
                b -> sendBtn(RadioTransmitterBlockEntity.BTN_FREQ_MINUS_100))
                .bounds(this.leftPos + BX_MINUS_100, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("-10"),
                b -> sendBtn(RadioTransmitterBlockEntity.BTN_FREQ_MINUS_10))
                .bounds(this.leftPos + BX_MINUS_10, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("-1"),
                b -> sendBtn(RadioTransmitterBlockEntity.BTN_FREQ_MINUS_1))
                .bounds(this.leftPos + BX_MINUS_1, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+1"),
                b -> sendBtn(RadioTransmitterBlockEntity.BTN_FREQ_PLUS_1))
                .bounds(this.leftPos + BX_PLUS_1, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+10"),
                b -> sendBtn(RadioTransmitterBlockEntity.BTN_FREQ_PLUS_10))
                .bounds(this.leftPos + BX_PLUS_10, by, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+100"),
                b -> sendBtn(RadioTransmitterBlockEntity.BTN_FREQ_PLUS_100))
                .bounds(this.leftPos + BX_PLUS_100, by, BTN_W, BTN_H).build());

        this.freqBox = new EditBox(this.font,
                this.leftPos + 40, this.topPos + 50, 120, 12,
                Component.literal("Frequency"));
        this.freqBox.setMaxLength(10);
        this.freqBox.setBordered(true);
        this.freqBox.setCanLoseFocus(true);
        this.freqBox.setValue(String.valueOf(menu.getFreqValue()));
        addRenderableWidget(this.freqBox);
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

        // Active band tab highlight
        int activeOrd = menu.getBand().ordinal();
        graphics.fill(this.leftPos + 1 + activeOrd * TAB_W, this.topPos + TAB_Y,
                this.leftPos + 1 + activeOrd * TAB_W + TAB_W, this.topPos + TAB_Y + TAB_H,
                0xFF888888);

        // EditBox frame
        graphics.fill(this.leftPos + 38, this.topPos + 48, this.leftPos + 162, this.topPos + 64, 0xFF000000);
        graphics.fill(this.leftPos + 39, this.topPos + 49, this.leftPos + 161, this.topPos + 63, 0xFFFFFFFF);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);

        // Band indicator + frequency display
        FrequencyBand band = menu.getBand();
        String freqStr = "[" + band.displayName() + "] " + menu.getFreqDisplay();
        int fw = this.font.width(freqStr);
        int fx = DISP_X + (DISP_W - fw) / 2;
        graphics.text(this.font, freqStr, fx, BTN_Y + 2, 0xFF00AAFF, false);

        graphics.text(this.font, "Direct:", 6, 53, 0xFF888888, false);

        float sig = menu.getCurrentSignal();
        String sigStr = String.format("Input RS: %.0f  (%.2f analog)", sig, sig);
        graphics.text(this.font, sigStr, 6, 68, signalColor(sig), false);

        // Band capability note below signal
        String note = bandNote(band);
        graphics.text(this.font, note, 6, 78, 0xFF777777, false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendBtn(int btnId) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, btnId);
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
            float v = menu.getFreqValue();
            FrequencyBand b = menu.getBand();
            freqBox.setValue(b.freqStep() < 1f ? String.format("%.1f", v) : String.format("%.0f", v));
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
        if (!band.audioAllowed())     return "Redstone only";
        if (band.noisy())             return "Audio + noise";
        if (band.maxRange() > 0)      return "Range: " + band.maxRange() + " blocks";
        if (band.interdimensional())  return "Interdimensional";
        return "Full audio";
    }
}
