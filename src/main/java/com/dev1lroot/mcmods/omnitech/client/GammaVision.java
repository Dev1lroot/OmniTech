/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.radiation.ClientRadiationData;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Helmet gamma camera: sees radioactivity through walls. Each ray walks the voxels like
 * {@link XrayVision}, but instead of summing density it collects the gamma flux of every
 * radioactive block it passes — each source attenuated by the shielding in front of it
 * (Beer–Lambert on the X-ray density table, so lead/metal blocks and thick rock hide
 * sources) and by distance. The flux is drawn as a green → yellow → white haze over a
 * dimmed view, with sensor "sparkle" noise that grows with the ambient fallout dose from
 * {@link ClientRadiationData}, plus Geiger clicks
 * (dose figures are on the helmet HUD).
 */
public final class GammaVision {

    private static final int W = 120, H = 68;
    private static final int ROWS_PER_FRAME = 17;   // full image every 4 frames
    private static final double RANGE = 40.0;
    /** Shielding: flux × exp(−MU · Σ density·length) between camera and source. */
    private static final double MU = 0.22;
    /** Display response: brightness = 1 − exp(−GAIN · flux). */
    private static final double GAIN = 2.2;

    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(OmniTech.MODID, "dynamic/helmet_gamma");
    private static GammaTexture texture;
    private static final float[] flux = new float[W * H];
    private static int nextRow;
    private static final Map<Block, Float> ACTIVITY = new IdentityHashMap<>();

    /** Relative gamma activity of the mod's radioactive blocks (by registry path). */
    private static final Map<String, Float> SOURCES = Map.ofEntries(
            Map.entry("radium_block", 6f), Map.entry("polonium_block", 5f),
            Map.entry("curium_block", 5f), Map.entry("plutonium_block", 4.5f),
            Map.entry("americium_block", 4f), Map.entry("actinium_block", 4f),
            Map.entry("reactor_cell", 4f), Map.entry("nuclear_bomb", 3.5f),
            Map.entry("neptunium_block", 3f), Map.entry("protactinium_block", 3f),
            Map.entry("uranium_block", 2.5f), Map.entry("thorium_block", 1.5f),
            Map.entry("uraninite", 1.0f), Map.entry("carnotite", 0.6f),
            Map.entry("monazite", 0.4f), Map.entry("xenotime", 0.3f));

    /** Haze ramp: transparent → green → yellow → white. {t, a, r, g, b} */
    private static final float[][] PALETTE = {
            {0.00f, 0x00, 0x00, 0x00, 0x00},
            {0.15f, 0x50, 0x10, 0x90, 0x20},
            {0.45f, 0xB0, 0x40, 0xF0, 0x30},
            {0.75f, 0xE0, 0xF0, 0xF0, 0x40},
            {1.00f, 0xF0, 0xFF, 0xFF, 0xE0},
    };

    private GammaVision() {}

