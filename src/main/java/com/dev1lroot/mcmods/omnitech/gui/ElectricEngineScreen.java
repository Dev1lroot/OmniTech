package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * GUI for the Electric Engine.
 *
 * <p>Displays current operating mode, power state, and I/O values.
 * A clickable mode-toggle button switches between KF→EU (forward) and EU→KF (reverse).
 *
 * <p>Layout (within the 176×166 GUI background):
 * <pre>
 *  y=6    Title: "Electric Engine"
 *  y=17   [  Mode: KF→EU  ] button  (80×16 px, centered)
 *  y=36   Input label  (KF received / EU buffer)
 *  y=48   Output label (EU/t produced / KF produced)
 *  y=60   Status banner
 *  y=72   "Inventory"
 *  y=84   Player inventory
 * </pre>
 */
public class ElectricEngineScreen extends AbstractContainerScreen<ElectricEngineMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "textures/gui/empty.png");

    private static final int BTN_W = 90;
    private static final int BTN_H = 14;

    private Button modeButton;

    public ElectricEngineScreen(ElectricEngineMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title);
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

        int bx = this.leftPos + (this.imageWidth - BTN_W) / 2;
        int by = this.topPos + 17;

        modeButton = this.addRenderableWidget(
                Button.builder(modeLabel(), b ->
                        Minecraft.getInstance().gameMode
                                .handleInventoryButtonClick(menu.containerId, 0))
                        .bounds(bx, by, BTN_W, BTN_H)
                        .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos, y = this.topPos;

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);

        boolean powered = menu.isPowered();
        boolean reverse = menu.isReverse();

        if (reverse) {
            // ── Reverse mode: EU → KF ─────────────────────────────────────
            float euBuf  = menu.getEuBuffer();
            float kfOut  = menu.getKfOutput();

            String inputLabel = String.format("EU Buffer: %.1f / %.1f EU",
                    euBuf, com.dev1lroot.mcmods.omnitech.blocks.ElectricEngineBlockEntity.MAX_EU_BUFFER);
            graphics.text(this.font, inputLabel, 8, 36, 0xFF44AAFF, false);

            String outputLabel = powered
                    ? String.format("KF Output: %.2f KF/t", kfOut)
                    : "KF Output: 0 KF/t";
            graphics.text(this.font, outputLabel, 8, 48, powered ? 0xFF55FF55 : 0xFF888888, false);

        } else {
            // ── Forward mode: KF → EU ─────────────────────────────────────
            String inputLabel = powered
                    ? String.format("KF Input:  %.2f KF/t", menu.getKfReceived())
                    : "KF Input:  Idle";
            graphics.text(this.font, inputLabel, 8, 36, powered ? 0xFF55FF55 : 0xFF888888, false);

            String outputLabel = powered
                    ? String.format("EU Output: %.1f EU/t", menu.getEuPerTick())
                    : "EU Output: 0 EU/t";
            graphics.text(this.font, outputLabel, 8, 48, powered ? 0xFF44AAFF : 0xFF888888, false);
        }

        // Status banner
        String status = powered ? "[ RUNNING ]" : "[  IDLE  ]";
        int stColor   = powered ? 0xFFFFFF44 : 0xFF666666;
        graphics.text(this.font, status,
                (this.imageWidth - this.font.width(status)) / 2, 60, stColor, false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Component modeLabel() {
        return menu.isReverse()
                ? Component.translatable("gui.omnitech.electric_engine.mode_reverse")
                : Component.translatable("gui.omnitech.electric_engine.mode_forward");
    }
}
