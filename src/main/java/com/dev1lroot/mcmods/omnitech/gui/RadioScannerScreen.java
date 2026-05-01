package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.radio.FrequencyBand;
import com.dev1lroot.mcmods.omnitech.blocks.radio.RadioConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Waterfall spectrogram for the Radio Scanner, with one tab per {@link FrequencyBand}.
 *
 * <p>Each band has its own persistent {@link NativeImage} / {@link DynamicTexture}.
 * Incoming rows are written to the band's image and the GPU texture is updated on
 * the next frame.  The active band's texture is blitted; inactive bands accumulate
 * history in the background.
 *
 * <p>Canvas width is fixed at {@value CANVAS_W} pixels.  For bands with fewer
 * channels, each channel is scaled to {@code CANVAS_W / band.channels()} pixels wide.
 */
public class RadioScannerScreen extends AbstractContainerScreen<RadioScannerMenu> {

    private static final int CANVAS_W = RadioConstants.SCANNER_CANVAS_W; // 270
    private static final int CANVAS_H = RadioConstants.SCAN_HISTORY;     // 100
    private static final int CANVAS_X = 10;

    private static final int TAB_Y = 14;
    private static final int TAB_H = 12;
    private static final int TAB_W = CANVAS_W / FrequencyBand.values().length; // 30

    private static final int CANVAS_Y = TAB_Y + TAB_H + 2; // 28
    private static final int W        = CANVAS_X * 2 + CANVAS_W;         // 290
    private static final int H        = CANVAS_Y + CANVAS_H + 18;        // 146

    private static final Identifier[] TEXTURE_IDS;
    static {
        FrequencyBand[] bands = FrequencyBand.values();
        TEXTURE_IDS = new Identifier[bands.length];
        for (FrequencyBand b : bands) {
            TEXTURE_IDS[b.ordinal()] = Identifier.fromNamespaceAndPath(
                    OmniTech.MODID, "radio_scanner_" + b.name().toLowerCase());
        }
    }

    private final NativeImage[]    images   = new NativeImage[FrequencyBand.values().length];
    private final DynamicTexture[] textures = new DynamicTexture[FrequencyBand.values().length];
    private final boolean[]        dirty    = new boolean[FrequencyBand.values().length];

    public RadioScannerScreen(RadioScannerMenu menu, Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (W - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 9999;

        FrequencyBand[] bands = FrequencyBand.values();
        Minecraft mc = Minecraft.getInstance();

        for (FrequencyBand b : bands) {
            NativeImage img = new NativeImage(CANVAS_W, CANVAS_H, true);
            DynamicTexture tex = new DynamicTexture(
                    () -> "radio_scanner_" + b.name().toLowerCase(), img);
            mc.getTextureManager().register(TEXTURE_IDS[b.ordinal()], tex);
            images[b.ordinal()]   = img;
            textures[b.ordinal()] = tex;

            final int bandOrd = b.ordinal();
            addRenderableWidget(Button.builder(Component.literal(b.displayName()),
                    btn -> Minecraft.getInstance().gameMode
                            .handleInventoryButtonClick(menu.containerId, bandOrd))
                    .bounds(this.leftPos + CANVAS_X + bandOrd * TAB_W,
                            this.topPos + TAB_Y,
                            TAB_W, TAB_H)
                    .build());
        }
    }

    @Override
    public void removed() {
        super.removed();
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < FrequencyBand.values().length; i++) {
            mc.getTextureManager().release(TEXTURE_IDS[i]);
            textures[i] = null;
            images[i]   = null;
        }
    }

