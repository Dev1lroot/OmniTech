/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Helmet heat vision: a low-resolution thermal camera. Each ray from the camera stops at
 * the first thing it meets (thermal IR doesn't pass walls or glass):
 * <ul>
 *   <li>blocks — temperature from the block light in front of the visible face, plus the
 *       block's own light emission, capped at {@link #BLOCK_CAP} (ironbow palette);</li>
 *   <li>living entities — always red hot, drawn outside the block palette;</li>
 *   <li>sky / nothing within range — cold (black).</li>
 * </ul>
 * Drawn full-screen under the HUD from a linear-filtered {@value #W}×{@value #H} image,
 * refreshed a slice of rows per frame, like {@link XrayVision}.
 */
public final class ThermalVision {

    private static final int W = 160, H = 90;
    private static final int ROWS_PER_FRAME = 30;
    private static final double RANGE = 64.0;
    /** Hottest a block can read (0..1 on the palette); entities sit above it, off-palette. */
    private static final float BLOCK_CAP = 0.85f;
    private static final float SKY_WARMTH = 0.12f;
    private static final float ENTITY = -1f;   // marker: red-hot entity pixel

    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(OmniTech.MODID, "dynamic/helmet_thermal");
    private static ThermalTexture texture;
    private static final float[] heat = new float[W * H];
    private static int nextRow;
    private static final List<AABB> bodies = new ArrayList<>();

    /** Ironbow-style ramp for blocks: black → indigo → purple → magenta → orange. */
    private static final float[][] PALETTE = {
            {0.00f, 0x00, 0x00, 0x00},
            {0.25f, 0x16, 0x08, 0x46},
            {0.50f, 0x5A, 0x14, 0x8C},
            {0.70f, 0xB0, 0x1E, 0x78},
            {0.85f, 0xF0, 0x70, 0x20},
    };
    private static final int RED_HOT = 0xFFFF2410;

    private ThermalVision() {}

    private static final class ThermalTexture extends DynamicTexture {
        ThermalTexture() {
            super(() -> "Helmet thermal", W, H, true);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    public static void reset() {
        java.util.Arrays.fill(heat, 0f);
        nextRow = 0;
    }

    /** RenderGuiEvent.Pre while heat vision is active. */
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (texture == null) {
            texture = new ThermalTexture();
            mc.getTextureManager().register(TEXTURE_ID, texture);
        }

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 origin = camera.position();
        Vector3fc fwd = camera.forwardVector(), up = camera.upVector(), left = camera.leftVector();
        double tanV = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double tanH = tanV * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getGuiScaledHeight());

        if (nextRow == 0) {
            bodies.clear();
            for (Entity e : mc.level.getEntities(mc.player, new AABB(origin, origin).inflate(RANGE))) {
                if (e instanceof LivingEntity && e != mc.getCameraEntity()) bodies.add(e.getBoundingBox());
            }
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int end = Math.min(H, nextRow + ROWS_PER_FRAME);
        for (int y = nextRow; y < end; y++) {
            double v = 1.0 - (y + 0.5) / H * 2.0;
            for (int x = 0; x < W; x++) {
                double u = (x + 0.5) / W * 2.0 - 1.0;
                double dx = fwd.x() - left.x() * u * tanH + up.x() * v * tanV;
                double dy = fwd.y() - left.y() * u * tanH + up.y() * v * tanV;
                double dz = fwd.z() - left.z() * u * tanH + up.z() * v * tanV;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                heat[y * W + x] = sample(mc.level, origin, dx / len, dy / len, dz / len, cursor);
            }
        }
        nextRow = end >= H ? 0 : end;

        NativeImage pixels = texture.getPixels();
        int seed = (int) (mc.level.getGameTime() * 7919);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float t = heat[y * W + x];
                int color;
                if (t == ENTITY) color = RED_HOT;
                else color = ramp(Math.max(0f, t + ((hash(x, y, seed) & 0xFF) / 255f - 0.5f) * 0.03f));
                pixels.setPixel(x, y, color);
            }
        }
        texture.upload();

        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        g.blit(TEXTURE_ID, 0, 0, sw, sh, 0f, 1f, 0f, 1f);
        drawLegend(g, sw, sh);
    }

    /** First hit along the ray: its temperature on the palette, or {@link #ENTITY}. */
    private static float sample(ClientLevel level, Vec3 o, double dx, double dy, double dz, BlockPos.MutableBlockPos cursor) {
        double entityT = Double.MAX_VALUE;
        for (AABB box : bodies) {
            double t = entry(box, o, dx, dy, dz);
            if (t >= 0 && t < entityT) entityT = t;
        }

        int x = (int) Math.floor(o.x), y = (int) Math.floor(o.y), z = (int) Math.floor(o.z);
        int px = x, py = y, pz = z;   // the air cell in front of the hit face
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = Math.abs(1.0 / dx), tdy = Math.abs(1.0 / dy), tdz = Math.abs(1.0 / dz);
        double tx = (dx > 0 ? (x + 1 - o.x) : (o.x - x)) * tdx;
        double ty = (dy > 0 ? (y + 1 - o.y) : (o.y - y)) * tdy;
        double tz = (dz > 0 ? (z + 1 - o.z) : (o.z - z)) * tdz;
        double t = 0;
        while (t < RANGE && t < entityT) {
            px = x; py = y; pz = z;
            if (tx <= ty && tx <= tz) { t = tx; x += sx; tx += tdx; }
            else if (ty <= tz) { t = ty; y += sy; ty += tdy; }
            else { t = tz; z += sz; tz += tdz; }
            if (t >= entityT) break;
            BlockState state = level.getBlockState(cursor.set(x, y, z));
            if (state.isAir()) continue;
            int emitted = state.getLightEmission();
            cursor.set(px, py, pz);
            int blockLight = level.getBrightness(LightLayer.BLOCK, cursor);
            int skyLight = level.getBrightness(LightLayer.SKY, cursor);
            float temp = Math.max(emitted, blockLight) / 15f * BLOCK_CAP + skyLight / 15f * SKY_WARMTH;
            return Math.min(BLOCK_CAP, temp);
        }
        return entityT < RANGE ? ENTITY : 0f;
    }

    /** Distance along the ray to where it enters the box, or −1 if it misses. */
    private static double entry(AABB b, Vec3 o, double dx, double dy, double dz) {
        double t0 = 0, t1 = RANGE;
        double[][] axes = {{o.x, dx, b.minX, b.maxX}, {o.y, dy, b.minY, b.maxY}, {o.z, dz, b.minZ, b.maxZ}};
        for (double[] a : axes) {
            if (Math.abs(a[1]) < 1e-9) {
                if (a[0] < a[2] || a[0] > a[3]) return -1;
                continue;
            }
            double ta = (a[2] - a[0]) / a[1], tb = (a[3] - a[0]) / a[1];
            t0 = Math.max(t0, Math.min(ta, tb));
            t1 = Math.min(t1, Math.max(ta, tb));
            if (t0 > t1) return -1;
        }
        return t0;
    }

    private static int ramp(float t) {
        for (int i = 1; i < PALETTE.length; i++) {
            if (t <= PALETTE[i][0] || i == PALETTE.length - 1) {
                float[] a = PALETTE[i - 1], b = PALETTE[i];
                float k = Math.max(0f, Math.min(1f, (t - a[0]) / (b[0] - a[0])));
                int r = (int) (a[1] + (b[1] - a[1]) * k), gg = (int) (a[2] + (b[2] - a[2]) * k), bl = (int) (a[3] + (b[3] - a[3]) * k);
                return 0xFF000000 | (r << 16) | (gg << 8) | bl;
            }
        }
        return 0xFF000000;
    }

    /** Small cold → hot scale in the bottom-right corner, the entity colour at the top end. */
    private static void drawLegend(GuiGraphicsExtractor g, int sw, int sh) {
        var font = Minecraft.getInstance().font;
        int x0 = sw - 90, y0 = sh - 52, w = 70;
        for (int i = 0; i < w; i++) {
            int c = i < w - 8 ? ramp(BLOCK_CAP * i / (w - 9f)) : RED_HOT;
            g.fill(x0 + i, y0, x0 + i + 1, y0 + 6, c);
        }
        g.text(font, "COLD", x0, y0 + 8, 0xFFAAAAAA);
        g.text(font, "HOT", x0 + w - font.width("HOT"), y0 + 8, 0xFFFF6040);
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
}