    private static final class GammaTexture extends DynamicTexture {
        GammaTexture() {
            super(() -> "Helmet gamma", W, H, true);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    public static void reset() {
        java.util.Arrays.fill(flux, 0f);
        nextRow = 0;
    }

    /** RenderGuiEvent.Pre while the gamma camera is active. */
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (texture == null) {
            texture = new GammaTexture();
            mc.getTextureManager().register(TEXTURE_ID, texture);
        }

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 origin = camera.position();
        Vector3fc fwd = camera.forwardVector(), up = camera.upVector(), left = camera.leftVector();
        double tanV = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double tanH = tanV * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getGuiScaledHeight());

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
                flux[y * W + x] = (float) march(mc.level, origin, dx / len, dy / len, dz / len, cursor);
            }
        }
        nextRow = end >= H ? 0 : end;

        float ambient = ClientRadiationData.getRadiationLevel(mc.player);
        NativeImage pixels = texture.getPixels();
        int seed = (int) (mc.level.getGameTime() * 7919 + System.nanoTime() / 50_000_000L);
        // Sensor hits: random single-pixel sparkles, more of them in fallout.
        int sparkleChance = (int) (2 + ambient * 60);   // per 1024
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double b = 1.0 - Math.exp(-GAIN * flux[y * W + x]);
                int h = hash(x, y, seed);
                if ((h & 0x3FF) < sparkleChance) b = Math.max(b, 0.5 + ((h >>> 10) & 0xFF) / 510.0);
                pixels.setPixel(x, y, ramp((float) Math.min(1.0, b + ambient * 0.12)));
            }
        }
        texture.upload();

        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        g.fill(0, 0, sw, sh, 0xA0000A04);   // dim, faintly green: the camera's view is a detector, not an eye
        g.blit(TEXTURE_ID, 0, 0, sw, sh, 0f, 1f, 0f, 1f);

        ClientRadiationData.tickCrackSound(Math.max(ambient, (float) (1.0 - Math.exp(-0.6 * nearbyFlux(mc.level, origin, cursor)))));
    }

    /** Gamma flux reaching the camera along the ray: Σ activity · shielding · 1/r². */
    private static double march(ClientLevel level, Vec3 o, double dx, double dy, double dz, BlockPos.MutableBlockPos cursor) {
        int x = (int) Math.floor(o.x), y = (int) Math.floor(o.y), z = (int) Math.floor(o.z);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = Math.abs(1.0 / dx), tdy = Math.abs(1.0 / dy), tdz = Math.abs(1.0 / dz);
        double tx = (dx > 0 ? (x + 1 - o.x) : (o.x - x)) * tdx;
        double ty = (dy > 0 ? (y + 1 - o.y) : (o.y - y)) * tdy;
        double tz = (dz > 0 ? (z + 1 - o.z) : (o.z - z)) * tdz;

        double sum = 0, shield = 0, t = 0;
        while (t < RANGE && shield < 30) {
            double next = Math.min(tx, Math.min(ty, tz));
            double segment = Math.min(next, RANGE) - t;
            BlockState state = level.getBlockState(cursor.set(x, y, z));
            float a = activity(state);
            if (a > 0) {
                double r = Math.max(1.0, t);
                sum += a * segment * Math.exp(-MU * shield) / (1.0 + r * r * 0.015);
            }
            if (t > 0.3) shield += XrayVision.density(state) * segment;
            t = next;
            if (tx <= ty && tx <= tz) { x += sx; tx += tdx; }
            else if (ty <= tz) { y += sy; ty += tdy; }
            else { z += sz; tz += tdz; }
        }
        return sum;
    }

    /** Isotropic dose from sources within a few blocks, for the Geiger counter (unshielded, coarse). */
    private static double nearbyFlux(ClientLevel level, Vec3 o, BlockPos.MutableBlockPos cursor) {
        int cx = (int) Math.floor(o.x), cy = (int) Math.floor(o.y), cz = (int) Math.floor(o.z);
        double sum = 0;
        for (int x = -6; x <= 6; x++)
            for (int y = -6; y <= 6; y++)
                for (int z = -6; z <= 6; z++) {
                    float a = activity(level.getBlockState(cursor.set(cx + x, cy + y, cz + z)));
                    if (a > 0) sum += a / (1.0 + x * x + y * y + z * z);
                }
        return sum;
    }

    private static float activity(BlockState state) {
        Block block = state.getBlock();
        Float cached = ACTIVITY.get(block);
        if (cached != null) return cached;
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        float a = OmniTech.MODID.equals(id.getNamespace()) ? SOURCES.getOrDefault(id.getPath(), 0f) : 0f;
        ACTIVITY.put(block, a);
        return a;
    }

    private static int ramp(float t) {
        for (int i = 1; i < PALETTE.length; i++) {
            if (t <= PALETTE[i][0] || i == PALETTE.length - 1) {
                float[] p = PALETTE[i - 1], q = PALETTE[i];
                float k = Math.max(0f, Math.min(1f, (t - p[0]) / (q[0] - p[0])));
                int a = (int) (p[1] + (q[1] - p[1]) * k), r = (int) (p[2] + (q[2] - p[2]) * k);
                int gg = (int) (p[3] + (q[3] - p[3]) * k), bl = (int) (p[4] + (q[4] - p[4]) * k);
                return (a << 24) | (r << 16) | (gg << 8) | bl;
            }
        }
        return 0;
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
}
