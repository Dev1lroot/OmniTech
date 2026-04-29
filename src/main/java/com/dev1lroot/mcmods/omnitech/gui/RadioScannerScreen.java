package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Radio Scanner — waterfall spectrogram display.
 *
 * <p>Maintains a persistent {@link NativeImage} / {@link DynamicTexture} of size
 * {@value CANVAS_W}×{@value CANVAS_H}.  When a new row of signal data arrives:
 * <ol>
 *   <li>All existing pixel rows are shifted DOWN by one (y → y+1), iterating
 *       bottom-to-top to avoid aliasing.</li>
 *   <li>The new row is written at y=0 (top), including explicit black (0xFF000000)
 *       for every channel with no signal.</li>
 *   <li>The texture is uploaded to the GPU.</li>
 * </ol>
 * Nothing below y=0 is ever erased; the canvas accumulates history until rows
 * scroll off the bottom edge.
 *
 * <p>The texture is rendered as a single blit — no per-pixel {@code fill()} calls
 * each frame.
 */
public class RadioScannerScreen extends AbstractContainerScreen<RadioScannerMenu> {

    // Canvas dimensions
    private static final int CANVAS_W = RadioConstants.CHANNELS; // 300
    private static final int CANVAS_H = RadioConstants.SCAN_HISTORY; // 100

    // Panel-relative canvas origin
    private static final int CANVAS_X = 10;
    private static final int CANVAS_Y = 14;

    // Total panel size
    private static final int W = CANVAS_X * 2 + CANVAS_W;  // 320
    private static final int H = CANVAS_Y + CANVAS_H + 8;   // 122

    private static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath(OmniTech.MODID, "radio_scanner_display");

    private DynamicTexture texture;
    private NativeImage image;
    private boolean dirty = false;

    public RadioScannerScreen(RadioScannerMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (W - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 9999; // hide unused "Inventory" label

        // Allocate canvas — zero = all black
        this.image   = new NativeImage(CANVAS_W, CANVAS_H, true);
        this.texture = new DynamicTexture(() -> "radio_scanner_display", this.image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, this.texture);
    }

    @Override
    public void removed() {
        super.removed();
        Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
        // DynamicTexture.close() is called by TextureManager.release, which frees the
        // NativeImage, so we don't double-free here.
        this.texture = null;
        this.image   = null;
    }

    /**
     * Called by the packet handler when a new scanner row arrives from the server.
     * Shifts all rows down by 1, then writes the new data at y=0 (top of canvas).
     */
    public void receiveRow(float[] row) {
        if (image == null) return;

        // Shift rows down: y=98 → y=99, y=97 → y=98, …, y=0 → y=1
        // Iterate bottom-to-top to avoid overwriting source pixels before they're copied.
        for (int y = CANVAS_H - 1; y > 0; y--) {
            for (int x = 0; x < CANVAS_W; x++) {
                image.setPixel(x, y, image.getPixel(x, y - 1));
            }
        }

        // Write new row at y=0 — black for no signal, white for full signal
        int len = Math.min(row.length, CANVAS_W);
        for (int x = 0; x < len; x++) {
            image.setPixel(x, 0, toArgb(row[x]));
        }
        // Fill remainder of row with black if row is shorter than CANVAS_W
        for (int x = len; x < CANVAS_W; x++) {
            image.setPixel(x, 0, 0xFF000000);
        }

        dirty = true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        // Panel background
        graphics.fill(this.leftPos, this.topPos,
                this.leftPos + W, this.topPos + H, 0xFF1A1A1A);

        // Canvas border
        graphics.fill(this.leftPos + CANVAS_X - 1, this.topPos + CANVAS_Y - 1,
                this.leftPos + CANVAS_X + CANVAS_W + 1,
                this.topPos + CANVAS_Y + CANVAS_H + 1, 0xFF444444);

        // Upload texture to GPU if new data arrived since last frame
        if (dirty && texture != null) {
            texture.upload();
            dirty = false;
        }

        // Render the persistent waterfall texture — one blit for the whole canvas
        if (texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED,
                    TEXTURE_ID,
                    this.leftPos + CANVAS_X,
                    this.topPos  + CANVAS_Y,
                    0f, 0f,
                    CANVAS_W, CANVAS_H,
                    CANVAS_W, CANVAS_H);
        }

        // Axis tick marks every 10 channels (1 MHz)
        int cx = this.leftPos + CANVAS_X;
        int cy = this.topPos  + CANVAS_Y;
        for (int t = 0; t <= CANVAS_W; t += 10) {
            graphics.fill(cx + t, cy + CANVAS_H, cx + t + 1, cy + CANVAS_H + 2, 0xFF888888);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, 3, 0xFFAAAAAA, false);

        renderFreqLabel(graphics, 0,   "87.5");
        renderFreqLabel(graphics, 125, "100.0");
        renderFreqLabel(graphics, 250, "112.5");
        renderFreqLabel(graphics, 299, "117.4");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int toArgb(float signal) {
        int b = Math.clamp((int)(signal / 15f * 255f), 0, 255);
        return 0xFF000000 | (b << 16) | (b << 8) | b;
    }

    private void renderFreqLabel(GuiGraphicsExtractor graphics, int channel, String label) {
        int lx = CANVAS_X + channel - this.font.width(label) / 2;
        int ly = CANVAS_Y + CANVAS_H + 4;
        graphics.text(this.font, label, lx, ly, 0xFF666666, false);
    }
}
