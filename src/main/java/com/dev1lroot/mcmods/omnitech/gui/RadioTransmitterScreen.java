package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioConstants;
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
 *   <li>y=6   – title "Radio Transmitter"</li>
 *   <li>y=22  – "Frequency:" label + six ±step buttons + frequency display</li>
 *   <li>y=42  – EditBox for direct frequency entry (Enter to apply)</li>
 *   <li>y=58  – "Input: X.XX" (redstone signal being transmitted)</li>
 * </ul>
 */
public class RadioTransmitterScreen extends AbstractContainerScreen<RadioTransmitterMenu> {

    private static final int W = 200;
    private static final int H = 80;

    private static final int BTN_W = 16;
    private static final int BTN_H = 12;
    private static final int BTN_Y = 22;

    // Button x positions (panel-relative)
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
        this.inventoryLabelY = 9999; // hide "Inventory" label

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
                this.leftPos + 40, this.topPos + 42, 120, 12,
                Component.literal("Frequency"));
        this.freqBox.setMaxLength(7);
        this.freqBox.setBordered(true);
        this.freqBox.setCanLoseFocus(true);
        this.freqBox.setValue(String.format("%.1f", menu.getFrequencyMHz()));
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
        // Tinted panel background
        graphics.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFFC6C6C6);
        // Frequency field label
        graphics.fill(this.leftPos + 38, this.topPos + 40, this.leftPos + 162, this.topPos + 56, 0xFF000000);
        graphics.fill(this.leftPos + 39, this.topPos + 41, this.leftPos + 161, this.topPos + 55, 0xFFFFFFFF);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Title
        graphics.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);

        // Frequency display (between buttons)
        String freqStr = String.format("%.1f MHz", menu.getFrequencyMHz());
        int fw = this.font.width(freqStr);
        int fx = DISP_X + (DISP_W - fw) / 2;
        graphics.text(this.font, freqStr, fx, BTN_Y + 2, 0xFF00AAFF, false);

        // Edit-box label
        graphics.text(this.font, "Direct:", 6, 45, 0xFF888888, false);

        // Signal readout
        float sig = menu.getCurrentSignal();
        String sigStr = String.format("Input RS: %.0f  (%.2f analog)", sig, sig);
        graphics.text(this.font, sigStr, 6, 60, signalColor(sig), false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendBtn(int btnId) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, btnId);
        // Sync the text box to the new frequency
        freqBox.setValue(String.format("%.1f", menu.getFrequencyMHz()));
    }

    private void applyFreqText() {
        try {
            float mhz = Float.parseFloat(freqBox.getValue().trim());
            int x10 = Math.clamp(Math.round(mhz * 10f),
                    RadioConstants.FREQ_MIN_X10, RadioConstants.FREQ_MAX_X10);
            ClientPacketDistributor.sendToServer(new SetRadioFrequencyPacket(menu.getBlockPos(), x10));
        } catch (NumberFormatException ignored) {
            // restore last valid value
            freqBox.setValue(String.format("%.1f", menu.getFrequencyMHz()));
        }
    }

    private static int signalColor(float sig) {
        if (sig >= 12) return 0xFFFFFFFF;
        if (sig >= 8)  return 0xFFFFCC00;
        if (sig >= 4)  return 0xFF44CC44;
        if (sig > 0)   return 0xFF44AA44;
        return 0xFF888888;
    }
}
