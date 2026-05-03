package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.network.SetGPIOIdPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class GPIOPortScreen extends AbstractContainerScreen<GPIOPortMenu> {

    private static final int W = 176;
    private static final int H = 80;

    private EditBox idBox;

    public GPIOPortScreen(GPIOPortMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        this.inventoryLabelY = 9999;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (W - this.font.width(this.title)) / 2;

        idBox = new EditBox(this.font,
                this.leftPos + 60, this.topPos + 30, 56, 12,
                Component.literal("Port ID"));
        idBox.setMaxLength(5); // 0..65535
        idBox.setBordered(true);
        idBox.setCanLoseFocus(true);
        idBox.setValue(String.valueOf(menu.getPortId()));
        addRenderableWidget(idBox);

        addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.set"),
                b -> applyId())
                .bounds(this.leftPos + 62, this.topPos + 46, 52, 12).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (idBox.isFocused() && event.isConfirmation()) { applyId(); return true; }
        if (event.isEscape()) { this.minecraft.player.closeContainer(); return true; }
        return idBox.keyPressed(event) ? true : super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFFC6C6C6);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);
        graphics.text(this.font, Component.literal("Port ID (0-65535):"), 8, 33, 0xFF404040, false);

        int in  = menu.getInputSignal();
        int out = menu.getOutputSignal();
        graphics.text(this.font,
                Component.literal("IN: " + in + "  OUT: " + out),
                8, 60, 0xFF666666, false);
    }

    private void applyId() {
        try {
            int id = Integer.parseInt(idBox.getValue().trim());
            id = Math.clamp(id, 0, 65535);
            ClientPacketDistributor.sendToServer(new SetGPIOIdPacket(menu.getBlockPos(), id));
        } catch (NumberFormatException ignored) {
            idBox.setValue(String.valueOf(menu.getPortId()));
        }
    }
}
