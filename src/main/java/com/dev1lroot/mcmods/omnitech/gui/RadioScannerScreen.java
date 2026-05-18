/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
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
 * Waterfall spectrogram for the Radio Scanner.
 *
 * <p>Each band has its own persistent {@link NativeImage}/{@link DynamicTexture}.
 * Incoming rows are written to the band's image on the client thread; the texture
 * is uploaded to the GPU on the next frame that band is active.
 *
 * <p>◄ / ► arrows cycle through the nine {@link FrequencyBand}s.
 * When the cursor hovers the canvas a hairline follows it and a floating label
 * shows the exact frequency under the crosshair.
 *
 * <p>Coordinate conventions used here:
 * <ul>
 *   <li>{@code extractBackground} receives absolute screen coordinates — all
 *       {@code fill}/{@code blit} calls must add {@code this.leftPos}/{@code topPos}.</li>
 *   <li>{@code extractLabels} is called after the renderer has already translated
 *       the pose by {@code (leftPos, topPos)}, so rendering coords are panel-relative
 *       (no leftPos/topPos added).  Mouse coords are still absolute.</li>
 * </ul>
 */
public class RadioScannerScreen extends AbstractContainerScreen<RadioScannerMenu> {

    private static final int CANVAS_W = RadioConstants.SCANNER_CANVAS_W; // 270
    private static final int CANVAS_H = RadioConstants.SCAN_HISTORY;     // 100
    private static final int CANVAS_X = 10;                              // left margin

    private static final int NAV_Y = 14;  // top of band-navigation row
    private static final int NAV_H = 12;
    private static final int ARR_W = 16;  // width of each ◄ / ► button

    private static final int CANVAS_Y = NAV_Y + NAV_H + 2; // 28
    private static final int W        = CANVAS_X * 2 + CANVAS_W; // 290
    private static final int H        = CANVAS_Y + CANVAS_H + 18; // 146

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

    public RadioScannerScreen(RadioScannerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX     = (W - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 9999; // hide the vanilla "Inventory" label

        Minecraft mc = Minecraft.getInstance();
        for (FrequencyBand b : FrequencyBand.values()) {
            NativeImage img = new NativeImage(CANVAS_W, CANVAS_H, true);
            DynamicTexture tex = new DynamicTexture(
                    () -> "radio_scanner_" + b.name().toLowerCase(), img);
            mc.getTextureManager().register(TEXTURE_IDS[b.ordinal()], tex);
            images[b.ordinal()]   = img;
            textures[b.ordinal()] = tex;
        }

        // ◄ / ► band navigation
        addRenderableWidget(Button.builder(Component.literal("◄"), btn -> navigateBand(-1))
                .bounds(this.leftPos + CANVAS_X,
                        this.topPos  + NAV_Y,
                        ARR_W, NAV_H)
                .build());
        addRenderableWidget(Button.builder(Component.literal("►"), btn -> navigateBand(+1))
                .bounds(this.leftPos + CANVAS_X + CANVAS_W - ARR_W,
                        this.topPos  + NAV_Y,
                        ARR_W, NAV_H)
                .build());
    }

    private void navigateBand(int delta) {
        FrequencyBand[] bands = FrequencyBand.values();
        int next = (menu.getActiveBand().ordinal() + delta + bands.length) % bands.length;
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, next);
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
     * Shifts existing rows down, writes the new row at y=0 (newest data at top).
     */
    public void receiveRow(int bandOrdinal, float[] row) {
        if (bandOrdinal < 0 || bandOrdinal >= images.length) return;
        NativeImage img = images[bandOrdinal];
        if (img == null) return;

        FrequencyBand band = FrequencyBand.values()[bandOrdinal];
        int pxPerCh = Math.max(1, CANVAS_W / row.length);

        for (int y = CANVAS_H - 1; y > 0; y--) {
            for (int x = 0; x < CANVAS_W; x++) {
                img.setPixel(x, y, img.getPixel(x, y - 1));
            }
        }

        for (int ch = 0; ch < row.length; ch++) {
            int color = bandColor(band, row[ch]);
            for (int px = 0; px < pxPerCh; px++) {
                int x = ch * pxPerCh + px;
                if (x < CANVAS_W) img.setPixel(x, 0, color);
            }
        }
        int filled = row.length * pxPerCh;
        for (int x = filled; x < CANVAS_W; x++) img.setPixel(x, 0, 0xFF000000);

        dirty[bandOrdinal] = true;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick) {
        // Skip super — it would blit the misaligned vanilla 176×166 inventory texture.
        // All coordinates below are absolute (leftPos/topPos already included).

        graphics.fill(this.leftPos, this.topPos, this.leftPos + W, this.topPos + H, 0xFF1A1A1A);

        int activeOrd = menu.getActiveBand().ordinal();
        int absCx     = this.leftPos + CANVAS_X;
        int absCy     = this.topPos  + CANVAS_Y;

        // Canvas border
        graphics.fill(absCx - 1, absCy - 1, absCx + CANVAS_W + 1, absCy + CANVAS_H + 1, 0xFF444444);

        // Upload (if dirty) and blit the active band's waterfall texture
        if (dirty[activeOrd] && textures[activeOrd] != null) {
            textures[activeOrd].upload();
            dirty[activeOrd] = false;
        }
        if (textures[activeOrd] != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_IDS[activeOrd],
                    absCx, absCy, 0f, 0f, CANVAS_W, CANVAS_H, CANVAS_W, CANVAS_H);
        }

        // Cursor hairline + floating frequency label while hovering the canvas
        if (mouseX >= absCx && mouseX < absCx + CANVAS_W
                && mouseY >= absCy && mouseY < absCy + CANVAS_H) {
            graphics.fill(mouseX, absCy, mouseX + 1, absCy + CANVAS_H, 0xAAFFFFFF);
            renderCursorLabel(graphics, mouseX, mouseY, absCx, absCy);
        }

        // Frequency axis tick marks
        for (int t = 0; t <= CANVAS_W; t += CANVAS_W / 5) {
            graphics.fill(absCx + t, absCy + CANVAS_H,
                          absCx + t + 1, absCy + CANVAS_H + 2, 0xFF888888);
        }
    }

