package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.items.FloppyDiskItem;
import com.dev1lroot.mcmods.omnitech.network.SetFloppyDriveIdPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class FloppyDriveScreen extends AbstractContainerScreen<FloppyDriveMenu> {

    private static final int W = 176;
    private static final int H = 166;

    private EditBox idBox;

    public FloppyDriveScreen(FloppyDriveMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        this.inventoryLabelY = 9999;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (W - this.font.width(this.title)) / 2;

        idBox = new EditBox(this.font,
                this.leftPos + 60, this.topPos + 10, 56, 12,
                Component.literal("Drive ID"));
        idBox.setMaxLength(5);
        idBox.setBordered(true);
        idBox.setCanLoseFocus(true);
        idBox.setValue(String.valueOf(menu.getDriveId()));
        addRenderableWidget(idBox);

        addRenderableWidget(Button.builder(
                Component.translatable("gui.omnitech.set"),
                b -> applyId())
                .bounds(this.leftPos + 62, this.topPos + 24, 52, 12).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (idBox.isFocused() && event.isConfirmation()) { applyId(); return true; }
        if (event.isEscape()) { this.minecraft.player.closeContainer(); return true; }
        return idBox.keyPressed(event) ? true : super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFFC6C6C6);
        // Disk slot frame
        g.fill(this.leftPos + 76, this.topPos + 31, this.leftPos + 98, this.topPos + 53, 0xFF000000);
        g.fill(this.leftPos + 77, this.topPos + 32, this.leftPos + 97, this.topPos + 52, 0xFF8B8B8B);
        // Player inventory area
        g.fill(this.leftPos + 4,  this.topPos + 80, this.leftPos + 172, this.topPos + 162, 0xFF8B8B8B);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, this.title, this.titleLabelX, 6, 0xFF404040, false);
        g.text(this.font, Component.literal("ID (0-65535):"), 8, 13, 0xFF404040, false);

        ItemStack disk = menu.getSlot(0).getItem();
        if (!disk.isEmpty() && disk.getItem() instanceof FloppyDiskItem) {
            com.dev1lroot.mcmods.omnitech.OmniTechDataComponents.ByteData data = disk.get(com.dev1lroot.mcmods.omnitech.OmniTechDataComponents.FLOPPY_DATA.get());
            int used = data != null ? data.data().length : 0;
            g.text(this.font, Component.literal(String.format("%.1f / 1440 KB", used / 1024.0)),
                    8, 55, 0xFF4488FF, false);
        } else {
            g.text(this.font, Component.literal("No disk"), 8, 55, 0xFF888888, false);
        }
    }

    private void applyId() {
        try {
            int id = Math.clamp(Integer.parseInt(idBox.getValue().trim()), 0, 65535);
            ClientPacketDistributor.sendToServer(new SetFloppyDriveIdPacket(menu.getBlockPos(), id));
        } catch (NumberFormatException ignored) {
            idBox.setValue(String.valueOf(menu.getDriveId()));
        }
    }
}