    /**
     * Called by the packet handler when a new row arrives from the server.
     * Updates the band's image (shifts all rows down, writes new data at y=0).
     */
    public void receiveRow(int bandOrdinal, float[] row) {
        if (bandOrdinal < 0 || bandOrdinal >= images.length) return;
        NativeImage img = images[bandOrdinal];
        if (img == null) return;

        FrequencyBand band = FrequencyBand.values()[bandOrdinal];
        int pxPerCh = Math.max(1, CANVAS_W / row.length);

        // Shift existing rows down
        for (int y = CANVAS_H - 1; y > 0; y--) {
            for (int x = 0; x < CANVAS_W; x++) {
                img.setPixel(x, y, img.getPixel(x, y - 1));
            }
        }

        // Write new row at y=0
        for (int ch = 0; ch < row.length; ch++) {
            int color = bandColor(band, row[ch]);
            for (int px = 0; px < pxPerCh; px++) {
                int x = ch * pxPerCh + px;
                if (x < CANVAS_W) img.setPixel(x, 0, color);
            }
        }
        // Fill any remainder with black
        int filled = row.length * pxPerCh;
        for (int x = filled; x < CANVAS_W; x++) img.setPixel(x, 0, 0xFF000000);

        dirty[bandOrdinal] = true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        // Panel
        graphics.fill(this.leftPos, this.topPos,
                this.leftPos + W, this.topPos + H, 0xFF1A1A1A);

        // Active tab highlight
        int activeOrd = menu.getActiveBand().ordinal();
        graphics.fill(
                this.leftPos + CANVAS_X + activeOrd * TAB_W,
                this.topPos  + TAB_Y,
                this.leftPos + CANVAS_X + activeOrd * TAB_W + TAB_W,
                this.topPos  + TAB_Y + TAB_H,
                0xFF333333);

        // Canvas border
        graphics.fill(this.leftPos + CANVAS_X - 1, this.topPos + CANVAS_Y - 1,
                this.leftPos + CANVAS_X + CANVAS_W + 1,
                this.topPos  + CANVAS_Y + CANVAS_H + 1, 0xFF444444);

        // Upload the active band's texture if dirty
        if (dirty[activeOrd] && textures[activeOrd] != null) {
            textures[activeOrd].upload();
            dirty[activeOrd] = false;
        }

        // Blit the active band's waterfall
        if (textures[activeOrd] != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED,
                    TEXTURE_IDS[activeOrd],
                    this.leftPos + CANVAS_X,
                    this.topPos  + CANVAS_Y,
                    0f, 0f,
                    CANVAS_W, CANVAS_H,
                    CANVAS_W, CANVAS_H);
        }

        // Frequency axis tick marks
        int cx = this.leftPos + CANVAS_X;
        int cy = this.topPos  + CANVAS_Y;
        for (int t = 0; t <= CANVAS_W; t += CANVAS_W / 5) {
            graphics.fill(cx + t, cy + CANVAS_H, cx + t + 1, cy + CANVAS_H + 2, 0xFF888888);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, 3, 0xFFAAAAAA, false);

        FrequencyBand band = menu.getActiveBand();
        renderBandLabel(graphics, band, 0,                    0);
        renderBandLabel(graphics, band, band.channels() / 2,  CANVAS_W / 2);
        renderBandLabel(graphics, band, band.channels() - 1,  CANVAS_W - 1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Map a signal level (0–15) to an ARGB pixel color specific to the given band. */
    private static int bandColor(FrequencyBand band, float signal) {
        int v = Math.clamp((int)(signal / 15f * 255f), 0, 255);
        return switch (band) {
            case ELF -> 0xFF000000 | ((v * 3 / 5) << 16)           | v;          // purple
            case VLF -> 0xFF000000                                   | v;          // blue
            case LF  -> 0xFF000000 | ((v * 3 / 5) << 8)            | v;          // cyan
            case MF  -> 0xFF000000 | (v << 8);                                    // green
            case HF  -> 0xFF000000 | ((v / 2) << 16) | (v << 8);                 // yellow-green
            case VHF -> 0xFF000000 | (v << 16)        | (v << 8);                // yellow
            case UHF -> 0xFF000000 | (v << 16)        | ((v / 2) << 8);          // orange
            case SHF -> 0xFF000000 | (v << 16);                                   // red
            case EHF -> 0xFF000000 | ((v * 4 / 5) << 16)           | (v / 5);    // dark crimson
        };
    }

    private void renderBandLabel(GuiGraphicsExtractor graphics, FrequencyBand band,
                                 int channelIndex, int canvasPixelX) {
        String label = band.freqDisplay(channelIndex);
        int lx = this.leftPos + CANVAS_X + canvasPixelX - this.font.width(label) / 2;
        int ly = this.topPos  + CANVAS_Y + CANVAS_H + 4;
        graphics.text(this.font, label, lx, ly, 0xFF666666, false);
    }
}