    private void renderCursorLabel(GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, int absCx, int absCy) {
        FrequencyBand band = menu.getActiveBand();
        int channel = Math.clamp((mouseX - absCx) * band.channels() / CANVAS_W,
                0, band.channels() - 1);
        String label = band.freqDisplay(channel);
        int labelW   = this.font.width(label);

        // Default: right of cursor; flip left when close to right edge
        int lx = mouseX + 4;
        if (lx + labelW + 2 > absCx + CANVAS_W) lx = mouseX - labelW - 4;
        lx = Math.clamp(lx, absCx + 1, absCx + CANVAS_W - labelW - 1);

        // Vertically: just above cursor, clamped inside canvas
        int ly = Math.clamp(mouseY - 11, absCy + 1, absCy + CANVAS_H - 10);

        graphics.fill(lx - 1, ly - 1, lx + labelW + 1, ly + 9, 0xBB000000);
        graphics.text(this.font, label, lx, ly, 0xFFFFFFFF, true);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // All coordinates here are panel-relative (renderer is pre-translated by leftPos, topPos).
        // Do NOT add leftPos/topPos — that would double the offset and break alignment.

        graphics.text(this.font, this.title, this.titleLabelX, 3, 0xFFAAAAAA, false);

        // Active band name centred between the two arrow buttons
        FrequencyBand band = menu.getActiveBand();
        String bandName    = band.displayName();
        int innerLeft  = CANVAS_X + ARR_W;
        int innerRight = CANVAS_X + CANVAS_W - ARR_W;
        int nameCx     = innerLeft + (innerRight - innerLeft) / 2;
        graphics.text(this.font, bandName,
                nameCx - this.font.width(bandName) / 2,
                NAV_Y + (NAV_H - 8) / 2,
                0xFFCCCCCC, false);

        // Frequency axis labels at left / centre / right of canvas (panel-relative X)
        renderAxisLabel(graphics, band, 0,                   CANVAS_X);
        renderAxisLabel(graphics, band, band.channels() / 2, CANVAS_X + CANVAS_W / 2);
        renderAxisLabel(graphics, band, band.channels() - 1, CANVAS_X + CANVAS_W - 1);
    }

    /** Panel-relative axis tick label. {@code labelCenterX} is the X around which the text is centred. */
    private void renderAxisLabel(GuiGraphicsExtractor graphics, FrequencyBand band,
                                 int channelIndex, int labelCenterX) {
        String label = band.freqDisplay(channelIndex);
        int lx = labelCenterX - this.font.width(label) / 2;
        int ly = CANVAS_Y + CANVAS_H + 4;
        graphics.text(this.font, label, lx, ly, 0xFF666666, false);
    }

    // ── Colour mapping ────────────────────────────────────────────────────────

    /** Maps a signal level (0–15) to an ARGB pixel colour per band. */
    private static int bandColor(FrequencyBand band, float signal) {
        int v = Math.clamp((int)(signal / 15f * 255f), 0, 255);
        return switch (band) {
            case ELF -> 0xFF000000 | ((v * 3 / 5) << 16)        | v;         // purple
            case VLF -> 0xFF000000                               | v;         // blue
            case LF  -> 0xFF000000 | ((v * 3 / 5) << 8)        | v;         // cyan
            case MF  -> 0xFF000000 | (v << 8);                               // green
            case HF  -> 0xFF000000 | ((v / 2) << 16) | (v << 8);            // yellow-green
            case VHF -> 0xFF000000 | (v << 16)        | (v << 8);           // yellow
            case UHF -> 0xFF000000 | (v << 16)        | ((v / 2) << 8);     // orange
            case SHF -> 0xFF000000 | (v << 16);                              // red
            case EHF -> 0xFF000000 | ((v * 4 / 5) << 16)        | (v / 5);  // dark crimson
        };
    }
}
