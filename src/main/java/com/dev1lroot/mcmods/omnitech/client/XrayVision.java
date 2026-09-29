/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import com.dev1lroot.mcmods.omnitech.blocks.OmniTechOreBlock;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helmet X-ray: a volumetric view through walls. The GPU only keeps the first surface
 * per pixel, so this ray-marches the world on the CPU instead: a {@link #W}×{@link #H}
 * grid of rays from the camera walks the voxels (Amanatides–Woo) up to {@link #RANGE}
 * blocks, summing each block's density × path length (mobs count as soft tissue). The
 * sum becomes a black-and-white X-ray film — dense rock glows through as layers, ores and
 * metal stand out, caves and hollows stay black — drawn full-screen under the HUD.
 *
 * <p>{@link #ROWS_PER_FRAME} rows are re-marched per frame, so the image refreshes every
 * few frames; linear texture filtering smooths the low resolution.
 */
public final class XrayVision {

    private static final int W = 160, H = 90;
    private static final int ROWS_PER_FRAME = 18;   // full image every 5 frames; ~115k block lookups/frame worst case
    private static final double RANGE = 32.0;
    /** Film response: brightness = 1 − exp(−K · Σ density·length). */
    private static final double K = 0.085;
    private static final double SATURATED = 4.0 / K;
    private static final double ENTITY_DENSITY = 0.45;

    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(OmniTech.MODID, "dynamic/helmet_xray");
    private static XrayTexture texture;
    private static final float[] film = new float[W * H];
    private static int nextRow;
    private static final List<AABB> tissue = new ArrayList<>();
    private static final Map<BlockState, Float> DENSITY = new IdentityHashMap<>();

    private XrayVision() {}

    /** Linear-filtered dynamic texture (the base class samples NEAREST). */
    private static final class XrayTexture extends DynamicTexture {
        XrayTexture() {
            super(() -> "Helmet X-ray", W, H, true);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    /** Drop the texture and film, e.g. when leaving the world. */
    public static void reset() {
        java.util.Arrays.fill(film, 0f);
        nextRow = 0;
    }

    /** RenderGuiEvent.Pre while X-ray mode is active: march some rows, upload, cover the view. */
    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (texture == null) {
            texture = new XrayTexture();
            mc.getTextureManager().register(TEXTURE_ID, texture);
        }

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 origin = camera.position();
        Vector3fc fwd = camera.forwardVector(), up = camera.upVector(), left = camera.leftVector();
        double tanV = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double tanH = tanV * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getGuiScaledHeight());

        if (nextRow == 0) collectTissue(mc, origin);
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
                film[y * W + x] = (float) march(mc.level, origin, dx / len, dy / len, dz / len, cursor);
            }
        }
        nextRow = end >= H ? 0 : end;

        NativeImage pixels = texture.getPixels();
        int grainSeed = (int) (mc.level.getGameTime() * 7919);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double b = 1.0 - Math.exp(-K * film[y * W + x]);
                b = Math.pow(b, 0.8) + ((hash(x, y, grainSeed) & 0xFF) / 255.0 - 0.5) * 0.05;
                int gray = (int) (Math.max(0.0, Math.min(1.0, b)) * 255);
                pixels.setPixel(x, y, 0xFF000000 | (gray << 16) | (gray << 8) | gray);
            }
        }
        texture.upload();

        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        g.blit(TEXTURE_ID, 0, 0, sw, sh, 0f, 1f, 0f, 1f);
    }

    private static void collectTissue(Minecraft mc, Vec3 origin) {
        tissue.clear();
        AABB area = new AABB(origin, origin).inflate(RANGE);
        for (Entity e : mc.level.getEntities(mc.player, area)) {
            if (e instanceof LivingEntity && e != mc.getCameraEntity()) tissue.add(e.getBoundingBox());
        }
    }

    /** Σ density × path length along the ray (voxel walk), plus soft tissue from mob boxes. */
    private static double march(ClientLevel level, Vec3 o, double dx, double dy, double dz, BlockPos.MutableBlockPos cursor) {
        int x = (int) Math.floor(o.x), y = (int) Math.floor(o.y), z = (int) Math.floor(o.z);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = Math.abs(1.0 / dx), tdy = Math.abs(1.0 / dy), tdz = Math.abs(1.0 / dz);
        double tx = (dx > 0 ? (x + 1 - o.x) : (o.x - x)) * tdx;
        double ty = (dy > 0 ? (y + 1 - o.y) : (o.y - y)) * tdy;
        double tz = (dz > 0 ? (z + 1 - o.z) : (o.z - z)) * tdz;

        double sum = 0, t = 0;
        while (t < RANGE && sum < SATURATED) {
            double next = Math.min(tx, Math.min(ty, tz));
            double segment = Math.min(next, RANGE) - t;
            if (t > 0.3) sum += density(level.getBlockState(cursor.set(x, y, z))) * segment;   // skip the block the eyes are in
            t = next;
            if (tx <= ty && tx <= tz) { x += sx; tx += tdx; }
            else if (ty <= tz) { y += sy; ty += tdy; }
            else { z += sz; tz += tdz; }
        }

        for (AABB box : tissue) sum += ENTITY_DENSITY * slabLength(box, o, dx, dy, dz);
        return sum;
    }

    /** Length of the ray segment inside an AABB (0 if missed), clipped to RANGE. */
    private static double slabLength(AABB b, Vec3 o, double dx, double dy, double dz) {
        double t0 = 0, t1 = RANGE;
        double[][] axes = {{o.x, dx, b.minX, b.maxX}, {o.y, dy, b.minY, b.maxY}, {o.z, dz, b.minZ, b.maxZ}};
        for (double[] a : axes) {
            if (Math.abs(a[1]) < 1e-9) {
                if (a[0] < a[2] || a[0] > a[3]) return 0;
                continue;
            }
            double ta = (a[2] - a[0]) / a[1], tb = (a[3] - a[0]) / a[1];
            t0 = Math.max(t0, Math.min(ta, tb));
            t1 = Math.min(t1, Math.max(ta, tb));
            if (t0 >= t1) return 0;
        }
        return t1 - t0;
    }

    /** Relative radiodensity per block state (cached). */
    static float density(BlockState state) {   // also used by GammaVision for attenuation
        Float cached = DENSITY.get(state);
        if (cached != null) return cached;
        float d;
        if (state.isAir()) d = 0f;
        else if (state.getBlock() instanceof LiquidBlock) d = state.getFluidState().is(FluidTags.LAVA) ? 0.9f : 0.3f;
        else if (state.is(Blocks.BEDROCK)) d = 2.2f;
        else if (state.is(BlockTags.LEAVES)) d = 0.08f;
        else if (state.getBlock() instanceof OmniTechOreBlock || state.is(Tags.Blocks.ORES)) d = 1.6f;
        else if (state.is(Tags.Blocks.STORAGE_BLOCKS)) d = 2.0f;
        else if (state.is(Tags.Blocks.GLASS_BLOCKS)) d = 0.25f;
        else if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) d = 0.4f;
        else if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)) d = 0.6f;
        else if (state.is(BlockTags.BLOCKS_MOTION_NO_LEAVES)) d = 0.85f;
        else d = 0.05f;   // plants, torches, rails…
        DENSITY.put(state, d);
        return d;
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
}
